const assert = require('node:assert/strict');
const { readFileSync } = require('node:fs');
const { resolve } = require('node:path');
const { test } = require('node:test');

// T4.3: rf-charts.js builds the config the page repo builds — bubble type; the custom
// generateLabels legend with exactly three entries; grid #e0e0e0; legend/axis text from
// the theme resolver; tooltip lines with the click prompt; onClick opens repoUrl + path
// (isHttpUrl-gated, noopener); onHover sets a pointer cursor only when clickable.

const ASSETS_DIR = resolve(__dirname, '../../main/resources/assets');

function loadRf() {
    // Fresh window per load: rf-core publishes RF onto the browser `window`;
    // in Node that is the shared global.
    global.window = {};
    eval(readFileSync(resolve(ASSETS_DIR, 'rf-core.js'), 'utf8'));
    eval(readFileSync(resolve(ASSETS_DIR, 'rf-charts.js'), 'utf8'));
    return global.window.RF;
}

function capturedConfig(bubbles) {
    const RF = loadRf();
    const configs = [];
    window.Chart = function FakeChart(context, config) {
        configs.push(config);
    };
    RF.initBubbleChart(
        {getContext: () => ({})},
        'God Classes',
        {xaxisLabel: 'Effort to refactor', yaxisLabel: 'Relative churn (impact)', bubbles: bubbles},
        'https://github.com/test/test/blob/');
    return configs[0];
}

const BUBBLE = {
    id: 'TestClass',
    label: 'TestClass.java',
    x: 5,
    y: 10,
    r: 24,
    priority: 1,
    effortRank: 5,
    changePronenessRank: 10,
    color: 'rgba(235, 64, 52, 0.75)',
    borderColor: 'rgb(235, 64, 52)',
    path: 'src/main/java/TestClass.java',
};

test('chart config is a bubble chart carrying the dataset colors', () => {
    const config = capturedConfig([BUBBLE]);
    assert.equal(config.type, 'bubble');
    assert.equal(config.data.datasets[0].label, 'God Classes');
    assert.deepEqual(config.data.datasets[0].backgroundColor, ['rgba(235, 64, 52, 0.75)']);
    assert.deepEqual(config.data.datasets[0].borderColor, ['rgb(235, 64, 52)']);
    assert.equal(config.data.datasets[0].borderWidth, 1);
    assert.equal(config.data.datasets[0].data[0].x, 5);
    assert.equal(config.data.datasets[0].data[0].raw.url, 'https://github.com/test/test/blob/src/main/java/TestClass.java');
});

test('legend has exactly the three priority entries with page-repo colors', () => {
    const config = capturedConfig([BUBBLE]);
    const labels = config.options.plugins.legend.labels.generateLabels();
    assert.deepEqual(
        labels.map(item => item.text),
        ['High Priority (1)', 'Medium Priority', 'Low Priority (max)']);
    assert.deepEqual(
        labels.map(item => item.fillStyle),
        ['rgb(235, 64, 52)', 'rgb(137, 119, 74)', 'rgb(39, 174, 96)']);
    assert.deepEqual(
        labels.map(item => item.strokeStyle),
        ['rgb(235, 64, 52)', 'rgb(137, 119, 74)', 'rgb(39, 174, 96)']);
    assert.equal(labels[0].fontColor, '#5c6b7a', 'legend text resolves from the theme');
});

test('grid color and axis titles match the page repo (U+27F6 suffix)', () => {
    const config = capturedConfig([BUBBLE]);
    assert.deepEqual(config.options.plugins.legend, config.options.plugins.legend);
    assert.equal(config.options.scales.x.title.text, 'Effort to refactor \u27F6');
    assert.equal(config.options.scales.y.title.text, 'Relative churn (impact) \u27F6');
    assert.deepEqual(config.options.scales.x.grid, {color: '#e0e0e0'});
    assert.deepEqual(config.options.scales.y.grid, {color: '#e0e0e0'});
    assert.equal(config.options.plugins.legend.position, 'bottom');
});

test('tooltip lists File/Priority/Effort Rank/Change Proneness Rank and the click prompt', () => {
    const config = capturedConfig([BUBBLE]);
    const label = config.options.plugins.tooltip.callbacks.label({
        raw: {raw: {label: 'TestClass.java', priority: 1, x: 5, y: 10, url: 'https://example.com/a.java'}},
    });
    assert.deepEqual(label, [
        'File: TestClass.java',
        'Priority: 1',
        'Effort Rank: 5',
        'Change Proneness Rank: 10',
        'Click to open source file',
    ]);

    const withoutUrl = config.options.plugins.tooltip.callbacks.label({
        raw: {raw: {label: 'TestClass.java', priority: 1, x: 5, y: 10, url: 'javascript:alert(1)'}},
    });
    assert.deepEqual(withoutUrl, [
        'File: TestClass.java',
        'Priority: 1',
        'Effort Rank: 5',
        'Change Proneness Rank: 10',
    ]);
});

test('onClick opens the bubble URL with noopener; scheme-injected URLs are refused', () => {
    const config = capturedConfig([BUBBLE]);
    const opened = [];
    window.open = (url, target, features) => {
        opened.push({url, target, features});
    };

    config.options.onClick({}, [{datasetIndex: 0, index: 0}], {
        data: {datasets: [{data: [{raw: {url: 'https://github.com/test/test/blob/src/main/java/TestClass.java'}}]}]},
    });
    assert.deepEqual(opened, [{
        url: 'https://github.com/test/test/blob/src/main/java/TestClass.java',
        target: '_blank',
        features: 'noopener',
    }]);

    opened.length = 0;
    config.options.onClick({}, [{datasetIndex: 0, index: 0}], {
        data: {datasets: [{data: [{raw: {url: 'javascript:alert(1)'}}]}]},
    });
    assert.deepEqual(opened, [], 'injected schemes never reach window.open');
});

test('onHover sets a pointer cursor only when the bubble is clickable', () => {
    const config = capturedConfig([BUBBLE]);
    const target = {style: {cursor: 'default'}};
    const event = {native: {target}};

    config.options.onHover(event, [{datasetIndex: 0, index: 0}], {
        data: {datasets: [{data: [{raw: {url: 'https://example.com/a.java'}}]}]},
    });
    assert.equal(target.style.cursor, 'pointer');

    config.options.onHover(event, [{datasetIndex: 0, index: 0}], {
        data: {datasets: [{data: [{raw: {url: 'data:text/html,hi'}}]}]},
    });
    assert.equal(target.style.cursor, 'default');
});
