package com.brian.dialbuddy.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.brian.dialbuddy.R
import com.brian.dialbuddy.data.NoticeLog
import com.brian.dialbuddy.domain.Notice
import com.brian.dialbuddy.domain.NoticeApp
import com.brian.dialbuddy.ui.theme.Danger
import com.brian.dialbuddy.ui.theme.NoticeBand
import com.brian.dialbuddy.ui.theme.NoticeBody
import com.brian.dialbuddy.ui.theme.NoticeInk
import com.brian.dialbuddy.ui.theme.NoticeMuted
import com.brian.dialbuddy.ui.theme.NoticeShade
import com.brian.dialbuddy.ui.theme.NoticeStroke
import com.brian.dialbuddy.ui.theme.NoticeText
import com.brian.dialbuddy.viewmodel.NoticeRow
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun NoticesMenu(
    apps: List<NoticeApp>,
    onSystemHistory: () -> Unit,
    onDeleteAll: () -> Unit,
    onPick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var open by remember { mutableStateOf(false) }
    val label = stringResource(R.string.notices_menu)
    val itemColors = MenuDefaults.itemColors(textColor = NoticeText, trailingIconColor = NoticeMuted)
    Column(modifier) {
        Canvas(
            Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(NoticeBody)
                .border(1.dp, NoticeStroke, CircleShape)
                .clickable { open = true }
                .semantics { contentDescription = label }
                .padding(8.dp),
        ) {
            val stroke = 1.8.dp.toPx()
            listOf(0.22f, 0.5f, 0.78f).forEach { y ->
                drawLine(
                    color = NoticeBand,
                    start = Offset(size.width * 0.12f, size.height * y),
                    end = Offset(size.width * 0.88f, size.height * y),
                    strokeWidth = stroke,
                )
            }
        }
        DropdownMenu(
            expanded = open,
            onDismissRequest = { open = false },
            containerColor = NoticeBody,
        ) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.notices_system_history)) },
                onClick = {
                    open = false
                    onSystemHistory()
                },
                colors = itemColors,
            )
            if (apps.isNotEmpty()) {
                HorizontalDivider(color = NoticeStroke)
            }
            apps.forEach { app ->
                DropdownMenuItem(
                    text = { Text(app.appName, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    trailingIcon = { Text(app.count.toString(), color = NoticeMuted) },
                    onClick = {
                        open = false
                        onPick(app.packageName)
                    },
                    colors = itemColors,
                )
            }
            if (apps.isNotEmpty()) {
                HorizontalDivider(color = NoticeStroke)
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.notices_delete_all)) },
                    onClick = {
                        open = false
                        onDeleteAll()
                    },
                    colors = MenuDefaults.itemColors(textColor = Danger),
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun NoticesPage(
    rows: List<NoticeRow>,
    access: Boolean,
    onOpen: (String) -> Unit,
    onDismiss: (String) -> Unit,
    onDismissAll: () -> Unit,
    onAllow: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier) {
        if (!access) {
            Text(
                stringResource(R.string.notices_need_access),
                color = NoticeText,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            )
            Button(
                onClick = onAllow,
                colors = noticeButtonColors(),
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
            ) {
                Text(stringResource(R.string.notices_allow))
            }
        } else if (rows.isEmpty()) {
            Text(
                stringResource(R.string.notices_empty),
                color = NoticeMuted,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            )
        }
        LazyColumn(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(top = 8.dp),
        ) {
            rows.groupBy { it.dayLabel }.forEach { (day, notices) ->
                stickyHeader(key = "day-$day") {
                    Text(
                        day,
                        color = NoticeInk,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(NoticeBand)
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                    )
                }
                items(notices, key = { "notice-${it.notice.key}" }) { row ->
                    NoticeRowView(row, onOpen, onDismiss)
                }
            }
        }
        if (rows.isNotEmpty()) {
            Button(
                onClick = onDismissAll,
                colors = noticeButtonColors(),
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            ) {
                Text(pluralStringResource(R.plurals.notices_dismiss_app, rows.size, rows.size))
            }
        }
    }
}

