const { test, expect } = require('@playwright/test');
const { pathToFileURL } = require('node:url');
const { readFileSync } = require('node:fs');
const path = require('node:path');

// Phase P8/T8.4: axe-core scans (WCAG 2.2 AA ruleset) — both reports, light and
// dark themes, before and after interacting (filter applied, popup open, theme
// dark) — zero violations. The 24px target-size rule is asserted via computed
// styles on the interactive controls (SC 2.5.8).

const INTERACTIVE_PATH = path.resolve(__dirname, '..', '..', 'target', 'e2e', 'refactor-first-report.junit4.html');
const SIMPLE_PATH = path.resolve(__dirname, '..', '..', 'target', 'e2e', 'refactor-first-report.simple.html');

function loadAxeSource() {
    return readFileSync(require.resolve('axe-core/axe.min.js'), 'utf8');
}

async function scan(page) {
    await page.addScriptTag({content: loadAxeSource()});
    return page.evaluate(() => axe.run(document, {
        resultTypes: ['violations'],
    }));
}

async function open(page, reportPath) {
    const consoleErrors = [];
    page.on('console', (message) => {
        if (message.type() === 'error') consoleErrors.push(message.text());
    });
    page.on('pageerror', (error) => consoleErrors.push(String(error)));
    await page.goto(pathToFileURL(reportPath).href);
    return consoleErrors;
}

function expectNoViolations(results, context) {
    const summary = results.violations.map(violation => ({
        id: violation.id,
        impact: violation.impact,
        nodes: violation.nodes.slice(0, 3).map(node => node.target.join(' ')),
    }));
    expect(summary, context).toEqual([]);
}

test.describe('wcag axe', () => {
    test('interactive report (light): zero violations before and after interacting', async ({ page }) => {
        page.emulateMedia({colorScheme: 'light'});
        const consoleErrors = await open(page, INTERACTIVE_PATH);

        expectNoViolations(await scan(page), 'interactive light, before interaction');

        // Interact: apply a filter, sort, open a popup.
        await page.locator('#rf-search-class-relationships').fill('junit');
        await page.waitForTimeout(500);
        expectNoViolations(await scan(page), 'interactive light, filter applied');

        await page.locator('button[data-popup-2d][data-popup="popup-classGraph"]').click();
        await expect(page.locator('#popup-classGraph')).toBeVisible();
        expectNoViolations(await scan(page), 'interactive light, popup open');
        await page.locator('#popup-classGraph .close-btn').click();

        expect(consoleErrors).toEqual([]);
    });

    test('interactive report (dark): zero violations after the theme switch', async ({ page }) => {
        page.emulateMedia({colorScheme: 'light'});
        const consoleErrors = await open(page, INTERACTIVE_PATH);

        await page.locator('#rf-theme-dark').check();
        await page.waitForTimeout(500);
        expectNoViolations(await scan(page), 'interactive dark, theme switched');
        expect(consoleErrors).toEqual([]);
    });

    test('simple report (light + dark): zero violations', async ({ page }) => {
        page.emulateMedia({colorScheme: 'light'});
        const consoleErrors = await open(page, SIMPLE_PATH);
        expectNoViolations(await scan(page), 'simple light');

        page.emulateMedia({colorScheme: 'dark'});
        await page.waitForTimeout(300);
        expectNoViolations(await scan(page), 'simple dark (system)');
        expect(consoleErrors).toEqual([]);
    });

    test('interactive controls meet the 24px minimum target size (SC 2.5.8)', async ({ page }) => {
        await open(page, INTERACTIVE_PATH);

        const undersized = await page.evaluate(() => {
            const selectors = [
                'button[data-popup-2d]',
                'button[data-popup-3d]',
                '.rf-export-btn',
                '.rf-page-btn',
                '.theme-option',
            ];
            const undersized = [];
            for (const selector of selectors) {
                const elements = Array.from(document.querySelectorAll(selector))
                    .filter(element => element.checkVisibility());
                if (elements.length === 0) undersized.push(`${selector}: no visible controls`);
                for (const [index, element] of elements.entries()) {
                    const box = element.getBoundingClientRect();
                    if (box.width < 24 || box.height < 24) {
                        undersized.push(`${selector}[${index}]: ${box.width}x${box.height}`);
                    }
                }
            }
            // The close button lives inside a hidden popup; measure it while open.
            const popupButton = document.querySelector('button[data-popup-2d][data-popup="popup-classGraph"]');
            if (popupButton) {
                popupButton.click();
                const close = document.querySelector('#popup-classGraph .close-btn');
                if (close) {
                    const box = close.getBoundingClientRect();
                    if (box.width < 24 || box.height < 24) {
                        undersized.push(`.close-btn: ${box.width}x${box.height}`);
                    }
                }
                window.RF.hidePopup();
            }
            return undersized;
        });
        expect(undersized).toEqual([]);
    });
});
