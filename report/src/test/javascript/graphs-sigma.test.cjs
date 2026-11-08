const assert = require('node:assert/strict');
const { readFileSync } = require('node:fs');
const { resolve } = require('node:path');
const { test } = require('node:test');

// T7.3: the Sigma 2D popup — buildGraphologyGraph drops subgraph/cluster nodes
// (children > 0 rule), adds arrow edges and applies the sugiyama layout;
// bindSigmaHoverHighlight's nodeReducer returns {color: accent, highlighted:
// true} on the hovered node and {color, size: 3} on adjacent edges, wired via
// enterNode/leaveNode; the dark hover renderer paints the #1a2330 label box.

const ASSETS_DIR = resolve(__dirname, '../../main/resources/assets');

/** Minimal graphlib-dot / graphology / Sigma stubs. */
function harness() {
    const graphlibGraph = {
        nodes: () => ['cluster_P', 'A', 'B'],
        node: (id) => (id === 'cluster_P' ? {label: 'P'} : {label: id, color: '#123456'}),
        children: (id) => (id === 'cluster_P' ? ['A', 'B'] : []),
        edges: () => [{v: 'cluster_P', w: 'A'}, {v: 'A', w: 'B'}],
        edge: (e) => ({color: '#654321', weight: 2}),
    };
    const added = {nodes: [], edges: []};
    const nodeAttributes = {};
    const graphNodes = [];
    const graphNeighbors = {};
    class FakeGraph {
        addNode(id, attrs) {
            added.nodes.push({id, attrs});
            nodeAttributes[id] = attrs;
            graphNodes.push(id);
            graphNeighbors[id] = [];
        }
        addEdge(v, w, attrs) {
            added.edges.push({v, w, attrs});
            graphNeighbors[v].push(w);
            graphNeighbors[w].push(v);
        }
        nodes() {
            return graphNodes.slice();
        }
        forEachNeighbor(node, callback) {
            (graphNeighbors[node] || []).forEach(callback);
        }
        setNodeAttribute(id, key, value) {
            nodeAttributes[id][key] = value;
        }
    }
    const reducers = {};
    const eventHandlers = {};
    const settings = {};
    class FakeSigma {
        on(event, handler) {
            eventHandlers[event] = handler;
            return this;
        }
        setSetting(name, value) {
            settings[name] = value;
            return this;
        }
        scheduleRefresh() {}
    }
    const window = {
        graphlibDot: {read: () => graphlibGraph},
        graphology: {Graph: FakeGraph},
        Sigma: FakeSigma,
        screen: {width: 1000},
        RF: {},
    };
    global.window = window;
    for (const asset of ['rf-core.js', 'rf-theme.js', 'rf-charts.js', 'rf-graphs-sigma.js']) {
        new Function(readFileSync(resolve(ASSETS_DIR, asset), 'utf8'))();
    }
    return {window, added, reducers, eventHandlers, settings, nodeAttributes};
}

test('buildGraphologyGraph drops subgraph nodes by parsed structure and keeps arrow edges', () => {
    const h = harness();
    const RF = h.window.RF;

    const graph = RF.buildGraphologyGraph('digraph {}');
    const nodeIds = h.added.nodes.map(node => node.id);
    assert.deepEqual(nodeIds, ['A', 'B'], 'the cluster_P subgraph node is dropped, its children kept');
    const edgePairs = h.added.edges.map(edge => [edge.v, edge.w]);
    assert.deepEqual(edgePairs, [['A', 'B']], 'edges touching the dropped subgraph are dropped too');
    assert.equal(h.added.edges[0].attrs.type, 'arrow', 'edges render as arrows');
    assert.equal(h.added.edges[0].attrs.color, '#654321', 'the DOT edge color rides along');

    // The sugiyama layout assigned x/y to every node.
    for (const node of h.added.nodes) {
        assert.equal(typeof h.nodeAttributes[node.id].x, 'number', node.id + ' has an x');
        assert.equal(typeof h.nodeAttributes[node.id].y, 'number', node.id + ' has a y');
    }
});

test('bindSigmaHoverHighlight flags the hovered node and adjacent edges via the reducers', () => {
    const h = harness();
    const RF = h.window.RF;

    const nodeStates = new Map([['A', 'focus']]);
    const edgeStates = new Map([['A->B', 'out']]);
    // Recreate the module-internal state through the public events instead:
    const sigma = new h.window.Sigma();
    const graph = {
        edges: (node) => (node === 'A' ? ['A->B', 'cluster_P->A'] : []),
        extremities: (edge) => (edge === 'A->B' ? ['A', 'B'] : ['cluster_P', 'A']),
    };
    RF.bindSigmaHoverHighlight(sigma, graph);

    h.eventHandlers.enterNode({node: 'A'});
    const nodeReducer = h.settings.nodeReducer;
    const edgeReducer = h.settings.edgeReducer;
    assert.equal(typeof nodeReducer, 'function', 'the node reducer is installed');
    assert.equal(typeof edgeReducer, 'function', 'the edge reducer is installed');

    const highlighted = nodeReducer('A', {color: '#123456', size: 5});
    assert.equal(highlighted.color, '#b45309', 'the hovered node fills with the amber accent (light palette)');
    assert.equal(highlighted.highlighted, true, 'sigma renders the label via the highlighted style');

    const outgoingEdge = edgeReducer('A->B', {color: '#654321', size: 1});
    assert.equal(outgoingEdge.color, '#2a6f97', 'the outgoing edge takes the blue accent');
    assert.equal(outgoingEdge.size, 3, 'flagged edges thicken to 3');

    // The sink of the hovered node's outgoing edge takes the directional accent too.
    const sink = nodeReducer('B', {color: '#123456'});
    assert.equal(sink.color, '#2a6f97', 'the sink node goes blue');
    assert.equal(sink.highlighted, true, 'sigma renders its label via the highlighted style');

    h.eventHandlers.leaveNode({});
    const afterLeave = nodeReducer('A', {color: '#123456'});
    assert.deepEqual(afterLeave, {color: '#123456'}, 'leaveNode clears the states');
});

test('the dark palette swaps the label color and installs the dark hover renderer', () => {
    const h = harness();
    const RF = h.window.RF;

    global.document = {
        getElementById: (id) => (id === 'rf-theme-dark' ? {checked: true} : null),
    };
    h.window.document = global.document;
    const settings = RF.sigmaPopupSettings();    assert.deepEqual(settings.labelColor, {color: '#9fb0c0'}, 'dark node labels use the muted ink');
    assert.equal(typeof settings.hoverRenderer, 'function');

    // The dark hover renderer paints the #1a2330 label box (canvas-drawn).
    const fills = [];
    const context = {
        font: '',
        fillStyle: '',
        shadowOffsetX: 0,
        shadowOffsetY: 0,
        shadowBlur: 0,
        shadowColor: '',
        measureText: (text) => ({width: text.length * 6}),
        beginPath: () => {},
        moveTo: () => {},
        lineTo: () => {},
        arc: () => {},
        closePath: () => {},
        fill: () => fills.push(context.fillStyle),
        fillText: () => {},
    };
    settings.hoverRenderer(
        context,
        {x: 10, y: 10, size: 5, label: 'NodeA'},
        {labelSize: 12, labelFont: 'sans-serif', labelWeight: '400', labelColor: {color: '#9fb0c0'}});
    assert.ok(fills.includes('#1a2330'), 'the label box paints the dark popup surface');
});
