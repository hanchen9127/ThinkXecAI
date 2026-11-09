import { Button, ConfigProvider, Input } from 'antd'
import { type ReactNode, useEffect, useRef, useState } from 'react'
import { stopTheme } from '../../theme'
import { cx } from '../../utils/cx'

/** Chat messages are at most 20,000 characters (ADR 032). */
const MESSAGE_LENGTH = 20000

const STOP_LABELS = ['Stop', 'Stopping…'] as const

/**
 * The message box (V1 element 8 C): Enter sends, Shift+Enter starts a new line. `onSend` gets the
 * trimmed text and throws to keep it; the box empties only once the message is stored. `tools`
 * sits left of Send (the agent picker of a new chat, V4 C1 C), else the keyboard hint.
 * <p>
 * With `onStop` (the agent is working), an outlined Stop sits left of Send and stays while the user
 * types, so a correction can be typed and the run stopped; Send still queues a message (ADR 060,
 * amended 2026-11-09). `stopping` shows "Stopping…" until the run ends.
 */
export default function Composer({
  placeholder,
  disabled,
  tools,
  onSend,
  onStop,
  stopping = false,
}: {
  placeholder?: string
  disabled?: boolean
  tools?: ReactNode
  onSend: (content: string) => Promise<void>
  onStop?: () => void
  stopping?: boolean
}) {
  const [text, setText] = useState('')
  const [sending, setSending] = useState(false)
  const box = useRef<HTMLTextAreaElement>(null)
  // Whether Stop held focus; a button that unmounts fires no blur, so this outlives it.
  const stopFocused = useRef(false)
  const blank = text.trim() === ''
  const showStop = onStop !== undefined && !disabled
  const shownStop = stopping ? 'Stopping…' : 'Stop'
  // How to send means nothing in a chat that cannot be continued.
  const hint = !disabled && (
    <span className="text-xs text-muted">Enter to send, Shift+Enter for a new line</span>
  )

  // When the run ends, Stop goes; focus that was on it moves to the box rather than the page.
  useEffect(() => {
    if (!showStop && stopFocused.current) {
      stopFocused.current = false
      box.current?.focus()
    }
  }, [showStop])

  async function send() {
    if (blank || sending || disabled) return
    setSending(true)
    try {
      await onSend(text.trim())
      setText('')
    } catch {
      // The caller shows why; the text stays to send again.
    } finally {
      setSending(false)
    }
  }

  return (
    <div className="flex flex-col gap-2">
      <div className="overflow-hidden rounded-ui border border-field bg-surface">
        <Input.TextArea
          ref={(input) => {
            box.current = input?.resizableTextArea?.textArea ?? null
          }}
          aria-label="Message"
          variant="borderless"
          autoSize={{ minRows: 3, maxRows: 12 }}
          value={text}
          maxLength={MESSAGE_LENGTH}
          placeholder={placeholder}
          disabled={disabled}
          onChange={(e) => setText(e.target.value)}
          onKeyDown={(e) => {
            if (e.key === 'Enter' && !e.shiftKey && !e.nativeEvent.isComposing) {
              e.preventDefault()
              void send()
            }
          }}
        />
        <div className="flex flex-wrap items-center gap-2 border-t border-hairline py-2.5 pr-3 pl-4">
          <span className="mr-auto">{tools ?? hint}</span>
          {showStop && (
            <ConfigProvider theme={stopTheme}>
              {/* Stopping… is aria-disabled, not disabled: a disabled button drops focus in Chrome, and
                  focus on it should stay until the run ends and it can move to the box. */}
              <Button
                className={cx(stopping && 'cursor-default opacity-60')}
                aria-disabled={stopping || undefined}
                icon={<span aria-hidden="true" className="shape-icon" data-shape="square" />}
                onClick={stopping ? undefined : onStop}
                onFocus={() => (stopFocused.current = true)}
                onBlur={() => (stopFocused.current = false)}
              >
                {/* The hidden label keeps the width while Stop turns into Stopping… (ADR 061). */}
                <span className="grid justify-items-start">
                  {STOP_LABELS.map((label) => (
                    <span
                      key={label}
                      aria-hidden={label === shownStop ? undefined : true}
                      className={cx(
                        'col-start-1 row-start-1 transition-opacity',
                        label !== shownStop && 'opacity-0',
                      )}
                    >
                      {label}
                    </span>
                  ))}
                </span>
              </Button>
            </ConfigProvider>
          )}
          <Button
            type="primary"
            loading={sending}
            disabled={disabled || blank}
            icon={!sending && <span aria-hidden="true" className="shape-icon" />}
            onClick={() => void send()}
          >
            Send
          </Button>
        </div>
      </div>
      {tools && hint}
    </div>
  )
}
