import XMarkdown from '@ant-design/x-markdown'
import { cx } from '../../utils/cx'

/**
 * Model and tool text as Markdown. Raw HTML in it is shown as text, never parsed, and the output is
 * sanitized by X Markdown (DOMPurify), so an answer cannot run script or add `javascript:` links.
 */
export default function Markdown({ text, className }: { text: string; className?: string }) {
  return (
    <XMarkdown
      content={text}
      escapeRawHtml
      openLinksInNewTab
      className={cx('min-w-0 break-words', className)}
    />
  )
}
