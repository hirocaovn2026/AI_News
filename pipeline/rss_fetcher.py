"""RSS and Atom news fetching, cleaning, deduplication, and ranking."""

from __future__ import annotations

from concurrent.futures import ThreadPoolExecutor
from dataclasses import dataclass
from datetime import datetime, timedelta, timezone
from email.utils import parsedate_to_datetime
import hashlib
import html
import re
from typing import Iterable, List, Optional, Sequence, Tuple
from urllib.parse import urlparse
import xml.etree.ElementTree as ET

from bs4 import BeautifulSoup
import requests

from config import (
    LOOKBACK_HOURS,
    MAX_CANDIDATES_PER_TOPIC,
    REQUEST_TIMEOUT_SECONDS,
    FeedSource,
    TopicConfig,
    USER_AGENT,
)


ATOM_NS = "{http://www.w3.org/2005/Atom}"
MEDIA_NS = "{http://search.yahoo.com/mrss/}"
CONTENT_NS = "{http://purl.org/rss/1.0/modules/content/}"

WORD_RE = re.compile(r"[a-z0-9]+")
WHITESPACE_RE = re.compile(r"\s+")
STOPWORDS = {
    "a", "an", "and", "are", "as", "at", "be", "by", "for", "from", "in",
    "into", "is", "it", "of", "on", "or", "that", "the", "to", "with", "after",
    "over", "new", "how", "why", "what", "says", "said", "amid",
}

BLOCKED_SOURCES = {
    "facebook", "instagram", "tiktok", "reddit", "youtube", "linkedin",
    "pinterest", "x", "twitter", "threads",
}

BOILERPLATE_PHRASES = (
    "posts from this topic will be added to your daily email digest",
    "this is today’s edition of the download",
    "this is today's edition of the download",
    "our weekday newsletter that provides a daily dose",
    "sign up for our newsletter",
    "subscribe to continue reading",
)


@dataclass
class RawArticle:
    """Normalized article entry extracted from RSS/Atom feeds."""

    id: str
    topic_id: str
    title: str
    url: str
    source_name: str
    published_at: datetime
    raw_summary: str
    image_url: Optional[str] = None
    authority_weight: float = 1.0
    score: float = 0.0


def clean_html(raw_html: str) -> str:
    """Strip HTML tags cleanly without triggering filename heuristics on plain text."""
    if not raw_html:
        return ""
    if "<" not in raw_html and ">" not in raw_html:
        text = html.unescape(raw_html)
    else:
        soup = BeautifulSoup(raw_html, "lxml")
        for tag in soup(["script", "style", "noscript"]):
            tag.decompose()
        text = soup.get_text(separator=" ", strip=True)
    return WHITESPACE_RE.sub(" ", text).strip()


def _is_boilerplate(text: str) -> bool:
    lower = text.lower()
    return any(phrase in lower for phrase in BOILERPLATE_PHRASES)


def _strip_boilerplate_sentences(text: str) -> str:
    if not text:
        return ""
    sentences = re.split(r"(?<=[.!?])\s+", text)
    kept = [s.strip() for s in sentences if s.strip() and not _is_boilerplate(s)]
    return WHITESPACE_RE.sub(" ", " ".join(kept)).strip()


def parse_datetime(value: Optional[str], fallback: Optional[datetime] = None) -> datetime:
    """Parse RSS RFC-2822 or Atom ISO-8601 timestamps into UTC datetime."""
    default_dt = fallback or datetime.now(timezone.utc)
    if not value:
        return default_dt
    text = value.strip()
    try:
        dt = parsedate_to_datetime(text)
        if dt.tzinfo is None:
            dt = dt.replace(tzinfo=timezone.utc)
        return dt.astimezone(timezone.utc)
    except Exception:
        pass
    try:
        normalized = text.replace("Z", "+00:00")
        dt = datetime.fromisoformat(normalized)
        if dt.tzinfo is None:
            dt = dt.replace(tzinfo=timezone.utc)
        return dt.astimezone(timezone.utc)
    except Exception:
        return default_dt


