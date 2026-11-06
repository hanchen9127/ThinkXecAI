import XMarkdown, { type XMarkdownProps } from '@ant-design/x-markdown'
import { cx } from '../../utils/cx'

/**
 * An image would load from its address as soon as it is shown, so text planted in a document or a
 * table row could send data to a third party without a click. It becomes a link to the image
 * instead, drawn by the same link renderer (new tab, no opener).
 */
const imagesAsLinks: NonNullable<XMarkdownProps['config']> = {
  renderer: {
    image({ href, title, text, tokens }) {
      const label = text ? tokens : [{ type: 'text', raw: href, text: href }]
      return this.parser.renderer.link({
        type: 'link',
        raw: '',
        href,
        title,
        text: text || href,
        tokens: label,
      })
    },
  },
}

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
      config={imagesAsLinks}
      className={cx('min-w-0 wrap-anywhere', className)}
    />
  )
}
