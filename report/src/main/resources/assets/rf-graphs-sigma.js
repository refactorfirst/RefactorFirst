// RefactorFirst interactive report Sigma 2D popup (Phase P7/T7.3): the
// sugiyama-layered graphology builder, the directional hover highlight model
// and the dark hover renderer. Ported from the page repo's
// lib/report-view.js sigma section. sigma.js/graphology/graphlib-dot are
// optional CDN dependencies; a missing library leaves the popup empty.
(function () {
    'use strict';

    // ---------------------------------------------------------------------------
    // Layered (sugiyama-style) layout for Sigma popup graphs.
    // ---------------------------------------------------------------------------

    function sugiyamaLayout(graph) {
        const layers = [];
        const nodeLevels = {};
        const nodes = graph.nodes();

        function assignLevels() {
            const visited = {};

            function visit(node, level) {
                if (visited[node]) return;
                visited[node] = true;
                nodeLevels[node] = level;
                if (!layers[level]) layers[level] = [];
                layers[level].push(node);
                graph.forEachNeighbor(node, neighbor => visit(neighbor, level + 1));
            }

            nodes.forEach(node => {
                if (!visited[node]) visit(node, 0);
            });
        }

        function reduceCrossings() {
            for (let i = 0; i < layers.length - 1; i++) {
                const layer = layers[i];
                const nextLayer = layers[i + 1];
                const positions = {};
                nextLayer.forEach((node, index) => {
                    positions[node] = index;
                });
                layer.sort((a, b) => {
                    let aPos = 0, bPos = 0;
                    graph.forEachNeighbor(a, neighbor => {
                        aPos += positions[neighbor] || 0;
                    });
                    graph.forEachNeighbor(b, neighbor => {
                        bPos += positions[neighbor] || 0;
                    });
                    return aPos - bPos;
                });
            }
        }

        function assignPositions() {
            const yStep = 100;
            const xStep = 2000;
            layers.forEach((layer, level) => {
                const layerWidth = layer.length * xStep;
                const offsetX = ((window.screen.width - 200) - layerWidth) / 2;
                layer.forEach((node, index) => {
                    graph.setNodeAttribute(node, 'x', offsetX + index * xStep);
                    graph.setNodeAttribute(node, 'y', -level * yStep);
                });
            });
        }

        assignLevels();
        reduceCrossings();
        assignPositions();
    }

    function buildGraphologyGraph(dot) {
        const graphlibGraph = window.graphlibDot.read(dot);
        const graphologyGraph = new window.graphology.Graph();
        // graphlib-dot parses subgraphs as compound parents: every named
        // subgraph becomes a node with the contained nodes parented to it, so
        // only actual subgraph ids ever have children. Packages are noise in
        // the popups, so drop the subgraph nodes — identified by that parsed
        // metadata, not by the ID prefix — while keeping every node the
        // subgraphs contained and the edges between them.
        const droppedSubgraphs = new Set();
        graphlibGraph.nodes().forEach(node => {
            if (graphlibGraph.children(node).length > 0) {
                droppedSubgraphs.add(node);
                return;
            }
            const attrs = graphlibGraph.node(node);
            graphologyGraph.addNode(node, {
                label: attrs.label || node,
                color: attrs.color,
                size: 5
            });
        });
        graphlibGraph.edges().forEach(edge => {
            if (droppedSubgraphs.has(edge.v) || droppedSubgraphs.has(edge.w)) return;
            const attrs = graphlibGraph.edge(edge);
            graphologyGraph.addEdge(edge.v, edge.w, {
                color: attrs.color,
                size: 1,
                type: 'arrow'
            });
        });
        sugiyamaLayout(graphologyGraph);
        return graphologyGraph;
    }

    // ---------------------------------------------------------------------------
    // Sigma 2D popup.
    // ---------------------------------------------------------------------------

    // Hovered-node label box backgrounds per palette. Sigma's default hover
    // renderer hardcodes "#FFF", which washes out the light label text the dark
    // palette switches to; the dark value is the dark popup surface, so the box
    // blends into it while keeping >= 4.5:1 against CHART_LEGEND_TEXT.dark.
    const SIGMA_HOVER_BG = Object.freeze({
        light: '#ffffff',
        dark: '#1a2330'
    });

    // Hover accents for the canvas/WebGL popup graphs, mirroring the
    // --graph-hover-* custom properties of each palette in rf-palette.css. CSS
    // cannot reach canvas or WebGL, so the popups resolve accents in JS: the 2D
    // sigma popup follows the active palette, while the 3D force-graph popup
    // always paints its own dark WebGL background and therefore always uses the
    // dark values.
    const GRAPH_HOVER_ACCENTS = Object.freeze({
        light: Object.freeze({edge: '#b45309', in: '#1e8449', out: '#2a6f97'}),
        dark: Object.freeze({edge: '#f0b95a', in: '#7ee2a8', out: '#8ed0fa'})
    });

    /**
     * Dark-palette counterpart of sigma.js' default hover renderer (upstream
     * drawHover, MIT): identical geometry — a rounded label box trailing a drop
     * shadow, or a shadowed circle when the node has no label — but the box
     * fills with the dark popup surface instead of the hardcoded white, so the
     * light label color stays readable. Canvas-drawn, so CSS cannot reach it.
     */
    function drawDarkHover(context, data, settings) {
        const size = settings.labelSize;
        const font = settings.labelFont;
        const weight = settings.labelWeight;

        context.font = weight + ' ' + size + 'px ' + font;

        // Label box background in the dark surface color.
        context.fillStyle = SIGMA_HOVER_BG.dark;
        context.shadowOffsetX = 0;
        context.shadowOffsetY = 0;
        context.shadowBlur = 8;
        context.shadowColor = '#000';

        const PADDING = 2;

        if (typeof data.label === 'string') {
            const textWidth = context.measureText(data.label).width;
            const boxWidth = Math.round(textWidth + 5);
            const boxHeight = Math.round(size + 2 * PADDING);
            const radius = Math.max(data.size, size / 2) + PADDING;

            const angleRadian = Math.asin(boxHeight / 2 / radius);
            const xDeltaCoord = Math.sqrt(Math.abs(Math.pow(radius, 2) - Math.pow(boxHeight / 2, 2)));

            context.beginPath();
            context.moveTo(data.x + xDeltaCoord, data.y + boxHeight / 2);
            context.lineTo(data.x + radius + boxWidth, data.y + boxHeight / 2);
            context.lineTo(data.x + radius + boxWidth, data.y - boxHeight / 2);
            context.lineTo(data.x + xDeltaCoord, data.y - boxHeight / 2);
            context.arc(data.x, data.y, radius, angleRadian, -angleRadian);
            context.closePath();
            context.fill();
        } else {
            context.beginPath();
            context.arc(data.x, data.y, data.size + PADDING, 0, Math.PI * 2);
            context.closePath();
            context.fill();
        }

        context.shadowOffsetX = 0;
        context.shadowOffsetY = 0;
        context.shadowBlur = 0;

        // Label text in the same themed color sigma's default label renderer
        // uses (settings.labelColor.color), mirroring upstream drawLabel.
        if (data.label) {
            context.fillStyle = settings.labelColor.color;
            context.fillText(data.label, data.x + data.size + 3, data.y + size / 3);
        }
    }

    /**
     * Sigma settings for a 2D popup graph under the active palette: undefined in
     * light mode (sigma defaults — #000 labels on the #FFF hover box — stay
     * untouched), and in dark mode the chart legend text color for node labels
     * plus the dark hover renderer above. Popups are rebuilt on every open, so
     * resolving at open time suffices; the theme radios sit under the modal
     * overlay while a popup is open.
     */
    function sigmaPopupSettings() {
        if (window.RF.activeChartTheme() !== 'dark') return undefined;
        return {
            labelColor: {color: '#9fb0c0'},
            hoverRenderer: drawDarkHover
        };
    }

    /**
     * Highlights a hovered sigma popup node's neighborhood: the hovered node
     * fills with the amber attention accent, its incoming edges and their
     * source nodes fill green, and its outgoing edges and their sink nodes
     * fill blue. Sigma 2.4.0's event model has no edge hover events, so the
     * 2D popup binds node hover only; the 3D popup carries the edge-hover half.
     */
    function bindSigmaHoverHighlight(sigma, graph) {
        if (!sigma || typeof sigma.on !== 'function' || typeof sigma.setSetting !== 'function' || !graph) return undefined;

        const accentForTheme = () => {
            const theme = window.RF && typeof window.RF.activeChartTheme === 'function'
                ? window.RF.activeChartTheme()
                : 'light';
            return GRAPH_HOVER_ACCENTS[theme] || GRAPH_HOVER_ACCENTS.light;
        };
        const nodeStates = new Map();
        const edgeStates = new Map();

        const clear = () => {
            nodeStates.clear();
            edgeStates.clear();
        };
        const refresh = () => {
            if (typeof sigma.scheduleRefresh === 'function') sigma.scheduleRefresh();
        };
        const applyThemeSettings = () => {
            const settings = sigmaPopupSettings();
            if (settings) {
                sigma.setSetting('labelColor', settings.labelColor);
                sigma.setSetting('hoverRenderer', settings.hoverRenderer);
            } else {
                sigma.setSetting('labelColor', undefined);
                sigma.setSetting('hoverRenderer', undefined);
            }
            refresh();
        };

        let themeCleanup = null;
        if (window.RF && window.RF.theme && typeof window.RF.theme.onChange === 'function') {
            themeCleanup = window.RF.theme.onChange(() => {
                applyThemeSettings();
            });
            const originalKill = sigma.kill.bind(sigma);
            sigma.kill = function () {
                if (themeCleanup) {
                    themeCleanup();
                    themeCleanup = null;
                }
                return originalKill();
            };
        }

        sigma.on('enterNode', payload => {
            const node = payload && payload.node;
            if (!node) return;
            // Clear-then-populate so hopping straight from one node to another
            // never leaves stale members behind.
            clear();
            nodeStates.set(node, 'focus');
            if (typeof graph.edges === 'function') {
                graph.edges(node).forEach(edge => {
                    const extremities = typeof graph.extremities === 'function'
                        ? graph.extremities(edge) : [];
                    const source = extremities[0];
                    const target = extremities[1];
                    if (target === node) {
                        edgeStates.set(edge, 'in');
                        if (source !== node) {
                            nodeStates.set(source, nodeStates.get(source) === 'out' ? 'both' : 'in');
                        }
                    } else if (source === node) {
                        edgeStates.set(edge, 'out');
                        if (target !== node) {
                            nodeStates.set(target, nodeStates.get(target) === 'in' ? 'both' : 'out');
                        }
                    }
                });
            }
            // Scheduled, not synchronous: sigma coalesces per animation frame.
            refresh();
        });
        sigma.on('leaveNode', () => {
            if (!nodeStates.size && !edgeStates.size) return;
            clear();
            refresh();
        });
        sigma.setSetting('nodeReducer', (node, data) => {
            const state = nodeStates.get(node);
            if (!state) return data;
            const accents = accentForTheme();
            const accent = state === 'in' ? accents.in : state === 'out' ? accents.out : accents.edge;
            return Object.assign({}, data, {color: accent, highlighted: true});
        });
        sigma.setSetting('edgeReducer', (edge, data) => {
            const state = edgeStates.get(edge);
            if (!state) return data;
            return Object.assign({}, data, {color: accentForTheme()[state], size: 3});
        });
        applyThemeSettings();
        return () => {
            if (themeCleanup) {
                themeCleanup();
                themeCleanup = null;
            }
        };
    }


    window.RF.sugiyamaLayout = sugiyamaLayout;
    window.RF.buildGraphologyGraph = buildGraphologyGraph;
    window.RF.sigmaPopupSettings = sigmaPopupSettings;
    window.RF.bindSigmaHoverHighlight = bindSigmaHoverHighlight;
    window.RF.drawDarkHover = drawDarkHover;
    window.RF.GRAPH_HOVER_ACCENTS = GRAPH_HOVER_ACCENTS;
})();
