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
     * Registers the device for presence observation and persists [associationId] to Room.
     * Call this after [onAssociationCreated] fires (i.e. user confirmed the dialog).
     *
     * [startObservingDevicePresence] takes the device MAC address on API 33.
     */
    suspend fun enableWatch(macAddress: String, associationId: Int) {
        withContext(Dispatchers.Main) {
            cdm.startObservingDevicePresence(macAddress)
        }
        dao.updateCdmAssociationId(macAddress, associationId)
        Log.d(TAG, "Watch enabled for $macAddress (associationId=$associationId)")
    }

    /**
     * Stops presence observation, removes the CDM association, and clears [cdmAssociationId]
     * in Room.
     *
     * Each step is attempted independently — a failure in one does not prevent the others.
     * [stopObservingDevicePresence] takes the device MAC address on API 33.
     */
    suspend fun disableWatch(macAddress: String, associationId: Int) {
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
        Log.d(TAG, "Watch disabled for $macAddress")
    }

    companion object {
        private const val TAG = "DeviceWatchManager"
    }
}
