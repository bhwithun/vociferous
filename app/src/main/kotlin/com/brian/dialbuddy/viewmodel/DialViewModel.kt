package com.brian.dialbuddy.viewmodel

import android.Manifest
import android.app.Application
import android.app.role.RoleManager
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.brian.dialbuddy.R
import com.brian.dialbuddy.data.AppStorage
import com.brian.dialbuddy.domain.DeletionStamp
import com.brian.dialbuddy.domain.DEFAULT_THRESHOLD_SECONDS
import com.brian.dialbuddy.domain.VoicemailEntry
import com.brian.dialbuddy.domain.clampThreshold
import com.brian.dialbuddy.domain.deletedInWindow
import com.brian.dialbuddy.domain.formatDeletionStamps
import com.brian.dialbuddy.domain.nextDeletedTotal
import com.brian.dialbuddy.domain.parseDeletionStamps
import com.brian.dialbuddy.domain.presentVoicemails
import com.brian.dialbuddy.domain.qualifiesForBulkDelete
import com.brian.dialbuddy.domain.retainRecentStamps
import com.brian.dialbuddy.domain.WINDOW_MONTH_MILLIS
import com.brian.dialbuddy.domain.WINDOW_WEEK_MILLIS
import com.brian.dialbuddy.domain.WINDOW_YEAR_MILLIS
import com.brian.dialbuddy.telecom.ContactLookup
import com.brian.dialbuddy.telecom.VoicemailAccessDenied
import com.brian.dialbuddy.telecom.VoicemailPlayer
import com.brian.dialbuddy.telecom.VoicemailStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class MainPage { Voicemail, Keypad }

enum class MailboxTab { Known, Unknown, Stats }

fun visibleMailboxTabs(knownCount: Int, unknownCount: Int): List<MailboxTab> {
    val tabs = ArrayList<MailboxTab>(3)
    if (knownCount > 0) tabs.add(MailboxTab.Known)
    if (unknownCount > 0) tabs.add(MailboxTab.Unknown)
    tabs.add(MailboxTab.Stats)
    return tabs
}

data class MailboxRow(
    val entry: VoicemailEntry,
    val contactName: String?,
    val dayLabel: String,
)

sealed class PendingConfirm {
    data class Bulk(val count: Int, val threshold: Int) : PendingConfirm()
    data class One(val id: Long, val label: String) : PendingConfirm()
}

data class DialUiState(
    val page: MainPage = MainPage.Voicemail,
    val focusKeypad: Int = 0,
    val mailboxTab: MailboxTab = MailboxTab.Unknown,
    val thresholdSeconds: Int = DEFAULT_THRESHOLD_SECONDS,
    val entries: List<VoicemailEntry> = emptyList(),
    val contacts: Map<String, String> = emptyMap(),
    val busy: Boolean = false,
    val scanned: Boolean = false,
    val statusText: String = "",
    val isDialer: Boolean = false,
    val hasVoicemailPermission: Boolean = false,
    val hasContactsPermission: Boolean = false,
    val digits: String = "",
    val dialContactName: String? = null,
    val dialContactPhoto: String? = null,
    val speakerOn: Boolean = false,
    val stamps: List<DeletionStamp> = emptyList(),
    val deletedLife: Long = 0,
    val pendingConfirm: PendingConfirm? = null,
    val toast: String? = null,
    val loaded: Boolean = false,
)

sealed class DialCommand {
    data class PlaceCall(val number: String) : DialCommand()
    data object RequestDialerRole : DialCommand()
    data object BrowseContacts : DialCommand()
    data class ViewContact(val uri: String) : DialCommand()
    data class RequestPermissions(val permissions: Array<String>) : DialCommand() {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is RequestPermissions) return false
            return permissions.contentEquals(other.permissions)
        }

        override fun hashCode(): Int = permissions.contentHashCode()
    }
}

