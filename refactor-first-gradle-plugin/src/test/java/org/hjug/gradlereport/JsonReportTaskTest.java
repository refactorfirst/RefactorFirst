package org.hjug.gradlereport;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.gradle.api.Project;
import org.gradle.testfixtures.ProjectBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class JsonReportTaskTest {
    private Project project;
    private JsonReportTask task;

    /** Creates a JSON report task for each test. */
    @BeforeEach
    void setUp() {
        project = ProjectBuilder.builder().build();
        task = project.getTasks()
                .register("refactorFirstJsonReport", JsonReportTask.class)
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

    /** Verifies the task exposes its output directory property. */
    @Test
    void taskHasOutputDirectoryProperty() {
        assertNotNull(task.getOutputDirectory());
    }
}
