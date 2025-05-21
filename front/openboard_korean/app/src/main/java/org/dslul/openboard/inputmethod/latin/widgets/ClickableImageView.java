package org.dslul.openboard.inputmethod.latin.widgets;

import android.content.Context;
import android.util.AttributeSet;

import androidx.annotation.Nullable;
import androidx.appcompat.widget.AppCompatImageView;

public class ClickableImageView extends AppCompatImageView {
    public ClickableImageView(Context context) {
        super(context);
        init();
    }
    public ClickableImageView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }
    private void init() {
        // 이 뷰에 클릭 가능 표시를 해 주고
        setClickable(true);
    }
    @Override
    public boolean performClick() {
        // 접근성 이벤트, ripple 효과 등 올바르게 처리해 주려면
        super.performClick();
        return true;
    }
}