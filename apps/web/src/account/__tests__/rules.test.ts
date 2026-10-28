import test from 'node:test'
import assert from 'node:assert/strict'
import { passwordIssue, safeReturnPath } from '../rules.ts'
test('login returns only to local supported product resources',()=>{assert.equal(safeReturnPath('/plants/2'),'/plants/2');assert.equal(safeReturnPath('/workbench?plantId=2'),'/workbench?plantId=2');assert.equal(safeReturnPath('/review?candidateId=abc'),'/review?candidateId=abc');for(const value of ['https://evil.invalid','//evil.invalid','/\\evil.invalid','/login?redirect=/login','/%2f%2fevil.invalid','/private-admin',null,['/account']])assert.equal(safeReturnPath(value),'/account')})
test('password help uses actual UTF8 limits without silently truncating',()=>{assert.equal(passwordIssue('植序研究'),'');assert.ok(passwordIssue('short'));assert.ok(passwordIssue('植'.repeat(25)));assert.ok(passwordIssue('valid-password\n'));assert.equal(passwordIssue('a'.repeat(72)),'')})
