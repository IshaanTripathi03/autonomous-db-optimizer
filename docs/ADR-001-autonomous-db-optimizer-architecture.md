# ADR-001: Autonomous Database Optimizer — Core Architecture

**Status:** Accepted
**Date:** 2026-09-19
**Deciders:** Ishaan (solo project)

## Context

Developers building AI apps on PostgreSQL + pgvector hit slow vector searches as data grows past a few hundred thousand rows, and most backend devs aren't DBAs — they don't know when to use IVFFlat vs HNSW, how to tune `m`/`ef_construction`, or how to structure hybrid search indexes (vector + filter columns like `user_id`, `status`).

This project builds a Spring AI application that watches a target Postgres instance, diagnoses slow queries using RAG over indexing knowledge, and proposes or applies fixes autonomously.

The first draft of the architecture stored the RAG knowledge base inside the same Postgres instance being optimized, and let the LLM's function call go straight to DDL execution with no review step. Both of those are disqualifying for anything meant to run against a real (or even a portfolio-credible) production database.

## Decision

Build the system as three physically separated concerns:

1. **Target database** — the customer's/demo's live Postgres. The optimizer only ever *reads* from it (via `pg_stat_statements`, `EXPLAIN ANALYZE`) and *writes* to it through one narrow, allowlisted path (`CREATE INDEX CONCURRENTLY` and equivalent safe DDL).
2. **Knowledge base database** — a separate Postgres + pgvector instance the app owns, holding embedded docs/optimization guides. Never touches the target schema or data.
3. **Application layer** (Spring Boot + Spring AI) — contains a diagnosis engine (stats collector + `EXPLAIN ANALYZE` + RAG lookup against the knowledge DB), a call out to Gemini for the decision, and a **safety gate** that only lets pre-registered, parameterized functions execute — never raw LLM-generated SQL strings.

Default execution mode is **propose, don't auto-execute**: the safety gate surfaces the recommended fix to the dashboard/Slack for approval. Auto-execute is an opt-in setting, not the default.

## Options Considered

### Option A: Single shared Postgres instance for target data + RAG knowledge base
| Dimension | Assessment |
|-----------|------------|
| Complexity | Low — one datasource, one connection pool |
| Cost | Lower — one instance to run |
| Scalability | Poor — RAG writes/reads compete with the workload you're trying to speed up |
| Team familiarity | N/A (solo project) |

**Pros:** Simpler local setup, one `application.yml` datasource block.
**Cons:** Can't run against a real customer DB you don't own the schema for. Couples your app's internal state to the thing you're optimizing — a design a reviewer will flag immediately.

### Option B: Separate target DB and knowledge base DB (chosen)
| Dimension | Assessment |
|-----------|------------|
| Complexity | Medium — two datasources, need to keep transaction boundaries separate |
| Cost | Slightly higher — two Postgres containers, both free to run locally |
| Scalability | Good — knowledge base growth/query load never touches the target DB |
| Team familiarity | N/A |

**Pros:** Matches how a real DBA tool would be deployed against a customer's DB. Demonstrates you understand blast radius and separation of concerns — this is the detail that reads as "systems thinking" to a recruiter.
**Cons:** More setup (docker-compose with two Postgres services, two datasource configs in Spring).

### Option C: LLM-driven direct DDL execution (no safety gate)
| Dimension | Assessment |
|-----------|------------|
| Complexity | Low — function call straight to `JdbcClient.execute()` |
| Cost | N/A |
| Scalability | N/A |
| Team familiarity | N/A |

**Pros:** Fastest to demo — "watch the AI fix your DB live."
**Cons:** Unreviewed DDL on a live database from an LLM decision is a real safety failure mode (bad index choice, hallucinated column, or a prompt-injected slow-query comment steering the model). This is the single fastest way to make an interviewer trust the project *less*, not more.

### Option D: Safety gate — allowlisted functions + approval workflow (chosen)
| Dimension | Assessment |
|-----------|------------|
| Complexity | Medium — need a function registry and an approval state machine |
| Cost | N/A |
| Scalability | Fine at this project's scale |
| Team familiarity | N/A |

**Pros:** The LLM can only ever call `createIndexConcurrently(table, columns, indexType, params)` — never freeform SQL. Approval-by-default is the honest, defensible story. Auto-execute becomes an explicit, documented trust decision later.
**Cons:** More upfront engineering than "just run the SQL," and the demo needs one extra click (approve) to look impressive live — solvable by pre-seeding a demo scenario.

## Trade-off Analysis

The core trade-off is **demo speed vs credibility**. Option A + C together would make for a flashier 60-second demo video ("AI watches DB, AI fixes DB, done"). Option B + D takes longer to build but is the version that survives a technical interviewer asking "what stops this from dropping a table by accident?" — and that question *will* come up, because it's the obvious one. Given the goal is recruiting signal for SDE roles, not YouTube virality, B + D is the right call. If you want the flashy demo later, you can add a "trusted mode" toggle on top of the safety gate without re-architecting anything.

## Consequences

- **Easier:** Testing the diagnosis engine in isolation (mock the target DB, real knowledge DB). Clear audit trail — every applied fix has a corresponding approval record.
- **Harder:** Local dev setup needs docker-compose with two Postgres containers instead of one. Slightly more Spring config (two `DataSource` beans, careful about which one `@Transactional` methods bind to — remember `CREATE INDEX CONCURRENTLY` can't run inside a transaction at all).
- **Revisit later:** Move the allowlist from hardcoded Java functions to a config-driven policy (JSON/YAML rules) once you have more than 2-3 fix types. Consider a proper approval UI state machine (pending → approved → applied → verified) once the dashboard exists.

## Action Items

1. [ ] Scaffold Spring Boot project with two `DataSource` beans (target, knowledge base)
2. [ ] Build the stats collector function (`pg_stat_statements`, `pg_class.reltuples`)
3. [ ] Build the RAG ingestion job (embeddings generator → knowledge base vector store)
4. [ ] Define the allowlisted function registry (start with just `createIndexConcurrently`)
5. [ ] Build the safety gate as "propose only" — dashboard approval flow before auto-execute exists
6. [ ] Add verification step: re-run `EXPLAIN ANALYZE` after a fix, log before/after into audit table