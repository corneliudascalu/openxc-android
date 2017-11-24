package com.openxc.enabler.preferences;

import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.util.Log;

import com.openxc.interfaces.ble.BLEException;
import com.openxc.interfaces.ble.BLEHelper;
import com.openxc.interfaces.ble.BLEVehicleInterface;
import com.openxc.interfaces.bluetooth.BluetoothException;
import com.openxc.interfaces.bluetooth.BluetoothVehicleInterface;
import com.openxc.interfaces.bluetooth.DeviceManager;
import com.openxc.remote.VehicleServiceException;
import com.openxc.util.SupportSettingsUtils;
import com.openxcplatform.enabler.R;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Created by AKUMA128 on 11/18/2017.
 */

public class BLEPreferenceManager extends VehiclePreferenceManager {
    private final static String TAG = BLEPreferenceManager.class.getSimpleName();
    private BLEHelper mBleHelper;
    private HashMap<String, String> mDiscoveredDevices =
            new HashMap<String, String>();
    public BLEPreferenceManager(Context context) {
        super(context);

        IntentFilter filter = new IntentFilter(BluetoothDevice.ACTION_FOUND);
        context.registerReceiver(mDiscoveryReceiver, filter);

        try {
            mBleHelper = new BLEHelper(context);
            fillBleDeviceList();
        } catch(BLEException e) {
            Log.w(TAG, "This device most likely does not have " +
                    "a Bluetooth adapter");
        }
    }

    @SuppressWarnings("unchecked")
    public Map<String, String> getDiscoveredDevices() {
        return (Map<String, String>) mDiscoveredDevices.clone();
    }

    @Override
    public void close() {
        super.close();
        getContext().unregisterReceiver(mDiscoveryReceiver);
        mBleHelper.stop();
    }

    @Override
    protected PreferenceListener createPreferenceListener() {
        return new PreferenceListener() {
            private int[] WATCHED_PREFERENCE_KEY_IDS = {
                    R.string.vehicle_interface_key,
                    R.string.ble_polling_key,
                    R.string.ble_mac_key,
            };

            protected int[] getWatchedPreferenceKeyIds() {
                return WATCHED_PREFERENCE_KEY_IDS;
            }

            public void readStoredPreferences() {
                setBluetoothStatus(getPreferences().getString(
                        getString(R.string.vehicle_interface_key), "").equals(
                        getString(R.string.ble_interface_option_value)));
                getVehicleManager().setBluetoothPollingStatus(
                        getPreferences().getBoolean(
                                getString(R.string.ble_polling_key), true));
            }
        };
    }
    private synchronized void setBluetoothStatus(boolean enabled) {
        if(enabled) {
            Log.i(TAG, "Enabling the BLE vehicle interface");
            String deviceAddress = getPreferenceString(
                    R.string.ble_mac_key);
            if(deviceAddress == null || deviceAddress.equals(
                    getString(R.string.ble_mac_automatic_option))) {
                deviceAddress = null;
                Log.d(TAG, "No BLE vehicle interface selected -- " +
                        "starting in automatic mode");
            }

            try {
                getVehicleManager().setVehicleInterface(
                        BLEVehicleInterface.class, deviceAddress);
            } catch(VehicleServiceException e) {
                Log.e(TAG, "Unable to start BLE interface", e);
            }
        }
    }

    private void fillBleDeviceList() {
        for(BluetoothDevice device :
                mBleHelper.getCandidateDevices()) {
            mDiscoveredDevices.put(device.getAddress(),
                    device.getName() + " (" + device.getAddress() + ")");
        }

        persistCandidateDiscoveredDevices();
        mBleHelper.startDiscovery();
    }


    private BroadcastReceiver mDiscoveryReceiver = new BroadcastReceiver() {
        public void onReceive(Context context, Intent intent) {
            if(BluetoothDevice.ACTION_FOUND.equals(intent.getAction())) {
                BluetoothDevice device = intent.getParcelableExtra(
                        BluetoothDevice.EXTRA_DEVICE);
                if(device.getBondState() != BluetoothDevice.BOND_BONDED) {
                    String summary = device.getName() + " (" +
                            device.getAddress() + ")";
                    Log.d(TAG, "Found unpaired ble device: " + summary);
                    mDiscoveredDevices.put(device.getAddress(), summary);
                    persistCandidateDiscoveredDevices();
                }
            }
        }
    };

    private void persistCandidateDiscoveredDevices() {
        // TODO I don't think the MULTI_PROCESS flag is necessary
        SharedPreferences.Editor editor =
                getContext().getSharedPreferences(
                        BLEHelper.KNOWN_BLE_DEVICE_PREFERENCES,
                        Context.MODE_MULTI_PROCESS).edit();
        Set<String> candidates = new HashSet<String>();
        for(Map.Entry<String, String> device : mDiscoveredDevices.entrySet()) {
            if(device.getValue().startsWith(
                    BLEVehicleInterface.DEVICE_NAME_PREFIX)) {
                candidates.add(device.getKey());
            }
        }
        SupportSettingsUtils.putStringSet(editor,
                BLEHelper.KNOWN_BLE_DEVICE_PREF_KEY, candidates);
        editor.commit();
    }



}
