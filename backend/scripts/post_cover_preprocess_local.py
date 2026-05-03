#!/usr/bin/env python3
"""从本机文件读取音频，以 audio_base64 调用 MoodMuse 后端的 /api/cover/preprocess。

示例（Windows 路径请加引号）：
  python scripts/post_cover_preprocess_local.py \\
    "C:\\Users\\mowen\\Music\\华语群星 - 梅庄_L.mp3" \\
    http://127.0.0.1:8010
"""

from __future__ import annotations

import argparse
import base64
import json
import sys
import urllib.error
import urllib.request


def main() -> int:
    p = argparse.ArgumentParser()
    p.add_argument("audio_path", help="本机音频文件路径（mp3/wav/flac 等）")
    p.add_argument(
        "base_url",
        nargs="?",
        default="http://127.0.0.1:8010",
        help="MoodMuse 后端根地址，默认 http://127.0.0.1:8010",
    )
    p.add_argument(
        "--max-mb",
        type=float,
        default=35.0,
        help="最大原始文件体积（MB），默认 35，避免请求体过大",
    )
    args = p.parse_args()

    max_bytes = int(args.max_mb * 1024 * 1024)
    try:
        with open(args.audio_path, "rb") as f:
            raw = f.read()
    except OSError as e:
        print(f"无法读取文件: {e}", file=sys.stderr)
        return 2

    if len(raw) > max_bytes:
        print(f"文件过大: {len(raw)} bytes > {max_bytes} bytes", file=sys.stderr)
        return 3

    body = {
        "audio_base64": base64.standard_b64encode(raw).decode("ascii"),
    }
    data = json.dumps(body, ensure_ascii=False).encode("utf-8")
    url = args.base_url.rstrip("/") + "/api/cover/preprocess"
    req = urllib.request.Request(
        url,
        data=data,
        headers={"Content-Type": "application/json"},
        method="POST",
    )
    try:
        with urllib.request.urlopen(req, timeout=600) as resp:
            print(resp.read().decode("utf-8", errors="replace"))
    except urllib.error.HTTPError as e:
        print(e.read().decode("utf-8", errors="replace"), file=sys.stderr)
        return 4
    except urllib.error.URLError as e:
        print(str(e), file=sys.stderr)
        return 5
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
