package subhamdivakar.consrv.water.smartphonefingerprintignition;

import android.Manifest;
import android.app.Activity;
import android.app.ProgressDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.speech.tts.TextToSpeech;
import android.support.annotation.WorkerThread;
import android.telephony.SmsManager;
import android.telephony.SmsMessage;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.EditText;
import android.widget.Button;
import android.widget.Toast;

import com.karumi.dexter.Dexter;
import com.karumi.dexter.MultiplePermissionsReport;
import com.karumi.dexter.PermissionToken;
import com.karumi.dexter.listener.PermissionRequest;
import com.karumi.dexter.listener.multi.MultiplePermissionsListener;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.DecimalFormat;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class ReceiveData extends Activity
{
    TextView myLabel;
    GPSTracker gps;
    EditText myTextbox;
    EditText txt;
    BluetoothAdapter mBluetoothAdapter;
    BluetoothSocket mmSocket;
    BluetoothDevice mmDevice;
    OutputStream mmOutputStream;
    InputStream mmInputStream;
    Thread workerThread;
    byte[] readBuffer;
    ProgressDialog pd1;
    int readBufferPosition;
    int counter;
    private TextToSpeech tts;
    double latitude;
    int ctr=0;
    double longitude;
    boolean safe1=false;
    public String data="";
    boolean safe=false;
    boolean detected=false;
    volatile boolean stopWorker;
    LinearLayout ly1;
    String greet="Hey";
    ScheduledExecutorService scheduleTaskExecutor;

    @Override
    public void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_receive_data);

        Button openButton = (Button)findViewById(R.id.open);
        Button sendButton = (Button)findViewById(R.id.send);
        Button closeButton = (Button)findViewById(R.id.close);
        myLabel = (TextView)findViewById(R.id.label);
        myTextbox = (EditText)findViewById(R.id.entry);
        txt=findViewById(R.id.number);
        ly1=findViewById(R.id.ly1);
        pd1=new ProgressDialog(ReceiveData.this);

        ly1.setVisibility(View.GONE);

        tts = new TextToSpeech(this, new TextToSpeech.OnInitListener() {
            @Override
            public void onInit(int status) {
                if (status == TextToSpeech.SUCCESS) {
                    int result = tts.setLanguage(Locale.US);
                    if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                        Log.e("TTS", "This Language is not supported");
                    }
                } else {
                    Log.e("TTS", "Initilization Failed!");
                }
            }
        });


