package com.openxc.interfaces.ble;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGattCallback;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;

import com.google.common.base.MoreObjects;
import com.openxc.interfaces.bluetooth.BluetoothException;
import com.openxc.interfaces.bluetooth.DeviceManager;
import com.openxc.sources.BytestreamDataSource;
import com.openxc.interfaces.VehicleInterface;
import com.openxc.sources.DataSourceException;
import com.openxc.sources.DataSourceResourceException;
import com.openxc.sources.SourceCallback;
import com.openxcplatform.R;

import java.io.IOException;
import java.util.ArrayList;
import java.util.concurrent.TimeUnit;

/**
 * Created by Srilaxmi on 9/1/17.
 */

public class BLEVehicleInterface extends BytestreamDataSource implements VehicleInterface {

    private BLEHelper mBLEHelper;
    private BluetoothGattCallback mGattCallback;
    private boolean mPerformAutomaticScan = true;

    private String mExplicitAddress;

    private String mDeviceAddress;
    public static final String DEVICE_NAME_PREFIX = "OpenXC";

    private final static String TAG = BLEVehicleInterface.class.getSimpleName();

    public BLEVehicleInterface(SourceCallback callback, Context context,
                               String address) throws DataSourceException {
        super(callback, context);

        LocalBroadcastManager.getInstance(context).registerReceiver(mBroadcastReceiver,
                new IntentFilter("ble-scan-complete"));

        try {
            mBLEHelper = new BLEHelper(context);
            mGattCallback = new GattCallback(mBLEHelper, context);
            mBLEHelper.setGattCallback(mGattCallback);
        } catch(BLEException e) {
            throw new DataSourceException(
                    "Unable to connect to BLE device", e);
        }
        SharedPreferences preferences =
                PreferenceManager.getDefaultSharedPreferences(context);
        /*mUsePolling = preferences.getBoolean(
                context.getString(R.string.bluetooth_polling_key), true);
        Log.d(TAG, "Bluetooth(BLE) device polling is " + (mUsePolling ? "enabled" : "disabled"));
*/
        setAddress(address);
        start();
    }

    public BLEVehicleInterface(Context context, String address)
            throws DataSourceException {
        this(null, context, address);
    }

  /*  public void setPollingStatus(boolean enabled) {
        mUsePolling = enabled;
    }
*/
    @Override
    public boolean isConnected() {
        boolean connected = false;
        connected = super.isConnected(); /*** ?? **/
        if (mBLEHelper.getConnectionState() == BLEHelper.STATE_CONNECTED) {
            connected = true;
        } else {
            connected = false;
        }
        return connected;
    }

    public boolean isConnecting() {
        boolean connecting = false;
        if (mBLEHelper.getConnectionState() == BLEHelper.STATE_CONNECTING)
            connecting = true;
        else
            connecting = false;

        return connecting;
    }

    @Override
    public boolean setResource(String otherAddress) throws DataSourceException {
        boolean reconnect = false;
        if (otherAddress == null || !sameResource(otherAddress, mDeviceAddress)) {
            reconnect = true;
        }

        setAddress(otherAddress);

        if (reconnect) setFastPolling(true);

        return reconnect;
    }


    private static boolean sameResource(String address, String otherAddress) {
        return otherAddress != null && otherAddress.equals(address);
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
                .add("DeviceAddress", mDeviceAddress)
                .add("explicitDeviceAddress", mExplicitAddress)
                .toString();
    }

    @Override
    protected int read(byte[] bytes) throws IOException {
        mConnectionLock.readLock().lock();
        int bytesRead = -1;
        try {
          /*  if(isConnected() && mInStream != null) {
                bytesRead = Ga.read(bytes, 0, bytes.length);
            }*/
        } finally {
            mConnectionLock.readLock().unlock();
        }
        return bytesRead;
    }

    @Override
    protected boolean write(byte[] bytes) {
        return false;
    }

    @Override
    protected void disconnect() {

    }

    @Override
    public synchronized void stop() { /** why is it synchronized? **/
        if(isRunning()) { //*** do we need this check?
            try {
                getContext().unregisterReceiver(mBroadcastReceiver);
            } catch(IllegalArgumentException e) {
                Log.w(TAG, "Broadcast receiver not registered but we expected it to be");
            }
            mBLEHelper.stop();
            super.stop();
        }
    }

    @Override
    protected void connect(){
        if(!isRunning()||isConnected()) {
            return;
        }

        BluetoothDevice lastConnectedDevice =
                mBLEHelper.getLastConnectedDevice();
        if(mExplicitAddress != null || !mPerformAutomaticScan) {
            if (mDeviceAddress == null && lastConnectedDevice != null) {
                mDeviceAddress = lastConnectedDevice.getAddress();
            }
            if (mDeviceAddress != null) {
                Log.i(TAG, "Connecting to Bluetooth device " + mDeviceAddress);
                try {
                    if (!isConnected()) {
                        mBLEHelper.connect(mDeviceAddress);
                        mConnectionLock.writeLock().lock();
                        connected();
                    }
                } catch (BLEException e) {
                    Log.w(TAG, "Unable to connect to device " + mDeviceAddress, e);
                }
            } else {
                Log.d(TAG, "No detected or stored Bluetooth(BLE) device MAC, not attempting connection");
            }
        } else {
            mPerformAutomaticScan = false;
            Log.v(TAG, "Attempting automatic detection of Bluetooth VI");

            ArrayList<BluetoothDevice> candidateDevices =
                    new ArrayList<>(
                            mBLEHelper.getCandidateDevices());
            Log.d(TAG,"Candidate Devices are : " + candidateDevices.toString());

            if(lastConnectedDevice != null) {
                Log.v(TAG, "First trying last connected BT VI: " +
                        lastConnectedDevice);
                candidateDevices.add(0, lastConnectedDevice);
            }

            for(BluetoothDevice device : candidateDevices) {
                try {
                    if(!isConnected()) {
                        Log.i(TAG, "Attempting connection to auto-detected " +
                                "VI " + device);
                        mBLEHelper.connect(device.getAddress());
                        mConnectionLock.writeLock().lock();
                        connected();
                        break;
                    }
                } catch(BLEException e) {
                    Log.w(TAG, "Unable to connect to auto-detected device " +
                            device, e);
                }
            }

            if(lastConnectedDevice == null && candidateDevices.size() > 0) {
                Log.i(TAG, "No BLE VI ever connected, and none of " +
                        "discovered devices could connect - storing " +
                        candidateDevices.get(0).getAddress() +
                        " as the next one to try");
                mBLEHelper.storeLastConnectedDevice(
                        candidateDevices.get(0));
            }
        }
    }

    private void setAddress(String address) throws DataSourceResourceException {
        if(address != null && !BluetoothAdapter.checkBluetoothAddress(address)) {
            throw new DataSourceResourceException("\"" + address +
                    "\" is not a valid MAC address");
        }
        mDeviceAddress = address;
    }

    private BroadcastReceiver mBroadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            Log.d(TAG, "Received BLE scan complete message");
            if (!isConnected() && !isConnecting()) {
                setFastPolling(true);
            }
        }
    };
}
