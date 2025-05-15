package org.dslul.openboard.inputmethod.latin.search;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ContentUris;
import android.content.Context;
import android.graphics.Bitmap;
import android.net.Uri;
import android.provider.MediaStore;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.dslul.openboard.inputmethod.keyboard.MainKeyboardView;
import org.dslul.openboard.inputmethod.keyboard.MoreKeysPanel;
import org.dslul.openboard.inputmethod.keyboard.MoreKeysPanel.Controller;
import org.dslul.openboard.inputmethod.keyboard.KeyboardActionListener;
import org.dslul.openboard.inputmethod.keyboard.emoji.OnKeyEventListener;
import org.dslul.openboard.inputmethod.latin.network.InfoResult;
import org.dslul.openboard.inputmethod.latin.network.MessageResponse;
import org.dslul.openboard.inputmethod.latin.network.PhotoResult;
import org.dslul.openboard.inputmethod.latin.R;

import java.util.List;

public class SearchResultView extends FrameLayout implements MoreKeysPanel {

    // 채팅 말풍선 전체를 스크롤할 뷰
    private final ScrollView mChatScroll;
    // 말풍선들을 순서대로 추가할 컨테이너
    private final LinearLayout mChatContainer;

    private MainKeyboardView mKeyboardView;
    private Controller mController;
    private boolean mShowing;

    public SearchResultView(Context context) {
        super(context);
        LayoutInflater.from(context)
                .inflate(R.layout.search_result_view, this, true);
        mChatScroll = findViewById(R.id.chat_scroll);
        mChatContainer = findViewById(R.id.chat_container);
        mShowing = false;
    }

    /** 사용자 질문 말풍선 추가 */
    public void bindUserQuery(String query) {
        TextView bubble = new TextView(getContext());
        bubble.setText(query);
        bubble.setBackgroundResource(R.drawable.bg_bubble_user);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.gravity = Gravity.END;
        lp.setMargins(0, dpToPx(4), 0, dpToPx(4));
        bubble.setLayoutParams(lp);
        mChatContainer.addView(bubble);
        scrollToBottom();
    }

    /** 서버 응답 말풍선 + 사진 포함 바인딩 */
    public void bindResponseAndDetails(MessageResponse resp) {
        // 1) 전체 말풍선 컨테이너 생성
        LinearLayout bubble = new LinearLayout(getContext());
        bubble.setOrientation(LinearLayout.VERTICAL);
        bubble.setBackgroundResource(R.drawable.bg_bubble_bot);
        LinearLayout.LayoutParams bubbleLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        bubbleLp.gravity = Gravity.START;
        bubbleLp.setMargins(0, dpToPx(4), 0, dpToPx(4));
        bubble.setLayoutParams(bubbleLp);

        // 2) 응답 텍스트 추가
        TextView tv = new TextView(getContext());
        String ans = resp.getAnswer() != null ? resp.getAnswer() : "결과 없음";
        tv.setText(ans);
        tv.setTextSize(14);
        tv.setLineSpacing(0, 1.2f);
        tv.setTextColor(0xFF202020);
        tv.setPadding(dpToPx(8), dpToPx(6), dpToPx(8), dpToPx(6));
        bubble.addView(tv);

        // 3) 사진들 추가 (없으면 건너뜀)
        if (resp.getPhotoResults() != null && !resp.getPhotoResults().isEmpty()) {
            // 가로 스크롤이 아니라, 말풍선 내에서 세로로 나열하거나,
            // 필요시 가로 스크롤뷰 wrapping 가능
            LinearLayout photosContainer = new LinearLayout(getContext());
            photosContainer.setOrientation(LinearLayout.HORIZONTAL);
            photosContainer.setPadding(dpToPx(8), dpToPx(4), dpToPx(8), dpToPx(4));
            for (PhotoResult p : resp.getPhotoResults()) {
                try {
                    long id = Long.parseLong(p.getId());
                    Bitmap thumb = MediaStore.Images.Thumbnails.getThumbnail(
                            getContext().getContentResolver(),
                            id,
                            MediaStore.Images.Thumbnails.MINI_KIND,
                            null);
                    ImageView iv = new ImageView(getContext());
                    LinearLayout.LayoutParams ivLp = new LinearLayout.LayoutParams(
                            dpToPx(80), dpToPx(80));
                    ivLp.setMargins(0, 0, dpToPx(4), 0);
                    iv.setLayoutParams(ivLp);
                    iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
                    iv.setImageBitmap(thumb);
                    photosContainer.addView(iv);

                    // 클릭 시 클립보드 복사
                    Uri uri = ContentUris.withAppendedId(
                            MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id);
                    iv.setOnClickListener(v -> {
                        ClipboardManager cm = (ClipboardManager)
                                getContext().getSystemService(Context.CLIPBOARD_SERVICE);
                        cm.setPrimaryClip(ClipData.newUri(
                                getContext().getContentResolver(),
                                "Image", uri));
                        Toast.makeText(getContext(),
                                "이미지가 클립보드에 복사되었습니다",
                                Toast.LENGTH_SHORT).show();
                    });
                } catch (NumberFormatException ignored) { }
            }
            bubble.addView(photosContainer);
        }

        // 4) 최종적으로 chat_container 에 말풍선 추가
        mChatContainer.addView(bubble);

        // 5) 마지막으로 스크롤 맨 아래로
        mChatScroll.post(() -> mChatScroll.fullScroll(ScrollView.FOCUS_DOWN));
    }


    private void scrollToBottom() {
        mChatScroll.post(() -> mChatScroll.fullScroll(ScrollView.FOCUS_DOWN));
    }

    private int dpToPx(int dp) {
        return Math.round(dp * getResources().getDisplayMetrics().density);
    }

    // -------- MoreKeysPanel 구현 --------

    private void attachToParent(MainKeyboardView kbView) {
        ViewGroup root = (ViewGroup) kbView.getParent();
        if (getParent() != null) ((ViewGroup) getParent()).removeView(this);
        kbView.setVisibility(View.INVISIBLE);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                LayoutParams.MATCH_PARENT,
                kbView.getHeight());
        lp.gravity = Gravity.BOTTOM;
        root.addView(this, lp);
        mKeyboardView = kbView;
        mShowing = true;
    }

    @Override
    public boolean isShowingInParent() {
        return mShowing;
    }

    @Override
    public void dismissMoreKeysPanel() {
        if (!mShowing) return;
        if (getParent() != null) ((ViewGroup) getParent()).removeView(this);
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

    @Override
    public void showMoreKeysPanel(View parent, Controller ctr,
                                  int x, int y, KeyboardActionListener l) {
        mController = ctr;
        attachToParent((MainKeyboardView) parent);
        ctr.onShowMoreKeysPanel(this);
    }

    @Override
    public void showMoreKeysPanel(View parent, Controller ctr,
                                  int x, int y, OnKeyEventListener l) {
        mController = ctr;
        attachToParent((MainKeyboardView) parent);
        ctr.onShowMoreKeysPanel(this);
    }

    @Override
    public int translateX(int x) { return x; }
    @Override
    public int translateY(int y) { return y; }
    @Override
    public void showInParent(ViewGroup parentView) {}
    @Override
    public void removeFromParent() {}
    public String getAnswerText() {
        // 채팅 말풍선 컨테이너의 자식 수를 확인
        int count = mChatContainer.getChildCount();
        if (count == 0) {
            return "";
        }
        // 마지막 자식을 가져와서 TextView이면 텍스트 리턴
        View last = mChatContainer.getChildAt(count - 1);
        if (last instanceof TextView) {
            return ((TextView) last).getText().toString();
        }
        return "";
    }
}
