package com.openxc.interfaces.ble;

import android.annotation.TargetApi;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;

import static com.openxc.interfaces.ble.BLEHelper.STATE_CONNECTED;
import static com.openxc.interfaces.ble.BLEHelper.STATE_DISCONNECTED;

/**
 * Created by Srilaxmi on 8/30/17.
 */
@TargetApi(18)
public class GattCallback extends BluetoothGattCallback {

    public static final String C5_OPENXC_BLE_SERVICE_UUID = "6800D38B-423D-4BDB-BA05-C9276D8453E1";
    public static final String C5_OPENXC_BLE_CHARACTERISTIC_WRITE_UUID = "6800D38B-5262-11E5-885D-FEFF819CDCE2";
    public static final String C5_OPENXC_BLE_CHARACTERISTIC_NOTIFY_UUID = "6800D38B-5262-11E5-885D-FEFF819CDCE3";
    public static final String C5_OPENXC_BLE_DESCRIPTOR_NOTIFY_UUID = "00002902-0000-1000-8000-00805f9b34fb";
    public static final String OPENXC_BLE_UUID = "00002a05-0000-1000-8000-00805f9b34fb";
    protected static final UUID CHARACTERISTIC_UPDATE_NOTIFICATION_DESCRIPTOR_UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");


    private final static String TAG = GattCallback.class.getSimpleName();
    private BluetoothGatt mBluetoothGatt;
    private BLEHelper mBLEHelper;
    private Context mContext;

    private byte[] data;
    private int i = 0;
    private byte[] old;
    public GattCallback(BLEHelper bleHelper, Context context) {

        mBLEHelper = bleHelper;
        mBluetoothGatt = bleHelper.getBluetoothGatt();
        mContext = context;
    }

    @Override
    public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
        String intentAction;
        if (newState == BluetoothProfile.STATE_CONNECTED) {

            mBLEHelper.setConnectionState(STATE_CONNECTED);
            Log.i(TAG, "Connected to GATT server.");

            //Stop scanning for BLE devices as we are connected
            mBLEHelper.scanLeDevice(false);
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

            List<BluetoothGattService> gattServices = gatt.getServices();
            for (BluetoothGattService gattService : gattServices) {
                if(C5_OPENXC_BLE_SERVICE_UUID.equalsIgnoreCase(gattService.getUuid().toString())){
                    List<BluetoothGattCharacteristic> gattCharacteristics =
                            gattService.getCharacteristics();
                    for (BluetoothGattCharacteristic gattCharacteristic :
                            gattCharacteristics) {
                        if(C5_OPENXC_BLE_CHARACTERISTIC_NOTIFY_UUID.equalsIgnoreCase(gattCharacteristic.getUuid().toString())){
                            gatt.setCharacteristicNotification(gattCharacteristic,true);
                            BluetoothGattDescriptor descriptor = gattCharacteristic.getDescriptor( UUID.fromString(C5_OPENXC_BLE_DESCRIPTOR_NOTIFY_UUID));
                            descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
                            gatt.writeDescriptor(descriptor);
                        }
                        if(C5_OPENXC_BLE_CHARACTERISTIC_WRITE_UUID.equalsIgnoreCase(gattCharacteristic.getUuid().toString())){
                            gatt.setCharacteristicNotification(gattCharacteristic,true);
                            List<BluetoothGattDescriptor> descriptors = gattCharacteristic.getDescriptors();
                            for(BluetoothGattDescriptor descriptor: descriptors){
                                descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
                                gatt.writeDescriptor(descriptor);
                            }
                        }
                    }
                }
            }
        } else {
            Log.w(TAG, "onServicesDiscovered received: " + status);
        }
    }

    @Override
    public void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
        System.out.println("ALOK!!");
    }

    @Override
    public void onCharacteristicRead(BluetoothGatt gatt,
                                     BluetoothGattCharacteristic characteristic,
                                     int status) {
        Log.i("CharacteristicRead", characteristic.toString());
        if (status == BluetoothGatt.GATT_SUCCESS) {
         /*   final byte[] data = characteristic.getValue();
            if (data != null && data.length > 0) {
                final StringBuilder stringBuilder = new StringBuilder(data.length);
                for(byte byteChar : data)
                    stringBuilder.append(String.format("%02X ", byteChar));
                System.out.println("ALOK!!"+ stringBuilder.toString());
            }*/
        }
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
        descriptor.setValue(enabled ? BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE : new byte[] { 0x00, 0x00 });
        mBluetoothGatt.writeDescriptor(descriptor);
    }

    public void readChangedCharacteristic(BluetoothGattCharacteristic characteristic) {
        data = characteristic.getValue(); // *** this is going to get overwritten by next call, so make a queue
        if (data != null && data.length > 0) {
            final StringBuilder stringBuilder = new StringBuilder(data.length);
            Log.i(TAG, "Alok's Data: " + new String(data));
        }
    }

}
