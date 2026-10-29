import { isSecurityMeta } from './api.ts'

// An HTML SPA fallback or a JSON error is not proof that the API exists.
export async function backendAvailable(signal: AbortSignal): Promise<boolean> {
  try {
    const response = await fetch('/api/v1/security/meta', {
      headers: { Accept: 'application/json' }, cache: 'no-store', signal,
    })
    return response.ok && response.headers.get('content-type')?.includes('application/json') === true
      && isSecurityMeta(await response.json())
  } catch { return false }
}
