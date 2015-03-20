package com.treeapps.audiorecorder;

import android.app.AlertDialog;
import android.app.Fragment;
import android.app.FragmentManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioRecord;
import android.media.AudioTrack;
import android.media.MediaRecorder;
import android.os.AsyncTask;
import android.os.Bundle;
import android.preference.ListPreference;
import android.preference.PreferenceManager;
import android.support.v7.app.ActionBarActivity;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.Toast;

import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;

import com.treeapps.audiorecorder.AudioLib.AudioSample;

import static com.treeapps.audiorecorder.ActivityAudioRecorder.State.*;
import static com.treeapps.audiorecorder.ActivityAudioRecorder.State.ReadyWithNoSample;


public class ActivityAudioRecorder extends ActionBarActivity {


    public static final String INTENT_WORK_FOLDER_FULL_PATH = "com.example.audio_record.work_folder_full_path";
    public static final String INTENT_AUDIO_FULL_FILENAME = "com.example.audio_record.audio_filename";
    private static final int GET_PREFERENCES = 1;


    private final String TAG = "Recorder";

    private static final int GRAPH_PAGE_SIZE_IN_MS = 10000;




    public enum  State {
        ReadyWithNoSample, ReadyWithSample, Playing, Recording
    }

    public enum Event {
        RecordPressed, RecordStopPressed, RecordError, PlayPressed, PlayStopPressed, PlayError, ClearAll, BackPressed
    }

    String strAudioCurrentPlayFilenameWithoutExt = "audiocurrent";
    String strAudioLeftFilenameWithoutExt = "audioleft";
    String strAudioInsertFilenameWithoutExt = "audioinsert";
    String strAudioRightFilenameWithoutExt = "audioright";
    String strAudioScratchFilenameWithoutExt = "audioscratch";

    // GUI items
    EditText editTextDescription;
    ImageButton buttonSkipToStart;
    ImageButton buttonRecord;
    ImageButton buttonPlay;
    ImageButton buttonStop;
    ImageButton buttonSkipToEnd;
    ImageButton buttonDelete;



    public static class SessionDataFragment extends Fragment {

        String strWorkFolderFullPath;

        String strAudioEditFullFilename;

        public Context objContext;
        public StateMachine<State, Event> sm;

        AudioTrack audioTrack;

        public boolean isRecording = false;
        public RecordAsyncTask recordAsyncTask = null;

        public int intPlaybackPositionPercentage = 0;
        public boolean isPlaying = false;
        public PlayBackAsyncTask playBackAsyncTask = null;

        public AudioSample audioSampleCurrent;
        public AudioSample audioSampleLeft;
        public AudioSample audioSampleRight;
        public AudioSample audioSampleInsert;

        public AudioGraph audioGraph;
        public int intSampleRate;

