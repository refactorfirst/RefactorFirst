const assert = require('node:assert/strict');
const { readFileSync } = require('node:fs');
const { test } = require('node:test');
const vm = require('node:vm');

const viewer = readFileSync(`${__dirname}/../../main/resources/viewer/index.html`, 'utf8');
const scripts = [...viewer.matchAll(/<script(?:\s[^>]*)?>([\s\S]*?)<\/script>/g)]
    .map(match => match[1].replace(/^\s*import .*;$/gm, ''));
const synthesis = scripts.find(script => script.includes('function hierarchyToDot'))
    .split('        async function loadReport()')[0];
const builders = vm.runInNewContext(`${synthesis}\n({ hierarchyToDot, packageHierarchyToDot })`, { URL });

for (const builder of ['renderGraph', 'createForceGraph']) {
    test(`${builder} keeps cluster-prefixed classes and filters subgraphs and their edges`, () => {
        // Given: nested subgraphs, including one without a cluster_ prefix, and class leaves.
        const children = { packageScope: ['cluster_nested'], cluster_nested: ['cluster_Helper', 'Other'] };
        const edges = [
            { v: 'cluster_Helper', w: 'Other' },
            { v: 'packageScope', w: 'Other' },
            { v: 'Other', w: 'cluster_nested' },
        ];
        const fixture = {
            nodes: () => ['packageScope', 'cluster_nested', 'cluster_Helper', 'Other'],
            children: node => children[node] || [],
            node: node => ({ label: node }),
            edges: () => edges,
            edge: () => ({ weight: 1 }),
        };
        let result;
        class Graph {
            constructor() { this.nodes = []; this.edges = []; result = this; }
            addNode(id) { this.nodes.push({ id }); }
            addEdge(source, target) { this.edges.push({ source, target }); }
        }
        const forceGraph = new Proxy({}, {
            get: (_, method) => value => {
                if (method === 'graphData') result = value;
                return forceGraph;
            },
        });
        const context = vm.createContext({
            graphlibDot: { read: () => fixture },
            graphology: { Graph },
            document: { addEventListener() {}, getElementById: () => ({ style: {} }) },
            ForceGraph3D: function () { return forceGraph; },
            window: {},
            console: { log() {} },
        });
        const script = scripts.find(source => source.includes(`function ${builder}(`));
        vm.runInContext(script, context);
        context.sugiyamaLayout = () => {};

        // When: the actual popup builder consumes the parsed compound graph.
        vm.runInContext(builder === 'renderGraph'
            ? "renderGraph('fixture')" : "createForceGraph('popup', 'container', 'fixture')", context);

        // Then: class nodes and their relationship survive, with no subgraph artifacts.
        assert.deepEqual(Array.from(result.nodes, node => node.id), ['cluster_Helper', 'Other']);
        assert.deepEqual(Array.from(result.edges || result.links, edge => [edge.source, edge.target]),
            [['cluster_Helper', 'Other']]);
    });
}

const unsafeIds = [
    undefined, null, 17, {}, '', '17Class', 'bad-id', 'bad.id', 'bad id',
    'A; injected -> B', 'A"', 'A\\B', '<A>', 'A\n', 'A\r',
    'graph', 'DiGraph', 'node', 'edge', 'strict', 'subgraph',
];

test('nested synthesis rejects unsafe cluster, class, package-node and edge IDs', () => {
    for (const id of unsafeIds) {
        // Given: a valid sibling next to an unsafe identifier at each interpolation site.
        const good = { id: 'cluster_good', name: 'good', classes: [{ id: 'cluster_Helper', label: 'Helper' }] };
        const cases = [
            { packages: [{ id, name: 'bad' }, good] },
            { packages: [{ ...good, classes: [{ id, label: 'bad' }, ...good.classes] }] },
            { packages: [{ ...good, renderAsNode: true, nodeId: id }] },
            { packages: [good], edges: [{ source: id, target: 'cluster_Helper', weight: 1 }] },
            { packages: [good], edges: [{ source: 'cluster_Helper', target: id, weight: 1 }] },
        ];
        for (const hierarchy of cases) {
            // When / Then: invalid entries are omitted without affecting valid output.
            assert.equal(builders.hierarchyToDot(hierarchy), builders.hierarchyToDot({ packages: [good] }),
                `unsafe identifier: ${String(id)}`);
        }
    }
});

