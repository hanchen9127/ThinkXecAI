# ADR 063: A fixed-height shell, and the list or the item at narrow width

- **Status:** Accepted, 2026-11-08 (amends 058's layout rule)

## Context

The whole application was one scrolling document: the rail, the list panel, and the page scrolled together. A long chat list therefore set the height of every page, so with 45 chats a chat opened at the list's tail, far below its own messages. Below about 935 px the row wrapped and the whole list sat above the page, so every page started below it, and the agent profile squeezed its values to 20 px at phone width. Found by the design critique of 2026-11-06 (25/40).

## Options considered

- **Wide screens:** a fixed-height shell where only the content area scrolls; the page keeps scrolling and the rail and list are `sticky` with their own scroll; only the list limited in height.
- **Narrow screens:** the list and the item as separate screens on the existing routes; the list in a drawer opened by a button; the list kept above the page.

## Decision

The shell is as tall as the window (`h-dvh`) and never scrolls itself. The rail, the list panel, and the content area (`main`) each scroll on their own, and `main` is the only scroll container for a page: following new messages (`useFollowLatest`) follows `main`, and the message box sticks to the bottom of `main`.

At narrow width a section's root route (`/agent`, `/chat`, `/knowledge-base`) shows its list, and an opened item shows only the item, its header starting with a back arrow to the list. The rail becomes a bar on top of every screen: the wordmark, then the three sections as icon links (owner, on the canvas; bottom tabs, which share the bottom with the message box, and the desktop icon strip on list screens only were not chosen). The new-chat page is an item too, so it gets its own route, `/chat/new`, and every link that starts a new chat goes there: `/chat` had been the new chat, and one address cannot be both the list and the new chat when only one shows (owner; telling them apart by a query parameter was not chosen, as it hides which screen shows in a parameter). `/` and, on a wide screen, `/chat` still open a new chat. The agent profile stacks each label above its value.

The owner asked which is easier to maintain. The sticky variant depends on several things holding at once (window scroll, sticky offsets, a panel height, its own scroll), and once the row wraps a sticky list would cover the page, so it needs a media query matching a wrap point that today follows from the content's width: the same breakpoint kept in two places. With one scroll container, each panel is independent and nothing depends on where the row wraps. The skip link and the focus after a route change (Phase 14) also have one clear target, `main`.

Separate screens reuse the routes the app already has, and the browser's Back works as expected. A drawer adds open and closed state, its own focus handling, and a second way to reach the list.

This replaces the earlier choice (2026-11-06) to keep the list above the page at phone width.

## Trade-offs

- The browser no longer restores a page's scroll position on Back, because the window does not scroll; the app did not rely on it.
- Narrow width now has a breakpoint of its own (one value, in the theme), where the layout used to wrap by itself.
- At narrow width the list and the item cannot be seen together.
- Revisit if a page ever needs the window to scroll, for example to print a long chat.
