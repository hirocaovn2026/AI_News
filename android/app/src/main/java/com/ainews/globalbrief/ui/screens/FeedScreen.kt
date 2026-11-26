package com.ainews.globalbrief.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CalendarToday
import androidx.compose.material.icons.rounded.Checklist
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Newspaper
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ainews.globalbrief.data.model.BilingualArticle
import com.ainews.globalbrief.data.model.ManifestEntry
import com.ainews.globalbrief.data.model.ParallelLayoutMode
import com.ainews.globalbrief.data.model.SavedArticleEntry
import com.ainews.globalbrief.ui.components.BilingualArticleCard
import com.ainews.globalbrief.ui.components.DeleteSavedConfirmDialog
import com.ainews.globalbrief.ui.components.EditionPickerDialog
import com.ainews.globalbrief.ui.components.ParallelLanguageBlock
import com.ainews.globalbrief.ui.components.SaveManageDialog
import com.ainews.globalbrief.ui.components.SavedVaultFilterBar
import com.ainews.globalbrief.ui.components.SettingsDialog
import com.ainews.globalbrief.ui.components.TopicTabBar
import com.ainews.globalbrief.ui.components.shortTopicTitle
import com.ainews.globalbrief.ui.state.NewsUiState
import com.ainews.globalbrief.ui.theme.topicAccentColor

