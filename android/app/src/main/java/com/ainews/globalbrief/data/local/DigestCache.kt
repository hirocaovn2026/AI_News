package com.ainews.globalbrief.data.local

import android.content.Context
import com.ainews.globalbrief.data.model.BilingualArticle
import com.ainews.globalbrief.data.model.DailyDigest
import com.ainews.globalbrief.data.model.DigestManifest
import com.ainews.globalbrief.data.model.FeedDefaults
import com.ainews.globalbrief.data.model.ParallelLayoutMode
import com.ainews.globalbrief.data.model.SavedArticleEntry
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class DigestCache(private val context: Context) {

    private val prefs = context.getSharedPreferences("globalbrief_prefs", Context.MODE_PRIVATE)
    private val cacheDir = File(context.filesDir, "digests").apply { mkdirs() }
    private val vaultDir = File(context.filesDir, "vault").apply { mkdirs() }

    val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
    }

    fun getFeedBaseUrl(): String {
        return prefs.getString(KEY_FEED_BASE_URL, FeedDefaults.DEFAULT_FEED_BASE_URL)
            ?.let(FeedDefaults::normalizeBaseUrl)
            ?: ""
    }

    fun setFeedBaseUrl(url: String) {
        prefs.edit().putString(KEY_FEED_BASE_URL, FeedDefaults.normalizeBaseUrl(url)).apply()
    }

    fun getLayoutMode(): ParallelLayoutMode {
        val raw = prefs.getString(KEY_LAYOUT_MODE, ParallelLayoutMode.STACKED.name)
        return runCatching { ParallelLayoutMode.valueOf(raw ?: "") }
            .getOrDefault(ParallelLayoutMode.STACKED)
    }

    fun setLayoutMode(mode: ParallelLayoutMode) {
        prefs.edit().putString(KEY_LAYOUT_MODE, mode.name).apply()
    }

    fun isDarkThemeOverride(): Boolean? {
        return if (!prefs.contains(KEY_DARK_THEME)) null else prefs.getBoolean(KEY_DARK_THEME, false)
    }

    fun setDarkThemeOverride(dark: Boolean?) {
        val editor = prefs.edit()
        if (dark == null) editor.remove(KEY_DARK_THEME) else editor.putBoolean(KEY_DARK_THEME, dark)
        editor.apply()
    }

    fun getReadArticleIds(): Set<String> {
        return prefs.getStringSet(KEY_READ_IDS, emptySet())?.toSet() ?: emptySet()
    }

    fun markArticleRead(articleId: String): Set<String> {
        val updated = getReadArticleIds() + articleId
        prefs.edit().putStringSet(KEY_READ_IDS, updated).apply()
        return updated
    }

    /**
     * Permanent storage for user-saved important articles.
     * Isolated from daily digest cache so items persist indefinitely until the user deletes them.
     */
    fun loadSavedVault(): List<SavedArticleEntry> {
        val vaultFile = File(vaultDir, "saved_vault.json")
        if (vaultFile.exists()) {
            return runCatching {
                json.decodeFromString<List<SavedArticleEntry>>(vaultFile.readText(Charsets.UTF_8))
            }.getOrDefault(emptyList())
        }

        // One-time migration from legacy bookmarks.json if present
        val legacyFile = File(cacheDir, "bookmarks.json")
        if (legacyFile.exists()) {
            val migrated = runCatching {
                val old = json.decodeFromString<List<BilingualArticle>>(legacyFile.readText(Charsets.UTF_8))
                val now = currentTimestamp()
                old.map { SavedArticleEntry(article = it, savedAt = now) }
            }.getOrDefault(emptyList())
            if (migrated.isNotEmpty()) {
                saveSavedVault(migrated)
            }
            return migrated
        }
        return emptyList()
    }

    fun saveSavedVault(items: List<SavedArticleEntry>) {
        val vaultFile = File(vaultDir, "saved_vault.json")
        vaultFile.writeText(json.encodeToString(items), Charsets.UTF_8)
    }

    fun currentTimestamp(): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
        return sdf.format(Date())
    }

    fun saveLatestDigest(digest: DailyDigest) {
        val encoded = json.encodeToString(digest)
        File(cacheDir, "daily_digest.json").writeText(encoded, Charsets.UTF_8)
        File(cacheDir, "edition_${digest.date}.json").writeText(encoded, Charsets.UTF_8)
    }

    fun saveEditionDigest(digest: DailyDigest) {
        val encoded = json.encodeToString(digest)
        File(cacheDir, "edition_${digest.date}.json").writeText(encoded, Charsets.UTF_8)
    }

    fun loadLatestDigest(): DailyDigest? {
        val file = File(cacheDir, "daily_digest.json")
        if (!file.exists()) return null
        return runCatching {
            json.decodeFromString<DailyDigest>(file.readText(Charsets.UTF_8)).enforcedLimit()
        }.getOrNull()
    }

    fun loadEditionDigest(date: String): DailyDigest? {
        val file = File(cacheDir, "edition_$date.json")
        if (!file.exists()) return null
        return runCatching {
            json.decodeFromString<DailyDigest>(file.readText(Charsets.UTF_8)).enforcedLimit()
        }.getOrNull()
    }

    fun saveManifest(manifest: DigestManifest) {
        File(cacheDir, "manifest.json").writeText(json.encodeToString(manifest), Charsets.UTF_8)
    }

    fun loadManifest(): DigestManifest? {
        val file = File(cacheDir, "manifest.json")
        if (!file.exists()) return null
        return runCatching {
            json.decodeFromString<DigestManifest>(file.readText(Charsets.UTF_8))
        }.getOrNull()
    }

    fun loadBundledSampleDigest(): DailyDigest? {
        return runCatching {
            val raw = context.assets.open("sample_digest.json").bufferedReader(Charsets.UTF_8).use {
                it.readText()
            }
            json.decodeFromString<DailyDigest>(raw).enforcedLimit()
        }.getOrNull()
    }

    companion object {
        private const val KEY_FEED_BASE_URL = "feed_base_url"
        private const val KEY_LAYOUT_MODE = "parallel_layout_mode"
        private const val KEY_DARK_THEME = "dark_theme_override"
        private const val KEY_READ_IDS = "read_article_ids"
    }
}
