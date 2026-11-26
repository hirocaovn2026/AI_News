"""Configuration for GlobalBrief daily bilingual news pipeline."""

from dataclasses import dataclass, field
from typing import Dict, List, Tuple


# Hard content limits requested by the user:
# - Max 10 articles per topic
# - Each article's text fits within 2 mobile pages (excluding images)
MAX_ARTICLES_PER_TOPIC = 10
MAX_CANDIDATES_PER_TOPIC = 18
LOOKBACK_HOURS = 48

MAX_TITLE_CHARS = 120
MAX_SUMMARY_CHARS = 780
MAX_KEY_POINTS = 3
MAX_KEY_POINT_CHARS = 150
MAX_WHY_MATTERS_CHARS = 220

ARCHIVE_RETENTION_DAYS = 30
REQUEST_TIMEOUT_SECONDS = 12
USER_AGENT = "GlobalBriefBot/1.0 (+https://github.com/globalbrief-news)"

# Ordered fallback list of Gemini Flash models available on the free tier.
# Includes Flash-Lite as a higher-quota backup if standard Flash hits daily caps.
DEFAULT_GEMINI_MODELS: Tuple[str, ...] = (
    "gemini-3.8-flash",
    "gemini-3.6-flash",
    "gemini-3.5-flash-lite",
    "gemini-2.5-flash",
)


@dataclass(frozen=True)
class FeedSource:
    """Definition of an RSS/Atom news source."""

    name: str
    url: str
    authority_weight: float = 1.0
    filter_keywords: Tuple[str, ...] = ()
    is_google_news: bool = False


@dataclass(frozen=True)
class TopicConfig:
    """Configuration for one of the 4 core news topics."""

    id: str
    name_en: str
    name_vi: str
    description_en: str
    description_vi: str
    accent_hex: str
    feeds: Tuple[FeedSource, ...] = field(default_factory=tuple)


AI_KEYWORDS: Tuple[str, ...] = (
    "ai",
    "artificial intelligence",
    "machine learning",
    "llm",
    "language model",
    "generative",
    "openai",
    "anthropic",
    "deepmind",
    "gemini",
    "claude",
    "gpt",
    "nvidia",
    "chip",
    "gpu",
    "robotics",
    "agent",
    "neural",
    "compute",
    "model",
)

CLIMATE_KEYWORDS: Tuple[str, ...] = (
    "climate",
    "warming",
    "carbon",
    "emission",
    "emissions",
    "greenhouse",
    "renewable",
    "solar",
    "wind",
    "cop",
    "wildfire",
    "flood",
    "drought",
    "heatwave",
    "glacier",
    "sea level",
    "ocean",
    "biodiversity",
    "net zero",
    "fossil",
    "extreme weather",
    "storm",
    "hurricane",
    "typhoon",
)

CONFLICT_KEYWORDS: Tuple[str, ...] = (
    "war",
    "conflict",
    "ceasefire",
    "military",
    "strike",
    "strikes",
    "airstrike",
    "missile",
    "drone",
    "troops",
    "army",
    "battle",
    "frontline",
    "peace talks",
    "truce",
    "sanction",
    "sanctions",
    "nato",
    "defense",
    "defence",
    "ukraine",
    "russia",
    "gaza",
    "israel",
    "iran",
    "lebanon",
    "sudan",
    "syria",
    "myanmar",
    "taiwan",
    "security",
    "hostage",
)


