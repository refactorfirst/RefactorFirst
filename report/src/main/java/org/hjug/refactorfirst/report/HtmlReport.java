package org.hjug.refactorfirst.report;

import java.util.*;
import lombok.extern.slf4j.Slf4j;
import org.hjug.cbc.RankedCycle;
import org.hjug.cbc.RankedDisharmony;
import org.hjug.gdg.GraphDataGenerator;
import org.hjug.graphbuilder.CodebaseGraphDTO;
import org.jgrapht.Graph;
import org.jgrapht.graph.DefaultWeightedEdge;

@Slf4j
public class HtmlReport extends SimpleHtmlReport {

    int dotGraphThreshold = 4000;

    // use Files.readString(Path.of(file))
    // Created by generative AI and modified slightly
    public static final String SUGIYAMA_SIGMA_GRAPH =
            """
            <script>
            function sugiyamaLayout(graph) {
                var layers = [];
                var nodeLevels = {};
                var nodes = graph.nodes();
                //var edges = graph.edges();

                // Step 1: Assign levels to nodes
                function assignLevels() {
                    var visited = {};
                    var stack = [];

                    function visit(node, level) {
                        if (visited[node]) return;
                        visited[node] = true;
                        nodeLevels[node] = level;
                        if (!layers[level]) layers[level] = [];
                        layers[level].push(node);
                        stack.push(node);
                        graph.forEachNeighbor(node, function (neighbor) {
                            visit(neighbor, level + 1);
                        });
                    }

                    nodes.forEach(function (node) {
                        if (!visited[node]) visit(node, 0);
                    });
                }

                // Step 2: Reduce edge crossings
                function reduceCrossings() {
                    for (var i = 0; i < layers.length - 1; i++) {
                        var layer = layers[i];
                        var nextLayer = layers[i + 1];
                        var positions = {};

                        nextLayer.forEach(function (node, index) {
                            positions[node] = index;
                        });

                        layer.sort(function (a, b) {
                            var aPos = 0, bPos = 0;
                            graph.forEachNeighbor(a, function (neighbor) {
                                aPos += positions[neighbor] || 0;
                            });
                            graph.forEachNeighbor(b, function (neighbor) {
                                bPos += positions[neighbor] || 0;
                            });
                            return aPos - bPos;
                        });
                    }
                }

                // Step 3: Assign positions to nodes
                function assignPositions() {
                    var yStep = 100;
                    var xStep = 2000;

                    layers.forEach(function (layer, level) {
                        var layerWidth = layer.length * xStep;
                        var offsetX = ((screen.width - 200) - layerWidth) / 2; // Centering the nodes

                        layer.forEach(function (node, index) {
                            graph.setNodeAttribute(node, 'x', offsetX + index * xStep);
                            graph.setNodeAttribute(node, 'y', -level * yStep);
                        });
                    });
                }

                assignLevels();
                reduceCrossings();
                assignPositions();
            }

            function renderGraph(dot) {
                // Parse the DOT graph using graphlib-dot
                const graphlibGraph = graphlibDot.read(dot);

                // Convert graphlib graph to graphology graph
                const graphologyGraph = new graphology.Graph();
                graphlibGraph.nodes().forEach(node => {
                    const attrs = graphlibGraph.node(node);
                    graphologyGraph.addNode(node, {
                        label: attrs.label || node,
                        color: attrs.color,
                        size: 5,
                    });
                });

                graphlibGraph.edges().forEach(edge => {
                    const attrs = graphlibGraph.edge(edge);
                    graphologyGraph.addEdge(edge.v, edge.w, {
                        color: attrs.color,
                        size: 1,
                        type: 'arrow',
                    });
                });

                sugiyamaLayout(graphologyGraph)

                return graphologyGraph;
            }
            </script>""";

