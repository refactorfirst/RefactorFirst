const { test, expect } = require('@playwright/test');
const { pathToFileURL } = require('node:url');
const { readFileSync } = require('node:fs');
const path = require('node:path');

// Phase P9/T9.2 + T9.3: mechanical parity with the page repo — the DOM contract
// (caption text, aria-sort transitions, match-count phrasing, toast phrasing,
// CSV filename pattern) and the chart data contract (the bubble set emitted by
// the Java report equals the page repo's fixture values) verified against the
// page repo's published fixture (tests/fixtures/junit4-report.json).

const REPORT_PATH = path.resolve(__dirname, '..', '..', 'target', 'e2e', 'refactor-first-report.junit4.html');
const FIXTURE = JSON.parse(
    readFileSync(path.resolve(__dirname, '..', '..', 'src', 'test', 'resources', 'parity', 'junit4-report.json'), 'utf8'));

async function openReport(page) {
    const consoleErrors = [];
    page.on('console', (message) => {
        if (message.type() === 'error') consoleErrors.push(message.text());
    });
    page.on('pageerror', (error) => consoleErrors.push(String(error)));
    await page.goto(pathToFileURL(REPORT_PATH).href);
    await page.waitForTimeout(400);
    return consoleErrors;
}

test.describe('page-repo parity', () => {
    test('T9.2: table captions carry the page-repo phrasing', async ({ page }) => {
        const consoleErrors = await openReport(page);
        const captions = await page.evaluate(() =>
            [...document.querySelectorAll('.rf-data-table caption')].map(caption => caption.textContent.trim()));

        expect(captions).toContain('Class relationships to remove, in priority order');
        expect(captions).toContain('Package relationships to remove, in priority order');
        expect(captions).toContain('Class cycles summary');
        expect(captions).toContain('Classes and relationships in the largest cycle');
        expect(consoleErrors).toEqual([]);
    });

    test('T9.2: match-status phrasing matches the page repo ("N of M rows match")', async ({ page }) => {
        const consoleErrors = await openReport(page);

        await page.locator('#rf-search-class-relationships').fill('junit');
        await page.waitForTimeout(500);
        const match = await page.locator('[data-rf-match="class-relationships"]').textContent();
        expect(match).toMatch(/^\d+ of \d+ rows match$/);
        expect(consoleErrors).toEqual([]);
    });

    test('T9.2: toast phrasing and CSV filename pattern match the page repo', async ({ page }) => {
        const consoleErrors = await openReport(page);

        // Toast: the copy feedback message family ("Copied …").
        await page.locator('table[data-rf-table="class-cycles-summary"] tbody td').first().click();
        const toast = await page.locator('#rf-copy-toast').textContent();
        expect(toast).toMatch(/^Copied/);

        // CSV: refactorfirst-<slugified-table-id>-<ISO-timestamp>.csv
        const downloadPromise = page.waitForEvent('download');
        await page.locator('button[data-rf-export="class-relationships"]').click();
        const download = await downloadPromise;
        expect(download.suggestedFilename()).toMatch(
            /^refactorfirst-class-relationships-\d{4}-\d{2}-\d{2}T\d{2}-\d{2}-\d{2}\.csv$/);
        expect(consoleErrors).toEqual([]);
    });

    test('T9.3: the emitted bubble set equals the page repo fixture values', async ({ page }) => {
        const consoleErrors = await openReport(page);

        // The rf-data chart payload carries the same bubbles the page repo
        // renders from the fixture JSON (single source: ReportModelBuilder.createBubble).
        const bubbles = await page.evaluate(() => {
            const god = window.RF.data.disharmonies.find(d => d.anchorId === 'GOD');
            return god.chart.bubbles.map(bubble => ({
                label: bubble.label,
                x: bubble.x,
                y: bubble.y,
                r: bubble.r,
                color: bubble.color,
                borderColor: bubble.borderColor,
            }));
        });
        const expected = FIXTURE.disharmonies
            .find(d => d.anchorId === 'GOD')
            .chart.bubbles
            .map(bubble => ({
                label: bubble.label,
                x: bubble.x,
                y: bubble.y,
                r: bubble.r,
                color: bubble.color,
                borderColor: bubble.borderColor,
            }));
        expect(bubbles).toEqual(expected);

        // The priority-2-of-14 ramp golden from the plan (RPB section).
        const rpb = await page.evaluate(() => {
            const section = window.RF.data.disharmonies.find(d => d.anchorId === 'RPB');
            return section.chart.bubbles.find(bubble => bubble.priority === 2);
        });
        expect(rpb.color).toBe('rgba(220, 72, 55, 0.75)');
        expect(rpb.borderColor).toBe('rgb(220, 72, 55)');
        expect(consoleErrors).toEqual([]);
    });

    test('T9.1: computed table-header and body styles equal the palette contract', async ({ page }) => {
        const consoleErrors = await openReport(page);

        // Light theme: the sticky header background/text and the body palette.
        const header = await page.evaluate(() => {
            const th = document.querySelector('.rf-data-table thead th');
            const style = getComputedStyle(th);
            return {background: style.backgroundColor, color: style.color};
        });
        expect(header.background).toBe('rgb(42, 111, 151)'); // #2a6f97
        expect(header.color).toBe('rgb(255, 255, 255)'); // #fff

        const body = await page.evaluate(() => {
            const style = getComputedStyle(document.body);
            return {
                bg: style.getPropertyValue('--bg-color').trim(),
                text: style.getPropertyValue('--text-color').trim(),
                link: style.getPropertyValue('--link-color').trim(),
            };
        });
        expect(body.bg).toBe('#ffffff');
        expect(body.text).toBe('#1f2d3d');
        expect(body.link).toBe('#2a6f97');
        expect(consoleErrors).toEqual([]);
    });
});
