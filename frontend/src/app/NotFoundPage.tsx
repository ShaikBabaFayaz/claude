import { Link } from 'react-router'
import { usePageTitle } from '../lib/usePageTitle'

/** Unknown route: friendly message with a link back to Books (architecture 9.2, AC-X.3). */
export function NotFoundPage() {
  usePageTitle('Page not found')
  return (
    <>
      <h1 tabIndex={-1}>Page not found</h1>
      <p>The page you are looking for does not exist.</p>
      <p>
        <Link to="/books">Back to Books</Link>
      </p>
    </>
  )
}