    public static final String FORCE_3D_GRAPH =
            """
            <script type="module">
            // SpriteText will only work as import
                    // this script block requires type=module since we are using an import
                    import SpriteText from "https://esm.sh/three-spritetext";

                    function createForceGraph(popupId, containerName, dot) {
                        // Add event listener for Escape key to close the popup
                        document.addEventListener('keydown', function (event) {
                            if (event.key === 'Escape') {
                                hidePopup();
                            }
                        });

                        document.getElementById('overlay').style.display = 'block';
                        document.getElementById(popupId).style.display = 'block';
                        var container = document.getElementById(containerName);

                        // Parse the DOT graph using graphlib-dot
                        const graphlibGraph = graphlibDot.read(dot);

                        var nodes = [];
                        var links = [];

                        graphlibGraph.nodes().forEach(function (node) {
                            var nodeData = graphlibGraph.node(node);
                            nodes.push({
                                id: node,
                                color: nodeData.color || 'white',
                            });
                        });

                        graphlibGraph.edges().forEach(function (edge) {
                            links.push({
                                source: edge.v,
                                target: edge.w,
                                color: graphlibGraph.edge(edge).color || 'white',
                                weight: graphlibGraph.edge(edge).weight,
                            });
                        });

                        const gData = {
                            nodes: nodes,
                            links: links
                        };

                        // cross-link node objects
                        gData.links.forEach(link => {
                            const a = gData.nodes.find(node => node.id === link.source);
                            const b = gData.nodes.find(node => node.id === link.target);
                            !a.neighbors && (a.neighbors = []);
                            !b.neighbors && (b.neighbors = []);
                            a.neighbors.push(b);
                            b.neighbors.push(a);

                            !a.links && (a.links = []);
                            !b.links && (b.links = []);
                            a.links.push(link);
                            b.links.push(link);
                        });

                        const Graph = new ForceGraph3D(container)
                            .graphData(gData)
                            .nodeLabel('id')
                            .width(container.clientWidth)
                            .height(container.clientHeight);

                        if(gData.links.length + gData.nodes.length < 4000) {
                            console.log(gData.links.length + gData.nodes.length);


                            // use node labels instead of spheres
                            Graph.nodeThreeObject(node => {
                                const sprite = new SpriteText(node.id);
                                sprite.material.depthWrite = false; // make sprite background transparent
                                sprite.color = node.color;
                                sprite.textHeight = 4;
                                return sprite;
                            });

                            // code to display weight as link text
                            // may be too much for browsers to handle
                            // Graph
                            //     .linkThreeObjectExtend(true)
                            //     .linkThreeObject(link => {
                            //         // extend link with text sprite
                            //         const sprite = new SpriteText(`${link.weight}`);
                            //         sprite.color = 'lightgrey';
                            //         sprite.textHeight = 3;
                            //         return sprite;
                            //     })
                            //     .linkPositionUpdate((sprite, {start, end}) => {
                            //         const middlePos = Object.assign(...['x', 'y', 'z'].map(c => ({
                            //             [c]: start[c] + (end[c] - start[c]) / 2 // calc middle point
                            //         })));
                            //
                            //         // Position sprite
                            //         Object.assign(sprite.position, middlePos);
                            //     });


                            // code to highlight nodes & links
                            // TODO: enable via control - see Manipulate Link Force Distance for example
                            const highlightNodes = new Set();
                            const highlightLinks = new Set();
                            let hoverNode = null;
                            Graph
                                .nodeColor(node => highlightNodes.has(node) ? node === hoverNode ? 'rgb(255,0,0,1)' : 'rgba(255,160,0,0.8)' : 'rgba(0,255,255,0.6)')
                                .linkWidth(link => highlightLinks.has(link) ? 4 : 1)
                                .linkDirectionalParticles(link => highlightLinks.has(link) ? 4 : 0)
                                .linkDirectionalParticleWidth(4)
                                .onNodeHover(node => {
                                    // no state change
                                    if ((!node && !highlightNodes.size) || (node && hoverNode === node)) return;

                                    highlightNodes.clear();
                                    highlightLinks.clear();
                                    if (node) {
                                        highlightNodes.add(node);
                                        node.neighbors.forEach(neighbor => highlightNodes.add(neighbor));
                                        node.links.forEach(link => highlightLinks.add(link));
                                    }

                                    hoverNode = node || null;

                                    updateHighlight(Graph);
                                })
                                .onLinkHover(link => {
                                    highlightNodes.clear();
                                    highlightLinks.clear();

                                    if (link) {
                                        highlightLinks.add(link);
                                        highlightNodes.add(link.source);
                                        highlightNodes.add(link.target);
                                    }

                                    updateHighlight(Graph);
                                });

                        }
                    }

                    // used by highlighting functionality
                    function updateHighlight(Graph) {
                        // trigger update of highlighted objects in scene
                        Graph
                            .nodeColor(Graph.nodeColor())
                            .linkWidth(Graph.linkWidth())
                            .linkDirectionalParticles(Graph.linkDirectionalParticles());
                    }

                    // needed to allow the button to open the graph
                    window.createForceGraph = createForceGraph;\
                </script>""";

