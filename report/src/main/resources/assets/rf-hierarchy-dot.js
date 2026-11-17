// Client-side DOT synthesis from the nested package/class hierarchy in the
// rf-data payload (classMap.hierarchy, packageMap.hierarchy,
// classCycles.largestCycle.hierarchy). Port of the page repo's
// lib/hierarchy-dot.js, which mirrors NestedDotBuilder in the RefactorFirst
// report module (Java) — the two render byte-identical DOT and must stay in
// lockstep; T7.1 pins the byte parity against the NestedDotBuilder goldens.
//
// Layout contract (why clusters come first): in DOT a node belongs to the
// cluster where it is first defined, so packages are emitted as labelled
// `subgraph cluster_…` blocks containing their class nodes (ellipses) and
// child packages before any edge is declared.
//
// Color contract: classes/edges marked for removal stay red; packages that
// need to be broken up render pink — clusters via `style=filled
// fillcolor=pink` when the package has no node of its own, package-map
// rectangle nodes via `color=pink style=filled`. Every cluster declares an
// explicit fill (white when unflagged) because vizdom propagates a styled
// cluster's fill to its nested cluster rects. Only edge labels sit on the
// page background, which is why edgeFontColor exists (vizdom bakes fontcolor
// into fill-only glyph paths at parse time, unreachable by CSS; the dark
// palette re-synthesizes with fontcolor = #9fb0c0).
//
// Identifier contract: cluster ids, node ids, edge endpoints and weights are
// interpolated UNQUOTED — re-quoting them would break the byte-identical
// lockstep with the Java builder — so every raw identifier is first validated
// against the NestedDotBuilder producer contract. Labels and URLs render
// inside quoted strings and keep their own escaping and http(s) gate.
(function () {
    'use strict';

    /**
     * Escapes a value for a DOT double-quoted string: backslashes, quotes and
     * line breaks become their DOT escape sequences (mirrors the Java
     * escapeDotQuoted in the report module).
     */
    function escapeDotQuoted(value) {
        if (value === null || value === undefined) return '';
        return String(value)
            .replace(/\\/g, '\\\\')
            .replace(/"/g, '\\"')
            .replace(/\n/g, '\\n')
            .replace(/\r/g, '\\r')
            .replace(/\t/g, '\\t');
    }

    /**
     * A producer-shaped bare DOT identifier: the NestedDotBuilder producer
     * contract yields only letters, digits and underscores, so anything else
     * cannot be interpolated raw without risking DOT injection.
     */
    const DOT_ID = /^[\p{L}\p{Nd}_]+$/u;

    /** Whether the value may be emitted as a bare DOT token. */
    function isDotId(value) {
        return typeof value === 'string' && DOT_ID.test(value);
    }

    /** Only http(s) URLs may become links: report payloads are untrusted. */
    function httpUrl(url) {
        try {
            const parsed = new URL(url);
            return parsed.protocol === 'http:' || parsed.protocol === 'https:';
        } catch (error) {
            return false;
        }
    }

    /** Returns a node's hyperlink target from its source path, or null when unresolvable. */
    function nodeHyperlinkUrl(path, repoUrl) {
        if (!path || path === '') return null;
        const url = (repoUrl || '') + path;
        return httpUrl(url) ? url : null;
    }

    /**
     * Synthesizes the nested DOT graph for one hierarchy payload. Out-of-contract
     * ids are dropped rather than emitted; labels and URLs keep their escaping
     * and http(s) gate.
     */
    function hierarchyToDot(hierarchy, options) {
        const repoUrl = options && options.repoUrl ? options.repoUrl : '';
        const edgeFontColor =
            options && typeof options.edgeFontColor === 'string' ? options.edgeFontColor : '';
        const parts = ['strict digraph G {'];
        appendPackages((hierarchy && hierarchy.packages) || [], repoUrl, edgeFontColor, parts);
        appendEdges((hierarchy && hierarchy.edges) || [], edgeFontColor, parts);
        parts.push('}');
        return parts.join('\n');
    }

    /**
     * Synthesizes the package-map graph's flat DOT: one `shape=ellipse` node per
     * package that participates in at least one relationship (or was selected for
     * removal to break cycles), and the edges carry only the relationship weights.
     */
    function packageHierarchyToDot(hierarchy, options) {
        const repoUrl = options && options.repoUrl ? options.repoUrl : '';
        const edgeFontColor =
            options && typeof options.edgeFontColor === 'string' ? options.edgeFontColor : '';
        const packages = (hierarchy && hierarchy.packages) || [];
        const edges = (hierarchy && hierarchy.edges) || [];

        const parts = ['strict digraph G {'];
        const appendNodes = pkgs => {
            for (const pkg of pkgs) {
                if (isDotId(pkg && pkg.nodeId)) {
                    let line = pkg.nodeId + ' [label="' + escapeDotQuoted(pkg.name) + '" shape=ellipse';
                    if (pkg.needsBreakUp) line += ' color=red style=filled';
                    const url = nodeHyperlinkUrl(pkg.path, repoUrl);
                    if (url) line += ' URL="' + escapeDotQuoted(url) + '" target="_blank"';
                    parts.push(line + '];');
                }
                appendNodes((pkg && pkg.children) || []);
            }
        };
        appendNodes(packages);
        appendEdges(edges, edgeFontColor, parts);
        parts.push('}');
        return parts.join('\n');
    }

    /** Appends one package cluster block per entry. */
    function appendPackages(packages, repoUrl, edgeFontColor, parts) {
        for (const pkg of packages) {
            appendPackage(pkg, repoUrl, edgeFontColor, parts);
        }
    }

    /**
     * Appends one package cluster block. A package without a producer-contract
     * cluster id cannot be named in the raw `subgraph` header, so the whole
     * block — children and classes included — is dropped.
     */
    function appendPackage(pkg, repoUrl, edgeFontColor, parts) {
        if (!isDotId(pkg && pkg.id)) return;
        parts.push('subgraph ' + pkg.id + ' {');
        parts.push('label="' + escapeDotQuoted(pkg.name) + '";');
        // Every cluster declares an explicit fill: vizdom propagates a styled
        // cluster's fill to its nested cluster rects, so unflagged children of
        // a pink break-up cluster must say white explicitly or they would
        // inherit the pink.
        if (pkg.needsBreakUp && !pkg.renderAsNode) {
            parts.push('style=filled fillcolor=pink;');
        } else {
            parts.push('style=filled fillcolor=white;');
        }
        if (pkg.renderAsNode && isDotId(pkg.nodeId)) {
            let line = pkg.nodeId + ' [label="' + escapeDotQuoted(pkg.name) + '" shape=rectangle';
            if (pkg.needsBreakUp) line += ' color=pink style=filled';
            parts.push(line + '];');
        }
        for (const cls of pkg.classes || []) {
            if (!isDotId(cls && cls.id)) continue;
            let line = cls.id + ' [';
            if (cls.label !== cls.id) line += ' label="' + escapeDotQuoted(cls.label) + '"';
            line += ' shape=ellipse';
            if (cls.markedForRemoval) line += ' color=red style=filled';
            const url = nodeHyperlinkUrl(cls.path, repoUrl);
            if (url) line += ' URL="' + escapeDotQuoted(url) + '" target="_blank"';
            parts.push(line + '];');
        }
        appendPackages(pkg.children || [], repoUrl, edgeFontColor, parts);
        parts.push('}');
    }

    /**
     * Appends the graph's edges after all nodes, keeping the emitters' exact
     * attribute format and highlighting removals in red. Both endpoints must be
     * bare DOT ids and the weight a finite number — out-of-contract edges are
     * dropped rather than emitted raw.
     */
    function appendEdges(edges, edgeFontColor, parts) {
        for (const edge of edges) {
            if (
                !edge ||
                !isDotId(edge.source) ||
                !isDotId(edge.target) ||
                !Number.isFinite(edge.weight)
            ) {
                continue;
            }
            let line = edge.source + ' -> ' + edge.target + ' [ label = "' + edge.weight + '" weight = "' + edge.weight + '"';
            if (edge.markedForRemoval) line += ' color = "red"';
            if (edgeFontColor) line += ' fontcolor = "' + edgeFontColor + '"';
            parts.push(line + ' ];');
        }
    }

    window.RF.hierarchyToDot = hierarchyToDot;
    window.RF.packageHierarchyToDot = packageHierarchyToDot;
    window.RF.escapeDotQuoted = escapeDotQuoted;
})();
