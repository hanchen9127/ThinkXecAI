# ADR 025: Markdown upload is all or nothing

- **Status:** Accepted, 2026-10-12

## Context

Uploading a file creates a document, stores the file, splits it into sections, and stores one searchable chunk per section, with each section title turned into a vector by the local embedding model. In the starting design any failure while splitting or embedding was only logged: the document and its file stayed, without chunks, and the upload still reported success. The user then had a document that search could never find. It also accepted any file type, decoded invalid text into garbled characters, and gave no clear message for oversized files.

## Options considered

- **Keep the document when ingestion fails** (starting design), possibly with a status field: needs a retry or repair path and a visible "failed" state.
- **Do everything in one database transaction**, including the embedding call: simple, but holds a database connection for as long as the model takes, so a slow model can exhaust the connection pool.
- **Check and compute first, then write in one short transaction**, and remove the saved file if that transaction rolls back.

## Decision

Everything that can fail on bad input, and the slow embedding call, happens before any write. The upload is refused (code 400, naming the problem) for an unknown knowledge base, a file not named `.md` / `.markdown`, an empty file, text that is not valid UTF-8, or Markdown without a single heading with text (it would produce no searchable chunk). All titles are embedded in one request. Then one short transaction inserts the document, saves the file, records its path, and inserts the chunks. A hook registered before the file is written deletes the document's directory if the transaction does not commit. Files are limited to 1 MB each, with a clear "too large" message.

The service takes a file name and bytes rather than the web request's file object, and the per-file limit is separate from the whole-request limit (10 MB). A later batch upload can therefore run each file through the same path, each one atomic on its own, and report which files failed.

## Trade-offs

- A knowledge base deleted between the check and the insert makes the insert fail on its foreign key; the upload then fails like any other database error, and nothing is left behind.
- If the embedding model is down, the user sees a generic server error rather than a specific "embedding unavailable" message. Worth a dedicated error code when the frontend shows upload errors.
- Files that are not UTF-8 (for example GBK-encoded Chinese) must be converted by the user before uploading.
