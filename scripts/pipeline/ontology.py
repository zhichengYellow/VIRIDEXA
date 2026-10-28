"""Conservative, versioned projections of scientific candidates.

Vocabulary v1 performs exact lexical lookup only. It preserves source-native
identifiers with their namespace and never infers missing values.
"""
from __future__ import annotations

import argparse
import hashlib
import json
import sys
from collections import Counter
from pathlib import Path

VOCABULARY_VERSION = "lexical-vocabulary-v1"
LEXICAL = {
    "leaf_shape": {"ovate": "OVATE", "elliptic": "ELLIPTIC", "linear": "LINEAR", "lanceolate": "LANCEOLATE", "oblong": "OBLONG"},
    "leaf_margin": {"entire": "ENTIRE", "serrate": "SERRATE", "dentate": "DENTATE", "crenate": "CRENATE", "lobed": "LOBED"},
    "flower_colour": {"white": "WHITE", "yellow": "YELLOW", "red": "RED", "pink": "PINK", "purple": "PURPLE", "blue": "BLUE", "green": "GREEN"},
    "growth_form": {"tree": "TREE", "shrub": "SHRUB", "herb": "HERB", "vine": "VINE", "climber": "CLIMBER", "woody": "WOODY"},
    "fruit_type": {"berry": "BERRY", "drupe": "DRUPE", "capsule": "CAPSULE", "legume": "LEGUME", "achene": "ACHENE", "nut": "NUT", "follicle": "FOLLICLE", "cone": "CONE"},
    "fruit_shape": {"globose": "GLOBOSE", "ovoid": "OVOID", "ellipsoid": "ELLIPSOID", "oblong": "OBLONG"},
}

def _native(namespace, value):
    return {"namespace": namespace, "id": str(value)} if value not in (None, "") and namespace else None

def project(pipeline, normalized, raw=None):
    """Return (projection, state, coverage); source wording is never guessed."""
    n, r = normalized or {}, raw or {}
    if not n:
        return {"source_namespace": None, "source_native_ids": {}, "taxonomy": {},
                "chinese_name": None, "chinese_name_state": "NOT_REPORTED", "traits": {},
                "lipid": {}, "unmapped_raw": r}, "UNMAPPABLE_SOURCE", {"source": False}
    ns = n.get("source_namespace")
    out = {"source_namespace": ns, "source_native_ids": {}, "taxonomy": {}, "chinese_name": None,
           "traits": {}, "lipid": {}}
    for key in ("source_taxon_id", "source_name_id", "source_synonym_id"):
        val = n.get(key)
        if val is not None: out["source_native_ids"][key] = _native(ns, val)
    tax = out["taxonomy"]
    if n.get("accepted_taxon_id") is not None:
        out["source_native_ids"]["accepted_taxon_id"] = _native(ns, n["accepted_taxon_id"])
    for k in ("accepted_name", "synonym_name", "scientific_name", "source_taxonomic_status", "rank"):
        if n.get(k) is not None: tax[k] = n[k]
    tax["parent_native_id"] = _native(ns, n.get("parent_taxon_id"))
    tax["family"] = None; tax["genus"] = None
    classification = n.get("classification")
    if isinstance(classification, list):
        for item in classification:
            if isinstance(item, dict):
                rank = str(item.get("rank", "")).casefold()
                if rank == "family": tax["family"] = item.get("name")
                if rank == "genus": tax["genus"] = item.get("name")
    out["chinese_name"] = n.get("name_zh") or None
    out["chinese_name_state"] = "SOURCE_REPORTED_PENDING" if out["chinese_name"] else "NOT_REPORTED"
    traits = n.get("normalized_traits") or {}
    unknown = 0
    for field, vocab in LEXICAL.items():
        values = traits.get(field)
        if values is None:
            out["traits"][field] = None; continue
        values = values if isinstance(values, list) else [values]
        mapped=[]
        for value in values:
            original = str(value)
            concept = vocab.get(original.casefold())
            mapped.append({"source_term": original, "concept": concept})
            unknown += concept is None
        out["traits"][field] = mapped
    if pipeline == "LIPID" or n.get("kind"):
        out["lipid"] = {"kind": n.get("kind"), "oil_content": n.get("value") if n.get("kind") == "OIL_CONTENT" else None,
                        "unit": n.get("unit"), "basis": n.get("basis"), "analyte": n.get("analyte")}
    out["coverage"] = {"source": True, "accepted_or_synonym_id": bool(out["source_native_ids"]),
                       "parent": tax["parent_native_id"] is not None, "family": tax["family"] is not None,
                       "genus": tax["genus"] is not None, "chinese_name": out["chinese_name"] is not None,
                       "traits_present": any(v is not None for v in out["traits"].values()),
                       "traits_unknown": unknown > 0, "lipid_basis": bool(out["lipid"].get("basis"))}
    state = "PARTIAL_UNKNOWN" if unknown else "SOURCE_PRESENT"
    return out, state, out["coverage"]

def _canonical(value):
    return json.dumps(value, ensure_ascii=False, sort_keys=True, separators=(",", ":"))

