#!/usr/bin/env python3
"""Bounded, repeatable iNaturalist CC0/CC-BY botanical image collector."""
from __future__ import annotations
import argparse, hashlib, json, re, time
from pathlib import Path
from urllib.parse import urlparse
from urllib.request import Request, urlopen

ROOT = Path(__file__).resolve().parents[2]
RAW = ROOT / "data/raw/images"
PUBLIC = ROOT / "data/media/public"
API = "https://api.inaturalist.org/v1/observations"
LICENSES = {"cc0", "cc-by"}
HOSTS = {"inaturalist-open-data.s3.amazonaws.com", "static.inaturalist.org", "inaturalist-user-images.s3.amazonaws.com"}
MAX_BYTES = 15_000_000
MAX_PIXELS = 40_000_000


def approved_photo(photo: dict) -> bool:
    url = photo.get("url", "")
    license_code = (photo.get("license_code") or "").lower()
    host = urlparse(url).hostname
    return license_code in LICENSES and host in HOSTS and urlparse(url).scheme == "https" and not photo.get("hidden") and not photo.get("flags") and not photo.get("moderator_actions")


def bare_binomial(name: str) -> bool:
    return bool(re.fullmatch(r"[A-Z][a-z]+ [a-z][a-z-]+", name.strip()))


def fetch(url: str, limit: int = MAX_BYTES) -> bytes:
    if urlparse(url).scheme != "https" or urlparse(url).hostname not in HOSTS | {"api.inaturalist.org"}: raise ValueError("unapproved media host")
    with urlopen(Request(url, headers={"User-Agent": "VIRIDEXA botanical media collector/1.0"}), timeout=20) as r:
        if urlparse(r.url).scheme != "https" or urlparse(r.url).hostname not in HOSTS | {"api.inaturalist.org"}: raise ValueError("unapproved redirect")
        data = r.read(limit + 1)
    if len(data) > limit: raise ValueError("response exceeds byte limit")
    return data


def collect(name: str, plant_id: int, limit: int = 10) -> int:
    if not bare_binomial(name): raise ValueError(f"not an exact bare binomial: {name}")
    if not isinstance(plant_id,int) or isinstance(plant_id,bool) or plant_id<1: raise ValueError("plant_id must be positive")
    if not 1 <= limit <= 30: raise ValueError("limit must be between 1 and 30")
    RAW.mkdir(parents=True, exist_ok=True); PUBLIC.mkdir(parents=True, exist_ok=True)
    q = f"{API}?taxon_name={name.replace(' ', '%20')}&quality_grade=research&photo_license=cc0,cc-by&per_page={limit}&order_by=votes"
    cache = RAW / ("query-" + hashlib.sha256(q.encode()).hexdigest() + ".json")
    if not cache.exists():
        metadata = fetch(q, 2_000_000)
        with cache.open("xb") as stream: stream.write(metadata)
    metadata = cache.read_bytes()
    rows = json.loads(metadata).get("results", [])
    count = 0
    for obs in rows:
        if obs.get("quality_grade") != "research": continue
        taxon = obs.get("taxon") or {}
        if taxon.get("rank") != "species" or taxon.get("name") != name: continue
        photos = [p for p in obs.get("photos", []) if approved_photo(p)]
        if not photos: continue
        photo = photos[0]; original = photo["url"].replace("square.", "original.")
        raw = fetch(original)
        if not raw.startswith(b"\xff\xd8") and not raw.startswith(b"\x89PNG\r\n\x1a\n"): continue
        sha = hashlib.sha256(raw).hexdigest(); raw_path = RAW / f"{sha}.image"
        if not raw_path.exists(): raw_path.write_bytes(raw)
        try:
            from PIL import Image, ImageOps
            import io
            image = Image.open(io.BytesIO(raw))
            if image.width * image.height > MAX_PIXELS: continue
            image.load(); image = ImageOps.exif_transpose(image)
            image = image.convert("RGB")
            for width in (640, 960):
                thumb = image.copy(); thumb.thumbnail((width, width * 2))
                dest=PUBLIC / f"{sha}-{width}.webp"
                if not dest.exists(): thumb.save(dest, "WEBP", quality=84)
            dest=PUBLIC / f"{sha}-full.webp"
            if not dest.exists(): image.save(dest, "WEBP", quality=88)
        except ImportError as e: raise RuntimeError("Pillow is required to create WebP derivatives") from e
        record = {"plant_id": plant_id, "scientific_name": name, "author": photo.get("attribution") or obs.get("user", {}).get("login", "Unknown"),
                  "license_code": photo["license_code"].upper(), "source_url": f"https://www.inaturalist.org/observations/{obs['id']}",
                  "source_taxon_id": taxon["id"], "source_observation_id": obs["id"], "sha256": sha,
                  "public_filename": f"{sha}-full.webp", "width": image.width, "height": image.height, "state": "PUBLIC"}
        sidecar=RAW / f"{sha}.json"
        if not sidecar.exists():
            with sidecar.open("x") as stream: stream.write(json.dumps(record, ensure_ascii=False, indent=2))
        count += 1
        time.sleep(1)
    return count


def main():
    p = argparse.ArgumentParser(); p.add_argument("--plant-id", type=int, required=True); p.add_argument("--scientific-name", required=True); p.add_argument("--limit", type=int, default=10)
    args = p.parse_args(); print(f"collected {collect(args.scientific_name, args.plant_id, args.limit)} images")
if __name__ == "__main__": main()
