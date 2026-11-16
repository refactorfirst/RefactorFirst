const assert = require('node:assert/strict');
const { readFileSync } = require('node:fs');
const { resolve } = require('node:path');
const { test } = require('node:test');

// T5.3: the theme redraw hooks — rf-charts.js redrawChartsForTheme re-reads the
// legend/axis text colors on live charts; rf-graphs-vizdom.js redrawGraphsForTheme
// re-synthesizes DOT via rf-hierarchy-dot.js with the dark edge-label option
// (fontcolor = "#9fb0c0") and re-renders; sigma popups rebuild on open with
// sigmaPopupSettings() (dark: labelColor + drawDarkHover, hover bg #1a2330); the 3D
// popup always uses the dark accent set (its WebGL canvas paints its own background).

const ASSETS_DIR = resolve(__dirname, '../../main/resources/assets');
const HIERARCHY = {
    packages: [
        {
            name: 'com.example',
            id: 'cluster_abc',
            nodeId: null,
            renderAsNode: false,
            needsBreakUp: false,
            classes: [{id: 'class_A', label: 'A', path: '/src/A.java', markedForRemoval: false}],
            children: [],
        },
    ],
    edges: [{source: 'class_A', target: 'class_B', weight: 2, markedForRemoval: false}],
};

/** DOM/window harness with the theme radios and a Vizdom DOT capture. */
function harness() {
    const radios = {};
    for (const mode of ['light', 'dark', 'system']) {
        radios[mode] = {id: `rf-theme-${mode}`, checked: mode === 'system', name: 'rf-theme', value: mode};
    }
    const listeners = {};
    const store = new Map();
    const parsedDots = [];

    const window = {
        localStorage: {
            getItem: (key) => (store.has(key) ? store.get(key) : null),
            setItem: (key, value) => store.set(key, value),
        },
        matchMedia: (query) => ({
            addEventListener: (type, callback) => {
                window.__systemChange = callback;
            },
        }),
        Vizdom: {
            DotParser: function () {
                this.parse = (dot) => {
                    parsedDots.push(dot);
                    return {
                        to_directed: () => ({
                            layout: () => ({
                                to_svg: () => ({to_string: () => '<svg><g class="node"></g></svg>'}),
                            }),
                        }),
                    };
                };
            },
        },
    };
    const containers = {};
    const document = {
        documentElement: {hasAttribute: () => false, setAttribute: () => {}},
        getElementById: (id) => {
            const mode = id.replace('rf-theme-', '');
            if (radios[mode]) return radios[mode];
            return containers[id] || null;
        },
        addEventListener: (type, handler) => {
            (listeners[type] = listeners[type] || []).push(handler);
        },
    };
    // Graph containers need just enough DOM surface for the render + hover binding.
    const makeContainer = () => ({
        isConnected: true,
        innerHTML: '',
        querySelector: () => null,
        querySelectorAll: () => [],
    });
    return {
        document, window, radios, parsedDots, containers, makeContainer,
        fireChange(target) {
            (listeners.change || []).forEach(handler => handler({target}));
        },
        selectTheme(mode) {
            for (const key of Object.keys(radios)) radios[key].checked = key === mode;
        },
    };
}

function loadReportModules(h) {
    global.DOMParser = class {
        parseFromString(markup) {
            return {documentElement: {localName: 'svg', namespaceURI: 'http://www.w3.org/2000/svg',
                querySelectorAll: () => [], markup}, querySelector: () => null};
        }
    };
    global.XMLSerializer = class { serializeToString(root) { return root.markup; } };
    global.window = h.window;
    global.document = h.document;
    h.window.RF = {};
    for (const asset of ['rf-core.js', 'rf-theme.js', 'rf-hierarchy-dot.js', 'rf-charts.js', 'rf-graphs-sigma.js', 'rf-graphs-3d.js', 'rf-graphs-vizdom.js']) {
        new Function(readFileSync(resolve(ASSETS_DIR, asset), 'utf8'))();
    }
    h.window.RF.theme.restore();
    return h.window.RF;
}

