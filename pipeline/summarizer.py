"""Bilingual (EN/VI) AI summarizer using Gemini API with strict length enforcement."""

from __future__ import annotations

import os
import re
import time
from typing import Dict, List, Optional, Sequence

from pydantic import BaseModel, Field

from config import (
    DEFAULT_GEMINI_MODELS,
    MAX_ARTICLES_PER_TOPIC,
    MAX_KEY_POINTS,
    MAX_KEY_POINT_CHARS,
    MAX_SUMMARY_CHARS,
    MAX_TITLE_CHARS,
    MAX_WHY_MATTERS_CHARS,
    TopicConfig,
)
from rss_fetcher import RawArticle


class SummarizedArticlePayload(BaseModel):
    """Structured output returned by Gemini for one selected news item."""

    candidate_index: int = Field(description="0-based index of the selected candidate article.")
    title_en: str = Field(description="Concise English headline (max 110 chars).")
    title_vi: str = Field(description="Natural, accurate Vietnamese headline (max 110 chars).")
    summary_en: str = Field(
        description="2 short paragraphs summarizing the event in English (90-130 words, max 750 chars)."
    )
    summary_vi: str = Field(
        description="2 short paragraphs summarizing the event in Vietnamese (100-145 words, max 750 chars)."
    )
    key_points_en: List[str] = Field(
        description="Exactly 3 crisp bullet takeaways in English (each max 140 chars)."
    )
    key_points_vi: List[str] = Field(
        description="Exactly 3 crisp bullet takeaways in Vietnamese (each max 140 chars)."
    )
    why_it_matters_en: str = Field(
        description="1 short sentence explaining global significance in English (max 200 chars)."
    )
    why_it_matters_vi: str = Field(
        description="1 short sentence explaining global significance in Vietnamese (max 200 chars)."
    )


class TopicDigestResponse(BaseModel):
    """Structured response containing up to 10 top bilingual summaries for a topic."""

    topic_overview_en: str = Field(description="1-2 sentence daily overview for this topic in English.")
    topic_overview_vi: str = Field(description="1-2 sentence daily overview for this topic in Vietnamese.")
    articles: List[SummarizedArticlePayload] = Field(
        description="Up to 10 most significant distinct events selected from the candidates."
    )


class BilingualArticle(BaseModel):
    """Final serialized article stored in daily_digest.json and read by Android app."""

    id: str
    topic_id: str
    rank: int
    source_name: str
    source_url: str
    image_url: Optional[str] = None
    published_at: str
    read_minutes: int
    title_en: str
    title_vi: str
    summary_en: str
    summary_vi: str
    key_points_en: List[str]
    key_points_vi: List[str]
    why_it_matters_en: str
    why_it_matters_vi: str


class TopicDigest(BaseModel):
    """Serialized topic section containing at most 10 bilingual articles."""

    id: str
    name_en: str
    name_vi: str
    description_en: str
    description_vi: str
    accent_hex: str
    overview_en: str
    overview_vi: str
    article_count: int
    articles: List[BilingualArticle]


def truncate_cleanly(text: str, max_chars: int) -> str:
    """Truncate text within max_chars at sentence or word boundaries."""
    cleaned = re.sub(r"[ \t]+", " ", (text or "").strip())
    cleaned = re.sub(r"\n{3,}", "\n\n", cleaned)
    if len(cleaned) <= max_chars:
        return cleaned

    slice_ = cleaned[: max_chars - 1]
    # Prefer ending at the last complete sentence if it preserves most of the text.
    last_sentence = max(slice_.rfind("."), slice_.rfind("!"), slice_.rfind("?"))
    if last_sentence >= int(max_chars * 0.65):
        return slice_[: last_sentence + 1].strip()

    last_space = slice_.rfind(" ")
    if last_space >= int(max_chars * 0.5):
        return slice_[:last_space].rstrip(" ,;:-") + "…"
    return slice_.strip() + "…"


def estimate_read_minutes(summary_en: str, summary_vi: str, key_points_en: Sequence[str], why_en: str) -> int:
    """Estimate compact reading time; bounded to 1-2 minutes for <=2 mobile pages."""
    total_words = len((summary_en + " " + " ".join(key_points_en) + " " + why_en).split())
    return 1 if total_words <= 175 else 2


