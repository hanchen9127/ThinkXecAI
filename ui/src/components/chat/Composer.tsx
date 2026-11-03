import { Button, Input } from 'antd'
import { type ReactNode, useState } from 'react'
import { cx } from '../../utils/cx'

/** Chat messages are at most 20,000 characters (ADR 032). */
const MESSAGE_LENGTH = 20000

const LABELS = ['Send', 'Stop', 'Stopping…'] as const

/**
 * The message box (V1 element 8 C): Enter sends, Shift+Enter starts a new line. `onSend` gets the
 * trimmed text and throws to keep it; the box empties only once the message is stored. `tools`
 * sits left of Send (the agent picker of a new chat, V4 C1 C), else the keyboard hint.
 * <p>
 * With `onStop` (the agent is working), an empty box turns Send into Stop; typing turns it back, so a
 * message can still be queued. `stopping` shows "Stopping…" until the run ends (ADR 060).
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
  const blank = text.trim() === ''
  const stopMode = onStop !== undefined && blank && !disabled
  const shown = stopMode ? (stopping ? 'Stopping…' : 'Stop') : 'Send'
  // How to send means nothing in a chat that cannot be continued.
  const hint = !disabled && (
    <span className="text-xs text-muted">Enter to send, Shift+Enter for a new line</span>
  )

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
        <div className="flex flex-wrap items-center justify-between gap-3 border-t border-hairline py-2.5 pr-3 pl-4">
          {tools ?? hint}
          {/* One button that changes its icon and label in place (ADR 061, M4); the hidden labels
              keep its width. */}
          <Button
            type="primary"
            className={cx('ml-auto', stopping && 'stopping')}
            loading={!stopMode && sending}
            disabled={stopMode ? stopping : disabled || blank}
            icon={
              !stopping && (
                <span
                  aria-hidden="true"
                  className="shape-icon"
                  data-shape={stopMode ? 'square' : 'triangle'}
                />
              )
            }
            onClick={stopMode ? onStop : () => void send()}
          >
            <span className="grid">
              {LABELS.map((label) => (
                <span
                  key={label}
                  aria-hidden={label === shown ? undefined : true}
                  className={cx(
                    'col-start-1 row-start-1 transition-opacity',
                    label !== shown && 'opacity-0',
                  )}
                >
                  {label}
                </span>
              ))}
            </span>
          </Button>
        </div>
      </div>
      {tools && hint}
    </div>
  )
}
