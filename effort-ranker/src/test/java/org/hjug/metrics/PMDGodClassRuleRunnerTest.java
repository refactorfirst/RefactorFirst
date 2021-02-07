package org.hjug.metrics;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.util.Optional;

public class PMDGodClassRuleRunnerTest {

    private PMDGodClassRuleRunner PMDGodClassRuleRunner;

    @Before
    public void setUp() {
        PMDGodClassRuleRunner = new PMDGodClassRuleRunner();
    }

    @Test
    public void testRuleRunnerExpectOneClass() throws Exception {
        String attributeHandler = "AttributeHandler.java";
        Optional<GodClass> optionalResult =
                PMDGodClassRuleRunner.runGodClassRule(
                        attributeHandler, getClass().getClassLoader().getResourceAsStream(attributeHandler));

        GodClass result = optionalResult.get();

        
        Assert.assertEquals("AttributeHandler.java", result.getFileName());
        Assert.assertEquals("org.apache.myfaces.tobago.facelets", result.getPackageName());
        Assert.assertEquals(77, result.getWmc().longValue());
        Assert.assertEquals(105, result.getAtfd().longValue());
        Assert.assertEquals(15.555999755859375, result.getTcc(), 0.001);
    }

    @Test
    public void testRuleRunnerExpectJavaElevenClass() throws Exception {
        String attributeHandler = "AttributeHandlerJavaEleven.java";
        Optional<GodClass> optionalResult =
                PMDGodClassRuleRunner.runGodClassRule(
                        attributeHandler, getClass().getClassLoader().getResourceAsStream(attributeHandler));

        GodClass result = optionalResult.get();


        Assert.assertEquals("AttributeHandlerJavaEleven.java", result.getFileName());
        Assert.assertEquals("org.apache.myfaces.tobago.facelets", result.getPackageName());
        Assert.assertEquals(77, result.getWmc().longValue());
        Assert.assertEquals(105, result.getAtfd().longValue());
        Assert.assertEquals(15.555999755859375, result.getTcc(), 0.001);
    }

    @Test
    public void testRuleRunnerExpectNoResults() throws Exception {
        String attributeHandler = "Attributes.java";
        Optional<GodClass> result =
                PMDGodClassRuleRunner.runGodClassRule(
                        attributeHandler, getClass().getClassLoader().getResourceAsStream(attributeHandler));

        Assert.assertFalse(result.isPresent());
    }

    @Test //Only returns one result
    public void testRuleRunnerWithTwoClasses() throws Exception {
        String attributeHandler = "AttributeHandlerAndSorter.java";
        Optional<GodClass> result =
                PMDGodClassRuleRunner.runGodClassRule(
                        attributeHandler, getClass().getClassLoader().getResourceAsStream(attributeHandler));

        Assert.assertTrue(result.isPresent());
    }

}
