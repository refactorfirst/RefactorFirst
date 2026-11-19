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
        Assert.Equal(0, point.ProtectedMembers);
    }

    /// An assignment can reach a property through a tuple, parentheses or `?.`; it is still a write.
    [Theory]
    [InlineData("Tuple", new[] { "Syntax.Order.set_Total(int)" })]
    [InlineData("TupleWithLocal", new[] { "Syntax.Order.set_Total(int)" })]
    [InlineData("Parenthesized", new[] { "Syntax.Order.set_Total(int)" })]
    [InlineData("NullConditional", new[] { "Syntax.Order.set_Total(int)" })]
    [InlineData("NestedTuple", new[] { "Syntax.Order.set_Total(int)" })]
    [InlineData("NullConditionalBump", new[] { "Syntax.Order.get_Total()", "Syntax.Order.set_Total(int)" })]
    [InlineData("NullConditionalRead", new[] { "Syntax.Order.get_Total()" })]
    public async Task Given_APropertyOfAnotherType_When_ReachedThroughATupleParenthesesOrNullConditional_Then_TheMatchingAccessorIsCalled(string method, string[] expected)
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
    [InlineData("Create", new[] { "Syntax.Grid.set_Item(int,int)" })]
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

    /// Files that share a name are told apart by their folder, and a dot in a file name does not end up in the type's name.
    [Fact]
    public async Task Given_FileLocalTypesInFilesWithOneName_When_Extracted_Then_TheFolderTellsThemApart()
    {
        Facts facts = await Extracted.Value;

        Assert.Equal(
            new[] { "Syntax.Tool@A_Helpers", "Syntax.Tool@B_Helpers", "Syntax.Tool@Dotted_Part", "Syntax.Tool@Dotted_Part~2" },
            facts.Types.Where(t => t.Name == "Tool").Select(t => t.Fqn));
        Assert.Equal(new[] { "Syntax.Tool@A_Helpers.RunA()" }, (await Method("UsesA", "Go")).CalledForeignMethods);
        Assert.Equal(new[] { "Syntax.Tool@B_Helpers.RunB()" }, (await Method("UsesB", "Go")).CalledForeignMethods);
    }

    /// `Dotted.Part.cs` and `Dotted_Part.cs` read the same once the dot is replaced; the second one in path order is numbered.
    [Fact]
    public async Task Given_FileLocalTypesInFilesThatDifferOnlyByDotAndUnderscore_When_Extracted_Then_BothAreKept()
    {
        Assert.Equal(new[] { "Syntax.Tool@Dotted_Part~2.RunUnderscore()" }, (await Method("UsesUnderscore", "Go")).CalledForeignMethods);
        Assert.Contains("Syntax.Tool@Dotted_Part.RunDotted()", (await Method("UsesDotted", "Go")).CalledForeignMethods);
    }

    /// `file class Gen` and `file class Gen<T>` in one file are two types.
    [Fact]
    public async Task Given_AFileLocalTypeAndItsGenericNamesake_When_Extracted_Then_BothAreKept()
    {
        Facts facts = await Extracted.Value;

        Assert.Equal(
            new[] { "Syntax.Gen@Dotted_Part", "Syntax.Gen`1@Dotted_Part" },
            facts.Types.Where(t => t.Name == "Gen").Select(t => t.Fqn));
        Assert.Equal(
            new[] { "Syntax.Gen@Dotted_Part.Plain()", "Syntax.Gen`1@Dotted_Part.Generic()", "Syntax.Tool@Dotted_Part.RunDotted()" },
            (await Method("UsesDotted", "Go")).CalledForeignMethods);
    }

    /// An `extension(...) { }` block is not a type of its own; its members belong to the static class around it.
    /// A member is named with its receiver, as a classic `this` extension method is, so two blocks can each have a `Twice`.
    [Fact]
    public async Task Given_ExtensionBlocks_When_Extracted_Then_TheirMembersBelongToTheEnclosingClassWithTheReceiverInTheSignature()
    {
        Facts facts = await Extracted.Value;

        Assert.DoesNotContain(facts.Types, t => t.Name.Length == 0 || t.Fqn.EndsWith('$'));
        TypeFacts ext = await TypeNamed("Ext");
        Assert.Equal(
            new[] { "Size<T>(List<T>)", "Twice(Grid)", "Twice(Order)", "get_Doubled(Order)" },
            ext.Methods.Select(m => m.Signature).OrderBy(s => s, StringComparer.Ordinal));
        Assert.Equal(new[] { "Syntax.Grid", "Syntax.Order" }, ext.Dependencies.Select(d => d.To));
        Assert.Equal(1, ext.Methods.Single(m => m.Signature == "Twice(Order)").Parameters);
    }

    /// However an extension member is called, the call names it the way it names itself.
    [Theory]
    [InlineData("Go", "Syntax.Ext.Twice(Order)")]
    [InlineData("GoStatic", "Syntax.Ext.Twice(Order)")]
    [InlineData("GoGrid", "Syntax.Ext.Twice(Grid)")]
    [InlineData("GoProperty", "Syntax.Ext.get_Doubled(Order)")]
    [InlineData("GoGeneric", "Syntax.Ext.Size<T>(List<T>)")]
    public async Task Given_AnExtensionBlockMember_When_Called_Then_TheCallUsesTheSameSignature(string method, string expected)
    {
        Assert.Equal(new[] { expected }, (await Method("ExtUser", method)).CalledForeignMethods);
    }
}
