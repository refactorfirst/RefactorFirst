package org.hjug.gradlereport;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.internal.TaskInternal;
import org.gradle.api.internal.TaskOutputsInternal;
import org.gradle.testfixtures.ProjectBuilder;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link RefactorFirstPlugin} task registration, using {@link ProjectBuilder}.
 * These tests pin the plugin contract: plugin id, task names, and the always-run behavior.
 */
class RefactorFirstPluginTest {

    private static final String[] CONTRACT_TASK_NAMES = {
        "refactorFirstHtmlReport", "refactorFirstSimpleHtmlReport", "refactorFirstCsvReport", "refactorFirstJsonReport"
    };

    private Project newProjectWithPlugin() {
        Project project = ProjectBuilder.builder().build();
        project.getPluginManager().apply("org.hjug.refactorfirst");
        return project;
    }

    /** T1: the plugin is applied by its contract id {@code org.hjug.refactorfirst}. */
    @Test
    void appliesByContractId() {
        Project project = newProjectWithPlugin();
        assertTrue(project.getPluginManager().hasPlugin("org.hjug.refactorfirst"));
    }

    /** T2: all four contract task names are registered with group and description. */
    @Test
    void registersAllFourTasksWithContractNames() {
        Project project = newProjectWithPlugin();
        for (String taskName : CONTRACT_TASK_NAMES) {
            Task task = project.getTasks().getByName(taskName);
            assertNotNull(task, "Expected task " + taskName + " to be registered");
            assertEquals("RefactorFirst", task.getGroup(), "Unexpected group for task " + taskName);
            assertTrue(
                    task.getDescription() != null && !task.getDescription().isBlank(),
                    "Expected a non-blank description for task " + taskName);
        }
    }

    /** T11: the extension mirrors the Maven plugin defaults. */
    @Test
    void extensionDefaultsMatchContract() {
        Project project = newProjectWithPlugin();
        RefactorFirstExtension extension = project.getExtensions().getByType(RefactorFirstExtension.class);
        assertNotNull(extension, "Expected the refactorFirst extension to be created");
        assertFalse(extension.isShowDetails());
        assertEquals(50, extension.getBackEdgeAnalysisCount());
        assertTrue(extension.isAnalyzeCycles());
        assertFalse(extension.isMinifyHtml());
        assertTrue(extension.isExcludeTests());
        assertTrue(isBlank(extension.getTestSourceDirectory()));
        assertTrue(isBlank(extension.getProjectName()));
        assertTrue(isBlank(extension.getProjectVersion()));
        assertTrue(extension.getOutputDirectory() == null, "No outputDirectory override by default");
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    /**
     * T3: tasks always run when invoked — never UP-TO-DATE, never FROM-CACHE.
     * No declared inputs/outputs and an explicit never-up-to-date guard.
     */
    @Test
    void tasksAreNeverUpToDate() {
        Project project = newProjectWithPlugin();
        for (String taskName : CONTRACT_TASK_NAMES) {
            Task task = project.getTasks().getByName(taskName);
            // Gradle exposes the composite up-to-date spec only via the internal API.
            TaskOutputsInternal outputs = (TaskOutputsInternal) task.getOutputs();
            assertFalse(
                    outputs.getUpToDateSpec().isSatisfiedBy((TaskInternal) task),
                    "Task " + taskName + " must never be considered up-to-date");
            assertFalse(
                    task.getInputs().getHasInputs(), "Task " + taskName + " must not declare inputs (it always runs)");
        }
    }
}
