package org.lilleypad.featherloggerapp.ble;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanResult;

import java.util.ArrayList;
import java.util.List;

/**
 *
 * Created by Duncan Lilley on 10/16/2016.
 */

public class BleScanner {
    private ArrayList<BleScannerListener> listeners;

    private BluetoothAdapter bleAdapter;
    private BluetoothLeScanner bleScanner;

    private ScanCallback callback = new ScanCallback() {
        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            super.onScanResult(callbackType, result);

            BluetoothDevice device = result.getDevice();
            if(device != null) { // Double check to make sure device isn't null
                for(BleScannerListener listener : listeners) {
                    listener.onBleDeviceDiscovered(device);
                }
            }
        }

        @Override
        public void onBatchScanResults(List<ScanResult> results) {
            super.onBatchScanResults(results);
        }

        @Override
        public void onScanFailed(int errorCode) {
            super.onScanFailed(errorCode);

            for(BleScannerListener listener : listeners) {
                listener.onBleScanError(errorCode);
            }
        }
    };



    public BleScanner() {
        bleAdapter = BluetoothAdapter.getDefaultAdapter();
        bleScanner = bleAdapter.getBluetoothLeScanner();

        listeners = new ArrayList<>();
    }

    public void startScanning() {
        bleScanner.startScan(callback);
    }

    public void stopScanning() {
        bleScanner.stopScan(callback);
    }


    /**
     * Adds a listener to the scanner. The listener will receive callbacks.
     * @param listener the listener being added
     */
    public void addListener(BleScannerListener listener) {
        listeners.add(listener);
    }

    /**
     * Removes a listener from the scanner. The listener will no longer receive callbacks.
     * @param listener the listener being removed
     */
    public void removeListener(BleScannerListener listener) {
        listeners.remove(listener);
    }


    /**
     * Definition of the BleScannerListener interface.
     * The functions defined are used as callbacks
     */
    public interface BleScannerListener {
        /**
         * Called when a device is discovered by the scanner
         * @param device the device which was discovered
         */
        void onBleDeviceDiscovered(BluetoothDevice device);

        /**
         * Called when an error occurs while scanning
         * @param errorCode the error code received
         */
        void onBleScanError(int errorCode);
    }
}