def main():
    sys.path.insert(0, str(Path(__file__).resolve().parent))
    from run_science import mysql
    from run_science import fingerprint
    p=argparse.ArgumentParser(description=__doc__); p.add_argument('--apply',action='store_true'); a=p.parse_args()
    # HEX prevents mysql --batch from rewriting JSON backslashes, tabs, or newlines.
    before=fingerprint() if a.apply else None
    rows=mysql("SELECT HEX(CAST(JSON_ARRAY(c.id,c.pipeline,c.normalized_payload,c.raw_payload,s.dataset) AS CHAR)) FROM scientific_candidate c JOIN scientific_source_version s ON s.id=c.source_id ORDER BY c.id;")
    candidates=[]
    for line in rows.splitlines():
        cid, pipe, n, r, dataset=json.loads(bytes.fromhex(line).decode('utf-8')); candidates.append((cid,pipe,n or {},r or {}))
    native={}
    for cid,pipe,n,r in candidates:
        if n.get('source_namespace') and n.get('source_taxon_id'):
            native[(n['source_namespace'],str(n['source_taxon_id']))]=n
    totals=Counter(); insertions=[]; unknown=Counter()
    for cid,pipe,n,r in candidates:
        # Resolve WFO parent chains only from explicit native parent IDs in this source namespace.
        seen=set(); parent=n.get('parent_taxon_id'); lineage=[]
        while parent and n.get('source_namespace'):
            key=(n['source_namespace'],str(parent))
            if key in seen: break
            seen.add(key); ancestor=native.get(key)
            if not ancestor: break
            lineage.append(ancestor); parent=ancestor.get('parent_taxon_id')
        n=dict(n)
        for ancestor in lineage:
            rank=str(ancestor.get('rank','')).casefold()
            if rank in ('family','genus') and ancestor.get('scientific_name'):
                n.setdefault('classification',[]).append({'rank':rank,'name':ancestor['scientific_name']})
        projection,state,cov=project(pipe,n,r); totals[pipe]+=1; totals[state]+=1
        for field,value in cov.items():
            totals['coverage_'+field]+=bool(value)
        if cov.get('traits_unknown'): unknown[pipe]+=1
        body=_canonical(projection); sha=hashlib.sha256(body.encode()).hexdigest()
        insertions.append((cid,body,sha,state))
    table_exists=bool(mysql("SHOW TABLES LIKE 'ontology_projection';").strip())
    if a.apply and not table_exists:
        raise RuntimeError('ontology_projection table is not installed; apply requires the parent migration')
    existing=mysql("SELECT candidate_id,vocabulary_version,projection_sha256,mapping_state FROM ontology_projection;") if table_exists else ''
    existing_map={line.split('\t')[0]:line.split('\t')[1:] for line in existing.splitlines()}
    conflicts=[]; pending=[]
    for item in insertions:
        cid,body,sha,state=item; old=existing_map.get(cid)
        if old and old[1] != sha: conflicts.append(cid)
        elif not old: pending.append(item)
    if a.apply and not conflicts:
        for start in range(0,len(pending),250):
            group=pending[start:start+250]
            statements=[]
            for cid,body,sha,state in group:
                # candidate_id is the 64-character ASCII hex key, not its 32-byte digest.
                cid_hex=cid.encode('ascii').hex()
                statements.append("INSERT INTO ontology_projection(candidate_id,vocabulary_version,projection,projection_sha256,mapping_state) SELECT CONVERT(UNHEX('%s') USING ascii),'%s',CAST(CONVERT(UNHEX('%s') USING utf8mb4) AS JSON),'%s','%s' WHERE NOT EXISTS (SELECT 1 FROM ontology_projection WHERE candidate_id=CONVERT(UNHEX('%s') USING ascii));" % (cid_hex,VOCABULARY_VERSION,body.encode('utf-8').hex(),sha,state,cid_hex))
            try:
                mysql('START TRANSACTION; '+' '.join(statements)+' COMMIT;')
            except Exception as error:
                diagnostic={"batch_start":start,"batch_size":len(group),"committed_previous_batches":start//250,"error":str(error)}
                diag_path=Path(__file__).resolve().parents[2]/'reports'/'phase2b'/'ontology-apply-failure.json'
                diag_path.write_text(json.dumps(diagnostic,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
                raise
        # Verify persisted hashes before reporting a successful apply.
        persisted=mysql("SELECT candidate_id,projection_sha256 FROM ontology_projection;")
        persisted_map=dict(line.split('\t') for line in persisted.splitlines())
        conflicts=[cid for cid,body,sha,state in insertions if persisted_map.get(cid)!=sha]
    after=fingerprint() if a.apply and not conflicts else None
    report={"vocabulary_version":VOCABULARY_VERSION,"mode":"applied" if a.apply else "dry-run","coverage":dict(totals),"unknown_by_pipeline":dict(unknown),"projected_candidates":len(insertions),"pending_inserts":len(pending),"projection_table_available":table_exists,"existing_projection_conflicts":conflicts,"source_review_replay":{"before":before,"after":after,"unchanged":before==after if after is not None else None},"success":not conflicts}
    report_path=Path(__file__).resolve().parents[2]/'reports'/'phase2b'/'ontology-report.json'
    report_path.parent.mkdir(parents=True,exist_ok=True)
    report_path.write_text(json.dumps(report,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
    print(json.dumps(report,ensure_ascii=False,indent=2))

if __name__ == '__main__': main()
