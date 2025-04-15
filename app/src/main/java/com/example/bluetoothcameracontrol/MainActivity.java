package com.example.bluetoothcameracontrol;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.view.MotionEvent;
import android.webkit.WebView;
import android.widget.EditText;
import android.widget.Toast;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.app.ActivityCompat;
import android.content.pm.PackageManager;
import androidx.appcompat.widget.SwitchCompat;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;

import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Set;
import java.util.UUID;

public class MainActivity extends AppCompatActivity {

    private BluetoothAdapter bluetoothAdapter;
    private BluetoothSocket bluetoothSocket;
    private OutputStream outputStream;
    private WebView cameraView;
    private EditText ipInput;
    private MaterialButton loadFeedBtn, connectBtBtn;
    private final UUID BT_MODULE_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");

    private boolean isBluetoothConnected = false;
    private SwitchCompat flashToggle;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
        setContentView(R.layout.activity_main);

        // Request Bluetooth permissions for Android 12+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{Manifest.permission.BLUETOOTH_CONNECT}, 1001);
                return;
            }
        }

        // Initialize views
        cameraView = findViewById(R.id.camera_view);
        // Ensure WebView touch events work properly
        cameraView.setOnTouchListener((v, event) -> {
            v.getParent().requestDisallowInterceptTouchEvent(true);
            return false;
        });

        ipInput = findViewById(R.id.ip_input);
        loadFeedBtn = findViewById(R.id.load_feed_btn);
        connectBtBtn = findViewById(R.id.connect_bt_btn);
        flashToggle = findViewById(R.id.flash_toggle);

        // Enable JavaScript on the camera view WebView
        if (cameraView != null) {
            cameraView.getSettings().setJavaScriptEnabled(true);
        }

        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (bluetoothAdapter == null) {
            Toast.makeText(this, "Bluetooth not supported", Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        // Enable Bluetooth if it is off
        if (!bluetoothAdapter.isEnabled()) {
            startActivityForResult(new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE), 1);
        }

        // Connect Button Click: Show dialog for paired Bluetooth devices
        connectBtBtn.setOnClickListener(v -> showBluetoothDevicesPopup());

        // Set Movement Buttons (using IDs in your layout)
        setMovementButton(R.id.btn_up, "R");
        setMovementButton(R.id.btn_down, "L");
        setMovementButton(R.id.btn_left, "B");
        setMovementButton(R.id.btn_right, "F");

        // Load Camera Feed Button
        loadFeedBtn.setOnClickListener(v -> {
            String ip = ipInput.getText().toString().trim();
            if (!ip.isEmpty()) {
                cameraView.loadUrl("http://" + ip);
            } else {
                Toast.makeText(this, "Enter IP address", Toast.LENGTH_SHORT).show();
            }
        });

        // Flash Toggle Switch: Send HTTP GET to ESP32 /flash/on or /flash/off
        flashToggle.setOnCheckedChangeListener((buttonView, isChecked) -> {
            String ip = ipInput.getText().toString().trim();
            if (ip.isEmpty()) {
                Toast.makeText(MainActivity.this, "Enter IP address first", Toast.LENGTH_SHORT).show();
                flashToggle.setChecked(!isChecked); // revert the toggle
                return;
            }
            String flashUrl = "http://" + ip + "/flash/" + (isChecked ? "on" : "off");
            toggleFlash(flashUrl);
        });
    }

    // Sends an HTTP GET request to the given URL in a background thread.
    private void toggleFlash(final String urlString) {
        new Thread(() -> {
            try {
                URL url = new URL(urlString);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setConnectTimeout(3000);
                conn.setReadTimeout(3000);
                int responseCode = conn.getResponseCode();
                runOnUiThread(() -> {
                    if (responseCode == 200) {
                        Toast.makeText(MainActivity.this, "Flash command sent", Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(MainActivity.this, "Failed to send flash command", Toast.LENGTH_SHORT).show();
                    }
                });
                conn.disconnect();
            } catch (Exception e) {
                e.printStackTrace();
                runOnUiThread(() ->
                        Toast.makeText(MainActivity.this, "Error: " + e.getMessage(), Toast.LENGTH_SHORT).show());
            }
        }).start();
    }

    // Displays a pop-up to select a paired Bluetooth device.
    private void showBluetoothDevicesPopup() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        Set<BluetoothDevice> pairedDevices = bluetoothAdapter.getBondedDevices();
        if (pairedDevices.isEmpty()) {
            Toast.makeText(this, "No paired Bluetooth devices found.", Toast.LENGTH_SHORT).show();
            return;
        }
        ArrayList<String> deviceNames = new ArrayList<>();
        ArrayList<String> deviceAddresses = new ArrayList<>();
        for (BluetoothDevice device : pairedDevices) {
            deviceNames.add(device.getName());
            deviceAddresses.add(device.getAddress());
        }
        String[] deviceArray = deviceNames.toArray(new String[0]);
        new AlertDialog.Builder(MainActivity.this)
                .setTitle("Select a Bluetooth Device")
                .setItems(deviceArray, (dialog, which) -> {
                    String address = deviceAddresses.get(which);
                    connectToDevice(address);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    // Connects to the selected Bluetooth device.
    private void connectToDevice(String address) {
        BluetoothDevice device = bluetoothAdapter.getRemoteDevice(address);
        try {
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                return;
            }
            bluetoothSocket = device.createRfcommSocketToServiceRecord(BT_MODULE_UUID);
            bluetoothSocket.connect();
            outputStream = bluetoothSocket.getOutputStream();
            isBluetoothConnected = true;
            Toast.makeText(this, "Connected to " + device.getName(), Toast.LENGTH_SHORT).show();
        } catch (IOException e) {
            Toast.makeText(this, "Connection failed", Toast.LENGTH_SHORT).show();
            e.printStackTrace();
        }
    }

    // Assigns the given command to a movement button.
    private void setMovementButton(int buttonId, final String command) {
        findViewById(buttonId).setOnTouchListener((v, event) -> {
            if (!isBluetoothConnected) {
                Toast.makeText(MainActivity.this, "Not connected to Bluetooth", Toast.LENGTH_SHORT).show();
                return true;
            }
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    sendCommand(command);
                    break;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    sendCommand("S");
                    break;
            }
            return true;
        });
    }

    // Sends a command over Bluetooth.
    private void sendCommand(String cmd) {
        if (outputStream != null) {
            try {
                outputStream.write(cmd.getBytes());
            } catch (IOException e) {
                Toast.makeText(this, "Error sending command", Toast.LENGTH_SHORT).show();
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 1001) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                recreate();
            } else {
                Toast.makeText(this, "Bluetooth permission denied", Toast.LENGTH_SHORT).show();
            }
        }
    }
}
