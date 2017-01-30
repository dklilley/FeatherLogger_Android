package org.lilleypad.featherloggerapp;

import android.Manifest;
import android.bluetooth.BluetoothDevice;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Environment;
import android.support.annotation.NonNull;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import org.lilleypad.featherloggerapp.ble.BleUartGattManager;
import org.lilleypad.featherloggerapp.util.FeatherLoggerCmd;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Arrays;

public class ConnectedDeviceActivity extends AppCompatActivity implements BleUartGattManager.BleUartListener {

    // Constants
    private final static int kPermissionRequestExternalStorage = 0;

    // Log
    private final static String TAG = ConnectedDeviceActivity.class.getSimpleName();

    // UI
    private TextView deviceNameTV;
    private TextView deviceDataTV;
    private TextView startedLoggingTV;
    private TextView startedLoggingValTV;
    private TextView batteryTV;
    private TextView batteryValTV;
    private TextView maxStorageTV;
    private TextView maxStorageValTV;
    private TextView storageLeftTV;
    private TextView storageLeftValTV;
    private Button downloadBtn;
    private Button deleteBtn;
    private TextView deleteHintTV;

    private AlertDialog mDownloadingDialog;

    // Data
    private BluetoothDevice device;
    private BleUartGattManager connectionManager;

    private String bleDataReceived = ""; // Holds data received from Bluetooth

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_connected_device);

        // Get UI Elements
        deviceNameTV = (TextView) findViewById(R.id.deviceNameTextView);
        deviceDataTV = (TextView) findViewById(R.id.deviceDataTextView);
        startedLoggingTV = (TextView) findViewById(R.id.startedLoggingTextView);
        startedLoggingValTV = (TextView) findViewById(R.id.startedLoggingValTextView);
        batteryTV = (TextView) findViewById(R.id.batteryVoltageTextView);
        batteryValTV = (TextView) findViewById(R.id.batteryVoltageValTextView);
        maxStorageTV = (TextView) findViewById(R.id.maximumStorageTextView);
        maxStorageValTV = (TextView) findViewById(R.id.maximumStorageValTextView);
        storageLeftTV = (TextView) findViewById(R.id.storageRemainingTextView);
        storageLeftValTV = (TextView) findViewById(R.id.storageRemainingValTextView);
        downloadBtn = (Button) findViewById(R.id.downloadDataButton);
        deleteBtn = (Button) findViewById(R.id.deleteDataButton);
        deleteHintTV = (TextView) findViewById(R.id.deleteWarningTextView);

        downloadBtn.setEnabled(false);
        deleteBtn.setEnabled(false);

        // TODO: For when deleting files is implemented
        deleteBtn.setVisibility(View.GONE);
        deleteHintTV.setVisibility(View.GONE);

        // Get transferred data
        Intent transition = getIntent();
        device = transition.getParcelableExtra(DeviceListActivity.kActivityExtraDevice);
        String deviceName = transition.getStringExtra(DeviceListActivity.kActivityExtraDeviceName);
        deviceNameTV.setText(deviceName);

        // Set up connection to device
        connectionManager = new BleUartGattManager(device, getApplicationContext());
        connectionManager.setListener(this);
    }

    @Override
    public void onResume() {
        super.onResume();

    }

    @Override
    public void onPause() {
        super.onPause();

    }

    @Override
    public void onDestroy() {
        teardown();

        super.onDestroy();
    }

    @Override
    public void onStop() {

        super.onStop();
    }

    /**
     * To be called when finished with this view
     */
    private void teardown() {
        connectionManager.disconnect();
        connectionManager.removeListener();
    }


    /* -------------------- BleUartListener Callbacks -------------------- */

    @Override
    public void onDataReceived(String string) {
        // Concat the data received onto the data previously received
        bleDataReceived += string;
        parseBleInput(); // Check if data received is a complete command
    }

    @Override
    public void onUartReady() {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                downloadBtn.setEnabled(true);
                deleteBtn.setEnabled(true);
            }
        });
    }

    @Override
    public void onConnectionLost() {
        final AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Connection to Device Lost")
                .setMessage("The connection to the connected BLE device has been lost.")
                .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {

                        onBackPressed(); // Return to device list view
                    }
                });

        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (mDownloadingDialog != null) {
                    mDownloadingDialog.cancel();
                    mDownloadingDialog = null;
                }

                AlertDialog dialog = builder.create();
                dialog.setCanceledOnTouchOutside(false);
                dialog.show();
            }
        });
    }

    /**
     * Parses the input currently received from the connected BLE device, and takes action
     * depending on the data received so far.
     */
    private void parseBleInput() {
        if (mDownloadingDialog == null && bleDataReceived.startsWith(FeatherLoggerCmd.LOGGER_FILE_DOWNLOAD)) {
            // Display Downloading Message
            final AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setMessage("Downloading File...");

            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    mDownloadingDialog = builder.create();
                    mDownloadingDialog.setCanceledOnTouchOutside(false);
                    mDownloadingDialog.setOnKeyListener(new DialogInterface.OnKeyListener() {
                        @Override
                        public boolean onKey(DialogInterface dialogInterface, int i, KeyEvent keyEvent) {
                            if (keyEvent.getKeyCode() == KeyEvent.KEYCODE_BACK) {
                                mDownloadingDialog.cancel();
                                mDownloadingDialog = null;
                            }
                            return true;
                        }
                    });

                    mDownloadingDialog.show();
                }
            });
        }

        if (bleDataReceived.contains(FeatherLoggerCmd.END)) { // If a full command has been received
            String cmd = bleDataReceived.substring(0, bleDataReceived.indexOf(FeatherLoggerCmd.END) + FeatherLoggerCmd.END.length()); // Get command from data received (data could contain more than one command)
            bleDataReceived = bleDataReceived.substring(cmd.length());

            String[] broken = cmd.split(FeatherLoggerCmd.DELIM);

            Log.d(TAG, "Command received: " + broken[0]);

            // Call appropriate command handler
            if (broken[0].equals(FeatherLoggerCmd.LOGGER_INFO_CMD)) {
                handleFeatherLoggerInfo(broken);
            } else if (broken[0].equals(FeatherLoggerCmd.LOGGER_FILES_CMD)) {
                handleFeatherLoggerFileList(broken);
            } else if (broken[0].equals(FeatherLoggerCmd.LOGGER_FILE_DOWNLOAD)) {
                handleFeatherLoggerFileDownload(broken);
            }
        }
    }

    /**
     * Handles an INFO command from the FeatherLogger. This command contains info about the state
     * of the data logger, which are displayed in the app.
     *
     * @param data The data sent from the data logger, split into its separate parts
     */
    private void handleFeatherLoggerInfo(final String[] data) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                startedLoggingValTV.setText(data[1]);

                batteryValTV.setText(String.format(getString(R.string.data_battery_value), data[2]));
                storageLeftValTV.setText(String.format(getString(R.string.data_storage_left_value), data[3]));
                maxStorageValTV.setText(String.format(getString(R.string.data_storage_max_value), data[4]));
            }
        });
    }

    /**
     * Handles a FILES command from the FeatherLogger. This command sends a list of the files stored
     * on the data logger to the app, allowing the user to choose between the files.
     *
     * @param data The data sent from the data logger, split into its separate parts
     */
    private void handleFeatherLoggerFileList(final String[] data) {
        final String[] files = Arrays.copyOfRange(data, 1, data.length - 1);
        final AlertDialog.Builder builder = new AlertDialog.Builder(this);

        builder.setTitle("Select File to Download:");

        final String[] fileToDownload = {""};

        builder.setSingleChoiceItems(files, -1, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialogInterface, int i) {
                fileToDownload[0] = files[i];
            }
        });

        builder.setPositiveButton("Download", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialogInterface, int i) {
                if (!fileToDownload[0].equals("")) {
                    // Selected a file
                    connectionManager.sendData("REQ+FILE&" + fileToDownload[0]);
                }
            }
        });
        builder.setNegativeButton("Cancel", null);

        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                builder.show();
            }
        });
    }

    /**
     * Handles a FILEDL command from the FeatherLogger. This command sends the contents of
     * a file from the FeatherLogger to the app, which is then saved into a file locally.
     *
     * @param data The data sent from the data logger, split into its separate parts
     */
    private void handleFeatherLoggerFileDownload(String[] data) {
        if (mDownloadingDialog != null) {
            mDownloadingDialog.cancel();
            mDownloadingDialog = null;
        }

        String fileName = data[1].toLowerCase().trim();
        String body = data[2];

        Log.d(TAG, "File received: " + fileName);

        if (isExternalStorageWritable()) {
            final File directory = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS), "FeatherLogger");
            //boolean madeDir = directory.mkdirs();
            //Log.d(TAG, "Made FeatherLogger Directory: " + madeDir);

            File file = new File(directory, fileName);
            Log.d(TAG, "Save File: " + file);

            try {
                OutputStream os = new FileOutputStream(file);
                os.write(body.getBytes());
                os.close();

                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        Toast.makeText(getApplicationContext(), "File saved to Documents/FeatherLogger", Toast.LENGTH_LONG)
                                .show();
                    }
                });
            } catch (IOException e) {
                // Unable to create file, likely because external storage is not currently mounted
                final AlertDialog.Builder builder = new AlertDialog.Builder(this);
                builder.setTitle("Error Saving File")
                        .setMessage("An error was encountered while trying to save file.")
                        .setPositiveButton(android.R.string.ok, null);
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        builder.show();
                    }
                });

                Log.w("ExternalStorage", "Error writing " + file, e);
            }
        }
    }

    /* -------------------- UI Actions -------------------- */

    /**
     * Called when the user clicks the download button
     *
     * @param view The view containing the button
     */
    public void onClickDownload(View view) {
        if (this.checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED) {
            // Storage permissions are enabled
            connectionManager.sendData("REQ+DOWNLOAD");
        } else {
            // Need permissions for storage
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setTitle(R.string.request_storage_dialog_title)
                    .setMessage(R.string.request_storage_dialog_body)
                    .setPositiveButton(android.R.string.ok, null)
                    .setOnDismissListener(new DialogInterface.OnDismissListener() {
                        @Override
                        public void onDismiss(DialogInterface dialogInterface) {
                            requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, kPermissionRequestExternalStorage);
                        }
                    })
                    .show();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == kPermissionRequestExternalStorage) {
            if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Log.d(TAG, "Write External Storage permission granted.");

                // Send command the user was trying to send before permission request was prompted
                connectionManager.sendData("REQ+DOWNLOAD");
            } else {
                AlertDialog.Builder builder = new AlertDialog.Builder(this);
                builder.setTitle(R.string.denied_storage_dialog_title)
                        .setMessage(R.string.denied_storage_dialog_body)
                        .setPositiveButton(android.R.string.ok, null)
                        .show();
            }
        }
    }

    /**
     * Called when the user clicks the delete button - NOTE: Not currently implemented
     *
     * @param view The view containing the button
     */
    public void onClickDelete(View view) {
    }

    /* -------------------- Helper Methods -------------------- */

    /**
     * Checks whether external storage is writable
     *
     * @return true if the app can write to external storage, false otherwise
     */
    public boolean isExternalStorageWritable() {
        String state = Environment.getExternalStorageState();
        return Environment.MEDIA_MOUNTED.equals(state);
    }
}
