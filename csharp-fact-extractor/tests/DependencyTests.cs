using CSharpFactExtractor;
using Xunit;

public class DependencyTests
{
    [Fact]
    public async Task Given_TinyGreeter_When_Extracted_Then_CycleBetweenGreeterAndMessageStore()
    {
        var root = Path.GetDirectoryName(TypeListTests.FixtureSln)!;
        Facts facts = await Extractor.RunAsync(TypeListTests.FixtureSln, root, excludeTests: true);
        TypeFacts greeter = facts.Types.Single(t => t.Name == "Greeter");
        TypeFacts store = facts.Types.Single(t => t.Name == "MessageStore");
        TypeFacts program = facts.Types.Single(t => t.Name == "Program");

        DependencyFacts toStore = Assert.Single(greeter.Dependencies);
        DependencyFacts toGreeter = Assert.Single(store.Dependencies);
        Assert.Equal("TinyGreeter.MessageStore", toStore.To);
        Assert.Equal("TinyGreeter.Greeter", toGreeter.To);
        Assert.True(toStore.Count > toGreeter.Count, "Greeter uses MessageStore more than the reverse");
        Assert.Equal("TinyGreeter.Greeter", Assert.Single(program.Dependencies).To);
        Assert.DoesNotContain(facts.Types.SelectMany(t => t.Dependencies), d => d.To.StartsWith("System"));
    }
}
