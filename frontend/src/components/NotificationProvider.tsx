import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useRef,
  useState,
  type ReactNode,
} from 'react'
import { Notice, type NoticeKind } from './Notice'

export interface NotifyOptions {
  kind?: NoticeKind
  /** Move keyboard focus to the notice, for example after an action removed the focused row (DR-11). */
  focus?: boolean
}

interface ActiveNotice {
  id: number
  kind: NoticeKind
  message: string
  focus: boolean
}

interface NotificationContextValue {
  notice: ActiveNotice | null
  notify: (message: string, options?: NotifyOptions) => void
  dismiss: () => void
}

const NotificationContext = createContext<NotificationContextValue | null>(null)

/** Holds the one visible success/info notice of the application (architecture 9.1). */
export function NotificationProvider({ children }: { children: ReactNode }) {
  const [notice, setNotice] = useState<ActiveNotice | null>(null)
  const nextId = useRef(1)

  const notify = useCallback((message: string, { kind = 'success', focus = false }: NotifyOptions = {}) => {
    setNotice({ id: nextId.current++, kind, message, focus })
  }, [])
  const dismiss = useCallback(() => setNotice(null), [])

  const value = useMemo(() => ({ notice, notify, dismiss }), [notice, notify, dismiss])
  return <NotificationContext.Provider value={value}>{children}</NotificationContext.Provider>
}

export function useNotifications(): NotificationContextValue {
  const context = useContext(NotificationContext)
  if (!context) throw new Error('useNotifications must be used inside <NotificationProvider>')
  return context
}

/**
 * Renders the current notice in a persistent live region. Place it once, inside `<main>`, near the
 * top of the page. The notice stays until it is dismissed or replaced.
 */
export function NotificationRegion() {
  const { notice, dismiss } = useNotifications()
  const regionRef = useRef<HTMLDivElement>(null)

  const noticeId = notice?.id
  const shouldFocus = notice?.focus ?? false
  useEffect(() => {
    if (noticeId !== undefined && shouldFocus) regionRef.current?.focus()
  }, [noticeId, shouldFocus])

  return (
    <Notice ref={regionRef} kind={notice?.kind} onDismiss={dismiss}>
      {notice?.message}
    </Notice>
  )
}
