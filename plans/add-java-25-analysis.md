# Plan: Add Java 25 Analysis Support to RefactorFirst

## Executive Summary

This plan outlines the approach to add support for analyzing Java 25 codebases to RefactorFirst while maintaining backward compatibility with Java 17 runtime requirements for the Maven and Gradle plugins. The solution uses conditional class loading to detect Java 25 runtime and dynamically load the `org.openrewrite:rewrite-java-25` dependency only when available.

## Background

### Current State
- RefactorFirst currently supports Java 11, 17, and 21 analysis via OpenRewrite parsers
- The project is compiled for Java 17 (parent pom.xml: `maven.compiler.source/target = 17`)
- Maven and Gradle plugins require Java 17 runtime
- OpenRewrite provides `rewrite-java-25` artifact for Java 25 source parsing

### The Challenge
- `rewrite-java-25` is compiled with Java 25 class file version (69.0)
- It will fail to load on Java 17/21 runtimes with `UnsupportedClassVersionError`
- OpenRewrite's own `JavaParser.fromJavaVersion()` logic checks runtime version, not source version
- Per OpenRewrite issue #6686, `rewrite-java-25` only works on Java 25+ runtime

### Key Constraint
- Maven and Gradle plugins must continue to require only Java 17 runtime
- Users with Java 25 codebases should get enhanced analysis when running on Java 25 runtime
- Users on Java 17 runtime should continue to work with Java 17/21 codebases

## Technical Approach

Use test-driven development (TDD) approach to implement the solution.

### 1. Dependency Management
b
#### 1.1 Add Optional Java 25 Dependency
Add `rewrite-java-25` as an optional dependency in `../codebase-graph-builder/pom.xml`:

```xml
<dependency>
    <groupId>org.openrewrite</groupId>
    <artifactId>rewrite-java-25</artifactId>
    <version>${rewrite.version}</version>
    <optional>true</optional>
</dependency>
```

**Rationale:** Marking it as `optional` prevents it from being transitively included in the Maven/Gradle plugins, keeping their Java 17 runtime requirement.

#### 1.2 Update Parent POM Dependency Management
Add the version to parent `../pom.xml` `<dependencyManagement>` section aligned with the current OpenRewrite BOM version (8.90.4):

```xml
<dependency>
    <groupId>org.openrewrite</groupId>
    <artifactId>rewrite-java-25</artifactId>
    <version>8.90.4</version>
</dependency>
```

### 2. Runtime Detection Mechanism

#### 2.1 Create Java Version Detection Utility
Create `org.hjug.graphbuilder.JavaRuntimeDetector` utility class:

```java
package org.hjug.graphbuilder;

public class JavaRuntimeDetector {
    
    private static final int JAVA_25_VERSION = 25;
    
    /**
     * Detects if the current JVM runtime is Java 25 or higher.
     * Uses java.specification.version system property which returns
     * the feature version (e.g., "25" for Java 25).
     */
    public static boolean isJava25OrHigher() {
        String javaVersion = System.getProperty("java.specification.version");
        if (javaVersion == null) {
            return false;
        }
        
        try {
            int version = Integer.parseInt(javaVersion);
            return version >= JAVA_25_VERSION;
        } catch (NumberFormatException e) {
            // Fallback to Runtime.version() for edge cases
            return Runtime.version().feature() >= JAVA_25_VERSION;
        }
    }
    
    /**
     * Gets the current Java runtime feature version.
     */
    public static int getRuntimeVersion() {
        String javaVersion = System.getProperty("java.specification.version");
        if (javaVersion != null) {
            try {
                return Integer.parseInt(javaVersion);
            } catch (NumberFormatException e) {
                // Fall through to Runtime.version()
            }
        }
        return Runtime.version().feature();
    }
}
```

**Rationale:** Uses `java.specification.version` which is stable and documented as returning the feature version per Java 25 system properties documentation.

### 3. Conditional Parser Loading

#### 3.1 Create Java 25 Parser Wrapper
Create `org.hjug.graphbuilder.graphbuilder.Java25ParserWrapper` to handle conditional loading:

