package com.treeapps.audiorecorder;

import android.content.Intent;
import android.os.Environment;
import android.support.v7.app.ActionBarActivity;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;

import java.io.File;


public class MainActivity extends ActionBarActivity {

    private static final int INTENT_REQUEST_CODE = 1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_test) {
            Intent intent = new Intent(getApplicationContext(),ActivityAudioRecorder.class);
            String root = Environment.getExternalStorageDirectory().toString();
            String strAppWorkFolderFullPath = root + "/AudioRecorder";
            File fileDir = new File(strAppWorkFolderFullPath);
            if (!fileDir.exists()) {
                fileDir.mkdirs();
            }
            intent.putExtra(ActivityAudioRecorder.INTENT_WORK_FOLDER_FULL_PATH, strAppWorkFolderFullPath);
            intent.putExtra(ActivityAudioRecorder.INTENT_AUDIO_FULL_FILENAME, strAppWorkFolderFullPath +"/WavTestFile.wav");
            startActivityForResult(intent, INTENT_REQUEST_CODE);
        }

        return super.onOptionsItemSelected(item);
    }
}
