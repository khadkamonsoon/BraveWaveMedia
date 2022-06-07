    package com.example.brainwavemedia;

import androidx.appcompat.app.AppCompatActivity;

import android.bluetooth.BluetoothAdapter;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.webkit.URLUtil;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.VideoView;

import com.google.android.material.badge.BadgeUtils;
import com.neurosky.AlgoSdk.NskAlgoDataType;
import com.neurosky.AlgoSdk.NskAlgoSdk;
import com.neurosky.AlgoSdk.NskAlgoState;
import com.neurosky.AlgoSdk.NskAlgoType;
import com.neurosky.connection.ConnectionStates;
import com.neurosky.connection.DataType.MindDataType;
import com.neurosky.connection.TgStreamHandler;
import com.neurosky.connection.TgStreamReader;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Timer;
import java.util.TimerTask;


    public class MainActivity extends AppCompatActivity {
    private NskAlgoSdk nskAlgoSdk;
    private BluetoothAdapter bluetoothAdapter;
    private TgStreamReader tgStreamReader;

    private short raw_data[] = {0};
    private int raw_data_index= 0;

    private Button connectBtn;
    private VideoView videoView;
    private TextView connectionStatus;

    private int attention=0;

    private int eyeclosed =0 ;
    private String videoList[] = {"https://www.learningcontainer.com/wp-content/uploads/2020/05/sample-mp4-file.mp4","https://file-examples-com.github.io/uploads/2017/04/file_example_MP4_480_1_5MG.mp4"};

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        try {
            // (1) Make sure that the device supports Bluetooth and Bluetooth is on
            bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
            if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled()) {
                Toast.makeText(
                        this,
                        "Please enable your Bluetooth and re-run this program !",
                        Toast.LENGTH_LONG).show();
                //finish();
            }
        } catch (Exception e) {
            e.printStackTrace();
            Log.e("TAG", "error:" + e.getMessage());
            return;
        }

        init();
    }

    @Override
    protected void onResume() {
        connect();
        super.onResume();
    }

    private void init() {
        nskAlgoSdk = new NskAlgoSdk();
        connectBtn = findViewById(R.id.connectBtn);
        videoView = findViewById(R.id.videoView);
        connectionStatus = findViewById(R.id.connectionStatus);
        connectBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
               receivedata();
            }
        });

        nskAlgoSdk.setOnStateChangeListener(new NskAlgoSdk.OnStateChangeListener() {
            @Override
            public void onStateChange(int state, int reason) {
                String stateStr = "";
                String reasonStr = "";
                for (NskAlgoState s : NskAlgoState.values()) {
                    if (s.value == state) {
                        stateStr = s.toString();
                    }
                }
                for (NskAlgoState r : NskAlgoState.values()) {
                    if (r.value == reason) {
                        reasonStr = r.toString();
                    }
                }
                Log.e("TAG", "NskAlgoSdkStateChangeListener: state: " + stateStr + ", reason: " + reasonStr);
                final String finalStateStr = stateStr + " | " + reasonStr;
                final int finalState = state;
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        // change UI elements here
                        if (finalState == NskAlgoState.NSK_ALGO_STATE_RUNNING.value || finalState == NskAlgoState.NSK_ALGO_STATE_COLLECTING_BASELINE_DATA.value) {
                            connectionStatus.setText("running");
                        } else if (finalState == NskAlgoState.NSK_ALGO_STATE_STOP.value) {
                            raw_data = null;
                            raw_data_index = 0;
                            connectionStatus.setText("Stopped");
                            if (tgStreamReader != null && tgStreamReader.isBTConnected()) {

                                // Prepare for connecting
                                tgStreamReader.stop();
                                tgStreamReader.close();
                            }

                            System.gc();
                        } else if (finalState == NskAlgoState.NSK_ALGO_STATE_PAUSE.value) {
                            connectionStatus.setText("paused");
                        } else if (finalState == NskAlgoState.NSK_ALGO_STATE_ANALYSING_BULK_DATA.value) {
                            connectionStatus.setText("analyzing");
                        } else if (finalState == NskAlgoState.NSK_ALGO_STATE_INITED.value || finalState == NskAlgoState.NSK_ALGO_STATE_UNINTIED.value) {
                            connectionStatus.setText("inited");
                        }
                    }
                });
            }
        });

        nskAlgoSdk.setOnAttAlgoIndexListener(new NskAlgoSdk.OnAttAlgoIndexListener() {
            @Override
            public void onAttAlgoIndex(int value) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        attention = value;
                        showToast("atention :"+attention,Toast.LENGTH_SHORT);
                        Log.e("TAG","attention : " +attention);
                    }
                });
            }
        });

        nskAlgoSdk.setOnEyeBlinkDetectionListener(new NskAlgoSdk.OnEyeBlinkDetectionListener() {
            @Override
            public void onEyeBlinkDetect(int strength) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        Timer timer = new Timer();

                        timer.schedule(new TimerTask() {
                            public void run() {
                                runOnUiThread(new Runnable() {
                                    @Override
                                    public void run() {
                                        if (eyeclosed == 4) {
                                            eyeclosed = 0;
                                            if(attention<40) {
                                                startMediaPlayer(2);
                                            }
                                        }else {
                                            eyeclosed ++;
                                        }
                                    }
                                });
                            }
                        }, 500);
                    }
                });
            }
        });



    }

    private void connect() {
        raw_data = new short[512];
        raw_data_index = 0;

        tgStreamReader = new TgStreamReader(bluetoothAdapter,callback);

        if(tgStreamReader != null && tgStreamReader.isBTConnected()){

            // Prepare for connecting
            tgStreamReader.stop();
            tgStreamReader.close();
        }

        // (4) Demo of  using connect() and start() to replace connectAndStart(),
        // please call start() when the state is changed to STATE_CONNECTED
        tgStreamReader.connect();
    }

    private void receivedata() {
        showToast("Runing",Toast.LENGTH_LONG);
        startMediaPlayer(0);
        int algoTypes = NskAlgoType.NSK_ALGO_TYPE_ATT.value;

        int ret = nskAlgoSdk.NskAlgoInit(algoTypes, getFilesDir().getAbsolutePath());
        if(ret == 0){
            showToast("Receiving data",Toast.LENGTH_LONG);
            nskAlgoSdk.NskAlgoStart(false);
        }

    }
    private void startMediaPlayer(int videoNumber) {

        if(videoView.isPlaying()) {
            videoView.stopPlayback();
        }
        videoView.setVideoURI(getMedia(videoList[videoNumber]));
        videoView.start();
    }

    private Uri getMedia(String mediaName) {
        if (URLUtil.isValidUrl(mediaName)) {
            // media name is an external URL
            return Uri.parse(mediaName);
        } else { // media name is a raw resource embedded in the app
            return Uri.parse("android.resource://" + getPackageName() +
                    "/raw/" + mediaName);
        }
    }


    private TgStreamHandler callback = new TgStreamHandler() {

        @Override
        public void onStatesChanged(int connectionStates) {
            // TODO Auto-generated method stub
            Log.d("TAG", "connectionStates change to: " + connectionStates);
            switch (connectionStates) {
                case ConnectionStates.STATE_CONNECTING:
                    showToast("Connecting",Toast.LENGTH_LONG);
                    // Do something when connecting
                    break;
                case ConnectionStates.STATE_CONNECTED:
                    // Do something when connected
                    tgStreamReader.start();
                    showToast("Connected", Toast.LENGTH_SHORT);
                    break;
                case ConnectionStates.STATE_WORKING:
                    // Do something when working

                    //(9) demo of recording raw data , stop() will call stopRecordRawData,
                    //or you can add a button to control it.
                    //You can change the save path by calling setRecordStreamFilePath(String filePath) before startRecordRawData
                    //tgStreamReader.startRecordRawData();

                    MainActivity.this.runOnUiThread(new Runnable() {
                        public void run() {
                            connectBtn.setVisibility(View.VISIBLE);
                            Log.e("TAG","connected");
                           //receivedata();

                        }

                    });

                    break;
                case ConnectionStates.STATE_GET_DATA_TIME_OUT:
                    // Do something when getting data timeout

                    //(9) demo of recording raw data, exception handling
                    //tgStreamReader.stopRecordRawData();

                    showToast("Get data time out!", Toast.LENGTH_SHORT);

                    if (tgStreamReader != null && tgStreamReader.isBTConnected()) {
                        tgStreamReader.stop();
                        tgStreamReader.close();
                    }

                    break;
                case ConnectionStates.STATE_STOPPED:
                    // Do something when stopped
                    // We have to call tgStreamReader.stop() and tgStreamReader.close() much more than
                    // tgStreamReader.connectAndstart(), because we have to prepare for that.

                    showToast("stopped",Toast.LENGTH_LONG);
                    break;
                case ConnectionStates.STATE_DISCONNECTED:
                    // Do something when disconnected
                    Log.e("TAG","disconnected");
                    connectBtn.setVisibility(View.INVISIBLE);
                    showToast("Disconnected",Toast.LENGTH_LONG);
                    if(videoView.isPlaying()) {
                        videoView.pause();
                    }
                    break;
                case ConnectionStates.STATE_ERROR:
                    // Do something when you get error message
                    showToast("error",Toast.LENGTH_LONG);
                    break;
                case ConnectionStates.STATE_FAILED:
                    // Do something when you get failed message
                    // It always happens when open the BluetoothSocket error or timeout
                    // Maybe the device is not working normal.
                    // Maybe you have to try again
                    Log.e("TAG","failed");
                    break;
            }
        }

        @Override
        public void onRecordFail(int flag) {
            // You can handle the record error message here
            Log.e("TAG","onRecordFail: " +flag);

        }

        @Override
        public void onChecksumFail(byte[] payload, int length, int checksum) {
            // You can handle the bad packets here.
        }

        @Override
        public void onDataReceived(int datatype, int data, Object obj) {
            // You can handle the received data here
            // You can feed the raw data to algo sdk here if necessary.
            //Log.i(TAG,"onDataReceived");
            switch (datatype) {
                case MindDataType.CODE_ATTENTION:
                    short attValue[] = {(short)data};
                    nskAlgoSdk.NskAlgoDataStream(NskAlgoDataType.NSK_ALGO_DATA_TYPE_ATT.value, attValue, 1);
                    break;
                case MindDataType.CODE_MEDITATION:
                    short medValue[] = {(short)data};
                    nskAlgoSdk.NskAlgoDataStream(NskAlgoDataType.NSK_ALGO_DATA_TYPE_MED.value, medValue, 1);
                    break;
                case MindDataType.CODE_POOR_SIGNAL:
                    short pqValue[] = {(short)data};
                    nskAlgoSdk.NskAlgoDataStream(NskAlgoDataType.NSK_ALGO_DATA_TYPE_PQ.value, pqValue, 1);
                    break;
                case MindDataType.CODE_RAW:
                    raw_data[raw_data_index++] = (short)data;
                    if (raw_data_index == 512) {
                        nskAlgoSdk.NskAlgoDataStream(NskAlgoDataType.NSK_ALGO_DATA_TYPE_EEG.value, raw_data, raw_data_index);
                        raw_data_index = 0;
                    }
                    break;
                default:
                    break;
            }
        }

    };

    public void showToast(final String msg, final int timeStyle) {
        MainActivity.this.runOnUiThread(new Runnable() {
            public void run() {
                Toast.makeText(getApplicationContext(), msg, timeStyle).show();
            }
        });
    }


}