```java
package org.hjug.graphbuilder.graphbuilder;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import lombok.extern.slf4j.Slf4j;
import org.openrewrite.ExecutionContext;
import org.openrewrite.java.JavaParser;

/**
 * Wrapper for conditionally loading Java 25 parser.
 * Uses reflection to avoid compile-time dependency on rewrite-java-25.
 */
@Slf4j
public class Java25ParserWrapper {
    
    private static final String JAVA25_PARSER_CLASS = "org.openrewrite.java.Java25Parser";
    private static final String JAVA_PARSER_BUILDER_CLASS = "org.openrewrite.java.JavaParser$Builder";
    
    /**
     * Attempts to create a Java 25 parser using reflection.
     * Returns null if rewrite-java-25 is not available or loading fails.
     */
    public static JavaParser tryCreateJava25Parser(ExecutionContext ctx) {
        if (!JavaRuntimeDetector.isJava25OrHigher()) {
            log.debug("Java 25 runtime not detected, skipping Java 25 parser");
            return null;
        }
        
        try {
            // Load Java25Parser class
            Class<?> java25ParserClass = Class.forName(JAVA25_PARSER_CLASS);
            
            // Get the builder method
            Method builderMethod = java25ParserClass.getMethod("builder");
            Object builder = builderMethod.invoke(null);
            
            // Build the parser
            Method buildMethod = builder.getClass().getMethod("build");
            return (JavaParser) buildMethod.invoke(builder);
            
        } catch (ClassNotFoundException e) {
            log.debug("rewrite-java-25 not available on classpath, falling back to standard parser");
            return null;
        } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException e) {
            log.warn("Failed to create Java 25 parser via reflection", e);
            return null;
        }
    }
}
```

**Rationale:** 
- Reflection avoids compile-time dependency on `rewrite-java-25`
- Graceful fallback to standard parser when Java 25 not available
- Only attempts loading when Java 25 runtime is detected

#### 3.2 Update JavaSourceFileGraphBuilder
Modify `JavaSourceFileGraphBuilder.buildGraph()` to conditionally use Java 25 parser:

```java
@Override
public CodebaseGraphDTO buildGraph(String repositoryPath, String repositoryRoot, GraphBuilderConfig config)
        throws IOException {
    File srcDirectory = new File(repositoryPath);

    // Try Java 25 parser first if runtime supports it
    JavaParser javaParser = Java25ParserWrapper.tryCreateJava25Parser(
            new InMemoryExecutionContext(e -> log.warn("OpenRewrite parse/visit error", e)));
    
    // Fall back to standard parser if Java 25 not available
    if (javaParser == null) {
        javaParser = JavaParser.fromJavaVersion().build();
        log.debug("Using standard Java parser (version: {})", JavaRuntimeDetector.getRuntimeVersion());
    } else {
        log.info("Using Java 25 parser for enhanced Java 25 feature support");
    }
    
    ExecutionContext ctx = new InMemoryExecutionContext(e -> log.warn("OpenRewrite parse/visit error", e));
    
    // ... rest of existing implementation unchanged
}
```

**Rationale:** 
- Attempts Java 25 parser first when runtime supports it
- Graceful fallback maintains backward compatibility
- Logging provides visibility into which parser is being used

### 4. Configuration Updates

#### 4.1 Update GraphBuilderConfig
Add optional Java 25 configuration field to `GraphBuilderConfig`:

```java
@Value
@Builder
public class GraphBuilderConfig {

    @Builder.Default
    boolean excludeTests = true;

    @Builder.Default
    String testSourceDirectory = "src/test";

    @Builder.Default
    String kotlinLanguageLevel = "KOTLIN_2_4";

    @Builder.Default
    String repositoryRoot = "";

    /**
     * Whether to force use of Java 25 parser even when runtime detection fails.
     * For advanced use cases where user knows Java 25 parser is available.
     * Default: false (rely on runtime detection).
     */
    @Builder.Default
    boolean forceJava25Parser = false;

    public static GraphBuilderConfig defaultConfig() {
        return GraphBuilderConfig.builder().build();
    }
}
```

**Rationale:** Provides escape hatch for edge cases where runtime detection might fail but parser is available.

#### 4.2 Update Maven Plugin Configuration
Add configuration parameter to `RefactorFirstMavenReport`:

```java
@Parameter(property = "forceJava25Parser")
private boolean forceJava25Parser = false;
```

Pass this through to the graph builder configuration.

### 5. Testing Strategy

#### 5.1 Unit Tests
Create `JavaRuntimeDetectorTest`:
- Test detection on various runtime versions (mocked system properties)
- Test edge cases (null values, malformed version strings)

Create `Java25ParserWrapperTest`:
- Test successful parser creation when Java 25 available
- Test graceful fallback when not available
- Test reflection failure scenarios

#### 5.2 Integration Tests
Create `Java25AnalysisIntegrationTest`:
- Test with actual Java 25 source code (requires Java 25 runtime in CI)
- Verify Java 25 features are properly parsed
- Test fallback behavior on Java 17 runtime

#### 5.3 Test Resources
Add Java 25 test fixtures to `test-resources/src/test/resources/`:
- Simple Java 25 class using new features
- Class with Java 25-specific syntax

#### 5.4 CI Configuration
Update GitHub Actions workflows:
- Add Java 25 runtime matrix for testing
- Ensure Java 17 tests still pass without Java 25 dependency

### 6. Documentation Updates

#### 6.1 Update CLAUDE.md
Add section on Java 25 support:
- Explain runtime detection mechanism
- Document optional dependency nature
- Note that Maven/Gradle plugins still require Java 17

#### 6.2 Update AGENTS.md
Add Java 25 to key architecture points:
- Document the conditional loading approach
- Explain the reflection-based wrapper