//        Dexter.withActivity(this)
//                .withPermissions(
//                        Manifest.permission.READ_EXTERNAL_STORAGE,
//                        Manifest.permission.WRITE_EXTERNAL_STORAGE,
//                        Manifest.permission.SEND_SMS,
//                        Manifest.permission.ACCESS_FINE_LOCATION)
//                .withListener(new MultiplePermissionsListener() {
//                    @Override
//                    public void onPermissionsChecked(MultiplePermissionsReport report) {
//                        // check if all permissions are granted
//                        if (report.areAllPermissionsGranted()) {
//                            // do you work now
//                            Toast.makeText(ReceiveData.this, "Permission Granted", Toast.LENGTH_SHORT).show();
//                        }
//
//                        // check for permanent denial of any permission
//                        if (report.isAnyPermissionPermanentlyDenied()) {
//                            // permission is denied permenantly, navigate user to app settings
//                            finish();
//                        }
//                    }
//
//                    @Override
//                    public void onPermissionRationaleShouldBeShown(List<PermissionRequest> permissions, PermissionToken token) {
//                        token.continuePermissionRequest();
//                    }
//                })
//                .onSameThread()
//                .check();

        //Open Button
        openButton.setOnClickListener(new View.OnClickListener()
        {
            public void onClick(View v)
            {
                try
                {
                    findBT();
                    openBT();
                }
                catch (IOException ex) { }
            }
        });

        //Send Button
        sendButton.setOnClickListener(new View.OnClickListener()
        {
            public void onClick(View v)
            {
                try
                {
                    sendData();
                }
                catch (IOException ex) { }
            }
        });

        //Close button
        closeButton.setOnClickListener(new View.OnClickListener()
        {
            public void onClick(View v)
            {
                try
                {
                    closeBT();
                }
                catch (IOException ex) { }
            }
        });
    }

    void findBT()
    {
        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if(mBluetoothAdapter == null)
        {
            myLabel.setText("No bluetooth adapter available");
        }

        if(!mBluetoothAdapter.isEnabled())
        {
            Intent enableBluetooth = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBluetooth, 0);
        }

        Set<BluetoothDevice> pairedDevices = mBluetoothAdapter.getBondedDevices();
        if(pairedDevices.size() > 0)
        {
            for(BluetoothDevice device : pairedDevices)
            {
                if(device.getName().equals("SSP"))
                {
                    mmDevice = device;
                    break;
                }
            }
        }
        myLabel.setText("Bluetooth Device Found");
    }

    void openBT() throws IOException
    {
        UUID uuid = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB"); //Standard SerialPortService ID
        mmSocket = mmDevice.createRfcommSocketToServiceRecord(uuid);
        mmSocket.connect();
        mmOutputStream = mmSocket.getOutputStream();
        mmInputStream = mmSocket.getInputStream();

        beginListenForData();

        myLabel.setText("Bluetooth Opened");
    }

    void beginListenForData()
    {

        final Handler handler = new Handler();
        final byte delimiter = 10; //This is the ASCII code for a newline character

        stopWorker = false;
        readBufferPosition = 0;
        readBuffer = new byte[1024];
        workerThread = new Thread(new Runnable()
        {
            public void run()
            {
                while(!Thread.currentThread().isInterrupted() && !stopWorker)
                {
                    try
                    {
                        int bytesAvailable = mmInputStream.available();
                        if(bytesAvailable > 0)
                        {
                            byte[] packetBytes = new byte[bytesAvailable];
                            mmInputStream.read(packetBytes);
                            for(int i=0;i<bytesAvailable;i++)
                            {
                                byte b = packetBytes[i];
                                if(b == delimiter)
                                {
                                    byte[] encodedBytes = new byte[readBufferPosition];
                                    System.arraycopy(readBuffer, 0, encodedBytes, 0, encodedBytes.length);
                                    data = new String(encodedBytes, "US-ASCII");
                                    readBufferPosition = 0;

                                    handler.post(new Runnable()
                                    {
                                        public void run() {
                                            myLabel.setText(data);
                                            //Toast.makeText(gps, "dfdg"+data, Toast.LENGTH_SHORT).show();
                                            //Log.e("Data",data);
                                            String d=data.trim();
                                            myLabel.setText(d);
//                                            if(detected )
//                                            {
//                                                ly1.setVisibility(View.VISIBLE);
//                                            }
//                                            //txt.setText(data);
//                                            Log.e("dfgfdg","dfgdf"+data+"----"+data.length());
//                                            if(data.length()>10)
//                                            {
//                                                d.trim();
//                                                if (d.equals("ACCIDENT DETECTED"))
//                                                {
//                                                    detected=true;
//                                                    ly1.setVisibility(View.VISIBLE);
//                                                    Log.e("INSIDE","dfgdf"+data);
//                                                    if(!safe)
//                                                    {
//                                                        calling();
//                                                    }
//                                                }
//                                            }

//                                            else if(d.equals("SAFE"))
//                                            {
//                                                Log.e("INSIDE","dfgdf"+data);
//                                                calling();
//                                            }
                                        }
                                    });
                                }
                                else
                                {
                                    readBuffer[readBufferPosition++] = b;
                                }
                            }
                        }
                    }
                    catch (IOException ex)
                    {
                        stopWorker = true;
                    }
                }
            }
        });

        workerThread.start();
    }
    private void speak(String text){
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, null);

        }else{
            tts.speak(text, TextToSpeech.QUEUE_FLUSH, null);
        }
    }

    void calling()
    {
        ctr++;
        if(ctr%5 ==0)
        {
            Log.e("Speaking","kllll");
            speak("Are you safe. Accident Detected");
        }
        if(detected)
        {
            ly1.setVisibility(View.VISIBLE);
        }
        Log.e("FOUND","djjgdfj");
        SmsManager smsManager = SmsManager.getDefault();
        String no;
        no=txt.getText().toString();
        gps = new GPSTracker(ReceiveData.this);
        if(gps.canGetLocation()){

            latitude = gps.getLatitude();
            longitude = gps.getLongitude();
            Toast.makeText(getApplicationContext(), "Your Location is - \nLat: " + latitude + "\nLong: " + longitude, Toast.LENGTH_LONG).show();
        }else
            {
            gps.showSettingsAlert();
        }
        if(TextUtils.isEmpty(no))
        {
            Toast.makeText(ReceiveData.this, "Please Enter number", Toast.LENGTH_SHORT).show();
        }
        else
            {
                    Toast.makeText(ReceiveData.this, "ACCIDENT", Toast.LENGTH_SHORT).show();
                    String sms = "HELP ME,I AM IN DANGER.   My location is  http://maps.google.com/?q="+latitude+","+longitude;
                    smsManager.sendTextMessage(no, null, "ACCIDENT OCCURED"+sms, null, null);
            }
    }


    void safe_unsafe(View view)
    {
        if(view.getId()==R.id.safe1)
        {
            safe=true;
            //ly1.setVisibility(View.GONE);
        }
        else if(view.getId()==R.id.safe2)
        {
            safe=false;
        }
    }

    void sendData() throws IOException
    {
        String msg = myTextbox.getText().toString();
        msg += "\n";
        mmOutputStream.write(msg.getBytes());
        myLabel.setText("Data Sent");
    }

    void closeBT() throws IOException
    {
        stopWorker = true;
        mmOutputStream.close();
        mmInputStream.close();
        mmSocket.close();
        myLabel.setText("Bluetooth Closed");
    }
    class MyTask extends AsyncTask<Void, Void, Void> {
        ProgressDialog pd;
        public MyTask(ProgressDialog pd){
            this.pd=pd;
        }
        @Override
        protected void onPreExecute () {
            super.onPreExecute();
            pd.setMessage("Calculating . . .");
            pd.show();
            pd.setCancelable(false);
        }

        @Override
        protected Void doInBackground (Void...voids){

            scheduleTaskExecutor.scheduleAtFixedRate(new Runnable() {
                @Override
                public void run() {
                    // Do stuff here!

                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            // Do stuff to update UI here!
                            Toast.makeText(ReceiveData.this, "Its been 5 seconds", Toast.LENGTH_SHORT).show();
                        }
                    });

                }
            }, 0, 5, TimeUnit.SECONDS); // or .MINUTES, .HOURS etc.
            return null;//download file
        }

        @Override
        protected void onPostExecute (Void aVoid){
            super.onPostExecute(aVoid);
            pd.dismiss();
//            Intent obj=new Intent(Burns.this,BurnAreaCalculator.class);
//            startActivity(obj);
        }
    }
}