class DialViewModel(app: Application) : AndroidViewModel(app) {
    private val storage = AppStorage(app)
    private val store = VoicemailStore(app.contentResolver)
    private val player = VoicemailPlayer(app, store, speakerOn = false) { message -> toast(message) }
    private var loadGeneration = 0
    private val _state = MutableStateFlow(DialUiState())
    val state: StateFlow<DialUiState> = _state.asStateFlow()

    private val _commands = MutableSharedFlow<DialCommand>(extraBufferCapacity = 1)
    val commands: SharedFlow<DialCommand> = _commands.asSharedFlow()

    private var lookupGeneration = 0
    private var pendingNumber: String? = null
    private var pendingBrowse = false
    private var pendingContactNumber: String? = null

    init {
        viewModelScope.launch {
            val threshold = clampThreshold(
                storage.getIntOnce(AppStorage.Keys.THRESHOLD, DEFAULT_THRESHOLD_SECONDS),
            )
            val stamps = parseDeletionStamps(storage.getStringOnce(AppStorage.Keys.DELETION_STAMPS))
            val total = storage.getLongOnce(AppStorage.Keys.DELETED_TOTAL)
            val speaker = storage.getBooleanOnce(AppStorage.Keys.SPEAKER)
            player.setSpeaker(speaker)
            _state.update {
                it.copy(
                    thresholdSeconds = threshold,
                    stamps = stamps,
                    deletedLife = total,
                    speakerOn = speaker,
                    loaded = true,
                )
            }
            refreshCapabilities()
        }
    }

    override fun onCleared() {
        player.release()
        super.onCleared()
    }

    fun refreshCapabilities() {
        val app = getApplication<Application>()
        val roleManager = app.getSystemService(RoleManager::class.java)
        val dialer = roleManager?.isRoleHeld(RoleManager.ROLE_DIALER) == true
        val voicemail = has(app, Manifest.permission.READ_VOICEMAIL) ||
            has(app, "com.android.voicemail.permission.ADD_VOICEMAIL")
        val contacts = has(app, Manifest.permission.READ_CONTACTS)
        _state.update {
            it.copy(
                isDialer = dialer,
                hasVoicemailPermission = voicemail,
                hasContactsPermission = contacts,
            )
        }
        reloadMailbox()
    }

    fun showPage(page: MainPage) {
        _state.update { it.copy(page = page) }
    }

    fun showMailbox(tab: MailboxTab) {
        _state.update { it.copy(mailboxTab = tab) }
    }

    fun setThreshold(seconds: Int) {
        val clamped = clampThreshold(seconds)
        _state.update { it.copy(thresholdSeconds = clamped) }
        viewModelScope.launch {
            storage.setInt(AppStorage.Keys.THRESHOLD, clamped)
        }
    }

    fun toggleSpeaker() {
        val next = !_state.value.speakerOn
        _state.update { it.copy(speakerOn = next) }
        player.setSpeaker(next)
        viewModelScope.launch {
            storage.setBoolean(AppStorage.Keys.SPEAKER, next)
        }
    }

    fun scan() {
        reloadMailbox()
    }

    private fun reloadMailbox() {
        val app = getApplication<Application>()
        val generation = ++loadGeneration
        if (!_state.value.isDialer) {
            _state.update {
                it.copy(
                    entries = emptyList(),
                    contacts = emptyMap(),
                    busy = false,
                    scanned = false,
                    loaded = true,
                    statusText = app.getString(R.string.need_role_to_scan),
                )
            }
            return
        }
        _state.update { it.copy(busy = true, statusText = app.getString(R.string.scanning)) }
        viewModelScope.launch {
            val loaded = withContext(Dispatchers.IO) {
                try {
                    store.load() to null
                } catch (_: VoicemailAccessDenied) {
                    emptyList<VoicemailEntry>() to app.getString(R.string.voicemail_denied)
                }
            }
            if (generation != loadGeneration) return@launch
            val (entries, failure) = loaded
            if (failure != null) {
                _state.update {
                    it.copy(
                        entries = emptyList(),
                        contacts = emptyMap(),
                        busy = false,
                        scanned = false,
                        loaded = true,
                        statusText = failure,
                    )
                }
                return@launch
            }
            val names = withContext(Dispatchers.IO) { namesFor(entries) }
            if (generation != loadGeneration) return@launch
            _state.update {
                it.copy(
                    entries = entries,
                    contacts = names,
                    busy = false,
                    scanned = true,
                    loaded = true,
                    statusText = if (presentVoicemails(entries).isEmpty()) {
                        app.getString(R.string.mailbox_empty)
                    } else {
                        ""
                    },
                )
            }
        }
    }

