package com.ainews.globalbrief

import com.ainews.globalbrief.data.model.BilingualArticle
import com.ainews.globalbrief.data.model.DailyDigest
import com.ainews.globalbrief.data.model.FeedDefaults
import com.ainews.globalbrief.data.model.SavedArticleEntry
import com.ainews.globalbrief.data.model.SavedExportFormatter
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DigestModelsTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun enforcedLimitCapsEachTopicAtTenArticles() {
        val articlesJson = (1..13).joinToString(",") { idx ->
            """
            {
              "id": "id-$idx",
              "topic_id": "ai",
              "rank": $idx,
              "source_name": "MIT Tech Review",
              "source_url": "https://example.com/$idx",
              "published_at": "2026-11-26T06:00:00Z",
              "read_minutes": 1,
              "title_en": "AI Story $idx",
              "title_vi": "Tin AI $idx",
              "summary_en": "Concise English summary.",
              "summary_vi": "Tóm tắt tiếng Việt ngắn gọn."
            }
            """.trimIndent()
        }

        val payload = """
            {
              "schema_version": 1,
              "date": "2026-11-26",
              "generated_at": "2026-11-26T06:05:00Z",
              "max_articles_per_topic": 10,
              "total_articles": 13,
              "topics": [
                {
                  "id": "ai",
                  "name_en": "AI & Tech",
                  "name_vi": "Trí tuệ nhân tạo",
                  "article_count": 13,
                  "articles": [$articlesJson]
                }
              ]
            }
        """.trimIndent()

        val parsed = json.decodeFromString<DailyDigest>(payload).enforcedLimit()
        assertEquals(10, parsed.topics.first().articles.size)
        assertEquals(10, parsed.topics.first().articleCount)
        assertEquals(10, parsed.totalArticles)
    }

    @Test
    fun normalizeBaseUrlAppendsTrailingSlash() {
        assertEquals("", FeedDefaults.normalizeBaseUrl("   "))
        assertEquals(
            "https://raw.githubusercontent.com/user/repo/main/data/",
            FeedDefaults.normalizeBaseUrl("https://raw.githubusercontent.com/user/repo/main/data")
        )
        assertEquals(
            "https://api.github.com/repos/hirocaovn2026/AI_News/contents/data/daily_digest.json?ref=main",
            FeedDefaults.toGitHubApiRawUrl(
                "https://raw.githubusercontent.com/hirocaovn2026/AI_News/main/data/daily_digest.json"
            )
        )
    }

    @Test
    fun savedVaultRoundTripAndMarkdownExportWork() {
        val article = BilingualArticle(
            id = "ai-99",
            topicId = "ai",
            rank = 1,
            sourceName = "Reuters",
            sourceUrl = "https://reuters.com/ai-99",
            publishedAt = "2026-11-26T06:00:00Z",
            titleEn = "Global AI safety framework signed",
            titleVi = "Ký kết khung an toàn AI toàn cầu",
            summaryEn = "Countries agreed on shared evaluation benchmarks.",
            summaryVi = "Các quốc gia thống nhất tiêu chuẩn đánh giá chung.",
            keyPointsEn = listOf("Shared safety tests"),
            keyPointsVi = listOf("Kiểm thử an toàn chung"),
            whyItMattersEn = "Sets baseline cross-border rules.",
            whyItMattersVi = "Đặt chuẩn mực xuyên biên giới."
        )
        val entry = SavedArticleEntry(
            article = article,
            savedAt = "2026-11-26 21:20",
            isImportant = true,
            userNote = "Cần theo dõi tác động pháp lý"
        )

        val encoded = json.encodeToString(listOf(entry))
        val decoded = json.decodeFromString<List<SavedArticleEntry>>(encoded)
        assertEquals(1, decoded.size)
        assertTrue(decoded.first().isImportant)
        assertEquals("Cần theo dõi tác động pháp lý", decoded.first().userNote)

        val markdown = SavedExportFormatter.toMarkdown(decoded, "2026-11-26")
        assertTrue(markdown.contains("QUAN TRỌNG"))
        assertTrue(markdown.contains("Ký kết khung an toàn AI toàn cầu"))
        assertTrue(markdown.contains("Global AI safety framework signed"))
        assertTrue(markdown.contains("Cần theo dõi tác động pháp lý"))
    }
}
