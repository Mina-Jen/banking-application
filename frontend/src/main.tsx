import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import App from './App'
// Self-hosted brand face. Imported before the stylesheet so Tailwind's
// --font-sans resolves against a family that is already declared.
import '@fontsource-variable/manrope'
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