test('redrawChartsForTheme updates live charts and prunes detached ones', () => {
    const h = harness();
    const RF = loadReportModules(h);

    const updates = [];
    const destroys = [];
    const connected = {isConnected: true, getContext: () => ({})};
    const detached = {isConnected: false, getContext: () => ({})};

    window.Chart = function FakeChart(context, config) {
        this.update = () => updates.push(this);
        this.destroy = () => destroys.push(this);
        this.config = config;
    };

    RF.initBubbleChart(connected, 'God Classes', {bubbles: []}, 'https://repo/');
    RF.initBubbleChart(detached, 'Data Classes', {bubbles: []}, 'https://repo/');
    assert.equal(updates.length, 0);

    RF.redrawCharts();
    assert.equal(updates.length, 1, 'the live chart updates on redraw');
    assert.equal(destroys.length, 1, 'the pre-detached chart is destroyed and pruned');

    RF.redrawCharts();
    assert.equal(updates.length, 2, 'the connected chart updates again');
    assert.equal(destroys.length, 1, 'no additional destroy churn');
});

test('chart text colors re-resolve per theme', () => {
    const h = harness();
    const RF = loadReportModules(h);
    assert.equal(RF.resolveLegendTextColor(), '#5c6b7a', 'light legend text');

    h.selectTheme('dark');
    assert.equal(RF.activeChartTheme(), 'dark');
    assert.equal(RF.resolveLegendTextColor(), '#9fb0c0', 'dark legend text');
});

test('redrawGraphsForTheme re-synthesizes dark DOT with the edge fontcolor and re-renders', () => {
    const h = harness();
    const RF = loadReportModules(h);

    const container = h.makeContainer();
    h.containers.classGraph = container;

    const data = {
        project: {repoUrl: 'https://github.com/test/test/blob/'},
        classMap: {dotThresholdExceeded: false, hierarchy: HIERARCHY},
    };
    RF.initWasmGraphs(data);
    assert.equal(h.parsedDots.length, 1);
    const initialDot = h.parsedDots[0];
    assert.ok(!initialDot.includes('fontcolor'), 'the initial render carries no fontcolor');

    // Switch to dark: the redraw re-synthesizes from the hierarchy with the
    // dark edge-label color baked in.
    h.selectTheme('dark');
    RF.redrawGraphs();

    assert.equal(h.parsedDots.length, 2, 'the graph re-rendered on theme change');
    const redrawnDot = h.parsedDots[1];
    assert.ok(
        redrawnDot.includes('fontcolor = "#9fb0c0"'),
        'the dark redraw bakes the muted edge-label color: ' + redrawnDot);
    assert.ok(redrawnDot.includes('class_A -> class_B'), redrawnDot);
    assert.equal(redrawnDot, RF.hierarchyToDot(HIERARCHY, {edgeFontColor: '#9fb0c0', repoUrl: 'https://github.com/test/test/blob/'}));
    assert.ok(container.innerHTML.includes('<svg'), 'the container re-rendered');

    // Back to light: the redraw re-synthesizes without the fontcolor.
    h.selectTheme('light');
    RF.redrawGraphs();
    assert.equal(h.parsedDots.length, 3);
    assert.ok(!h.parsedDots[2].includes('fontcolor'), 'the light redraw keeps vizdom defaults');
});

test('a payload without a hierarchy renders no graph (the Java-DOT bridge is gone)', () => {
    const h = harness();
    const RF = loadReportModules(h);

    const container = h.makeContainer();
    h.containers.classGraph = container;

    const data = {
        project: {repoUrl: 'https://github.com/test/test/blob/'},
        classMap: {dotThresholdExceeded: false, hierarchy: null},
    };
    RF.initWasmGraphs(data);
    assert.equal(h.parsedDots.length, 0, 'no hierarchy: nothing renders');
    assert.equal(container.innerHTML, '');

    h.selectTheme('dark');
    RF.redrawGraphs();
    assert.equal(h.parsedDots.length, 0, 'the redraw has nothing to re-render either');
});

