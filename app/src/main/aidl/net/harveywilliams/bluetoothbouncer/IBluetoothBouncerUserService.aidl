// IBluetoothBouncerUserService.aidl
package net.harveywilliams.bluetoothbouncer;

interface IBluetoothBouncerUserService {
    /**
     * Set the connection policy for a Bluetooth device across all supported profiles.
     *
     * @param macAddress The MAC address of the device.
     * @param policy     CONNECTION_POLICY_FORBIDDEN (0) or CONNECTION_POLICY_ALLOWED (100).
     * @return int array of size 3: [a2dpResult, headsetResult, hidResult]
     *         1 = success, 0 = failed, -1 = skipped (proxy unavailable)
     */
    int[] setConnectionPolicy(String macAddress, int policy);

    /** Returns true if the service is alive and ready. */
    boolean isAlive();
}
