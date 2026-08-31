package org.hjug.refactorfirst.report;

import static org.junit.jupiter.api.Assertions.*;

import java.io.File;
import java.nio.file.Files;
import java.util.*;
import org.hjug.cbc.RankedDisharmony;
import org.hjug.graphbuilder.CodebaseGraphDTO;
import org.jgrapht.Graph;
import org.jgrapht.graph.DefaultDirectedWeightedGraph;
import org.jgrapht.graph.DefaultWeightedEdge;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class SimpleHtmlReportTest {

    /**
     * Test that generateReport does NOT return early when projectBaseDir != parentOfGitDir.
     * This is the multi-module case where the module directory (e.g., fxgl-core) differs from
     * the Git repo root (e.g., FXGL). The report should still generate by passing both paths
     * to CycleRanker for correct URL canonicalization.
     */
    @Test
    void generateReport_multiModuleProject_doesNotReturnEarly() throws Exception {
        // Create a test fixture with a parent repo and a submodule
        File tempDir = File.createTempFile("multiModuleTest", "");
        tempDir.delete();
        tempDir.mkdirs();
        File gitDir = new File(tempDir, ".git");
        gitDir.mkdirs();
        File submoduleDir = new File(tempDir, "submodule");
        submoduleDir.mkdirs();

        // Create a minimal Kotlin source file in the submodule
        File srcDir = new File(submoduleDir, "src/main/kotlin/com/example");
        srcDir.mkdirs();
        File kotlinFile = new File(srcDir, "TestClass.kt");
        Files.writeString(
                kotlinFile.toPath(),
                """
            package com.example

            class TestClass {
                fun doSomething() = "test"
            }
            """);

        // Initialize a git repo in the parent
        ProcessBuilder pb = new ProcessBuilder("git", "init");
        pb.directory(tempDir);
        pb.start().waitFor();
        pb = new ProcessBuilder("git", "config", "user.email", "test@test.com");
        pb.directory(tempDir);
        pb.start().waitFor();
        pb = new ProcessBuilder("git", "config", "user.name", "Test");
        pb.directory(tempDir);
        pb.start().waitFor();
        pb = new ProcessBuilder("git", "add", ".");
        pb.directory(tempDir);
        pb.start().waitFor();
        pb = new ProcessBuilder("git", "commit", "-m", "initial");
        pb.directory(tempDir);
        pb.start().waitFor();

        SimpleHtmlReport htmlReport = new SimpleHtmlReport();

        // Use reflection to call generateReport with projectBaseDir = submodule, parentOfGitDir = parent
        java.lang.reflect.Method method = SimpleHtmlReport.class.getDeclaredMethod(
                "generateReport",
                boolean.class,
                int.class,
                boolean.class,
                boolean.class,
                String.class,
                String.class,
                String.class,
                File.class);
        method.setAccessible(true);

        StringBuilder result = (StringBuilder)
                method.invoke(htmlReport, false, 50, true, false, "src/test", "TestProject", "1.0", submoduleDir);

        // The report should NOT contain the early-return warning message
        String resultStr = result.toString();
        assertFalse(
                resultStr.contains("Project Base Directory does not match Git Parent Directory"),
                "Should not return early for multi-module projects");

        // Should NOT contain the 'no git repo' message either
        assertFalse(resultStr.contains("No Git repository found"), "Should find git repo in parent directory");

        // Should contain some report content (not just early return)
        assertTrue(resultStr.length() > 200, "Should generate substantial report content for multi-module project");

        // Cleanup
        deleteDir(tempDir);
    }

    private void deleteDir(File dir) {
        for (File file : dir.listFiles()) {
            if (file.isDirectory()) {
                deleteDir(file);
            } else {
                file.delete();
            }
        }
        dir.delete();
    }

    @Test
    void isDateTime() {
        HtmlReport htmlReport = new HtmlReport();
        String commitDateTime = "7/22/23, 5:00 AM";
        assertTrue(htmlReport.isDateTime(commitDateTime));
    }

    @Test
    void testSimpleMethodSignature() {
        HtmlReport htmlReport = new HtmlReport();
        String sig = "foo(java.lang.String, java.lang.String)";
        assertEquals("foo(String,String)", htmlReport.getSimpleMethodSignature(sig));
    }

    @Test
    void testSimpleMethodSignatureWithGenerics() {
        HtmlReport htmlReport = new HtmlReport();
        String sig = "foo(java.util.List<java.lang.String>, java.util.List<java.lang.String>)";
        assertEquals("foo(List<String>,List<String>)", htmlReport.getSimpleMethodSignature(sig));
    }

    @Test
    void testSimpleMethodSignatureWithGenericsAndWildcard() {
        HtmlReport htmlReport = new HtmlReport();
        String sig = "foo(java.util.List<? extends java.lang.String>, java.util.List<? super java.lang.String>)";
        assertEquals("foo(List<? extends String>,List<? super String>)", htmlReport.getSimpleMethodSignature(sig));
    }

    @Test
    void testSimpleMethodSignatureWithGenericsAndWildcardAndBounds() {
        HtmlReport htmlReport = new HtmlReport();
        String sig =
                "foo(java.util.List<? extends java.lang.String, java.lang.String>, java.util.List<? super java.lang.String, java.lang.String>)";
        assertEquals(
                "foo(List<? extends String,String>,List<? super String,String>)",
                htmlReport.getSimpleMethodSignature(sig));
    }

    @Test
    void testSimpleMethodSignatureWithClassTypeParameter() {
        HtmlReport htmlReport = new HtmlReport();
        String sig = "isAllSuitableNodesOffline(Generic{R extends hudson.model.AbstractBuild}, Generic{R}>})";
        assertEquals("isAllSuitableNodesOffline(R)", htmlReport.getSimpleMethodSignature(sig));
    }

    @Test
    void testSimpleMethodSignatureWithMethodTypeParameter() {
        HtmlReport htmlReport = new HtmlReport();
        String sig = "copy(Generic{T extends hudson.model.TopLevelItem},java.lang.String)";
        assertEquals("copy(T,String)", htmlReport.getSimpleMethodSignature(sig));
    }

    @Test
    void testSimplifyDuplicatePartners() {
        HtmlReport htmlReport = new HtmlReport();
        String duplicationPartners =
                "upWaitQueue(com.tonikelope.megabasterd.Transference) \u2194 TransferenceManager.downWaitQueue(com.tonikelope.megabasterd.Transference)";
        assertEquals(
                "upWaitQueue(Transference) \u2194 TransferenceManager.downWaitQueue(Transference)",
                htmlReport.simplifyDuplicatePartners(duplicationPartners));
    }

    @Test
    void testSimplifyDuplicatePartnersWithDollarSign() {
        HtmlReport htmlReport = new HtmlReport();
        String duplicationPartners = "method(com.example.Outer$Inner) \u2194 Other.method(com.example.Outer$Inner)";
        assertEquals(
                "method(Outer$Inner) \u2194 Other.method(Outer$Inner)",
                htmlReport.simplifyDuplicatePartners(duplicationPartners));
    }

    @Test
    void getClassName_preservesDollarForJavaAnonymous() {
        HtmlReport htmlReport = new HtmlReport();
        assertEquals("Outer$1", htmlReport.getClassName("com.example.Outer$1"));
        assertEquals("<anonymous>", htmlReport.getClassName("<anonymous>"));
    }

    @Test
    void hyperlinkClass_missingPath_rendersPlainTextNoAnchor() {
        HtmlReport htmlReport = new HtmlReport();
        CodebaseGraphDTO dto = Mockito.mock(CodebaseGraphDTO.class);
        HashMap<String, String> map = new HashMap<>();
        map.put("com.example.Known", "/src/main/java/com/example/Known.java");
        Mockito.when(dto.getClassToSourceFilePathMapping()).thenReturn(map);

        String repoUrl = "https://github.com/example/repo/blob/";
        String result = htmlReport.hyperlinkClass("com.example.Unknown", repoUrl, dto);

        assertFalse(result.contains("<a href"), "missing-path vertex must not render <a href>");
        assertFalse(result.contains("null"), "result must not contain the literal 'null'");
        assertEquals("Unknown", result);
    }

    /**
     * Tests that getPackageRelationshipDisharmony handles the case where
     * classRelationshipsInPackageRelationship returns null for a package edge
     * (i.e., no class-level relationships map to that package relationship).
     * This prevents NPE when iterating over a null set.
     */
    @Test
    void getPackageRelationshipDisharmony_nullClassRelationships_returnsEmptyList() throws Exception {
        SimpleHtmlReport htmlReport = new SimpleHtmlReport();
        CodebaseGraphDTO dto = Mockito.mock(CodebaseGraphDTO.class);

        // Mock package graph with an edge
        Graph<String, DefaultWeightedEdge> packageGraph = new DefaultDirectedWeightedGraph<>(DefaultWeightedEdge.class);
        packageGraph.addVertex("com.pkg1");
        packageGraph.addVertex("com.pkg2");
        DefaultWeightedEdge pkgEdge = packageGraph.addEdge("com.pkg1", "com.pkg2");

        // Set the packageGraph field via reflection
        java.lang.reflect.Field packageGraphField = SimpleHtmlReport.class.getDeclaredField("packageGraph");
        packageGraphField.setAccessible(true);
        packageGraphField.set(htmlReport, packageGraph);

        // Return empty map for classRelationshipsInPackageRelationship
        Map<DefaultWeightedEdge, Set<DefaultWeightedEdge>> emptyMap = new HashMap<>();
        Mockito.when(dto.getClassRelationshipsInPackageRelationship()).thenReturn(emptyMap);
        Mockito.when(dto.getPackageReferencesGraph()).thenReturn(packageGraph);

        // Create a mock RankedDisharmony with the package edge
        RankedDisharmony edgeInfo = Mockito.mock(RankedDisharmony.class);
        Mockito.when(edgeInfo.getEdge()).thenReturn(pkgEdge);
        Mockito.when(edgeInfo.getPriority()).thenReturn(1);
        Mockito.when(edgeInfo.getCycleCount()).thenReturn(0);
        Mockito.when(edgeInfo.getEffortRank()).thenReturn(1);

        String repoUrl = "https://github.com/example/repo/blob/";

        // Use reflection to call the private method
        java.lang.reflect.Method method = SimpleHtmlReport.class.getDeclaredMethod(
                "getPackageRelationshipDisharmony", RankedDisharmony.class, String.class, CodebaseGraphDTO.class);
        method.setAccessible(true);

        // This should not throw NPE
        String[] result = (String[]) method.invoke(htmlReport, edgeInfo, repoUrl, dto);

        // Should return valid array with empty class edges
        assertNotNull(result);
        assertEquals(5, result.length);
        assertEquals("", result[4]); // class edges should be empty string
    }
}
