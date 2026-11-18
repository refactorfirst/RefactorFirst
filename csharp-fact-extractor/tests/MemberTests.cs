using CSharpFactExtractor;
using Xunit;

public class MemberTests
{
    private static async Task<Facts> ExtractFixture()
    {
        var root = Path.GetDirectoryName(TypeListTests.FixtureSln)!;
        return await Extractor.RunAsync(TypeListTests.FixtureSln, root, excludeTests: true);
    }

    [Fact]
    public async Task Given_Greeter_When_Extracted_Then_AttributesAccessorsAndGreetFacts()
    {
        TypeFacts greeter = (await ExtractFixture()).Types.Single(t => t.Name == "Greeter");

        // A field is an attribute; an auto-property is a private attribute plus accessor methods (Option A).
        Assert.Equal(new[] { "Punctuation", "_store" }, greeter.Attributes.Select(a => a.Name).OrderBy(x => x, StringComparer.Ordinal));
        Assert.All(greeter.Attributes, a => Assert.False(a.IsPublic));
        Assert.Equal(
            new[] { "get_Punctuation()", "set_Punctuation(string)" },
            greeter.Methods.Where(m => m.IsAccessor).Select(m => m.Signature).OrderBy(x => x, StringComparer.Ordinal));
        Assert.Single(greeter.Methods, m => m.IsConstructor);
        // An auto-accessor touches the attribute behind it, as a Java getter touches its field.
        Assert.All(greeter.Methods.Where(m => m.IsAccessor), m => Assert.Equal(new[] { "Punctuation" }, m.AccessedOwnAttributes));

        MethodFacts greet = greeter.Methods.Single(m => m.Name == "Greet");
        Assert.Equal("Greet(string)", greet.Signature);
        Assert.Equal(1, greet.Parameters);
        Assert.Equal(2, greet.Cyclo);
        Assert.Equal(1, greet.MaxNesting);
        Assert.Equal(8, greet.Loc);
        Assert.False(greet.IsAccessor);
        Assert.Equal(new[] { "Punctuation", "_store" }, greet.AccessedOwnAttributes);
        Assert.Equal(new[] { "Punctuation", "_store" }, greet.AccessedVariables);
        Assert.Empty(greet.AccessedForeignAttributes);
        Assert.Empty(greet.AccessedForeignClasses);
        // Like the Java reader, calls to framework types count as foreign calls too.
        Assert.Equal(
            new[] { "System.String.IsNullOrEmpty(string)", "TinyGreeter.MessageStore.Find(string)" },
            greet.CalledForeignMethods);
        Assert.Equal(new[] { "System.String", "TinyGreeter.MessageStore" }, greet.CalledForeignMethodClasses);
    }

    [Fact]
    public async Task Given_MessageStore_When_Extracted_Then_TernaryCountsAsABranch()
    {
        TypeFacts store = (await ExtractFixture()).Types.Single(t => t.Name == "MessageStore");

        MethodFacts find = store.Methods.Single(m => m.Name == "Find");
        Assert.Equal(2, find.Cyclo);
        Assert.Equal(0, find.MaxNesting);
        Assert.Empty(find.AccessedOwnAttributes);

        MethodFacts constructor = store.Methods.Single(m => m.IsConstructor);
        Assert.Equal("MessageStore(Greeter)", constructor.Signature);
        Assert.Equal(new[] { "_owner" }, constructor.AccessedOwnAttributes);
    }

    [Fact]
    public async Task Given_Program_When_Extracted_Then_LocalIsAnAccessedVariableAndGreetIsAForeignCall()
    {
        TypeFacts program = (await ExtractFixture()).Types.Single(t => t.Name == "Program");

        MethodFacts main = program.Methods.Single(m => m.Name == "Main");
        Assert.Equal(new[] { "greeter" }, main.AccessedVariables);
        Assert.Contains("TinyGreeter.Greeter.Greet(string)", main.CalledForeignMethods);
        Assert.Contains("System.Console.WriteLine(string)", main.CalledForeignMethods);
    }
}
