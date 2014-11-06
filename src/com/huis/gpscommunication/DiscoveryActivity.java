package com.huis.gpscommunication;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import android.app.Activity;
import android.app.ListActivity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

public class DiscoveryActivity extends ListActivity {

	public static String EXTRA_DEVICE_ADDRESS = "device_address";

	BluetoothAdapter adapter;
	List<BluetoothDevice> _devices;
	BroadcastReceiver mReceiver;
	
	DeviceItemAdapter listAdapter;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.discovery);

		adapter = BluetoothAdapter.getDefaultAdapter();
		_devices = new ArrayList<BluetoothDevice>();


		//创建蓝牙广播信息的receiver  
		mReceiver = new BlueToothReceiver();  
		//设定广播接收的filter  
        IntentFilter intentFilter = new IntentFilter(BluetoothDevice.ACTION_FOUND);  
        registerReceiver(mReceiver,intentFilter);  

        //设定广播结束的filter  
        intentFilter = new IntentFilter(BluetoothAdapter.ACTION_DISCOVERY_FINISHED);
        registerReceiver(mReceiver, intentFilter);
        
        adapter.startDiscovery();
		
		listAdapter = new DeviceItemAdapter(this);
		this.setListAdapter(listAdapter);
	}
	
	@Override
	public void onBackPressed() {
		super.onBackPressed();
		_devices.clear();
		listAdapter.clear();
	}

	@Override
	protected void onListItemClick(ListView l, View v, int position, long id) {
		super.onListItemClick(l, v, position, id);
		adapter.cancelDiscovery();
		Intent result = new Intent();
		String addressStr = _devices.get(position).getAddress();
		String address = addressStr.substring(addressStr.length() - 17);
		result.putExtra(EXTRA_DEVICE_ADDRESS, address);
		setResult(Activity.RESULT_OK, result);
		unregisterReceiver(mReceiver);  
		finish();
	}
	
	class BlueToothReceiver extends BroadcastReceiver {

		@Override
		public void onReceive(Context context, Intent intent) {
			String action = intent.getAction();
			Log.d("MyDebug",action);
			if(BluetoothDevice.ACTION_FOUND.equals(action)) {
				BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
				HashMap<String, String> map= new HashMap<String, String>();
				map.put("address", device.getAddress());
				map.put("name", device.getName());
				listAdapter.add(map);
				_devices.add(device);
				listAdapter.notifyDataSetChanged();
				Log.d("MyDebug",device.getName());
			} else if(BluetoothAdapter.ACTION_DISCOVERY_FINISHED.equals(action)){
				displayLongToast("搜索结束，请选择一个设备进行连接！");
			}
			
		}
	}
	
	class DeviceItemAdapter extends BaseAdapter {

		public static final String ADDRESS = "address";
		public static final String NAME = "name";
		private LayoutInflater inflater;
		List<HashMap<String, String>> items;
		
		public DeviceItemAdapter(Context context) {
			super();
			inflater = LayoutInflater.from(context);
			items = new ArrayList<HashMap<String, String>>();
		}
		
		public void clear() {
			items.clear();
		}

		public void add(HashMap<String, String> map){
			HashMap<String, String> existMap = getExists(map);
			if(existMap == null) {
				items.add(map);
			} else if(!existMap.get(NAME).equals(map.get(NAME))){
				existMap.put(NAME, map.get(NAME));
			}
		}

		private HashMap<String, String> getExists(HashMap<String, String> map) {
			for(HashMap<String, String> tmp : items) {
				if(tmp.get(ADDRESS).equals(map.get(ADDRESS))) {
					return tmp;
				}
			}
			return null;
		}
		
		@Override
		public int getCount() {
			return items.size();
		}

		@Override
		public HashMap<String, String> getItem(int location) {
			return items.get(location);
		}

		@Override
		public long getItemId(int position) {
			return position;
		}

		@Override
		public View getView(int position, View view, ViewGroup parent) {
			if(view == null){  
                view = inflater.inflate(R.layout.device, null);  
            }  
            final TextView name = (TextView) view.findViewById(R.id.name);
            final TextView address = (TextView) view.findViewById(R.id.address);
            HashMap<String, String> item = getItem(position);
            name.setText(item.get(NAME));
            address.setText(item.get(ADDRESS));
            return view;  
		}
		
	}

	private void displayLongToast(String toastMsg) {
		Toast.makeText(this, toastMsg, Toast.LENGTH_LONG).show();
	}
}
