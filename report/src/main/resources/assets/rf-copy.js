// RefactorFirst interactive report copy-to-clipboard (Phase P6/T6.4): port of
// the page repo's table-operations.js copy section — the async Clipboard API
// with a textarea + execCommand fallback, never throwing, plus the polite
// status toast with message dedupe and the configured auto-dismiss.
(function () {
    'use strict';

    /** Copies text through the legacy DOM API when Clipboard API access is unavailable. */
    function fallbackCopy(text) {
        if (typeof document === 'undefined' || typeof document.execCommand !== 'function') {
            return false;
        }
        try {
            const textarea = document.createElement('textarea');
            textarea.value = text;
            textarea.setAttribute('readonly', '');
            textarea.style.position = 'absolute';
            textarea.style.left = '-9999px';
            document.body.appendChild(textarea);
            textarea.select();
            const ok = document.execCommand('copy');
            document.body.removeChild(textarea);
            return ok === true;
        } catch (error) {
            return false;
        }
    }

    /**
     * Copies cell text to the clipboard, preferring the async Clipboard API and
     * falling back to the legacy execCommand path. Never throws.
     */
    async function copyCellContent(text) {
        const value = String(text ?? '');
        const nav = (typeof window !== 'undefined' && window.navigator) || navigator;
        const clipboard = nav ? nav.clipboard : undefined;
        if (clipboard && typeof clipboard.writeText === 'function') {
            try {
                await clipboard.writeText(value);
                return true;
            } catch (error) {
                // fall through to the legacy path
            }
        }
        return fallbackCopy(value);
    }

    /** Creates (once) the polite status live region the toasts render into. */
    function ensureToastRegion() {
        let region = document.getElementById('rf-copy-toast');
        if (!region) {
            region = document.createElement('div');
            region.id = 'rf-copy-toast';
            region.setAttribute('role', 'status');
            region.setAttribute('aria-live', 'polite');
            region.className = 'rf-copy-toast';
            document.body.appendChild(region);
        }
        return region;
    }

    let activeToastTimer = null;
    let activeToastMessage = null;

    /**
     * Shows a toast message in the status region. A repeated message while the
     * toast is visible does not stack a second toast — the visible one stays
     * and its auto-dismiss timer restarts (message dedupe).
     */
    function showToast(message, durationMs) {
        const region = ensureToastRegion();
        const delay = durationMs ?? (window.RF.TABLE_CONFIG ? window.RF.TABLE_CONFIG.copy.toastDuration : 3000);
        if (activeToastMessage === message && activeToastTimer !== null) {
            window.clearTimeout(activeToastTimer);
        } else {
            region.textContent = message;
        }
        activeToastMessage = message;
        activeToastTimer = window.setTimeout(() => {
            region.textContent = '';
            activeToastTimer = null;
            activeToastMessage = null;
        }, delay);
    }

    window.RF.copyCellContent = copyCellContent;
    window.RF.fallbackCopy = fallbackCopy;
    window.RF.showToast = showToast;
    window.RF.ensureToastRegion = ensureToastRegion;
})();
