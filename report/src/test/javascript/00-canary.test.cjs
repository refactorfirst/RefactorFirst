const assert = require('node:assert/strict');
const { readFileSync } = require('node:fs');
const { test } = require('node:test');
const vm = require('node:vm');

// P0/T0.2 harness canary: proves the Node built-in test runner can execute the
// vm-sandboxed script extraction used by the report JS test suite (the same
// pattern the future rf-core.js / rf-*.js unit tests will use, with libs stubbed
// in the vm context). It asserts the harness itself is wired, not report
// behavior — report behavior arrives with the rf-* feature files in later phases.

test('node:test harness executes assertions in a vm context', () => {
    const context = vm.createContext({ URL, console: { log() {} } });
    vm.runInContext('this.x = 41 + 1;', context);
    assert.equal(context.x, 42);
});

test('script extraction follows HTML end-tag boundaries and permits end-tag attributes', () => {
    assert.deepEqual(
        extractInlineScripts('<script>first()</script\t\n data-marker="value">'),
        ['first()']
    );
    assert.deepEqual(
        extractInlineScripts('<script>first()</scripture><script>second()</script>'),
        ['first()</scripture><script>second()']
    );
});

test('node:test harness can extract and run the DOT synthesis from a script', () => {
    const viewer = readFileSync(`${__dirname}/../../main/resources/viewer/index.html`, 'utf8');
    const scripts = extractInlineScripts(viewer)
        .map(script => script.replace(/^\s*import .*;$/gm, ''));
    const synthesis = scripts.find(script => script.includes('function hierarchyToDot'))
        .split('        async function loadReport()')[0];
    const builders = vm.runInNewContext(`${synthesis}\n({ hierarchyToDot, packageHierarchyToDot })`, { URL });

    const dot = builders.hierarchyToDot({
        packages: [{ id: 'cluster_abc123', nodeId: 'package_abc123', name: 'demo', classes: [] }],
        edges: [],
    });
    assert.equal(dot.split('\n')[0], 'strict digraph G {');
});

function extractInlineScripts(html) {
    const scripts = [];
    let cursor = 0;
    while (cursor < html.length) {
        const openingStart = findHtmlTag(html, '<script', cursor);
        if (openingStart < 0) break;
        const openingNameEnd = openingStart + '<script'.length;
        if (!isHtmlTagBoundary(html[openingNameEnd])) {
            cursor = openingNameEnd;
            continue;
        }

        const openingEnd = html.indexOf('>', openingNameEnd);
        if (openingEnd < 0) break;

        let closingStart = findHtmlTag(html, '</script', openingEnd + 1);
        while (closingStart >= 0 && !isHtmlTagBoundary(html[closingStart + '</script'.length])) {
            closingStart = findHtmlTag(html, '</script', closingStart + '</script'.length);
        }
        if (closingStart < 0) break;

        const closingEnd = html.indexOf('>', closingStart + '</script'.length);
        if (closingEnd < 0) break;
        scripts.push(html.slice(openingEnd + 1, closingStart));
        cursor = closingEnd + 1;
    }
    return scripts;
}

function findHtmlTag(html, tag, from) {
    let candidate = html.indexOf('<', from);
    while (candidate >= 0) {
        if (html.slice(candidate, candidate + tag.length).toLowerCase() === tag) return candidate;
        candidate = html.indexOf('<', candidate + 1);
    }
    return -1;
}

function isHtmlTagBoundary(character) {
    return character === '>' || character === '/' ||
        character === ' ' || character === '\t' || character === '\n' ||
        character === '\f' || character === '\r';
}
