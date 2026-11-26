package com.ainews.globalbrief.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.BookmarkBorder
import androidx.compose.material.icons.rounded.CheckBox
import androidx.compose.material.icons.rounded.CheckBoxOutlineBlank
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Eco
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.RocketLaunch
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.ainews.globalbrief.data.model.BilingualArticle
import com.ainews.globalbrief.data.model.ManifestEntry
import com.ainews.globalbrief.data.model.ParallelLayoutMode
import com.ainews.globalbrief.data.model.SavedArticleEntry
import com.ainews.globalbrief.data.model.TopicDigest
import com.ainews.globalbrief.ui.state.NewsUiState
import com.ainews.globalbrief.ui.theme.EnglishBadgeBgDark
import com.ainews.globalbrief.ui.theme.EnglishBadgeBgLight
import com.ainews.globalbrief.ui.theme.EnglishBadgeFgDark
import com.ainews.globalbrief.ui.theme.EnglishBadgeFgLight
import com.ainews.globalbrief.ui.theme.VietnameseBadgeBgDark
import com.ainews.globalbrief.ui.theme.VietnameseBadgeBgLight
import com.ainews.globalbrief.ui.theme.VietnameseBadgeFgDark
import com.ainews.globalbrief.ui.theme.VietnameseBadgeFgLight
import com.ainews.globalbrief.ui.theme.topicAccentColor

fun topicIcon(topicId: String): ImageVector {
    return when (topicId) {
        "ai" -> Icons.Rounded.AutoAwesome
        "space" -> Icons.Rounded.RocketLaunch
        "climate" -> Icons.Rounded.Eco
        "conflict" -> Icons.Rounded.Shield
        NewsUiState.SECTION_SAVED -> Icons.Rounded.Bookmark
        else -> Icons.Rounded.Public
    }
}

fun shortTopicTitle(topic: TopicDigest): String {
    return when (topic.id) {
        "ai" -> "AI"
        "space" -> "Vũ trụ / Space"
        "climate" -> "Khí hậu / Climate"
        "conflict" -> "Xung đột / War"
        else -> topic.nameVi
    }
}

fun shortTopicTitleById(topicId: String): String {
    return when (topicId) {
        "ai" -> "AI"
        "space" -> "Vũ trụ"
        "climate" -> "Khí hậu"
        "conflict" -> "Xung đột"
        else -> topicId.uppercase()
    }
}

fun formatTimestampCompact(isoString: String): String {
    if (isoString.length >= 16 && isoString.contains("T")) {
        return isoString.substring(5, 10) + " " + isoString.substring(11, 16) + " UTC"
    }
    return isoString.take(10)
}

@Composable
fun LanguageBadge(
    code: String,
    isDark: Boolean = isSystemInDarkTheme(),
    modifier: Modifier = Modifier
) {
    val isEn = code.equals("EN", ignoreCase = true)
    val bg = when {
        isEn && isDark -> EnglishBadgeBgDark
        isEn -> EnglishBadgeBgLight
        isDark -> VietnameseBadgeBgDark
        else -> VietnameseBadgeBgLight
    }
    val fg = when {
        isEn && isDark -> EnglishBadgeFgDark
        isEn -> EnglishBadgeFgLight
        isDark -> VietnameseBadgeFgDark
        else -> VietnameseBadgeFgLight
    }

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(6.dp),
        color = bg
    ) {
        Text(
            text = code.uppercase(),
            color = fg,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }
}

@Composable
fun ParallelLanguageBlock(
    englishContent: @Composable () -> Unit,
    vietnameseContent: @Composable () -> Unit,
    layoutMode: ParallelLayoutMode,
    isDark: Boolean,
    modifier: Modifier = Modifier,
) {
    if (layoutMode == ParallelLayoutMode.SIDE_BY_SIDE) {
        Row(
            modifier = modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.Top
        ) {
            Column(modifier = Modifier.weight(1f)) {
                LanguageBadge("EN", isDark = isDark)
                Spacer(modifier = Modifier.height(6.dp))
                englishContent()
            }
            Box(
                modifier = Modifier
                    .width(1.dp)
                    .height(60.dp)
                    .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.6f))
            )
            Column(modifier = Modifier.weight(1f)) {
                LanguageBadge("VI", isDark = isDark)
                Spacer(modifier = Modifier.height(6.dp))
                vietnameseContent()
            }
        }
    } else {
        Column(
            modifier = modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                LanguageBadge("EN", isDark = isDark, modifier = Modifier.padding(top = 2.dp))
                Box(modifier = Modifier.weight(1f)) {
                    englishContent()
                }
            }
            Row(
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                LanguageBadge("VI", isDark = isDark, modifier = Modifier.padding(top = 2.dp))
                Box(modifier = Modifier.weight(1f)) {
                    vietnameseContent()
                }
            }
        }
    }
}

