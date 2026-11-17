const assert = require('node:assert/strict');
const { readFileSync } = require('node:fs');
const { resolve } = require('node:path');
const { test } = require('node:test');

// T6.1: the pure table operations ported from the page repo's
// lib/table-operations.js — filterRows (case-insensitive substring across all
// columns, stripHtml before matching), sortRows (numeric/date-time semantics —
// the Java-side oracle for the localized ", " date delimiter is
// SimpleHtmlReport.isDateTime), the aria-sort cycle ascending→descending→none
// with ▲/▼/blank indicators, paginate defaults, and the "N of M rows match" /
// "Page X of Y" statuses.

const ASSETS_DIR = resolve(__dirname, '../../main/resources/assets');

function loadRfTables() {
    const window = { RF: {}, addEventListener: () => {} };
    global.window = window;
    new Function(readFileSync(resolve(ASSETS_DIR, 'rf-tables.js'), 'utf8'))();
    return window.RF;
}

const TABLE_CONFIG = {
    pagination: {threshold: 20, pageSize: 20},
    sorting: {enabled: true, defaultSortColumn: 'priority', defaultSortDirection: 'asc'},
};

test('filterTableData matches case-insensitively across columns and strips HTML', () => {
    const RF = loadRfTables();
    const rows = [
        {cells: [{content: 'BlockJUnit4ClassRunner.java'}, {content: '1'}]},
        {cells: [{content: 'ParentRunner.java'}, {content: '2'}]},
    ];
    const haystack = row => row.cells.map(cell => RF.stripHtml(cell.content)).join(' ');

    assert.equal(RF.filterTableData(rows, 'PARENTRUNNER', haystack).length, 1);
    assert.equal(RF.filterTableData(rows, 'runner.java', haystack).length, 2);
    assert.equal(RF.filterTableData(rows, '', haystack).length, 2, 'empty term returns everything');

    // Markup is stripped before matching, so an tagged cell still matches its text
    // (the tag itself becomes a space, mirroring the page repo's stripHtml).
    const tagged = [{cells: [{content: 'ParentRunner<b>.java</b>'}, {content: '2'}]}];
    assert.equal(RF.filterTableData(tagged, 'parentrunner', haystack).length, 1);
    assert.equal(RF.stripHtml(tagged[0].cells[0].content), 'ParentRunner .java');
});

test('stripHtml decodes named and numeric entities and collapses whitespace', () => {
    const RF = loadRfTables();
    assert.equal(RF.stripHtml('<a href="x">A & B</a>'), 'A & B');
    assert.equal(RF.stripHtml('A &#8594; B'), 'A \u2192 B');
    assert.equal(RF.stripHtml('A &#x2192; B'), 'A \u2192 B');
    assert.equal(RF.stripHtml('a<br>b'), 'a b');
    assert.equal(RF.stripHtml(null), '');
});

test('sortTableData sorts numerically for numeric text and stable for ties', () => {
    const RF = loadRfTables();
    const rows = [{priority: '10'}, {priority: '2'}, {priority: '1'}, {priority: undefined}];
    const sorted = RF.sortTableData(rows, 'priority', 'asc', (row, key) => row[key]);
    assert.deepEqual(
        sorted.map(row => row.priority),
        ['1', '2', '10', undefined],
        'numeric-looking strings compare numerically; missing values sink last');

    const descending = RF.sortTableData(rows, 'priority', 'desc', (row, key) => row[key]);
    assert.equal(descending[descending.length - 1].priority, undefined, 'missing values stay last in desc');
});

test('sortRows on date-time text uses the localized ", " delimiter ordering', () => {
    // The Java-side oracle for this format is SimpleHtmlReport.isDateTime:
    // a value containing ", " is treated as a date-time cell and right-aligned.
    const RF = loadRfTables();
    const rows = [
        {when: '7/22/23, 5:00 AM'},
        {when: '7/21/23, 9:30 AM'},
    ];
    const sorted = RF.sortTableData(rows, 'when', 'asc', (row, key) => row[key]);
    // Same-date prefix compares the time part lexically per the page repo rules.
    assert.equal(sorted[0].when, '7/21/23, 9:30 AM');
});

test('sort cycle: ascending → descending → none with ▲/▼/blank indicators', () => {
    const RF = loadRfTables();
    const cycle = RF.nextSortState('asc');
    assert.deepEqual(cycle, {sortDir: 'desc', indicator: '▼', ariaSort: 'descending'});
    assert.deepEqual(RF.nextSortState('desc'), {sortDir: null, indicator: '', ariaSort: 'none'});
    assert.deepEqual(RF.nextSortState(null), {sortDir: 'asc', indicator: '▲', ariaSort: 'ascending'});
});

