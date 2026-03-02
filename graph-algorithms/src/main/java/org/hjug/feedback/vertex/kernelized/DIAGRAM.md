# Kernelized Directed Feedback Vertex Set (DFVS) Algorithm

Based on: *"Wannabe Bounded Treewidth Graphs Admit a Polynomial Kernel for DFVS"* (Lokshtanov et al.)  
https://doi.org/10.1145/3711669
## Class Architecture

```mermaid
classDiagram
    direction TB

    class DirectedFeedbackVertexSetSolver~V,E~ {
        -Graph graph
        -Set~V~ modulator
        -Map vertexWeights
        -int eta
        -Set~V~ remainder
        -Map zones
        -Map kDfvsRepresentatives
        -int k
        +solve() DirectedFeedbackVertexSetResult
        +solve(int k) DirectedFeedbackVertexSetResult
        -computeZoneDecomposition(k)
        -computeKDfvsRepresentatives(k)
        -solveWithReductionRules(k)
    }

    class EnhancedParameterComputer~V,E~ {
        -TreewidthComputer treewidthComputer
        -FeedbackVertexSetComputer fvsComputer
        -ModulatorComputer modulatorComputer
        +computeOptimalParameters(graph, maxSize) EnhancedParameters
        +computeParameters(graph, modulator) EnhancedParameters
    }

    class ParameterComputer~V,E~ {
        -TreewidthComputer treewidthComputer
        -FeedbackVertexSetComputer fvsComputer
        +computeParameters(graph) Parameters
        +computeParametersWithOptimalModulator(graph, maxSize) Parameters
    }

    class FeedbackVertexSetComputer~V,E~ {
        +computeK(graph) int
        +greedyFeedbackVertexSet(graph) Set~V~
        -stronglyConnectedComponentsBasedFVS(graph) Set~V~
        -degreeBasedFeedbackVertexSet(graph) Set~V~
        -localSearchFeedbackVertexSet(graph) Set~V~
    }

    class TreewidthComputer~V,E~ {
        +computeEta(graph, modulator) int
        -minDegreeEliminationTreewidth(graph) int
        -fillInHeuristicTreewidth(graph) int
        -maxCliqueTreewidth(graph) int
        -greedyTriangulationTreewidth(graph) int
    }

    class ModulatorComputer~V,E~ {
        +computeModulator(graph, targetTw, maxSize) ModulatorResult
        -computeGreedyDegreeModulator() Set~V~
        -computeFeedbackVertexSetModulator() Set~V~
        -computeTreewidthDecompositionModulator() Set~V~
        -computeHighDegreeVertexModulator() Set~V~
        -computeBottleneckVertexModulator() Set~V~
    }

    class DirectedFeedbackVertexSetResult~V~ {
        -Set~V~ feedbackVertices
        +getFeedbackVertices() Set~V~
        +size() int
    }

    EnhancedParameterComputer --> TreewidthComputer : uses
    EnhancedParameterComputer --> FeedbackVertexSetComputer : uses
    EnhancedParameterComputer --> ModulatorComputer : uses
    ParameterComputer --> TreewidthComputer : uses
    ParameterComputer --> FeedbackVertexSetComputer : uses
    ModulatorComputer --> TreewidthComputer : uses
    ModulatorComputer --> FeedbackVertexSetComputer : uses
    DirectedFeedbackVertexSetSolver --> DirectedFeedbackVertexSetResult : produces
```

## Algorithm Overview — Three-Phase Kernelization

