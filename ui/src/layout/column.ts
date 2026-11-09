/**
 * The one column every page sits in, centred in the space beside the list panel (ADR 058). Header
 * bands span the pane and hold their content in it, so their text lines up with the page below. A
 * narrow screen keeps 16px at each side (ADR 063).
 */
export const COLUMN = 'mx-auto box-border w-full max-w-230 px-4 wide:px-12'

/**
 * An item page's header band. On a narrow screen it stays at the top while the page scrolls, so the
 * way back to the list is always in view (ADR 063, found in the Phase 14 check); a wide screen has
 * the list beside the page, and the header scrolls away as before.
 */
export const ITEM_HEADER = 'sticky top-0 z-10 border-b border-line bg-surface wide:static'
