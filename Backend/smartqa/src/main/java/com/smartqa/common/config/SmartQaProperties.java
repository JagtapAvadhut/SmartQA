package com.smartqa.common.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "smartqa")
public class SmartQaProperties {

    private final Cors cors = new Cors();
    private final Execution execution = new Execution();
    private final Screenshots screenshots = new Screenshots();
    private final Ai ai = new Ai();
    private final Browser browser = new Browser();
    private final Mcp mcp = new Mcp();
    private final Debug debug = new Debug();
    private final Rag rag = new Rag();
    private final Intelligence intelligence = new Intelligence();
    private final Recovery recovery = new Recovery();

    public Cors getCors() {
        return cors;
    }

    public Execution getExecution() {
        return execution;
    }

    public Screenshots getScreenshots() {
        return screenshots;
    }

    public Ai getAi() {
        return ai;
    }

    public Browser getBrowser() {
        return browser;
    }

    public Mcp getMcp() {
        return mcp;
    }

    public Debug getDebug() {
        return debug;
    }

    public Rag getRag() {
        return rag;
    }

    public Intelligence getIntelligence() {
        return intelligence;
    }

    public Recovery getRecovery() {
        return recovery;
    }

    public static class Cors {
        private String allowedOrigins = "http://localhost:5173";

        public String getAllowedOrigins() {
            return allowedOrigins;
        }

        public void setAllowedOrigins(String allowedOrigins) {
            this.allowedOrigins = allowedOrigins;
        }
    }

    public static class Execution {
        private int timeoutSeconds = 180;
        /** Isolated generated-test validator budget. Labeled separately from generation/final execution. */
        private int validatorTimeoutSeconds = 180;
        private String screenshotMode = "IMPORTANT";
        private boolean stepByStep = false;

        public int getTimeoutSeconds() {
            return timeoutSeconds;
        }

        public void setTimeoutSeconds(int timeoutSeconds) {
            this.timeoutSeconds = timeoutSeconds;
        }

        public int getValidatorTimeoutSeconds() {
            return validatorTimeoutSeconds > 0 ? validatorTimeoutSeconds : timeoutSeconds;
        }

        public void setValidatorTimeoutSeconds(int validatorTimeoutSeconds) {
            this.validatorTimeoutSeconds = validatorTimeoutSeconds;
        }

        public String getScreenshotMode() {
            return screenshotMode;
        }

        public void setScreenshotMode(String screenshotMode) {
            this.screenshotMode = screenshotMode;
        }

        public boolean isStepByStep() {
            return stepByStep;
        }

        public void setStepByStep(boolean stepByStep) {
            this.stepByStep = stepByStep;
        }
    }

    public static class Screenshots {
        private String baseDir = "./screenshots";

        public String getBaseDir() {
            return baseDir;
        }

        public void setBaseDir(String baseDir) {
            this.baseDir = baseDir;
        }
    }

    public static class Ai {
        private String provider = "gemini";
        private String primaryProvider = "";
        private String fallbackProvider = "ollama";
        private int timeoutSeconds = 60;
        /** Intent understanding budget. Must stay below execution timeout. */
        private int intentTimeoutSeconds = 45;
        private int connectTimeoutSeconds = 10;
        private int maxRetries = 1;
        /** When true, important failures also request an Ollama/Gemini second opinion. */
        private boolean consensusEnabled = true;
        /** Below this confidence, primary diagnosis requests a second opinion. */
        private double consensusLowConfidence = 0.65;
        private final Ollama ollama = new Ollama();
        private final Gemini gemini = new Gemini();
        private final OpenAiCompatible openaiCompatible = new OpenAiCompatible();

        public String getProvider() {
            return provider;
        }

        public void setProvider(String provider) {
            this.provider = provider;
        }

        public String getPrimaryProvider() {
            if (primaryProvider != null && !primaryProvider.isBlank()) {
                return primaryProvider;
            }
            return provider == null || provider.isBlank() ? "gemini" : provider;
        }

        public void setPrimaryProvider(String primaryProvider) {
            this.primaryProvider = primaryProvider;
        }

        public String getFallbackProvider() {
            return fallbackProvider;
        }

        public void setFallbackProvider(String fallbackProvider) {
            this.fallbackProvider = fallbackProvider;
        }

        public int getTimeoutSeconds() {
            return timeoutSeconds;
        }

        public void setTimeoutSeconds(int timeoutSeconds) {
            this.timeoutSeconds = timeoutSeconds;
        }

        public int getIntentTimeoutSeconds() {
            return intentTimeoutSeconds;
        }

        public void setIntentTimeoutSeconds(int intentTimeoutSeconds) {
            this.intentTimeoutSeconds = intentTimeoutSeconds;
        }

