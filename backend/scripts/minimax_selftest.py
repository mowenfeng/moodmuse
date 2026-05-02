from __future__ import annotations

import argparse
import json
import os
import sys
import time
from typing import Any

import requests
from dotenv import load_dotenv


def _extract_base_resp(payload: dict[str, Any]) -> dict[str, Any] | None:
    br = payload.get("base_resp")
    return br if isinstance(br, dict) else None


def _raise_if_base_resp_error(payload: dict[str, Any], context: str) -> None:
    br = _extract_base_resp(payload)
    if not br:
        return

    status_code = br.get("status_code")
    status_msg = br.get("status_msg")

    if status_code is None:
        return

    try:
        code_int = int(status_code)
    except (TypeError, ValueError):
        return

    if code_int == 0:
        return

    raise SystemExit(
        f"[FAIL] {context}: base_resp status_code={code_int}, status_msg={status_msg!r}\n"
        f"raw={json.dumps(payload, ensure_ascii=False)[:2000]}"
    )


def _pick_str(obj: Any, keys: set[str]) -> str | None:
    if isinstance(obj, dict):
        for k, v in obj.items():
            if k in keys and isinstance(v, str) and v.strip():
                return v
            nested = _pick_str(v, keys)
            if nested:
                return nested
    elif isinstance(obj, list):
        for item in obj:
            nested = _pick_str(item, keys)
            if nested:
                return nested
    return None


def _extract_audio_url(payload: dict[str, Any], *, output_format: str) -> str | None:
    fmt = (output_format or "url").lower()

    def _maybe_playable_url(value: str, *, field: str) -> str | None:
        v = value.strip()
        if v.startswith("http://") or v.startswith("https://"):
            return v
        if field == "audio" and fmt == "hex":
            return None
        if field == "audio" and fmt == "url":
            if v.startswith("http://") or v.startswith("https://"):
                return v
        return None

    data = payload.get("data")
    if isinstance(data, dict):
        for k in ("audio", "audio_url", "preview_url", "url"):
            v = data.get(k)
            if isinstance(v, str) and v.strip():
                playable = _maybe_playable_url(v, field=k)
                if playable:
                    return playable
    for k in ("audio_url", "preview_url", "audioUrl"):
        v = payload.get(k)
        if isinstance(v, str) and v.strip():
            playable = _maybe_playable_url(v, field=k)
            if playable:
                return playable
    return _pick_str(payload, {"audio_url", "preview_url", "audioUrl"})


def _extract_task_id(payload: dict[str, Any]) -> str | None:
    data = payload.get("data")
    if isinstance(data, dict):
        for k in ("task_id", "id", "taskId"):
            v = data.get(k)
            if isinstance(v, str) and v.strip():
                return v
    for k in ("task_id", "id", "taskId"):
        v = payload.get(k)
        if isinstance(v, str) and v.strip():
            return v
    return _pick_str(payload, {"task_id", "id", "taskId"})