    private fun namesFor(entries: List<VoicemailEntry>): Map<String, String> {
        val app = getApplication<Application>()
        if (!has(app, Manifest.permission.READ_CONTACTS)) return emptyMap()
        val names = LinkedHashMap<String, String>()
        for (number in entries.mapNotNull { it.number }.distinct()) {
            val name = ContactLookup.findName(app, number)?.takeIf { it.isNotBlank() } ?: continue
            names[number] = name
        }
        return names
    }

    fun requestDialerRole() {
        _commands.tryEmit(DialCommand.RequestDialerRole)
    }

    fun requestVoicemailPermission() {
        _commands.tryEmit(
            DialCommand.RequestPermissions(
                arrayOf(
                    "com.android.voicemail.permission.ADD_VOICEMAIL",
                    Manifest.permission.READ_VOICEMAIL,
                    Manifest.permission.WRITE_VOICEMAIL,
                ),
            ),
        )
    }

    fun requestContactsPermission() {
        _commands.tryEmit(DialCommand.RequestPermissions(arrayOf(Manifest.permission.READ_CONTACTS)))
    }

    fun appendDigit(digit: String) {
        _state.update { it.copy(digits = it.digits + digit) }
        lookupDialContact()
    }

    fun backspace() {
        _state.update { it.copy(digits = it.digits.dropLast(1)) }
        lookupDialContact()
    }

    fun holdZeroForPlus() {
        val digits = _state.value.digits
        if (digits.endsWith("0")) {
            _state.update { it.copy(digits = digits.dropLast(1) + "+") }
        } else {
            _state.update { it.copy(digits = digits + "+") }
        }
        lookupDialContact()
    }

    fun consumeTelIntent(intent: Intent?) {
        val uri = intent?.data ?: return
        if (uri.scheme != "tel") return
        val number = uri.schemeSpecificPart ?: return
        if (number.isBlank()) return
        _state.update { it.copy(page = MainPage.Keypad, digits = number, focusKeypad = it.focusKeypad + 1) }
        lookupDialContact()
        if (intent.action == Intent.ACTION_CALL) {
            requestCall(number)
        }
    }

    fun browseContacts() {
        val app = getApplication<Application>()
        if (!has(app, Manifest.permission.READ_CONTACTS)) {
            pendingBrowse = true
            _commands.tryEmit(DialCommand.RequestPermissions(arrayOf(Manifest.permission.READ_CONTACTS)))
            return
        }
        pendingBrowse = false
        _commands.tryEmit(DialCommand.BrowseContacts)
    }

    fun setDialedNumber(number: String) {
        val kept = number.filter { it.isDigit() || it == '+' || it == '*' || it == '#' }
        if (kept.isBlank()) return
        _state.update { it.copy(digits = kept) }
        lookupDialContact()
    }

    fun requestCall(number: String = _state.value.digits) {
        val app = getApplication<Application>()
        if (number.isBlank()) return
        if (!_state.value.isDialer) {
            toast(app.getString(R.string.need_role_to_call))
            return
        }
        if (!has(app, Manifest.permission.CALL_PHONE)) {
            pendingNumber = number
            _commands.tryEmit(DialCommand.RequestPermissions(arrayOf(Manifest.permission.CALL_PHONE)))
            return
        }
        _commands.tryEmit(DialCommand.PlaceCall(number))
    }

