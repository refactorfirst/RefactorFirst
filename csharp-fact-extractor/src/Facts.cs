namespace CSharpFactExtractor;

public record Facts(int SchemaVersion, string Root, List<string> Namespaces, List<TypeFacts> Types);

public record TypeFacts(
    string Fqn, string Namespace, string Name, string SourceFile, int Loc, string? Parent,
    List<AttributeFacts> Attributes, List<DependencyFacts> Dependencies, List<MethodFacts> Methods,
    int ProtectedMembers, List<string> OverriddenMethods, List<string> UsedParentMembers);

public record AttributeFacts(string Name, bool IsPublic);

public record DependencyFacts(string To, int Count);

public record MethodFacts(
    string Name, string Signature, int Loc, int Cyclo, int MaxNesting, int Parameters,
    bool IsAccessor, bool IsConstructor,
    List<string> AccessedVariables,
    List<string> AccessedOwnAttributes, List<string> AccessedForeignAttributes, List<string> AccessedForeignClasses,
    List<string> CalledForeignMethods, List<string> CalledForeignMethodClasses);
