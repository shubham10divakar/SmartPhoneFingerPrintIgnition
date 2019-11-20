package subhamdivakar.consrv.water.smartphonefingerprintignition;

import android.Manifest;
import android.annotation.TargetApi;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.hardware.fingerprint.FingerprintManager;
import android.os.Build;
import android.os.CancellationSignal;
import android.support.v4.app.ActivityCompat;
import android.widget.Toast;


@TargetApi(Build.VERSION_CODES.M)

public class HandlingFingerprint extends FingerprintManager.AuthenticationCallback {

    private CancellationSignal cancellationSignal;
    private Context context;
    int ctr=0;
    boolean flag=false;

    public HandlingFingerprint(Context mContext) {
        context = mContext;
    }

    public void startAuth(FingerprintManager manager, FingerprintManager.CryptoObject cryptoObject) {

        cancellationSignal = new CancellationSignal();
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.USE_FINGERPRINT) != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        manager.authenticate(cryptoObject, cancellationSignal, 0, this, null);
    }

    @Override
    public void onAuthenticationError(int errMsgId, CharSequence errString) {
        Toast.makeText(context, "Authentication error\n" + errString, Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onAuthenticationFailed() {
//        TextView obj=(TextView)findViewById(R.id.textView1);
//        obj.setText("Authentication failed");
        ctr++;
        if(ctr>=0)
        {
            Toast.makeText(context, "Authentication failed", Toast.LENGTH_SHORT).show();
            flag=false;
        }
        if(ctr==4)
        {
            Toast.makeText(context, "You have tried more than 3 times.", Toast.LENGTH_SHORT).show();
            flag=false;

        }
    }

    @Override
    public void onAuthenticationHelp(int helpMsgId, CharSequence helpString)
    {
        Toast.makeText(context, "Authentication help\n" + helpString, Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onAuthenticationSucceeded(
            FingerprintManager.AuthenticationResult result) {
        Toast.makeText(context, "You have Been Successfully Logged in!", Toast.LENGTH_SHORT).show();
        //flag=true;
        //Value();
        //MainActivity obj1=new MainActivity();
        Intent obj=new Intent(context,DeviceList.class);
        context.startActivity(obj);
        //obj.putExtra("address",DeviceList.EXTRA_ADDRESS);
    }
    public boolean Value()
    {

        return flag;
    }
}