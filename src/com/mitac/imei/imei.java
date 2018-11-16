package com.mitac.imei;

import android.app.Activity;
import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
//import android.widget.TextView;
//import android.widget.Button;
import android.widget.Toast;
import com.android.internal.telephony.Phone;
import com.android.internal.telephony.PhoneFactory;
import android.view.View.OnKeyListener;
import android.view.KeyEvent;
import android.os.Message;
import android.os.Handler;
import android.os.AsyncResult;
import android.util.Log;
import android.app.AlertDialog;
import android.os.SystemProperties;
import android.content.Intent;

import java.io.IOException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.File;
import java.io.FileNotFoundException;


public class imei extends Activity {
    private static final String TAG = "IMEI";
    private Phone mPhone = null;
    private EditText CmdRespText = null;
    private static final int EVENT_RIL_OEM_HOOK_CMDRAW_COMPLETE = 1300;
    private static final int EVENT_RIL_OEM_HOOK_CMDSTR_COMPLETE = 1400;
    private static final int EVENT_UNSOL_RIL_OEM_HOOK_RAW = 500;
    private static final int EVENT_UNSOL_RIL_OEM_HOOK_STR = 600;
    private static final int EVENT_RIL_SET_URING = 700;
    private String imei = null;
    private boolean mReadIMEI = false;
    private static final int RESULT_FAIL = 0;
    private static final int RESULT_PASS = 1;

    @Override
        public void onCreate(Bundle icicle) {
            super.onCreate(icicle);
            setContentView(R.layout.main);

            // Initially turn on first button.
            // Get our main phone object.
            mPhone = PhoneFactory.getDefaultPhone();
            // Register for OEM raw notification.
            // mPhone.mCM.setOnUnsolOemHookRaw(mHandler,EVENT_UNSOL_RIL_OEM_HOOK_RAW, null);
        }

    @Override
        public void onPause() {
            super.onPause();
            log("onPause()");
            // Unregister for OEM raw notification.
            // mPhone.mCM.unSetOnUnsolOemHookRaw(mHandler);
        }

    @Override
        public void onResume() {
            super.onResume();
            log("onResume()");
            // Register for OEM raw notification.
            // mPhone.mCM.setOnUnsolOemHookRaw(mHandler,EVENT_UNSOL_RIL_OEM_HOOK_RAW, null);
        }


    public void onReadIMEI(View view) {
        mReadIMEI = true;
        String[] oemhookstring = { "AT+CGSN" + '\r' };
        // Create message
        Message msg = mHandler.obtainMessage(EVENT_RIL_OEM_HOOK_CMDSTR_COMPLETE);
        // Send request
        mPhone.invokeOemRilRequestStrings(oemhookstring, msg);
        CmdRespText = (EditText) findViewById(R.id.edit_response);
        CmdRespText.setText("---Wait response---");
        return ;
    }

    private int checksum(int[] digits) {
        int sum = 0;
        int length = digits.length;
        for (int i=0; i<length; i++) {
            // get digits in order
            int digit = digits[i];
            // every 2nd number multiply with 2
            if (i%2 == 1) {
                digit *= 2;
            }
            //log("index: "+i+" digit: "+digit);
            sum += digit>9?(digit-9):digit;
        }
        return 10-(sum%10);
    }

    public void onChangeIMEI(View view) {
        mReadIMEI = false;
        if(imei == null) return;

        String[] oemhookstring = { "AT+UCUSTIMEI=1,\"" + imei+"\"\r" };
        log(""+oemhookstring[0]);
        // Create message
        Message msg = mHandler.obtainMessage(EVENT_RIL_OEM_HOOK_CMDSTR_COMPLETE);
        // Send request
        mPhone.invokeOemRilRequestStrings(oemhookstring, msg);
        CmdRespText = (EditText) findViewById(R.id.edit_response);
        CmdRespText.setText("---Wait response---");
        return ;
    }

    public void onCloseIMEI(View view) {
        //reboot the device after a while
        //Intent intent=new Intent(Intent.ACTION_REBOOT);
        //intent.putExtra("nowait", 1);
        //intent.putExtra("interval", 1);
        //intent.putExtra("window", 0);
        //sendBroadcast(intent);
        if(imei != null) {
            Bundle bundle = new Bundle();
            bundle.putString("item", "imei");
            bundle.putString("Result", imei);
            Intent mIntent = new Intent();
            mIntent.putExtras(bundle);
            setResult(RESULT_PASS, mIntent);
        }

        finish();
    }

