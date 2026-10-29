import { readdir, lstat, readFile } from 'node:fs/promises'
import { resolve, relative, extname, basename } from 'node:path'
const root = resolve(import.meta.dirname, '../dist')
const allowed = new Set(['.html','.js','.css','.json','.png','.webp','.svg','.ico','.woff','.woff2','.txt'])
let count=0, bytes=0
async function scan(dir) {
  for (const entry of await readdir(dir)) {
    const path=resolve(dir,entry), name=relative(root,path), stat=await lstat(path)
    if (stat.isSymbolicLink()) throw Error(`Symbolic link forbidden: ${name}`)
    if (stat.isDirectory()) { if (/^(data|database|backups|raw|node_modules|\.git)$/i.test(entry)) throw Error(`Private directory: ${name}`); await scan(path); continue }
    if (/^(\.env|\.dev\.vars)/i.test(entry) || (!allowed.has(extname(entry)) && !['.assetsignore','_headers','_redirects'].includes(basename(entry)))) throw Error(`Unexpected asset: ${name}`)
    if (stat.size>25*1024*1024) throw Error(`Asset exceeds 25 MiB: ${name}`)
    if (['.js','.html','.json','.css','.txt'].includes(extname(entry))) {
      const text=await readFile(path,'utf8')
      if (/sk-or-v1-[a-f0-9]{32,}|-----BEGIN (?:RSA |EC |OPENSSH )?PRIVATE KEY-----|AKIA[0-9A-Z]{16}/i.test(text)) throw Error(`Credential pattern in ${name}`)
    }
    count++; bytes+=stat.size
  }
}
await scan(root)
console.log(`Static asset audit passed: ${count} files, ${bytes} bytes; no private input types, symlinks or recognized credential patterns.`)
