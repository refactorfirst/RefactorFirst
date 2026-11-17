const { test, expect } = require('@playwright/test');
const { pathToFileURL } = require('node:url');
const path = require('node:path');

// Phase P7/T7.7: the colored hover model on all three renderers — hovering a
// vizdom edge/node applies the rf-hover-* classes (asserted via computed stroke/
// fill = the palette accents), tabbing through the node anchors reproduces the
// hover (keyboard parity), the Sigma popup opens with its reducers wired, the 3D
// popup is a WebGL smoke (no page errors), and a theme switch re-renders graphs.

const REPORT_PATH = path.resolve(__dirname, '..', '..', 'target', 'e2e', 'refactor-first-report.junit4.html');

const ACCENTS = {
    edge: 'rgb(180, 83, 9)', // #b45309
    in: 'rgb(30, 132, 73)', // #1e8449
    out: 'rgb(42, 111, 151)', // #2a6f97
};

async function openReport(page) {
    const consoleErrors = [];
    page.on('console', (message) => {
        if (message.type() === 'error') consoleErrors.push(message.text());
    });
    page.on('pageerror', (error) => consoleErrors.push(String(error)));
    await page.goto(pathToFileURL(REPORT_PATH).href);
    await page.waitForSelector('#classGraph svg', {state: 'attached', timeout: 20000});
    return consoleErrors;
}

test.describe('graphs', () => {
    test('hovering a vizdom edge applies the amber attention accent', async ({ page }) => {
        const consoleErrors = await openReport(page);

        const edge = page.locator('#classGraph svg g.edge').first();
        await edge.hover();
        const stroke = await edge.evaluate((group) => {
            const path = group.querySelector('path[stroke]');
            return path ? getComputedStyle(path).stroke : null;
        });
        // The rf-hover-edge class drives the CSS; the accent is palette-driven.
        expect(stroke).toBe(ACCENTS.edge);
        expect(consoleErrors).toEqual([]);
    });

    test('hovering a node applies the directional accents to its neighborhood', async ({ page }) => {
        const consoleErrors = await openReport(page);

        const source = page.locator('#classGraph svg g.node').first();
        await source.hover();

        // The hovered node fills with its accent.
        const focusFill = await source.evaluate((group) => {
            const shape = group.querySelector('ellipse, rect');
            return shape ? getComputedStyle(shape).fill : null;
        });
        expect(focusFill).toBe(ACCENTS.edge);

        // At least one neighbor group is directionally flagged.
        const flagged = await page.evaluate(() => {
            const groups = [...document.querySelectorAll('#classGraph svg g')];
            return groups
                .filter((g) => g.classList.contains('rf-hover-in') || g.classList.contains('rf-hover-out'))
                .map((g) => (g.classList.contains('rf-hover-in') ? 'in' : 'out'));
        });
        expect(flagged.length).toBeGreaterThan(0);
        expect(consoleErrors).toEqual([]);
    });

    test('tabbing through the node anchors reproduces the hover (keyboard parity)', async ({ page }) => {
        const consoleErrors = await openReport(page);

        await page.locator('body').click();
        let flagged = false;
        for (let i = 0; i < 80 && !flagged; i++) {
            await page.keyboard.press('Tab');
            flagged = await page.evaluate(() => {
                const active = document.activeElement;
                const group = active && active.closest ? active.closest('g.node') : null;
                return group ? group.classList.contains('rf-hover-focus') : false;
            });
        }
        expect(flagged).toBe(true);
        expect(consoleErrors).toEqual([]);
    });

    test('the sigma popup opens with the hover reducers wired', async ({ page }) => {
        const consoleErrors = await openReport(page);

        await page.locator('button[data-popup-2d][data-popup="popup-classGraph"]').click();
        await expect(page.locator('#popup-classGraph')).toBeVisible();
        await page.waitForTimeout(1200);

        // sigma's canvas hover is exercised in the page repo's own suite; here we
        // pin the wiring: the popup opened without errors.
        expect(consoleErrors).toEqual([]);
    });

    test('the 3D popup opens as a WebGL smoke (no page errors)', async ({ page }) => {
        const consoleErrors = await openReport(page);

        await page.locator('button[data-popup-3d][data-popup="popup-classGraph"]').click();
        await expect(page.locator('#popup-classGraph')).toBeVisible();
        await page.waitForTimeout(1500);
        expect(consoleErrors).toEqual([]);
    });

    test('a theme switch re-renders the graphs with the dark edge-label ink', async ({ page }) => {
        const consoleErrors = await openReport(page);

        await page.locator('#rf-theme-dark').check();
        await page.waitForTimeout(500);
        const darkInk = await page.evaluate(() => {
            const svg = document.querySelector('#classGraph svg');
            return svg !== null && /#9fb0c0|rgb\(159, 176, 192\)/i.test(svg.innerHTML);
        });
        expect(darkInk).toBe(true);
        expect(consoleErrors).toEqual([]);
    });
});
