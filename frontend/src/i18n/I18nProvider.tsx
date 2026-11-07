import { useCallback, useEffect, useMemo, useState, type ReactNode } from 'react'
import { setDisplayLocale } from '../catalog'
import { ar } from './ar'
import { I18nContext, setPreferredCurrency, type Language, type Preferences, type Translate } from './context'
import { de } from './de'
import { en, type Messages } from './en'
import { fr } from './fr'

const MESSAGES: Record<Language, Messages> = { en, de, fr, ar }
/** Number and date format for each language. Arabic uses Tunisian formats, with Western digits. */
const LOCALES: Record<Language, string> = { en: 'en-GB', de: 'de-DE', fr: 'fr-FR', ar: 'ar-TN' }
const STORAGE_KEY = 'nexus.preferences'

function load(): Preferences {
  // English unless the person picks another language in Settings: never the browser's language,
  // so dates and month names always match the language of the screens.
  const fallback: Preferences = { language: 'en', theme: 'system', currency: '' }
  try {
    const saved = JSON.parse(localStorage.getItem(STORAGE_KEY) ?? 'null') as Partial<Preferences> | null
    if (!saved) return fallback
    return {
      language: saved.language && saved.language in MESSAGES ? saved.language : fallback.language,
      theme: saved.theme === 'light' || saved.theme === 'dark' ? saved.theme : 'system',
      currency: typeof saved.currency === 'string' ? saved.currency : '',
    }
  } catch {
    return fallback
  }
}

/** Language, theme and default currency for everything below it. */
export function I18nProvider({ children }: { children: ReactNode }) {
  const [preferences, setState] = useState<Preferences>(() => {
    const loaded = load()
    setDisplayLocale(LOCALES[loaded.language]) // before the first render, so amounts are right at once
    setPreferredCurrency(loaded.currency)
    return loaded
  })

  const setPreferences = useCallback((change: Partial<Preferences>) => {
    setState((current) => {
      const next = { ...current, ...change }
      setDisplayLocale(LOCALES[next.language])
      setPreferredCurrency(next.currency)
      try {
        localStorage.setItem(STORAGE_KEY, JSON.stringify(next))
      } catch {
        // Storage blocked: the choice still applies until the tab closes.
      }
      return next
    })
  }, [])

  useEffect(() => {
    const root = document.documentElement
    root.lang = preferences.language
    root.dir = preferences.language === 'ar' ? 'rtl' : 'ltr'
    if (preferences.theme === 'system') root.removeAttribute('data-theme')
    else root.dataset.theme = preferences.theme
  }, [preferences])

  const t = useCallback<Translate>(
    (key, values) => {
      let text: string = MESSAGES[preferences.language][key] ?? en[key]
      if (values) for (const [name, value] of Object.entries(values)) text = text.replaceAll(`{${name}}`, String(value))
      return text
    },
    [preferences.language],
  )

  const value = useMemo(() => ({ preferences, setPreferences, t }), [preferences, setPreferences, t])
  return <I18nContext.Provider value={value}>{children}</I18nContext.Provider>
}
