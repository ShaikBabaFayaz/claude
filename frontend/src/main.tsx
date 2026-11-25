import { QueryClientProvider } from '@tanstack/react-query'
import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import { RouterProvider } from 'react-router'
import { createAppRouter } from './app/routes'
import { NotificationProvider } from './components/NotificationProvider'
import { createQueryClient } from './lib/queryClient'
import './styles/global.css'

const queryClient = createQueryClient()
const router = createAppRouter()

const container = document.getElementById('root')
if (!container) {
  throw new Error('Root element #root not found in index.html')
}

createRoot(container).render(
  <StrictMode>
    <QueryClientProvider client={queryClient}>
      <NotificationProvider>
        <RouterProvider router={router} />
      </NotificationProvider>
    </QueryClientProvider>
  </StrictMode>,
)
