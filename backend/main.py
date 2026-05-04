from __future__ import annotations

import asyncio
from uuid import uuid4

from fastapi import FastAPI, HTTPException, status
from fastapi.middleware.cors import CORSMiddleware

from models.schemas import (
    CoverGenerateRequest,
    CoverGenerateResponse,
    CoverPreprocessRequest,
    CoverPreprocessResponse,
    DownloadResponse,
    GenerateRequest,
    GenerateResponse,
    MockPayResponse,
    TaskResponse,
)
from services.minimax_music import MiniMaxMusicService
from services.prompt_builder import build_music_prompt
from services.task_store import MusicTask, TaskStore

app = FastAPI(title="MoodMuse AI Backend", version="0.1.0")
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

task_store = TaskStore()
minimax_service = MiniMaxMusicService()


@app.get("/health")
def health() -> dict[str, str]:
    return {"status": "ok"}


@app.post("/api/generate", response_model=GenerateResponse)
async def generate_music(payload: GenerateRequest) -> GenerateResponse:
    task_id = uuid4().hex
    prompt = build_music_prompt(payload.emotion_text, payload.style)
    task_store.create(MusicTask(task_id=task_id, status="generating", prompt=prompt))

    asyncio.create_task(_generate_in_background(task_id, prompt, payload.duration))
    return GenerateResponse(task_id=task_id, status="generating")


async def _generate_in_background(task_id: str, prompt: str, duration: int) -> None:
    try:
        if minimax_service.use_mock:
            await asyncio.sleep(3)
        result = minimax_service.create_generation(prompt=prompt, duration=duration)
        err = None
        if result.get("status") == "failed":
            meta = result.get("meta") or {}
            raw = meta.get("raw") if isinstance(meta, dict) else None
            if isinstance(raw, dict):
                base_resp = raw.get("base_resp")
                if isinstance(base_resp, dict):
                    err = f"MiniMax: status_code={base_resp.get('status_code')}, status_msg={base_resp.get('status_msg')}"
                else:
                    err = f"MiniMax failed: {raw}"
            else:
                err = "MiniMax failed"
        task_store.update(
            task_id,
            status=result["status"],
            title=result["title"],
            audio_url=result["audio_url"],
            preview_url=result["preview_url"],
            error_message=err,
        )
    except Exception as e:
        task_store.update(task_id, status="failed", error_message=str(e)[:300])


@app.get("/api/tasks/{task_id}", response_model=TaskResponse)
def query_task(task_id: str) -> TaskResponse:
    task = task_store.get(task_id)
    if task is None:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="task not found")
    return TaskResponse(**task.to_dict())


@app.post("/api/tasks/{task_id}/mock-pay", response_model=MockPayResponse)
def mock_pay(task_id: str) -> MockPayResponse:
    task = task_store.update(task_id, is_export_paid=True)
    if task is None:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="task not found")
    return MockPayResponse(task_id=task_id, is_export_paid=True)


@app.get("/api/tasks/{task_id}/download", response_model=DownloadResponse)
def download(task_id: str) -> DownloadResponse:
    task = task_store.get(task_id)
    if task is None:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="task not found")
    if not task.is_export_paid:
        raise HTTPException(status_code=status.HTTP_403_FORBIDDEN, detail="payment required")
    if not task.audio_url:
        raise HTTPException(status_code=status.HTTP_409_CONFLICT, detail="audio not ready")
    return DownloadResponse(task_id=task_id, download_url=task.audio_url)


@app.post("/api/cover/preprocess", response_model=CoverPreprocessResponse)
def cover_preprocess(payload: CoverPreprocessRequest) -> CoverPreprocessResponse:
    try:
        result = minimax_service.cover_preprocess(
            audio_url=payload.audio_url,
            audio_base64=payload.audio_base64,
        )
        return CoverPreprocessResponse(
            cover_feature_id=result.get("cover_feature_id"),
            formatted_lyrics=result.get("formatted_lyrics"),
            audio_duration=result.get("audio_duration"),
            structure_result=result.get("structure_result"),
            dtw_result=result.get("dtw_result"),
            beat_result=result.get("beat_result"),
            raw=result.get("raw") if isinstance(result.get("raw"), dict) else None,
        )
    except Exception as e:
        raise HTTPException(status_code=status.HTTP_502_BAD_GATEWAY, detail=str(e)[:800]) from e


@app.post("/api/cover/generate", response_model=CoverGenerateResponse)
def cover_generate(payload: CoverGenerateRequest) -> CoverGenerateResponse:
    # https://platform.minimaxi.com/docs/api-reference/music-generation — music-cover / music-cover-free + cover_feature_id 时 lyrics 必填 [10,1000]；prompt [10,300]
    pl = payload.prompt.strip()
    ly = payload.lyrics.strip()
    if len(pl) < 10 or len(pl) > 300:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail="prompt 需符合 MiniMax 翻唱要求：长度 [10, 300] 字符（当前 {}）".format(len(pl)),
        )
    if len(ly) < 10 or len(ly) > 1000:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail="使用 cover_feature_id 时 lyrics 必填：长度 [10, 1000] 字符（当前 {}）".format(len(ly)),
        )
    try:
        result = minimax_service.cover_generate(
            prompt=payload.prompt.strip(),
            lyrics=payload.lyrics.strip(),
            cover_feature_id=payload.cover_feature_id.strip(),
            audio_duration=payload.audio_duration,
            dtw_result=payload.dtw_result,
            beat_result=payload.beat_result,
            structure_result=payload.structure_result,
        )
        return CoverGenerateResponse(
            audio_url=result.get("audio_url"),
            preview_url=result.get("preview_url"),
            raw=result.get("raw") if isinstance(result.get("raw"), dict) else None,
        )
    except Exception as e:
        raise HTTPException(status_code=status.HTTP_502_BAD_GATEWAY, detail=str(e)[:800]) from e
