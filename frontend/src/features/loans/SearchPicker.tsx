import { useId, type ReactNode } from 'react'
import type { UseQueryResult } from '@tanstack/react-query'
import type { PageResponse } from '../../api/types'
import { ErrorState } from '../../components/ErrorState'
import { FormField } from '../../components/FormField'
import { LoadingState } from '../../components/LoadingState'
import styles from './SearchPicker.module.css'

interface SearchPickerProps<T extends { id: number }> {
  /** Text of the search box, e.g. "Find a member". */
  searchLabel: string
  /** Legend of the radio list, e.g. "Matching members". */
  legend: string
  noun: { singular: string; plural: string }
  search: string
  onSearchChange: (value: string) => void
  maxSearchLength: number
  query: UseQueryResult<PageResponse<T>>
  selected: T | null
  onSelect: (item: T) => void
  /** Row content (name, availability badge, ...). Always rendered as text (NFR-6). */
  renderItem: (item: T) => ReactNode
  /** Text echoed after "Selected <noun>: ". */
  selectedText: (item: T) => string
  /** Message under the list when nothing matches. */
  emptyText: string
}

/**
 * Accessible picker (ADR-20, DR-19): a labelled search box plus a `fieldset`/legend radio list, not
 * an ARIA combobox. The first page of matches is shown; when there are more, the live region says
 * "Showing 20 of N - refine your search". The chosen item is echoed as text.
 */
export function SearchPicker<T extends { id: number }>({
  searchLabel,
  legend,
  noun,
  search,
  onSearchChange,
  maxSearchLength,
  query,
  selected,
  onSelect,
  renderItem,
  selectedText,
  emptyText,
}: SearchPickerProps<T>) {
  const groupName = useId()
  const data = query.data

  let countText = ''
  if (data && !query.isError) {
    const shown = data.content.length
    countText =
      data.totalElements > shown
        ? `Showing ${shown} of ${data.totalElements} - refine your search`
        : `${data.totalElements} ${data.totalElements === 1 ? noun.singular : noun.plural} found`
  }

  let list: ReactNode
  if (query.isError) {
    list = <ErrorState error={query.error} onRetry={() => void query.refetch()} retrying={query.isFetching} />
  } else if (!data) {
    list = <LoadingState label={`Loading ${noun.plural}…`} />
  } else if (data.content.length === 0) {
    list = <p className={styles.empty}>{emptyText}</p>
  } else {
    list = (
      <fieldset className={styles.list} aria-busy={query.isPlaceholderData}>
        <legend>{legend}</legend>
        {data.content.map((item) => (
          <label key={item.id} className={styles.option}>
            <input
              type="radio"
              name={groupName}
              checked={selected?.id === item.id}
              onChange={() => onSelect(item)}
            />
            <span className={styles.optionText}>{renderItem(item)}</span>
          </label>
        ))}
      </fieldset>
    )
  }

  return (
    <div className={styles.picker}>
      <FormField label={searchLabel}>
        {(control) => (
          <input
            type="search"
            className="input"
            autoComplete="off"
            maxLength={maxSearchLength}
            value={search}
            onChange={(event) => onSearchChange(event.target.value)}
            {...control}
          />
        )}
      </FormField>
      {/* Always rendered so screen readers announce the new result count (NFR-3). */}
      <p role="status" className={styles.count}>
        {countText}
      </p>
      {list}
      <p className={styles.selected}>
        {selected ? `Selected ${noun.singular}: ${selectedText(selected)}` : `No ${noun.singular} selected.`}
      </p>
    </div>
  )
}
