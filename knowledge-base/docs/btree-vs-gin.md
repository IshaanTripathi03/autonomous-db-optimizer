# B-tree vs GIN Indexes

Most indexing advice assumes B-tree, but B-tree only works for
equality/range comparisons on scalar values. Some column types need
a completely different index type to be searchable at all.

## B-tree (the default)
- Works for: =, <, >, <=, >=, BETWEEN, ORDER BY
- Use for: numeric IDs, timestamps, status strings/enums, foreign keys
- This is what applies to user_id, status, created_at — i.e. most of
  this project's diagnosis cases

## GIN (Generalized Inverted Index)
- Works for: containment/membership queries — "does this JSONB contain
  this key?", "does this array contain this value?", full-text search
- Use for: JSONB columns queried with @>, ?, ?| operators; array
  columns queried with @>, &&; tsvector full-text search columns
- A B-tree CANNOT speed up a JSONB containment query — it's the wrong
  index type entirely, not just a suboptimal one

## Example
  SELECT * FROM orders WHERE metadata @> '{"priority": "high"}';

A B-tree on `metadata` does nothing for this. You need:
  CREATE INDEX idx_orders_metadata ON orders USING GIN (metadata);

## Applied to this project
Before recommending any index, the diagnosis engine should check the
column type and operator being used in the slow query:
- Scalar column + =/</>  → B-tree (current default assumption)
- JSONB/array column + @>/?/&&  → GIN
- Recommending a B-tree on a JSONB containment query would be a wrong
  fix, not just a suboptimal one — this is a good case to test the
  RAG's reasoning against
  