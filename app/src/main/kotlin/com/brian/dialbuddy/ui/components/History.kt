package com.brian.dialbuddy.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.brian.dialbuddy.R
import com.brian.dialbuddy.domain.CallKind
import com.brian.dialbuddy.domain.callDurationClock
import com.brian.dialbuddy.ui.theme.Danger
import com.brian.dialbuddy.ui.theme.HistoryBand
import com.brian.dialbuddy.ui.theme.HistoryInk
import com.brian.dialbuddy.ui.theme.HistoryMuted
import com.brian.dialbuddy.ui.theme.HistoryText
import com.brian.dialbuddy.ui.theme.HistoryWell
import com.brian.dialbuddy.ui.theme.LilacLight
import com.brian.dialbuddy.viewmodel.HistoryAccess
import com.brian.dialbuddy.viewmodel.HistoryRow
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HistoryPage(
    rows: List<HistoryRow>,
    recognized: Boolean,
    access: HistoryAccess,
    onCall: (String) -> Unit,
    onViewContact: (String) -> Unit,
    onBecomeDialer: () -> Unit,
    onAllow: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier) {
        if (access != HistoryAccess.Ready && access != HistoryAccess.Loading) {
            val message = when (access) {
                HistoryAccess.NeedsRole -> stringResource(R.string.history_need_role)
                HistoryAccess.Denied -> stringResource(R.string.history_denied)
                else -> ""
            }
            if (message.isNotEmpty()) {
                Text(message, color = HistoryText, modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
            }
            if (access == HistoryAccess.NeedsRole) {
                Button(
                    onClick = onBecomeDialer,
                    colors = historyButtonColors(),
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                ) {
                    Text(stringResource(R.string.become_dialer))
                }
            }
            if (access == HistoryAccess.NeedsPermission || access == HistoryAccess.Denied) {
                Button(
                    onClick = onAllow,
                    colors = historyButtonColors(),
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                ) {
                    Text(stringResource(R.string.allow_call_log))
                }
            }
        } else if (access == HistoryAccess.Ready && rows.isEmpty()) {
            Text(
                stringResource(
                    if (recognized) R.string.history_empty_recognized else R.string.history_empty_unrecognized,
                ),
                color = HistoryMuted,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            )
        }
        LazyColumn(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(top = 8.dp),
        ) {
            rows.groupBy { it.dayLabel }.forEach { (day, calls) ->
                stickyHeader(key = "day-$day") {
                    Text(
                        day,
                        color = HistoryInk,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(HistoryBand)
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                    )
                }
                items(calls, key = { "call-${it.record.id}" }) { row ->
                    HistoryRowView(row, onCall, onViewContact)
                }
            }
        }
    }
}

@Composable
private fun HistoryRowView(
    row: HistoryRow,
    onCall: (String) -> Unit,
    onViewContact: (String) -> Unit,
) {
    val number = row.record.number
    val title = row.contactName
        ?: number.takeIf { it.isNotBlank() }
        ?: stringResource(R.string.private_number)
    val kindColor = if (row.record.kind == CallKind.Missed ||
        row.record.kind == CallKind.Rejected ||
        row.record.kind == CallKind.Blocked
    ) {
        Danger
    } else {
        HistoryBand
    }
    val duration = callDurationClock(row.record.durationSeconds)
    val detail = if (duration == null) kindLabel(row.record.kind) else "${kindLabel(row.record.kind)} · $duration"
    Row(
        Modifier.fillMaxWidth().padding(vertical = 10.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.width(84.dp),
        ) {
            val mark = row.contactName?.firstOrNull()?.uppercase() ?: stringResource(R.string.unknown_mark)
            Box(
                Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(HistoryWell)
                    .then(
                        if (row.contactName != null) {
                            Modifier.clickable { onViewContact(number) }
                        } else {
                            Modifier
                        },
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Text(mark, color = LilacLight, fontWeight = FontWeight.Bold, fontSize = 22.sp)
            }
            Text(
                title,
                color = HistoryText,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
        Column(
            Modifier.weight(1f).padding(start = 8.dp),
            horizontalAlignment = Alignment.End,
        ) {
            val callLabel = stringResource(R.string.call)
            Box(
                Modifier
                    .height(40.dp)
                    .width(72.dp)
                    .clip(CircleShape)
                    .border(1.dp, if (number.isBlank()) HistoryMuted else HistoryBand, CircleShape)
                    .clickable(enabled = number.isNotBlank()) { onCall(number) },
                contentAlignment = Alignment.Center,
            ) {
                Text(callLabel, color = if (number.isBlank()) HistoryMuted else HistoryBand, fontSize = 14.sp)
            }
            Text(
                timeLabel(row.record.dateMillis),
                color = HistoryMuted,
                fontSize = 14.sp,
                modifier = Modifier.padding(top = 6.dp),
            )
            Text(detail, color = kindColor, fontSize = 14.sp)
            if (row.contactName != null && number.isNotBlank()) {
                Text(number, color = HistoryMuted, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun kindLabel(kind: CallKind): String = when (kind) {
    CallKind.Incoming -> stringResource(R.string.call_incoming)
    CallKind.Outgoing -> stringResource(R.string.call_outgoing)
    CallKind.Missed -> stringResource(R.string.call_missed)
    CallKind.Rejected -> stringResource(R.string.call_rejected)
    CallKind.Blocked -> stringResource(R.string.call_blocked)
    CallKind.Voicemail -> stringResource(R.string.call_voicemail)
    CallKind.Other -> stringResource(R.string.call_other)
}

@Composable
private fun historyButtonColors() = ButtonDefaults.buttonColors(
    containerColor = HistoryBand,
    contentColor = HistoryInk,
)

private fun timeLabel(millis: Long): String {
    val time = Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault())
    return DateTimeFormatter.ofPattern("h:mm a").format(time)
}
