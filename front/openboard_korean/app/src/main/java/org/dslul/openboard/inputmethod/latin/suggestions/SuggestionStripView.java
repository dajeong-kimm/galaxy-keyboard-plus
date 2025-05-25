/*
 * Copyright (C) 2011 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.dslul.openboard.inputmethod.latin.suggestions;

import android.animation.ValueAnimator;
import android.content.ClipData;
import android.content.ClipDescription;
import android.content.ClipboardManager;
import android.content.ContentUris;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Point;
import android.graphics.PorterDuff;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.provider.MediaStore;
import android.util.AttributeSet;
import android.util.Log;
import android.util.TypedValue;
import android.view.DragEvent;
import android.view.GestureDetector;
import android.view.HapticFeedbackConstants;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnLongClickListener;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.view.accessibility.AccessibilityEvent;
import android.view.animation.OvershootInterpolator;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.ExtractedText;
import android.view.inputmethod.ExtractedTextRequest;
import android.view.inputmethod.InputConnection;
import android.widget.Button;
import android.widget.HorizontalScrollView;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import org.dslul.openboard.inputmethod.accessibility.AccessibilityUtils;
import org.dslul.openboard.inputmethod.keyboard.Keyboard;
import org.dslul.openboard.inputmethod.keyboard.KeyboardActionListener;
import org.dslul.openboard.inputmethod.keyboard.MainKeyboardView;
import org.dslul.openboard.inputmethod.keyboard.MoreKeysPanel;
import org.dslul.openboard.inputmethod.latin.AudioAndHapticFeedbackManager;
import org.dslul.openboard.inputmethod.latin.LatinIME;
import org.dslul.openboard.inputmethod.latin.R;
import org.dslul.openboard.inputmethod.latin.SuggestedWords;
import org.dslul.openboard.inputmethod.latin.SuggestedWords.SuggestedWordInfo;
import org.dslul.openboard.inputmethod.latin.auth.AuthManager;
import org.dslul.openboard.inputmethod.latin.common.Constants;
import org.dslul.openboard.inputmethod.latin.define.DebugFlags;
import org.dslul.openboard.inputmethod.latin.network.ApiClient;
import org.dslul.openboard.inputmethod.latin.network.ChatSaveService;
import org.dslul.openboard.inputmethod.latin.network.ClipBoardResponse;
import org.dslul.openboard.inputmethod.latin.network.ClipboardService;
import org.dslul.openboard.inputmethod.latin.network.KeywordApi;
import org.dslul.openboard.inputmethod.latin.network.KeywordExistsResponse;
import org.dslul.openboard.inputmethod.latin.network.KeywordImagesResponse;
import org.dslul.openboard.inputmethod.latin.network.MessageResponse;
import org.dslul.openboard.inputmethod.latin.search.SearchResultView;
import org.dslul.openboard.inputmethod.latin.settings.Settings;
import org.dslul.openboard.inputmethod.latin.settings.SettingsValues;
import org.dslul.openboard.inputmethod.latin.suggestions.MoreSuggestionsView.MoreSuggestionsListener;

import java.util.ArrayList;
import java.util.List;

import androidx.core.content.ContextCompat;
import androidx.core.view.ViewCompat;
import androidx.core.view.inputmethod.EditorInfoCompat;
import androidx.core.view.inputmethod.InputConnectionCompat;
import androidx.core.view.inputmethod.InputContentInfoCompat;
import androidx.interpolator.view.animation.FastOutSlowInInterpolator;

import com.airbnb.lottie.LottieAnimationView;
import com.airbnb.lottie.LottieDrawable;
import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions;
import com.google.gson.Gson;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;
import org.dslul.openboard.inputmethod.event.HangulCommitEvent;

public final class SuggestionStripView extends RelativeLayout implements OnClickListener, OnLongClickListener {
    public interface Listener {
        void pickSuggestionManually(SuggestedWordInfo word);

        void onCodeInput(int primaryCode, int x, int y, boolean isKeyRepeat);

        void onTextInput(final String rawText);

        CharSequence getSelection();
    }

    /* ▼ 새로 추가할 필드들 --------------------------------------------------- */
    private static final Typeface STRIP_TYPEFACE = Typeface.create("samsung_one", Typeface.NORMAL); // 시스템 폰트명
    private static final float STRIP_TEXT_SCALE = 0.9f;     // 10% 확대 (원하면 조정)
    private ImageButton mFetchClipboardKey;
    private int mDefaultHeight = 0;
    private HorizontalScrollView mPhotoBar;
    private View mWrapper;
    private LinearLayout mPhotoBarContainer;
    private TextView mSearchAnswer;
    private LottieAnimationView mSearchKey;
    private LottieAnimationView mKeywordKey;
    private ImageView mLoadingSpinner;
    private String mLastKeywordWithImages = null;
    private ImageButton mVoiceKey;       // 마이크
    private Button mSearchStatus;
    private boolean mInSearchMode = false;
    private String mLastQuery;
    private LatinIME mImeService = null;

    private boolean mIsDragging = false;
    private int mDragExtra = 0;
    private boolean mDragHover = false;
    private Paint mOverlayPaint;
    private Paint mOverlayPaintHover;

    // 기존 필드 바로 아래
    private Drawable mIconClose;    // X 아이콘
