using System.Text.Json;
using CSharpFactExtractor;

if (args.Length < 1)
{
    Console.Error.WriteLine("usage: CSharpFactExtractor <solution-or-project> [<project> ...] --root <repo-root> --out <facts.json> [--include-tests]");
    return 2;
}
// Everything before the first flag is a solution or project file to analyse.
string[] targets = args.TakeWhile(a => !a.StartsWith("--")).Select(Path.GetFullPath).ToArray();
string root = ArgAfter("--root") ?? Path.GetDirectoryName(targets[0])!;
string outPath = ArgAfter("--out") ?? "facts.json";
bool excludeTests = !args.Contains("--include-tests");

Facts facts = await Extractor.RunAsync(targets, Path.GetFullPath(root), excludeTests);
var json = JsonSerializer.Serialize(facts, new JsonSerializerOptions { PropertyNamingPolicy = JsonNamingPolicy.CamelCase, WriteIndented = true });
await File.WriteAllTextAsync(outPath, json);
Console.WriteLine($"wrote {facts.Types.Count} types to {outPath}");
return 0;

string? ArgAfter(string flag)
{
    int i = Array.IndexOf(args, flag);
    return i >= 0 && i + 1 < args.Length ? args[i + 1] : null;
}
