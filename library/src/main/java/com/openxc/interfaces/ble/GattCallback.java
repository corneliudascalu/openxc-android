package com.openxc.interfaces.ble;

import android.annotation.TargetApi;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.os.Build;
import android.util.Log;

import java.util.UUID;

import static com.openxc.interfaces.ble.BLEHelper.STATE_CONNECTED;
import static com.openxc.interfaces.ble.BLEHelper.STATE_DISCONNECTED;


@TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR2)
public class GattCallback extends BluetoothGattCallback {
    private final static String TAG = GattCallback.class.getSimpleName();

    private BluetoothGatt mBluetoothGatt;
    private BLEHelper mBLEHelper;
    private Context mContext;

    private BLEInputStream bleInputStream;

    public static final String C5_OPENXC_BLE_SERVICE_UUID = "6800D38B-423D-4BDB-BA05-C9276D8453E1";
    public static final String C5_OPENXC_BLE_CHARACTERISTIC_NOTIFY_UUID = "6800D38B-5262-11E5-885D-FEFF819CDCE3";
    public static final String C5_OPENXC_BLE_DESCRIPTOR_NOTIFY_UUID = "00002902-0000-1000-8000-00805f9b34fb";
    public static final String C5_OPENXC_BLE_CHARACTERISTIC_WRITE_UUID = "6800D38B-5262-11E5-885D-FEFF819CDCE2";
    protected static final UUID CHARACTERISTIC_UPDATE_NOTIFICATION_DESCRIPTOR_UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");

    public GattCallback(BLEHelper bleHelper, Context context) {

        mBLEHelper = bleHelper;
        mBluetoothGatt = bleHelper.getBluetoothGatt();
        mContext = context;
    }

    @Override
    public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {

        if (newState == BluetoothProfile.STATE_CONNECTED) {

            mBLEHelper.setConnectionState(STATE_CONNECTED);
            Log.i(TAG, "Connected to GATT server.");

            //Stop scanning for BLE devices as we are connected
            //mBLEHelper.scanLeDevice(false);
            mBluetoothGatt = mBLEHelper.getBluetoothGatt();
            // Attempts to discover services after successful connection.
            Log.i(TAG, "Attempting to start service discovery:" +
                    mBluetoothGatt.discoverServices());

        } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
            mBLEHelper.setConnectionState(STATE_DISCONNECTED);
            Log.i(TAG, "Disconnected from GATT server.");
        }
    }


    @Override
    public void onServicesDiscovered(BluetoothGatt gatt, int status) {
        if (status == BluetoothGatt.GATT_SUCCESS) {

            BluetoothGattService gattService = gatt.getService(UUID.fromString(C5_OPENXC_BLE_SERVICE_UUID));
            BluetoothGattCharacteristic gattCharacteristic = gattService.getCharacteristic(UUID.fromString(C5_OPENXC_BLE_CHARACTERISTIC_NOTIFY_UUID));
            gatt.setCharacteristicNotification(gattCharacteristic, true);
            BluetoothGattDescriptor descriptor = gattCharacteristic.getDescriptor(UUID.fromString(C5_OPENXC_BLE_DESCRIPTOR_NOTIFY_UUID));
            descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
            gatt.writeDescriptor(descriptor);
        }
    }

    @Override
    public void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
       if(status == BluetoothGatt.GATT_SUCCESS){
           int counter = BLEHelper.getWriteCounter();
           Log.d(TAG,"Write Counter ::" + counter);
           BLEHelper.setWriteCounter(counter-1);
           Log.d(TAG,"Write Success");
       } else {
           Log.d(TAG,"Write failure");
       }
    }

    @Override
    public void onCharacteristicRead(BluetoothGatt gatt,
                                     BluetoothGattCharacteristic characteristic,
                                     int status) {
        Log.i("CharacteristicRead", characteristic.toString());

    }

    @Override
    public void onCharacteristicChanged(BluetoothGatt gatt,
                                        BluetoothGattCharacteristic characteristic) {
        readChangedCharacteristic(characteristic);
    }

    /**
     * Enables or disables notification on a give characteristic.
     *
     * @param characteristic Characteristic to act on.
     * @param enabled        If true, enable notification.  False otherwise.
     */
    public void setCharacteristicNotification(BluetoothGattCharacteristic characteristic,
                                              boolean enabled) {
        System.out.println(characteristic.getUuid());
        if (mBluetoothGatt == null) {
            Log.w(TAG, "BluetoothGatt not initialized");
            return;
        }
        mBluetoothGatt.setCharacteristicNotification(characteristic, enabled);

        BluetoothGattDescriptor descriptor = characteristic.getDescriptor(CHARACTERISTIC_UPDATE_NOTIFICATION_DESCRIPTOR_UUID);
        descriptor.setValue(enabled ? BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE : new byte[]{0x00, 0x00});
        mBluetoothGatt.writeDescriptor(descriptor);
    }

    public void readChangedCharacteristic(BluetoothGattCharacteristic characteristic) {
        byte[] data;
        data = characteristic.getValue(); // *** this is going to get overwritten by next call, so make a queue
        if (data != null && data.length > 0) {
            final StringBuilder stringBuilder = new StringBuilder(data.length);
            for (byte byteChar : data)
                stringBuilder.append(String.format("%02X ", byteChar));
            BLEInputStream.getInstance().putDataInBuffer(data);
            // parseNextMessage();
        }
    }
}

