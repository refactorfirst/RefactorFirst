const assert = require('node:assert/strict');
const { readFileSync } = require('node:fs');
const { resolve } = require('node:path');
const { test } = require('node:test');

// T6.2: the sticky/scroll math as pure calculations, ported from the page repo's
// table-enhancer.js — measureHorizontalScroll toggles rf-scroll-x-enabled only when
// the wrapper overflows (scrollWidth > clientWidth + 1), and refreshStickyHeaders
// translates the header by the viewport scroll past the table top, clamped so the
// header never leaves the table's box. DOM behavior is covered in E2E.

const ASSETS_DIR = resolve(__dirname, '../../main/resources/assets');

function loadRf() {
    const window = {RF: {}};
    global.window = window;
    for (const asset of ['rf-tables.js']) {
        new Function(readFileSync(resolve(ASSETS_DIR, asset), 'utf8'))();
    }
    return window.RF;
}

test('horizontal scrolling engages only on real overflow (1px slack)', () => {
    const RF = loadRf();
    assert.equal(RF.shouldEnableHorizontalScroll(1000, 1000), false, 'exact fit: no scroll');
    assert.equal(RF.shouldEnableHorizontalScroll(1001, 1000), false, '1px slack tolerated');
    assert.equal(RF.shouldEnableHorizontalScroll(1002, 1000), true, 'beyond 1px: scroll enabled');
    assert.equal(RF.shouldEnableHorizontalScroll(2000, 1000), true);
});

test('stickyHeaderTransform pins the header below its resting box and clamps to the table', () => {
    const RF = loadRf();

    // Table fully visible: no transform.
    assert.equal(RF.stickyHeaderTransform({tableTop: 100, tableHeight: 500, headTop: 100, headHeight: 40}), '');

    // Viewport scrolled 200px past the table top (rect tops go negative):
    // header translates down 200.
    assert.equal(
        RF.stickyHeaderTransform({tableTop: -200, tableHeight: 500, headTop: -200, headHeight: 40}),
        'translateY(200px)');

    // The caption sits between table top and thead: the resting box anchors
    // below it (30px above the thead), so the offset shrinks by those 30px.
    assert.equal(
        RF.stickyHeaderTransform({tableTop: -100, tableHeight: 500, headTop: -70, headHeight: 40}),
        'translateY(70px)');

    // Clamp: the header never leaves the table's bottom edge.
    assert.equal(
        RF.stickyHeaderTransform({tableTop: -1000, tableHeight: 100, headTop: -1000, headHeight: 40}),
        'translateY(60px)',
        'maxOffset = tableHeight - headTopOffset - headHeight');
});

test('the DOM wrappers apply the pure math to real wrappers', () => {
    const RF = loadRf();
    const toggled = [];
    const fakeWrapper = {
        scrollWidth: 2000,
        clientWidth: 1000,
        classList: {
            toggle: (name, value) => toggled.push([name, value]),
            contains: () => true,
        },
    };
    const fakeTable = {
        querySelectorAll: () => [{style: {}}],
        querySelector: () => null,
        getBoundingClientRect: () => ({top: 0, height: 500}),
    };
    const fakeRoot = {
        querySelectorAll: (selector) => (selector.startsWith('.rf-table-scroll') ? [fakeWrapper] : []),
    };

    RF.measureHorizontalScroll(fakeRoot);
    assert.deepEqual(toggled, [['rf-scroll-x-enabled', true]]);

    const transforms = [];
    const th = {style: {set transform(v) { transforms.push(v); }, get transform() { return transforms.at(-1) ?? ''; }}};
    fakeTable.querySelectorAll = () => [th];
    const fakeRoot2 = {
        querySelectorAll: (selector) => (selector.startsWith('.rf-table-scroll') ? [fakeWrapper] : []),
    };
    // refreshStickyHeaders reads the wrapper's table through querySelector.
    fakeWrapper.querySelector = () => fakeTable;
    fakeWrapper.getBoundingClientRect = () => ({top: 0, height: 500});
    RF.refreshStickyHeaders(fakeRoot2);
    assert.ok(transforms.length >= 1, 'the header transform was applied');
});
