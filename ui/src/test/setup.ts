import '@testing-library/jest-dom/vitest'
import { cleanup } from '@testing-library/react'
import { afterEach, beforeEach, vi } from 'vitest'
import { FakeEventSource } from './fakeEventSource'

// jsdom has no EventSource; chats open one. Each test starts with no streams.
beforeEach(() => {
  FakeEventSource.instances = []
  vi.stubGlobal('EventSource', FakeEventSource)
})

// Testing Library unmounts automatically only when test globals are enabled; they are not.
afterEach(() => {
  cleanup()
})

// jsdom has no ResizeObserver; Ant Design's dialogs and selects observe their size. Nothing resizes.
if (!window.ResizeObserver) {
  window.ResizeObserver = class {
    observe() {}
    unobserve() {}
    disconnect() {}
  }
}

// jsdom has no matchMedia; Ant Design reads it for responsive layouts. Nothing matches, except a
// wish for reduced motion: tests check states, not motion (ADR 061), and without motion Ant Design
// removes a closed menu or dialog at once.
if (!window.matchMedia) {
  window.matchMedia = (query: string) =>
    ({
      matches: query === '(prefers-reduced-motion: reduce)',
      media: query,
      onchange: null,
      addEventListener: () => {},
      removeEventListener: () => {},
      addListener: () => {},
      removeListener: () => {},
      dispatchEvent: () => false,
    }) as MediaQueryList
}
