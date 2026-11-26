"""Unit tests for the GlobalBrief daily bilingual news pipeline."""

from __future__ import annotations

from datetime import datetime, timedelta, timezone
import json
from pathlib import Path
import sys
from types import SimpleNamespace

PIPELINE_DIR = Path(__file__).resolve().parents[1]
if str(PIPELINE_DIR) not in sys.path:
    sys.path.insert(0, str(PIPELINE_DIR))

from config import (
    MAX_ARTICLES_PER_TOPIC,
    MAX_KEY_POINTS,
    MAX_KEY_POINT_CHARS,
    MAX_SUMMARY_CHARS,
    MAX_TITLE_CHARS,
    MAX_WHY_MATTERS_CHARS,
    TOPICS_BY_ID,
    FeedSource,
)
from generate_digest import DailyDigest, write_digest_outputs
from rss_fetcher import (
    RawArticle,
    are_similar_titles,
    deduplicate_and_rank,
    parse_feed_xml,
)
from summarizer import (
    SummarizedArticlePayload,
    TopicDigestResponse,
    summarize_topic_with_gemini,
)


SAMPLE_RSS = b"""<?xml version="1.0" encoding="UTF-8"?>
<rss version="2.0" xmlns:media="http://search.yahoo.com/mrss/">
  <channel>
    <title>Test Feed</title>
    <item>
      <title>New AI model improves climate forecasting accuracy - Reuters</title>
      <link>https://news.google.com/rss/articles/abc123</link>
      <pubDate>Thu, 26 Nov 2026 06:00:00 GMT</pubDate>
      <description>&lt;p&gt;Posts from this topic will be added to your daily email digest and your homepage feed. Researchers introduced an &lt;b&gt;AI weather model&lt;/b&gt; that cuts prediction errors by 20 percent.&lt;/p&gt;</description>
      <media:content url="https://example.com/photo.jpg" medium="image" />
    </item>
    <item>
      <title>AI drone clip trending online - Facebook</title>
      <link>https://news.google.com/rss/articles/blockedfb</link>
      <pubDate>Thu, 26 Nov 2026 05:30:00 GMT</pubDate>
      <description>Social media post.</description>
    </item>
    <item>
      <title>Celebrity fashion gala highlights - Reuters</title>
      <link>https://news.google.com/rss/articles/offtopic</link>
      <pubDate>Thu, 26 Nov 2026 05:00:00 GMT</pubDate>
      <description>Red carpet arrivals in Paris.</description>
    </item>
  </channel>
</rss>
"""

SAMPLE_ATOM = b"""<?xml version="1.0" encoding="UTF-8"?>
<feed xmlns="http://www.w3.org/2005/Atom">
  <title>Space Atom Feed</title>
  <entry>
    <title>James Webb Telescope detects water vapor plume on exoplanet</title>
    <link rel="alternate" href="https://example.org/jwst-plume" />
    <published>2026-11-26T07:30:00Z</published>
    <summary type="html">&lt;div&gt;Astronomers observed strong atmospheric signals.&lt;img src="https://example.org/jwst.png"/&gt;&lt;/div&gt;</summary>
  </entry>
</feed>
"""


def test_parse_rss_and_atom_with_filtering():
    now = datetime(2026, 11, 26, 10, 0, tzinfo=timezone.utc)
    ai_feed = FeedSource(
        name="Google News AI",
        url="https://news.google.com/rss",
        filter_keywords=("ai", "model"),
        is_google_news=True,
    )
    rss_items = parse_feed_xml(SAMPLE_RSS, feed=ai_feed, topic_id="ai", now=now)
    assert len(rss_items) == 1
    assert rss_items[0].title == "New AI model improves climate forecasting accuracy"
    assert rss_items[0].source_name == "Reuters"
    assert rss_items[0].image_url == "https://example.com/photo.jpg"
    assert "AI weather model" in rss_items[0].raw_summary
    assert "daily email digest" not in rss_items[0].raw_summary.lower()

    climate_feed = FeedSource(
        name="Google News Climate",
        url="https://news.google.com/rss",
        filter_keywords=("climate", "emissions"),
        is_google_news=True,
    )
    false_pos_rss = b"""<?xml version="1.0" encoding="UTF-8"?>
    <rss version="2.0"><channel><item>
      <title>Country seeks fair business climate for trade - Reuters</title>
      <link>https://news.google.com/rss/articles/biz1</link>
      <pubDate>Thu, 26 Nov 2026 06:00:00 GMT</pubDate>
      <description>Trade ministers met to improve investment conditions.</description>
    </item></channel></rss>"""
    assert parse_feed_xml(false_pos_rss, feed=climate_feed, topic_id="climate", now=now) == []

    space_feed = FeedSource(name="Space Atom", url="https://example.org/atom")
    atom_items = parse_feed_xml(SAMPLE_ATOM, feed=space_feed, topic_id="space", now=now)
    assert len(atom_items) == 1
    assert atom_items[0].url == "https://example.org/jwst-plume"
    assert atom_items[0].image_url == "https://example.org/jwst.png"


