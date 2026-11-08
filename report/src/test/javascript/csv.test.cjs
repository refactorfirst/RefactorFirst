const assert = require('node:assert/strict');
const { readFileSync } = require('node:fs');
const { resolve } = require('node:path');
const { test } = require('node:test');

// T6.3: the CSV export ported from the page repo's table-operations.js —
// escapeCsvValue (RFC 4180: quote-doubling, wrap on comma/quote/newline;
// formula-injection neutralization: leading = + - @ after whitespace gets an
// apostrophe prefix), buildCsv, generateCsvFilename
// (refactorfirst-<slugified-table-id>-<ISO-timestamp>.csv) and exportTableToCsv
// (export honors current filter + sort, ignores pagination).

const ASSETS_DIR = resolve(__dirname, '../../main/resources/assets');

function loadRfCsv() {
    const window = {RF: {}};
    global.window = window;
    new Function(readFileSync(resolve(ASSETS_DIR, 'rf-tables.js'), 'utf8'))();
    new Function(readFileSync(resolve(ASSETS_DIR, 'rf-csv.js'), 'utf8'))();
    return window.RF;
}

test('escapeCsvValue doubles quotes and wraps on comma/quote/newline', () => {
    const RF = loadRfCsv();
    assert.equal(RF.escapeCsvValue('plain'), 'plain');
    assert.equal(RF.escapeCsvValue('a,b'), '"a,b"');
    assert.equal(RF.escapeCsvValue('say "hi"'), '"say ""hi"""');
    assert.equal(RF.escapeCsvValue('line1\nline2'), '"line1\nline2"');
    assert.equal(RF.escapeCsvValue('cr\rreturn'), '"cr\rreturn"');
    assert.equal(RF.escapeCsvValue(null), '');
    assert.equal(RF.escapeCsvValue(undefined), '');
});

test('escapeCsvValue neutralizes spreadsheet formulas with an apostrophe prefix', () => {
    const RF = loadRfCsv();
    assert.equal(RF.escapeCsvValue('=cmd|expr'), "'=cmd|expr");
    assert.equal(RF.escapeCsvValue('+2+2'), "'+2+2");
    assert.equal(RF.escapeCsvValue('-1'), "'-1");
    assert.equal(RF.escapeCsvValue('@import'), "'@import");
    assert.equal(RF.escapeCsvValue('  =hidden formula'), "'  =hidden formula", 'whitespace-prefixed too');
    assert.equal(RF.escapeCsvValue('total = 5'), 'total = 5', 'an interior = is inert');
});

test('buildCsv joins headers and rows', () => {
    const RF = loadRfCsv();
    const csv = RF.buildCsv(['Name', 'Priority'], [['A.java', '1'], ['B,java', '2']]);
    assert.equal(csv, 'Name,Priority\nA.java,1\n"B,java",2');
});

test('generateCsvFilename slugs the table id and strips unsafe timestamp characters', () => {
    const RF = loadRfCsv();
    const filename = RF.generateCsvFilename('class-relationships', new Date('2026-09-19T10:20:30.000Z'));
    assert.equal(filename, 'refactorfirst-class-relationships-2026-09-19T10-20-30.csv');

    assert.equal(
        RF.generateCsvFilename('Disharmony: GOD!', new Date('2026-09-19T10:20:30.000Z')),
        'refactorfirst-disharmony-god-2026-09-19T10-20-30.csv');
    assert.equal(
        RF.generateCsvFilename('', new Date('2026-09-19T10:20:30.000Z')),
        'refactorfirst-table-2026-09-19T10-20-30.csv');
});

test('exportTableToCsv returns the document without downloading when asked', () => {
    const RF = loadRfCsv();
    const rows = [{a: 1, b: 'x,y'}];
    const result = RF.exportTableToCsv(
        rows,
        [{key: 'a', label: 'A'}, {key: 'b', label: 'B'}],
        'test-table',
        {download: false, now: new Date('2026-09-19T10:20:30.000Z')});
    assert.equal(result.csv, 'A,B\n1,"x,y"');
    assert.equal(result.filename, 'refactorfirst-test-table-2026-09-19T10-20-30.csv');
    assert.equal(result.rowCount, 1);
});

test('triggerDownload creates a text/csv blob download link and revokes it', () => {
    const RF = loadRfCsv();
    const clicks = [];
    const revocations = [];
    const link = {
        setAttribute: (name, value) => {
            link[name] = value;
        },
        style: {},
        click: () => clicks.push(link),
    };
    global.document = {
        createElement: () => link,
        body: {appendChild: () => {}, removeChild: () => {}},
    };
    global.Blob = class {
        constructor(parts, options) {
            Blob.lastParts = parts;
            Blob.lastOptions = options;
        }
    };
    global.URL = {
        createObjectURL: () => 'blob:fake',
        revokeObjectURL: (url) => revocations.push(url),
    };

    RF.triggerDownload('a,b', 'file.csv');
    assert.deepEqual(Blob.lastOptions, {type: 'text/csv;charset=utf-8;'});
    assert.equal(link.download, 'file.csv');
    assert.equal(link.href, 'blob:fake');
    assert.equal(clicks.length, 1);
    assert.deepEqual(revocations, ['blob:fake']);
});
