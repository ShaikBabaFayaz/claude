import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, type RenderResult } from '@testing-library/react'
import type { ReactElement } from 'react'
import { createMemoryRouter, MemoryRouter, RouterProvider } from 'react-router'
import { routes } from '../app/routes'
import { NotificationProvider } from '../components/NotificationProvider'
import { createQueryClient } from '../lib/queryClient'

interface TestQueryClientOptions {
  /**
   * Use the application's retry policy (`lib/queryClient.ts`, DR-19: one retry for
   * BACKEND_UNAVAILABLE/5xx only) with no back-off delay. Off by default so ordinary
   * tests fail fast; turn it on where retry behaviour matters.
   */
  appRetryPolicy?: boolean
}

/** A fresh client per test so cache state never leaks between tests. */
export function createTestQueryClient({ appRetryPolicy = false }: TestQueryClientOptions = {}): QueryClient {
  if (appRetryPolicy) {
    const client = createQueryClient({ retryDelay: 0 })
    const defaults = client.getDefaultOptions()
    client.setDefaultOptions({ ...defaults, queries: { ...defaults.queries, gcTime: Infinity } })
    return client
  }
  return new QueryClient({
    defaultOptions: {
      queries: { retry: false, gcTime: Infinity },
      mutations: { retry: false },
    },
  })
}

interface RenderOptions {
  /** Initial URL for the in-memory router, e.g. `/books?page=2`. */
  route?: string
  queryClient?: QueryClient
}

export interface RenderWithProvidersResult extends RenderResult {
  queryClient: QueryClient
}

/** Renders a component inside the query client, notification provider and an in-memory router. */
export function renderWithProviders(
  ui: ReactElement,
  { route = '/', queryClient = createTestQueryClient() }: RenderOptions = {},
): RenderWithProvidersResult {
  const result = render(
    <QueryClientProvider client={queryClient}>
      <NotificationProvider>
        <MemoryRouter initialEntries={[route]}>{ui}</MemoryRouter>
      </NotificationProvider>
    </QueryClientProvider>,
  )
  return { ...result, queryClient }
}

/** Renders the whole route table (layout + pages) at `route`, as the real app does. */
export function renderApp({
  route = '/books',
  queryClient = createTestQueryClient(),
}: RenderOptions = {}): RenderWithProvidersResult & { router: ReturnType<typeof createMemoryRouter> } {
  const router = createMemoryRouter(routes, { initialEntries: [route] })
  const result = render(
    <QueryClientProvider client={queryClient}>
      <NotificationProvider>
        <RouterProvider router={router} />
      </NotificationProvider>
    </QueryClientProvider>,
  )
  return { ...result, queryClient, router }
}
