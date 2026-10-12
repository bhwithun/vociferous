package com.brian.dialbuddy.ui.screens

import android.widget.Toast
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.brian.dialbuddy.R
import com.brian.dialbuddy.domain.DeletionCounts
import com.brian.dialbuddy.domain.presentVoicemails
import com.brian.dialbuddy.telecom.Calls
import com.brian.dialbuddy.ui.components.KeypadPane
import com.brian.dialbuddy.ui.components.MailboxPane
import com.brian.dialbuddy.ui.components.RaggedCard
import com.brian.dialbuddy.ui.components.SpeakerButton
import com.brian.dialbuddy.ui.components.StatTable
import com.brian.dialbuddy.ui.components.StatsActions
import com.brian.dialbuddy.ui.theme.Accent
import com.brian.dialbuddy.ui.theme.DigitStroke
import com.brian.dialbuddy.ui.theme.Forest
import com.brian.dialbuddy.ui.theme.InkMuted
import com.brian.dialbuddy.ui.theme.PhoneBand
import com.brian.dialbuddy.ui.theme.PhoneInk
import com.brian.dialbuddy.ui.theme.PhoneStroke
import com.brian.dialbuddy.ui.theme.TitleBand
import com.brian.dialbuddy.ui.theme.TitleInk
import com.brian.dialbuddy.ui.theme.StatCoral
import com.brian.dialbuddy.ui.theme.StatHoney
import com.brian.dialbuddy.ui.theme.StatLilac
import com.brian.dialbuddy.viewmodel.DialUiState
import com.brian.dialbuddy.viewmodel.DialViewModel
import com.brian.dialbuddy.viewmodel.MailboxTab
import com.brian.dialbuddy.viewmodel.PendingConfirm
import com.brian.dialbuddy.viewmodel.visibleMailboxTabs

private const val PHONE = 0
private const val VOICEMAIL = 1

@Composable
fun DialScreen(
    viewModel: DialViewModel,
    state: DialUiState,
) {
    val context = LocalContext.current
    val inCall by Calls.state.collectAsState()
    val callActive = inCall.calls.isNotEmpty()
    var section by remember { mutableIntStateOf(PHONE) }
    var wasInCall by remember { mutableStateOf(false) }
    val phonePager = rememberPagerState(initialPage = 0) { if (callActive) 2 else 1 }
    val mailCards = visibleMailboxTabs(viewModel.knownCount(state), viewModel.unknownCount(state))
    val mailPager = rememberPagerState(initialPage = 0) { mailCards.size }
    var shownMailCard by remember { mutableStateOf<MailboxTab?>(null) }
    LaunchedEffect(state.toast) {
        val message = state.toast ?: return@LaunchedEffect
        Toast.makeText(context, message, Toast.LENGTH_LONG).show()
        viewModel.consumeToast()
    }
    LaunchedEffect(state.focusKeypad) {
        if (state.focusKeypad > 0) {
            section = PHONE
            phonePager.scrollToPage(0)
        }
    }
    LaunchedEffect(callActive) {
        if (callActive && !wasInCall) {
            section = PHONE
            phonePager.animateScrollToPage(1)
        }
        if (!callActive && phonePager.currentPage != 0) {
            phonePager.scrollToPage(0)
        }
        wasInCall = callActive
    }
    LaunchedEffect(mailPager.settledPage, mailCards) {
        mailCards.getOrNull(mailPager.settledPage)?.let { shownMailCard = it }
    }
    LaunchedEffect(mailCards) {
        if (mailPager.currentPage >= mailCards.size) {
            mailPager.scrollToPage(mailCards.lastIndex)
        }
        val current = shownMailCard ?: return@LaunchedEffect
        val at = mailCards.getOrNull(mailPager.currentPage)
        if (current in mailCards && at == current) return@LaunchedEffect
        val preferred = when {
            current in mailCards -> current
            current == MailboxTab.Known && MailboxTab.Unknown in mailCards -> MailboxTab.Unknown
            else -> mailCards.last()
        }
        val target = mailCards.indexOf(preferred)
        if (target >= 0 && mailPager.currentPage != target) mailPager.scrollToPage(target)
    }
    Column(
        Modifier
            .fillMaxSize()
            .background(Forest)
            .windowInsetsPadding(WindowInsets.safeDrawing),
    ) {
        if (section == PHONE) {
            PageDots(
                page = phonePager.currentPage,
                count = if (callActive) 2 else 1,
                color = PhoneBand,
            )
            HorizontalPager(
                state = phonePager,
                modifier = Modifier.weight(1f).fillMaxWidth(),
                pageSpacing = 20.dp,
            ) { page ->
                PageCard(
                    seed = page,
                    title = if (page == 1 && callActive) stringResource(R.string.state_call) else null,
                    band = PhoneBand,
                    ink = PhoneInk,
                    stroke = PhoneStroke,
                ) {
                    if (page == 0 || !callActive) {
                        KeypadPane(
                            digits = state.digits,
                            contactName = state.dialContactName,
                            contactPhoto = state.dialContactPhoto,
                            hint = stringResource(R.string.keypad_hint),
                            backspaceLabel = stringResource(R.string.backspace),
                            callLabel = stringResource(R.string.call),
                            browseLabel = stringResource(R.string.browse),
                            onDigit = viewModel::appendDigit,
                            onPlus = viewModel::holdZeroForPlus,
                            onBackspace = viewModel::backspace,
                            onCall = { viewModel.requestCall() },
                            onBrowse = viewModel::browseContacts,
                            stroke = PhoneStroke,
                            accent = PhoneBand,
                            modifier = Modifier.fillMaxSize(),
                        )
                    } else {
                        InCallPane(inCall, Modifier.fillMaxSize())
                    }
                }
            }
        } else {
            PageDots(page = mailPager.currentPage, count = mailCards.size, color = TitleBand)
            HorizontalPager(
                state = mailPager,
                modifier = Modifier.weight(1f).fillMaxWidth(),
                pageSpacing = 20.dp,
            ) { page ->
                val card = mailCards[page]
                PageCard(
                    seed = card.ordinal + 2,
                    title = mailTitle(card),
                    band = TitleBand,
                    ink = TitleInk,
                    stroke = DigitStroke,
                    overlay = {
                        if (card != MailboxTab.Stats) {
                            SpeakerButton(
                                on = state.speakerOn,
                                onClick = viewModel::toggleSpeaker,
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(top = 10.dp, end = 16.dp),
                            )
                        }
                    },
                ) {
                    if (card == MailboxTab.Stats) {
                        StatsPage(viewModel, state)
                    } else {
                        MailboxPage(viewModel, state, card)
                    }
                }
            }
        }
        Row(
            Modifier
                .fillMaxWidth()
                .height(68.dp)
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            BottomTab(
                stringResource(R.string.tab_phone),
                section == PHONE,
                PhoneBand,
                PhoneInk,
                Modifier.weight(1f),
                icon = { color -> PhoneTabIcon(color) },
            ) { section = PHONE }
            BottomTab(
                stringResource(
                    R.string.tab_voicemail_count,
                    viewModel.knownCount(state) + viewModel.unknownCount(state),
                ),
                section == VOICEMAIL,
                TitleBand,
                TitleInk,
                Modifier.weight(1f),
                icon = { color -> VoicemailTabIcon(color) },
            ) { section = VOICEMAIL }
        }
    }
    ConfirmDialogs(viewModel, state.pendingConfirm)
}

