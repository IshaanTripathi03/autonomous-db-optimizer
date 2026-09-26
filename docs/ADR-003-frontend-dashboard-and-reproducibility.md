# ADR-003: Frontend Dashboard Implementation, CORS Scope, and Repository Structure

**Status:** Accepted
**Date:** 2026-09-26
**Deciders:** Ishaan (solo project)

## Context

ADR-002 proposed a React SPA dashboard but left three things undecided at design time: the exact CORS policy, whether the frontend would live in the same repository as the backend, and whether the system's headline result (99.97% speedup) was a one-off or something the agent could rediscover independently. This session built the dashboard and answered all three.

## Decision

### Part 1: CORS — scoped, not wildcard

Added a single `CorsConfig` bean restricting `/api/**` to `allowedOrigins("http://localhost:5173")` with only `GET`/`POST` methods. Rejected `@CrossOrigin("*")` outright — with no authentication on any endpoint, a wildcard origin would let any page in any browser tab call the approval/execution endpoints. Scoping to the exact dev origin costs nothing and closes that door.

### Part 2: Two separate repositories, not a monorepo

`autonomous-db-optimizer` (backend) and `autonomous-db-optimizer-frontend` (dashboard) are kept as independent GitHub repos rather than folders in one repo. This matches ADR-002's own reasoning for choosing a separate frontend in the first place — decoupling presentation from the safety-critical backend — and avoids restructuring the backend's existing repo root, which would break every documented command path for no functional benefit.

### Part 3: Reproducibility test — proving the result generalizes

The README's headline number (85.487ms → 0.027ms, 99.97%) came from one earlier run. To confirm the system wasn't just replaying a lucky first result, the existing composite index was manually dropped, `pg_stat_statements` reset, and load regenerated on the same query shape. The autonomous monitor independently rediscovered the missing index, and Gemini re-derived the same reasoning (high-cardinality column first) without being told the prior answer. Result: 43.617ms → 0.037ms (99.92% faster) — same order-of-magnitude improvement, same correct architectural reasoning, arrived at from scratch.

## Options Considered

### Option A: `@CrossOrigin("*")` on controllers (rejected)
**Pros:** Zero config, works immediately from any origin.
**Cons:** With no auth layer, opens every write endpoint (`/approve`, `/reject`) to any page in any browser tab. Directly contradicts the project's stated safety posture from ADR-001.

### Option B: Scoped `WebMvcConfigurer` CORS bean, single origin (chosen)
**Pros:** Costs one file, one bean. Explicit about what's allowed rather than solving a problem that doesn't exist yet.
**Cons:** Needs updating if the frontend's dev port or a future deployed origin changes — an acceptable, explicit trade-off over a silent wildcard.

### Option C: Monorepo — frontend as a `demo/frontend` subfolder (rejected)
**Pros:** One repo to manage, one clone for a recruiter to review everything.
**Cons:** Backend repo root is already the Maven project root; nesting it would break every existing documented command. No functional gain to offset that churn.

### Option D: Two independent repos (chosen)
**Pros:** Zero disruption to working backend paths. Matches ADR-002's decoupling rationale. Standard for a recruiter to see two clearly-scoped, independently-buildable projects.
**Cons:** A recruiter has to open two repo links instead of one — mitigated by cross-linking both READMEs directly to each other.

## Trade-off Analysis

The central trade-off this session was speed of demo-readiness vs. completeness. The CORS and repo-structure decisions were low-risk and resolved quickly. The reproducibility test was the highest-value use of the remaining time: it converts the project's headline claim from "it worked once" into "it rediscovers the same class of problem from scratch," which is a materially stronger thing to say in an interview.

## Consequences

- **Easier:** The dashboard is now the fastest way to demo the project — no more manually sequencing `curl` calls to show the loop working.
- **Harder:** Two repos means two READMEs to keep in sync when either side changes; addressed for now by cross-linking, not automation.
- **Known, deliberately deferred:** the manual `GET /api/recommendation` endpoint doesn't yet have the same Gemini-call dedup guard the scheduler has (per ADR-002 Part 2), so repeated manual/dashboard-triggered calls can consume quota faster than the scheduler alone would. Approval state also remains in-memory only, carried over from ADR-001/002. Neither blocked this session's goal of a working, demoable dashboard.

## Action Items

1. [x] Add scoped `CorsConfig` bean
2. [x] Build React dashboard: approval queue view with approve/reject actions
3. [x] Create separate frontend GitHub repository
4. [x] Cross-link both READMEs with embedded demo screenshot
5. [x] Reproduce the headline result independently (drop index, regenerate load, confirm rediscovery)
