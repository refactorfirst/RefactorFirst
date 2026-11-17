package org.hjug.refactorfirst.report;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/**
 * Pins the shared rendering helpers that survive the P4 migration (used by the model
 * builder, the JSON report and the templates). Suite mapping per §10.2/§10.3: the
 * multi-module generateReport test moved to {@link ReportModelBuilderTest}; the
 * isDateTime/hyperlinkClass/package-relationship cell tests were replaced by the
 * template tests ({@link SimpleHtmlReportTemplateTest}, {@link SimpleReportWcagTest}).
 */
class SimpleHtmlReportTest {

    @Test
    void testSimpleMethodSignature() {
        SimpleHtmlReport htmlReport = new SimpleHtmlReport();
        String sig = "foo(java.lang.String, java.lang.String)";
        assertEquals("foo(String,String)", htmlReport.getSimpleMethodSignature(sig));
    }

    @Test
    void testSimpleMethodSignatureWithGenerics() {
        SimpleHtmlReport htmlReport = new SimpleHtmlReport();
        String sig = "foo(java.util.List<java.lang.String>, java.util.List<java.lang.String>)";
        assertEquals("foo(List<String>,List<String>)", htmlReport.getSimpleMethodSignature(sig));
    }

    @Test
    void testSimpleMethodSignatureWithGenericsAndWildcard() {
        SimpleHtmlReport htmlReport = new SimpleHtmlReport();
        String sig = "foo(java.util.List<? extends java.lang.String>, java.util.List<? super java.lang.String>)";
        assertEquals("foo(List<? extends String>,List<? super String>)", htmlReport.getSimpleMethodSignature(sig));
    }

    @Test
    void testSimpleMethodSignatureWithGenericsAndWildcardAndBounds() {
        SimpleHtmlReport htmlReport = new SimpleHtmlReport();
        String sig =
                "foo(java.util.List<? extends java.lang.String, java.lang.String>, java.util.List<? super java.lang.String, java.lang.String>)";
        assertEquals(
                "foo(List<? extends String,String>,List<? super String,String>)",
                htmlReport.getSimpleMethodSignature(sig));
    }

    @Test
    void testSimpleMethodSignatureWithClassTypeParameter() {
        SimpleHtmlReport htmlReport = new SimpleHtmlReport();
        String sig = "isAllSuitableNodesOffline(Generic{R extends hudson.model.AbstractBuild}, Generic{R}>})";
        assertEquals("isAllSuitableNodesOffline(R)", htmlReport.getSimpleMethodSignature(sig));
    }

    @Test
    void testSimpleMethodSignatureWithMethodTypeParameter() {
        SimpleHtmlReport htmlReport = new SimpleHtmlReport();
        String sig = "copy(Generic{T extends hudson.model.TopLevelItem},java.lang.String)";
        assertEquals("copy(T,String)", htmlReport.getSimpleMethodSignature(sig));
    }

    @Test
    void testSimplifyDuplicatePartners() {
        SimpleHtmlReport htmlReport = new SimpleHtmlReport();
        String duplicationPartners =
                "upWaitQueue(com.tonikelope.megabasterd.Transference) \u2194 TransferenceManager.downWaitQueue(com.tonikelope.megabasterd.Transference)";
        assertEquals(
                "upWaitQueue(Transference) \u2194 TransferenceManager.downWaitQueue(Transference)",
                htmlReport.simplifyDuplicatePartners(duplicationPartners));
    }

    @Test
    void testSimplifyDuplicatePartnersWithDollarSign() {
        SimpleHtmlReport htmlReport = new SimpleHtmlReport();
        String duplicationPartners = "method(com.example.Outer$Inner) \u2194 Other.method(com.example.Outer$Inner)";
        assertEquals(
                "method(Outer$Inner) \u2194 Other.method(Outer$Inner)",
                htmlReport.simplifyDuplicatePartners(duplicationPartners));
    }

    @Test
    void getClassName_preservesDollarForJavaAnonymous() {
        SimpleHtmlReport htmlReport = new SimpleHtmlReport();
        assertEquals("Outer$1", htmlReport.getClassName("com.example.Outer$1"));
        assertEquals("<anonymous>", htmlReport.getClassName("<anonymous>"));
    }
}
