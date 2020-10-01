package org.hjug.metrics;



import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by Wendy on 11/16/2016.
 */
public class GodClassRankerTest {

    private GodClassRanker godClassRanker = new GodClassRanker();

    private GodClass attributeHandler = new GodClass("org/hjug/git/AttributeHandler.java", "org.apache.myfaces.tobago.facelets", "null (WMC=79, ATFD=79, TCC=0.027777777777777776)");
    private GodClass attributeHandler2 = new GodClass("org/hjug/git/AttributeHandler.java", "org.apache.myfaces.tobago.facelets", "null (WMC=79, ATFD=79, TCC=0.027777777777777776)");
    private GodClass sorter = new GodClass("Sorter.java", "org.apache.myfaces.tobago.facelets", " God class (WMC=51, ATFD=25, TCC=0.2)");
    private GodClass sorter2 = new GodClass("Sorter2.java", "org.apache.myfaces.tobago.facelets", " God class (WMC=51, ATFD=25, TCC=0.2)");
    private GodClass themeImpl = new GodClass("ThemeImpl.java", "org.apache.myfaces.tobago.facelets", "God class (WMC=60, ATFD=16, TCC=0.07816091954022988)");
    private GodClass themeImpl2 = new GodClass("ThemeImpl2.java", "org.apache.myfaces.tobago.facelets", "God class (WMC=60, ATFD=16, TCC=0.07816091954022988)");


    private List<GodClass> godClasses = new ArrayList<GodClass>();

    @Before
    public void setUp() {
        godClasses.add(attributeHandler);
        godClasses.add(sorter);
        godClasses.add(themeImpl);
    }

    @Test
    public void testRankGodClasses() {
        godClassRanker.rankGodClasses(godClasses);

        Assert.assertEquals("ThemeImpl.java", godClasses.get(0).getFileName());
        Assert.assertEquals("Sorter.java", godClasses.get(1).getFileName());
        Assert.assertEquals("org/hjug/git/AttributeHandler.java", godClasses.get(2).getFileName());

        Assert.assertEquals(godClasses.get(0).getSumOfRanks().longValue(), 5);
        Assert.assertEquals(godClasses.get(1).getSumOfRanks().longValue(), 6);
        Assert.assertEquals(godClasses.get(2).getSumOfRanks().longValue(), 7);

        Assert.assertEquals(godClasses.get(0).getOverallRank().longValue(), 1);
        Assert.assertEquals(godClasses.get(1).getOverallRank().longValue(), 2);
        Assert.assertEquals(godClasses.get(2).getOverallRank().longValue(), 3);
    }

    @Test
    public void testWmcRanker() {
        godClassRanker.rankWmc(godClasses);

        Assert.assertEquals("Sorter.java", godClasses.get(0).getFileName());
        Assert.assertEquals("ThemeImpl.java", godClasses.get(1).getFileName());
        Assert.assertEquals("org/hjug/git/AttributeHandler.java", godClasses.get(2).getFileName());


        Assert.assertEquals(godClasses.get(0).getWmcRank().longValue(), 1);
        Assert.assertEquals(godClasses.get(1).getWmcRank().longValue(), 2);
        Assert.assertEquals(godClasses.get(2).getWmcRank().longValue(), 3);
    }

    @Test
    public void testWmcRankerWithDupeValue() {
        godClasses.add(themeImpl2);
        godClassRanker.rankWmc(godClasses);

        Assert.assertEquals(1, godClasses.get(0).getWmcRank().longValue());
        Assert.assertEquals(2, godClasses.get(1).getWmcRank().longValue());
        Assert.assertEquals(2, godClasses.get(2).getWmcRank().longValue());
        Assert.assertEquals(3, godClasses.get(3).getWmcRank().longValue());
    }

    @Test
    public void testAtfdRanker() {
        godClassRanker.rankAtfd(godClasses);

        Assert.assertEquals("ThemeImpl.java", godClasses.get(0).getFileName());
        Assert.assertEquals("Sorter.java", godClasses.get(1).getFileName());
        Assert.assertEquals("org/hjug/git/AttributeHandler.java", godClasses.get(2).getFileName());

        Assert.assertEquals(1, godClasses.get(0).getAtfdRank().longValue());
        Assert.assertEquals(2, godClasses.get(1).getAtfdRank().longValue());
        Assert.assertEquals(3, godClasses.get(2).getAtfdRank().longValue());
    }

    @Test
    public void testAtfdRankerWithDupeValue() {
        godClasses.add(sorter2);
        godClassRanker.rankAtfd(godClasses);

        Assert.assertEquals(1, godClasses.get(0).getAtfdRank().longValue());
        Assert.assertEquals(2, godClasses.get(1).getAtfdRank().longValue());
        Assert.assertEquals(2, godClasses.get(2).getAtfdRank().longValue());
        Assert.assertEquals(3, godClasses.get(3).getAtfdRank().longValue());
    }

    @Test
    public void testTccRanker() {
        godClassRanker.rankTcc(godClasses);

        Assert.assertEquals("org/hjug/git/AttributeHandler.java", godClasses.get(0).getFileName());
        Assert.assertEquals("ThemeImpl.java", godClasses.get(1).getFileName());
        Assert.assertEquals("Sorter.java", godClasses.get(2).getFileName() );

        Assert.assertEquals(1, godClasses.get(0).getTccRank().longValue());
        Assert.assertEquals(2, godClasses.get(1).getTccRank().longValue());
        Assert.assertEquals(3, godClasses.get(2).getTccRank().longValue());
    }

    @Test
    public void testTccRankerWithDuplicateValue() {
        godClasses.add(attributeHandler2);
        godClassRanker.rankTcc(godClasses);

        //Two classes with a rank of 1
        Assert.assertEquals(1, godClasses.get(0).getTccRank().longValue());
        Assert.assertEquals(1, godClasses.get(1).getTccRank().longValue());
        Assert.assertEquals(2, godClasses.get(2).getTccRank().longValue());
        Assert.assertEquals(3, godClasses.get(3).getTccRank().longValue());
    }
}
