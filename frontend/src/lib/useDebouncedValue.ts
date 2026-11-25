import { useEffect, useState } from 'react'

/** Delay of the typing pause before a search is sent (AC-2.FE.2). */
export const DEBOUNCE_MS = 300

/** Returns `value` only after it has stopped changing for `delayMs`. */
export function useDebouncedValue<T>(value: T, delayMs: number = DEBOUNCE_MS): T {
  const [debounced, setDebounced] = useState(value)

  useEffect(() => {
    const timer = setTimeout(() => setDebounced(value), delayMs)
    return () => clearTimeout(timer)
  }, [value, delayMs])

  return debounced
}
