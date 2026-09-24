# Composite Index Column Order

When creating a multi-column (composite) index, column order determines
which queries the index can actually serve efficiently.

## The Rule
Put equality-filtered columns first, range/sort columns last.
A composite index on (a, b) can serve:
- WHERE a = ? AND b = ?          (fully used)
- WHERE a = ?                    (leftmost prefix, still used)
- WHERE b = ?                    (NOT used — b isn't a leftmost prefix)

## Applied to user_id + status filtering
For a query like:
  SELECT * FROM orders WHERE user_id = ? AND status = ?

Index as (user_id, status) — NOT (status, user_id) — because:
- user_id is typically high-cardinality (many distinct values) and
  usually filtered by equality
- status is usually low-cardinality (e.g., 5-10 values) — putting it
  first means the index barely narrows the search before user_id
  filtering kicks in

## Rule of thumb
High-cardinality equality column first, low-cardinality column second.
If a range condition is involved (e.g., created_at > ?), it goes last,
after all equality columns.