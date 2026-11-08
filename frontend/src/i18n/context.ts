import { createContext, useContext } from 'react'
import type { MessageKey } from './en'

export type Language = 'en' | 'de' | 'fr' | 'ar'
export type Theme = 'system' | 'light' | 'dark'
/** Per person, per computer: kept in the browser, not on the server. */
export type Preferences = { language: Language; theme: Theme; currency: string }

export const LANGUAGES: { code: Language; name: string }[] = [
  { code: 'en', name: 'English' },
  { code: 'de', name: 'Deutsch' },
  { code: 'fr', name: 'Français' },
  { code: 'ar', name: 'العربية' },
]

export type Translate = (key: MessageKey, values?: Record<string, string | number>) => string

type I18n = {
  preferences: Preferences
  setPreferences: (change: Partial<Preferences>) => void
  t: Translate
}

export const I18nContext = createContext<I18n | null>(null)

export function useI18n(): I18n {
  const value = useContext(I18nContext)
  if (!value) throw new Error('useI18n must be used inside <I18nProvider>')
  return value
}

/** Shortcut for the translate function alone. */
export const useT = (): Translate => useI18n().t

/** Set by the provider whenever the person changes it, so forms can read it without a hook. */
let preferredCurrency = ''

export function setPreferredCurrency(currency: string) {
  preferredCurrency = currency
}

/** The currency forms start with: the person's choice from Settings when available, else EUR, else the first. */
export function defaultCurrency(currencies: string[]): string {
  if (preferredCurrency && currencies.includes(preferredCurrency)) return preferredCurrency
  return currencies.includes('EUR') ? 'EUR' : (currencies[0] ?? '')
}
