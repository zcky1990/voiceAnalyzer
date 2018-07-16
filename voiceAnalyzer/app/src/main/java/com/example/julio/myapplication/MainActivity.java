package com.example.julio.myapplication;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioRecord;
import android.media.AudioTrack;
import android.media.MediaRecorder;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;

import com.jjoe64.graphview.GraphView;
import com.jjoe64.graphview.series.DataPoint;
import com.jjoe64.graphview.series.LineGraphSeries;

import java.io.DataInputStream;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;

public class MainActivity extends AppCompatActivity {
    EditText outputText;
    private static final int REQUEST_RECORD_AUDIO_PERMISSION = 200;
    private static String mFileName = null;
    private boolean permissionToRecordAccepted = false;

    private int minSize = 0;
    AudioTrack track;
    AudioRecord audioInput;
    boolean isRecording = false;
    private Thread recordingThread = null;

    int BytesPerElement = 2; // 2 bytes in 16bit format

    double[] toTransform;
    GraphView graph;

    private int blockSize = /*2048;// = */256;
    private int frequency = 44100; //44100hz

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        switch (requestCode){
            case REQUEST_RECORD_AUDIO_PERMISSION:
                permissionToRecordAccepted  = grantResults[0] == PackageManager.PERMISSION_GRANTED;
                break;
        }
        if (!permissionToRecordAccepted ) finish();
    }

    private void checkPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (!(ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED)) {
                Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.parse("package:" + getPackageName()));
                startActivity(intent);
                finish();
            }
        }
    }

    //====================
    private void startRecording() {
        audioInput = new AudioRecord(MediaRecorder.AudioSource.MIC, frequency, AudioFormat.CHANNEL_CONFIGURATION_MONO, AudioFormat.ENCODING_PCM_16BIT, minSize);
        audioInput.startRecording();
        isRecording = true;

        recordingThread = new Thread(new Runnable() {
            public void run() {
                writeAudioDataToFile();
            }
        }, "AudioRecorder Thread");
        recordingThread.start();
    }
    //Conversion of short to byte
    private byte[] short2byte(short[] sData) {
        int shortArrsize = sData.length;
        byte[] bytes = new byte[shortArrsize * 2];
        for (int i = 0; i < shortArrsize; i++) {
            bytes[i * 2] = (byte) (sData[i] & 0x00FF);
            bytes[(i * 2) + 1] = (byte) (sData[i] >> 8);
            sData[i] = 0;
        }
        return bytes;
    }

    private void writeAudioDataToFile() {
        short sData[] = new short[blockSize];
        toTransform = new double[blockSize];

        FileOutputStream os = null;
        try {
            os = new FileOutputStream(mFileName);
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
        int bufferReadResult;


        while (isRecording) {
            //this is where the graph is created
            bufferReadResult = audioInput.read(sData, 0, blockSize);
            for (int i = 0; i < blockSize && i < bufferReadResult; i++) {
                toTransform[i] = (double) sData[i] / 32768.0; // signed 16 bit
            }

            try {
                byte bData[] = short2byte(sData);
                os.write(bData, 0, blockSize * BytesPerElement);

            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        try {
            os.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void stopRecording() {
        DataPoint[] data = new DataPoint[blockSize];
        for(int i = 0 ; i < toTransform.length ; i++){
            data[i] = new DataPoint(i, toTransform[i]);
        }
        LineGraphSeries<DataPoint> series = new LineGraphSeries<>(data);
        graph.addSeries(series);
        if (null != audioInput) {
            isRecording = false;
            audioInput.stop();
            audioInput.release();
            audioInput = null;
            recordingThread = null;
        }
    }

    public void playAudio(){
        track = new AudioTrack(AudioManager.STREAM_MUSIC,
                frequency, AudioFormat.CHANNEL_CONFIGURATION_MONO,
                AudioFormat.ENCODING_PCM_16BIT, minSize,
                AudioTrack.MODE_STREAM);
        int i = 0;
        byte[] s = new byte[blockSize];
        try {
            FileInputStream fin = new FileInputStream(mFileName);
            DataInputStream dis = new DataInputStream(fin);
            track.play();
            while((i = dis.read(s, 0, blockSize)) > -1){
                track.write(s, 0, i);
            }
            track.stop();
            track.release();
            dis.close();
            fin.close();
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mFileName = getExternalCacheDir().getAbsolutePath();
        mFileName += "/audiorecordtest.pcm";

        checkPermission();
        final EditText editText = findViewById(R.id.processText);
        outputText = findViewById(R.id.outputText);

        minSize = AudioTrack.getMinBufferSize(frequency, AudioFormat.CHANNEL_CONFIGURATION_MONO, AudioFormat.ENCODING_PCM_16BIT);

        final Button speakBtn = findViewById(R.id.speakBtn);
        speakBtn.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {
              if(isRecording == false){
                  speakBtn.setText("STOP");
                  editText.setText("");
                  editText.setHint("Listening...");
                  startRecording();
              }else{
                  editText.setHint("You will see input here");
                  speakBtn.setText("START");
                  stopRecording();
              }
            }
        });

        Button playBtn = findViewById(R.id.playBtn);
        playBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                    playAudio();
            }
        });

        graph = (GraphView) findViewById(R.id.graph);
        GraphView graph = (GraphView) findViewById(R.id.graph);
    }
}
