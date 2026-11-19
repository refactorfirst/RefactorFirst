using Microsoft.Build.Locator;
using Microsoft.CodeAnalysis;
using Microsoft.CodeAnalysis.MSBuild;

namespace CSharpFactExtractor;

public static class Extractor
{
    private static readonly object RegistrationLock = new();

    public static Task<Facts> RunAsync(string solutionOrProject, string root, bool excludeTests) =>
        RunAsync(new[] { solutionOrProject }, root, excludeTests);

    /// One solution, or several project files when the repository has no solution at its root.
    public static async Task<Facts> RunAsync(IReadOnlyList<string> targets, string root, bool excludeTests)
    {
        lock (RegistrationLock)
        {
            if (!MSBuildLocator.IsRegistered) MSBuildLocator.RegisterDefaults();
        }
        return await ExtractAsync(targets, root, excludeTests);
    }

    // Separate method: nothing that touches Microsoft.Build types may be JIT-compiled before the locator has run.
    private static async Task<Facts> ExtractAsync(IReadOnlyList<string> targets, string root, bool excludeTests)
    {
        using var workspace = MSBuildWorkspace.Create();
        foreach (string target in targets.Select(Path.GetFullPath))
        {
            if (!target.EndsWith(".csproj", StringComparison.OrdinalIgnoreCase))
            {
                await workspace.OpenSolutionAsync(target);
            }
            // A project already pulled in as another project's reference is not opened twice.
            else if (!workspace.CurrentSolution.Projects.Any(p => string.Equals(p.FilePath, target, StringComparison.OrdinalIgnoreCase)))
            {
                await workspace.OpenProjectAsync(target);
            }
        }
        Solution solution = workspace.CurrentSolution;

        // Unrestored packages and broken project files show up here; without them types stop resolving.
        var failures = workspace.Diagnostics.Where(d => d.Kind == WorkspaceDiagnosticKind.Failure).ToList();
        if (failures.Count > 0)
        {
            Console.Error.WriteLine($"warning: MSBuild reported {failures.Count} problem(s) loading the code; results may be incomplete. Has `dotnet restore` been run?");
            foreach (WorkspaceDiagnostic failure in failures.Take(3)) Console.Error.WriteLine("  " + failure.Message.Split('\n')[0]);
        }

        var types = new Dictionary<string, TypeFacts>();
        var duplicates = new SortedSet<string>(StringComparer.Ordinal);
        foreach (Project project in solution.Projects)
        {
            if (excludeTests && TestProjects.IsTestProject(project.Name, project.MetadataReferences.Select(r => r.Display ?? ""))) continue;
            Compilation? compilation = await project.GetCompilationAsync();
            if (compilation is null) continue;
            foreach (INamedTypeSymbol type in AllTypes(compilation.Assembly.GlobalNamespace))
            {
                if (type.TypeKind == TypeKind.Delegate || TypeNames.Namespace(type).Length == 0) continue;
                string fqn = TypeNames.Fqn(type);
                // The same full name in two projects: the first one is kept, and the run says so below.
                if (types.ContainsKey(fqn))
                {
                    duplicates.Add(fqn);
                    continue;
                }
                MemberFacts members = MemberWalker.Collect(type, compilation);
                types[fqn] = new TypeFacts(
                    fqn, TypeNames.Namespace(type), type.Name, LargestFile(type, root), Loc(type),
                    Parent(type), members.Attributes, DependencyWalker.Collect(type, compilation), members.Methods,
                    members.ProtectedMembers, members.OverriddenMethods, members.UsedParentMembers);
            }
        }
        if (duplicates.Count > 0)
        {
            Console.Error.WriteLine($"warning: {duplicates.Count} type name(s) are declared in more than one project; only the first of each was analysed.");
            foreach (string duplicate in duplicates.Take(3)) Console.Error.WriteLine("  " + duplicate);
        }
        var list = types.Values.OrderBy(t => t.Fqn, StringComparer.Ordinal).ToList();
        return new Facts(1, root, list.Select(t => t.Namespace).Distinct().OrderBy(n => n, StringComparer.Ordinal).ToList(), list);
    }

    private static IEnumerable<INamedTypeSymbol> AllTypes(INamespaceSymbol ns)
    {
        foreach (INamedTypeSymbol t in ns.GetTypeMembers())
            foreach (INamedTypeSymbol x in WithNested(t)) yield return x;
        foreach (INamespaceSymbol child in ns.GetNamespaceMembers())
            foreach (INamedTypeSymbol x in AllTypes(child)) yield return x;
    }

    private static IEnumerable<INamedTypeSymbol> WithNested(INamedTypeSymbol t)
    {
        if (!TypeNames.IsInSource(t) || t.IsImplicitlyDeclared || t.IsExtension) yield break;
        // A type that exists only in generated files is left out, along with anything nested in it.
        if (!TypeNames.SourceReferences(t).Any()) yield break;
        yield return t;
        foreach (INamedTypeSymbol n in t.GetTypeMembers())
            foreach (INamedTypeSymbol x in WithNested(n)) yield return x;
    }

    private static string LargestFile(INamedTypeSymbol type, string root) =>
        TypeNames.SourceReferences(type)
            .OrderByDescending(r => r.Span.Length)
            .Select(r => Path.GetRelativePath(root, r.SyntaxTree.FilePath).Replace('\\', '/'))
            .First();

    private static int Loc(INamedTypeSymbol type) =>
        TypeNames.SourceReferences(type).Sum(r =>
        {
            var span = r.SyntaxTree.GetLineSpan(r.Span);
            return span.EndLinePosition.Line - span.StartLinePosition.Line + 1;
        });

    private static string? Parent(INamedTypeSymbol type) =>
        type.BaseType is { } b && TypeNames.IsInSource(b) ? TypeNames.Fqn(b) : null;
}
