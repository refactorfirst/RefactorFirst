package org.hjug.metrics;

import static org.junit.Assert.assertEquals;

import java.util.Locale;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class GodClassParsingTest {

    private Locale defaultLocale;

    @Before
    public void before() {
        defaultLocale = Locale.getDefault(Locale.Category.FORMAT);
        Locale.setDefault(Locale.Category.FORMAT, Locale.ENGLISH);
    }

    @After
    public void after() {
        Locale.setDefault(defaultLocale);
    }

    @Test
    public void test() {
        String result = "Possible God Class (WMC=9200, ATFD=1,700, TCC=4.597%)";
        GodClass god = new GodClass("a.txt", "org.hjug", result);
        assertEquals(Integer.valueOf(9200), god.getWmc());
        assertEquals(Integer.valueOf(1700), god.getAtfd());
        assertEquals(Float.valueOf(4.597f), god.getTcc());
    }
}
