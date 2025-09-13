package com.example.nwipe_android;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.drawable.AnimationDrawable;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.widget.ProgressBar;

import com.google.android.material.progressindicator.CircularProgressIndicator;
import com.google.android.material.progressindicator.LinearProgressIndicator;

/**
 * Helper class for managing smooth animations throughout the app
 */
public class AnimationHelper {
    
    private static final int ANIMATION_DURATION_SHORT = 200;
    private static final int ANIMATION_DURATION_MEDIUM = 300;
    private static final int ANIMATION_DURATION_LONG = 500;
    
    /**
     * Animate progress bar with smooth transitions
     */
    public static void animateProgress(LinearProgressIndicator progressBar, int targetProgress) {
        if (progressBar == null) return;
        
        int currentProgress = progressBar.getProgress();
        ValueAnimator animator = ValueAnimator.ofInt(currentProgress, targetProgress);
        animator.setDuration(ANIMATION_DURATION_MEDIUM);
        animator.setInterpolator(new DecelerateInterpolator());
        
        animator.addUpdateListener(animation -> {
            int animatedValue = (int) animation.getAnimatedValue();
            progressBar.setProgress(animatedValue);
        });
        
        animator.start();
    }
    
    /**
     * Animate circular progress indicator
     */
    public static void animateCircularProgress(CircularProgressIndicator progressIndicator, int targetProgress) {
        if (progressIndicator == null) return;
        
        // Switch to determinate mode if needed
        if (progressIndicator.isIndeterminate()) {
            progressIndicator.setIndeterminate(false);
        }
        
        int currentProgress = progressIndicator.getProgress();
        ValueAnimator animator = ValueAnimator.ofInt(currentProgress, targetProgress);
        animator.setDuration(ANIMATION_DURATION_MEDIUM);
        animator.setInterpolator(new DecelerateInterpolator());
        
        animator.addUpdateListener(animation -> {
            int animatedValue = (int) animation.getAnimatedValue();
            progressIndicator.setProgress(animatedValue);
        });
        
        animator.start();
    }
    
    /**
     * Start indeterminate circular progress with pulse animation
     */
    public static void startIndeterminateProgress(CircularProgressIndicator progressIndicator) {
        if (progressIndicator == null) return;
        
        progressIndicator.setIndeterminate(true);
        
        // Add pulse animation
        ObjectAnimator pulseAnimator = ObjectAnimator.ofFloat(progressIndicator, "alpha", 0.6f, 1.0f);
        pulseAnimator.setDuration(1000);
        pulseAnimator.setRepeatCount(ValueAnimator.INFINITE);
        pulseAnimator.setRepeatMode(ValueAnimator.REVERSE);
        pulseAnimator.setInterpolator(new AccelerateDecelerateInterpolator());
        pulseAnimator.start();
        
        // Store animator in tag for later cleanup
        progressIndicator.setTag(R.id.pulse_animator_tag, pulseAnimator);
    }
    
    /**
     * Stop indeterminate progress and cleanup animations
     */
    public static void stopIndeterminateProgress(CircularProgressIndicator progressIndicator) {
        if (progressIndicator == null) return;
        
        // Stop pulse animation
        ObjectAnimator pulseAnimator = (ObjectAnimator) progressIndicator.getTag(R.id.pulse_animator_tag);
        if (pulseAnimator != null) {
            pulseAnimator.cancel();
            progressIndicator.setTag(R.id.pulse_animator_tag, null);
        }
        
        // Reset alpha
        progressIndicator.setAlpha(1.0f);
        progressIndicator.setIndeterminate(false);
    }
    
    /**
     * Slide in animation for cards
     */
    public static void slideInCard(View card) {
        if (card == null) return;
        
        card.setTranslationY(100f);
        card.setAlpha(0f);
        card.setVisibility(View.VISIBLE);
        
        card.animate()
            .translationY(0f)
            .alpha(1f)
            .setDuration(ANIMATION_DURATION_MEDIUM)
            .setInterpolator(new DecelerateInterpolator())
            .start();
    }
    
