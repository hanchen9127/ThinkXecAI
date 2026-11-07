# ADR 059: A run is shown as a trace, not as a chat reply

- **Status:** Accepted, 2026-11-04 (amends 055: how "working" is shown)

## Context

The chat drew each run like a chatbot reply: a dark rounded user bubble, a bordered card titled with the agent's name, three pulsing dots while working, and only the last step visible until asked. The product's distinctive content, the agent's Think and Execute steps and their tool results, was the part hidden by default. The owner chose a replacement element by element on the design canvas (round 2) and compared the new-chat page with today's.

## Options considered

- **The user's message:** a heading row; a prompt line on a band; a right-aligned outline box.
- **The steps:** numbered rows with times; a dot timeline; each thought paired with its action — the owner kept the existing V4 step rail instead.
- **Tool results:** a summary line that opens in place; the first rows shown; a side panel.
- **While working:** the current step in words; an elapsed bar; a waiting row with a timer.
- **When done:** fold the steps into one line above the answer; answer first; keep the steps open.
- **Starting a chat:** three new layouts and today's page.

## Decision

The user's message is a surface-coloured band with a muted mono `>` before it, no bubble. A run has no card around it. The V4 step rail stays, in ADR 058's colours and tags. Each Execute step shows the tool, a one-line summary read from its result (rows of a table, sections found), and Show, which opens the arguments and the result under the step. While a run works, every step is shown and the next one is a skeleton row with an elapsed timer (`0:03`) in place of the pulsing dots; the rule for when it is working is ADR 055's, unchanged. When the answer arrives, the steps fold into one line above it — "4 steps in 6.8 s", the tools used, and Show steps; a step's time is the gap between its message and the one before. The new-chat page stays as it is (centred "What should {agent} look into?" and the composer with the Agent select); a "Can use" line was tried in the combined mockup and dropped by the owner.

**Amended 2026-11-06.** A reply with text that called `terminate` is the run's answer and folds the run like any answer; `terminate` itself is not shown (found against the running stack: half of the finished runs ended this way and never folded). A chat opens at its end and follows new messages while the reader is at the end (owner).

**Amended 2026-11-08 (owner, from the design critique of 2026-11-06).** The trace says when something went wrong. A tool call whose result records a failure (ADR 064) gets a danger `×` on the rail in place of its dot, and its summary starts with the kind in words ("Refused:", "Wrong arguments:", "Unknown tool:", "Failed:"); the folded line adds "× N refused" when every failure is a refusal, else "× N failed". A shape, not only a colour, because the danger red and the Execute orange are close in hue; a danger dot with "failed" and a chip per kind (a tag style ADR 058 had removed) were not chosen. A run that ends with a notice (Stopped., a failure, out of steps) stays open instead of folding, so the step where it ended is in view. Notices no longer look like answers: a muted line after a small mark (a square for Stopped., a circled "!" for a failure), Try again still a text button; a bordered box and a tinted block for failures were not chosen. Not taken: one time per step for calls made in parallel; each call keeps its own time, so their times can add up to more than the step's. Tools with outside effects, such as sending email, get no mark of their own (owner, 2026-11-06).

## Trade-offs

- Summaries are derived on the client from stored tool results; a result the summary does not understand shows only the tool name.
- Times come from the database's creation times (ADR 034), so they include saving, not only the model or tool.
- Showing every step while running makes long runs tall until they fold.
