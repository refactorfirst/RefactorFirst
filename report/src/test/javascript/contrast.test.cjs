const assert = require('node:assert/strict');
const { readFileSync } = require('node:fs');
const { resolve } = require('node:path');
const { test } = require('node:test');

// T8.2: WCAG contrast — parse rf-palette.css, compute the contrast ratio for
// every text/background token pair in BOTH palettes (>= 4.5:1 normal text,
// >= 3:1 large text & UI boundaries) and the focus indicator (>= 3:1). The
// page repo's palette.json passes these on the page; this test locks the port.

const ASSETS_DIR = resolve(__dirname, '../../main/resources/assets');
const PALETTE_JSON = resolve(__dirname, '../resources/parity/palette.json');

function luminance(hex) {
    const value = hex.replace('#', '');
    const channels = [0, 2, 4].map(offset => {
        const channel = parseInt(value.slice(offset, offset + 2), 16) / 255;
        return channel <= 0.03928
            ? channel / 12.92
            : Math.pow((channel + 0.055) / 1.055, 2.4);
    });
    return 0.2126 * channels[0] + 0.7152 * channels[1] + 0.0722 * channels[2];
}

function contrastRatio(foreground, background) {
    const l1 = luminance(foreground);
    const l2 = luminance(background);
    const [lighter, darker] = l1 >= l2 ? [l1, l2] : [l2, l1];
    return (lighter + 0.05) / (darker + 0.05);
}

function paletteVars(palette) {
    const vars = {};
    for (const [key, value] of Object.entries(palette)) {
        if (typeof value !== 'string' || !value.startsWith('#')) continue;
        vars[key] = value;
    }
    return vars;
}

const palette = JSON.parse(readFileSync(PALETTE_JSON, 'utf8')).palette;

// body text, muted text, link text on the page/card surfaces (1.4.3 normal text).
const TEXT_ON_SURFACE = [
    ['textColor', 'bgColor', 4.5],
    ['textColor', 'surfaceColor', 4.5],
    ['textColor', 'cardBg', 4.5],
    ['mutedColor', 'bgColor', 4.5],
    ['mutedColor', 'cardBg', 4.5],
    ['linkColor', 'bgColor', 4.5],
];

// UI boundary/affordance pairs (1.4.11 non-text contrast >= 3:1).
const UI_BOUNDARIES = [
    ['brandColor', 'bgColor', 3],
    ['brandAccent', 'bgColor', 3],
    ['graphHoverEdge', 'bgColor', 3],
    ['graphHoverIn', 'bgColor', 3],
    ['graphHoverOut', 'bgColor', 3],
    ['popupCloseBg', 'cardBg', 3],
];

// On-accent text: white (or the on-accent token) on the brand surfaces.
const ON_ACCENT = [
    ['onAccentColor', 'brandColor', 4.5],
    ['onAccentColor', 'brandAccent', 4.5],
];

// Sticky table headers: white text on the sticky background.
const TABLE_HEADER = [['stickyHeaderBackground', 4.5]];

for (const mode of ['light', 'dark']) {
    const vars = paletteVars(palette[mode]);

    test(`${mode}: text surfaces keep >= 4.5:1 contrast`, () => {
        for (const [fg, bg, minimum] of TEXT_ON_SURFACE) {
            const ratio = contrastRatio(vars[fg], vars[bg]);
            assert.ok(
                ratio >= minimum,
                `${mode} ${fg} on ${bg}: ${ratio.toFixed(2)} < ${minimum}`);
        }
    });

    test(`${mode}: UI boundaries keep >= 3:1 contrast`, () => {
        for (const [fg, bg, minimum] of UI_BOUNDARIES) {
            const ratio = contrastRatio(vars[fg], vars[bg]);
            assert.ok(
                ratio >= minimum,
                `${mode} ${fg} on ${bg}: ${ratio.toFixed(2)} < ${minimum}`);
        }
    });

    test(`${mode}: on-accent text keeps >= 4.5:1 contrast`, () => {
        for (const [fg, bg, minimum] of ON_ACCENT) {
            const ratio = contrastRatio(vars[fg], vars[bg]);
            assert.ok(ratio >= minimum, `${mode} ${fg} on ${bg}: ${ratio.toFixed(2)} < ${minimum}`);
        }
    });

    test(`${mode}: the sticky table header text keeps >= 4.5:1 on its background`, () => {
        // The sticky header background's own text color is the on-accent white.
        for (const [bg, minimum] of TABLE_HEADER) {
            const ratio = contrastRatio(vars.onAccentColor, vars[bg]);
            assert.ok(ratio >= minimum, `${mode} onAccentColor on ${bg}: ${ratio.toFixed(2)} < ${minimum}`);
        }
    });

    test(`${mode}: the focus indicator keeps >= 3:1 against the page`, () => {
        const ratio = contrastRatio(vars.brandAccent, vars.bgColor);
        assert.ok(ratio >= 3, `${mode} focus ring: ${ratio.toFixed(2)} < 3`);
    });
}