    private void logRilOemHookResponse(AsyncResult ar) {
        log("received oem hook response");
        String str = new String("");
        if (ar.exception != null) {
            log("Exception:" + ar.exception);
            str += "Exception:" + ar.exception + "\n\n";
        }
        if (ar.result != null) {
            byte[] oemResponse = (byte[]) ar.result;
            int size = oemResponse.length;
            log("oemResponse length=[" + Integer.toString(size) + "]");
            str += "oemResponse length=[" + Integer.toString(size) + "]" + "\n";
            if (size > 0) {
                for (int i = 0; i < size; i++) {
                    byte myByte = oemResponse[i];
                    int myInt = (int) (myByte & 0xFF);
                    log("oemResponse[" + Integer.toString(i) + "]=[0x"
                            + Integer.toString(myInt, 16) + "]");
                    str += "oemResponse[" + Integer.toString(i) + "]=[0x"
                        + Integer.toString(myInt, 16) + "]" + "\n";
                }
            }
        } else {
            log("received NULL oem hook response");
            str += "received NULL oem hook response";
        }
        // Display message box
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setMessage(str);
        builder.setPositiveButton("OK", null);
        AlertDialog alert = builder.create();
        alert.show();
    }


    private void logRilOemHookResponseString(AsyncResult ar) {
        log("received oem hook string response");
        String str = new String("");
        CmdRespText = (EditText) findViewById(R.id.edit_response);
        if (ar.exception != null) {
            log("Exception:" + ar.exception);
            str += "Exception:" + ar.exception + "\n\n";
        }
        if (ar.result != null) {
            String[] oemStrResponse = (String[]) ar.result;
            int sizeStr = oemStrResponse.length;
            log("oemResponseString[0] [" + oemStrResponse[0] + "]");
            CmdRespText.setText("" + oemStrResponse[0]);
            ////////////////////////////////////////////////////////////
            ///////////// Caculate New IMEI ////////////////////////////
            ////////////////////////////////////////////////////////////
            if(mReadIMEI) {
                str = oemStrResponse[0];
                int[]  digits = new int[]{3,5,4,3,3,9,1,0,0,0,0,0,0,0};
                //TAC + FAC + device serial number
                byte[] bytes = str.getBytes();
                for(int i=8; i<14; i++) {
                    digits[i] = bytes[i+2]-48; //filter two characters(\r\n)
                    //log("digits["+i+"]"+" = "+digits[i]);
                }
                //check sum
                int lastbyte = checksum(digits);
                //log("checksum = "+lastbyte);
                //New IMEI number
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < 14; i++) {
                    sb.append(String.valueOf(digits[i]));
                }
                sb.append(String.valueOf(lastbyte));
                imei = ""+sb;
                //log("NEW IMEI: "+imei);
            }
        } else {
            log("received NULL oem hook response");
            CmdRespText.setText("No response or error received");
        }
    }

    private void log(String msg) {
        Log.d(TAG, "IMEI: " + msg);
    }

    private Handler mHandler = new Handler() {
        public void handleMessage(Message msg) {
            AsyncResult ar;
            switch (msg.what) {
                case EVENT_RIL_OEM_HOOK_CMDRAW_COMPLETE:
                    log("EVENT_RIL_OEM_HOOK_CMDRAW_COMPLETE");
                    ar = (AsyncResult) msg.obj;
                    logRilOemHookResponse(ar);
                    break;
                case EVENT_RIL_OEM_HOOK_CMDSTR_COMPLETE:
                    log("EVENT_RIL_OEM_HOOK_CMDSTR_COMPLETE");
                    ar = (AsyncResult) msg.obj;
                    logRilOemHookResponseString(ar);
                    break;
                case EVENT_UNSOL_RIL_OEM_HOOK_RAW:
                    break;
                case EVENT_UNSOL_RIL_OEM_HOOK_STR:
                    break;
                case EVENT_RIL_SET_URING:
                    break;
            }
        }
    };
}
