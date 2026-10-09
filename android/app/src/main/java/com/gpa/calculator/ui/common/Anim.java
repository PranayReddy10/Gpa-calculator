package com.gpa.calculator.ui.common;

import android.view.View;
import android.view.animation.AnimationUtils;

import androidx.dynamicanimation.animation.DynamicAnimation;
import androidx.dynamicanimation.animation.SpringAnimation;
import androidx.dynamicanimation.animation.SpringForce;

import com.gpa.calculator.R;

/** Small reusable animation helpers (spring reveal, heart pop, shake). */
public final class Anim {
    private Anim() {}

    /** Slides the view up with a spring and fades it in. */
    public static void springIn(View v) {
        v.setVisibility(View.VISIBLE);
        v.setAlpha(0f);
        v.setTranslationY(80f * v.getResources().getDisplayMetrics().density);
        v.setScaleX(0.96f);
        v.setScaleY(0.96f);
        v.animate().alpha(1f).setDuration(300).start();
        spring(v, DynamicAnimation.TRANSLATION_Y, 0f, SpringForce.STIFFNESS_LOW, SpringForce.DAMPING_RATIO_MEDIUM_BOUNCY);
        spring(v, DynamicAnimation.SCALE_X, 1f, SpringForce.STIFFNESS_MEDIUM, SpringForce.DAMPING_RATIO_LOW_BOUNCY);
        spring(v, DynamicAnimation.SCALE_Y, 1f, SpringForce.STIFFNESS_MEDIUM, SpringForce.DAMPING_RATIO_LOW_BOUNCY);
    }

    public static void spring(View v, DynamicAnimation.ViewProperty prop, float to, float stiffness, float damping) {
        SpringAnimation s = new SpringAnimation(v, prop, to);
        s.getSpring().setStiffness(stiffness).setDampingRatio(damping);
        s.start();
    }

    /** Heart-style pop: scale up then spring back. */
    public static void pop(View v) {
        v.setScaleX(0.6f);
        v.setScaleY(0.6f);
        spring(v, DynamicAnimation.SCALE_X, 1f, SpringForce.STIFFNESS_MEDIUM, SpringForce.DAMPING_RATIO_HIGH_BOUNCY);
        spring(v, DynamicAnimation.SCALE_Y, 1f, SpringForce.STIFFNESS_MEDIUM, SpringForce.DAMPING_RATIO_HIGH_BOUNCY);
    }

    public static void shake(View v) {
        v.startAnimation(AnimationUtils.loadAnimation(v.getContext(), R.anim.shake));
    }

    /** Staggered fade/slide for children of a container that were just added. */
    public static void staggerChildren(android.view.ViewGroup group, long baseDelay) {
        for (int i = 0; i < group.getChildCount(); i++) {
            View c = group.getChildAt(i);
            c.setAlpha(0f);
            c.setTranslationY(24f * c.getResources().getDisplayMetrics().density);
            c.animate().alpha(1f).translationY(0f).setStartDelay(baseDelay + i * 45L).setDuration(320)
                    .setInterpolator(new android.view.animation.DecelerateInterpolator(2f)).start();
        }
    }
}
