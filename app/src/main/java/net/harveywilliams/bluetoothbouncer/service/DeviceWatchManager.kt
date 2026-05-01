package net.harveywilliams.bluetoothbouncer.service

import android.annotation.SuppressLint
import android.companion.AssociationInfo
import android.companion.AssociationRequest
import android.companion.BluetoothDeviceFilter
import android.companion.CompanionDeviceManager
import android.content.Context
import android.content.IntentSender
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.harveywilliams.bluetoothbouncer.data.BlockedDeviceDao

/**
 * Manages CDM (CompanionDeviceManager) watch operations for a device.
 *
 * Required API 33 (TIRAMISU) because:
 *  - [startObservingDevicePresence] / [stopObservingDevicePresence] are API 33+
 *  - [AssociationInfo] callbacks ([onAssociationPending] / [onAssociationCreated]) are API 33+
 *
 * Callers are responsible for guarding instantiation/use with an API version check.
 */
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
class DeviceWatchManager(
    private val context: Context,
    private val dao: BlockedDeviceDao,
) {
    private val cdm: CompanionDeviceManager =
        context.getSystemService(CompanionDeviceManager::class.java)

    /**
     * Initiates a CDM Bluetooth association for [macAddress].
     *
     * - [onPendingIntent]: called with an [IntentSender] the UI must launch via
     *   [startIntentSenderForResult] so the user can confirm in the system dialog.
     * - [onSuccess]: called with the [AssociationInfo] once the user confirms.
     * - [onFailure]: called with an optional error message if association cannot proceed.
     */
    @SuppressLint("MissingPermission")
    fun associate(
        macAddress: String,
        onPendingIntent: (IntentSender) -> Unit,
        onSuccess: (AssociationInfo) -> Unit,
        onFailure: (String?) -> Unit,
    ) {
        val deviceFilter = BluetoothDeviceFilter.Builder()
            .setAddress(macAddress)
            .build()
        val request = AssociationRequest.Builder()
            .addDeviceFilter(deviceFilter)
            .setSingleDevice(true)
            .build()

        cdm.associate(request, context.mainExecutor, object : CompanionDeviceManager.Callback() {
            override fun onAssociationPending(intentSender: IntentSender) {
                Log.d(TAG, "Association pending for $macAddress — launching dialog")
                onPendingIntent(intentSender)
            }

            override fun onAssociationCreated(associationInfo: AssociationInfo) {
                Log.d(TAG, "Association created for $macAddress: id=${associationInfo.id}")
                onSuccess(associationInfo)
            }

            override fun onFailure(error: CharSequence?) {
                Log.w(TAG, "Association failed for $macAddress: $error")
                onFailure(error?.toString())
            }
        })
    }

    /**
     * Ensures a CDM association exists for [macAddress].
     *
     * If a CDM association is already present ([existingAssociationId] is non-null),
     * [onSuccess] is called immediately with the existing ID — no system dialog is shown.
     * Otherwise, falls through to a full [associate] call (showing the dialog).
     *
     * This is used by the Connect button so that repeat taps on the same device skip
     * the system confirmation dialog after the first use.
     */
    fun ensureAssociated(
        macAddress: String,
        existingAssociationId: Int?,
        onPendingIntent: (IntentSender) -> Unit,
        onSuccess: (Int) -> Unit,
        onFailure: (String?) -> Unit,
    ) {
        if (existingAssociationId != null) {
            Log.d(TAG, "CDM association already exists for $macAddress (id=$existingAssociationId) — skipping dialog")
            onSuccess(existingAssociationId)
            return
        }
        associate(
            macAddress = macAddress,
            onPendingIntent = onPendingIntent,
            onSuccess = { associationInfo -> onSuccess(associationInfo.id) },
            onFailure = onFailure,
        )
    }

    /**
     * Registers the device for presence observation, persists [associationId] to Room,
     * and sets [isAlertEnabled] to true.
     *
     * Call this after a CDM association is confirmed (either newly created or pre-existing).
     * [startObservingDevicePresence] takes the device MAC address on API 33.
     */
    suspend fun enableWatch(macAddress: String, associationId: Int) {
        withContext(Dispatchers.Main) {
            cdm.startObservingDevicePresence(macAddress)
        }
        dao.updateCdmAssociationId(macAddress, associationId)
        dao.updateIsAlertEnabled(macAddress, true)
        Log.d(TAG, "Watch enabled for $macAddress (associationId=$associationId)")
    }

    /**
     * Starts presence observation and persists [associationId] to Room, but does NOT set
     * [isAlertEnabled]. Used by the Connect button path so that [DeviceWatcherService]
     * can auto-revert the device to blocked when it leaves range, without turning on Alert
     * notifications (the user did not request notifications, only a temporary connection).
     */
    suspend fun startObservingForConnect(macAddress: String, associationId: Int) {
        withContext(Dispatchers.Main) {
            cdm.startObservingDevicePresence(macAddress)
        }
        dao.updateCdmAssociationId(macAddress, associationId)
        Log.d(TAG, "Observation started for connect on $macAddress (associationId=$associationId)")
    }

    /**
     * Stops presence observation and sets [isAlertEnabled] to false, but retains the CDM
     * association so that a future Connect action does not require a new system dialog.
     *
     * Use this for the Alert toggle OFF path.
     * [stopObservingDevicePresence] takes the device MAC address on API 33.
     */
    suspend fun disableWatch(macAddress: String) {
        withContext(Dispatchers.Main) {
            try {
                cdm.stopObservingDevicePresence(macAddress)
                Log.d(TAG, "Stopped observing presence for $macAddress")
            } catch (e: Exception) {
                Log.w(TAG, "stopObservingDevicePresence failed for $macAddress", e)
            }
        }
        dao.updateIsAlertEnabled(macAddress, false)
        Log.d(TAG, "Alert disabled for $macAddress (CDM association retained)")
    }

    /**
     * Stops presence observation, removes the CDM association, clears [cdmAssociationId],
     * and sets [isAlertEnabled] to false in Room.
     *
     * Use this when a device is fully unblocked (device record will be deleted from Room).
     * Each step is attempted independently — a failure in one does not prevent the others.
     */
    suspend fun disableWatchAndDisassociate(macAddress: String, associationId: Int) {
        withContext(Dispatchers.Main) {
            try {
                cdm.stopObservingDevicePresence(macAddress)
                Log.d(TAG, "Stopped observing presence for $macAddress")
            } catch (e: Exception) {
                Log.w(TAG, "stopObservingDevicePresence failed for $macAddress", e)
            }
            try {
                cdm.disassociate(associationId)
                Log.d(TAG, "Disassociated associationId=$associationId")
            } catch (e: Exception) {
                Log.w(TAG, "disassociate failed for associationId=$associationId", e)
            }
        }
        dao.updateCdmAssociationId(macAddress, null)
        dao.updateIsAlertEnabled(macAddress, false)
        Log.d(TAG, "Watch fully disabled and disassociated for $macAddress")
    }

    companion object {
        private const val TAG = "DeviceWatchManager"
    }
}
