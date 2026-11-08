const { test, expect } = require('@playwright/test');
const { pathToFileURL } = require('node:url');
const path = require('node:path');

// Phase P0/T0.3 E2E canary: the fixture report generated into target/e2e/ by
// SampleReportGenerationTest must open via file:// with zero console errors,
// zero failed local requests and zero uncaught page errors — the CORS gate
// every later phase (P4 interactive template, P6 tables, P7 graphs, P8 WCAG)
// must keep green.
const REPORT_PATH = path.resolve(__dirname, '..', '..', 'target', 'e2e', 'refactor-first-report.html');

test.describe('file protocol', () => {
    test('generated interactive report opens via file:// cleanly', async ({ page }) => {
        const consoleErrors = [];
        const failedRequests = [];

        page.on('console', (message) => {
            if (message.type() === 'error') {
                consoleErrors.push(message.text());
            }
        });
        page.on('pageerror', (error) => consoleErrors.push(String(error)));
        page.on('requestfailed', (request) => {
            const url = request.url();
            // Only local file:// requests can violate CORS; CDN reachability is
            // exercised by later phases with explicit online legs.
            if (url.startsWith('file://')) {
                failedRequests.push(`${url} -> ${request.failure()?.errorText ?? 'failed'}`);
            }
        });

        const response = await page.goto(pathToFileURL(REPORT_PATH).href);
        expect(response.status()).toBe(200);

        // The report shell renders (h1 + nav landmarks) before widget scripts.
        const heading = page.getByRole('heading', { level: 1 });
        await expect(heading).toBeVisible();

        // Let deferred CDN scripts (buttons, mvp.css, chart.js, …) settle.
        await page.waitForTimeout(2000);

        expect(consoleErrors).toEqual([]);
        expect(failedRequests).toEqual([]);
    });
});
