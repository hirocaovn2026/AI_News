package com.ainews.globalbrief.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

object FeedDefaults {
    /**
     * Default GitHub raw data directory for this repository.
     * Can be changed anytime inside the app's Settings dialog.
     */
    const val DEFAULT_FEED_BASE_URL = "https://raw.githubusercontent.com/hirocaovn2026/AI_News/main/data/"
    const val MAX_ARTICLES_PER_TOPIC = 10

    fun normalizeBaseUrl(raw: String): String {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return ""
        return if (trimmed.endsWith("/")) trimmed else "$trimmed/"
    }

    /**
     * Converts https://raw.githubusercontent.com/{owner}/{repo}/{branch}/{path}
     * to GitHub Contents API URL for reliable private-repo access with Fine-Grained PATs.
     */
    fun toGitHubApiRawUrl(rawUrl: String): String? {
        val prefix = "https://raw.githubusercontent.com/"
        if (!rawUrl.startsWith(prefix)) return null
        val parts = rawUrl.removePrefix(prefix).split("/", limit = 4)
        if (parts.size < 4) return null
        val (owner, repo, branch, path) = parts
        if (owner.isBlank() || repo.isBlank() || branch.isBlank() || path.isBlank()) return null
        return "https://api.github.com/repos/$owner/$repo/contents/$path?ref=$branch"
    }
}

@Serializable
data class BilingualArticle(
    val id: String,
    @SerialName("topic_id") val topicId: String,
    val rank: Int,
    @SerialName("source_name") val sourceName: String,
    @SerialName("source_url") val sourceUrl: String,
    @SerialName("image_url") val imageUrl: String? = null,
    @SerialName("published_at") val publishedAt: String,
    @SerialName("read_minutes") val readMinutes: Int = 1,
    @SerialName("title_en") val titleEn: String,
    @SerialName("title_vi") val titleVi: String,
    @SerialName("summary_en") val summaryEn: String,
    @SerialName("summary_vi") val summaryVi: String,
    @SerialName("key_points_en") val keyPointsEn: List<String> = emptyList(),
    @SerialName("key_points_vi") val keyPointsVi: List<String> = emptyList(),
    @SerialName("why_it_matters_en") val whyItMattersEn: String = "",
    @SerialName("why_it_matters_vi") val whyItMattersVi: String = "",
)

@Serializable
data class SavedArticleEntry(
    val article: BilingualArticle,
    @SerialName("saved_at") val savedAt: String,
    @SerialName("is_important") val isImportant: Boolean = false,
    @SerialName("user_note") val userNote: String = "",
)

@Serializable
data class TopicDigest(
    val id: String,
    @SerialName("name_en") val nameEn: String,
    @SerialName("name_vi") val nameVi: String,
    @SerialName("description_en") val descriptionEn: String = "",
    @SerialName("description_vi") val descriptionVi: String = "",
    @SerialName("accent_hex") val accentHex: String = "#2563EB",
    @SerialName("overview_en") val overviewEn: String = "",
    @SerialName("overview_vi") val overviewVi: String = "",
    @SerialName("article_count") val articleCount: Int = 0,
    val articles: List<BilingualArticle> = emptyList(),
)

@Serializable
data class DailyDigest(
    @SerialName("schema_version") val schemaVersion: Int = 1,
    val date: String,
    @SerialName("generated_at") val generatedAt: String,
    @SerialName("max_articles_per_topic") val maxArticlesPerTopic: Int = FeedDefaults.MAX_ARTICLES_PER_TOPIC,
    @SerialName("total_articles") val totalArticles: Int = 0,
    val topics: List<TopicDigest> = emptyList(),
) {
    fun enforcedLimit(): DailyDigest {
        val cappedTopics = topics.map { topic ->
            val cappedArticles = topic.articles.take(FeedDefaults.MAX_ARTICLES_PER_TOPIC)
            topic.copy(
                articleCount = cappedArticles.size,
                articles = cappedArticles
            )
        }
        return copy(
            topics = cappedTopics,
            totalArticles = cappedTopics.sumOf { it.articles.size }
        )
    }
}

@Serializable
data class ManifestEntry(
    val date: String,
    @SerialName("generated_at") val generatedAt: String,
    @SerialName("total_articles") val totalArticles: Int = 0,
    val path: String,
)

@Serializable
data class DigestManifest(
    @SerialName("latest_date") val latestDate: String,
    @SerialName("updated_at") val updatedAt: String,
    val editions: List<ManifestEntry> = emptyList(),
)

enum class ParallelLayoutMode {
    STACKED,
    SIDE_BY_SIDE
}

object SavedExportFormatter {
    fun toMarkdown(entries: List<SavedArticleEntry>, exportedDateLabel: String): String {
        return buildString {
            appendLine("# GlobalBrief — Kho tin quan trọng đã lưu ($exportedDateLabel)")
            appendLine()
            appendLine("Tổng số bài viết: ${entries.size}")
            appendLine()
            entries.forEachIndexed { idx, entry ->
                val a = entry.article
                val star = if (entry.isImportant) "⭐ QUAN TRỌNG | " else ""
                appendLine("---")
                appendLine("## ${idx + 1}. ${star}[${a.topicId.uppercase()}] ${a.titleVi}")
                appendLine("**English:** ${a.titleEn}")
                appendLine()
                appendLine("- **Nguồn:** ${a.sourceName} | **Ngày đăng:** ${a.publishedAt}")
                appendLine("- **Lưu lúc:** ${entry.savedAt}")
                appendLine("- **Link gốc:** ${a.sourceUrl}")
                if (entry.userNote.isNotBlank()) {
                    appendLine("- **Ghi chú cá nhân:** ${entry.userNote}")
                }
                appendLine()
                appendLine("### Tóm tắt (VI)")
                appendLine(a.summaryVi)
                appendLine()
                appendLine("### Summary (EN)")
                appendLine(a.summaryEn)
                if (a.keyPointsVi.isNotEmpty() || a.keyPointsEn.isNotEmpty()) {
                    appendLine()
                    appendLine("### Điểm chính / Key Takeaways")
                    val maxK = maxOf(a.keyPointsVi.size, a.keyPointsEn.size)
                    for (i in 0 until maxK) {
                        a.keyPointsEn.getOrNull(i)?.let { appendLine("- **EN:** $it") }
                        a.keyPointsVi.getOrNull(i)?.let { appendLine("- **VI:** $it") }
                    }
                }
                if (a.whyItMattersVi.isNotBlank() || a.whyItMattersEn.isNotBlank()) {
                    appendLine()
                    appendLine("**Tầm ảnh hưởng / Why it matters:**")
                    if (a.whyItMattersEn.isNotBlank()) appendLine("- EN: ${a.whyItMattersEn}")
                    if (a.whyItMattersVi.isNotBlank()) appendLine("- VI: ${a.whyItMattersVi}")
                }
                appendLine()
            }
        }
    }
}
