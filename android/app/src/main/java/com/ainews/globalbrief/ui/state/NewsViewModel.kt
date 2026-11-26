package com.ainews.globalbrief.ui.state

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ainews.globalbrief.data.local.DigestCache
import com.ainews.globalbrief.data.model.BilingualArticle
import com.ainews.globalbrief.data.model.DailyDigest
import com.ainews.globalbrief.data.model.DigestManifest
import com.ainews.globalbrief.data.model.ManifestEntry
import com.ainews.globalbrief.data.model.ParallelLayoutMode
import com.ainews.globalbrief.data.model.SavedArticleEntry
import com.ainews.globalbrief.data.model.SavedExportFormatter
import com.ainews.globalbrief.data.model.TopicDigest
import com.ainews.globalbrief.data.repository.NewsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class NewsUiState(
    val digest: DailyDigest? = null,
    val manifest: DigestManifest? = null,
    val selectedSection: String = SECTION_ALL,
    val selectedArticle: BilingualArticle? = null,
    val searchQuery: String = "",
    val isSearchActive: Boolean = false,
    val savedVault: List<SavedArticleEntry> = emptyList(),
    val savedTopicFilter: String = SECTION_ALL,
    val savedOnlyImportant: Boolean = false,
    val isSelectionMode: Boolean = false,
    val selectedBatchIds: Set<String> = emptySet(),
    val readIds: Set<String> = emptySet(),
    val feedBaseUrl: String = "",
    val layoutMode: ParallelLayoutMode = ParallelLayoutMode.STACKED,
    val darkThemeOverride: Boolean? = null,
    val isRefreshing: Boolean = false,
    val isFromNetwork: Boolean = false,
    val statusMessage: String? = null,
) {
    val savedById: Map<String, SavedArticleEntry> get() = savedVault.associateBy { it.article.id }
    val bookmarkedIds: Set<String> get() = savedById.keys

    fun currentTopic(): TopicDigest? {
        return digest?.topics?.firstOrNull { it.id == selectedSection }
    }

    fun visibleSavedEntries(): List<SavedArticleEntry> {
        val query = searchQuery.trim().lowercase()
        return savedVault.filter { entry ->
            val topicMatch = savedTopicFilter == SECTION_ALL || entry.article.topicId == savedTopicFilter
            val importantMatch = !savedOnlyImportant || entry.isImportant
            val searchMatch = query.isEmpty() || matchesArticleQuery(entry.article, query) ||
                entry.userNote.lowercase().contains(query)
            topicMatch && importantMatch && searchMatch
        }
    }

    fun visibleArticles(): List<BilingualArticle> {
        if (selectedSection == SECTION_SAVED) {
            return visibleSavedEntries().map { it.article }
        }
        val baseList = when (selectedSection) {
            SECTION_ALL -> digest?.topics?.flatMap { it.articles }.orEmpty()
            else -> digest?.topics?.firstOrNull { it.id == selectedSection }?.articles.orEmpty()
        }
        val query = searchQuery.trim().lowercase()
        if (query.isEmpty()) return baseList
        return baseList.filter { matchesArticleQuery(it, query) }
    }

    private fun matchesArticleQuery(article: BilingualArticle, query: String): Boolean {
        return article.titleEn.lowercase().contains(query) ||
            article.titleVi.lowercase().contains(query) ||
            article.summaryEn.lowercase().contains(query) ||
            article.summaryVi.lowercase().contains(query) ||
            article.sourceName.lowercase().contains(query)
    }

    companion object {
        const val SECTION_ALL = "all"
        const val SECTION_SAVED = "saved"
    }
}

class NewsViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = NewsRepository(DigestCache(application.applicationContext))
    private val _uiState = MutableStateFlow(NewsUiState())
    val uiState: StateFlow<NewsUiState> = _uiState.asStateFlow()

    init {
        val initial = repository.loadInitialCachedOrBundled()
        val feedUrl = repository.getFeedBaseUrl()
        _uiState.value = NewsUiState(
            digest = initial.digest,
            manifest = initial.manifest,
            savedVault = repository.getSavedVault(),
            readIds = repository.getReadIds(),
            feedBaseUrl = feedUrl,
            layoutMode = repository.getLayoutMode(),
            darkThemeOverride = repository.isDarkThemeOverride(),
            isFromNetwork = initial.fromNetwork,
        )
        if (feedUrl.isNotBlank()) {
            refresh(silentWhenUnconfigured = true)
        }
    }

    fun refresh(silentWhenUnconfigured: Boolean = false) {
        if (_uiState.value.isRefreshing) return
        if (silentWhenUnconfigured && _uiState.value.feedBaseUrl.isBlank()) return

        viewModelScope.launch {
            _uiState.update { it.copy(isRefreshing = true, statusMessage = null) }
            val result = repository.refreshFromRemote()
            _uiState.update { state ->
                state.copy(
                    digest = result.digest ?: state.digest,
                    manifest = result.manifest ?: state.manifest,
                    isRefreshing = false,
                    isFromNetwork = result.fromNetwork,
                    statusMessage = result.errorMessage
                        ?: if (result.fromNetwork) "Đã cập nhật bản tin mới nhất (${result.digest?.date})" else null
                )
            }
        }
    }

    fun selectEdition(entry: ManifestEntry) {
        viewModelScope.launch {
            _uiState.update { it.copy(isRefreshing = true, statusMessage = null) }
            val result = repository.loadEdition(entry)
            _uiState.update { state ->
                state.copy(
                    digest = result.digest ?: state.digest,
                    isRefreshing = false,
                    isFromNetwork = result.fromNetwork,
                    statusMessage = result.errorMessage
                )
            }
        }
    }

    fun selectSection(sectionId: String) {
        _uiState.update {
            it.copy(
                selectedSection = sectionId,
                selectedArticle = null,
                isSelectionMode = false,
                selectedBatchIds = emptySet()
            )
        }
    }

    fun openArticle(article: BilingualArticle) {
        val updatedRead = repository.markRead(article.id)
        _uiState.update { it.copy(selectedArticle = article, readIds = updatedRead) }
    }

    fun closeArticle() {
        _uiState.update { it.copy(selectedArticle = null) }
    }

    fun navigateAdjacentArticle(delta: Int) {
        val current = _uiState.value.selectedArticle ?: return
        val list = _uiState.value.visibleArticles()
        val index = list.indexOfFirst { it.id == current.id }
        if (index < 0) return
        val nextIndex = index + delta
        if (nextIndex in list.indices) {
            openArticle(list[nextIndex])
        }
    }

    fun saveArticleToVault(
        article: BilingualArticle,
        isImportant: Boolean? = null,
        userNote: String? = null,
    ) {
        val updated = repository.saveOrUpdateInVault(article, isImportant, userNote)
        _uiState.update {
            it.copy(
                savedVault = updated,
                statusMessage = "Đã lưu tin vào kho lưu trữ lâu dài (chỉ xóa khi bạn xóa)."
            )
        }
    }

    fun deleteSavedArticle(articleId: String) {
        val updated = repository.deleteFromVault(articleId)
        _uiState.update {
            it.copy(
                savedVault = updated,
                statusMessage = "Đã xóa tin khỏi kho lưu trữ."
            )
        }
    }

    fun toggleSelectionMode() {
        _uiState.update { state ->
            val next = !state.isSelectionMode
            state.copy(
                isSelectionMode = next,
                selectedBatchIds = if (next) state.selectedBatchIds else emptySet()
            )
        }
    }

    fun toggleBatchItem(articleId: String) {
        _uiState.update { state ->
            val updated = if (articleId in state.selectedBatchIds) {
                state.selectedBatchIds - articleId
            } else {
                state.selectedBatchIds + articleId
            }
            state.copy(selectedBatchIds = updated)
        }
    }

    fun saveBatchSelected(markImportant: Boolean): List<SavedArticleEntry> {
        val selectedIds = _uiState.value.selectedBatchIds
        val articles = _uiState.value.visibleArticles().filter { it.id in selectedIds }
        if (articles.isEmpty()) return emptyList()
        val updated = repository.saveBatchToVault(articles, markImportant = markImportant)
        _uiState.update {
            it.copy(
                savedVault = updated,
                isSelectionMode = false,
                selectedBatchIds = emptySet(),
                statusMessage = "Đã lưu ${articles.size} tin đã chọn vào kho lưu trữ."
            )
        }
        return updated.filter { it.article.id in selectedIds }
    }

    fun setSavedTopicFilter(topicId: String) {
        _uiState.update { it.copy(savedTopicFilter = topicId) }
    }

    fun toggleSavedOnlyImportant() {
        _uiState.update { it.copy(savedOnlyImportant = !it.savedOnlyImportant) }
    }

    fun buildSavedMarkdownExport(entries: List<SavedArticleEntry>): String {
        val dateLabel = _uiState.value.digest?.date ?: "GlobalBrief"
        return SavedExportFormatter.toMarkdown(entries, dateLabel)
    }

    fun notifyStatus(message: String) {
        _uiState.update { it.copy(statusMessage = message) }
    }

    fun toggleSearch(active: Boolean) {
        _uiState.update {
            it.copy(
                isSearchActive = active,
                searchQuery = if (active) it.searchQuery else ""
            )
        }
    }

    fun updateSearchQuery(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
    }

    fun toggleLayoutMode() {
        val next = if (_uiState.value.layoutMode == ParallelLayoutMode.STACKED) {
            ParallelLayoutMode.SIDE_BY_SIDE
        } else {
            ParallelLayoutMode.STACKED
        }
        repository.setLayoutMode(next)
        _uiState.update { it.copy(layoutMode = next) }
    }

    fun setLayoutMode(mode: ParallelLayoutMode) {
        repository.setLayoutMode(mode)
        _uiState.update { it.copy(layoutMode = mode) }
    }

    fun toggleDarkTheme(currentlyDark: Boolean) {
        val next = !currentlyDark
        repository.setDarkThemeOverride(next)
        _uiState.update { it.copy(darkThemeOverride = next) }
    }

    fun saveFeedUrlAndRefresh(url: String) {
        repository.saveFeedBaseUrl(url)
        val normalized = repository.getFeedBaseUrl()
        _uiState.update { it.copy(feedBaseUrl = normalized, statusMessage = null) }
        if (normalized.isNotBlank()) {
            refresh(silentWhenUnconfigured = false)
        }
    }

    fun clearStatusMessage() {
        _uiState.update { it.copy(statusMessage = null) }
    }
}
