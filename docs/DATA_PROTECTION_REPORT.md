# Scientific data protection report

**Safety gate:** 2026-10-24, before moving protected files out of the Git worktree.
**Result:** PASS. The source byte trees were moved intact after the gate; no source file was deleted or rewritten.

## Protected location and boundary

The project reads source material from `../VIRIDEXA-protected/data` by default, configurable with `VIRIDEXA_DATA_DIR`. The former `database/` tree is retained at `../VIRIDEXA-protected/database` as an archive. Both paths are outside the VIRIDEXA Git worktree.

Before the move, the complete inventory was recomputed and every file size and SHA-256 matched the then-current asset manifest. The check included original archives, source documents, extracted JSON/CSV, database backups, image files, PDF page renders, crop mappings, and evidence screenshots. It verified:

- 3 archived SQL gzip backups pass decompression checks.
- The current 2026-10-24 consistent database snapshot has SHA-256 `367f37bb5c77399141786f0b47d153e70a232c3cce4d9c3073c4bad150ec6063` and passes gzip validation. A copy is preserved as `data/backups/viridexa-live-snapshot-20261024.sql.gz` outside Git.
- The snapshot had already been restored successfully to an isolated MySQL instance; a real plant record was read through the API from that restored copy.
- The protected course PDF and the Downloads original have matching SHA-256 `669e9adbfbdfb855d196e5389b2b8aee763a226fee0ce7f0a7e83bc5bab73685`.
- 231 image files, 140 files under extracted PDF evidence, and all 45 crop references in the image manifest resolve to files.
- The source snapshot and protected input directories are not Docker volumes. The normal application profile mounts no research input. The opt-in snapshot-ingestion profile mounts only the verified SQL snapshot as read-only into a temporary source container; the target's only named volume contains the rebuildable VIRIDEXA development database.

`git clean -ndx -- data database` was used as a dry run only and showed that an ignored in-worktree data directory would be removed. No destructive clean was run. After the safety gate passed, both trees were moved to the external protected location. The regenerated `ASSET_MANIFEST.md` now records 292 protected files totaling 222,484,424 bytes, including the preserved copy of the verified current snapshot. A later `git clean` or Docker volume removal cannot reach those sibling directories.

## Source database observations

The source MySQL service was queried in read-only transactions. It had 36 tables: 150 plant records (117 marked `VERIFIED`, 33 `NEEDS_REVIEW`), 116 oil samples, 88 verified oil measurements, 27 review-pending oil measurements, 1 missing/unreadable oil value, 295 review-pending fatty-acid candidates, 1,180 trait values, 1,003 review-pending knowledge entries, 2,791 review events, 6 audit logs, 3 external candidates, and no users.

The snapshot restore exposed a quality issue in the 117 public plant rows: all have scientific-name state `NEEDS_REVIEW`, their Chinese-name field duplicates the scientific name, and row-level source citations are absent. Those states are preserved as observations, not treated as accepted identity or fabricated citation.

The historical event records include nullable actors. VIRIDEXA's review model keeps both a nullable new-user reference and the historical actor reference; it does not create a reviewer identity for a NULL actor.

## Clean-slate handling

The old application source was not present in the project workspace. No JSP, Servlet, DAO, service, page, stylesheet, or API implementation was copied. The old schema and SQL files are archived outside the repository and are not mounted by the VIRIDEXA application. The new API connects only to the separately migrated `viridexa` schema. Historical snapshots remain available to an explicit data-ingestion adapter; they are not a runtime compatibility database.

Original files remain immutable inputs. `ASSET_MANIFEST.md` can be regenerated with `scripts/build_asset_manifest.py`. Derived import reports and SQL are written under the external data directory's `processed/` folder and are excluded from the source inventory.
