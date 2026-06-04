package org.hjug.refactorfirst.report;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class SimpleHtmlReportTest {

    @Test
    void isDateTime() {
        HtmlReport htmlReport = new HtmlReport();
        String commitDateTime = "7/22/23, 5:00 AM";
        Assertions.assertTrue(htmlReport.isDateTime(commitDateTime));
    }

    @Test
    void testSimpleMethodSignature() {
        HtmlReport htmlReport = new HtmlReport();
        String sig = "foo(java.lang.String, java.lang.String)";
        Assertions.assertEquals("foo(String,String)", htmlReport.getSimpleMethodSignature(sig));
    }

    @Test
    void testSimpleMethodSignatureWithGenerics() {
        HtmlReport htmlReport = new HtmlReport();
        String sig = "foo(java.util.List<java.lang.String>, java.util.List<java.lang.String>)";
        Assertions.assertEquals("foo(List<String>,List<String>)", htmlReport.getSimpleMethodSignature(sig));
    }

    @Test
    void testSimpleMethodSignatureWithGenericsAndWildcard() {
        HtmlReport htmlReport = new HtmlReport();
        String sig = "foo(java.util.List<? extends java.lang.String>, java.util.List<? super java.lang.String>)";
        Assertions.assertEquals(
                "foo(List<? extends String>,List<? super String>)", htmlReport.getSimpleMethodSignature(sig));
    }

    @Test
    void testSimpleMethodSignatureWithGenericsAndWildcardAndBounds() {
        HtmlReport htmlReport = new HtmlReport();
        String sig =
                "foo(java.util.List<? extends java.lang.String, java.lang.String>, java.util.List<? super java.lang.String, java.lang.String>)";
        Assertions.assertEquals(
                "foo(List<? extends String,String>,List<? super String,String>)",
                htmlReport.getSimpleMethodSignature(sig));
    }

    @Test
    void testSimplifyDuplicatePartners() {
        HtmlReport htmlReport = new HtmlReport();
        String duplicationPartners =
                "upWaitQueue(com.tonikelope.megabasterd.Transference) ↔ TransferenceManager.downWaitQueue(com.tonikelope.megabasterd.Transference)";
        Assertions.assertEquals(
                "upWaitQueue(Transference) ↔ TransferenceManager.downWaitQueue(Transference)",
                htmlReport.simplifyDuplicatePartners(duplicationPartners));
    }

    @Test
    void testSimpleMethodSignatureWithClassTypeParameter() {
        HtmlReport htmlReport = new HtmlReport();
        String sig = "isAllSuitableNodesOffline(Generic{R extends hudson.model.AbstractBuild}, Generic{R}>})";
        Assertions.assertEquals("isAllSuitableNodesOffline(R)", htmlReport.getSimpleMethodSignature(sig));
    }

    @Test
    void testSimpleMethodSignatureWithMethodTypeParameter() {
        HtmlReport htmlReport = new HtmlReport();
        String sig = "copy(Generic{T extends hudson.model.TopLevelItem},java.lang.String)";
        Assertions.assertEquals("copy(T,String)", htmlReport.getSimpleMethodSignature(sig));
    }

    @Test
    void testSimplifyDuplicatePartnersWithDollarSign() {
        HtmlReport htmlReport = new HtmlReport();
        String duplicationPartners = "method(com.example.Outer$Inner) ↔ Other.method(com.example.Outer$Inner)";
        Assertions.assertEquals(
                "method(Outer$Inner) ↔ Other.method(Outer$Inner)",
                htmlReport.simplifyDuplicatePartners(duplicationPartners));
    }
}
