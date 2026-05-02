from __future__ import annotations

EMOTION_MAPPING = {
    "孤独": "lonely, intimate, reflective mood",
    "开心": "happy, uplifting, bright energy",
    "失恋": "heartbroken, bittersweet, emotional depth",
    "治愈": "healing, warm, comforting atmosphere",
    "夜晚开车": "night drive, neon city lights, steady groove",
    "电影感": "cinematic, dramatic arc, wide ambience",
    "下雨": "rainy night, soft textures, melancholic warmth",
}


def build_music_prompt(emotion_text: str, style: str) -> str:
    matched_tags: list[str] = []
    for keyword, prompt in EMOTION_MAPPING.items():
        if keyword in emotion_text:
            matched_tags.append(prompt)

    if not matched_tags:
        matched_tags.append("emotional, expressive, story-driven mood")

    return (
        f"{style} music, "
        f"{', '.join(matched_tags)}, "
        "slow to medium tempo, soft piano, ambient texture, cinematic atmosphere"
    )
