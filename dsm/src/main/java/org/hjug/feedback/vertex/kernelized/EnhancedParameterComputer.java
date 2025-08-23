package org.hjug.feedback.vertex.kernelized;

import org.hjug.feedback.SuperTypeToken;
import org.jgrapht.Graph;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Enhanced parameter computer with integrated modulator calculation
 */
public class EnhancedParameterComputer<V, E> {

    private final TreewidthComputer<V, E> treewidthComputer;
    private final FeedbackVertexSetComputer<V, E> fvsComputer;
    private final ModulatorComputer<V, E> modulatorComputer;
    private final ExecutorService executorService;

    public EnhancedParameterComputer(SuperTypeToken<E> edgeTypeToken) {
        this.treewidthComputer = new TreewidthComputer<>();
        this.fvsComputer = new FeedbackVertexSetComputer<>(edgeTypeToken);
        this.modulatorComputer = new ModulatorComputer<>(edgeTypeToken);
        this.executorService = Executors.newWorkStealingPool();
    }

    public EnhancedParameterComputer(SuperTypeToken<E> edgeTypeToken, int parallelismLevel) {
        this.treewidthComputer = new TreewidthComputer<>(parallelismLevel);
        this.fvsComputer = new FeedbackVertexSetComputer<>(edgeTypeToken, parallelismLevel);
        this.modulatorComputer = new ModulatorComputer<>(edgeTypeToken, parallelismLevel);
        this.executorService = Executors.newWorkStealingPool(parallelismLevel);
    }

    /**
     * Computes parameters with automatic modulator optimization
     */
    public EnhancedParameters<V> computeOptimalParameters(Graph<V, E> graph, int maxModulatorSize) {
        return computeOptimalParameters(graph, maxModulatorSize, 3); // Default target treewidth
    }

    /**
     * Computes parameters with specific target treewidth
     */
    public EnhancedParameters<V> computeOptimalParameters(Graph<V, E> graph, int maxModulatorSize, int targetTreewidth) {
        // Compute k (feedback vertex set size) - this doesn't depend on modulator
        CompletableFuture<Integer> kFuture = CompletableFuture.supplyAsync(() ->
                fvsComputer.computeK(graph), executorService);

        // Compute optimal modulator
        CompletableFuture<ModulatorComputer.ModulatorResult<V>> modulatorFuture =
                CompletableFuture.supplyAsync(() ->
                                modulatorComputer.computeModulator(graph, targetTreewidth, maxModulatorSize),
                        executorService);

        // Wait for both computations
        try {
            int k = kFuture.get();
            ModulatorComputer.ModulatorResult<V> modulatorResult = modulatorFuture.get();

            return new EnhancedParameters<>(
                    k,
                    modulatorResult.getModulator(),
                    modulatorResult.getResultingTreewidth(),
                    modulatorResult.getQualityScore()
            );

        } catch (Exception e) {
            throw new RuntimeException("Parameter computation failed", e);
        }
    }

    /**
     * Computes parameters with given modulator
     */
    public EnhancedParameters<V> computeParameters(Graph<V, E> graph, Set<V> modulator) {
        int k = fvsComputer.computeK(graph);
        int eta = treewidthComputer.computeEta(graph, modulator);
        double quality = computeParameterQuality(k, modulator.size(), eta);

        return new EnhancedParameters<>(k, modulator, eta, quality);
    }

    /**
     * Finds multiple good modulators and returns the best parameters
     */
    public List<EnhancedParameters<V>> computeMultipleParameterOptions(Graph<V, E> graph,
                                                                       int maxModulatorSize,
                                                                       int numOptions) {
        List<CompletableFuture<EnhancedParameters<V>>> futures = new ArrayList<>();

        // Try different target treewidths
        for (int targetTreewidth = 1; targetTreewidth <= Math.min(5, maxModulatorSize); targetTreewidth++) {
            final int tw = targetTreewidth;
            futures.add(CompletableFuture.supplyAsync(() ->
                    computeOptimalParameters(graph, maxModulatorSize, tw), executorService));
        }

        // Try different modulator size limits
        for (int maxSize = Math.min(3, maxModulatorSize); maxSize <= maxModulatorSize; maxSize += Math.max(1, maxModulatorSize / 4)) {
            final int size = maxSize;
            futures.add(CompletableFuture.supplyAsync(() ->
                    computeOptimalParameters(graph, size, 3), executorService));
        }

        return futures.stream()
                .map(CompletableFuture::join)
                .distinct()
                .sorted((p1, p2) -> Double.compare(p1.getQualityScore(), p2.getQualityScore()))
                .limit(numOptions)
                .collect(java.util.stream.Collectors.toList());
    }

    /**
     * Validates that a modulator actually achieves the desired treewidth
     */
    public boolean validateModulator(Graph<V, E> graph, Set<V> modulator, int targetTreewidth) {
        int actualTreewidth = treewidthComputer.computeEta(graph, modulator);
        return actualTreewidth <= targetTreewidth;
    }

    /**
     * Computes parameter quality score
     */
    private double computeParameterQuality(int k, int modulatorSize, int eta) {
        // Lower is better: prioritize small k, then small modulator, then small eta
        return k * 10.0 + modulatorSize * 5.0 + eta * 1.0;
    }

    public void shutdown() {
        treewidthComputer.shutdown();
        fvsComputer.shutdown();
        modulatorComputer.shutdown();
        if (executorService != null && !executorService.isShutdown()) {
            executorService.shutdown();
        }
    }

    /**
     * Enhanced parameters container with modulator information
     */
    public static class EnhancedParameters<V> {
        private final int k;              // feedback vertex set size
        private final Set<V> modulator;   // treewidth modulator
        private final int eta;            // treewidth after modulator removal
        private final double qualityScore; // overall quality score

        public EnhancedParameters(int k, Set<V> modulator, int eta, double qualityScore) {
            this.k = k;
            this.modulator = new HashSet<>(modulator);
            this.eta = eta;
            this.qualityScore = qualityScore;
        }

        public int getK() { return k; }
        public Set<V> getModulator() { return new HashSet<>(modulator); }
        public int getModulatorSize() { return modulator.size(); }
        public int getEta() { return eta; }
        public double getQualityScore() { return qualityScore; }

        /**
         * Total parameter for the DFVS kernelization: k + ℓ
         */
        public int getTotalParameter() { return k + modulator.size(); }

        /**
         * Kernel size bound: (k·ℓ)^O(η²)
         */
        public double getKernelSizeBound() {
            if (k == 0 || modulator.size() == 0) return 1.0;
            return Math.pow(k * modulator.size(), eta * eta);
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (!(obj instanceof EnhancedParameters)) return false;
            EnhancedParameters<?> other = (EnhancedParameters<?>) obj;
            return k == other.k && eta == other.eta && modulator.equals(other.modulator);
        }

        @Override
        public int hashCode() {
            return Objects.hash(k, modulator, eta);
        }

        @Override
        public String toString() {
            return String.format("EnhancedParameters{k=%d, |M|=%d, η=%d, quality=%.2f, kernelBound=%.0f}",
                    k, modulator.size(), eta, qualityScore, getKernelSizeBound());
        }
    }
}
