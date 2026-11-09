import { useEffect, useId, useRef, useState, type KeyboardEvent } from 'react'
import { useT } from '../i18n/context'
import {
  addDays,
  addMonths,
  dayCount,
  formatDayLong,
  formatMonth,
  formatRange,
  monthGrid,
  PRESETS,
  presetLabel,
  presetRange,
  todayIso,
  weekdayNames,
  type Period,
  type Range,
} from '../period'

type Props = { value: Period; onChange: (period: Period) => void; allowCompare?: boolean }

/** One button showing the chosen period; it opens a panel with months, quick ranges and a calendar. */
export function PeriodPicker({ value, onChange, allowCompare = true }: Props) {
  const t = useT()
  const [open, setOpen] = useState(false)
  const button = useRef<HTMLButtonElement>(null)
  const panelId = useId()

  const label = value.preset ? t(presetLabel(value.preset)) : t('period.custom')
  const dates = value.preset === 'all' ? '' : formatRange(value)

  function close() {
    setOpen(false)
    button.current?.focus()
  }

  return (
    <div className="period-picker">
      <button
        ref={button}
        type="button"
        className="secondary period-button"
        aria-haspopup="dialog"
        aria-expanded={open}
        aria-controls={open ? panelId : undefined}
        onClick={() => setOpen((o) => !o)}
      >
        <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" aria-hidden="true">
          <rect x="3" y="5" width="18" height="16" rx="2" />
          <path d="M3 10h18M8 3v4M16 3v4" />
        </svg>
        <span className="strong">{label}</span>
        {dates && value.preset !== 'today' && <span className="muted">· {dates}</span>}
        {value.compare && <span className="badge">±</span>}
        <span aria-hidden="true" className="caret">▾</span>
      </button>
      {open && (
        <PeriodPanel
          allowCompare={allowCompare}
          id={panelId}
          initial={value}
          onCancel={close}
          onApply={(period) => {
            onChange(period)
            close()
          }}
        />
      )}
    </div>
  )
}

type PanelProps = { allowCompare: boolean; id: string; initial: Period; onCancel: () => void; onApply: (p: Period) => void }