//    private ImageButton mCopyKey;

    private static final String TAG_NET = "SearchAPI";
    private static String DEFAULT_USER_ID;
    private SearchResultView mSearchPanel;

    static final boolean DBG = DebugFlags.DEBUG_ENABLED;
    private static final float DEBUG_INFO_TEXT_SIZE_IN_DIP = 6.0f;

    private final ViewGroup mSuggestionsStrip;
    private final ImageButton mClipboardKey;
    //    private final ImageButton mOtherKey;
    MainKeyboardView mMainKeyboardView;

    // ── ① LONG_TEXT 상황 구분용
    private enum ResponseType {LONG_TEXT, PHOTO_ONLY, SHORT_TEXT}

    private ResponseType mResponseType;
    private MessageResponse mLastResponse;       // ◀ 수정
    private boolean mKeyHighlighted = false; // 깜빡임→강조 상태 구분  ◀ 수정
    private boolean mAnswerShown = false;    // 답변(말풍선) 이미 그렸는지  ◀ 수정

    private final View mMoreSuggestionsContainer;
    private final MoreSuggestionsView mMoreSuggestionsView;
    private final MoreSuggestions.Builder mMoreSuggestionsBuilder;

    private final ArrayList<TextView> mWordViews = new ArrayList<>();
    private final ArrayList<TextView> mDebugInfoViews = new ArrayList<>();
    private final ArrayList<View> mDividerViews = new ArrayList<>();

    Listener mListener;
    private SuggestedWords mSuggestedWords = SuggestedWords.getEmptyInstance();
    private int mStartIndexOfMoreSuggestions;

    private final SuggestionStripLayoutHelper mLayoutHelper;
    private final StripVisibilityGroup mStripVisibilityGroup;

    private ValueAnimator mBorderPulseAnimator;
    private Drawable mOriginalSearchKeyBg;

    private EditorInfo mEditorInfo;
    private Drawable mDropIcon;
    private int mDropIconSize;

    private final int mPhotoBarSizePx;
    private final LinearLayout.LayoutParams mPhotoItemLp;

    private Drawable mOriginalStripBackground;

    /**
     * IME 서비스로부터 EditorInfo 를 전달받습니다
     */
    public void setEditorInfo(EditorInfo info) {
        mEditorInfo = info;

        // 지원할 MIME 타입을 에디터에 등록
        // AndroidX EditorInfoCompat 사용
        String[] mimeTypes = new String[]{"image/*"};
        EditorInfoCompat.setContentMimeTypes(info, mimeTypes);
    }

    private static class StripVisibilityGroup {
        private final View mSuggestionStripView;
        private final View mSuggestionsStrip;

        public StripVisibilityGroup(final View suggestionStripView, final ViewGroup suggestionsStrip) {
            mSuggestionStripView = suggestionStripView;
            mSuggestionsStrip = suggestionsStrip;
            showSuggestionsStrip();
        }

        public void setLayoutDirection(final boolean isRtlLanguage) {
            final int layoutDirection = isRtlLanguage ? ViewCompat.LAYOUT_DIRECTION_RTL : ViewCompat.LAYOUT_DIRECTION_LTR;
            ViewCompat.setLayoutDirection(mSuggestionStripView, layoutDirection);
            ViewCompat.setLayoutDirection(mSuggestionsStrip, layoutDirection);
        }

        public void showSuggestionsStrip() {
            mSuggestionsStrip.setVisibility(GONE);
        }
    }

    /**
     * Construct a {@link SuggestionStripView} for showing suggestions to be picked by the user.
     *
     * @param context
     * @param attrs
     */
    public SuggestionStripView(final Context context, final AttributeSet attrs) {
        this(context, attrs, R.attr.suggestionStripViewStyle);
    }

    public SuggestionStripView(final Context context, final AttributeSet attrs, final int defStyle) {
        super(context, attrs, defStyle);

        // 1) dp → px 변환을 미리 해두기
        mPhotoBarSizePx = dpToPx(96);
        int photoItemMargin = dpToPx(4);

        // 2) LayoutParams도 한 번만 생성
        mPhotoItemLp = new LinearLayout.LayoutParams(mPhotoBarSizePx, mPhotoBarSizePx);
        mPhotoItemLp.setMargins(photoItemMargin, 0, photoItemMargin, 0);

        // ─── ContextThemeWrapper 언래핑 ───
        Context base = context;
        while (base instanceof ContextWrapper && !(base instanceof LatinIME)) {
            base = ((ContextWrapper) base).getBaseContext();
        }
        if (base instanceof LatinIME) {
            mImeService = (LatinIME) base;
        } else {
            Log.w(TAG_NET, "SuggestionStripView: LatinIME 인스턴스 찾기 실패");
        }
        // ─────────────────────────────────────

        // AuthManager에 들어있는 userId를 사용.
        DEFAULT_USER_ID = AuthManager.getInstance(context).getUserId();

        final LayoutInflater inflater = LayoutInflater.from(context);
        inflater.inflate(R.layout.suggestions_strip, this);
        setBackgroundColor(ContextCompat.getColor(context, R.color.phokey_strip_bg));

        mSuggestionsStrip = findViewById(R.id.suggestions_strip);
        mSuggestionsStrip.setVisibility(View.GONE);
        mVoiceKey = findViewById(R.id.suggestions_strip_voice_key);
        mClipboardKey = findViewById(R.id.suggestions_strip_clipboard_key);
        mStripVisibilityGroup = new StripVisibilityGroup(this, mSuggestionsStrip);

        // blink 애니메이션 리소스 로드  ◀ 수정
        mKeyHighlighted = false;
        mAnswerShown = false;

        mDropIcon = ContextCompat.getDrawable(context, R.drawable.ic_paste_here);
        mDropIconSize = dpToPx(200); // 원하는 픽셀 크기

        for (int pos = 0; pos < SuggestedWords.MAX_SUGGESTIONS; pos++) {
            final TextView word = new TextView(context, null, R.attr.suggestionWordStyle);
            // ① 글꼴 적용
            word.setTypeface(STRIP_TYPEFACE);
            // ② 글꼴 크기 일괄 스케일
            word.setTextSize(TypedValue.COMPLEX_UNIT_PX, word.getTextSize() * STRIP_TEXT_SCALE);

            word.setContentDescription(getResources().getString(R.string.spoken_empty_suggestion));
            word.setOnClickListener(this);
            word.setOnLongClickListener(this);
            mWordViews.add(word);
            final View divider = inflater.inflate(R.layout.suggestion_divider, null);
            mDividerViews.add(divider);
            final TextView info = new TextView(context, null, R.attr.suggestionWordStyle);
            info.setTextColor(Color.WHITE);
            info.setTextSize(TypedValue.COMPLEX_UNIT_DIP, DEBUG_INFO_TEXT_SIZE_IN_DIP);
            mDebugInfoViews.add(info);
        }

        mLayoutHelper = new SuggestionStripLayoutHelper(context, attrs, defStyle, mWordViews, mDividerViews, mDebugInfoViews);

        mMoreSuggestionsContainer = inflater.inflate(R.layout.more_suggestions, null);
        mMoreSuggestionsView = mMoreSuggestionsContainer.findViewById(R.id.more_suggestions_view);
        mMoreSuggestionsBuilder = new MoreSuggestions.Builder(context, mMoreSuggestionsView);
        applyStripTypefaceRecursively(mMoreSuggestionsContainer);

        final Resources res = context.getResources();
        mMoreSuggestionsModalTolerance = res.getDimensionPixelOffset(R.dimen.config_more_suggestions_modal_tolerance);
        mMoreSuggestionsSlidingDetector = new GestureDetector(context, mMoreSuggestionsSlidingListener);

        final TypedArray keyboardAttr = context.obtainStyledAttributes(attrs, R.styleable.Keyboard, defStyle, R.style.SuggestionStripView);
        final Drawable iconVoice = keyboardAttr.getDrawable(R.styleable.Keyboard_iconShortcutKey);
        final Drawable iconIncognito = keyboardAttr.getDrawable(R.styleable.Keyboard_iconIncognitoKey);
        final Drawable iconClipboard = keyboardAttr.getDrawable(R.styleable.Keyboard_iconClipboardNormalKey);
        keyboardAttr.recycle();

        mVoiceKey.setImageDrawable(iconVoice);

        mSearchStatus = findViewById(R.id.suggestions_strip_search_status);
        // 🔍, ❌ 아이콘 준비
        mIconClose = getResources().getDrawable(R.drawable.ic_close, null);

        mSearchKey = findViewById(R.id.suggestions_strip_search_key);
        if (mSearchKey == null) {
            throw new IllegalStateException("suggestions_strip_search_key not found in current layout variant");
        }
        mOriginalSearchKeyBg = mSearchKey.getBackground();

        mKeywordKey = findViewById(R.id.suggestions_strip_keyword_key);
        mKeywordKey.setVisibility(View.GONE);
        if (mKeywordKey == null) {
            throw new IllegalStateException(
                    "suggestions_strip_keyword_key not found in current layout variant");
        }
        mKeywordKey.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                Log.d("KeywordSearch", "사진(키워드) 버튼 클릭됨");

                if (mLastKeywordWithImages == null) {
                    Log.d("KeywordSearch", "검색할 키워드 없음 → 패널에 '검색할 키워드가 없습니다' 표시");
                    return;
                }
                Log.d("KeywordSearch", "키워드 \"" + mLastKeywordWithImages + "\"에 대해 이미지 API 호출");
                KeywordApi api = ApiClient.getKeywordApi();
                Call<KeywordImagesResponse> call = api.getImages(DEFAULT_USER_ID, mLastKeywordWithImages, 1, 20);
                call.enqueue(new Callback<KeywordImagesResponse>() {
                    @Override
                    public void onResponse(Call<KeywordImagesResponse> call, Response<KeywordImagesResponse> response) {
                        if (response.isSuccessful() && response.body() != null) {
                            List<String> imageIds = response.body().imageIds;
                            Log.d("KeywordSearch", "이미지 API 응답 성공, 이미지 개수: " + (imageIds != null ? imageIds.size() : 0));
                        } else {
                            Log.d("KeywordSearch", "이미지 API 응답 실패 또는 결과 없음 → 패널에 '이미지가 없습니다' 표시");
                        }
                    }

                    @Override
                    public void onFailure(Call<KeywordImagesResponse> call, Throwable t) {
                        Log.e("KeywordSearch", "이미지 API 호출 실패: " + t.getMessage(), t);
                    }
                });
            }
        });

        mSearchKey.setOnClickListener(this);
        mSearchStatus.setOnClickListener(this);

        mVoiceKey.setOnClickListener(this);
        mClipboardKey.setImageDrawable(iconClipboard);
        mClipboardKey.setOnClickListener(this);
        mClipboardKey.setOnLongClickListener(this);

        ContextCompat.getColor(context, R.color.phokey_icon_tint);
        int tint = ContextCompat.getColor(context, R.color.phokey_icon_tint);
        mVoiceKey.setColorFilter(tint, PorterDuff.Mode.SRC_IN);
        mClipboardKey.setColorFilter(tint, PorterDuff.Mode.SRC_IN);

        mFetchClipboardKey = findViewById(R.id.suggestions_strip_fetch_clipboard);
        mFetchClipboardKey.setOnClickListener(this);

        mWrapper = findViewById(R.id.suggestions_strip_wrapper);
        mPhotoBar = findViewById(R.id.suggestions_strip_photo_bar);
        mPhotoBarContainer = findViewById(R.id.photo_bar_container);
        mSearchAnswer = findViewById(R.id.search_answer);

        post(() -> {
            if (mDefaultHeight == 0) {
                mDefaultHeight = getHeight();
            }
        });

        // 로딩 스피너 준비
        mLoadingSpinner = new ImageView(context);
        mLoadingSpinner.setVisibility(GONE);
        mLoadingSpinner.setScaleType(ImageView.ScaleType.CENTER_INSIDE);

        // 2) Glide로 GIF 로드
        Glide.with(context)
                .asGif()
                .load(R.drawable.galaxyai_loading_spinner)
                .diskCacheStrategy(DiskCacheStrategy.ALL)          // 메모리+디스크 캐시
                .transition(DrawableTransitionOptions.withCrossFade())
                .into(mLoadingSpinner);

        // 3) 레이아웃 파라미터 (CENTER_IN_PARENT)
        LayoutParams lpSpinner =
                new LayoutParams(
                        LayoutParams.WRAP_CONTENT,
                        LayoutParams.WRAP_CONTENT);
        lpSpinner.addRule(RelativeLayout.CENTER_IN_PARENT);

        // 4) 뷰 계층에 추가 (`this`는 RelativeLayout)
        this.addView(mLoadingSpinner, lpSpinner);

        // SuggestionStripView 생성자나 init() 내부
        this.setOnDragListener((v, event) -> {
            switch (event.getAction()) {
                case DragEvent.ACTION_DRAG_STARTED:
                    // 드래그 가능한 데이터인지 검사
                    if (event.getClipDescription().hasMimeType(ClipDescription.MIMETYPE_TEXT_URILIST)) {
                        // 1) IME Insets Freeze ON
                        if (mImeService != null) mImeService.setDragging(true);

                        expandDragArea();
                        return true;
                    }
                    return false;

                case DragEvent.ACTION_DRAG_LOCATION:
                    // y 좌표가 strip 최상단(=0)에서 mDragExtra 안쪽이면 hover 로 간주
                    float y = event.getY();
                    boolean nowHover = (y < mDragExtra);
                    if (nowHover != mDragHover) {
                        mDragHover = nowHover;   // 상태 갱신
                        invalidate();            // 색 다시 칠하게 draw() 호출
                    }
                    return true;

                case DragEvent.ACTION_DROP:
                    // 드롭 위치가 추가된 빈 공간 안인지 체크
                    float area = event.getY();
                    if (area < mDragExtra) {
                        // ── 추가된 영역: 기존 로직 (commitContent or clipboard-paste) 실행
                        Uri uri = Uri.parse(event.getClipData().getItemAt(0).getText().toString());
                        InputConnection ic = mMainKeyboardView.getInputConnection();
                        boolean handled = false;
                        if (ic != null && mEditorInfo != null) {
                            ClipDescription desc = new ClipDescription("pasted image", new String[]{"image/*"});
                            InputContentInfoCompat content =
                                    new InputContentInfoCompat(uri, desc, null);
                            handled = InputConnectionCompat.commitContent(
                                    ic, mEditorInfo, content,
                                    InputConnectionCompat.INPUT_CONTENT_GRANT_READ_URI_PERMISSION,
                                    null);
                        }
                        if (!handled && ic != null) {
                            // fallback: clipboard → paste
                            ClipboardManager cm =
                                    (ClipboardManager) getContext().getSystemService(Context.CLIPBOARD_SERVICE);
                            cm.setPrimaryClip(ClipData.newUri(
                                    getContext().getContentResolver(), "Image", uri));
                            ic.performContextMenuAction(android.R.id.paste);
                        }
                    } else {
                        // ── 기존 썸네일 영역: 아무 작업 없이 드래그만 정리
                    }
                    // 공통으로 드래그 모드 해제 & 원복
                    mDragHover = false;
                    if (mImeService != null) mImeService.setDragging(false);
                    collapseDragArea();
                    return true;

                case DragEvent.ACTION_DRAG_ENDED:
                    mDragHover = false;
                    // 1) IME Insets Freeze OFF
                    if (mImeService != null) mImeService.setDragging(false);

                    // 혹시 DROP 이외에 취소된 경우에도 알파 복원
                    View original = (View) event.getLocalState();
                    if (original != null) original.setAlpha(1f);

                    // 크기 원복
                    collapseDragArea();
                    return true;

                default:
                    return true;
            }
        });

        // 반투명 오버레이용 Paint
        mOverlayPaint = new Paint();
        mOverlayPaintHover = new Paint();
        mOverlayPaint.setColor(Color.parseColor("#15000000"));
        mOverlayPaintHover.setColor(Color.parseColor("#40000000"));
    }

    /* ▼ EventBus로 HangulCommitEvent 이벤트 구독 --------------------------------------------------- */
    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onHangulCommitEvent(HangulCommitEvent event) {
        Log.d("KeywordSearch", "받은 HangulCommitEvent: type=" + event.type + ", text=" + event.text);

        // 실제 동작 예시
        if (event.type == HangulCommitEvent.TYPE_SYLLABLE) {
            // 1. 입력창에서 최근 100자 이내 텍스트 가져오기
            // String input = getSearchInput().getText().toString();
            String input = event.text != null ? event.text : "";

            if (input.length() > 100) {
                input = input.substring(input.length() - 100);
            }

            // 2. 띄어쓰기(split)해서 마지막 단어 추출
            String[] tokens = input.split("\\s+"); // 여러 공백도 대응
            if (tokens.length == 0) return;        // 아무 단어도 없으면 중단

            String lastWord = tokens[tokens.length - 1];
            if (lastWord.isEmpty()) return;        // 마지막이 공백일 경우도 방지

            // 3. (예시) user_id를 준비 (실제 값에 맞게)
            String userId = DEFAULT_USER_ID; // 실제 구현에서는 세션 등에서 받아오기

            // 4. exists API 호출 (Retrofit2 사용)
            KeywordApi api = ApiClient.getKeywordApi();
            Call<KeywordExistsResponse> call = api.exists(userId, lastWord);

            // 5. 비동기 호출 및 결과 처리
            call.enqueue(new Callback<KeywordExistsResponse>() {
                @Override
                public void onResponse(Call<KeywordExistsResponse> call, Response<KeywordExistsResponse> response) {
                    if (response.isSuccessful() && response.body() != null) {
                        boolean exists = response.body().exists;
                        Log.d("KeywordSearch", "[API] 단어 \"" + lastWord + "\" 존재여부: " + exists);

                        if (exists && mSearchKey != null) {
                            if (!mSearchKey.isAnimating() && !mKeyHighlighted) {
                                int[] gradientColors = new int[]{
                                        Color.parseColor("#DDA0FF"), // 연한 네온 바이올렛
                                        Color.parseColor("#A0DFFF"), // 연한 네온 스카이블루
                                        Color.parseColor("#A0FFD6")  // 연한 네온 민트
                                };

                                GradientDrawable glowBg = new GradientDrawable();
                                glowBg.setShape(GradientDrawable.OVAL);
                                // 그라데이션 타입을 RADIAL 로
                                glowBg.setGradientType(GradientDrawable.LINEAR_GRADIENT);
                                glowBg.setOrientation(GradientDrawable.Orientation.TL_BR);
                                glowBg.setColors(gradientColors);
                                // 부드러운 흐림 효과를 위해 외곽 스트로크를 반투명으로
                                glowBg.setStroke(dpToPx(2), Color.argb(0x40, 255, 255, 255));

                                // 2) 뷰 배경에 바로 적용
                                mSearchKey.setBackground(glowBg);

                                mSearchKey.setRepeatCount(LottieDrawable.INFINITE);
                                mSearchKey.setAnimation("ic_search.json");
                                mSearchKey.playAnimation();

                                mBorderPulseAnimator = ValueAnimator.ofInt(1, 200);
                                mBorderPulseAnimator.setDuration(500);
                                mBorderPulseAnimator.setRepeatMode(ValueAnimator.REVERSE);
                                mBorderPulseAnimator.setRepeatCount(ValueAnimator.INFINITE);
                                mBorderPulseAnimator.addUpdateListener(anim -> {
                                    int alpha = (int) anim.getAnimatedValue();
                                    // 전체 글로우 배경의 투명도 조절
                                    glowBg.setAlpha(alpha);
                                });
                                mBorderPulseAnimator.start();

                                mLastKeywordWithImages = lastWord;
                            }
                        }
                    } else {
                        Log.e("KeywordSearch", "API 응답 실패: " + response.code());
                    }
                }

                @Override
                public void onFailure(Call<KeywordExistsResponse> call, Throwable t) {
                    Log.e("KeywordSearch", "API 호출 에러: ", t);
                }
            });
        } else if (event.type == HangulCommitEvent.TYPE_END) {
            if (mSearchKey != null && mSearchKey.isAnimating()) {
                mSearchKey.pauseAnimation();
                mSearchKey.setProgress(0f);

                if (mBorderPulseAnimator != null) {
                    mBorderPulseAnimator.cancel();
                    mBorderPulseAnimator = null;
                }

                mSearchKey.setBackground(mOriginalSearchKeyBg);
                mSearchKey.setLayerType(View.LAYER_TYPE_NONE, null);
            }

            mKeyHighlighted = false;
            mLastKeywordWithImages = null;
            // 애니메이션 JSON도 원래대로 돌려놓기
            mSearchKey.clearAnimation();
            mSearchKey.setAnimation("ic_search.json");
            mSearchKey.setRepeatCount(0);
            mSearchKey.setProgress(0f);
        }
    }

    // ========== Search Mode helpers ======================================
    private void enterSearchMode() {
        if (mInSearchMode) return;
        mInSearchMode = true;

        // 1) 모든 키/버튼 숨기기
        mSearchKey.setVisibility(GONE);
        mVoiceKey.setVisibility(GONE);
        mClipboardKey.setVisibility(GONE);
        mFetchClipboardKey.setVisibility(GONE);
        mSearchStatus.setVisibility(GONE);
        mSuggestionsStrip.setVisibility(GONE);

        // 2) 뷰 높이를 애니메이션으로 늘리기 (PHOTO_ONLY 때처럼)
//        int photoBarHeight = dpToPx(96) + dpToPx(6);
//        final View strip = this;
//        int startH = strip.getHeight();
//        int endH = photoBarHeight;
//        ValueAnimator expandAnim = ValueAnimator.ofInt(startH, endH);
//        expandAnim.setDuration(500);
//        expandAnim.setInterpolator(new FastOutSlowInInterpolator());
//        expandAnim.addUpdateListener(anim -> {
//            ViewGroup.LayoutParams lp = strip.getLayoutParams();
//            lp.height = (int) anim.getAnimatedValue();
//            strip.setLayoutParams(lp);
//        });
//        expandAnim.start();

        // 3) 로딩 스피너 보이기
        mLoadingSpinner.setScaleX(0.8f);
        mLoadingSpinner.setScaleY(0.8f);
        mLoadingSpinner.setVisibility(VISIBLE);
        mLoadingSpinner.bringToFront();

        // 3) 실제 API 호출
        dispatchSearchQuery();
    }

    private void dispatchSearchQuery() {
        // 0) panel이 없으면 새로 만들고 리스너 바인드`
        if (mSearchPanel == null) {
            mSearchPanel = new SearchResultView(getContext());
            // SuggestionStripView의 mListener와 키보드 뷰를 넘겨 줍니다
            setListener(mListener, getRootView());
        }

        String query;
        // 내부 입력창이 아니라 외부 입력창에서 텍스트 가져오기
        InputConnection ic = mMainKeyboardView.getInputConnection();
        if (ic != null) {
            ExtractedText et = ic.getExtractedText(new ExtractedTextRequest(), 0);
            query = et != null && et.text != null ? et.text.toString().trim() : "";
        } else {
            query = "";
        }

        mLastQuery = query;  // ◀ 사용자가 입력한 원본 질문 보관
        Log.d("SugStrip", "dispatchSearchQuery: mLastQuery = \"" + mLastQuery + "\"");
        // ✅ 로그 출력 추가
        Log.d(TAG_NET, "🔍 전송된 query: " + query);

        // ─── 사용자 질문 저장 ───
        ChatSaveService.enqueue(
                getContext(),
                DEFAULT_USER_ID,
                "user",
                query,
                null
        );

        // 3) 로딩 스피너만 붙이기
        mSearchPanel.clearLoadingBubble();  // 혹시 이전 로딩이 남아 있으면 지우고
        mSearchPanel.bindLoading();

        Log.d(TAG_NET, "▶ REQUEST\n" + "user_id = " + DEFAULT_USER_ID + "\n" + "query   = " + query);

        // ① Retrofit 호출
        ApiClient.getChatApiService().search(DEFAULT_USER_ID, query).enqueue(new Callback<MessageResponse>() {
            @Override
            public void onResponse(Call<MessageResponse> call, Response<MessageResponse> res) {
                if (!res.isSuccessful()) {
                    Log.e(TAG_NET, "❌ " + res.code() + " " + res.message());
                    return;
                }
                MessageResponse body = res.body();
                if (body == null) return;

                // ─── 봇 응답 저장 ───
                List<String> photos = body.getPhotoIds();
                String itemsJson = null;
                if (photos != null) {
                    itemsJson = new Gson().toJson(photos);
                }
                ChatSaveService.enqueue(
                        getContext(),
                        DEFAULT_USER_ID,
                        "bot",
                        body.getAnswer(),
                        itemsJson
                );

                post(() -> {
                    // ① **스피너 숨기기**
                    mLoadingSpinner.setVisibility(View.GONE);
                    mSearchKey.setVisibility(VISIBLE);

                    if (body.getType().equals("info_search") || body.getType().equals("conversation"))
                        mResponseType = ResponseType.LONG_TEXT;
                    else mResponseType = ResponseType.PHOTO_ONLY;

                    mLastResponse = body;

                    View strip;
                    int startH;
                    int endH;

                    // 2) 분기별 행동
                    switch (mResponseType) {
                        case LONG_TEXT:
                            // ── 50자 이상: 버튼 강조 후 대기 ──
                            Log.d("행동", "LONG_TEXT = \"" + body.getAnswer() + "\"");
                            mSearchPanel.clearLoadingBubble();
                            mSearchStatus.setVisibility(View.GONE);
                            mSearchKey.pauseAnimation();
                            mSearchKey.setRepeatCount(0);
                            mSearchKey.setAnimation("ic_search_blue.json"); // 파랑 정지된 JSON
                            mSearchKey.setProgress(0f);
                            mKeyHighlighted = true;

                            // ── SuggestionStripView 높이 애니메이션으로 원복 ──
                            strip = SuggestionStripView.this;
                            startH = strip.getHeight();
                            endH = mDefaultHeight;  // 생성 시 저장해 둔 기본 높이
                            ValueAnimator collapseAnim = ValueAnimator.ofInt(startH, endH);
                            collapseAnim.setDuration(500);
                            collapseAnim.setInterpolator(new FastOutSlowInInterpolator());
                            collapseAnim.addUpdateListener(anim -> {
                                strip.getLayoutParams().height = (int) anim.getAnimatedValue();
                                strip.requestLayout();
                            });
                            collapseAnim.start();

                            if (mBorderPulseAnimator != null) {
                                mBorderPulseAnimator.cancel();
                                mBorderPulseAnimator = null;
                            }
                            // ② 원래 배경으로 복원
                            mSearchKey.setBackground(mOriginalSearchKeyBg);
                            // ③ (선택) 레이어 타입도 원래대로 돌려놓기
                            mSearchKey.setLayerType(View.LAYER_TYPE_NONE, null);

                            mVoiceKey.setVisibility(VISIBLE);
                            mFetchClipboardKey.setVisibility(VISIBLE);

                            // ★ 키보드 웨이브 애니메이션 중지
                            stopKeyboardAnimation();

                            break;
                        case PHOTO_ONLY:
                            SuggestionStripView.this.setLayerType(View.LAYER_TYPE_HARDWARE, null);

                            Log.d("행동", "PHOTO_ONLY = \"" + body.getAnswer() + "\"");
                            Log.d(TAG_NET, "[PHOTO_ONLY] before dismiss: panelShowing=" + isShowingMoreSuggestionPanel() + ", stripVis=" + mSuggestionsStrip.getVisibility());

                            // 1) 남아 있는 추천 단어 팝업이 떠 있으면 닫기
                            if (isShowingMoreSuggestionPanel()) {
                                dismissMoreSuggestionsPanel();
                                Log.d(TAG_NET, "[PHOTO_ONLY] after dismiss: panelShowing=" + isShowingMoreSuggestionPanel());
                            }
                            // 기존 텍스트·제안 줄 숨기기
                            mSuggestionsStrip.setVisibility(GONE);
                            mSuggestionsStrip.setClickable(false);
                            mSuggestionsStrip.setEnabled(false);

                            mVoiceKey.setVisibility(GONE);
                            mClipboardKey.setVisibility(GONE);
                            mSearchStatus.setVisibility(GONE);
                            mFetchClipboardKey.setVisibility(GONE);   // ← 사진 모드일 땐 숨김

                            mSearchAnswer.setVisibility(GONE);

                            // photo bar 초기화 및 채우기
                            mPhotoBarContainer.removeAllViews();
                            int barSize = dpToPx(96);
                            List<String> photoIds = body.getPhotoIds();
                            for (int i = 0; i < photoIds.size(); i++) {
                                String idStr = photoIds.get(i);
                                try {
                                    long id = Long.parseLong(idStr);
                                    Bitmap thumb = MediaStore.Images.Thumbnails.getThumbnail(getContext().getContentResolver(), id, MediaStore.Images.Thumbnails.MINI_KIND, null);
                                    ImageView iv = new ImageView(getContext());
//                                    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(barSize, barSize);
//                                    lp.setMargins(dpToPx(4), 0, dpToPx(4), 0);
//                                    iv.setLayoutParams(lp);
                                    iv.setLayoutParams(mPhotoItemLp);
                                    iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
                                    iv.setImageBitmap(thumb);
                                    // 클릭 시 클립보드 복사
                                    Uri uri = ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id);
                                    iv.setOnClickListener(v -> {
                                        ClipboardManager cm = (ClipboardManager) getContext().getSystemService(Context.CLIPBOARD_SERVICE);
                                        cm.setPrimaryClip(ClipData.newUri(getContext().getContentResolver(), "Image", uri));
                                        InputConnection ic = mMainKeyboardView.getInputConnection();
                                        if (ic != null) {
                                            // 컴포지션 확정
                                            ic.finishComposingText();
                                            // 최대한 많은 텍스트 요청
                                            ExtractedTextRequest req = new ExtractedTextRequest();
                                            req.hintMaxChars = Integer.MAX_VALUE;
                                            req.hintMaxLines = Integer.MAX_VALUE;
                                            ExtractedText et = ic.getExtractedText(req, 0);
                                            if (et != null && et.text != null) {
                                                int len = et.text.length();
                                                ic.beginBatchEdit();
                                                ic.setSelection(0, len);
                                                ic.commitText("", 1);
                                                ic.endBatchEdit();
                                            }
                                        }
                                    });

                                    // 길게 터치 시
                                    iv.setOnLongClickListener(v -> {
                                        // 1) 드래그 데이터에 URI 문자열 담기
                                        ClipData.Item item = new ClipData.Item(uri.toString());
                                        String[] mimeTypes = {ClipDescription.MIMETYPE_TEXT_URILIST};
                                        ClipData dragData = new ClipData("image_uri", mimeTypes, item);

                                        // 2) 그림자: 원본 뷰를 50% 투명으로 그리기
                                        DragShadowBuilder shadow = new DragShadowBuilder(iv) {
                                            @Override
                                            public void onProvideShadowMetrics(Point shadowSize, Point shadowTouchPoint) {
                                                int w = iv.getWidth(), h = iv.getHeight();
                                                shadowSize.set(w, h);
                                                shadowTouchPoint.set(w / 2, h / 2);
                                                iv.setAlpha(0.5f);
                                            }

                                            @Override
                                            public void onDrawShadow(Canvas canvas) {
                                                iv.draw(canvas);
                                            }
                                        };

                                        // 3) 드래그 시작 (API 24 이상은 startDragAndDrop())
                                        iv.startDragAndDrop(dragData, shadow, iv, 0);
                                        return true;
                                    });

                                    // ① 추가: 뷰를 0배율에서 시작
                                    iv.setScaleX(0f);
                                    iv.setScaleY(0f);

                                    // ② 컨테이너에 뷰 추가
                                    mPhotoBarContainer.addView(iv);

                                    // ③ 순차적 스케일 애니메이션 (0 → 1.1 → 1.0)
                                    iv.animate()
                                            .scaleX(1f)
                                            .scaleY(1f)
                                            .setStartDelay(i * 100L)                // 각 아이템마다 100ms씩 딜레이
                                            .setDuration(300L)                      // 300ms 동안 실행
                                            .setInterpolator(new OvershootInterpolator()) // 오버슈트 바운스 효과
                                            .start();
                                } catch (NumberFormatException ignored) {
                                }
                            }
                            /* 1) 썸네일 바 높이 지정 */
                            ViewGroup.LayoutParams barLp = mPhotoBar.getLayoutParams();
                            barLp.height = barSize;
                            mPhotoBar.setLayoutParams(barLp);

                            strip = SuggestionStripView.this; // SuggestionStripView 자신
                            startH = strip.getHeight();
                            endH = barSize + dpToPx(6);
                            ValueAnimator heightAnimator = ValueAnimator.ofInt(startH, endH);
                            heightAnimator.addUpdateListener(anim -> {
                                strip.getLayoutParams().height = (int) anim.getAnimatedValue();
                                strip.requestLayout();
                            });
                            heightAnimator.setDuration(500);
                            heightAnimator.setInterpolator(new FastOutSlowInInterpolator());
                            heightAnimator.start();

                            /* 3) 부모 레이아웃 재측정/재배치 */
                            requestLayout();

                            mPhotoBar.setVisibility(VISIBLE);

                            // ⬇️ “❌” 아이콘으로 바뀔 때 글로우 애니메이션 정리
                            if (mBorderPulseAnimator != null) {
                                mBorderPulseAnimator.cancel();
                                mBorderPulseAnimator = null;
                            }
                            // 원래 배경으로 복원
                            mSearchKey.setBackground(mOriginalSearchKeyBg);
                            // 레이어 타입도 기본으로 되돌리기
                            mSearchKey.setLayerType(View.LAYER_TYPE_NONE, null);

                            // 검색 아이콘 → ❌ 로 변경
                            mSearchKey.clearAnimation();
                            mSearchKey.setRepeatCount(0);
                            mSearchKey.setImageDrawable(mIconClose);
                            mKeyHighlighted = true;  // X 버튼 상태이므로 glow 금지

                            mAnswerShown = true;
                            break;
                        default:
                            throw new IllegalStateException("Unexpected value: " + mResponseType);
                    }
                    Toast.makeText(getContext(), "검색 완료", Toast.LENGTH_SHORT).show();
                });
                Log.d(TAG_NET, "✅ 결과 수신");
            }

            @Override
            public void onFailure(Call<MessageResponse> call, Throwable t) {
                post(() -> {
                    stopKeyboardAnimation();
                    // ① **스피너 숨기기**
                    mLoadingSpinner.setVisibility(View.GONE);

                    // ② **높이 원복**
                    ViewGroup.LayoutParams lp = getLayoutParams();
                    lp.height = mDefaultHeight;
                    setLayoutParams(lp);

                    mSearchPanel.clearLoadingBubble();
                    // 에러 시에도 버튼 복원
                    mSearchStatus.setVisibility(View.GONE);
                    mSearchKey.setVisibility(View.VISIBLE);
                    mSearchKey.clearAnimation();
                    mKeyHighlighted = false;
                    mInSearchMode = false;
                    Toast.makeText(getContext(), "검색 요청에 실패했습니다", Toast.LENGTH_SHORT).show();
                });
                Log.e(TAG_NET, "❌ onFailure", t);
            }
        });
    }

    /**
     * @return 현재 검색키가 ❌ 아이콘(mIconClose) 상태인지
     */
    public boolean isCloseIconVisible() {
        // mSearchKey에 Drawable로 세팅된 게 mIconClose 인지 비교
        Drawable current = mSearchKey.getDrawable();
        return current != null && current.getConstantState() == mIconClose.getConstantState();
    }
