# Autonomous Database Optimizer

An AI-driven system that watches a PostgreSQL + pgvector database, diagnoses slow queries using RAG over indexing knowledge, and proposes (or applies, with approval) index fixes autonomously.

## Why this exists

Developers building AI apps on Postgres + pgvector hit slow vector/filtered searches as data grows, but most backend devs aren't DBAs — they don't know when to use IVFFlat vs HNSW, how to tune index params, or how to structure hybrid search indexes. This project builds that expertise into an autonomous agent.

## Architecture

Three physically separated concerns (see `/docs/ADR-001` for full reasoning):

1. **Target database** — the live Postgres being optimized. Only ever *read* from (via `pg_stat_statements`, `EXPLAIN ANALYZE`) and *written* to through one narrow, allowlisted path.
2. **Knowledge base database** — a separate Postgres + pgvector instance holding embedded indexing docs. Never touches target data.
3. **Application layer** (Spring Boot + Spring AI) — diagnosis engine, LLM decision layer, and a safety gate that only allows pre-registered, parameterized functions to execute — never raw LLM-generated SQL.

Default mode is **propose, don't auto-execute**. The LLM can only ever call vetted functions like `createIndexConcurrently(...)` — never freeform SQL.

## Progress

- [x] Two isolated `DataSource`/`JdbcClient` beans (target DB, knowledge DB)
- [x] Stats collector — reads `pg_stat_statements` on the target DB
- [x] Table diagnostics — joins slow queries with `pg_class`/`pg_indexes` to detect missing indexes on hot tables
- [x] `GET /api/diagnosis/slow-queries` — returns slow queries enriched with table row counts + existing indexes
- [x] RAG ingestion job — embeds a curated indexing-knowledge corpus via Gemini and stores it in the knowledge base's pgvector store
- [x] `GET /api/knowledge/files` — lists available knowledge base documents
- [x] `POST /api/knowledge/ingest` — embeds and stores all knowledge base documents
- [ ] RAG retrieval + LLM reasoning — turn a diagnosed slow query into a justified index recommendation
- [ ] Allowlisted function registry (`createIndexConcurrently`)
- [ ] Safety gate + approval workflow
- [ ] Verification step (re-run `EXPLAIN ANALYZE` post-fix, audit log)

## Running locally

Set your Gemini API key (get one free at [aistudio.google.com/apikey](https://aistudio.google.com/apikey)):

```bash
export GEMINI_API_KEY="your_key_here"   # macOS/Linux
$env:GEMINI_API_KEY="your_key_here"     # Windows PowerShell
```

Start the databases and app:

```bash
docker compose up -d
./mvnw spring-boot:run
```

Diagnose slow queries on the target DB:
```bash
curl http://localhost:8080/api/diagnosis/slow-queries
```

Ingest the knowledge base into the RAG vector store (one-time, or after editing `knowledge-base/docs/`):
```bash
curl -X POST http://localhost:8080/api/knowledge/ingest
```

## Knowledge base

`knowledge-base/docs/` holds the indexing-expertise corpus that powers RAG-based recommendations: composite index column order, IVFFlat vs HNSW trade-offs, partial indexes, B-tree vs GIN, and when not to index at all. Each doc is embedded via Gemini and stored in the knowledge DB's `vector_store` table, tagged with its source filename for traceability.

## Tech stack

Spring Boot 4.1, Java 26, Spring AI 2.0, Google Gemini (chat + embeddings), PostgreSQL 16 + pgvector, Docker Compose