package org.hjug.refactorfirst.report;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.hjug.cbc.RankedCycle;
import org.hjug.cbc.RankedDisharmony;
import org.hjug.gdg.GraphDataGenerator;
import org.jgrapht.Graph;
import org.jgrapht.graph.DefaultWeightedEdge;

@Slf4j
public class HtmlReport extends SimpleHtmlReport {

    int d3Threshold = 700;

    // use Files.readString(Path.of(file))
    // Created by generative AI and modified slightly
    public static final String SUGIYAMA_SIGMA_GRAPH = "<script>\n"
            + "function sugiyamaLayout(graph) {\n" + "    var layers = [];\n"
            + "    var nodeLevels = {};\n"
            + "    var nodes = graph.nodes();\n"
            + "    //var edges = graph.edges();\n"
            + "\n"
            + "    // Step 1: Assign levels to nodes\n"
            + "    function assignLevels() {\n"
            + "        var visited = {};\n"
            + "        var stack = [];\n"
            + "\n"
            + "        function visit(node, level) {\n"
            + "            if (visited[node]) return;\n"
            + "            visited[node] = true;\n"
            + "            nodeLevels[node] = level;\n"
            + "            if (!layers[level]) layers[level] = [];\n"
            + "            layers[level].push(node);\n"
            + "            stack.push(node);\n"
            + "            graph.forEachNeighbor(node, function (neighbor) {\n"
            + "                visit(neighbor, level + 1);\n"
            + "            });\n"
            + "        }\n"
            + "\n"
            + "        nodes.forEach(function (node) {\n"
            + "            if (!visited[node]) visit(node, 0);\n"
            + "        });\n"
            + "    }\n"
            + "\n"
            + "    // Step 2: Reduce edge crossings\n"
            + "    function reduceCrossings() {\n"
            + "        for (var i = 0; i < layers.length - 1; i++) {\n"
            + "            var layer = layers[i];\n"
            + "            var nextLayer = layers[i + 1];\n"
            + "            var positions = {};\n"
            + "\n"
            + "            nextLayer.forEach(function (node, index) {\n"
            + "                positions[node] = index;\n"
            + "            });\n"
            + "\n"
            + "            layer.sort(function (a, b) {\n"
            + "                var aPos = 0, bPos = 0;\n"
            + "                graph.forEachNeighbor(a, function (neighbor) {\n"
            + "                    aPos += positions[neighbor] || 0;\n"
            + "                });\n"
            + "                graph.forEachNeighbor(b, function (neighbor) {\n"
            + "                    bPos += positions[neighbor] || 0;\n"
            + "                });\n"
            + "                return aPos - bPos;\n"
            + "            });\n"
            + "        }\n"
            + "    }\n"
            + "\n"
            + "    // Step 3: Assign positions to nodes\n"
            + "    function assignPositions() {\n"
            + "        var yStep = 100;\n"
            + "        var xStep = 2000;\n"
            + "\n"
            + "        layers.forEach(function (layer, level) {\n"
            + "            var layerWidth = layer.length * xStep;\n"
            + "            var offsetX = ((screen.width - 200) - layerWidth) / 2; // Centering the nodes\n"
            + "\n"
            + "            layer.forEach(function (node, index) {\n"
            + "                graph.setNodeAttribute(node, 'x', offsetX + index * xStep);\n"
            + "                graph.setNodeAttribute(node, 'y', -level * yStep);\n"
            + "            });\n"
            + "        });\n"
            + "    }\n"
            + "\n"
            + "    assignLevels();\n"
            + "    reduceCrossings();\n"
            + "    assignPositions();\n"
            + "}\n"
            + "\n"
            + "function renderGraph(dot) {\n"
            + "    // Parse the DOT graph using graphlib-dot\n"
            + "    const graphlibGraph = graphlibDot.read(dot);\n"
            + "\n"
            + "    // Convert graphlib graph to graphology graph\n"
            + "    const graphologyGraph = new graphology.Graph();\n"
            + "    graphlibGraph.nodes().forEach(node => {\n"
            + "        const attrs = graphlibGraph.node(node);\n"
            + "        graphologyGraph.addNode(node, {\n"
            + "            label: node,\n"
            + "            color: attrs.color,\n"
            + "            // x: Math.random(),\n"
            + "            // y: Math.random(),\n"
            + "            size: 5,\n"
            + "        });\n"
            + "    });\n"
            + "\n"
            + "    graphlibGraph.edges().forEach(edge => {\n"
            + "        const attrs = graphlibGraph.edge(edge);\n"
            + "        graphologyGraph.addEdge(edge.v, edge.w, {\n"
            + "            color: attrs.color,\n"
            + "            size: 1,\n"
            + "            type: 'arrow',\n"
            + "        });\n"
            + "    });\n"
            + "\n"
            + "    sugiyamaLayout(graphologyGraph)\n"
            + "\n"
            + "    return graphologyGraph;\n"
            + "}\n"
            + "</script>";

