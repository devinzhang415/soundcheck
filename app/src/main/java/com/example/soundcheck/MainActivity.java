package com.example.soundcheck;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.media.audiofx.NoiseSuppressor;
import android.os.Bundle;
import android.os.Vibrator;
import android.util.Log;
import android.view.View;

import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.util.ArrayList;

public class MainActivity extends AppCompatActivity {

    private static final int AUDIO_SOURCE = MediaRecorder.AudioSource.MIC;
    private static final int SAMPLE_RATE = 44100; // DEFAULT
    private static final int CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_DEFAULT;
    private static final int AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT;
    private static final int BUFFER_SIZE = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT);
    private static final int REQUEST_CODE = 0;
    private static final int DATA_OFFSET = 0;

    private static final int AVERAGE_DIVISOR = 15;
    private static final int WAIT_PERIOD = 100;
    private static final int VIBE_PERIOD = 300;
    private static final int VOLUME_THRESHOLD = 40;

    private static boolean isRecording = false;
    private static Thread listeningThread;
    private static final Object Lock = new Object();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        FloatingActionButton fab = findViewById(R.id.microphone_button);

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.RECORD_AUDIO}, REQUEST_CODE);
        }
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.VIBRATE) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.VIBRATE}, REQUEST_CODE);
        }

        AudioRecord mic = new AudioRecord(AUDIO_SOURCE, SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT, BUFFER_SIZE);
        Vibrator vibe = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
        if (NoiseSuppressor.isAvailable()) {
            NoiseSuppressor suppressor = NoiseSuppressor.create(mic.getAudioSessionId());
        }

        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (isRecording) {
                    isRecording = false;
                } else {
                    isRecording = true;
                    mic.startRecording();

                    listeningThread = new Thread(new Runnable() {
                        public void run() {
                            short[] buffer = new short[BUFFER_SIZE];
                            ArrayList<Double> volHistory = new ArrayList<>();
                            while (isRecording) {
                                double volume = getVolume(mic, buffer);
                                volHistory.add(volume);

                                double average = 0;
                                for (int i = volHistory.size() - 1; i >= Math.max(0, volHistory.size() - 1 - AVERAGE_DIVISOR); i--) {
                                    average += volHistory.get(i);
                                }
                                average /= AVERAGE_DIVISOR;

                                if (average > VOLUME_THRESHOLD) {
                                    vibe.vibrate(VIBE_PERIOD);
                                }
                            }
                            mic.stop();
                            listeningThread.interrupt();
                        }
                    }, "AudioRecorder Thread");
                    listeningThread.start();
                }
            }
        });
    }

    private double getVolume(AudioRecord mic, short[] buffer) {
        int read = mic.read(buffer, DATA_OFFSET, BUFFER_SIZE);
        int v = 0;
        for (short s : buffer) {
            v += s * s;
        }
        double volume = 20 * Math.log10((double) v / read);
        synchronized (Lock) {
            try {
                Lock.wait(WAIT_PERIOD);
            } catch (Exception e) {

            }
        }
        return volume;
    }
}