// A server 401 always wins over a stale client profile. Preserve the current research URL.
export function requireLoginOnUnauthorized(status: number) {
  if (status !== 401 || !['/assistant', '/workbench'].includes(window.location.pathname)) return
  const redirect = window.location.pathname + window.location.search + window.location.hash
  window.location.replace(`/login?${new URLSearchParams({ redirect })}`)
}