    // Created by generative AI and modified
    public static final String POPUP_STYLE =
            """
            <style>
                main {
                    max-width: 100vw;/*3840px;*/
                    width: 100vw;   /* or 100vw for viewport width */
                    padding: 0px 0px; /* 0px top & bottom, 40px left & right */
                }

                nav {
                    justify-content: center; /* Center horizontally */
                    padding: 0px 40px; /* 0px top & bottom, 40px left & right */
                    margin: 0px auto;
                }

                header {
                    padding: 0px 40px; /* 0px top & bottom, 40px left & right */
                }

                /* Scale the SVG to fill the screen */
                .fullscreen-svg {
                    width: 100%;
                    height: 100%;
                }\
                    /* Popup container */
                    .popup {
                        position: fixed;
                        display: none;
                        width: 95%;
                        height: 95%;
                        background-color: white;
                        border: 1px solid #ccc;
                        box-shadow: 0 0 10px rgba(0, 0, 0, 0.1);
                        top: 50%;
                        left: 50%;
                        transform: translate(-50%, -50%);
                        z-index: 1000;
                        padding: 20px;
                        box-sizing: border-box;
                    }

                    /* Popup overlay */
                    .overlay {
                        position: fixed;
                        display: none;
                        width: 100%;
                        height: 100%;
                        top: 0;
                        left: 0;
                        background: rgba(0, 0, 0, 0.5);
                        z-index: 999;
                    }

                    /* Close button */
                    .close-btn {
                        position: absolute;
                        top: 10px;
                        right: 10px;
                        cursor: pointer;
                    }
                </style>""";

    // Created by generative AI and modified
    public static final String POPUP_FUNCTIONS =
            """
            <script>
                function showPopup(popupId, containerName, dot) {
                    // Add event listener for Escape key to close the popup
                    document.addEventListener('keydown', function (event) {
                        if (event.key === 'Escape') {
                            hidePopup();
                        }
                    });\
                    \
                    document.getElementById('overlay').style.display = 'block';
                    document.getElementById(popupId).style.display = 'block';

                    var graph = renderGraph(dot);
                    var container = document.getElementById(containerName);

                    // Render with Sigma.js
                    new Sigma(graph, container);
                }

                function hidePopup() {
                    document.getElementById('overlay').style.display = 'none';
                    var popups = document.getElementsByClassName('popup');
                    for (var i = 0; i < popups.length; i++) {
                        popups[i].style.display = 'none';
                    }

                    // Clear the graph containers to remove the previous graphs
                    var containers = document.querySelectorAll('[id^="graph-container"]');
                    containers.forEach(function(container) {
                        while (container.firstChild) {
                            container.removeChild(container.firstChild);
                        }
                    });
            // Remove the Escape key event listener
                        document.removeEventListener('keydown', function (event) {
                            if (event.key === 'Escape') {
                                hidePopup();
                            }
                        });\
                }
            </script>""";

