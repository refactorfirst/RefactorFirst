using System.Text.Json;
using CSharpFactExtractor;
using Xunit;

public class ContractTests
{
    /// The Java side's tests read this same file, so this test is what keeps the two programs in step.
    [Fact]
    public async Task Given_TinyGreeter_When_Extracted_Then_OutputMatchesTheCheckedInFactsFile()
    {
        var root = Path.GetDirectoryName(TypeListTests.FixtureSln)!;
        var golden = Path.GetFullPath(Path.Combine(root, "..", "..", "..",
            "codebase-graph-builder", "src", "test", "resources", "csharp", "tiny-greeter-facts.json"));
        var options = new JsonSerializerOptions { PropertyNamingPolicy = JsonNamingPolicy.CamelCase, WriteIndented = true };

        Facts actual = (await Extractor.RunAsync(TypeListTests.FixtureSln, root, excludeTests: true)) with { Root = "." };

        Assert.Equal(
            File.ReadAllText(golden).ReplaceLineEndings("\n").Trim(),
            JsonSerializer.Serialize(actual, options).ReplaceLineEndings("\n").Trim());
    }
}
