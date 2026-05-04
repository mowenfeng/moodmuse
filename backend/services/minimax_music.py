from __future__ import annotations

import json
import os
import time
from uuid import uuid4

from dotenv import load_dotenv
import requests

load_dotenv()


class MiniMaxMusicService:
    def __init__(self) -> None:
        self.api_key = (os.getenv("MINIMAX_API_KEY", "") or "").strip()
        self.use_mock = os.getenv("MINIMAX_USE_MOCK", "true").lower() == "true"
        # 注意：中国大陆文档站点 `platform.minimaxi.com` 对应的 API host 通常是 `api.minimaxi.com`
        # 若你账号实际要求其它网关，请在 .env 里显式覆盖 `MINIMAX_BASE_URL`。
        self.base_url = os.getenv("MINIMAX_BASE_URL", "https://api.minimaxi.com").rstrip("/")
        self.model = os.getenv("MINIMAX_MODEL", "music-2.6")
        self.timeout_s = int(os.getenv("MINIMAX_TIMEOUT_S", "120"))
        self.poll_interval_s = float(os.getenv("MINIMAX_POLL_INTERVAL_S", "3"))
        self.output_format = (os.getenv("MINIMAX_OUTPUT_FORMAT", "url") or "url").lower()
        self.http_read_timeout_s = int(os.getenv("MINIMAX_HTTP_TIMEOUT_S", "300"))
        # 翻唱第二步 /v1/music_generation 的 model；若账号报 2061 不支持 music-cover-free，可在 .env 改为账号支持的值（如 music-cover）
        self.cover_generate_model = (os.getenv("MINIMAX_COVER_GENERATE_MODEL", "music-cover-free") or "music-cover-free").strip()

    def create_generation(self, prompt: str, duration: int) -> dict:
        if self.use_mock:
            return self._mock_generation(prompt=prompt, duration=duration)
        if not self.api_key:
            raise RuntimeError(
                "MINIMAX_API_KEY 为空：请检查服务器 /root/moodmuse-backend/.env 是否包含 "
                "`MINIMAX_API_KEY=...`，并确认变量名不是把 key 写进了 getenv 的第一个参数。"
            )
        return self._real_generation(prompt=prompt, duration=duration)

    def cover_preprocess(self, audio_url: str | None = None, audio_base64: str | None = None) -> dict:
        if self.use_mock:
            return {
                "cover_feature_id": f"mock-cover-{uuid4().hex[:12]}",
                "formatted_lyrics": "[mock verse]\nmock lyrics line 1\nmock lyrics line 2\n",
                "audio_duration": 12.34,
                "structure_result": json.dumps({"mock": True}, ensure_ascii=False),
                "raw": {"mock": True},
            }
        if not self.api_key:
            raise RuntimeError("MINIMAX_API_KEY 未配置")

        url = f"{self.base_url}/v1/music_cover_preprocess"
        headers = {
            "Authorization": f"Bearer {self.api_key}",
            "Content-Type": "application/json",
        }
        payload: dict = {"model": "music-cover"}
        if audio_url:
            payload["audio_url"] = audio_url
        else:
            payload["audio_base64"] = audio_base64
        timeout_tuple = (30, self.http_read_timeout_s)
        resp = self._post_json(url, headers=headers, body=payload, timeout_tuple=timeout_tuple)
        resp.raise_for_status()
        data = resp.json()
        self._raise_if_base_resp_error(data, context="music_cover_preprocess")

        inner = data.get("data") if isinstance(data, dict) else None
        inner_dict = inner if isinstance(inner, dict) else {}
        root_dict = data if isinstance(data, dict) else {}

        cover_feature_id = self._pick_first_str(inner_dict, {"cover_feature_id", "cover_feature", "feature_id"})
        if not cover_feature_id:
            cover_feature_id = self._pick_first_str(root_dict, {"cover_feature_id", "cover_feature", "feature_id"})

        formatted_lyrics = self._pick_first_str(inner_dict, {"formatted_lyrics", "lyrics"})
        if not formatted_lyrics:
            formatted_lyrics = self._pick_first_str(root_dict, {"formatted_lyrics", "lyrics"})

        audio_duration = self._pick_first_number(inner_dict, {"audio_duration", "duration"})
        if audio_duration is None:
            audio_duration = self._pick_first_number(root_dict, {"audio_duration", "duration"})

        structure_result = self._stringify_structure(inner_dict.get("structure_result"))
        if structure_result is None:
            structure_result = self._stringify_structure(root_dict.get("structure_result"))

        dtw_blob = self._find_first_blob(data, ("dtw_result", "dtwResult"))
        beat_blob = self._find_first_blob(data, ("beat_result", "beatResult"))

        return {
            "cover_feature_id": cover_feature_id,
            "formatted_lyrics": formatted_lyrics,
            "audio_duration": audio_duration,
            "structure_result": structure_result,
            "dtw_result": self._coerce_json_blob(dtw_blob) if dtw_blob is not None else None,
            "beat_result": self._coerce_json_blob(beat_blob) if beat_blob is not None else None,
            "raw": data if isinstance(data, dict) else {"value": data},
        }

    def cover_generate(
        self,
        prompt: str,
        lyrics: str,
        cover_feature_id: str,
        audio_duration: float | None = None,
        dtw_result: object | None = None,
        beat_result: object | None = None,
    ) -> dict:
        if self.use_mock:
            demo_mp3 = "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-1.mp3"
            return {
                "audio_url": demo_mp3,
                "preview_url": demo_mp3,
                "raw": {"mock": True},
            }
        if not self.api_key:
            raise RuntimeError("MINIMAX_API_KEY 未配置")

        url = f"{self.base_url}/v1/music_generation"
        headers = {
            "Authorization": f"Bearer {self.api_key}",
            "Content-Type": "application/json",
        }
        payload = {
            "model": self.cover_generate_model,
            "prompt": prompt,
            "lyrics": lyrics,
            "cover_feature_id": cover_feature_id,
            "output_format": "url",
            "audio_setting": {
                "sample_rate": int(os.getenv("MINIMAX_AUDIO_SAMPLE_RATE", "44100")),
                "bitrate": int(os.getenv("MINIMAX_AUDIO_BITRATE", "256000")),
                "format": os.getenv("MINIMAX_AUDIO_FORMAT", "mp3"),
            },
        }
        # music-cover（非 free）第二步常见要求：与 preprocess 返回的分析字段一并提交
        if audio_duration is not None:
            payload["audio_duration"] = audio_duration
        if dtw_result is not None:
            payload["dtw_result"] = self._coerce_json_blob(dtw_result)
        if beat_result is not None:
            payload["beat_result"] = self._coerce_json_blob(beat_result)

        timeout_tuple = (30, self.http_read_timeout_s)
        resp = self._post_json(url, headers=headers, body=payload, timeout_tuple=timeout_tuple)
        resp.raise_for_status()
        data = resp.json()
        self._raise_if_base_resp_error(data, context="music_cover music_generation")

        audio_url = self._extract_audio_url(data)
        preview_url = audio_url
        return {"audio_url": audio_url, "preview_url": preview_url, "raw": data if isinstance(data, dict) else {"value": data}}

    def _mock_generation(self, prompt: str, duration: int) -> dict:
        fake_id = f"mock-{uuid4().hex[:12]}"
        # 公共演示链接，MVP 用于跑通流程。
        demo_mp3 = "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-1.mp3"
        return {
            "provider_task_id": fake_id,
            "title": "雨夜的最后一口热气",
            "audio_url": demo_mp3,
            "preview_url": demo_mp3,
            "status": "completed",
            "meta": {"duration": duration, "prompt": prompt},
        }

    def _real_generation(self, prompt: str, duration: int) -> dict:
        if not self.api_key:
            raise RuntimeError("MINIMAX_API_KEY 未配置")

        create_url = f"{self.base_url}/v1/music_generation"
        query_url = f"{self.base_url}/v1/query/music_generation"
        headers = {
            "Authorization": f"Bearer {self.api_key}",
            "Content-Type": "application/json",
        }

        # 兼容官方/代理常见字段，服务端会忽略不支持字段。
        payload = {
            "model": self.model,
            "prompt": prompt,
            "duration": duration,
            "is_instrumental": True,
            # 官方示例常用：url 便于直接播放/下载；也可用 hex（默认）自行解码保存
            "output_format": os.getenv("MINIMAX_OUTPUT_FORMAT", "url"),
            "audio_setting": {
                "sample_rate": int(os.getenv("MINIMAX_AUDIO_SAMPLE_RATE", "44100")),
                "bitrate": int(os.getenv("MINIMAX_AUDIO_BITRATE", "256000")),
                "format": os.getenv("MINIMAX_AUDIO_FORMAT", "mp3"),
            },
        }

        timeout_tuple = (30, self.http_read_timeout_s)

        def post_json(url: str, body: dict) -> requests.Response:
            return self._post_json(url, headers=headers, body=body, timeout_tuple=timeout_tuple)

        def get_json(url: str, params: dict) -> requests.Response:
            last_err: Exception | None = None
            for attempt in range(1, 4):
                try:
                    return requests.get(url, headers=headers, params=params, timeout=timeout_tuple)
                except (requests.exceptions.ReadTimeout, requests.exceptions.ConnectTimeout) as e:
                    last_err = e
                    time.sleep(2.0 * attempt)
            assert last_err is not None
            raise last_err

        create_resp = post_json(create_url, payload)
        create_resp.raise_for_status()
        create_data = create_resp.json()
        self._raise_if_base_resp_error(create_data, context="music_generation create")

        immediate_audio = self._extract_audio_url(create_data)
        immediate_status = self._pick_first_str(create_data, {"status", "state"}) or "completed"
        if immediate_audio:
            return {
                "provider_task_id": self._extract_task_id(create_data) or "",
                "title": "MoodMuse 生成曲目",
                "audio_url": immediate_audio,
                "preview_url": immediate_audio,
                "status": "completed" if immediate_status.lower() != "failed" else "failed",
                "meta": {"raw": create_data},
            }

        provider_task_id = self._extract_task_id(create_data)
        if not provider_task_id:
            raise RuntimeError(
                f"MiniMax 返回缺少 task_id（请检查返回 JSON 是否包含 base_resp 错误）: {create_data}"
            )

        deadline = time.time() + self.timeout_s
        last_payload: dict = create_data
        while time.time() < deadline:
            query_resp = get_json(query_url, {"task_id": provider_task_id})
            query_resp.raise_for_status()
            last_payload = query_resp.json()
            self._raise_if_base_resp_error(last_payload, context="music_generation query")

            status_value = (self._pick_first_str(last_payload, {"status", "state"}) or "").lower()
            audio_url = self._extract_audio_url(last_payload)

            if audio_url:
                return {
                    "provider_task_id": provider_task_id,
                    "title": "MoodMuse 生成曲目",
                    "audio_url": audio_url,
                    "preview_url": audio_url,
                    "status": "completed",
                    "meta": {"raw": last_payload},
                }
            if status_value in {"failed", "error"}:
                return {
                    "provider_task_id": provider_task_id,
                    "title": "MoodMuse 生成曲目",
                    "audio_url": None,
                    "preview_url": None,
                    "status": "failed",
                    "meta": {"raw": last_payload},
                }
            time.sleep(self.poll_interval_s)

        raise TimeoutError(f"MiniMax 任务超时，task_id={provider_task_id}, last={last_payload}")

    def _find_first_blob(self, obj: object, key_candidates: tuple[str, ...]) -> object | None:
        """在任意嵌套 dict/list 中查找首个存在的键（网关可能把字段放在 data/extra 等子对象里）。"""
        if isinstance(obj, dict):
            for k in key_candidates:
                if k in obj:
                    v = obj[k]
                    if v is not None and v != "":
                        return v
            for v in obj.values():
                found = self._find_first_blob(v, key_candidates)
                if found is not None:
                    return found
        elif isinstance(obj, list):
            for item in obj:
                found = self._find_first_blob(item, key_candidates)
                if found is not None:
                    return found
        return None

    def _coerce_json_blob(self, value: object) -> object:
        """若接口返回 JSON 字符串，则解析为对象再带给下游请求。"""
        if isinstance(value, str):
            s = value.strip()
            if s.startswith("{") or s.startswith("["):
                try:
                    return json.loads(s)
                except json.JSONDecodeError:
                    pass
        return value

    def _post_json(self, url: str, headers: dict, body: dict, timeout_tuple: tuple[int, int]) -> requests.Response:
        last_err: Exception | None = None
        for attempt in range(1, 4):
            try:
                return requests.post(url, headers=headers, json=body, timeout=timeout_tuple)
            except (requests.exceptions.ReadTimeout, requests.exceptions.ConnectTimeout) as e:
                last_err = e
                time.sleep(2.0 * attempt)
        assert last_err is not None
        raise last_err

    def _pick_first_number(self, obj: object, keys: set[str]) -> float | None:
        if isinstance(obj, dict):
            for k, v in obj.items():
                if k in keys:
                    n = self._coerce_number(v)
                    if n is not None:
                        return n
                nested = self._pick_first_number(v, keys)
                if nested is not None:
                    return nested
        elif isinstance(obj, list):
            for item in obj:
                nested = self._pick_first_number(item, keys)
                if nested is not None:
                    return nested
        return None

    def _coerce_number(self, value: object) -> float | None:
        if isinstance(value, bool):
            return None
        if isinstance(value, int | float):
            return float(value)
        if isinstance(value, str) and value.strip():
            try:
                return float(value.strip())
            except ValueError:
                return None
        return None

    def _stringify_structure(self, value: object) -> str | None:
        if value is None:
            return None
        if isinstance(value, str):
            return value
        try:
            return json.dumps(value, ensure_ascii=False)
        except TypeError:
            return str(value)

    def _extract_audio_url(self, payload: dict) -> str | None:
        def _maybe_playable_url(value: str, *, field: str) -> str | None:
            v = value.strip()
            if v.startswith("http://") or v.startswith("https://"):
                return v
            # output_format=hex 时，`audio` 往往是超长 hex 字符串，不应当作 URL 透传给客户端播放器
            if field == "audio" and self.output_format == "hex":
                return None
            if field == "audio" and self.output_format == "url":
                # 文档/实现偶发仍把 url 放在 audio 字段里
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
        # 避免把 hex 误当成 URL：这里不再递归兜底匹配 audio
        return self._pick_first_str(payload, {"audio_url", "preview_url", "audioUrl"})

    def _extract_task_id(self, payload: dict) -> str | None:
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
        return self._pick_first_str(payload, {"task_id", "id", "taskId"})

    def _raise_if_base_resp_error(self, payload: object, context: str) -> None:
        if not isinstance(payload, dict):
            return
        base_resp = payload.get("base_resp")
        if not isinstance(base_resp, dict):
            return

        status_code = base_resp.get("status_code")
        status_msg = base_resp.get("status_msg")

        # MiniMax 常见约定：0 表示成功；部分接口也可能省略 status_code。
        if status_code is None:
            return

        try:
            code_int = int(status_code)
        except (TypeError, ValueError):
            return

        if code_int == 0:
            return

        hint = ""
        if code_int == 2061:
            hint = (
                " 提示：多为「当前套餐/Token 不含该 model」。"
                "翻唱生成可在服务器 .env 设置 MINIMAX_COVER_GENERATE_MODEL（例如改为 music-cover，需与官方文档及你账号权限一致）后重启后端。"
            )

        raise RuntimeError(
            f"MiniMax API 错误（{context}）: status_code={code_int}, status_msg={status_msg!r}, raw={payload}{hint}"
        )

    def _pick_first_str(self, obj: object, keys: set[str]) -> str | None:
        if isinstance(obj, dict):
            for k, v in obj.items():
                if k in keys and isinstance(v, str) and v.strip():
                    return v
                nested = self._pick_first_str(v, keys)
                if nested:
                    return nested
        elif isinstance(obj, list):
            for item in obj:
                nested = self._pick_first_str(item, keys)
                if nested:
                    return nested
        return None
