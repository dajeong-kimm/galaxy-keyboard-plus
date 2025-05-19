package org.dslul.openboard.inputmethod.latin.search;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ContentUris;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.provider.MediaStore;
import android.text.TextUtils;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.content.res.AppCompatResources;

import com.airbnb.lottie.LottieAnimationView;
import com.airbnb.lottie.LottieDrawable;

import org.dslul.openboard.inputmethod.keyboard.MainKeyboardView;
import org.dslul.openboard.inputmethod.keyboard.MoreKeysPanel;
import org.dslul.openboard.inputmethod.keyboard.KeyboardActionListener;
import org.dslul.openboard.inputmethod.keyboard.emoji.OnKeyEventListener;
import org.dslul.openboard.inputmethod.latin.network.MessageResponse;
import org.dslul.openboard.inputmethod.latin.network.PhotoResult;
import org.dslul.openboard.inputmethod.latin.R;

import java.util.List;

public class SearchResultView extends FrameLayout implements MoreKeysPanel {

    // 채팅 말풍선 전체를 스크롤할 뷰
    private final ScrollView mChatScroll;
    // 말풍선들을 순서대로 추가할 컨테이너
    private final LinearLayout mChatContainer;
    private LinearLayout mLoadingBubble;

    private MainKeyboardView mKeyboardView;
    private Controller mController;
    private boolean mShowing;
    private String mLastQuery;

    public SearchResultView(Context context) {
        super(context);
        LayoutInflater.from(context)
                .inflate(R.layout.search_result_view, this, true);
        mChatScroll = findViewById(R.id.chat_scroll);
        mChatContainer = findViewById(R.id.chat_container);
        mShowing = false;
    }

    public void bindPhotosOnly(MessageResponse resp) {
        // 1) 기존 내용 다 지우기
        removeAllViews();

        // 2) 닫기(X) 버튼
        ImageButton btnClose = new ImageButton(getContext());
        Drawable closeIcon = AppCompatResources.getDrawable(getContext(), R.drawable.ic_close);
        btnClose.setImageDrawable(closeIcon);
        btnClose.setBackground(null);
        btnClose.setOnClickListener(v -> dismissMoreKeysPanel());
//        FrameLayout.LayoutParams lpClose = new FrameLayout.LayoutParams(
//                dpToPx(24), dpToPx(24), Gravity.START | Gravity.TOP);
        FrameLayout.LayoutParams lpClose = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dpToPx(80),               // <<── 80dp 로 설정
                Gravity.BOTTOM
        );
        lpClose.setMargins(dpToPx(4), dpToPx(4), 0, 0);
        addView(btnClose, lpClose);

