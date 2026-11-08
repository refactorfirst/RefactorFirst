const assert = require('node:assert/strict');
const { readFileSync } = require('node:fs');
const { resolve } = require('node:path');
const { test } = require('node:test');

// T7.1: rf-hierarchy-dot.js on the parity fixture's inline hierarchies must be
// byte-identical to the Java NestedDotBuilder goldens (light); the dark-mode
// variant equals the light output plus the baked fontcolor="#9fb0c0" edge
// labels; unsafe ids are rejected; the repoUrl hyperlinks stay http(s)-gated.
// This test IS the AGENTS.md three-implementations constraint reduced to two
// (Java golden + this JS renderer) and cross-pinned.

const ASSETS_DIR = resolve(__dirname, '../../main/resources/assets');
const GOLDEN_DIR = resolve(__dirname, '../resources/golden/hierarchy-dot');
const FIXTURE = JSON.parse(
    readFileSync(resolve(__dirname, '../resources/parity/junit4-report.json'), 'utf8'));

function loadRfHierarchyDot() {
    const window = {RF: {}};
    global.window = window;
    new Function(readFileSync(resolve(ASSETS_DIR, 'rf-core.js'), 'utf8'))();
    new Function(readFileSync(resolve(ASSETS_DIR, 'rf-hierarchy-dot.js'), 'utf8'))();
    return window.RF;
}

const DARK_FONT = '#9fb0c0';

test('classMap synthesis is byte-identical to the Java NestedDotBuilder golden', () => {
    const RF = loadRfHierarchyDot();
    const hierarchy = FIXTURE.classMap.hierarchy;
    const repoUrl = FIXTURE.project.repoUrl;

    const dot = RF.hierarchyToDot(hierarchy, {repoUrl});
    const golden = readFileSync(resolve(GOLDEN_DIR, 'classMap.dot'), 'utf8');
    assert.equal(dot, golden.replace(/\r\n/g, '\n'));
});

test('packageMap synthesis is byte-identical to the Java buildPackageDot golden', () => {
    const RF = loadRfHierarchyDot();
    const hierarchy = FIXTURE.packageMap.hierarchy;
    const repoUrl = FIXTURE.project.repoUrl;

    const dot = RF.packageHierarchyToDot(hierarchy, {repoUrl});
    const golden = readFileSync(resolve(GOLDEN_DIR, 'packageMap.dot'), 'utf8');
    assert.equal(dot, golden.replace(/\r\n/g, '\n'));
});

test('cycleMap synthesis is byte-identical to the Java NestedDotBuilder golden', () => {
    const RF = loadRfHierarchyDot();
    const hierarchy = FIXTURE.classCycles.largestCycle.hierarchy;
    const repoUrl = FIXTURE.project.repoUrl;

    const dot = RF.hierarchyToDot(hierarchy, {repoUrl});
    const golden = readFileSync(resolve(GOLDEN_DIR, 'cycleMap.dot'), 'utf8');
    assert.equal(dot, golden.replace(/\r\n/g, '\n'));
});

test('dark-mode synthesis equals light plus the baked dark edge-label color', () => {
    const RF = loadRfHierarchyDot();
    const hierarchy = FIXTURE.classMap.hierarchy;
    const repoUrl = FIXTURE.project.repoUrl;

    const light = RF.hierarchyToDot(hierarchy, {repoUrl});
    const dark = RF.hierarchyToDot(hierarchy, {repoUrl, edgeFontColor: DARK_FONT});

    const lightLines = light.split('\n');
    const darkLines = dark.split('\n');
    assert.equal(lightLines.length, darkLines.length);
    let fontcolored = 0;
    for (let i = 0; i < lightLines.length; i++) {
        if (lightLines[i].includes('->')) {
            assert.ok(
                darkLines[i].includes(`fontcolor = "${DARK_FONT}"`),
                'every edge carries the dark fontcolor: ' + darkLines[i]);
            fontcolored++;
            assert.equal(
                darkLines[i].replace(` fontcolor = "${DARK_FONT}"`, ''),
                lightLines[i],
                'the edge line is otherwise identical');
        } else {
            assert.equal(darkLines[i], lightLines[i], 'non-edge lines unchanged');
        }
    }
    assert.ok(fontcolored > 0, 'the fixture graph has edges');
});

test('out-of-contract ids and edges are dropped, not emitted raw', () => {
    const RF = loadRfHierarchyDot();
    const hostile = {
        packages: [
            {
                name: 'evil" package',
                id: 'cluster_not"quoted', // breaks out of the subgraph header
                renderAsNode: false,
                needsBreakUp: false,
                classes: [{id: 'class_ok', label: 'ok', markedForRemoval: false}],
                children: [],
            },
        ],
        edges: [
            {source: 'class_ok', target: 'class_ok" [label="injected"]', weight: 1, markedForRemoval: false},
            {source: 'class_ok', target: 'class_ok', weight: Number.NaN, markedForRemoval: false},
            {source: 'class_ok', target: 'class_ok', weight: 1, markedForRemoval: true},
        ],
    };
    const dot = RF.hierarchyToDot(hostile, {});
    assert.ok(!dot.includes('not"quoted'), 'the hostile cluster id must be dropped: ' + dot);
    assert.ok(!dot.includes('injected'), 'the hostile edge endpoint must be dropped');
    assert.ok(!dot.includes('NaN'), 'a non-finite weight must drop the edge');
    assert.ok(dot.includes('class_ok -> class_ok [ label = "1" weight = "1" color = "red" ]'), dot);
});

test('non-http repoUrls never become hyperlinks', () => {
    const RF = loadRfHierarchyDot();
    const hierarchy = {
        packages: [
            {
                name: 'p',
                id: 'cluster_p',
                renderAsNode: false,
                needsBreakUp: false,
                classes: [{id: 'class_a', label: 'a', path: '/src/A.java', markedForRemoval: false}],
                children: [],
            },
        ],
        edges: [],
    };
    const evil = RF.hierarchyToDot(hierarchy, {repoUrl: 'javascript:alert(1)'});
    assert.ok(!evil.includes('javascript:'), evil);
    const missing = RF.hierarchyToDot(hierarchy, {repoUrl: ''});
    assert.ok(!missing.includes('URL='), missing);
    const https = RF.hierarchyToDot(hierarchy, {repoUrl: 'https://github.com/x/blob'});
    assert.ok(https.includes('URL="https://github.com/x/blob/src/A.java"'), https);
});
