/*********************************************************************/
/* Copyright (c) 2014 TOYOTA MOTOR CORPORATION. All rights reserved. */
/*********************************************************************/

package com.example.sample;

import java.nio.ByteBuffer;
import java.util.Timer;
import java.util.TimerTask;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

public class SampleActivity extends Activity implements ICommNotify{
	private static final int REQUEST_BTDEVICE_SELECT = 1;
	private Button _btnConnect;
	private Button _btnDisconnect;
	private Button _btnSelectDevice;
	private TextView _tvDataLabel;

	/* declaration of Communication class */
	private Communication _comm;

	private Timer _timer;
	private TimerTask _timerTask;

	/* variable of the CAN-Gateway ECU Address */
	private String _strDevAddress = "";

	private final String _tag = "SampleActivity";
	/* interval for sending vehicle signal request (milliseconds) */
	private final int TIMER_INTERVAL = 100;
	private final int ENGINE_REVOLUTION_SPEED_ID = 0x0C;
	private ByteBuffer _buf = null;
	
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);

        /* Create the Communication class */
        _comm = new Communication();
        /* Set the Notification interface */
        _comm.setICommNotify(this);

        _tvDataLabel = (TextView)findViewById(R.id.textView_signal);
        _btnConnect = (Button)findViewById(R.id.button_connect);
        _btnDisconnect = (Button)findViewById(R.id.button_disconnect);
        _btnSelectDevice = (Button)findViewById(R.id.button_select);
        _btnConnect.setOnClickListener(_onClickListener);
        _btnDisconnect.setOnClickListener(_onClickListener);
        _btnSelectDevice.setOnClickListener(_onClickListener);
    }

	@Override
	public void onDestroy() {
		super.onDestroy();
	}
	
	@Override
	public void finish() {
		stopTimer();
        /* Set the Notification interface */
        _comm.setICommNotify(null);
		/* Close the session */
		_comm.closeSession();

		super.finish();
	}	
    
	OnClickListener _onClickListener = new OnClickListener(){
		@Override
		public void onClick(View v) {
			Button btn = (Button)v;
			if (btn == _btnConnect){
				if (_comm.isCommunication()){
					return;
				}
				/* Open the session */
				if (!_comm.openSession(_strDevAddress)){
					showAlertDialog("OpenSession Failed");
				};
			}else if(btn == _btnDisconnect){	
				stopTimer();
				/* Close the session */
				_comm.closeSession();
			}else if(btn == _btnSelectDevice){	
				Intent intent = new Intent(SampleActivity.this,DeviceListActivity.class);
				startActivityForResult(intent, REQUEST_BTDEVICE_SELECT);
			}
		}
	};

	@Override
	public void notifyReceiveData(Object data) {
		Log.d(_tag,String.format("RECEIVE"));
		ByteBuffer rcvData = (ByteBuffer)data;

		/* Combine received messages */
		if(isCombineFrame(rcvData) == true){
			/* all data received */
			if (isFrameCheck(_buf) != true)
			{
				/* frame error */
				_buf.clear();
				_buf = null;
				return;
			}
			else
			{
				rcvData = _buf;
				_buf.clear();
				_buf = null;
			}
		}
		else
		{
			/* all data not received */
			return;
		}

		byte tmps[] = rcvData.array();
		int len = rcvData.limit();
		/* Analyze the message */
		if (isCarInfoGetFrame(rcvData) == true && len >= 8){
			/* message of vehicle signal request */
			String strData = "";
			/* Number of signals */
			int dataCount = (int)tmps[4] & 0xff;
			int index = 5;
			/* Vehicle signal */
			for (int i = 0 ; i < dataCount ; i++){
				int tmpData = toUint16Value(tmps, index);
				long value   = toUint32Value(tmps, index + 2); 
				int signalID = (tmpData & 0x0fff);
				int stat 	 = ((tmpData >> 12) & 0x0f);
				if (ENGINE_REVOLUTION_SPEED_ID == signalID){
					/* Engine Revolution Speed = 14bit */
					value = value & 0x00003FFF;
					/* Resolution of Engine Revolution Speed = "1" */
					value = value * 1;
					strData = String.valueOf(value);
				}
				Log.d(_tag,String.format("SIGNALID = %d, SIGNALSTAT = %d, VALUE = %d", signalID,stat,value));
				index += 6;
			}
			if (strData.length() > 0){
				updateContetnts(strData);
			}
		}else{
			Log.d(_tag,"UNKNOWN FRAME");
		}
	}

	/* Notify Bluetooth state of change */
	@Override
	public void notifyBluetoothState(int nState) {
		String strState;
		if (nState == Communication.STATE_NONE){
			/* non status */
			strState = "NOTE";
		}
		else if (nState == Communication.STATE_CONNECTING){
			/* connecting */
			strState = "CONNECTING";
		}
		else if (nState == Communication.STATE_CONNECTED){
			/* connected */
			strState = "CONNECTED";
		}
		else if (nState == Communication.STATE_CONNECT_FAILED){
			/* connect failed */
			strState = "CONNECT_FAILED";
		}
		else if (nState == Communication.STATE_DISCONNECTED){
			/* disconnected */
			_buf = null;
			strState = "DISCONNECTED";
		}
		else{
			/* unknown */
			strState = "UNKNOWN";
		}
		dspToast(strState);
		
		Log.d(_tag,String.format("STATE = %s",strState));
		if(nState == Communication.STATE_CONNECTED){
			/* delay time                                            */
			/* (Connect to the CAN-Gateway -> Send the first message */
			_handler.sendMessageDelayed(_handler.obtainMessage(), 2000);
		}
	}

	Handler _handler = new Handler(){
		@Override
		public void handleMessage(Message msg) {
			/* Send the message of vehicle signal request */
			startTimer(TIMER_INTERVAL);
		}
	};
	
	public void onActivityResult(int requestCode, int resultCode, Intent data) {
		if (requestCode == REQUEST_BTDEVICE_SELECT){
			if (resultCode == Activity.RESULT_OK) {
				_strDevAddress = data.getExtras().getString(DeviceListActivity.EXTRA_DEVICE_ADDRESS);
			}
		}
	}	
	
	private void updateContetnts(final String strData){
		_handler.post(new Runnable(){
			@Override
			public void run() {
				_tvDataLabel.setText(strData);
			}
		});
	}
	
	/* Create the message of vehicle signal request */
	private ByteBuffer createCarInfoGetFrame(){
		/* e.g.) request of Engine Revolution Speed */
		byte[] buf = {0x7e,0x00,0x00,0x01,0x01,0x00,0x00,0x00,0x00,0x7f};
		int length = buf.length;
		/* Set the message length */
		buf[1] = (byte)(((length - 6) >> 8) & 0xff);
		buf[2] = (byte)((length - 6) & 0xff);
		/* Set the request signal ID */
		buf[6] = (byte)(ENGINE_REVOLUTION_SPEED_ID);
		/* Calculate and set the CRC */
		int crc = calcCRC(buf, 1, buf.length - 4);
		/* Convert endian from little to big */
		buf[length - 3] = (byte)((crc >> 8) & 0xff);
		buf[length - 2] = (byte)(crc & 0xff);
		return ByteBuffer.wrap(buf);
	}
	
	private void startTimer(int timerCount){
		stopTimer();
		_timer = new Timer(false);
		_timerTask = new TimerTask() {
			public void run(){
				/* Send the message of vehicle signal request */
				_comm.writeData(createCarInfoGetFrame());
			}
		};
		_timer.schedule(_timerTask,0,timerCount);
	}
	
	private void stopTimer(){
		if (_timer != null){
			_timer.cancel();
			_timer = null;
		}
	}
	
	private void showAlertDialog(String strMessage){
		AlertDialog.Builder dlg = new AlertDialog.Builder(this);
		dlg.setTitle(strMessage);
		dlg.setPositiveButton("OK", new DialogInterface.OnClickListener() {
			@Override
			public void onClick(DialogInterface dialog, int which) {
				/* non-treated */
			}
		});
		dlg.show();
	}

	/* Combine received messages */
	public boolean isCombineFrame(ByteBuffer frame){
		frame.position(0);
		byte[] rcv = new byte[frame.limit()];
		frame.get(rcv, 0, frame.limit());

		/* Buffer for received message */
		if(_buf == null){
			_buf = ByteBuffer.allocate(rcv.length);
			_buf.put(rcv);
		}else{
			byte[] tmp = _buf.array();
			ByteBuffer newBuf = ByteBuffer.allocate(tmp.length + rcv.length);
			newBuf.put(tmp);
			newBuf.put(rcv);
			_buf = newBuf;
		}

		/* Check the message length */
		byte[] tmps = _buf.array();
		int len = _buf.limit();
		int dataLen = this.toUint16Value(tmps, 1);
		if ((dataLen + 6) > len){
			/* all data not received */
			return false;
		}
		else
		{
			/* all data received */
			return true;
		}
	}
	
	private boolean isFrameCheck(ByteBuffer frame){
		byte[] tmps = frame.array();
		int len = frame.limit();
		if(len < 3){
			Log.d(_tag,"FRAME LENGTH ERROR1");
			return false;
		}
		int dataLen = this.toUint16Value(tmps, 1);
		if ((dataLen + 6) != len){
			Log.d(_tag,"FRAME LENGTH ERROR2");
			return false;
		}
		if (tmps[0] != 0x7E){
			Log.d(_tag,"HEADER ERROR");
			return false;
		}
		if (tmps[len - 1] != 0x7F){
			Log.d(_tag,"FOOTER ERROR");
			return false;
		}
		if (tmps[3] != 0x11){
			Log.d(_tag,"FRAME TYPE ERROR");
			return false;
		}
		int crc = this.toUint16Value(tmps, len - 3);
		int calcCrc = this.calcCRC(tmps, 1, len - 4);
		if (crc != calcCrc){
			Log.d(_tag,"CRC ERROR");
			return false;
		}
		return true;
	}
		
	private boolean isCarInfoGetFrame(ByteBuffer frame){
		byte tmp = frame.get(3);
		if (tmp == 0x11){
			return true;
		}
		return false;
	}
	
    private int calcCRC(byte[] buffer, int index, int length) {
		int crcValue = 0x0000;
	    boolean flag;
	    boolean c15;
	    for( int i = 0; i < length; i++ ) {
	        for(int j = 0; j < 8; j++){
	            flag = ( (buffer[i + index] >> (7 - j) ) & 0x0001)==1;
	            c15  = ((crcValue >> 15 & 1) == 1);
	            crcValue <<= 1;
	            if(c15 ^ flag){
	                crcValue ^= 0x1021;
	            }
	        }
	    }
	    crcValue ^= 0x0000;
	    crcValue &= 0x0000ffff;
	    return crcValue;
    } 	
		
    private int toUint16Value(byte[] buffer, int index) {
    	int value = 0;
    	value |= (buffer[index + 0] << 8) & 0x0000ff00;
    	value |= (buffer[index + 1] << 0) & 0x000000ff;
    	return value & 0xffff;
    }
    
    private long toUint32Value(byte[] buffer, int index) {
    	int value = 0;
    	value |= (buffer[index + 0] << 24) & 0xff000000;
    	value |= (buffer[index + 1] << 16) & 0x00ff0000;
    	value |= (buffer[index + 2] <<  8) & 0x0000ff00;
    	value |= (buffer[index + 3] <<  0) & 0x000000ff;
    	return value & 0xffffffffL;
    }
    
	private void dspToast(final String strToast){
		_handler.post(new Runnable(){
			@Override
			public void run() {
				Toast toast = Toast.makeText(SampleActivity.this, strToast, Toast.LENGTH_SHORT);
				toast.show();
			}
		});
	}
	
}