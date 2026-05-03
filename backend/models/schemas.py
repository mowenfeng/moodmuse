from pydantic import BaseModel, Field


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
    audio_url: str = Field(min_length=1)


class CoverPreprocessResponse(BaseModel):
    cover_feature_id: str | None = None
    formatted_lyrics: str | None = None
    audio_duration: float | None = None
    structure_result: str | None = None
    raw: dict | None = None


class CoverGenerateRequest(BaseModel):
    prompt: str = Field(min_length=1)
    lyrics: str = Field(min_length=1)
    cover_feature_id: str = Field(min_length=1)


class CoverGenerateResponse(BaseModel):
    audio_url: str | None = None
    preview_url: str | None = None
    raw: dict | None = None