    public static final String FORCE_3D_GRAPH =
            "<script type=\"module\">\n" + "// SpriteText will only work as import\n"
                    + "        // this script block requires type=module since we are using an import\n"
                    + "        import SpriteText from \"https://esm.sh/three-spritetext\";\n"
                    + "\n"
                    + "        function createForceGraph(popupId, containerName, dot) {\n"
                    + "            // Add event listener for Escape key to close the popup\n"
                    + "            document.addEventListener('keydown', function (event) {\n"
                    + "                if (event.key === 'Escape') {\n"
                    + "                    hidePopup();\n"
                    + "                }\n"
                    + "            });\n"
                    + "\n"
                    + "            document.getElementById('overlay').style.display = 'block';\n"
                    + "            document.getElementById(popupId).style.display = 'block';\n"
                    + "            var container = document.getElementById(containerName);\n"
                    + "\n"
                    + "            // Parse the DOT graph using graphlib-dot\n"
                    + "            const graphlibGraph = graphlibDot.read(dot);\n"
                    + "\n"
                    + "            var nodes = [];\n"
                    + "            var links = [];\n"
                    + "\n"
                    + "            graphlibGraph.nodes().forEach(function (node) {\n"
                    + "                var nodeData = graphlibGraph.node(node);\n"
                    + "                nodes.push({\n"
                    + "                    id: node,\n"
                    + "                    color: nodeData.color || 'white',\n"
                    + "                });\n"
                    + "            });\n"
                    + "\n"
                    + "            graphlibGraph.edges().forEach(function (edge) {\n"
                    + "                links.push({\n"
                    + "                    source: edge.v,\n"
                    + "                    target: edge.w,\n"
                    + "                    color: graphlibGraph.edge(edge).color || 'white',\n"
                    + "                    weight: graphlibGraph.edge(edge).weight,\n"
                    + "                });\n"
                    + "            });\n"
                    + "\n"
                    + "            const gData = {\n"
                    + "                nodes: nodes,\n"
                    + "                links: links\n"
                    + "            };\n"
                    + "\n"
                    + "            // cross-link node objects\n"
                    + "            gData.links.forEach(link => {\n"
                    + "                const a = gData.nodes.find(node => node.id === link.source);\n"
                    + "                const b = gData.nodes.find(node => node.id === link.target);\n"
                    + "                !a.neighbors && (a.neighbors = []);\n"
                    + "                !b.neighbors && (b.neighbors = []);\n"
                    + "                a.neighbors.push(b);\n"
                    + "                b.neighbors.push(a);\n"
                    + "\n"
                    + "                !a.links && (a.links = []);\n"
                    + "                !b.links && (b.links = []);\n"
                    + "                a.links.push(link);\n"
                    + "                b.links.push(link);\n"
                    + "            });\n"
                    + "\n"
                    + "            const Graph = new ForceGraph3D(container)\n"
                    + "                .graphData(gData)\n"
                    + "                .nodeLabel('id')\n"
                    + "                .width(container.clientWidth)\n"
                    + "                .height(container.clientHeight);\n"
                    + "\n"
                    + "            if(gData.links.length + gData.nodes.length < 4000) {\n"
                    + "                console.log(gData.links.length + gData.nodes.length);\n"
                    + "\n"
                    + "\n"
                    + "                // use node labels instead of spheres\n"
                    + "                Graph.nodeThreeObject(node => {\n"
                    + "                    const sprite = new SpriteText(node.id);\n"
                    + "                    sprite.material.depthWrite = false; // make sprite background transparent\n"
                    + "                    sprite.color = node.color;\n"
                    + "                    sprite.textHeight = 4;\n"
                    + "                    return sprite;\n"
                    + "                });\n"
                    + "\n"
                    + "                // code to display weight as link text\n"
                    + "                // may be too much for browsers to handle\n"
                    + "                // Graph\n"
                    + "                //     .linkThreeObjectExtend(true)\n"
                    + "                //     .linkThreeObject(link => {\n"
                    + "                //         // extend link with text sprite\n"
                    + "                //         const sprite = new SpriteText(`${link.weight}`);\n"
                    + "                //         sprite.color = 'lightgrey';\n"
                    + "                //         sprite.textHeight = 3;\n"
                    + "                //         return sprite;\n"
                    + "                //     })\n"
                    + "                //     .linkPositionUpdate((sprite, {start, end}) => {\n"
                    + "                //         const middlePos = Object.assign(...['x', 'y', 'z'].map(c => ({\n"
                    + "                //             [c]: start[c] + (end[c] - start[c]) / 2 // calc middle point\n"
                    + "                //         })));\n"
                    + "                //\n"
                    + "                //         // Position sprite\n"
                    + "                //         Object.assign(sprite.position, middlePos);\n"
                    + "                //     });\n"
                    + "\n"
                    + "\n"
                    + "                // code to highlight nodes & links\n"
                    + "                // TODO: enable via control - see Manipulate Link Force Distance for example\n"
                    + "                const highlightNodes = new Set();\n"
                    + "                const highlightLinks = new Set();\n"
                    + "                let hoverNode = null;\n"
                    + "                Graph\n"
                    + "                    .nodeColor(node => highlightNodes.has(node) ? node === hoverNode ? 'rgb(255,0,0,1)' : 'rgba(255,160,0,0.8)' : 'rgba(0,255,255,0.6)')\n"
                    + "                    .linkWidth(link => highlightLinks.has(link) ? 4 : 1)\n"
                    + "                    .linkDirectionalParticles(link => highlightLinks.has(link) ? 4 : 0)\n"
                    + "                    .linkDirectionalParticleWidth(4)\n"
                    + "                    .onNodeHover(node => {\n"
                    + "                        // no state change\n"
                    + "                        if ((!node && !highlightNodes.size) || (node && hoverNode === node)) return;\n"
                    + "\n"
                    + "                        highlightNodes.clear();\n"
                    + "                        highlightLinks.clear();\n"
                    + "                        if (node) {\n"
                    + "                            highlightNodes.add(node);\n"
                    + "                            node.neighbors.forEach(neighbor => highlightNodes.add(neighbor));\n"
                    + "                            node.links.forEach(link => highlightLinks.add(link));\n"
                    + "                        }\n"
                    + "\n"
                    + "                        hoverNode = node || null;\n"
                    + "\n"
                    + "                        updateHighlight(Graph);\n"
                    + "                    })\n"
                    + "                    .onLinkHover(link => {\n"
                    + "                        highlightNodes.clear();\n"
                    + "                        highlightLinks.clear();\n"
                    + "\n"
                    + "                        if (link) {\n"
                    + "                            highlightLinks.add(link);\n"
                    + "                            highlightNodes.add(link.source);\n"
                    + "                            highlightNodes.add(link.target);\n"
                    + "                        }\n"
                    + "\n"
                    + "                        updateHighlight(Graph);\n"
                    + "                    });\n"
                    + "\n"
                    + "            }\n"
                    + "        }\n"
                    + "\n"
                    + "        // used by highlighting functionality\n"
                    + "        function updateHighlight(Graph) {\n"
                    + "            // trigger update of highlighted objects in scene\n"
                    + "            Graph\n"
                    + "                .nodeColor(Graph.nodeColor())\n"
                    + "                .linkWidth(Graph.linkWidth())\n"
                    + "                .linkDirectionalParticles(Graph.linkDirectionalParticles());\n"
                    + "        }\n"
                    + "\n"
                    + "        // needed to allow the button to open the graph\n"
                    + "        window.createForceGraph = createForceGraph;"
                    + "    </script>";

