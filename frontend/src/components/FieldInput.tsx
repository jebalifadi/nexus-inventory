import type { CategoryField } from '../api'

type Props = {
  field: CategoryField
  value: string
  onChange: (value: string) => void
}

/** One input for an owner-defined category field, matching its type. Values are always text. */
export function FieldInput({ field, value, onChange }: Props) {
  const label = (
    <span>
      {field.label}
      {!field.required && <span className="muted"> (optional)</span>}
    </span>
  )

  switch (field.type) {
    case 'CHOICE':
      return (
        <label>
          {label}
          <select value={value} onChange={(e) => onChange(e.target.value)} required={field.required}>
            <option value="">Choose…</option>
            {field.choices.map((choice) => (
              <option key={choice} value={choice}>
                {choice}
              </option>
            ))}
          </select>
        </label>
      )
    case 'BOOLEAN':
      return (
        <label>
          {label}
          <select value={value} onChange={(e) => onChange(e.target.value)} required={field.required}>
            <option value="">Choose…</option>
            <option value="true">Yes</option>
            <option value="false">No</option>
          </select>
        </label>
      )
    default:
      return (
        <label>
          {label}
          <input
            type={field.type === 'NUMBER' ? 'number' : field.type === 'DATE' ? 'date' : 'text'}
            step={field.type === 'NUMBER' ? 'any' : undefined}
            value={value}
            onChange={(e) => onChange(e.target.value)}
            required={field.required}
          />
        </label>
      )
  }
}