test('flat package synthesis rejects unsafe node and edge IDs and still visits children', () => {
    const good = { nodeId: 'package_good', name: 'good' };
    for (const id of unsafeIds) {
        // Given: a bad parent node with a valid child and bad edge endpoints.
        for (const edge of [{ source: id, target: 'package_good' }, { source: 'package_good', target: id }]) {
            const hierarchy = { packages: [{ nodeId: id, children: [good] }], edges: [{ ...edge, weight: 1 }] };
            // When / Then: both unsafe node and edge IDs are omitted; the child survives.
            assert.equal(builders.packageHierarchyToDot(hierarchy),
                builders.packageHierarchyToDot({ packages: [good] }), `unsafe identifier: ${String(id)}`);
        }
    }
});

test('valid DOT output keeps labels, styling, hyperlinks and weighted edges byte for byte', () => {
    // Given: representative pre-resolved identifiers with removal styling and escaped labels.
    const hierarchy = {
        packages: [{
            id: 'cluster_abc123', nodeId: 'package_abc123', name: 'a"b', path: 'src/',
            renderAsNode: true, needsBreakUp: true,
            classes: [{ id: 'cluster_Helper', label: 'Helper$Inner', markedForRemoval: true, path: 'src/Helper.java' }],
            children: [{ id: 'cluster_child', name: 'child', needsBreakUp: true, classes: [{ id: '_Other9', label: '_Other9' }] }],
        }],
        edges: [{ source: 'cluster_Helper', target: '_Other9', weight: 2.5, markedForRemoval: true }],
    };
    const options = { repoUrl: 'https://example.com/' };
    // When / Then: valid data retains the original synthesis output exactly.
    assert.equal(builders.hierarchyToDot(hierarchy, options), [
        'strict digraph G {', 'subgraph cluster_abc123 {', 'label="a\\"b";',
        'style=filled fillcolor=white;', 'package_abc123 [label="a\\"b" shape=rectangle color=pink style=filled];',
        'cluster_Helper [ label="Helper$Inner" shape=ellipse color=red style=filled URL="https://example.com/src/Helper.java" target="_blank"];',
        'subgraph cluster_child {', 'label="child";', 'style=filled fillcolor=pink;',
        '_Other9 [ shape=ellipse];', '}', '}',
        'cluster_Helper -> _Other9 [ label = "2.5" weight = "2.5" color = "red" ];', '}',
    ].join('\n'));
    assert.equal(builders.packageHierarchyToDot({ ...hierarchy, edges: [
        { source: 'package_abc123', target: 'package_abc123', weight: 2.5, markedForRemoval: true },
    ] }, options), [
        'strict digraph G {',
        'package_abc123 [label="a\\"b" shape=ellipse color=red style=filled URL="https://example.com/src/" target="_blank"];',
        'package_abc123 -> package_abc123 [ label = "2.5" weight = "2.5" color = "red" ];', '}',
    ].join('\n'));
});

test('edge weights are escaped as DOT quoted strings in both attributes', () => {
    // Given: a weight containing characters that must be escaped in a DOT string.
    const hierarchy = {
        edges: [{ source: 'A', target: 'B', weight: '2"\\\n' }],
    };

    // When: the hierarchy is synthesized.
    const dot = builders.hierarchyToDot(hierarchy);

    // Then: the same escaped weight is used for both the label and weight attributes.
    assert.ok(dot.includes('label = "2\\"\\\\\\n" weight = "2\\"\\\\\\n"'));
});
