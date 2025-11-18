package com.example.sounds;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.content.res.AssetFileDescriptor;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.media.SoundPool;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.util.Log;
import androidx.core.app.NotificationCompat;
import org.tensorflow.lite.Interpreter;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.util.Calendar;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;

public class SoundscapeService extends Service {

    private static final String TAG = "SoundscapeService";
    private static final int NOTIFICATION_ID = 1;
    private static final String CHANNEL_ID = "SoundscapeServiceChannel";

    public static final String ACTION_PLAY = "com.example.sounds.action.PLAY";
    public static final String ACTION_STOP = "com.example.sounds.action.STOP";
    public static final String ACTION_SET_MODE = "com.example.sounds.action.SET_MODE";
    public static final String EXTRA_MODE = "com.example.sounds.extra.MODE";

    private MediaPlayer rainPlayer;
    private MediaPlayer windPlayer;
    private SoundPool soundPool;
    private boolean audioLoaded = false;
    private int birdSoundId;
    private int thunderSoundId;

    private Interpreter tflite;
    private final String MODEL_FILE_NAME = "model.tflite";
    private final float[][] tfliteInputs = new float[1][2];
    private final float[][] tfliteOutputs = new float[1][4];

    private final Handler soundLoopHandler = new Handler();
    private final Random random = new Random();
    private final int LOOP_INTERVAL_MS = 5000;

    // --- State Management ---
    private boolean isPlaying = false;
    private boolean isPendingPlay = false; // <-- FIX: New flag for the race condition
    private int currentMode = 1;

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "Service creating...");
        try {
            setupAudio();
            setupTFLiteModel();
        } catch (IOException e) {
            Log.e(TAG, "Failed to setup service", e);
            stopSelf();
        }
        createNotificationChannel();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null || intent.getAction() == null) {
            return START_NOT_STICKY;
        }

        String action = intent.getAction();
        Log.d(TAG, "Received action: " + action);

        switch (action) {
            case ACTION_PLAY:
                isPendingPlay = true;
                startAudioIfReady(); // Try to start immediately
                break;
            case ACTION_STOP:
                stopSoundscapeLoop();
                break;
            case ACTION_SET_MODE:
                currentMode = intent.getIntExtra(EXTRA_MODE, 1);
                if (isPlaying) {
                    soundLoopHandler.removeCallbacks(soundscapeRunnable);
                    soundscapeRunnable.run();
                }
                break;
        }

        return START_NOT_STICKY;
    }

    private void startAudioIfReady() {
        // This is the single entry point to starting the audio loop.
        // It will only run if the audio is loaded AND the user has requested to play.
        if (!audioLoaded || !isPendingPlay || isPlaying) {
            return; // Not ready, not requested, or already playing
        }

        isPlaying = true;
        isPendingPlay = false; // Reset the flag
        Log.d(TAG, "Starting sound loop.");

        startForeground(NOTIFICATION_ID, createNotification("Playing soundscape..."));

        if (rainPlayer != null && !rainPlayer.isPlaying()) {
            rainPlayer.start();
        }
        if (windPlayer != null && !windPlayer.isPlaying()) {
            windPlayer.start();
        }
        soundLoopHandler.post(soundscapeRunnable);
    }

    private void stopSoundscapeLoop() {
        isPendingPlay = false; // A stop command should also clear any pending play requests.
        if (!isPlaying) return;

        isPlaying = false;
        Log.d(TAG, "Stopping sound loop.");
        soundLoopHandler.removeCallbacks(soundscapeRunnable);

        if (rainPlayer != null) {
            rainPlayer.setVolume(0, 0);
        }
        if (windPlayer != null) {
            windPlayer.setVolume(0, 0);
        }
        if (soundPool != null) {
            soundPool.autoPause();
        }

        stopForeground(true);
        stopSelf();
    }

    private void setupAudio() {
        AudioAttributes audioAttributes = new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build();

        soundPool = new SoundPool.Builder().setMaxStreams(2).setAudioAttributes(audioAttributes).build();

        final AtomicInteger soundLoadCount = new AtomicInteger(0);
        soundPool.setOnLoadCompleteListener((sp, sampleId, status) -> {
            if (status == 0) {
                if (soundLoadCount.incrementAndGet() == 2) {
                    audioLoaded = true;
                    Log.d(TAG, "All SoundPool sounds loaded.");
                    startAudioIfReady(); // Audio is ready, try to start if the user already clicked play.
                }
            } else {
                Log.e(TAG, "Error loading sound ID: " + sampleId);
            }
        });

        birdSoundId = soundPool.load(this, R.raw.birds_clip1, 1);
        thunderSoundId = soundPool.load(this, R.raw.thunder_clip1, 1);

        rainPlayer = MediaPlayer.create(this, R.raw.rain_loop);
        if (rainPlayer != null) {
            rainPlayer.setLooping(true);
            rainPlayer.setVolume(0, 0);
        } else {
            Log.e(TAG, "Error: Failed to create MediaPlayer for rain_loop.");
        }

        windPlayer = MediaPlayer.create(this, R.raw.wind_loop);
        if (windPlayer != null) {
            windPlayer.setLooping(true);
            windPlayer.setVolume(0, 0);
        } else {
            Log.e(TAG, "Error: Failed to create MediaPlayer for wind_loop.");
        }
    }

    private final Runnable soundscapeRunnable = new Runnable() {
        @Override
        public void run() {
            if (!isPlaying) return;

            try {
                tfliteInputs[0][0] = (float) currentMode;
                tfliteInputs[0][1] = (float) Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
                tflite.run(tfliteInputs, tfliteOutputs);

                float[] results = tfliteOutputs[0];
                if (rainPlayer != null) rainPlayer.setVolume(results[0], results[0]);
                if (windPlayer != null) windPlayer.setVolume(results[1], results[1]);
                if (random.nextFloat() < results[2]) soundPool.play(thunderSoundId, 0.7f, 0.7f, 1, 0, 1.0f);
                if (random.nextFloat() < results[3]) soundPool.play(birdSoundId, 0.6f, 0.6f, 1, 0, 1.0f);

            } catch (Exception e) {
                Log.e(TAG, "Error during model inference: " + e.getMessage(), e);
            }

            soundLoopHandler.postDelayed(this, LOOP_INTERVAL_MS);
        }
    };

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "Service destroying...");
        isPlaying = false;
        soundLoopHandler.removeCallbacks(soundscapeRunnable);
        if (rainPlayer != null) rainPlayer.release();
        if (windPlayer != null) windPlayer.release();
        if (soundPool != null) soundPool.release();
        if (tflite != null) tflite.close();
    }

    // Boilerplate Code (unchanged)
    private void setupTFLiteModel() throws IOException {
        AssetFileDescriptor fileDescriptor = this.getAssets().openFd(MODEL_FILE_NAME);
        FileInputStream inputStream = new FileInputStream(fileDescriptor.getFileDescriptor());
        FileChannel fileChannel = inputStream.getChannel();
        long startOffset = fileDescriptor.getStartOffset();
        long declaredLength = fileDescriptor.getDeclaredLength();
        MappedByteBuffer modelBuffer = fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength);
        tflite = new Interpreter(modelBuffer);
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, "Soundscape Service", NotificationManager.IMPORTANCE_LOW);
            getSystemService(NotificationManager.class).createNotificationChannel(channel);
        }
    }

    private Notification createNotification(String text) {
        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Smart Soundscape")
                .setContentText(text)
                .setSmallIcon(R.drawable.ic_notification_sound)
                .build();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}