    @Override
    public String printHead() {
        // !Remember to update RefactorFirstMavenReport if this is modified
        return // GH Buttons import
        "<script async defer src=\"https://buttons.github.io/buttons.js\"></script>\n"
                // google chart import
                + "<script type=\"text/javascript\" src=\"https://www.gstatic.com/charts/loader.js\"></script>\n"
                // graphing imports - sigma, graphology, vizdom
                + "<script src=\"https://cdn.jsdelivr.net/npm/svg-pan-zoom@3.6.1/dist/svg-pan-zoom.min.js\"></script>"
                + "<script src=\"https://cdnjs.cloudflare.com/ajax/libs/sigma.js/2.4.0/sigma.min.js\"></script>\n"
                + "<script src=\"https://cdnjs.cloudflare.com/ajax/libs/graphology/0.25.4/graphology.umd.min.js\"></script>\n"
                + "<script src=\"https://cdn.jsdelivr.net/npm/graphlib-dot@0.6.4/dist/graphlib-dot.min.js\"></script>\n"
                + "<script src=\"https://cdn.jsdelivr.net/npm/3d-force-graph\"></script>\n"
                + "<script type=\"module\" src=\"https://cdn.jsdelivr.net/npm/@vizdom/vizdom-ts-web@0.1.19/vizdom_ts.min.js\"></script>\n"
                // Make the output look decent.  Don't use in RefactorFirstMavenReport.
                + "<link rel=\"stylesheet\" href=\"https://unpkg.com/mvp.css\">\n";
    }

    String printScripts() {
        return SUGIYAMA_SIGMA_GRAPH + FORCE_3D_GRAPH + POPUP_FUNCTIONS + POPUP_STYLE;
    }

    @Override
    public String printOpenBodyTag() {
        return "  <body class=\"composite\">\n" + printOverlay();
    }

    private String printOverlay() {
        return "<div class=\"overlay\" id=\"overlay\" onclick=\"hidePopup()\"></div>";
    }

    @Override
    public String printTitle(String projectName, String projectVersion) {
        return "<title>Refactor First Report for " + projectName + " " + projectVersion + " </title>\n";
    }

    void renderClassMapMenu(StringBuilder stringBuilder) {
        stringBuilder.append("<li><a href=\"#CLASSMAP\">Class Map</a></li>\n");
    }

    void renderPackageMapMenu(StringBuilder stringBuilder) {
        stringBuilder.append("<li><a href=\"#PACKAGEMAP\">Package Map</a></li>\n");
    }

    @Override
    String renderGithubButtons() {
        return """
                <div align="center">
                <h2>Show RefactorFirst some &#10084;&#65039;</h2>
                <a class="github-button" href="https://github.com/refactorfirst/refactorfirst" data-icon="octicon-star" data-size="large" data-show-count="true" aria-label="Star refactorfirst/refactorfirst on GitHub">Star</a>
                <a class="github-button" href="https://github.com/refactorfirst/refactorfirst/fork" data-icon="octicon-repo-forked" data-size="large" data-show-count="true" aria-label="Fork refactorfirst/refactorfirst on GitHub">Fork</a>
                <a class="github-button" href="https://github.com/refactorfirst/refactorfirst/subscription" data-icon="octicon-eye" data-size="large" data-show-count="true" aria-label="Watch refactorfirst/refactorfirst on GitHub">Watch</a>
                <a class="github-button" href="https://github.com/refactorfirst/refactorfirst/issues" data-icon="octicon-issue-opened" data-size="large" data-show-count="false" aria-label="Issue refactorfirst/refactorfirst on GitHub">Issue</a>
                <a class="github-button" href="https://github.com/sponsors/jimbethancourt" data-icon="octicon-heart" data-size="large" aria-label="Sponsor @jimbethancourt on GitHub">Sponsor</a>
                </div>""";
    }

    public String getName(Locale locale) {
        // Name of the report when listed in the project-reports.html page of a project
        return "Refactor First Report";
    }

