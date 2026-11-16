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

for (const dimension of ['2d', '3d']) {
    test(`${dimension} popup moves focus, traps Tab, restores trigger and disposes on close`, async () => {
        // Given real popup handlers with deterministic DOM and renderer stand-ins
        const handlers = {};
        const focus = node => { document.activeElement = node; };
        const trigger = {getAttribute: key => ({'data-popup': 'popup', 'data-container': 'graph-container-x', 'data-dot-var': 'graph'}[key]),
            hasAttribute: key => key === `data-popup-${dimension}`, addEventListener: (key, fn) => { handlers[key] = fn; }, focus() { focus(this); }};
        const close = {focus() { focus(this); }};
        const last = {focus() { focus(this); }};
        const popup = {style: {}, querySelector: () => close, querySelectorAll: () => [close, last],
            addEventListener: (key, fn) => { handlers.popupKey = fn; }};
        const container = {clientWidth: 800, clientHeight: 600};
        const overlay = {style: {}};
        const document = {activeElement: trigger, contains: () => true,
            getElementById: id => ({overlay, popup, 'graph-container-x': container}[id]),
            getElementsByClassName: () => [popup],
            querySelectorAll: selector => selector.startsWith('[id^=') ? [container] : selector.includes('data-popup-2d') ? [trigger] : []};
        let disposed = 0;
        const graph = new Proxy({}, {get: (_, key) => key === '_destructor' ? () => { disposed++; } : () => graph});
        const window = {graphlibDot: {read: () => ({nodes: () => [], edges: () => []})},
            ForceGraph3D: function () { return graph; }, SpriteText: function () {}, graphology: {},
            Sigma: function () { this.kill = () => { disposed++; }; }};
        const vm = require('node:vm');
        const context = vm.createContext({window, document, HTMLElement: Object, URL, console});
        for (const name of ['rf-core.js', 'rf-graphs-3d.js']) vm.runInContext(readAsset(name), context);
        Object.assign(window.RF, {graphDot: () => 'dot', buildGraphologyGraph: () => ({}), sigmaPopupSettings: () => ({}), bindSigmaHoverHighlight() {}});
        window.RF.bindPopupHandlers();
        // When either entrypoint opens, then focus enters and wraps in both directions
        handlers.click();
        await Promise.resolve();
        assert.equal(document.activeElement, close);
        let prevented = 0;
        handlers.popupKey({key: 'Tab', shiftKey: true, preventDefault() { prevented++; }});
        assert.equal(document.activeElement, last);
        handlers.popupKey({key: 'Tab', shiftKey: false, preventDefault() { prevented++; }});
        assert.equal(document.activeElement, close);
        assert.equal(prevented, 2);
        window.RF.hidePopup();
        assert.equal(document.activeElement, trigger);
        assert.equal(disposed, 1);
    });
}
test('cell copy preserves nested link activation and only handles keys on the cell', async () => {
    // Given the real binding function and a cell containing a linked child
    const source = readAsset('rf-tables.js');
    const bind = new Function('window', 'stripHtml',
        source.slice(source.indexOf('    function bindCopyableCells('),
            source.indexOf('    /** Applies the filter')) + '; return bindCopyableCells;');
    const handlers = {};
    const cell = {innerHTML: 'Cell text', setAttribute() {},
        addEventListener: (name, handler) => { handlers[name] = handler; },
        closest: () => null};
    const linkChild = {closest: selector => selector === 'a' ? {} : null};
    let copies = 0;
    let prevented = 0;
    bind({RF: {copyCellContent: async () => { copies++; return true; }}}, text => text)(
        {querySelectorAll: () => [cell]}, () => {});
    // When a linked descendant is clicked or receives Enter/Space
    handlers.click({target: linkChild});
    for (const key of ['Enter', ' ']) handlers.keydown({target: linkChild, key,
        preventDefault() { prevented++; }});
    // Then link activation is left to the browser
    assert.equal(copies, 0);
    assert.equal(prevented, 0);
    // When the cell itself is clicked or receives Enter/Space, then it still copies
    handlers.click({target: cell});
    for (const key of ['Enter', ' ']) handlers.keydown({target: cell, key,
        preventDefault() { prevented++; }});
    assert.equal(copies, 3);
    assert.equal(prevented, 2);
});