    fun onPermissionsReturned(granted: Map<String, Boolean>) {
        refreshCapabilities()
        val number = pendingNumber
        pendingNumber = null
        if (number != null && granted[Manifest.permission.CALL_PHONE] == true) {
            requestCall(number)
        } else if (number != null) {
            toast(getApplication<Application>().getString(R.string.call_permission_denied))
        }
        if (pendingBrowse) {
            pendingBrowse = false
            if (granted[Manifest.permission.READ_CONTACTS] == true) browseContacts()
        }
        val contactNumber = pendingContactNumber
        pendingContactNumber = null
        if (contactNumber != null && granted[Manifest.permission.READ_CONTACTS] == true) {
            openContact(contactNumber)
        }
        lookupDialContact()
    }

    fun openContact(number: String?) {
        if (number.isNullOrBlank()) return
        val app = getApplication<Application>()
        if (!has(app, Manifest.permission.READ_CONTACTS)) {
            pendingContactNumber = number
            _commands.tryEmit(DialCommand.RequestPermissions(arrayOf(Manifest.permission.READ_CONTACTS)))
            return
        }
        viewModelScope.launch {
            val uri = withContext(Dispatchers.IO) { ContactLookup.viewUri(app, number) }
            if (uri == null) {
                toast(app.getString(R.string.contact_missing))
            } else {
                _commands.tryEmit(DialCommand.ViewContact(uri.toString()))
            }
        }
    }

    fun play(id: Long) {
        player.play(id)
    }

    fun askDeleteVisible(tab: MailboxTab) {
        val current = _state.value.copy(mailboxTab = tab)
        val app = getApplication<Application>()
        if (!current.isDialer) {
            toast(app.getString(R.string.need_role_to_scan))
            return
        }
        val targets = bulkTargets(current)
        if (current.busy || targets.isEmpty()) return
        _state.update {
            it.copy(
                mailboxTab = tab,
                pendingConfirm = PendingConfirm.Bulk(targets.size, current.thresholdSeconds),
            )
        }
    }

    fun askDeleteOne(id: Long) {
        val row = visibleRows(_state.value).firstOrNull { it.entry.id == id } ?: return
        val label = row.contactName ?: getApplication<Application>().getString(R.string.unknown_caller)
        _state.update { it.copy(pendingConfirm = PendingConfirm.One(id, label)) }
    }

    fun dismissConfirm() {
        _state.update { it.copy(pendingConfirm = null) }
    }

    fun confirmPending() {
        val pending = _state.value.pendingConfirm ?: return
        when (pending) {
            is PendingConfirm.Bulk -> deleteIds(bulkTargets(_state.value).map { it.id }.toSet())
            is PendingConfirm.One -> deleteIds(setOf(pending.id))
        }
    }

    fun consumeToast() {
        _state.update { it.copy(toast = null) }
    }

    fun visibleRows(state: DialUiState = _state.value, tab: MailboxTab = state.mailboxTab): List<MailboxRow> {
        val wantKnown = tab == MailboxTab.Known
        return presentVoicemails(state.entries)
            .map { entry ->
                MailboxRow(
                    entry = entry,
                    contactName = state.contacts[entry.number],
                    dayLabel = dayLabel(entry.dateMillis),
                )
            }
            .filter { (it.contactName != null) == wantKnown }
            .sortedByDescending { it.entry.dateMillis }
    }

    fun knownCount(state: DialUiState = _state.value): Int =
        presentVoicemails(state.entries).count { state.contacts[it.number] != null }

    fun unknownCount(state: DialUiState = _state.value): Int =
        presentVoicemails(state.entries).size - knownCount(state)

    fun bulkCount(state: DialUiState = _state.value, tab: MailboxTab = state.mailboxTab): Int =
        bulkTargets(state.copy(mailboxTab = tab)).size

