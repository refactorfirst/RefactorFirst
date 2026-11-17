const assert = require('node:assert/strict');
const { readFileSync } = require('node:fs');
const { resolve } = require('node:path');
const { test } = require('node:test');

// T6.4: copyCellContent — navigator.clipboard.writeText with the
// textarea+execCommand('copy') fallback (never throws); toast message dedupe;
// 3 s auto-dismiss; the toast region carries role="status" aria-live="polite".

const ASSETS_DIR = resolve(__dirname, '../../main/resources/assets');

function harness({clipboard} = {}) {
    const timeCalls = [];
    const cleared = [];
    const appended = [];
    const removed = [];
    const written = [];

    function makeNode() {
        const node = {
            style: {},
            attributes: {},
            texts: [],
            select: () => {},
            setAttribute: (name, value) => {
                node.attributes[name] = value;
            },
            appendChild: () => {},
        };
        Object.defineProperty(node, 'textContent', {
            get: () => node.texts.at(-1) ?? '',
            set: (value) => node.texts.push(value),
        });
        return node;
    }

    const document = {
        createElement: () => makeNode(),
        body: {
            appendChild: (node) => appended.push(node),
            removeChild: (node) => removed.push(node),
        },
        getElementById: (id) => appended.find(node => node.id === id) || null,
        execCommand: () => true,
    };
    const window = {
        navigator: clipboard ? {clipboard} : {},
        setTimeout: (handler, delay) => {
            const id = timeCalls.length + 1;
            timeCalls.push({id, handler, delay});
            return id;
        },
        clearTimeout: (id) => cleared.push(id),
    };
    return {
        document, window, appended, removed, written, timeCalls, cleared,
        fireTimeout: (id) => {
            if (cleared.includes(id)) return false;
            timeCalls.find(call => call.id === id).handler();
            return true;
        },
        get toast() {
            return appended.find(node => node.id === 'rf-copy-toast');
        },
    };
}

function loadRfCopy(h, config) {
    global.window = h.window;
    global.document = h.document;
    h.window.RF = {};
    if (config) h.window.RF.TABLE_CONFIG = config;
    new Function(readFileSync(resolve(ASSETS_DIR, 'rf-copy.js'), 'utf8'))();
    return h.window.RF;
}

test('copyCellContent prefers the async Clipboard API', async () => {
    const h = harness({clipboard: {writeText: async (text) => h.written.push(text)}});
    const RF = loadRfCopy(h);
    const ok = await RF.copyCellContent('cell text');
    assert.equal(ok, true);
    assert.deepEqual(h.written, ['cell text']);
});

test('copyCellContent falls back to the textarea + execCommand path', async () => {
    const h = harness({clipboard: {writeText: async () => {
        throw new Error('denied');
    }}});
    const RF = loadRfCopy(h);
    const ok = await RF.copyCellContent('cell text');
    assert.equal(ok, true, 'the legacy path reports success');
    assert.equal(h.appended.filter(node => node.attributes.readonly === '').length, 1, 'textarea fallback used');
});

test('copyCellContent never throws, even when everything fails', async () => {
    const h = harness({clipboard: {writeText: async () => {
        throw new Error('denied');
    }}});
    h.document.execCommand = () => {
        throw new Error('unsupported');
    };
    const RF = loadRfCopy(h);
    const ok = await RF.copyCellContent(null);
    assert.equal(ok, false, 'failure is reported, never thrown');
});

test('showToast dedupes a repeated message and auto-dismisses after 3s', async () => {
    const h = harness();
    const RF = loadRfCopy(h);

    RF.showToast('Copied cell');
    RF.showToast('Copied cell');

    assert.ok(h.toast, 'the toast region was created');
    assert.deepEqual(h.toast.texts, ['Copied cell'], 'duplicate message deduped');
    assert.equal(h.timeCalls.length, 2, 'the duplicate re-arms the auto-dismiss');
    assert.deepEqual(h.cleared, [1], 'the first timer is cancelled (no double dismiss)');
    assert.equal(h.timeCalls[1].delay, 3000, 'auto-dismiss is 3s');

    assert.equal(h.fireTimeout(1), false, 'a canceled callback cannot clear the toast');
    assert.equal(h.toast.texts.at(-1), 'Copied cell');
    assert.equal(h.fireTimeout(2), true);
    assert.equal(h.toast.texts.at(-1), '', 'the toast clears after the timeout');
});

test('showToast cancels the previous timer before replacing the visible message', () => {
    const h = harness();
    const RF = loadRfCopy(h);
    RF.showToast('Copied A');
    RF.showToast('Copied B');
    assert.deepEqual(h.toast.texts, ['Copied A', 'Copied B']);
    assert.deepEqual(h.cleared, [1], 'the previous timer is canceled before the replacement toast schedules');
    assert.equal(h.fireTimeout(1), false, 'the previous timer cannot clear the replacement toast');
    assert.equal(h.toast.texts.at(-1), 'Copied B');
    assert.equal(h.fireTimeout(2), true);
    assert.equal(h.toast.texts.at(-1), '');
});

test('the toast region is a polite status live region', () => {
    const h = harness();
    const RF = loadRfCopy(h);
    RF.showToast('Copied');
    assert.equal(h.toast.attributes.role, 'status');
    assert.equal(h.toast.attributes['aria-live'], 'polite');
});

test('fallback removes its textarea and restores focus when copying throws', () => {
    // Given a focused trigger and a failing legacy clipboard
    const h = harness();
    let focused = false;
    h.document.activeElement = {focus: () => { focused = true; }};
    h.document.execCommand = () => { throw new Error('denied'); };
    const RF = loadRfCopy(h);
    // When copying fails, then cleanup and focus restoration still run
    assert.equal(RF.fallbackCopy('text'), false);
    assert.equal(h.removed.length, 1);
    assert.equal(focused, true);
});