def _extract_first_image_from_html(raw_html: str) -> Optional[str]:
    if not raw_html or "<img" not in raw_html.lower():
        return None
    soup = BeautifulSoup(raw_html, "lxml")
    for img in soup.find_all("img"):
        src = (img.get("src") or "").strip()
        if not src.startswith("http"):
            continue
        lower = src.lower()
        if any(bad in lower for bad in ("pixel", "tracker", "1x1", "googleusercontent.com/s2")):
            continue
        return src
    return None


def _extract_google_news_source(title: str, default_source: str) -> Tuple[str, str]:
    """Google News titles typically end with ' - Source Name'. Separate them cleanly."""
    if " - " in title:
        head, tail = title.rsplit(" - ", 1)
        tail = tail.strip()
        if 2 <= len(tail) <= 45 and len(head.strip()) >= 15:
            return head.strip(), tail
    return title.strip(), default_source


def _find_child_text(elem: ET.Element, tags: Sequence[str]) -> str:
    for tag in tags:
        found = elem.find(tag)
        if found is not None and found.text:
            return found.text.strip()
    return ""


def _extract_image_from_entry(entry: ET.Element, description_html: str) -> Optional[str]:
    for media_tag in (f"{MEDIA_NS}content", f"{MEDIA_NS}thumbnail"):
        for node in entry.findall(media_tag):
            url = (node.attrib.get("url") or "").strip()
            medium = (node.attrib.get("medium") or "image").lower()
            if url.startswith("http") and ("image" in medium or medium == ""):
                return url

    for enclosure in entry.findall("enclosure"):
        url = (enclosure.attrib.get("url") or "").strip()
        mime = (enclosure.attrib.get("type") or "").lower()
        if url.startswith("http") and (
            mime.startswith("image/") or url.lower().endswith((".jpg", ".jpeg", ".png", ".webp"))
        ):
            return url

    return _extract_first_image_from_html(description_html)


def _normalize_url(url: str) -> str:
    parsed = urlparse(url.strip())
    return f"{parsed.scheme}://{parsed.netloc}{parsed.path}".rstrip("/")


def _title_tokens(title: str) -> set[str]:
    return {w for w in WORD_RE.findall(title.lower()) if w not in STOPWORDS and len(w) > 2}


def are_similar_titles(title_a: str, title_b: str, threshold: float = 0.55) -> bool:
    """Return True if two headlines likely describe the same news event."""
    tokens_a = _title_tokens(title_a)
    tokens_b = _title_tokens(title_b)
    if not tokens_a or not tokens_b:
        return title_a.strip().lower() == title_b.strip().lower()
    overlap = len(tokens_a & tokens_b)
    union = len(tokens_a | tokens_b)
    return (overlap / union) >= threshold


def matches_keywords(title: str, summary: str, keywords: Tuple[str, ...]) -> bool:
    """Check if an entry matches at least one topic keyword when feed is general."""
    if not keywords:
        return True
    haystack = f" {title} {summary} ".lower()
    if "business climate" in haystack or "investment climate" in haystack or "political climate" in haystack:
        # Avoid false positives when filtering climate feeds unless climate science terms also exist.
        if not any(term in haystack for term in ("climate change", "global warming", "emissions", "carbon")):
            return False
    for kw in keywords:
        kw_lower = kw.lower()
        if " " in kw_lower:
            if kw_lower in haystack:
                return True
        else:
            if re.search(rf"\b{re.escape(kw_lower)}\b", haystack):
                return True
    return False