    public String getDescription(Locale locale) {
        // Description of the report when listed in the project-reports.html page of a project
        return "Ranks the disharmonies in a codebase.  The classes that should be refactored first "
                + " have the highest priority values.";
    }

    @Override
    String renderDisharmonyChart(String anchorId, String title, List<RankedDisharmony> ranked, int maxPriority) {
        String slug = anchorId.toLowerCase().replaceAll("[^a-z0-9]", "_");
        String funcName = "draw_" + slug;
        String dataVar = "data_" + slug;
        String chartVar = "chart_" + slug;
        String divId = "chart_div_" + slug;

        GraphDataGenerator gen = new GraphDataGenerator();
        String script = gen.getDisharmonyScriptStart(funcName, dataVar)
                + gen.generateBubbleChartData(ranked, maxPriority - 1, "Effort")
                + gen.getDisharmonyScriptEnd(
                        funcName, chartVar, divId, dataVar, "Priority Ranking for Refactoring " + title, "Effort");

        return "<div id=\"" + divId + "\" align=\"center\"><script>" + script + "</script></div>\n"
                + "<h2>" + title + " Chart Legend:</h2>"
                + "<table border=\"5px\"><tbody>"
                + "<tr><td><strong>X-Axis:</strong> Effort to refactor</td></tr>"
                + "<tr><td><strong>Y-Axis:</strong> Relative churn</td></tr>"
                + "<tr><td><strong>Color:</strong> Priority of what to fix first</td></tr>"
                + "<tr><td><strong>Circle size:</strong> Priority (Visual) of what to fix first</td></tr>"
                + "</tbody></table><br/>";
    }

    @Override
    public String renderClassGraphVisuals(String repoUrl, CodebaseGraphDTO codebaseGraphDTO) {
        String dot = buildClassGraphDot(classGraph, repoUrl, codebaseGraphDTO);
        String classGraphName = "classGraph";

        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append("<h1 align=\"center\"><a id=\"CLASSMAP\">Class Map</a></h1>");
        stringBuilder.append(generateGraphButtons(classGraphName, dot));

        stringBuilder.append(
                "<div align=\"center\">Clicking on a node in the DOT graph (if present below) will open its source file in the repo.  Right/Alt click to open in a new browser tab.<br>Excludes classes that have no incoming and outgoing edges<br></div>");

        int classCount = classGraph.vertexSet().size();
        int relationshipCount = classGraph.edgeSet().size();
        stringBuilder.append("<div align=\"center\">Number of classes: " + classCount + "  Number of relationships: "
                + relationshipCount + "<br></div>");
        if (classCount + relationshipCount < dotGraphThreshold) {
            stringBuilder.append(generateDotImage(classGraphName));
        } else {
            // revisit and add DOT SVG popup button
            stringBuilder.append("<div align=\"center\">\nSVG is too big to render quickly</div>\n");
        }

        return stringBuilder.toString();
    }

    private StringBuilder generateGraphButtons(String graphName, String dot) {
        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append("<script>\n");
        stringBuilder.append("const " + graphName + "_dot = " + dot + "\n");
        stringBuilder.append("</script>\n");
        stringBuilder.append(generateForce3DPopup(graphName));
        stringBuilder.append(generate2DPopup(graphName));
        stringBuilder.append(generateHidePopup(graphName));

        stringBuilder.append("<div align=\"center\">\nRed lines represent relationships to remove.<br>\n");
        stringBuilder.append("Red nodes represent classes to remove.<br>\n");
        stringBuilder.append("Zoom in / out with your mouse wheel and click/move to drag the image.<br>\n");
        stringBuilder.append("</div>\n");
        return stringBuilder;
    }

