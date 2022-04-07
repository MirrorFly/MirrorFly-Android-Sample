/*
 * @category ContusFly
 * @copyright Copyright (C) 2016 Contus. All rights reserved.
 * @license http://www.apache.org/licenses/LICENSE-2.0
 */

package com.contus.call;

import android.animation.Animator;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.widget.RelativeLayout;

import java.util.ArrayList;

/**
 * This is custom layout which shows ripple effect for background
 *
 * @author ContusTeam <developers@contus.in>
 * @version 2.0
 */
public class RippleBackgroundView extends RelativeLayout {

    private static final int DEFAULT_RIPPLE_COUNT = 6;
    private static final int DEFAULT_DURATION_TIME = 3000;
    private static final float DEFAULT_SCALE = 6.0f;
    private static final int DEFAULT_FILL_TYPE = 0;

    private static final float DEFAULT_STROKE_WIDTH = 2.0f;

    private float rippleStrokeWidth;
    private Paint paint;
    private boolean animationRunning = false;
    private AnimatorSet animatorSet;
    private ArrayList<RippleView> rippleViewList = new ArrayList<>();

    /**
     * Constructs with context
     *
     * @param context the app component context
     */
    public RippleBackgroundView(Context context) {
        super(context);
    }

    /**
     * Constructs with context & attrs
     *
     * @param context the app component context
     * @param attrs   the attribute set
     */
    public RippleBackgroundView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context, attrs);
    }

    /**
     * Constructs with context & attrs & defStyleAttr
     *
     * @param context      the app component context
     * @param attrs        the attribute set
     * @param defStyleAttr the style attribute
     */
    public RippleBackgroundView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context, attrs);
    }

    /**
     * Initialization
     *
     * @param context the app component context
     * @param attrs   the attribute set
     */
    private void init(final Context context, final AttributeSet attrs) {
        if (isInEditMode())
            return;

        if (null == attrs) {
            throw new IllegalArgumentException("Attributes should be provided to this view,");
        }
        float defaultStrokeWidth =   TypedValue.applyDimension( TypedValue.COMPLEX_UNIT_DIP, 10.0f,
                context.getResources().getDisplayMetrics() );
        float defaultRippleRadius =   TypedValue.applyDimension( TypedValue.COMPLEX_UNIT_DIP, 64.0f,
                context.getResources().getDisplayMetrics() );

        final TypedArray typedArray = context.obtainStyledAttributes(attrs, R.styleable.RippleBackgroundView);
        int rippleColor = typedArray.getColor(R.styleable.RippleBackgroundView_rb_color, getResources().getColor(android.R.color.white));
        rippleStrokeWidth = typedArray.getDimension(R.styleable.RippleBackgroundView_rb_strokeWidth, defaultStrokeWidth);
        float rippleRadius = typedArray.getDimension(R.styleable.RippleBackgroundView_rb_radius, defaultRippleRadius);
        int rippleDurationTime = typedArray.getInt(R.styleable.RippleBackgroundView_rb_duration, DEFAULT_DURATION_TIME);
        int rippleAmount = typedArray.getInt(R.styleable.RippleBackgroundView_rb_rippleAmount, DEFAULT_RIPPLE_COUNT);
        float rippleScale = typedArray.getFloat(R.styleable.RippleBackgroundView_rb_scale, DEFAULT_SCALE);
        int rippleType = typedArray.getInt(R.styleable.RippleBackgroundView_rb_type, DEFAULT_FILL_TYPE);
        typedArray.recycle();

        int rippleDelay = rippleDurationTime / rippleAmount;

        paint = new Paint();
        paint.setAntiAlias(true);
        if (rippleType == DEFAULT_FILL_TYPE) {
            rippleStrokeWidth = 0;
            paint.setStyle(Paint.Style.FILL);
        } else
            paint.setStyle(Paint.Style.STROKE);
        paint.setColor(rippleColor);
        paint.setStrokeWidth(DEFAULT_STROKE_WIDTH);
        paint.setPathEffect(new DashPathEffect(new float[]{4, 8}, 4));

        LayoutParams rippleParams = new LayoutParams((int) (2 * (rippleRadius + rippleStrokeWidth)), (int) (2 * (rippleRadius + rippleStrokeWidth)));
        rippleParams.addRule(CENTER_IN_PARENT, TRUE);

        animatorSet = new AnimatorSet();
        animatorSet.setInterpolator(new AccelerateDecelerateInterpolator());
        ArrayList<Animator> animatorList = new ArrayList<>();

        for (int i = 0; i < rippleAmount; i++) {
            RippleView rippleView = new RippleView(getContext());
            addView(rippleView, rippleParams);
            rippleViewList.add(rippleView);
            final ObjectAnimator scaleXAnimator = ObjectAnimator.ofFloat(rippleView, "ScaleX", 1.0f, rippleScale);
            scaleXAnimator.setRepeatCount(ObjectAnimator.INFINITE);
            scaleXAnimator.setRepeatMode(ObjectAnimator.RESTART);
            scaleXAnimator.setStartDelay(i * rippleDelay);
            scaleXAnimator.setDuration(rippleDurationTime);
            animatorList.add(scaleXAnimator);
            final ObjectAnimator scaleYAnimator = ObjectAnimator.ofFloat(rippleView, "ScaleY", 1.0f, rippleScale);
            scaleYAnimator.setRepeatCount(ObjectAnimator.INFINITE);
            scaleYAnimator.setRepeatMode(ObjectAnimator.RESTART);
            scaleYAnimator.setStartDelay(i * rippleDelay);
            scaleYAnimator.setDuration(rippleDurationTime);
            animatorList.add(scaleYAnimator);
            final ObjectAnimator alphaAnimator = ObjectAnimator.ofFloat(rippleView, "Alpha", 1.0f, 0f);
            alphaAnimator.setRepeatCount(ObjectAnimator.INFINITE);
            alphaAnimator.setRepeatMode(ObjectAnimator.RESTART);
            alphaAnimator.setStartDelay(i * rippleDelay);
            alphaAnimator.setDuration(rippleDurationTime);
            animatorList.add(alphaAnimator);
        }

        animatorSet.playTogether(animatorList);
    }

    /**
     * starts ripple animation for the view
     */
    public void startRippleAnimation() {
        if (!isRippleAnimationRunning()) {
            for (RippleView rippleView : rippleViewList) {
                rippleView.setVisibility(VISIBLE);
            }
            animatorSet.start();
            animationRunning = true;
        }
    }

    /**
     * stops ripple animation for the view
     */
    public void stopRippleAnimation() {
        if (isRippleAnimationRunning()) {
            animatorSet.end();
            animationRunning = false;
        }
    }

    /**
     * Ripple Animation
     *
     * @return running animation
     */
    public boolean isRippleAnimationRunning() {
        return animationRunning;
    }

    private class RippleView extends View {

        public RippleView(Context context) {
            super(context);
            this.setVisibility(View.INVISIBLE);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            int radius = (Math.min(getWidth(), getHeight())) / 2;
            canvas.drawCircle(radius, radius, radius - rippleStrokeWidth, paint);
        }
    }
}