def enforce_article_budget(
    article_id: str,
    topic_id: str,
    rank: int,
    candidate: RawArticle,
    payload: SummarizedArticlePayload,
) -> BilingualArticle:
    """Enforce hard length limits so every article stays <= 2 mobile pages (excluding images)."""
    title_en = truncate_cleanly(payload.title_en or candidate.title, MAX_TITLE_CHARS)
    title_vi = truncate_cleanly(payload.title_vi or candidate.title, MAX_TITLE_CHARS)

    summary_en = truncate_cleanly(payload.summary_en or candidate.raw_summary or candidate.title, MAX_SUMMARY_CHARS)
    summary_vi = truncate_cleanly(payload.summary_vi or summary_en, MAX_SUMMARY_CHARS)

    kp_en = [truncate_cleanly(item, MAX_KEY_POINT_CHARS) for item in payload.key_points_en if item.strip()][:MAX_KEY_POINTS]
    kp_vi = [truncate_cleanly(item, MAX_KEY_POINT_CHARS) for item in payload.key_points_vi if item.strip()][:MAX_KEY_POINTS]

    if not kp_en:
        kp_en = [truncate_cleanly(title_en, MAX_KEY_POINT_CHARS)]
    if not kp_vi:
        kp_vi = [truncate_cleanly(title_vi, MAX_KEY_POINT_CHARS)]

    why_en = truncate_cleanly(
        payload.why_it_matters_en or "Follow the source report for evolving developments.",
        MAX_WHY_MATTERS_CHARS,
    )
    why_vi = truncate_cleanly(
        payload.why_it_matters_vi or "Theo dõi bản tin gốc để cập nhật thêm diễn biến mới.",
        MAX_WHY_MATTERS_CHARS,
    )

    return BilingualArticle(
        id=article_id,
        topic_id=topic_id,
        rank=rank,
        source_name=candidate.source_name,
        source_url=candidate.url,
        image_url=candidate.image_url,
        published_at=candidate.published_at.isoformat(),
        read_minutes=estimate_read_minutes(summary_en, summary_vi, kp_en, why_en),
        title_en=title_en,
        title_vi=title_vi,
        summary_en=summary_en,
        summary_vi=summary_vi,
        key_points_en=kp_en,
        key_points_vi=kp_vi,
        why_it_matters_en=why_en,
        why_it_matters_vi=why_vi,
    )


def _build_prompt(topic: TopicConfig, candidates: Sequence[RawArticle], max_articles: int) -> str:
    lines = [
        f"You are an expert bilingual international news editor producing a daily mobile briefing for '{topic.name_en}' ({topic.name_vi}).",
        f"From the {len(candidates)} candidate articles below, select at most {max_articles} of the most important, distinct, high-impact events.",
        "",
        "STRICT RULES:",
        f"1. Select at most {max_articles} unique events (no duplicate stories).",
        "2. Use ONLY facts present in the candidate headlines and summaries. Do NOT invent figures, dates, or quotes.",
        "3. Every article is displayed in PARALLEL English + Vietnamese on a smartphone and MUST stay concise (<= 2 mobile pages total without images):",
        f"   - title_en / title_vi: <= {MAX_TITLE_CHARS} chars, crisp journalistic headline.",
        f"   - summary_en / summary_vi: 2 short paragraphs (around 90-130 words, strictly <= {MAX_SUMMARY_CHARS} chars each).",
        f"   - key_points_en / key_points_vi: exactly 3 concise bullet points (each <= {MAX_KEY_POINT_CHARS} chars).",
        f"   - why_it_matters_en / why_it_matters_vi: 1 clear sentence (<= {MAX_WHY_MATTERS_CHARS} chars).",
        "4. Write Vietnamese in natural, accurate journalistic prose (Standard Vietnamese with proper diacritics).",
        "5. Provide a brief 1-2 sentence topic_overview_en and topic_overview_vi summarizing today's biggest theme in this category.",
        "",
        "CANDIDATES:",
    ]

    for idx, item in enumerate(candidates):
        snippet = (item.raw_summary or item.title).replace("\n", " ")[:700]
        lines.append(
            f"[{idx}] Source: {item.source_name} | Published: {item.published_at.strftime('%Y-%m-%d %H:%M UTC')}\n"
            f"Title: {item.title}\n"
            f"Excerpt: {snippet}\n"
        )

    return "\n".join(lines)


def _fallback_summarize_topic(
    topic: TopicConfig,
    candidates: Sequence[RawArticle],
    max_articles: int,
) -> TopicDigest:
    """Deterministic fallback when GEMINI_API_KEY is missing or API fails."""
    selected = list(candidates[:max_articles])
    articles: List[BilingualArticle] = []

    for rank, cand in enumerate(selected, start=1):
        base_summary = cand.raw_summary or cand.title
        sentences = [s.strip() for s in re.split(r"(?<=[.!?])\s+", base_summary) if len(s.strip()) > 20]
        lead = " ".join(sentences[:3]) if sentences else base_summary

        payload = SummarizedArticlePayload(
            candidate_index=rank - 1,
            title_en=cand.title,
            title_vi=f"[EN-Source] {cand.title}",
            summary_en=lead,
            summary_vi=(
                f"Bản tin từ {cand.source_name}: {lead} "
                "(Chưa cấu hình GEMINI_API_KEY nên phần tóm tắt tiếng Việt đang hiển thị bản trích xuất gốc.)"
            ),
            key_points_en=sentences[:3] if sentences else [cand.title],
            key_points_vi=[
                f"Nguồn tin: {cand.source_name}",
                "Cần gắn GEMINI_API_KEY để kích hoạt dịch và tóm tắt AI tiếng Việt.",
            ],
            why_it_matters_en=f"Reported by {cand.source_name} under {topic.name_en}.",
            why_it_matters_vi=f"Tin nổi bật thuộc chuyên đề {topic.name_vi} từ {cand.source_name}.",
        )
        articles.append(enforce_article_budget(cand.id, topic.id, rank, cand, payload))

    return TopicDigest(
        id=topic.id,
        name_en=topic.name_en,
        name_vi=topic.name_vi,
        description_en=topic.description_en,
        description_vi=topic.description_vi,
        accent_hex=topic.accent_hex,
        overview_en=f"Top {len(articles)} {topic.name_en} updates curated from verified RSS feeds.",
        overview_vi=f"Tổng hợp {len(articles)} tin nổi bật chuyên đề {topic.name_vi}.",
        article_count=len(articles),
        articles=articles,
    )


