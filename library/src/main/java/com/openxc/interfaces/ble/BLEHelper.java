package com.openxc.interfaces.ble;

import android.annotation.TargetApi;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.openxc.interfaces.bluetooth.BluetoothVehicleInterface;
import com.openxc.util.SupportSettingsUtils;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.nio.BufferOverflowException;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Created by AKUMA128 on 12/13/2017.
 */

public class BLEHelper {
    private final static String TAG = BLEHelper.class.getSimpleName();
    public static final int MAX_WRITE_BUFFER_CAPACITY = 1024;
    private static final String KNOWN_BLE_DEVICE_PREFERENCES = "known_ble_devices";
    public static final String LAST_CONNECTED_BLE_DEVICE_PREF_KEY = "last_connected_ble_device";
    public static final String KNOWN_BLUETOOTH_DEVICE_PREF_KEY = "known_bluetooth_devices";

    private Context mContext;
    private Handler mHandler;
    private BluetoothDevice mBluetoothDevice;
    private BluetoothGatt mBluetoothGatt;
    private BluetoothGattCallback mGattCallback;

    private int mConnectionState = STATE_DISCONNECTED;

    private BluetoothAdapter mBluetoothAdapter;
    public static final int STATE_DISCONNECTED = 0;
    public static final int STATE_CONNECTING = 1;
    public static final int STATE_CONNECTED = 2;
    private byte[] writeArray = new byte[MAX_WRITE_BUFFER_CAPACITY];
    private static int writeCounter = 0;
    int queueEnd = 0;

    public BLEHelper(Context context) throws BLEException {

        mContext = context;
        if (Looper.myLooper() == null) {
            Looper.prepare();

        }
        mHandler = new Handler();

        if (getDefaultAdapter() == null) {
            String message = "This device most likely does not have a Bluetooth adapter";
            Log.w(TAG, message);
            throw new BLEException(message);

        } else {
            Log.d(TAG, "Initializing Bluetooth(BLE) device manager");

        }
    }

