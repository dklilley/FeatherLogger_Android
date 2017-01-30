package org.lilleypad.featherloggerapp.ble;

import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.content.Context;
import android.util.Log;

import org.lilleypad.featherloggerapp.util.FeatherLoggerCmd;

import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.UUID;

/**
 * Class used to create and manage a GATT connection to a Bluetooth LE device
 * <p>
 * Created by Duncan Lilley on 10/21/2016.
 */

public class BleUartGattManager {

    // Log
    private final static String TAG = BleUartGattManager.class.getSimpleName();

    // Constants
    private static final String UUID_SERVICE = "6E400001-B5A3-F393-E0A9-E50E24DCCA9E";
    private static final String UUID_RX = "6E400003-B5A3-F393-E0A9-E50E24DCCA9E";
    private static final String UUID_TX = "6E400002-B5A3-F393-E0A9-E50E24DCCA9E";
    private static final String CHARACTERISTIC_CONFIG = "00002902-0000-1000-8000-00805F9B34FB";
    private static final int kTxMaxCharacters = 20;

    // Listener
    private BleUartListener listener;

    // Bluetooth
    private BluetoothGattService uartService;
    private BluetoothGattCharacteristic rxCharacteristic;
    private BluetoothGattCharacteristic txCharacteristic;
    private BluetoothGattDescriptor notificationDescriptor;
    private boolean isUartReady = false; // Whether or not the needed characteristics have been discovered and notifications enabled

    //    private BluetoothDevice device;
    private BluetoothGatt gatt;
    private BluetoothGattCallback gattCallback = new BluetoothGattCallback() {
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            super.onConnectionStateChange(gatt, status, newState);

            if (newState == BluetoothGatt.STATE_CONNECTED) {
                // Device has been connected to, begin to search for services
                gatt.discoverServices();
            } else if (newState == BluetoothGatt.STATE_DISCONNECTED) {
                // Connection to device lost, alert the listener
                if (listener != null) listener.onConnectionLost();
            }
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            super.onServicesDiscovered(gatt, status);

            uartService = gatt.getService(UUID.fromString(UUID_SERVICE));

            if (uartService != null) {
                rxCharacteristic = uartService.getCharacteristic(UUID.fromString(UUID_RX));
                txCharacteristic = uartService.getCharacteristic(UUID.fromString(UUID_TX));

                if (rxCharacteristic != null && txCharacteristic != null) {
                    // Necessary characteristics have been discovered - enable RX notifications
                    gatt.setCharacteristicNotification(rxCharacteristic, true);

                    notificationDescriptor = rxCharacteristic.getDescriptor(UUID.fromString(CHARACTERISTIC_CONFIG));
                    notificationDescriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
                    gatt.writeDescriptor(notificationDescriptor);
                }
            }
        }

        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
            super.onCharacteristicChanged(gatt, characteristic);

            // Data received from RX characteristic
            String data = characteristic.getStringValue(0);
            if (listener != null) {
                listener.onDataReceived(data);
            }
        }

        @Override
        public void onDescriptorWrite(BluetoothGatt gatt, BluetoothGattDescriptor descriptor, int status) {
            super.onDescriptorWrite(gatt, descriptor, status);

            if (descriptor == notificationDescriptor) {
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    // Notifications have been enabled. Notify listeners that UART is ready, and request data from FeatherLogger

                    isUartReady = true;
                    if (listener != null) listener.onUartReady();
                    sendData(FeatherLoggerCmd.REQUEST_DATA);
                } else {
                    Log.e(TAG, "Error enabling notifications.");
                }
            }
        }
    };

    public BleUartGattManager(BluetoothDevice device, Context context) {
        //this.device = device;
        gatt = device.connectGatt(context, false, gattCallback);
        gatt.connect();
    }

    /**
     * Sends some data to the connected Bluetooth device
     *
     * @param data A string containing the data to be sent
     */
    public void sendData(String data) {
        if (data != null) {
            sendData(data.getBytes(Charset.forName("UTF-8")));
        }
    }

    private LinkedList<byte[]> sendQueue = new LinkedList<>();

    /**
     * Helper method to send some bytes to the connected Bluetooth device
     *
     * @param data The bytes of data to be sent
     */
    private void sendData(byte[] data) {
        if (txCharacteristic != null) {
            // Split data into chunks (UART service has a maximum number of characters that can be written)
            for (int i = 0; i < data.length; i += kTxMaxCharacters) {
                final byte[] chunk = Arrays.copyOfRange(data, i, Math.min(i + kTxMaxCharacters, data.length));
                for(byte b : chunk) Log.e(TAG, (char)b + "");

                sendQueue.add(chunk);

                txCharacteristic.setValue(chunk);

                // TODO: Devise better method of ensuring mutliple chunks of data send as this could cause infinite loop(?)
                while(!gatt.writeCharacteristic(txCharacteristic));

            }

        } else {
            Log.w(TAG, "Uart Service not discovered - Unable to send data");
        }
    }

    /**
     * Disconnects the gatt server
     */
    public void disconnect() {
        gatt.disconnect();
    }

    /**
     * Adds a listener to this class
     *
     * @param listener the listener being added
     */
    public void setListener(BleUartListener listener) {
        this.listener = listener;
        if (isUartReady) {
            listener.onUartReady();
            sendData(FeatherLoggerCmd.REQUEST_DATA);
        }
    }

    /**
     * Removes the listener from this class
     */
    public void removeListener() {
        listener = null;
    }

    /**
     * This interface is used to define callback methods used to alert listeners to changes in
     * communication with the FeatherLogger.
     */
    public interface BleUartListener {
        /**
         * Called when data is received from the FeatherLogger
         *
         * @param string the data received
         */
        void onDataReceived(String string);

        /**
         * Called when all necessary characteristics for communication have been discovered
         */
        void onUartReady();

        /**
         * Called when the connection to the BLE device is lost
         */
        void onConnectionLost();
    }
}
