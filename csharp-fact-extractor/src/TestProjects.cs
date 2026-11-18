using System.Text.RegularExpressions;

namespace CSharpFactExtractor;

/// Decides which projects hold tests, so they can be left out like src/test is on the Java side.
public static class TestProjects
{
    // "App.Tests", "AppTest", "App.UnitTests", "App.Specs". Case matters: "Contest" is not a test project.
    private static readonly Regex TestName = new(@"(Tests?|Specs?)$", RegexOptions.Compiled);

    // A project loaded once per target framework is named "App.Tests(net8.0)".
    private static readonly Regex TargetFrameworkSuffix = new(@"\([^()]*\)$", RegexOptions.Compiled);

    private static readonly string[] TestFrameworkAssemblies =
    {
        "xunit.", "nunit.framework", "Microsoft.VisualStudio.TestPlatform.", "Microsoft.TestPlatform.", "MSTest.", "TUnit.",
    };

    /// True when the project's name says it is a test project, or it references a test framework.
    public static bool IsTestProject(string projectName, IEnumerable<string> referencePaths)
    {
        string name = TargetFrameworkSuffix.Replace(projectName.Trim(), "");
        if (TestName.IsMatch(name)) return true;
        return referencePaths
            .Select(path => path.Replace('\\', '/'))
            .Select(path => path[(path.LastIndexOf('/') + 1)..])
            .Any(file => TestFrameworkAssemblies.Any(prefix => file.StartsWith(prefix, StringComparison.OrdinalIgnoreCase)));
    }
}
