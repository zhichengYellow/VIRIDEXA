# Media attribution and reuse

The v0.1.0 local media store contains **21 iNaturalist photos across 17 exact-species catalogue records**, plus three licensed static fallback assets. The union covers **19 catalogue entries**, not 19 independently verified species. All image-bearing entries currently also have traceable Chinese names. Missing images remain explicit placeholders.

Selection requires source research-grade observations, exact species names and CC0/CC BY photo licensing. iNaturalist research grade is an upstream identification state, not VIRIDEXA human scientific verification. Each source page remains authoritative for its individual licence. [Official media-use guidance](https://help.inaturalist.org/en/support/solutions/articles/151000169918) explains that default licensing is not universally CC BY; this pipeline rejects NC, ND, unknown and all-rights-reserved photos.

Original attribution strings are retained below, including CC0 entries whose source provides only “no rights reserved”, for which a named creator is not asserted. Public UI links to the observation for full context. Derived images undergo EXIF orientation correction, resizing, RGB/WebP encoding and layout cropping; original downloaded bytes and their SHA-256 remain local. No downloaded photos are bundled into Git. The three small static assets and their licences are documented in [UI_ASSET_PROVENANCE.md](UI_ASSET_PROVENANCE.md).

| Plant ID / scientific name | Source attribution | Photo licence | Observation | Original SHA-256 |
| --- | --- | --- | --- | --- |
| 68 / *Acer japonicum* | (c) pavlaki1968, some rights reserved (CC BY) | CC-BY | [source](https://www.inaturalist.org/observations/249930878) | `72ea3ff13a51a098996864fab7d14b6597218460369b15a1cf67350afc1f701b` |
| 68 / *Acer japonicum* | (c) Marco Mussita, some rights reserved (CC BY) | CC-BY | [source](https://www.inaturalist.org/observations/360825666) | `c17ed06bb582ca4dbfdbc9db9535882082ac1b43ecbf5c789ddfe34937a24776` |
| 72 / *Achyrospermum densiflorum* | (c) Forest Botial-Jarvis, some rights reserved (CC BY) | CC-BY | [source](https://www.inaturalist.org/observations/178195133) | `e1d295896b078cdc2e166505b85b151cb49d8428e72d382571cf851f89bc7b54` |
| 77 / *Acacia dealbata* | (c) Jose Castro, some rights reserved (CC BY) | CC-BY | [source](https://www.inaturalist.org/observations/332988984) | `16da0f693ef161ffc2db8c17d24a29001bb665b9a7bc7ef3c0cba944c2c8cc22` |
| 77 / *Acacia dealbata* | (c) zebedeugalinha, some rights reserved (CC BY) | CC-BY | [source](https://www.inaturalist.org/observations/405055093) | `bbd2e6cd82bb7f64c32cfa3328d4620a6694af15ff2e43fe54fb51e84f527249` |
| 79 / *Acer buergerianum* | (c) Marco Mussita, some rights reserved (CC BY) | CC-BY | [source](https://www.inaturalist.org/observations/42464511) | `679205d43c088d26844d01f9eb7ed57b020eff45c1dc48e75599545c257f649b` |
| 8064 / *Brassica napus* | (c) Thomas Ebner, some rights reserved (CC BY) | CC-BY | [source](https://www.inaturalist.org/observations/42400956) | `f7a236c04d3a338eaac76e19883417d0fd72ba89c1aaa20214a3919baa9cc2dd` |
| 9445 / *Camellia oleifera* | no rights reserved | CC0 | [source](https://www.inaturalist.org/observations/239518356) | `9df091df34e0054a4683f2d4b8d574e672207a620dd115a1ebd1430647a34816` |
| 9445 / *Camellia oleifera* | no rights reserved | CC0 | [source](https://www.inaturalist.org/observations/322954739) | `1b841c64d998d99effc018f06427322853db127c33f0a2f588be1319d6e5a9be` |
| 25926 / *Gossypium hirsutum* | (c) Lauren McLaurin, some rights reserved (CC BY) | CC-BY | [source](https://www.inaturalist.org/observations/19742972) | `c710871542c1411bad50c3dbc71f060db33c1dff67974156478c0a6c775db881` |
| 26923 / *Helianthus annuus* | (c) Michelle W. （鍾偉瑋）, some rights reserved (CC BY) | CC-BY | [source](https://www.inaturalist.org/observations/61287321) | `9e2cabf3564ba50b5b42729ae91c32a81a0457f0ecd5abd8d038c0a6bb2f7a21` |
| 30610 / *Jatropha curcas* | (c) Carlos Domínguez-Rodríguez, some rights reserved (CC BY) | CC-BY | [source](https://www.inaturalist.org/observations/41091904) | `21a18032edcbdb9cb67838dc9fff315d7414a30a17d0f9cc747b8c92c1ac6c3f` |
| 30610 / *Jatropha curcas* | (c) Antonio Nonato, some rights reserved (CC BY) | CC-BY | [source](https://www.inaturalist.org/observations/146849303) | `05a3a25b3a2fee9cbf32c126b20134c304c957dbf7a9650a7c125eea6f63e02f` |
| 34259 / *Magnolia grandiflora* | (c) Joseph McPhail, some rights reserved (CC BY) | CC-BY | [source](https://www.inaturalist.org/observations/147017544) | `023a151405db695a20a9138b0371abca80b4f9d2f29c35d2a1c11a1c8832a7cf` |
| 37548 / *Nelumbo nucifera* | (c) Adrien Jailloux, some rights reserved (CC BY) | CC-BY | [source](https://www.inaturalist.org/observations/336151407) | `d7b969790355adefb925fb3467f29bb2d32a70861468cfb3049e5551a9129ecb` |
| 38947 / *Oryza sativa* | (c) Вячеслав Юсупов, some rights reserved (CC BY) | CC-BY | [source](https://www.inaturalist.org/observations/190555486) | `11f7f60cbe03808c3a46ad796679270520a485607ed6ec73b9304ccb07d7a520` |
| 39267 / *Paeonia lactiflora* | no rights reserved | CC0 | [source](https://www.inaturalist.org/observations/376014961) | `1b67ae4102066a83d50ada27480b78646dec35857cf9c79aef7f28352de86322` |
| 43231 / *Pinus massoniana* | (c) Denys Vynokurov, some rights reserved (CC BY) | CC-BY | [source](https://www.inaturalist.org/observations/350043048) | `670af23a80badfa0d2a76d65af32914698d613056a6dc52b65b72e2c310d28c3` |
| 45438 / *Prunus serrulata* | (c) W Rao, some rights reserved (CC BY) | CC-BY | [source](https://www.inaturalist.org/observations/207339838) | `599fb278c5571648b0bea74aa169f4dd5d4b65c294c794acd246ef056c9061dd` |
| 46223 / *Quercus robur* | (c) Natalya Ivanova, some rights reserved (CC BY) | CC-BY | [source](https://www.inaturalist.org/observations/7891944) | `b17f39fe796a8cc519ec15a52c89980f2a4018c6fa28d76a017488dd259ba2f3` |
| 60611 / *Zea mays* | (c) Илья Руденко, some rights reserved (CC BY) | CC-BY | [source](https://www.inaturalist.org/observations/282061777) | `9b10f2ef41b74cb208a1e6cbd69a180b9fd42faa1646df49e5e77cbdb98645b6` |

## Persistent storage and repeatable collection

Local metadata/raw bytes live in ignored `data/raw/images/`; public WebP derivatives live in ignored `data/media/public/`. Compose mounts only the latter at `/media-public:ro` in Nginx. `/api/v1/plants/{id}/media` returns at most12 server-filtered entries for a public plant, with identity, attribution and licence; no internal filesystem paths or private coordinates are returned. `/media/` serves derived image bytes; raw metadata and original source documents are not served.

```sh
python3 -m pip install -r requirements-pipeline.txt
python3 scripts/pipeline/collect_botanical_media.py --plant-id 9445 --scientific-name 'Camellia oleifera' --limit 2
python3 scripts/pipeline/import_botanical_media.py
# Explicit append-only import after reviewing the dry-run:
python3 scripts/pipeline/provision_botanical_importer.py
python3 scripts/pipeline/import_botanical_media.py --apply
python3 scripts/pipeline/audit_botanical_completeness.py
```

The importer verifies original bytes, derivatives, catalogue identity, path shape and per-image licence. Existing raw files and derivatives are not overwritten. Duplicate import does not replace media rows. No R2/OSS adapter is implemented; local persistence works without any cloud credentials. Reuse of application code grants no rights over independently licensed data or media. No project source-code licence has been selected.
