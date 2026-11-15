// RefactorFirst interactive report core: the rf-data JSON reader, URL safety
// helpers, popup plumbing and section navigation. Ported from the page repo's
// lib/report-view.js (adapted: data arrives from the embedded rf-data JSON
// block instead of a fetch, and widget initializers live in rf-charts.js /
// rf-graphs.js). All CDN dependencies are optional — any failure degrades to
// the static rendered report.
(function () {
    'use strict';

    /**
     * Validates an untrusted URL from the report payload before it reaches
     * window.open: only absolute http:/https: URLs pass, so injected schemes
     * (javascript:, data:, file:, …) and malformed values are rejected.
     */
    function isHttpUrl(value) {
        if (typeof value !== 'string') return false;
        let parsed;
        try {
            parsed = new URL(value);
        } catch (error) {
            return false;
        }
        return parsed.protocol === 'http:' || parsed.protocol === 'https:';
    }

    /**
     * Builds a source-file URL from the report's repository URL and the path
     * relative to the project root. The payload lists only the path; repoUrl
     * is specified once in the project metadata.
     */
    function sourceUrl(repoUrl, path) {
        if (typeof repoUrl !== 'string' || typeof path !== 'string') return null;
        const base = repoUrl.endsWith('/') ? repoUrl : repoUrl + '/';
        const relative = path.startsWith('/') ? path.slice(1) : path;
        return base + relative.split('/').map(encodeURIComponent).join('/');
    }

    /**
     * Reads the embedded rf-data JSON block. Returns null when the block is
     * missing or malformed, so every consumer degrades to the static report.
     */
    function readRfData() {
        const block = document.getElementById('rf-data');
        if (!block) return null;
        try {
            return JSON.parse(block.textContent);
        } catch (error) {
            console.warn('rf-data payload unreadable:', error);
            return null;
        }
    }

    // ---------------------------------------------------------------------------
    // Popup helpers. The template carries only data-* attributes (inline
    // handlers are untrusted), so handlers attach here after rendering.
    // ---------------------------------------------------------------------------

    // The element focused when the popup opened, restored on close (WCAG 2.4.3).
    let popupTriggerElement = null;

    /** Focus-traps Tab inside the given popup (WCAG 2.4.3: focus order). */
    function bindFocusTrap(popup) {
        if (popup.__rfFocusTrapBound) return;
        popup.__rfFocusTrapBound = true;
        popup.addEventListener('keydown', event => {
            if (event.key !== 'Tab') return;
            const focusable = popup.querySelectorAll(
                'button, [href], input, select, textarea, [tabindex]:not([tabindex="-1"])');
            if (focusable.length === 0) return;
            const first = focusable[0];
            const last = focusable[focusable.length - 1];
            if (event.shiftKey && document.activeElement === first) {
                event.preventDefault();
                last.focus();
            } else if (!event.shiftKey && document.activeElement === last) {
                event.preventDefault();
                first.focus();
            }
        });
    }

    function openPopup(popupId) {
        const overlay = document.getElementById('overlay');
        const popup = document.getElementById(popupId);
        if (!overlay || !popup) return false;
        popupTriggerElement = document.activeElement instanceof HTMLElement ? document.activeElement : null;
        overlay.style.display = 'block';
        popup.style.display = 'block';
        bindFocusTrap(popup);
        // Move focus into the popup: the close control is the safest first stop.
        const close = popup.querySelector('.close-btn');
        if (close) close.focus();
        return true;
    }

    const popupRenderers = new Map();
    function disposePopupRenderer(container) {
        const teardown = popupRenderers.get(container);
        popupRenderers.delete(container);
        if (teardown) teardown();
        while (container.firstChild) container.removeChild(container.firstChild);
    }

    function retainPopupRenderer(container, teardown) {
        popupRenderers.set(container, teardown);
    }

    function showPopup(popupId, containerName, dot) {
        if (!openPopup(popupId)) return;
        const container = document.getElementById(containerName);
        if (!container) return;
        disposePopupRenderer(container);
        if (window.graphlibDot && window.graphology && window.Sigma) {
            const graph = window.RF.buildGraphologyGraph(dot);
            const instance = new window.Sigma(graph, document.getElementById(containerName), window.RF.sigmaPopupSettings());
            retainPopupRenderer(container, () => instance.kill());
            window.RF.bindSigmaHoverHighlight(instance, graph);
        }
    }

    function hidePopup() {
        const overlay = document.getElementById('overlay');
        if (!overlay) return;
        overlay.style.display = 'none';
        for (const popup of document.getElementsByClassName('popup')) {
            popup.style.display = 'none';
        }
        // Clear graph containers to free the previous renderings
        document.querySelectorAll('[id^="graph-container"]').forEach(container => {
            disposePopupRenderer(container);
        });
        // Restore focus to the trigger (WCAG 2.4.3: focus order).
        if (popupTriggerElement && document.contains(popupTriggerElement)) {
            popupTriggerElement.focus();
        }
        popupTriggerElement = null;
    }

    function bindPopupHandlers(root) {
        const scope = root || document;
        scope.querySelectorAll('[data-popup-2d], [data-popup-3d]').forEach(button => {
            button.addEventListener('click', () => {
                const popup = button.getAttribute('data-popup');
                const container = button.getAttribute('data-container');
                const dotVar = button.getAttribute('data-dot-var');
                if (!popup || !container || !document.getElementById(popup)) return;
                const dot = window.RF.graphDot(dotVar);
                if (button.hasAttribute('data-popup-3d')) {
                    window.RF.createForceGraph(popup, container, dot);
                } else {
                    showPopup(popup, container, dot);
                }
            });
        });
        scope.querySelectorAll('[data-popup-close]').forEach(closer => {
            closer.addEventListener('click', hidePopup);
        });
    }

    // ---------------------------------------------------------------------------
    // Section navigation: explicit scrolling because re-clicking the current
    // fragment is otherwise a native no-op.
    // ---------------------------------------------------------------------------

    function findSectionTarget(hash) {
        if (!hash || hash === '#') return null;
        const fragment = hash.slice(1);
        const literalTarget = document.getElementById(fragment);
        if (literalTarget) return literalTarget;
        let decodedFragment;
        try {
            decodedFragment = decodeURIComponent(fragment);
        } catch (error) {
            return null;
        }
        return document.getElementById(decodedFragment);
    }

    function bindSectionNavLinks(root) {
        const scope = root || document;
        const guard = scope === document ? scope.documentElement : scope;
        if (guard.hasAttribute('data-rf-nav-bound')) return;
        guard.setAttribute('data-rf-nav-bound', '');
        scope.addEventListener('click', event => {
            if (event.defaultPrevented || event.button !== 0 ||
                event.metaKey || event.ctrlKey || event.shiftKey || event.altKey) return;
            const link = typeof event.target.closest === 'function'
                ? event.target.closest('a[href^="#"]')
                : null;
            if (!link || (scope !== document && !scope.contains(link))) return;
            const destination = link.getAttribute('target');
            if (destination !== null && destination !== '_self') return;
            const href = link.getAttribute('href');
            const target = findSectionTarget(href);
            if (!target) return;
            event.preventDefault();
            if (window.location.hash !== href) {
                history.pushState(null, '', href);
            }
            target.scrollIntoView();
            if (typeof target.focus === 'function') {
                if (!target.hasAttribute('tabindex') && target.tabIndex < 0) {
                    target.setAttribute('tabindex', '-1');
                    target.addEventListener('blur', () => target.removeAttribute('tabindex'), {once: true});
                }
                target.focus({preventScroll: true});
            }
        });
    }

    /**
     * Announces a failed CDN widget in a visible, polite live region instead of
     * failing silently (T7.5): offline/proxy-blocked widgets degrade to the
     * static report and the reader learns which view is unavailable.
     */
    function announceWidgetFailure(widgetName) {
        let region = document.getElementById('rf-widget-notice');
        if (!region) {
            region = document.createElement('div');
            region.id = 'rf-widget-notice';
            region.setAttribute('role', 'status');
            region.setAttribute('aria-live', 'polite');
            region.className = 'rf-widget-notice';
            document.body.appendChild(region);
        }
        const line = document.createElement('p');
        line.textContent =
            'The interactive ' + widgetName
            + ' view could not be loaded (network unavailable or blocked). The tables above carry the same data.';
        region.appendChild(line);
    }

    const RF = {
        data: null,
        isHttpUrl: isHttpUrl,
        sourceUrl: sourceUrl,
        readRfData: readRfData,
        openPopup: openPopup,
        disposePopupRenderer: disposePopupRenderer,
        retainPopupRenderer: retainPopupRenderer,
        showPopup: showPopup,
        hidePopup: hidePopup,
        bindPopupHandlers: bindPopupHandlers,
        bindSectionNavLinks: bindSectionNavLinks,
        announceWidgetFailure: announceWidgetFailure,

        /** Resolves a template button's data-dot-var against the rf-data payload:
         * the DOT synthesizes client-side from the inline hierarchy payloads
         * (rf-hierarchy-dot.js — byte-identical to the Java NestedDotBuilder);
         * the Java-built parity bridge is gone (P7/T7.6). */
        graphDot: function (dotVar) {
            if (!this.data || typeof dotVar !== 'string') return null;
            const key = dotVar.replace(/_dot$/, '');
            const data = this.data;
            const options = Object.assign(
                this.graphDotOptions ? this.graphDotOptions() : {},
                {repoUrl: this.repoUrl()});
            if (key === 'classGraph' && data.classMap && data.classMap.hierarchy) {
                return this.hierarchyToDot(data.classMap.hierarchy, options);
            }
            if (key === 'packageGraph' && data.packageMap && data.packageMap.hierarchy) {
                return this.packageHierarchyToDot(data.packageMap.hierarchy, options);
            }
            const cycle = data.classCycles && data.classCycles.largestCycle;
            if (cycle && cycle.cycleIdentifier === key && cycle.hierarchy) {
                return this.hierarchyToDot(cycle.hierarchy, options);
            }
            return null;
        },

        /** Theme hook: P5 replaces this stub with palette-aware chart/graph redraws. */
        redrawCharts: function () {},

        /** The repo URL prefix for source hyperlinks, or '' when unavailable. */
        repoUrl: function () {
            return this.data && this.data.project && typeof this.data.project.repoUrl === 'string'
                ? this.data.project.repoUrl
                : '';
        }
    };

    window.RF = RF;
})();
