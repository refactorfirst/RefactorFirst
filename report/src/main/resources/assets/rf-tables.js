// RefactorFirst interactive report tables (Phase P6/T6.6): pure table
// operations + the DOM enhancer. The pure operations (filter, sort, paginate,
// statuses, sort-cycle) are ports of the page repo's lib/table-operations.js
// semantics; the enhancer captures the server-rendered rows once, then
// rebuilds the <tbody> on every state change (filter → sort → paginate),
// re-wiring the copy targets and restoring focus after each rebuild. The
// date-time sort oracle on the Java side is SimpleHtmlReport.isDateTime (a
// value containing the localized ", " delimiter is a date-time cell).
(function () {
    'use strict';

    // Documented defaults; every knob can be overridden by the caller.
    const TABLE_CONFIG = {
        pagination: {
            threshold: 20, // Minimum filtered rows before pagination kicks in
            pageSize: 20, // Rows per page
        },
        sorting: {
            enabled: true,
            // Every table opens sorted by its Priority column, ascending
            // (priority 1 first), so the report's built-in priority ordering is
            // visible and the up arrow signals that headers are sortable.
            defaultSortColumn: 'priority',
            defaultSortDirection: 'asc',
        },
        search: {
            enabled: true,
            debounceMs: 300,
        },
        export: {
            enabled: true,
        },
        copy: {
            enabled: true,
            toastDuration: 3000,
        },
        stickyHeaders: true,
    };

    // ---------------------------------------------------------------------------
    // Text extraction
    // ---------------------------------------------------------------------------

    const NAMED_ENTITIES = {amp: '&', lt: '<', gt: '>', quot: '"', apos: "'", nbsp: ' ', hellip: '…'};

    /**
     * Strips HTML tags and decodes entities into plain text. Deliberately
     * regex-based: the report JSON is untrusted, so building DOM nodes from it
     * (innerHTML) could execute inline handlers before sanitization.
     */
    function stripHtml(html) {
        if (html === null || html === undefined) return '';
        return String(html)
            .replace(/<[^>]*>/g, ' ')
            .replace(/&(#x[0-9a-fA-F]+|#[0-9]+|[a-zA-Z]+);/g, (match, entity) => {
                if (entity[0] === '#') {
                    const code = entity[1].toLowerCase() === 'x'
                        ? parseInt(entity.slice(2), 16)
                        : parseInt(entity.slice(1), 10);
                    if (Number.isFinite(code) && code > 0 && code <= 0x10FFFF) {
                        try {
                            return String.fromCodePoint(code);
                        } catch (error) {
                            return match;
                        }
                    }
                    return match;
                }
                return NAMED_ENTITIES[entity] ?? match;
            })
            .replace(/\s+/g, ' ')
            .trim();
    }

    // ---------------------------------------------------------------------------
    // Filtering
    // ---------------------------------------------------------------------------

    /**
     * Case-insensitively filters rows by a search term across every string
     * visible via the accessor. An empty/absent term returns the original array.
     */
    function filterTableData(data, searchTerm, accessor) {
        if (!Array.isArray(data)) return [];
        const term = (searchTerm ?? '').toString().trim().toLowerCase();
        if (!term) return data;
        const textOf = accessor || (row => Object.values(row ?? {}).join(' '));
        return data.filter(row => String(textOf(row)).toLowerCase().includes(term));
    }

    // ---------------------------------------------------------------------------
    // Sorting
    // ---------------------------------------------------------------------------

    /** Converts a cell value into a normalized numeric or textual sort value. */
    function comparable(value) {
        if (value === null || value === undefined) return null;
        if (typeof value === 'boolean') return {numeric: true, value: value ? 1 : 0};
        if (typeof value === 'number') {
            return Number.isNaN(value) ? null : {numeric: true, value: value};
        }
        const text = String(value).trim();
        if (text !== '' && !Number.isNaN(Number(text))) {
            return {numeric: true, value: Number(text)};
        }
        if (text.includes(', ')) {
            const timestamp = Date.parse(text.replace(/[\u202f\u00a0]/g, ' '));
            if (Number.isFinite(timestamp)) return {numeric: true, value: timestamp};
        }
        return {numeric: false, value: text};
    }

    /** Compares two normalized, non-null sort values. */
    function compareValues(a, b) {
        if (a.numeric && b.numeric) return a.value - b.value;
        return String(a.value).localeCompare(String(b.value), undefined, {
            numeric: true,
            sensitivity: 'base',
        });
    }

    /**
     * Stable-sorts rows by a column. Missing/undefined values always sink to
     * the end regardless of direction, and numeric-looking strings compare
     * numerically. A null column returns the original array untouched.
     */
    function sortTableData(data, sortColumn, sortDirection, accessor) {
        if (!Array.isArray(data)) return [];
        if (sortColumn === null || sortColumn === undefined) return data;
        const direction = sortDirection === 'desc' ? -1 : 1;
        const valueOf = accessor || (row => row?.[sortColumn]);
        const annotated = data.map((row, index) => ({
            row: row,
            index: index,
            value: comparable(valueOf(row, sortColumn)),
        }));
        annotated.sort((a, b) => {
            if (a.value === null && b.value === null) return a.index - b.index;
            if (a.value === null) return 1; // missing values always go last
            if (b.value === null) return -1;
            const order = compareValues(a.value, b.value);
            return order === 0 ? a.index - b.index : order * direction;
        });
        return annotated.map(entry => entry.row);
    }

    /**
     * Resolves a requested sort column against column descriptors. An exact
     * column key match wins; when there is none, a case-insensitive label match
     * is attempted (dynamic disharmony tables use positional col<n> keys, so a
     * semantic key like "priority" must hit the Priority-labelled column).
     * Returns null when nothing matches.
     */
    function resolveSortKey(columns, sortKey) {
        if (!sortKey) return null;
        const cols = Array.isArray(columns) ? columns : [];
        if (cols.some(col => col.key === sortKey)) return sortKey;
        const wanted = String(sortKey).trim().toLowerCase();
        const byLabel = cols.find(col => String(col.label ?? '').trim().toLowerCase() === wanted);
        return byLabel?.key ?? null;
    }

    /**
     * The aria-sort cycle: ascending → descending → none (original report
     * order) → ascending. Indicators follow: ▲ / ▼ / blank.
     */
    function nextSortState(currentDirection) {
        if (currentDirection === 'asc') {
            return {sortDir: 'desc', indicator: '▼', ariaSort: 'descending'};
        }
        if (currentDirection === 'desc') {
            return {sortDir: null, indicator: '', ariaSort: 'none'};
        }
        return {sortDir: 'asc', indicator: '▲', ariaSort: 'ascending'};
    }

    // ---------------------------------------------------------------------------
    // Pagination
    // ---------------------------------------------------------------------------

    /** Slices one page out of a row set. Pages are 1-based; invalid page
     * numbers (0, negative, non-numeric) fall back to page 1. Out-of-range
     * pages yield an empty array. */
    function paginateTableData(data, pageSize, currentPage) {
        const rows = Array.isArray(data) ? data : [];
        let page = Number(currentPage);
        if (!Number.isFinite(page) || page < 1) page = 1;
        page = Math.floor(page);
        const start = (page - 1) * pageSize;
        return rows.slice(start, start + pageSize);
    }

    /** Computes the number of pages for a row count (0 for empty data). */
    function pageCount(rowCount, pageSize) {
        if (!rowCount || rowCount < 0) return 0;
        return Math.ceil(rowCount / pageSize);
    }

    /** Pagination renders only above the configured threshold. */
    function paginationEngaged(matchCount, pagination) {
        return matchCount > (pagination ?? TABLE_CONFIG.pagination).threshold;
    }

    /** "N of M rows match" while a filter is active; empty otherwise. */
    function matchStatus(matchCount, totalRows, searchActive) {
        if (!searchActive) return '';
        return matchCount + ' of ' + totalRows + ' rows match';
    }

    /** "Page X of Y". */
    function pageStatus(currentPage, totalPages) {
        return 'Page ' + currentPage + ' of ' + Math.max(totalPages, 1);
    }

    // ---------------------------------------------------------------------------
    // Sticky headers / horizontal scroll (pure math + DOM wrappers)
    // ---------------------------------------------------------------------------

    /** Horizontal scrolling engages only on real overflow (1px slack). */
    function shouldEnableHorizontalScroll(scrollWidth, clientWidth) {
        return scrollWidth > clientWidth + 1;
    }

    /**
     * Computes the sticky-header transform for an overflowing wrapper: the
     * header translates down by the amount the viewport scrolled past the
     * table's top, clamped so the header never leaves the table's box. The
     * thead's layout box anchors the resting position (it sits below the table
     * top whenever a caption precedes it). Empty string clears the transform.
     */
    function stickyHeaderTransform(layout) {
        const tableTop = layout.tableTop;
        const tableHeight = layout.tableHeight;
        const headTop = layout.headTop;
        const headHeight = layout.headHeight;
        const maxOffset = Math.max(tableHeight - (headTop - tableTop) - headHeight, 0);
        const offset = Math.min(Math.max(-headTop, 0), maxOffset);
        return offset > 0 ? 'translateY(' + offset + 'px)' : '';
    }

    /** Toggles horizontal scrolling only on table wrappers that overflow. */
    function measureHorizontalScroll(root) {
        root.querySelectorAll('.rf-table-scroll[data-rf-scroll]').forEach(wrapper => {
            wrapper.classList.toggle(
                'rf-scroll-x-enabled',
                shouldEnableHorizontalScroll(wrapper.scrollWidth, wrapper.clientWidth));
        });
    }

    /** Keeps headers viewport-sticky when their table wrapper scrolls horizontally. */
    function refreshStickyHeaders(root) {
        root.querySelectorAll('.rf-table-scroll[data-rf-scroll]').forEach(wrapper => {
            const table = wrapper.querySelector('table[data-rf-table]');
            const headers = table ? table.querySelectorAll('thead th') : [];
            if (!table || headers.length === 0) return;
            let value = '';
            if (wrapper.classList.contains('rf-scroll-x-enabled')) {
                const thead = table.querySelector('thead');
                const tableRect = table.getBoundingClientRect();
                const headRect = thead ? thead.getBoundingClientRect() : tableRect;
                value = stickyHeaderTransform({
                    tableTop: tableRect.top,
                    tableHeight: tableRect.height,
                    headTop: headRect.top,
                    headHeight: headRect.height,
                });
            }
            headers.forEach(th => {
                th.style.transform = value;
            });
        });
    }

    // ---------------------------------------------------------------------------
    // Report table registry (column models shared with rf-csv.js)
    // ---------------------------------------------------------------------------

    /** Builds the plain-text form of one relationship (source [marked] → target [marked]). */
    function relationshipText(relationship, sourceNameKey, targetNameKey) {
        if (!relationship || typeof relationship !== 'object') return '';
        const hasStructuredFields =
            relationship[sourceNameKey] != null || relationship[targetNameKey] != null;
        if (!hasStructuredFields) return '';
        const source = String(relationship[sourceNameKey] ?? '') + (relationship.sourceMarked ? '*' : '');
        const target = String(relationship[targetNameKey] ?? '') + (relationship.targetMarked ? '*' : '');
        return source + ' → ' + target;
    }

    function classRelationshipText(relationship) {
        return relationshipText(relationship, 'simpleSourceClassName', 'simpleTargetClassName');
    }

    function packageRelationshipText(relationship) {
        return relationshipText(relationship, 'sourcePackage', 'targetPackage');
    }

    const REPORT_TABLES = {
        'class-relationships': {
            id: 'class-relationships',
            caption: 'Class relationships to remove, in priority order',
            getRows: data => data?.classRelationshipsToRemove?.relationships ?? [],
            columns: [
                {key: 'classRelationship', label: 'Class Relationship', value: classRelationshipText},
                {key: 'priority', label: 'Priority'},
                {key: 'cycleCount', label: 'In Class Cycles'},
                {key: 'effortRank', label: 'Relationship Strength'},
                {
                    key: 'alsoRemovesPackageRelationship',
                    label: 'Also Removes Pkg Cycle Relationship',
                    value: row => (row.alsoRemovesPackageRelationship ? 'true' : 'false'),
                },
                {key: 'packageCycleCount', label: 'In Package Cycles'},
            ],
        },
        'package-relationships': {
            id: 'package-relationships',
            caption: 'Package relationships to remove, in priority order',
            getRows: data => data?.packageRelationshipsToRemove?.relationships ?? [],
            columns: [
                {key: 'packageRelationship', label: 'Package Relationship', value: packageRelationshipText},
                {key: 'priority', label: 'Priority'},
                {key: 'cycleCount', label: 'In Pkg Cycles'},
                {key: 'effortRank', label: 'Relationship Strength'},
                {
                    key: 'classRelationshipsToBreakPackage',
                    label: 'Class Relationships to Remove To Break Package Relationship',
                    value: row =>
                        (row.classRelationshipsToBreakPackage ?? [])
                            .map(entry => classRelationshipText(entry))
                            .join('; '),
                },
            ],
        },
        'class-cycles-summary': {
            id: 'class-cycles-summary',
            caption: 'Class cycles summary',
            getRows: data => data?.classCycles?.summary ?? [],
            columns: [
                {key: 'cycleName', label: 'Cycle Name'},
                {key: 'priority', label: 'Priority'},
                {key: 'classCount', label: 'Class Count'},
                {key: 'relationshipCount', label: 'Relationship Count'},
            ],
        },
        'largest-cycle-breakdown': {
            id: 'largest-cycle-breakdown',
            caption: 'Classes and relationships in the largest cycle',
            getRows: data => data?.classCycles?.largestCycle?.breakdown ?? [],
            columns: [
                {key: 'className', label: 'Classes', value: row => stripHtml(row.className ?? '') + (row.marked ? '*' : '')},
                {key: 'edgesHtml', label: 'Relationships', value: row => stripHtml(row.edgesHtml)},
            ],
        },
    };

    /** Builds the table descriptor for one disharmony findings table. */
    function disharmonyTableDescriptor(disharmony, index) {
        const anchor = disharmony?.anchorId ?? 'findings-' + (index ?? 0);
        return {
            id: 'disharmony-' + anchor,
            caption: (disharmony?.title ?? 'Disharmony') + ' findings, in priority order',
            getRows: () => disharmony?.table?.rows ?? [],
            columns: (disharmony?.table?.headers ?? []).map((label, columnIndex) => ({
                key: 'col' + columnIndex,
                label: label,
                value: row => stripHtml(row.cells?.[columnIndex]?.content),
            })),
        };
    }

    /** Extracts the plain-text value of a row for a descriptor column. */
    function columnValue(column, row) {
        return column.value ? column.value(row) : row?.[column.key];
    }

    /** Looks up the table descriptor for a table id, resolving dynamic
     * disharmony tables against the report data. */
    function getTableDescriptor(data, tableId) {
        if (Object.prototype.hasOwnProperty.call(REPORT_TABLES, tableId)) {
            return REPORT_TABLES[tableId];
        }
        const match = /^disharmony-(.+)$/.exec(String(tableId));
        if (match) {
            const disharmony = (data?.disharmonies ?? []).find(d => d.anchorId === match[1]);
            if (disharmony) return disharmonyTableDescriptor(disharmony);
        }
        return null;
    }

    // ---------------------------------------------------------------------------
    // DOM enhancer: capture rows once, rebuild the tbody on state change
    // ---------------------------------------------------------------------------

    /** The per-table UI state and captured rows, keyed by table id. */
    const tableStates = {};
    const capturedTables = {};

    /** Captures a table's header labels and row elements once. innerHTML (not
     * textContent) is the source so <br> separators survive as spaces; the
     * regex-based stripHtml keeps untrusted markup inert. */
    function captureTable(table) {
        const headers = Array.from(table.querySelectorAll('thead th')).map(th => ({
            key: th.querySelector('[data-sort-key]')?.getAttribute('data-sort-key') ?? '',
            label: stripHtml(th.innerHTML),
            element: th,
        }));
        const rows = Array.from(table.querySelectorAll('tbody tr')).map(tr => ({
            element: tr.cloneNode(true),
            cells: Array.from(tr.querySelectorAll('td')).map(td => stripHtml(td.innerHTML)),
        }));
        return {headers: headers, rows: rows};
    }

    /** Makes body cells keyboard-operable copy targets with result feedback. */
    function bindCopyableCells(table, onCopy) {
        table.querySelectorAll('tbody td').forEach(cell => {
            cell.setAttribute('tabindex', '0');
            cell.setAttribute('data-rf-copy', '');
            cell.setAttribute('title', 'Click to copy this cell');
            const copy = () => {
                const text = stripHtml(cell.innerHTML);
                window.RF.copyCellContent(text).then(ok => onCopy(ok, text));
            };
            cell.addEventListener('click', event => {
                if (!event.target.closest('a')) copy();
            });
            cell.addEventListener('keydown', event => {
                if (event.target === cell && (event.key === 'Enter' || event.key === ' ')) {
                    event.preventDefault();
                    copy();
                }
            });
        });
    }

    /** Applies the filter → sort → paginate pipeline to one captured table. */
    function applyTableState(root, tableId, config) {
        const captured = capturedTables[tableId];
        const state = tableStates[tableId];
        const table = root.querySelector('table[data-rf-table="' + tableId + '"]');
        const tbody = table ? table.querySelector('tbody') : null;
        if (!captured || !state || !tbody) return;

        // Focus preservation (pendingFocusRef pattern): remember the focused
        // cell position so the rebuild can restore it.
        const active = document.activeElement;
        let pendingFocusIndex = -1;
        if (active && active.tagName === 'TD') {
            const row = active.closest('tr');
            const rowIndex = Array.prototype.indexOf.call(tbody.querySelectorAll('tr'), row);
            if (rowIndex >= 0) {
                pendingFocusIndex =
                    rowIndex * Math.max(captured.headers.length, 1) +
                    Array.prototype.indexOf.call(row.querySelectorAll('td'), active);
            }
        }

        const searchActive = (state.search ?? '').trim().length > 0;
        const filtered = filterTableData(
            captured.rows,
            state.search,
            row => row.cells.join(' '));
        const sorted = state.sortKey === null || state.sortKey === undefined
            ? filtered
            : sortTableData(
                filtered,
                state.sortKey,
                state.sortDir,
                row => {
                    const index = captured.headers.findIndex(
                        header => header.key === state.sortKey || header.label === state.sortKey);
                    return index >= 0 ? row.cells[index] : '';
                });
        const paginated = paginationEngaged(filtered.length, config.pagination);
        const totalPages = pageCount(filtered.length, config.pagination.pageSize);
        state.page = Math.min(Math.max(state.page ?? 1, 1), Math.max(totalPages, 1));
        const visible = paginated ? paginateTableData(sorted, config.pagination.pageSize, state.page) : sorted;

        // Rebuild the tbody from the captured row elements.
        const fragment = document.createDocumentFragment ? document.createDocumentFragment() : null;
        const builder = fragment || [];
        visible.forEach(row => builder.appendChild ? builder.appendChild(row.element.cloneNode(true)) : builder.push(row.element));
        tbody.innerHTML = '';
        if (fragment) {
            tbody.appendChild(fragment);
        } else {
            builder.forEach(node => tbody.appendChild(node));
        }

        measureHorizontalScroll(root);
        refreshStickyHeaders(root);

        // Sortable header state: the sorted column carries aria-sort + indicator.
        captured.headers.forEach((header, index) => {
            const button = header.element.querySelector('[data-sort-key]');
            if (!button) return;
            const indicator = header.element.querySelector('.rf-sort-indicator');
            if (header.key === state.sortKey) {
                header.element.setAttribute('aria-sort', state.sortDir === 'desc' ? 'descending' : 'ascending');
                if (indicator) indicator.textContent = state.sortDir === 'desc' ? '▼' : '▲';
            } else {
                header.element.setAttribute('aria-sort', 'none');
                if (indicator) indicator.textContent = '';
            }
        });

        // Statuses.
        const match = root.querySelector('[data-rf-match="' + tableId + '"]');
        if (match) match.textContent = matchStatus(filtered.length, captured.rows.length, searchActive);
        const pagination = root.querySelector('[data-rf-pagination="' + tableId + '"]');
        if (pagination) pagination.hidden = !paginated;
        const pageStatusElement = root.querySelector('[data-rf-page-status="' + tableId + '"]');
        if (pageStatusElement) pageStatusElement.textContent = pageStatus(state.page, totalPages);
        const prev = root.querySelector('[data-rf-page="' + tableId + '"][data-page-dir="prev"]');
        if (prev) prev.disabled = state.page <= 1;
        const next = root.querySelector('[data-rf-page="' + tableId + '"][data-page-dir="next"]');
        if (next) next.disabled = paginated ? state.page >= totalPages : true;

        // Re-wire copy targets and restore focus.
        if (config.copy.enabled) {
            bindCopyableCells(table, window.RF.onCopyFeedback ?? (() => {}));
        }
        if (pendingFocusIndex >= 0) {
            const allCells = tbody.querySelectorAll('td');
            if (pendingFocusIndex < allCells.length) allCells[pendingFocusIndex].focus();
        }
    }

    /** Installs the shared resize and scroll listeners (bound once per window). */
    function bindWindowResizeMeasure(root) {
        if (bindWindowResizeMeasure.bound || typeof window === 'undefined') return;
        bindWindowResizeMeasure.bound = root;
        window.addEventListener('resize', () => {
            const bound = bindWindowResizeMeasure.bound;
            if (!bound) return;
            measureHorizontalScroll(bound);
            refreshStickyHeaders(bound);
        });
        window.addEventListener('scroll', () => {
            const bound = bindWindowResizeMeasure.bound;
            if (bound) refreshStickyHeaders(bound);
        }, {passive: true});
    }

    /** Debounces input events (search box). */
    function debounce(handler, delayMs) {
        let timer = null;
        return () => {
            if (timer !== null) clearTimeout(timer);
            timer = setTimeout(handler, delayMs);
        };
    }

    /** Injects the trusted filter input and clear button for a rendered table. */
    function buildSearchControl(slot, tableId, caption, state, config, applyState) {
        const inputId = 'rf-search-' + tableId;
        const matchId = 'rf-match-' + tableId;

        // Screen-reader-only label: the visible prompt lives in the input's
        // placeholder and disappears as the user types.
        const label = document.createElement('label');
        label.setAttribute('for', inputId);
        label.className = 'rf-search-label rf-visually-hidden';
        label.textContent = 'Filter table';

        const input = document.createElement('input');
        input.type = 'search';
        input.id = inputId;
        input.placeholder = 'Filter table...';
        input.setAttribute('data-rf-search', tableId);
        input.setAttribute('aria-label', 'Filter the ' + caption + ' table');
        input.setAttribute('aria-describedby', matchId);
        input.autocomplete = 'off';
        input.value = state.search ?? '';

        // Clear control overlaid on the input's right edge; only offered while
        // the box holds a term (CSS hides it, hidden keeps it out of the
        // accessibility tree entirely).
        const clear = document.createElement('button');
        clear.type = 'button';
        clear.className = 'rf-search-clear';
        clear.setAttribute('aria-label', 'Clear the ' + caption + ' table filter');
        clear.title = 'Clear the ' + caption + ' table filter';
        clear.textContent = '×';
        clear.hidden = (state.search ?? '') === '';

        const applySearch = value => {
            state.search = value;
            state.page = 1;
            applyState();
        };
        const clearSearch = () => {
            input.value = '';
            clear.hidden = true;
            applySearch('');
        };
        const debounced = debounce(() => applySearch(input.value), config.search.debounceMs);
        input.addEventListener('input', () => {
            clear.hidden = input.value === '';
            if (config.search.debounceMs <= 0) {
                applySearch(input.value);
            } else {
                debounced();
            }
        });
        input.addEventListener('keydown', event => {
            if (event.key === 'Escape') {
                event.preventDefault();
                clearSearch();
            }
        });
        clear.addEventListener('click', clearSearch);

        slot.appendChild(label);
        slot.appendChild(input);
        slot.appendChild(clear);
    }

    /**
     * Binds all interactive table controls found in the report DOM: captures
     * each table's rows once, applies the default sort, and wires the search,
     * sort, pagination, export and copy controls.
     *
     * @param {Element} root - Report container.
     * @param {object} [options] - {data, config, onCopy}
     */
    function enhanceTables(root, options = {}) {
        const data = options.data ?? {};
        const config = options.config ?? TABLE_CONFIG;
        window.RF.onCopyFeedback = options.onCopy ?? (() => {});

        root.querySelectorAll('table[data-rf-table]').forEach(table => {
            const tableId = table.getAttribute('data-rf-table');
            const descriptor = getTableDescriptor(data, tableId);
            if (!descriptor) return;

            capturedTables[tableId] = captureTable(table);
            const state = tableStates[tableId] ?? {};
            state.search = state.search ?? '';
            state.page = state.page ?? 1;
            const defaultSortKey = config.sorting.enabled
                ? resolveSortKey(
                    capturedTables[tableId].headers.map(header => ({key: header.key, label: header.label})),
                    config.sorting.defaultSortColumn)
                : null;
            // The default sort resolves by key first, then by label — the
            // disharmony tables emit their header text as the sort key.
            state.sortKey = config.sorting.enabled
                ? (state.sortKey !== undefined
                    ? state.sortKey
                    : defaultSortKey ??
                        (capturedTables[tableId].headers.find(header => header.key === config.sorting.defaultSortColumn)
                            ? config.sorting.defaultSortColumn
                            : capturedTables[tableId].headers.find(header =>
                                header.label.toLowerCase() === config.sorting.defaultSortColumn)?.key ?? null))
                : null;
            if (state.sortKey === undefined || state.sortKey === '') {
                state.sortKey = null;
            }
            state.sortDir = config.sorting.enabled
                ? (state.sortDir ?? config.sorting.defaultSortDirection)
                : null;
            tableStates[tableId] = state;

            if (config.search.enabled) {
                const slot = root.querySelector('[data-rf-search-slot="' + tableId + '"]');
                if (slot) {
                    buildSearchControl(slot, tableId, descriptor.caption, state, config, () => applyTableState(root, tableId, config));
                }
                const match = root.querySelector('[data-rf-match="' + tableId + '"]');
                if (match && !match.id) match.id = 'rf-match-' + tableId;
            }

            // Sort buttons: the three-state cycle starting from the effective
            // direction, so clicking the default-sorted column flips it first.
            const buttons = config.sorting.enabled
                ? Array.from(root.querySelectorAll('[data-rf-sort="' + tableId + '"]')) : [];
            buttons.forEach(button => {
                button.addEventListener('click', () => {
                    const sortKey = button.getAttribute('data-sort-key');
                    const headerIndex = capturedTables[tableId].headers.findIndex(
                        header => header.key === sortKey || header.label === sortKey);
                    const sameColumn = state.sortKey !== null && headerIndex >= 0 &&
                        (capturedTables[tableId].headers[headerIndex].key === state.sortKey);
                    const next = nextSortState(sameColumn ? state.sortDir : null);
                    state.sortDir = next.sortDir;
                    state.sortKey = next.sortDir === null ? null : sortKey;
                    state.page = 1;
                    applyTableState(root, tableId, config);
                });
            });

            // Pagination buttons.
            root.querySelectorAll('[data-rf-page="' + tableId + '"]').forEach(button => {
                button.addEventListener('click', () => {
                    if (button.disabled) return;
                    const dir = button.getAttribute('data-page-dir') === 'next' ? 1 : -1;
                    state.page = Math.max((state.page ?? 1) + dir, 1);
                    applyTableState(root, tableId, config);
                });
            });

            // CSV export: honors the current filter + sort, ignores pagination.
            root.querySelectorAll('[data-rf-export="' + tableId + '"]').forEach(button => {
                button.addEventListener('click', () => {
                    const captured = capturedTables[tableId];
                    const filtered = filterTableData(captured.rows, state.search, row => row.cells.join(' '));
                    const sorted = state.sortKey === null || state.sortKey === undefined
                        ? filtered
                        : sortTableData(
                            filtered,
                            state.sortKey,
                            state.sortDir,
                            row => {
                                const index = captured.headers.findIndex(
                                    header => header.key === state.sortKey || header.label === state.sortKey);
                                return index >= 0 ? row.cells[index] : '';
                            });
                    const csv = window.RF.buildCsv(
                        captured.headers.map(header => header.label),
                        sorted.map(row => row.cells));
                    window.RF.triggerDownload(csv, window.RF.generateCsvFilename(tableId, new Date()));
                });
            });

            applyTableState(root, tableId, config);
        });

        measureHorizontalScroll(root);
        refreshStickyHeaders(root);
        bindWindowResizeMeasure(root);
    }

    window.RF.TABLE_CONFIG = TABLE_CONFIG;
    window.RF.REPORT_TABLES = REPORT_TABLES;
    window.RF.stripHtml = stripHtml;
    window.RF.filterTableData = filterTableData;
    window.RF.sortTableData = sortTableData;
    window.RF.resolveSortKey = resolveSortKey;
    window.RF.nextSortState = nextSortState;
    window.RF.paginateTableData = paginateTableData;
    window.RF.pageCount = pageCount;
    window.RF.paginationEngaged = paginationEngaged;
    window.RF.matchStatus = matchStatus;
    window.RF.pageStatus = pageStatus;
    window.RF.shouldEnableHorizontalScroll = shouldEnableHorizontalScroll;
    window.RF.stickyHeaderTransform = stickyHeaderTransform;
    window.RF.measureHorizontalScroll = measureHorizontalScroll;
    window.RF.refreshStickyHeaders = refreshStickyHeaders;
    window.RF.disharmonyTableDescriptor = disharmonyTableDescriptor;
    window.RF.columnValue = columnValue;
    window.RF.getTableDescriptor = getTableDescriptor;
    window.RF.enhanceTables = enhanceTables;
})();
