package org.hjug.graphbuilder;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class GraphBuilderConfig {

    @Builder.Default
    boolean excludeTests = true;

    @Builder.Default
    String testSourceDirectory = "src/test";

    /**
     * Kotlin language level string (e.g. {@code "KOTLIN_2_2"}). Translated to
     * {@code KotlinParser.KotlinLanguageLevel} inside
     * {@link graphbuilder.KotlinSourceFileGraphBuilder}. Kept as a
     * {@code String} so this config DTO does not carry a compile-time import
     * on {@code rewrite-kotlin}'s enum type.
     */
    @Builder.Default
    String kotlinLanguageLevel = "KOTLIN_2_2";

    /**
     * Git repository root path for URL canonicalization. When set, source file
     * paths are canonicalized relative to this root instead of the repositoryPath
     * (source root). This enables correct GitHub URLs in multi-module projects
     * where the source root is a subdirectory of the Git repo.
     */
    @Builder.Default
    String repositoryRoot = "";

    public static GraphBuilderConfig defaultConfig() {
        return GraphBuilderConfig.builder().build();
    }
}
