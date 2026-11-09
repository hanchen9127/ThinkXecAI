# ADR 066: An error says what failed and what to do next

- **Status:** Accepted, 2026-11-08

## Context

The UI showed the backend's message as it was. Many messages are written for developers:
- "Chat session not found"
- "Internal server error"
- "agentId: unknown agent …"

A send failure showed one of these alone in a red Alert, without saying what had failed or what to do. The design critique of 2026-11-08 (27/40) scored error recovery 2 out of 4 for this.

## Options considered

- **Where the wording comes from:**
  - The frontend maps errors once, by status.
  - The backend's `BizException` messages are rewritten for people.
- **How an error looks:**
  - The red Alert is kept, with new wording.
  - A quiet line in the style of the trace's notices.

## Decision

The owner chose the frontend mapping and to keep the Alert.

**One function, `errorMessage`, says why a request failed and what to do next, by status:**
- **No answer from the server, or a 502 or 504:** "Can't reach the server. Check that it is running, then try again." A proxy put in front of the server answers 502 or 504 when the server is down (amended 2026-11-09; the Docker setup has none, ADR 062).
- **404:** "It no longer exists; it may have been deleted in another tab."
- **503:** the backend's own message, already written for people (ADR 057).
- **Any other 5xx:** "The server ran into a problem. Try again; if it keeps happening, check the server log."
- **400 and 409:** the backend's message, ended as a sentence. These messages name the field or the conflict, which is the useful part.

**Each place says what failed.** An Alert's title names the action that failed, and its description gives the reason:
- "Couldn't send your message."
- "Couldn't stop the agent."
- "Couldn't save the agent."
- "Couldn't delete the knowledge base."

Inline messages put the action first in the same way ("Couldn't load agents. …", "Couldn't rename it. …").

**The backend is unchanged.** Its messages still go to logs and to API clients as they are.

## Trade-offs

- **Some 404s are not deletions.** A 404 always reads as deleted, which is usually right in this app (a chat or agent removed in another tab). A 404 for any other reason gets the same sentence.
- **400 messages keep the field-name style** ("name: must not be blank."). Forms check these fields before sending, so a user rarely sees one.
- **A new status needs a case here.** Any other 4xx falls back to the backend's message.
