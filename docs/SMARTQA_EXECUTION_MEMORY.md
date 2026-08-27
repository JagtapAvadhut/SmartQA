# Execution memory

Class: `com.smartqa.browser.intelligence.memory.ExecutionMemoryService`.

## What it is

In-process `CopyOnWriteArrayList` of `ExecutionMemoryRecord`. Max entries from `smartqa.intelligence.memory-max-entries` (default **200**). Trimmed FIFO.

Scope: application host (from URL). Records include action, semantic target, role, contexts, locator type/hint, confidence, success flag, timestamp.

## What it is not

- Not a PostgreSQL table.
- Not shared across API processes.
- Not a second executor.
- Secrets in target/locator strings are **not** stored (`looksSensitive`).

## Policy

Live DOM outranks memory. Memory is an advisory hint for ranking/prompts, not ground truth.