    @TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR2)
    public int getDeviceType(String address) {
        return getDefaultAdapter().getRemoteDevice(address).getType();
    }

    public BluetoothAdapter getDefaultAdapter() {

        if (mBluetoothAdapter == null) {

            if (!mContext.getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
                return null;
            }
            // work around an Android bug, requires that this is called before
            // getting the default adapter
            if (Looper.myLooper() == null) {
                Looper.prepare();
            }
            mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        }

        return mBluetoothAdapter;
    }

    public boolean connect(String address) throws BLEException {
         return connect(getDefaultAdapter().getRemoteDevice(address));
    }

    /**
     * Connects to the GATT server hosted on the Bluetooth LE device.
     *
     * @param device The destination device.
     * @return Return true if the connection is initiated successfully. The connection result
     * is reported asynchronously through the
     * {@code BluetoothGattCallback#onConnectionStateChange(android.bluetooth.BluetoothGatt, int, int)}
     * callback.
     */
    @TargetApi(18)
    public boolean connect(BluetoothDevice device) throws BLEException {
        Log.d(TAG, "Connect called!!!");
        if (Build.VERSION.SDK_INT < 18) {
            Log.i(TAG, "BLE not supported on API Version < 18");
            throw new BLEException("BLE not supported on API Version < 18");
        }

        if (device == null && mBluetoothDevice != null) {
            device = mBluetoothDevice;
        }

        if (mBluetoothAdapter == null) {
            Log.w(TAG, "BluetoothAdapter not initialized");
            throw new BLEException("BluetoothAdapter not initialized");
        }

        // Previously connected device.  Try to reconnect.
        if (mBluetoothDevice != null && device.equals(mBluetoothDevice)
                && mBluetoothGatt != null) {
            Log.d(TAG, "Trying to use an existing mBluetoothGatt for connection.");
            if (mBluetoothGatt.connect()) { //*** This will autoconnect?
                mConnectionState = STATE_CONNECTING;
                return true;
            } else {
                return false;
            }
        }

        if (device == null) {
            Log.w(TAG, "Device not found.  Unable to connect.");
            throw new BLEException("Device not found.  Unable to connect.");
        }

        if (mGattCallback == null) {
            Log.i(TAG, "GattCallback not instantiated");
            throw new BLEException("GattCallback not instantiated");
        }
        mBluetoothGatt = device.connectGatt(mContext, true, mGattCallback);
        Log.d(TAG, "Trying to create a new connection.");
        mBluetoothDevice = device;//*** should this be set after connected?
        return true;
    }

    /**
     * Disconnects an existing connection or cancel a pending connection. The disconnection result
     * is reported asynchronously through the
     * {@code BluetoothGattCallback#onConnectionStateChange(android.bluetooth.BluetoothGatt, int, int)}
     * callback.
     */
    @TargetApi(18)
    public void disconnect() {

        if (Build.VERSION.SDK_INT < 18) {
            Log.i(TAG, "BLE not supported on API Version < 18");
            return;
        }
        if (mBluetoothAdapter == null || mBluetoothGatt == null) {
            Log.w(TAG, "BluetoothAdapter not initialized");
            return;
        }
        mBluetoothGatt.disconnect();
        mConnectionState = STATE_DISCONNECTED;

    }

    /**
     * After using a given BLE device, the app must call this method to ensure resources are
     * released properly.
     */
    @TargetApi(18)
    public void close() {
        if (Build.VERSION.SDK_INT < 18) {
            Log.i(TAG, "BLE not supported on API Version < 18");
            return;
        }
        if (mBluetoothGatt == null) {
            return;
        }
        mBluetoothGatt.close();
        mBluetoothGatt = null;
    }

    public BluetoothGatt getBluetoothGatt() {
        return mBluetoothGatt;
    }

    public void setGattCallback(BluetoothGattCallback bluetoothGattCallback) {
        mGattCallback = bluetoothGattCallback;
    }

    public void setConnectionState(int mConnectionState) {
        this.mConnectionState = mConnectionState;
    }

    public int getConnectionState() {
        return mConnectionState;
    }

    @TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR2)
    public boolean writeCharacteristic(byte[] bytes) {
        try {
            for (int i = 0; i < bytes.length; i++) {
                writeArray[queueEnd++] = bytes[i];
            }
        } catch (BufferOverflowException e) {
            //TODO : How to handle???
            Log.d(TAG, "Buffer overflowing!!!!");
            return false;
        }

        BLESendData();
        return true;
    }

    //TODO : the packet that you are sending must be in UTF-8
    @TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR2)
    private void BLESendData() {
        if (queueEnd <= 0) {
            return;
        }
        if (mBluetoothGatt != null) {
            BluetoothGattService openXCService = mBluetoothGatt.getService(UUID.fromString(GattCallback.C5_OPENXC_BLE_SERVICE_UUID));
            if (openXCService != null) {
                BluetoothGattCharacteristic characteristic = openXCService.getCharacteristic(UUID.fromString(GattCallback.C5_OPENXC_BLE_CHARACTERISTIC_WRITE_UUID));
                if (characteristic != null) {

                    while (queueEnd !=0) {
                        byte[] sendingPacket;
                        if (queueEnd>=20) {
                            sendingPacket = new byte[20];
                            System.arraycopy(writeArray, 0, sendingPacket, 0, 20);
                            System.arraycopy(writeArray,20,writeArray,0,queueEnd-20);
                            queueEnd = queueEnd -20;

                        } else {
                            //TODO : Remove this crude code!!!
                            sendingPacket = new byte[queueEnd];
                            System.arraycopy(writeArray, 0, sendingPacket, 0, queueEnd);
                            queueEnd = 0;
                        }
                        String sendingPacketString = new String (sendingPacket);
                        byte[] sendingPacketUtf8 = new byte[0];
                        try {
                            sendingPacketUtf8 = sendingPacketString.getBytes("UTF-8");
                        } catch (UnsupportedEncodingException e) {
                            e.printStackTrace();
                            Log.d(TAG,"UnsupportedEncoding");

                        }
                        characteristic.setValue(sendingPacketUtf8);
                        try {
                            Thread.sleep(50);
                        } catch (InterruptedException e) {
                            Log.d(TAG,"Interrupted");
                            e.printStackTrace();
                        }
                        boolean status = mBluetoothGatt.writeCharacteristic(characteristic);
                        if(status) {
                            writeCounter++;
                        }
                    }
                } else {
                    Log.d(TAG, "characteristic is null");
                }
            } else {
                Log.d(TAG, "OpenXC Service not found!");
            }
        } else {
            Log.d(TAG, "Gatt not found!");
        }
    }

    public static int getWriteCounter() {
        return writeCounter;
    }

    public static void setWriteCounter(int writeCounter) {
        BLEHelper.writeCounter = writeCounter;
    }

    public void storeLastConnectedDevice(BluetoothDevice device) {
        SharedPreferences.Editor editor =
                mContext.getSharedPreferences(
                        KNOWN_BLE_DEVICE_PREFERENCES,
                        Context.MODE_MULTI_PROCESS).edit();
        editor.putString(LAST_CONNECTED_BLE_DEVICE_PREF_KEY,
                device.getAddress());
        editor.apply();
        Log.d(TAG, "Stored last connected device: " + device.getAddress());
    }

    public BluetoothDevice getLastConnectedDevice() {
        SharedPreferences preferences =
                mContext.getSharedPreferences(KNOWN_BLE_DEVICE_PREFERENCES,
                        Context.MODE_MULTI_PROCESS);
        String lastConnectedDeviceAddress = preferences.getString(
                LAST_CONNECTED_BLE_DEVICE_PREF_KEY, null);
        BluetoothDevice lastConnectedDevice = null;
        if (lastConnectedDeviceAddress != null) {
            lastConnectedDevice = getDefaultAdapter().getRemoteDevice(lastConnectedDeviceAddress);
        }
        return lastConnectedDevice;
    }

    @TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR2)
    public Set<BluetoothDevice> getCandidateDevices() {
        Set<BluetoothDevice> candidates = new HashSet<>();

        for (BluetoothDevice device : getPairedDevices()) {
            if (device.getName().startsWith(
                    BluetoothVehicleInterface.DEVICE_NAME_PREFIX) && device.getType() == BluetoothDevice.DEVICE_TYPE_LE) {
                candidates.add(device);
            }
        }

        SharedPreferences preferences =
                mContext.getSharedPreferences(KNOWN_BLE_DEVICE_PREFERENCES,
                        Context.MODE_MULTI_PROCESS);
        Set<String> detectedDevices = SupportSettingsUtils.getStringSet(
                preferences, KNOWN_BLUETOOTH_DEVICE_PREF_KEY,
                new HashSet<String>());
        for (String address : detectedDevices) {
            if (BluetoothAdapter.checkBluetoothAddress(address)) {
                candidates.add(getDefaultAdapter().getRemoteDevice(address));
            }
        }

        for (BluetoothDevice candidate : candidates) {
            Log.d(TAG, "Found previously discovered or paired OpenXC BT VI "
                    + candidate.getAddress());
        }
        return candidates;
    }

    public Set<BluetoothDevice> getPairedDevices() {
        Set<BluetoothDevice> devices = new HashSet<>();
        if (getDefaultAdapter() != null && getDefaultAdapter().isEnabled()) {
            devices = getDefaultAdapter().getBondedDevices();
            Log.d(TAG, "Bonded devices : " + devices);
        }
        return devices;
    }

    public void stop() {
        if(getDefaultAdapter() != null) {
            getDefaultAdapter().cancelDiscovery();
        }
    }

    public boolean isConnected(){
        return mConnectionState == STATE_CONNECTED;
    }
}
