package com.warmtel.bluetooth.service;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.UUID;

import android.app.IntentService;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.content.Intent;
import android.os.Binder;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.util.Log;
import android.widget.Toast;

import com.warmtel.bluetooth.R;
import com.warmtel.bluetooth.util.Constances;
import com.warmtel.bluetooth.util.Logs;

public class BlueClientHandlerService extends IntentService {
	private static final String PROTOCOL_SCHEME_RFCOMM = "btspp";
	public static final String UUIDS = "00001101-0000-1000-8000-00805F9B34FB";
	private BluetoothServerSocket mBlueServerSocket = null;
	private BluetoothSocket mBlueSocket = null;
	private BluetoothDevice mBlueDevice = null;
	private ReadThread mReadThread = null;
	private Handler mMessageHandler;
	private BluetoothAdapter mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
	private BluetoothSerivceiBinder mBluetoothSerivceiBinder = new BluetoothSerivceiBinder();
	private Context mContext;

	public static void actionBlueClientHandlerService(Context context,int mServerOrClientType,String mBluetoothAddress){
		Intent intent = new Intent(context, BlueClientService.class);
		intent.putExtra(Constances.SERVER_CLIENT_TYPE, mServerOrClientType);
		intent.putExtra(Constances.BLUETOOTH_ADDRESS, mBluetoothAddress);
		context.startService(intent);
	}
	public static void actionBlueClientHandlerService(Context context,int mServerOrClientType){
		Intent intent = new Intent(context, BlueClientService.class);
		intent.putExtra(Constances.SERVER_CLIENT_TYPE, mServerOrClientType);
		context.startService(intent);
	}
	public interface IBluetoothSerivice {
		public void sendMessage(String msg);
		public void setHandler(Handler handler);
	}

	class BluetoothSerivceiBinder extends Binder implements IBluetoothSerivice {

		@Override
		public void setHandler(Handler handler) {
			mMessageHandler = handler;
		}

		@Override
		public void sendMessage(String msg) {
			if (mBlueSocket == null) {
				Toast.makeText(mContext,getString(R.string.chat_no_connection_message),Toast.LENGTH_SHORT).show();
				return;
			}
			try {
				OutputStream os = mBlueSocket.getOutputStream();
				os.write(msg.getBytes());
			} catch (IOException e) {
				e.printStackTrace();
			}

			setStateMsg(msg, 0);
		}

	}
	
	public BlueClientHandlerService() {
		super("BlueClientHandlerService");
	}

	@Override
	public void onCreate() {
		super.onCreate();
		mContext = this;
		Logs.v("onCreate >>>>>>>>>>>>>>");
	}

	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		Logs.v("onStartCommand >>>>>>>>>>>>>>");
		return super.onStartCommand(intent, flags, startId);
	}

	@Override
	public IBinder onBind(Intent intent) {
		return mBluetoothSerivceiBinder;
	}

	@Override
	public void onDestroy() {
		super.onDestroy();
		shutClient();
		shutServer();
	}

	@Override
	protected void onHandleIntent(Intent intent) {
		int serverOrClientType = intent != null ? intent.getIntExtra(Constances.SERVER_CLIENT_TYPE, -1) : -1;
		String blueToothAddress = intent != null ? intent.getStringExtra(Constances.BLUETOOTH_ADDRESS) : "";
		Logs.v("onHandleIntent mServerOrCleintType :"+serverOrClientType + " mBlueToothAddress :"+blueToothAddress);
		
		switch (serverOrClientType) {
		case Constances.CLIENT_TYPE:

			mBlueDevice = mBluetoothAdapter.getRemoteDevice(blueToothAddress);
			try {
				// 创建一个Socket连接：只需要服务器在注册时的UUID号
				int sdk = Integer.parseInt(Build.VERSION.SDK);
				if (sdk >= 10) {
					mBlueSocket = mBlueDevice.createInsecureRfcommSocketToServiceRecord(UUID.fromString(UUIDS));
				} else {
					mBlueSocket = mBlueDevice.createRfcommSocketToServiceRecord(UUID.fromString(UUIDS));
				}

				setStateMsg(String.format(getString(R.string.chat_connectioning_message,blueToothAddress)), 0);

				mBlueSocket.connect();

				setStateMsg(getString(R.string.chat_connectioned_sendok_message), 0);

				mReadThread = new ReadThread();
				mReadThread.start();

			} catch (IOException e) {
				e.printStackTrace();
				setStateMsg(getString(R.string.chat_connection_error_message),0);
			}
			break;
		case Constances.SERVER_TYPE:
			try {
				/*
				 * 创建一个蓝牙服务器 参数分别：服务器名称、UUID
				 */
				mBlueServerSocket = mBluetoothAdapter
						.listenUsingRfcommWithServiceRecord(
								PROTOCOL_SCHEME_RFCOMM, UUID.fromString(UUIDS));

				setStateMsg(getString(R.string.chat_please_client_connectioning_message),0);
				/* 接受客户端的连接请求 */
				mBlueSocket = mBlueServerSocket.accept();

				setStateMsg(getString(R.string.chat_client_connection_success_message),0);

				mReadThread = new ReadThread();
				mReadThread.start();
			} catch (IOException e) {
				e.printStackTrace();
			}
			break;
		case Constances.SHUP_CLIENT_TYPE:
			shutClient();
			break;
		case Constances.SHUP_SERVER_TYPE:
			shutServer();
			break;
		}
	}
	
	private void shutClient(){
		if (mReadThread != null) {
			mReadThread.interrupt();
			mReadThread = null;
		}
		if (mBlueSocket != null) {
			try {
				mBlueSocket.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
			mBlueSocket = null;
		}
	}
	private void shutServer(){
		if (mReadThread != null) {
			mReadThread.interrupt();
			mReadThread = null;
		}
		try {
			if (mBlueSocket != null) {
				mBlueSocket.close();
				mBlueSocket = null;
			}
			if (mBlueServerSocket != null) {
				mBlueServerSocket.close();
				mBlueServerSocket = null;
			}
		} catch (IOException e) {
			e.printStackTrace();
			Log.e("server", "mserverSocket.close()", e);
		}
	}
	
	// 接受数据
	class ReadThread extends Thread {
		public void run() {

			byte[] buffer = new byte[1024];
			int bytes;
			InputStream mmInStream = null;

			try {
				mmInStream = mBlueSocket.getInputStream();
			} catch (IOException e1) {
				e1.printStackTrace();
			}
			while (true) {
				try {
					// Read from the InputStream
					if ((bytes = mmInStream.read(buffer)) > 0) {
						byte[] buf_data = new byte[bytes];
						for (int i = 0; i < bytes; i++) {
							buf_data[i] = buffer[i];
						}
						String s = new String(buf_data);
						setStateMsg(s, 1);
					}
				} catch (IOException e) {
					try {
						mmInStream.close();
					} catch (IOException e1) {
						e1.printStackTrace();
					}
					break;
				}
			}
		}
	}
	private void setStateMsg(String obj, int what) {
		Message msg = Message.obtain();
		msg.obj = obj;
		msg.what = what;
		mMessageHandler.sendMessage(msg);
	}
}
