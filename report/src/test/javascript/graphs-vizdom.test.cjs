const assert = require('node:assert/strict');
const { readFileSync } = require('node:fs');
const { resolve } = require('node:path');
const { test } = require('node:test');

// T7.2: the vizdom hover model — the neighborhood derives from the vizdom
// <title> texts; hovering an edge flags the edge plus both endpoint nodes
// (rf-hover-edge); hovering a node flags it (rf-hover-focus) while its incoming
// edges + sources go green (rf-hover-in) and its outgoing edges + sinks go blue
// (rf-hover-out); every mouseover recomputes idempotently from scratch (no
// stale classes); focusin/focusout re-use the same model (keyboard parity).
// rf-graphs.css fills the states from the palette tokens.

const ASSETS_DIR = resolve(__dirname, '../../main/resources/assets');

/** A tiny DOM stand-in for one vizdom SVG. */
function fakeSvg() {
    const groups = {};
    const classesByGroup = {};
    const listeners = {};

    function makeGroup(kind, id, title) {
        const element = {
            kind,
            id,
            classList: {
                add: (name) => classesByGroup[id].add(name),
                remove: (...names) => names.forEach(name => classesByGroup[id].delete(name)),
                contains: (name) => classesByGroup[id].has(name),
            },
            querySelector: (selector) => (selector === 'title' ? {textContent: title} : null),
            querySelectorAll: () => [],
            addEventListener: (type, handler) => {
                (listeners[id] = listeners[id] || []).push(handler);
            },
            closest: (selector) => (selector === 'g.' + kind ? element : null),
        };
        groups[id] = element;
        classesByGroup[id] = new Set();
        return element;
    }

    const svg = {
        querySelectorAll: (selector) => {
            if (selector === 'g.node') return ['A', 'B'].map(id => groups[id]);
            if (selector === 'g.edge') return ['A->B'].map(id => groups[id]);
            return [];
        },
        addEventListener: (type, handler) => {
            (listeners['svg:' + type] = listeners['svg:' + type] || []).push(handler);
        },
        closest: () => null,
        querySelector: () => null,
    };
    // bindGraphHoverHighlights takes the graph CONTAINER and looks up the svg.
    const container = {
        querySelector: (selector) => (selector === 'svg' ? svg : null),
    };

    const nodeA = makeGroup('node', 'A', 'class_A');
    const nodeB = makeGroup('node', 'B', 'class_B');
    const edgeAB = makeGroup('edge', 'A->B', 'class_A -> class_B');
    // The closest() lookup for an edge group's children must resolve to the group itself.
    edgeAB.closest = (selector) => (selector === 'g.edge' ? edgeAB : null);
    nodeA.closest = (selector) => (selector === 'g.node' ? nodeA : null);
    nodeB.closest = (selector) => (selector === 'g.node' ? nodeB : null);

    return {
        svg, container, groups, classesByGroup, listeners,
        nodeA, nodeB, edgeAB,
        fire(type, target) {
            (listeners['svg:' + type] || []).forEach(handler => handler({target}));
        },
    };
}

function loadRf() {
    const window = {RF: {}};
    global.window = window;
    for (const asset of ['rf-core.js', 'rf-hierarchy-dot.js', 'rf-graphs-vizdom.js']) {
        new Function(readFileSync(resolve(ASSETS_DIR, asset), 'utf8'))();
    }
    return window.RF;
}

test('hovering an edge flags the edge and both endpoint nodes amber', () => {
    const RF = loadRf();
    const dom = fakeSvg();
    RF.bindGraphHoverHighlights(dom.container);

    dom.fire('mouseover', dom.edgeAB);

    assert.deepEqual(
        [...dom.classesByGroup['A->B']].filter(name => name.startsWith('rf-hover')),
        ['rf-hover-edge']);
    for (const nodeId of ['A', 'B']) {
        assert.deepEqual(
            [...dom.classesByGroup[nodeId]].filter(name => name.startsWith('rf-hover')),
            ['rf-hover-edge'],
            'endpoint ' + nodeId + ' takes the attention accent');
    }
});

