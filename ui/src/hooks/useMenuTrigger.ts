import { useRef } from 'react'

/**
 * For a ⋯ menu whose items open a dialog: `then(action)` gives an item handler that puts focus back on
 * the menu's button before the action. Clicking an item focuses the item, which hides with the menu,
 * so the dialog would remember a hidden element and focus would be lost when it closes (Phase 14, D6;
 * `useReturnFocus`). Put `trigger` on the menu's button.
 *
 * Both wait a tick: an item chosen with Enter is followed by that key's keypress, which would click
 * the button and open the menu again if focus were already on it (critique of 2026-11-08).
 */
export function useMenuTrigger() {
  const trigger = useRef<HTMLButtonElement>(null)
  return {
    trigger,
    then: (action: () => void) => () => {
      setTimeout(() => {
        trigger.current?.focus()
        action()
      })
    },
  }
}
