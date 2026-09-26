# Autonomous Database Optimizer

An AI-driven system that watches a PostgreSQL + pgvector database, diagnoses slow queries using RAG over indexing knowledge, and proposes (or applies, with human approval) index fixes autonomously. A React dashboard provides a visual interface for the approval workflow.

## Why this exists

Developers building AI apps on Postgres + pgvector hit slow vector/filtered searches as data grows, but most backend devs aren't DBAs — they don't know when to use IVFFlat vs HNSW, how to tune index params, or how to structure hybrid search indexes. This project builds that expertise into an autonomous agent.

## Result

On a 400,000-row test table, the system's own verification step measured a **99.97% reduction in query execution time** (85.487ms to 0.027ms) after applying its recommended composite index — a ~3,166x speedup. This number was captured automatically by the system's before/after `EXPLAIN ANALYZE` comparison, not benchmarked by hand.


https://github.com/user-attachments/assets/d87d71b2-fe9a-439c-81ad-b341a524291d


*Live reproduction via the [dashboard](https://github.com/IshaanTripathi03/autonomous-db-optimizer-frontend): after dropping the existing index to recreate the problem, the system independently rediscovered it and measured 43.6ms → 0.037ms (99.92% faster) after approval — confirming the result above is reproducible, not a one-off.*

## Architecture

Three physically separated concerns (see `/docs/ADR-001` for full reasoning):

1. **Target database** — the live Postgres being optimized. Only ever *read* from (via `pg_stat_statements`, `EXPLAIN ANALYZE`) and *written* to through one narrow, allowlisted path.
2. **Knowledge base database** — a separate Postgres + pgvector instance holding embedded indexing docs. Never touches target data.
3. **Application layer** (Spring Boot + Spring AI) — diagnosis engine, LLM decision layer, safety gate, and an autonomous scheduler.
4. **Frontend dashboard** (React + Vite) — a separate client that consumes the REST API to visualize the approval queue and trigger approve/reject actions. See `/docs/ADR-002` for the frontend decision.

Default mode is **propose, don't auto-execute**. The LLM can only ever call vetted functions like `createIndexConcurrently(...)` — never freeform SQL. Every identifier the LLM returns is validated against `information_schema` before any DDL is built.

## How it works end to end

1. **Watch** — a scheduled job (`AutonomousMonitorService`) periodically queries `pg_stat_statements` on the target database, filtered to exclude DDL and system-catalog noise.
2. **Diagnose** — the top slow query is joined with table/index metadata (`pg_class`, `pg_indexes`) to build a full picture of the problem.
3. **Retrieve + Reason** — the diagnosis is used to do a similarity search against the knowledge base's pgvector store, and the retrieved indexing guidance plus the diagnosis are sent to Gemini, which returns a structured recommendation (table, columns, index type, justification) — never raw SQL.
4. **Propose** — the recommendation is queued as a `PENDING` approval. Nothing executes yet.
5. **Approve** — a human, via the dashboard or the API directly, approves or rejects the recommendation. Approving is the only path that can trigger execution.
6. **Execute safely** — approved recommendations are validated again (table/column existence, allowlisted index types) and executed via `CREATE INDEX CONCURRENTLY`.
7. **Verify** — the system automatically re-runs `EXPLAIN ANALYZE` on a representative query before and after the index exists, logging the real timing improvement.

The autonomous scheduler includes safeguards against runaway API usage: it skips re-analyzing a table that already has an active (pending or applied) recommendation, and enters a cooldown period after any LLM call failure instead of retrying every cycle.

## Progress

- [x] Two isolated `DataSource`/`JdbcClient` beans (target DB, knowledge DB)
- [x] Stats collector — reads `pg_stat_statements` on the target DB, filtered to real application queries
- [x] Table diagnostics — joins slow queries with `pg_class`/`pg_indexes` to detect missing indexes on hot tables
- [x] `GET /api/diagnosis/slow-queries` — returns slow queries enriched with table row counts + existing indexes
- [x] RAG ingestion job — embeds a curated indexing-knowledge corpus via Gemini and stores it in the knowledge base's pgvector store
- [x] RAG retrieval + LLM reasoning — turns a diagnosed slow query into a structured, justified index recommendation
- [x] Allowlisted function registry — `createIndexConcurrently`, validated against `information_schema` before execution
- [x] Safety gate + approval workflow — propose/approve/reject via API, no auto-execute by default
- [x] Verification step — automatic before/after `EXPLAIN ANALYZE`, real measured timing improvement
- [x] Autonomous scheduler — watches and diagnoses on an interval with no manual trigger required
- [x] CORS configuration for local frontend development
- [x] React dashboard — approval queue view with live approve/reject actions against the real API
- [ ] Persistent audit trail (currently in-memory; a Postgres-backed audit table is a natural next step)

## API

| Endpoint | Method | Description |
|---|---|---|
| `/api/diagnosis/slow-queries` | GET | Raw diagnosis output for the top N slow queries |
| `/api/knowledge/files` | GET | List available knowledge base documents |
| `/api/knowledge/ingest` | POST | Embed and store all knowledge base documents |
| `/api/recommendation` | GET | Diagnose the top slow query and queue a recommendation for approval |
| `/api/approvals` | GET | List all queued recommendations and their status |
| `/api/approvals/{id}/approve` | POST | Approve a recommendation — validates, executes DDL, runs verification |
| `/api/approvals/{id}/reject` | POST | Reject a recommendation without executing it |

## Running locally

### Backend

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

The autonomous monitor runs every 5 minutes by default. To override for testing:

```bash
./mvnw spring-boot:run "-Dspring-boot.run.arguments=--app.monitor.interval-ms=60000"
```

Diagnose slow queries manually:
```bash
curl http://localhost:8080/api/recommendation
```

Ingest the knowledge base into the RAG vector store (one-time, or after editing `knowledge-base/docs/`):
```bash
curl -X POST http://localhost:8080/api/knowledge/ingest
```

### Frontend

From the `frontend/` directory (separate project, sibling to `demo/`):

```bash
npm install
npm run dev
```

Open `http://localhost:5173`. The dashboard fetches from `http://localhost:8080` directly, so the backend must be running first. CORS is scoped to `localhost:5173` only (see `CorsConfig.java`).

## Knowledge base

`knowledge-base/docs/` holds the indexing-expertise corpus that powers RAG-based recommendations: composite index column order, IVFFlat vs HNSW trade-offs, partial indexes, B-tree vs GIN, and when not to index at all. Each doc is embedded via Gemini and stored in the knowledge DB's `vector_store` table, tagged with its source filename for traceability.

## Known limitations

- Approval state is currently in-memory and does not survive an application restart. A Postgres-backed audit table is the natural next step.
- The current demo primarily exercises relational (equality) filtering on a seeded table; the knowledge base's pgvector-specific guidance (IVFFlat vs HNSW) has not yet been exercised end-to-end against a live vector similarity query.
- Gemini's free tier enforces a daily request quota (20 requests/day/model). The scheduler's dedup-before-call guard (`hasActiveApprovalForTable`) prevents runaway usage on the autonomous path; the manual `GET /api/recommendation` endpoint does not yet have the same guard, so repeated manual calls can exhaust the quota faster than the scheduler would.
- No authentication on any endpoint (local dev only, not intended to be internet-facing as-is).

## Tech stack

**Backend:** Spring Boot 4.1, Java 26, Spring AI 2.0, Google Gemini (chat + embeddings), PostgreSQL 16 + pgvector, Docker Compose
**Frontend:** React 19, Vite
