package org.hjug.graphbuilder.graphbuilder;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;

/**
 * The facts contract written by csharp-fact-extractor: raw facts about a C# codebase,
 * from which the usual collectors compute the same metrics they compute for Java.
 * See csharp-fact-extractor/docs/facts-contract.md.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record CSharpFacts(int schemaVersion, String root, List<String> namespaces, List<TypeFacts> types) {

    static final int SUPPORTED_SCHEMA_VERSION = 1;

    public CSharpFacts {
        namespaces = CSharpFactLists.orEmpty(namespaces);
        types = CSharpFactLists.orEmpty(types);
    }

    static CSharpFacts read(InputStream in) throws IOException {
        CSharpFacts facts = new ObjectMapper().readValue(in, CSharpFacts.class);
        if (facts.schemaVersion() != SUPPORTED_SCHEMA_VERSION) {
            throw new IOException("Unsupported C# facts schema version " + facts.schemaVersion() + " (expected "
                    + SUPPORTED_SCHEMA_VERSION + ")");
        }
        return facts;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record TypeFacts(
            String fqn,
            String namespace,
            String name,
            String sourceFile,
            int loc,
            String parent,
            List<AttributeFacts> attributes,
            List<DependencyFacts> dependencies,
            List<MethodFacts> methods,
            int protectedMembers,
            List<String> overriddenMethods,
            List<String> usedParentMembers) {

        public TypeFacts {
            attributes = CSharpFactLists.orEmpty(attributes);
            dependencies = CSharpFactLists.orEmpty(dependencies);
            methods = CSharpFactLists.orEmpty(methods);
            overriddenMethods = CSharpFactLists.orEmpty(overriddenMethods);
            usedParentMembers = CSharpFactLists.orEmpty(usedParentMembers);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record AttributeFacts(String name, @JsonProperty("isPublic") boolean isPublic) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record DependencyFacts(String to, int count) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record MethodFacts(
            String name,
            String signature,
            int loc,
            int cyclo,
            int maxNesting,
            int parameters,
            @JsonProperty("isAccessor") boolean isAccessor,
            @JsonProperty("isConstructor") boolean isConstructor,
            List<String> accessedVariables,
            List<String> accessedOwnAttributes,
            List<String> accessedForeignAttributes,
            List<String> accessedForeignClasses,
            List<String> calledForeignMethods,
            List<String> calledForeignMethodClasses) {

        public MethodFacts {
            accessedVariables = CSharpFactLists.orEmpty(accessedVariables);
            accessedOwnAttributes = CSharpFactLists.orEmpty(accessedOwnAttributes);
            accessedForeignAttributes = CSharpFactLists.orEmpty(accessedForeignAttributes);
            accessedForeignClasses = CSharpFactLists.orEmpty(accessedForeignClasses);
            calledForeignMethods = CSharpFactLists.orEmpty(calledForeignMethods);
            calledForeignMethodClasses = CSharpFactLists.orEmpty(calledForeignMethodClasses);
        }
    }
}
