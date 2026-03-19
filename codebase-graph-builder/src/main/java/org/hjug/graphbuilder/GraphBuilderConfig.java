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

    public static GraphBuilderConfig defaultConfig() {
        return GraphBuilderConfig.builder().build();
    }
}
