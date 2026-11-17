using Microsoft.CodeAnalysis;
using Microsoft.CodeAnalysis.CSharp;
using Microsoft.CodeAnalysis.CSharp.Syntax;

namespace CSharpFactExtractor;

/// Attributes, methods and per-method facts for one type.
/// Counting follows the Java reader (ComplexityCalculator and MetricsVisitorLogic) so one set of thresholds fits both languages.
public record MemberFacts(
    List<AttributeFacts> Attributes, List<MethodFacts> Methods,
    int ProtectedMembers, List<string> OverriddenMethods, List<string> UsedParentMembers);

public static class MemberWalker
{
    public static MemberFacts Collect(INamedTypeSymbol type, Compilation compilation)
    {
        // The base class, when it is part of the analysed code: its members used here feed Refused Parent Bequest.
        string? parent = type.BaseType is { } b && TypeNames.IsInSource(b) ? TypeNames.Fqn(b) : null;
        var usedParentMembers = new SortedSet<string>(StringComparer.Ordinal);
        var overridden = new List<string>();
        int protectedMembers = 0;
        var attributes = new List<AttributeFacts>();
        var methods = new List<MethodFacts>();
        var autoProperties = new HashSet<IPropertySymbol>(SymbolEqualityComparer.Default);

        if (type.TypeKind != TypeKind.Enum)
        {
            foreach (ISymbol member in type.GetMembers())
            {
                if (TypeNames.IsGeneratedOnly(member)) continue;
                if (IsProtected(member) && member is IFieldSymbol { IsImplicitlyDeclared: false } or IPropertySymbol) protectedMembers++;
                switch (member)
                {
                    // An auto-property is a private attribute plus its accessor methods, as a Java field with getter and setter.
                    case IFieldSymbol { AssociatedSymbol: IPropertySymbol property }:
                        autoProperties.Add(property);
                        attributes.Add(new AttributeFacts(property.Name, false));
                        break;
                    case IFieldSymbol { IsImplicitlyDeclared: false } field:
                        attributes.Add(new AttributeFacts(field.Name, field.DeclaredAccessibility == Accessibility.Public));
                        break;
                }
            }
        }

        foreach (IMethodSymbol method in type.GetMembers().OfType<IMethodSymbol>())
        {
            if (method.IsImplicitlyDeclared && method.AssociatedSymbol is not IPropertySymbol) continue;
            if (!IsCounted(method.MethodKind)) continue;
            if (TypeNames.IsGeneratedOnly(method)) continue;
            if (method.MethodKind == MethodKind.Ordinary && IsProtected(method)) protectedMembers++;
            MethodFacts facts = Describe(method, type, compilation, autoProperties, parent, usedParentMembers);
            if (method.IsOverride && method.MethodKind == MethodKind.Ordinary) overridden.Add(facts.Signature);
            methods.Add(facts);
        }

        return new MemberFacts(
            attributes.OrderBy(a => a.Name, StringComparer.Ordinal).ToList(),
            methods.OrderBy(m => m.Signature, StringComparer.Ordinal).ToList(),
            protectedMembers,
            overridden.OrderBy(s => s, StringComparer.Ordinal).ToList(),
            usedParentMembers.ToList());
    }

    private static bool IsProtected(ISymbol symbol) => symbol.DeclaredAccessibility is
        Accessibility.Protected or Accessibility.ProtectedOrInternal or Accessibility.ProtectedAndInternal;

    private static bool IsCounted(MethodKind kind) => kind is
        MethodKind.Ordinary or MethodKind.Constructor or MethodKind.StaticConstructor or MethodKind.Destructor or
        MethodKind.UserDefinedOperator or MethodKind.Conversion or MethodKind.PropertyGet or MethodKind.PropertySet or
        MethodKind.ExplicitInterfaceImplementation;

