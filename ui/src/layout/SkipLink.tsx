import { MAIN_ID } from './Content'

/**
 * The first stop in the tab order: "Skip to content" moves focus to the page, past the rail and the
 * list (Phase 14, D6; 15 Tab presses reached a chat's first message before). Hidden until focused.
 * It focuses `main` itself rather than following `#main`, so the address does not change.
 */
export default function SkipLink() {
  return (
    <a
      href={`#${MAIN_ID}`}
      onClick={(event) => {
        event.preventDefault()
        document.getElementById(MAIN_ID)?.focus()
      }}
      // `not-sr-only` resets padding, so the padding is set for focus too.
      className="sr-only rounded-ui bg-ink text-sm font-semibold text-white no-underline focus:not-sr-only focus:fixed focus:top-2 focus:left-2 focus:z-50 focus:px-3 focus:py-2"
    >
      Skip to content
    </a>
  )
}
