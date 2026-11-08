const assert = require('node:assert/strict');
const { readFileSync } = require('node:fs');
const { resolve } = require('node:path');
const { test } = require('node:test');

// T5.2: rf-theme.js reads/persists localStorage['rf-theme'] (invalid values fall
// back to system), publishes theme changes on radio change and on
// prefers-color-scheme change while in system, and never touches styles itself
// (D6: zero inline style writes).

const ASSETS_DIR = resolve(__dirname, '../../main/resources/assets');

/** A minimal document/window harness with the three theme radios. */
function harness({stored} = {}) {
    const radios = {};
    for (const mode of ['light', 'dark', 'system']) {
        radios[mode] = {id: `rf-theme-${mode}`, checked: mode === 'system', name: 'rf-theme', value: mode};
    }
    const listeners = {};
    const store = new Map(stored ? [['rf-theme', stored]] : []);

    const document = {
        documentElement: {hasAttribute: () => false, setAttribute: () => {}},
        getElementById: (id) => {
            const mode = id.replace('rf-theme-', '');
            return radios[mode] || null;
        },
        addEventListener: (type, handler) => {
            (listeners[type] = listeners[type] || []).push(handler);
        },
    };
    const window = {
        localStorage: {
            getItem: (key) => (store.has(key) ? store.get(key) : null),
            setItem: (key, value) => store.set(key, value),
        },
        // rf-theme registers its system-palette listener here; the harness
        // captures the callback as __systemChange so tests can fire it.
        matchMedia: (query) => ({
            addEventListener: (type, callback) => {
                window.__systemChange = callback;
            },
        }),
    };
    return {
        document, window, radios, store, listeners,
        fireChange(target) {
            (listeners.change || []).forEach(handler => handler({target}));
        },
    };
}

function loadThemeModule(h) {
    global.window = h.window;
    global.document = h.document;
    // RF namespace must exist before rf-theme.js runs.
    h.window.RF = global.window.RF || {};
    new Function(readFileSync(resolve(ASSETS_DIR, 'rf-theme.js'), 'utf8'))();
    return h.window.RF.theme;
}

test('restore defaults to system and binds the radios', () => {
    const h = harness();
    const theme = loadThemeModule(h);
    assert.equal(theme.restore(), 'system');
    assert.equal(theme.current(), 'system');
    assert.equal(h.radios.system.checked, true);
});

test('restore applies the persisted mode; invalid values fall back to system', () => {
    for (const [stored, expected] of [
        ['dark', 'dark'],
        ['light', 'light'],
        ['system', 'system'],
        ['nonsense', 'system'],
        ['', 'system'],
    ]) {
        const h = harness({stored});
        const theme = loadThemeModule(h);
        assert.equal(theme.restore(), expected, `stored=${JSON.stringify(stored)}`);
        assert.equal(theme.current(), expected);
    }
});

test('radio changes emit onThemeChange and persist the value', () => {
    const h = harness();
    const theme = loadThemeModule(h);
    theme.restore();

    const seen = [];
    theme.onChange(mode => seen.push(mode));

    h.radios.dark.checked = true;
    h.radios.system.checked = false;
    h.fireChange(h.radios.dark);

    assert.deepEqual(seen, ['dark']);
    assert.equal(h.store.get('rf-theme'), 'dark');
});

test('prefers-color-scheme changes emit only while in system mode', () => {
    const h = harness();
    const theme = loadThemeModule(h);
    theme.restore();

    const seen = [];
    theme.onChange(mode => seen.push(mode));

    // Simulate the media-query listener rf-theme registered on the window.
    assert.equal(typeof h.window.matchMedia, 'function');
    h.window.__systemChange();
    assert.deepEqual(seen, ['system'], 'a system palette change re-emits in system mode');

    // Switch to explicit dark: media changes no longer emit.
    h.radios.dark.checked = true;
    h.radios.system.checked = false;
    h.fireChange(h.radios.dark);
    seen.length = 0;
    h.window.__systemChange();
    assert.deepEqual(seen, [], 'explicit modes ignore OS palette changes');
});

test('onChange returns an unsubscribe function', () => {
    const h = harness();
    const theme = loadThemeModule(h);
    theme.restore();

    const seen = [];
    const unsubscribe = theme.onChange(mode => seen.push(mode));
    unsubscribe();

    h.radios.dark.checked = true;
    h.radios.system.checked = false;
    h.fireChange(h.radios.dark);
    assert.deepEqual(seen, [], 'unsubscribed listeners are not called');
});

test('rf-theme never touches styles (D6: zero inline style writes)', () => {
    const source = readFileSync(resolve(ASSETS_DIR, 'rf-theme.js'), 'utf8');
    assert.ok(!/\.style\.\w+\s*=/.test(source), 'rf-theme.js must not write inline styles');
    assert.ok(!source.includes('setProperty'), 'rf-theme.js must not set CSS properties');
});
