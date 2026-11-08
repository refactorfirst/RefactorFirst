const assert = require('node:assert/strict');
const { readFileSync } = require('node:fs');
const { resolve } = require('node:path');
const { test } = require('node:test');

// T4.4: every classpath report asset parses as JavaScript and, together with the
// template, produces an inlined page whose script bodies still parse after the
// MinifyHtml (setMinifyJs) round trip. The E2E minified leg exercises execution.

const ASSETS_DIR = resolve(__dirname, '../../main/resources/assets');
const JS_ASSETS = [
    'rf-core.js',
    'rf-theme.js',
    'rf-hierarchy-dot.js',
    'rf-tables.js',
    'rf-csv.js',
    'rf-copy.js',
    'rf-charts.js',
    'rf-graphs-sigma.js',
    'rf-graphs-3d.js',
    'rf-graphs-vizdom.js',
    'rf-main.js',
];

test('every report asset parses as a classic script', () => {
    for (const asset of JS_ASSETS) {
        const source = readFileSync(resolve(ASSETS_DIR, asset), 'utf8');
        // new Function throws on syntax errors without executing the body.
        assert.doesNotThrow(() => new Function(source), `${asset} must parse as JavaScript`);
    }
});

test('rf-core exposes the boot contract the other assets rely on', () => {
    const core = readFileSync(resolve(ASSETS_DIR, 'rf-core.js'), 'utf8');
    assert.ok(core.includes('window.RF = RF'), 'rf-core must publish the RF namespace');
    assert.ok(core.includes('readRfData'), 'rf-core must read the rf-data block');
    assert.ok(core.includes('graphDot'), 'rf-core must resolve template dot-var buttons');
});

test('rf-graphs-vizdom restores the new-tab anchor target on every hyperlinked anchor', () => {
    const vizdom = readFileSync(resolve(ASSETS_DIR, 'rf-graphs-vizdom.js'), 'utf8');
    assert.ok(
        vizdom.includes("svg.replaceAll('<a href=', '<a target=\"_blank\" href=')"),
        'vizdom drops the target attribute; the render must restore it globally');
    assert.ok(!/svg\.replace\('<a href='/m.test(vizdom), 'a non-global replacement would miss later anchors');
});

test('rf-graphs drop subgraphs by parsed structure, not id prefix', () => {
    // graphlib-dot parents every node a subgraph contains to the subgraph's own node, so a
    // node is a subgraph iff it has children — never by the cluster_ id prefix (class ids
    // from existing payloads like cluster_Helper would be dropped with their edges).
    const sigma = readFileSync(resolve(ASSETS_DIR, 'rf-graphs-sigma.js'), 'utf8');
    const threeD = readFileSync(resolve(ASSETS_DIR, 'rf-graphs-3d.js'), 'utf8');
    const structuralFilters = sigma.split('graphlibGraph.children(node).length > 0').length - 1
        + threeD.split('graphlibGraph.children(node).length > 0').length - 1;
    assert.equal(structuralFilters, 2, 'both the 2D and 3D builders must drop subgraph nodes by parsed structure');
    assert.ok(!sigma.includes("startsWith('cluster_')"), 'the cluster_ id prefix must not filter nodes');
    assert.ok(!threeD.includes("startsWith('cluster_')"), 'the cluster_ id prefix must not filter nodes');
    const edgeGuards = sigma.split('droppedSubgraphs.has').length - 1
        + threeD.split('droppedSubgraphs.has').length - 1;
    assert.equal(edgeGuards, 4, 'both builders must guard edges against dropped subgraph nodes');
});

test('rf-main boots defensively and exposes the popup globals', () => {
    const main = readFileSync(resolve(ASSETS_DIR, 'rf-main.js'), 'utf8');
    assert.ok(main.includes('window.showPopup = RF.showPopup'));
    assert.ok(main.includes('window.RF'), 'rf-main must publish the RF namespace usage');
    assert.ok(main.includes("event.key === 'Escape'"), 'Escape closes the popup');
});

test('rf-charts builds the page-repo legend labels and axis arrow', () => {
    const charts = readFileSync(resolve(ASSETS_DIR, 'rf-charts.js'), 'utf8');
    assert.ok(charts.includes("'High Priority (1)'"));
    assert.ok(charts.includes("'Medium Priority'"));
    assert.ok(charts.includes("'Low Priority (max)'"));
    assert.ok(charts.includes("'rgb(235, 64, 52)'"));
    assert.ok(charts.includes("'rgb(137, 119, 74)'"));
    assert.ok(charts.includes("'rgb(39, 174, 96)'"));
    assert.ok(charts.includes("grid: {color: '#e0e0e0'}"));
    assert.ok(charts.includes("|| 'Effort to refactor'"), 'x-axis fallback label');
    assert.ok(charts.includes("|| 'Relative churn (impact)'"), 'y-axis fallback label');
    assert.ok(charts.includes('Click to open source file'), 'tooltip click prompt');
    assert.ok(charts.includes("'noopener'"), 'click opens with noopener');
});
