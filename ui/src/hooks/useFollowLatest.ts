import { useCallback, useEffect, useLayoutEffect, useRef } from 'react'

/** How close to the end of the page still counts as being there, in px. */
const NEAR_END = 48

/**
 * Goes to the end of the page each time `latest` changes, as long as the reader is there, so a chat
 * opens at its end and new messages stay in view. Only `latest` moves the page: opening a result
 * makes it taller without taking the reader anywhere. Scrolling up stops following and coming back
 * to the end starts it again; a scroll that is not upwards never stops it, because a page growing
 * under a late scroll event also looks far from its end. `follow` starts it again, as sending does.
 */
export function useFollowLatest(latest: unknown) {
  const following = useRef(true)

  useEffect(() => {
    const page = document.documentElement
    let before = window.scrollY
    const onScroll = () => {
      const y = window.scrollY
      if (page.scrollHeight - page.clientHeight - y < NEAR_END) following.current = true
      else if (y < before) following.current = false
      before = y
    }
    window.addEventListener('scroll', onScroll, { passive: true })
    return () => window.removeEventListener('scroll', onScroll)
  }, [])

  useLayoutEffect(() => {
    if (following.current) window.scrollTo(0, document.documentElement.scrollHeight)
  }, [latest])

  return useCallback(() => {
    following.current = true
  }, [])
}