@Composable
private fun NoticeRowView(
    row: NoticeRow,
    onOpen: (String) -> Unit,
    onDismiss: (String) -> Unit,
) {
    val notice = row.notice
    val ink = if (notice.dismissed) NoticeMuted else NoticeText
    val headline = notice.title.ifBlank { notice.text }.ifBlank { stringResource(R.string.notices_untitled) }
    val body = if (notice.title.isBlank()) "" else notice.text
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            Modifier
                .weight(1f)
                .clickable { onOpen(notice.key) },
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                NoticeAvatar(notice)
                Text(
                    headline,
                    color = ink,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .padding(start = 8.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(NoticeShade)
                        .padding(horizontal = 8.dp, vertical = 3.dp),
                )
            }
            if (body.isNotBlank()) {
                Text(body, color = ink, fontSize = 14.sp, maxLines = 3, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 2.dp))
            }
            val whenLabel = if (notice.dismissed) {
                "${timeLabel(notice.updatedAt)} · ${stringResource(R.string.notices_dismissed)}"
            } else {
                timeLabel(notice.updatedAt)
            }
            Text(whenLabel, color = NoticeMuted, fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp))
        }
        val dismissLabel = stringResource(R.string.notices_dismiss)
        Box(
            Modifier
                .padding(start = 8.dp)
                .size(36.dp)
                .clip(CircleShape)
                .clickable { onDismiss(notice.key) }
                .semantics { contentDescription = dismissLabel },
            contentAlignment = Alignment.Center,
        ) {
            TrashIcon()
        }
    }
}

@Composable
private fun TrashIcon() {
    Canvas(Modifier.size(22.dp)) {
        val stroke = Stroke(width = 1.7.dp.toPx(), cap = StrokeCap.Round)
        drawLine(
            color = Danger,
            start = Offset(size.width * 0.36f, size.height * 0.16f),
            end = Offset(size.width * 0.64f, size.height * 0.16f),
            strokeWidth = stroke.width,
            cap = StrokeCap.Round,
        )
        drawLine(
            color = Danger,
            start = Offset(size.width * 0.12f, size.height * 0.30f),
            end = Offset(size.width * 0.88f, size.height * 0.30f),
            strokeWidth = stroke.width,
            cap = StrokeCap.Round,
        )
        val body = Path().apply {
            moveTo(size.width * 0.24f, size.height * 0.36f)
            lineTo(size.width * 0.76f, size.height * 0.36f)
            lineTo(size.width * 0.68f, size.height * 0.88f)
            lineTo(size.width * 0.32f, size.height * 0.88f)
            close()
        }
        drawPath(body, Danger, style = stroke)
        drawLine(
            color = Danger,
            start = Offset(size.width * 0.42f, size.height * 0.46f),
            end = Offset(size.width * 0.42f, size.height * 0.76f),
            strokeWidth = stroke.width,
            cap = StrokeCap.Round,
        )
        drawLine(
            color = Danger,
            start = Offset(size.width * 0.58f, size.height * 0.46f),
            end = Offset(size.width * 0.58f, size.height * 0.76f),
            strokeWidth = stroke.width,
            cap = StrokeCap.Round,
        )
    }
}

@Composable
private fun NoticeAvatar(notice: Notice) {
    val context = LocalContext.current
    val bitmap = remember(notice.iconName, notice.packageName) {
        NoticeLog.avatarFor(context, notice)
    }
    Box(
        Modifier
            .size(28.dp)
            .clip(CircleShape)
            .background(NoticeShade),
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        }
    }
}

@Composable
private fun noticeButtonColors() = ButtonDefaults.buttonColors(
    containerColor = NoticeBand,
    contentColor = NoticeInk,
)

private fun timeLabel(millis: Long): String {
    val time = Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault())
    return DateTimeFormatter.ofPattern("h:mm a").format(time)
}
