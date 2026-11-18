using CSharpFactExtractor;
using Xunit;

public class TestProjectsTests
{
    private static readonly string[] None = Array.Empty<string>();

    [Theory]
    [InlineData("App.Tests")]
    [InlineData("App.Test")]
    [InlineData("AppTests")]
    [InlineData("AppTest")]
    [InlineData("App.UnitTests")]
    [InlineData("App.Specs")]
    [InlineData("App.Tests(net8.0)")]
    public void Given_ATestLikeName_Then_ItIsATestProject(string name) =>
        Assert.True(TestProjects.IsTestProject(name, None));

    [Theory]
    [InlineData("App")]
    [InlineData("App.Core")]
    [InlineData("Contest")]
    [InlineData("App.Testing.Helpers.Api")]
    [InlineData("App(net8.0)")]
    public void Given_AnOrdinaryName_Then_ItIsNotATestProject(string name) =>
        Assert.False(TestProjects.IsTestProject(name, None));

    [Theory]
    [InlineData("/pkgs/xunit.core/2.9.3/lib/netstandard1.1/xunit.core.dll")]
    [InlineData(@"C:\pkgs\nunit\4.0.0\lib\net6.0\nunit.framework.dll")]
    [InlineData("/pkgs/mstest/Microsoft.VisualStudio.TestPlatform.TestFramework.dll")]
    public void Given_AnOrdinaryNameThatReferencesATestFramework_Then_ItIsATestProject(string reference) =>
        Assert.True(TestProjects.IsTestProject("App.Checks", new[] { reference }));

    [Fact]
    public void Given_OrdinaryReferences_Then_ItIsNotATestProject() =>
        Assert.False(TestProjects.IsTestProject("App", new[] { "/pkgs/System.Text.Json.dll", "/pkgs/Serilog.dll" }));
}
