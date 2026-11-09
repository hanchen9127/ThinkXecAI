# ADR 065: Tools are named by a label from the backend, everywhere

- **Status:** Accepted, 2026-11-08 (amends 058; changes `GET /api/tools` from ISSUE-008)

## Context

The UI named a tool by one of two code names. The agent list, the profile, and the form showed the name stored in `allowedTools` (`dataBaseTool`), and the trace showed the name the model called (`databaseQuery`). A person could not tell they were the same tool. The agent list counted only the chosen tools ("1 tool"), while the profile also listed the four tools every agent has, as a hard-coded sentence. Found by the design critique of 2026-11-08 (29/40), which rated it a P1. The owner also judged the look of a tool tag, a small orange square before a mono name (ADR 058), as not good enough.

## Options considered

- **Where a label comes from:**
  - the backend, as a field of each tool;
  - a table in the frontend from either code name to a label.
- **How a tool is shown** (Redesign 8 on the canvas):
  - A: a line icon before a plain name;
  - B: a verb phrase per tool and no mark ("Read the database");
  - C: an outlined token.
- **What `GET /api/tools` lists:**
  - the optional tools only, as before;
  - every tool a person sees.

## Decision

- **Labels come from the backend.**
  - Each `Tool` has `getLabel()`, for example "Database", "Email", "Knowledge search", "Date", "Sample city", "Sample weather".
  - `ToolVO` carries `name` (the stored name), `callName` (the name the model calls), `label`, `description`, and `type`.
  - `callName` is read from the tool's own Spring AI callback, the same way a run reads it, so it cannot drift from the method's annotation. Startup fails if a tool does not have exactly one `@Tool` method.
- **`GET /api/tools` returns every tool a person sees,** fixed and optional.
  - A tool that only runs the loop (`terminate`, `isShown()` false) is left out.
  - The agent form still offers only the optional tools, and names the rest in its hint ("Every agent also has: …").
- **The frontend loads the list once.** It loads in a `ToolsProvider` and names a tool by either code name through `labelOf`. Until the list arrives, or if it fails, it shows the code name.
- **A tool is an outlined token** (C, owner).
  - It is solid when the agent's setup chose the tool, and dashed when every agent has it.
  - The token is the same in the list, the profile, the form, and the trace.
  - The agent list counts all of an agent's tools ("6 tools"), matching the profile.
  - The code name the model called appears once, in mono, when a tool call is opened in the trace.

The owner chose the backend so that a new tool is described in one place. The token replaces the orange square and the mono name of ADR 058, which tied the tag to Execute's colour and to code; Execute's colour stays on the step label.

## Trade-offs

- **One more request when the app loads.** It is small, and it is shared by every page.
- **Labels are English strings in Java.** If the UI is ever translated, they become keys.
- **Old stored names fall back to the code name.** A name that is no longer a tool, in an old agent or an old trace, shows as code because it has no label.
- **Fixed tools always look the same.** The dashed tokens are repeated on every agent; with many fixed tools they could be folded into one "+4 always on".