        // 3) 사진 컨테이너
        LinearLayout photosContainer = new LinearLayout(getContext());
        photosContainer.setOrientation(LinearLayout.HORIZONTAL);
        FrameLayout.LayoutParams lpPhotos = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dpToPx(100),  // 높이는 취향껏 조정
                Gravity.BOTTOM);
        lpPhotos.setMargins(dpToPx(4), 0, dpToPx(4), dpToPx(4));
        addView(photosContainer, lpPhotos);

        // 4) 사진들 채우기
        List<String> photoIds = resp.getPhotoIds();
        if (photoIds != null) {
            for (String idStr : photoIds) {
                try {
                    long id = Long.parseLong(idStr);
                    Bitmap thumb = MediaStore.Images.Thumbnails.getThumbnail(
                            getContext().getContentResolver(),
                            id,
                            MediaStore.Images.Thumbnails.MINI_KIND,
                            null);
                    ImageView iv = new ImageView(getContext());
                    LinearLayout.LayoutParams ivLp = new LinearLayout.LayoutParams(
                            dpToPx(80), dpToPx(80));
                    ivLp.setMargins(dpToPx(4), 0, dpToPx(4), 0);
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
        }

    }

    public void bindShortTextOnly(MessageResponse resp) {
        // 1) 전체 레이아웃 초기화
        removeAllViews();

        // 2) 컨테이너 (가로)
        LinearLayout container = new LinearLayout(getContext());
        container.setOrientation(LinearLayout.HORIZONTAL);
        FrameLayout.LayoutParams lpContainer = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dpToPx(48),  // 높이 조정 가능
                Gravity.BOTTOM
        );
        lpContainer.setMargins(dpToPx(4), 0, dpToPx(4), dpToPx(4));
        addView(container, lpContainer);

        // 3) X 버튼
        ImageButton btnClose = new ImageButton(getContext());
        btnClose.setImageDrawable(
                AppCompatResources.getDrawable(getContext(), R.drawable.ic_close)
        );
        btnClose.setBackground(null);
        btnClose.setOnClickListener(v -> dismissMoreKeysPanel());
        LinearLayout.LayoutParams lpClose = new LinearLayout.LayoutParams(
                dpToPx(24), dpToPx(24)
        );
        lpClose.gravity = Gravity.CENTER_VERTICAL;
        lpClose.setMargins(0, 0, dpToPx(8), 0);
        container.addView(btnClose, lpClose);

        // 4) 응답 텍스트
        String ans = resp.getAnswer() != null ? resp.getAnswer() : "";
        TextView tv = new TextView(getContext());
        tv.setText(ans);
        tv.setTextSize(14);
        tv.setMaxLines(1);
        tv.setEllipsize(TextUtils.TruncateAt.END);
        tv.setTextIsSelectable(true);
        LinearLayout.LayoutParams lpText = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f
        );
        lpText.gravity = Gravity.CENTER_VERTICAL;
        container.addView(tv, lpText);

        // 5) 복사 버튼
        ImageButton btnCopy = new ImageButton(getContext());
        btnCopy.setImageDrawable(
                AppCompatResources.getDrawable(getContext(), R.drawable.ic_copy)
        );
        btnCopy.setBackground(null);
        btnCopy.setOnClickListener(v -> {
            ClipboardManager cm = (ClipboardManager)
                    getContext().getSystemService(Context.CLIPBOARD_SERVICE);
            cm.setPrimaryClip(ClipData.newPlainText("답변", ans));
            Toast.makeText(getContext(), "복사되었습니다", Toast.LENGTH_SHORT).show();
        });
        LinearLayout.LayoutParams lpCopy = new LinearLayout.LayoutParams(
                dpToPx(24), dpToPx(24)
        );
        lpCopy.gravity = Gravity.CENTER_VERTICAL;
        lpCopy.setMargins(dpToPx(8), 0, 0, 0);
        container.addView(btnCopy, lpCopy);
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

//        // 1) 화면 전체 높이 가져오기
//        DisplayMetrics metrics = getResources().getDisplayMetrics();
//        int screenHeight = metrics.heightPixels;
//
//        // 2) 60% 비율 계산
//        int targetHeight = (int) (screenHeight * 0.6f);

        // ① 키보드 높이를 구해 온다
        int keyboardHeight = kbView.getHeight();

        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                LayoutParams.MATCH_PARENT,
                keyboardHeight);
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

    /** 에러 메시지 말풍선 추가 */
    public void bindError(String errorMsg) {
        TextView bubble = new TextView(getContext());
        bubble.setText(errorMsg);
        bubble.setTextSize(14);
        bubble.setTextColor(0xFF202020);
        bubble.setBackgroundResource(R.drawable.bg_bubble_bot);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.gravity = Gravity.START;
        lp.setMargins(0, dpToPx(4), 0, dpToPx(4));
        bubble.setLayoutParams(lp);

        mChatContainer.addView(bubble);
        scrollToBottom();
    }

    // (A) 로딩 스피너 말풍선 추가
    public void bindLoading() {
        // 1) 기존 로딩 말풍선 제거
        if (mLoadingBubble != null) {
            mChatContainer.removeView(mLoadingBubble);
        }
        // 2) 말풍선 컨테이너 생성
        LinearLayout bubble = new LinearLayout(getContext());
        bubble.setOrientation(LinearLayout.VERTICAL);
        bubble.setBackgroundResource(R.drawable.bg_bubble_bot);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.gravity = Gravity.START;
        lp.setMargins(0, dpToPx(4), 0, dpToPx(4));
        bubble.setLayoutParams(lp);

        // 3) LottieAnimationView 세팅
        LottieAnimationView lottie = new LottieAnimationView(getContext());
        LinearLayout.LayoutParams animLp = new LinearLayout.LayoutParams(
                dpToPx(40), dpToPx(40)); // 원하는 크기로 조절
        animLp.gravity = Gravity.CENTER;
        lottie.setLayoutParams(animLp);
        // assets/loading_spinner.json 로부터 애니메이션 로드
        lottie.setAnimation("chat_loading_spinner.json");
        lottie.setRepeatCount(LottieDrawable.INFINITE);
        lottie.playAnimation();

        bubble.addView(lottie);

        // 4) 뷰에 붙이고 스크롤
        mChatContainer.addView(bubble);
        scrollToBottom();
        mLoadingBubble = bubble;
    }

    // (B) 로딩 말풍선을 실제 응답 말풍선으로 교체