@Composable
private fun PageDots(page: Int, count: Int, color: Color) {
    Text(
        text = sectionDots(page, count),
        color = color,
        fontSize = 14.sp,
        textAlign = TextAlign.Center,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 2.dp, bottom = 2.dp),
    )
}

@Composable
private fun PageCard(
    seed: Int,
    title: String?,
    band: Color,
    ink: Color,
    stroke: Color,
    overlay: @Composable BoxScope.() -> Unit = {},
    content: @Composable () -> Unit,
) {
    RaggedCard(
        seed = seed,
        title = title,
        band = band,
        ink = ink,
        stroke = stroke,
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 14.dp, vertical = 10.dp),
        overlay = overlay,
    ) {
        content()
    }
}

private val tabShape = RoundedCornerShape(topStart = 14.dp, topEnd = 14.dp)

@Composable
private fun BottomTab(
    label: String,
    selected: Boolean,
    selectedFill: Color,
    selectedInk: Color,
    modifier: Modifier,
    icon: @Composable (Color) -> Unit,
    onClick: () -> Unit,
) {
    val fill = if (selected) selectedFill else Forest
    val ink = if (selected) selectedInk else InkMuted
    val edge = if (selected) selectedFill else DigitStroke
    Column(
        modifier
            .fillMaxSize()
            .clip(tabShape)
            .background(fill)
            .border(1.dp, edge, tabShape)
            .clickable(onClick = onClick)
            .padding(top = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        icon(ink)
        Text(label, color = ink, fontSize = 13.sp, modifier = Modifier.padding(top = 2.dp))
    }
}

@Composable
private fun PhoneTabIcon(color: Color) {
    Canvas(Modifier.size(22.dp)) {
        val stroke = Stroke(width = 1.6.dp.toPx())
        drawRoundRect(
            color = color,
            topLeft = Offset(size.width * 0.28f, size.height * 0.06f),
            size = Size(size.width * 0.44f, size.height * 0.88f),
            cornerRadius = CornerRadius(size.width * 0.1f),
            style = stroke,
        )
        drawLine(
            color = color,
            start = Offset(size.width * 0.4f, size.height * 0.22f),
            end = Offset(size.width * 0.6f, size.height * 0.22f),
            strokeWidth = 1.6.dp.toPx(),
        )
    }
}

@Composable
private fun VoicemailTabIcon(color: Color) {
    Canvas(Modifier.size(22.dp)) {
        val stroke = Stroke(width = 1.6.dp.toPx())
        val radius = size.minDimension * 0.2f
        val cy = size.height * 0.58f
        drawCircle(color, radius, Offset(size.width * 0.28f, cy), style = stroke)
        drawCircle(color, radius, Offset(size.width * 0.72f, cy), style = stroke)
        drawLine(
            color = color,
            start = Offset(size.width * 0.28f, cy),
            end = Offset(size.width * 0.72f, cy),
            strokeWidth = 1.6.dp.toPx(),
        )
    }
}

@Composable
private fun mailTitle(card: MailboxTab): String {
    return when (card) {
        MailboxTab.Known -> stringResource(R.string.tab_known)
        MailboxTab.Unknown -> stringResource(R.string.tab_unknown)
        MailboxTab.Stats -> stringResource(R.string.tab_settings)
    }
}

private fun sectionDots(page: Int, count: Int): String {
    return (0 until count).joinToString(" ") { if (it == page) "●" else "○" }
}

@Composable
private fun DeletionCard(counts: DeletionCounts, title: String, color: Color) {
    StatTable(
        title = title,
        recognized = counts.known.toLong(),
        unrecognized = counts.unknown.toLong(),
        bulk = counts.bulk.toLong(),
        color = color,
    )
}

@Composable
private fun StatsPage(
    viewModel: DialViewModel,
    state: DialUiState,
) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
            DeletionCard(viewModel.deletedIn(DialViewModel.weekWindow), stringResource(R.string.deleted_week_label), Accent)
            DeletionCard(viewModel.deletedIn(DialViewModel.monthWindow), stringResource(R.string.deleted_month_label), StatHoney)
            DeletionCard(viewModel.deletedIn(DialViewModel.yearWindow), stringResource(R.string.deleted_year_label), StatLilac)
            StatTable(
                title = stringResource(R.string.deleted_life_label),
                recognized = state.deletedKnown,
                unrecognized = state.deletedUnknown,
                bulk = state.deletedBulk,
                color = StatCoral,
            )
            StatsActions(
                isDialer = state.isDialer,
                showVoicemail = state.isDialer && !state.hasVoicemailPermission,
                showContacts = state.isDialer && !state.hasContactsPermission,
                becomeLabel = stringResource(R.string.become_dialer),
                voicemailLabel = stringResource(R.string.allow_voicemail),
                contactsLabel = stringResource(R.string.allow_contacts),
                onBecome = viewModel::requestDialerRole,
                onVoicemail = viewModel::requestVoicemailPermission,
                onContacts = viewModel::requestContactsPermission,
            )
        }
    }
}

