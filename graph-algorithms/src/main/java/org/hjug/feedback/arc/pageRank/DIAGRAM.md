# PageRank Feedback Arc Set (PageRankFAS) Algorithm

Based on the paper *"Computing a Feedback Arc Set Using PageRank"* by Geladaris, Lionakis, and Tollis ([arXiv:2208.09234](https://arxiv.org/abs/2208.09234)).

## High-Level Algorithm Flow

```mermaid
flowchart TD
    A["**Input:** Directed Graph G(V, E)"] --> B["Copy graph into<br/>working graph G'"]
    B --> C{"Does G'<br/>have cycles?"}
    C -- No --> D["**Output:** Feedback Arc Set<br/>(set of removed edges)"]
    C -- Yes --> E["Find Strongly Connected<br/>Components (SCCs)<br/>using Kosaraju's algorithm"]
    E --> F["Filter to non-trivial SCCs<br/>(size > 1)"]
    F --> G["Process each SCC"]
    G --> H["Extract subgraph<br/>for this SCC"]
    H --> I["Build Line Digraph L(G)<br/>from SCC subgraph"]
    I --> J["Run PageRank<br/>on Line Digraph"]
    J --> K["Select edge with<br/>highest PageRank score"]
    K --> L["Remove edge from G'<br/>and add to FAS"]
    L --> C
```

## Line Digraph Construction

Each edge in the original graph becomes a **vertex** in the line digraph. Edges in the line digraph represent adjacency (consecutive traversal) in the original graph.

```mermaid
flowchart TD
    subgraph Original["**Original SCC Subgraph**"]
        direction LR
        oA((A)) -->|e1| oB((B))
        oB -->|e2| oC((C))
        oC -->|e3| oA
        oA -->|e4| oC
    end

    Original --> Transform["Transform: each original edge<br/>becomes a line digraph vertex"]

    subgraph Line["**Line Digraph L(G)**"]
        direction LR
        le1["e1 (A→B)"] -->|"B is source of e2"| le2["e2 (B→C)"]
        le2 -->|"C is source of e3"| le3["e3 (C→A)"]
        le2 -->|"C is source of... none extra"| le2x[ ]
        le3 -->|"A is source of e1"| le1
        le3 -->|"A is source of e4"| le4["e4 (A→C)"]
        le4 -->|"C is source of e3"| le3
        le1 -.-> le4x[ ]
    end

    style le2x display:none
    style le4x display:none
```

## Line Digraph Edge Creation (DFS-Based — Algorithm 3)

```mermaid
flowchart TD
    S["Start DFS from<br/>arbitrary vertex v₀"] --> V["Visit vertex v,<br/>mark as visited"]
    V --> OE["For each outgoing<br/>edge e of v"]
    OE --> LV["Get LineVertex<br/>for edge e"]
    LV --> PREV{"Previous<br/>LineVertex<br/>exists?"}
    PREV -- Yes --> ADD_EDGE["Add edge:<br/>prevLineVertex → currentLineVertex<br/>in Line Digraph"]
    PREV -- No --> CHECK
    ADD_EDGE --> CHECK{"Target of e<br/>already visited?"}
    CHECK -- No --> REC["Recurse DFS on target<br/>with currentLineVertex as prev"]
    CHECK -- Yes --> BACK["Add edges from currentLineVertex<br/>to all LineVertices of<br/>target's outgoing edges<br/>(back-edge handling)"]
    REC --> OE
    BACK --> OE
```

## PageRank Computation (Algorithm 4)

```mermaid
flowchart TD
    INIT["Initialize all LineVertex scores<br/>score(v) = 1 / N"] --> ITER{"Iteration<br/>i < maxIterations?"}
    ITER -- No --> RESULT["Return PageRank scores<br/>for all LineVertices"]
    ITER -- Yes --> NEWMAP["Create new score map<br/>(all zeros)"]
    NEWMAP --> EACH["For each LineVertex v<br/>(in parallel)"]
    EACH --> SINK{"v has outgoing<br/>neighbors?"}
    SINK -- "No (sink)" --> SELF["newScore(v) += score(v)<br/>(keep score on itself)"]
    SINK -- Yes --> DIST["Distribute score(v) equally<br/>among outgoing neighbors:<br/>each gets score(v) / outDegree(v)"]
    DIST --> MERGE["newScore(target) += share<br/>using atomic merge"]
    SELF --> SWAP
    MERGE --> SWAP["Swap: currentScores = newScores"]
    SWAP --> ITER
```

## Selecting the Feedback Edge

```mermaid
flowchart LR
    PR["PageRank scores<br/>on Line Digraph"] --> MAX["Find LineVertex with<br/>**maximum** PageRank score"]
    MAX --> ORIG["Map back to<br/>original edge via<br/>LineVertex.getOriginalEdge()"]
    ORIG --> REMOVE["Remove edge from<br/>working graph &<br/>add to FAS"]
```

## Class Relationships

```mermaid
classDiagram
    class PageRankFAS~V, E~ {
        -Graph originalGraph
        -int pageRankIterations
        -Class edgeClass
        +computeFeedbackArcSet() Set~E~
        -processStronglyConnectedComponent(graph, scc) E
        -createLineDigraph(graph) LineDigraph
        -createLineDigraphEdges(graph, lineDigraph, map)
        -createLineDigraphEdgesDFS(graph, lineDigraph, map, vertex, prev, visited)
        -computePageRank(lineDigraph) Map
        -applyOneIteration(vertices, lineDigraph, current, new)
        -findStronglyConnectedComponents(graph) List
        -hasCycles(graph) boolean
        -createGraphCopy(original) Graph
        -createSubgraph(graph, vertices) Graph
    }

    class LineDigraph~V, E~ {
        -Set vertices
        -Map adjacencyMap
        -Map incomingMap
        +addVertex(vertex) boolean
        +addEdge(source, target) boolean
        +vertexSet() Set
        +getOutgoingNeighbors(vertex) Set
        +getIncomingNeighbors(vertex) Set
    }

    class LineVertex~V, E~ {
        -V source
        -V target
        -E originalEdge
        +getSource() V
        +getTarget() V
        +getOriginalEdge() E
    }

    PageRankFAS --> LineDigraph : creates
    PageRankFAS --> LineVertex : creates
    LineDigraph o-- LineVertex : contains
```
