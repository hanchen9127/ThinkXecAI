# ADR 020: The API description says exactly which fields are present and which can be null

- **Status:** Accepted, 2026-10-09

## Context

The backend now publishes an OpenAPI description generated from its controllers, and the frontend's TypeScript types will be generated from it. By default every response field came out as optional, so the frontend would have had to treat even an id as possibly missing. In reality the JSON library writes every field of a response, using `null` for empty ones. The starting design had no API description: its frontend types were written by hand and marked "may be empty" by guesswork, including some wrong guesses.

## Options considered

- **Leave the defaults**: every field optional; the frontend checks everything, and the description cannot reveal mistakes about null values.
- **Annotate every response field by hand**: exact, but about forty annotations that are easy to forget on new fields.
- **One rule plus a marker**: every field of a response object is required, and only fields marked `@Nullable` in Java may be `null`.

## Decision

One rule plus a marker. Every field of the response objects, and of the nested types that are always complete in responses, is marked as required; fields annotated `@Nullable` get `null` added to their type. Request objects keep the required fields that follow from their validation rules. Chat options were split into two classes so the description can tell them apart: requests send a partial version, and responses always return the complete one. The merge behaviour from ADR 015 is unchanged. Only the `/api` endpoints are described, and the output is sorted so it is identical between runs. Tests check the endpoint list and these rules, and breaking each rule on purpose makes them fail.

## Trade-offs

- The required lists of the nested metadata records are checked only through the generated frontend types and the contract diff, not by a backend test of their own.

- A new nullable response field needs `@Nullable`; if it is forgotten, the description is stricter than reality, which shows up as a type error rather than a silent bug.
- Nested types that are always complete are listed by name in the configuration and must be added there when new ones appear.
