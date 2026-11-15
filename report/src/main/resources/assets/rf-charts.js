// RefactorFirst interactive report charts: Chart.js bubble charts per disharmony.
// Ported from the page repo's lib/report-view.js chart section (adapted: no SPA
// re-render registry churn — the generated report renders once). Chart.js is an
// optional CDN dependency; a missing library degrades to the static report.
(function () {
    'use strict';

    // Legend text colors mirroring the light palette's muted color. The chart
    // canvas is transparent, so legend text sits on the page background; the
    // value keeps >= 4.5:1 contrast (WCAG 1.4.3). P5 theme support re-resolves
    // this per palette.
    const CHART_LEGEND_TEXT = Object.freeze({
        light: '#5c6b7a',
        dark: '#9fb0c0'
    });

    // U+27F6 LONG RIGHTWARDS ARROW: suffixed onto every chart axis title so
    // the direction of each axis reads at a glance.
    const AXIS_ARROW = ' \u27F6';

    // Live Chart.js instances by canvas, so theme changes can redraw the
    // canvas-drawn legend/axis text (the palette switch itself is pure CSS).
    const bubbleCharts = new Map();

    /** The active theme: the explicit radios win, otherwise prefers-color-scheme. */
    function activeChartTheme() {
        const doc = typeof document === 'undefined' ? null : document;
        if (doc) {
            const dark = doc.getElementById('rf-theme-dark');
            const light = doc.getElementById('rf-theme-light');
            if (dark && dark.checked) return 'dark';
            if (light && light.checked) return 'light';
        }
        if (typeof window.matchMedia === 'function') {
            try {
                if (window.matchMedia('(prefers-color-scheme: dark)').matches) return 'dark';
            } catch (error) {
                // Media query evaluation unavailable: fall through to light.
            }
        }
        return 'light';
    }

    function resolveLegendTextColor() {
        return CHART_LEGEND_TEXT[activeChartTheme()] || CHART_LEGEND_TEXT.light;
    }

    /**
     * Redraws every live bubble chart so legend/axis text re-resolves against
     * the newly active theme. Entries whose canvas left the DOM are destroyed
     * (releasing their Chart.js resources) and pruned instead of redrawn.
     */
    function redrawChartsForTheme() {
        for (const [canvas, chart] of bubbleCharts) {
            if (!canvas.isConnected) {
                if (chart && typeof chart.destroy === 'function') chart.destroy();
                bubbleCharts.delete(canvas);
                continue;
            }
            if (chart && typeof chart.update === 'function') chart.update();
        }
    }

    /**
     * Subscribes the chart redraws to the theme module's changes: radio
     * switches and prefers-color-scheme changes (while in system mode) both
     * re-resolve the canvas-drawn text colors. Idempotent per document.
     */
    function bindThemeChartRedraw() {
        const doc = typeof document === 'undefined' ? null : document;
        if (!doc || doc.documentElement.hasAttribute('data-rf-theme-redraw-bound')) return;
        doc.documentElement.setAttribute('data-rf-theme-redraw-bound', '');
        if (window.RF && window.RF.theme && typeof window.RF.theme.onChange === 'function') {
            window.RF.theme.onChange(() => redrawChartsForTheme());
        }
    }

    function axisTitle(label) {
        return label + AXIS_ARROW;
    }

    /**
     * Creates a disharmony bubble chart. Clicking a bubble with a URL opens its
     * source in a new tab with noopener. Warns and returns if Chart.js is unavailable.
     */
    function initBubbleChart(canvas, title, chartData, repoUrl) {
        const ChartLib = window.Chart;
        if (!ChartLib) {
            console.warn('Chart.js not loaded');
            return;
        }
        const bubbles = chartData.bubbles || [];
        const chart = new ChartLib(canvas.getContext('2d'), {            type: 'bubble',
            data: {
                datasets: [{
                    label: title,
                    // The constructed repoUrl + path URL wins; a payload url is kept as a
                    // legacy fallback when no URL can be constructed, and null only when
                    // neither exists (the click/hover handlers still gate on isHttpUrl).
                    data: bubbles.map(b => ({
                        x: b.x,
                        y: b.y,
                        r: b.r,
                        raw: Object.assign({}, b, {url: window.RF.sourceUrl(repoUrl, b.path) || b.url || null})
                    })),
                    backgroundColor: bubbles.map(b => b.color),
                    borderColor: bubbles.map(b => b.borderColor),
                    borderWidth: 1
                }]
            },
            options: {
                responsive: true,
                maintainAspectRatio: false,
                // Clicking a bubble opens the source file the report links it to
                // in a new tab, with noopener so the opened page can't reach
                // back into this one. The url comes from the report payload,
                // which is untrusted: only parsed http(s) URLs may reach
                // window.open, so a malicious javascript:/data: value can't
                // execute when the bubble is clicked.
                onClick: (event, elements, chart) => {
                    const hit = elements && elements[0];
                    if (!hit || !chart || !chart.data || !chart.data.datasets) return;
                    const point = chart.data.datasets[hit.datasetIndex];
                    const raw = point && point.data && point.data[hit.index] ? point.data[hit.index].raw : null;
                    const url = raw ? raw.url : null;
                    if (window.RF.isHttpUrl(url) && typeof window.open === 'function') {
                        window.open(url, '_blank', 'noopener');
                    }
                },
                // Pointer affordance while the cursor is over a bubble — only
                // when the hovered bubble carries a URL the click handler would
                // actually open (isHttpUrl), so the hover hint can never
                // promise a navigation the click refuses to perform.
                onHover: (event, elements, chart) => {
                    if (!event || !event.native || !event.native.target) return;
                    const hit = elements && elements[0];
                    const dataset = hit && chart && chart.data && chart.data.datasets
                        ? chart.data.datasets[hit.datasetIndex]
                        : null;
                    const raw = dataset && dataset.data && dataset.data[hit.index]
                        ? dataset.data[hit.index].raw
                        : null;
                    event.native.target.style.cursor = hit && window.RF.isHttpUrl(raw ? raw.url : null)
                        ? 'pointer'
                        : 'default';
                },
                plugins: {
                    legend: {
                        display: true,
                        position: 'bottom',
                        labels: {
                            font: {size: 12},
                            padding: 15,
                            boxWidth: 20,
                            boxHeight: 20,
                            // Chart.js renders each legend item's text from
                            // item.fontColor; labels.color only feeds the DEFAULT
                            // generateLabels, which this chart overrides.
                            generateLabels: () =>
                                ['High Priority (1)', 'Medium Priority', 'Low Priority (max)'].map((label, i) => ({
                                    text: label,
                                    fillStyle: ['rgb(235, 64, 52)', 'rgb(137, 119, 74)', 'rgb(39, 174, 96)'][i],
                                    strokeStyle: ['rgb(235, 64, 52)', 'rgb(137, 119, 74)', 'rgb(39, 174, 96)'][i],
                                    fontColor: resolveLegendTextColor(),
                                    lineWidth: 1,
                                    hidden: false,
                                    index: i
                                }))
                        }
                    },
                    tooltip: {
                        callbacks: {
                            label: context => {
                                const raw = context.raw.raw;
                                const lines = [
                                    'File: ' + raw.label,
                                    'Priority: ' + raw.priority,
                                    'Effort Rank: ' + raw.x,
                                    'Change Proneness Rank: ' + raw.y
                                ];
                                // Same gate as the click handler: the prompt only
                                // appears when the url is actually openable.
                                if (window.RF.isHttpUrl(raw.url)) lines.push('Click to open source file');
                                return lines;
                            }
                        }
                    }
                },
                scales: {
                    x: {
                        title: {
                            display: true,
                            text: axisTitle(chartData.xaxisLabel || 'Effort to refactor'),
                            color: () => resolveLegendTextColor()
                        },
                        ticks: {color: () => resolveLegendTextColor()},
                        grid: {color: '#e0e0e0'}
                    },
                    y: {
                        title: {
                            display: true,
                            text: axisTitle(chartData.yaxisLabel || 'Relative churn (impact)'),
                            color: () => resolveLegendTextColor()
                        },
                        ticks: {color: () => resolveLegendTextColor()},
                        grid: {color: '#e0e0e0'}
                    }
                }
            }
        });
        bubbleCharts.set(canvas, chart);
        return chart;
    }

    function initDisharmonyCharts(disharmonies, repoUrl) {
        (disharmonies || []).forEach(d => {
            const canvas = document.getElementById('chart_' + d.anchorId);
            if (canvas && d.chart && d.chart.bubbles) {
                initBubbleChart(canvas, d.title, d.chart, repoUrl);
            }
        });
    }

    window.RF.initBubbleChart = initBubbleChart;
    window.RF.initDisharmonyCharts = initDisharmonyCharts;
    window.RF.resolveLegendTextColor = resolveLegendTextColor;
    window.RF.activeChartTheme = activeChartTheme;
    window.RF.redrawCharts = redrawChartsForTheme;
    window.RF.bindThemeChartRedraw = bindThemeChartRedraw;
})();
