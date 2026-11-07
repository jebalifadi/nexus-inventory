// Date ranges for the period picker. Days are local ISO dates (YYYY-MM-DD), compared as text.
import { getDisplayLocale } from './catalog'
import type { MessageKey } from './i18n/en'

/** Both ends included. `from` and `to` are null for "all time". */
export type Range = { from: string | null; to: string | null }
export type PresetId =
  | 'thisMonth' | 'lastMonth' | 'today' | 'last7' | 'last30'
  | 'last3m' | 'last6m' | 'thisYear' | 'all'
/** What the person chose: a preset, or a custom range dragged on the calendar (preset null). */
export type Period = Range & { preset: PresetId | null; compare: boolean }

/** In the order they are offered. */
export const PRESETS: PresetId[] = ['thisMonth', 'lastMonth', 'today', 'last7', 'last30', 'last3m', 'last6m', 'thisYear', 'all']
export const presetLabel = (id: PresetId): MessageKey => `period.${id}`

const toDate = (iso: string) => new Date(`${iso}T00:00:00`)
export const toIso = (date: Date) => date.toLocaleDateString('sv')
export const todayIso = () => toIso(new Date())

export function addDays(iso: string, days: number): string {
  const d = toDate(iso)
  d.setDate(d.getDate() + days)
  return toIso(d)
}

/** Same day n months later, kept inside the month (31 Mar − 1 month = 28/29 Feb). */
export function addMonths(iso: string, months: number): string {
  const d = toDate(iso)
  const day = d.getDate()
  d.setDate(1)
  d.setMonth(d.getMonth() + months)
  const last = new Date(d.getFullYear(), d.getMonth() + 1, 0).getDate()
  d.setDate(Math.min(day, last))
  return toIso(d)
}

export const startOfMonth = (iso: string) => `${iso.slice(0, 7)}-01`
export const endOfMonth = (iso: string) => toIso(new Date(toDate(iso).getFullYear(), toDate(iso).getMonth() + 1, 0))
const minIso = (a: string, b: string) => (a < b ? a : b)

export function presetRange(id: PresetId, today = todayIso()): Range {
  switch (id) {
    case 'today':
      return { from: today, to: today }
    case 'lastMonth': {
      const first = addMonths(startOfMonth(today), -1)
      return { from: first, to: endOfMonth(first) }
    }
    case 'last7':
      return { from: addDays(today, -6), to: today }
    case 'last30':
      return { from: addDays(today, -29), to: today }
    case 'thisMonth':
      return { from: startOfMonth(today), to: today }
    case 'last3m':
      return { from: addDays(addMonths(today, -3), 1), to: today }
    case 'last6m':
      return { from: addDays(addMonths(today, -6), 1), to: today }
    case 'thisYear':
      return { from: `${today.slice(0, 4)}-01-01`, to: today }
    case 'all':
      return { from: null, to: null }
  }
}

export function dayCount(range: Range): number | null {
  if (!range.from || !range.to) return null
  return Math.round((toDate(range.to).getTime() - toDate(range.from).getTime()) / 86_400_000) + 1
}

/**
 * The period to compare with; null for all time.
 * - This month / Last month (or a custom range that is one whole month): the same days of the
 *   month before, so 1–15 Nov compares with 1–15 Oct, and all of October with all of September.
 * - This year: the same dates last year, so 1 Jan–15 Nov 2026 compares with 1 Jan–15 Nov 2025.
 * - Anything else (Today, Last 7 days…): the same number of days right before.
 */
export function previousRange(period: Range & { preset?: PresetId | null }): Range | null {
  const { from, to, preset } = period
  const days = dayCount(period)
  if (!days || !from || !to) return null
  const wholeMonth = preset == null && from.endsWith('-01') && to === endOfMonth(from)
  if (preset === 'thisMonth' || preset === 'lastMonth' || wholeMonth) {
    const start = addMonths(from, -1)
    const end = to === endOfMonth(to) ? endOfMonth(start) : minIso(addMonths(to, -1), endOfMonth(start))
    return { from: start, to: end }
  }
  if (preset === 'thisYear') return { from: addMonths(from, -12), to: addMonths(to, -12) }
  return { from: addDays(from, -days), to: addDays(from, -1) }
}

export const inRange = (iso: string, range: Range) => (!range.from || iso >= range.from) && (!range.to || iso <= range.to)

/** Always starts on This month: the 1st of the current month until today. */
export const defaultPeriod = (): Period => ({ ...presetRange('thisMonth'), preset: 'thisMonth', compare: false })

// --- Words, in the chosen language ---

export function formatMonth(month: string, style: 'long' | 'short' = 'long'): string {
  return toDate(`${month}-01`).toLocaleDateString(getDisplayLocale(), { month: style, year: 'numeric' })
}

/** "9 Oct – 7 Nov 2026", or one date when both ends are the same day. */
export function formatRange(range: Range, style: 'short' | 'long' = 'short'): string {
  if (!range.from || !range.to) return ''
  const options: Intl.DateTimeFormatOptions = { day: 'numeric', month: style, year: 'numeric' }
  const format = new Intl.DateTimeFormat(getDisplayLocale(), options)
  if (range.from === range.to) return format.format(toDate(range.from))
  return format.formatRange(toDate(range.from), toDate(range.to))
}

/** Monday-first weeks for the month that holds `month` (YYYY-MM); null pads days of other months. */
export function monthGrid(month: string): (string | null)[][] {
  const first = toDate(`${month}-01`)
  const pad = (first.getDay() + 6) % 7 // Monday = 0
  const days = new Date(first.getFullYear(), first.getMonth() + 1, 0).getDate()
  const cells: (string | null)[] = [
    ...Array<null>(pad).fill(null),
    ...Array.from({ length: days }, (_, i) => `${month}-${String(i + 1).padStart(2, '0')}`),
  ]
  while (cells.length % 7) cells.push(null)
  return Array.from({ length: cells.length / 7 }, (_, w) => cells.slice(w * 7, w * 7 + 7))
}

/** Short weekday names, Monday first: "Mo Di Mi…" in German. */
export function weekdayNames(): { short: string; long: string }[] {
  const monday = toDate('2024-01-01') // a Monday
  return Array.from({ length: 7 }, (_, i) => {
    const d = new Date(monday)
    d.setDate(d.getDate() + i)
    return {
      short: d.toLocaleDateString(getDisplayLocale(), { weekday: 'narrow' }),
      long: d.toLocaleDateString(getDisplayLocale(), { weekday: 'long' }),
    }
  })
}

export const formatDayLong = (iso: string) =>
  toDate(iso).toLocaleDateString(getDisplayLocale(), { weekday: 'long', day: 'numeric', month: 'long', year: 'numeric' })
