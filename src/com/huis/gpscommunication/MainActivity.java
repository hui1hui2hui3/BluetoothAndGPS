package com.huis.gpscommunication;

import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Iterator;
import java.util.Set;
import java.util.UUID;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.AsyncTask;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.Toast;

public class MainActivity extends Activity {

	private static final int REQUEST_ENABLE_BT = 0x1;
	private static final int REQUEST_CONNECT_DEVICE = 0x2;
	private static final UUID MY_UUID = UUID.fromString("00001101-0000-1000-8000-00805f9b34fb");
														
	BluetoothAdapter adapter;
	BluetoothDevice device;
	BluetoothSocket socket;
	DataOutputStream outStream;
	AutoConnectTask autoConnTask;
	BroadcastReceiver blueStatusReceiver;
	
	Button btnSend;
	Button btnListDevice;
	ProgressBar progressAutoConnect;
	
	boolean isNeedDeviceList = false;
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);
		
		adapter = BluetoothAdapter.getDefaultAdapter();
		//if the adapter is null, then Bluetooth is not supported
		if(adapter == null) {
			displayLongToast("蓝牙不可用！");
			finish();
			return;
		}
		this.init();
	}
	
	private void init() {
		btnSend = (Button) findViewById(R.id.btn_send);
		btnListDevice = (Button) findViewById(R.id.btn_list_device);
		progressAutoConnect = (ProgressBar) findViewById(R.id.progress_auto_connect);
		autoConnTask = new AutoConnectTask(this);
		blueStatusReceiver = new BlueToothStatusReceiver();
		
		btnSend.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				sendMessage("GPS:121.32312:32.32322:上海市红牛而是路可是减肥工厂");
			}
		});
		btnListDevice.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				selectDevice(true);
			}
		});
		
		if(adapter.isEnabled()) {
			autoConnTask.execute();
		}
	}

	@Override
	protected void onStart() {
		//注册广播接收和广播结束的filter  
		IntentFilter intentFilter = new IntentFilter();
	    intentFilter.addAction(BluetoothAdapter.ACTION_STATE_CHANGED);
	    registerReceiver(blueStatusReceiver,intentFilter);
	    
	    if(!adapter.isEnabled()) {
			//Intent intent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
			//startActivityForResult(intent, REQUEST_ENABLE_BT);
			//不做提示强行打开蓝牙
			if(!adapter.enable()) {
				displayLongToast("蓝牙不可用！");
			}
	    }
		super.onStart();
	}
	
	@Override
	protected void onStop() {
		unregisterReceiver(blueStatusReceiver);	//反注册广播接收器
		super.onStop();
	}
	
	@Override
	protected void onResume() {
		super.onResume();
	}
	
	@Override
	protected void onDestroy() {
		super.onDestroy();
		if(adapter.isEnabled()) {
			adapter.disable();
		}
	}
	
	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		super.onActivityResult(requestCode, resultCode, data);
		switch (requestCode) {
		case REQUEST_ENABLE_BT:	//强行打开蓝牙时，该项不可用
			if (resultCode == Activity.RESULT_OK) {
				displayLongToast("打开蓝牙成功！");
				autoConnTask.execute();
			} else {
				displayLongToast("蓝牙不可用！");
				finish();
			}
			break;
		case REQUEST_CONNECT_DEVICE:
			if (resultCode == Activity.RESULT_OK) {
				String addressStr = data.getExtras().getString(DiscoveryActivity.EXTRA_DEVICE_ADDRESS);
				boolean isConn = connectDevice(addressStr);
				if(!isConn) {
					displayLongToast("设备连接失败！");
					btnListDevice.setVisibility(Button.VISIBLE);
				}
			}
			break;
		default:
			break;
		}
	}
	
	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		getMenuInflater().inflate(R.menu.activity_main, menu);
		return true;
	}
	
	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		int itemId = item.getItemId();
		switch (itemId) {
		case R.id.device_list:
			selectDevice(true);
			break;
		default:
			break;
		}
		return super.onOptionsItemSelected(item);
	}
	
	/**
	 * 蓝牙状态广播接收器
	 * @author Administrator
	 *
	 */
	class BlueToothStatusReceiver extends BroadcastReceiver {

		@Override
		public void onReceive(Context context, Intent intent) {
			String action = intent.getAction();
			if(BluetoothAdapter.ACTION_STATE_CHANGED.equals(action)) {
				int state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE,BluetoothAdapter.STATE_OFF);
				if (state == BluetoothAdapter.STATE_ON) {
					displayLongToast("打开蓝牙成功！");
					autoConnTask.execute();
				} else if(state == BluetoothAdapter.STATE_OFF) {
					displayLongToast("蓝牙不可用！");
					finish();
				}
			}
		}
		
	}
	
	/**
	 * 自动连接设备线程(异步)
	 * @author Administrator
	 */
	class AutoConnectTask extends AsyncTask<Void,Integer,Boolean> {

		private Context context;  
		AutoConnectTask(Context context) {  
            this.context = context;
        }
		
		/** 
         * 运行在UI线程中，在调用doInBackground()之前执行 
         */  
        @Override  
        protected void onPreExecute() {  
            Toast.makeText(context,"开始自动连接设备",Toast.LENGTH_SHORT).show();
            progressAutoConnect.setProgress(0);
        }  
		
        /** 
         * 后台运行的方法，可以运行非UI线程，可以执行耗时的方法 
         */
		@Override
		protected Boolean doInBackground(Void... params) {
			publishProgress(0);
			Set<BluetoothDevice> devices = adapter.getBondedDevices();
			publishProgress(10);
			if (devices.size() > 0) {
				Iterator<BluetoothDevice> it = devices.iterator();
				BluetoothDevice device = null;
				publishProgress(20);
				while (it.hasNext()) {
					device = it.next();
					publishProgress(50);
					if(connectDevice(device.getAddress())) {
						publishProgress(100);
						return true;
					}
				}
			}
			publishProgress(100);
			return false;
		}
		
		/** 
         * 运行在ui线程中，在doInBackground()执行完毕后执行 
         */  
		@Override
		protected void onPostExecute(Boolean result) {
			isNeedDeviceList = !result;
			if(result) {
				displayLongToast("设备已自动连接成功！");
				btnSend.setVisibility(Button.VISIBLE);
			} else {
				displayLongToast("无可用设备！请手动选择设备进行连接");
				selectDevice(false);
			}
			progressAutoConnect.setVisibility(ProgressBar.GONE);
		}
		
		/** 
         * 在publishProgress()被调用以后执行，publishProgress()用于更新进度 
         */  
        @Override  
        protected void onProgressUpdate(Integer... values) {
        	progressAutoConnect.setProgress(values[0]);
        } 
	}
	
	/**
	 * 打开蓝牙设备列表-进行手动选择设备
	 * @param isForce : 是否强制打开设备列表
	 */
	public void selectDevice(boolean isForce){
		btnSend.setVisibility(Button.GONE);
		btnListDevice.setVisibility(Button.GONE);
		if(isForce || isNeedDeviceList) {
			Intent intent = new Intent(this,DiscoveryActivity.class);
			startActivityForResult(intent, REQUEST_CONNECT_DEVICE);
		}
	}
	
	/**
	 * 给已连接设备发送信息
	 * @param string ：信息内容，格式（ 经度：纬度：地址）
	 */
	private void sendMessage(String string) {
		if(outStream != null) {
			try {
				outStream.writeUTF(string);
				outStream.flush();
			} catch (IOException e) {
				displayLongToast("发送数据失败！");
				e.printStackTrace();
				try {
					outStream.close();
				} catch (IOException e1) {
					e1.printStackTrace();
				} finally {
					outStream = null;
				}
			}
		}
	}
	
	/**
	 * 显示长时间的Toast提示信息
	 * @param toastMsg : 消息内容
	 */
	private void displayLongToast(String toastMsg){
		Toast.makeText(this, toastMsg, Toast.LENGTH_LONG).show();
	}
	
	/**
	 * 连接设备
	 * @param addressStr ： 连接设备蓝牙地址
	 * @return 是否连接成功
	 */
	private boolean connectDevice(String addressStr) {
		device = adapter.getRemoteDevice(addressStr);
		try {
			socket = device.createRfcommSocketToServiceRecord(MY_UUID);
			if(socket != null) {
				socket.connect();
				outStream = new DataOutputStream(socket.getOutputStream());
				return true;
			}
		} catch (IOException e) {
			try {
				socket.close();
			} catch (IOException e1) {
				e1.printStackTrace();
			}
			e.printStackTrace();
		}
		return false;
	}
}