TOPICS: Tuple[TopicConfig, ...] = (
    TopicConfig(
        id="ai",
        name_en="AI & Tech",
        name_vi="Trí tuệ nhân tạo",
        description_en="Frontier models, AI research, chips, and global regulation.",
        description_vi="Mô hình AI mới, nghiên cứu, bán dẫn và chính sách toàn cầu.",
        accent_hex="#2563EB",
        feeds=(
            FeedSource(
                name="MIT Technology Review",
                url="https://www.technologyreview.com/feed/",
                authority_weight=1.35,
                filter_keywords=AI_KEYWORDS,
            ),
            FeedSource(
                name="The Verge AI",
                url="https://www.theverge.com/rss/ai-artificial-intelligence/index.xml",
                authority_weight=1.2,
            ),
            FeedSource(
                name="Ars Technica",
                url="https://feeds.arstechnica.com/arstechnica/technology-lab",
                authority_weight=1.15,
                filter_keywords=AI_KEYWORDS,
            ),
            FeedSource(
                name="Hugging Face Blog",
                url="https://huggingface.co/blog/feed.xml",
                authority_weight=1.05,
            ),
            FeedSource(
                name="Google News AI",
                url=(
                    "https://news.google.com/rss/search?"
                    "q=(%22artificial+intelligence%22+OR+OpenAI+OR+DeepMind+OR+Anthropic+OR+NVIDIA)"
                    "+(site:reuters.com+OR+site:apnews.com+OR+site:techcrunch.com+OR+site:wired.com+OR+site:nature.com+OR+site:cnbc.com)"
                    "+when:48h&ceid=US:en&hl=en-US&gl=US"
                ),
                authority_weight=1.15,
                is_google_news=True,
            ),
        ),
    ),
    TopicConfig(
        id="space",
        name_en="Space Science",
        name_vi="Khoa học vũ trụ",
        description_en="Space missions, telescopes, astronomy breakthroughs, and launches.",
        description_vi="Sứ mệnh không gian, kính thiên văn, khám phá vũ trụ và phóng tàu.",
        accent_hex="#7C3AED",
        feeds=(
            FeedSource(
                name="NASA Breaking News",
                url="https://www.nasa.gov/rss/dyn/breaking_news.rss",
                authority_weight=1.45,
            ),
            FeedSource(
                name="Space.com",
                url="https://www.space.com/feeds.xml",
                authority_weight=1.2,
            ),
            FeedSource(
                name="Google News Space",
                url=(
                    "https://news.google.com/rss/search?"
                    "q=(NASA+OR+SpaceX+OR+telescope+OR+astronomy+OR+orbit)"
                    "+(site:reuters.com+OR+site:apnews.com+OR+site:spacenews.com+OR+site:spaceflightnow.com+OR+site:nature.com+OR+site:bbc.com)"
                    "+when:72h&ceid=US:en&hl=en-US&gl=US"
                ),
                authority_weight=1.15,
                is_google_news=True,
            ),
        ),
    ),
    TopicConfig(
        id="climate",
        name_en="Climate",
        name_vi="Khí hậu",
        description_en="Climate milestones, extreme weather, emissions, and clean energy.",
        description_vi="Biến đổi khí hậu, thời tiết cực đoan, phát thải và năng lượng sạch.",
        accent_hex="#059669",
        feeds=(
            FeedSource(
                name="Carbon Brief",
                url="https://www.carbonbrief.org/feed/",
                authority_weight=1.4,
            ),
            FeedSource(
                name="The Guardian Climate",
                url="https://www.theguardian.com/environment/climate-crisis/rss",
                authority_weight=1.25,
            ),
            FeedSource(
                name="BBC Science & Environment",
                url="https://feeds.bbci.co.uk/news/science_and_environment/rss.xml",
                authority_weight=1.25,
                filter_keywords=CLIMATE_KEYWORDS,
            ),
            FeedSource(
                name="Google News Climate",
                url=(
                    "https://news.google.com/rss/search?"
                    "q=(%22climate+change%22+OR+%22global+warming%22+OR+emissions+OR+renewable+OR+COP)"
                    "+(site:reuters.com+OR+site:apnews.com+OR+site:nature.com+OR+site:insideclimatenews.org+OR+site:carbonbrief.org)"
                    "+when:72h&ceid=US:en&hl=en-US&gl=US"
                ),
                authority_weight=1.15,
                filter_keywords=CLIMATE_KEYWORDS,
                is_google_news=True,
            ),
        ),
    ),
    TopicConfig(
        id="conflict",
        name_en="Conflicts & War",
        name_vi="Xung đột & Chiến tranh",
        description_en="Major armed conflicts, ceasefires, military shifts, and diplomacy.",
        description_vi="Diễn biến chiến sự, ngừng bắn, an ninh quốc tế và ngoại giao.",
        accent_hex="#DC2626",
        feeds=(
            FeedSource(
                name="Reuters World Conflict",
                url=(
                    "https://news.google.com/rss/search?"
                    "q=site:reuters.com+(war+OR+conflict+OR+ceasefire+OR+military)+when:48h"
                    "&ceid=US:en&hl=en-US&gl=US"
                ),
                authority_weight=1.45,
                is_google_news=True,
            ),
            FeedSource(
                name="BBC World News",
                url="https://feeds.bbci.co.uk/news/world/rss.xml",
                authority_weight=1.3,
                filter_keywords=CONFLICT_KEYWORDS,
            ),
            FeedSource(
                name="Al Jazeera",
                url="https://www.aljazeera.com/xml/rss/all.xml",
                authority_weight=1.2,
                filter_keywords=CONFLICT_KEYWORDS,
            ),
        ),
    ),
)

TOPICS_BY_ID: Dict[str, TopicConfig] = {topic.id: topic for topic in TOPICS}
