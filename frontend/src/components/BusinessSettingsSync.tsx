import { useEffect } from 'react'
import * as api from '../api'
import { useAuth, useIsOwner } from '../auth'
import { useI18n } from '../i18n/context'

/**
 * Brings the business currency from the server into the screens after login. A currency chosen on
 * this computer before settings moved to the server is saved there once, so nobody loses their choice.
 */
export function BusinessSettingsSync() {
  const { session } = useAuth()
  const isOwner = useIsOwner()
  const { preferences, setPreferences } = useI18n()
  const token = session?.token
  const local = preferences.currency

  useEffect(() => {
    if (!token) return
    let alive = true
    api
      .fetchBusinessSettings()
      .then((settings) => {
        if (!alive) return
        if (settings.currency) {
          if (settings.currency !== local) setPreferences({ currency: settings.currency })
        } else if (isOwner && local) {
          return api.saveBusinessSettings({ ...settings, currency: local })
        }
      })
      .catch(() => {
        // The screens keep the last known currency; the next login tries again.
      })
    return () => {
      alive = false
    }
  }, [token, isOwner, local, setPreferences])

  return null
}
