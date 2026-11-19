using CSharpFactExtractor;
using Xunit;

/// Newer or less common C# syntax that the extractor used to read wrongly.
public class SyntaxGapTests
{
    private static string Fixture => Path.GetFullPath(
        Path.Combine(AppContext.BaseDirectory, "..", "..", "..", "..", "fixtures", "Syntax"));

    private static readonly Lazy<Task<Facts>> Extracted = new(() =>
        Extractor.RunAsync(new[] { Path.Combine(Fixture, "Syntax.csproj") }, Fixture, excludeTests: true));

    private static async Task<TypeFacts> TypeNamed(string name) => (await Extracted.Value).Types.Single(t => t.Name == name);

    private static async Task<MethodFacts> Method(string type, string method) =>
        (await TypeNamed(type)).Methods.Single(m => m.Name == method);

    /// The compiler gives every record an EqualityContract property; nobody wrote it, so it is not a method of the record.
    [Fact]
    public async Task Given_ARecord_When_Extracted_Then_OnlyTheMembersItDeclaresAreMethods()
    {
        TypeFacts point = await TypeNamed("Point");

        Assert.Equal(
            new[] { "Point(int,int)", "get_X()", "get_Y()", "set_X(int)", "set_Y(int)" },
            point.Methods.Select(m => m.Signature).OrderBy(s => s, StringComparer.Ordinal));
    }

    /// An assignment can reach a property through a tuple, parentheses or `?.`; it is still a write.
    [Theory]
    [InlineData("Tuple", new[] { "Syntax.Order.set_Total(int)" })]
    [InlineData("TupleWithLocal", new[] { "Syntax.Order.set_Total(int)" })]
    [InlineData("Parenthesized", new[] { "Syntax.Order.set_Total(int)" })]
    [InlineData("NullConditional", new[] { "Syntax.Order.set_Total(int)" })]
    [InlineData("NullConditionalRead", new[] { "Syntax.Order.get_Total()" })]
    public async Task Given_APropertyOfAnotherType_When_WrittenThroughATupleParenthesesOrNullConditional_Then_TheSetterIsCalled(string method, string[] expected)
    {
        Assert.Equal(expected, (await Method("Writer", method)).CalledForeignMethods);
    }

    /// `nameof(a.Total)` is the text "Total"; nothing is read when the program runs.
    [Fact]
    public async Task Given_APropertyInsideNameof_When_Extracted_Then_NoAccessorIsCalled()
    {
        Assert.Empty((await Method("Writer", "NameOf")).CalledForeignMethods);
    }

    /// An indexer is a property without a name: `grid[0] = 1` calls its setter, `grid[0]` its getter.
    [Theory]
    [InlineData("Write", new[] { "Syntax.Grid.set_Item(int,int)" })]
    [InlineData("Read", new[] { "Syntax.Grid.get_Item(int)" })]
    [InlineData("Bump", new[] { "Syntax.Grid.get_Item(int)", "Syntax.Grid.set_Item(int,int)" })]
    [InlineData("ReadIfAny", new[] { "Syntax.Grid.get_Item(int)" })]
    public async Task Given_AnIndexerOfAnotherType_When_ReadOrWritten_Then_TheMatchingAccessorIsCalled(string method, string[] expected)
    {
        Assert.Equal(expected, (await Method("GridUser", method)).CalledForeignMethods);
    }

    /// `field` inside an accessor is the attribute behind the property.
    [Theory]
    [InlineData("get_Value")]
    [InlineData("set_Value")]
    public async Task Given_AnAccessorThatUsesTheFieldKeyword_When_Extracted_Then_ItUsesItsOwnAttribute(string accessor)
    {
        TypeFacts clamped = await TypeNamed("Clamped");

        Assert.Equal(new[] { "Value" }, clamped.Attributes.Select(a => a.Name));
        Assert.Equal(new[] { "Value" }, clamped.Methods.Single(m => m.Name == accessor).AccessedOwnAttributes);
    }

    /// Two `file class Helper` in two files are two types; each caller must point at its own.
    [Fact]
    public async Task Given_TwoFileLocalTypesWithOneName_When_Extracted_Then_BothAreKeptAndToldApartByFile()
    {
        Facts facts = await Extracted.Value;

        Assert.Equal(
            new[] { "Syntax.Helper@FirstFile", "Syntax.Helper@SecondFile" },
            facts.Types.Where(t => t.Name == "Helper").Select(t => t.Fqn));
        Assert.Equal(new[] { "Syntax.Helper@FirstFile.One()" }, (await Method("UsesFirst", "Go")).CalledForeignMethods);
        Assert.Equal(new[] { "Syntax.Helper@SecondFile.Two()" }, (await Method("UsesSecond", "Go")).CalledForeignMethods);
    }

    /// An `extension(...) { }` block is not a type of its own; its members belong to the static class around it.
    [Fact]
    public async Task Given_AnExtensionBlock_When_Extracted_Then_ItsMembersBelongToTheEnclosingClass()
    {
        Facts facts = await Extracted.Value;

        Assert.DoesNotContain(facts.Types, t => t.Fqn.StartsWith("Syntax.Ext$", StringComparison.Ordinal));
        Assert.Equal(new[] { "Twice()" }, (await TypeNamed("Ext")).Methods.Select(m => m.Signature));
        Assert.Equal(new[] { "Syntax.Ext.Twice()" }, (await Method("ExtUser", "Go")).CalledForeignMethods);
        Assert.Equal(new[] { "Syntax.Order" }, (await TypeNamed("Ext")).Dependencies.Select(d => d.To));
    }
}
