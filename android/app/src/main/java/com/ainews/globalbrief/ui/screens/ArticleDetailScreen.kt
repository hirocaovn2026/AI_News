package com.ainews.globalbrief.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.BookmarkBorder
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.ViewAgenda
import androidx.compose.material.icons.rounded.ViewColumn
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.ainews.globalbrief.data.model.BilingualArticle
import com.ainews.globalbrief.data.model.ParallelLayoutMode
import com.ainews.globalbrief.data.model.SavedArticleEntry
import com.ainews.globalbrief.ui.components.DeleteSavedConfirmDialog
import com.ainews.globalbrief.ui.components.ParallelLanguageBlock
import com.ainews.globalbrief.ui.components.SaveManageDialog
import com.ainews.globalbrief.ui.components.formatTimestampCompact
import com.ainews.globalbrief.ui.theme.topicAccentColor

@Composable
fun ArticleDetailScreen(
    article: BilingualArticle,
    topicTitle: String,
    savedEntry: SavedArticleEntry?,
    layoutMode: ParallelLayoutMode,
    isDark: Boolean,
    hasPrevious: Boolean,
    hasNext: Boolean,
    onBack: () -> Unit,
    onSaveArticle: (Boolean, String) -> Unit,
    onDeleteSaved: () -> Unit,
    onBuildExportMarkdown: (List<SavedArticleEntry>) -> String,
    onNotifyStatus: (String) -> Unit,
    onToggleLayoutMode: () -> Unit,
    onNavigatePrevious: () -> Unit,
    onNavigateNext: () -> Unit,
) {
    val context = LocalContext.current
    val accent = topicAccentColor(article.topicId)
    val isBookmarked = savedEntry != null

    var showSaveDialog by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
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
                onNotifyStatus("Đã lưu file bài viết về điện thoại.")
            }.onFailure { err ->
                onNotifyStatus("Không thể ghi file: ${err.message}")
            }
        }
    }

    fun exportThisArticle() {
        val entry = savedEntry ?: SavedArticleEntry(
            article = article,
            savedAt = formatTimestampCompact(article.publishedAt)
        )
        pendingExportText = onBuildExportMarkdown(listOf(entry))
        exportLauncher.launch("GlobalBrief_${article.topicId}_${article.id.take(6)}.md")
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            Surface(
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = 2.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = "Quay lại"
                        )
                    }
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = accent.copy(alpha = 0.14f)
                    ) {
                        Text(
                            text = "$topicTitle • #${article.rank}/10",
                            color = accent,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                    Spacer(modifier = Modifier.weight(1f))
                    IconButton(onClick = onToggleLayoutMode) {
                        Icon(
                            imageVector = if (layoutMode == ParallelLayoutMode.STACKED)
                                Icons.Rounded.ViewColumn
                            else
                                Icons.Rounded.ViewAgenda,
                            contentDescription = "Đổi bố cục song ngữ"
                        )
                    }
                    IconButton(onClick = { showSaveDialog = true }) {
                        Icon(
                            imageVector = if (isBookmarked) Icons.Rounded.Bookmark else Icons.Rounded.BookmarkBorder,
                            contentDescription = "Lưu & ghi chú",
                            tint = if (isBookmarked) Color(0xFFD97706) else MaterialTheme.colorScheme.onSurface
                        )
                    }
                    IconButton(onClick = { exportThisArticle() }) {
                        Icon(
                            imageVector = Icons.Rounded.Download,
                            contentDescription = "Xuất file về điện thoại"
                        )
                    }
                    IconButton(onClick = {
                        val shareText = buildString {
                            appendLine(article.titleEn)
                            appendLine(article.titleVi)
                            appendLine()
                            appendLine(article.summaryVi)
                            appendLine()
                            append(article.sourceUrl)
                        }
                        val intent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_SUBJECT, article.titleVi)
                            putExtra(Intent.EXTRA_TEXT, shareText)
                        }
                        context.startActivity(Intent.createChooser(intent, "Chia sẻ bản tin"))
                    }) {
                        Icon(
                            imageVector = Icons.Rounded.Share,
                            contentDescription = "Chia sẻ"
                        )
                    }
                }
            }
        },
        bottomBar = {
            Surface(
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = 4.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = onNavigatePrevious,
                        enabled = hasPrevious,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("← Tin trước")
                    }
                    OutlinedButton(
                        onClick = onNavigateNext,
                        enabled = hasNext,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Tin tiếp →")
                    }
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            if (!article.imageUrl.isNullOrBlank()) {
                AsyncImage(
                    model = article.imageUrl,
                    contentDescription = article.titleEn,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(190.dp)
                        .clip(RoundedCornerShape(14.dp))
                )
            }

            if (savedEntry != null) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFFFEF3C7).copy(alpha = if (isDark) 0.16f else 0.75f),
                    border = BorderStroke(1.dp, Color(0xFFD97706).copy(alpha = 0.45f))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (savedEntry.isImportant) Icons.Rounded.Star else Icons.Rounded.Bookmark,
                            contentDescription = null,
                            tint = Color(0xFFD97706),
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (savedEntry.isImportant)
                                    "Tin quan trọng đã lưu (${savedEntry.savedAt})"
                                else
                                    "Đã lưu trong kho (${savedEntry.savedAt})",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold
                            )
                            if (savedEntry.userNote.isNotBlank()) {
                                Text(
                                    text = "Ghi chú: ${savedEntry.userNote}",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                        TextButton(onClick = { showSaveDialog = true }) {
                            Icon(Icons.Rounded.EditNote, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Sửa")
                        }
                    }
                }
            }

            Text(
                text = "${article.sourceName}  •  ${formatTimestampCompact(article.publishedAt)}  •  ~${article.readMinutes} phút đọc",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            ParallelLanguageBlock(
                englishContent = {
                    Text(
                        text = article.titleEn,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                },
                vietnameseContent = {
                    Text(
                        text = article.titleVi,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                },
                layoutMode = layoutMode,
                isDark = isDark
            )

            HorizontalDivider(color = MaterialTheme.colorScheme.outline)

            Text(
                text = "Tóm tắt song ngữ / Bilingual Summary",
                style = MaterialTheme.typography.labelLarge,
                color = accent,
                fontWeight = FontWeight.Bold
            )

            ParallelLanguageBlock(
                englishContent = {
                    Text(
                        text = article.summaryEn,
                        style = MaterialTheme.typography.bodyLarge
                    )
                },
                vietnameseContent = {
                    Text(
                        text = article.summaryVi,
                        style = MaterialTheme.typography.bodyLarge
                    )
                },
                layoutMode = layoutMode,
                isDark = isDark
            )

            val maxBullets = maxOf(article.keyPointsEn.size, article.keyPointsVi.size)
            if (maxBullets > 0) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = "Điểm chính / Key Takeaways",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        for (i in 0 until maxBullets) {
                            val enPoint = article.keyPointsEn.getOrNull(i).orEmpty()
                            val viPoint = article.keyPointsVi.getOrNull(i).orEmpty()
                            ParallelLanguageBlock(
                                englishContent = {
                                    Text(
                                        text = "${i + 1}. $enPoint",
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                },
                                vietnameseContent = {
                                    Text(
                                        text = "${i + 1}. $viPoint",
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                },
                                layoutMode = layoutMode,
                                isDark = isDark
                            )
                            if (i < maxBullets - 1) {
                                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.45f))
                            }
                        }
                    }
                }
            }

            if (article.whyItMattersEn.isNotBlank() || article.whyItMattersVi.isNotBlank()) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = accent.copy(alpha = 0.08f),
                    border = BorderStroke(1.dp, accent.copy(alpha = 0.35f))
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Tầm ảnh hưởng / Why It Matters",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = accent
                        )
                        ParallelLanguageBlock(
                            englishContent = {
                                Text(
                                    text = article.whyItMattersEn,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            },
                            vietnameseContent = {
                                Text(
                                    text = article.whyItMattersVi,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            },
                            layoutMode = layoutMode,
                            isDark = isDark
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = { showSaveDialog = true },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = if (isBookmarked) Icons.Rounded.Bookmark else Icons.Rounded.BookmarkBorder,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(if (isBookmarked) "Ghi chú / ⭐" else "Lưu kho")
                }
                OutlinedButton(
                    onClick = { exportThisArticle() },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Download,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Lưu file (.md)")
                }
            }

            Button(
                onClick = {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(article.sourceUrl))
                    context.startActivity(intent)
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.OpenInNew,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Đọc bài gốc tại ${article.sourceName} / Open Source")
            }
        }
    }

    if (showSaveDialog) {
        SaveManageDialog(
            article = article,
            existingEntry = savedEntry,
            onSave = onSaveArticle,
            onExportSingle = { exportThisArticle() },
            onRequestDelete = { showDeleteConfirm = true },
            onDismiss = { showSaveDialog = false }
        )
    }

    if (showDeleteConfirm) {
        DeleteSavedConfirmDialog(
            articleTitle = article.titleVi,
            onConfirmDelete = onDeleteSaved,
            onDismiss = { showDeleteConfirm = false }
        )
    }
}
