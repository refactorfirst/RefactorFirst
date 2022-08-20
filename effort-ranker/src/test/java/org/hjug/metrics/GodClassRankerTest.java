package org.hjug.metrics;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Created by Wendy on 11/16/2016.
 */
public class GodClassRankerTest {

    private final GodClassRanker godClassRanker = new GodClassRanker();

    private final GodClass attributeHandler = new GodClass(
            "AttributeHandler",
            "org/hjug/git/AttributeHandler.java",
            "org.apache.myfaces.tobago.facelets",
            "null (WMC=79, ATFD=79, TCC=0.027777777777777776)");
    private final GodClass attributeHandler2 = new GodClass(
            "AttributeHandler",
            "org/hjug/git/AttributeHandler.java",
            "org.apache.myfaces.tobago.facelets",
            "null (WMC=79, ATFD=79, TCC=0.027777777777777776)");
    private final GodClass sorter = new GodClass(
            "Sorter", "Sorter.java", "org.apache.myfaces.tobago.facelets", " God class (WMC=51, ATFD=25, TCC=0.2)");
    private final GodClass sorter2 = new GodClass(
            "Sorter", "Sorter2.java", "org.apache.myfaces.tobago.facelets", " God class (WMC=51, ATFD=25, TCC=0.2)");
    private final GodClass themeImpl = new GodClass(
            "ThemeImpl",
            "ThemeImpl.java",
            "org.apache.myfaces.tobago.facelets",
            "God class (WMC=60, ATFD=16, TCC=0.07816091954022988)");
    private final GodClass themeImpl2 = new GodClass(
            "ThemeImpl",
            "ThemeImpl2.java",
            "org.apache.myfaces.tobago.facelets",
            "God class (WMC=60, ATFD=16, TCC=0.07816091954022988)");

    private final List<GodClass> godClasses = new ArrayList<>();

    @BeforeEach
    public void setUp() {
        godClasses.add(attributeHandler);
        godClasses.add(sorter);
        godClasses.add(themeImpl);
    }

    @Test
    void testRankGodClasses() {
        godClassRanker.rankGodClasses(godClasses);

        Assertions.assertEquals("ThemeImpl.java", godClasses.get(0).getFileName());
        Assertions.assertEquals("Sorter.java", godClasses.get(1).getFileName());
        Assertions.assertEquals(
                "org/hjug/git/AttributeHandler.java", godClasses.get(2).getFileName());

        Assertions.assertEquals(5, godClasses.get(0).getSumOfRanks().longValue());
        Assertions.assertEquals(6, godClasses.get(1).getSumOfRanks().longValue());
        Assertions.assertEquals(7, godClasses.get(2).getSumOfRanks().longValue());

        Assertions.assertEquals(1, godClasses.get(0).getOverallRank().longValue());
        Assertions.assertEquals(2, godClasses.get(1).getOverallRank().longValue());
        Assertions.assertEquals(3, godClasses.get(2).getOverallRank().longValue());
    }

    @Test
    void testWmcRanker() {
        godClassRanker.rankWmc(godClasses);

        Assertions.assertEquals("Sorter.java", godClasses.get(0).getFileName());
        Assertions.assertEquals("ThemeImpl.java", godClasses.get(1).getFileName());
        Assertions.assertEquals(
                "org/hjug/git/AttributeHandler.java", godClasses.get(2).getFileName());

        Assertions.assertEquals(1, godClasses.get(0).getWmcRank().longValue());
        Assertions.assertEquals(2, godClasses.get(1).getWmcRank().longValue());
        Assertions.assertEquals(3, godClasses.get(2).getWmcRank().longValue());
    }

    @Test
    void testWmcRankerWithDupeValue() {
        godClasses.add(themeImpl2);
        godClassRanker.rankWmc(godClasses);

        Assertions.assertEquals(1, godClasses.get(0).getWmcRank().longValue());
        Assertions.assertEquals(2, godClasses.get(1).getWmcRank().longValue());
        Assertions.assertEquals(2, godClasses.get(2).getWmcRank().longValue());
        Assertions.assertEquals(3, godClasses.get(3).getWmcRank().longValue());
    }

    @Test
    void testAtfdRanker() {
        godClassRanker.rankAtfd(godClasses);

        Assertions.assertEquals("ThemeImpl.java", godClasses.get(0).getFileName());
        Assertions.assertEquals("Sorter.java", godClasses.get(1).getFileName());
        Assertions.assertEquals(
                "org/hjug/git/AttributeHandler.java", godClasses.get(2).getFileName());

        Assertions.assertEquals(1, godClasses.get(0).getAtfdRank().longValue());
        Assertions.assertEquals(2, godClasses.get(1).getAtfdRank().longValue());
        Assertions.assertEquals(3, godClasses.get(2).getAtfdRank().longValue());
    }

    @Test
    void testAtfdRankerWithDupeValue() {
        godClasses.add(sorter2);
        godClassRanker.rankAtfd(godClasses);

        Assertions.assertEquals(1, godClasses.get(0).getAtfdRank().longValue());
        Assertions.assertEquals(2, godClasses.get(1).getAtfdRank().longValue());
        Assertions.assertEquals(2, godClasses.get(2).getAtfdRank().longValue());
        Assertions.assertEquals(3, godClasses.get(3).getAtfdRank().longValue());
    }

    @Test
    void testTccRanker() {
        godClassRanker.rankTcc(godClasses);

        Assertions.assertEquals(
                "org/hjug/git/AttributeHandler.java", godClasses.get(0).getFileName());
        Assertions.assertEquals("ThemeImpl.java", godClasses.get(1).getFileName());
        Assertions.assertEquals("Sorter.java", godClasses.get(2).getFileName());

        Assertions.assertEquals(1, godClasses.get(0).getTccRank().longValue());
        Assertions.assertEquals(2, godClasses.get(1).getTccRank().longValue());
        Assertions.assertEquals(3, godClasses.get(2).getTccRank().longValue());
    }

    @Test
    void testTccRankerWithDuplicateValue() {
        godClasses.add(attributeHandler2);
        godClassRanker.rankTcc(godClasses);

        // Two classes with a rank of 1
        Assertions.assertEquals(1, godClasses.get(0).getTccRank().longValue());
        Assertions.assertEquals(1, godClasses.get(1).getTccRank().longValue());
        Assertions.assertEquals(2, godClasses.get(2).getTccRank().longValue());
        Assertions.assertEquals(3, godClasses.get(3).getTccRank().longValue());
    }
}