    // Created by generative AI and modified
    public static final String POPUP_STYLE = "<style>\n" + "        /* Popup container */\n"
            + "        .popup {\n"
            + "            position: fixed;\n"
            + "            display: none;\n"
            + "            width: 95%;\n"
            + "            height: 95%;\n"
            + "            background-color: white;\n"
            + "            border: 1px solid #ccc;\n"
            + "            box-shadow: 0 0 10px rgba(0, 0, 0, 0.1);\n"
            + "            top: 50%;\n"
            + "            left: 50%;\n"
            + "            transform: translate(-50%, -50%);\n"
            + "            z-index: 1000;\n"
            + "            padding: 20px;\n"
            + "            box-sizing: border-box;\n"
            + "        }\n"
            + "\n"
            + "        /* Popup overlay */\n"
            + "        .overlay {\n"
            + "            position: fixed;\n"
            + "            display: none;\n"
            + "            width: 100%;\n"
            + "            height: 100%;\n"
            + "            top: 0;\n"
            + "            left: 0;\n"
            + "            background: rgba(0, 0, 0, 0.5);\n"
            + "            z-index: 999;\n"
            + "        }\n"
            + "\n"
            + "        /* Close button */\n"
            + "        .close-btn {\n"
            + "            position: absolute;\n"
            + "            top: 10px;\n"
            + "            right: 10px;\n"
            + "            cursor: pointer;\n"
            + "        }\n"
            + "    </style>";

