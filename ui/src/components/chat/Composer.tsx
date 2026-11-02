import { Button, Input } from 'antd'
import { type ReactNode, useState } from 'react'

/** Chat messages are at most 20,000 characters (ADR 032). */
const MESSAGE_LENGTH = 20000

/**
 * The message box (V1 element 8 C): Enter sends, Shift+Enter starts a new line. `onSend` gets the
 * trimmed text and throws to keep it; the box empties only once the message is stored. `tools`
 * sits left of Send (the agent picker of a new chat, V4 C1 C), else the keyboard hint.
 */
export default function Composer({
  placeholder,
  disabled,
  tools,
  onSend,
}: {
  placeholder?: string
  disabled?: boolean
  tools?: ReactNode
  onSend: (content: string) => Promise<void>
}) {
  const [text, setText] = useState('')
  const [sending, setSending] = useState(false)
  const blank = text.trim() === ''
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
          <Button
            type="primary"
            className="ml-auto"
            loading={sending}
            disabled={disabled || blank}
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
