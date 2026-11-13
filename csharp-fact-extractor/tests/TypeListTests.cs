using CSharpFactExtractor;
using Xunit;

public class TypeListTests
{
    public static string FixtureSln =>
        Path.GetFullPath(Path.Combine(AppContext.BaseDirectory, "..", "..", "..", "..", "fixtures", "TinyGreeter", "TinyGreeter.sln"));

    [Fact]
    public async Task Given_TinyGreeter_When_Extracted_Then_ThreeTypesWithFilesAndNamespace()
    {
        var root = Path.GetDirectoryName(FixtureSln)!;

        Facts facts = await Extractor.RunAsync(FixtureSln, root, excludeTests: true);

        Assert.Equal(1, facts.SchemaVersion);
        Assert.Equal(new[] { "TinyGreeter" }, facts.Namespaces);
        Assert.Equal(
            new[] { "TinyGreeter.Greeter", "TinyGreeter.MessageStore", "TinyGreeter.Program" },
            facts.Types.Select(t => t.Fqn).OrderBy(x => x));
        Assert.Equal("Greeter.cs", facts.Types.Single(t => t.Name == "Greeter").SourceFile);
    }
}
