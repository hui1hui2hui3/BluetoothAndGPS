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
import android.content.Context;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.view.Menu;
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
	
	Button btnSend;
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
		btnSend = (Button) findViewById(R.id.btn_send);
		btnSend.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				sendMessage("GPS:121.32312:32.32322:上海市红牛而是路可是减肥工厂");
			}
		});
		progressAutoConnect = (ProgressBar) findViewById(R.id.progress_auto_connect);
		new AutoConnectTask(this).execute();
	}
	
	@Override
	protected void onStart() {
		super.onStart();
		
		//if Bluetooth is not on, request that is be enabled
		//startChart will be called during onActivityResult
		if(!adapter.isEnabled()) {
			Intent intent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
			startActivityForResult(intent, REQUEST_ENABLE_BT);
			//不做提示强行打开蓝牙
			//adapter.enable();
		} else {
			if(outStream == null) selectDevice(false);
		}
	}
	
	@Override
	protected void onResume() {
		super.onResume();
	}
	
	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		super.onActivityResult(requestCode, resultCode, data);
		switch (requestCode) {
		case REQUEST_ENABLE_BT:
			if (resultCode == Activity.RESULT_OK) {
				displayLongToast("打开蓝牙成功！");
				selectDevice(false);
			} else {
				displayLongToast("蓝牙不可用！");
				finish();
			}
			break;
		case REQUEST_CONNECT_DEVICE:
			if (resultCode == Activity.RESULT_OK) {
				String addressStr = data.getExtras().getString(DiscoveryActivity.EXTRA_DEVICE_ADDRESS);
				connectDevice(addressStr);
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
				btnSend.setEnabled(true);
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
		if(isForce || isNeedDeviceList) {
			Intent intent = new Intent(this,DiscoveryActivity.class);
			displayLongToast("请选择一个蓝牙设备进行连接！");
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