function PeriodPanel({ id, initial, onCancel, onApply, allowCompare }: PanelProps) {
  const t = useT()
  const today = todayIso()
  const panel = useRef<HTMLDivElement>(null)
  const [draft, setDraft] = useState<Period>(initial)
  const [view, setView] = useState((initial.to ?? today).slice(0, 7)) // the month the calendar shows
  const [focusDay, setFocusDay] = useState(initial.to ?? today)
  /** The first day picked, while waiting for the last one. */
  const [anchor, setAnchor] = useState<string | null>(null)
  const [hoverDay, setHoverDay] = useState<string | null>(null)
  /** True while the pointer is held down on the calendar. State for drawing, ref for the listeners. */
  const [dragging, setDragging] = useState(false)
  const draggingRef = useRef(false)
  const draggedRef = useRef(false)
  const onCancelRef = useRef(onCancel)
  const weekdays = weekdayNames()
  const helpId = `${id}-help`

  useEffect(() => {
    onCancelRef.current = onCancel
  }, [onCancel])

  // On open: focus the current choice.
  useEffect(() => {
    const node = panel.current
    ;(node?.querySelector<HTMLElement>('[aria-pressed="true"]') ?? node?.querySelector<HTMLElement>('button'))?.focus()
  }, [])

  // Esc or a click outside cancels. Letting go of the pointer ends a drag.
  useEffect(() => {
    const onKey = (e: globalThis.KeyboardEvent) => {
      if (e.key === 'Escape') {
        e.preventDefault()
        onCancelRef.current()
      }
    }
    const onPointer = (e: PointerEvent) => {
      const picker = panel.current?.parentElement
      if (picker && !picker.contains(e.target as Node)) onCancelRef.current()
    }
    const onUp = () => {
      if (!draggingRef.current) return
      draggingRef.current = false
      setDragging(false)
      // A drag picked a whole range; a plain click picked the first day and waits for the last.
      if (draggedRef.current) setAnchor(null)
    }
    document.addEventListener('keydown', onKey)
    document.addEventListener('pointerdown', onPointer)
    document.addEventListener('pointerup', onUp)
    return () => {
      document.removeEventListener('keydown', onKey)
      document.removeEventListener('pointerdown', onPointer)
      document.removeEventListener('pointerup', onUp)
    }
  }, [])

  const setRange = (range: Range, extra: Partial<Period> = {}) =>
    setDraft((d) => ({ ...d, ...range, preset: null, ...extra }))
  const sorted = (a: string, b: string): Range => (a <= b ? { from: a, to: b } : { from: b, to: a })

  function startPick(day: string) {
    if (anchor) {
      setRange(sorted(anchor, day))
      setAnchor(null)
      return false
    }
    setAnchor(day)
    setRange({ from: day, to: day })
    return true
  }

  /** While waiting for the last day, the range follows the pointer as a preview. */
  const shown: Range =
    anchor && hoverDay && !dragging ? sorted(anchor, hoverDay) : { from: draft.from, to: draft.to }

  function moveFocus(day: string) {
    const next = day > today ? today : day
    setFocusDay(next)
    setView(next.slice(0, 7))
    requestAnimationFrame(() => panel.current?.querySelector<HTMLElement>(`[data-day="${next}"]`)?.focus())
  }

  function onGridKey(e: KeyboardEvent) {
    const moves: Record<string, () => string> = {
      ArrowLeft: () => addDays(focusDay, document.dir === 'rtl' ? 1 : -1),
      ArrowRight: () => addDays(focusDay, document.dir === 'rtl' ? -1 : 1),
      ArrowUp: () => addDays(focusDay, -7),
      ArrowDown: () => addDays(focusDay, 7),
      PageUp: () => addMonths(focusDay, -1),
      PageDown: () => addMonths(focusDay, 1),
    }
    const move = moves[e.key]
    if (!move) return
    e.preventDefault()
    moveFocus(move())
  }

  const days = dayCount(draft)
  const words =
    draft.preset === 'all' || !draft.from
      ? t('period.allWords')
      : `${formatRange(draft, 'long')} (${days === 1 ? t('period.oneDay') : t('period.days', { n: days ?? 0 })})`
  const canCompare = Boolean(draft.from)
  const nextMonthDisabled = view >= today.slice(0, 7)

  return (
    <div ref={panel} id={id} className="period-panel card" role="dialog" aria-label={t('period.dialog')}>
      <div className="period-body">
        <div className="period-presets" role="group" aria-label={t('period.presets')}>
          {PRESETS.map((preset) => (
            <button
              key={preset}
              type="button"
              aria-pressed={draft.preset === preset}
              onClick={() => {
                const range = presetRange(preset, today)
                setDraft((d) => ({ ...d, ...range, preset, compare: range.from ? d.compare : false }))
                setAnchor(null)
                if (range.to) {
                  setView(range.to.slice(0, 7))
                  setFocusDay(range.to)
                }
              }}
            >
              {t(presetLabel(preset))}
            </button>
          ))}
        </div>

        <div className="period-main">
          <div className="calendar">
            <div className="calendar-head">
              <button
                type="button"
                className="secondary icon-btn"
                aria-label={t('period.prevMonth')}
                onClick={() => setView(addMonths(`${view}-01`, -1).slice(0, 7))}
              >
                <span aria-hidden="true">‹</span>
              </button>
              <strong aria-live="polite">{formatMonth(view)}</strong>
              <button
                type="button"
                className="secondary icon-btn"
                aria-label={t('period.nextMonth')}
                disabled={nextMonthDisabled}
                onClick={() => setView(addMonths(`${view}-01`, 1).slice(0, 7))}
              >
                <span aria-hidden="true">›</span>
              </button>
            </div>
            <p id={helpId} className="sr-only">
              {t('period.calendarHelp')}
            </p>
            <table className="calendar-grid" role="grid" aria-label={formatMonth(view)} aria-describedby={helpId} onKeyDown={onGridKey}>
              <thead>
                <tr>
                  {weekdays.map((w) => (
                    <th key={w.long} scope="col" abbr={w.long}>
                      {w.short}
                    </th>
                  ))}
                </tr>
              </thead>
              <tbody onPointerLeave={() => setHoverDay(null)}>
                {monthGrid(view).map((week, i) => (
                  <tr key={i}>
                    {week.map((day, j) => {
                      if (!day) return <td key={j} />
                      const future = day > today
                      const inside = shown.from !== null && shown.to !== null && day >= shown.from && day <= shown.to
                      const edge = day === shown.from || day === shown.to
                      const classes = ['day', inside && 'in-range', edge && 'edge', day === today && 'today'].filter(Boolean).join(' ')
                      return (
                        <td
                          key={day}
                          role="gridcell"
                          aria-selected={inside}
                          className={[day === shown.from && 'range-start', day === shown.to && 'range-end'].filter(Boolean).join(' ') || undefined}
                        >
                          <button
                            type="button"
                            className={classes}
                            data-day={day}
                            tabIndex={day === focusDay ? 0 : -1}
                            aria-label={formatDayLong(day)}
                            aria-disabled={future || undefined}
                            onFocus={() => setFocusDay(day)}
                            onPointerDown={(e) => {
                              if (future || e.button !== 0) return
                              e.preventDefault()
                              ;(e.currentTarget as HTMLElement).releasePointerCapture?.(e.pointerId) // let other days get the drag
                              setFocusDay(day)
                              const started = startPick(day)
                              draggingRef.current = started
                              draggedRef.current = false
                              setDragging(started)
                            }}
                            onPointerEnter={() => {
                              if (future) return
                              if (dragging && anchor) {
                                if (day !== anchor) draggedRef.current = true
                                setRange(sorted(anchor, day))
                              } else setHoverDay(day)
                            }}
                            onClick={(e) => {
                              // Mouse and touch are handled on pointer down; this is Enter or Space.
                              if (e.detail !== 0 || future) return
                              startPick(day)
                            }}
                          >
                            {Number(day.slice(8))}
                          </button>
                        </td>
                      )
                    })}
                  </tr>
                ))}
              </tbody>
            </table>
            {anchor && !dragging && (
              <p className="muted small" role="status">
                {t('period.pickEnd')}
              </p>
            )}
          </div>
        </div>
      </div>

      <div className="period-foot">
        {allowCompare && (
        <label className="choice-inline">
          <input
            type="checkbox"
            className="check"
            checked={draft.compare && canCompare}
            disabled={!canCompare}
            onChange={(e) => setDraft((d) => ({ ...d, compare: e.target.checked }))}
          />
          <span>
            {t('period.compare')}
            {!canCompare && <span className="muted small"> · {t('period.noCompare')}</span>}
          </span>
        </label>
        )}
        <p className="period-words" aria-live="polite">
          {words}
        </p>
        <div className="actions">
          <button type="button" className="secondary" onClick={onCancel}>
            {t('period.cancel')}
          </button>
          <button
            type="button"
            onClick={() => onApply({ ...draft, compare: draft.compare && canCompare })}
          >
            {t('period.apply')}
          </button>
        </div>
      </div>
    </div>
  )
}