@Composable
private fun ConfirmDialogs(viewModel: DialViewModel, pending: PendingConfirm?) {
    when (pending) {
        is PendingConfirm.Bulk -> AlertDialog(
            onDismissRequest = viewModel::dismissConfirm,
            title = { Text(stringResource(R.string.delete_title)) },
            text = { Text(stringResource(R.string.delete_message, pending.count, pending.threshold)) },
            confirmButton = {
                TextButton(onClick = viewModel::confirmPending) { Text(stringResource(R.string.delete)) }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissConfirm) { Text(stringResource(R.string.cancel)) }
            },
        )
        is PendingConfirm.One -> AlertDialog(
            onDismissRequest = viewModel::dismissConfirm,
            title = { Text(stringResource(R.string.delete_one_title)) },
            text = { Text(stringResource(R.string.delete_one_message, pending.label)) },
            confirmButton = {
                TextButton(onClick = viewModel::confirmPending) { Text(stringResource(R.string.delete)) }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissConfirm) { Text(stringResource(R.string.cancel)) }
            },
        )
        null -> Unit
    }
}

@Composable
private fun MailboxPage(viewModel: DialViewModel, state: DialUiState, tab: MailboxTab) {
    val rows = viewModel.visibleRows(state, tab)
    val bulk = viewModel.bulkCount(state, tab)
    MailboxPane(
        tab = tab,
        knownCount = viewModel.knownCount(state),
        unknownCount = viewModel.unknownCount(state),
        rows = rows,
        bulkCount = bulk,
        thresholdSeconds = state.thresholdSeconds,
        statusText = state.statusText,
        showSamsungNote = state.isDialer && state.scanned && !state.busy &&
            presentVoicemails(state.entries).isEmpty(),
        samsungNote = stringResource(R.string.samsung_note),
        thresholdLabel = stringResource(R.string.threshold_label),
        deleteLabel = pluralStringResource(R.plurals.delete_button, bulk, bulk),
        deleteEnabled = state.isDialer && !state.busy && bulk > 0,
        busy = state.busy,
        onTab = viewModel::showMailbox,
        onThreshold = viewModel::setThreshold,
        onDelete = { viewModel.askDeleteVisible(tab) },
        onPlay = viewModel::play,
        onDeleteOne = viewModel::askDeleteOne,
        onViewContact = { viewModel.openContact(it.entry.number) },
        stats = {},
        showTabs = false,
        modifier = Modifier.fillMaxSize(),
    )
}
