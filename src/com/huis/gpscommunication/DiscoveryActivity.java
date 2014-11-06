package com.huis.gpscommunication;

import java.lang.reflect.Method;
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
	BroadcastReceiver mReceiver;
	DeviceItemAdapter listAdapter;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.discovery);

		adapter = BluetoothAdapter.getDefaultAdapter();
		listAdapter = new DeviceItemAdapter(this);
		mReceiver = new BlueToothReceiver();  

		this.setListAdapter(listAdapter);
	}
	
	@Override
	protected void onStop() {
		unregisterReceiver(mReceiver);	//反注册广播接收器
		super.onStop();
	}
	
	@Override
	protected void onStart() {
		listAdapter.clear();
		
		//注册广播接收和广播结束的filter  
		IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(BluetoothDevice.ACTION_FOUND);
        intentFilter.addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED);
        intentFilter.addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED);
        registerReceiver(mReceiver,intentFilter);
        
        adapter.startDiscovery();	//开始搜索设备
        super.onStart();
	}
	
	@Override
	protected void onListItemClick(ListView l, View v, int position, long id) {
		adapter.cancelDiscovery();	//取消搜索
		
		HashMap<String, String> selectedItem = listAdapter.getItem(position);
		String addressStr = selectedItem.get(DeviceItemAdapter.ADDRESS);
		BluetoothDevice btDev = adapter.getRemoteDevice(addressStr);
		
		switch (btDev.getBondState()) {
		case BluetoothDevice.BOND_NONE:		//未配对设备进行配对
			displayLongToast("开始配对");
			try {
				Method createBondMethod  = BluetoothDevice.class.getMethod("createBond");
				createBondMethod.invoke(btDev);
			} catch (Exception e) {
				e.printStackTrace();
				Log.e("MyError", "配对失败："+e.getMessage());
			}
			break;
		case BluetoothDevice.BOND_BONDED:	//已配对设备，返回设备地址
			Intent result = new Intent();
			
			String address = addressStr.substring(addressStr.length() - 17);
			result.putExtra(EXTRA_DEVICE_ADDRESS, address);
			setResult(Activity.RESULT_OK, result);
			finish();
			break;
		default:
			break;
		}
		super.onListItemClick(l, v, position, id);
	}
	
	/**
	 * 刷新设备列表
	 * @param v : 响应的视图
	 */
	public void refreshList(View v){
		if(!adapter.isDiscovering()) {
			listAdapter.clear();
			adapter.startDiscovery();	//开始搜索设备
		}
	}
	
	/**
	 * 蓝牙广播接收器
	 * @author Administrator
	 */
	class BlueToothReceiver extends BroadcastReceiver {

		@Override
		public void onReceive(Context context, Intent intent) {
			String action = intent.getAction();
			if(BluetoothDevice.ACTION_FOUND.equals(action)) {	//搜索到设备时,加入设备列表
				BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
				HashMap<String, String> map= new HashMap<String, String>();
				int bondState = device.getBondState();
				String bondStateStr = "";
				
				switch (bondState) {
				case BluetoothDevice.BOND_NONE:
					bondStateStr = "未配对";
					break;
				case BluetoothDevice.BOND_BONDED:
					bondStateStr = "已配对";
					break;
				default:
					break;
				}
				map.put(DeviceItemAdapter.ADDRESS, device.getAddress());
				map.put(DeviceItemAdapter.NAME, device.getName());
				map.put(DeviceItemAdapter.STATE, bondStateStr);
				
				listAdapter.add(map);
				listAdapter.notifyDataSetChanged();
			} else if(BluetoothAdapter.ACTION_DISCOVERY_FINISHED.equals(action)){	//搜索完成
				displayLongToast("搜索结束，请选择一个设备进行连接！");
			} else if(BluetoothDevice.ACTION_BOND_STATE_CHANGED.equals(action)) {	//设备状态变更时
				BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
				
				switch (device.getBondState()) {
				case BluetoothDevice.BOND_BONDED:		//配对成功
					listAdapter.update(device.getAddress(), "已配对");
					listAdapter.notifyDataSetChanged();
					break;
				case BluetoothDevice.BOND_NONE:			//取消配对
					listAdapter.update(device.getAddress(), "未配对");
					listAdapter.notifyDataSetChanged();
				default:
					break;
				}
			}
			
		}
	}
	
	/**
	 * ListView 显示适配类
	 * @author Administrator
	 */
	class DeviceItemAdapter extends BaseAdapter {

		public static final String ADDRESS = "address";
		public static final String NAME = "name";
		public static final String STATE = "state";
		
		private LayoutInflater inflater;
		List<HashMap<String, String>> items;
		
		public DeviceItemAdapter(Context context) {
			super();
			inflater = LayoutInflater.from(context);
			items = new ArrayList<HashMap<String, String>>();
		}
		
		/**
		 * 清除设备列表
		 */
		public void clear() {
			items.clear();
		}

		/**
		 * 添加设备
		 * @param map : 设备信息
		 */
		public void add(HashMap<String, String> map){
			HashMap<String, String> existMap = getItem(map.get(ADDRESS));
			if(existMap == null) {
				items.add(map);
			} else if(!existMap.get(NAME).equals(map.get(NAME))){
				existMap.put(NAME, map.get(NAME));
			}
		}

		/**
		 * 更新设备状态
		 * @param address
		 * @param state
		 */
		public void update(String address, String state) {
			HashMap<String, String> tmp = getItem(address);
			if(tmp != null) {
				tmp.put(STATE, state);
			}
		}
		
		/**
		 * 获取设备信息
		 * @param address
		 * @return
		 */
		public HashMap<String, String> getItem(String address) {
			for(HashMap<String, String> tmp : items) {
				if(tmp.get(ADDRESS).equals(address)) {
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

		/**
		 * 界面与数据绑定规则
		 */
		@Override
		public View getView(int position, View view, ViewGroup parent) {
			if(view == null){  
                view = inflater.inflate(R.layout.device, null);  
            }  
            final TextView name = (TextView) view.findViewById(R.id.name);
            final TextView address = (TextView) view.findViewById(R.id.address);
            final TextView state = (TextView) view.findViewById(R.id.status);
            HashMap<String, String> item = getItem(position);
            name.setText(item.get(NAME));
            address.setText(item.get(ADDRESS));
            state.setText(item.get(STATE));
            return view;  
		}
		
	}

	/**
	 * 弹出提示信息
	 * @param toastMsg ：信息内容
	 */
	private void displayLongToast(String toastMsg) {
		Toast.makeText(this, toastMsg, Toast.LENGTH_LONG).show();
	}
}