#### 6.3 Update README.md
Add Java 25 support to features list with runtime requirements note.

### 7. Migration Path

#### 7.1 Backward Compatibility
- No breaking changes for existing users
- Java 17 runtime users continue to work as before
- Java 25 users get enhanced analysis automatically

#### 7.2 Rollout Strategy
1. Implement changes in `codebase-graph-builder` module
2. Add optional dependency to parent POM
3. Update tests to cover both scenarios
4. Release as minor version bump (0.11.0 → 0.12.0)
5. Monitor for issues with Java 25 detection

### 8. Potential Issues and Mitigations

#### 8.1 Class Loading Issues
**Risk:** Reflection might fail in certain classloading environments (e.g., OSGi, custom classloaders)

**Mitigation:** 
- Add comprehensive error handling in wrapper
- Provide configuration option to disable Java 25 parser
- Log detailed failure information for debugging

#### 8.2 Version Detection Edge Cases
**Risk:** `java.specification.version` might not be reliable on all JVMs

**Mitigation:**
- Use `Runtime.version().feature()` as fallback
- Add unit tests for various version string formats
- Provide manual override via configuration

#### 8.3 Dependency Conflicts
**Risk:** Users might have conflicting OpenRewrite versions

**Mitigation:**
- Use parent POM dependency management to ensure consistent versions
- Document that Java 25 support requires OpenRewrite 8.90.4+
- Add validation in wrapper to check version compatibility

#### 8.4 Performance Impact
**Risk:** Reflection overhead on every parser creation

**Mitigation:**
- Cache reflection results (Class objects, Method objects)
- Only perform reflection once at startup
- Benchmark to ensure minimal impact

### 9. Implementation Order

1. **Phase 1: Infrastructure**
   - Create `JavaRuntimeDetector` utility
   - Add unit tests for runtime detection
   - Update parent POM with `rewrite-java-25` dependency management

2. **Phase 2: Parser Loading**
   - Create `Java25ParserWrapper` with reflection
   - Add unit tests for wrapper
   - Update `JavaSourceFileGraphBuilder` to use conditional loading

3. **Phase 3: Configuration**
   - Update `GraphBuilderConfig` with force option
   - Update Maven plugin configuration
   - Pass configuration through pipeline

4. **Phase 4: Testing**
   - Add Java 25 test fixtures
   - Create integration tests
   - Update CI workflows for Java 25 testing

5. **Phase 5: Documentation**
   - Update CLAUDE.md
   - Update AGENTS.md
   - Update README.md
   - Add release notes

### 10. Success Criteria

- [x] Java 25 codebases can be analyzed when running on Java 25 runtime
- [x] Java 17 runtime users continue to work without errors
- [x] Maven and Gradle plugins still require only Java 17 runtime
- [x] `rewrite-java-25` is not transitively included in plugin distributions
- [x] Unit tests cover both Java 17 and Java 25 scenarios
- [x] Integration tests verify Java 25 feature parsing
- [x] Documentation clearly explains runtime requirements
- [x] No performance regression on Java 17 runtime

## Alternatives Considered

### Alternative 1: Separate Java 25 Module
**Approach:** Create a separate `codebase-graph-builder-java25` module that requires Java 25 compilation.

**Pros:**
- Clean separation of concerns
- No reflection needed
- Type-safe

**Cons:**
- Increases module complexity
- Makes plugin distribution more complex
- Would require separate Maven/Gradle plugin variants
- Violates requirement that plugins only need Java 17

**Decision:** Rejected in favor of conditional loading approach.

### Alternative 2: Multi-Release JAR
**Approach:** Use Java 9+ multi-release JAR feature to include Java 25-specific classes.

**Pros:**
- Standard Java mechanism for version-specific code
- No reflection needed

**Cons:**
- Requires Java 9+ base (already satisfied)
- More complex build configuration
- Still requires careful dependency management
- Multi-release JARs can be confusing for users

**Decision:** Rejected due to complexity and the need for optional dependency management.

### Alternative 3: Always Include Java 25 Dependency
**Approach:** Always include `rewrite-java-25` and let it fail gracefully on Java 17.

**Pros:**
- Simplest implementation
- No conditional logic needed

**Cons:**
- Violates plugin Java 17 requirement
- Would cause class loading errors on Java 17
- Increases distribution size unnecessarily
- OpenRewrite issue #6686 shows this breaks on Java 21

**Decision:** Rejected due to incompatible with plugin requirements.

## Conclusion

This plan provides a pragmatic approach to adding Java 25 support while maintaining backward compatibility. The conditional loading strategy using reflection allows RefactorFirst to leverage Java 25 parsing capabilities when available without requiring Java 25 runtime for the Maven and Gradle plugins themselves. The optional dependency ensures that `rewrite-java-25` is only included when explicitly needed, keeping plugin distributions lean and compatible with Java 17 runtimes.