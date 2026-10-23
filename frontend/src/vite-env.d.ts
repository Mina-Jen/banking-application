/// <reference types="vite/client" />

interface ImportMetaEnv {
  /**
   * Base URL of the Spring Boot backend, for example "http://localhost:18080".
   * Leave it unset in development: the Vite dev server proxies /api there, so
   * the browser sees one origin and no CORS configuration is needed.
   */
  readonly VITE_API_BASE_URL?: string
}

interface ImportMeta {
  readonly env: ImportMetaEnv
}