    private static MethodFacts Describe(
        IMethodSymbol method, INamedTypeSymbol type, Compilation compilation, HashSet<IPropertySymbol> autoProperties,
        string? parent, SortedSet<string> usedParentMembers)
    {
        string self = TypeNames.Fqn(type);
        var variables = new SortedSet<string>(StringComparer.Ordinal);
        var ownAttributes = new SortedSet<string>(StringComparer.Ordinal);
        var foreignAttributes = new SortedSet<string>(StringComparer.Ordinal);
        var foreignClasses = new SortedSet<string>(StringComparer.Ordinal);
        var foreignMethods = new SortedSet<string>(StringComparer.Ordinal);
        var foreignMethodClasses = new SortedSet<string>(StringComparer.Ordinal);
        var complexity = new ComplexityWalker();
        int loc = 0, statements = 0;
        bool hasBody = false;

        // A partial method's declaration has no body; the body is in its other half, possibly in another file.
        IEnumerable<SyntaxReference> references = method.PartialImplementationPart is { } implementation
            ? TypeNames.SourceReferences(method).Concat(TypeNames.SourceReferences(implementation))
            : TypeNames.SourceReferences(method);

        foreach (SyntaxReference reference in references)
        {
            SyntaxNode node = reference.GetSyntax();
            // A primary constructor is declared by the type itself; its length is its parameter list, not the whole type.
            SyntaxNode measured = node is TypeDeclarationSyntax { ParameterList: { } parameters } ? parameters : node;
            var span = measured.SyntaxTree.GetLineSpan(measured.Span);
            loc += span.EndLinePosition.Line - span.StartLinePosition.Line + 1;

            // The part that runs: a block or an expression body. An auto-accessor (`get;`) has neither.
            SyntaxNode? body = node switch
            {
                BaseMethodDeclarationSyntax m => (SyntaxNode?)m.Body ?? m.ExpressionBody,
                AccessorDeclarationSyntax a => (SyntaxNode?)a.Body ?? a.ExpressionBody,
                ArrowExpressionClauseSyntax arrow => arrow,
                _ => null,
            };
            if (body is null) continue;
            hasBody = true;
            statements += body is BlockSyntax block ? block.Statements.Count : 1;
            complexity.Visit(body);

            SemanticModel model = compilation.GetSemanticModel(node.SyntaxTree);
            // A constructor's `: base(...)` / `: this(...)` call is part of what it runs.
            IEnumerable<SyntaxNode> scope = node is ConstructorDeclarationSyntax { Initializer: { } initializer }
                ? body.DescendantNodesAndSelf().Concat(initializer.DescendantNodesAndSelf())
                : body.DescendantNodesAndSelf();

            foreach (SyntaxNode n in scope)
            {
                switch (n)
                {
                    // Locals count as accessed variables, as in the Java reader.
                    case VariableDeclaratorSyntax local when local.Parent?.Parent is LocalDeclarationStatementSyntax or ForStatementSyntax or UsingStatementSyntax:
                        variables.Add(local.Identifier.ValueText);
                        break;
                    case ForEachStatementSyntax each:
                        variables.Add(each.Identifier.ValueText);
                        break;
                    case SingleVariableDesignationSyntax designation:
                        variables.Add(designation.Identifier.ValueText);
                        break;
                    case SimpleNameSyntax name:
                        Classify(model.GetSymbolInfo(name).Symbol, name);
                        break;
                }
            }
        }

        void Classify(ISymbol? symbol, SimpleNameSyntax name)
        {
            switch (symbol)
            {
                case IFieldSymbol field when field.ContainingType is { } owner:
                    if (parent != null && TypeNames.Fqn(owner) == parent) usedParentMembers.Add(field.Name);
                    variables.Add(field.Name);
                    if (TypeNames.Fqn(owner) == self) ownAttributes.Add(field.Name);
                    else
                    {
                        foreignClasses.Add(TypeNames.Fqn(owner));
                        foreignAttributes.Add(TypeNames.Fqn(owner) + "." + field.Name);
                    }
                    break;

                case IPropertySymbol property when property.ContainingType is { } owner:
                    if (parent != null && TypeNames.Fqn(owner) == parent) usedParentMembers.Add(property.Name);
                    if (TypeNames.Fqn(owner) == self)
                    {
                        // Using your own auto-property is using the attribute behind it.
                        if (autoProperties.Contains(property.OriginalDefinition))
                        {
                            variables.Add(property.Name);
                            ownAttributes.Add(property.Name);
                        }
                    }
                    else
                    {
                        // Someone else's property is a call to its accessor, as a Java getter or setter is.
                        foreach (IMethodSymbol accessor in AccessorsUsed(property, name))
                        {
                            foreignMethods.Add(TypeNames.Fqn(owner) + "." + TypeNames.Signature(accessor.OriginalDefinition));
                            foreignMethodClasses.Add(TypeNames.Fqn(owner));
                        }
                    }
                    break;

                case IMethodSymbol called when called.MethodKind is MethodKind.Ordinary or MethodKind.ReducedExtension:
                    IMethodSymbol definition = (called.ReducedFrom ?? called).OriginalDefinition;
                    if (definition.ContainingType is { } declaring && TypeNames.Fqn(declaring) != self)
                    {
                        if (parent != null && TypeNames.Fqn(declaring) == parent) usedParentMembers.Add(definition.Name);
                        foreignMethods.Add(TypeNames.Fqn(declaring) + "." + TypeNames.Signature(definition));
                        foreignMethodClasses.Add(TypeNames.Fqn(declaring));
                    }
                    break;
            }
        }

        bool isPropertyAccessor = method.MethodKind is MethodKind.PropertyGet or MethodKind.PropertySet;
        // An auto-accessor (`get;` / `set;`) has no body but does touch the attribute behind it, as a Java getter touches its field.
        if (isPropertyAccessor && !hasBody && method.AssociatedSymbol is IPropertySymbol own && autoProperties.Contains(own))
        {
            variables.Add(own.Name);
            ownAttributes.Add(own.Name);
        }
        return new MethodFacts(
            method.MethodKind is MethodKind.Constructor or MethodKind.StaticConstructor ? type.Name : method.Name,
            SignatureOf(method, type),
            Math.Max(loc, 1),
            complexity.Cyclomatic,
            complexity.MaxNesting,
            method.Parameters.Length,
            isPropertyAccessor || LooksLikeAnAccessor(method.Name, hasBody, statements),
            method.MethodKind is MethodKind.Constructor or MethodKind.StaticConstructor,
            variables.ToList(), ownAttributes.ToList(), foreignAttributes.ToList(), foreignClasses.ToList(),
            foreignMethods.ToList(), foreignMethodClasses.ToList());
    }

