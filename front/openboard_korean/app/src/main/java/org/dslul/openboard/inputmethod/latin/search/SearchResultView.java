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
import android.widget.GridLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.bumptech.glide.Glide;

import org.dslul.openboard.inputmethod.keyboard.MainKeyboardView;
import org.dslul.openboard.inputmethod.keyboard.MoreKeysPanel;
import org.dslul.openboard.inputmethod.keyboard.KeyboardActionListener;
import org.dslul.openboard.inputmethod.keyboard.emoji.OnKeyEventListener;
import org.dslul.openboard.inputmethod.latin.R;
import org.dslul.openboard.inputmethod.latin.network.InfoResult;
import org.dslul.openboard.inputmethod.latin.network.MessageResponse;
import org.dslul.openboard.inputmethod.latin.network.PhotoResult;

import java.util.ArrayList;
import java.util.List;

public class SearchResultView extends LinearLayout implements MoreKeysPanel {

    private View mKeyboardView;       // 숨겼던 키보드 뷰 복원용
    private Controller mController;
    private boolean mShowing = false;

    private final TextView mAnswer;
    private final LinearLayout mInfos;

    private HorizontalScrollView mPhotoScroll;
    private LinearLayout mPhotoStrip;
    private ScrollView mScroll;

    private final View   mLoadingBox;

    public SearchResultView(Context context) {
        super(context);
        LayoutInflater.from(context).inflate(R.layout.search_result_view, this, true);
        mAnswer = findViewById(R.id.search_result_answer);
        mInfos = findViewById(R.id.search_result_infos);
        mLoadingBox  = findViewById(R.id.loading_box);
        mScroll = findViewById(R.id.search_scroll);
        mPhotoScroll = findViewById(R.id.photo_scroll);
        mPhotoStrip = findViewById(R.id.search_result_photos);
        setOrientation(VERTICAL);
    }

    /* ------------------------------------------------------------------
     * public helper : API 응답 바인딩
     * ------------------------------------------------------------------ */
    public void bind(MessageResponse resp) {
        hideLoading();
        // 스크롤 최상단으로 초기화
        mScroll.scrollTo(0, 0);

        /* ─ 1. 로딩 → 본문 전환 ──────────────────────────────────────── */
        mScroll.setVisibility(VISIBLE);

        /* ─ 2. 텍스트 결과 ──────────────────────────────────────────── */
        String ans = resp.getAnswer() != null ? resp.getAnswer() : "결과 없음";
        mAnswer.setText(ans);

        /* ─ 3. 사진 썸네일 처리 ─────────────────────────────────────── */
        mPhotoStrip.removeAllViews();
        List<Uri> uris = new ArrayList<>();
        addUrisFromPhotos(resp.getPhotoResults(), uris);
        addUrisFromInfos (resp.getInfoResults(),  uris);

        if (!uris.isEmpty()) {
            mPhotoScroll.setVisibility(VISIBLE);

            final int size = dpToPx(120);          // 120dp 정사각형
            for (Uri u : uris) {
                long mediaId = ContentUris.parseId(u);
                Bitmap thumb = MediaStore.Images.Thumbnails.getThumbnail(
                        getContext().getContentResolver(),
                        mediaId,
                        MediaStore.Images.Thumbnails.MINI_KIND,
                        null);

                ImageView iv = new ImageView(getContext());
                GridLayout.LayoutParams lp = new GridLayout.LayoutParams();
                lp.width  = size;
                lp.height = size;
                lp.setMargins(0, 0, dpToPx(4), dpToPx(4));
                iv.setLayoutParams(lp);
                iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
                iv.setImageBitmap(thumb);

                iv.setOnClickListener(v -> {
                    // 클립보드에 이미지 URI 복사
                    ClipboardManager cm = (ClipboardManager)
                            getContext().getSystemService(Context.CLIPBOARD_SERVICE);
                    ClipData clip = ClipData.newUri(
                            getContext().getContentResolver(),
                            "Image",
                            u);
                    cm.setPrimaryClip(clip);
                    Toast.makeText(getContext(),
                            "이미지가 클립보드에 복사되었습니다",
                            Toast.LENGTH_SHORT).show();
                });

                mPhotoStrip.addView(iv);
            }
        } else {
            mPhotoScroll.setVisibility(GONE);
        }

        /* ─ 4. 추가 정보 텍스트 ─────────────────────────────────────── */
//        mInfos.removeAllViews();
//        List<InfoResult> infos = resp.getInfoResults();
//        if (infos != null) {
//            for (InfoResult info : infos) {
//                TextView tv = new TextView(getContext());
//                tv.setText("• " + info.getText());
//                tv.setTextColor(0xFFDDDDDD);
//                tv.setTextSize(13);
//                tv.setLineSpacing(0, 1.1f);
//                mInfos.addView(tv);
//            }
//        }

        /* ─ 5. 스크롤 최상단으로 ────────────────────────────────────── */
        mScroll.scrollTo(0, 0);
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
    public boolean isShowingInParent() {
        return mShowing;
    }

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
    @Override
    public int translateX(int x) {
        return x;
    }

    @Override
    public int translateY(int y) {
        return y;
    }

    @Override
    public void showInParent(ViewGroup parentView) {

    }

    @Override
    public void removeFromParent() {

    }

    private void addUrisFromPhotos(List<PhotoResult> list, List<Uri> out) {
        if (list == null) return;
        for (PhotoResult r : list) {
            try {
                long id = Long.parseLong(r.getId());   // "2455" 같은 숫자 ID
                out.add(ContentUris.withAppendedId(
                        MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id));
            } catch (NumberFormatException ignored) { }
        }
    }

    private void addUrisFromInfos(List<InfoResult> list, List<Uri> out) {
        if (list == null) return;
        for (InfoResult r : list) {
            try {
                long id = Long.parseLong(r.getId());
                out.add(ContentUris.withAppendedId(
                        MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id));
            } catch (NumberFormatException ignored) { }
        }
    }

    private int dpToPx(int dp) {
        return Math.round(dp * getResources().getDisplayMetrics().density);
    }

    /* ---- Loading helpers -------------------------------------------- */
    public void showLoading() {
        mLoadingBox.setVisibility(VISIBLE);
        mScroll.setVisibility(GONE);        // 본문·썸네일 숨김
        mPhotoScroll.setVisibility(GONE);
    }

    private void hideLoading() {
        mLoadingBox.setVisibility(GONE);
        mScroll.setVisibility(VISIBLE);     // 본문 표시
    }

    public String getAnswerText() {
        return mAnswer.getText().toString();
    }


}
