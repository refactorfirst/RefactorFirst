// RefactorFirst interactive report 3D force-graph popup (Phase P7/T7.4):
// node/link arrays from DOT with the always-dark accent set (the WebGL
// canvas paints its own background), sprite-label nodes, directional hover
// states and the subgraph-dropping graphlib flattening. Ported from the
// page repo's lib/report-view.js 3D section. graphlib-dot and 3d-force-graph
// are optional CDN dependencies.
(function () {
    'use strict';

    // ---------------------------------------------------------------------------
    // 3D force-graph popup.
    // ---------------------------------------------------------------------------

    function loadSpriteText() {
        if (window.SpriteText) return Promise.resolve(window.SpriteText);
        return window.rfSpriteTextReady || Promise.resolve(null);
    }

    async function createForceGraph(popupId, containerName, dot) {
        if (!window.RF.openPopup(popupId)) return;
        const container = document.getElementById(containerName);
        if (!container || !window.graphlibDot || !window.ForceGraph3D || typeof dot !== 'string') return;

        const graphlibGraph = window.graphlibDot.read(dot);
        const nodes = [];
        const links = [];
        // Packages are noise in the popups: drop the subgraph nodes graphlib-dot
        // creates per named subgraph, keeping every node they contained and the
        // edges between the kept nodes.
        const droppedSubgraphs = new Set();
        graphlibGraph.nodes().forEach(node => {
            if (graphlibGraph.children(node).length > 0) {
                droppedSubgraphs.add(node);
                return;
            }
            nodes.push({
                id: node,
                label: graphlibGraph.node(node).label || node,
                color: graphlibGraph.node(node).color || 'white',
                neighbors: [],
                links: []
            });
        });
        graphlibGraph.edges().forEach(edge => {
            if (droppedSubgraphs.has(edge.v) || droppedSubgraphs.has(edge.w)) return;
            links.push({
                source: edge.v,
                target: edge.w,
                color: graphlibGraph.edge(edge).color || 'white',
                weight: graphlibGraph.edge(edge).weight
            });
        });
        const gData = {nodes: nodes, links: links};
        gData.links.forEach(link => {
            const a = gData.nodes.find(node => node.id === link.source);
            const b = gData.nodes.find(node => node.id === link.target);
            // Resolved endpoint objects ride on each link so the hover handlers
            // below never depend on force-graph's ingestion-time rewiring.
            link.sourceNode = a;
            link.targetNode = b;
            a.neighbors.push(b);
            b.neighbors.push(a);
            a.links.push(link);
            b.links.push(link);
        });

        window.RF.disposePopupRenderer(container);
        const graph = new window.ForceGraph3D(container)
            .graphData(gData)
            .nodeLabel('label')
            .width(container.clientWidth)
            .height(container.clientHeight);

        // 3d-force-graph 1.80.1 exposes _destructor: stops animation and disposes its scene.
        const teardown = () => graph._destructor();
        let disposed = false;
        window.RF.retainPopupRenderer(container, () => { disposed = true; teardown(); });

        if (gData.links.length + gData.nodes.length < 4000) {
            const SpriteText = await loadSpriteText();
            if (disposed) return;
            if (!SpriteText) {
                window.RF.announceWidgetFailure('three-spritetext');
                return;
            }
            // The directional hover model, always on: hovering a node fills it
            // amber while its incoming links and sources go green and its
            // outgoing links and sinks go blue; hovering a link fills it and
            // both endpoints amber. The accents are always the dark values —
            // the WebGL canvas paints its own dark background in every theme.
            const accents = {edge: '#f0b95a', in: '#7ee2a8', out: '#8ed0fa'};
            const nodeStates = new Map();
            const linkStates = new Map();
            const spriteByNode = new Map();

            const nodeAccent = node => {
                const state = nodeStates.get(node);
                if (!state) return node.color || 'white';
                return state === 'in' ? accents.in : state === 'out' ? accents.out : accents.edge;
            };
            const clearStates = () => {
                nodeStates.clear();
                linkStates.clear();
            };

            // Sprites recolor only for nodes whose highlight state changed since
            // the previous hover — the previous ∪ current flag set.
            let touchedNodes = new Set();
            const syncSprites = () => {
                const dirty = new Set([...touchedNodes, ...nodeStates.keys()]);
                dirty.forEach(node => {
                    const sprite = spriteByNode.get(node);
                    if (sprite) sprite.color = nodeAccent(node);
                });
                touchedNodes = new Set(nodeStates.keys());
            };
            const updateHighlight = () => {
                syncSprites();
                graph
                    .linkColor(graph.linkColor())
                    .linkWidth(graph.linkWidth())
                    .linkDirectionalParticles(graph.linkDirectionalParticles());
            };

            graph
                .nodeThreeObject(node => {
                    let sprite = spriteByNode.get(node);
                    if (!sprite) {
                        sprite = new SpriteText(node.label);
                        sprite.material.depthWrite = false;
                        sprite.textHeight = 4;
                        spriteByNode.set(node, sprite);
                    }
                    sprite.color = nodeAccent(node);
                    return sprite;
                })
                .linkColor(link => (linkStates.has(link) ? accents[linkStates.get(link)] : link.color || 'white'))
                .linkWidth(link => (linkStates.has(link) ? 4 : 1))
                .linkDirectionalParticles(link => (linkStates.has(link) ? 4 : 0))
                .linkDirectionalParticleWidth(4)
                .onNodeHover(node => {
                    if ((!node && !nodeStates.size) || (node && nodeStates.get(node) === 'focus')) return;
                    clearStates();
                    if (node) {
                        nodeStates.set(node, 'focus');
                        node.links.forEach(link => {
                            if (link.targetNode === node) {
                                linkStates.set(link, 'in');
                                if (link.sourceNode !== node) {
                                    nodeStates.set(link.sourceNode,
                                        nodeStates.get(link.sourceNode) === 'out' ? 'both' : 'in');
                                }
                            } else if (link.sourceNode === node) {
                                linkStates.set(link, 'out');
                                if (link.targetNode !== node) {
                                    nodeStates.set(link.targetNode,
                                        nodeStates.get(link.targetNode) === 'in' ? 'both' : 'out');
                                }
                            }
                        });
                    }
                    updateHighlight();
                })
                .onLinkHover(link => {
                    linkStates.clear();
                    nodeStates.clear();
                    if (link) {
                        linkStates.set(link, 'edge');
                        nodeStates.set(link.sourceNode, 'edge');
                        nodeStates.set(link.targetNode, 'edge');
                    }
                    updateHighlight();
                });
        }
    }

    window.RF.createForceGraph = createForceGraph;
    window.RF.loadSpriteText = loadSpriteText;
})();