    fun deletedIn(windowMillis: Long, nowMillis: Long = System.currentTimeMillis()): Long =
        deletedInWindow(_state.value.stamps, nowMillis, windowMillis)

    private fun bulkTargets(state: DialUiState): List<VoicemailEntry> {
        if (state.mailboxTab == MailboxTab.Stats) return emptyList()
        return visibleRows(state).map { it.entry }.filter { entry ->
            val number = entry.number?.trim().orEmpty()
            val recognized = state.contacts[entry.number] != null && number.isNotEmpty()
            entry.qualifiesForBulkDelete(state.thresholdSeconds, recognized)
        }
    }

    private fun deleteIds(ids: Set<Long>) {
        if (ids.isEmpty()) return
        val app = getApplication<Application>()
        _state.update { it.copy(pendingConfirm = null, busy = true) }
        viewModelScope.launch {
            val removed = withContext(Dispatchers.IO) {
                ids.filter { id ->
                    try {
                        store.delete(id)
                    } catch (_: VoicemailAccessDenied) {
                        false
                    }
                }.toSet()
            }
            if (removed.isEmpty()) {
                _state.update { it.copy(busy = false) }
                toast(app.getString(R.string.voicemail_denied))
                return@launch
            }
            player.stop()
            val now = System.currentTimeMillis()
            val current = _state.value
            val updated = current.entries.map { entry ->
                if (entry.id in removed) entry.copy(deleted = true) else entry
            }
            val stamps = retainRecentStamps(current.stamps + DeletionStamp(now, removed.size), now)
            val total = nextDeletedTotal(current.deletedLife, removed.size)
            _state.update {
                it.copy(
                    entries = updated,
                    stamps = stamps,
                    deletedLife = total,
                    busy = false,
                    toast = app.resources.getQuantityString(R.plurals.deleted_now, removed.size, removed.size),
                )
            }
            storage.setString(AppStorage.Keys.DELETION_STAMPS, formatDeletionStamps(stamps))
            storage.setLong(AppStorage.Keys.DELETED_TOTAL, total)
        }
    }

    private fun lookupDialContact() {
        val generation = ++lookupGeneration
        val number = _state.value.digits
        val allowed = _state.value.hasContactsPermission
        val longEnough = number.count { it.isDigit() } > 4
        if (!longEnough || !allowed) {
            _state.update {
                if (generation == lookupGeneration) {
                    it.copy(dialContactName = null, dialContactPhoto = null)
                } else {
                    it
                }
            }
            return
        }
        viewModelScope.launch {
            val match = withContext(Dispatchers.IO) {
                ContactLookup.find(getApplication(), number)
            }
            if (generation != lookupGeneration) return@launch
            val name = match?.name?.takeIf { it.isNotBlank() }
            _state.update {
                it.copy(
                    dialContactName = name,
                    dialContactPhoto = if (name == null) null else match.photoUri?.toString(),
                )
            }
        }
    }

    private fun dayLabel(dateMillis: Long): String {
        val app = getApplication<Application>()
        val zone = java.time.ZoneId.systemDefault()
        val day = java.time.Instant.ofEpochMilli(dateMillis).atZone(zone).toLocalDate()
        val today = java.time.LocalDate.now(zone)
        return when (day) {
            today -> app.getString(R.string.date_today)
            today.minusDays(1) -> app.getString(R.string.date_yesterday)
            else -> day.format(java.time.format.DateTimeFormatter.ofPattern("EEEE MMMM d, yyyy"))
        }
    }

    private fun toast(text: String) {
        _state.update { it.copy(toast = text) }
    }

    private fun has(app: Application, permission: String): Boolean =
        ContextCompat.checkSelfPermission(app, permission) == PackageManager.PERMISSION_GRANTED

    companion object {
        val weekWindow = WINDOW_WEEK_MILLIS
        val monthWindow = WINDOW_MONTH_MILLIS
        val yearWindow = WINDOW_YEAR_MILLIS
    }
}
