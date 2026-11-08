const assert = require('node:assert/strict');
const { readFileSync } = require('node:fs');
const { resolve } = require('node:path');
const { test } = require('node:test');

// T7.4: the 3D force-graph popup (stubbed ForceGraph3D) — node/link arrays
// derive from the DOT with subgraph nodes dropped; onNodeHover/onLinkHover
// maintain the nodeStates/linkStates maps; the sprite recolor is O(changed)
// (touchedNodes ∪ current flags, not all sprites); the always-dark accents
// recolor only nodes whose state changed; links carry 4 directional particles
// and width 4 while flagged; below the 4000-element threshold the sprite
// labels load; DOT's own colors show when nothing is hovered.

const ASSETS_DIR = resolve(__dirname, '../../main/resources/assets');

const DOT = `strict digraph G {
subgraph cluster_P { label="P"; style=filled fillcolor=white; }
A [ label="A" shape=ellipse color=red style=filled];
B [ label="B" shape=ellipse];
A -> B [ label = "2" weight = "2" color = "red" ];
}`;

function harness() {
    const calls = [];
    const spriteByLabel = {};
    let linkColorAccessor = null;
    let linkWidthAccessor = null;
    let particlesAccessor = null;
    let nodeThreeObjectAccessor = null;
    const hoverHandlers = {};

    const graphlibGraph = {
        nodes: () => ['cluster_P', 'A', 'B'],
        node: (id) => (id === 'cluster_P' ? {label: 'P'} : {label: id, color: id === 'A' ? 'red' : 'white'}),
        children: (id) => (id === 'cluster_P' ? ['A', 'B'] : []),
        edges: () => [{v: 'A', w: 'B'}],
        edge: (e) => ({color: 'red', weight: 2}),
    };

    function makeSprite(label) {
        if (!spriteByLabel[label]) {
            spriteByLabel[label] = {label: label, color: null, material: {depthWrite: null}, textHeight: null};
        }
        return spriteByLabel[label];
    }

    class FakeForceGraph3D {
        constructor(container) {
            calls.push(['constructor', container]);
        }
        graphData(data) {
            calls.push(['graphData', data]);
            this.data = data;
            return this;
        }
        nodeLabel(key) {
            calls.push(['nodeLabel', key]);
            return this;
        }
        width(value) {
            calls.push(['width', value]);
            return this;
        }
        height(value) {
            calls.push(['height', value]);
            return this;
        }
        nodeThreeObject(accessor) {
            nodeThreeObjectAccessor = accessor;
            return this;
        }
        linkColor(accessor) {
            // force-graph's fluent API: a set with an argument, a read without.
            if (accessor === undefined) return linkColorAccessor;
            linkColorAccessor = accessor;
            return this;
        }
        linkWidth(accessor) {
            if (accessor === undefined) return linkWidthAccessor;
            linkWidthAccessor = accessor;
            return this;
        }
        linkDirectionalParticles(accessor) {
            if (accessor === undefined) return particlesAccessor;
            particlesAccessor = accessor;
            return this;
        }
        linkDirectionalParticleWidth(value) {
            calls.push(['particleWidth', value]);
            return this;
        }
        onNodeHover(handler) {
            hoverHandlers.node = handler;
            return this;
        }
        onLinkHover(handler) {
            hoverHandlers.link = handler;
            return this;
        }
    }

    const window = {
        graphlibDot: {read: () => graphlibGraph},
        ForceGraph3D: FakeForceGraph3D,
        SpriteText: function (label) {
            return makeSprite(label);
        },
        RF: {},
        setTimeout: (handler) => {
            handler();
            return 1;
        },
        clearTimeout: () => {},
    };
    return {
        window, calls, hoverHandlers, spriteByLabel, makeSprite,
        get linkColorAccessor() {
            return linkColorAccessor;
        },
        get linkWidthAccessor() {
            return linkWidthAccessor;
        },
        get particlesAccessor() {
            return particlesAccessor;
        },
        get nodeThreeObjectAccessor() {
            return nodeThreeObjectAccessor;
        },
    };
}

async function loadAndCreate(h, dot) {
    global.window = h.window;
    global.document = {
        getElementById: (id) =>
            id === 'overlay' || id === 'popup-x'
                ? {style: {}}
                : {clientWidth: 800, clientHeight: 600},
    };
    for (const asset of ['rf-core.js', 'rf-theme.js', 'rf-charts.js', 'rf-graphs-3d.js']) {
        new Function(readFileSync(resolve(ASSETS_DIR, asset), 'utf8'))();
    }
    await h.window.RF.createForceGraph('popup-x', 'graph-container-x', dot);
    return h.window.RF;
}