        // Recording
        public double fltPlayPercentBeforeRecording;
        public double fltEndPercentBeforeRecording;

        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            // retain this fragment
            setRetainInstance(true);
        }
    }

    Context context;
    SessionDataFragment sd;
    AudioLib audioLib;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_audio_recorder);

        context = this;

        editTextDescription = (EditText) findViewById(R.id.editTextDescription);
        buttonSkipToStart = (ImageButton) findViewById(R.id.imageButtonSkipToStart);
        buttonRecord = (ImageButton) findViewById(R.id.imageButtonRecord);
        buttonPlay = (ImageButton) findViewById(R.id.imageButtonPlay);
        buttonStop = (ImageButton) findViewById(R.id.imageButtonStop);
        buttonSkipToEnd = (ImageButton) findViewById(R.id.imageButtonSkipToEnd);
        buttonDelete = (ImageButton) findViewById(R.id.imageButtonTrash);


        // Initialize all in persistent session data
        FragmentManager fragmentManager = getFragmentManager();
        sd = (SessionDataFragment) fragmentManager.findFragmentByTag("SESSION_DATA");
        if (sd == null) {
            sd = new SessionDataFragment();
            fragmentManager.beginTransaction().add(sd, "SESSION_DATA").commit();
        }

        try {
            if (savedInstanceState == null) {

                // Fill session data
                Bundle bundle = getIntent().getExtras();
                sd.strWorkFolderFullPath = bundle.getString(INTENT_WORK_FOLDER_FULL_PATH);
                // Setup state machine
                setupStateMachine();

                // Audio files
                audioLib = new AudioLib(sd.strWorkFolderFullPath);
                sd.strAudioEditFullFilename = bundle.getString(INTENT_AUDIO_FULL_FILENAME);

                sd.audioSampleCurrent = audioLib.new AudioSample(strAudioCurrentPlayFilenameWithoutExt); // Fill this on AudioGraph init callback
                sd.audioSampleInsert = audioLib.new AudioSample(strAudioInsertFilenameWithoutExt);
                sd.intSampleRate = getSampleRate();

                // Setup GUI
                setupGui();


            }
        } catch (IOException e) {
            Toast.makeText(this, "There has been an IO error. See stack trace", Toast.LENGTH_SHORT).show();
            Log.e(TAG, "IO exception", e);
        }
    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_activity_audio_recorder, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onPause() {
        // TODO Auto-generated method stub
        super.onPause();
    }

    @Override
    protected void onResume() {
        // TODO Auto-generated method stub
        super.onResume();
        LoadSession();
    }



    void LoadSession() {
        context = this;
        FragmentManager fragmentManager = getFragmentManager();
        sd = (SessionDataFragment) fragmentManager.findFragmentByTag("SESSION_DATA");
        if (sd == null) {
            sd = new SessionDataFragment();
            fragmentManager.beginTransaction().add(sd, "SESSION_DATA").commit();
        }
        audioLib = new AudioLib(sd.strWorkFolderFullPath);
    }

    private int getSampleRate() {
        // Setup with preferences
        SharedPreferences prefs= PreferenceManager.getDefaultSharedPreferences(this);
        String strValue = prefs.getString(ActivityPreferences.KEY_LIST_PREFERENCE,"");
        switch (strValue) {
            case "1":
                return 44100;
            case "2":
                return 22050;
            case "3":
                return 16000;
            case "4":
               return 11025;
            default:
                return 16000;        }
    }




    void setupStateMachine() {

        // What happens when entering each state
        sd.sm = new StateMachine<State, Event>();

        sd.sm.onEnteringState(ReadyWithNoSample, new StateMachine.StateTransition() {
            @Override
            public void onState(Enum newState, Enum prevState, Enum triggerEvent) {
                Log.d(TAG, "ReadyWithNoSample entered");
                // Display
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        buttonPlay.setVisibility(View.GONE);
                        buttonRecord.setVisibility(View.VISIBLE);
                        buttonRecord.setImageResource(R.mipmap.ic_action_mic);
                        buttonStop.setVisibility(View.GONE);
                        buttonSkipToStart.setVisibility(View.GONE);
                        buttonSkipToEnd.setVisibility(View.GONE);
                        buttonDelete.setVisibility(View.GONE);

                        // Cursor enable/disable
                        sd.audioGraph.enablePlayCursor(true);
                    }
                });

                // Action
                sd.audioSampleCurrent.clear();
                sd.audioGraph.clearGraph();
                sd.audioGraph.invalidate();
            }
        });

        sd.sm.onEnteringState(ReadyWithSample, new StateMachine.StateTransition() {
            @Override
            public void onState(Enum newState, Enum prevState, Enum triggerEvent) {
                Log.d(TAG, "ReadyWithSample entered");
                // Display
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        buttonPlay.setVisibility(View.VISIBLE);
                        buttonRecord.setVisibility(View.VISIBLE);
                        buttonRecord.setImageResource(R.mipmap.ic_action_mic);
                        buttonStop.setVisibility(View.GONE);
                        buttonSkipToStart.setVisibility(View.VISIBLE);
                        buttonSkipToEnd.setVisibility(View.VISIBLE);
                        buttonDelete.setVisibility(View.VISIBLE);

                        // Cursor enable/disable
                        sd.audioGraph.enablePlayCursor(true);
                    }
                });
                // Action
            }
        });

        sd.sm.onEnteringState(Playing, new StateMachine.StateTransition() {
            @Override
            public void onState(Enum newState, Enum prevState, Enum triggerEvent) {
                Log.d(TAG, "Playing entered");

                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        // Display
                        buttonPlay.setVisibility(View.GONE);
                        buttonRecord.setVisibility(View.GONE);
                        buttonStop.setVisibility(View.VISIBLE);
                        buttonStop.setImageResource(R.mipmap.ic_action_playback_stop);
                        buttonSkipToStart.setVisibility(View.GONE);
                        buttonSkipToEnd.setVisibility(View.GONE);
                        buttonDelete.setVisibility(View.GONE);

                        // Cursor enable/disable
                        sd.audioGraph.enablePlayCursor(false);

                        // Action
                        startPlay(new OnPlayComplete() {
                            @Override
                            public void onComplete(boolean boolIsSuccess, String strErrorMessage) {
                                if (boolIsSuccess) {
                                    sd.sm.triggerEvent(Event.PlayStopPressed);
                                } else {
                                    Toast.makeText(getApplicationContext(), strErrorMessage, Toast.LENGTH_LONG).show();
                                    sd.sm.triggerEvent(Event.PlayError);
                                }
                            }

                        });
                    }
                });
            }
        });


        sd.sm.onEnteringState(Recording, new StateMachine.StateTransition() {
            @Override
            public void onState(Enum Playing, Enum prevState, Enum triggerEvent) {
                Log.d(TAG, "Recording entered");
                // Display
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        buttonPlay.setVisibility(View.GONE);
                        buttonRecord.setVisibility(View.GONE);
                        buttonStop.setVisibility(View.VISIBLE);
                        buttonStop.setImageResource(R.mipmap.ic_action_micoff);
                        buttonSkipToStart.setVisibility(View.GONE);
                        buttonSkipToEnd.setVisibility(View.GONE);
                        buttonDelete.setVisibility(View.GONE);
                    }
                });

                // Action
                startRecording(new OnRecordingComplete() {
                    @Override
                    public void onComplete(boolean boolIsSuccess, String strErrorMessage) {
                        if (boolIsSuccess) {

                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                // Update the display with latest
                                try {
                                    int intRmsFrameSizeInShorts = sd.audioGraph.getOptimalDataSampleBufferSizeInShorts(sd.intSampleRate);
                                    int intPageSizeInRmsFrames = sd.audioGraph.getPageSizeInRmsFrames();
                                    AudioGraph.PageValue pageValue = sd.audioGraph.getPageValue();
                                    long lngStartRmsFrame = (long) ((pageValue.fltPageNum -1) * intPageSizeInRmsFrames);
                                    int[] intGraphBuffer = sd.audioSampleCurrent.getGraphBuffer(lngStartRmsFrame, intPageSizeInRmsFrames, intRmsFrameSizeInShorts);
                                    sd.audioGraph.updateGraph(intGraphBuffer);
                                    sd.audioGraph.setPageValue(pageValue);
                                    sd.audioGraph.invalidate();
                                } catch (Exception e) {
                                    Log.e(TAG, "Could not get graph values", e);
                                }
                                }
                            });

                            sd.sm.triggerEvent(Event.RecordStopPressed);
                        } else {
                            Toast.makeText(getApplicationContext(), strErrorMessage, Toast.LENGTH_LONG).show();
                            sd.sm.triggerEvent(Event.RecordError);
                        }
                    }


                });
            }
        });

        // Define what state gets called when event gets triggered
        sd.sm.onTriggeringEvent(Event.RecordPressed, new StateMachine.EventTransition() {
            // Starts the recording process
            @Override
            public Enum nextState(Enum triggerEvent, Enum prevState) {
                return Recording;
            }
        });

        sd.sm.onTriggeringEvent(Event.RecordStopPressed, new StateMachine.EventTransition() {
            // Recording stopped without error
            @Override
            public Enum nextState(Enum triggerEvent, Enum prevState) {
                sd.isRecording = false;
                return ReadyWithSample;
            }
        });


        sd.sm.onTriggeringEvent(Event.RecordError, new StateMachine.EventTransition() {
            // Recording stopped without error
            @Override
            public Enum nextState(Enum triggerEvent, Enum prevState) {
                return ReadyWithNoSample;
            }
        });

        sd.sm.onTriggeringEvent(Event.PlayPressed, new StateMachine.EventTransition() {
            // Recording stopped without error
            @Override
            public Enum nextState(Enum triggerEvent, Enum prevState) {
                return Playing;
            }
        });

        sd.sm.onTriggeringEvent(Event.PlayStopPressed, new StateMachine.EventTransition() {
            // Recording stopped without error
            @Override
            public Enum nextState(Enum triggerEvent, Enum prevState) {
                sd.isPlaying = false;
                return ReadyWithSample;
            }
        });

        sd.sm.onTriggeringEvent(Event.PlayError, new StateMachine.EventTransition() {
            // Recording stopped without error
            @Override
            public Enum nextState(Enum triggerEvent, Enum prevState) {
                return ReadyWithSample;
            }
        });

        sd.sm.onTriggeringEvent(Event.ClearAll, new StateMachine.EventTransition() {
            // Recording stopped without error
            @Override
            public Enum nextState(Enum triggerEvent, Enum prevState) {
                return ReadyWithNoSample;
            }
        });

        sd.sm.onTriggeringEvent(Event.BackPressed, new StateMachine.EventTransition() {
            // Recording stopped without error
            @Override
            public Enum nextState(Enum triggerEvent, Enum prevState) {
                switch ((State) sd.sm.getState()) {
                    case Playing:
                        sd.sm.triggerEvent(Event.PlayStopPressed);
                        return null;
                    case Recording:
                        sd.sm.triggerEvent(Event.RecordStopPressed);
                        return null;
                    default:
                        return (State) sd.sm.getState();
                }
            }
        });

    }



    void setupGui() {
        // Ensure soft keyboard hides unless required
        getWindow().setSoftInputMode(
                WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN
        );

        // Audio display
        sd.audioGraph = (AudioGraph)findViewById(R.id.audioGraph);
        sd.audioGraph.setOnInitCompleteListener(new AudioGraph.OnInitCompleteListener() {
            @Override
            public void onComplete() {
                sd.audioGraph.clearGraph();
                // Get edit file if passed on via intent
                if (!sd.strAudioEditFullFilename.isEmpty()) {
                    File fileEditFile = new File(sd.strAudioEditFullFilename);
                    if (fileEditFile.exists()) {
                        final WavFile wavFile = new WavFile(context);
                        wavFile.ReadFileAsync(fileEditFile, sd.audioSampleCurrent, new WavFile.OnReadWriteCompleteListener() {
                            @Override
                            public void onComplete(final boolean boolIsSuccess, final String strErrorMessage) {
                                runOnUiThread(new Runnable() {
                                    @Override
                                    public void run() {
                                        if (boolIsSuccess) {
                                            try {
                                                sd.intSampleRate = wavFile.getSampleRate();
                                                displayAudioSampleCurrent();
                                                sd.sm.setInitialState(ReadyWithSample);
                                                return;
                                            } catch (Exception e) {
                                                Toast.makeText(context, "Could not read wav file. " + e.getMessage(), Toast.LENGTH_SHORT).show();
                                            }
                                        } else {
                                            Toast.makeText(context, "Could not read wav file. " + strErrorMessage, Toast.LENGTH_SHORT).show();
                                        }
                                        sd.sm.setInitialState(ReadyWithNoSample);
                                        sd.audioGraph.setPageValue(sd.audioGraph.new PageValue(1, 0, 0, 0, 100));
                                    }
                                });
                            }
                        });

                    }  else {
                        Toast.makeText(context, "Source file does not exist", Toast.LENGTH_SHORT).show();
                        sd.sm.setInitialState(ReadyWithNoSample);
                        sd.audioGraph.setPageValue(sd.audioGraph.new PageValue(1,0,0,0,100));
                    }
                }
            }
        });

        sd.audioGraph.setOnScreenSizeChangedListener(new AudioGraph.OnScreenSizeChangedListener() {
            @Override
            public void onChanged(AudioGraph.PageValue pageValue) {
                if (sd.audioSampleCurrent.exists()) {
                    try {
                        sd.audioGraph.setPageSizeInMs(GRAPH_PAGE_SIZE_IN_MS); // This is done here because orientation can change any time
                        int intRmsFrameSizeInShorts = sd.audioGraph.getOptimalDataSampleBufferSizeInShorts(sd.intSampleRate);
                        int intPageSizeInRmsFrames = sd.audioGraph.getPageSizeInRmsFrames();
                        long lngStartRmsFrame = (long) ((pageValue.fltPageNum -1) * intPageSizeInRmsFrames);
                        int[] intGraphBuffer = sd.audioSampleCurrent.getGraphBuffer(lngStartRmsFrame, intPageSizeInRmsFrames, intRmsFrameSizeInShorts);
                        sd.audioGraph.updateGraph(intGraphBuffer);
                        sd.audioGraph.setPageValue(pageValue);
                    } catch (Exception e) {
                        Log.e(TAG, "Could not get graph values", e);
                    }
                }
            }
        });

        sd.audioGraph.setOnPageChangedListener(new AudioGraph.OnPageChangedListener() {
            @Override
            public void onChanged(AudioGraph.PageValue pageValue) {
                if (sd.audioSampleCurrent.exists()) {
                    try {
                        sd.audioGraph.setPageSizeInMs(GRAPH_PAGE_SIZE_IN_MS); // This is done here because orientation can change any time
                        int intRmsFrameSizeInShorts = sd.audioGraph.getOptimalDataSampleBufferSizeInShorts(sd.intSampleRate);
                        int intPageSizeInRmsFrames = sd.audioGraph.getPageSizeInRmsFrames();
                        long lngStartRmsFrame = (long) ((pageValue.fltPageNum -1) * intPageSizeInRmsFrames);
                        int[] intGraphBuffer = sd.audioSampleCurrent.getGraphBuffer(lngStartRmsFrame, intPageSizeInRmsFrames, intRmsFrameSizeInShorts);
                        sd.audioGraph.updateGraph(intGraphBuffer);
                    } catch (Exception e) {
                        Log.e(TAG, "Could not get graph values", e);
                    }
                }
            }
        });

        sd.audioGraph.setOnEndCursorChangedListener(new AudioGraph.OnEndCursorChangedListener() {
            @Override
            public int[] getSinglePageAfterCursorBuffer(double fltPercent) {
                int[] intGraphBuffer = null;
                try {

                    int intRmsFrameSizeInShorts = sd.audioGraph.getOptimalDataSampleBufferSizeInShorts(sd.intSampleRate);
                    int intPageSizeInRmsFrames = sd.audioGraph.getPageSizeInRmsFrames();
                    long lngStartRmsFrame = sd.audioGraph.percentToRmsFrame(fltPercent, sd.audioSampleCurrent.lngSizePcmInShorts, sd.intSampleRate);
                    intGraphBuffer = sd.audioSampleCurrent.getGraphBuffer(lngStartRmsFrame, intPageSizeInRmsFrames, intRmsFrameSizeInShorts);

                } catch (IOException e) {
                    e.printStackTrace();
                }
                return intGraphBuffer;
            }
        });

        // SkipToStartButton
        buttonSkipToStart.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                try {
                    // Calculate new PageValue
                    AudioGraph.PageValue pageValue = sd.audioGraph.getPageValue();
                    pageValue.fltPlayPercent = 0;
                    pageValue.fltPageNum = 1;
                    sd.audioGraph.setPageValue(pageValue);

                    // Get the accompanying buffer data
                    if (sd.audioSampleCurrent.exists()) {
                        int intRmsFrameSizeInShorts = sd.audioGraph.getOptimalDataSampleBufferSizeInShorts(sd.intSampleRate);
                        int intPageSizeInRmsFrames = sd.audioGraph.getPageSizeInRmsFrames();
                        long lngStartRmsFrame = (long) ((pageValue.fltPageNum -1) * intPageSizeInRmsFrames);
                        int[] intGraphBuffer = sd.audioSampleCurrent.getGraphBuffer(lngStartRmsFrame, intPageSizeInRmsFrames, intRmsFrameSizeInShorts);
                        sd.audioGraph.updateGraph(intGraphBuffer);
                    }

                    sd.audioGraph.invalidate();
                } catch (IOException e) {
                    Log.e(TAG, "Could not skip to end of timeline", e);
                }
            }

        });

        // Play button
        buttonPlay.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                switch ((State) sd.sm.getState()) {
                    case ReadyWithSample:
                        sd.sm.triggerEvent(Event.PlayPressed);
                        break;
                }
            }
        });

        // Record button
        buttonRecord.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {

                if (sd.sm.getState() == State.ReadyWithSample) {
                    // Warn user there can be some overwrite
                    if (!sd.audioGraph.isPlayCursorAtEndOfFile()) {
                        AlertDialog.Builder builder = new AlertDialog.Builder(context);
                        builder.setTitle("Based on your cursor settings you may overwrite some audio, are you sure?");
                        builder.setPositiveButton("OK", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialogInterface, int i) {
                                sd.sm.triggerEvent(Event.RecordPressed);
                            }
                        });
                        builder.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialogInterface, int i) {
                                return;
                            }
                        });
                        builder.show();
                    } else {
                        sd.sm.triggerEvent(Event.RecordPressed);
                    }
                } else {
                    switch ((State) sd.sm.getState()) {
                        case ReadyWithNoSample:
                            sd.sm.triggerEvent(Event.RecordPressed);
                            break;
                    }
                }
            }
        });

        buttonRecord.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                switch ((State) sd.sm.getState()) {
                    case ReadyWithNoSample:
                    case ReadyWithSample:
                        sd.sm.triggerEvent(Event.RecordPressed);
                        break;
                }
                return true;
            }
        });


        // Stop button
        buttonStop.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                switch ((State) sd.sm.getState()) {
                    case Recording:
                        sd.sm.triggerEvent(Event.RecordStopPressed);
                    case Playing:
                        sd.sm.triggerEvent(Event.PlayStopPressed);
                    default:
                        // Do nothing
                }
            }
        });

        // SkipToEndButton
        buttonSkipToEnd.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                try {
                    // Calculate new PageValue
                    AudioGraph.PageValue pageValue = sd.audioGraph.getPageValue();
                    pageValue = sd.audioGraph.updatePageValueToDisplayEndPage(pageValue, sd.audioSampleCurrent.lngSizePcmInShorts, sd.intSampleRate);
                    sd.audioGraph.setPageValue(pageValue);


                    // Get the accompanying buffer data
                    if (sd.audioSampleCurrent.exists()) {
                        int intRmsFrameSizeInShorts = sd.audioGraph.getOptimalDataSampleBufferSizeInShorts(sd.intSampleRate);
                        int intPageSizeInRmsFrames = sd.audioGraph.getPageSizeInRmsFrames();
                        long lngStartRmsFrame = (long) ((pageValue.fltPageNum -1) * intPageSizeInRmsFrames);
                        int[] intGraphBuffer = sd.audioSampleCurrent.getGraphBuffer(lngStartRmsFrame, intPageSizeInRmsFrames, intRmsFrameSizeInShorts);
                        sd.audioGraph.updateGraph(intGraphBuffer);
                    }
                    sd.audioGraph.invalidate();
                } catch (IOException e) {
                    Log.e(TAG, "Could not skip to end of timeline", e);
                }
            }
        });

        // Delete button
        buttonDelete.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                AlertDialog.Builder builder = new AlertDialog.Builder(context);
                builder.setTitle("Delete options?");
                builder.setPositiveButton("Complete\nrecording", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {

                        switch ((State) sd.sm.getState()) {
                            case ReadyWithSample:
                                sd.sm.triggerEvent(Event.ClearAll);
                                break;
                        }
                    }
                });
                builder.setNeutralButton("Between\ncursors", new DialogInterface.OnClickListener() {

                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        try {
                            // Merge shortened file
                            AudioGraph.PageValue pageValue = sd.audioGraph.getPageValue();
                            long lngStartCursorPositionInShort = sd.audioGraph.percentToShort(pageValue.fltStartPercent, sd.audioSampleCurrent.lngSizePcmInShorts, sd.intSampleRate);
                            long lngEndCursorPositionInShort = sd.audioGraph.percentToShort(pageValue.fltEndPercent, sd.audioSampleCurrent.lngSizePcmInShorts, sd.intSampleRate);
                            sd.audioSampleLeft = audioLib.new AudioSample(strAudioLeftFilenameWithoutExt, strAudioCurrentPlayFilenameWithoutExt);
                            sd.audioSampleLeft.trimRight(lngStartCursorPositionInShort*2);
                            sd.audioSampleRight = audioLib.new AudioSample(strAudioRightFilenameWithoutExt, strAudioCurrentPlayFilenameWithoutExt);
                            sd.audioSampleRight.trimLeft(lngEndCursorPositionInShort*2);
                            ArrayList<AudioSample> nameList = new ArrayList<AudioSample> ();
                            nameList.add(sd.audioSampleLeft);
                            nameList.add(sd.audioSampleRight);
                            sd.audioSampleCurrent.mergeInto(nameList);

                            // Refresh display
                            // Calculate new PageValue
                            AudioGraph.PageValue updatePageValue = sd.audioGraph.updatePageValueAfterDeletion(pageValue, sd.audioSampleCurrent.getDataAmountInRmsFrames(sd.audioGraph.getOptimalDataSampleBufferSizeInShorts(sd.intSampleRate)));
                            sd.audioGraph.setPageValue(updatePageValue);

                            // Get the accompanying buffer data
                            int intRmsFrameSizeInShorts = sd.audioGraph.getOptimalDataSampleBufferSizeInShorts(sd.intSampleRate);
                            int intPageSizeInRmsFrames = sd.audioGraph.getPageSizeInRmsFrames();
                            long lngStartRmsFrame = (long) ((updatePageValue.fltPageNum -1) * intPageSizeInRmsFrames);
                            int[] intGraphBuffer = sd.audioSampleCurrent.getGraphBuffer(lngStartRmsFrame, intPageSizeInRmsFrames, intRmsFrameSizeInShorts);
                            sd.audioGraph.updateGraph(intGraphBuffer);
                            sd.audioGraph.invalidate();

                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                });

                builder.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {

                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        // Do nothing
                    }
                });
                builder.show();
            }
        });
    }

    @Override
    public void onBackPressed() {

        sd.sm.triggerEvent(Event.BackPressed);

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Do you want to save before exiting?");
        builder.setPositiveButton("Save", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialogInterface, int i) {
                File fileEditFile = new File(sd.strAudioEditFullFilename);
                WavFile wavFile = new WavFile(context);
                wavFile.WriteFileAsync(sd.audioSampleCurrent, sd.intSampleRate, fileEditFile, new WavFile.OnReadWriteCompleteListener() {
                    @Override
                    public void onComplete(final boolean boolIsSuccess, final String strErrorMessage) {
                        runOnUiThread( new Runnable() {
                            @Override
                            public void run() {
                                if (boolIsSuccess) {
                                    setResult(RESULT_OK, getIntent());
                                    ActivityAudioRecorder.this.finish();
                                    ActivityAudioRecorder.super.onBackPressed();
                                } else {
                                    Toast.makeText(context,strErrorMessage, Toast.LENGTH_LONG).show();
                                }
                            }
                        });
                    }
                });
            }
        });
        builder.setNeutralButton("Exit", new DialogInterface.OnClickListener() {

            @Override
            public void onClick(DialogInterface dialogInterface, int i) {
                setResult(RESULT_CANCELED, getIntent());
                ActivityAudioRecorder.this.finish();
                ActivityAudioRecorder.super.onBackPressed();
            }
        });

        builder.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {

            @Override
            public void onClick(DialogInterface dialog, int which) {
                // Do nothing
            }
        });
        builder.show();
    }

    private void displayAudioSampleCurrent() throws IOException {
        sd.audioGraph.setPageSizeInMs(GRAPH_PAGE_SIZE_IN_MS); // This is done here because orientation can change any time
        int intRmsFrameSizeInShorts = sd.audioGraph.getOptimalDataSampleBufferSizeInShorts(sd.intSampleRate);
        int intPageSizeInRmsFrames = sd.audioGraph.getPageSizeInRmsFrames();
        long lngStartRmsFrame = 0;
        int[] intGraphBuffer = sd.audioSampleCurrent.getGraphBuffer(lngStartRmsFrame, intPageSizeInRmsFrames, intRmsFrameSizeInShorts);
        sd.audioGraph.updateGraph(intGraphBuffer);
        int intDataAmountInRmsFrames = sd.audioSampleCurrent.getDataAmountInRmsFrames(intRmsFrameSizeInShorts);
        sd.audioGraph.setPageValue(sd.audioGraph.new PageValue(1,intDataAmountInRmsFrames,0,0,100));
    }


    interface OnRecordingComplete {
        void onComplete(boolean boolIsSuccess, String strErrorMessage);
    }

    void startRecording(final OnRecordingComplete onRecordingComplete) {
        // Do work

        sd.audioGraph.updatePageValueBeforeRecording(); // Align the cursors and page
        sd.fltPlayPercentBeforeRecording = sd.audioGraph.getPageValue().fltPlayPercent;
        sd.fltEndPercentBeforeRecording = sd.audioGraph.getPageValue().fltEndPercent;

        sd.recordAsyncTask  = new RecordAsyncTask(sd.intSampleRate, AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT, new OnRecordingComplete() {
            @Override
            public void onComplete(boolean boolIsSuccess, String strErrorMessage) {
                onRecordingComplete.onComplete(boolIsSuccess, strErrorMessage);
            }

        });

        sd.recordAsyncTask.execute(null,null,null);
    }

    public class RecordAsyncTask extends AsyncTask<Void, int[], Void> {

        int frequency;
        int channelConfiguration;
        int audioEncoding;
        OnRecordingComplete onRecordingComplete;
        private ProgressProxy progress;


        public RecordAsyncTask(int frequency, int channelConfiguration, int audioEncoding,
                                  OnRecordingComplete onRecordingComplete) {
            this.frequency = frequency;
            this.channelConfiguration = channelConfiguration;
            this.audioEncoding = audioEncoding;
            this.onRecordingComplete = onRecordingComplete;
            this.progress = new ProgressProxy(this);
        }

        @Override
        protected Void doInBackground(Void... params) {
            try {
                conductRecording(progress);
                onRecordingComplete.onComplete(true, null);
            } catch (Exception e) {
                Log.e(TAG,"Record error", e);
                onRecordingComplete.onComplete(false, e.getMessage());
            }
            return null;
        }

        @Override
        protected void onCancelled(Void aVoid) {
            super.onCancelled(aVoid);
            onRecordingComplete.onComplete(false, "Cancelled by user");
        }

        @Override
        protected void onProgressUpdate(int[]... values) {
            if (values.length != 0) {
                try {
                    AudioGraph.PageValue pageValueBefore = sd.audioGraph.getPageValue();
                    AudioGraph.PageValue pageValueAfter = sd.audioGraph.updateGraph(values[0], pageValueBefore);
                    sd.audioGraph.setPageValue(pageValueAfter);
                    Log.d(TAG,"PlayPercent=" + pageValueAfter.fltPlayPercent);
                    Log.d(TAG,"DataPercent=" + sd.audioGraph.getDataAmountAsPercent());
                    Log.d(TAG,"DataAmount=" + sd.audioGraph.getPageValue().lngDataAmountInRmsFrames);
                    sd.audioGraph.invalidate();
                } catch (Exception e) {
                    Log.e(TAG,"Record error", e);
                }
            }
        }

        public class ProgressProxy {
            private RecordAsyncTask task;
            public ProgressProxy(RecordAsyncTask task) {
                this.task = task;
            }

            public void callPublishProgress(int[] data) {
                task.publishProgress(data);
            }
        }
    }

    private void conductRecording(RecordAsyncTask.ProgressProxy progressProxy) {



        sd.isRecording = true;
        File file = sd.audioSampleInsert.filePathPcm;

        try {
            // Save data to Insert file
            file.createNewFile();

            OutputStream outputStream = new FileOutputStream(file);
            BufferedOutputStream bufferedOutputStream = new BufferedOutputStream(outputStream);
            DataOutputStream dataOutputStream = new DataOutputStream(bufferedOutputStream);

            int minBufferSize = AudioRecord.getMinBufferSize(sd.intSampleRate, AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT);

            short[] audioData = new short[minBufferSize/2];

            AudioRecord audioRecord = new AudioRecord(MediaRecorder.AudioSource.MIC, sd.intSampleRate,
                    AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, minBufferSize);

            audioRecord.startRecording();

            int intRmsDataBufferSize = sd.audioGraph.getOptimalDataSampleBufferSizeInShorts(sd.intSampleRate);
            int[] intRmsBuffer = new int[intRmsDataBufferSize];
            int[] intRmsData = new int[10];
            int intRmsBufferIndex = 0;
            int intRmsDataIndex = 0;
            double sum = 0;

            boolean boolStopRecording = false;
            while (!boolStopRecording) {
                int numberOfShort = audioRecord.read(audioData, 0, audioData.length);

                for (int i = 0; i < numberOfShort; i++) {
                    dataOutputStream.writeShort(audioData[i]);
                    intRmsBuffer[intRmsBufferIndex] = audioData[i];
                    intRmsBufferIndex += 1;
                    if ((intRmsBufferIndex == intRmsBuffer.length)){
                        sum = 0;
                        for (int j = 0; j < intRmsBufferIndex; j++) {
                            sum += intRmsBuffer[j] * intRmsBuffer[j];
                        }
                        double amplitude = sum / intRmsBufferIndex;
                        intRmsData[intRmsDataIndex] = ((int) Math.sqrt(amplitude));
                        intRmsBufferIndex = 0;
                        intRmsDataIndex += 1;
                        if (intRmsDataIndex == intRmsData.length) {
                            progressProxy.callPublishProgress(intRmsData);
                            intRmsDataIndex = 0;
                            if (!sd.isRecording) {
                                // Ensure the amount of data in the audioBuffer ties up with the Rms frames in the graph
                                boolStopRecording = true;
                            }
                        }
                    }
                    if (boolStopRecording) {
                       break;
                    }
                }
            }

            audioRecord.stop();
            dataOutputStream.close();
            bufferedOutputStream.close();
            outputStream.close();

            sd.audioSampleInsert.updateFileSize();

            // Merge the result
            mergeAudio();



        } catch (IOException e) {
            Log.e(TAG, "Recording failed", e);
        }

    }

    public void mergeAudio() throws IOException {

        // Overwrite everything after play cursor with new data, appending everything to it after the end cursor
        if ( sd.fltPlayPercentBeforeRecording == 0) {
            // Play cursor is at start, so insert sample gets used as root sample
            sd.audioSampleLeft = audioLib.new AudioSample(strAudioLeftFilenameWithoutExt);
            sd.audioSampleLeft.clear();
        } else {
            // Play cursor is later, so insert sample gets appended after cursor
            // Strip all to right of cursor
            long lngPlayCursorStartInBytes = sd.audioGraph.percentToByte(sd.fltPlayPercentBeforeRecording, sd.audioSampleCurrent.lngSizePcmInShorts, sd.intSampleRate);
            sd.audioSampleLeft = audioLib.new AudioSample(strAudioLeftFilenameWithoutExt, strAudioCurrentPlayFilenameWithoutExt);
            sd.audioSampleLeft.trimRight(lngPlayCursorStartInBytes);
        }
        // Use whatever is on the right side of the end cursor
        if (sd.audioSampleCurrent.exists()) {
            if ( sd.audioGraph.isPercentEndOfFile(sd.fltEndPercentBeforeRecording)) {
                // End cursor is at end, so insert sample does not get anything added at end
                sd.audioSampleRight = audioLib.new AudioSample(strAudioRightFilenameWithoutExt);
                sd.audioSampleRight.clear();
            } else {
                // End cursor is not at end, so insert sample does get something added at end
                // Get that "something"
                long lngEndCursorPosInBytes = sd.audioGraph.percentToByte(sd.fltEndPercentBeforeRecording, sd.audioSampleCurrent.lngSizePcmInShorts, sd.intSampleRate);
                sd.audioSampleRight = audioLib.new AudioSample(strAudioRightFilenameWithoutExt, strAudioCurrentPlayFilenameWithoutExt);
                sd.audioSampleRight.trimLeft(lngEndCursorPosInBytes);
            }
        } else {
            sd.audioSampleRight = audioLib.new AudioSample(strAudioRightFilenameWithoutExt);
            sd.audioSampleRight.clear();
        }

        // Merge together
        ArrayList<AudioSample> audioSamples = new ArrayList<AudioSample>();
        audioSamples.add(sd.audioSampleLeft);
        audioSamples.add(sd.audioSampleInsert);
        audioSamples.add(sd.audioSampleRight);
        sd.audioSampleCurrent.mergeInto(audioSamples);

    }

    interface OnPlayComplete {
        void onComplete(boolean boolIsSuccess, String strErrorMessage);
    }

    void startPlay(final OnPlayComplete onPlayComplete) {
        // Do work
        sd.playBackAsyncTask = new PlayBackAsyncTask(sd.intSampleRate, AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT, new OnPlayComplete() {

            @Override
            public void onComplete(boolean boolIsSuccess, String strErrorMessage) {
                onPlayComplete.onComplete(boolIsSuccess, strErrorMessage);
            }

        });
        sd.playBackAsyncTask.execute(null,null,null);

    }

	public class PlayBackAsyncTask extends AsyncTask<Void, Integer, Void> {

		int frequency;
		int channelConfiguration;
		int audioEncoding;
        OnPlayComplete onPlayComplete;
		private ProgressProxy progress;


		public PlayBackAsyncTask(int frequency, int channelConfiguration, int audioEncoding,
                                    OnPlayComplete onPlayComplete) {
			this.frequency = frequency;
			this.channelConfiguration = channelConfiguration;
			this.audioEncoding = audioEncoding;
			this.onPlayComplete = onPlayComplete;
			this.progress = new ProgressProxy(this);
		}

		@Override
		protected Void doInBackground(Void... params) {
			try {
                conductPlayBack(progress);
			} catch (Exception e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
                onPlayComplete.onComplete(false, e.getMessage());
			}
			return null;
		}

        @Override
        protected void onPostExecute(Void aVoid) {
            super.onPostExecute(aVoid);
            // If exit due to file end reached, ensure the play pointer is exactly at the end
            if (sd.isPlaying) {
                sd.audioGraph.setPlayCursorToEndOfFile();
            }
            sd.isPlaying = false; // Only set it here because its still used before
            onPlayComplete.onComplete(true, null);
        }

        @Override
		protected void onProgressUpdate(final Integer... intNewlyPlayedRmsFrames) {
            if (intNewlyPlayedRmsFrames.length != 0) {
                // sd.audioGraph.updateGraph(values[0], false);
                try {
                    AudioGraph.PageValue pageValueBefore = sd.audioGraph.getPageValue();
                    AudioGraph.PageValue pageValueAfter = sd.audioGraph.updatePageValueWhilePlayback(pageValueBefore, intNewlyPlayedRmsFrames[0]);
                    if (pageValueAfter.getCurrentPage() != pageValueBefore.getCurrentPage() ) {
                        // Page changed, get new buffer data from file and display
                        // Get the accompanying buffer data
                        int intRmsFrameSizeInShorts = sd.audioGraph.getOptimalDataSampleBufferSizeInShorts(sd.intSampleRate);
                        int intPageSizeInRmsFrames = sd.audioGraph.getPageSizeInRmsFrames();
                        long lngStartRmsFrame = (long) ((pageValueAfter.fltPageNum -1) * intPageSizeInRmsFrames);
                        int[] intGraphBuffer = sd.audioSampleCurrent.getGraphBuffer(lngStartRmsFrame, intPageSizeInRmsFrames, intRmsFrameSizeInShorts);
                        sd.audioGraph.updateGraph(intGraphBuffer);
                    }
                    // Set the PageValue to update cursors
                    sd.audioGraph.setPageValue(pageValueAfter);
                    sd.audioGraph.invalidate();

                } catch (Exception e) {
                    Log.e(TAG,"Record error", e);
                }
            }
		}

        @Override
        protected void onCancelled() {
            super.onCancelled();
            if(sd.audioTrack != null) {
                sd.audioTrack.flush();
                sd.audioTrack.stop();
                sd.audioTrack.release();
            }
        }

        public class ProgressProxy {
			private PlayBackAsyncTask task;
			public ProgressProxy(PlayBackAsyncTask task) {
				this.task = task;
			}

            public void callPublishProgress(int data) {
                task.publishProgress(data);
            }
		}

	}

	public void conductPlayBack(PlayBackAsyncTask.ProgressProxy progressProxy){

        // Determine where to start in file
        int intSkipPositionInBytes = (int) sd.audioGraph.percentToByte(sd.audioGraph.getPageValue().fltPlayPercent, sd.audioSampleCurrent.lngSizePcmInShorts, sd.intSampleRate);

	    int minBufferSize = AudioTrack.getMinBufferSize(sd.intSampleRate,
                AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT);

	    sd.audioTrack = new AudioTrack(AudioManager.STREAM_MUSIC, sd.intSampleRate,
				AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT, minBufferSize, AudioTrack.MODE_STREAM);

	    int i = 0;
	    int intDataBufferSize = sd.audioGraph.getOptimalDataSampleBufferSizeInShorts(sd.intSampleRate) * 2;
	    byte[] bDataBuffer = new byte[intDataBufferSize];
        int intRmsFrameCount = 0;
        int RMS_FRAME_AMOUNT_TO_TRIGGER_PROGRESS_UPDATE = 10;

	    try {
	    	File file = sd.audioSampleCurrent.filePathPcm;
	        FileInputStream fin = new FileInputStream(file.getAbsoluteFile());
	        DataInputStream dis = new DataInputStream(fin);
            dis.skipBytes(intSkipPositionInBytes);


	        sd.audioTrack.play();
            sd.isPlaying = true;

	        while((i = dis.read(bDataBuffer, 0, intDataBufferSize)) > -1){
                // Send to audio device - because buffer size is same optimal, each full buffer read accounts for one RMS frame
                short[] s = byte2short(bDataBuffer);
                sd.audioTrack.write(s, 0, i/2);

                // Call progress updater if a pre-determined amount of RMS frames has been played
                intRmsFrameCount += 1;
                if (intRmsFrameCount == RMS_FRAME_AMOUNT_TO_TRIGGER_PROGRESS_UPDATE) {
                    progressProxy.callPublishProgress(intRmsFrameCount);
                    intRmsFrameCount = 0;

                    // Break if needed, placed here to ensure always a full frameCount is displayed
                    if (!sd.isPlaying) {
                        break;
                    }
                }

            }

            sd.audioTrack.flush();
	        sd.audioTrack.stop();
	        sd.audioTrack.release();
	        dis.close();
	        fin.close();


	    } catch (FileNotFoundException e) {
	        e.printStackTrace();
	    } catch (IOException e) {
	        e.printStackTrace();
	    }
	}

	// Convert bytes to shorts
	private short[] byte2short(byte[] sData) {
		byte[] bytes = sData;
		short[] shorts = new short[bytes.length/2];
		// to turn bytes to shorts as either big endian or little endian.
		ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN).asShortBuffer().get(shorts);
		return shorts;
	}


    public int[] accumulateBuffer(int[] intRootBuffer, int[] intAppendBuffer) {
        int[] destination;
        if (intRootBuffer != null) {
            destination = new int[intRootBuffer.length + intAppendBuffer.length];
            for (int i = 0; i < intRootBuffer.length; i++) {
                destination[i] = intRootBuffer[i];
            }
            for (int j = 0; j < intAppendBuffer.length; j++) {
                destination[j + intRootBuffer.length] = intAppendBuffer[j];
            }
        } else {
            destination = new int[intAppendBuffer.length];
            for (int j = 0; j < intAppendBuffer.length; j++) {
                destination[j] = intAppendBuffer[j];
            }
        }
        return destination;
    }

    // Convert short to byte
    private byte[] short2byte(short[] sData) {
        int intArraySize = sData.length;
        byte[] bytes = new byte[intArraySize * 2];
        for (int i = 0; i < intArraySize; i++) {
            bytes[i * 2] = (byte) (sData[i] & 0x00FF);
            bytes[(i * 2) + 1] = (byte) (sData[i] >> 8);
            sData[i] = 0;
        }
        return bytes;

    }

    /**
     * Not used, was used during development
     * @param item
     */
    public void onMenuImportClicked(MenuItem item) {
        WavFile wavFile = new WavFile(this);
        File fileInput = new File( sd.strWorkFolderFullPath + "/WavTestFile.wav");
        if (!fileInput.exists()) {
            Toast.makeText(this, "File " + fileInput.toString() + " does not exist", Toast.LENGTH_LONG).show();
        }
        try {
            wavFile.ReadFile(fileInput, sd.audioSampleCurrent);
            sd.audioGraph.clearGraph();
            displayAudioSampleCurrent();

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void onMenuExportClicked(MenuItem item) {
        WavFile wavFile = new WavFile(this);
        File fileOutput = new File( sd.strWorkFolderFullPath + "/WavTestFileOut.wav");
        if (fileOutput.exists()) {
            fileOutput.delete();
        }
        try {
            wavFile.WriteFile(sd.audioSampleCurrent,sd.intSampleRate,fileOutput);
            Toast.makeText(this,"File exported",Toast.LENGTH_LONG).show();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void onMenuSettingsClicked(MenuItem item) {
        Intent intent = new Intent(this,ActivityPreferences.class);
        startActivityForResult(intent, GET_PREFERENCES);
    }

    public void onMenuCreateTestSignalClicked(MenuItem item) {
        sd.audioSampleCurrent.createTestSignal();
        sd.audioGraph.clearGraph();
        try {
            displayAudioSampleCurrent();
        } catch (IOException e) {
            e.printStackTrace();
        }

    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode == RESULT_OK) {
            if (requestCode == GET_PREFERENCES) {
                int intNewSampleRate = getSampleRate();
                if (sd.intSampleRate != intNewSampleRate) {
                    // Clear current capture
                    sd.audioGraph.clearGraph();
                    sd.audioSampleCurrent.clear();
                    sd.intSampleRate = intNewSampleRate;
                }
            }
        }
    }
}
