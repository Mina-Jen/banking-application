import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import App from './App'
import './index.css'

const container = document.getElementById('root')
if (!container) {
  throw new Error('Missing #root element in index.html')
}

// StrictMode double-invokes effects in development, which is how the account
// loader's cleanup guard gets exercised on every run.
createRoot(container).render(
  <StrictMode>
    <App />
  </StrictMode>,
)
