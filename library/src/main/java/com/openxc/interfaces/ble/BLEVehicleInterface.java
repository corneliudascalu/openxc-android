package com.openxc.interfaces.ble;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGattCallback;
import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.util.Log;

import com.google.common.base.MoreObjects;
import com.openxc.interfaces.VehicleInterface;
import com.openxc.sources.BytestreamDataSource;
import com.openxc.sources.DataSourceException;
import com.openxc.sources.DataSourceResourceException;
import com.openxc.sources.SourceCallback;
import com.openxcplatform.R;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.util.ArrayList;

/**
 * Created by AKUMA128 on 12/25/2017.
 */

public class BLEVehicleInterface extends BytestreamDataSource implements VehicleInterface {
    public static final String TAG = BLEVehicleInterface.class.getSimpleName();
    private BLEHelper mBleHelper;
    private BluetoothGattCallback mGattCallback;

    private String mConnectedAddress;
    private String mExplicitAddress;
    private BufferedInputStream mInStream;
    private BLEInputStream mBleInputStream;
    private boolean mUsePolling = false;
    private boolean mIsConnected = false;

    public BLEVehicleInterface(SourceCallback callback, Context context, String address) throws DataSourceResourceException {
        super(callback, context);
        try {
            mBleHelper = new BLEHelper(context);
        } catch (BLEException e) {
            e.printStackTrace();
        }
        mGattCallback = new GattCallback(mBleHelper, context);
        mBleInputStream = BLEInputStream.getInstance();
        mBleHelper.setGattCallback(mGattCallback);
        SharedPreferences preferences =
                PreferenceManager.getDefaultSharedPreferences(context);
        mUsePolling = preferences.getBoolean(
                context.getString(R.string.bluetooth_polling_key), true);

        setAddress(address);
        start();
    }

    public BLEVehicleInterface(Context context, String address)
            throws DataSourceException {
        this(null, context, address);
    }

    /**
     * Control whether periodic polling is used to detect a Bluetooth VI.
     * <p>
     * This class opens a Bluetooth socket and will accept incoming connections
     * from a VI that can act as the Bluetooth master. For VIs that are only
     * able to act as slave, we have to poll for a connection occasionally to
     * see if it's within range.
     */
    public void setPollingStatus(boolean enabled) {
        mUsePolling = enabled;
    }

    @Override
    public boolean setResource(String otherAddress) throws DataSourceException {
        Log.d(TAG, "Calling Set Resource");

        boolean reconnect = false;
        if (isConnected()) {
            if (otherAddress == null) {
                // switch to automatic but don't break the existing connection
                reconnect = false;
            } else if (!sameResource(mConnectedAddress, otherAddress) &&
                    !sameResource(mExplicitAddress, otherAddress)) {
                reconnect = true;
            }
        }

        setAddress(otherAddress);

        if (reconnect) {
            setFastPolling(true);
        }
        return reconnect;
    }

    private void setAddress(String address) throws DataSourceResourceException {
        if (address != null && !BluetoothAdapter.checkBluetoothAddress(address)) {
            throw new DataSourceResourceException("\"" + address +
                    "\" is not a valid MAC address");
        }
        mExplicitAddress = address;
    }

    private static boolean sameResource(String address, String otherAddress) {
        return otherAddress != null && otherAddress.equals(address);
    }

    @Override
    protected int read(byte[] bytes) throws IOException {
        mConnectionLock.readLock().lock();
        int bytesRead = 0;
        try {
            if (isConnected()
                    && mBleInputStream != null
                    && mBleInputStream.doesBufferHasRemaining()) {
                bytesRead = mInStream.read(bytes, 0, bytes.length);
            }
        } finally {
            mConnectionLock.readLock().unlock();
        }
        return bytesRead;
    }

    @Override
    protected boolean write(byte[] bytes) {
        mConnectionLock.readLock().lock();

        boolean success = false;
        try {
            if (isConnected()) {
                success = mBleHelper.writeCharacteristic(bytes);
            } else {
                Log.w(TAG, "Unable to write -- not connected");
            }
        } finally {
            mConnectionLock.readLock().unlock();
        }
        return success;
    }

    @Override
    protected void disconnect() {
        if (mBleHelper != null && mInStream != null) {
            mBleHelper.disconnect();
            mConnectionLock.writeLock().lock();
            try {
                try {
                    if (mInStream != null) {
                        mInStream.close();
                        Log.d(TAG, "Disconnected from the input stream");
                    }
                } catch (IOException e) {
                    Log.w(TAG, "Unable to close the input stream", e);
                } finally {
                    mInStream = null;
                }
                disconnected();
            } finally {
                mConnectionLock.writeLock().unlock();
            }
        }
    }

    @Override
    protected void connect() throws DataSourceException {
        Log.d(TAG,"BLEVehicleInterface connect()");
        if (!mUsePolling || !isRunning()) {
            return;
        }

        BluetoothDevice lastConnectedDevice = mBleHelper.getLastConnectedDevice();

        mConnectionLock.writeLock().lock();

        if (mExplicitAddress != null) {
            String address = mExplicitAddress;
            if (lastConnectedDevice != null) {
                address = lastConnectedDevice.getAddress();
            }
            if (!isConnected() && address != null) {
                try {
                    connectStreams();
                    connected();
                    mIsConnected = mBleHelper.connect(address);
                    mConnectedAddress = address;
                } catch (BLEException e) {
                    disconnect();
                    Log.d(TAG, "Exception Occurred in connecting with BLE!" + e);
                }
            }
        } else {
            ArrayList<BluetoothDevice> candidateDevices =
                    new ArrayList<>(
                            mBleHelper.getCandidateDevices());

            if (lastConnectedDevice == null && candidateDevices.size() > 0) {
                Log.i(TAG, "No BLE VI ever connected, and none of " +
                        "discovered devices could connect - storing " +
                        candidateDevices.get(0).getAddress() +
                        " as the next one to try");
                mBleHelper.storeLastConnectedDevice(candidateDevices.get(0));
            }
        }
        mConnectionLock.writeLock().unlock();
    }

    private void connectStreams() throws BLEException {
        mConnectionLock.writeLock().lock();
        try {
            try {
                mInStream = new BufferedInputStream(BLEInputStream.getInstance());
                Log.i(TAG, "Socket stream to vehicle interface " +
                        "opened successfully");
            } catch (Exception e) {
                Log.e(TAG, "Error opening streams ", e);
                disconnect();
                throw new BLEException();
            }
        } finally {
            mConnectionLock.writeLock().unlock();
        }
    }


    @Override
    public boolean isConnected() {
       return mIsConnected;
    }

    @Override
    public synchronized void stop() {
        if (isRunning()) {
            mBleHelper.stop();
            super.stop();
        }
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
                .add("explicitDeviceAddress", mExplicitAddress)
                .add("connectedDeviceAddress", mConnectedAddress)
                .add("bleHelper", mBleHelper)
                .toString();
    }

}
