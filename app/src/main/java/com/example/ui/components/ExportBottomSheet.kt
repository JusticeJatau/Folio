package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.TextSnippet
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.domain.export.ExportFormat
import com.example.domain.export.ExportTarget

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExportBottomSheet(
    target: ExportTarget,
    isExporting: Boolean,
    onSaveFormat: (ExportFormat) -> Unit,
    onShareFormat: (ExportFormat) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val subtitle = when (target) {
        is ExportTarget.Single -> stringResource(
            R.string.export_single_subtitle,
            target.todo.title.ifBlank { "Untitled" }
        )
        is ExportTarget.All -> stringResource(R.string.export_all_subtitle, target.todos.size)
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .navigationBarsPadding()
                .padding(bottom = 24.dp)
        ) {
            Text(
                text = stringResource(R.string.export_sheet_title),
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            if (isExporting) {
                Spacer(modifier = Modifier.height(12.dp))
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Spacer(modifier = Modifier.height(18.dp))

            ExportFormatRow(
                format = ExportFormat.PDF,
                title = stringResource(R.string.export_format_pdf_title),
                description = stringResource(R.string.export_format_pdf_desc),
                icon = Icons.Outlined.PictureAsPdf,
                badgeColor = MaterialTheme.colorScheme.secondaryContainer,
                badgeTint = MaterialTheme.colorScheme.onSecondaryContainer,
                enabled = !isExporting,
                onSave = { onSaveFormat(ExportFormat.PDF) },
                onShare = { onShareFormat(ExportFormat.PDF) }
            )

            Spacer(modifier = Modifier.height(12.dp))

            ExportFormatRow(
                format = ExportFormat.DOCX,
                title = stringResource(R.string.export_format_docx_title),
                description = stringResource(R.string.export_format_docx_desc),
                icon = Icons.Outlined.Description,
                badgeColor = MaterialTheme.colorScheme.primaryContainer,
                badgeTint = MaterialTheme.colorScheme.onPrimaryContainer,
                enabled = !isExporting,
                onSave = { onSaveFormat(ExportFormat.DOCX) },
                onShare = { onShareFormat(ExportFormat.DOCX) }
            )

            Spacer(modifier = Modifier.height(12.dp))

            ExportFormatRow(
                format = ExportFormat.TXT,
                title = stringResource(R.string.export_format_txt_title),
                description = stringResource(R.string.export_format_txt_desc),
                icon = Icons.AutoMirrored.Outlined.TextSnippet,
                badgeColor = MaterialTheme.colorScheme.tertiaryContainer,
                badgeTint = MaterialTheme.colorScheme.onTertiaryContainer,
                enabled = !isExporting,
                onSave = { onSaveFormat(ExportFormat.TXT) },
                onShare = { onShareFormat(ExportFormat.TXT) }
            )
        }
    }
}

@Composable
private fun ExportFormatRow(
    format: ExportFormat,
    title: String,
    description: String,
    icon: ImageVector,
    badgeColor: Color,
    badgeTint: Color,
    enabled: Boolean,
    onSave: () -> Unit,
    onShare: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(badgeColor),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = badgeTint,
                    modifier = Modifier.size(22.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                IconButton(
                    onClick = onShare,
                    enabled = enabled,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("export_share_${format.extension}")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Share,
                        contentDescription = stringResource(R.string.action_share),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }

                FilledTonalButton(
                    onClick = onSave,
                    enabled = enabled,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("export_save_${format.extension}")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Download,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(stringResource(R.string.action_save_to_device))
                }
            }
        }
    }
}
