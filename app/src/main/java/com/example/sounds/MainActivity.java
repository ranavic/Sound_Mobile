package com.example.sounds;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import android.content.Intent;
import android.graphics.drawable.AnimatedVectorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.TransitionDrawable;
import android.os.Bundle;
import android.util.Log;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.google.android.material.floatingactionbutton.FloatingActionButton;

public class MainActivity extends AppCompatActivity {

    // --- UI Elements ---
    private RelativeLayout mainLayout;
    private TextView titleText;
    private LinearLayout cardSleep, cardFocus, cardRelax;
    private FloatingActionButton fabPlayPause;

    // --- State ---
    private boolean isPlaying = false; // This is now just for the UI state
    private int currentMode = 1; // 0=Sleep, 1=Focus, 2=Relax. Default to Focus.
    private Drawable currentGradient;

    // --- Animations ---
    private AnimatedVectorDrawable playToPause;
    private AnimatedVectorDrawable pauseToPlay;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        linkUiElements();
        setupAnimations();
        setupClickListeners();

        // Set the initial UI state
        selectMode(currentMode);

        // Note: All audio and ML setup is GONE from the activity.
    }

    private void linkUiElements() {
        mainLayout = findViewById(R.id.mainLayout);
        titleText = findViewById(R.id.titleText);
        cardSleep = findViewById(R.id.cardSleep);
        cardFocus = findViewById(R.id.cardFocus);
        cardRelax = findViewById(R.id.cardRelax);
        fabPlayPause = findViewById(R.id.fabPlayPause);

        currentGradient = ContextCompat.getDrawable(this, R.drawable.gradient_focus);
    }

    private void setupAnimations() {
        Drawable playToPauseDrawable = getDrawable(R.drawable.avd_play_to_pause);
        if (playToPauseDrawable instanceof AnimatedVectorDrawable) {
            playToPause = (AnimatedVectorDrawable) playToPauseDrawable;
        } else {
            Log.e("MainActivity", "Error: avd_play_to_pause is not an AnimatedVectorDrawable!");
        }

        Drawable pauseToPlayDrawable = getDrawable(R.drawable.avd_pause_to_play);
        if (pauseToPlayDrawable instanceof AnimatedVectorDrawable) {
            pauseToPlay = (AnimatedVectorDrawable) pauseToPlayDrawable;
        } else {
            Log.e("MainActivity", "Error: avd_pause_to_play is not an AnimatedVectorDrawable!");
        }
    }

    private void setupClickListeners() {
        fabPlayPause.setOnClickListener(v -> {
            if (isPlaying) {
                stopSoundscape();
            } else {
                startSoundscape();
            }
        });

        cardSleep.setOnClickListener(v -> selectMode(0));
        cardFocus.setOnClickListener(v -> selectMode(1));
        cardRelax.setOnClickListener(v -> selectMode(2));
    }

    private void selectMode(int mode) {
        currentMode = mode;

        // Update card selection state (UI)
        cardSleep.setSelected(mode == 0);
        cardFocus.setSelected(mode == 1);
        cardRelax.setSelected(mode == 2);

        // Update UI elements based on mode
        Drawable newGradient;
        String newTitle;

        if (mode == 0) { // Sleep
            newGradient = ContextCompat.getDrawable(this, R.drawable.gradient_sleep);
            newTitle = "Sleep";
        } else if (mode == 1) { // Focus
            newGradient = ContextCompat.getDrawable(this, R.drawable.gradient_focus);
            newTitle = "Focus";
        } else { // Relax
            newGradient = ContextCompat.getDrawable(this, R.drawable.gradient_relax);
            newTitle = "Relax";
        }

        titleText.setText(newTitle);
        animateBackground(newGradient);

        // If already playing, send the new mode to the service
        if (isPlaying) {
            sendCommand(SoundscapeService.ACTION_SET_MODE, currentMode);
        }
    }

    private void animateBackground(Drawable newGradient) {
        TransitionDrawable transition = new TransitionDrawable(new Drawable[]{currentGradient, newGradient});
        mainLayout.setBackground(transition);
        transition.startTransition(500); // 500ms fade duration
        currentGradient = newGradient;
    }

    // --- NEW HELPER METHODS TO CONTROL THE SERVICE ---

    private void startSoundscape() {
        isPlaying = true;
        if (playToPause != null) {
            fabPlayPause.setImageDrawable(playToPause);
            playToPause.start();
        }

        // Send the current mode *when we start*
        sendCommand(SoundscapeService.ACTION_SET_MODE, currentMode);
        // Send the play command
        sendCommand(SoundscapeService.ACTION_PLAY, -1);
    }

    private void stopSoundscape() {
        isPlaying = false;
        if (pauseToPlay != null) {
            fabPlayPause.setImageDrawable(pauseToPlay);
            pauseToPlay.start();
        }
        sendCommand(SoundscapeService.ACTION_STOP, -1);
    }

    /**
     * Sends a command to the SoundscapeService.
     * @param action The action string (e.g., ACTION_PLAY)
     * @param mode The mode to send (-1 if not applicable)
     */
    private void sendCommand(String action, int mode) {
        Intent intent = new Intent(this, SoundscapeService.class);
        intent.setAction(action);
        if (mode != -1) {
            intent.putExtra(SoundscapeService.EXTRA_MODE, mode);
        }
        startService(intent);
    }
}