def test_deduplicate_and_rank_collapses_similar_stories():
    now = datetime(2026, 11, 26, 12, 0, tzinfo=timezone.utc)
    a1 = RawArticle(
        id="1",
        topic_id="conflict",
        title="UN diplomats push for emergency ceasefire talks in Sudan",
        url="https://bbc.co.uk/news/1",
        source_name="BBC",
        published_at=now - timedelta(hours=1),
        raw_summary="Long summary " * 20,
        authority_weight=1.3,
    )
    a2 = RawArticle(
        id="2",
        topic_id="conflict",
        title="Emergency ceasefire talks in Sudan pushed by UN diplomats",
        url="https://aljazeera.com/news/1",
        source_name="Al Jazeera",
        published_at=now - timedelta(hours=2),
        raw_summary="Similar event",
        authority_weight=1.1,
    )
    a3 = RawArticle(
        id="3",
        topic_id="conflict",
        title="Maritime security patrols expanded across Red Sea shipping corridor",
        url="https://reuters.com/news/2",
        source_name="Reuters",
        published_at=now - timedelta(hours=3),
        raw_summary="Naval forces announced new escort protocols.",
        authority_weight=1.4,
    )
    assert are_similar_titles(a1.title, a2.title)
    ranked = deduplicate_and_rank([a1, a2, a3], now=now, max_items=10)
    assert len(ranked) == 2
    assert {item.id for item in ranked} == {"1", "3"}

    distinct_titles = [
        "Baltic naval patrols expand underwater cable monitoring",
        "Eastern border artillery exchange intensifies overnight",
        "Red Sea maritime coalition intercepts anti-ship drones",
        "Diplomats draft Sudan humanitarian corridor agreement",
        "Arctic defense summit approves radar modernization plan",
        "Pacific fleet conducts joint amphibious readiness drill",
    ]
    many_reuters = [
        RawArticle(
            id=f"r-{i}",
            topic_id="conflict",
            title=distinct_titles[i],
            url=f"https://reuters.com/{i}",
            source_name="Reuters",
            published_at=now - timedelta(minutes=i),
            raw_summary="Short snippet.",
            authority_weight=1.5,
        )
        for i in range(6)
    ]
    bbc_extra = RawArticle(
        id="bbc-1",
        topic_id="conflict",
        title="United Nations Security Council convenes emergency peacekeeping session",
        url="https://bbc.co.uk/world/1",
        source_name="BBC World News",
        published_at=now - timedelta(hours=1),
        raw_summary="Detailed summary from BBC.",
        authority_weight=1.2,
    )
    diverse = deduplicate_and_rank(many_reuters + [bbc_extra], now=now, max_items=5, max_per_source=3)
    assert len(diverse) == 5
    assert any(item.source_name == "BBC World News" for item in diverse)


def test_summarizer_enforces_ten_article_limit_and_two_page_budget():
    now = datetime(2026, 11, 26, 12, 0, tzinfo=timezone.utc)
    candidates = [
        RawArticle(
            id=f"art-{i}",
            topic_id="ai",
            title=f"AI milestone event number {i} announced by research lab",
            url=f"https://example.com/ai/{i}",
            source_name="MIT Technology Review",
            published_at=now - timedelta(hours=i),
            raw_summary="Detailed announcement about AI systems and safety benchmarks.",
        )
        for i in range(15)
    ]

    # Mock Gemini returning 13 articles with overly long text to verify strict enforcement.
    overlong = "Sentence one explains the breakthrough clearly. " * 35
    mock_payloads = [
        SummarizedArticlePayload(
            candidate_index=i,
            title_en=("Headline " * 25) + str(i),
            title_vi=("Tiêu đề " * 25) + str(i),
            summary_en=overlong,
            summary_vi=overlong,
            key_points_en=["Point A " * 30, "Point B", "Point C", "Point D extra"],
            key_points_vi=["Điểm 1 " * 30, "Điểm 2", "Điểm 3", "Điểm 4 dư"],
            why_it_matters_en="Global impact " * 30,
            why_it_matters_vi="Tác động toàn cầu " * 30,
        )
        for i in range(13)
    ]

    fake_response = SimpleNamespace(
        parsed=TopicDigestResponse(
            topic_overview_en="Busy day in AI research.",
            topic_overview_vi="Ngày sôi động của ngành AI.",
            articles=mock_payloads,
        ),
        text="{}",
    )

    class FakeModels:
        def generate_content(self, **kwargs):
            return fake_response

    fake_client = SimpleNamespace(models=FakeModels())
    digest = summarize_topic_with_gemini(
        topic=TOPICS_BY_ID["ai"],
        candidates=candidates,
        max_articles=MAX_ARTICLES_PER_TOPIC,
        client_override=fake_client,
    )

    assert digest.article_count == 10
    assert len(digest.articles) == 10
    for idx, article in enumerate(digest.articles, start=1):
        assert article.rank == idx
        assert len(article.title_en) <= MAX_TITLE_CHARS
        assert len(article.title_vi) <= MAX_TITLE_CHARS
        assert len(article.summary_en) <= MAX_SUMMARY_CHARS
        assert len(article.summary_vi) <= MAX_SUMMARY_CHARS
        assert len(article.key_points_en) <= MAX_KEY_POINTS
        assert len(article.key_points_vi) <= MAX_KEY_POINTS
        assert all(len(kp) <= MAX_KEY_POINT_CHARS for kp in article.key_points_en)
        assert all(len(kp) <= MAX_KEY_POINT_CHARS for kp in article.key_points_vi)
        assert len(article.why_it_matters_en) <= MAX_WHY_MATTERS_CHARS
        assert len(article.why_it_matters_vi) <= MAX_WHY_MATTERS_CHARS
        assert article.read_minutes <= 2


