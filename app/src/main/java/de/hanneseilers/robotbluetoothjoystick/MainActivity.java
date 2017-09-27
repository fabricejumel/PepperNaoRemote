package de.hanneseilers.robotbluetoothjoystick;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import de.hanneseilers.robotbluetoothjoystick.R;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.CompoundButton.OnCheckedChangeListener;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import com.aldebaran.qi.CallError;
import com.aldebaran.qi.EmbeddedTools;
import com.aldebaran.qi.Session;
import com.aldebaran.qi.helper.proxies.ALMotion;

/**
 * An example full-screen activity that shows and hides the system UI (i.e.
 * status bar and navigation/system bar) with user interaction.
 */
public class MainActivity extends Activity implements
	OnClickListener,
	OnCheckedChangeListener,
	SeekBar.OnSeekBarChangeListener,
	SensorEventListener {
	
	private static final String UUID_SERIAL = "00001101-0000-1000-8000-00805f9b34fb";
	private static final String TAG = MainActivity.class.getName();
	
	private static final float ORIENTATION_PITCH_CHANGE_ANGLE = 15;
	private static final float ORIENTATION_ROLL_CHANGE_ANGLE = 15;
	
	private static final String REQUEST_FORWARD = "F";
	private static final String REQUEST_BACKWARD = "B";
	private static final String REQUEST_LEFT = "L";
	private static final String REQUEST_RIGHT ="R";
	private static final String REQUEST_STOP = "S";
	private static final String REQUEST_CONNECT = "C";
	private static final String REQUEST_DISCONNECT = "D";
	
	private static final String RESPONSE_CONNECTED = "connected";
	private static final String RESPONSE_DISCONNECTED = "disconnected";
	
	private int sensorLeftColor = android.R.color.transparent;
	private int sensorRightColor = android.R.color.transparent;
	
	/**
	 * Views
	 */
	private View contentView;
	private ImageButton btnUp;
	private ImageButton btnDown;
	private ImageButton btnLeft;
	private ImageButton btnRight;
	private ImageButton btnStop;
	private Button btnConnect;
	private CheckBox chkDeviceUseOrientation;
	private EditText txtDeviceIP;
	private TextView txtSensorLeft;
	private TextView txtSensorRight;
	private LinearLayout controler;
	private View devices;
	private TextView txtInstruction;
	private SeekBar skbSpeed;
	
	/**
	 * Orientation Stuff
	 */
	private float []mLastMagFields;
	private float []mLastAccels;
	private float[] mRotationMatrix = new float[16];
	private float[] mOrientation = new float[4];
	private float mPitchOffset = 0.0f;
	private float mRollOffset = 0.0f;
	private float mPitch = 0.0f;
	private float mRoll = 0.0f;

    // NAOqi Stuff
	private Session naoqiSession = null;
    private ALMotion alMotion = null;
	private String lastCommand = null;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		requestWindowFeature(Window.FEATURE_NO_TITLE);
		getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
	            WindowManager.LayoutParams.FLAG_FULLSCREEN);
		setContentView(R.layout.activity_main);		

		contentView = findViewById(R.id.content_layout);
		devices = findViewById(R.id.devices);
		btnConnect = (Button) findViewById(R.id.btnConnect);
		btnUp = (ImageButton) findViewById(R.id.btnUp);
		btnDown = (ImageButton) findViewById(R.id.btnDown);
		btnLeft = (ImageButton) findViewById(R.id.btnLeft);
		btnRight = (ImageButton) findViewById(R.id.btnRight);
		btnStop = (ImageButton) findViewById(R.id.btnStop);
		chkDeviceUseOrientation = (CheckBox) findViewById(R.id.chkDeviceUseOrientation);
		txtDeviceIP = (EditText) findViewById(R.id.txtDeviceIP);
		txtSensorLeft = (TextView) findViewById(R.id.txtSensorLeft);
		txtSensorRight = (TextView) findViewById(R.id.txtSensorRight);
		controler = (LinearLayout) findViewById(R.id.controler);
		txtInstruction = (TextView) findViewById(R.id.txtInstruction);
		skbSpeed = (SeekBar) findViewById(R.id.skbSpeed);
		
		// set onclick listener
		contentView.setOnClickListener(this);
		btnConnect.setOnClickListener(this);
		btnUp.setOnClickListener(this);
		btnDown.setOnClickListener(this);
		btnLeft.setOnClickListener(this);
		btnRight.setOnClickListener(this);
		btnStop.setOnClickListener(this);
		
		// set checkbox state changed listener
		chkDeviceUseOrientation.setOnCheckedChangeListener(this);

		// set seekbar change listener
		skbSpeed.setOnSeekBarChangeListener(this);
		
		// register sensor listener
		registerSensorListener();

        // Load embedded tools
        EmbeddedTools ebt = new EmbeddedTools();
        File cacheDir = getApplicationContext().getCacheDir();
        ebt.overrideTempDirectory(cacheDir);
        ebt.loadEmbeddedLibraries();
	}
	
	@Override
    protected void onDestroy() {
		// check if device connected
		if( isConnected() ){
			disconnect();
		}
    	super.onDestroy();
    }
	
	/**
	 * Registers listeners for accelerometer and magnometer sensor
	 */
	private void registerSensorListener() {
		SensorManager sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
		sensorManager.registerListener(this, sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD), SensorManager.SENSOR_DELAY_NORMAL);
		sensorManager.registerListener(this, sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER), SensorManager.SENSOR_DELAY_NORMAL);
    }
	/**
	 * @return	{@code true} if connected to {@link BluetoothDevice}, {@code false} otwherwise.
	 */
	private boolean isConnected() {
		return naoqiSession != null ? naoqiSession.isConnected() : false;
	}
	
	/**
	 * Connects to a robot.
	 * @param aDeviceIP	IP of robot
	 */
	private void connect(String aDeviceIP){
		if(aDeviceIP != null){

			if( isConnected() )
				disconnect();

			try {

                Toast.makeText( this, R.string.device_connecting, Toast.LENGTH_SHORT ).show();
				String host = "tcp://" + aDeviceIP + ":9559";
				Log.i(TAG, host);

				naoqiSession = new Session();
				naoqiSession.connect(host).sync(500, TimeUnit.MILLISECONDS);

                alMotion = new ALMotion(naoqiSession);
                Toast.makeText( this, R.string.device_connected, Toast.LENGTH_LONG ).show();
				alMotion.wakeUp();

                // Show ui
                devices.setVisibility(View.GONE);
                contentView.setVisibility(View.VISIBLE);


			} catch(Exception e){
				e.printStackTrace();
				Log.e(TAG, "error", e);
                Toast.makeText( this, R.string.device_disconnected, Toast.LENGTH_SHORT ).show();
			}
		}
	}
	
	/**
	 * Disconnect from device
	 */
	private void disconnect(){
		if( naoqiSession != null ) {
			try {

				alMotion.stopMove();

			} catch (CallError callError) {
				callError.printStackTrace();
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
			naoqiSession.close();
		}

		naoqiSession = null;
		Toast.makeText( this, R.string.device_disconnected, Toast.LENGTH_SHORT ).show();
	}
	
	/**
	 * Sends {@link String} data to connected {@link BluetoothDevice}.
	 * @param aData		{@link String} data.
	 * @return			{@code true} if successful send, {@code false} otherwise.
	 */
	private boolean sendData(String aData){
		if( isConnected() && alMotion != null ){
            Log.i(TAG, aData);
			lastCommand = aData;

			try {

				float vSpeed = skbSpeed.getProgress()/100.0f;

				if( aData.contains(REQUEST_LEFT) )
					alMotion.move( 0.0f, 0.0f, vSpeed );
				else if( aData.contains(REQUEST_RIGHT) )
					alMotion.move( 0.0f, 0.0f, -vSpeed );
				else if( aData.contains(REQUEST_FORWARD) )
					alMotion.move( vSpeed, 0.0f, 0.0f );
				else if( aData.contains(REQUEST_BACKWARD) )
					alMotion.move( -vSpeed, 0.0f, 0.0f );
				else
					alMotion.move( 0.0f, 0.0f, 0.0f );

			} catch (CallError callError) {
				callError.printStackTrace();
			} catch (InterruptedException e) {
				e.printStackTrace();
			}

			return true;
		}
		return false;
	}
	
	/**
	 * Set accelerometer data
	 * @param event	{@link SensorEvent}
	 */
	private void accel(SensorEvent event) {
        if (mLastAccels == null) {
            mLastAccels = new float[3];
        }
 
        System.arraycopy(event.values, 0, mLastAccels, 0, 3);
 
        /*if (m_lastMagFields != null) {
            computeOrientation();
        }*/
    }
 
	/**
	 * Set magnometer data
	 * @param event	{@link SensorEvent}
	 */
    private void mag(SensorEvent event) {
        if (mLastMagFields == null) {
            mLastMagFields = new float[3];
        }
 
        System.arraycopy(event.values, 0, mLastMagFields, 0, 3);
 
        if (mLastAccels != null) {
            computeOrientation();
        }
    }
    
    /**
     * Computes orientation of device
     */
    private void computeOrientation() {
        if (SensorManager.getRotationMatrix(mRotationMatrix, null, mLastAccels, mLastMagFields)) {
            SensorManager.getOrientation(mRotationMatrix, mOrientation); 
            /* 1 radian = 57.2957795 degrees */
            /* [0] : yaw, rotation around z axis
             * [1] : pitch, rotation around x axis
             * [2] : roll, rotation around y axis */
            mPitch = mOrientation[1] * 57.2957795f;
            mRoll = mOrientation[2] * 57.2957795f;
            
            sendCommandOnDeviceOrientation( mPitch - mPitchOffset, mRoll - mRollOffset );
        }
    }
    
    /**
     * Sends a drive command depending on the current device orientation
     * @param aPitch	{@link Float} device pitch in degree
     * @param aRoll		{@link Float} device roll in degree
     */
    private synchronized void sendCommandOnDeviceOrientation(float aPitch, float aRoll){
    	if( chkDeviceUseOrientation.isChecked() ){
    		
    		if( Math.abs(aPitch) > ORIENTATION_PITCH_CHANGE_ANGLE ){
    			
    			// check if to drive left or right
    			if( aPitch > ORIENTATION_PITCH_CHANGE_ANGLE ){
    				sendData( REQUEST_LEFT );
    			} else {
    				sendData( REQUEST_RIGHT );
    			}
    			
    		} else if( Math.abs(aRoll) > ORIENTATION_ROLL_CHANGE_ANGLE ){
    			
    			// check if to drive forward or backward
    			if( aRoll > ORIENTATION_ROLL_CHANGE_ANGLE ){
    				sendData( REQUEST_FORWARD );
    			} else {
    				sendData( REQUEST_BACKWARD );
    			}
    			
    		} else {
    			
    			// stop
    			sendData( REQUEST_STOP );
    			
    		}
    		
    	}
    }

	@Override
	public void onClick(View v) {
		if( v == btnConnect ){
			if( isConnected() )
				disconnect();
			this.connect( txtDeviceIP.getText().toString() );
		} else if( v == btnUp ){
			sendData( REQUEST_FORWARD );
		} else if( v == btnDown ){
			sendData( REQUEST_BACKWARD );
		} else if( v == btnLeft ){
			sendData( REQUEST_LEFT );
		} else if( v == btnRight ){
			sendData( REQUEST_RIGHT );
		} else if( v == btnStop ){
			sendData( REQUEST_STOP );
		}
	}

	@Override
	public void onSensorChanged(SensorEvent event) {
		 if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
	            accel(event);
	        }
	        if (event.sensor.getType() == Sensor.TYPE_MAGNETIC_FIELD) {
	            mag(event);
	        }
	}

	@Override
	public void onAccuracyChanged(Sensor sensor, int accuracy) {}
	
	@Override
	public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
		if (isChecked) {
			mPitchOffset = mPitch;
			mRollOffset = mRoll;
		}
	}

	@Override
	public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
		sendData(lastCommand);
	}

	@Override
	public void onStartTrackingTouch(SeekBar seekBar) {
		sendData(lastCommand);
	}

	@Override
	public void onStopTrackingTouch(SeekBar seekBar) {
		sendData(lastCommand);
	}
}