    /**
     * Slide out animation for cards
     */
    public static void slideOutCard(View card, Runnable onComplete) {
        if (card == null) return;
        
        card.animate()
            .translationY(-100f)
            .alpha(0f)
            .setDuration(ANIMATION_DURATION_SHORT)
            .setInterpolator(new AccelerateDecelerateInterpolator())
            .setListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    card.setVisibility(View.GONE);
                    if (onComplete != null) {
                        onComplete.run();
                    }
                }
            })
            .start();
    }
    
    /**
     * Fade in animation
     */
    public static void fadeIn(View view) {
        if (view == null) return;
        
        view.setAlpha(0f);
        view.setVisibility(View.VISIBLE);
        view.animate()
            .alpha(1f)
            .setDuration(ANIMATION_DURATION_MEDIUM)
            .start();
    }
    
    /**
     * Fade out animation
     */
    public static void fadeOut(View view, Runnable onComplete) {
        if (view == null) return;
        
        view.animate()
            .alpha(0f)
            .setDuration(ANIMATION_DURATION_SHORT)
            .setListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    view.setVisibility(View.GONE);
                    if (onComplete != null) {
                        onComplete.run();
                    }
                }
            })
            .start();
    }
    
    /**
     * Scale animation for button press feedback
     */
    public static void scaleButton(View button) {
        if (button == null) return;
        
        button.animate()
            .scaleX(0.95f)
            .scaleY(0.95f)
            .setDuration(100)
            .setListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    button.animate()
                        .scaleX(1f)
                        .scaleY(1f)
                        .setDuration(100)
                        .setListener(null)
                        .start();
                }
            })
            .start();
    }
    
    /**
     * Start wave animation on progress bar
     */
    public static void startWaveAnimation(LinearProgressIndicator progressBar) {
        if (progressBar == null) return;
        
        // Create a subtle wave effect by animating the track color
        ValueAnimator waveAnimator = ValueAnimator.ofFloat(0f, 1f);
        waveAnimator.setDuration(2000);
        waveAnimator.setRepeatCount(ValueAnimator.INFINITE);
        waveAnimator.setRepeatMode(ValueAnimator.RESTART);
        waveAnimator.setInterpolator(new AccelerateDecelerateInterpolator());
        
        waveAnimator.addUpdateListener(animation -> {
            float fraction = animation.getAnimatedFraction();
            // Create a subtle alpha animation effect
            float alpha = 0.7f + (0.3f * (float) Math.sin(fraction * Math.PI * 2));
            progressBar.setAlpha(alpha);
        });
        
        waveAnimator.start();
        
        // Store animator in tag for cleanup
        progressBar.setTag(R.id.wave_animator_tag, waveAnimator);
    }
    
    /**
     * Stop wave animation on progress bar
     */
    public static void stopWaveAnimation(LinearProgressIndicator progressBar) {
        if (progressBar == null) return;
        
        ValueAnimator waveAnimator = (ValueAnimator) progressBar.getTag(R.id.wave_animator_tag);
        if (waveAnimator != null) {
            waveAnimator.cancel();
            progressBar.setTag(R.id.wave_animator_tag, null);
        }
        
        // Reset alpha
        progressBar.setAlpha(1.0f);
    }
    
    /**
     * Animate number counting effect
     */
    public static void animateNumberCount(NumberCountCallback callback, int startValue, int endValue) {
        ValueAnimator animator = ValueAnimator.ofInt(startValue, endValue);
        animator.setDuration(ANIMATION_DURATION_LONG);
        animator.setInterpolator(new DecelerateInterpolator());
        
        animator.addUpdateListener(animation -> {
            int animatedValue = (int) animation.getAnimatedValue();
            callback.onNumberUpdate(animatedValue);
        });
        
        animator.start();
    }
    
    /**
     * Interface for number counting animation callback
     */
    public interface NumberCountCallback {
        void onNumberUpdate(int value);
    }
}