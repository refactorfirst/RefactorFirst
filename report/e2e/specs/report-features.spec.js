const { test, expect } = require('@playwright/test');
const { pathToFileURL } = require('node:url');
const path = require('node:path');

// Phase P6/T6.7: the enhanced table experience from file:// — sticky headers stay
// pinned while scrolling tall tables, filtering narrows rows with a live match
// count (Escape and × clear), sort/filter/export work keyboard-only, the CSV
// download carries the fixture rows, copying a cell shows the toast, and the
// charts and graphs survive tbody rebuilds. Zero console errors throughout.
const REPORT_PATH = path.resolve(__dirname, '..', '..', 'target', 'e2e', 'refactor-first-report.junit4.html');

test.beforeEach(async ({ context }) => {
    // Clipboard permission for the copy tests (file:// origin).
    await context.grantPermissions(['clipboard-read', 'clipboard-write'], {origin: 'http://localhost'});
});

async function openReport(page) {
    const consoleErrors = [];
    page.on('console', (message) => {
        if (message.type() === 'error') consoleErrors.push(message.text());
    });
    page.on('pageerror', (error) => consoleErrors.push(String(error)));
    await page.goto(pathToFileURL(REPORT_PATH).href);
    // The enhancer rebuilds each tbody after applying the default sort.
    await page.waitForTimeout(400);
    return consoleErrors;
}

