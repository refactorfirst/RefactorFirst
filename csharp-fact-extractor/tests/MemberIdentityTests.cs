using CSharpFactExtractor;
using Xunit;

public class MemberIdentityTests
{
    private static string Fixture => Path.GetFullPath(
        Path.Combine(AppContext.BaseDirectory, "..", "..", "..", "..", "fixtures", "Members"));

    private static Task<Facts> Extract() =>
        Extractor.RunAsync(new[] { Path.Combine(Fixture, "Members.csproj") }, Fixture, excludeTests: true);

    private static async Task<TypeFacts> TypeNamed(string name) => (await Extract()).Types.Single(t => t.Name == name);

    /// Save() and Save<T>(), Add(int) and Add(ref int) used to share one signature, and the consumer keeps one method per signature.
    [Fact]
    public async Task Given_OverloadsThatDifferByTypeParameterOrPassingMode_When_Extracted_Then_EachHasItsOwnSignature()
    {
        TypeFacts overloads = await TypeNamed("Overloads");

        Assert.Equal(
            new[] { "Add(int)", "Add(ref int)", "Read(out int)", "Save()", "Save<T>()" },
            overloads.Methods.Select(m => m.Signature).OrderBy(s => s, StringComparer.Ordinal));
    }

    /// A caller must name the overload exactly as the overload names itself.
    [Fact]
    public async Task Given_ACallToAGenericOrRefOverload_When_Extracted_Then_TheCallUsesTheSameSignature()
    {
        MethodFacts go = Assert.Single((await TypeNamed("OverloadCaller")).Methods);

        Assert.Equal(new[] { "Members.Overloads.Add(ref int)", "Members.Overloads.Save<T>()" }, go.CalledForeignMethods);
    }

    [Fact]
    public async Task Given_AStaticAndAnInstanceConstructor_When_Extracted_Then_EachHasItsOwnSignature()
    {
        TypeFacts cache = await TypeNamed("Cache");

        Assert.Equal(
            new[] { "Cache()", "static Cache()" },
            cache.Methods.Where(m => m.IsConstructor).Select(m => m.Signature).OrderBy(s => s, StringComparer.Ordinal));
    }

    [Theory]
    [InlineData("Write", new[] { "Members.Order.set_Total(int)" })]
    [InlineData("Read", new[] { "Members.Order.get_Total()" })]
    [InlineData("Bump", new[] { "Members.Order.get_Total()", "Members.Order.set_Total(int)" })]
    [InlineData("Create", new[] { "Members.Order.set_Total(int)" })]
    public async Task Given_APropertyOfAnotherType_When_ReadOrWritten_Then_TheMatchingAccessorIsCalled(string method, string[] expected)
    {
        TypeFacts clerk = await TypeNamed("Clerk");

        Assert.Equal(expected, clerk.Methods.Single(m => m.Name == method).CalledForeignMethods);
    }

    /// `Child = { Value = 1 }` and `Items = { 1, 2 }` read the property; only `Child = new Node()` replaces it.
    [Theory]
    [InlineData("Nested", new[] { "Members.Holder.get_Child()", "Members.Node.set_Value(int)" })]
    [InlineData("Replaced", new[] { "Members.Holder.set_Child(Node)" })]
    [InlineData("Filled", new[] { "Members.Holder.get_Items()" })]
    public async Task Given_APropertyInAnObjectInitializer_When_NestedOrReplaced_Then_TheMatchingAccessorIsCalled(string method, string[] expected)
    {
        TypeFacts nester = await TypeNamed("Nester");

        Assert.Equal(expected, nester.Methods.Single(m => m.Name == method).CalledForeignMethods);
    }

    /// A partial method's declaration has no body; the body is in its other half.
    [Fact]
    public async Task Given_APartialMethod_When_Extracted_Then_ItsBodyIsMeasured()
    {
        TypeFacts validator = await TypeNamed("Validator");

        MethodFacts check = validator.Methods.Single(m => m.Name == "Check");
        Assert.Equal(3, check.Cyclo);
        Assert.True(check.Loc > 1);
        Assert.Equal(2, validator.Methods.Single(m => m.Name == "Note").Cyclo);
    }

    [Fact]
    public async Task Given_ATypeDeclaredOnlyInGeneratedCode_When_Used_Then_ItIsNotADependency()
    {
        Facts facts = await Extract();

        Assert.DoesNotContain(facts.Types, t => t.Name == "GeneratedHelper");
        Assert.Empty(facts.Types.Single(t => t.Name == "UsesGenerated").Dependencies);
    }
}
