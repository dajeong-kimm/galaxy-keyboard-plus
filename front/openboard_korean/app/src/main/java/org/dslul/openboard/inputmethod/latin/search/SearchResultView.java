package org.dslul.openboard.inputmethod.latin.search;

import android.content.Context;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.dslul.openboard.inputmethod.keyboard.MainKeyboardView;
import org.dslul.openboard.inputmethod.keyboard.MoreKeysPanel;
import org.dslul.openboard.inputmethod.keyboard.KeyboardActionListener;
import org.dslul.openboard.inputmethod.keyboard.emoji.OnKeyEventListener;
import org.dslul.openboard.inputmethod.latin.R;
import org.dslul.openboard.inputmethod.latin.network.InfoResult;
import org.dslul.openboard.inputmethod.latin.network.MessageResponse;

public class SearchResultView extends LinearLayout implements MoreKeysPanel {

    private View mKeyboardView;       // 숨겼던 키보드 뷰 복원용
    private Controller mController;
    private boolean mShowing = false;

    private final TextView    mAnswer;
    private final LinearLayout mInfos;

    public SearchResultView(Context context) {
        super(context);
        LayoutInflater.from(context).inflate(R.layout.search_result_view, this, true);
        mAnswer = findViewById(R.id.search_result_answer);
        mInfos  = findViewById(R.id.search_result_infos);
        setOrientation(VERTICAL);
    }

    /* ------------------------------------------------------------------
     * public helper : API 응답 바인딩
     * ------------------------------------------------------------------ */
    public void bind(MessageResponse resp) {
        // 스크롤 최상단으로 초기화
        ScrollView sv = findViewById(R.id.search_scroll);
        sv.scrollTo(0, 0);

        // 2-A. 줄바꿈(\n) 존중하도록 그대로 setText
        mAnswer.setText(resp.getAnswer() == null ? "결과 없음" : resp.getAnswer());

        // 2-B. 추가 정보 리스트
        mInfos.removeAllViews();
        for (InfoResult info : resp.getInfoResults()) {
            TextView tv = new TextView(getContext());
            tv.setText("• " + info.getText());
            tv.setTextColor(0xFFDDDDDD);
            tv.setTextSize(13);
            tv.setLineSpacing(0, 1.1f);
            mInfos.addView(tv);
        }
    }


    /* ------------------------------------------------------------------
     * MoreKeysPanel 구현부
     * ------------------------------------------------------------------ */
    // SearchResultView.java

    private void attachToParent(MainKeyboardView kbView) {
        ViewGroup root = (ViewGroup) kbView.getParent();   // FrameLayout

        // ① 이미 붙어 있던 패널 제거
        if (getParent() != null) ((ViewGroup) getParent()).removeView(this);

        /* ② 키보드 뷰 가리기
         *    - GONE  → 레이아웃에서 제외 ⇒ 높이 0
         *    - INVISIBLE → 레이아웃 크기는 유지, 렌더만 안 함 ✅
         */
        kbView.setVisibility(View.INVISIBLE);   // ← 여기만 GONE → INVISIBLE 로!

        // ③ 나를 같은 FrameLayout에 덮어쓰기
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                LayoutParams.MATCH_PARENT,
                kbView.getHeight()               // 키보드 원본 높이
        );
        lp.gravity = Gravity.BOTTOM;
        root.addView(this, lp);

        mKeyboardView = kbView;
        mShowing = true;
    }




    @Override
    public boolean isShowingInParent() { return mShowing; }

    @Override
    public void dismissMoreKeysPanel() {
        if (!mShowing) return;
        if (getParent() != null) ((ViewGroup) getParent()).removeView(this);

        /* INVISIBLE → VISIBLE 로 되돌려 원래 높이를 살립니다 */
        if (mKeyboardView != null) mKeyboardView.setVisibility(View.VISIBLE);

        if (mController != null) mController.onDismissMoreKeysPanel();
        mShowing = false;
    }


    @Override
    public void onMoveEvent(int x, int y, int pointerId, long eventTime) {

    }

    @Override
    public void onDownEvent(int x, int y, int pointerId, long eventTime) {

    }

    @Override
    public void onUpEvent(int x, int y, int pointerId, long eventTime) {

    }

    // ----- showMoreKeysPanel : AOSP 13 / 14 두 가지 시그니처를 모두 구현 -----
    @Override
    public void showMoreKeysPanel(View parent, Controller ctr,
                                  int x, int y, KeyboardActionListener l) {
        mController = ctr;
        attachToParent((MainKeyboardView) parent);          // parent == MainKeyboardView
        ctr.onShowMoreKeysPanel(this);
    }

    @Override
    public void showMoreKeysPanel(View parent, Controller ctr,
                                  int x, int y, OnKeyEventListener l) {
        mController = ctr;
        attachToParent((MainKeyboardView) parent);
        ctr.onShowMoreKeysPanel(this);
    }

    // 좌표 변환 : 키보드 뷰 기준 그대로 사용
    @Override public int translateX(int x) { return x; }
    @Override public int translateY(int y) { return y; }

    @Override
    public void showInParent(ViewGroup parentView) {

    }

    @Override
    public void removeFromParent() {

    }
}
