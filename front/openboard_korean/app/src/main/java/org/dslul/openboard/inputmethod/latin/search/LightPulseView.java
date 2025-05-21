// File: app/src/main/java/org/dslul/openboard/inputmethod/latin/search/LightPulseView.java
package org.dslul.openboard.inputmethod.latin.search;

import android.animation.ArgbEvaluator;
import android.animation.AnimatorSet;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.View;

public class LightPulseView extends View {
    private final Paint mPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF mRect = new RectF();

    private static final int COLOR_LAVENDER  = 0xFF8D80F7;
    private static final int COLOR_MID       = 0xFF79B8FF; // 중간 파랑
    private static final int COLOR_MINT      = 0xFF6195F5;

    private int mCurrentColor = COLOR_LAVENDER;
    private AnimatorSet mAnimatorSet;

    public LightPulseView(Context context, AttributeSet attrs) {
        super(context, attrs);
        initAnimator();
    }

    private void initAnimator() {
        // 1) 컬러 애니메이터: lavender → mid → mint
        ValueAnimator colorAnim = ValueAnimator.ofObject(
                new ArgbEvaluator(),
                COLOR_LAVENDER,
                COLOR_MID,
                COLOR_MINT
        );
        colorAnim.setDuration(2000);
        colorAnim.setRepeatMode(ValueAnimator.REVERSE);
        colorAnim.setRepeatCount(ValueAnimator.INFINITE);
        colorAnim.addUpdateListener(anim -> {
            mCurrentColor = (int) anim.getAnimatedValue();
            updateShader();
            invalidate();
        });

        // 2) 알파 애니메이터: 0 → 255 (밝기)
        ValueAnimator alphaAnim = ValueAnimator.ofInt(0, 255);
        alphaAnim.setDuration(2000);
        alphaAnim.setRepeatMode(ValueAnimator.REVERSE);
        alphaAnim.setRepeatCount(ValueAnimator.INFINITE);
        alphaAnim.addUpdateListener(anim -> {
            mPaint.setAlpha((int) anim.getAnimatedValue());
            invalidate();
        });

        // 3) 동기 실행
        mAnimatorSet = new AnimatorSet();
        mAnimatorSet.playTogether(colorAnim, alphaAnim);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        mAnimatorSet.start();
    }

    @Override
    protected void onDetachedFromWindow() {
        mAnimatorSet.cancel();
        super.onDetachedFromWindow();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        updateShader();
    }

    private void updateShader() {
        int w = getWidth(), h = getHeight();
        if (w <= 0 || h <= 0) return;

        float cx = w / 2f, cy = h;
        float radius = Math.max(w, h) * 0.2f;  // 충분히 크게

        int centerColor = mCurrentColor;
        int edgeColor   = mCurrentColor & 0x00FFFFFF;  // 투명

        RadialGradient radial = new RadialGradient(
                cx, cy, radius,
                centerColor, edgeColor,
                Shader.TileMode.CLAMP
        );

        // Optional: 타원형으로 늘리기
        Matrix m = new Matrix();
        m.setScale(1.8f, 1f, cx, cy);
        radial.setLocalMatrix(m);

        mPaint.setShader(radial);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        // 그냥 사각
        mRect.set(0, 0, getWidth(), getHeight());
        canvas.drawRect(mRect, mPaint);
    }
}
