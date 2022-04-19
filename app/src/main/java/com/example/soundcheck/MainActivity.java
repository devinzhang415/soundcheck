package com.example.soundcheck;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.Bundle;
import android.os.Vibrator;
import android.util.Log;
import android.view.View;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import java.util.ArrayList;
import java.util.Arrays;

public class MainActivity extends AppCompatActivity {

    private static final int AUDIO_SOURCE = MediaRecorder.AudioSource.VOICE_RECOGNITION;
    private static final int SAMPLE_RATE = 44100; // DEFAULT
    private static final int CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO;
    private static final int AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT;
    private static final int BUFFER_SIZE = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT);
    private static final int REQUEST_CODE = 0;
    private static final int DATA_OFFSET = 0;

    private static final int AVERAGE_PERIOD = 10;
//    private static final double MIN_THRESHOLD = 102;
    private static final double MIN_THRESHOLD = 110;
//    private static final double VOLUME_THRESHOLD_PERCENTAGE = 1.1;
    private static final double VOLUME_THRESHOLD_PERCENTAGE = 1.18;
//    private static final double VOLUME_THRESHOLD_PERCENTAGE = 1.65;
    private static final double PAD_VOLUME = 40;

    private static final int VIBE_PERIOD = 200;

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
                            ArrayList<Double> volumes = new ArrayList<>(
                                    Arrays.asList(100.0, 100.0, 100.0, 100.0, 100.0, 100.0, 100.0, 100.0, 100.0, 100.0));
                            while (isRecording) {
                                double volume = getVolume(mic, buffer);
                                double threshold = Math.max(MIN_THRESHOLD, getAverageVolume(volumes) * VOLUME_THRESHOLD_PERCENTAGE);

                                if (volume > threshold) {
                                    vibe.vibrate(VIBE_PERIOD);
                                    Log.d("DEBUG", "TRIGGER");
                                }

                                if (volume == 0) {
                                    volumes.add(PAD_VOLUME);
                                } else {
                                    volumes.add(volume);
                                }

                                Log.d("DEBUG", String.valueOf(threshold) + " " + String.valueOf(volume));
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
        if (Double.isNaN(volume)) volume = 0;
        return volume;
    }

    private double getAverageVolume(ArrayList<Double> volumes) {
        double average = 0;
        for (int i = volumes.size() - 1; i >= Math.max(0, volumes.size() - 1 - AVERAGE_PERIOD); i--) {
            average += volumes.get(i);
        }
        average /= AVERAGE_PERIOD;
        return average;
    }
}