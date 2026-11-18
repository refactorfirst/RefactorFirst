using CSharpFactExtractor;
using Xunit;

public class ShapesTests
{
    private static string Fixture => Path.GetFullPath(
        Path.Combine(AppContext.BaseDirectory, "..", "..", "..", "..", "fixtures", "Shapes"));

    private static Task<Facts> Extract() =>
        Extractor.RunAsync(new[] { Path.Combine(Fixture, "Shapes.csproj") }, Fixture, excludeTests: true);

    /// Result and Result<T> used to share one name, and one silently replaced the other.
    [Fact]
    public async Task Given_APlainAndAGenericTypeWithOneName_When_Extracted_Then_BothAreKept()
    {
        Facts facts = await Extract();

        Assert.Equal(
            new[] { "Shapes.Box", "Shapes.Counter", "Shapes.Result", "Shapes.Result`1" },
            facts.Types.Select(t => t.Fqn));
        Assert.Contains(facts.Types.Single(t => t.Fqn == "Shapes.Result").Methods, m => m.Name == "Describe");
        Assert.Contains(facts.Types.Single(t => t.Fqn == "Shapes.Result`1").Methods, m => m.Name == "HasValue");
    }

    /// Whoever uses the generic one must point at the same name it was given.
    [Fact]
    public async Task Given_AUserOfTheGenericNamesake_When_Extracted_Then_ItsDependencyUsesTheSameName()
    {
        TypeFacts counter = (await Extract()).Types.Single(t => t.Name == "Counter");

        Assert.Equal(new[] { "Shapes.Result`1" }, counter.Dependencies.Select(d => d.To));
    }

    [Fact]
    public async Task Given_APrimaryConstructor_When_Extracted_Then_ItsLengthIsNotTheWholeClass()
    {
        TypeFacts counter = (await Extract()).Types.Single(t => t.Name == "Counter");

        MethodFacts constructor = Assert.Single(counter.Methods, m => m.IsConstructor);
        Assert.Equal(1, constructor.Loc);
        Assert.True(counter.Loc > 10);
    }
}