    private static String generateDotImage(String graphName) {
        // revisit and add D3 popup button as well
        return "<div id=\"" + graphName
                + "\" style=\"width: 95%; height: 70vh; margin: auto; border: thin solid black\"></div>\n"
                + "<script type=\"module\">\n"
                + "import init, { DotParser } from \"https://cdn.jsdelivr.net/npm/@vizdom/vizdom-ts-web@0.1.19/vizdom_ts.min.js\";\n"
                + "    if(DotParser) {\n"
                + "        // Wait for the WASM binary to be compiled and the 'wasm' object to be populated\n"
                + "        await init();\n"
                + "        // Create a new Dot Parser\n"
                + "        const parser = new DotParser();\n"
                + "        const dotGraph = parser.parse("
                + graphName + "_dot);\n" + "        const directedGraph = dotGraph.to_directed();\n"
                + "        const positioned = directedGraph.layout();\n"
                + "        let svg = positioned.to_svg().to_string();\n"
                + "        // Modify the SVG string to include width and height attributes\n"
                + "        svg = svg.replace('<svg ', `<svg class=\"fullscreen-svg\"`);\n"
                + "\n"
                + "        document.getElementById(\""
                + graphName + "\").innerHTML = svg;\n" + "\n"
                + "        // Make the SVG zoomable\n"
                + "        svgPanZoom('#"
                + graphName + " svg', {\n" + "            zoomEnabled: true,\n"
                + "            controlIconsEnabled: true\n"
                + "        });\n"
                + "    }\n" + "</script>\n";
    }

    String buildClassGraphDot(
            Graph<String, DefaultWeightedEdge> classGraph, String repoUrl, CodebaseGraphDTO codebaseGraphDTO) {
        StringBuilder dot = new StringBuilder();
        dot.append("`strict digraph G {\n");

        for (DefaultWeightedEdge edge : classGraph.edgeSet()) {
            renderClassGraphEdge(classGraph, edge, dot);
        }

        // capture only classes that have a relationship with one or more other classes
        Set<String> vertexesToRender = new HashSet<>();
        for (DefaultWeightedEdge edge : classGraph.edgeSet()) {
            String[] vertexes = extractVertexes(edge);
            vertexesToRender.add(vertexes[0].trim());
            vertexesToRender.add(vertexes[1].trim());
        }

        // render vertices
        renderClassVertices(classGraph, repoUrl, codebaseGraphDTO, vertexesToRender, dot);

        dot.append("}`;");
        return dot.toString();
    }

    private void renderClassVertices(
            Graph<String, DefaultWeightedEdge> classGraph,
            String repoUrl,
            CodebaseGraphDTO codebaseGraphDTO,
            Set<String> vertexesToRender,
            StringBuilder dot) {
        for (String vertex : vertexesToRender) {
            String className = getClassName(vertex);

            // if the vertex is a nested class and has no outgoing edges, skip it
            if (className.contains("$")
                    && className.split("\\$")[className.split("\\$").length - 1].matches("\\d+")
                    && classGraph.outDegreeOf(vertex) == 0) {
                continue;
            }

            dot.append(className.replace("$", "_"));

            dot.append(" [");
            dot.append(hyperlinkClassForDot(vertex, repoUrl, codebaseGraphDTO));
            if (className.contains("$")) {
                dot.append(" label=\"").append(className.replace("$", "\\$")).append("\"");
            }

            if (classesToRemove.contains(vertex)) {
                dot.append(" color=red style=filled");
            }

            dot.append("];\n");
        }
    }

    String hyperlinkClassForDot(String fqClassName, String repoUrl, CodebaseGraphDTO codebaseGraphDTO) {
        StringBuilder sb = new StringBuilder();
        String path = codebaseGraphDTO.getClassToSourceFilePathMapping().get(fqClassName);
        return sb.append("URL=\"" + repoUrl + path + "\" target=\"_blank\"").toString();
    }

