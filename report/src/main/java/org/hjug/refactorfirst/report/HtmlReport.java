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
    public static final String POPUP_FUNCTIONS =
            "<script>\n" + "    function showPopup(popupId, containerName, dot) {\n"
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
                + "<script src=\"https://d3js.org/d3.v5.min.js\"></script>\n"
                + "<script src=\"https://unpkg.com/d3-graphviz@3.0.5/build/d3-graphviz.min.js\"></script>\n"
                + "<script src=\"https://unpkg.com/@hpcc-js/wasm@0.3.11/dist/index.min.js\"></script>\n"
                // sigma graph imports - sigma, graphology, graphlib, and graphlib-dot
                + "<script src=\"https://cdnjs.cloudflare.com/ajax/libs/sigma.js/2.4.0/sigma.min.js\"></script>\n"
                + "<script src=\"https://cdnjs.cloudflare.com/ajax/libs/graphology/0.25.4/graphology.umd.min.js\"></script>\n"
                // may only need graphlib-dot
                + "<script src=\"https://cdnjs.cloudflare.com/ajax/libs/graphlib/2.1.8/graphlib.min.js\"></script>\n"
                + "<script src=\"https://cdn.jsdelivr.net/npm/graphlib-dot@0.6.4/dist/graphlib-dot.min.js\"></script>\n"
                + SUGIYAMA_SIGMA_GRAPH
                + POPUP_FUNCTIONS
                + POPUP_STYLE;
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
    public String renderClassGraphDotImage() {
        String dot = buildClassGraphDot(classGraph);

        String classGraphName = "classGraph";

        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append("<h1>Class Map</h1>");
        if (classGraph.vertexSet().size() + classGraph.edgeSet().size() < d3Threshold) {
            stringBuilder.append(
                    "<div align=\"center\" id=\"" + classGraphName + "\" style=\"border: thin solid black\"></div>\n");
            stringBuilder.append("<script>\n");
            stringBuilder.append("const " + classGraphName + "_dot = " + dot + "\n");
            stringBuilder.append("d3.select(\"#" + classGraphName + "\")\n");
            stringBuilder.append(".graphviz()\n");
            stringBuilder.append(".width(screen.width - " + pixels + ")\n");
            stringBuilder.append(".height(screen.height)\n");
            stringBuilder.append(".fit(true)\n");
            stringBuilder.append(".renderDot(" + classGraphName + "_dot);\n");
            stringBuilder.append("</script>\n");
        } else {
            // revisit and add D3 popup button as well
            stringBuilder.append("<script>\n");
            stringBuilder.append("const " + classGraphName + "_dot = " + dot + "\n");
            stringBuilder.append("</script>\n");
            stringBuilder.append(generatePopup(classGraphName));
        }

        stringBuilder.append("<div align=\"center\">\n");
        stringBuilder.append(
                "<p>Red lines represent back edges to remove. Remove one to start decomposing the cycle.</p>\n");
        stringBuilder.append("<p>Zoom in / out with your mouse wheel and click/move to drag the image.</p>\n");
        stringBuilder.append("</div>\n");
        stringBuilder.append("<br/>\n");
        stringBuilder.append("<br/>\n");

        return stringBuilder.toString();
    }

    String buildClassGraphDot(Graph<String, DefaultWeightedEdge> classGraph) {
        StringBuilder dot = new StringBuilder();
        dot.append("`strict digraph G {\n");

        Set<String> vertexesToRender = new HashSet<>();
        for (DefaultWeightedEdge edge : classGraph.edgeSet()) {
            // DownloadManager -> Download [ label="1" color="red" ];

            // render edge
            String[] vertexes = extractVertexes(edge);
            String start = getClassName(vertexes[0].trim()).replace("$", "_");
            String end = getClassName(vertexes[1].trim()).replace("$", "_");

            vertexesToRender.add(vertexes[0].trim());
            vertexesToRender.add(vertexes[1].trim());

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

            if (edgesAboveDiagonal.contains(edge)) {
                dot.append(" color = \"red\"");
            }

            dot.append(" ];\n");
        }

        // render vertices
        // e.g DownloadManager;
        //        for (String vertex : classGraph.vertexSet()) {
        for (String vertex : vertexesToRender) {
            dot.append(getClassName(vertex).replace("$", "_"));
            dot.append(";\n");
        }

        dot.append("}`;");
        return dot.toString();
    }

    @Override
    public String renderCycleDotImage(RankedCycle cycle) {
        String dot = buildCycleDot(classGraph, cycle);

        String cycleName = getClassName(cycle.getCycleName()).replace("$", "_");

        StringBuilder stringBuilder = new StringBuilder();
        if (cycle.getCycleNodes().size() + cycle.getEdgeSet().size() < d3Threshold) {
            stringBuilder.append(
                    "<div align=\"center\" id=\"" + cycleName + "\" style=\"border: thin solid black\"></div>\n");
            stringBuilder.append("<script>\n");
            stringBuilder.append("const " + cycleName + "_dot = " + dot + "\n");
            stringBuilder.append("d3.select(\"#" + cycleName + "\")\n");
            stringBuilder.append(".graphviz()\n");
            stringBuilder.append(".width(screen.width - " + pixels + ")\n");
            stringBuilder.append(".height(screen.height)\n");
            stringBuilder.append(".fit(true)\n");
            stringBuilder.append(".renderDot(" + cycleName + "_dot);\n");
            stringBuilder.append("</script>\n");
        } else {
            // revisit and add D3 popup button as well
            stringBuilder.append("<script>\n");
            stringBuilder.append("const " + cycleName + "_dot = " + dot + "\n");
            stringBuilder.append("</script>\n");
            stringBuilder.append(generatePopup(cycleName));
        }

        stringBuilder.append("<div align=\"center\">\n");
        stringBuilder.append(
                "<p>Red lines represent back edges to remove. Remove one to start decomposing the cycle.</p>\n");
        stringBuilder.append("<p>Zoom in / out with your mouse wheel and click/move to drag the image.</p>\n");
        stringBuilder.append("</div>\n");
        stringBuilder.append("<br/>\n");
        stringBuilder.append("<br/>\n");

        return stringBuilder.toString();
    }

    String buildCycleDot(Graph<String, DefaultWeightedEdge> classGraph, RankedCycle cycle) {
        StringBuilder dot = new StringBuilder();

        dot.append("`strict digraph G {\n");

        // render vertices
        // e.g DownloadManager;
        for (String vertex : cycle.getVertexSet()) {
            dot.append(getClassName(vertex).replace("$", "_"));
            dot.append(";\n");
        }

        for (DefaultWeightedEdge edge : cycle.getEdgeSet()) {
            // DownloadManager -> Download [ label="1" color="red" ];

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

            if (edgesAboveDiagonal.contains(edge)) {
                dot.append(" color = \"red\"");
            }

            if (cycle.getMinCutEdges().contains(edge) && !edgesAboveDiagonal.contains(edge)) {
                dot.append(" color = \"blue\"");
            }

            dot.append(" ];\n");
        }

        dot.append("}`;");

        return dot.toString().replace("$", "_");
    }

    String generatePopup(String cycleName) {
        // Created by generative AI and modified
        return "<button style=\"display: block; margin: 0 auto;\" onclick=\"showPopup('popup-" + cycleName
                + "', 'graph-container-" + cycleName + "'," + cycleName + "_dot )\">Show " + cycleName
                + " Cycle Popup</button>\n" + "\n"
                + "<div class=\"popup\" id=\"popup-"
                + cycleName + "\">\n" + "    <span class=\"close-btn\" onclick=\"hidePopup()\">×</span>\n"
                + "    <div id=\"graph-container-"
                + cycleName + "\" style=\"width: 100%; height: 100%;\"></div>\n" + "</div>";
    }
}
