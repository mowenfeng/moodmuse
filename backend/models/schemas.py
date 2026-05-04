from typing import Any

from pydantic import BaseModel, Field, model_validator


class GenerateRequest(BaseModel):
    emotion_text: str = Field(min_length=1)
    style: str = "lofi"
    duration: int = Field(default=30, ge=10, le=180)


class GenerateResponse(BaseModel):
    task_id: str
    status: str


class TaskResponse(BaseModel):
    task_id: str
    status: str
    title: str | None = None
    audio_url: str | None = None
    preview_url: str | None = None
    is_export_paid: bool = False
    error_message: str | None = None


class MockPayResponse(BaseModel):
    task_id: str
    is_export_paid: bool


class DownloadResponse(BaseModel):
    task_id: str
    download_url: str


class CoverPreprocessRequest(BaseModel):
    """与 MiniMax 一致：`audio_url` 与 `audio_base64` 二选一。"""

    audio_url: str | None = None
    audio_base64: str | None = None

    @model_validator(mode="after")
    def exactly_one_audio_source(self) -> "CoverPreprocessRequest":
        u = (self.audio_url or "").strip()
        b = (self.audio_base64 or "").strip()
        if not u and not b:
            raise ValueError("必须提供 audio_url 或 audio_base64 之一")
        if u and b:
            raise ValueError("audio_url 与 audio_base64 只能二选一")
        self.audio_url = u or None
        self.audio_base64 = b or None
        return self


class CoverPreprocessResponse(BaseModel):
    cover_feature_id: str | None = None
    formatted_lyrics: str | None = None
    audio_duration: float | None = None
    structure_result: str | None = None
    # music-cover 第二步有时需要（由预处理接口返回，字段名因网关可能略有差异）
    dtw_result: Any | None = None
    beat_result: Any | None = None
    raw: dict | None = None


class CoverGenerateRequest(BaseModel):
    prompt: str = Field(min_length=1)
    lyrics: str = Field(min_length=1)
    cover_feature_id: str = Field(min_length=1)
    audio_duration: float | None = None
    dtw_result: Any | None = None
    beat_result: Any | None = None
    # 预处理返回的 structure_result（JSON 字符串）；网关未给 dtw/beat 时由服务端用于兼容填参
    structure_result: str | None = None


class CoverGenerateResponse(BaseModel):
    audio_url: str | None = None
    preview_url: str | None = None
    raw: dict | None = None
