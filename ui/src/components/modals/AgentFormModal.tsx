import { Alert, Input, Modal, Select, Slider } from 'antd'
import { type ReactNode, useState } from 'react'
import { AGENT_MODELS, type Agent } from '../../api/api'
import { useKnowledgeBases } from '../../hooks/useKnowledgeBases'
import { useTools } from '../../hooks/useTools'
import { cx } from '../../utils/cx'
import { errorMessage } from '../../utils/text'
import Tag from '../Tag'
import {
  type AgentDraft,
  type DraftErrors,
  LIMITS,
  draftFrom,
  emptyDraft,
  validate,
} from '../agents/agentForm'

const SECTIONS = [
  { id: 'basics', label: 'Basics' },
  { id: 'model', label: 'Model' },
  { id: 'tools', label: 'Tools' },
  { id: 'knowledge', label: 'Knowledge' },
  { id: 'options', label: 'Chat options' },
] as const
type SectionId = (typeof SECTIONS)[number]['id']

/**
 * Creates an agent, or edits `agent` (V2 A4 B: a wide dialog with sections). `onSave` gets the
 * whole draft and throws the backend's error to show; the dialog stays open until it succeeds.
 */
export default function AgentFormModal({
  agent,
  onSave,
  onClose,
}: {
  agent?: Agent
  onSave: (draft: AgentDraft) => Promise<void>
  onClose: () => void
}) {
  const [draft, setDraft] = useState<AgentDraft>(() => (agent ? draftFrom(agent) : emptyDraft()))
  const [section, setSection] = useState<SectionId>('basics')
  const [errors, setErrors] = useState<DraftErrors>({})
  const [saveError, setSaveError] = useState<unknown>()
  const [saving, setSaving] = useState(false)
  const change = (patch: Partial<AgentDraft>) => setDraft((d) => ({ ...d, ...patch }))

  async function save() {
    const found = validate(draft)
    setErrors(found)
    if (found.name) {
      setSection('basics')
      return
    }
    setSaving(true)
    setSaveError(undefined)
    try {
      await onSave(draft)
    } catch (error) {
      setSaveError(error)
    } finally {
      setSaving(false)
    }
  }

  return (
    <Modal
      open
      title={agent ? `Edit ${agent.name}` : 'New agent'}
      width={800}
      okText={agent ? 'Save changes' : 'Create agent'}
      onOk={save}
      onCancel={onClose}
      confirmLoading={saving}
      destroyOnHidden
    >
      {/* A fixed height, so switching sections never resizes the dialog. */}
      <div className="-mx-6 flex h-112 border-y border-hairline">
        <nav
          aria-label="Form sections"
          className="flex w-40 shrink-0 flex-col border-r border-hairline py-3"
        >
          {SECTIONS.map(({ id, label }) => (
            <button
              key={id}
              type="button"
              aria-current={section === id ? 'step' : undefined}
              onClick={() => setSection(id)}
              className={cx(
                'cursor-pointer px-5 py-2.5 text-left',
                section === id ? 'bg-ground font-semibold text-ink' : 'text-muted hover:text-ink',
              )}
            >
              {label}
            </button>
          ))}
        </nav>
        <div className="flex min-w-0 flex-1 flex-col gap-4 overflow-y-auto px-7 py-5">
          {saveError !== undefined && (
            <Alert type="error" showIcon title={errorMessage(saveError)} />
          )}
          {section === 'basics' && <Basics draft={draft} errors={errors} change={change} />}
          {section === 'model' && <ModelSection draft={draft} change={change} />}
          {section === 'tools' && <ToolsSection draft={draft} change={change} />}
          {section === 'knowledge' && <KnowledgeSection draft={draft} change={change} />}
          {section === 'options' && <OptionsSection draft={draft} change={change} />}
        </div>
      </div>
    </Modal>
  )
}

interface SectionProps {
  draft: AgentDraft
  change: (patch: Partial<AgentDraft>) => void
}

function Field({
  id,
  label,
  hint,
  error,
  children,
}: {
  id: string
  label: string
  hint?: string
  error?: string
  children: ReactNode
}) {
  return (
    <div className="flex flex-col gap-1">
      <label htmlFor={id} className="text-xs font-semibold text-muted">
        {label}
      </label>
      {children}
      {error && (
        <span id={`${id}-error`} className="text-xs text-danger">
          {error}
        </span>
      )}
      {hint && <span className="text-xs text-muted">{hint}</span>}
    </div>
  )
}

function Basics({ draft, errors, change }: SectionProps & { errors: DraftErrors }) {
  return (
    <>
      <Field id="agent-name" label="Name" error={errors.name}>
        <Input
          id="agent-name"
          variant="underlined"
          value={draft.name}
          maxLength={LIMITS.name}
          status={errors.name ? 'error' : undefined}
          aria-invalid={errors.name ? true : undefined}
          aria-describedby={errors.name ? 'agent-name-error' : undefined}
          onChange={(e) => change({ name: e.target.value })}
        />
      </Field>
      <Field id="agent-description" label="Description">
        <Input
          id="agent-description"
          variant="underlined"
          value={draft.description}
          maxLength={LIMITS.description}
          onChange={(e) => change({ description: e.target.value })}
        />
      </Field>
      <Field
        id="agent-instructions"
        label="Instructions"
        hint="Sent to the model before every conversation."
      >
        <Input.TextArea
          id="agent-instructions"
          rows={9}
          value={draft.systemPrompt}
          maxLength={LIMITS.systemPrompt}
          onChange={(e) => change({ systemPrompt: e.target.value })}
        />
      </Field>
    </>
  )
}

