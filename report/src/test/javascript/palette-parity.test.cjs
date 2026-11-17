const assert = require('node:assert/strict');
const { readFileSync } = require('node:fs');
const { resolve } = require('node:path');
const { test } = require('node:test');

// T5.1 (Node twin of PaletteParityTest): every custom property in rf-palette.css equals
// the palette.json fixture (light, dark, and the prefers-color-scheme + system
// combination), and the two dark blocks never drift apart.

const PALETTE_CSS = resolve(__dirname, '../../main/resources/assets/rf-palette.css');
const PALETTE_JSON = resolve(__dirname, '../resources/parity/palette.json');

const DARK_RADIO_SELECTOR = ':root:has(#rf-theme-dark:checked)';
const DARK_SYSTEM_SELECTOR = ':root:has(#rf-theme-system:checked)';

function cssVarOf(paletteKey) {
    return '--' + paletteKey.replace(/([a-z])([A-Z])/g, '$1-$2').toLowerCase();
}

function parseRules(noCommentsCss) {
    const rules = new Map();
    const ruleRe = /([^{}]+)\{([^{}]*)\}/g;
    let match;
    while ((match = ruleRe.exec(noCommentsCss)) !== null) {
        for (const selector of match[1].split(',')) {
            rules.set(selector.trim(), match[2]);
        }
    }
    return rules;
}

function cssVarsOf(body) {
    const vars = new Map();
    const varRe = /(--[\w-]+)\s*:\s*([^;]+);/g;
    let match;
    while ((match = varRe.exec(body)) !== null) {
        vars.set(match[1], match[2].trim());
    }
    return vars;
}

const css = readFileSync(PALETTE_CSS, 'utf8').replace(/\/\*[\s\S]*?\*\//g, '');
const palette = JSON.parse(readFileSync(PALETTE_JSON, 'utf8')).palette;
const rules = parseRules(css);

function assertPaletteMatches(expected, selector) {
    assert.ok(rules.has(selector), `missing palette block for ${selector}`);
    const vars = cssVarsOf(rules.get(selector));
    const mismatches = [];
    for (const [key, value] of Object.entries(expected)) {
        if (typeof value === 'object') continue; // legendSwatches/bubbleColors are JS data
        const varName = cssVarOf(key);
        const actual = vars.get(varName);
        if (actual === undefined) mismatches.push(`${varName} missing`);
        else if (actual.toLowerCase() !== String(value).toLowerCase()) {
            mismatches.push(`${varName}=${actual} != ${value}`);
        }
    }
    assert.deepEqual(mismatches, [], `palette drift for ${selector}`);
}

test('light palette block equals the palette.json light tokens', () => {
    assertPaletteMatches(palette.light, ':root');
});

test('explicit dark radio block equals the palette.json dark tokens', () => {
    assertPaletteMatches(palette.dark, DARK_RADIO_SELECTOR);
});

test('system + prefers-color-scheme block equals the palette.json dark tokens', () => {
    assertPaletteMatches(palette.dark, DARK_SYSTEM_SELECTOR);
});

test('the two dark blocks are byte-identical (mod whitespace)', () => {
    const normalize = (body) => body.replace(/\s+/g, ' ').trim();
    assert.equal(normalize(rules.get(DARK_RADIO_SELECTOR)), normalize(rules.get(DARK_SYSTEM_SELECTOR)));
});
