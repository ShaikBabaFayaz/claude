import { Link } from 'react-router'
import type { BookResponse } from '../../api/types'
import { AvailabilityBadge } from './AvailabilityBadge'
import styles from './BookTable.module.css'

interface BookTableProps {
  books: BookResponse[]
  onDelete: (book: BookResponse) => void
}

/**
 * Catalogue table (AC-1.FE.1, AC-2.FE.3): title, author, ISBN, total and available copies, an
 * availability badge, plus Edit and Delete. Book data is always rendered as text, never as HTML (NFR-6). The row actions carry the
 * book title in their accessible name so a screen reader can tell them apart.
 */
export function BookTable({ books, onDelete }: BookTableProps) {
  return (
    <table className="table">
      <caption>Book catalogue</caption>
      <thead>
        <tr>
          <th scope="col">Title</th>
          <th scope="col">Author</th>
          <th scope="col">ISBN</th>
          <th scope="col" className="numeric">
            Total copies
          </th>
          <th scope="col" className="numeric">
            Available copies
          </th>
          <th scope="col">Availability</th>
          <th scope="col">Actions</th>
        </tr>
      </thead>
      <tbody>
        {books.map((book) => (
          <tr key={book.id}>
            <td className={styles.title}>{book.title}</td>
            <td>{book.author}</td>
            <td className={styles.isbn}>{book.isbn}</td>
            <td className="numeric">{book.totalCopies}</td>
            <td className="numeric">{book.availableCopies}</td>
            <td>
              <AvailabilityBadge availableCopies={book.availableCopies} />
            </td>
            <td>
              <div className={styles.actions}>
                <Link to={`/books/${book.id}/edit`} className="button button--small">
                  Edit<span className="visually-hidden"> {book.title}</span>
                </Link>
                <button type="button" className="button button--small button--danger" onClick={() => onDelete(book)}>
                  Delete<span className="visually-hidden"> {book.title}</span>
                </button>
              </div>
            </td>
          </tr>
        ))}
      </tbody>
    </table>
  )
}
