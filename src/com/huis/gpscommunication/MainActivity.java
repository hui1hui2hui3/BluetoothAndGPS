package com.huis.gpscommunication;

import java.io.DataOutputStream;
import java.io.IOException;
import java.util.UUID;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Intent;
import android.os.Bundle;
import android.view.Menu;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
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
				sendMessage("GPS");
			}
		});
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
			if(outStream == null) connectDeivice();
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
				connectDeivice();
			} else {
				displayLongToast("蓝牙不可用！");
				finish();
			}
			break;
		case REQUEST_CONNECT_DEVICE:
			if (resultCode == Activity.RESULT_OK) {
				String addressStr = data.getExtras().getString(DiscoveryActivity.EXTRA_DEVICE_ADDRESS);
				device = adapter.getRemoteDevice(addressStr);
				try {
					socket = device.createRfcommSocketToServiceRecord(MY_UUID);
				} catch (IOException e) {
					e.printStackTrace();
					displayLongToast("建立连接失败！");
				}
				if(socket != null) {
					try {
						socket.connect();
						displayLongToast("通道连接成功！");
					} catch (IOException e) {
						e.printStackTrace();
						displayLongToast("通道连接失败！");
						try {
							socket.close();
						} catch (IOException e1) {
							e1.printStackTrace();
						}
					}
					try {
						outStream = new DataOutputStream(socket.getOutputStream());
					} catch (IOException e) {
						displayLongToast("数据流创建失败！");
						e.printStackTrace();
					}
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
	
	public void connectDeivice(){
		Intent intent = new Intent(this,DiscoveryActivity.class);
		displayLongToast("请选择一个蓝牙设备进行连接！");
		startActivityForResult(intent, REQUEST_CONNECT_DEVICE);
	}
	
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
	
	private void displayLongToast(String toastMsg){
		Toast.makeText(this, toastMsg, Toast.LENGTH_LONG).show();
	}
}
