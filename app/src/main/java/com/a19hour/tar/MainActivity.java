package com.a19hour.tar;

import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.os.BatteryManager;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;

import com.wilddog.client.AuthData;
import com.wilddog.client.ChildEventListener;
import com.wilddog.client.DataSnapshot;
import com.wilddog.client.ValueEventListener;
import com.wilddog.client.Wilddog;
import com.wilddog.client.WilddogError;

import org.shaded.json.JSONException;
import org.shaded.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

public class MainActivity extends AppCompatActivity {
    private Wilddog ref;
    private ProgressBar progressBar;
    private ProgressDialog progressDialog;
    private AlertDialog.Builder builder;
    private String accessCode = null;
    private SigninClassTask mSigninClassTask = null;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        progressDialog = new ProgressDialog(MainActivity.this);
        progressDialog.setMessage("Signing in, please wait...");
        progressDialog.show();
        progressDialog.setCanceledOnTouchOutside(false);
        progressDialog.setCancelable(false);
        Wilddog.setAndroidContext(this);
        SharedPreferences preferences = getSharedPreferences("user",0);
        String signoff = preferences.getString("signoff", "yes");
        String email = preferences.getString("email","");
        String password = preferences.getString("password","");
        if (email.equals("")){
            //the email is empty, goes to sign up page
            progressDialog.dismiss();
            Intent intent = new Intent(getApplicationContext(), LoginActivity.class);
            startActivity(intent);
        }
        else{
            ref = new Wilddog("https://tar.wilddogio.com");
            Wilddog.AuthResultHandler authResultHandler = new Wilddog.AuthResultHandler() {
                @Override
                public void onAuthenticated(AuthData authData) {
                    SharedPreferences userInfo = getSharedPreferences("user",0);
                    SharedPreferences.Editor editor = userInfo.edit();
                    editor.putString("uid",authData.getUid());
                    editor.commit();
                    progressDialog.dismiss();
                }
                @Override
                public void onAuthenticationError(WilddogError error) {
                    progressDialog.dismiss();
                    Intent intent = new Intent(getApplicationContext(), LoginActivity.class);
                    startActivity(intent);
                }
            };
            ref.authWithPassword(email, password, authResultHandler);

        }
        progressBar = (ProgressBar) findViewById(R.id.sign_in_class_progress);
        Button signinClassButton = (Button) findViewById(R.id.sign_in_class_button);
        signinClassButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                attemptSigninClass();
            }
        });
        Button signoutButton = (Button) findViewById(R.id.sign_out_button);
        signoutButton.setOnClickListener(new View.OnClickListener(){
            @Override
            public void onClick(View view) {
                attemptSignout();
            }
        });
        EditText accessCode = (EditText) findViewById(R.id.accesscode);
        accessCode.requestFocus();
        //set the default alert builder
        builder = new AlertDialog.Builder(this);
        builder.setMessage("Error");
        builder.setCancelable(true);

        builder.setPositiveButton(
                "OK",
                new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        dialog.cancel();
                    }
                });
        if (signoff.equalsIgnoreCase("no")){
            Intent intent = new Intent(getApplicationContext(), CurrentClassActivity.class);
            startActivity(intent);
        }
    }

    private void attemptSigninClass(){
        EditText accessCodeText = (EditText) findViewById(R.id.accesscode);
        accessCode = accessCodeText.getText().toString();
        progressBar.setVisibility(View.VISIBLE);
        mSigninClassTask = new SigninClassTask(accessCode);
        mSigninClassTask.execute((Void) null);
    }

    private void attemptSignout(){
        ref.unauth();
        SharedPreferences userInfo = getSharedPreferences("user",0);
        SharedPreferences.Editor editor = userInfo.edit();
        editor.putString("uid","");
        editor.putString("email","");
        editor.putString("password","");
        editor.putString("name","");
        editor.putString("signoff", "no");
        editor.putString("survey", "no");
        editor.commit();
        Intent intent = new Intent(getApplicationContext(), LoginActivity.class);
        startActivity(intent);
    }

    public class SigninClassTask extends AsyncTask<Void, Void, Boolean> {

        private final String mAccessCode;
        SigninClassTask(String accessCode) {
            this.mAccessCode = accessCode;
        }

        @Override
        protected Boolean doInBackground(Void... params) {
            ref = new Wilddog("https://tar.wilddogio.com");
            SimpleDateFormat sdf = new SimpleDateFormat("dd-MM-yyyy");
            final String currentDateandTime = sdf.format(new Date());
            sdf = new SimpleDateFormat("dd-MM-HH-mm-ss");
            final String startTime = sdf.format(new Date());
            SharedPreferences userInfo = getSharedPreferences("user",0);
            SharedPreferences.Editor editor = userInfo.edit();
            editor.putString("signoff", "no");
            editor.putString("survey", "no");
            editor.putString("lastdate", currentDateandTime);
            editor.putString("lastaccesscode", accessCode);
            editor.commit();
            Intent batteryIntent = registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
            int level = batteryIntent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
            int scale = batteryIntent.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
            final float batteryPct = ((float)level / (float)scale);

            ref.addListenerForSingleValueEvent(new ValueEventListener() {
                @Override
                public void onDataChange(DataSnapshot snapshot) {
                    Map<String, Object> allClassesUnderOneDay = new HashMap<String, Object>();
                    allClassesUnderOneDay = (HashMap<String, Object>)snapshot.getValue();
                    allClassesUnderOneDay = (HashMap<String, Object>)allClassesUnderOneDay.get("classes");
                    allClassesUnderOneDay = (HashMap<String, Object>)allClassesUnderOneDay.get(currentDateandTime);
                    allClassesUnderOneDay = (HashMap<String, Object>)allClassesUnderOneDay.get(accessCode);

                    Map<String, Object> userInfo = new HashMap<String, Object>();
                    userInfo = (HashMap<String, Object>)snapshot.getValue();
                    userInfo = (HashMap<String, Object>)userInfo.get("users");
                    SharedPreferences user = getSharedPreferences("user", 0);
                    String uid = user.getString("uid","15e773764c41d38ea26b054a9451");//this uid belongs to test account
                    userInfo = (HashMap<String, Object>)userInfo.get(uid);
                    String name = (String) userInfo.get("name");
                    String email = (String) userInfo.get("email");
                    SharedPreferences.Editor editor = user.edit();
                    editor.putString("name", name);
                    editor.putString("email", email);
                    editor.commit();
                    if (allClassesUnderOneDay == null){
                        // no class found on the day
                        progressBar.setVisibility(View.GONE);
                        builder.setMessage("No class found for today");
                        AlertDialog alert = builder.create();
                        alert.show();
                    }
                    else {
                        if (allClassesUnderOneDay == null) {
                            // no class with the provided access code found
                            progressBar.setVisibility(View.GONE);
                            builder.setMessage("No class with the access code found for today");
                            AlertDialog alert = builder.create();
                            alert.show();
                        } else {
                            // class with the access code found
                            Map<String, String> updateInfo = new HashMap<String, String>();
                            updateInfo.put("email", email);
                            updateInfo.put("name", name);
                            updateInfo.put("startbattery", String.valueOf(batteryPct));
                            updateInfo.put("starttime", startTime);
                            Map<String, Object> updateComponent = new HashMap<String, Object>();
                            updateComponent.put(uid, updateInfo);
                            if (allClassesUnderOneDay.get("students") == null){
                                // this is the first student to add to the class
                                allClassesUnderOneDay.put("students", updateComponent);
                                ref.child("classes").child(currentDateandTime).child(accessCode).setValue(allClassesUnderOneDay);
                            }
                            else {
                                allClassesUnderOneDay = (HashMap<String, Object>)allClassesUnderOneDay.get("students");
                                allClassesUnderOneDay.put(uid, updateInfo);
                                ref.child("classes").child(currentDateandTime).child(accessCode).child("students").setValue(updateComponent);
                            }
                            progressBar.setVisibility(View.GONE);
                            builder.setMessage("Class found!");
                            builder.setPositiveButton(
                                    "OK",
                                    new DialogInterface.OnClickListener() {
                                        public void onClick(DialogInterface dialog, int id) {
                                            dialog.cancel();
                                            Intent intent = new Intent(getApplicationContext(), CurrentClassActivity.class);
                                            startActivity(intent);
                                        }
                                    });
                            AlertDialog alert = builder.create();
                            alert.show();
                        }
                    }
                }

                @Override
                public void onCancelled(WilddogError wilddogError) {}
            });
            return true;
        }

        @Override
        protected void onPostExecute(final Boolean success) {
            mSigninClassTask = null;
        }

        @Override
        protected void onCancelled() {
            mSigninClassTask = null;
        }
    }
}
