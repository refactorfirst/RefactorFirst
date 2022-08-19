package org.hjug.metrics;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Locale;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class GodClassParsingTest {

    private Locale defaultLocale;

    @BeforeEach
    public void before() {
        defaultLocale = Locale.getDefault(Locale.Category.FORMAT);
        Locale.setDefault(Locale.Category.FORMAT, Locale.ENGLISH);
    }

    @AfterEach
    public void after() {
        Locale.setDefault(defaultLocale);
    }

    @Test
    void test() {
        String result = "Possible God Class (WMC=9200, ATFD=1,700, TCC=4.597%)";
        GodClass god = new GodClass("a", "a.txt", "org.hjug", result);
        assertEquals(Integer.valueOf(9200), god.getWmc());
        assertEquals(Integer.valueOf(1700), god.getAtfd());
        assertEquals(Float.valueOf(4.597f), god.getTcc());
    }
}
