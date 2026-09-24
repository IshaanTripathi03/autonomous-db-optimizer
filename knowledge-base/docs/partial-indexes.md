# Partial Indexes

A partial index only includes rows matching a WHERE condition, instead
of the whole table. Smaller index, faster writes, faster lookups for
the specific slice of data that's actually queried often.

## When it beats a full composite index
If queries almost always filter on a condition that only matches a
small subset of rows, index just that subset.

Example: orders table where 95% of queries are:
  SELECT * FROM orders WHERE status = 'pending' AND user_id = ?

A partial index:
  CREATE INDEX idx_orders_pending_user
  ON orders (user_id)
  WHERE status = 'pending';

This index is much smaller than a full (user_id, status) index because
it excludes 'completed', 'cancelled', etc. rows entirely. Smaller index
= faster to scan, faster to maintain on writes to non-pending rows
(those writes don't touch this index at all).

## When NOT to use it
- If queries filter on many different status values roughly evenly,
  a partial index only helps one value — you'd need one partial index
  per value, which is wasteful. Use a full composite index instead.
- If the "small subset" isn't actually small (e.g., 60% of rows are
  'pending'), the size savings aren't worth the added complexity.

## Applied to this project
The diagnosis engine should recommend a partial index instead of a
full composite index when:
1. The WHERE clause includes a low-cardinality column (like `status`)
2. That column's value in the slow query is a minority of total rows
   (check via a quick COUNT(*) WHERE status = ? vs COUNT(*) total)
3. Otherwise, default to a full composite index — it's the safer,
   more broadly useful choice