//    public void updateLoadingToResponse(MessageResponse resp) {
//        if (mLoadingBubble == null) {
//            // 혹시 로딩 말풍선이 없으면 기존 동작
//            bindResponseAndDetails(resp);
//            return;
//        }
//        // 기존 스피너 제거
//        mLoadingBubble.removeAllViews();
//        // 응답 텍스트 추가
//        TextView tv = new TextView(getContext());
//        String ans = resp.getAnswer() != null ? resp.getAnswer() : "결과 없음";
//        tv.setText(ans);
//        tv.setTextSize(14);
//        tv.setLineSpacing(0, 1.2f);
//        tv.setTextColor(0xFF202020);
//        tv.setPadding(dpToPx(8), dpToPx(6), dpToPx(8), dpToPx(6));
//        mLoadingBubble.addView(tv);
//
//        // (선택) 사진 결과도 동일 말풍선에 넣고 싶으면 bindResponseAndDetails 안의 사진 추가 코드를 여기에 복사
//
//        scrollToBottom();
//        mLoadingBubble = null;
//    }

    // (C) 로딩 말풍선을 에러 메시지 말풍선으로 교체
    public void updateLoadingToError(String errorMsg) {
        if (mLoadingBubble == null) {
            // 만약 로딩 말풍선이 없으면 그냥 에러 말풍선
            bindError(errorMsg);
            return;
        }
        mLoadingBubble.removeAllViews();
        TextView tv = new TextView(getContext());
        tv.setText(errorMsg);
        tv.setTextSize(14);
        tv.setTextColor(0xFF202020);
        tv.setPadding(dpToPx(8), dpToPx(6), dpToPx(8), dpToPx(6));
        mLoadingBubble.addView(tv);
        scrollToBottom();
        mLoadingBubble = null;
    }

    // 로딩 스피너만 지우는 메서드
    public void clearLoadingBubble() {
        if (mLoadingBubble != null) {
            mChatContainer.removeView(mLoadingBubble);
            mLoadingBubble = null;
        }
    }

    // 카드 UI 그리는 메서드
    public void bindLongTextCard(String query, MessageResponse resp) {
        // 1) 기존 뷰 제거
        mChatContainer.removeAllViews();

        // 2) fullText 준비
        final String fullText = resp != null && resp.getAnswer() != null
                ? resp.getAnswer() : "";

        // 3) 레이아웃 inflate
        FrameLayout card = (FrameLayout) LayoutInflater.from(getContext())
                .inflate(R.layout.view_long_text_response,
                        mChatContainer, false);

        // 4) 카드 높이 고정 (키보드 높이의 90%)
        int kbH = mKeyboardView.getHeight();
        if (kbH <= 0) {
            DisplayMetrics dm = getResources().getDisplayMetrics();
            kbH = (int)(dm.heightPixels * 0.6f);
        }
        int cardH = (int)(kbH * 0.9f);
        ViewGroup.LayoutParams cardLp = card.getLayoutParams();
        cardLp.height = cardH;
        card.setLayoutParams(cardLp);

        // 5) 제목 / 구분선
        TextView tvTitle = card.findViewById(R.id.lt_title);
        tvTitle.setVisibility(View.VISIBLE);
        tvTitle.setText(query);
//        card.findViewById(R.id.lt_divider).setVisibility(View.VISIBLE);
        View divider = card.findViewById(R.id.lt_divider);
        divider.setVisibility(View.VISIBLE);

        // 6) 본문 (ellipsize/maxLines 는 XML 에 선언되어 있습니다)
        final TextView tvContent = card.findViewById(R.id.lt_content);
        tvContent.setText(fullText);
        tvContent.setEllipsize(TextUtils.TruncateAt.END);

        tvContent.setTextIsSelectable(true);

        // 7) 버튼 높이 + 패딩/마진 보존용 dp → px
        int btnArea = dpToPx(40 /* 버튼 높이 */ + 12 /* 위여유 */ + 12 /* 아래여유 */);

        // 8) 제목+구분선+패딩 높이를 측정해서 reserved 픽셀 계산
        //    (post 로 measure 가 끝난 뒤에 실행)
        tvContent.post(() -> {
            int reserved = 0;
            // 제목 높이
            reserved += tvTitle.getHeight();
            // 구분선 + 위/아래 margin
            reserved += divider.getHeight();
            reserved += dpToPx(8 + 8); // XML에 준 top/bottom margin
            // 컨테이너 패딩 (FrameLayout padding)
            FrameLayout container = card;
            reserved += container.getPaddingTop() + container.getPaddingBottom();
            // 버튼 영역
            reserved += btnArea;

            // 9) tvContent 에 최대 높이만큼만 보이도록 설정
            int contentMaxH = cardH - reserved;
            tvContent.setMaxHeight(contentMaxH);
        });

        ImageButton btnCopy= card.findViewById(R.id.lt_btn_copy);
        btnCopy.setVisibility(View.VISIBLE);
        btnCopy.setOnClickListener(v -> {
            ClipboardManager cm = (ClipboardManager)
                    getContext().getSystemService(Context.CLIPBOARD_SERVICE);
            cm.setPrimaryClip(ClipData.newPlainText("tvContent", fullText));
            Toast.makeText(getContext(), "클립보드에 복사되었습니다", Toast.LENGTH_SHORT).show();
        });

        // ─────── 여기서 photosContainer 한 번만 선언 ───────
        HorizontalScrollView photoScroll =
                card.findViewById(R.id.photo_scroll);
        LinearLayout photosContainer =
                card.findViewById(R.id.photos_container);
        photoScroll.setVisibility(View.GONE);

        // 7) PhotoResult 썸네일 채우기
        List<String> photoIds = resp.getPhotoIds();
        if (photoIds != null) {
            for (String idStr : photoIds) {
                try {
                    long id = Long.parseLong(idStr);
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

                    Uri uri = ContentUris.withAppendedId(
                            MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id);
                    iv.setOnClickListener(v -> {
                        ClipboardManager cm = (ClipboardManager)
                                getContext().getSystemService(Context.CLIPBOARD_SERVICE);
                        cm.setPrimaryClip(ClipData.newUri(
                                getContext().getContentResolver(), "Image", uri));
                        Toast.makeText(getContext(),
                                "이미지가 클립보드에 복사되었습니다",
                                Toast.LENGTH_SHORT).show();
                    });
                } catch (NumberFormatException ignored) { }
            }
        }


        // 8) 전체 보기 버튼
        Button btnMore = card.findViewById(R.id.lt_btn_more);
        btnMore.setOnClickListener(v -> {
            // a) 전체 텍스트 표시
            tvContent.setText(fullText);
            tvContent.setEllipsize(null);
            tvContent.setMaxLines(Integer.MAX_VALUE);

            // b) 썸네일 스크롤뷰 보이기
            if (photosContainer.getChildCount() > 0) {
                photoScroll.setVisibility(View.VISIBLE);
                ViewGroup.MarginLayoutParams vlp =
                        (ViewGroup.MarginLayoutParams) card.findViewById(R.id.lt_vertical).getLayoutParams();
                vlp.bottomMargin = 0;
                card.findViewById(R.id.lt_vertical).setLayoutParams(vlp);
            }

            // c) 버튼 숨김
            btnMore.setVisibility(View.GONE);

            // d) 스크롤 맨 아래로
            mChatScroll.post(() -> mChatScroll.fullScroll(ScrollView.FOCUS_DOWN));

            // e) 카드 높이 wrap_content 로 해제 (필요시)
            ViewGroup.LayoutParams lp = card.getLayoutParams();
            lp.height = ViewGroup.LayoutParams.WRAP_CONTENT;
            card.setLayoutParams(lp);
        });

        // 9) 뷰 추가 & 스크롤
        mChatContainer.addView(card);
        scrollToBottom();
    }

    public void setLastQuery(String query) {
        mLastQuery = query;
    }


}