    /// Constructors are named after their type (Roslyn calls them ".ctor").
    private static string SignatureOf(IMethodSymbol method, INamedTypeSymbol type)
    {
        string signature = TypeNames.Signature(method);
        return method.MethodKind switch
        {
            MethodKind.Constructor => type.Name + signature[signature.IndexOf('(')..],
            // Kept apart from the parameterless instance constructor, which is also written "Name()".
            MethodKind.StaticConstructor => "static " + type.Name + signature[signature.IndexOf('(')..],
            _ => signature,
        };
    }

    /// The setter when the property is assigned, the getter when it is read, both for `+=` and `++`.
    /// Falls back to whichever accessor exists.
    private static List<IMethodSymbol> AccessorsUsed(IPropertySymbol property, SimpleNameSyntax name)
    {
        // `order.Total` is the expression that is read or written, not the bare name inside it.
        ExpressionSyntax expression = name.Parent is MemberAccessExpressionSyntax access && access.Name == name ? access : name;
        bool written = false, read = true;
        switch (expression.Parent)
        {
            case AssignmentExpressionSyntax assignment when assignment.Left == expression:
                written = true;
                read = !assignment.IsKind(SyntaxKind.SimpleAssignmentExpression);
                break;
            case PrefixUnaryExpressionSyntax prefix when prefix.IsKind(SyntaxKind.PreIncrementExpression) || prefix.IsKind(SyntaxKind.PreDecrementExpression):
            case PostfixUnaryExpressionSyntax postfix when postfix.IsKind(SyntaxKind.PostIncrementExpression) || postfix.IsKind(SyntaxKind.PostDecrementExpression):
                written = true;
                break;
        }
        IMethodSymbol? getter = property.GetMethod, setter = property.SetMethod;
        var used = new List<IMethodSymbol>();
        if (read && (getter ?? setter) is { } readWith) used.Add(readWith);
        if (written && (setter ?? getter) is { } writtenWith && !used.Contains(writtenWith, SymbolEqualityComparer.Default)) used.Add(writtenWith);
        return used;
    }