        public int getConnectTimeoutSeconds() {
            return connectTimeoutSeconds;
        }

        public void setConnectTimeoutSeconds(int connectTimeoutSeconds) {
            this.connectTimeoutSeconds = connectTimeoutSeconds;
        }

        public int getMaxRetries() {
            return maxRetries;
        }

        public void setMaxRetries(int maxRetries) {
            this.maxRetries = maxRetries;
        }

        public boolean isConsensusEnabled() {
            return consensusEnabled;
        }

        public void setConsensusEnabled(boolean consensusEnabled) {
            this.consensusEnabled = consensusEnabled;
        }

        public double getConsensusLowConfidence() {
            return consensusLowConfidence;
        }

        public void setConsensusLowConfidence(double consensusLowConfidence) {
            this.consensusLowConfidence = consensusLowConfidence;
        }

        public Ollama getOllama() {
            return ollama;
        }

        public Gemini getGemini() {
            return gemini;
        }

        public OpenAiCompatible getOpenaiCompatible() {
            return openaiCompatible;
        }
    }

    public static class Ollama {
        private String baseUrl = "http://localhost:11434";
        private String model = "qwen2.5-coder:3b";

        public String getBaseUrl() {
            return baseUrl;
        }

        public void setBaseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
        }

        public String getModel() {
            return model;
        }

