import { act } from '@testing-library/react'

/**
 * Stands in for the browser's `EventSource` (jsdom has none); every test gets it (setup.ts). A test
 * finds the stream a chat opened with `FakeEventSource.latest()` and drives it.
 */
export class FakeEventSource extends EventTarget {
  static readonly CONNECTING = 0
  static readonly OPEN = 1
  static readonly CLOSED = 2
  static instances: FakeEventSource[] = []

  readonly url: string
  readyState: number = FakeEventSource.CONNECTING

  constructor(url: string | URL) {
    super()
    this.url = String(url)
    FakeEventSource.instances.push(this)
  }

  close() {
    this.readyState = FakeEventSource.CLOSED
  }

  get closed() {
    return this.readyState === FakeEventSource.CLOSED
  }

  /** The server sends an event; `data` objects are sent as JSON, like Spring MVC writes them. */
  emit(type: string, data: unknown = '') {
    if (this.closed) throw new Error(`emit(${type}) on a closed stream`)
    this.readyState = FakeEventSource.OPEN
    const text = typeof data === 'string' ? data : JSON.stringify(data)
    act(() => {
      this.dispatchEvent(new MessageEvent(type, { data: text }))
    })
  }

  /** The connection failed: the browser retries (`CONNECTING`) or gives up (`CLOSED`). */
  fail(state: 'retrying' | 'gave up') {
    this.readyState = state === 'gave up' ? FakeEventSource.CLOSED : FakeEventSource.CONNECTING
    act(() => {
      this.dispatchEvent(new Event('error'))
    })
  }

  static latest(): FakeEventSource {
    const source = FakeEventSource.instances.at(-1)
    if (!source) throw new Error('No EventSource was opened')
    return source
  }

  static open(): FakeEventSource[] {
    return FakeEventSource.instances.filter((s) => !s.closed)
  }
}
