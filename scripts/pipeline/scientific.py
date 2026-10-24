#!/usr/bin/env python3
"""Fail-closed, immutable candidate import. Inputs are never modified.
Only explicit source adapters may normalize; all other rows stay held.
"""
import argparse
import csv
import hashlib
import json
from pathlib import Path
from decimal import Decimal, InvalidOperation


def canonical(value):
    return json.dumps(value, ensure_ascii=False, sort_keys=True, separators=(',', ':'))


def digest(value):
    return hashlib.sha256(value.encode()).hexdigest()


def normalize(row, pipeline):
    if pipeline == 'TAXONOMY':
        name = row.get('scientificName')
        if not name:
            return None, 'MISSING_SCIENTIFIC_NAME'
        return {k: row.get(k) for k in ('scientificName', 'acceptedNameUsageID', 'taxonID',
                'parentNameUsageID', 'taxonRank', 'taxonomicStatus', 'vernacularName')}, None
    if pipeline == 'MORPHOLOGY':
        field = row.get('measurementType')
        if field not in ('leaf shape', 'leaf margin', 'flower colour', 'growth form', 'fruit type'):
            return None, 'UNMAPPED_TRAIT'
        value = row.get('measurementValue')
        if not value:
            return None, 'MISSING_VALUE'
        return {'trait': field, 'value': value.strip(), 'rule': 'verbatim-trim-v1',
                'identity': row.get('scientificName'), 'identity_state': 'UNVERIFIED'}, None
    kind = row.get('measurement_kind')
    if kind not in ('OIL_CONTENT', 'OIL_FA_COMPOSITION', 'FAME_COMPOSITION', 'FOOD_NUTRIENT'):
        return None, 'UNKNOWN_MEASUREMENT_BASIS'
    if not row.get('sample_id') or not row.get('basis'):
        return None, 'MISSING_SAMPLE_OR_BASIS'
    unit = row.get('unit')
    if unit not in ('%', 'g/100g', 'mg/100g', 'g/kg'):
        return None, 'UNSUPPORTED_UNIT'
    if kind != 'FOOD_NUTRIENT' and unit != '%':
        return None, 'INCOMPATIBLE_UNIT'
    value = row.get('value')
    try:
        number = None if value in (None, '', 'NA', 'ND') else Decimal(str(value))
        if number is not None and (not number.is_finite() or number < 0 or (unit == '%' and number > 100)):
            return None, 'OUT_OF_RANGE'
    except InvalidOperation:
        return None, 'INVALID_NUMBER'
    return {'sample_id': row['sample_id'], 'kind': kind, 'basis': row['basis'], 'unit': unit,
            'value': None if number is None else str(number), 'analyte': row.get('analyte'),
            'taxon_id': None, 'linkage_state': 'UNLINKED', 'rule': 'decimal-no-conversion-v1'}, None


def records(path):
    with path.open(encoding='utf-8-sig') as f:
        if path.suffix.lower() in ('.csv', '.tsv'):
            yield from csv.DictReader(f, delimiter='\t' if path.suffix == '.tsv' else ',')
        elif path.suffix.lower() == '.jsonl':
            for line in f:
                if line.strip():
                    yield json.loads(line)
        else:
            raise ValueError('UNSUPPORTED_FORMAT')


def scan(root, manifest):
    result = []
    for line in manifest.read_text().splitlines():
        cells = [c.strip() for c in line.split('|')]
        if len(cells) < 6 or not cells[1].startswith(('data/', 'database/')):
            continue
        logical, expected = cells[1], cells[4]
        path = root / logical
        state = 'MISSING'
        if path.is_file():
            with path.open('rb') as source_stream:
                state = 'VERIFIED' if hashlib.file_digest(source_stream, 'sha256').hexdigest() == expected else 'MISMATCH'
        result.append({'path': logical, 'sha256': expected, 'state': state})
    return result


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--root', type=Path, default=Path(__file__).resolve().parents[2].parent / 'VIRIDEXA-protected')
    parser.add_argument('--manifest', type=Path, default=Path('docs/ASSET_MANIFEST.md'))
    parser.add_argument('--report', type=Path, default=Path('reports/phase2/source-audit.json'))
    parser.add_argument('--dry-run', action='store_true')
    args = parser.parse_args()
    inventory = scan(args.root, args.manifest)
    report = {'mode': 'dry-run', 'manifest_entries': len(inventory),
              'actual_files_verified': sum(x['state'] == 'VERIFIED' for x in inventory),
              'operation': 'hash_inventory_only', 'status': 'INVENTORY_VERIFIED' if all(x['state']=='VERIFIED' for x in inventory) else 'SOURCE_INTEGRITY_FAILURE',
              'files': inventory}
    args.report.parent.mkdir(parents=True, exist_ok=True)
    args.report.write_text(json.dumps(report, ensure_ascii=False, indent=2) + '\n')
    print(canonical({k: v for k, v in report.items() if k != 'files'}))
    return 2 if any(x['state'] != 'VERIFIED' for x in inventory) else 0

if __name__ == '__main__':
    raise SystemExit(main())
