import { createBrowserRouter, Navigate, type RouteObject } from 'react-router'
import { BookFormPage } from '../features/books/BookFormPage'
import { BooksPage } from '../features/books/BooksPage'
import { IssueReturnPage } from '../features/loans/IssueReturnPage'
import { OverduePage } from '../features/loans/OverduePage'
import { MemberFormPage } from '../features/members/MemberFormPage'
import { MembersPage } from '../features/members/MembersPage'
import { AppLayout } from './AppLayout'
import { NotFoundPage } from './NotFoundPage'
import { RootErrorElement } from './RouteErrorBoundary'

/**
 * Route table (architecture 9.2). Titles per route are "<heading> - Library" (DR-11).
 */
export const routes: RouteObject[] = [
  {
    path: '/',
    element: <AppLayout />,
    errorElement: <RootErrorElement />,
    children: [
      { index: true, element: <Navigate to="/books" replace /> },
      { path: 'books', element: <BooksPage /> },
      { path: 'books/new', element: <BookFormPage mode="create" /> },
      { path: 'books/:id/edit', element: <BookFormPage mode="edit" /> },
      { path: 'members', element: <MembersPage /> },
      { path: 'members/new', element: <MemberFormPage mode="create" /> },
      { path: 'members/:id/edit', element: <MemberFormPage mode="edit" /> },
      { path: 'loans', element: <IssueReturnPage /> },
      { path: 'loans/overdue', element: <OverduePage /> },
      { path: '*', element: <NotFoundPage /> },
    ],
  },
]

export function createAppRouter() {
  return createBrowserRouter(routes)
}
