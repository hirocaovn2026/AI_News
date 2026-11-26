"""CLI entrypoint to generate daily bilingual news digest JSON files."""

from __future__ import annotations

import argparse
from datetime import datetime, timedelta, timezone
import json
from pathlib import Path
import shutil
import sys
import time
from typing import Dict, List, Optional

from pydantic import BaseModel
import requests

# Ensure sibling imports work whether invoked from repo root or pipeline/
PIPELINE_DIR = Path(__file__).resolve().parent
if str(PIPELINE_DIR) not in sys.path:
    sys.path.insert(0, str(PIPELINE_DIR))

from config import (
    ARCHIVE_RETENTION_DAYS,
    MAX_ARTICLES_PER_TOPIC,
    MAX_CANDIDATES_PER_TOPIC,
    TOPICS,
    TOPICS_BY_ID,
)
from rss_fetcher import fetch_topic_candidates
from summarizer import TopicDigest, summarize_topic_with_gemini


VIETNAM_TZ = timezone(timedelta(hours=7))


class DailyDigest(BaseModel):
    """Top-level JSON document consumed by the Android app."""

    schema_version: int = 1
    date: str
    generated_at: str
    max_articles_per_topic: int = MAX_ARTICLES_PER_TOPIC
    total_articles: int
    topics: List[TopicDigest]


class ManifestEntry(BaseModel):
    """Entry inside data/manifest.json describing an archived daily edition."""

    date: str
    generated_at: str
    total_articles: int
    path: str


class DigestManifest(BaseModel):
    """Index of latest and archived daily editions."""

    latest_date: str
    updated_at: str
    editions: List[ManifestEntry]


def build_daily_digest(
    date_str: Optional[str] = None,
    max_per_topic: int = MAX_ARTICLES_PER_TOPIC,
    use_llm: bool = True,
    topic_ids: Optional[List[str]] = None,
) -> DailyDigest:
    """Fetch feeds for selected topics and produce a validated DailyDigest."""
    now_utc = datetime.now(timezone.utc)
    edition_date = date_str or now_utc.astimezone(VIETNAM_TZ).strftime("%Y-%m-%d")

    selected_topics = (
        [TOPICS_BY_ID[tid] for tid in topic_ids if tid in TOPICS_BY_ID]
        if topic_ids
        else list(TOPICS)
    )

    session = requests.Session()
    topic_digests: List[TopicDigest] = []

    for idx, topic in enumerate(selected_topics):
        candidates = fetch_topic_candidates(
            topic=topic,
            session=session,
            now=now_utc,
            max_candidates=max(max_per_topic + 6, MAX_CANDIDATES_PER_TOPIC),
        )
        print(f"[info] {topic.id}: fetched {len(candidates)} ranked candidates")

        digest = summarize_topic_with_gemini(
            topic=topic,
            candidates=candidates,
            api_key=None if use_llm else "",
            max_articles=min(max_per_topic, MAX_ARTICLES_PER_TOPIC),
        )
        topic_digests.append(digest)
        if use_llm and idx < len(selected_topics) - 1:
            time.sleep(1.5)

    total_articles = sum(t.article_count for t in topic_digests)
    return DailyDigest(
        schema_version=1,
        date=edition_date,
        generated_at=now_utc.isoformat(),
        max_articles_per_topic=min(max_per_topic, MAX_ARTICLES_PER_TOPIC),
        total_articles=total_articles,
        topics=topic_digests,
    )


def write_digest_outputs(
    digest: DailyDigest,
    output_dir: Path,
    android_asset_path: Optional[Path] = None,
) -> None:
    """Persist daily_digest.json, dated archive snapshot, and manifest.json."""
    output_dir.mkdir(parents=True, exist_ok=True)
    archive_dir = output_dir / "archive"
    archive_dir.mkdir(parents=True, exist_ok=True)

    payload_json = digest.model_dump_json(indent=2)
    latest_file = output_dir / "daily_digest.json"
    archive_file = archive_dir / f"{digest.date}.json"

    latest_file.write_text(payload_json, encoding="utf-8")
    archive_file.write_text(payload_json, encoding="utf-8")

    # Rebuild manifest from archive files, keeping latest ARCHIVE_RETENTION_DAYS editions.
    archive_files = sorted(archive_dir.glob("*.json"), reverse=True)
    for stale in archive_files[ARCHIVE_RETENTION_DAYS:]:
        stale.unlink(missing_ok=True)

    editions: List[ManifestEntry] = []
    for path in archive_files[:ARCHIVE_RETENTION_DAYS]:
        try:
            data = json.loads(path.read_text(encoding="utf-8"))
            editions.append(
                ManifestEntry(
                    date=data["date"],
                    generated_at=data["generated_at"],
                    total_articles=int(data.get("total_articles", 0)),
                    path=f"archive/{path.name}",
                )
            )
        except Exception:
            continue

    manifest = DigestManifest(
        latest_date=digest.date,
        updated_at=digest.generated_at,
        editions=editions,
    )
    (output_dir / "manifest.json").write_text(manifest.model_dump_json(indent=2), encoding="utf-8")

    if android_asset_path is not None:
        android_asset_path.parent.mkdir(parents=True, exist_ok=True)
        shutil.copyfile(latest_file, android_asset_path)


def parse_args(argv: Optional[List[str]] = None) -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Generate daily bilingual AI/Space/Climate/Conflict news digest.")
    parser.add_argument("--output-dir", type=Path, default=Path("data"), help="Directory for output JSON files.")
    parser.add_argument("--date", type=str, default=None, help="Edition date (YYYY-MM-DD). Defaults to today (ICT).")
    parser.add_argument(
        "--max-per-topic",
        type=int,
        default=MAX_ARTICLES_PER_TOPIC,
        help="Max stories per topic (capped at 10).",
    )
    parser.add_argument(
        "--no-llm",
        action="store_true",
        help="Disable Gemini API calls and generate extractive fallback summaries.",
    )
    parser.add_argument(
        "--sync-android-asset",
        type=Path,
        default=None,
        help="Optional path to copy daily_digest.json as bundled Android fallback asset.",
    )
    return parser.parse_args(argv)


def main(argv: Optional[List[str]] = None) -> int:
    args = parse_args(argv)
    digest = build_daily_digest(
        date_str=args.date,
        max_per_topic=min(args.max_per_topic, MAX_ARTICLES_PER_TOPIC),
        use_llm=not args.no_llm,
    )
    write_digest_outputs(
        digest=digest,
        output_dir=args.output_dir,
        android_asset_path=args.sync_android_asset,
    )
    print(f"[done] Wrote {digest.total_articles} articles across {len(digest.topics)} topics to {args.output_dir}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
