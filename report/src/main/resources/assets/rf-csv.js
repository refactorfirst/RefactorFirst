// RefactorFirst interactive report CSV export (Phase P6/T6.3): port of the
// page repo's table-operations.js CSV section — RFC 4180 escaping with
// formula-injection neutralization, deterministic filenames, and the Blob
// download plumbing.
(function () {
    'use strict';

    /**
     * Escapes a single CSV field per RFC 4180 (quotes doubled, values
     * containing commas/quotes/newlines wrapped in quotes).
     */
    function escapeCsvValue(value) {
        if (value === null || value === undefined) return '';
        const raw = String(value);
        // Neutralize spreadsheet formulas: a report can plant values starting
        // with =, +, - or @ (possibly behind whitespace) that Excel/Sheets
        // would evaluate on open; an apostrophe prefix keeps them as inert text.
        const text = typeof value === 'string' && /^[\t\r\n ]*[=+\-@]/.test(raw)
            ? "'" + raw
            : raw;
        if (/[",\n\r]/.test(text)) {
            return '"' + text.replace(/"/g, '""') + '"';
        }
        return text;
    }

    /** Builds a CSV document from header labels and pre-extracted string rows. */
    function buildCsv(headers, rows) {
        const lines = [headers.map(escapeCsvValue).join(',')];
        for (const row of rows) {
            lines.push(row.map(escapeCsvValue).join(','));
        }
        return lines.join('\n');
    }

    /**
     * Builds a deterministic export filename: slugified table name plus an ISO
     * timestamp (colons/dots stripped to stay filesystem-safe).
     */
    function generateCsvFilename(tableName, date) {
        const slug = String(tableName || 'table')
            .toLowerCase()
            .replace(/[^a-z0-9]+/g, '-')
            .replace(/^-+|-+$/g, '') || 'table';
        const stamp = (date instanceof Date && !Number.isNaN(date.getTime())
            ? date
            : new Date()
        ).toISOString().replace(/:/g, '-').replace(/\..+$/, '');
        return 'refactorfirst-' + slug + '-' + stamp + '.csv';
    }

    /** Creates and activates a temporary browser download link for CSV text. */
    function triggerDownload(csv, filename) {
        if (typeof document === 'undefined' || typeof Blob === 'undefined') return;
        const blob = new Blob([csv], {type: 'text/csv;charset=utf-8;'});
        if (typeof URL === 'undefined' || typeof URL.createObjectURL !== 'function') return;
        const url = URL.createObjectURL(blob);
        const link = document.createElement('a');
        link.setAttribute('href', url);
        link.setAttribute('download', filename);
        link.style.display = 'none';
        document.body.appendChild(link);
        link.click();
        document.body.removeChild(link);
        if (typeof URL.revokeObjectURL === 'function') URL.revokeObjectURL(url);
    }

    /**
     * Serializes rows to CSV and (by default) triggers a browser download.
     * rows are pre-extracted string arrays; columns carry the header labels.
     */
    function exportTableToCsv(rows, columns, tableName, options = {}) {
        const data = Array.isArray(rows) ? rows : [];
        const cols = (Array.isArray(columns) ? columns : []).map(col =>
            typeof col === 'string' ? {key: col, label: col} : col);
        const valueFor = options.valueFor || ((row, key) => row?.[key]);
        const csv = buildCsv(
            cols.map(col => col.label ?? col.key),
            data.map(row => cols.map(col => valueFor(row, col.key))));
        const filename = options.filename
            || generateCsvFilename(tableName, options.now ?? new Date());
        if (options.download !== false) {
            triggerDownload(csv, filename);
        }
        return {csv: csv, filename: filename, rowCount: data.length};
    }

    window.RF.escapeCsvValue = escapeCsvValue;
    window.RF.buildCsv = buildCsv;
    window.RF.generateCsvFilename = generateCsvFilename;
    window.RF.triggerDownload = triggerDownload;
    window.RF.exportTableToCsv = exportTableToCsv;
})();