def main() -> int:
    parser = argparse.ArgumentParser(description="MiniMax Music Generation self-test (server-side).")
    parser.add_argument(
        "--env-file",
        default="/root/moodmuse-backend/.env",
        help="Path to .env file (default: /root/moodmuse-backend/.env)",
    )
    parser.add_argument(
        "--base-url",
        default=os.getenv("MINIMAX_BASE_URL", "https://api.minimaxi.com").rstrip("/"),
        help="MiniMax API base URL",
    )
    parser.add_argument(
        "--model",
        default=os.getenv("MINIMAX_MODEL", "music-2.6"),
        help="MiniMax music model name",
    )
    parser.add_argument("--prompt", default="lofi chill rainy night, soft piano, slow tempo")
    parser.add_argument("--duration", type=int, default=30)
    parser.add_argument("--instrumental", action="store_true", default=True)
    parser.add_argument("--no-instrumental", action="store_false", dest="instrumental")
    parser.add_argument("--timeout-s", type=int, default=120)
    parser.add_argument("--poll-interval-s", type=float, default=3.0)
    parser.add_argument(
        "--http-timeout-s",
        type=int,
        default=int(os.getenv("MINIMAX_HTTP_TIMEOUT_S", "300")),
        help="Per-request HTTP timeout for MiniMax API calls (seconds). Music generation can exceed 60s.",
    )
    args = parser.parse_args()

    # Always load explicit path to avoid stdin/heredoc edge cases with find_dotenv().
    load_dotenv(args.env_file)

    api_key = (os.getenv("MINIMAX_API_KEY", "") or "").strip()
    if not api_key:
        print(
            "[FAIL] MINIMAX_API_KEY is empty.\n"
            f"- Check {args.env_file} contains a line like: MINIMAX_API_KEY=...\n"
            "- Do NOT put the key into os.getenv('...') first argument; it must be the env var name.\n",
            file=sys.stderr,
        )
        return 2

    create_url = f"{args.base_url}/v1/music_generation"
    query_url = f"{args.base_url}/v1/query/music_generation"
    headers = {"Authorization": f"Bearer {api_key}", "Content-Type": "application/json"}

    output_format = os.getenv("MINIMAX_OUTPUT_FORMAT", "url")
    payload = {
        "model": args.model,
        "prompt": args.prompt,
        "duration": args.duration,
        "is_instrumental": bool(args.instrumental),
        "output_format": output_format,
        "audio_setting": {
            "sample_rate": int(os.getenv("MINIMAX_AUDIO_SAMPLE_RATE", "44100")),
            "bitrate": int(os.getenv("MINIMAX_AUDIO_BITRATE", "256000")),
            "format": os.getenv("MINIMAX_AUDIO_FORMAT", "mp3"),
        },
    }

    print(f"[INFO] env_file={args.env_file}")
    print(f"[INFO] base_url={args.base_url}")
    print(f"[INFO] model={args.model}")
    print(f"[INFO] key_prefix={api_key[:6]} len={len(api_key)}")

    http_timeout = int(args.http_timeout_s)
    # (connect_timeout, read_timeout) — 音乐生成读超时经常需要分钟级
    timeout_tuple = (30, http_timeout)

    def post_with_retry(url: str, *, json_body: dict[str, Any]) -> requests.Response:
        last_err: Exception | None = None
        for attempt in range(1, 4):
            try:
                return requests.post(url, headers=headers, json=json_body, timeout=timeout_tuple)
            except (requests.exceptions.ReadTimeout, requests.exceptions.ConnectTimeout) as e:
                last_err = e
                print(f"[WARN] POST timeout ({attempt}/3): {e}", file=sys.stderr)
                time.sleep(2.0 * attempt)
        assert last_err is not None
        raise last_err

    r = post_with_retry(create_url, json_body=payload)
    print(f"[INFO] create http_status={r.status_code}")
    try:
        create_data = r.json()
    except Exception:
        print(r.text[:2000])
        return 1

    print(json.dumps(create_data, ensure_ascii=False, indent=2)[:4000])
    _raise_if_base_resp_error(create_data, context="create")

    audio_url = _extract_audio_url(create_data, output_format=output_format)
    if audio_url:
        print(f"[OK] audio_url={audio_url}")
        return 0

    task_id = _extract_task_id(create_data)
    if task_id:
        print(f"[OK] task_id={task_id}")
        # 兼容异步：继续轮询直到拿到音频或失败
        print(f"[INFO] polling task_id={task_id}")
    else:
        print("[FAIL] No task_id and no audio_url/audio in create response.", file=sys.stderr)
        return 1

    deadline = time.time() + float(args.timeout_s)
    last: dict[str, Any] = create_data
    while time.time() < deadline:
        qr = requests.get(
            query_url,
            headers=headers,
            params={"task_id": task_id},
            timeout=timeout_tuple,
        )
        print(f"[INFO] query http_status={qr.status_code}")
        try:
            last = qr.json()
        except Exception:
            print(qr.text[:2000])
            return 1

        print(json.dumps(last, ensure_ascii=False, indent=2)[:4000])
        _raise_if_base_resp_error(last, context="query")

        status_raw = _pick_str(last, {"status", "state"})
        status_value = (status_raw or "").lower()
        audio_url = _extract_audio_url(last, output_format=output_format)

        # MiniMax MusicData.status 常见为整数：1 进行中，2 已完成
        data_obj = last.get("data")
        data_status = None
        if isinstance(data_obj, dict):
            data_status = data_obj.get("status")

        if audio_url:
            print(f"[OK] completed audio_url={audio_url}")
            return 0

        if status_value in {"failed", "error"}:
            br = _extract_base_resp(last)
            print(f"[FAIL] task failed status={status_value} base_resp={br}", file=sys.stderr)
            return 1

        if data_status in {2, "2"} and not audio_url:
            print(
                "[FAIL] MiniMax reports completed (data.status=2) but no playable URL was found. "
                "If output_format is hex, audio may be a hex string rather than https URL.",
                file=sys.stderr,
            )
            return 1

        time.sleep(float(args.poll_interval_s))

    print(f"[FAIL] timeout. last={json.dumps(last, ensure_ascii=False)[:2000]}", file=sys.stderr)
    return 1


if __name__ == "__main__":
    raise SystemExit(main())
