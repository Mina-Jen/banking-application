#!/usr/bin/env node
/**
 * Downloads the live OpenAPI document from the running Spring Boot backend and
 * writes it to frontend/openapi.json, which is the committed source of truth
 * for `npm run generate:api`.
 *
 * Usage:
 *   1. Start the backend on 18080.
 *   2. npm run sync:api      (fetches the spec, then regenerates the types)
 *
 * Override the backend address with BACKEND_URL=http://host:port.
 */
import { writeFile } from 'node:fs/promises'
import { dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

const BACKEND = process.env.BACKEND_URL ?? 'http://localhost:18080'
const url = `${BACKEND}/v3/api-docs`
const target = resolve(dirname(fileURLToPath(import.meta.url)), '..', 'openapi.json')

const response = await fetch(url)
if (!response.ok) {
  console.error(`Backend answered ${response.status} at ${url}`)
  console.error('Is the backend running? Start it with: cd backend && ./mvnw spring-boot:run')
  process.exit(1)
}

const spec = await response.json()

// Pretty-print so a changed endpoint shows up as a small, readable git diff.
await writeFile(target, `${JSON.stringify(spec, null, 2)}\n`, 'utf8')

const paths = Object.keys(spec.paths ?? {})
const operations = paths.reduce(
  (total, path) => total + Object.keys(spec.paths[path]).filter((m) => m !== 'parameters').length,
  0,
)
const schemas = Object.keys(spec.components?.schemas ?? {})

console.log(`Saved ${url} -> ${target}`)
console.log(`  ${operations} operations across ${paths.length} paths`)
console.log(`  ${schemas.length} schemas: ${schemas.join(', ')}`)
