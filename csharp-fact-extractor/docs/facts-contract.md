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
- `fqn` = namespace + "." + name. Nested types: `Outer$Inner`. Generic arity is dropped.
- `dependencies` lists only types declared in the analysed source, never framework or package types, never the type itself.
- `count` = how many names in the type's code resolve to the other type or to one of its members.
- `parent` = fqn of the base class if it is in the analysed source, else null.
- Method `signature` = name + "(" + parameter type names joined by "," + ")".
- `calledForeignMethods` entries are the callee's type fqn + "." + its signature.
- `accessedVariables` = names of fields used (own or foreign), own auto-properties used, and locals declared in the method. Cohesion is computed from it.
- A call to a method of any other type counts as a foreign call, framework types included. Constructor calls do not.
- An auto-property is one private attribute plus `get_X` / `set_X` accessor methods. Using your own auto-property is using that attribute; using another type's property is a call to its accessor.
- Complexity: 1, plus one for each `if`, loop, non-default `case` or switch-expression arm, `catch`, `&&`, `||` and `?:`. Nesting counts `if`, loops and `catch`.
- `protectedMembers` = how many of the type's own fields, properties and methods are protected. `overriddenMethods` = signatures of its methods marked `override`. `usedParentMembers` = names of its base class's members that it uses. "Base class" means one declared in the analysed source. These feed Refused Parent Bequest and Tradition Breaker.
- Types declared only in generated files are left out: anything under `obj` or `bin`, and files ending `.g.cs`, `.g.i.cs`, `.Designer.cs` or `.generated.cs`.
- The extractor takes one solution, or several project files when the repository has no solution at its root.
- Raw facts only. ATFD, TCC and WMC are computed on the Java side.
- A reader must ignore fields it does not know and reject a `schemaVersion` it does not know.
