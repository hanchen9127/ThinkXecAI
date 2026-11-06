# ADR 012: The email tool sends through Gmail with OAuth 2.0 and a recipient allowlist

- **Status:** Accepted, 2026-10-06 (implemented in the tool-system phase)

## Context

Agents can be given an optional tool that sends email. The starting design sent through a QQ Mail account over SMTP with a mailbox password, and the model could send to any address. The project switches to Gmail. Because the model decides who receives mail, a manipulated prompt (for example, instructions hidden in a knowledge-base document) could make the agent send private data, such as database query results, to an outside address from the owner's own account.

## Options considered

- **Gmail SMTP with an App Password**: smallest change (one host name), but it is a long-lived password with full mailbox access, and Google discourages it. Regular passwords no longer work at all.
- **Gmail SMTP with OAuth 2.0 (XOAUTH2)**: no password, but SMTP needs the full-mailbox scope, which also allows reading and deleting mail.
- **Gmail API with OAuth 2.0 and the send-only scope** (`gmail.send`).
- **A transactional email service** (for example SendGrid): built for application email, but adds a third-party account and a sending domain to set up.

## Decision

Use the Gmail API with OAuth 2.0 and only the `gmail.send` scope. If the credentials leak, they can send mail but cannot read or delete anything. A refresh token is stored as a secret, and short-lived access tokens are renewed automatically. Every send is checked against a **recipient allowlist** kept in the environment, so personal addresses never enter the public repository. The allowlist is **fail-closed**: if it is empty, nothing is sent. A refused send tells the model it was refused, without revealing the allowed addresses.

## Trade-offs

- More setup than a password: a Google Cloud project, the Gmail API, an OAuth client, and a one-time consent step. The OAuth consent screen must be "In production": in "Testing", Google expires refresh tokens after 7 days and the tool would silently stop working.
- An unverified personal app shows a warning on the one-time consent screen. Acceptable for a single owner.
- The allowlist limits the agent to known recipients by design. Sending to a new person requires changing configuration, not just asking the agent.
- Whether the app should still start without Gmail credentials (with the email tool disabled) is decided when the tool is implemented.
