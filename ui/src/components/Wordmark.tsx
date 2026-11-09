import { cx } from '../utils/cx'

/**
 * The product name, smaller than a page heading (ADR 058): at the top of the list panel, or in the
 * top bar of a narrow screen (ADR 063).
 */
export default function Wordmark({ className }: { className?: string }) {
  return <p className={cx('m-0 text-sm font-semibold text-ink', className)}>ThinkXecAI</p>
}
