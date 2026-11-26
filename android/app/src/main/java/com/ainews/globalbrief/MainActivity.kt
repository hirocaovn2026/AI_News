package com.ainews.globalbrief

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ainews.globalbrief.ui.components.shortTopicTitle
import com.ainews.globalbrief.ui.screens.ArticleDetailScreen
import com.ainews.globalbrief.ui.screens.FeedScreen
import com.ainews.globalbrief.ui.state.NewsViewModel
import com.ainews.globalbrief.ui.theme.GlobalBriefTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val viewModel: NewsViewModel = viewModel()
            val uiState by viewModel.uiState.collectAsState()
            val isDark = uiState.darkThemeOverride ?: isSystemInDarkTheme()

            GlobalBriefTheme(darkThemeOverride = isDark) {
                val activeArticle = uiState.selectedArticle
                if (activeArticle != null) {
                    BackHandler { viewModel.closeArticle() }
                    val visibleList = uiState.visibleArticles()
                    val currentIndex = visibleList.indexOfFirst { it.id == activeArticle.id }
                    val topicMeta = uiState.digest?.topics?.firstOrNull { it.id == activeArticle.topicId }
                    val topicTitle = topicMeta?.let(::shortTopicTitle) ?: activeArticle.topicId.uppercase()

                    ArticleDetailScreen(
                        article = activeArticle,
                        topicTitle = topicTitle,
                        savedEntry = uiState.savedById[activeArticle.id],
                        layoutMode = uiState.layoutMode,
                        isDark = isDark,
                        hasPrevious = currentIndex > 0,
                        hasNext = currentIndex in 0 until visibleList.lastIndex,
                        onBack = viewModel::closeArticle,
                        onSaveArticle = { important, note ->
                            viewModel.saveArticleToVault(activeArticle, important, note)
                        },
                        onDeleteSaved = { viewModel.deleteSavedArticle(activeArticle.id) },
                        onBuildExportMarkdown = viewModel::buildSavedMarkdownExport,
                        onNotifyStatus = viewModel::notifyStatus,
                        onToggleLayoutMode = viewModel::toggleLayoutMode,
                        onNavigatePrevious = { viewModel.navigateAdjacentArticle(-1) },
                        onNavigateNext = { viewModel.navigateAdjacentArticle(1) }
                    )
                } else {
                    FeedScreen(
                        uiState = uiState,
                        isDark = isDark,
                        onRefresh = { viewModel.refresh(silentWhenUnconfigured = false) },
                        onSelectSection = viewModel::selectSection,
                        onOpenArticle = viewModel::openArticle,
                        onSaveArticle = viewModel::saveArticleToVault,
                        onDeleteSavedArticle = viewModel::deleteSavedArticle,
                        onToggleSelectionMode = viewModel::toggleSelectionMode,
                        onToggleBatchItem = viewModel::toggleBatchItem,
                        onSaveBatchSelected = viewModel::saveBatchSelected,
                        onSelectSavedTopicFilter = viewModel::setSavedTopicFilter,
                        onToggleSavedOnlyImportant = viewModel::toggleSavedOnlyImportant,
                        onBuildExportMarkdown = viewModel::buildSavedMarkdownExport,
                        onNotifyStatus = viewModel::notifyStatus,
                        onToggleSearch = viewModel::toggleSearch,
                        onSearchQueryChange = viewModel::updateSearchQuery,
                        onSelectEdition = viewModel::selectEdition,
                        onSaveFeedConfig = viewModel::saveFeedUrlAndRefresh,
                        onSetLayoutMode = viewModel::setLayoutMode,
                        onToggleDark = { viewModel.toggleDarkTheme(isDark) },
                        onDismissStatus = viewModel::clearStatusMessage
                    )
                }
            }
        }
    }
}