test('sigma popups rebuild on open with the dark settings and hover renderer', () => {
    const h = harness();
    const RF = loadReportModules(h);

    // Light: sigma defaults stay untouched.
    h.selectTheme('light');
    assert.equal(RF.sigmaPopupSettings(), undefined);

    // Dark: labelColor + the dark hover renderer with the #1a2330 label box.
    h.selectTheme('dark');
    const settings = RF.sigmaPopupSettings();
    assert.deepEqual(settings.labelColor, {color: '#9fb0c0'});
    assert.equal(typeof settings.hoverRenderer, 'function');

    const fillStyles = [];
    const fakeContext = new Proxy({}, {
        get: (target, property) => {
            if (property === 'measureText') return () => ({width: 40});
            return (value) => {
                if (property === 'fillStyle') fillStyles.push(value);
            };
        },
        set: (target, property, value) => {
            if (property === 'fillStyle') fillStyles.push(value);
            return true;
        },
    });
    settings.hoverRenderer(
        fakeContext,
        {x: 10, y: 10, size: 5, label: 'NodeA'},
        {labelSize: 12, labelFont: 'sans-serif', labelWeight: '400', labelColor: {color: '#9fb0c0'}});
    assert.ok(
        fillStyles.includes('#1a2330'),
        'the dark hover renderer paints the dark popup surface label box: ' + JSON.stringify(fillStyles));
});

test('the 3D popup always uses the dark accent set; the 2D popup follows the palette', () => {
    const h = harness();
    const RF = loadReportModules(h);

    assert.deepEqual(RF.GRAPH_HOVER_ACCENTS.dark, {edge: '#f0b95a', in: '#7ee2a8', out: '#8ed0fa'});
    assert.deepEqual(RF.GRAPH_HOVER_ACCENTS.light, {edge: '#b45309', in: '#1e8449', out: '#2a6f97'});

    // The 3D builder hardcodes the dark set (its WebGL canvas paints its own
    // dark background in every theme).
    const graphsSource = readFileSync(resolve(ASSETS_DIR, 'rf-graphs-3d.js'), 'utf8');
    assert.ok(
        graphsSource.includes("const accents = {edge: '#f0b95a', in: '#7ee2a8', out: '#8ed0fa'};"),
        'createForceGraph must use the always-dark accents');
});

test('theme redraw destroys pan zoom before replacing SVG and prunes detached instances', () => {
    // Given: a rendered graph with a pan zoom instance.
    const h = harness();
    const RF = loadReportModules(h);
    const events = [];
    const el = h.makeContainer();
    Object.defineProperty(el, 'innerHTML', {set() { events.push('replace'); }});
    h.containers.classGraph = el;
    h.window.svgPanZoom = () => {
        events.push('create');
        return {destroy() { events.push('destroy'); }};
    };
    RF.initWasmGraphs({classMap: {hierarchy: HIERARCHY}});
    // When: multiple palette changes replace the SVG.
    RF.redrawGraphs();
    RF.redrawGraphs();
    // Then: each old instance is destroyed before replacement.
    assert.deepEqual(events, ['replace', 'create', 'destroy', 'replace', 'create', 'destroy', 'replace', 'create']);
    el.isConnected = false;
    RF.redrawGraphs();
    RF.redrawGraphs();
    assert.equal(events.filter(e => e === 'destroy').length, 3);
});

for (const failure of ['missing root', 'non-SVG root', 'parse error', 'sanitizer exception']) {
    test(`SVG rendering fails closed for ${failure}`, () => {
        const h = harness();
        const RF = loadReportModules(h);
        const container = h.makeContainer();
        container.innerHTML = 'static fallback';
        h.containers.classGraph = container;
        const failures = [];
        RF.announceWidgetFailure = widget => failures.push(widget);
        global.DOMParser = class {
            parseFromString() {
                const root = {localName: failure === 'non-SVG root' ? 'html' : 'svg',
                    namespaceURI: 'http://www.w3.org/2000/svg',
                    querySelectorAll() { throw new Error('sanitizer failure'); }};
                return {documentElement: failure === 'missing root' ? null : root,
                    querySelector: () => failure === 'parse error' ? {} : null};
            }
        };
        RF.initWasmGraphs({project: {}, classMap: {hierarchy: HIERARCHY}});
        assert.equal(container.innerHTML, 'static fallback');
        assert.deepEqual(failures, ['classGraph']);
    });
}
