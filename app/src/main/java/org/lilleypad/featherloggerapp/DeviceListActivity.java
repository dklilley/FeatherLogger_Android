package org.lilleypad.featherloggerapp;


import android.Manifest;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import org.lilleypad.featherloggerapp.ble.BleScanner;
import org.lilleypad.featherloggerapp.ble.BleUtils;

public class DeviceListActivity extends AppCompatActivity implements BleScanner.BleScannerListener {

    // Log
    private final static String TAG = DeviceListActivity.class.getSimpleName();

    // Constants
    private final int kActivityRequestCode_EnableBluetooth = 1;
    private final int kActivityRequestCode_ConnectedActivity = 2;
    private final int kPermissionRequestCoarseLocation = 3;
    public final static String kActivityExtraDevice = "net.lilleypad.featherloggerapp.DeviceListActivity.bleDevice";
    public final static String kActivityExtraDeviceName = "net.lilleypad.featherloggerapp.DeviceListActivity.bleDeviceName";

    // UI
    private ListView mScannedDevicesListView;
    private ArrayAdapter<BluetoothDeviceData> mScannedDevicesAdapter;
    private Button mScanButton;
    private TextView mNoDevicesTextView;
    private TextView mDiscoveredDevicesTextView;
    private TextView mConnectDirectionsTextView;

