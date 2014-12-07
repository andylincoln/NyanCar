/*********************************************************************/
/* Copyright (c) 2014 TOYOTA MOTOR CORPORATION. All rights reserved. */
/*********************************************************************/

package com.nyancar.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.drawable.AnimationDrawable;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MenuInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.nyancar.app.util.Utility;
import com.nyancar.app.util.Utility.*;

import java.io.File;
import java.nio.ByteBuffer;
import java.util.Timer;
import java.util.TimerTask;

public class MainActivity extends Activity implements ICommNotify {

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

	private final String _tag = "MainActivity";
	/* interval for sending vehicle signal request (milliseconds) */
	private final int TIMER_INTERVAL = 100;
	private final int ENGINE_REVOLUTION_SPEED_ID = 0x0C;
	private ByteBuffer _buf = null;


    File file;
    private ImageView imageview;
    private ImageView imagecover;
    private GifAnimationDrawable big;


    private SharedPreferences mSharedPreferences;
    SharedPreferences.OnSharedPreferenceChangeListener listener = new SharedPreferences.OnSharedPreferenceChangeListener() {
        @Override
        public void onSharedPreferenceChanged(final SharedPreferences sharedPreferences, final String key) {
            if (key.equals(getString(R.string.pref_high_score))) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        _tvDataLabel.setText(String.format("%d",sharedPreferences.getLong(key, 0)));
                    }
                });
            }
        }
    };


    private MediaPlayer mediaPlayer;
    private Handler durationHandler = new Handler();
    AudioManager audioManager;

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater=getMenuInflater();
        inflater.inflate(R.menu.main, menu);
        return super.onCreateOptionsMenu(menu);
    }



    @Override
    public boolean onOptionsItemSelected(MenuItem item) {

        switch (item.getItemId()) {
            case R.id.button_connect:
                if (_comm.isCommunication()){
                    return true;
                }
				/* Open the session */
                if (!_comm.openSession(_strDevAddress)){
                    showAlertDialog("OpenSession Failed");
                };
                break;

            case R.id.button_disconnect:
                stopTimer();
				/* Close the session */
                _comm.closeSession();
                break;

            case R.id.button_select:
                Intent intent = new Intent(MainActivity.this,DeviceListActivity.class);
                startActivityForResult(intent, REQUEST_BTDEVICE_SELECT);
                break;

        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        mSharedPreferences = getSharedPreferences("com.nyancar.app", MODE_PRIVATE);

        /* Create the Communication class */
        _comm = new Communication();
        /* Set the Notification interface */
        _comm.setICommNotify(this);

        _tvDataLabel = (TextView)findViewById(R.id.textView_signal);
        _tvDataLabel.setText(String.format("%d", mSharedPreferences.getLong(getString(R.string.pref_high_score), 0)));

        audioManager = (AudioManager)getSystemService(Context.AUDIO_SERVICE);
        file = new File("res/raw/nyangif.gif");

        try {
            AnimationDrawable drawable = new GifAnimationDrawable(file);
        } catch (Exception e){
            Log.e("DRAWABLE BAD>?", e.toString());
        }

        imageview = (ImageView)findViewById(R.id.imageView);
        imagecover = (ImageView)findViewById(R.id.imageCover);

        // and add the GifAnimationDrawable
        try{
            android.util.Log.v("GifAnimationDrawable", "===>One");
            android.util.Log.v("GifAnimationDrawable", "===>Two");
            android.util.Log.v("GifAnimationDrawable", "===>Three");
            big = new GifAnimationDrawable(getResources().openRawResource(R.raw.nyangif));
            //big.setOneShot(true);
            android.util.Log.v("GifAnimationDrawable", "===>Four");
            imageview.setImageDrawable(big);

        }catch(Exception e){
            Log.e("DRAWABLE BAD?>?", e.toString());

        }


        initializeViews();


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

    public void initializeViews() {
        try {
            mediaPlayer = MediaPlayer.create(this, R.raw.nyancat);
            mediaPlayer.setLooping(true);
        } catch (Exception e) {
            System.out.println("Exception starting player: " + e);
        }
    }

    public void play() {
        mediaPlayer.start();
    }

    @Override
    protected void onResume() {
        super.onResume();
        mSharedPreferences.registerOnSharedPreferenceChangeListener(listener);
    }

    @Override
    protected void onStop() {
        super.onStop();
        mSharedPreferences.unregisterOnSharedPreferenceChangeListener(listener);
    }

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
		if (Utility.isCarInfoGetFrame(rcvData) == true && len >= 8){
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
				if (signalID == ENGINE_REVOLUTION_SPEED_ID){
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
				updateContents(strData);
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
            play();

            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    imagecover.setVisibility(View.INVISIBLE);
                }
            });
		}
		else if (nState == Communication.STATE_CONNECT_FAILED){
			/* connect failed */
			strState = "CONNECT_FAILED";
            mediaPlayer.pause();

            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    imagecover.setVisibility(View.VISIBLE);
                }
            });		}
		else if (nState == Communication.STATE_DISCONNECTED){
			/* disconnected */
			_buf = null;
			strState = "DISCONNECTED";
            mediaPlayer.pause();
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    imagecover.setVisibility(View.VISIBLE);
                }
            });
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

	Handler _handler = new Handler() {
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
	
	private void updateContents(final String strData){

        _handler.post(new Runnable(){
			@Override
			public void run() {
                int vol, maxVol;
                double x, max = 9000.0, pct, dec;

                maxVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
                x = Double.parseDouble(strData);
                dec = x/max;
                pct = dec * maxVol;
                vol = (int)pct;
                //System.out.println("VOLUME: " + vol + " PCT: " + pct + " x: " + x + " dec: " + dec);
                audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, vol, 0);

                long highScore = mSharedPreferences.getLong(getString(R.string.pref_high_score), 0);
                Log.v(_tag, String.format("High score: %d", highScore));
                long value = Long.parseLong(strData);
                if (value > highScore) {
                    mSharedPreferences.edit().putLong(getString(R.string.pref_high_score), value).commit();
                    Log.v(_tag, String.format("High score: %d", value));
                    _tvDataLabel.setText(strData);
                }
			}
		});

	}
	
	private void startTimer(int timerCount){
		stopTimer();
		_timer = new Timer(false);
		_timerTask = new TimerTask() {
			public void run(){
				/* Send the message of vehicle signal request */
				_comm.writeData(Utility.createRequest(Utility.STATUS_ENGINE_REVOLUTION_SPEED));
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
		int calcCrc = Utility.calcCRC(tmps, 1, len - 4);
		if (crc != calcCrc){
			Log.d(_tag,"CRC ERROR");
			return false;
		}
		return true;
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
				Toast toast = Toast.makeText(MainActivity.this, strToast, Toast.LENGTH_SHORT);
				toast.show();
			}
		});
	}
	
}