# RAG (pgvector)

RAG is **advisory**. Live DOM always outranks retrieved snippets. RAG never executes actions.

## Storage

Flyway `V6__create_rag_knowledge.sql`:

- Extension `vector`
- Table `smartqa_knowledge`
- Column `embedding vector(768)` (must match the embedding model)
- Scopes: `GLOBAL_GENERIC`, `APPLICATION`, `EXECUTION` (`KnowledgeScope`)
- HNSW index `idx_smartqa_knowledge_embedding_hnsw` using `vector_cosine_ops`

## Classes

| Class | Role |
|-------|------|
| `RagRetrievalService` | Embed query, search, apply threshold, return accepted/rejected |
| `RagKnowledgeRepository` | SQL cosine search |
| `RagIngestionService` | Insert sanitized documents |
| `RagFeedbackService` | success/failure counts |
| `RagSeedService` | Optional seed data |
| `OllamaEmbeddingProvider` | Default embeddings (`nomic-embed-text`) |
| `GeminiEmbeddingProvider` | Alternate (`text-embedding-004`) |
| `FallbackEmbeddingProvider` | Provider failover for embeddings |
| `KnowledgeSanitizer` | Strip secrets before persist/prompt |
| `RagStatusController` | `GET /api/internal/rag/stats` |

## Config (`smartqa.rag`)

| Key | Default |
|-----|---------|
| `enabled` | `true` (`SMARTQA_RAG_ENABLED`) |
| `embedding-provider` | `ollama` |
| `ollama-embedding-model` | `nomic-embed-text` |
| `gemini-embedding-model` | `text-embedding-004` |
| `embedding-dimension` | `768` |
| `top-k` | 5 |
| `relevance-threshold` | `0.55` |

If embeddings or pgvector are unavailable, retrieval returns empty and logs `RETRIEVAL_FAILED`. Pipeline preflight treats embedding health as **non-fatal** (`EMBEDDING_UNAVAILABLE` event).

## Scope isolation

Search passes `GLOBAL_GENERIC`, `APPLICATION`, and `EXECUTION` with an application key derived from the page URL. Weak cosine scores below 0.55 are rejected and not injected into AI prompts.

## Internal stats

`GET /api/internal/rag/stats` reports pgvector extension version, embedding provider/model/dimension, availability, and `knowledgeCount`. It is not a UI page.
