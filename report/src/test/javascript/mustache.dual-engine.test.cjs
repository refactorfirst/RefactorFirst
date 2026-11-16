const assert = require('node:assert/strict');
const { readFileSync } = require('node:fs');
const { resolve } = require('node:path');
const { test } = require('node:test');

const Mustache = require('./vendor/mustache.js');

const TEMPLATE_PATH = resolve(__dirname, '../../main/resources/templates/refactor-first-report.mustache');
const GOLDEN_PATH = resolve(__dirname, '../../test/resources/golden/mustache/refactor-first-report.mustache.html');

function loadTemplate() {
    return readFileSync(TEMPLATE_PATH, 'utf8');
}

function escapeHtml(string) {
    return String(string)
        .replace(/&/g, '&amp;')
        .replace(/</g, '&lt;')
        .replace(/>/g, '&gt;')
        .replace(/"/g, '&quot;')
        .replace(/'/g, '&#39;');
}

function loadGolden() {
    return readFileSync(GOLDEN_PATH, 'utf8');
}

function fixtureView() {
    const project = {
        name: 'TestProject',
        version: '1.0.0',
        repoUrl: 'https://github.com/test/test/blob/',
        baseDir: '/test',
        scanTimestamp: '9/8/26, 7:34 PM',
        hasAnyDisharmony: true,
        analysisFailed: false,
    };
    const classMap = {
        graphId: 'classGraph',
        classCount: 10,
        relationshipCount: 20,
        dotThreshold: 4000,
        dotThresholdExceeded: false,
        hasEdges: false,
    };
    const classRelationshipsToRemove = {
        cycleCount: 1,
        relationshipsToRemoveCount: 1,
        hasRelationships: true,
        relationships: [
            {
                sourceClass: 'com.example.A',
                targetClass: 'com.example.B',
                sourceClassPath: 'src/main/java/com/example/A.java',
                targetClassPath: 'src/main/java/com/example/B.java',
                simpleSourceClassName: 'A',
                simpleTargetClassName: 'B',
                sourceMarked: true,
                targetMarked: false,
                weight: 5,
                priority: 1,
                cycleCount: 3,
                effortRank: 2,
                alsoRemovesPackageRelationship: false,
                packageCycleCount: 0,
            },
        ],
    };
    const packageMap = {
        graphId: 'packageGraph',
        classCount: 0,
        relationshipCount: 0,
        dotThreshold: 4000,
        dotThresholdExceeded: false,
        hasEdges: false,
    };
    const packageRelationshipsToRemove = {
        cycleCount: 0,
        relationshipsToRemoveCount: 0,
        hasRelationships: false,
        relationships: [],
    };
    const disharmonies = [
        {
            type: 'God Class',
            anchorId: 'GOD',
            title: 'God Classes',
            methodLevel: false,
            problem: 'God Classes take on too much responsibility',
            solution: 'Extract related functionality',
            maxPriority: 5,
            chart: {
                canvasId: 'chart_GOD',
                xAxisLabel: 'Effort to refactor',
                yAxisLabel: 'Relative churn',
                bubbles: [
                    {
                        id: 'TestClass',
                        label: 'TestClass.java',
                        x: 5,
                        y: 10,
                        r: 24,
                        priority: 1,
                        effortRank: 5,
                        changePronenessRank: 10,
                    },
                ],
            },
            table: {
                headers: ['Class', 'Priority'],
                rows: [
                    {
                        cells: [
                            { content: 'TestClass.java', path: 'src/main/java/TestClass.java', align: 'left' },
                            { content: '1', align: 'right' },
                        ],
                    },
                ],
            },
        },
    ];
    const classCycles = {
        hasCycles: true,
        summary: [{ cycleName: 'A -> B -> C -> A', priority: 1, classCount: 3, relationshipCount: 3 }],
        largestCycle: {
            hasCycleMap: true,
            cycleName: 'A -> B -> C -> A',
            cycleIdentifier: 'graph_cycle',
            classCount: 2,
            relationshipCount: 2,
            breakdown: [
                { className: 'ClassA', classPath: 'src/main/java/ClassA.java', marked: true, edgesHtml: 'A &#8594; B : 1' },
            ],
        },
    };
    return {
        project,
        classMap,
        classRelationshipsToRemove,
        packageMap,
        packageRelationshipsToRemove,
        hasDisharmonies: true,
        disharmonies,
        classCycles,
        // The rf-data JSON block and inlined-asset markers are pipeline-provided;
        // the fixture pins inert placeholder strings matching DualEngineGoldenTest.
        rfDataBlock: '{"project":{"name":"TestProject"},"graphs":{"classGraph":"digraph {}"}}',
    };
}

// T2.3: cross-language dual-engine golden — the Java mustache engine and the vendored
// mustache.js must render the same fixture view model to the same bytes.
test('mustache.js renders the fixture view model byte-identically to the Java golden', () => {
    const rendered = Mustache.render(loadTemplate(), fixtureView(), undefined, { escape: escapeHtml });
    assert.equal(rendered, loadGolden());
});

// P3/T3.4 subset addition: implicit iterator ({{.}}) over string lists and parent-scope
// dotted lookups — the simple-report template's table subset — must render identically
// to the Java engine (TemplateEngineTest.rendersImplicitIteratorsAndParentScopeDottedLookups).
test('mustache.js renders the implicit-iterator subset like the Java engine', () => {
    const view = {
        project: { repoUrl: 'https://github.com/test/test/blob/' },
        headers: ['Class', 'Priority'],
        rows: [{ path: 'src/main/java/TestClass.java' }],
    };
    const template =
        '{{#headers}}<th scope="col">{{.}}</th>{{/headers}}' +
        '{{#rows}}{{#path}}<a href="{{project.repoUrl}}{{path}}">{{/path}}x{{#path}}</a>{{/path}}{{/rows}}';
    assert.equal(
        Mustache.render(template, view, undefined, { escape: escapeHtml }),
        '<th scope="col">Class</th><th scope="col">Priority</th>' +
            '<a href="https://github.com/test/test/blob/src/main/java/TestClass.java">x</a>');
});

test('simple cycle navigation follows the availability of its section targets', () => {
    // Given cycles with and without a renderable map
    const template = readFileSync(resolve(__dirname,
        '../../main/resources/templates/refactor-first-simple-report.mustache'), 'utf8');
    for (const hasCycles of [false, true]) {
        for (const hasCycleMap of [false, true]) {
            // When rendering the simple report
            const html = Mustache.render(template, {project: {hasAnyDisharmony: true},
                classCycles: {hasCycles, largestCycle: {hasCycleMap}}});
            // Then each link exists exactly when its target section exists
            assert.equal(html.includes('href="#CYCLES"'), hasCycles);
            assert.equal(html.includes('href="#CYCLEMAP"'), hasCycles && hasCycleMap);
        }
    }
});
