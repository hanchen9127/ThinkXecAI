# ADR 024: Strict-order scanning for knowledge-base vector search

- **Status:** Accepted, 2026-10-11

## Context

A knowledge search returns the 3 chunks of one knowledge base closest to the question. All knowledge bases share one table and one approximate-nearest-neighbour (HNSW) index. When PostgreSQL uses that index, it collects about 40 nearest candidates across all knowledge bases and only then drops those from other knowledge bases. Reproduced with 200 chunks near the question in one knowledge base and 3 farther ones in another: searching the second through the index returned 0 of its 3 chunks. Small tables are still read in full, so the problem appears only as data grows.

## Options considered

- **Raise the candidate count** (`hnsw.ef_search`, e.g. 1000): fixes this case, but any fixed number fails once enough closer chunks belong to other knowledge bases, and every search pays for it.
- **Set iterative scanning for the whole database** in the init script: applies only to newly created databases, and the reason is invisible where the search is written.
- **Iterative scanning in strict order, set by the search itself** for its own transaction: the index keeps returning candidates, nearest first, until 3 pass the filter.
- **One index or table per knowledge base**: exact, but the schema is fixed by the specs.

## Decision

The search sets `hnsw.iterative_scan = strict_order` with `SET LOCAL` inside a short read-only transaction (measured: 3 of 3, nearest first). The question is embedded before that transaction begins, so no database connection is held while the local model works. A test reproduces the 200-versus-3 case through the index and, as a control, shows that the same query without the setting finds nothing. Forcing the index in the test needed care: switching off full-table scans alone made PostgreSQL use the knowledge-base index with an exact sort, which always succeeds. Switching off sorting leaves the HNSW index as the only option.

## Trade-offs

- A knowledge base that is far from the question and crowded out by others makes the index scan read more entries before it finds 3. pgvector caps this (`hnsw.max_scan_tuples`, default 20,000), so in extreme cases a search can still return fewer than 3.
- The setting lives in a transaction, so the search cannot share a caller's long transaction without the setting applying to it as well. That is harmless: it only changes filtered HNSW scans.
