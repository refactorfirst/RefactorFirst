package org.hjug.refactorfirst.report;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * T5.1: the palette CSS is the single source of truth for the report's colors — every
 * custom property in {@code rf-palette.css} must equal the page-repo {@code palette.json}
 * fixture (light, dark, and the {@code prefers-color-scheme: dark} + system combination),
 * and the two dark blocks (explicit radio vs system media query) must never drift apart
 * (port of the page repo's theme-css.test.js guard).
 */
class PaletteParityTest {

    private static final String PALETTE_CSS = "assets/rf-palette.css";
    private static final String DARK_RADIO_SELECTOR = ":root:has(#rf-theme-dark:checked)";
    private static final String DARK_SYSTEM_SELECTOR = ":root:has(#rf-theme-system:checked)";

    private static JsonNode palette;
    private static Map<String, String> rules;

    /** camelCase palette.json key → kebab-case CSS custom property. */
    private static String cssVarOf(String paletteKey) {
        return "--" + paletteKey.replaceAll("([a-z])([A-Z])", "$1-$2").toLowerCase(java.util.Locale.ROOT);
    }

    @BeforeAll
    static void loadCssAndPalette() throws Exception {
        String css;
        try (var in = PaletteParityTest.class.getClassLoader().getResourceAsStream(PALETTE_CSS)) {
            if (in == null) {
                throw new IllegalStateException("palette stylesheet not found on classpath: " + PALETTE_CSS);
            }
            css = new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        }
        ObjectMapper mapper = new ObjectMapper();
        try (var in = PaletteParityTest.class.getResourceAsStream("/parity/palette.json")) {
            if (in == null) {
                throw new IllegalStateException("palette fixture not found on classpath: /parity/palette.json");
            }
            palette = mapper.readTree(in).get("palette");
        }
        rules = parseRules(css.replaceAll("/\\*[\\s\\S]*?\\*/", ""));
    }

    /** Indexes CSS declaration blocks by selector (same parser as the page repo's guard). */
    static Map<String, String> parseRules(String noCommentsCss) {
        Map<String, String> rules = new LinkedHashMap<>();
        Matcher matcher = Pattern.compile("([^{}]+)\\{([^{}]*)\\}").matcher(noCommentsCss);
        while (matcher.find()) {
            for (String selector : matcher.group(1).split(",")) {
                rules.put(selector.trim(), matcher.group(2));
            }
        }
        return rules;
    }

    /** Extracts custom property declarations from one declaration block. */
    static Map<String, String> cssVarsOf(String body) {
        Map<String, String> vars = new LinkedHashMap<>();
        Matcher matcher = Pattern.compile("(--[\\w-]+)\\s*:\\s*([^;]+);").matcher(body);
        while (matcher.find()) {
            vars.put(matcher.group(1), matcher.group(2).trim());
        }
        return vars;
    }

    /** Asserts every palette.json color token appears as an equal custom property in the block. */
    private void assertPaletteMatches(JsonNode expected, String selector) {
        assertTrue(rules.containsKey(selector), "missing palette block for " + selector);
        Map<String, String> vars = cssVarsOf(rules.get(selector));
        StringBuilder mismatches = new StringBuilder();
        Iterator<String> keys = expected.fieldNames();
        while (keys.hasNext()) {
            String key = keys.next();
            if (!expected.get(key).isValueNode()) {
                continue; // legendSwatches/bubbleColors are JS data, not CSS tokens
            }
            String varName = cssVarOf(key);
            String actual = vars.get(varName);
            String expectedValue = expected.get(key).asText();
            if (actual == null) {
                mismatches.append(varName).append(" missing; ");
            } else if (!actual.equalsIgnoreCase(expectedValue)) {
                mismatches
                        .append(varName)
                        .append("=")
                        .append(actual)
                        .append(" != ")
                        .append(expectedValue)
                        .append("; ");
            }
        }
        assertEquals("", mismatches.toString(), "palette drift for " + selector);
    }

    @DisplayName("light palette block equals the palette.json light tokens")
    @Test
    void lightPaletteMatchesFixture() {
        assertPaletteMatches(palette.get("light"), ":root");
    }

    @DisplayName("explicit dark radio block equals the palette.json dark tokens")
    @Test
    void darkRadioPaletteMatchesFixture() {
        assertPaletteMatches(palette.get("dark"), DARK_RADIO_SELECTOR);
    }

    @DisplayName("system + prefers-color-scheme block equals the palette.json dark tokens")
    @Test
    void systemDarkPaletteMatchesFixture() {
        assertPaletteMatches(palette.get("dark"), DARK_SYSTEM_SELECTOR);
    }

    @DisplayName("the two dark blocks are byte-identical (mod whitespace)")
    @Test
    void darkBlocksNeverDrift() {
        String radioBody = normalize(rules.get(DARK_RADIO_SELECTOR));
        String systemBody = normalize(rules.get(DARK_SYSTEM_SELECTOR));
        assertEquals(radioBody, systemBody, "the duplicated dark palettes must stay identical");
    }

    private String normalize(String body) {
        return body.replaceAll("\\s+", " ").trim();
    }
}
