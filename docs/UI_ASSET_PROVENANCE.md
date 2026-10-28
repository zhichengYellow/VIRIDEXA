# Botanical UI asset provenance

These three files are decorative course UI illustrations. They do not establish identification, taxonomy, traits, or scientific evidence; use the course record and cited research sources for those purposes. The API records are metadata-only.

| UI file | Course record verified in `GET /api/v1/plants?query=...` | Source file and author | License and source |
| --- | --- | --- | --- |
| [`ginkgo-biloba-hero.jpg`](assets/botanical-originals/ginkgo-biloba-hero.jpg) | ID 2, *Ginkgo biloba* L.; `VERIFIED_FROM_PRINTED_HEADING` (course PDF, printed p. 12) | Dan Keck, “Ginkgo biloba (35096003376).jpg” | CC0 1.0. [Commons file page](https://commons.wikimedia.org/wiki/File:Ginkgo_biloba_(35096003376).jpg); [original file](https://upload.wikimedia.org/wikipedia/commons/8/8f/Ginkgo_biloba_%2835096003376%29.jpg); [CC0 deed](https://creativecommons.org/publicdomain/zero/1.0/). Downloaded the Commons 1280 × 720 thumbnail. |
| [`jatropha-curcas-plate.jpg`](assets/botanical-originals/jatropha-curcas-plate.jpg) | ID 30610, *Jatropha curcas*; `SOURCE_REPORTED` (eFloras Flora of China source archive) | Francisco Manuel Blanco (O.S.A.), “Jatropha curcas Blanco2.384,” plate from *Flora de Filipinas* (1880–1883); cropped by CarolSpears. | Public domain / Public Domain Mark 1.0 per Commons file page. [Commons file page](https://commons.wikimedia.org/wiki/File:Jatropha_curcas_Blanco2.384-cropped.jpg); [original cropped file](https://upload.wikimedia.org/wikipedia/commons/3/35/Jatropha_curcas_Blanco2.384-cropped.jpg). |
| [`camellia-oleifera.jpg`](assets/botanical-originals/camellia-oleifera.jpg) | ID 9445, *Camellia oleifera*; `SOURCE_REPORTED` (eFloras Flora of China source archive) | USDA Agricultural Research Service, “Camellia oleifera.jpg,” image K8235-1, Lu Shan Snow Camellia at the U.S. National Arboretum (2006). | U.S. federal work, public domain per Commons file page. [Commons file page](https://commons.wikimedia.org/wiki/File:Camellia_oleifera.jpg); [original file](https://upload.wikimedia.org/wikipedia/commons/0/03/Camellia_oleifera.jpg). |

## Integrity

SHA-256 hashes of the checked-in files:

```text
4e4007ae94d75e405a27082bffa5653b076c9eb0ed279175b3fc0dad3111a031  ginkgo-biloba-hero.jpg
f4a472e1848f10987d36fb3fe694a21bb57bc34926107ba5b52e6c223b0d9ecd  jatropha-curcas-plate.jpg
400eb03e827ba421252a3ffc5b879627d194adaa495616e0f203f44a8f0d8078  camellia-oleifera.jpg
```

Each source file page records the attribution and license information. The Ginkgo hero is a Commons-generated 1280-pixel thumbnail; the other images are the Commons JPEG files listed above (including the documented cropped Jatropha derivative). Captions should retain the linked file-page credit and license when these images are displayed.

## Final use boundary
Only Ginkgo, Jatropha and Camellia assets are served by the application. The mixed-taxa Cycas plate was excluded during review and moved outside public assets: multiple labeled species and a weak historical date make it unsuitable as a single-species card. Photographs/plates are UI illustrations, never new scientific evidence, and do not change database image/license/scientific verification states. Infraspecific names and cultivars never receive a species illustration automatically.

## Deployment derivatives (2026-10-27)

The original downloaded JPEG bytes above are retained in `docs/assets/botanical-originals/`, outside the public web bundle. The site serves same-dimension WebP derivatives (Pillow quality 86, method 6); scientific files were not involved. Attribution and illustration-only status are unchanged. Exact original/derivative hashes and byte sizes are in `reports/final-hardening/image-encoding.json`. Three images total 817,089 → 405,366 bytes.

## 沉浸式体验响应式派生资源

2026-10-28：从上述仓库内原件生成每个物种的 384、640、960px WebP，共9个资源。保留原许可和严格学名映射，不新增图片来源或物种认定。图片组件使用真实宽高与 `srcset`，图鉴图像不作为观测证据。详细尺寸与字节清单：`reports/immersive/responsive-images.json`。受保护科研目录未被修改。