    private void renderClassGraphEdge(
            Graph<String, DefaultWeightedEdge> classGraph, DefaultWeightedEdge edge, StringBuilder dot) {
        // render edge
        String[] vertexes = extractVertexes(edge);

        String startVertex = vertexes[0].trim();
        String start = getClassName(startVertex.trim()).replace("$", "_");
        String endVertex = vertexes[1].trim();
        String end = getClassName(endVertex.trim()).replace("$", "_");

        // if the vertex is a nested class and has no outgoing edges, skip it
        if (start.contains("$")
                && start.split("\\$")[startVertex.split("\\$").length - 1].matches("\\d+")
                && classGraph.outDegreeOf(startVertex) == 0) {
            log.debug("Skipping edge: {} -> {}", startVertex, endVertex);
            return;
        }

        if (endVertex.contains("$")
                && endVertex.split("\\$")[endVertex.split("\\$").length - 1].matches("\\d+")
                && classGraph.outDegreeOf(endVertex) == 0) {
            log.debug("Skipping edge: {} -> {}", startVertex, endVertex);
            return;
        }

        log.debug("Rendering edge: {} -> {}", startVertex, endVertex);
        dot.append(start);
        dot.append(" -> ");
        dot.append(end);

        // render edge attributes
        int edgeWeight = (int) classGraph.getEdgeWeight(edge);
        dot.append(" [ ");
        dot.append("label = \"");
        dot.append(edgeWeight);
        dot.append("\" ");
        dot.append("weight = \"");
        dot.append(edgeWeight);
        dot.append("\"");

        if (classRelationshipsToRemove.contains(edge)) {
            dot.append(" color = \"red\"");
        }

        dot.append(" ];\n");
    }

    @Override
    public String renderClassCycleVisuals(RankedCycle cycle, String repoUrl, CodebaseGraphDTO codebaseGraphDTO) {
        String dot = buildClassCycleDot(classGraph, cycle, repoUrl, codebaseGraphDTO);

        String cycleName = getClassName(cycle.getCycleName()).replace("$", "_");

        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append("<h1 align=\"center\">Cycle Map</h1>");
        stringBuilder.append(generateGraphButtons(cycleName, dot));

        stringBuilder.append(
                "<div align=\"center\">Clicking on a node in the DOT graph (if present below) will open its source file in the repo.  Right/Alt click to open in a new browser tab.<br></div>");

        if (cycle.getCycleNodes().size() + cycle.getEdgeSet().size() < dotGraphThreshold) {
            stringBuilder.append(generateDotImage(cycleName));
        } else {
            // revisit and add DOT SVG popup button
            stringBuilder.append("<div align=\"center\">\nSVG is too big to render quickly</div>\n");
        }

        stringBuilder.append("<br/>\n");
        stringBuilder.append("<br/>\n");

        return stringBuilder.toString();
    }

    String buildClassCycleDot(
            Graph<String, DefaultWeightedEdge> classGraph,
            RankedCycle cycle,
            String repoUrl,
            CodebaseGraphDTO codebaseGraphDTO) {
        StringBuilder dot = new StringBuilder();
        dot.append("`strict digraph G {\n");

        for (DefaultWeightedEdge edge : cycle.getEdgeSet()) {
            renderClassGraphEdge(classGraph, edge, dot);
        }

        // render vertices
        Set<String> vertexSet = cycle.getVertexSet();
        renderClassVertices(classGraph, repoUrl, codebaseGraphDTO, vertexSet, dot);

        dot.append("}`;");
        return dot.toString();
    }

    @Override
    public String renderPackageGraphVisuals(String repoUrl, CodebaseGraphDTO codebaseGraphDTO) {
        if (packageGraph.edgeSet().isEmpty()) {
            return "";
        }

        String dot = buildPackageGraphDot(packageGraph, repoUrl, codebaseGraphDTO);
        String packageGraphName = "packageGraph";

        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append("<h1 align=\"center\"><a id=\"PACKAGEMAP\">Package Map</a></h1>");
        stringBuilder.append(generateGraphButtons(packageGraphName, dot));

        stringBuilder.append(
                "<div align=\"center\">Excludes packages that have no incoming and outgoing edges<br></div>");

        int packageCount = packageGraph.vertexSet().size();
        int relationshipCount = packageGraph.edgeSet().size();
        stringBuilder.append("<div align=\"center\">Number of packages: " + packageCount + "  Number of relationships: "
                + relationshipCount + "<br></div>");
        if (packageCount + relationshipCount < dotGraphThreshold) {
            stringBuilder.append(generateDotImage(packageGraphName));
        } else {
            // revisit and add DOT SVG popup button
            stringBuilder.append("<div align=\"center\">\nSVG is too big to render quickly</div>\n");
        }

        return stringBuilder.toString();
    }