    // Created by generative AI and modified
    public static final String POPUP_FUNCTIONS = "<script>\n"
            + "    function showPopup(popupId, containerName, dot) {\n"
            + "        // Add event listener for Escape key to close the popup\n"
            + "        document.addEventListener('keydown', function (event) {\n"
            + "            if (event.key === 'Escape') {\n"
            + "                hidePopup();\n"
            + "            }\n"
            + "        });"
            + "        "
            + "        document.getElementById('overlay').style.display = 'block';\n"
            + "        document.getElementById(popupId).style.display = 'block';\n"
            + "\n"
            + "        var graph = renderGraph(dot);\n"
            + "        var container = document.getElementById(containerName);\n"
            + "\n"
            + "        // Render with Sigma.js\n"
            + "        new Sigma(graph, container);\n"
            + "    }\n"
            + "\n"
            + "    function hidePopup() {\n"
            + "        document.getElementById('overlay').style.display = 'none';\n"
            + "        var popups = document.getElementsByClassName('popup');\n"
            + "        for (var i = 0; i < popups.length; i++) {\n"
            + "            popups[i].style.display = 'none';\n"
            + "        }\n"
            + "\n"
            + "        // Clear the graph containers to remove the previous graphs\n"
            + "        var containers = document.querySelectorAll('[id^=\"graph-container\"]');\n"
            + "        containers.forEach(function(container) {\n"
            + "            while (container.firstChild) {\n"
            + "                container.removeChild(container.firstChild);\n"
            + "            }\n"
            + "        });\n"
            + "// Remove the Escape key event listener\n"
            + "            document.removeEventListener('keydown', function (event) {\n"
            + "                if (event.key === 'Escape') {\n"
            + "                    hidePopup();\n"
            + "                }\n"
            + "            });"
            + "    }\n"
            + "</script>";

    private static final String GOD_CLASS_CHART_LEGEND =
            "       <h2>God Class Chart Legend:</h2>" + "       <table border=\"5px\">\n"
                    + "          <tbody>\n"
                    + "            <tr><td><strong>X-Axis:</strong> Effort to refactor to a non-God class</td></tr>\n"
                    + "            <tr><td><strong>Y-Axis:</strong> Relative churn</td></tr>\n"
                    + "            <tr><td><strong>Color:</strong> Priority of what to fix first</td></tr>\n"
                    + "            <tr><td><strong>Circle size:</strong> Priority (Visual) of what to fix first</td></tr>\n"
                    + "          </tbody>\n"
                    + "        </table>"
                    + "        <br/>";

