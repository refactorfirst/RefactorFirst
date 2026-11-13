using Microsoft.CodeAnalysis;

namespace CSharpFactExtractor;

public static class TypeNames
{
    /// Namespace + name; nested types joined with '$' so the namespace stays the package.
    public static string Fqn(INamedTypeSymbol type)
    {
        var names = new Stack<string>();
        for (INamedTypeSymbol? t = type.OriginalDefinition; t != null; t = t.ContainingType) names.Push(t.Name);
        var ns = Namespace(type);
        return (ns.Length == 0 ? "" : ns + ".") + string.Join("$", names);
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

    public static string Signature(IMethodSymbol method) =>
        method.Name + "(" + string.Join(",", method.Parameters.Select(p => p.Type.ToDisplayString(ParameterFormat))) + ")";
}
