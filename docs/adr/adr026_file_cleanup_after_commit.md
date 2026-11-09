# ADR 026: Stored files are deleted after the database commit

- **Status:** Accepted, 2026-10-12

## Context

Each uploaded document has a file on disk next to its database rows. Files cannot take part in a database transaction: once deleted, a rollback cannot bring them back. In the starting design, deleting a document removed its file first and the row second, so a failed or rolled-back delete left a document whose file was gone. Deleting a knowledge base removed its documents through the database but left all their files on disk for good.

## Options considered

- **Delete the files first, then the rows** (starting design): a rollback leaves rows pointing at missing files.
- **Delete the rows, then the files in the same method**: if a later step in the same transaction fails, the files are already gone.
- **Delete the files after the transaction commits**: a rollback keeps everything; the worst case is a leftover directory.
- **Mark rows as deleted and clean up files in a background job**: robust, but needs a job, a status column, and filtering everywhere.

## Decision

The delete runs in the database transaction as before and registers a hook that removes the document's or knowledge base's directory only after the commit. The directory is derived from the ids, never from the stored path. A failed file deletion is logged and the request still succeeds, because the database change already stands. The same helper deletes an upload's file when its transaction does not commit (ADR 025), so both directions share one place that logs failures.

## Trade-offs

- A crash between the commit and the file deletion, or a failed deletion, leaves an orphaned directory. It is harmless (nothing points at it) and can be found by comparing directory names with document ids, if a cleanup job is ever needed.
- Spring rethrows exceptions from after-commit hooks to the caller, so the helper must catch them; a test pins this.