def test_fallback_and_write_outputs(tmp_path: Path):
    now = datetime(2026, 11, 26, 12, 0, tzinfo=timezone.utc)
    candidates = [
        RawArticle(
            id="space-1",
            topic_id="space",
            title="Artemis lunar rover completes thermal vacuum testing",
            url="https://nasa.gov/artemis-rover",
            source_name="NASA",
            published_at=now,
            raw_summary="Engineers verified rover operations in extreme cold conditions.",
            image_url="https://nasa.gov/rover.jpg",
        )
    ]
    topic_digest = summarize_topic_with_gemini(
        topic=TOPICS_BY_ID["space"],
        candidates=candidates,
        api_key="",
        max_articles=10,
    )
    assert topic_digest.article_count == 1
    assert topic_digest.articles[0].title_en == "Artemis lunar rover completes thermal vacuum testing"

    digest = DailyDigest(
        date="2026-11-26",
        generated_at=now.isoformat(),
        total_articles=1,
        topics=[topic_digest],
    )
    asset_target = tmp_path / "assets" / "sample_digest.json"
    write_digest_outputs(digest, output_dir=tmp_path / "data", android_asset_path=asset_target)

    latest = json.loads((tmp_path / "data" / "daily_digest.json").read_text(encoding="utf-8"))
    archive = json.loads((tmp_path / "data" / "archive" / "2026-11-26.json").read_text(encoding="utf-8"))
    manifest = json.loads((tmp_path / "data" / "manifest.json").read_text(encoding="utf-8"))
    assert latest["total_articles"] == 1
    assert archive["date"] == "2026-11-26"
    assert manifest["latest_date"] == "2026-11-26"
    assert asset_target.exists()


def test_summarizer_falls_back_across_models_on_quota_error(monkeypatch):
    monkeypatch.setattr("summarizer.time.sleep", lambda _sec: None)
    now = datetime(2026, 11, 26, 12, 0, tzinfo=timezone.utc)
    candidates = [
        RawArticle(
            id="clim-1",
            topic_id="climate",
            title="Global renewable capacity hits new milestone",
            url="https://carbonbrief.org/1",
            source_name="Carbon Brief",
            published_at=now,
            raw_summary="Solar and wind additions set a record across major grids.",
        )
    ]

    called_models = []

    class FlakyModels:
        def generate_content(self, **kwargs):
            model = kwargs["model"]
            called_models.append(model)
            if model == "model-primary":
                raise RuntimeError("429 RESOURCE_EXHAUSTED quota limit")
            return SimpleNamespace(
                parsed=TopicDigestResponse(
                    topic_overview_en="Clean energy growth leads today's climate updates.",
                    topic_overview_vi="Năng lượng sạch dẫn đầu tin khí hậu hôm nay.",
                    articles=[
                        SummarizedArticlePayload(
                            candidate_index=0,
                            title_en="Global renewable capacity hits record",
                            title_vi="Công suất năng lượng tái tạo đạt kỷ lục",
                            summary_en="Solar and wind installations reached a record pace.",
                            summary_vi="Điện mặt trời và điện gió tăng trưởng kỷ lục.",
                            key_points_en=["Record grid additions"],
                            key_points_vi=["Bổ sung lưới điện kỷ lục"],
                            why_it_matters_en="Accelerates power sector decarbonization.",
                            why_it_matters_vi="Thúc đẩy giảm phát thải ngành điện.",
                        )
                    ],
                ),
                text="{}",
            )

    digest = summarize_topic_with_gemini(
        topic=TOPICS_BY_ID["climate"],
        candidates=candidates,
        models=("model-primary", "model-lite"),
        client_override=SimpleNamespace(models=FlakyModels()),
    )

    assert called_models == ["model-primary", "model-primary", "model-lite"]
    assert digest.article_count == 1
    assert digest.articles[0].title_vi == "Công suất năng lượng tái tạo đạt kỷ lục"
