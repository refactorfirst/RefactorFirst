// RefactorFirst interactive report boot (the "main JS" asset). Reads the
// embedded rf-data payload, exposes the popup globals for debugging, binds
// the data-attribute handlers and initializes the optional CDN widgets.
// Every step is defensive: a missing payload or unavailable widget leaves
// the server-rendered report fully readable.
(function () {
    'use strict';

    /**
     * Waits for the vizdom WASM bridge to materialize window.Vizdom (the ES
     * module initializes asynchronously, racing DOMContentLoaded), then runs
     * the callback. Gives up after ~20s — vizdom is an optional CDN dependency
     * and the report degrades to the static tables.
     */
    function whenVizdomReady(callback, attempts = 200) {
        if (window.Vizdom && window.Vizdom.DotParser) {
            callback();
            return;
        }
        if (attempts <= 0) {
            console.warn('vizdom did not become ready; skipping inline graphs');
            if (window.RF && window.RF.announceWidgetFailure) window.RF.announceWidgetFailure('class and package maps');
            return;
        }
        setTimeout(() => whenVizdomReady(callback, attempts - 1), 100);
    }

    function boot() {
        const RF = window.RF;
        if (!RF) return;
        RF.data = RF.readRfData();

        // Popups are interactive via data attributes; globals stay available
        // for compatibility/debugging.
        window.showPopup = RF.showPopup;
        window.hidePopup = RF.hidePopup;
        window.createForceGraph = RF.createForceGraph;

        // Bind once: re-adding the listener would stack duplicate handlers.
        if (!document.documentElement.hasAttribute('data-rf-esc-bound')) {
            document.documentElement.setAttribute('data-rf-esc-bound', '');
            document.addEventListener('keydown', event => {
                if (event.key === 'Escape') RF.hidePopup();
            });
        }

        RF.bindPopupHandlers(document);
        RF.bindSectionNavLinks(document);

        // Reload the GitHub buttons script so the badges render. The head
        // already declares it; this re-trigger covers sanitizers that strip
        // async head scripts.
        if (!document.querySelector('script[src*="buttons.github.io"]')) {
            const script = document.createElement('script');
            script.src = 'https://buttons.github.io/buttons.js';
            script.async = true;
            script.defer = true;
            document.body.appendChild(script);
        }

        if (!RF.data) return;

        // Bootstrap order (T5.5/T6.6): theme first (restores the persisted mode
        // and binds the pub-sub), then tables, then charts and graphs so their
        // redraw hooks are subscribed before any user interaction.
        if (RF.theme && typeof RF.theme.restore === 'function') {
            RF.theme.restore();
        }
        if (RF.bindThemeChartRedraw) RF.bindThemeChartRedraw();
        if (typeof RF.redrawGraphs === 'function' && !document.documentElement.hasAttribute('data-rf-graph-redraw-bound')) {
            document.documentElement.setAttribute('data-rf-graph-redraw-bound', '');
            if (RF.theme && typeof RF.theme.onChange === 'function') {
                RF.theme.onChange(() => RF.redrawGraphs());
            }
        }

        // Tables: capture the server-rendered rows, apply the default sort and
        // wire the toolbar controls (search, sort, pagination, export, copy).
        try {
            if (RF.enhanceTables) {
                RF.enhanceTables(document, {
                    data: RF.data,
                    onCopy: ok => RF.showToast(ok ? 'Copied to clipboard' : 'Copy failed'),
                });
            }
        } catch (error) {
            console.warn('Table enhancement failed:', error);
        }

        if (window.Chart) {
            try {
                RF.initDisharmonyCharts(RF.data.disharmonies, RF.repoUrl());
            } catch (error) {
                console.warn('Chart initialization failed:', error);
            }
        }

        try {
            whenVizdomReady(() => RF.initWasmGraphs(RF.data));
        } catch (error) {
            console.warn('Graph initialization failed:', error);
        }
    }

    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', boot);
    } else {
        boot();
    }
})();
