using CSharpFactExtractor;
using Xunit;

public class MultiProjectTests
{
    private static string Fixture => Path.GetFullPath(
        Path.Combine(AppContext.BaseDirectory, "..", "..", "..", "..", "fixtures", "TwoProjects"));

    /// A repository with projects but no solution at its root: every project is analysed, once.
    [Fact]
    public async Task Given_TwoProjectsAndNoSolution_When_BothAreGiven_Then_TypesFromBothAppearOnce()
    {
        var targets = new[]
        {
            Path.Combine(Fixture, "Alpha", "Alpha.csproj"),
            Path.Combine(Fixture, "Beta", "Beta.csproj"),
        };

        Facts facts = await Extractor.RunAsync(targets, Fixture, excludeTests: true);

        Assert.Equal(new[] { "Alpha.AlphaService", "Beta.BaseStore", "Beta.BetaStore" }, facts.Types.Select(t => t.Fqn));
        Assert.Equal(new[] { "Alpha", "Beta" }, facts.Namespaces);
        TypeFacts alpha = facts.Types.Single(t => t.Name == "AlphaService");
        Assert.Equal("Alpha/AlphaService.cs", alpha.SourceFile);
        Assert.Equal("Beta.BetaStore", Assert.Single(alpha.Dependencies).To);
        // Another type's property is a call to its accessor.
        Assert.Contains("Beta.BetaStore.get_Size()", alpha.Methods.Single(m => m.Name == "Count").CalledForeignMethods);
    }

    /// Alpha references Beta, so loading Alpha alone already brings Beta in; naming Beta too must not double it.
    [Fact]
    public async Task Given_OnlyTheReferencingProject_When_Extracted_Then_TheReferencedProjectIsIncluded()
    {
        Facts facts = await Extractor.RunAsync(new[] { Path.Combine(Fixture, "Alpha", "Alpha.csproj") }, Fixture, excludeTests: true);

        Assert.Equal(new[] { "Alpha.AlphaService", "Beta.BaseStore", "Beta.BetaStore" }, facts.Types.Select(t => t.Fqn));
    }
}