def parse_feed_xml(
    xml_bytes: bytes,
    feed: FeedSource,
    topic_id: str,
    now: Optional[datetime] = None,
) -> List[RawArticle]:
    """Parse an RSS 2.0 or Atom XML payload into RawArticle items."""
    reference_now = now or datetime.now(timezone.utc)
    root = ET.fromstring(xml_bytes)

    entries = root.findall(".//item")
    is_atom = False
    if not entries:
        entries = root.findall(f".//{ATOM_NS}entry")
        is_atom = True

    articles: List[RawArticle] = []
    for entry in entries:
        if is_atom:
            raw_title = _find_child_text(entry, [f"{ATOM_NS}title"])
            link = ""
            for link_el in entry.findall(f"{ATOM_NS}link"):
                rel = link_el.attrib.get("rel", "alternate")
                href = (link_el.attrib.get("href") or "").strip()
                if rel == "alternate" and href:
                    link = href
                    break
                if not link and href:
                    link = href
            raw_pub = _find_child_text(entry, [f"{ATOM_NS}published", f"{ATOM_NS}updated"])
            raw_desc = _find_child_text(entry, [f"{ATOM_NS}summary", f"{ATOM_NS}content"])
        else:
            raw_title = _find_child_text(entry, ["title"])
            link = _find_child_text(entry, ["link", "guid"])
            raw_pub = _find_child_text(entry, ["pubDate", "{http://purl.org/dc/elements/1.1/}date"])
            raw_desc = _find_child_text(entry, [f"{CONTENT_NS}encoded", "description"])

        title = clean_html(raw_title)
        if not title or not link:
            continue

        source_name = feed.name
        if feed.is_google_news:
            title, source_name = _extract_google_news_source(title, feed.name)
            if source_name.strip().lower() in BLOCKED_SOURCES:
                continue

        summary_text = _strip_boilerplate_sentences(clean_html(raw_desc))
        if feed.is_google_news and summary_text.lower().startswith(title.lower()[:20]):
            summary_text = summary_text[:500]

        if not matches_keywords(title, summary_text, feed.filter_keywords):
            continue

        published_at = parse_datetime(raw_pub, fallback=reference_now)
        image_url = _extract_image_from_entry(entry, raw_desc)
        article_id = hashlib.sha256(
            f"{topic_id}:{_normalize_url(link)}:{title.lower()}".encode("utf-8")
        ).hexdigest()[:16]

        articles.append(
            RawArticle(
                id=article_id,
                topic_id=topic_id,
                title=title,
                url=link.strip(),
                source_name=source_name,
                published_at=published_at,
                raw_summary=summary_text[:1200],
                image_url=image_url,
                authority_weight=feed.authority_weight,
            )
        )

    return articles


def score_article(article: RawArticle, now: datetime) -> float:
    """Rank articles by recency, source authority, and richness."""
    age_hours = max(0.0, (now - article.published_at).total_seconds() / 3600.0)
    recency_score = max(0.1, 1.0 - (age_hours / 72.0))
    richness_bonus = 0.15 if len(article.raw_summary) >= 140 else 0.0
    image_bonus = 0.08 if article.image_url else 0.0
    return (recency_score * 2.0) + article.authority_weight + richness_bonus + image_bonus


def deduplicate_and_rank(
    articles: Iterable[RawArticle],
    now: Optional[datetime] = None,
    lookback_hours: int = LOOKBACK_HOURS,
    max_items: int = MAX_CANDIDATES_PER_TOPIC,
    max_per_source: int = 4,
) -> List[RawArticle]:
    """Deduplicate similar stories and return top-ranked diverse candidates."""
    reference_now = now or datetime.now(timezone.utc)
    cutoff = reference_now - timedelta(hours=lookback_hours)

    all_list = list(articles)
    recent = [a for a in all_list if a.published_at >= cutoff]
    pool = recent if len(recent) >= max_items // 2 else all_list

    for item in pool:
        item.score = score_article(item, reference_now)

    pool.sort(key=lambda a: (a.score, a.published_at), reverse=True)

    selected: List[RawArticle] = []
    overflow: List[RawArticle] = []
    seen_urls: set[str] = set()
    source_counts: dict[str, int] = {}

    for item in pool:
        norm_url = _normalize_url(item.url)
        if norm_url in seen_urls:
            continue
        if any(are_similar_titles(item.title, existing.title) for existing in selected + overflow):
            continue
        seen_urls.add(norm_url)

        src_key = item.source_name.strip().lower()
        if source_counts.get(src_key, 0) < max_per_source:
            source_counts[src_key] = source_counts.get(src_key, 0) + 1
            selected.append(item)
            if len(selected) >= max_items:
                break
        else:
            overflow.append(item)

    if len(selected) < max_items and overflow:
        selected.extend(overflow[: max_items - len(selected)])

    return selected


