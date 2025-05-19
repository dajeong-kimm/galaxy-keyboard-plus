package org.dslul.openboard.inputmethod.latin.setup;

import android.animation.ValueAnimator;
import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;
import androidx.core.content.ContextCompat;
import androidx.interpolator.view.animation.FastOutSlowInInterpolator;
import org.dslul.openboard.inputmethod.latin.R;

public class StepGaugeView extends View {

    // attrs
    private int total = 7;
    private final Paint done = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint todo = new Paint(Paint.ANTI_ALIAS_FLAG);

    // runtime state
    private float progress = 0f;   // 0 ~ total, 애니메이션용

    public StepGaugeView(Context c, AttributeSet a) {
        super(c, a);

        TypedArray ta = c.obtainStyledAttributes(a, R.styleable.StepGaugeView);

        total = ta.getInt(R.styleable.StepGaugeView_sgv_totalSteps, total);
        progress = ta.getInt(R.styleable.StepGaugeView_sgv_currentStep, 0);

        int finished = ta.getColor(R.styleable.StepGaugeView_sgv_finishedColor,
                ContextCompat.getColor(c, R.color.toss_primary));
        int unfinished = ta.getColor(R.styleable.StepGaugeView_sgv_unfinishedColor,
                ContextCompat.getColor(c, R.color.toss_primary_20));

        done.setColor(finished);
        todo.setColor(unfinished);
        done.setStrokeCap(Paint.Cap.ROUND);
        todo.setStrokeCap(Paint.Cap.ROUND);

        ta.recycle();
    }

    /* 게이지 높이는 View 높이에 맞춰 매번 갱신 */
    @Override protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        float stroke = h;                    // 전체 높이를 그대로 사용
        done.setStrokeWidth(stroke);
        todo.setStrokeWidth(stroke);
    }

    @Override protected void onDraw(Canvas c) {
        float w = getWidth(), h = getHeight();
        float per = w / total;

        // 회색(unfinished)
        c.drawLine(0, h / 2f, w, h / 2f, todo);
        // 파란(완료)
        c.drawLine(0, h / 2f, per * progress, h / 2f, done);
    }

    /** 단계(1-based)를 애니메이션과 함께 설정 */
    public void setStep(int stepNo) {
        float target = Math.max(0, Math.min(stepNo, total));

        ValueAnimator va = ValueAnimator.ofFloat(progress, target);
        va.setDuration(240);
        va.setInterpolator(new FastOutSlowInInterpolator());
        va.addUpdateListener(a -> {
            progress = (float) a.getAnimatedValue();
            invalidate();
        });
        va.start();
    }
}
