import { useEffect, useRef, useState, type KeyboardEvent, type PointerEvent } from 'react'
import { formatMoney, getDisplayLocale } from '../catalog'
import { useT } from '../i18n/context'
import type { Money, TrendMonth } from '../api'
import { formatMonth } from '../period'

const HEIGHT = 280
const MARGIN = { top: 16, bottom: 28, left: 60 }
const WIDE_RIGHT = 84 // room for the direct labels
const NARROW_RIGHT = 14
const WIDE_ENOUGH = 520

/** Round numbers from below the lowest value to above the highest, always including zero. */
function ticks(values: number[]): number[] {
  const low = Math.min(0, ...values)
  const high = Math.max(0, ...values)
  if (low === high) return [0, 1]
  const rough = (high - low) / 4
  const power = 10 ** Math.floor(Math.log10(rough))
  const step = [1, 2, 5, 10].map((m) => m * power).find((s) => s >= rough) ?? power * 10
  const result: number[] = []
  for (let v = Math.floor(low / step) * step; v < high + step; v += step) {
    result.push(Number(v.toFixed(6)))
    if (v >= high) break
  }
  return result
}

type Series = { key: 'revenue' | 'profit'; label: string; color: string }

/** Revenue and profit per month: two lines on one money axis, with a value readout on hover or arrow keys. */
export function TrendChart({ months }: { months: TrendMonth[] }) {
  const t = useT()
  const box = useRef<HTMLDivElement>(null)
  const [width, setWidth] = useState(640)
  const [active, setActive] = useState<number | null>(null)

  useEffect(() => {
    const element = box.current
    if (!element) return
    const observer = new ResizeObserver(([entry]) => setWidth(entry.contentRect.width))
    observer.observe(element)
    return () => observer.disconnect()
  }, [])

  const series: Series[] = [
    { key: 'revenue', label: t('dash.revenue'), color: 'var(--series-1)' },
    { key: 'profit', label: t('dash.profit'), color: 'var(--series-2)' },
  ]
  const value = (i: number, key: Series['key']) => Number(months[i][key].amount)
  const scale = ticks(months.flatMap((_, i) => series.map((s) => value(i, s.key))))
  const wide = width >= WIDE_ENOUGH
  const right = wide ? WIDE_RIGHT : NARROW_RIGHT
  const plot = Math.max(width - MARGIN.left - right, 1)
  const bottom = HEIGHT - MARGIN.bottom
  const lowest = scale[0]
  const highest = scale[scale.length - 1]
  const x = (i: number) => MARGIN.left + (months.length === 1 ? plot / 2 : (i * plot) / (months.length - 1))
  const y = (v: number) => bottom - ((v - lowest) / (highest - lowest)) * (bottom - MARGIN.top)
  const axis = new Intl.NumberFormat(getDisplayLocale(), { notation: 'compact', maximumFractionDigits: 1 })
  const everyNth = Math.max(1, Math.ceil(months.length / Math.max(Math.floor(plot / 64), 1)))

  // Direct labels at the line ends, pushed apart when the lines finish close together.
  const last = months.length - 1
  const ends = series.map((s) => ({ ...s, y: y(value(last, s.key)) })).sort((a, b) => a.y - b.y)
  if (ends[1].y - ends[0].y < 16) {
    const middle = (ends[0].y + ends[1].y) / 2
    ends[0].y = middle - 8
    ends[1].y = middle + 8
  }

  function pick(event: PointerEvent<SVGRectElement>) {
    const bounds = event.currentTarget.getBoundingClientRect()
    const position = ((event.clientX - bounds.left) / bounds.width) * (months.length - 1)
    setActive(Math.max(0, Math.min(last, Math.round(position))))
  }

  function onKey(event: KeyboardEvent) {
    const step = event.key === 'ArrowRight' ? 1 : event.key === 'ArrowLeft' ? -1 : 0
    if (!step) return
    event.preventDefault()
    setActive((current) => Math.max(0, Math.min(last, (current ?? (step > 0 ? -1 : last + 1)) + step)))
  }

  const row = (money: Money) => formatMoney(money)
  const tipOnLeft = active !== null && x(active) > width / 2

  return (
    <div className="trend" dir="ltr">
      <div className="trend-legend">
        {series.map((s) => (
          <span key={s.key}>
            <i style={{ background: s.color }} aria-hidden="true" />
            {s.label}
          </span>
        ))}
      </div>
      <div
        className="trend-plot"
        ref={box}
        tabIndex={0}
        onKeyDown={onKey}
        onBlur={() => setActive(null)}
        role="img"
        aria-label={t('ana.trend')}
      >
        <svg width={width} height={HEIGHT} aria-hidden="true">
          {scale.map((tick) => (
            <g key={tick}>
              <line
                className={tick === 0 ? 'trend-zero' : 'trend-grid'}
                x1={MARGIN.left}
                x2={MARGIN.left + plot}
                y1={y(tick)}
                y2={y(tick)}
              />
              <text className="trend-tick" x={MARGIN.left - 8} y={y(tick)} dy="0.32em" textAnchor="end">
                {axis.format(tick)}
              </text>
            </g>
          ))}
          {months.map((m, i) =>
            i % everyNth === 0 ? (
              <text key={m.month} className="trend-tick" x={x(i)} y={HEIGHT - 8} textAnchor="middle">
                {formatMonth(m.month, 'short')}
              </text>
            ) : null,
          )}
          {active !== null && (
            <line className="trend-cross" x1={x(active)} x2={x(active)} y1={MARGIN.top} y2={bottom} />
          )}
          {series.map((s) => (
            <g key={s.key}>
              <path
                className="trend-line"
                stroke={s.color}
                d={months.map((_, i) => `${i ? 'L' : 'M'}${x(i)},${y(value(i, s.key))}`).join('')}
              />
              {(months.length <= 12 ? months.map((_, i) => i) : active !== null ? [active] : [last]).map((i) => (
                <circle key={i} className="trend-dot" cx={x(i)} cy={y(value(i, s.key))} r={4} fill={s.color} />
              ))}
            </g>
          ))}
          {wide &&
            ends.map((end) => (
              <text key={end.key} className="trend-end" x={MARGIN.left + plot + 10} y={end.y} dy="0.32em">
                {end.label}
              </text>
            ))}
          <rect
            className="trend-hit"
            x={MARGIN.left}
            y={MARGIN.top}
            width={plot}
            height={bottom - MARGIN.top}
            onPointerMove={pick}
            onPointerDown={pick}
            onPointerLeave={() => setActive(null)}
          />
        </svg>
        {active !== null && (
          <div
            className="trend-tip"
            style={tipOnLeft ? { right: width - x(active) + 12 } : { left: x(active) + 12 }}
          >
            <span className="muted small">{formatMonth(months[active].month)}</span>
            {series.map((s) => (
              <span className="trend-tip-row" key={s.key}>
                <i style={{ background: s.color }} aria-hidden="true" />
                <strong>{row(months[active][s.key])}</strong>
                <span className="muted small">{s.label}</span>
              </span>
            ))}
          </div>
        )}
      </div>
    </div>
  )
}
