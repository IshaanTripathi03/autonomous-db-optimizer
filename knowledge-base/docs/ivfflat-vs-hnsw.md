# IVFFlat vs HNSW (pgvector index types)

pgvector supports two approximate-nearest-neighbor index types. Choosing
wrong means either slow searches or slow/expensive index builds.

## IVFFlat
- Splits vectors into `lists` (clusters), searches only nearby clusters
- Faster to build, smaller index size
- Needs the table to have data BEFORE building the index (it clusters
  based on existing data — build it after a bulk load, not before)
- Recall/speed tradeoff tuned by `lists` (build time) and `probes`
  (query time — how many clusters to search)
- Rule of thumb: `lists = rows / 1000` for up to ~1M rows

## HNSW
- Graph-based index (Hierarchical Navigable Small World)
- Slower to build, larger index size, more memory during build
- Can be built on an empty table (no need to wait for data)
- Generally better query performance and recall than IVFFlat at the
  same speed, especially as data grows
- Tuned by `m` (max connections per node — higher = better recall,
  bigger index) and `ef_construction` (build-time search depth —
  higher = better index quality, slower build)
- Query-time recall tuned by `ef_search` (higher = more accurate,
  slower query)

## When to use which
- Small/static dataset, build time matters more than query speed →
  IVFFlat
- Growing dataset, query speed/recall matters more than build time →
  HNSW (this is the default recommendation for most production RAG
  use cases as of pgvector 0.5+)
- If data is added incrementally and index freshness matters → HNSW
  (no need to rebuild after each bulk load like IVFFlat often needs)

## Applied to this project
The optimizer's own knowledge base (this RAG store) should use HNSW —
docs get added incrementally, and query recall matters more than
build speed for a small doc corpus.