test('paginateTableData slices 1-based pages; pageCount and statuses match the page repo', () => {
    const RF = loadRfTables();
    const rows = Array.from({length: 45}, (_, i) => i);
    assert.deepEqual(RF.paginateTableData(rows, 20, 1), rows.slice(0, 20));
    assert.deepEqual(RF.paginateTableData(rows, 20, 3), rows.slice(40, 45));
    assert.deepEqual(RF.paginateTableData(rows, 20, 0), rows.slice(0, 20), 'invalid pages fall back to 1');
    assert.deepEqual(RF.paginateTableData(rows, 20, 4), [], 'out-of-range pages are empty');

    assert.equal(RF.pageCount(45, 20), 3);
    assert.equal(RF.pageCount(0, 20), 0);

    assert.equal(RF.matchStatus(7, 45, true), '7 of 45 rows match');
    assert.equal(RF.matchStatus(45, 45, false), '', 'an unfiltered table shows no match status');
    assert.equal(RF.pageStatus(2, 3), 'Page 2 of 3');
});

test('pagination kicks in only above the configured threshold', () => {
    const RF = loadRfTables();
    assert.equal(RF.paginationEngaged(20, TABLE_CONFIG.pagination), false, 'at threshold: no pagination');
    assert.equal(RF.paginationEngaged(21, TABLE_CONFIG.pagination), true);
});

test('resolveSortKey matches exact keys then falls back to case-insensitive labels', () => {
    const RF = loadRfTables();
    const columns = [
        {key: 'col0', label: 'Class'},
        {key: 'col1', label: 'Priority'},
    ];
    assert.equal(RF.resolveSortKey(columns, 'col1'), 'col1');
    assert.equal(RF.resolveSortKey(columns, 'priority'), 'col1', 'label fallback');
    assert.equal(RF.resolveSortKey(columns, 'nope'), null);
});

test('sorting disabled keeps sort buttons inert and keeps the default sort unset', () => {
    const RF = loadRfTables();
    const table = {
        getAttribute: () => 'class-relationships',
        querySelectorAll: selector => {
            if (selector === 'thead th') return [{
                querySelector: () => null,
                innerHTML: 'Priority',
                getAttribute: () => null,
                setAttribute: () => {},
                querySelectorAll: () => [],
            }];
            if (selector === 'tbody tr') return [{
                querySelectorAll: () => [{innerHTML: '1'}],
                cloneNode: () => ({querySelectorAll: () => []}),
            }];
            return [];
        },
    };
    const button = {
        listeners: {},
        addEventListener: (type, handler) => { button.listeners[type] = handler; },
        getAttribute: () => 'priority',
        setAttribute: () => {},
    };
    const pageButton = {listeners: {}, addEventListener(type, handler) { this.listeners[type] = handler; }};
    const exportButton = {listeners: {}, addEventListener(type, handler) { this.listeners[type] = handler; }};
    const root = {
        querySelectorAll: selector => {
            if (selector === 'table[data-rf-table]') return [table];
            if (selector === '[data-rf-sort="class-relationships"]') return [button];
            if (selector === '[data-rf-page="class-relationships"]') return [pageButton];
            if (selector === '[data-rf-export="class-relationships"]') return [exportButton];
            return [];
        },
        querySelector: () => null,
    };
    const config = {
        pagination: {threshold: 20, pageSize: 20},
        sorting: {enabled: false, defaultSortColumn: 'priority', defaultSortDirection: 'asc'},
        search: {enabled: false, debounceMs: 300},
        export: {enabled: false},
        copy: {enabled: false, toastDuration: 3000},
        stickyHeaders: false,
    };
    RF.enhanceTables(root, {data: {classRelationshipsToRemove: {relationships: [{priority: '1'}]}}, config});
    assert.equal(button.listeners.click, undefined, 'click handlers stay disabled while sorting is off');
    assert.equal(typeof pageButton.listeners.click, 'function');
    assert.equal(typeof exportButton.listeners.click, 'function');
});

test('localized date cells sort chronologically across years', () => {
    const RF = loadRfTables();
    const rows = [{date: '1/1/26, 1:00 PM'}, {date: '12/31/25, 1:00 PM'}];
    assert.equal(RF.sortTableData(rows, 'date', 'asc')[0], rows[1]);
});
