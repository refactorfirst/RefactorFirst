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
        stringBuilder
                .append("<div align=\"center\">Number of classes: ")
                .append(classCount)
                .append("  Number of relationships: ")
                .append(relationshipCount)
                .append("<br></div>");
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
        stringBuilder
                .append("const ")
                .append(graphName)
                .append("_dot = ")
                .append(dot)
                .append("\n");
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
            renderClassGraphEdge(classGraph, edge, codebaseGraphDTO, dot);
        }

        // capture only classes that have a relationship with one or more other classes
        Set<String> vertexesToRender = new HashSet<>();
        for (DefaultWeightedEdge edge : classGraph.edgeSet()) {
            String[] vertexes = extractVertexes(edge);
            String originVertex = vertexes[0].trim();
            vertexesToRender.add(originVertex);
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

            // Suppress sink-only anonymous/synthetic vertices (no outgoing edges) so the DOT graph
            // stays readable; active anonymous/synthetic classes still render.
            if (isSinkAnonymousOrSyntheticVertex(classGraph, vertex)) {
                continue;
            }

            dot.append(renderSafeNodeId(vertex, classGraph, codebaseGraphDTO));

            dot.append(" [");
            dot.append(hyperlinkClassForDot(vertex, repoUrl, codebaseGraphDTO));
            if (className.contains("$")) {
                dot.append(" label=\"").append(className.replace("$", "\\$")).append("\"");
            } else if (isAnonymousFqn(vertex)) {
                // Kotlin "<anonymous>" renders under the enclosing source file's base name as the
                // owner with $ as the enclosing-class separator (escaped for DOT).
                dot.append(" label=\"")
                        .append(anonymousOwnerLabel(vertex, codebaseGraphDTO).replace("$", "\\$"))
                        .append("\"");
            }

            if (classesToRemove.contains(vertex)) {
                dot.append(" color=red style=filled");
            }

            dot.append("];\n");
        }
    }

    String hyperlinkClassForDot(String fqClassName, String repoUrl, CodebaseGraphDTO codebaseGraphDTO) {
        if (codebaseGraphDTO == null || codebaseGraphDTO.getClassToSourceFilePathMapping() == null) {
            return "";
        }
        String path = codebaseGraphDTO.getClassToSourceFilePathMapping().get(fqClassName);
        if (path == null || path.isBlank()) {
            return "";
        }
        return "URL=\"" + repoUrl + path + "\" target=\"_blank\"";
    }

    /**
     * Returns the DOT-safe node id for a class vertex. This is the fully qualified class name
     * with {@code .} replaced by {@code _} and {@code $} replaced by {@code _} (for inner classes),
     * extended to also handle the Kotlin literal {@code "<anonymous>"} FQN,
     * whose {@code <}/{@code >} characters are illegal in Graphviz node ids.
     * When the source-file mapping is unavailable ({@code codebaseGraphDTO == null} or no path
     * is mapped for the vertex), {@code <}/{@code >} are reversibly encoded as {@code lt_}/{@code _gt}.
     * Prefer the {@link #renderSafeNodeId(String, CodebaseGraphDTO)} overload, which is source-aware
     * for anonymous vertices.
     *
     * @param vertex the fully qualified (or literal) class vertex name
     * @return a deterministic DOT-legal node id derived from the fully qualified class name
     */
    String renderSafeNodeId(String vertex) {
        // Full FQN with dots→underscores for uniqueness across packages (Option A)
        // $ -> _ (Java inner/anonymous convention)
        // < -> lt_ and > -> _gt (Kotlin <anonymous> literal)
        return vertex.replace(".", "_").replace("$", "_").replace("<", "lt_").replace(">", "_gt");
    }

    /**
     * Source-aware DOT node id. For an anonymous Kotlin vertex (literal {@code "<anonymous>"} or
     * any {@code <...>} FQN) the enclosing owner is recovered from the source-file path mapped in
     * {@code codebaseGraphDTO.classToSourceFilePathMapping}: the source file's base name without
     * extension (e.g. {@code DeveloperWASDControl.kt} -> {@code DeveloperWASDControl}). The
     * resulting DOT id is {@code <owner>_anonymous}, which is {@code <}/{@code >}-free and human
     * recognizable. When no source path is mapped (or {@code codebaseGraphDTO == null}) this
     * degrades to {@link #renderSafeNodeId(String)} (the {@code lt_anonymous_gt} encoding).
     *
     * <p>For non-anonymous vertices this is identical to {@link #renderSafeNodeId(String)}.
     *
     * @param vertex the fully qualified (or literal) class vertex name
     * @param codebaseGraphDTO the DTO carrying the {@code vertex -> source file path} mapping
     * @return a deterministic DOT-legal node id for the vertex
     */
    String renderSafeNodeId(String vertex, CodebaseGraphDTO codebaseGraphDTO) {
        if (isAnonymousFqn(vertex)) {
            String owner = enclosingSourceFileBaseName(vertex, codebaseGraphDTO);
            if (owner != null) {
                // Append hash of FQN to prevent collisions between anonymous classes
                // from same-named files in different packages
                String discriminator = String.valueOf(Math.abs(vertex.hashCode()));
                return owner.replace("$", "_") + "_anonymous_" + discriminator;
            }
        }
        return renderSafeNodeId(vertex);
    }

    /**
     * Graph-aware DOT node id. Uses simple class name when unique across the graph,
     * falls back to full FQN with dots→underscores when collision exists.
     * Special handling for anonymous/synthetic vertices (uses lt_anonymous_gt encoding),
     * inner classes (always uses FQN), and anonymous with enclosing prefix.
     *
     * @param vertex the fully qualified (or literal) class vertex name
     * @param classGraph the class graph for collision detection
     * @return a deterministic DOT-legal node id for the vertex
     */
    String renderSafeNodeId(String vertex, Graph<String, DefaultWeightedEdge> classGraph) {
        return renderSafeNodeId(vertex, classGraph, null);
    }

    /**
     * Graph-aware DOT node id with DTO support. Uses simple class name when unique across the graph,
     * falls back to full FQN with dots→underscores when collision exists.
     * Special handling for anonymous/synthetic vertices (uses lt_anonymous_gt encoding or
     * source-aware ID from DTO), inner classes (always uses FQN), and anonymous with enclosing prefix.
     *
     * @param vertex the fully qualified (or literal) class vertex name
     * @param classGraph the class graph for collision detection
     * @param codebaseGraphDTO optional DTO for source-aware anonymous vertex IDs
     * @return a deterministic DOT-legal node id for the vertex
     */
    String renderSafeNodeId(
            String vertex, Graph<String, DefaultWeightedEdge> classGraph, CodebaseGraphDTO codebaseGraphDTO) {
        // Special handling for anonymous/synthetic vertices
        if (isAnonymousFqn(vertex)) {
            // If DTO provided, try to get source-aware ID
            if (codebaseGraphDTO != null) {
                String owner = enclosingSourceFileBaseName(vertex, codebaseGraphDTO);
                if (owner != null) {
                    return owner.replace("$", "_") + "_anonymous";
                }
            }
            // No DTO or no source mapping: use lt_anonymous_gt encoding
            return vertex.replace(".", "_")
                    .replace("$", "_")
                    .replace("<", "lt_")
                    .replace(">", "_gt");
        }

        // Check if this is an anonymous class with enclosing class prefix (e.g., dev.Class.<anonymous>)
        if (vertex.contains(".<anonymous>")) {
            return vertex.replace(".", "_")
                    .replace("$", "_")
                    .replace("<", "lt_")
                    .replace(">", "_gt");
        }

        // Check if this is an inner class (contains $)
        if (vertex.contains("$")) {
            // Inner classes always use FQN-based ID
            return vertex.replace(".", "_").replace("$", "_");
        }

        // For regular classes, check if simple name is unique in the graph
        String simpleName = getClassName(vertex);

        // Count occurrences of this simple name in the graph
        long count = classGraph.vertexSet().stream()
                .map(this::getClassName)
                .filter(simpleName::equals)
                .count();

        if (count == 1) {
            // Unique simple name - use it (with $/< > escaping for safety)
            return simpleName.replace("$", "_").replace("<", "lt_").replace(">", "_gt");
        } else {
            // Collision - use full FQN with dots→underscores
            return vertex.replace(".", "_").replace("$", "_");
        }
    }

    /**
     * Derives the enclosing Kotlin/Java class name from the source-file path mapped for an
     * anonymous vertex, e.g. {@code /fxgl-samples/src/main/kotlin/dev/DeveloperWASDControl.kt} ->
     * {@code DeveloperWASDControl}. Returns {@code null} when the vertex has no mapped source
     * path or {@code codebaseGraphDTO == null}.
     */
    private String enclosingSourceFileBaseName(String vertex, CodebaseGraphDTO codebaseGraphDTO) {
        if (codebaseGraphDTO == null) {
            return null;
        }
        Map<String, String> mapping = codebaseGraphDTO.getClassToSourceFilePathMapping();
        if (mapping == null) {
            return null;
        }
        String path = mapping.get(vertex);
        if (path == null || path.isEmpty()) {
            return null;
        }
        // strip a trailing '/' then take the last path segment
        String name = path;
        int slash = name.lastIndexOf('/');
        if (slash >= 0) {
            name = name.substring(slash + 1);
        }
        int dot = name.lastIndexOf('.');
        if (dot >= 0) {
            name = name.substring(0, dot);
        }
        return name.isEmpty() ? null : name;
    }

    /**
     * Builds the DOT label for an anonymous vertex using the enclosing source-file base name as
     * the owner with {@code $} as the enclosing-class separator (escaped as {@code \$} for DOT),
     * e.g. {@code DeveloperWASDControl$anonymous} -> {@code DeveloperWASDControl\$anonymous}.
     * Falls back to the raw class name (e.g. {@code <anonymous>}) when no owner is recoverable.
     */
    private String anonymousOwnerLabel(String vertex, CodebaseGraphDTO codebaseGraphDTO) {
        String owner = enclosingSourceFileBaseName(vertex, codebaseGraphDTO);
        String simple = getClassName(vertex);
        if (owner != null) {
            return owner + "$anonymous";
        }
        return simple;
    }

    /**
     * Returns {@code true} for a Kotlin anonymous-class FQN. OpenRewrite attributes a Kotlin
     * anonymous object / function-literal type with the literal {@code "<anonymous>"} as its
     * trailing simple-name segment, either standalone ({@code "<anonymous>"}) or prefixed by the
     * enclosing class/package (e.g. {@code "dev.DeveloperWASDControl.<anonymous>"}). Such a
     * vertex cannot appear verbatim in a DOT node id (the {@code <}/{@code >} are illegal), so
     * this predicate decides when the source-aware id/label derivation must kick in.
     */
    static boolean isAnonymousFqn(String vertex) {
        if (vertex == null) {
            return false;
        }
        // trailing simple-name segment (text after the last '.'); matches the behaviour of the
        // package-private getClassName(...) used by the renderer without pulling in the
        // non-static helper.
        int dot = vertex.lastIndexOf('.');
        String simple = dot >= 0 ? vertex.substring(dot + 1) : vertex;
        return simple.startsWith("<");
    }

    /**
     * Render-time noise filter: returns {@code true} for sink-only anonymous/synthetic vertices
     * (those with <em>no</em> outgoing edges), so the Class/Cycle Map DOT graph is not cluttered
     * with leaf {@code Outer$1}/{@code Outer$}/{@code <anonymous>}/lambda nodes that contribute
     * nothing to refactor decisions. Active anonymous/synthetic classes (with at least one
     * outgoing edge) are <em>not</em> suppressed and still render.
     *
     * <p>Covers four shapes under one rule:
     * <ul>
     *   <li>Java anonymous inner classes: simple-name suffix after the last {@code $} is purely
     *       numeric (e.g. {@code Outer$1}, {@code Outer$2}).</li>
     *   <li>Java synthetic nested classes with an empty trailing {@code $} (e.g. {@code Outer$}).</li>
     *   <li>Kotlin {@code <anonymous>}: the vertex itself is the literal string.</li>
     *   <li>Kotlin synthetic classes: surfaced with numeric-suffix {@code $N} names exactly like
     *       Java's; the numeric predicate already covers them.</li>
     * </ul>
     *
     * @param graph the class graph; used to compute {@code outDegreeOf(vertex)}
     * @param vertex the candidate vertex
     * @return {@code true} if the vertex should be suppressed from the DOT graph
     */
    static boolean isSinkAnonymousOrSyntheticVertex(Graph<String, DefaultWeightedEdge> graph, String vertex) {
        if (!graph.containsVertex(vertex)) {
            return false;
        }
        if (graph.outDegreeOf(vertex) != 0) {
            return false; // keep nodes that reach out
        }
        if (isAnonymousFqn(vertex)) {
            return true; // Kotlin <anonymous> / literal anonymous sink
        }
        String simple = simpleNameAfterLastDollar(vertex); // post-last-`.` then post-last-`$`
        if (simple.isEmpty()) {
            return true; // trailing-$ synthetic
        }
        return simple.matches("\\d+"); // Outer$1, Foo$2, lambda $$
    }

    /**
     * Computes the simple-name suffix after the last {@code $}: take the text after the last
     * {@code .} (the simple class name), then the text after the last {@code $} within it.
     */
    private static String simpleNameAfterLastDollar(String vertex) {
        int dot = vertex.lastIndexOf('.');
        String afterDot = dot >= 0 ? vertex.substring(dot + 1) : vertex;
        int dollar = afterDot.lastIndexOf('$');
        return dollar >= 0 ? afterDot.substring(dollar + 1) : afterDot;
    }

    private void renderClassGraphEdge(
            Graph<String, DefaultWeightedEdge> classGraph,
            DefaultWeightedEdge edge,
            CodebaseGraphDTO codebaseGraphDTO,
            StringBuilder dot) {
        // render edge
        String[] vertexes = extractVertexes(edge);

        String startVertex = vertexes[0].trim();
        String start = renderSafeNodeId(startVertex, classGraph, codebaseGraphDTO);
        String endVertex = vertexes[1].trim();
        String end = renderSafeNodeId(endVertex, classGraph, codebaseGraphDTO);

        // Suppress edges that touch a sink-only anonymous/synthetic vertex; the vertex itself is
        // skipped in renderClassVertices, so an edge pointing at (or from) it would dangle.
        if (isSinkAnonymousOrSyntheticVertex(classGraph, startVertex)
                || isSinkAnonymousOrSyntheticVertex(classGraph, endVertex)) {
            log.debug("Skipping edge touching a sink anonymous/synthetic vertex: {} -> {}", startVertex, endVertex);
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
            renderClassGraphEdge(classGraph, edge, codebaseGraphDTO, dot);
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
        stringBuilder
                .append("<div align=\"center\">Number of packages: ")
                .append(packageCount)
                .append("  Number of relationships: ")
                .append(relationshipCount)
                .append("<br></div>");
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
