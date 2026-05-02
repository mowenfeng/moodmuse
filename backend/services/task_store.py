from __future__ import annotations

from dataclasses import dataclass, asdict
from threading import Lock
from typing import Any


@dataclass
class MusicTask:
    task_id: str
    status: str = "generating"
    title: str | None = None
    audio_url: str | None = None
    preview_url: str | None = None
    is_export_paid: bool = False
    prompt: str | None = None
    error_message: str | None = None

    def to_dict(self) -> dict[str, Any]:
        return asdict(self)


class TaskStore:
    def __init__(self) -> None:
        self._data: dict[str, MusicTask] = {}
        self._lock = Lock()

    def create(self, task: MusicTask) -> None:
        with self._lock:
            self._data[task.task_id] = task

    def get(self, task_id: str) -> MusicTask | None:
        with self._lock:
            return self._data.get(task_id)

    def update(self, task_id: str, **kwargs: Any) -> MusicTask | None:
        with self._lock:
            task = self._data.get(task_id)
            if not task:
                return None
            for key, value in kwargs.items():
                setattr(task, key, value)
            return task
