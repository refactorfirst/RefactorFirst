package org.hjug.refactorfirst.report;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Phase P0/T0.5 placeholder consumer for the canonical palette fixture
 * ({@code report/src/test/resources/parity/palette.json}, plan Appendix B).
 *
 * <p>Before the {@code rf-palette.css} stylesheet and the JS palette constants
 * land (Phase P5), this test pins the fixture's current values as the red
 * baseline: the day P5 starts, these assertions flip to reading
 * {@code rf-palette.css} / {@code RF.theme.ACCENTS} and fail until the port is
 * implemented — the red-green-refactor loop the plan mandates.
 */
class PaletteFixtureTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @SuppressWarnings("unchecked")
    private Map<String, Object> loadPalette() throws Exception {
        try (InputStream is = getClass().getResourceAsStream("/parity/palette.json")) {
            if (is == null) {
                throw new IllegalStateException("palette.json not found in classpath: /parity/palette.json");
            }
            return mapper.readValue(is, Map.class);
        }
    }

    private Map<String, Object> lightPalette() throws Exception {
        return (Map<String, Object>) loadPalette().get("palette");
    }

    private Map<String, Object> darkPalette() throws Exception {
        return (Map<String, Object>) loadPalette().get("palette");
    }

    @Test
    void paletteFixtureListsBothPalettesWithRequiredTokens() throws Exception {
        Map<String, Object> light = lightPalette();
        Map<String, Object> dark = darkPalette();
        Map<String, Object> lightTokens = (Map<String, Object>) light.get("light");
        Map<String, Object> darkTokens = (Map<String, Object>) dark.get("dark");

        assertTrue(lightTokens != null, "light palette must carry a light palette");
        assertTrue(darkTokens != null, "dark palette must carry a dark palette");

        List<String> tokens = List.of(
                "brandColor",
                "brandAccent",
                "linkColor",
                "onAccentColor",
                "textColor",
                "mutedColor",
                "bgColor",
                "surfaceColor",
                "cardBg",
                "borderColor",
                "inputBorder",
                "graphLine",
                "graphBreakupFill",
                "graphBreakupLine",
                "graphHoverEdge",
                "graphHoverIn",
                "graphHoverOut",
                "graphHoverOn",
                "errorColor",
                "successColor",
                "popupBorder",
                "popupCloseBg",
                "toastBg",
                "toastFg",
                "chartGrid",
                "chartLegendText",
                "stickyHeaderBackground",
                "stickyHeaderInsetShadow");
        for (String token : tokens) {
            assertTrue(lightTokens.containsKey(token), "light palette must carry token " + token);
            assertTrue(darkTokens.containsKey(token), "dark palette must carry token " + token);
        }
    }

    @Test
    void paletteFixtureCarriesCurrentLightBaselineColors() throws Exception {
        Map<String, Object> lightTokens = (Map<String, Object>) lightPalette().get("light");

        assertEquals("#2a6f97", lightTokens.get("brandColor"));
        assertEquals("#1f5273", lightTokens.get("brandAccent"));
        assertEquals("#ffffff", lightTokens.get("onAccentColor"));
        assertEquals("#1f2d3d", lightTokens.get("textColor"));
        assertEquals("#5c6b7a", lightTokens.get("mutedColor"));
        assertEquals("#ffffff", lightTokens.get("bgColor"));
        assertEquals("#f5f8fa", lightTokens.get("surfaceColor"));
        assertEquals("#ffffff", lightTokens.get("cardBg"));
        assertEquals("#dde4ea", lightTokens.get("borderColor"));
        assertEquals("#c6d2dc", lightTokens.get("inputBorder"));
        assertEquals("#000000", lightTokens.get("graphLine"));
        assertEquals("#ffc0cb", lightTokens.get("graphBreakupFill"));
        assertEquals("#000000", lightTokens.get("graphBreakupLine"));
        assertEquals("#b45309", lightTokens.get("graphHoverEdge"));
        assertEquals("#1e8449", lightTokens.get("graphHoverIn"));
        assertEquals("#2a6f97", lightTokens.get("graphHoverOut"));
        assertEquals("#ffffff", lightTokens.get("graphHoverOn"));
        assertEquals("#c0392b", lightTokens.get("errorColor"));
        assertEquals("#1e8449", lightTokens.get("successColor"));
        assertEquals("#cccccc", lightTokens.get("popupBorder"));
        assertEquals("#c0392b", lightTokens.get("popupCloseBg"));
        assertEquals("#1f2933", lightTokens.get("toastBg"));
        assertEquals("#ffffff", lightTokens.get("toastFg"));
        assertEquals("#e0e0e0", lightTokens.get("chartGrid"));
        assertEquals("#5c6b7a", lightTokens.get("chartLegendText"));
        assertEquals("#2a6f97", lightTokens.get("stickyHeaderBackground"));
        assertEquals("#1f5273", lightTokens.get("stickyHeaderInsetShadow"));
    }

    @Test
    void paletteFixtureCarriesCurrentDarkBaselineColors() throws Exception {
        Map<String, Object> darkTokens = (Map<String, Object>) darkPalette().get("dark");

        assertEquals("#3178a6", darkTokens.get("brandAccent"));
        assertEquals("#6fb1d9", darkTokens.get("linkColor"));
        assertEquals("#e6edf3", darkTokens.get("textColor"));
        assertEquals("#9fb0c0", darkTokens.get("mutedColor"));
        assertEquals("#10161d", darkTokens.get("bgColor"));
        assertEquals("#1a2330", darkTokens.get("surfaceColor"));
        assertEquals("#2c3a4d", darkTokens.get("borderColor"));
        assertEquals("#5b7396", darkTokens.get("inputBorder"));
        assertEquals("#9fb0c0", darkTokens.get("graphLine"));
        assertEquals("#7f3350", darkTokens.get("graphBreakupFill"));
        assertEquals("#f2b8cd", darkTokens.get("graphBreakupLine"));
        assertEquals("#f0b95a", darkTokens.get("graphHoverEdge"));
        assertEquals("#7ee2a8", darkTokens.get("graphHoverIn"));
        assertEquals("#8ed0fa", darkTokens.get("graphHoverOut"));
        assertEquals("#000000", darkTokens.get("graphHoverOn"));
        assertEquals("#f0928c", darkTokens.get("errorColor"));
        assertEquals("#5bbd8b", darkTokens.get("successColor"));
        assertEquals("#3a4b61", darkTokens.get("popupBorder"));
        assertEquals("#cf4436", darkTokens.get("popupCloseBg"));
        assertEquals("#566d8f", darkTokens.get("scrollbar"));
        assertEquals("#9fb0c0", darkTokens.get("chartLegendText"));
        assertEquals("#9fb0c0", darkTokens.get("darkDotEdgeFontColor"));
        assertEquals("#1a2330", darkTokens.get("sigmaHoverBg"));
        assertEquals("#ffffff", darkTokens.get("sigmaHoverBgLight"));
    }

    @Test
    void paletteFixtureCarriesLegendSwatchesAndBubbleRule() throws Exception {
        Map<String, Object> lightTokens = (Map<String, Object>) lightPalette().get("light");
        List<?> swatches = (List<?>) lightTokens.get("legendSwatches");
        assertTrue(swatches.size() == 3, "expected three legend swatches");
        assertEquals("rgb(235, 64, 52)", ((Map<?, ?>) swatches.get(0)).get("color"));
        assertEquals("rgb(137, 119, 74)", ((Map<?, ?>) swatches.get(1)).get("color"));
        assertEquals("rgb(39, 174, 96)", ((Map<?, ?>) swatches.get(2)).get("color"));

        Map<?, ?> bubbleRule = (Map<?, ?>) loadPalette().get("bubbleColorRule");
        if (bubbleRule == null) {
            throw new AssertionError("palette fixture must carry a bubbleColorRule");
        }
        assertTrue(bubbleRule.containsKey("red"));
        assertTrue(bubbleRule.containsKey("green"));
        assertTrue(bubbleRule.containsKey("blue"));
        assertTrue(bubbleRule.containsKey("t"));
        assertTrue(bubbleRule.containsKey("fillAlpha"));
    }
}
