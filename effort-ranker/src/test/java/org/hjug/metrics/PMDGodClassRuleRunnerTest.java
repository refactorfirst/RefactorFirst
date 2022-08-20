package org.hjug.metrics;

import java.util.Optional;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class PMDGodClassRuleRunnerTest {

    private PMDGodClassRuleRunner PMDGodClassRuleRunner;

    @BeforeEach
    public void setUp() {
        PMDGodClassRuleRunner = new PMDGodClassRuleRunner();
    }

    @Test
    void testRuleRunnerExpectOneClass() throws Exception {
        String attributeHandler = "AttributeHandler.java";
        Optional<GodClass> optionalResult = PMDGodClassRuleRunner.runGodClassRule(
                attributeHandler, getClass().getClassLoader().getResourceAsStream(attributeHandler));

        GodClass result = optionalResult.get();

        Assertions.assertEquals("AttributeHandler.java", result.getFileName());
        Assertions.assertEquals("org.apache.myfaces.tobago.facelets", result.getPackageName());
        Assertions.assertEquals(77, result.getWmc().longValue());
        Assertions.assertEquals(105, result.getAtfd().longValue());
        Assertions.assertEquals(15.555999755859375, result.getTcc(), 0.001);
    }

    @Test
    void testRuleRunnerExpectJavaElevenClass() throws Exception {
        String attributeHandler = "AttributeHandlerJavaEleven.java";
        Optional<GodClass> optionalResult = PMDGodClassRuleRunner.runGodClassRule(
                attributeHandler, getClass().getClassLoader().getResourceAsStream(attributeHandler));

        GodClass result = optionalResult.get();

        Assertions.assertEquals("AttributeHandlerJavaEleven.java", result.getFileName());
        Assertions.assertEquals("org.apache.myfaces.tobago.facelets", result.getPackageName());
        Assertions.assertEquals(77, result.getWmc().longValue());
        Assertions.assertEquals(105, result.getAtfd().longValue());
        Assertions.assertEquals(15.555999755859375, result.getTcc(), 0.001);
    }

    @Test
    void testRuleRunnerExpectNoResults() throws Exception {
        String attributeHandler = "Attributes.java";
        Optional<GodClass> result = PMDGodClassRuleRunner.runGodClassRule(
                attributeHandler, getClass().getClassLoader().getResourceAsStream(attributeHandler));

        Assertions.assertFalse(result.isPresent());
    }

    // Only returns one result
    @Test
    void testRuleRunnerWithTwoClasses() throws Exception {
        String attributeHandler = "AttributeHandlerAndSorter.java";
        Optional<GodClass> result = PMDGodClassRuleRunner.runGodClassRule(
                attributeHandler, getClass().getClassLoader().getResourceAsStream(attributeHandler));

        Assertions.assertTrue(result.isPresent());
    }
}
