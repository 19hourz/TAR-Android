package com.a19hour.tar;

import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.BatteryManager;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;

import com.androidadvance.androidsurvey.SurveyActivity;
import com.shaded.fasterxml.jackson.databind.util.JSONPObject;
import com.wilddog.client.DataSnapshot;
import com.wilddog.client.ValueEventListener;
import com.wilddog.client.Wilddog;
import com.wilddog.client.WilddogError;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
public class CurrentClassActivity extends AppCompatActivity {
    private Wilddog ref;
    private static final int SURVEY_REQUEST = 1337;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_current_class);
        Wilddog.setAndroidContext(this);
        ref = new Wilddog("https://tar.wilddogio.com");
        SharedPreferences preferences = getSharedPreferences("user",0);
        String signoff = preferences.getString("signoff", "no");
        String survey = preferences.getString("survey", "no");
        if (signoff.equalsIgnoreCase("yes") && survey.equalsIgnoreCase("no")){
            Intent i_survey = new Intent(CurrentClassActivity.this, SurveyActivity.class);
            i_survey.putExtra("json_survey", loadSurveyJson("survey.json"));
            startActivityForResult(i_survey, SURVEY_REQUEST);
        }
        Button signOff = (Button) findViewById(R.id.sign_off_class_button);
        signOff.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                attemptSignOff();
                Intent i_survey = new Intent(CurrentClassActivity.this, SurveyActivity.class);
                i_survey.putExtra("json_survey", loadSurveyJson("survey.json"));
                startActivityForResult(i_survey, SURVEY_REQUEST);
            }
        });
    }

    private void attemptSignOff(){
        Intent batteryIntent = registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        int level = batteryIntent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
        int scale = batteryIntent.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
        final float batteryPct = ((float)level / (float)scale);

        SimpleDateFormat sdf = new SimpleDateFormat("dd-MM-HH-mm-ss");
        final String endTime = sdf.format(new Date());

        SharedPreferences userInfo = getSharedPreferences("user",0);
        String uid = userInfo.getString("uid", "15e773764c41d38ea26b054a9451");
        String date = userInfo.getString("lastdate", "16-08-2016");
        String accessCode = userInfo.getString("lastaccesscode", "21587");
        ref = ref.child("classes").child(date).child(accessCode).child("students").child(uid);
        ref.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot snapshot) {
                Map<String, Object> studentInfo = new HashMap<String, Object>();
                studentInfo = (HashMap<String, Object>)snapshot.getValue();
                studentInfo.put("endtime", endTime);
                studentInfo.put("endbattery", String.valueOf(batteryPct));
                ref.setValue(studentInfo);
                SharedPreferences userInfo = getSharedPreferences("user",0);
                SharedPreferences.Editor editor = userInfo.edit();
                editor.putString("signoff", "yes");
                editor.putString("survey", "no");
                editor.commit();
            }

            @Override
            public void onCancelled(WilddogError wilddogError) {}
        });
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        final ProgressDialog progressDialog = new ProgressDialog(CurrentClassActivity.this);
        progressDialog.setMessage("Uploading, please wait...");
        progressDialog.show();
        progressDialog.setCanceledOnTouchOutside(false);
        progressDialog.setCancelable(false);
        if (requestCode == SURVEY_REQUEST) {
            if (resultCode == RESULT_OK) {
                String answers_json = data.getExtras().getString("answers");
                try {
                    final JSONObject json = new JSONObject(answers_json);
                    ref = new Wilddog("https://tar.wilddogio.com");
                    final SharedPreferences userInfo = getSharedPreferences("user",0);
                    String uid = userInfo.getString("uid", "15e773764c41d38ea26b054a9451");
                    String date = userInfo.getString("lastdate", "16-08-2016");
                    String accessCode = userInfo.getString("lastaccesscode", "21587");
                    ref = ref.child("classes").child(date).child(accessCode).child("students").child(uid);
                    ref.addListenerForSingleValueEvent(new ValueEventListener() {
                        @Override
                        public void onDataChange(DataSnapshot snapshot) {
                            Map<String, String> surveyAnswers = new HashMap<String, String>();
                            try {
                                surveyAnswers.put("0", json.getString("What was the name of the tutor? (Chinese characters or Pinyin)"));
                                surveyAnswers.put("1", json.getString("What was the class you took?"));
                                surveyAnswers.put("2", json.getString("How many lessons did you take?"));
                                surveyAnswers.put("3", json.getString("What is today's date? (MMDD)"));
                                surveyAnswers.put("4", json.getString("Was it fun (out of 10)"));
                                surveyAnswers.put("5", json.getString("Was it informative (out of 10)"));
                                surveyAnswers.put("6", json.getString("General rating (out of 10)"));
                            } catch (JSONException e) {
                                e.printStackTrace();
                            }
                            Map<String, Object> survey = new HashMap<String, Object>();
                            survey = (HashMap<String, Object>) snapshot.getValue();
                            survey.put("survey", surveyAnswers);
                            ref.setValue(survey, new Wilddog.CompletionListener() {
                                @Override
                                public void onComplete(WilddogError wilddogError, Wilddog wilddog) {
                                    SharedPreferences userInfo = getSharedPreferences("user",0);
                                    SharedPreferences.Editor editor = userInfo.edit();
                                    editor.putString("signoff", "yes");
                                    editor.putString("survey", "yes");
                                    editor.commit();
                                    progressDialog.dismiss();
                                    Intent intent = new Intent(getApplicationContext(), MainActivity.class);
                                    startActivity(intent);
                                }
                            });
                        }

                        @Override
                        public void onCancelled(WilddogError wilddogError) {}
                    });
                } catch (Throwable t) {
                }

            }
        }
    }

    private String loadSurveyJson(String filename) {
        try {
            InputStream is = getAssets().open(filename);
            int size = is.available();
            byte[] buffer = new byte[size];
            is.read(buffer);
            is.close();
            return new String(buffer, "UTF-8");
        } catch (IOException ex) {
            ex.printStackTrace();
            return null;
        }
    }

}