@Composable
fun FeedScreen(
    uiState: NewsUiState,
    isDark: Boolean,
    onRefresh: () -> Unit,
    onSelectSection: (String) -> Unit,
    onOpenArticle: (BilingualArticle) -> Unit,
    onSaveArticle: (BilingualArticle, Boolean?, String?) -> Unit,
    onDeleteSavedArticle: (String) -> Unit,
    onToggleSelectionMode: () -> Unit,
    onToggleBatchItem: (String) -> Unit,
    onSaveBatchSelected: (Boolean) -> List<SavedArticleEntry>,
    onSelectSavedTopicFilter: (String) -> Unit,
    onToggleSavedOnlyImportant: () -> Unit,
    onBuildExportMarkdown: (List<SavedArticleEntry>) -> String,
    onNotifyStatus: (String) -> Unit,
    onToggleSearch: (Boolean) -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onSelectEdition: (ManifestEntry) -> Unit,
    onSaveFeedConfig: (String, String) -> Unit,
    onSetLayoutMode: (ParallelLayoutMode) -> Unit,
    onToggleDark: () -> Unit,
    onDismissStatus: () -> Unit,
) {
    val context = LocalContext.current
    var showSettings by remember { mutableStateOf(false) }
    var showEditions by remember { mutableStateOf(false) }
    var manageTarget by remember { mutableStateOf<BilingualArticle?>(null) }
    var deleteTarget by remember { mutableStateOf<BilingualArticle?>(null) }
    var pendingExportText by remember { mutableStateOf<String?>(null) }

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/markdown")
    ) { uri ->
        val content = pendingExportText
        pendingExportText = null
        if (uri != null && content != null) {
            runCatching {
                context.contentResolver.openOutputStream(uri)?.use { out ->
                    out.write(content.toByteArray(Charsets.UTF_8))
                }
            }.onSuccess {
                onNotifyStatus("Đã xuất file tin song ngữ về điện thoại thành công.")
            }.onFailure { err ->
                onNotifyStatus("Không thể ghi file: ${err.message}")
            }
        }
    }

    fun launchExport(entries: List<SavedArticleEntry>, filePrefix: String) {
        if (entries.isEmpty()) return
        pendingExportText = onBuildExportMarkdown(entries)
        val datePart = uiState.digest?.date ?: "GlobalBrief"
        exportLauncher.launch("${filePrefix}_$datePart.md")
    }

    val digest = uiState.digest
    val topicsById = remember(digest) { digest?.topics?.associateBy { it.id }.orEmpty() }
    val visibleArticles = remember(uiState) { uiState.visibleArticles() }
    val isInSavedVault = uiState.selectedSection == NewsUiState.SECTION_SAVED

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            Surface(
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = 2.dp
            ) {
                Column(modifier = Modifier.statusBarsPadding()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Newspaper,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(26.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "GlobalBrief",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Bản tin Song Ngữ EN–VI hằng ngày",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        if (!isInSavedVault) {
                            IconButton(onClick = onToggleSelectionMode) {
                                Icon(
                                    imageVector = Icons.Rounded.Checklist,
                                    contentDescription = "Chọn lưu nhiều tin",
                                    tint = if (uiState.isSelectionMode)
                                        MaterialTheme.colorScheme.primary
                                    else
                                        MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                        IconButton(onClick = { onToggleSearch(!uiState.isSearchActive) }) {
                            Icon(
                                imageVector = if (uiState.isSearchActive) Icons.Rounded.Close else Icons.Rounded.Search,
                                contentDescription = "Tìm kiếm"
                            )
                        }
                        IconButton(onClick = onRefresh) {
                            Icon(
                                imageVector = Icons.Rounded.Refresh,
                                contentDescription = "Làm mới"
                            )
                        }
                        IconButton(onClick = { showSettings = true }) {
                            Icon(
                                imageVector = Icons.Rounded.Settings,
                                contentDescription = "Cài đặt"
                            )
                        }
                    }

                    if (uiState.isSearchActive) {
                        OutlinedTextField(
                            value = uiState.searchQuery,
                            onValueChange = onSearchQueryChange,
                            placeholder = { Text("Tìm tiêu đề, nội dung EN/VI hoặc ghi chú...") },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 6.dp)
                        )
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier.clickable { showEditions = true }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.CalendarToday,
                                    contentDescription = null,
                                    modifier = Modifier.size(14.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = "Số ngày ${digest?.date ?: "--"}",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }

                        Text(
                            text = "Tối đa 10 tin/chuyên đề • ≤ 2 trang/tin",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    if (digest != null) {
                        TopicTabBar(
                            topics = digest.topics,
                            selectedSection = uiState.selectedSection,
                            totalCount = digest.totalArticles,
                            savedCount = uiState.savedVault.size,
                            onSelectSection = onSelectSection
                        )
                    }

                    if (isInSavedVault && digest != null) {
                        SavedVaultFilterBar(
                            topics = digest.topics,
                            selectedTopicFilter = uiState.savedTopicFilter,
                            onlyImportant = uiState.savedOnlyImportant,
                            filteredCount = visibleArticles.size,
                            onSelectTopicFilter = onSelectSavedTopicFilter,
                            onToggleOnlyImportant = onToggleSavedOnlyImportant,
                            onExportAllToPhone = {
                                launchExport(uiState.visibleSavedEntries(), "GlobalBrief_Saved")
                            }
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                    }

                    if (uiState.isRefreshing) {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }
                }
            }
        },
        bottomBar = {
            if (uiState.isSelectionMode) {
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    shadowElevation = 6.dp
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Đã chọn ${uiState.selectedBatchIds.size}",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.weight(1f))
                        OutlinedButton(
                            onClick = { onSaveBatchSelected(false) },
                            enabled = uiState.selectedBatchIds.isNotEmpty()
                        ) {
                            Text("Lưu kho")
                        }
                        Button(
                            onClick = {
                                val saved = onSaveBatchSelected(true)
                                launchExport(saved, "GlobalBrief_Selected")
                            },
                            enabled = uiState.selectedBatchIds.isNotEmpty()
                        ) {
                            Text("Lưu ⭐ & Xuất file")
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (uiState.feedBaseUrl.isBlank() && !isInSavedVault) {
                item(key = "feed_setup_tip") {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.25f))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Đang dùng bản tin mẫu tích hợp sẵn",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Mở Cài đặt để gắn URL GitHub Raw/Pages của bạn và nhận tin tự động hằng ngày.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            TextButton(onClick = { showSettings = true }) {
                                Text("Cài đặt")
                            }
                        }
                    }
                }
            }

            if (!uiState.statusMessage.isNullOrBlank()) {
                item(key = "status_banner") {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = uiState.statusMessage,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.weight(1f)
                            )
                            TextButton(onClick = onDismissStatus) {
                                Text("Ẩn")
                            }
                        }
                    }
                }
            }

            val activeTopic = uiState.currentTopic()
            if (activeTopic != null && (activeTopic.overviewEn.isNotBlank() || activeTopic.overviewVi.isNotBlank())) {
                item(key = "topic_overview_${activeTopic.id}") {
                    val accent = topicAccentColor(activeTopic.id, activeTopic.accentHex)
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = accent.copy(alpha = 0.08f),
                        border = BorderStroke(1.dp, accent.copy(alpha = 0.3f))
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Text(
                                text = "Điểm nhấn ${activeTopic.nameVi} / ${activeTopic.nameEn}",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = accent
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            ParallelLanguageBlock(
                                englishContent = {
                                    Text(
                                        text = activeTopic.overviewEn,
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                },
                                vietnameseContent = {
                                    Text(
                                        text = activeTopic.overviewVi,
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                },
                                layoutMode = uiState.layoutMode,
                                isDark = isDark
                            )
                        }
                    }
                }
            }

            if (visibleArticles.isEmpty()) {
                item(key = "empty_state") {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 48.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = if (isInSavedVault)
                                    "Kho lưu trữ chưa có bài phù hợp"
                                else
                                    "Không tìm thấy bài viết phù hợp",
                                style = MaterialTheme.typography.titleMedium
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = if (isInSavedVault)
                                    "Bấm biểu tượng đánh dấu ở bất kỳ tin nào để lưu giữ lâu dài."
                                else
                                    "Thử đổi chuyên đề hoặc xóa từ khóa tìm kiếm.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            } else {
                items(visibleArticles, key = { "${it.topicId}_${it.id}" }) { article ->
                    val topicMeta = topicsById[article.topicId]
                    val topicLabel = topicMeta?.let(::shortTopicTitle) ?: article.topicId.uppercase()
                    val savedEntry = uiState.savedById[article.id]

                    BilingualArticleCard(
                        article = article,
                        topicLabel = topicLabel,
                        savedEntry = savedEntry,
                        isRead = article.id in uiState.readIds,
                        isDark = isDark,
                        isSelectionMode = uiState.isSelectionMode,
                        isBatchSelected = article.id in uiState.selectedBatchIds,
                        showVaultActions = isInSavedVault,
                        onOpen = {
                            if (uiState.isSelectionMode) {
                                onToggleBatchItem(article.id)
                            } else {
                                onOpenArticle(article)
                            }
                        },
                        onBookmarkAction = {
                            if (savedEntry == null) {
                                onSaveArticle(article, false, "")
                            } else {
                                manageTarget = article
                            }
                        },
                        onExportSingle = savedEntry?.let { entry ->
                            { launchExport(listOf(entry), "GlobalBrief_${article.topicId}") }
                        },
                        onDeleteSaved = savedEntry?.let {
                            { deleteTarget = article }
                        }
                    )
                }
            }
        }
    }

    manageTarget?.let { article ->
        SaveManageDialog(
            article = article,
            existingEntry = uiState.savedById[article.id],
            onSave = { important, note -> onSaveArticle(article, important, note) },
            onExportSingle = {
                val entry = uiState.savedById[article.id]
                    ?: SavedArticleEntry(article = article, savedAt = uiState.digest?.date ?: "")
                launchExport(listOf(entry), "GlobalBrief_${article.topicId}")
            },
            onRequestDelete = { deleteTarget = article },
            onDismiss = { manageTarget = null }
        )
    }

    deleteTarget?.let { article ->
        DeleteSavedConfirmDialog(
            articleTitle = article.titleVi,
            onConfirmDelete = { onDeleteSavedArticle(article.id) },
            onDismiss = { deleteTarget = null }
        )
    }

    if (showSettings) {
        SettingsDialog(
            currentFeedUrl = uiState.feedBaseUrl,
            currentGitHubToken = uiState.githubToken,
            layoutMode = uiState.layoutMode,
            isDark = isDark,
            onDismiss = { showSettings = false },
            onSaveFeedConfig = onSaveFeedConfig,
            onSetLayoutMode = onSetLayoutMode,
            onToggleDark = onToggleDark
        )
    }

    if (showEditions) {
        EditionPickerDialog(
            editions = uiState.manifest?.editions.orEmpty(),
            currentDate = digest?.date.orEmpty(),
            onSelectEdition = onSelectEdition,
            onDismiss = { showEditions = false }
        )
    }
}