    // Bluetooth
    private BleScanner bleScanner;
    private boolean isScanning = false;
    private boolean wasScanning = false; // To determine whether to restart scanning
    private boolean bleEnabled = false;
    private boolean locationEnabled = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_device_list);

        // Initialize Bluetooth Variables
        bleScanner = new BleScanner();
        bleScanner.addListener(this);

        // Initialize UI Variables
        mScannedDevicesListView = (ListView) findViewById(R.id.discoveredDevicesListView);
        mScannedDevicesAdapter = new ArrayAdapter<BluetoothDeviceData>(this, android.R.layout.simple_list_item_1) {
            @Override
            public void add(BluetoothDeviceData object) {
                if (object == null) return;

                BluetoothDevice deviceToAdd = object.getDevice();

                // Ensure that a duplicate device is not added to the list
                for (int i = 0; i < this.getCount(); i++) {
                    BluetoothDevice device = this.getItem(i).getDevice();
                    if (deviceToAdd.getAddress().equals(device.getAddress())) {
                        // Already in list
                        return;
                    }
                }

                // Not in list
                super.add(object);
            }
        };
        mScannedDevicesListView.setAdapter(mScannedDevicesAdapter);
        mScannedDevicesListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {
                wasScanning = isScanning;

                // Stop scanning
                stopScanning();

                // Get the device data for the device selecte
                BluetoothDeviceData deviceData = mScannedDevicesAdapter.getItem(i);

                // Attempt to connect to that device
                promptConnection(deviceData); // Attempt to connect to that device
            }
        });

        mScanButton = (Button) findViewById(R.id.refreshDevicesBtn);
        mNoDevicesTextView = (TextView) findViewById(R.id.noDevicesTextView);
        mDiscoveredDevicesTextView = (TextView) findViewById(R.id.discoveredDevicesLabel);
        mConnectDirectionsTextView = (TextView) findViewById(R.id.discoveredDevicesDirections);


        // Ensure Bluetooth is available
        manageBluetoothAvailability();

        // Request Bluetooth scanning permissions
        requestLocationPermissionIfNeeded();
    }

    /**
     * Requests permission for coarse location, if the user has not already granted it.
     * Location permission is needed for bluetooth scanning
     */
    private void requestLocationPermissionIfNeeded() {
        if (this.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            final AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setTitle(R.string.request_location_dialog_title)
                    .setMessage(R.string.request_location_dialog_body)
                    .setPositiveButton(android.R.string.ok, null)
                    .setOnDismissListener(new DialogInterface.OnDismissListener() {
                        @Override
                        public void onDismiss(DialogInterface dialogInterface) {
                            requestPermissions(new String[]{Manifest.permission.ACCESS_COARSE_LOCATION}, kPermissionRequestCoarseLocation);
                        }
                    })
                    .show();
        } else {
            locationEnabled = true;
            beginScanning();
        }
    }

    /**
     * Checks the status of Bluetooth on the device, and acts based on that. If it is disabled,
     * prompts the user to enable it. If it is enabled, begins scanning. Otherwise if bluetooth
     * is not available, displays an error.
     *
     * @return True if bluetooth is enabled
     */
    private boolean manageBluetoothAvailability() {

        // Check Bluetooth hardware status
        int errorMessageId = 0;
        final int bleStatus = BleUtils.getBleStatus(getBaseContext());

        switch (bleStatus) {
            case BleUtils.STATUS_BLE_NOT_AVAILABLE:
                errorMessageId = R.string.dialog_error_no_ble;
                break;
            case BleUtils.STATUS_BLUETOOTH_NOT_AVAILABLE:
                errorMessageId = R.string.dialog_error_no_bluetooth;
                break;
            case BleUtils.STATUS_BLUETOOTH_DISABLED:
                // If not enabled, prompt user to enable it
                Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                startActivityForResult(enableBtIntent, kActivityRequestCode_EnableBluetooth); // Execution will continue at onActivityResult

                break;
            case BleUtils.STATUS_BLE_ENABLED:
                bleEnabled = true;
                beginScanning();
                break;
        }

        if (errorMessageId > 0) { // Error occurred
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setMessage(errorMessageId)
                    .setPositiveButton(android.R.string.ok, null)
                    .show();
        }

        return bleStatus == BleUtils.STATUS_BLE_ENABLED;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == kPermissionRequestCoarseLocation) {
            if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Log.d(TAG, "Location permission granted.");
                locationEnabled = true;
                beginScanning();
                updateUI(); // Update UI
            } else {
                final AlertDialog.Builder builder = new AlertDialog.Builder(this);
                builder.setTitle(R.string.denied_location_dialog_title)
                        .setMessage(R.string.denied_location_dialog_body)
                        .setPositiveButton(android.R.string.ok, null)
                        .show();
            }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == kActivityRequestCode_EnableBluetooth) {
            if (resultCode == Activity.RESULT_OK) { // Permission Granted
                // Resume Scanning
                bleEnabled = true;
                beginScanning();
            } else if (resultCode == Activity.RESULT_CANCELED) {
                Toast.makeText(getBaseContext(), "Features will be unavailable unless Bluetooth is enabled.", Toast.LENGTH_LONG).show();
                mScanButton.setEnabled(false);
            }
        }
    }

    @Override
    public void onResume() {
        super.onResume();

        // Clear lists
        mScannedDevicesAdapter.clear();
        mScannedDevicesAdapter.notifyDataSetChanged();
        beginScanning();
    }

    @Override
    public void onPause() {

        super.onPause();
    }

    @Override
    public void onStop() {

        super.onStop();
    }

    @Override
    public void onDestroy() {

        super.onDestroy();
    }

    /**
     * Attempts to begin scanning for Bluetooth LE devices. Scanning will not begin unless
     * location permission has been granted and Bluetooth LE is available and enabled on
     * the device.
     */
    private void beginScanning() {
        if (bleEnabled && locationEnabled) { // Check to see if all is ready for scanning
            isScanning = true;
            bleScanner.startScanning();
            updateUI();
        }
    }

    /**
     * Stops scanning for Bluetooth LE devices
     */
    private void stopScanning() {
        isScanning = false;
        bleScanner.stopScanning();
        updateUI();
    }


    @Override
    public void onBleDeviceDiscovered(BluetoothDevice device) {
        mScannedDevicesAdapter.add(new BluetoothDeviceData(device));
        mScannedDevicesAdapter.notifyDataSetChanged();
        updateUI();
    }

    @Override
    public void onBleScanError(int errorCode) {
        Toast.makeText(getBaseContext(), "BLE Scan error occurred: " + errorCode, Toast.LENGTH_LONG).show();
    }

    /**
     * Prompts the user to confirm if they want to connect to the device that was selected.
     *
     * @param deviceData The data about a Bluetooth device
     */
    private void promptConnection(final BluetoothDeviceData deviceData) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(R.string.confirm_device_selection_title)
                .setMessage(String.format(getString(R.string.confirm_device_selection_body), deviceData.getNiceName()))
                .setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        // Initiate connection
                        connectToDevice(deviceData);
                    }
                })
                .setNegativeButton(android.R.string.no, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        // Cancel connection, resume scanning (if it was scanning)
                        if (wasScanning) beginScanning();
                    }
                })
                .show();
    }

    /**
     * Connects to the Bluetooth device specified by the given bluetooth device data
     *
     * @param deviceData The data about a bluetooth device
     */
    private void connectToDevice(BluetoothDeviceData deviceData) {
        Intent transition = new Intent(getApplicationContext(), ConnectedDeviceActivity.class);

        transition.putExtra(kActivityExtraDevice, deviceData.getDevice());
        transition.putExtra(kActivityExtraDeviceName, deviceData.getNiceName());

        startActivityForResult(transition, kActivityRequestCode_ConnectedActivity);
    }

    /**
     * Updates the UI to reflect any changes made.
     */
    private void updateUI() {
        // Update scan button
        mScanButton.setText((isScanning) ? R.string.scan_scanbutton_scanning : R.string.scan_scanbutton_scan);

        // Show/Hide list depending on # of devices discovered
        if (mScannedDevicesAdapter.getCount() == 0) {
            // No devices have been discovered, hid list show label
            mNoDevicesTextView.setVisibility(View.VISIBLE);
            mScannedDevicesListView.setVisibility(View.GONE);
            mConnectDirectionsTextView.setVisibility(View.GONE);
            mDiscoveredDevicesTextView.setVisibility(View.GONE);
        } else {
            mNoDevicesTextView.setVisibility(View.GONE);
            mScannedDevicesListView.setVisibility(View.VISIBLE);
            mConnectDirectionsTextView.setVisibility(View.VISIBLE);
            mDiscoveredDevicesTextView.setVisibility(View.VISIBLE);
        }

    }


    /**
     * Called when the scan button is pressed
     *
     * @param view The view containing the button
     */
    public void onClickScan(View view) {
        if (isScanning) {
            stopScanning();
        } else {
            beginScanning();
        }
    }


    /**
     * Helper class to hold data about a bluetooth device
     */
    private class BluetoothDeviceData {
        private final String NAME_REPLACEMENT = "Unknown Device";

        private BluetoothDevice device;
        private String deviceName;

        BluetoothDeviceData(BluetoothDevice device) {
            this.device = device;
            this.deviceName = getNiceName();
        }

        String getNiceName() {
            if (device == null) return null;

            if (device.getName() == null || device.getName().equals("")) {
                return NAME_REPLACEMENT;
            } else {
                return device.getName();
            }
        }

        BluetoothDevice getDevice() {
            return device;
        }

        @Override
        public String toString() {
            return deviceName + "\n\t" + device.getAddress();
        }
    }
}