        public void setModel(String model) {
            this.model = model;
        }
    }

    public static class Gemini {
        private String baseUrl = "https://generativelanguage.googleapis.com";
        private String apiKey = "";
        /** Comma-separated failover keys from GEMINI_API_KEYS (never commit real values). */
        private String apiKeys = "";
        private String model = "gemini-flash-latest";
        private final Rotation rotation = new Rotation();

        public String getBaseUrl() {
            return baseUrl;
        }

        public void setBaseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
        }

        public String getApiKey() {
            return apiKey;
        }

        public void setApiKey(String apiKey) {
            this.apiKey = apiKey;
        }

        public String getApiKeys() {
            return apiKeys;
        }

        public void setApiKeys(String apiKeys) {
            this.apiKeys = apiKeys;
        }

        /**
         * Ordered unique key pool: GEMINI_API_KEY first, then GEMINI_API_KEYS entries,
         * then GEMINI_API_KEY_2..8. Duplicates are dropped. Never log these values.
         */
        public java.util.List<String> resolvedApiKeys() {
            java.util.List<String> numbered = new java.util.ArrayList<>();
            for (int i = 2; i <= 8; i++) {
                numbered.add(envOrProperty("GEMINI_API_KEY_" + i));
            }
            return mergeApiKeys(apiKey, apiKeys, numbered);
        }

        /**
         * Testable merge: primary key, comma/whitespace-separated extras, numbered leftovers.
         */
        public static java.util.List<String> mergeApiKeys(String primaryKey, String extraKeysCsv, Iterable<String> numberedKeys) {
            java.util.LinkedHashSet<String> keys = new java.util.LinkedHashSet<>();
            addKey(keys, primaryKey);
            if (extraKeysCsv != null && !extraKeysCsv.isBlank()) {
                for (String part : extraKeysCsv.split("[,;\\s]+")) {
                    addKey(keys, part);
                }
            }
            if (numberedKeys != null) {
                for (String numbered : numberedKeys) {
                    addKey(keys, numbered);
                }
            }
            return java.util.List.copyOf(keys);
        }

        private static String envOrProperty(String name) {
            String env = System.getenv(name);
            if (env != null && !env.isBlank()) {
                return env;
            }
            return System.getProperty(name);
        }

        private static void addKey(java.util.Set<String> keys, String value) {
            if (value == null) {
                return;
            }
            String trimmed = value.trim();
            if (!trimmed.isBlank()) {
                keys.add(trimmed);
            }
        }

        public String getModel() {
            return model;
        }

        public void setModel(String model) {
            this.model = model;
        }

        public Rotation getRotation() {
            return rotation;
        }

        public static class Rotation {
            private boolean enabled = true;
            private int cooldownSeconds = 60;
            private int transientCooldownSeconds = 15;
            private int invalidKeyCooldownSeconds = 300;
            private String maxKeyAttempts = "all";
            private int retryPerKey = 1;

            public boolean isEnabled() {
                return enabled;
            }

            public void setEnabled(boolean enabled) {
                this.enabled = enabled;
            }

            public int getCooldownSeconds() {
                return cooldownSeconds;
            }

            public void setCooldownSeconds(int cooldownSeconds) {
                this.cooldownSeconds = cooldownSeconds;
            }

            public int getTransientCooldownSeconds() {
                return transientCooldownSeconds;
            }

            public void setTransientCooldownSeconds(int transientCooldownSeconds) {
                this.transientCooldownSeconds = transientCooldownSeconds;
            }

            public int getInvalidKeyCooldownSeconds() {
                return invalidKeyCooldownSeconds;
            }

            public void setInvalidKeyCooldownSeconds(int invalidKeyCooldownSeconds) {
                this.invalidKeyCooldownSeconds = invalidKeyCooldownSeconds;
            }

            public String getMaxKeyAttempts() {
                return maxKeyAttempts;
            }

            public void setMaxKeyAttempts(String maxKeyAttempts) {
                this.maxKeyAttempts = maxKeyAttempts;
            }

            public int getRetryPerKey() {
                return retryPerKey;
            }

            public void setRetryPerKey(int retryPerKey) {
                this.retryPerKey = retryPerKey;
            }
        }
    }

    public static class OpenAiCompatible {
        private String baseUrl = "";
        private String apiKey = "";
        private String model = "";

        public String getBaseUrl() {
            return baseUrl;
        }

        public void setBaseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
        }

        public String getApiKey() {
            return apiKey;
        }

        public void setApiKey(String apiKey) {
            this.apiKey = apiKey;
        }

        public String getModel() {
            return model;
        }

        public void setModel(String model) {
            this.model = model;
        }
    }

    public static class Browser {
        private String provider = "playwright";
        private String type = "chromium";
        private boolean headless = false;
        private boolean maximizeHeaded = true;
        /** Chromium page zoom percent for headed execution (Chrome ⋮ → Zoom). Default 50. Not window size. */
        private int zoomPercent = 50;
        private int headlessViewportWidth = 1280;
        private int headlessViewportHeight = 720;

        public String getProvider() {
            return provider;
        }

        public void setProvider(String provider) {
            this.provider = provider;
        }

        public String getType() {
            return type;
        }

        public void setType(String type) {
            this.type = type;
        }

        public boolean isHeadless() {
            return headless;
        }

        public void setHeadless(boolean headless) {
            this.headless = headless;
        }

        public boolean isMaximizeHeaded() {
            return maximizeHeaded;
        }

        public void setMaximizeHeaded(boolean maximizeHeaded) {
            this.maximizeHeaded = maximizeHeaded;
        }

        public int getZoomPercent() {
            return zoomPercent;
        }

        public void setZoomPercent(int zoomPercent) {
            this.zoomPercent = zoomPercent;
        }

        public int getHeadlessViewportWidth() {
            return headlessViewportWidth;
        }

        public void setHeadlessViewportWidth(int headlessViewportWidth) {
            this.headlessViewportWidth = headlessViewportWidth;
        }

        public int getHeadlessViewportHeight() {
            return headlessViewportHeight;
        }

        public void setHeadlessViewportHeight(int headlessViewportHeight) {
            this.headlessViewportHeight = headlessViewportHeight;
        }
    }

    public static class Mcp {
        private boolean enabled = false;
        private String url = "http://127.0.0.1:8931";

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getUrl() {
            return url;
        }

        public void setUrl(String url) {
            this.url = url;
        }
    }

    public static class Debug {
        private String logDir = "./logs/smartqa";
        private int maxFiles = 100;
        private int maxAgeDays = 7;

        public String getLogDir() {
            return logDir;
        }

        public void setLogDir(String logDir) {
            this.logDir = logDir;
        }

        public int getMaxFiles() {
            return maxFiles;
        }

        public void setMaxFiles(int maxFiles) {
            this.maxFiles = maxFiles;
        }

        public int getMaxAgeDays() {
            return maxAgeDays;
        }

        public void setMaxAgeDays(int maxAgeDays) {
            this.maxAgeDays = maxAgeDays;
        }
    }

    /**
     * Vector RAG (PostgreSQL + pgvector). Embedding dimension must match the model.
     * Default: Ollama nomic-embed-text → 768-d, cosine distance, HNSW index.
     */
    public static class Rag {
        private boolean enabled = true;
        /** ollama | gemini */
        private String embeddingProvider = "ollama";
        private String ollamaEmbeddingModel = "nomic-embed-text";
        private String geminiEmbeddingModel = "text-embedding-004";
        /** Must match embedding model output (nomic-embed-text = 768). */
        private int embeddingDimension = 768;
        private int topK = 5;
        /** Cosine similarity gate; weak matches are not injected into AI context. */
        private double relevanceThreshold = 0.55;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getEmbeddingProvider() {
            return embeddingProvider;
        }

        public void setEmbeddingProvider(String embeddingProvider) {
            this.embeddingProvider = embeddingProvider;
        }

        public String getOllamaEmbeddingModel() {
            return ollamaEmbeddingModel;
        }

        public void setOllamaEmbeddingModel(String ollamaEmbeddingModel) {
            this.ollamaEmbeddingModel = ollamaEmbeddingModel;
        }

        public String getGeminiEmbeddingModel() {
            return geminiEmbeddingModel;
        }

        public void setGeminiEmbeddingModel(String geminiEmbeddingModel) {
            this.geminiEmbeddingModel = geminiEmbeddingModel;
        }

        public int getEmbeddingDimension() {
            return embeddingDimension;
        }

        public void setEmbeddingDimension(int embeddingDimension) {
            this.embeddingDimension = embeddingDimension;
        }

        public int getTopK() {
            return topK;
        }

        public void setTopK(int topK) {
            this.topK = topK;
        }

        public double getRelevanceThreshold() {
            return relevanceThreshold;
        }

        public void setRelevanceThreshold(double relevanceThreshold) {
            this.relevanceThreshold = relevanceThreshold;
        }
    }

    /**
     * Browser intelligence telemetry. CDP snapshot is Chromium evidence, not execution.
     */
    public static class Intelligence {
        private boolean cdpEnabled = true;
        private int cdpTimeoutMs = 4000;
        /** OFF | ESCALATE | ALWAYS */
        private String cdpSnapshotMode = "ESCALATE";
        private boolean networkMonitoring = true;
        private int networkRetention = 80;
        private double aiEscalationThreshold = 0.70;
        private int memoryMaxEntries = 200;

        public boolean isCdpEnabled() {
            return cdpEnabled;
        }

        public void setCdpEnabled(boolean cdpEnabled) {
            this.cdpEnabled = cdpEnabled;
        }

        public int getCdpTimeoutMs() {
            return cdpTimeoutMs;
        }

        public void setCdpTimeoutMs(int cdpTimeoutMs) {
            this.cdpTimeoutMs = cdpTimeoutMs;
        }

        public String getCdpSnapshotMode() {
            return cdpSnapshotMode;
        }

        public void setCdpSnapshotMode(String cdpSnapshotMode) {
            this.cdpSnapshotMode = cdpSnapshotMode;
        }

        public boolean isNetworkMonitoring() {
            return networkMonitoring;
        }

        public void setNetworkMonitoring(boolean networkMonitoring) {
            this.networkMonitoring = networkMonitoring;
        }

        public int getNetworkRetention() {
            return networkRetention;
        }

        public void setNetworkRetention(int networkRetention) {
            this.networkRetention = networkRetention;
        }

        public double getAiEscalationThreshold() {
            return aiEscalationThreshold;
        }

        public void setAiEscalationThreshold(double aiEscalationThreshold) {
            this.aiEscalationThreshold = aiEscalationThreshold;
        }

        public int getMemoryMaxEntries() {
            return memoryMaxEntries;
        }

        public void setMemoryMaxEntries(int memoryMaxEntries) {
            this.memoryMaxEntries = memoryMaxEntries;
        }

        public boolean captureCdpOnInspect() {
            return "ALWAYS".equalsIgnoreCase(cdpSnapshotMode);
        }

        public boolean captureCdpOnEscalate() {
            return !"OFF".equalsIgnoreCase(cdpSnapshotMode);
        }
    }

    public static class Recovery {
        private int maxBacktrackSteps = 3;
        private int maxRetries = 3;
        private int maxReplans = 2;
        private int maxBacktracks = 1;
        private int maxSameStateRetries = 2;

        public int getMaxBacktrackSteps() {
            return maxBacktrackSteps;
        }

        public void setMaxBacktrackSteps(int maxBacktrackSteps) {
            this.maxBacktrackSteps = Math.max(1, maxBacktrackSteps);
        }

        public int getMaxRetries() {
            return maxRetries;
        }

        public void setMaxRetries(int maxRetries) {
            this.maxRetries = Math.max(1, maxRetries);
        }

        public int getMaxReplans() {
            return maxReplans;
        }

        public void setMaxReplans(int maxReplans) {
            this.maxReplans = Math.max(0, maxReplans);
        }

        public int getMaxBacktracks() {
            return maxBacktracks;
        }

        public void setMaxBacktracks(int maxBacktracks) {
            this.maxBacktracks = Math.max(0, maxBacktracks);
        }

        public int getMaxSameStateRetries() {
            return maxSameStateRetries;
        }

        public void setMaxSameStateRetries(int maxSameStateRetries) {
            this.maxSameStateRetries = Math.max(1, maxSameStateRetries);
        }
    }
}
