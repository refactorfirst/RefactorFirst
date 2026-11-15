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

test('node:test harness can extract and run the DOT synthesis from a script', () => {
    const viewer = readFileSync(`${__dirname}/../../main/resources/viewer/index.html`, 'utf8');
    const scripts = [...viewer.matchAll(/<script(?:\s[^>]*)?>([\s\S]*?)<\/script\s*>/gi)]
        .map(match => match[1].replace(/^\s*import .*;$/gm, ''));
    const synthesis = scripts.find(script => script.includes('function hierarchyToDot'))
        .split('        async function loadReport()')[0];
    const builders = vm.runInNewContext(`${synthesis}\n({ hierarchyToDot, packageHierarchyToDot })`, { URL });

    const dot = builders.hierarchyToDot({
        packages: [{ id: 'cluster_abc123', nodeId: 'package_abc123', name: 'demo', classes: [] }],
        edges: [],
    });
    assert.equal(dot.split('\n')[0], 'strict digraph G {');
});