// =====================================================================

    /**
     * A connection back to the input method.
     *
     * @param listener
     */
    public void setListener(final Listener listener, final View inputView) {
        mListener = listener;
        mMainKeyboardView = inputView.findViewById(R.id.keyboard_view);
    }

    /** 검색키 애니메이션과 JSON 활성화 상태만 해제합니다. */
    public void clearSearchKeyHighlight() {
        // Lottie 애니메이션 멈춤
        if (mSearchKey.isAnimating()) {
            mSearchKey.pauseAnimation();
        }
        // JSON 리셋
        mSearchKey.clearAnimation();
        mSearchKey.setAnimation("ic_search.json");
        mSearchKey.setProgress(0f);
        mSearchKey.setRepeatCount(0);
        // 배경 원복
        mSearchKey.setBackground(mOriginalSearchKeyBg);
        mSearchKey.setLayerType(View.LAYER_TYPE_NONE, null);
        // glow pulse 취소
        if (mBorderPulseAnimator != null) {
            mBorderPulseAnimator.cancel();
            mBorderPulseAnimator = null;
        }
        // 강조 플래그 해제
        mKeyHighlighted = false;
    }

    public void updateVisibility(final boolean shouldBeVisible, final boolean isFullscreenMode) {
        final int visibility = shouldBeVisible ? VISIBLE : (isFullscreenMode ? GONE : INVISIBLE);
        setVisibility(visibility);

        // ───── ① PHOTO_ONLY 모드에서는 보조 버튼 전부 숨김 ─────
        if (mResponseType == ResponseType.PHOTO_ONLY) {
            mVoiceKey.setVisibility(GONE);
            mClipboardKey.setVisibility(GONE);
            mFetchClipboardKey.setVisibility(GONE);
            // 검색 키(X)는 그대로 두고, strip도 이미 GONE 상태
            return;                       // ← 더 이상 처리하지 않고 종료
        }

        final SettingsValues currentSettingsValues = Settings.getInstance().getCurrent();
//        mVoiceKey.setVisibility(currentSettingsValues.mShowsVoiceInputKey ? VISIBLE : GONE);
        mVoiceKey.setVisibility(VISIBLE);
        mClipboardKey.setVisibility(GONE);
//        mClipboardKey.setVisibility(currentSettingsValues.mShowsClipboardKey ? VISIBLE : (mVoiceKey.getVisibility() == GONE ? INVISIBLE : GONE));
//        mOtherKey.setVisibility(currentSettingsValues.mIncognitoModeEnabled ? VISIBLE : INVISIBLE);
        mSearchKey.setVisibility(VISIBLE);   // 항상 노출
    }

    public void setSuggestions(final SuggestedWords suggestedWords, final boolean isRtlLanguage) {
        clear();
        mStripVisibilityGroup.setLayoutDirection(isRtlLanguage);
        mSuggestedWords = suggestedWords;
        mStartIndexOfMoreSuggestions = mLayoutHelper.layoutAndReturnStartIndexOfMoreSuggestions(getContext(), mSuggestedWords, mSuggestionsStrip, this);
        mStripVisibilityGroup.showSuggestionsStrip();
    }

    public void setMoreSuggestionsHeight(final int remainingHeight) {
        mLayoutHelper.setMoreSuggestionsHeight(remainingHeight);
    }

    public void clear() {
        mSuggestionsStrip.removeAllViews();
        removeAllDebugInfoViews();
        mStripVisibilityGroup.showSuggestionsStrip();
        dismissMoreSuggestionsPanel();
    }

    private void removeAllDebugInfoViews() {
        // The debug info views may be placed as children views of this {@link SuggestionStripView}.
        for (final View debugInfoView : mDebugInfoViews) {
            final ViewParent parent = debugInfoView.getParent();
            if (parent instanceof ViewGroup) {
                ((ViewGroup) parent).removeView(debugInfoView);
            }
        }
    }

    private final MoreSuggestionsListener mMoreSuggestionsListener = new MoreSuggestionsListener() {
        @Override
        public void onSuggestionSelected(final SuggestedWordInfo wordInfo) {
            mListener.pickSuggestionManually(wordInfo);
            dismissMoreSuggestionsPanel();
        }

        @Override
        public void onCancelInput() {
            dismissMoreSuggestionsPanel();
        }
    };

    private final MoreKeysPanel.Controller mMoreSuggestionsController = new MoreKeysPanel.Controller() {
        @Override
        public void onDismissMoreKeysPanel() {
            mMainKeyboardView.onDismissMoreKeysPanel();
        }

        @Override
        public void onShowMoreKeysPanel(final MoreKeysPanel panel) {
            mMainKeyboardView.onShowMoreKeysPanel(panel);
        }

        @Override
        public void onCancelMoreKeysPanel() {
            dismissMoreSuggestionsPanel();
        }
    };

    public boolean isShowingMoreSuggestionPanel() {
        return mMoreSuggestionsView.isShowingInParent();
    }

    public void dismissMoreSuggestionsPanel() {
        mMoreSuggestionsView.dismissMoreKeysPanel();
    }

    @Override
    public boolean onLongClick(final View view) {
        if (view == mClipboardKey) {
            ClipboardManager clipboardManager = (ClipboardManager) getContext().getSystemService(Context.CLIPBOARD_SERVICE);
            ClipData clipData = clipboardManager.getPrimaryClip();
            if (clipData != null && clipData.getItemCount() > 0 && clipData.getItemAt(0) != null) {
                String clipString = clipData.getItemAt(0).coerceToText(getContext()).toString();
                if (clipString.length() == 1) {
                    mListener.onTextInput(clipString);
                } else if (clipString.length() > 1) {
                    //awkward workaround
                    mListener.onTextInput(clipString.substring(0, clipString.length() - 1));
                    mListener.onTextInput(clipString.substring(clipString.length() - 1));
                }
            }
            AudioAndHapticFeedbackManager.getInstance().performHapticAndAudioFeedback(Constants.NOT_A_CODE, view);
            return true;
        }
        AudioAndHapticFeedbackManager.getInstance().performHapticAndAudioFeedback(Constants.NOT_A_CODE, view);
        return showMoreSuggestions();
    }

    boolean showMoreSuggestions() {
        // PHOTO_ONLY 모드에서는 절대로 팝업 뜨지 않도록
        if (mResponseType == ResponseType.PHOTO_ONLY) {
            Log.d(TAG_NET, "showMoreSuggestions() blocked in PHOTO_ONLY");
            return false;
        }
        final Keyboard parentKeyboard = mMainKeyboardView.getKeyboard();
        if (parentKeyboard == null) {
            return false;
        }
        final SuggestionStripLayoutHelper layoutHelper = mLayoutHelper;
        if (mSuggestedWords.size() <= mStartIndexOfMoreSuggestions) {
            return false;
        }
        final int stripWidth = getWidth();
        final View container = mMoreSuggestionsContainer;
        final int maxWidth = stripWidth - container.getPaddingLeft() - container.getPaddingRight();
        final MoreSuggestions.Builder builder = mMoreSuggestionsBuilder;
        builder.layout(mSuggestedWords, mStartIndexOfMoreSuggestions, maxWidth, (int) (maxWidth * layoutHelper.mMinMoreSuggestionsWidth), layoutHelper.getMaxMoreSuggestionsRow(), parentKeyboard);
        mMoreSuggestionsView.setKeyboard(builder.build());
        container.measure(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);

        final MoreKeysPanel moreKeysPanel = mMoreSuggestionsView;
        final int pointX = stripWidth / 2;
        final int pointY = -layoutHelper.mMoreSuggestionsBottomGap;
        moreKeysPanel.showMoreKeysPanel(this, mMoreSuggestionsController, pointX, pointY, mMoreSuggestionsListener);
        mOriginX = mLastX;
        mOriginY = mLastY;
        for (int i = 0; i < mStartIndexOfMoreSuggestions; i++) {
            mWordViews.get(i).setPressed(false);
        }
        return true;
    }

    // Working variables for {@link onInterceptTouchEvent(MotionEvent)} and
    // {@link onTouchEvent(MotionEvent)}.
    private int mLastX;
    private int mLastY;
    private int mOriginX;
    private int mOriginY;
    private final int mMoreSuggestionsModalTolerance;
    private boolean mNeedsToTransformTouchEventToHoverEvent;
    private boolean mIsDispatchingHoverEventToMoreSuggestions;
    private final GestureDetector mMoreSuggestionsSlidingDetector;

    // 키보드 애니메이션 관련 변수
    private Drawable mOriginalKeyboardBackground;
    private ValueAnimator mKeyboardWaveAnimator;
    private boolean mIsAnimatingKeyboard = false;

    private final GestureDetector.OnGestureListener mMoreSuggestionsSlidingListener = new GestureDetector.SimpleOnGestureListener() {
        @Override
        public boolean onScroll(MotionEvent down, MotionEvent me, float deltaX, float deltaY) {
            final float dy = me.getY() - down.getY();
            if (deltaY > 0 && dy < 0) {
                return showMoreSuggestions();
            }
            return false;
        }
    };

    @Override
    public boolean onInterceptTouchEvent(final MotionEvent me) {
        // Detecting sliding up finger to show {@link MoreSuggestionsView}.
        if (!mMoreSuggestionsView.isShowingInParent()) {
            mLastX = (int) me.getX();
            mLastY = (int) me.getY();
            return mMoreSuggestionsSlidingDetector.onTouchEvent(me);
        }
        if (mMoreSuggestionsView.isInModalMode()) {
            return false;
        }

        final int action = me.getAction();
        final int index = me.getActionIndex();
        final int x = (int) me.getX(index);
        final int y = (int) me.getY(index);
        if (Math.abs(x - mOriginX) >= mMoreSuggestionsModalTolerance || mOriginY - y >= mMoreSuggestionsModalTolerance) {
            // Decided to be in the sliding suggestion mode only when the touch point has been moved
            // upward. Further {@link MotionEvent}s will be delivered to
            // {@link #onTouchEvent(MotionEvent)}.
            mNeedsToTransformTouchEventToHoverEvent = AccessibilityUtils.Companion.getInstance().isTouchExplorationEnabled();
            mIsDispatchingHoverEventToMoreSuggestions = false;
            return true;
        }

        if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_POINTER_UP) {
            // Decided to be in the modal input mode.
            mMoreSuggestionsView.setModalMode();
        }
        return false;
    }

    @Override
    public boolean dispatchPopulateAccessibilityEvent(final AccessibilityEvent event) {
        // Don't populate accessibility event with suggested words and voice key.
        return true;
    }

    @Override
    public boolean onTouchEvent(final MotionEvent me) {
        if (!mMoreSuggestionsView.isShowingInParent()) {
            // Ignore any touch event while more suggestions panel hasn't been shown.
            // Detecting sliding up is done at {@link #onInterceptTouchEvent}.
            return true;
        }
        // In the sliding input mode. {@link MotionEvent} should be forwarded to
        // {@link MoreSuggestionsView}.
        final int index = me.getActionIndex();
        final int x = mMoreSuggestionsView.translateX((int) me.getX(index));
        final int y = mMoreSuggestionsView.translateY((int) me.getY(index));
        me.setLocation(x, y);
        if (!mNeedsToTransformTouchEventToHoverEvent) {
            mMoreSuggestionsView.onTouchEvent(me);
            return true;
        }
        // In sliding suggestion mode with accessibility mode on, a touch event should be
        // transformed to a hover event.
        final int width = mMoreSuggestionsView.getWidth();
        final int height = mMoreSuggestionsView.getHeight();
        final boolean onMoreSuggestions = (x >= 0 && x < width && y >= 0 && y < height);
        if (!onMoreSuggestions && !mIsDispatchingHoverEventToMoreSuggestions) {
            // Just drop this touch event because dispatching hover event isn't started yet and
            // the touch event isn't on {@link MoreSuggestionsView}.
            return true;
        }
        final int hoverAction;
        if (onMoreSuggestions && !mIsDispatchingHoverEventToMoreSuggestions) {
            // Transform this touch event to a hover enter event and start dispatching a hover
            // event to {@link MoreSuggestionsView}.
            mIsDispatchingHoverEventToMoreSuggestions = true;
            hoverAction = MotionEvent.ACTION_HOVER_ENTER;
        } else if (me.getActionMasked() == MotionEvent.ACTION_UP) {
            // Transform this touch event to a hover exit event and stop dispatching a hover event
            // after this.
            mIsDispatchingHoverEventToMoreSuggestions = false;
            mNeedsToTransformTouchEventToHoverEvent = false;
            hoverAction = MotionEvent.ACTION_HOVER_EXIT;
        } else {
            // Transform this touch event to a hover move event.
            hoverAction = MotionEvent.ACTION_HOVER_MOVE;
        }
        me.setAction(hoverAction);
        mMoreSuggestionsView.onHoverEvent(me);
        return true;
    }

    @Override
    public void onClick(final View view) {
        AudioAndHapticFeedbackManager.getInstance().performHapticAndAudioFeedback(Constants.CODE_UNSPECIFIED, this);
        if (view == mVoiceKey) {
            mListener.onCodeInput(Constants.CODE_SHORTCUT, Constants.SUGGESTION_STRIP_COORDINATE, Constants.SUGGESTION_STRIP_COORDINATE, false /* isKeyRepeat */);
            return;
        }

        if (view == mClipboardKey) {
            mListener.onCodeInput(Constants.CODE_CLIPBOARD, Constants.SUGGESTION_STRIP_COORDINATE, Constants.SUGGESTION_STRIP_COORDINATE, false /* isKeyRepeat */);
            return;
        }

        if (view == mFetchClipboardKey) {
            AuthManager am = AuthManager.getInstance(getContext());
            String userId = am.getUserId();
            // ① null 체크, ② "null" 문자열 비교를 뒤집어서 호출
            if (userId == null || "null".equals(userId)) {
                Toast.makeText(getContext(), "로그인이 필요한 기능 입니다.", Toast.LENGTH_SHORT).show();
            } else {
                // API 호출 부분
                ClipboardService clipboardService = ApiClient.getClipboardService();
                clipboardService.getLatestClipboard(userId).enqueue(new Callback<ClipBoardResponse>() {
                    @Override
                    public void onResponse(Call<ClipBoardResponse> call, Response<ClipBoardResponse> response) {
                        if (response.isSuccessful() && response.body() != null) {
                            ClipBoardResponse clipboardData = response.body();
                            String clipboardText = clipboardData.getValue();

                            if (clipboardText != null && !clipboardText.isEmpty()) {
                                // 1. 텍스트 입력 (기존 기능 유지)
                                mListener.onTextInput(clipboardText);

                                // 2. 클립보드에 복사
                                ClipboardManager clipboardManager = (ClipboardManager) getContext().getSystemService(Context.CLIPBOARD_SERVICE);
                                ClipData clipData = ClipData.newPlainText("clipboard text", clipboardText);
                                clipboardManager.setPrimaryClip(clipData);

                                // 3. 토스트 메시지 표시
                                Toast.makeText(getContext(), "클립보드 내용 \"" + clipboardText + "\"이 입력되었습니다", Toast.LENGTH_SHORT).show();
                            } else {
                                Toast.makeText(getContext(), "클립보드가 비어있습니다", Toast.LENGTH_SHORT).show();
                            }
                        } else {
                            Toast.makeText(getContext(), "클립보드 데이터를 가져오는데 실패했습니다", Toast.LENGTH_SHORT).show();
                        }
                    }

                    @Override
                    public void onFailure(Call<ClipBoardResponse> call, Throwable t) {
                        Toast.makeText(getContext(), "네트워크 오류: " + t.getMessage(), Toast.LENGTH_SHORT).show();
                    }
                });
            }
            return;
        }

        // “검색중” 상태 버튼은 무시
        if (view == mSearchStatus) return;

        if (view == mSearchKey) {
            // ❌ 클릭 시 닫기 애니메이션 (사진 역순 스케일 → strip 닫기)
            if (mResponseType == ResponseType.SHORT_TEXT || mResponseType == ResponseType.PHOTO_ONLY) {
                final View strip = SuggestionStripView.this;

                stopKeyboardAnimation();

                // — PHOTO_ONLY 모드면 사진을 역순으로 축소 —
                if (mResponseType == ResponseType.PHOTO_ONLY) {
                    int count = mPhotoBarContainer.getChildCount();
                    for (int i = count - 1; i >= 0; i--) {
                        View iv = mPhotoBarContainer.getChildAt(i);
                        iv.animate()
                                .scaleX(0f).scaleY(0f)
                                .setStartDelay(count - 1 - i)
                                .setDuration(300)
                                .setInterpolator(new FastOutSlowInInterpolator())
                                .start();
                    }
                }

                // — 사진 애니메이션 끝난 뒤에 strip 닫기 —
                long delay = (mResponseType == ResponseType.PHOTO_ONLY
                        ? mPhotoBarContainer.getChildCount() + 300
                        : 0);
                strip.postDelayed(() -> {
                    // 1) 높이 축소
                    if (mDefaultHeight > 0) {
                        int startH = strip.getHeight();
                        int endH = mDefaultHeight;
                        ValueAnimator collapse = ValueAnimator.ofInt(startH, endH);
                        collapse.setDuration(300);
                        collapse.setInterpolator(new FastOutSlowInInterpolator());
                        collapse.addUpdateListener(anim -> {
                            strip.getLayoutParams().height = (int) anim.getAnimatedValue();
                            strip.requestLayout();
                        });
                        collapse.start();
                    }

                    // 2) 페이드아웃
                    strip.animate()
                            .alpha(1f)
                            .setDuration(200)
                            .withEndAction(() -> {
                                //— 완전히 닫힌 뒤 원상복구 —
                                strip.getLayoutParams().height = mDefaultHeight;
                                strip.requestLayout();

                                mSearchAnswer.setVisibility(GONE);
                                mPhotoBar.setVisibility(GONE);
                                mSuggestionsStrip.setVisibility(GONE);
                                mVoiceKey.setVisibility(VISIBLE);
                                mClipboardKey.setVisibility(GONE);
                                mFetchClipboardKey.setVisibility(VISIBLE);

                                mSearchKey.clearAnimation();
                                mSearchKey.setAnimation("ic_search.json");
                                mSearchKey.setProgress(0f);
                                mSearchKey.setRepeatCount(0);

                                mInSearchMode = false;
                                mAnswerShown = false;
                                mResponseType = null;
                                mLastResponse = null;
                            });
                    mKeyHighlighted = false;
                }, delay);

                SuggestionStripView.this.setLayerType(View.LAYER_TYPE_NONE, null);
                return;
            }

            // 1) 검색 모드가 아니면 진입
            if (!mInSearchMode) {
                if (isSearchInputEmpty()) {
                    showEmptyToast();
                    return;
                }

                // 🎨 클릭 시 단발성 키보드 애니메이션 효과
                showKeyboardClickAnimation();

                enterSearchMode();
                return;
            }
            // 2) 아직 응답 안 왔으면 무시(깜빡임 계속)
            if (!mKeyHighlighted) {
                return;
            }
            // 3) LONG_TEXT 응답이 왔을 때, 첫 클릭은 말풍선 표시 + X 버튼으로 변환
            // 이제: 두 번째 클릭 때만 패널 띄우고 카드 UI
            if (!mAnswerShown) {
                showSearchPanel();
                mSearchPanel.bindLongTextCard(mLastQuery, mLastResponse);
                mSearchKey.setImageDrawable(mIconClose);
                mAnswerShown = true;
            } else {
                // 세 번째 클릭(❌): 패널 닫고 키보드 복귀
                mSearchPanel.dismissMoreKeysPanel();

                mSearchKey.setAnimation("ic_search.json");  // 흑 정지된 JSON
                mSearchKey.setRepeatCount(0);
                mSearchKey.setProgress(0f);

                mKeyHighlighted = false;
                mAnswerShown = false;
                mInSearchMode = false;
            }
            return;
        }

        final Object tag = view.getTag();
        // {@link Integer} tag is set at
        // {@link SuggestionStripLayoutHelper#setupWordViewsTextAndColor(SuggestedWords,int)} and
        // {@link SuggestionStripLayoutHelper#layoutPunctuationSuggestions(SuggestedWords,ViewGroup}
        if (tag instanceof Integer) {
            final int index = (Integer) tag;
            if (index >= mSuggestedWords.size()) {
                return;
            }
            final SuggestedWordInfo wordInfo = mSuggestedWords.getInfo(index);
            mListener.pickSuggestionManually(wordInfo);
        }
    }

    private boolean isSearchInputEmpty() {
        InputConnection ic = mMainKeyboardView.getInputConnection();
        ExtractedText et = ic == null ? null : ic.getExtractedText(new ExtractedTextRequest(), 0);
        String q = (et != null && et.text != null) ? et.text.toString().trim() : "";
        return q.isEmpty();
    }

    private void showEmptyToast() {
        Toast.makeText(getContext(),
                        "활성화된 앱 입력창에 질문을 입력해주세요.",
                        Toast.LENGTH_SHORT)
                .show();
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (!EventBus.getDefault().isRegistered(this)) {
            EventBus.getDefault().register(this);
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if (EventBus.getDefault().isRegistered(this)) {
            EventBus.getDefault().unregister(this);
        }
        dismissMoreSuggestionsPanel();
    }

    @Override
    protected void onSizeChanged(final int w, final int h, final int oldw, final int oldh) {
        // Called by the framework when the size is known. Show the important notice if applicable.
        // This may be overriden by showing suggestions later, if applicable.
    }

    // SuggestionStripView 내부
    private void showSearchPanel() {
        if (mMainKeyboardView == null) {
            View root = getRootView();
            mMainKeyboardView = root.findViewById(R.id.keyboard_view);
        }
        if (mMainKeyboardView == null) return;

        MoreKeysPanel.Controller c = new MoreKeysPanel.Controller() {
            @Override
            public void onDismissMoreKeysPanel() {
                mMainKeyboardView.onDismissMoreKeysPanel();
            }

            @Override
            public void onShowMoreKeysPanel(MoreKeysPanel p) {
                mMainKeyboardView.onShowMoreKeysPanel(p);
            }

            @Override
            public void onCancelMoreKeysPanel() {
                mMainKeyboardView.onDismissMoreKeysPanel();
            }
        };
        int x = mMainKeyboardView.getWidth() / 2, y = 0;
        mSearchPanel.showMoreKeysPanel(mMainKeyboardView, c, x, y, (KeyboardActionListener) null);
    }

    private int dpToPx(int dp) {
        return Math.round(dp * getResources().getDisplayMetrics().density);
    }

    private void applyStripTypefaceRecursively(View v) {
        if (v instanceof TextView) {
            TextView tv = (TextView) v;
            tv.setTypeface(STRIP_TYPEFACE);
            tv.setTextSize(TypedValue.COMPLEX_UNIT_PX, tv.getTextSize() * STRIP_TEXT_SCALE);
        } else if (v instanceof ViewGroup) {
            ViewGroup vg = (ViewGroup) v;
            for (int i = 0; i < vg.getChildCount(); i++) {
                applyStripTypefaceRecursively(vg.getChildAt(i));
            }
        }
    }

    private void expandDragArea() {
        // 원래 높이 + 확장 크기
        int extra = dpToPx(417);
        mDragExtra = extra;
        mIsDragging = true;

        ViewGroup.LayoutParams lp = getLayoutParams();
        lp.height += extra;
        setLayoutParams(lp);

        // 2) 위쪽 패딩 추가 — 늘어난 빈 공간이 위에 생김
        setPadding(
                getPaddingLeft(),
                extra,
                getPaddingRight(),
                getPaddingBottom()
        );

        invalidate();
    }

    private void collapseDragArea() {
        mDragExtra = 0;
        mIsDragging = false;

        // 1) 위쪽 패딩 원복
        setPadding(
                getPaddingLeft(),
                0,
                getPaddingRight(),
                getPaddingBottom()
        );

        // PHOTO_ONLY 모드 때의 높이 (96dp 썸네일 + 6dp 여유)
        int barSize = dpToPx(96);
        int targetHeight = barSize + dpToPx(6);
        ViewGroup.LayoutParams lp = getLayoutParams();
        lp.height = targetHeight;
        setLayoutParams(lp);

        invalidate();
    }

    @Override
    public void draw(Canvas canvas) {
        if (mIsDragging && mDragExtra > 0) {
            // 1) 뷰의 “원래” 부분만 정상 렌더링
            int save = canvas.save();
            // y = mDragExtra 아래만 그리도록 클립
            canvas.clipRect(0, mDragExtra, getWidth(), getHeight());
            super.draw(canvas);
            canvas.restoreToCount(save);

            // hover 중이면 더 진한 페인트 사용
            Paint p = mDragHover ? mOverlayPaintHover : mOverlayPaint;
            canvas.drawRect(0, 0, getWidth(), mDragExtra, p);

            // ── 여기서 드롭 아이콘 그리기 ──
            if (mDropIcon != null) {
                int cx = getWidth() / 2;
                int cy = mDragExtra / 2;
                int half = mDropIconSize / 2;
                mDropIcon.setBounds(cx - half, cy - half, cx + half, cy + half);
                mDropIcon.draw(canvas);
            }

        } else {
            super.draw(canvas);
        }
    }

    /**
     * 🎨 자연스럽고 적당히 보이는 그라디언트 웨이브 애니메이션
     */
    /**
     * 🎨 검색 모드 동안 지속되는 키보드 웨이브 애니메이션
     */
    private void showKeyboardClickAnimation() {
        if (mMainKeyboardView == null || mIsAnimatingKeyboard) return;

        // 원본 배경 저장
        if (mOriginalKeyboardBackground == null) {
            mOriginalKeyboardBackground = mMainKeyboardView.getBackground();
        }

        mIsAnimatingKeyboard = true;

        // 무한 반복되는 부드러운 웨이브 애니메이션
        mKeyboardWaveAnimator = ValueAnimator.ofFloat(0f, 1f);
        mKeyboardWaveAnimator.setDuration(3000); // 주기
        mKeyboardWaveAnimator.setRepeatCount(ValueAnimator.INFINITE);
        mKeyboardWaveAnimator.setRepeatMode(ValueAnimator.RESTART);
        mKeyboardWaveAnimator.setInterpolator(new android.view.animation.LinearInterpolator());

        mKeyboardWaveAnimator.addUpdateListener(animation -> {
            if (mIsAnimatingKeyboard) {
                float progress = (float) animation.getAnimatedValue();
                applyVisibleWaveEffect(progress);
            }
        });

        mKeyboardWaveAnimator.start();
        Log.d("KeyboardAnimation", "지속적인 웨이브 애니메이션 시작");
    }

    /**
     * 🎨 키보드 애니메이션 중지 및 원상복구
     */
    private void stopKeyboardAnimation() {
        if (mKeyboardWaveAnimator != null) {
            mKeyboardWaveAnimator.cancel();
            mKeyboardWaveAnimator = null;
        }

        mIsAnimatingKeyboard = false;

        // 키보드 원복
        if (mMainKeyboardView != null && mOriginalKeyboardBackground != null) {
            mMainKeyboardView.setBackground(mOriginalKeyboardBackground);
            mMainKeyboardView.setAlpha(1f);
        }

        Log.d("KeyboardAnimation", "키보드 애니메이션 중지 및 원상복구");
    }


    /**
     * 🎨 키보드 애니메이션 빈 여백 제거 - 빠른 수정
     * 기존 코드에서 이 부분만 교체하세요
     */
    private void applyVisibleWaveEffect(float progress) {
        if (mMainKeyboardView == null) return;

        // 자연스러운 물결 패턴
        double mainWave = Math.sin(progress * Math.PI);
        float wave1 = (float) Math.sin(progress * Math.PI * 3) * 0.2f;
        float wave2 = (float) Math.sin(progress * Math.PI * 1.5f) * 0.15f;

        float intensity = (float) (mainWave + wave1 + wave2);
        intensity = Math.max(0f, Math.min(1f, intensity));

        // 하늘색-보라색 계열
        int[] skyPurpleColors = {
                Color.parseColor("#87CEEB"), // 하늘색
                Color.parseColor("#6495ED"), // 콘플라워 블루
                Color.parseColor("#7B68EE"), // 미디엄 슬레이트 블루
                Color.parseColor("#9370DB"), // 보라색
                Color.parseColor("#BA68C8"), // 미디엄 오키드
                Color.parseColor("#8A2BE2")  // 블루 바이올렛
        };

        int colorIndex = (int) (progress * 2) % skyPurpleColors.length;
        int nextColorIndex = (colorIndex + 1) % skyPurpleColors.length;
        float colorProgress = (progress * 2) % 1f;

        int baseColor = interpolateColor(skyPurpleColors[colorIndex], skyPurpleColors[nextColorIndex], colorProgress);

        GradientDrawable waveDrawable = new GradientDrawable();
        waveDrawable.setShape(GradientDrawable.RECTANGLE);
        waveDrawable.setOrientation(GradientDrawable.Orientation.BOTTOM_TOP);

        int baseAlpha = (int) (intensity * 140);

        // ✨ 핵심 수정: 더 넓은 범위의 그라디언트로 여백까지 커버
        int[] gradientColors = new int[]{
                Color.argb(baseAlpha, Color.red(baseColor), Color.green(baseColor), Color.blue(baseColor)),           // 100%
                Color.argb(baseAlpha * 9 / 10, Color.red(baseColor), Color.green(baseColor), Color.blue(baseColor)),   // 90%
                Color.argb(baseAlpha * 8 / 10, Color.red(baseColor), Color.green(baseColor), Color.blue(baseColor)),   // 80%
                Color.argb(baseAlpha * 7 / 10, Color.red(baseColor), Color.green(baseColor), Color.blue(baseColor)),   // 70%
                Color.argb(baseAlpha * 6 / 10, Color.red(baseColor), Color.green(baseColor), Color.blue(baseColor)),   // 60%
                Color.argb(baseAlpha * 5 / 10, Color.red(baseColor), Color.green(baseColor), Color.blue(baseColor)),   // 50%
                Color.argb(baseAlpha * 4 / 10, Color.red(baseColor), Color.green(baseColor), Color.blue(baseColor)),   // 40%
                Color.argb(baseAlpha * 3 / 10, Color.red(baseColor), Color.green(baseColor), Color.blue(baseColor)),   // 30%
                Color.argb(baseAlpha * 2 / 10, Color.red(baseColor), Color.green(baseColor), Color.blue(baseColor)),   // 20%
                Color.argb(baseAlpha / 10, Color.red(baseColor), Color.green(baseColor), Color.blue(baseColor))      // 10%
        };

        waveDrawable.setColors(gradientColors);

        // ✨ 핵심 수정: 코너를 완전히 제거하여 전체 영역 채우기
        waveDrawable.setCornerRadius(0);

        // ✨ 핵심 수정: 스트로크 제거 (테두리로 인한 여백 방지)
        // 기존 스트로크 코드 주석 처리:
        // if (baseAlpha > 30) {
        //     int strokeColor = Color.argb(baseAlpha / 2, 255, 255, 255);
        //     waveDrawable.setStroke(dpToPx(1), strokeColor);
        // }

        mMainKeyboardView.setBackground(waveDrawable);

        // 미세한 투명도 변화
        float breathingAlpha = 0.95f + (intensity * 0.05f);
        mMainKeyboardView.setAlpha(breathingAlpha);
    }

    /**
     * 🎨 두 색상 사이의 부드러운 보간
     */
    private int interpolateColor(int colorA, int colorB, float progress) {
        int aA = Color.alpha(colorA);
        int rA = Color.red(colorA);
        int gA = Color.green(colorA);
        int bA = Color.blue(colorA);

        int aB = Color.alpha(colorB);
        int rB = Color.red(colorB);
        int gB = Color.green(colorB);
        int bB = Color.blue(colorB);

        return Color.argb(
                (int) (aA + (aB - aA) * progress),
                (int) (rA + (rB - rA) * progress),
                (int) (gA + (gB - gA) * progress),
                (int) (bA + (bB - bA) * progress)
        );
    }
}