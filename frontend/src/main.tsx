import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import App from './App'
// Self-hosted brand face. Imported before the stylesheet so Tailwind's
// --font-sans resolves against a family that is already declared.
import '@fontsource-variable/manrope'
// The data face: account numbers, references and columns of amounts. Three
// weights because the statement uses 400 for a reference, 500 for an amount and
// 600 for a day total, and nothing else.
import '@fontsource/ibm-plex-mono/400.css'
import '@fontsource/ibm-plex-mono/500.css'
import '@fontsource/ibm-plex-mono/600.css'
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
