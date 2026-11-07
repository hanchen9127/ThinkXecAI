import { useCallback, useEffect, useLayoutEffect, useRef } from 'react'
import { useScrollArea } from '../layout/scrollArea'

/** How close to the end of the page still counts as being there, in px. */
const NEAR_END = 48

/**
 * Goes to the end of the page each time `latest` changes, as long as the reader is there, so a chat
 * opens at its end and new messages stay in view. The page scrolls in `main`, not the window
 * (ADR 063). Only `latest` moves the page: opening a result makes it taller without taking the reader
 * anywhere. Scrolling up stops following and coming back to the end starts it again; a scroll that
 * is not upwards never stops it, because a page growing under a late scroll event also looks far
 * from its end. `follow` starts it again, as sending does.
 */
export function useFollowLatest(latest: unknown) {
  const area = useScrollArea()
  const following = useRef(true)

  useEffect(() => {
    if (!area) return
    let before = area.scrollTop
    const onScroll = () => {
      const y = area.scrollTop
      if (area.scrollHeight - area.clientHeight - y < NEAR_END) following.current = true
      else if (y < before) following.current = false
      before = y
    }
    area.addEventListener('scroll', onScroll, { passive: true })
    return () => area.removeEventListener('scroll', onScroll)
  }, [area])

  useLayoutEffect(() => {
    if (area && following.current) area.scrollTo({ top: area.scrollHeight })
  }, [latest, area])

  return useCallback(() => {
    following.current = true
  }, [])
}