function ModelSection({ draft, change }: SectionProps) {
  return (
    <div className="max-w-90">
      <Field
        id="agent-model"
        label="Model"
        hint="Gemini answers only when the server has its API key."
      >
        <Select
          id="agent-model"
          value={draft.model}
          virtual={false}
          options={AGENT_MODELS.map((model) => ({ value: model, label: model }))}
          onChange={(model) => change({ model })}
        />
      </Field>
    </div>
  )
}

function ToolsSection({ draft, change }: SectionProps) {
  const { tools, error } = useTools()
  const fixed = tools.filter((tool) => tool.type === 'FIXED').map((tool) => tool.label)
  return (
    <Field
      id="agent-tools"
      label="Tools"
      hint={
        error !== undefined
          ? `Couldn't load the tools. ${errorMessage(error)}`
          : fixed.length > 0
            ? `Every agent also has: ${fixed.join(', ')}.`
            : undefined
      }
    >
      <Select
        id="agent-tools"
        mode="multiple"
        virtual={false}
        maxCount={LIMITS.listEntries}
        value={draft.allowedTools}
        placeholder="No optional tools"
        onChange={(allowedTools) => change({ allowedTools })}
        options={tools
          .filter((tool) => tool.type === 'OPTIONAL')
          .map((tool) => ({ value: tool.name, label: tool.label, description: tool.description }))}
        optionRender={(option) => (
          <span>
            <span className="block">{option.data.label}</span>
            <span className="block text-xs whitespace-normal text-muted">
              {option.data.description}
            </span>
          </span>
        )}
        tagRender={({ label, onClose }) => (
          <ClosableTag kind="tool" label={label} onClose={onClose} />
        )}
      />
    </Field>
  )
}

function KnowledgeSection({ draft, change }: SectionProps) {
  const { knowledgeBases } = useKnowledgeBases()
  return (
    <Field
      id="agent-knowledge"
      label="Knowledge bases"
      hint="The agent searches these with knowledge search."
    >
      <Select
        id="agent-knowledge"
        mode="multiple"
        virtual={false}
        maxCount={LIMITS.listEntries}
        value={draft.allowedKbs}
        placeholder="None"
        optionFilterProp="label"
        onChange={(allowedKbs) => change({ allowedKbs })}
        options={knowledgeBases.map((kb) => ({ value: kb.id, label: kb.name }))}
        tagRender={({ label, onClose }) => (
          <ClosableTag kind="knowledge" label={label} onClose={onClose} />
        )}
      />
    </Field>
  )
}

function ClosableTag({
  kind,
  label,
  onClose,
}: {
  kind: 'tool' | 'knowledge'
  label: ReactNode
  onClose: () => void
}) {
  return (
    <span className="my-0.5 me-1 inline-flex items-center gap-1">
      <Tag kind={kind}>{label}</Tag>
      <button
        type="button"
        aria-label={`Remove ${typeof label === 'string' ? label : 'item'}`}
        onMouseDown={(e) => e.preventDefault()}
        onClick={onClose}
        className="cursor-pointer text-xs text-muted hover:text-ink"
      >
        ×
      </button>
    </span>
  )
}

function OptionsSection({ draft, change }: SectionProps) {
  const options = draft.chatOptions
  const set = (patch: Partial<AgentDraft['chatOptions']>) =>
    change({ chatOptions: { ...options, ...patch } })
  return (
    <div className="flex max-w-115 flex-col gap-5">
      <SliderField
        label="Remembered messages"
        hint="How many earlier messages the agent reads."
        value={options.messageLength}
        shown={String(options.messageLength)}
        range={LIMITS.messageLength}
        onChange={(messageLength) => set({ messageLength })}
      />
      <SliderField
        label="Temperature"
        value={options.temperature}
        shown={options.temperature.toFixed(1)}
        range={LIMITS.temperature}
        onChange={(temperature) => set({ temperature })}
      />
      <SliderField
        label="Top P"
        value={options.topP}
        shown={options.topP.toFixed(2)}
        range={LIMITS.topP}
        onChange={(topP) => set({ topP })}
      />
      <p className="m-0 text-xs text-muted">
        Temperature and top P are saved but not yet used when the agent runs.
      </p>
    </div>
  )
}

function SliderField({
  label,
  hint,
  value,
  shown,
  range,
  onChange,
}: {
  label: string
  hint?: string
  value: number
  shown: string
  range: { min: number; max: number; step: number }
  onChange: (value: number) => void
}) {
  return (
    <div>
      <div className="flex justify-between">
        <span className="font-semibold">{label}</span>
        <span>{shown}</span>
      </div>
      <Slider
        {...range}
        value={value}
        onChange={onChange}
        ariaLabelForHandle={label}
        tooltip={{ open: false }}
      />
      {hint && <span className="text-xs text-muted">{hint}</span>}
    </div>
  )
}
