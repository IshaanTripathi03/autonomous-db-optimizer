# When NOT to Index

An indexing tool that always says "add an index" isn't trustworthy —
part of the reasoning is knowing when an index makes things worse or
doesn't help at all. This is what separates a real recommendation
engine from a rule that fires on every slow query.

## Small tables
If a table has only a few thousand rows, a sequential scan is often
faster than an index scan anyway — Postgres's query planner will
usually just ignore the index. Building one adds write overhead for
no read benefit.
Rule of thumb: below ~1,000-10,000 rows (depends on row width), don't
bother recommending an index based on row count alone.

## Low selectivity columns
If a WHERE clause filters on a column where the matching rows are a
large fraction of the table (e.g., status = 'active' when 80% of rows
are active), an index scan touches almost as many rows as a full scan
— but with extra I/O for the index lookups. The planner will usually
skip the index and scan the table anyway.
Rule of thumb: if a column's queried value matches more than ~15-20%
of rows, indexing it alone won't help — reconsider for a partial index
on a different condition, or accept the scan.

## Write-heavy tables with rarely-run queries
Every index adds overhead to INSERT/UPDATE/DELETE. If a table is
written to constantly but the slow query in question runs once a day
for a report, the ongoing write cost may not be worth the occasional
read speedup.

## Already-covered by an existing index
Check `pg_indexes` before recommending a new index — if a composite
index already starts with the same leftmost columns, a new narrower
index is redundant. This is a direct check the diagnosis engine should
run before calling RAG at all, since it's a plain lookup, not reasoning.

## Applied to this project
Before the RAG recommends any new index, the diagnosis engine should
already have checked:
1. Table row count (pg_class.reltuples) — skip if too small
2. Existing indexes (pg_indexes) — skip if already covered
3. Column selectivity, if computable cheaply — flag if too low to help

This means the RAG's job isn't "should we index" (that's a pre-check),
it's "given that indexing makes sense, which type and shape of index
is correct."