    /// The Java reader's rule: named get/is/set and at most one statement.
    private static bool LooksLikeAnAccessor(string name, bool hasBody, int statements) =>
        hasBody && statements <= 1 &&
        (name.StartsWith("Get", StringComparison.Ordinal) || name.StartsWith("Is", StringComparison.Ordinal) || name.StartsWith("Set", StringComparison.Ordinal));

    /// Mirrors ComplexityCalculator.java: +1 for if, each loop, each non-default case, catch, &&, || and ?:.
    /// Nesting counts if, loops and catch.
    private sealed class ComplexityWalker : CSharpSyntaxWalker
    {
        private int _nesting;
        public int Cyclomatic { get; private set; } = 1;
        public int MaxNesting { get; private set; }

        private void Nested(Action visitChildren)
        {
            Cyclomatic++;
            _nesting++;
            MaxNesting = Math.Max(MaxNesting, _nesting);
            visitChildren();
            _nesting--;
        }

        public override void VisitIfStatement(IfStatementSyntax node) => Nested(() => base.VisitIfStatement(node));
        public override void VisitForStatement(ForStatementSyntax node) => Nested(() => base.VisitForStatement(node));
        public override void VisitForEachStatement(ForEachStatementSyntax node) => Nested(() => base.VisitForEachStatement(node));
        public override void VisitForEachVariableStatement(ForEachVariableStatementSyntax node) => Nested(() => base.VisitForEachVariableStatement(node));
        public override void VisitWhileStatement(WhileStatementSyntax node) => Nested(() => base.VisitWhileStatement(node));
        public override void VisitDoStatement(DoStatementSyntax node) => Nested(() => base.VisitDoStatement(node));
        public override void VisitCatchClause(CatchClauseSyntax node) => Nested(() => base.VisitCatchClause(node));

        public override void VisitCaseSwitchLabel(CaseSwitchLabelSyntax node)
        {
            Cyclomatic++;
            base.VisitCaseSwitchLabel(node);
        }

        public override void VisitCasePatternSwitchLabel(CasePatternSwitchLabelSyntax node)
        {
            Cyclomatic++;
            base.VisitCasePatternSwitchLabel(node);
        }

        public override void VisitSwitchExpressionArm(SwitchExpressionArmSyntax node)
        {
            if (node.Pattern is not DiscardPatternSyntax) Cyclomatic++;
            base.VisitSwitchExpressionArm(node);
        }

        public override void VisitBinaryExpression(BinaryExpressionSyntax node)
        {
            if (node.IsKind(SyntaxKind.LogicalAndExpression) || node.IsKind(SyntaxKind.LogicalOrExpression)) Cyclomatic++;
            base.VisitBinaryExpression(node);
        }

        public override void VisitConditionalExpression(ConditionalExpressionSyntax node)
        {
            Cyclomatic++;
            base.VisitConditionalExpression(node);
        }
    }
}