def fetch_article_excerpt(url: str, session: Optional[requests.Session] = None) -> Tuple[str, Optional[str]]:
    """Best-effort fetch of article lead paragraphs and og:image for richer summarization."""
    if not url or "news.google.com" in url:
        return "", None
    http = session or requests.Session()
    try:
        resp = http.get(
            url,
            timeout=5,
            headers={"User-Agent": USER_AGENT},
            allow_redirects=True,
        )
        if resp.status_code != 200 or "text/html" not in resp.headers.get("content-type", ""):
            return "", None
        soup = BeautifulSoup(resp.text, "lxml")

        og_image = None
        meta_img = soup.find("meta", attrs={"property": "og:image"}) or soup.find(
            "meta", attrs={"name": "twitter:image"}
        )
        if meta_img and meta_img.get("content", "").startswith("http"):
            og_image = meta_img["content"].strip()

        paragraphs: List[str] = []
        for p in soup.find_all("p"):
            txt = WHITESPACE_RE.sub(" ", p.get_text(" ", strip=True)).strip()
            if len(txt) >= 60 and not _is_boilerplate(txt):
                paragraphs.append(txt)
            if len(" ".join(paragraphs)) >= 1100:
                break
        return " ".join(paragraphs)[:1200], og_image
    except Exception:
        return "", None


def _fetch_single_feed(
    feed: FeedSource,
    topic_id: str,
    reference_now: datetime,
) -> List[RawArticle]:
    try:
        resp = requests.get(
            feed.url,
            timeout=REQUEST_TIMEOUT_SECONDS,
            headers={"User-Agent": USER_AGENT},
        )
        if resp.status_code != 200:
            print(f"[warn] {topic_id}/{feed.name}: HTTP {resp.status_code}")
            return []
        return parse_feed_xml(resp.content, feed=feed, topic_id=topic_id, now=reference_now)
    except Exception as exc:
        print(f"[warn] {topic_id}/{feed.name}: {type(exc).__name__}: {exc}")
        return []


def fetch_topic_candidates(
    topic: TopicConfig,
    session: Optional[requests.Session] = None,
    now: Optional[datetime] = None,
    max_candidates: int = MAX_CANDIDATES_PER_TOPIC,
    enrich_short_summaries: bool = True,
) -> List[RawArticle]:
    """Fetch, parse, deduplicate, and rank candidate stories for one topic."""
    del session  # Individual threads manage independent HTTP calls cleanly.
    reference_now = now or datetime.now(timezone.utc)
    collected: List[RawArticle] = []

    with ThreadPoolExecutor(max_workers=min(6, max(1, len(topic.feeds)))) as pool:
        futures = [pool.submit(_fetch_single_feed, feed, topic.id, reference_now) for feed in topic.feeds]
        for fut in futures:
            collected.extend(fut.result())

    ranked = deduplicate_and_rank(
        collected,
        now=reference_now,
        lookback_hours=LOOKBACK_HOURS,
        max_items=max_candidates,
    )

    if enrich_short_summaries:
        needs_enrich = [item for item in ranked if len(item.raw_summary) < 220 or not item.image_url]
        if needs_enrich:
            with ThreadPoolExecutor(max_workers=6) as pool:
                excerpts = list(pool.map(lambda item: fetch_article_excerpt(item.url), needs_enrich))
            for item, (excerpt, og_image) in zip(needs_enrich, excerpts):
                if len(excerpt) > len(item.raw_summary):
                    item.raw_summary = excerpt
                if not item.image_url and og_image:
                    item.image_url = og_image

    return ranked