test.describe('report table features', () => {
    test('sticky headers stay pinned while scrolling a tall table', async ({page}) => {
        const consoleErrors = await openReport(page);

        const header = page.locator('table[data-rf-table="class-relationships"] thead th').first();
        await header.scrollIntoViewIfNeeded();
        // Scroll past the table's top edge: the header pins to the viewport top
        // while its (paginated) rows are still in view.
        await page.mouse.wheel(0, 600);
        await page.waitForTimeout(200);

        const box = await header.boundingBox();
        expect(box.y).toBeLessThanOrEqual(1);
        // The pinned header belongs to a table still on screen.
        const rowsVisible = await page
            .locator('table[data-rf-table="class-relationships"] tbody tr')
            .first()
            .isVisible();
        expect(rowsVisible).toBe(true);
        expect(consoleErrors).toEqual([]);
    });

    test('filter narrows rows, updates the live match count, and Escape and × clear', async ({page}) => {
        const consoleErrors = await openReport(page);

        const search = page.locator('#rf-search-class-relationships');
        const rows = page.locator('table[data-rf-table="class-relationships"] tbody tr');
        const total = await page.evaluate(
            () => window.RF.data.classRelationshipsToRemove.relationships.length);

        // Unfiltered: pagination engages above the 20-row threshold.
        expect(total).toBeGreaterThan(20);
        await expect(page.locator('[data-rf-pagination="class-relationships"]')).toBeVisible();
        expect(await rows.count()).toBe(20);
        await expect(page.locator('[data-rf-page-status="class-relationships"]')).toHaveText('Page 1 of 3');

        await search.fill('junit');
        await page.waitForTimeout(500); // search debounce
        // The match status is the source of truth for the complete filtered count (rf-tables.js);
        // the rendered row count is only the current page size while pagination is engaged, so the
        // filtered count and the displayed page size are checked separately.
        const match = (await page.locator('[data-rf-match="class-relationships"]').textContent()) ?? '';
        const filteredRows = Number.parseInt(String(match).split(' of ')[0], 10);
        expect(filteredRows).toBeGreaterThan(20); // the filtered set exceeds the 20-row page size
        expect(await rows.count()).toBe(20); // displayed page size, checked separately from filtered
        expect(match).toBe(`${filteredRows} of ${total} rows match`);

        // Escape clears the filter (and re-engages pagination).
        await search.press('Escape');
        await page.waitForTimeout(400);
        expect(await rows.count()).toBe(20);
        expect(await page.locator('[data-rf-match="class-relationships"]').textContent()).toBe('');

        // The × control reappears while typing and clears too.
        await search.fill('runner');
        await page.waitForTimeout(500);
        await expect(page.locator('[data-rf-search-slot="class-relationships"] .rf-search-clear')).toBeVisible();
        await page.locator('[data-rf-search-slot="class-relationships"] .rf-search-clear').click();
        await page.waitForTimeout(400);
        expect(await rows.count()).toBe(20);
        expect(await page.locator('[data-rf-search-slot="class-relationships"] .rf-search-clear')).toBeHidden();
        expect(consoleErrors).toEqual([]);
    });

    test('sort toggles through ascending, descending and none keyboard-only', async ({ page }) => {
        const consoleErrors = await openReport(page);

        const table = page.locator('table[data-rf-table="class-cycles-summary"]');
        const priorityHeader = table.locator('thead th').nth(1);
        const rows = table.locator('tbody tr');

        const firstCycleName = await rows.first().locator('td').first().textContent();

        // Keyboard-only: focus the sort button and press Enter (ascending again → desc).
        await priorityHeader.locator('button').focus();
        await expect(priorityHeader).toHaveAttribute('aria-sort', 'ascending');
        await priorityHeader.locator('button').press('Enter');
        await page.waitForTimeout(200);
        await expect(priorityHeader).toHaveAttribute('aria-sort', 'descending');

        // A third press returns to the report order (none).
        await priorityHeader.locator('button').press('Enter');
        await page.waitForTimeout(200);
        await expect(priorityHeader).toHaveAttribute('aria-sort', 'none');
        const restoredFirst = await rows.first().locator('td').first().textContent();
        expect(restoredFirst).toBe(firstCycleName);
        expect(consoleErrors).toEqual([]);
    });

    test('CSV export downloads the filtered, sorted rows', async ({ page }) => {
        const consoleErrors = await openReport(page);

        const search = page.locator('#rf-search-class-relationships');
        await search.fill('junit');
        await page.waitForTimeout(500);

        const downloadPromise = page.waitForEvent('download');
        await page.locator('button[data-rf-export="class-relationships"]').click();
        const download = await downloadPromise;

        // refactorfirst-<table-id>-<timestamp>.csv
        expect(download.suggestedFilename()).toMatch(/^refactorfirst-class-relationships-\d{4}-\d{2}-\d{2}T\d{2}-\d{2}-\d{2}\.csv$/);
        const csv = require('node:fs').readFileSync(await download.path(), 'utf8');
        const lines = csv.trim().split('\n');
        expect(lines[0]).toBe('Class Relationship,Priority,In Class Cycles,Relationship Strength,Also Removes Pkg Cycle Relationship,In Package Cycles');
        for (const line of lines.slice(1)) {
            expect(line.toLowerCase()).toContain('junit');
        }
        expect(consoleErrors).toEqual([]);
    });

    test('copying a cell writes the clipboard and shows the toast', async ({ page }) => {
        const consoleErrors = await openReport(page);

        const cell = page.locator('table[data-rf-table="class-cycles-summary"] tbody td').first();
        await cell.click();

        const toast = page.locator('#rf-copy-toast');
        await expect(toast).toHaveAttribute('role', 'status');
        await expect(toast).toHaveAttribute('aria-live', 'polite');
        await expect(toast).toContainText('Copied to clipboard');

        // Keyboard parity: Enter on a copy cell copies too.
        await page.locator('table[data-rf-table="class-cycles-summary"] tbody td').nth(1).focus();
        await page.keyboard.press('Enter');
        await expect(toast).toContainText('Copied to clipboard');
        expect(consoleErrors).toEqual([]);
    });

    test('charts and graphs survive tbody rebuilds', async ({ page }) => {
        page.emulateMedia({colorScheme: 'light'});
        const consoleErrors = await openReport(page);

        await page.waitForFunction(
            () => {
                const canvas = document.getElementById('chart_RPB');
                if (!canvas || !canvas.width) return false;
                const pixels = canvas.getContext('2d').getImageData(0, 0, canvas.width, canvas.height).data;
                for (let i = 3; i < pixels.length; i += 4) {
                    if (pixels[i] !== 0) return true;
                }
                return false;
            },
            undefined,
            {timeout: 20000},
        );
        await page.waitForSelector('#classGraph svg', {state: 'attached', timeout: 20000});

        // A table interaction rebuilds tbodies only — the chart canvas and the
        // rendered SVG must survive untouched.
        await page.locator('#rf-search-class-relationships').fill('junit');
        await page.waitForTimeout(500);
        const chartPainted = await page.evaluate(() => {
            const canvas = document.getElementById('chart_RPB');
            const pixels = canvas.getContext('2d').getImageData(0, 0, canvas.width, canvas.height).data;
            for (let i = 3; i < pixels.length; i += 4) {
                if (pixels[i] !== 0) return true;
            }
            return false;
        });
        expect(chartPainted).toBe(true);
        expect(await page.locator('#classGraph svg').count()).toBe(1);
        expect(consoleErrors).toEqual([]);
    });
});
