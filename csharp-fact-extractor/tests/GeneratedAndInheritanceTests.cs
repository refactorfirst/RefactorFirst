using CSharpFactExtractor;
using Xunit;

public class GeneratedAndInheritanceTests
{
    private static string Fixture => Path.GetFullPath(
        Path.Combine(AppContext.BaseDirectory, "..", "..", "..", "..", "fixtures", "TwoProjects"));

    private static Task<Facts> Extract() =>
        Extractor.RunAsync(new[] { Path.Combine(Fixture, "Alpha", "Alpha.csproj") }, Fixture, excludeTests: true);

    [Theory]
    [InlineData("/repo/App/obj/Debug/net8.0/Pages_Index_cshtml.g.cs", true)]
    [InlineData("/repo/App/bin/Debug/Thing.cs", true)]
    [InlineData("/repo/App/Models/Thing.g.cs", true)]
    [InlineData("/repo/App/Models/Thing.g.i.cs", true)]
    [InlineData("/repo/App/Form1.Designer.cs", true)]
    [InlineData("/repo/App/Api.generated.cs", true)]
    [InlineData("/repo/App/Models/Thing.cs", false)]
    [InlineData("/repo/App/Objects/Binder.cs", false)]
    public void Given_APath_When_Classified_Then_GeneratedCodeIsRecognised(string path, bool expected)
    {
        Assert.Equal(expected, TypeNames.IsGeneratedPath(path));
    }

    [Fact]
    public async Task Given_AGeneratedFile_When_Extracted_Then_ItsTypesAreLeftOut()
    {
        Facts facts = await Extract();

        Assert.DoesNotContain(facts.Types, t => t.Name == "GeneratedThing");
        Assert.Equal(new[] { "Alpha.AlphaService", "Beta.BaseStore", "Beta.BetaStore" }, facts.Types.Select(t => t.Fqn));
    }

    [Fact]
    public async Task Given_ASubclass_When_Extracted_Then_OverridesAndUsedParentMembersAreReported()
    {
        Facts facts = await Extract();
        TypeFacts parent = facts.Types.Single(t => t.Name == "BaseStore");
        TypeFacts child = facts.Types.Single(t => t.Name == "BetaStore");

        // Capacity, Describe and Reset are protected; Limit is public.
        Assert.Equal(3, parent.ProtectedMembers);
        Assert.Empty(parent.OverriddenMethods);

        Assert.Equal("Beta.BaseStore", child.Parent);
        Assert.Equal(new[] { "Limit()" }, child.OverriddenMethods);
        Assert.Equal(new[] { "Capacity", "Reset" }, child.UsedParentMembers);
        Assert.Equal(0, child.ProtectedMembers);
    }
}