def summarize_topic_with_gemini(
    topic: TopicConfig,
    candidates: Sequence[RawArticle],
    api_key: Optional[str] = None,
    models: Sequence[str] = DEFAULT_GEMINI_MODELS,
    max_articles: int = MAX_ARTICLES_PER_TOPIC,
    client_override: Optional[object] = None,
) -> TopicDigest:
    """Summarize and translate up to 10 top events for a topic via Gemini API."""
    if not candidates:
        return TopicDigest(
            id=topic.id,
            name_en=topic.name_en,
            name_vi=topic.name_vi,
            description_en=topic.description_en,
            description_vi=topic.description_vi,
            accent_hex=topic.accent_hex,
            overview_en=f"No fresh stories available for {topic.name_en} in this run.",
            overview_vi=f"Chưa có tin mới cho chuyên đề {topic.name_vi} trong lần cập nhật này.",
            article_count=0,
            articles=[],
        )

    effective_key = api_key if api_key is not None else os.environ.get("GEMINI_API_KEY", "").strip()
    if not effective_key and client_override is None:
        print(f"[info] No GEMINI_API_KEY found; using extractive fallback for {topic.id}.")
        return _fallback_summarize_topic(topic, candidates, max_articles=max_articles)

    from google import genai
    from google.genai import types

    client = client_override or genai.Client(api_key=effective_key)
    prompt = _build_prompt(topic, candidates, max_articles=max_articles)

    last_error: Optional[Exception] = None
    parsed_response: Optional[TopicDigestResponse] = None

    for model_name in models:
        for attempt in range(2):
            try:
                response = client.models.generate_content(
                    model=model_name,
                    contents=prompt,
                    config=types.GenerateContentConfig(
                        temperature=0.25,
                        response_mime_type="application/json",
                        response_schema=TopicDigestResponse,
                    ),
                )
                if getattr(response, "parsed", None) is not None and isinstance(response.parsed, TopicDigestResponse):
                    parsed_response = response.parsed
                else:
                    parsed_response = TopicDigestResponse.model_validate_json(response.text)
                print(f"[ok] {topic.id}: summarized with {model_name}")
                break
            except Exception as exc:
                last_error = exc
                msg = str(exc).lower()
                is_rate_limit = "429" in msg or "rate" in msg or "quota" in msg or "resource_exhausted" in msg
                if is_rate_limit and attempt == 0:
                    print(f"[warn] {topic.id} model {model_name} rate-limited; retrying briefly...")
                    time.sleep(4)
                    continue
                print(f"[warn] {topic.id} model {model_name} failed: {type(exc).__name__}: {exc}")
                break
        if parsed_response is not None:
            break

    if parsed_response is None:
        print(f"[warn] Falling back to extractive summary for {topic.id}: {last_error}")
        return _fallback_summarize_topic(topic, candidates, max_articles=max_articles)

    articles: List[BilingualArticle] = []
    used_indices: set[int] = set()
    for item in parsed_response.articles:
        idx = item.candidate_index
        if idx < 0 or idx >= len(candidates) or idx in used_indices:
            continue
        used_indices.add(idx)
        cand = candidates[idx]
        rank = len(articles) + 1
        articles.append(enforce_article_budget(cand.id, topic.id, rank, cand, item))
        if len(articles) >= max_articles:
            break

    # If Gemini returned 0 valid mapped items, gracefully fall back rather than emitting empty feed.
    if not articles:
        return _fallback_summarize_topic(topic, candidates, max_articles=max_articles)

    return TopicDigest(
        id=topic.id,
        name_en=topic.name_en,
        name_vi=topic.name_vi,
        description_en=topic.description_en,
        description_vi=topic.description_vi,
        accent_hex=topic.accent_hex,
        overview_en=truncate_cleanly(parsed_response.topic_overview_en, 320),
        overview_vi=truncate_cleanly(parsed_response.topic_overview_vi, 320),
        article_count=len(articles),
        articles=articles,
    )
