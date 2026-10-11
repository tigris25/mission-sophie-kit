package fr.mission.sophie.nfc

import android.app.Activity
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.os.Bundle

enum class NfcStatus { OK, DISABLED, ABSENT }

/**
 * Reads only the tag UID (never writes), in reader mode so the system does not beep or open other apps.
 * Duplicate reads of the same UID within [debounceMs] are ignored.
 */
class NfcReader(
    private val activity: Activity,
    private val debounceMs: Long = 1500,
    private val onUid: (String) -> Unit,
) {
    private val adapter: NfcAdapter? = NfcAdapter.getDefaultAdapter(activity)
    private var lastUid: String? = null
    private var lastAt = 0L

    val status: NfcStatus
        get() = when {
            adapter == null -> NfcStatus.ABSENT
            !adapter.isEnabled -> NfcStatus.DISABLED
            else -> NfcStatus.OK
        }

    fun enable() {
        val a = adapter ?: return
        if (!a.isEnabled) return
        val flags = NfcAdapter.FLAG_READER_NFC_A or NfcAdapter.FLAG_READER_NFC_B or NfcAdapter.FLAG_READER_NFC_F or
            NfcAdapter.FLAG_READER_NFC_V or NfcAdapter.FLAG_READER_NO_PLATFORM_SOUNDS
        val extras = Bundle().apply { putInt(NfcAdapter.EXTRA_READER_PRESENCE_CHECK_DELAY, 250) }
        a.enableReaderMode(activity, { tag -> onTag(tag) }, flags, extras)
    }

    fun disable() {
        try { adapter?.disableReaderMode(activity) } catch (_: Exception) {}
    }

    private fun onTag(tag: Tag) {
        val uid = tag.id?.joinToString("") { "%02X".format(it) } ?: return
        val now = System.currentTimeMillis()
        synchronized(this) {
            if (uid == lastUid && now - lastAt < debounceMs) { lastAt = now; return }
            lastUid = uid; lastAt = now
        }
        activity.runOnUiThread { onUid(uid) }
    }
}
