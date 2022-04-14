package com.example.soundcheck;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;

import android.Manifest;
import android.content.pm.PackageManager;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.Bundle;
import android.util.Log;
import android.view.View;

import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.util.ArrayList;

public class MainActivity extends AppCompatActivity {

    private static final int AUDIO_SOURCE = MediaRecorder.AudioSource.MIC;
    private static final int SAMPLE_RATE = 8000;
    private static final int CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_DEFAULT;
    private static final int AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT;
    private static final int BUFFER_SIZE = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT);
    private static final int REQUEST_CODE = 0;
    private static final int DATA_OFFSET = 0;
    private static final int AVERAGE_NUMBER = 10;

    private static boolean isRecording = false;
    private static Thread listeningThread;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.RECORD_AUDIO}, REQUEST_CODE);
        } else {
            AudioRecord mic = new AudioRecord(AUDIO_SOURCE, SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT, BUFFER_SIZE);

            NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                    .setSmallIcon(R.drawable.notification_icon)
                    .setContentTitle(textTitle)
                    .setContentText(textContent)
                    .setPriority(NotificationCompat.PRIORITY_DEFAULT);

            FloatingActionButton fab = findViewById(R.id.microphone_button);
            fab.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    if (isRecording) {
                        isRecording = false;
                        mic.stop();
                        listeningThread.interrupt();
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
                                    for (int i = volHistory.size() - 1; i >= Math.max(0, volHistory.size() - 1 - AVERAGE_NUMBER); i--) {
                                        average += volHistory.get(i);
                                    }
                                    average /= AVERAGE_NUMBER;

                                    Log.d("DEBUG LOG", String.valueOf(average));
                                }
                            }
                        }, "AudioRecorder Thread");
                        listeningThread.start();
                    }
                }
            });
        }
    }

    private double getVolume(AudioRecord mic, short[] buffer) {
        int read = mic.read(buffer, DATA_OFFSET, BUFFER_SIZE);
        long v = 0;
        for (int i = 0; i < buffer.length; i++) {
            v += buffer[i] * buffer[i];
        }
        double volume = 10 * Math.log10((double) v / read);
        return volume;
    }
}