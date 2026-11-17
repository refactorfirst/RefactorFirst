using Microsoft.CodeAnalysis;

namespace CSharpFactExtractor;

public static class TypeNames
{
    /// Namespace + name; nested types joined with '$' so the namespace stays the package.
    /// A generic type keeps its plain name unless a type with the same name sits beside it
    /// (Result and Result<T>); then the generic one is named the .NET way, "Result`1".
    public static string Fqn(INamedTypeSymbol type)
    {
        var names = new Stack<string>();
        for (INamedTypeSymbol? t = type.OriginalDefinition; t != null; t = t.ContainingType)
            names.Push(t.Arity > 0 && HasNamesake(t) ? t.Name + "`" + t.Arity : t.Name);
        var ns = Namespace(type);
        return (ns.Length == 0 ? "" : ns + ".") + string.Join("$", names);
    }

    /// True when another type with the same name is declared in the same namespace or outer type of the same project.
    private static bool HasNamesake(INamedTypeSymbol type)
    {
        INamespaceOrTypeSymbol? container = (INamespaceOrTypeSymbol?)type.ContainingType ?? type.ContainingNamespace;
        return container is not null && container.GetTypeMembers(type.Name).Count(IsInSource) > 1;
    }

    public static string Namespace(INamedTypeSymbol type) =>
        type.ContainingNamespace is { IsGlobalNamespace: false } ns ? ns.ToDisplayString() : "";

    public static bool IsInSource(ISymbol symbol) => symbol.Locations.Any(l => l.IsInSource);

    private static readonly string[] GeneratedSuffixes = { ".g.cs", ".g.i.cs", ".designer.cs", ".generated.cs" };

    /// Build output and tool-generated files (Razor, designers, source generators): nobody refactors these.
    public static bool IsGeneratedPath(string? path)
    {
        if (string.IsNullOrEmpty(path)) return true;
        string normalized = path.Replace('\\', '/');
        if (normalized.Contains("/obj/") || normalized.Contains("/bin/")) return true;
        return GeneratedSuffixes.Any(suffix => normalized.EndsWith(suffix, StringComparison.OrdinalIgnoreCase));
    }

    /// The symbol's declarations that are in hand-written files.
    public static IEnumerable<SyntaxReference> SourceReferences(ISymbol symbol) =>
        symbol.DeclaringSyntaxReferences.Where(r => !IsGeneratedPath(r.SyntaxTree.FilePath));

    /// True when the symbol is declared only in generated files.
    public static bool IsGeneratedOnly(ISymbol symbol) =>
        symbol.DeclaringSyntaxReferences.Length > 0 && !SourceReferences(symbol).Any();

    // Nullable annotations ("string?") are left out so a method's own signature and a caller's view of it always match.
    private static readonly SymbolDisplayFormat ParameterFormat = SymbolDisplayFormat.MinimallyQualifiedFormat.WithMiscellaneousOptions(
        SymbolDisplayFormat.MinimallyQualifiedFormat.MiscellaneousOptions & ~SymbolDisplayMiscellaneousOptions.IncludeNullableReferenceTypeModifier);

    /// Type parameters and ref/out/in are part of the name: Save() and Save<T>(), Add(int) and Add(ref int) are different methods.
    public static string Signature(IMethodSymbol method)
    {
        string typeParameters = method.Arity > 0 ? "<" + string.Join(",", method.TypeParameters.Select(t => t.Name)) + ">" : "";
        return method.Name + typeParameters + "(" + string.Join(",", method.Parameters.Select(Parameter)) + ")";
    }

    private static string Parameter(IParameterSymbol parameter)
    {
        string passedBy = parameter.RefKind switch
        {
            RefKind.Ref => "ref ",
            RefKind.Out => "out ",
            RefKind.In => "in ",
            RefKind.RefReadOnlyParameter => "ref readonly ",
            _ => "",
        };
        return passedBy + parameter.Type.ToDisplayString(ParameterFormat);
    }
}