test('hovering a node flags focus plus directional in/out neighborhoods', () => {
    const RF = loadRf();
    // A -> B only: hovering A flags B as the sink (out), hovering B flags A as the source (in).
    const dom = fakeSvg();
    RF.bindGraphHoverHighlights(dom.container);

    dom.fire('mouseover', dom.nodeA);
    assert.deepEqual(
        [...dom.classesByGroup['A']].filter(n => n.startsWith('rf-hover')),
        ['rf-hover-focus'],
        'the hovered node takes the focus state');
    assert.deepEqual(
        [...dom.classesByGroup['A->B']].filter(n => n.startsWith('rf-hover')),
        ['rf-hover-out'],
        'the outgoing edge goes blue');
    assert.deepEqual(
        [...dom.classesByGroup['B']].filter(n => n.startsWith('rf-hover')),
        ['rf-hover-out'],
        'the sink node goes blue');

    dom.fire('mouseover', dom.nodeB);
    assert.deepEqual(
        [...dom.classesByGroup['B']].filter(n => n.startsWith('rf-hover')),
        ['rf-hover-focus']);
    assert.deepEqual(
        [...dom.classesByGroup['A->B']].filter(n => n.startsWith('rf-hover')),
        ['rf-hover-in'],
        'the incoming edge goes green');
    assert.deepEqual(
        [...dom.classesByGroup['A']].filter(n => n.startsWith('rf-hover')),
        ['rf-hover-in'],
        'the source node goes green');
});

test('recompute is idempotent: hopping between targets never leaves stale classes', () => {
    const RF = loadRf();
    const dom = fakeSvg();
    RF.bindGraphHoverHighlights(dom.container);

    dom.fire('mouseover', dom.nodeA);
    dom.fire('mouseover', dom.edgeAB);
    dom.fire('mouseover', dom.nodeB);

    const flagged = Object.entries(dom.classesByGroup)
        .filter(([, classes]) => [...classes].some(name => name.startsWith('rf-hover')))
        .map(([id]) => id)
        .sort();
    assert.deepEqual(
        flagged,
        ['A', 'A->B', 'B'],
        'only the current neighborhood is flagged');

    dom.fire('mouseleave');
    const anyFlagged = Object.values(dom.classesByGroup).some(classes => classes.size > 0);
    assert.equal(anyFlagged, false, 'mouseleave clears every state');
});

test('focusin re-uses the hover model; focusout clears (keyboard parity)', () => {
    const RF = loadRf();
    const dom = fakeSvg();
    RF.bindGraphHoverHighlights(dom.container);

    dom.fire('focusin', dom.nodeA);
    assert.ok(dom.classesByGroup['A'].has('rf-hover-focus'));
    assert.ok(dom.classesByGroup['A->B'].has('rf-hover-out'));

    dom.fire('focusout', dom.nodeA);
    assert.equal(
        Object.values(dom.classesByGroup).some(classes => classes.size > 0),
        false,
        'focusout clears like mouseleave');
});

test('an edge title that fails to parse binds no highlight', () => {
    const RF = loadRf();
    const dom = fakeSvg();
    dom.groups['A->B'].querySelector = (selector) =>
        selector === 'title' ? {textContent: 'garbage without arrow'} : null;
    RF.bindGraphHoverHighlights(dom.container);

    dom.fire('mouseover', dom.edgeAB);
    assert.equal(
        [...dom.classesByGroup['A->B']].filter(name => name.startsWith('rf-hover')).length,
        0,
        'an unparseable edge title binds no highlight');
});

test('rf-graphs.css fills the states from the palette tokens', () => {
    const css = readFileSync(resolve(ASSETS_DIR, 'rf-graphs.css'), 'utf8');
    for (const state of ['rf-hover-edge', 'rf-hover-in', 'rf-hover-out']) {
        assert.ok(css.includes(`g.edge.${state}`), `the ${state} edge rule exists`);
        assert.ok(css.includes(`g.node.${state}`), `the ${state} node rule exists`);
    }
    assert.ok(css.includes('var(--graph-hover-edge)'));
    assert.ok(css.includes('var(--graph-hover-in)'));
    assert.ok(css.includes('var(--graph-hover-out)'));
    assert.ok(css.includes('var(--graph-hover-on)'), 'highlighted node labels re-ink');
    assert.ok(css.includes('stroke-width: 2.5px'), 'flagged edges thicken');
    assert.ok(css.includes('var(--graph-breakup-fill)'), 'break-up clusters re-drive from the palette');
    assert.ok(css.includes('var(--graph-line)'), 'graph lines re-drive from the palette');
});
