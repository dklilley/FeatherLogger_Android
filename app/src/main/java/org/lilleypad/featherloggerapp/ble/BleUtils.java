package org.lilleypad.featherloggerapp.ble;

import android.bluetooth.BluetoothAdapter;
import android.content.Context;
import android.content.pm.PackageManager;

/**
 * Specifies util methods and variables/constants for Bluetooth LE
 *
 * Created by Duncan Lilley on 10/17/2016.
 */

public class BleUtils {
    public static final int STATUS_BLE_ENABLED = 0;
    public static final int STATUS_BLUETOOTH_NOT_AVAILABLE = 1;
    public static final int STATUS_BLE_NOT_AVAILABLE = 2;
    public static final int STATUS_BLUETOOTH_DISABLED = 3;

    /**
     * Determines the status of Bluetooth on the device
     *
     * @param context the context this function is being called from
     * @return one of: STATUS_BLE_ENABLED, STATUS_BLUETOOTH_NOT_AVAILABLE,
     * STATUS_BLE_NOT_AVAILABLE, or STATUS_BLUETOOTH_DISABLED.
     */
    public static int getBleStatus(Context context) {
        if (!context.getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
            return STATUS_BLE_NOT_AVAILABLE;
        }

        final BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();

        if (adapter == null) {
            return STATUS_BLUETOOTH_NOT_AVAILABLE;
        }

        if (!adapter.isEnabled()) {
            return STATUS_BLUETOOTH_DISABLED;
        }

        return STATUS_BLE_ENABLED;
    }
}