```mermaid
flowchart TD
    Start(["`**Input:** Directed graph G, modulator M, treewidth η, weights`"]) --> SCC

    SCC["`**Compute default k**
    Kosaraju SCC count as lower bound`"]
    SCC --> P1

    subgraph P1["Phase 1 — Zone Decomposition"]
        direction TB
        P1A["`**Remove modulator** from graph
        G' = G ∖ M`"]
        P1A --> P1B["`**Compute minimal FVS** S
        in G' (greedy, up to k vertices)`"]
        P1B --> P1C{"|S| > k?"}
        P1C -- Yes --> NO_INST(["`**NO-instance**
        return empty`"])
        P1C -- No --> P1D["`**Compute flow-blocker F**
        For each modulator pair (u,v):
        find min vertex cut ≤ k in G'`"]
        P1D --> P1E["`**Compute remainder R**
        R = S ∪ F
        Bound: |R| ≤ 2k(η+1)(|M|²+1)`"]
        P1E --> P1F["`**Partition into zones**
        Vertices not in M or R →
        connected components = zones`"]
    end

    P1 --> P2

    subgraph P2["Phase 2 — k-DFVS Representative Marking"]
        direction TB
        P2A["`**For each zone Z** (in parallel):`"]
        P2A --> P2B["`Compute **SCCs** within zone subgraph`"]
        P2B --> P2C["`From each non-trivial SCC,
        select **highest-degree vertex**
        as representative`"]
        P2C --> P2D["`Bound representative size:
        |rep| ≤ (k · |M|)^(η²)`"]
    end

    P2 --> P3

    subgraph P3["Phase 3 — Reduction Rules & Solve"]
        direction TB
        P3A["`**Apply Reduction Rules 5 & 6**
        For each zone:`"]
        P3A --> P3B["`Identify **non-representative**
        zone vertices`"]
        P3B --> P3C["`Remove edges between
        **modulator ↔ non-representative**
        vertices`"]
        P3C --> P3D["`**Add bypass edges** through
        representatives to preserve
        cycle structure`"]
        P3D --> P3E["`**Solve kernelized instance**
        Collect all representatives +
        high-degree remainder vertices`"]
    end

    P3 --> Result(["`**Output:** DirectedFeedbackVertexSetResult
    containing the DFVS`"])

    style P1 fill:#1a3a5c,stroke:#4a9eff,color:#fff
    style P2 fill:#3a1a5c,stroke:#9a4aff,color:#fff
    style P3 fill:#5c3a1a,stroke:#ff9a4a,color:#fff
    style Start fill:#0d7377,stroke:#14ffec,color:#fff
    style Result fill:#0d7377,stroke:#14ffec,color:#fff
    style NO_INST fill:#7a1a1a,stroke:#ff4a4a,color:#fff
```

## Bypass Edge Creation Detail

```mermaid
flowchart TD
    BE_Start(["`Edge to remove:
    **source → target**`"]) --> M1

    M1{"`**Method 1:** Find single
    representative R where
    source→R and R→target?`"}
    M1 -- Found --> M1A["`Add edges:
    source→R, R→target`"]
    M1A --> Done

    M1 -- Not found --> M2

    M2{"`**Method 2:** Find chain
    of representatives via BFS
    source→R₁→…→Rₙ→target?`"}
    M2 -- Found --> M2A["`Add edges along
    bypass chain`"]
    M2A --> Done

    M2 -- Not found --> M3

    M3["`**Method 3:** Minimal bypass
    Find sourceReachable ∩ reps
    Find targetReachable ∩ reps`"]
    M3 --> M3A{Same rep?}
    M3A -- Yes --> M3B["`source→rep→target`"]
    M3A -- No --> M3C["`source→srcRep→tgtRep→target`"]
    M3B --> Done
    M3C --> Done

    Done(["`Bypass complete
    *(rollback on failure)*`"])

    style BE_Start fill:#0d7377,stroke:#14ffec,color:#fff
    style Done fill:#0d7377,stroke:#14ffec,color:#fff
```

## Parameter Computation Pipeline

```mermaid
flowchart LR
    G(["`**Input Graph G**`"]) --> PC

    subgraph PC["EnhancedParameterComputer"]
        direction TB
        FVS["`**FeedbackVertexSetComputer**
        4 parallel algorithms:
        • Greedy max-degree
        • SCC-based
        • Degree-scored
        • Local search
        → min result = **k**`"]

        MC["`**ModulatorComputer**
        5 parallel strategies:
        • Greedy degree
        • FVS-based
        • Treewidth decomposition
        • High-degree vertex
        • Bottleneck vertex
        → best modulator = **M**`"]

        TWC["`**TreewidthComputer**
        4 parallel heuristics:
        • Min-degree elimination
        • Fill-in heuristic
        • Max-clique
        • Greedy triangulation
        → min result = **η**`"]

        FVS --> PARAMS
        MC --> TWC
        TWC --> PARAMS
        PARAMS["`**Parameters**
        k, M, η, quality`"]
    end

    PC --> SOLVER(["`**DirectedFeedbackVertexSetSolver**
    solve(k) with M and η`"])

    style G fill:#0d7377,stroke:#14ffec,color:#fff
    style SOLVER fill:#0d7377,stroke:#14ffec,color:#fff
    style PC fill:#1a1a3a,stroke:#4a4aff,color:#fff
```

## Key Concepts

| Symbol | Meaning |
|--------|---------|
| **G** | Input directed graph |
| **M** (modulator) | Set of vertices whose removal yields a bounded-treewidth graph |
| **η** (eta) | Treewidth of G ∖ M (undirected) |
| **k** | Size of the minimum directed feedback vertex set |
| **S** | Minimal FVS of G ∖ M |
| **F** | Flow-blocker — min vertex cuts between modulator pairs |
| **R** | Remainder = S ∪ F |
| **Zones** | Connected components of V ∖ (M ∪ R) |
| **Representatives** | Highest-degree vertices from each non-trivial SCC per zone |
| **Kernel bound** | (k · \|M\|)^O(η²) |
