// RefactorFirst interactive report inline graphs: vizdom WASM rendering of the
// class map, package map and cycle map as pan/zoom-able SVGs, with palette
// redraws. Split from the page repo's lib/report-view.js vizdom section
// (adapted: the P4 parity bridge renders the Java-built DOT shipped in the
// rf-data payload; a hierarchy in the payload re-synthesizes on theme redraws
// via rf-hierarchy-dot.js, baking the dark edge-label color vizdom needs —
// labels are fill-only glyph paths, unreachable by CSS). vizdom is an optional
// CDN dependency; a missing parser degrades to the static report.
(function () {
    'use strict';

    // Rendered graph entries keyed by container id: `{hierarchy, dot, repoUrl}`.
    // A hierarchy entry re-synthesizes its DOT on every redraw (the dark palette
    // bakes a different edge-label color), while a legacy entry — a payload with
    // only the raw Java-built DOT and no hierarchy — re-parses that DOT verbatim,
    // so the report renders consistently on both palettes. Entries whose
    // container left the DOM are pruned instead of redrawn.
    const wasmGraphHierarchies = new Map();
    const panZoomInstances = new Map();

    function destroyPanZoom(containerId) {
        const instance = panZoomInstances.get(containerId);
        panZoomInstances.delete(containerId);
        if (instance) instance.destroy();
    }

    /**
     * Synthesis options for the active palette: the dark palette bakes the muted
     * edge-label color into the DOT (vizdom renders labels as fill-only glyph
     * paths at parse time, beyond CSS reach), the light palette keeps vizdom's
     * default black. Only edge labels need that synthesis-time color: node
     * labels sit on white shapes in both palettes and keep the baked black ink.
     */
    function graphDotOptions() {
        return {
            edgeFontColor: window.RF.activeChartTheme() === 'dark' ? '#9fb0c0' : ''
        };
    }

    /**
     * Parses the graph's DOT (recolored for the active palette) into the graph
     * container's innerHTML and re-wires pan/zoom on the freshly injected SVG.
     * The entry carries either a hierarchy to synthesize from — the package map
     * renders its flat ellipse DOT, the class and cycle maps their nested
     * cluster DOT, and every node with a source path is hyperlinked through the
     * repository URL (http(s)-gated) — or the report's own raw DOT from legacy
     * payloads without a hierarchy, which is parsed verbatim.
     */
    function sanitizeSvgMarkup(svgMarkup) {
        if (typeof svgMarkup !== 'string' || !svgMarkup.trim()) throw new Error('Missing SVG');
        if (typeof DOMParser === 'undefined' || typeof XMLSerializer === 'undefined') throw new Error('SVG parser unavailable');
        try {
            const svgDoc = new DOMParser().parseFromString(svgMarkup, 'image/svg+xml');
            const svgRoot = svgDoc.documentElement;
            if (!svgRoot || svgRoot.localName !== 'svg' || svgRoot.namespaceURI !== 'http://www.w3.org/2000/svg' || svgDoc.querySelector('parsererror')) throw new Error('Invalid SVG');
            svgRoot.querySelectorAll('*').forEach(node => {
                Array.from(node.attributes || []).forEach(attr => {
                    const name = attr.name.toLowerCase();
                    if (/^on/i.test(name)) {
                        node.removeAttribute(attr.name);
                    } else if ((name === 'href' || name === 'xlink:href') && /^javascript:/i.test(attr.value || '')) {
                        node.removeAttribute(attr.name);
                    }
                });
                if (node.tagName && /^script$/i.test(node.tagName)) {
                    node.remove();
                }
            });
            svgRoot.querySelectorAll('a[href], a[xlink\\:href]').forEach(anchor => {
                anchor.setAttribute('target', '_blank');
                if (!anchor.hasAttribute('rel')) {
                    anchor.setAttribute('rel', 'noopener noreferrer');
                }
            });
            return new XMLSerializer().serializeToString(svgRoot);
        } catch (error) {
            console.warn('SVG sanitization failed:', error);
            throw error;
        }
    }

    function renderGraphInto(parser, el, containerId, entry) {
        let dotString;
        if (entry.hierarchy) {
            const synthesis = containerId === 'packageGraph'
                ? window.RF.packageHierarchyToDot
                : window.RF.hierarchyToDot;
            dotString = synthesis(entry.hierarchy, Object.assign(graphDotOptions(), {repoUrl: entry.repoUrl || ''}));
        } else if (entry.dot) {
            // Legacy payloads ship only the raw Java-built DOT with no hierarchy
            // to synthesize from: parse it verbatim — no palette fontcolor is
            // baked in, so it renders the same on every theme.
            dotString = entry.dot;
        } else {
            return;
        }
        const parsed = parser.parse(dotString).to_directed().layout();
        let svg = parsed.to_svg().to_string();
        svg = sanitizeSvgMarkup(svg);
        svg = svg.replace('<svg ', '<svg class="fullscreen-svg" ');
        destroyPanZoom(containerId);
        el.innerHTML = svg;
        bindGraphHoverHighlights(el);
        if (window.svgPanZoom) {
            panZoomInstances.set(containerId, window.svgPanZoom('#' + containerId + ' svg', {zoomEnabled: true, controlIconsEnabled: true}));
        }
    }

    // ---------------------------------------------------------------------------
    // DOT-graph hover highlights. vizdom writes every node and edge as a group
    // carrying a <title> with the DOT node ids ("TestCase" / "TestCase ->
    // TestResult"), so the neighbor model derives from the rendered SVG:
    // hovering an edge flags the edge plus both endpoint nodes (the attention
    // accent), hovering a node flags its incoming edges + source nodes and its
    // outgoing edges + sink nodes in two directional states. The rf-hover-*
    // state classes are styled from the palette (rf-graphs.css); ids are
    // producer-contract word characters, and an edge title that fails to parse
    // simply binds no highlight. Node anchors are real tab stops, so keyboard
    // focus re-uses the node hover state (SC 2.1.1 parity for the pointer-only
    // affordance); edges carry no tab stop — the highlight is a pointer
    // courtesy, the relationships stay available in the report tables.
    // ---------------------------------------------------------------------------

    /** The state classes the hover model toggles on g.node/g.edge groups. */
    const GRAPH_HOVER_STATES = ['rf-hover-edge', 'rf-hover-in', 'rf-hover-out', 'rf-hover-focus'];

    /**
     * Binds the hover/focus highlight model to the freshly injected graph SVG.
     * The node/edge neighbor map is rebuilt per render — theme redraws
     * re-inject the whole SVG, so the listeners must rebind with it — and every
     * mouseover/focusin recomputes the full state idempotently from scratch, so
     * moving between an edge's curve and arrowhead or between neighboring nodes
     * can never leave stale classes behind.
     */
    function bindGraphHoverHighlights(container) {
        const svg = container ? container.querySelector('svg') : null;
        if (!svg) return;

        const nodeIds = new Map(); // g.node group -> DOT node id
        const nodesById = new Map(); // DOT node id -> g.node group
        for (const node of svg.querySelectorAll('g.node')) {
            const title = node.querySelector('title');
            const id = title ? title.textContent.trim() : '';
            if (id) {
                nodeIds.set(node, id);
                nodesById.set(id, node);
            }
        }
        const edges = []; // {group, source, target} from "Source -> Target"
        for (const group of svg.querySelectorAll('g.edge')) {
            const title = group.querySelector('title');
            const parts = title ? title.textContent.split(' -> ') : [];
            if (parts.length === 2) edges.push({group: group, source: parts[0], target: parts[1]});
        }

        const flagged = new Set();
        const mark = (element, state) => {
            element.classList.add(state);
            flagged.add(element);
        };
        const markNode = (id, state) => {
            const node = nodesById.get(id);
            if (node) mark(node, state);
        };
        const clear = () => {
            for (const element of flagged) element.classList.remove(...GRAPH_HOVER_STATES);
            flagged.clear();
        };
        const applyFor = target => {
            clear();
            const edgeGroup = target.closest ? target.closest('g.edge') : null;
            if (edgeGroup) {
                const edge = edges.find(entry => entry.group === edgeGroup);
                if (edge) {
                    mark(edgeGroup, 'rf-hover-edge');
                    markNode(edge.source, 'rf-hover-edge');
                    markNode(edge.target, 'rf-hover-edge');
                }
                return;
            }
            const nodeGroup = target.closest ? target.closest('g.node') : null;
            if (!nodeGroup || !nodeIds.has(nodeGroup)) return;
            const id = nodeIds.get(nodeGroup);
            mark(nodeGroup, 'rf-hover-focus');
            for (const edge of edges) {
                if (edge.target === id) {
                    mark(edge.group, 'rf-hover-in');
                    markNode(edge.source, 'rf-hover-in');
                } else if (edge.source === id) {
                    mark(edge.group, 'rf-hover-out');
                    markNode(edge.target, 'rf-hover-out');
                }
            }
        };

        svg.addEventListener('mouseover', event => applyFor(event.target));
        svg.addEventListener('mouseleave', clear);
        // Keyboard parity: the hyperlinked node anchors are focusable tab stops.
        svg.addEventListener('focusin', event => applyFor(event.target));
        svg.addEventListener('focusout', clear);
    }

    /**
     * Re-renders every live graph so edge labels re-resolve against the newly
     * active palette. Entries whose container left the DOM are pruned instead
     * of redrawn.
     */
    function redrawGraphsForTheme() {
        const vizdom = typeof window !== 'undefined' ? window.Vizdom : null;
        if (!vizdom || !vizdom.DotParser) return;
        const parser = new vizdom.DotParser();
        for (const [containerId, entry] of wasmGraphHierarchies) {
            const el = typeof document !== 'undefined' ? document.getElementById(containerId) : null;
            if (!el || !el.isConnected) {
                destroyPanZoom(containerId);
                wasmGraphHierarchies.delete(containerId);
                continue;
            }
            try {
                renderGraphInto(parser, el, containerId, entry);
            } catch (error) {
                console.warn('WASM graph re-render failed:', error);
            }
        }
    }

    function initWasmGraphs(data) {
        try {
            const vizdom = window.Vizdom;
            const parser = vizdom && vizdom.DotParser ? new vizdom.DotParser() : null;

            const repoUrl = data.project && data.project.repoUrl ? data.project.repoUrl : '';
            const renderGraph = (containerId, hierarchy, dot) => {
                if (!parser) return;
                const el = document.getElementById(containerId);
                if (!el || !(hierarchy || typeof dot === 'string')) return;
                const entry = {hierarchy: hierarchy || null, dot: dot || null, repoUrl: repoUrl};
                try {
                    renderGraphInto(parser, el, containerId, entry);
                    wasmGraphHierarchies.set(containerId, entry);
                } catch (error) {
                    console.warn('WASM graph render failed for ' + containerId + ':', error);
                    window.RF.announceWidgetFailure(containerId);
                }
            };

            if (data.classMap && !data.classMap.dotThresholdExceeded && data.classMap.hierarchy) {
                // The graph synthesizes client-side from the inline hierarchy
                // payload (rf-hierarchy-dot.js, byte-identical to the Java
                // NestedDotBuilder — pinned by hierarchy-dot.test.cjs).
                renderGraph('classGraph', data.classMap.hierarchy, null);
            }
            if (data.packageMap
                && !data.packageMap.dotThresholdExceeded
                && data.packageMap.hasEdges
                && data.packageMap.hierarchy) {
                renderGraph('packageGraph', data.packageMap.hierarchy, null);
            }
            const cycle = data.classCycles && data.classCycles.largestCycle;
            if (cycle && !cycle.dotThresholdExceeded && cycle.cycleIdentifier && cycle.hierarchy) {
                renderGraph(cycle.cycleIdentifier, cycle.hierarchy, null);
            }
        } catch (error) {
            console.warn('WASM graph layout unavailable:', error);
        }
    }

    window.RF.initWasmGraphs = initWasmGraphs;
    window.RF.redrawGraphs = redrawGraphsForTheme;
    window.RF.graphDotOptions = graphDotOptions;
    window.RF.bindGraphHoverHighlights = bindGraphHoverHighlights;
})();
