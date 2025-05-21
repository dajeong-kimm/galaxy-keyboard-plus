package org.dslul.openboard.inputmethod.latin.search;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ContentUris;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.provider.MediaStore;
import android.text.TextUtils;
import android.text.method.LinkMovementMethod;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.ExtractedText;
import android.view.inputmethod.ExtractedTextRequest;
import android.view.inputmethod.InputConnection;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;

import com.airbnb.lottie.LottieAnimationView;
import com.airbnb.lottie.LottieDrawable;

import org.dslul.openboard.inputmethod.keyboard.MainKeyboardView;
import org.dslul.openboard.inputmethod.keyboard.MoreKeysPanel;
import org.dslul.openboard.inputmethod.keyboard.KeyboardActionListener;
import org.dslul.openboard.inputmethod.keyboard.emoji.OnKeyEventListener;
import org.dslul.openboard.inputmethod.latin.network.MessageResponse;
import org.dslul.openboard.inputmethod.latin.R;

import java.util.List;

import io.noties.markwon.AbstractMarkwonPlugin;
import io.noties.markwon.Markwon;
import io.noties.markwon.MarkwonConfiguration;
import io.noties.markwon.core.CorePlugin;
import io.noties.markwon.linkify.LinkifyPlugin;

public class SearchResultView extends FrameLayout implements MoreKeysPanel {
    // 채팅 말풍선 전체를 스크롤할 뷰
    private final ScrollView mChatScroll;
    // 말풍선들을 순서대로 추가할 컨테이너
    private final LinearLayout mChatContainer;
    private LinearLayout mLoadingBubble;
    private final Markwon markwon;
    private MainKeyboardView mKeyboardView;
    private Controller mController;
    private boolean mShowing;

    public SearchResultView(Context context) {
        super(context);

        // ★ Markdown 세팅
        markwon = Markwon.builder(context)
                .usePlugin(CorePlugin.create())
                .usePlugin(LinkifyPlugin.create())
                .usePlugin(new AbstractMarkwonPlugin() {
                    @Override
                    public void configureConfiguration(@NonNull MarkwonConfiguration.Builder builder) {
                        builder.linkResolver((view, link) -> {
                            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(link));
                            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                            view.getContext().startActivity(intent);
                        });
                    }
                })
                .build();

        LayoutInflater.from(context)
                .inflate(R.layout.search_result_view, this, true);
        mChatScroll = findViewById(R.id.chat_scroll);
        mChatContainer = findViewById(R.id.chat_container);
        mShowing = false;
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
    public void onMoveEvent(int x, int y, int pointerId, long eventTime) {}

    @Override
    public void onDownEvent(int x, int y, int pointerId, long eventTime) {}

    @Override
    public void onUpEvent(int x, int y, int pointerId, long eventTime) {}

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
    public int translateX(int x) {return x;}

    @Override
    public int translateY(int y) {return y;}

    @Override
    public void showInParent(ViewGroup parentView) {}

    @Override
    public void removeFromParent() {}

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
            kbH = (int) (dm.heightPixels * 0.6f);
        }
        int cardH = (int) (kbH * 0.9f);
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
        tvContent.setMovementMethod(LinkMovementMethod.getInstance());
        markwon.setMarkdown(tvContent, fullText);
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

        ImageButton btnCopy = card.findViewById(R.id.lt_btn_copy);
        btnCopy.setVisibility(View.VISIBLE);
        btnCopy.setOnClickListener(v -> {
            ClipboardManager cm = (ClipboardManager)
                    getContext().getSystemService(Context.CLIPBOARD_SERVICE);
            cm.setPrimaryClip(ClipData.newPlainText("tvContent", fullText));
            Log.d("clipboard", "clipboard 저장");

            // 2) 호스트 앱 편집창 전체 텍스트를 요청
            if (mKeyboardView != null) {
                InputConnection ic = mKeyboardView.getInputConnection();
                if (ic != null) {
                    // 요청 크기 한도 늘리기 (전체 텍스트 확보용)
                    ExtractedTextRequest req = new ExtractedTextRequest();
                    req.hintMaxChars = Integer.MAX_VALUE;
                    req.hintMaxLines = Integer.MAX_VALUE;
                    // 컴포지션 중인 텍스트 먼저 확정
                    ic.finishComposingText();
                    // 실제 텍스트 가져오기
                    ExtractedText et = ic.getExtractedText(req, 0);
                    if (et != null && et.text != null) {
                        int len = et.text.length();
                        // 전체 선택 후 빈 문자열로 덮어쓰기
                        ic.beginBatchEdit();
                        ic.setSelection(0, len);
                        ic.commitText("", 1);
                        ic.endBatchEdit();
                    }
                }
            }
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

                    // 실패했거나 null이면 그냥 스킵
                    if (thumb == null) {
                        continue;
                    }

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
                        if (mKeyboardView != null) {
                            InputConnection ic = mKeyboardView.getInputConnection();
                            if (ic != null) {
                                // 요청 크기 한도 늘리기 (전체 텍스트 확보용)
                                ExtractedTextRequest req = new ExtractedTextRequest();
                                req.hintMaxChars = Integer.MAX_VALUE;
                                req.hintMaxLines = Integer.MAX_VALUE;
                                // 컴포지션 중인 텍스트 먼저 확정
                                ic.finishComposingText();
                                // 실제 텍스트 가져오기
                                ExtractedText et = ic.getExtractedText(req, 0);
                                if (et != null && et.text != null) {
                                    int len = et.text.length();
                                    // 전체 선택 후 빈 문자열로 덮어쓰기
                                    ic.beginBatchEdit();
                                    ic.setSelection(0, len);
                                    ic.commitText("", 1);
                                    ic.endBatchEdit();
                                }
                            }
                        }
                    });
                } catch (NumberFormatException ignored) {
                }
            }
        }

        // 8) 전체 보기 버튼
        Button btnMore = card.findViewById(R.id.lt_btn_more);
        btnMore.setOnClickListener(v -> {
            // a) 전체 텍스트 표시
            markwon.setMarkdown(tvContent, fullText);
            tvContent.setMovementMethod(LinkMovementMethod.getInstance());
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
}
