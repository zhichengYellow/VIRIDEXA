/** Only supported local product destinations are accepted after authentication. */
export function safeReturnPath(input: unknown): string {
  if(typeof input !== 'string' || input.length > 2048 || /[\u0000-\u001f\u007f\\]/.test(input) || !input.startsWith('/') || input.startsWith('//')) return '/account'
  try { const url = new URL(input,'https://viridexa.invalid'); if(url.origin !== 'https://viridexa.invalid') return '/account'; if(!/^\/(?:plants\/[1-9][0-9]*|knowledge-graph|workbench|assistant|technology|review|account)?$/.test(url.pathname))return '/account';return url.pathname+url.search+url.hash } catch { return '/account' }
}
export function passwordIssue(value: string): string {
  const length = new TextEncoder().encode(value).length
  if (/[\p{Cc}]/u.test(value)) return '密码不能包含换行或控制字符。'
  if (length < 12) return '请设置至少 12 个 UTF-8 字节的密码，建议使用独立的长密码。'
  if (length > 72) return '密码最多为 72 个 UTF-8 字节；中文通常每字占 3 个字节。'
  return ''
}