    String buildPackageGraphDot(
            Graph<String, DefaultWeightedEdge> packageGraph, String repoUrl, CodebaseGraphDTO codebaseGraphDTO) {
        StringBuilder dot = new StringBuilder();
        dot.append("`strict digraph G {\n");

        for (DefaultWeightedEdge edge : packageGraph.edgeSet()) {
            renderPackageGraphEdge(packageGraph, edge, dot);
        }

        // capture only classes that have a relationship with one or more other classes
        Set<String> vertexesToRender = new HashSet<>();
        for (DefaultWeightedEdge edge : packageGraph.edgeSet()) {
            String[] vertexes = extractVertexes(edge);
            vertexesToRender.add(vertexes[0].trim());
            vertexesToRender.add(vertexes[1].trim());
        }

        // render vertices
        renderPackageVertices(packageGraph, repoUrl, codebaseGraphDTO, vertexesToRender, dot);

        dot.append("}`;");
        return dot.toString();
    }

    private void renderPackageGraphEdge(
            Graph<String, DefaultWeightedEdge> packageGraph, DefaultWeightedEdge edge, StringBuilder dot) {
        // render edge
        String[] vertexes = extractVertexes(edge);

        String start = vertexes[0].trim().replace(".", "_");
        String end = vertexes[1].trim().replace(".", "_");

        log.debug("Rendering edge: {} -> {}", start, end);
        dot.append(start);
        dot.append(" -> ");
        dot.append(end);

        // render edge attributes
        int edgeWeight = (int) packageGraph.getEdgeWeight(edge);
        dot.append(" [ ");
        dot.append("label = \"");
        dot.append(edgeWeight);
        dot.append("\" ");
        dot.append("weight = \"");
        dot.append(edgeWeight);
        dot.append("\"");

        if (packageRelationshipsToRemove.contains(edge)) {
            dot.append(" color = \"red\"");
        }

        dot.append(" ];\n");
    }

    private void renderPackageVertices(
            Graph<String, DefaultWeightedEdge> classGraph,
            String repoUrl,
            CodebaseGraphDTO codebaseGraphDTO,
            Set<String> vertexesToRender,
            StringBuilder dot) {
        for (String packageName : vertexesToRender) {
            dot.append(packageName.replace(".", "_"));

            dot.append(" [label=\"");
            dot.append(packageName);
            dot.append("\"");

            if (packagesToRemove.contains(packageName)) {
                dot.append(" color=red style=filled");
            }

            dot.append("];\n");
        }
    }

    String generate2DPopup(String cycleName) {
        // Created by generative AI and modified
        return "<button style=\"display: block; margin: 0 auto;\" onclick=\"showPopup('popup-" + cycleName
                + "', 'graph-container-" + cycleName + "', " + cycleName + "_dot )\">Show " + cycleName
                + " 2D Popup</button>\n";
    }

    String generateForce3DPopup(String cycleName) {
        // Created by generative AI and modified
        return "<button style=\"display: block; margin: 0 auto;\" onclick=\"createForceGraph('popup-" + cycleName
                + "', 'graph-container-" + cycleName + "', " + cycleName + "_dot )\">Show " + cycleName
                + " 3D Popup</button>\n";
    }

    String generateHidePopup(String cycleName) {
        return "<div class=\"popup\" id=\"popup-" + cycleName + "\">\n"
                + "<span class=\"close-btn\" onclick=\"hidePopup()\">×</span>\n"
                + "    <div id=\"graph-container-" + cycleName + "\" style=\"width: 100%; height: 100%;\"></div>"
                + "\n</div>\n";
    }
}