test('node/link arrays derive from the DOT with subgraph nodes dropped and endpoints resolved', async () => {
    const h = harness();
    const RF = await loadAndCreate(h, DOT);

    const graphData = h.calls.find(call => call[0] === 'graphData')[1];
    assert.deepEqual(
        graphData.nodes.map(node => node.id),
        ['A', 'B'],
        'the subgraph node is dropped, its children kept');
    assert.deepEqual(
        graphData.links.map(link => [link.sourceNode.id, link.targetNode.id]),
        [['A', 'B']],
        'link endpoints are resolved node references (sourceNode/targetNode), not ids');
    assert.equal(graphData.links[0].color, 'red', 'the DOT edge color rides along');
    assert.equal(graphData.nodes.find(node => node.id === 'A').color, 'red', 'the DOT node color rides along');
    assert.equal(h.calls.find(call => call[0] === 'nodeLabel')[1], 'label');
    assert.equal(h.calls.find(call => call[0] === 'particleWidth')[1], 4);
});

test('hovering a node flags the directional neighborhood; sprites recolor O(changed)', async () => {
    const h = harness();
    await loadAndCreate(h, DOT);

    const graphData = h.calls.find(call => call[0] === 'graphData')[1];
    const nodeA = graphData.nodes.find(node => node.id === 'A');
    const nodeB = graphData.nodes.find(node => node.id === 'B');
    const link = graphData.links[0];

    // ForceGraph3D renders each node through nodeThreeObject; the stub simulates
    // that pass so the sprite registry is populated like in the browser.
    h.nodeThreeObjectAccessor(nodeA);
    h.nodeThreeObjectAccessor(nodeB);

    // Hover A: A takes the amber focus accent, B (the sink) goes blue.
    h.hoverHandlers.node(nodeA);
    assert.equal(h.spriteByLabel.A.color, '#f0b95a', 'the hovered node fills amber');
    assert.equal(h.spriteByLabel.B.color, '#8ed0fa', 'the sink node fills blue');
    assert.equal(h.linkColorAccessor(link), '#8ed0fa', 'the outgoing link fills blue');
    assert.equal(h.linkWidthAccessor(link), 4, 'flagged links thicken to 4');
    assert.equal(h.particlesAccessor(link), 4, 'flagged links animate 4 directional particles');

    // Hover nothing: DOT's own colors show again.
    h.hoverHandlers.node(null);
    assert.equal(h.spriteByLabel.A.color, 'red', 'the DOT node color returns');
    assert.equal(h.linkColorAccessor(link), 'red', 'the DOT link color returns');
    assert.equal(h.particlesAccessor(link), 0, 'unflagged links carry no particles');
});

test('hovering a link flags it and both endpoints amber', async () => {
    const h = harness();
    await loadAndCreate(h, DOT);

    const graphData = h.calls.find(call => call[0] === 'graphData')[1];
    const nodeA = graphData.nodes.find(node => node.id === 'A');
    const nodeB = graphData.nodes.find(node => node.id === 'B');
    const link = graphData.links[0];

    h.nodeThreeObjectAccessor(nodeA);
    h.nodeThreeObjectAccessor(nodeB);

    h.hoverHandlers.link(link);
    assert.equal(h.spriteByLabel.A.color, '#f0b95a');
    assert.equal(h.spriteByLabel.B.color, '#f0b95a');
    assert.equal(h.linkColorAccessor(link), '#f0b95a');

    h.hoverHandlers.link(null);
    assert.equal(h.spriteByLabel.A.color, 'red');
});

test('below the 4000-element threshold the sprite labels load and render', async () => {
    const h = harness();
    await loadAndCreate(h, DOT);
    const graphData = h.calls.find(call => call[0] === 'graphData')[1];
    const nodeA = graphData.nodes.find(node => node.id === 'A');

    const sprite = h.nodeThreeObjectAccessor(nodeA);
    assert.equal(sprite.label, 'A', 'the sprite label carries the node label');
    assert.equal(sprite.textHeight, 4);
    assert.equal(sprite.material.depthWrite, false);
});
