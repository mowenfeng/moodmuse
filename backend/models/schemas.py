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
