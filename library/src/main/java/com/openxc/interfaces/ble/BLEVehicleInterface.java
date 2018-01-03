package com.openxc.interfaces.ble;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGattCallback;
import android.content.Context;
import android.util.Log;

import com.openxc.interfaces.VehicleInterface;
import com.openxc.sources.BytestreamDataSource;
import com.openxc.sources.DataSourceException;
import com.openxc.sources.DataSourceResourceException;
import com.openxc.sources.SourceCallback;

import java.io.BufferedInputStream;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;

/**
 * Created by AKUMA128 on 12/25/2017.
 */

public class BLEVehicleInterface extends BytestreamDataSource implements VehicleInterface {
    public static final String TAG = BLEVehicleInterface.class.getSimpleName();
    private BLEHelper mBleHelper;
    private BluetoothGattCallback mGattCallback;

    private String mConnectedAddress;
    private String mExplicitAddress;
    private boolean mIsConnected = false;
    private int counter = 0;
    private BufferedInputStream mInStream;

    public BLEVehicleInterface(SourceCallback callback, Context context, String address) throws DataSourceResourceException {
        super(callback, context);
        Log.d(TAG, "Calling Constructor");
        try {
            mBleHelper = new BLEHelper(context);
            mGattCallback = new GattCallback(mBleHelper, context);
            mBleHelper.setGattCallback(mGattCallback);
            setAddress(address);
        } catch (BLEException e) {
            e.printStackTrace();
        }
        start();
    }

    public BLEVehicleInterface(Context context, String address)
            throws DataSourceException {
        this(null, context, address);
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
        //mConnectionLock.readLock().lock();
        int bytesRead = -1;
        //TODO Have to do something about the error on read - returned -1 it can cause disconnection
        try {
            if (isConnected() && BLEInputStream.getInstance().doesBufferHasRemaining()) {
                bytesRead = mInStream.read(bytes, 0, bytes.length);
            }
        } finally {
          //  mConnectionLock.readLock().unlock();
        }
        return bytesRead;
    }

    @Override
    protected boolean write(byte[] bytes) {
        Log.d(TAG, "Calling Write");
        boolean success = false;

            if(isConnected()) {
                success = mBleHelper.writeCharacteristic(bytes);
            } else {
                Log.w(TAG, "Unable to write -- not connected");
            }
        return success;
    }

    @Override
    protected void disconnect() {
    }

    @Override
    protected void connect() throws DataSourceException {
        Log.d(TAG, "Calling Connect Explicit Address : " + mExplicitAddress);
        mConnectionLock.writeLock().lock();
        if (mExplicitAddress != null) {
            final String address = mExplicitAddress;
            if (!isConnected()) {
                if (mBleHelper.getDeviceType(address) == BluetoothDevice.DEVICE_TYPE_LE) {
                    try {
                        mIsConnected = mBleHelper.connect(address);
                        mInStream = new BufferedInputStream(BLEInputStream.getInstance());
                    } catch (BLEException e) {
                        mIsConnected = false;
                        Log.d(TAG, "Exception Occured!" + e);
                    }
                }
            }
        }
        connected();
        mConnectionLock.writeLock().unlock();
    }

    @Override
    public boolean isConnected() {
        return mIsConnected;
    }
}
