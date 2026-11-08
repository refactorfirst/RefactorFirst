const { test, expect } = require('@playwright/test');
const { pathToFileURL } = require('node:url');
const path = require('node:path');

// Phase P4/T4.8: the interactive single-file report must open via file:// with zero
// uncaught page errors and zero failed local requests, expose window.RF, and render
// Chart.js bubble charts (non-blank canvas pixels). Both the minifyHtml=false and
// minifyHtml=true legs are exercised. CDN reachability is assumed (pinned widgets);
// any widget failure degrades the report gracefully without page errors.
const UNMINIFIED_PATH = path.resolve(__dirname, '..', '..', 'target', 'e2e', 'refactor-first-report.html');
const MINIFIED_PATH = path.resolve(__dirname, '..', '..', 'target', 'e2e', 'refactor-first-report.minified.html');

async function openReport(page, reportPath) {
    const pageErrors = [];
    const failedLocalRequests = [];

    page.on('pageerror', (error) => pageErrors.push(String(error)));
    page.on('requestfailed', (request) => {
        const url = request.url();
        // Only local file:// requests can violate CORS; CDN reachability is
        // exercised with explicit online legs (P9).
        if (url.startsWith('file://')) {
            failedLocalRequests.push(`${url} -> ${request.failure()?.errorText ?? 'failed'}`);
        }
    });

    const response = await page.goto(pathToFileURL(reportPath).href);
    expect(response.status()).toBe(200);
    return {pageErrors, failedLocalRequests};
}

test.describe('interactive skeleton', () => {
    for (const [leg, reportPath] of [
        ['minifyHtml=false', UNMINIFIED_PATH],
        ['minifyHtml=true', MINIFIED_PATH],
    ]) {
        test(`${leg}: opens via file:// with zero page errors and window.RF present`, async ({page}) => {
            const {pageErrors, failedLocalRequests} = await openReport(page, reportPath);

            // The report shell renders (single h1 + nav landmark) before widget scripts.
            const heading = page.getByRole('heading', {level: 1});
            await expect(heading).toContainText('RefactorFirst');
            await expect(page.getByRole('navigation')).toBeVisible();

            // The rf-data payload is embedded and parsed into the RF namespace.
            const rfData = await page.evaluate(() => ({
                hasRf: typeof window.RF === 'object' && window.RF !== null,
                hasPayload: !!(window.RF && window.RF.data),
                project: window.RF && window.RF.data ? window.RF.data.project.name : null,
            }));
            expect(rfData.hasRf).toBe(true);
            expect(rfData.hasPayload).toBe(true);
            expect(rfData.project).toBe('ParityProject');

            expect(pageErrors).toEqual([]);
            expect(failedLocalRequests).toEqual([]);
        });

        test(`${leg}: charts render on the disharmony canvases`, async ({page}) => {
            // The parity fixture project is clean (no findings, no canvases); the
            // Kotlin fixture report carries the disharmony sections and charts.
            const chartsPath = leg.endsWith('true')
                ? path.resolve(__dirname, '..', '..', 'target', 'e2e', 'refactor-first-report.kt.minified.html')
                : path.resolve(__dirname, '..', '..', 'target', 'e2e', 'refactor-first-report.kt.html');
            await openReport(page, chartsPath);

            // Wait for Chart.js (CDN) and the chart boot to paint non-blank pixels.
            await page.waitForFunction(
                () => {
                    const canvas = document.getElementById('chart_DATA_CLASS_WITH_LOGIC');
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
        });

        test(`${leg}: popup buttons and close controls are wired`, async ({page}) => {
            await openReport(page, reportPath);

            const popup = page.locator('#popup-classGraph');
            await expect(popup).toBeHidden();
            await page.locator('button[data-popup-2d][data-popup="popup-classGraph"]').click();
            await expect(popup).toBeVisible();
            await page.locator('#popup-classGraph .close-btn').click();
            await expect(popup).toBeHidden();
        });
    }
});
