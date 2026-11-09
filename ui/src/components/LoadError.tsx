import { errorMessage } from '../utils/text'

/**
 * A load that failed: what could not be loaded, why and what to do next (ADR 066), and Try again when
 * the caller can load it again. One component, so every such message reads and lays out the same.
 */
export default function LoadError({
  what,
  error,
  onRetry,
}: {
  /** What failed to load, as it reads after "Couldn't load": "agents", "the documents". */
  what: string
  error: unknown
  onRetry?: () => void
}) {
  return (
    <div role="alert" className="flex flex-col items-start gap-2 text-muted">
      <p className="m-0">
        Couldn't load {what}. {errorMessage(error)}
      </p>
      {onRetry && (
        <button type="button" onClick={onRetry} className="cursor-pointer text-ink underline">
          Try again
        </button>
      )}
    </div>
  )
}
