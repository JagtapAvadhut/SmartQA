package com.smartqa.rag;

import java.util.List;
import java.util.UUID;

public record RagRetrievalRequest(
        String query,
        String applicationKey,
        String executionKey,
        String failureCategory,
        String contentTypeHint,
        int topK
) {
    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private String query;
        private String applicationKey;
        private String executionKey;
        private String failureCategory;
        private String contentTypeHint;
        private int topK = 5;

        public Builder query(String query) { this.query = query; return this; }
        public Builder applicationKey(String applicationKey) { this.applicationKey = applicationKey; return this; }
        public Builder executionKey(String executionKey) { this.executionKey = executionKey; return this; }
        public Builder failureCategory(String failureCategory) { this.failureCategory = failureCategory; return this; }
        public Builder contentTypeHint(String contentTypeHint) { this.contentTypeHint = contentTypeHint; return this; }
        public Builder topK(int topK) { this.topK = topK; return this; }

        public RagRetrievalRequest build() {
            return new RagRetrievalRequest(query, applicationKey, executionKey, failureCategory, contentTypeHint, topK);
        }
    }
}
