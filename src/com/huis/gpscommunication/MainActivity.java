package com.huis.gpscommunication;

import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import android.app.Activity;
import android.app.ActivityManager;
import android.app.ActivityManager.RunningTaskInfo;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo.State;
import android.os.AsyncTask;
import android.os.Bundle;
import android.provider.Settings;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends Activity {

	private static final int REQUEST_ENABLE_BT = 0x1;
	private static final int REQUEST_CONNECT_DEVICE = 0x2;
	private static final UUID MY_UUID = UUID.fromString("00001101-0000-1000-8000-00805f9b34fb");

	private static final String MY_TAG = "MyDebug";

	BluetoothAdapter adapter;
	BluetoothDevice device;
	BluetoothSocket socket;
	DataOutputStream outStream;
	AutoConnectTask autoConnTask;
	BroadcastReceiver blueStatusReceiver;

	LocationManager locationManager;
	Location location;
	GPSInfo curGpsInfo;

	TextView lonlatShow;
	Button btnListDevice;
	ProgressBar progressAutoConnect;

	boolean isNeedDeviceList = false;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);

		this.init();
	}

	@Override
	protected void onStart() {
		if (isRunningForeground()) {
			// 注册蓝牙状态变更的广播filter
			IntentFilter intentFilter = new IntentFilter();
			intentFilter.addAction(BluetoothAdapter.ACTION_STATE_CHANGED);
			registerReceiver(blueStatusReceiver, intentFilter);

			if (adapter != null) {
				// if the bluetooth is not open, open it
				if (!adapter.isEnabled()) {
					// 提示打开蓝牙
					/*
					 * Intent intent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
					 * startActivityForResult(intent,REQUEST_ENABLE_BT);
					 */
					// 不做提示强行打开蓝牙
					if (!adapter.enable()) { // if open fail,close app
						adapter = null;
						finish();
					}
				}
				// if the connection is not connected, show the deivces list to connect
				if (adapter.isEnabled() && isNeedDeviceList && outStream == null) {
					selectDevice(true);
				}
			}
		}
		super.onStart();
	}

	@Override
	protected void onStop() {
		unregisterReceiver(blueStatusReceiver); // 反注册广播接收器
		super.onStop();
	}

	@Override
	protected void onDestroy() {
		// if bluetooth is open,close it
		/*
		 * if(adapter.isEnabled()) { adapter.disable(); }
		 */
		super.onDestroy();
	}

	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		super.onActivityResult(requestCode, resultCode, data);
		switch (requestCode) {
		case REQUEST_ENABLE_BT: // 请求为打开蓝牙时，返回结果（强行打开蓝牙时，该项不可用）
			if (resultCode == Activity.RESULT_OK) {
				displayLongToast("打开蓝牙成功！");
				smartExecuteTask();
			} else {
				displayLongToast("蓝牙不可用！");
				finish();
			}
			break;
		case REQUEST_CONNECT_DEVICE: // 请求为设备连接时，返回结果
			if (resultCode == Activity.RESULT_OK) {
				String addressStr = data.getExtras().getString(DiscoveryActivity.EXTRA_DEVICE_ADDRESS);
				boolean isConn = connectDevice(addressStr);
				if (!isConn) {
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
	 * 初始化
	 */
	private void init() {
		adapter = BluetoothAdapter.getDefaultAdapter();
		// if the adapter is null, then Bluetooth is not supported
		if (adapter == null) {
			displayLongToast("蓝牙不可用！");
			finish();
			return;
		}

		btnListDevice = (Button) findViewById(R.id.btn_list_device);
		progressAutoConnect = (ProgressBar) findViewById(R.id.progress_auto_connect);
		blueStatusReceiver = new BlueToothStatusReceiver();
		lonlatShow = (TextView) findViewById(R.id.lonlat_textview);
		locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
		curGpsInfo = new GPSInfo();

		btnListDevice.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				selectDevice(true);
			}
		});

		// if the bluetooth is open, auto connect paired devices
		if (adapter.isEnabled()) {
			smartExecuteTask();
		}
	}

	/**
	 * 智能执行自动连接任务任务
	 */
	private void smartExecuteTask() {
		// if task is executed already, new task to execute
		if (autoConnTask == null) {
			autoConnTask = new AutoConnectTask(this);
			autoConnTask.execute();
		}
	}

	/**
	 * 当前Activity是否前台运行
	 * 
	 * @return
	 */
	private boolean isRunningForeground() {
		String packageName = getPackageName(this);
		String topActivityClassName = getTopActivityName(this);
		if (packageName != null && topActivityClassName != null && topActivityClassName.startsWith(packageName)) {
			return true;
		} else {
			return false;
		}
	}

	/**
	 * 获取当前最顶的Activity名称
	 * 
	 * @param context
	 *            : 上下文环境
	 * @return
	 */
	private String getTopActivityName(Context context) {
		String topActivityClassName = null;
		ActivityManager activityManager = (ActivityManager) (context
				.getSystemService(android.content.Context.ACTIVITY_SERVICE));
		List<RunningTaskInfo> runningTaskInfos = activityManager.getRunningTasks(1);
		if (runningTaskInfos != null) {
			ComponentName f = runningTaskInfos.get(0).topActivity;
			topActivityClassName = f.getClassName();
		}
		return topActivityClassName;
	}

	/**
	 * 获取当前上下文的包名
	 * 
	 * @param context
	 *            ：上下文环境
	 * @return
	 */
	private String getPackageName(Context context) {
		String packageName = context.getPackageName();
		return packageName;
	}

	/**
	 * 显示长时间的Toast提示信息
	 * 
	 * @param toastMsg
	 *            : 消息内容
	 */
	private void displayLongToast(String toastMsg) {
		Toast.makeText(this, toastMsg, Toast.LENGTH_LONG).show();
	}

	/**
	 * 给已连接设备发送信息
	 * 
	 * @param string
	 *            ：信息内容，格式（ 经度：纬度：地址）
	 */
	private void sendMessage(String string) {
		if (outStream != null) {
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
	 * 打开蓝牙设备列表-进行手动选择设备
	 * 
	 * @param isForce
	 *            : 是否强制打开设备列表
	 */
	private void selectDevice(boolean isForce) {
		// 判断主窗口是否还在运行,如果在前台，则继续进行
		if (isRunningForeground()) {
			btnListDevice.setVisibility(Button.GONE);
			if (isForce || isNeedDeviceList) {
				Intent intent = new Intent(this, DiscoveryActivity.class);
				startActivityForResult(intent, REQUEST_CONNECT_DEVICE);
			}
		}
	}

	/**
	 * 连接设备
	 * 
	 * @param addressStr
	 *            ： 连接设备蓝牙地址
	 * @return 是否连接成功
	 */
	private boolean connectDevice(String addressStr) {
		device = adapter.getRemoteDevice(addressStr);
		try {
			socket = device.createRfcommSocketToServiceRecord(MY_UUID);
			if (socket != null) {
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

	/**
	 * 初始化GPS, 判断GPS是否打开
	 */
	private void initGPS() {
		if (location == null) {
			boolean isOpenGps = isOpenGPS();
			if (isOpenGps) {
				Toast.makeText(this, "GPS is Ready!", Toast.LENGTH_LONG).show();
				getGPSInfo(true);
			} else {
				Intent intent = new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS);
				startActivityForResult(intent, 0);
			}
		}
	}

	/**
	 * 判断是否打开GPS定位
	 * 
	 * @return
	 */
	private boolean isOpenGPS() {
		// 通过GPS卫星定位，定位级别可以精确到街（通过24颗卫星定位，在室外和空旷的地方定位准确、速度快）
		return locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER);
	}

	/**
	 * 判断是否打开移动网络(4G/3G/2G)定位
	 * 
	 * @return
	 */
	private boolean isOpenNetWork() {
		// 通过WLAN或移动网络(3G/2G)确定的位置（也称作AGPS，辅助GPS定位。主要用于在室内或遮盖物（建筑群或茂密的深林等）密集的地方定位）
		return locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER) || isOpenWifi();
	}

	/**
	 * 判断当前是否使用的是 WIFI网络
	 * 
	 * @return
	 */
	private boolean isOpenWifi() {
		Context context = getApplicationContext();
		ConnectivityManager manager = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
		State wifi = manager.getNetworkInfo(ConnectivityManager.TYPE_WIFI).getState();
		if (wifi == State.CONNECTED) {
			return true;
		}
		return false;
	}

	/**
	 * 获取GPS经纬度信息
	 * 
	 * @param isGPS
	 *            获取经纬度方式（true：GPS获取，false：基站获取）
	 */
	private void getGPSInfo(boolean isGPS) {
		if (location == null) {
			MyLocationListener localListener = new MyLocationListener();
			String provider = LocationManager.NETWORK_PROVIDER;
			if (isGPS) {
				provider = LocationManager.GPS_PROVIDER;
			}
			location = locationManager.getLastKnownLocation(provider);
			locationManager.requestLocationUpdates(provider, 5000, 10, localListener); // 设置每5秒或每10米获取一次定位信息
		}
		updateView(location);
	}

	/**
	 * 更新经纬度信息
	 * 
	 * @param location
	 *            位置信息
	 */
	private void updateView(Location location) {
		if (location != null) {
			double lon = location.getLongitude();
			double lat = location.getLatitude();
			curGpsInfo.setLon(lon);
			curGpsInfo.setLat(lat);
			curGpsInfo.setApartment("科达公司");
			StringBuffer sb = new StringBuffer();
			sb.append("实时位置信息：\n经度：");
			sb.append(lon);
			sb.append("\n纬度：");
			sb.append(lat);
			sb.append("\n周边单位：");
			sb.append("科达科技股份有限公司");
			sb.append("\n单位地址：");
			sb.append("上海市虹梅路2007号");
			lonlatShow.setText(sb.toString());
		} else {
			// 如果传入的Location对象为空则清空EditText
			lonlatShow.setText("");
		}
	}

	/**
	 * 蓝牙状态广播接收器
	 * 
	 * @author Administrator
	 *
	 */
	class BlueToothStatusReceiver extends BroadcastReceiver {

		@Override
		public void onReceive(Context context, Intent intent) {
			String action = intent.getAction();
			if (BluetoothAdapter.ACTION_STATE_CHANGED.equals(action)) {
				int state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.STATE_OFF);
				if (state == BluetoothAdapter.STATE_ON) {
					displayLongToast("打开蓝牙成功！");
					smartExecuteTask();
				} else if (state == BluetoothAdapter.STATE_OFF) {
					displayLongToast("蓝牙不可用！");
					finish();
				}
			}
		}

	}

	/**
	 * 自动连接设备线程(异步)
	 * 
	 * @author Administrator
	 */
	class AutoConnectTask extends AsyncTask<Void, Integer, Boolean> {

		private Context context;

		AutoConnectTask(Context context) {
			this.context = context;
		}

		/**
		 * 运行在UI线程中，在调用doInBackground()之前执行
		 */
		@Override
		protected void onPreExecute() {
			Toast.makeText(context, "开始自动连接设备", Toast.LENGTH_SHORT).show();
			progressAutoConnect.setVisibility(ProgressBar.VISIBLE);
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
					if (connectDevice(device.getAddress())) {
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
			if (result) {
				displayLongToast("设备已自动连接成功！");
			} else {
				displayLongToast("无可用设备！请手动选择设备进行连接");
				selectDevice(false);
			}
			progressAutoConnect.setVisibility(ProgressBar.GONE);
			autoConnTask = null; // 执行结束后销毁当前任务
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
	 * 位置监听器
	 * 
	 * @author Administrator
	 *
	 */
	class MyLocationListener implements LocationListener {

		@Override
		public void onLocationChanged(Location location) {
			// 当GPS定位信息发生改变时，更新位置
			updateView(location);
			Log.d(MY_TAG, "GPS changed:" + location.getLongitude() + "," + location.getLatitude());
		}

		@Override
		public void onProviderDisabled(String provider) {
			updateView(null);
		}

		@Override
		public void onProviderEnabled(String provider) {
			// 当GPS LocationProvider可用时，更新位置
			updateView(locationManager.getLastKnownLocation(provider));
		}

		@Override
		public void onStatusChanged(String provider, int status, Bundle extras) {

		}
	}

	/**
	 * GPS信息类
	 * 
	 * @author Administrator
	 *
	 */
	class GPSInfo {
		private static final String SPLIT_SYMBOL = ":";

		private double lon;
		private double lat;
		private String apartment;
		private String address;

		public double getLon() {
			return lon;
		}

		public void setLon(double lon) {
			this.lon = lon;
		}

		public double getLat() {
			return lat;
		}

		public void setLat(double lat) {
			this.lat = lat;
		}

		public String getApartment() {
			return apartment;
		}

		public void setApartment(String apartment) {
			this.apartment = apartment;
		}

		public String getAddress() {
			return address;
		}

		public void setAddress(String address) {
			this.address = address;
		}

		@Override
		public String toString() {
			StringBuffer sb = new StringBuffer();
			sb.append("GPS");
			sb.append(SPLIT_SYMBOL);
			sb.append(lon);
			sb.append(SPLIT_SYMBOL);
			sb.append(lat);
			sb.append(SPLIT_SYMBOL);
			sb.append(apartment);
			sb.append(SPLIT_SYMBOL);
			sb.append(address);
			return sb.toString();
		}
	}
}
