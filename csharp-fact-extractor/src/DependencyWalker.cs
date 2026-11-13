using Microsoft.CodeAnalysis;
using Microsoft.CodeAnalysis.CSharp.Syntax;

namespace CSharpFactExtractor;

public static class DependencyWalker
{
    /// Every name inside the type's code that resolves to another source type, or to a member of one.
    public static List<DependencyFacts> Collect(INamedTypeSymbol type, Compilation compilation)
    {
        string self = TypeNames.Fqn(type);
        var counts = new Dictionary<string, int>();
        foreach (SyntaxReference reference in TypeNames.SourceReferences(type))
        {
            SyntaxNode declaration = reference.GetSyntax();
            SemanticModel model = compilation.GetSemanticModel(reference.SyntaxTree);
            // Stay out of nested type declarations: their names belong to the nested type, not this one.
            var names = declaration
                .DescendantNodes(node => node == declaration || node is not BaseTypeDeclarationSyntax)
                .OfType<SimpleNameSyntax>();
            foreach (SimpleNameSyntax name in names)
            {
                ISymbol? symbol = model.GetSymbolInfo(name).Symbol;
                INamedTypeSymbol? target = symbol as INamedTypeSymbol ?? symbol?.ContainingType;
                if (target is null || target.TypeKind == TypeKind.Delegate || !TypeNames.IsInSource(target)) continue;
                if (TypeNames.Namespace(target).Length == 0) continue;
                string fqn = TypeNames.Fqn(target);
                if (fqn == self) continue;
                counts[fqn] = counts.GetValueOrDefault(fqn) + 1;
            }
        }
        return counts.OrderBy(c => c.Key, StringComparer.Ordinal).Select(c => new DependencyFacts(c.Key, c.Value)).ToList();
    }
}
