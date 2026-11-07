import { useEffect, useRef } from 'react'

/**
 * Puts focus back on the control that opened a dialog once the dialog closes (Phase 14, D6). Ant Design
 * does this after its close animation, but these dialogs leave the page as soon as they close, so its
 * step never runs and focus fell to the page (seen in Chrome after Esc). Call `remember` in the handler
 * that opens the dialog. Focus goes back only if it was lost: a dialog whose save opened another page
 * leaves focus where that page put it.
 */
export function useReturnFocus(open: boolean): { remember: () => void } {
  const opener = useRef<HTMLElement | null>(null)

  useEffect(() => {
    if (open || !opener.current) return
    const target = opener.current
    opener.current = null
    // After the dialog's own clean-up and any page change in the same update.
    const timer = setTimeout(() => {
      const lost = document.activeElement === null || document.activeElement === document.body
      // An opener that left the page (a deleted row's button) cannot take focus; focus() does nothing.
      if (lost) target.focus()
    })
    return () => clearTimeout(timer)
  }, [open])

  return {
    remember: () => {
      opener.current = document.activeElement instanceof HTMLElement ? document.activeElement : null
    },
  }
}