@Composable
fun TopicTabBar(
    topics: List<TopicDigest>,
    selectedSection: String,
    totalCount: Int,
    savedCount: Int,
    onSelectSection: (String) -> Unit,
) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item(key = NewsUiState.SECTION_ALL) {
            TopicPill(
                label = "Tất cả ($totalCount)",
                icon = topicIcon(NewsUiState.SECTION_ALL),
                accentColor = MaterialTheme.colorScheme.primary,
                selected = selectedSection == NewsUiState.SECTION_ALL,
                onClick = { onSelectSection(NewsUiState.SECTION_ALL) }
            )
        }
        items(topics, key = { it.id }) { topic ->
            TopicPill(
                label = "${shortTopicTitle(topic)} (${topic.articles.size})",
                icon = topicIcon(topic.id),
                accentColor = topicAccentColor(topic.id, topic.accentHex),
                selected = selectedSection == topic.id,
                onClick = { onSelectSection(topic.id) }
            )
        }
        item(key = NewsUiState.SECTION_SAVED) {
            TopicPill(
                label = "Đã lưu ($savedCount)",
                icon = topicIcon(NewsUiState.SECTION_SAVED),
                accentColor = Color(0xFFD97706),
                selected = selectedSection == NewsUiState.SECTION_SAVED,
                onClick = { onSelectSection(NewsUiState.SECTION_SAVED) }
            )
        }
    }
}

@Composable
private fun TopicPill(
    label: String,
    icon: ImageVector,
    accentColor: Color,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val container = if (selected) accentColor else MaterialTheme.colorScheme.surface
    val content = if (selected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
    val border = if (selected) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outline)

    Surface(
        shape = RoundedCornerShape(20.dp),
        color = container,
        contentColor = content,
        border = border,
        modifier = Modifier.clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = if (selected) Color.White else accentColor
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
            )
        }
    }
}

@Composable
fun SavedVaultFilterBar(
    topics: List<TopicDigest>,
    selectedTopicFilter: String,
    onlyImportant: Boolean,
    filteredCount: Int,
    onSelectTopicFilter: (String) -> Unit,
    onToggleOnlyImportant: () -> Unit,
    onExportAllToPhone: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Kho lưu trữ lâu dài ($filteredCount tin)",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
            OutlinedButton(
                onClick = onExportAllToPhone,
                enabled = filteredCount > 0,
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Download,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text("Lưu file (.md)", style = MaterialTheme.typography.labelMedium)
            }
        }

        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item(key = "vault_important") {
                FilterChip(
                    selected = onlyImportant,
                    onClick = onToggleOnlyImportant,
                    label = { Text("Quan trọng") },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Rounded.Star,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = Color(0xFFD97706)
                        )
                    }
                )
            }
            item(key = "vault_all") {
                FilterChip(
                    selected = selectedTopicFilter == NewsUiState.SECTION_ALL,
                    onClick = { onSelectTopicFilter(NewsUiState.SECTION_ALL) },
                    label = { Text("Tất cả chuyên đề") }
                )
            }
            items(topics, key = { "vault_${it.id}" }) { topic ->
                FilterChip(
                    selected = selectedTopicFilter == topic.id,
                    onClick = { onSelectTopicFilter(topic.id) },
                    label = { Text(shortTopicTitleById(topic.id)) }
                )
            }
        }
    }
}

