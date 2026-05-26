#!/usr/bin/env python3
"""
lk21_minify.py
Strip field tidak perlu dari lk21_data.json
Hanya simpan: title, slug, poster, type

Usage:
    python lk21_minify.py input.json output.json
"""

import json
import sys
import os

def minify(input_path, output_path):
    print(f"Reading {input_path}...")
    with open(input_path, "r", encoding="utf-8") as f:
        raw = json.load(f)

    data = raw if isinstance(raw, list) else raw.get("data", [])
    total_in = len(data)

    # Deduplicate by slug (hapus duplikat akibat resume dari page lama)
    seen = set()
    stripped = []
    dupes = 0

    for item in data:
        slug = item.get("slug", "")
        if slug in seen:
            dupes += 1
            continue
        seen.add(slug)
        stripped.append({
            "title": item.get("title", ""),
            "slug": slug,
            "poster": item.get("poster", ""),
            "type": item.get("type", ""),
        })

    # Sort by title biar search lebih predictable
    stripped.sort(key=lambda x: x["title"].lower())

    output = {
        "total": len(stripped),
        "data": stripped
    }

    print(f"Writing {output_path}...")
    with open(output_path, "w", encoding="utf-8") as f:
        json.dump(output, f, ensure_ascii=False, separators=(",", ":"))

    # Stats
    size_in = os.path.getsize(input_path) / (1024 * 1024)
    size_out = os.path.getsize(output_path) / (1024 * 1024)

    print(f"\n✅ Done!")
    print(f"   Input  : {total_in} film, {size_in:.2f} MB")
    print(f"   Dupes  : {dupes} dihapus")
    print(f"   Output : {len(stripped)} film, {size_out:.2f} MB")
    print(f"   Reduced: {((size_in - size_out) / size_in * 100):.1f}%")

if __name__ == "__main__":
    if len(sys.argv) < 3:
        print("Usage: python lk21_minify.py <input.json> <output.json>")
        sys.exit(1)
    minify(sys.argv[1], sys.argv[2])
