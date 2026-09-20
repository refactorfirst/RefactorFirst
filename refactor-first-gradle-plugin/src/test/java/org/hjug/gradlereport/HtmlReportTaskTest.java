package org.hjug.gradlereport;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.gradle.api.Project;
import org.gradle.testfixtures.ProjectBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class HtmlReportTaskTest {
    private Project project;
    private HtmlReportTask task;

    /** Creates an HTML report task for each test. */
    @BeforeEach
    void setUp() {
        project = ProjectBuilder.builder().build();
        task = project.getTasks()
                .register("refactorFirstHtmlReport", HtmlReportTask.class)
                .get();
    }

    /** Verifies the task supports Gradle build caching. */
    @Test
    void taskIsCacheable() {
        assertTrue(task.getClass().isAnnotationPresent(org.gradle.api.tasks.CacheableTask.class));
    }

    /** Verifies the task exposes its output file property. */
    @Test
    void taskHasOutputFileProperty() {
        assertNotNull(task.getReportFile());
    }

    /** Verifies the task exposes its project adapter property. */
    @Test
    void taskHasProjectAdapterProperty() {
        assertNotNull(task.getProjectAdapter());
    }

    /** Verifies the task exposes its project name property. */
    @Test
    void taskHasProjectNameProperty() {
        assertNotNull(task.getProjectName());
    }

    /** Verifies the task exposes its project version property. */
    @Test
    void taskHasProjectVersionProperty() {
        assertNotNull(task.getProjectVersion());
    }

    /** Verifies the task exposes its cycle back-edge property. */
    @Test
    void taskHasBackEdgeAnalysisCountProperty() {
        assertNotNull(task.getBackEdgeAnalysisCount());
    }

    /** Verifies the task exposes its cycle analysis property. */
    @Test
    void taskHasAnalyzeCyclesProperty() {
        assertNotNull(task.getAnalyzeCycles());
    }

    /** Verifies the task exposes its detail flag property. */
    @Test
    void taskHasShowDetailsProperty() {
        assertNotNull(task.getShowDetails());
    }

    /** Verifies the task exposes its HTML minification property. */
    @Test
    void taskHasMinifyHtmlProperty() {
        assertNotNull(task.getMinifyHtml());
    }

    /** Verifies the task exposes its test-exclusion property. */
    @Test
    void taskHasExcludeTestsProperty() {
        assertNotNull(task.getExcludeTests());
    }

    /** Verifies the task exposes its test source directory property. */
    @Test
    void taskHasTestSourceDirectoryProperty() {
        assertNotNull(task.getTestSourceDirectory());
    }

    /** Verifies the task exposes its output directory property. */
    @Test
    void taskHasOutputDirectoryProperty() {
        assertNotNull(task.getOutputDirectory());
    }
}