@Composable
fun BilingualArticleCard(
    article: BilingualArticle,
    topicLabel: String,
    savedEntry: SavedArticleEntry?,
    isRead: Boolean,
    isDark: Boolean,
    isSelectionMode: Boolean = false,
    isBatchSelected: Boolean = false,
    showVaultActions: Boolean = false,
    onOpen: () -> Unit,
    onBookmarkAction: () -> Unit,
    onExportSingle: (() -> Unit)? = null,
    onDeleteSaved: (() -> Unit)? = null,
) {
    val accent = topicAccentColor(article.topicId)
    val isSaved = savedEntry != null

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isBatchSelected)
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
            else
                MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(
            width = if (isBatchSelected) 2.dp else 1.dp,
            color = if (isBatchSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
        )
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (isSelectionMode) {
                    Icon(
                        imageVector = if (isBatchSelected) Icons.Rounded.CheckBox else Icons.Rounded.CheckBoxOutlineBlank,
                        contentDescription = "Chọn tin",
                        tint = if (isBatchSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .size(22.dp)
                            .padding(end = 4.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                }

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = accent.copy(alpha = 0.12f)
                ) {
                    Text(
                        text = "$topicLabel • #${article.rank}",
                        color = accent,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }

                if (savedEntry?.isImportant == true) {
                    Spacer(modifier = Modifier.width(6.dp))
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color(0xFFFEF3C7)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Star,
                                contentDescription = null,
                                tint = Color(0xFFD97706),
                                modifier = Modifier.size(12.dp)
                            )
                            Spacer(modifier = Modifier.width(2.dp))
                            Text(
                                text = "Quan trọng",
                                color = Color(0xFF92400E),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "${article.sourceName} · ${article.readMinutes}m",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                if (isRead && !isSelectionMode) {
                    Icon(
                        imageVector = Icons.Rounded.CheckCircle,
                        contentDescription = "Đã đọc",
                        tint = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier
                            .size(18.dp)
                            .padding(end = 2.dp)
                    )
                }
                if (!isSelectionMode) {
                    IconButton(
                        onClick = onBookmarkAction,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = if (isSaved) Icons.Rounded.Bookmark else Icons.Rounded.BookmarkBorder,
                            contentDescription = if (isSaved) "Quản lý tin đã lưu" else "Lưu tin",
                            tint = if (isSaved) Color(0xFFD97706) else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            if (!article.imageUrl.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(10.dp))
                AsyncImage(
                    model = article.imageUrl,
                    contentDescription = article.titleEn,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(145.dp)
                        .clip(RoundedCornerShape(12.dp))
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                LanguageBadge("EN", isDark = isDark, modifier = Modifier.padding(top = 3.dp))
                Text(
                    text = article.titleEn,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            Row(
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                LanguageBadge("VI", isDark = isDark, modifier = Modifier.padding(top = 3.dp))
                Text(
                    text = article.titleVi,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(10.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.6f))
            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "EN: ${article.summaryEn}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "VI: ${article.summaryVi}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            if (savedEntry != null && (showVaultActions || savedEntry.userNote.isNotBlank())) {
                Spacer(modifier = Modifier.height(8.dp))
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "Đã lưu: ${savedEntry.savedAt}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (savedEntry.userNote.isNotBlank()) {
                            Text(
                                text = "Ghi chú: ${savedEntry.userNote}",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        if (showVaultActions) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End
                            ) {
                                TextButton(
                                    onClick = onBookmarkAction,
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                                ) {
                                    Icon(Icons.Rounded.EditNote, null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Ghi chú / ⭐")
                                }
                                if (onExportSingle != null) {
                                    TextButton(
                                        onClick = onExportSingle,
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                                    ) {
                                        Icon(Icons.Rounded.Download, null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Lưu file")
                                    }
                                }
                                if (onDeleteSaved != null) {
                                    TextButton(
                                        onClick = onDeleteSaved,
                                        colors = ButtonDefaults.textButtonColors(
                                            contentColor = MaterialTheme.colorScheme.error
                                        ),
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                                    ) {
                                        Icon(Icons.Rounded.DeleteOutline, null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Xóa")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SaveManageDialog(
    article: BilingualArticle,
    existingEntry: SavedArticleEntry?,
    onSave: (isImportant: Boolean, userNote: String) -> Unit,
    onExportSingle: () -> Unit,
    onRequestDelete: (() -> Unit)?,
    onDismiss: () -> Unit,
) {
    var isImportant by remember(article.id, existingEntry) {
        mutableStateOf(existingEntry?.isImportant ?: false)
    }
    var noteText by remember(article.id, existingEntry) {
        mutableStateOf(existingEntry?.userNote ?: "")
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (existingEntry == null) "Lưu vào kho lưu trữ" else "Quản lý tin đã lưu",
                style = MaterialTheme.typography.titleLarge
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = article.titleVi,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "Tin đã lưu sẽ tồn tại vĩnh viễn trong ứng dụng (không bị xóa khi cập nhật báo mới) cho đến khi bạn tự xóa.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Star,
                            contentDescription = null,
                            tint = Color(0xFFD97706)
                        )
                        Text("Đánh dấu tin quan trọng", style = MaterialTheme.typography.titleSmall)
                    }
                    Switch(checked = isImportant, onCheckedChange = { isImportant = it })
                }
                OutlinedTextField(
                    value = noteText,
                    onValueChange = { noteText = it },
                    label = { Text("Ghi chú cá nhân (tùy chọn)") },
                    placeholder = { Text("Ví dụ: Theo dõi chính sách AI EU...") },
                    minLines = 2,
                    maxLines = 4,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedButton(
                    onClick = {
                        onExportSingle()
                        onDismiss()
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Rounded.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Xuất bài này thành file (.md) về điện thoại")
                }
                if (existingEntry != null && onRequestDelete != null) {
                    TextButton(
                        onClick = {
                            onDismiss()
                            onRequestDelete()
                        },
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Rounded.DeleteOutline, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Xóa khỏi kho lưu trữ")
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                onSave(isImportant, noteText.trim())
                onDismiss()
            }) {
                Text("Lưu thay đổi")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Đóng")
            }
        }
    )
}

@Composable
fun DeleteSavedConfirmDialog(
    articleTitle: String,
    onConfirmDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Xác nhận xóa tin đã lưu", style = MaterialTheme.typography.titleLarge) },
        text = {
            Text(
                text = "Bạn chắc chắn muốn xóa bài viết \"$articleTitle\" khỏi kho lưu trữ?",
                style = MaterialTheme.typography.bodyMedium
            )
        },
        confirmButton = {
            Button(
                onClick = {
                    onConfirmDelete()
                    onDismiss()
                },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
            ) {
                Text("Xóa vĩnh viễn")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Hủy")
            }
        }
    )
}

@Composable
fun SettingsDialog(
    currentFeedUrl: String,
    currentGitHubToken: String,
    layoutMode: ParallelLayoutMode,
    isDark: Boolean,
    onDismiss: () -> Unit,
    onSaveFeedConfig: (String, String) -> Unit,
    onSetLayoutMode: (ParallelLayoutMode) -> Unit,
    onToggleDark: () -> Unit,
) {
    var urlText by remember(currentFeedUrl) { mutableStateOf(currentFeedUrl) }
    var tokenText by remember(currentGitHubToken) { mutableStateOf(currentGitHubToken) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("Cài đặt bản tin / Settings", style = MaterialTheme.typography.titleLarge)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "Nhập URL thư mục data/ từ GitHub Raw để tự động tải tin mỗi ngày:",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = urlText,
                    onValueChange = { urlText = it },
                    label = { Text("Feed Base URL") },
                    placeholder = { Text("https://raw.githubusercontent.com/user/repo/main/data/") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = tokenText,
                    onValueChange = { tokenText = it },
                    label = { Text("GitHub Token (chỉ cần nếu repo Private)") },
                    placeholder = { Text("github_pat_... (Contents: Read-only)") },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Chế độ song ngữ", style = MaterialTheme.typography.titleSmall)
                        Text(
                            text = if (layoutMode == ParallelLayoutMode.SIDE_BY_SIDE)
                                "2 cột song song (EN | VI)"
                            else
                                "Xếp dọc từng đoạn (EN / VI)",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    OutlinedButton(
                        onClick = {
                            onSetLayoutMode(
                                if (layoutMode == ParallelLayoutMode.STACKED)
                                    ParallelLayoutMode.SIDE_BY_SIDE
                                else
                                    ParallelLayoutMode.STACKED
                            )
                        }
                    ) {
                        Text(if (layoutMode == ParallelLayoutMode.STACKED) "Xếp dọc" else "2 cột")
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Giao diện tối (Dark Mode)", style = MaterialTheme.typography.titleSmall)
                    Switch(checked = isDark, onCheckedChange = { onToggleDark() })
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                onSaveFeedConfig(urlText, tokenText)
                onDismiss()
            }) {
                Text("Lưu & Cập nhật")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Đóng")
            }
        }
    )
}

@Composable
fun EditionPickerDialog(
    editions: List<ManifestEntry>,
    currentDate: String,
    onSelectEdition: (ManifestEntry) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("Chọn ngày phát hành / Editions", style = MaterialTheme.typography.titleLarge)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (editions.isEmpty()) {
                    Text("Chưa có dữ liệu lưu trữ thêm.", style = MaterialTheme.typography.bodyMedium)
                } else {
                    editions.take(10).forEach { entry ->
                        val selected = entry.date == currentDate
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onSelectEdition(entry)
                                    onDismiss()
                                }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .clip(CircleShape)
                                            .background(
                                                if (selected) MaterialTheme.colorScheme.primary
                                                else MaterialTheme.colorScheme.outline
                                            )
                                    )
                                    Text(
                                        text = entry.date,
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                                    )
                                }
                                Text(
                                    text = "${entry.totalArticles} tin",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Đóng")
            }
        }
    )
}
