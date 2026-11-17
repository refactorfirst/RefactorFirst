// RefactorFirst report theme state (Phase P5/T5.2): reads and persists the
// localStorage['rf-theme'] preference, restores the checked radio at boot and
// publishes theme changes to subscribers (the chart and vizdom redraw hooks).
// The palette itself is selected purely in CSS via :has(#rf-theme-…:checked)
// plus prefers-color-scheme (rf-palette.css) — this module NEVER touches
// styles (plan D6: zero inline style writes).
(function () {
    'use strict';

    const STORAGE_KEY = 'rf-theme';
    const VALID_MODES = ['light', 'dark', 'system'];

    let listeners = [];

    /** Reads the persisted mode; invalid or unavailable values fall back to 'system'. */
    function readStoredMode() {
        try {
            const value = window.localStorage.getItem(STORAGE_KEY);
            return VALID_MODES.indexOf(value) >= 0 ? value : 'system';
        } catch (error) {
            return 'system';
        }
    }

    /** Persists the mode; storage failures (private mode) are non-fatal. */
    function persistMode(mode) {
        try {
            window.localStorage.setItem(STORAGE_KEY, mode);
        } catch (error) {
            // Storage unavailable: the radio state still governs this page view.
        }
    }

    /** The active mode as selected by the radios (system is the fallback). */
    function current() {
        const dark = document.getElementById('rf-theme-dark');
        const light = document.getElementById('rf-theme-light');
        if (dark && dark.checked) return 'dark';
        if (light && light.checked) return 'light';
        return 'system';
    }

    /** Emits the current mode to every subscriber. */
    function emit() {
        const mode = current();
        listeners.slice().forEach(listener => listener(mode));
    }

    /**
     * Restores the persisted mode into the radios and binds the change
     * listeners (radio changes and prefers-color-scheme changes while in
     * system mode). Idempotent per document.
     */
    function restore() {
        const mode = readStoredMode();
        const radio = document.getElementById('rf-theme-' + mode);
        if (radio) radio.checked = true;
        bind();
        return mode;
    }

    function bind() {
        if (document.documentElement.hasAttribute('data-rf-theme-bound')) return;
        document.documentElement.setAttribute('data-rf-theme-bound', '');

        document.addEventListener('change', event => {
            const target = event.target;
            if (target && target.name === 'rf-theme') {
                persistMode(target.value);
                emit();
            }
        });

        // While the system radio is active, an OS palette change is a theme
        // change: subscribers (canvas/graph redraws) must re-run.
        if (typeof window.matchMedia === 'function') {
            try {
                const mediaQuery = window.matchMedia('(prefers-color-scheme: dark)');
                const onSystemChange = () => {
                    if (current() === 'system') emit();
                };
                if (typeof mediaQuery.addEventListener === 'function') {
                    mediaQuery.addEventListener('change', onSystemChange);
                } else if (typeof mediaQuery.addListener === 'function') {
                    mediaQuery.addListener(onSystemChange);
                }
            } catch (error) {
                // Media query evaluation unavailable: explicit radios still emit.
            }
        }
    }

    /**
     * Subscribes to theme changes; returns an unsubscribe function. The
     * listener receives the new mode ('light' | 'dark' | 'system').
     */
    function onChange(listener) {
        listeners.push(listener);
        return () => {
            listeners = listeners.filter(entry => entry !== listener);
        };
    }

    window.RF.theme = {
        current: current,
        onChange: onChange,
        persistMode: persistMode,
        readStoredMode: readStoredMode,
        restore: restore
    };
})();
