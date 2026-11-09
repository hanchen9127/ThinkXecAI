# ADR 019: Document metadata is written only by the server

- **Status:** Accepted, 2026-10-08

## Context

A document's `metadata` holds details about its stored file, including the file's path on disk. When a document is deleted, the server deletes that file. The create and update requests drafted earlier accepted `metadata` from the client, so a client could point the path at any file on the server (for example `../../.env`) and then have it deleted by deleting the document.

## Options considered

- **Accept client metadata and rely on a path check at deletion time**: one missed check is enough for an attacker.
- **Accept client metadata but reject server-owned keys** such as the file path: works, but every future server-owned key must be remembered and protected.
- **Do not accept metadata in requests at all** (the starting design): the server alone writes it, during upload.

## Decision

Requests cannot set `metadata`; responses still return it. Creating a document takes only the knowledge base and a name, and updating it changes only the name. The knowledge base cannot change either, because the document's chunks are stored with it. As a second layer, the upload and delete code (Phase 8) will only touch paths that resolve inside the storage directory.

## Trade-offs

- Clients cannot attach their own descriptive data to a document. If that is ever needed, it gets its own field, separate from server data.
- A document created without upload (record only) has no file and no metadata until the upload feature fills it.
