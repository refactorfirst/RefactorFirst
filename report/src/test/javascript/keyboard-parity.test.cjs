const assert = require('node:assert/strict');
const { readFileSync } = require('node:fs');
const { resolve } = require('node:path');
const { test } = require('node:test');

// T8.3: keyboard parity contracts — graph hover re-uses focusin/focusout (node
// anchors are tab stops), copy cells handle Enter/Space, sort buttons are real
// buttons (Enter/Space native), the radio group is a native group (arrow-key
// navigation is the browser's), search Escape clears, and the popup closes on
// Escape while trapping Tab inside.

const ASSETS_DIR = resolve(__dirname, '../../main/resources/assets');

function readAsset(name) {
    return readFileSync(resolve(ASSETS_DIR, name), 'utf8');
}

test('graph hover keyboard parity: focusin/focusout bind the hover model', () => {
    const vizdom = readAsset('rf-graphs-vizdom.js');
    assert.ok(vizdom.includes("svg.addEventListener('focusin'"), 'focusin re-uses the hover model');
    assert.ok(vizdom.includes("svg.addEventListener('focusout'"), 'focusout clears like mouseleave');
    // The hyperlinked node anchors are real tab stops (vizdom emits <a href>).
    assert.ok(vizdom.includes('target="_blank"'), 'node anchors stay hyperlinks (tab stops)');
});

test('copy cells handle Enter and Space alongside click', () => {
    const tables = readAsset('rf-tables.js');
    assert.ok(tables.includes("event.key === 'Enter' || event.key === ' '"), 'Enter/Space copy');
    assert.ok(tables.includes("cell.setAttribute('tabindex', '0')"), 'copy cells are tab stops');
});

test('sort controls are real buttons (native Enter/Space activation)', () => {
    const template = readFileSync(
        resolve(__dirname, '../../main/resources/templates/refactor-first-report.mustache'),
        'utf8');
    assert.ok(template.includes('<button type="button" class="rf-sort-btn"'), 'sort headers are buttons');
});

test('the theme radio group is a native radio group (arrow-key navigation)', () => {
    const template = readFileSync(
        resolve(__dirname, '../../main/resources/templates/refactor-first-report.mustache'),
        'utf8');
    assert.ok(template.includes('role="radiogroup"'), 'the toggle is a radiogroup');
    assert.ok(template.includes('type="radio" name="rf-theme"'), 'three native radios share the name');
    // Native radios get arrow-key navigation from the browser; no JS needed.
});

test('search clears on Escape; the popup closes on Escape and traps Tab', () => {
    const tables = readAsset('rf-tables.js');
    assert.ok(tables.includes("event.key === 'Escape'"), 'Escape clears the search');

    const core = readAsset('rf-core.js');
    const main = readAsset('rf-main.js');
    assert.ok(main.includes("if (event.key === 'Escape') RF.hidePopup();"), 'Escape closes the popup');
    assert.ok(core.includes('function bindFocusTrap(popup)'), 'the popup traps Tab (2.4.3)');
    assert.ok(core.includes('popupTriggerElement.focus()'), 'close restores focus to the trigger');
});

test('no keyboard trap outside the intentional popup trap', () => {
    // The copy cells are tab stops but focus always stays in the natural tab
    // order (Tab moves to the next cell/header) — no JS intercepts Tab outside
    // the popup focus trap.
    const tables = readAsset('rf-tables.js');
    assert.ok(!/keydown[\s\S]{0,200}preventDefault\(\)[\s\S]{0,200}'Tab'/.test(tables),
        'table cells must not intercept Tab');
    const theme = readAsset('rf-theme.js');
    assert.ok(!theme.includes("'Tab'"), 'the theme module must not intercept Tab');
});
