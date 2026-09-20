package org.hjug.gradlereport;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.gradle.api.Project;
import org.gradle.testfixtures.ProjectBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class RefactorFirstExtensionTest {
    private RefactorFirstExtension extension;

    @BeforeEach
    void setUp() {
        Project project = ProjectBuilder.builder().build();
        extension = project.getExtensions().create("refactorFirst", RefactorFirstExtension.class);
    }

    @Test
    void hasDefaultValues() {
        assertEquals(false, extension.getShowDetails().get());
        assertEquals(50, extension.getBackEdgeAnalysisCount().get());
        assertEquals(true, extension.getAnalyzeCycles().get());
        assertEquals(true, extension.getExcludeTests().get());
        assertEquals(false, extension.getMinifyHtml().get());
    }

    @Test
    void validatePassesWithValidConfiguration() {
        extension.getBackEdgeAnalysisCount().set(100);

        assertDoesNotThrow(() -> extension.validate());
    }

    @Test
    void validateThrowsExceptionForNegativeBackEdgeAnalysisCount() {
        extension.getBackEdgeAnalysisCount().set(-1);

        RefactorFirstPluginException exception =
                assertThrows(RefactorFirstPluginException.class, () -> extension.validate());

        assertTrue(exception.getMessage().contains("backEdgeAnalysisCount must be >= 0"));
    }

    @Test
    void validateThrowsExceptionForZeroBackEdgeAnalysisCount() {
        extension.getBackEdgeAnalysisCount().set(0);

        assertDoesNotThrow(() -> extension.validate(), "Zero should be valid");
    }
}
