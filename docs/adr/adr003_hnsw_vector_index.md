# ADR 003: HNSW index for knowledge-base vector search

- **Status:** Accepted, 2026-10-02; amended by ADR 024 (with a `kb_id` filter, HNSW alone can still return fewer than 3 results for a small knowledge base; the search also needs strict-order iterative scanning)

## Context

Knowledge-base retrieval finds the 3 text chunks closest to a question, using pgvector. The starting design used an `ivfflat` index created when the database is first set up, while the table is still empty. `ivfflat` groups vectors around centers it learns from the data present when the index is built, so with no data its groups are meaningless. With default settings, a search on a small knowledge base could return fewer than 3 results, or none.

## Options considered

- **Keep `ivfflat`** and rebuild it after data is loaded, or tell every query to scan more groups.
- **No index**: exact search over every row; always correct, slower as data grows.
- **HNSW index**: builds incrementally as rows arrive and needs no training data.

## Decision

Use an HNSW index with the same distance function. It gives correct results on a tiny knowledge base and stays fast as data grows, with no maintenance step. Verified on a fresh database: with 5 chunks, the query uses the index and returns 3 results, nearest first.

## Trade-offs

- HNSW uses more memory and makes inserts slightly slower than `ivfflat`. Irrelevant at this project's scale; documents are uploaded rarely and searched often.
- It is still approximate search. If exact results ever matter more than speed, dropping the index is an option.
