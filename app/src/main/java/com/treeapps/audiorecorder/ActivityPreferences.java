package com.treeapps.audiorecorder;

import android.app.Fragment;
import android.app.FragmentManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.media.AudioRecord;
import android.preference.ListPreference;
import android.preference.PreferenceActivity;
import android.support.v7.app.ActionBarActivity;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;


public class ActivityPreferences extends PreferenceActivity implements SharedPreferences.OnSharedPreferenceChangeListener {

    public static final String KEY_LIST_PREFERENCE = "listPref";
    public static final String BOOL_IS_BUSY_RECORDING_OR_PLAYING = "com.treeapps.audiorecorder.is_busy_recording_or_playing";

    public ListPreference mListPreference;


    @SuppressWarnings("deprecation")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        addPreferencesFromResource(R.xml.activity_preferences);



        setupGui();

    }

    @SuppressWarnings("deprecation")
    private void setupGui() {
        mListPreference = (ListPreference)getPreferenceScreen().findPreference(KEY_LIST_PREFERENCE);

        // Set list value
        if (mListPreference.getEntry() != null) {
            mListPreference.setSummary("Current value is " + mListPreference.getEntry().toString());
        }

        // Set up a listener whenever a key changes
        getPreferenceScreen().getSharedPreferences().registerOnSharedPreferenceChangeListener(this);

        Bundle bundle = getIntent().getExtras();
        boolean boolIsRecordingOrPlaying = bundle.getBoolean(BOOL_IS_BUSY_RECORDING_OR_PLAYING,true);
        mListPreference.setEnabled(!boolIsRecordingOrPlaying);


    }

    @Override
    protected void onResume() {
        super.onResume();

        setupGui();
    }

    @SuppressWarnings("deprecation")
    @Override
    protected void onPause() {
        super.onPause();

        // Unregister the listener whenever a key changes
        getPreferenceScreen().getSharedPreferences().unregisterOnSharedPreferenceChangeListener(this);
    }


    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        // Set new summary, when a preference value changes
        if (key.equals(KEY_LIST_PREFERENCE)) {
            mListPreference.setSummary("Current value is " + mListPreference.getEntry().toString());
        }
    }

    @Override
    public void onBackPressed() {
        setResult(RESULT_OK);
        finish();
    }
}
