package com.ainews.globalbrief.data.repository

import com.ainews.globalbrief.data.local.DigestCache
import com.ainews.globalbrief.data.model.BilingualArticle
import com.ainews.globalbrief.data.model.DailyDigest
import com.ainews.globalbrief.data.model.DigestManifest
import com.ainews.globalbrief.data.model.ManifestEntry
import com.ainews.globalbrief.data.model.ParallelLayoutMode
import com.ainews.globalbrief.data.model.SavedArticleEntry
import com.ainews.globalbrief.data.remote.DigestApiClient

data class LoadedFeedData(
    val digest: DailyDigest?,
    val manifest: DigestManifest?,
    val fromNetwork: Boolean,
    val errorMessage: String? = null,
)

class NewsRepository(
    private val cache: DigestCache,
    private val api: DigestApiClient = DigestApiClient(cache.json)
) {

    fun loadInitialCachedOrBundled(): LoadedFeedData {
        val cachedDigest = cache.loadLatestDigest() ?: cache.loadBundledSampleDigest()
        val cachedManifest = cache.loadManifest() ?: cachedDigest?.let {
            DigestManifest(
                latestDate = it.date,
                updatedAt = it.generatedAt,
                editions = listOf(
                    ManifestEntry(
                        date = it.date,
                        generatedAt = it.generatedAt,
                        totalArticles = it.totalArticles,
                        path = "archive/${it.date}.json"
                    )
                )
            )
        }
        return LoadedFeedData(
            digest = cachedDigest,
            manifest = cachedManifest,
            fromNetwork = false
        )
    }

    suspend fun refreshFromRemote(): LoadedFeedData {
        val baseUrl = cache.getFeedBaseUrl()
        val token = cache.getGitHubToken()
        if (baseUrl.isBlank()) {
            return loadInitialCachedOrBundled().copy(
                errorMessage = "Chưa cấu hình Feed URL. Đang hiển thị bản tin mẫu offline."
            )
        }
        return try {
            val digest = api.fetchLatestDigest(baseUrl, token)
            cache.saveLatestDigest(digest)
            val manifest = runCatching { api.fetchManifest(baseUrl, token) }.getOrNull()
            if (manifest != null) {
                cache.saveManifest(manifest)
            }
            LoadedFeedData(
                digest = digest,
                manifest = manifest ?: cache.loadManifest(),
                fromNetwork = true
            )
        } catch (exc: Exception) {
            val msg = exc.message.orEmpty()
            val hint = if ("HTTP 404" in msg && token.isBlank()) {
                "HTTP 404 — Nếu repo ở chế độ Private, hãy nhập GitHub Token (Read-only) trong Cài đặt."
            } else {
                "Không thể tải bản tin mới (${msg.ifBlank { "lỗi mạng" }}). Đang dùng dữ liệu offline."
            }
            loadInitialCachedOrBundled().copy(errorMessage = hint)
        }
    }

    suspend fun loadEdition(entry: ManifestEntry): LoadedFeedData {
        val cached = cache.loadEditionDigest(entry.date)
        if (cached != null) {
            return LoadedFeedData(
                digest = cached,
                manifest = cache.loadManifest(),
                fromNetwork = false
            )
        }
        val baseUrl = cache.getFeedBaseUrl()
        val token = cache.getGitHubToken()
        if (baseUrl.isBlank()) {
            return loadInitialCachedOrBundled().copy(
                errorMessage = "Chưa cấu hình Feed URL để tải các ngày trước."
            )
        }
        return try {
            val fetched = api.fetchEdition(baseUrl, entry.path, token)
            cache.saveEditionDigest(fetched)
            LoadedFeedData(
                digest = fetched,
                manifest = cache.loadManifest(),
                fromNetwork = true
            )
        } catch (exc: Exception) {
            loadInitialCachedOrBundled().copy(
                errorMessage = "Không tải được bản tin ngày ${entry.date}: ${exc.message}"
            )
        }
    }

    fun getFeedBaseUrl(): String = cache.getFeedBaseUrl()

    fun saveFeedBaseUrl(url: String) = cache.setFeedBaseUrl(url)

    fun getGitHubToken(): String = cache.getGitHubToken()

    fun saveGitHubToken(token: String) = cache.setGitHubToken(token)

    fun getSavedVault(): List<SavedArticleEntry> = cache.loadSavedVault()

    fun saveOrUpdateInVault(
        article: BilingualArticle,
        isImportant: Boolean? = null,
        userNote: String? = null,
    ): List<SavedArticleEntry> {
        val current = cache.loadSavedVault().toMutableList()
        val existingIdx = current.indexOfFirst { it.article.id == article.id }
        if (existingIdx >= 0) {
            val existing = current[existingIdx]
            current[existingIdx] = existing.copy(
                article = article,
                isImportant = isImportant ?: existing.isImportant,
                userNote = userNote ?: existing.userNote,
            )
        } else {
            current.add(
                0,
                SavedArticleEntry(
                    article = article,
                    savedAt = cache.currentTimestamp(),
                    isImportant = isImportant ?: false,
                    userNote = userNote ?: "",
                )
            )
        }
        cache.saveSavedVault(current)
        return current
    }

    fun saveBatchToVault(
        articles: List<BilingualArticle>,
        markImportant: Boolean = false,
    ): List<SavedArticleEntry> {
        val current = cache.loadSavedVault().toMutableList()
        val existingIds = current.mapTo(HashSet()) { it.article.id }
        val now = cache.currentTimestamp()
        articles.asReversed().forEach { article ->
            if (article.id in existingIds) {
                val idx = current.indexOfFirst { it.article.id == article.id }
                if (idx >= 0 && markImportant && !current[idx].isImportant) {
                    current[idx] = current[idx].copy(isImportant = true)
                }
            } else {
                current.add(
                    0,
                    SavedArticleEntry(
                        article = article,
                        savedAt = now,
                        isImportant = markImportant,
                    )
                )
                existingIds.add(article.id)
            }
        }
        cache.saveSavedVault(current)
        return current
    }

    fun deleteFromVault(articleId: String): List<SavedArticleEntry> {
        val updated = cache.loadSavedVault().filterNot { it.article.id == articleId }
        cache.saveSavedVault(updated)
        return updated
    }

    fun getReadIds(): Set<String> = cache.getReadArticleIds()

    fun markRead(articleId: String): Set<String> = cache.markArticleRead(articleId)

    fun getLayoutMode(): ParallelLayoutMode = cache.getLayoutMode()

    fun setLayoutMode(mode: ParallelLayoutMode) = cache.setLayoutMode(mode)

    fun isDarkThemeOverride(): Boolean? = cache.isDarkThemeOverride()

    fun setDarkThemeOverride(dark: Boolean?) = cache.setDarkThemeOverride(dark)
}
