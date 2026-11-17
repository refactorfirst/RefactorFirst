const { test, expect } = require('@playwright/test');
const { pathToFileURL } = require('node:url');
const path = require('node:path');

// Phase P5/T5.6: the report's three theme modes (light/dark/system, system default).
// Ported core assertions from the page repo: switching radios flips the computed
// palette on body, the system radio follows colorScheme emulation, the preference
// persists across reload, the chart legend text re-resolves, the vizdom SVG
// re-renders with the dark edge-label color, and N toggles leave zero console errors.
const REPORT_PATH = path.resolve(__dirname, '..', '..', 'target', 'e2e', 'refactor-first-report.kt.html');

async function openReport(page) {
    const consoleErrors = [];
    page.on('console', (message) => {
        if (message.type() === 'error') consoleErrors.push(message.text());
    });
    page.on('pageerror', (error) => consoleErrors.push(String(error)));
    await page.goto(pathToFileURL(REPORT_PATH).href);
    return consoleErrors;
}

const bodyPalette = () =>
    page.evaluate(() => {
        const style = getComputedStyle(document.body);
        return {bg: style.getPropertyValue('--bg-color').trim(), text: style.getPropertyValue('--text-color').trim()};
    });

test.describe('dark mode', () => {
    test('defaults to system (light under a light OS preference)', async ({page}) => {
        await page.emulateMedia({colorScheme: 'light'});
        const consoleErrors = await openReport(page);

        await expect(page.getByRole('radiogroup', {name: 'Color theme'})).toBeVisible();
        await expect(page.locator('#rf-theme-system')).toBeChecked();

        const palette = await page.evaluate(() => ({
            bg: getComputedStyle(document.body).getPropertyValue('--bg-color').trim(),
            text: getComputedStyle(document.body).getPropertyValue('--text-color').trim(),
        }));
        expect(palette.bg).toBe('#ffffff');
        expect(palette.text).toBe('#1f2d3d');
        expect(consoleErrors).toEqual([]);
    });

    test('switching the dark radio flips the computed palette on body', async ({page}) => {
        await page.emulateMedia({colorScheme: 'light'});
        await openReport(page);

        await page.locator('#rf-theme-dark').check();
        const dark = await page.evaluate(() => ({
            bg: getComputedStyle(document.body).getPropertyValue('--bg-color').trim(),
            text: getComputedStyle(document.body).getPropertyValue('--text-color').trim(),
        }));
        expect(dark.bg).toBe('#10161d');
        expect(dark.text).toBe('#e6edf3');

        await page.locator('#rf-theme-light').check();
        const light = await page.evaluate(() => ({
            bg: getComputedStyle(document.body).getPropertyValue('--bg-color').trim(),
            text: getComputedStyle(document.body).getPropertyValue('--text-color').trim(),
        }));
        expect(light.bg).toBe('#ffffff');
    });

    test('the system radio follows colorScheme emulation', async ({page}) => {
        await openReport(page);

        await page.emulateMedia({colorScheme: 'dark'});
        // The system radio is already the default; a toggle re-emits so live
        // widgets redraw, and CSS applies the dark palette.
        await page.locator('#rf-theme-system').check();
        const dark = await page.evaluate(() => getComputedStyle(document.body).getPropertyValue('--bg-color').trim());
        expect(dark).toBe('#10161d');

        await page.emulateMedia({colorScheme: 'light'});
        const light = await page.evaluate(() => getComputedStyle(document.body).getPropertyValue('--bg-color').trim());
        expect(light).toBe('#ffffff');
    });

    test('the preference persists across reload', async ({page}) => {
        await page.emulateMedia({colorScheme: 'light'});
        await openReport(page);

        await page.locator('#rf-theme-dark').check();
        await page.reload();
        await expect(page.locator('#rf-theme-dark')).toBeChecked();
        const bg = await page.evaluate(() => getComputedStyle(document.body).getPropertyValue('--bg-color').trim());
        expect(bg).toBe('#10161d');
    });

    test('chart legend text re-resolves and the vizdom SVG re-renders with dark edge labels', async ({page}) => {
        await page.emulateMedia({colorScheme: 'light'});
        await openReport(page);

        const lightLegend = await page.evaluate(() => window.RF.resolveLegendTextColor());
        expect(lightLegend).toBe('#5c6b7a');

        // The vizdom ES module arrives asynchronously; wait for the inline SVG.
        await page.waitForSelector('#classGraph svg', {timeout: 20000});

        await page.locator('#rf-theme-dark').check();
        await page.waitForTimeout(300);

        const darkLegend = await page.evaluate(() => window.RF.resolveLegendTextColor());
        expect(darkLegend).toBe('#9fb0c0');

        // vizdom bakes the dark edge-label fontcolor into fill-only glyph paths:
        // the re-rendered SVG carries the muted label ink (as an attribute fill).
        const svgHasDarkInk = await page.evaluate(() => {
            const svg = document.querySelector('#classGraph svg');
            return svg !== null && /#9fb0c0|rgb\(159, 176, 192\)/i.test(svg.innerHTML);
        });
        expect(svgHasDarkInk).toBe(true);
    });

    test('zero console errors after repeated toggles', async ({page}) => {
        await page.emulateMedia({colorScheme: 'light'});
        const consoleErrors = await openReport(page);

        for (const mode of ['dark', 'light', 'dark', 'system', 'dark', 'light']) {
            await page.locator(`#rf-theme-${mode}`).check();
        }
        expect(consoleErrors).toEqual([]);
    });
});