    private static final String COUPLING_BETWEEN_OBJECT_CHART_LEGEND =
            "       <h2>Coupling Between Objects Chart Legend:</h2>" + "       <table border=\"5px\">\n"
                    + "          <tbody>\n"
                    + "            <tr><td><strong>X-Axis:</strong> Number of objects the class is coupled to</td></tr>\n"
                    + "            <tr><td><strong>Y-Axis:</strong> Relative churn</td></tr>\n"
                    + "            <tr><td><strong>Color:</strong> Priority of what to fix first</td></tr>\n"
                    + "            <tr><td><strong>Circle size:</strong> Priority (Visual) of what to fix first</td></tr>\n"
                    + "          </tbody>\n"
                    + "        </table>"
                    + "        <br/>";

    @Override
    public String printHead() {
        // !Remember to update RefactorFirstMavenReport if this is modified
        return // GH Buttons import
        "<script async defer src=\"https://buttons.github.io/buttons.js\"></script>\n"
                // google chart import
                + "<script type=\"text/javascript\" src=\"https://www.gstatic.com/charts/loader.js\"></script>\n"
                // d3 dot graph imports
                //                + "<script src=\"https://d3js.org/d3.v5.min.js\"></script>\n"
                //                + "<script
                // src=\"https://cdnjs.cloudflare.com/ajax/libs/d3-graphviz/3.0.5/d3-graphviz.min.js\"></script>\n"
                //                + "<script
                // src=\"https://unpkg.com/@hpcc-js/wasm@0.3.11/dist/index.min.js\"></script>\n"

                //                + "<script
                // src=\"https://cdn.jsdelivr.net/npm/@hpcc-js/wasm-graphviz@1.7.0/dist/index.min.js\"></script>\n"
                + "<script src=\"https://cdn.jsdelivr.net/npm/svg-pan-zoom@3.6.1/dist/svg-pan-zoom.min.js\"></script>"

                // sigma graph imports - sigma, graphology, graphlib, and graphlib-dot
                + "<script src=\"https://cdnjs.cloudflare.com/ajax/libs/sigma.js/2.4.0/sigma.min.js\"></script>\n"
                + "<script src=\"https://cdnjs.cloudflare.com/ajax/libs/graphology/0.25.4/graphology.umd.min.js\"></script>\n"
                // may only need graphlib-dot
                + "<script src=\"https://cdnjs.cloudflare.com/ajax/libs/graphlib/2.1.8/graphlib.min.js\"></script>\n"
                + "<script src=\"https://cdn.jsdelivr.net/npm/graphlib-dot@0.6.4/dist/graphlib-dot.min.js\"></script>\n"
                + "<script src=\"https://cdn.jsdelivr.net/npm/3d-force-graph\"></script>\n";
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

    @Override
    String renderGithubButtons() {
        return "<div align=\"center\">\n" + "Show RefactorFirst some &#10084;&#65039;\n"
                + "<br/>\n"
                + "<a class=\"github-button\" href=\"https://github.com/refactorfirst/refactorfirst\" data-icon=\"octicon-star\" data-size=\"large\" data-show-count=\"true\" aria-label=\"Star refactorfirst/refactorfirst on GitHub\">Star</a>\n"
                + "<a class=\"github-button\" href=\"https://github.com/refactorfirst/refactorfirst/fork\" data-icon=\"octicon-repo-forked\" data-size=\"large\" data-show-count=\"true\" aria-label=\"Fork refactorfirst/refactorfirst on GitHub\">Fork</a>\n"
                + "<a class=\"github-button\" href=\"https://github.com/refactorfirst/refactorfirst/subscription\" data-icon=\"octicon-eye\" data-size=\"large\" data-show-count=\"true\" aria-label=\"Watch refactorfirst/refactorfirst on GitHub\">Watch</a>\n"
                + "<a class=\"github-button\" href=\"https://github.com/refactorfirst/refactorfirst/issues\" data-icon=\"octicon-issue-opened\" data-size=\"large\" data-show-count=\"false\" aria-label=\"Issue refactorfirst/refactorfirst on GitHub\">Issue</a>\n"
                + "<a class=\"github-button\" href=\"https://github.com/sponsors/jimbethancourt\" data-icon=\"octicon-heart\" data-size=\"large\" aria-label=\"Sponsor @jimbethancourt on GitHub\">Sponsor</a>\n"
                + "</div>";
    }

    @Override
    String writeGodClassGchartJs(List<RankedDisharmony> rankedDisharmonies, int maxPriority) {
        GraphDataGenerator graphDataGenerator = new GraphDataGenerator();
        String scriptStart = graphDataGenerator.getGodClassScriptStart();
        String bubbleChartData = graphDataGenerator.generateGodClassBubbleChartData(rankedDisharmonies, maxPriority);
        String scriptEnd = graphDataGenerator.getGodClassScriptEnd();

        return scriptStart + bubbleChartData + scriptEnd;
    }

    @Override
    String writeGCBOGchartJs(List<RankedDisharmony> rankedDisharmonies, int maxPriority) {
        GraphDataGenerator graphDataGenerator = new GraphDataGenerator();
        String scriptStart = graphDataGenerator.getCBOScriptStart();
        String bubbleChartData = graphDataGenerator.generateCBOBubbleChartData(rankedDisharmonies, maxPriority);
        String scriptEnd = graphDataGenerator.getCBOScriptEnd();

        return scriptStart + bubbleChartData + scriptEnd;
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
    String renderGodClassChart(List<RankedDisharmony> rankedGodClassDisharmonies, int maxGodClassPriority) {
        StringBuilder stringBuilder = new StringBuilder();

        String godClassChart = writeGodClassGchartJs(rankedGodClassDisharmonies, maxGodClassPriority - 1);
        stringBuilder.append(
                "<div id=\"series_chart_div\" align=\"center\"><script>" + godClassChart + "</script></div>\n");
        stringBuilder.append(renderGithubButtons());
        stringBuilder.append(GOD_CLASS_CHART_LEGEND);

        return stringBuilder.toString();
    }

    @Override
    String renderCBOChart(List<RankedDisharmony> rankedCBODisharmonies, int maxCboPriority) {
        StringBuilder stringBuilder = new StringBuilder();

        String cboChart = writeGCBOGchartJs(rankedCBODisharmonies, maxCboPriority - 1);
        stringBuilder.append(
                "<div id=\"series_chart_div_2\" align=\"center\"><script>" + cboChart + "</script></div>\n");
        stringBuilder.append(renderGithubButtons());
        stringBuilder.append(COUPLING_BETWEEN_OBJECT_CHART_LEGEND);
        return stringBuilder.toString();
    }

    @Override
    public String renderClassGraphVisuals() {
        String dot = buildClassGraphDot(classGraph);
        String classGraphName = "classGraph";

        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append(generateGraphButtons(classGraphName, dot));

        stringBuilder.append(
                "<div align=\"center\">Excludes classes that have no incoming and outgoing edges<br></div>");

        int classCount = classGraph.vertexSet().size();
        int relationshipCount = classGraph.edgeSet().size();
        stringBuilder.append("<div align=\"center\">Number of classes: " + classCount + "  Number of relationships: "
                + relationshipCount + "<br></div>");
        if (classCount + relationshipCount < d3Threshold) {
            stringBuilder.append(generateDotImage(classGraphName));
        } else {
            // revisit and add DOT SVG popup button
            stringBuilder.append("<div align=\"center\">\nSVG is too big to render quickly</div>\n");
        }

        return stringBuilder.toString();
    }

    private StringBuilder generateGraphButtons(String graphName, String dot) {
        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append("<h1 align=\"center\">Class Map</h1>");
        stringBuilder.append("<script>\n");
        stringBuilder.append("const " + graphName + "_dot = " + dot + "\n");
        stringBuilder.append("</script>\n");
        stringBuilder.append(generateForce3DPopup(graphName));
        stringBuilder.append(generate2DPopup(graphName));
        stringBuilder.append(generateHidePopup(graphName));

        stringBuilder.append("<div align=\"center\">\nRed lines represent back edges to remove.<br>\n");
        stringBuilder.append("Zoom in / out with your mouse wheel and click/move to drag the image.\n");
        stringBuilder.append("</div>\n");
        return stringBuilder;
    }

    private static String generateDotImage(String graphName) {
        // revisit and add D3 popup button as well
        return "<div id=\"" + graphName
                + "\" style=\"width: 95%; margin: auto; border: thin solid black\"></div>\n"
                + "<script type=\"module\">\n"
                + "import { Graphviz } from \"https://cdn.jsdelivr.net/npm/@hpcc-js/wasm/dist/index.js\";\n"
                + "    if (Graphviz) {\n"
                + "        const graphviz = await Graphviz.load();\n"
                + "        let svg = graphviz.layout("
                + graphName + "_dot, \"svg\", \"dot\");\n"
                + "        // Set desired width and height\n"
                + "\n"
                + "        // Modify the SVG string to include width and height attributes\n"
                + "        svg = svg.replace('<svg ', `<svg width=\"screen.width\" height=\"screen.height\"`);\n"
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

    String buildClassGraphDot(Graph<String, DefaultWeightedEdge> classGraph) {
        StringBuilder dot = new StringBuilder();
        dot.append("`strict digraph G {\n");

        for (DefaultWeightedEdge edge : classGraph.edgeSet()) {
            renderEdge(classGraph, edge, dot);
        }

        // capture only classes that have a relationship with one or more other classes
        Set<String> vertexesToRender = new HashSet<>();
        for (DefaultWeightedEdge edge : classGraph.edgeSet()) {
            String[] vertexes = extractVertexes(edge);
            vertexesToRender.add(vertexes[0].trim());
            vertexesToRender.add(vertexes[1].trim());
        }

        // render vertices
        for (String vertex : vertexesToRender) {
            dot.append(getClassName(vertex).replace("$", "_"));

            if (vertexesToRemove.contains(vertex)) {
                dot.append(" [color=red style=filled]\n");
            }

            dot.append(";\n");
        }

        dot.append("}`;");
        return dot.toString();
    }

    private void renderEdge(
            Graph<String, DefaultWeightedEdge> classGraph, DefaultWeightedEdge edge, StringBuilder dot) {
        // render edge
        String[] vertexes = extractVertexes(edge);

        String start = getClassName(vertexes[0].trim()).replace("$", "_");
        String end = getClassName(vertexes[1].trim()).replace("$", "_");

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

        if (edgesToRemove.contains(edge)) {
            dot.append(" color = \"red\"");
        }

        dot.append(" ];\n");
    }

    @Override
    public String renderCycleVisuals(RankedCycle cycle) {
        String dot = buildCycleDot(classGraph, cycle);

        String cycleName = getClassName(cycle.getCycleName()).replace("$", "_");

        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append(generateGraphButtons(cycleName, dot));

        if (cycle.getCycleNodes().size() + cycle.getEdgeSet().size() < d3Threshold) {
            stringBuilder.append(generateDotImage(cycleName));
        } else {
            // revisit and add DOT SVG popup button
            stringBuilder.append("<div align=\"center\">\nSVG is too big to render quickly</div>\n");
        }

        stringBuilder.append("<br/>\n");
        stringBuilder.append("<br/>\n");

        return stringBuilder.toString();
    }

    String buildCycleDot(Graph<String, DefaultWeightedEdge> classGraph, RankedCycle cycle) {
        StringBuilder dot = new StringBuilder();
        dot.append("`strict digraph G {\n");

        for (DefaultWeightedEdge edge : cycle.getEdgeSet()) {
            renderEdge(classGraph, edge, dot);
        }

        // render vertices
        for (String vertex : cycle.getVertexSet()) {
            dot.append(getClassName(vertex).replace("$", "_"));

            if (vertexesToRemove.contains(vertex)) {
                dot.append(" [color=red style=filled]\n");
            }

            dot.append(";\n");
        }

        dot.append("}`;");

        return dot.toString().replace("$", "_");
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
