import { readFileSync } from 'node:fs'
import { dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

/** Shape of `shared-test-vectors/*.json` (format version 1, see that folder's README.md). */
export interface ValidVector {
  input: string
  normalized?: string
  note?: string
}
export interface InvalidVector {
  input: string
  note?: string
}
export interface NotEquivalentVector {
  a: string
  b: string
  note?: string
}
export interface VectorFile {
  formatVersion: number
  valid: ValidVector[]
  invalid: InvalidVector[]
  notEquivalent?: NotEquivalentVector[]
}

/**
 * Loads a shared vector file from the repository-root folder `shared-test-vectors/` (R-4). The
 * files are read from disk (not imported) so Vite's file-system allow list and tsconfig's
 * `resolveJsonModule` are not involved. A missing file fails loudly: it is never replaced by a copy.
 */
export function loadVectors(name: 'isbn' | 'email'): VectorFile {
  // Not `new URL(..., import.meta.url)`: Vite would treat that as an asset import and deny the path.
  const path = resolve(dirname(fileURLToPath(import.meta.url)), '..', '..', '..', 'shared-test-vectors', `${name}.json`)
  let text: string
  try {
    text = readFileSync(path, 'utf8')
  } catch (error) {
    throw new Error(`Shared test vectors not found at ${path}. They are owned by task T-04.`, { cause: error })
  }
  const file = JSON.parse(text) as VectorFile
  if (file.formatVersion !== 1) {
    throw new Error(`Unsupported shared-test-vectors format ${file.formatVersion} in ${name}.json`)
  }
  return file
}
