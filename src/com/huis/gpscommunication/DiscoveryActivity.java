package com.huis.gpscommunication;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import android.app.Activity;
import android.app.ListActivity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ListView;
import android.widget.SimpleAdapter;
import android.widget.Toast;

public class DiscoveryActivity extends ListActivity {

	public static String EXTRA_DEVICE_ADDRESS = "device_address";

	BluetoothAdapter adapter;
	List<BluetoothDevice> _devices;
	List<HashMap<String, String>> list;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.discovery);

		adapter = BluetoothAdapter.getDefaultAdapter();
		_devices = new ArrayList<BluetoothDevice>();
		list = new ArrayList<HashMap<String, String>>();
		showDevices();
	}

	@Override
	protected void onListItemClick(ListView l, View v, int position, long id) {
		super.onListItemClick(l, v, position, id);
		Intent result = new Intent();
		String addressStr = _devices.get(position).getAddress();
		String address = addressStr.substring(addressStr.length() - 17);
		result.putExtra(EXTRA_DEVICE_ADDRESS, address);
		setResult(Activity.RESULT_OK, result);
		finish();
	}

	// 展示已配对的设备
	private void showDevices() {
		Set<BluetoothDevice> devices = adapter.getBondedDevices();
		if (devices.size() > 0) {
			Iterator<BluetoothDevice> it = devices.iterator();

			BluetoothDevice device = null;
			HashMap<String, String> map;
			while (it.hasNext()) {
				device = it.next();
				map = new HashMap<String, String>();
				map.put("address", device.getAddress());
				map.put("name", device.getName());

				list.add(map);

				_devices.add(device);
			}

			SimpleAdapter listAdapter = new SimpleAdapter(this, list,
					R.layout.device, new String[] { "name", "address" },
					new int[] { R.id.name, R.id.address });
			this.setListAdapter(listAdapter);
		} else {
			displayLongToast("没有已配对的设备！");
		}
	}

	private void displayLongToast(String toastMsg) {
		Toast.makeText(this, toastMsg, Toast.LENGTH_LONG).show();
	}
}
