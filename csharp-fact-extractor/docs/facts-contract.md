# Facts contract, schema version 1

One JSON file per run. Paths are relative to `root`, with forward slashes.

```json
{
  "schemaVersion": 1,
  "root": "/absolute/path/to/repo",
  "namespaces": ["TinyGreeter"],
  "types": [
    {
      "fqn": "TinyGreeter.Greeter",
      "namespace": "TinyGreeter",
      "name": "Greeter",
      "sourceFile": "Greeter.cs",
      "loc": 21,
      "parent": null,
      "attributes": [{ "name": "_store", "isPublic": false }],
      "dependencies": [{ "to": "TinyGreeter.MessageStore", "count": 4 }],
      "methods": [
        {
          "name": "Greet",
          "signature": "Greet(string)",
          "loc": 8,
          "cyclo": 2,
          "maxNesting": 1,
          "parameters": 1,
          "isAccessor": false,
          "isConstructor": false,
          "accessedVariables": ["Punctuation", "_store"],
          "accessedOwnAttributes": ["Punctuation", "_store"],
          "accessedForeignAttributes": [],
          "accessedForeignClasses": [],
          "calledForeignMethods": ["TinyGreeter.MessageStore.Find(string)"],
          "calledForeignMethodClasses": ["TinyGreeter.MessageStore"]
        }
      ],
      "protectedMembers": 0,
      "overriddenMethods": [],
      "usedParentMembers": []
    }
  ]
}
```

The numbers in this example are illustrative. The authoritative example is the
checked-in file `codebase-graph-builder/src/test/resources/csharp/tiny-greeter-facts.json`,
which is the extractor's real output for `fixtures/TinyGreeter`.

Rules
- `fqn` = namespace + "." + name. Nested types: `Outer$Inner`. Generic arity is dropped (`Box<T>` is `Box`), unless a type with the same name is declared beside it in the same project: then the generic one is named the .NET way, so `Result` and `Result<T>` are `Result` and ``Result`1``.
- A `file` type keeps its plain name unless another type with the same name sits beside it; then the file it is in is added after `@`: `Helper@FirstFile` and `Helper@SecondFile`. When the files also share a name, folders are added until they differ: `Helper@A_Helpers` and `Helper@B_Helpers`. Dots and slashes in that part become `_`, so the fqn still ends at its last dot. `name` stays `Helper`.
- An `extension(...) { }` block is not a type: its members, calls and dependencies belong to the static class around it. A member is signed with its receiver first, as the static method it compiles to and as a classic `this` extension method is: `Twice(Order)`, `get_Doubled(Order)`.
- A full name declared in more than one project appears once (the first project wins) and the extractor prints a warning.
- Types outside any namespace are left out, and so are delegates.
- `dependencies` lists only types declared in the analysed source, never framework or package types, never the type itself.
- `count` = how many names in the type's code resolve to the other type or to one of its members.
- `parent` = fqn of the base class if it is in the analysed source, else null.
- Method `signature` = name + type parameters, if any, as `<T,U>` + "(" + parameter type names joined by "," + ")". A parameter passed by `ref`, `out` or `in` keeps that word before its type. So `Save()` and `Save<T>()`, `Add(int)` and `Add(ref int)`, are four different signatures. A static constructor is `static Name()`, so it does not share the instance constructor's `Name()`.
- `calledForeignMethods` entries are the callee's type fqn + "." + its signature.
- `accessedVariables` = names of fields used (own or foreign), own auto-properties used, and locals declared in the method. Cohesion is computed from it.
- A call to a method of any other type counts as a foreign call, framework types included. Constructor calls do not.
- An auto-property is one private attribute plus `get_X` / `set_X` accessor methods. Using your own auto-property is using that attribute; using another type's property is a call to its accessor: the setter when it is assigned, the getter when it is read, both for `+=` and `++`. In an object initializer, `Child = { ... }` is a read: the existing value is filled, not replaced.
- An assignment through parentheses, a tuple or `?.` is still a write: `(a.Total, b.Total) = (1, 2)` and `a?.Total = 5` call the setter.
- An indexer is a property without a name: `grid[0]` calls `get_Item(int)`; `grid[0] = 1` and `new Grid { [0] = 1 }` call `set_Item(int,int)`. A string's indexer is `get_Chars(int)`. An array element is not a call.
- A property that uses the `field` keyword is one private attribute, as an auto-property is; `field` in its accessors is a use of that attribute.
- Nothing inside `nameof(...)` counts as a use or a call in a method's facts. The type named there is still a dependency.
- A record's methods are the ones written for it, plus the accessors of its positional properties. The compiler's `EqualityContract` is left out, and is not a protected member either.
- Complexity: 1, plus one for each `if`, loop, non-default `case` or switch-expression arm, `catch`, `&&`, `||` and `?:`. Nesting counts `if`, loops and `catch`.
- `protectedMembers` = how many of the type's own fields, properties and methods are protected. `overriddenMethods` = signatures of its methods marked `override`. `usedParentMembers` = names of its base class's members that it uses. "Base class" means one declared in the analysed source. These feed Refused Parent Bequest and Tradition Breaker.
- Types declared only in generated files are left out: anything under `obj` or `bin`, and files ending `.g.cs`, `.g.i.cs`, `.Designer.cs` or `.generated.cs`. They are not listed as dependencies either.
- A partial method is measured over both of its halves, so its body counts even when it is in another file.
- The extractor takes one solution, or several project files when the repository has no solution at its root.
- Test projects are left out unless `--include-tests` is given. A test project is one whose name ends in `Test`, `Tests`, `Spec` or `Specs` (a trailing target framework such as `(net8.0)` is ignored), or that references xUnit, NUnit, MSTest or the .NET test platform.
- A primary constructor's `loc` is the length of its parameter list, not of the type that declares it.
- Raw facts only. ATFD, TCC and WMC are computed on the Java side.
- A reader must ignore fields it does not know and reject a `schemaVersion` it does not know.
