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

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.util.Log;
import android.util.TypedValue;
import android.view.GestureDetector;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnLongClickListener;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.view.accessibility.AccessibilityEvent;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import org.dslul.openboard.inputmethod.accessibility.AccessibilityUtils;
import org.dslul.openboard.inputmethod.keyboard.Keyboard;
import org.dslul.openboard.inputmethod.keyboard.KeyboardActionListener;
import org.dslul.openboard.inputmethod.keyboard.KeyboardSwitcher;
import org.dslul.openboard.inputmethod.keyboard.MainKeyboardView;
import org.dslul.openboard.inputmethod.keyboard.MoreKeysPanel;
import org.dslul.openboard.inputmethod.latin.AudioAndHapticFeedbackManager;
import org.dslul.openboard.inputmethod.latin.LatinIME;
import org.dslul.openboard.inputmethod.latin.R;
import org.dslul.openboard.inputmethod.latin.SuggestedWords;
import org.dslul.openboard.inputmethod.latin.SuggestedWords.SuggestedWordInfo;
import org.dslul.openboard.inputmethod.latin.common.Constants;
import org.dslul.openboard.inputmethod.latin.define.DebugFlags;
import org.dslul.openboard.inputmethod.latin.network.ApiClient;
import org.dslul.openboard.inputmethod.latin.network.MessageResponse;
import org.dslul.openboard.inputmethod.latin.search.SearchResultView;
import org.dslul.openboard.inputmethod.latin.settings.Settings;
import org.dslul.openboard.inputmethod.latin.settings.SettingsValues;
import org.dslul.openboard.inputmethod.latin.suggestions.MoreSuggestionsView.MoreSuggestionsListener;

import java.util.ArrayList;

import androidx.core.view.ViewCompat;

import retrofit2.Call;

public final class SuggestionStripView extends RelativeLayout implements OnClickListener,
        OnLongClickListener {
    public interface Listener {
        void pickSuggestionManually(SuggestedWordInfo word);

        void onCodeInput(int primaryCode, int x, int y, boolean isKeyRepeat);

        void onTextInput(final String rawText);

        CharSequence getSelection();
    }

    /* ▼ 새로 추가할 필드들 --------------------------------------------------- */
    private ImageButton mSearchKey;      // 돋보기(검색 모드 진입)
    private ImageButton mSendKey;        // 전송
    private ImageButton mVoiceKey;       // 마이크(= 클립보드 키 자리에 있던 버튼)
    private LinearLayout mInputContainer;// EditText+Send 래퍼
    private EditText mSearchInput;       // 검색어 입력창
    private boolean mInSearchMode = false;
    // 기존 필드 바로 아래
    private Drawable mIconSearch;   // 돋보기
    private Drawable mIconClose;    // X 아이콘
    private ImageButton mCopyKey;

    private static final String TAG_NET = "SearchAPI";
    private static final String DEFAULT_USER_ID = "36648ad3-ed4b-4eb0-bcf1-1dc66fa5d258"; // TODO: 실제 계정으로 치환
    private SearchResultView mSearchPanel;

    static final boolean DBG = DebugFlags.DEBUG_ENABLED;
    private static final float DEBUG_INFO_TEXT_SIZE_IN_DIP = 6.0f;

    private final ViewGroup mSuggestionsStrip;
    private final ImageButton mClipboardKey;
    //    private final ImageButton mOtherKey;
    MainKeyboardView mMainKeyboardView;

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

    private static class StripVisibilityGroup {
        private final View mSuggestionStripView;
        private final View mSuggestionsStrip;

        public StripVisibilityGroup(final View suggestionStripView,
                                    final ViewGroup suggestionsStrip) {
            mSuggestionStripView = suggestionStripView;
            mSuggestionsStrip = suggestionsStrip;
            showSuggestionsStrip();
        }

        public void setLayoutDirection(final boolean isRtlLanguage) {
            final int layoutDirection = isRtlLanguage ? ViewCompat.LAYOUT_DIRECTION_RTL
                    : ViewCompat.LAYOUT_DIRECTION_LTR;
            ViewCompat.setLayoutDirection(mSuggestionStripView, layoutDirection);
            ViewCompat.setLayoutDirection(mSuggestionsStrip, layoutDirection);
        }

        public void showSuggestionsStrip() {
            mSuggestionsStrip.setVisibility(VISIBLE);
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

    public SuggestionStripView(final Context context, final AttributeSet attrs,
                               final int defStyle) {
        super(context, attrs, defStyle);

        final LayoutInflater inflater = LayoutInflater.from(context);
        inflater.inflate(R.layout.suggestions_strip, this);

        mSuggestionsStrip = findViewById(R.id.suggestions_strip);
        mVoiceKey = findViewById(R.id.suggestions_strip_voice_key);
        mClipboardKey = findViewById(R.id.suggestions_strip_clipboard_key);
//        mOtherKey = findViewById(R.id.suggestions_strip_other_key);
        mStripVisibilityGroup = new StripVisibilityGroup(this, mSuggestionsStrip);


        for (int pos = 0; pos < SuggestedWords.MAX_SUGGESTIONS; pos++) {
            final TextView word = new TextView(context, null, R.attr.suggestionWordStyle);
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

        mLayoutHelper = new SuggestionStripLayoutHelper(
                context, attrs, defStyle, mWordViews, mDividerViews, mDebugInfoViews);

        mMoreSuggestionsContainer = inflater.inflate(R.layout.more_suggestions, null);
        mMoreSuggestionsView = mMoreSuggestionsContainer
                .findViewById(R.id.more_suggestions_view);
        mMoreSuggestionsBuilder = new MoreSuggestions.Builder(context, mMoreSuggestionsView);

        final Resources res = context.getResources();
        mMoreSuggestionsModalTolerance = res.getDimensionPixelOffset(
                R.dimen.config_more_suggestions_modal_tolerance);
        mMoreSuggestionsSlidingDetector = new GestureDetector(
                context, mMoreSuggestionsSlidingListener);

        final TypedArray keyboardAttr = context.obtainStyledAttributes(attrs,
                R.styleable.Keyboard, defStyle, R.style.SuggestionStripView);
        final Drawable iconVoice = keyboardAttr.getDrawable(R.styleable.Keyboard_iconShortcutKey);
        final Drawable iconIncognito = keyboardAttr.getDrawable(R.styleable.Keyboard_iconIncognitoKey);
        final Drawable iconClipboard = keyboardAttr.getDrawable(R.styleable.Keyboard_iconClipboardNormalKey);
        keyboardAttr.recycle();

        mVoiceKey.setImageDrawable(iconVoice);

        mSearchKey = findViewById(R.id.suggestions_strip_search_key);
        if (mSearchKey == null) {
            throw new IllegalStateException(
                    "suggestions_strip_search_key not found in current layout variant");
        }
        // 🔍, ❌ 아이콘 준비
        mIconSearch = getResources().getDrawable(R.drawable.ic_search, null);
        mIconClose = getResources().getDrawable(R.drawable.ic_close, null);

        mSearchKey.setImageDrawable(mIconSearch);   // 기본은 🔍
        mSendKey = findViewById(R.id.suggestions_strip_send_key);
        mInputContainer = findViewById(R.id.suggestions_strip_input_container);
        mSearchInput = findViewById(R.id.suggestions_strip_search_input);
        mCopyKey = findViewById(R.id.suggestions_strip_copy_key);
        mCopyKey.setOnClickListener(this);
        mCopyKey.setVisibility(GONE);    // ← 초기엔 숨김

        mSearchInput.setFocusableInTouchMode(true);
        mSearchInput.setCursorVisible(true);


        mSearchKey.setOnClickListener(this);
        mSendKey.setOnClickListener(this);

        mVoiceKey.setOnClickListener(this);
        mClipboardKey.setImageDrawable(iconClipboard);
        mClipboardKey.setOnClickListener(this);
        mClipboardKey.setOnLongClickListener(this);

//        mOtherKey.setImageDrawable(iconIncognito);
    }

    // ========== Search Mode helpers ======================================
    private void enterSearchMode() {
        if (mInSearchMode) return;
        mInSearchMode = true;

        // ── 여기에만 한 번! ──
        if (mListener instanceof LatinIME) {
            ((LatinIME) mListener).resetSearchCombiner();
        }

        // ▼ 추가 : Listener(=LatinIME) 에 버퍼 초기화 요청
        if (mListener instanceof LatinIME) {
            ((LatinIME) mListener).resetSearchBuffers();
        }

        // 아이콘 ❌로 교체
        mSearchKey.setImageDrawable(mIconClose);

        // UI 전환
        mSuggestionsStrip.setVisibility(GONE);
        mVoiceKey.setVisibility(GONE);
        mClipboardKey.setVisibility(GONE);
//        mOtherKey.setVisibility(GONE);
        mInputContainer.setVisibility(VISIBLE);
        mCopyKey.setVisibility(GONE);


        mSearchInput.setText("");
        mSearchInput.requestFocus();
    }

    public void exitSearchMode() {
        if (!mInSearchMode) return;
        mInSearchMode = false;

        // ▼ 추가 : Listener(=LatinIME) 에 버퍼 초기화 요청
        if (mListener instanceof LatinIME) {
            ((LatinIME) mListener).resetSearchBuffers();
        }

        mSearchKey.setImageDrawable(mIconSearch);      // 🔍 복원
        mInputContainer.setVisibility(GONE);
        mSuggestionsStrip.setVisibility(VISIBLE);
        updateVisibility(true /* strip */, false /* isFullscreen */); // 버튼들 복원

        mCopyKey.setVisibility(GONE);

        if (mSearchPanel != null && mSearchPanel.isShowingInParent()) {
            mSearchPanel.dismissMoreKeysPanel();
        }

    }

    private void dispatchSearchQuery() {
        final String query = mSearchInput.getText().toString().trim();
        if (query.isEmpty()) return;

        /* ---------- ① 패널 & 로딩 스피너 바로 띄우기 ---------- */
        if (mSearchPanel == null)
            mSearchPanel = new SearchResultView(getContext());

        mSearchPanel.showLoading();                 // ⬅ 스피너 ON

        Log.d(TAG_NET, "▶ REQUEST\n" +
                "URL   : http://k12e201.p.ssafy.io:8090/rag/search/\n" +
                "user_id = " + DEFAULT_USER_ID + "\n" +
                "query   = " + query);

        // Controller → MainKeyboardView 로 위임
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

        int x = mMainKeyboardView.getWidth() / 2;
        int y = 0;
        mSearchPanel.showMoreKeysPanel(
                mMainKeyboardView, c, x, y, (KeyboardActionListener) null);

        // ① Retrofit 호출
        ApiClient.getChatApiService()
                .search(DEFAULT_USER_ID, query)
                .enqueue(new retrofit2.Callback<MessageResponse>() {
                    @Override
                    public void onResponse(Call<MessageResponse> call,
                                           retrofit2.Response<MessageResponse> res) {
                        if (!res.isSuccessful()) {
                            Log.e(TAG_NET, "❌ " + res.code() + " " + res.message());
                            return;
                        }
                        MessageResponse body = res.body();
                        post(() -> mSearchPanel.bind(body));   // 내용 채우기 + 스피너 OFF
                        Log.d(TAG_NET, "✅ 결과 수신");

                        // ------- 키보드 패널 보여주기 -------
                        post(() -> {                   // SuggestionStripView 는 이미 UI Thread
                            if (mSearchPanel == null) {
                                mSearchPanel = new SearchResultView(getContext());
                            }
                            mSearchPanel.bind(body);


                            // pointX, pointY는 키보드 상단 중앙에 붙이도록
                            int x = mMainKeyboardView.getWidth() / 2;
                            int y = 0;
                            mSearchPanel.showMoreKeysPanel(mMainKeyboardView, c, x, y,
                                    (KeyboardActionListener) null); // 두 시그니처 중 아무거나
                        });
                    }

                    @Override
                    public void onFailure(Call<MessageResponse> call, Throwable t) {
                        post(() -> {
                            // 에러 뷰를 보여주거나 패널 닫기 용도
                            mSearchPanel.dismissMoreKeysPanel();
                        });
                        Log.e(TAG_NET, "❌ onFailure", t);
                    }
                });

        // ② IME 텍스트 커밋(선택) – 결과를 채팅창 등에 그대로 넣고 싶다면
//        mListener.onTextInput(query);

        // ③ UI 복귀
//        exitSearchMode();

    }

    public boolean isInSearchMode() {
        return mInSearchMode;
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

    public void updateVisibility(final boolean shouldBeVisible, final boolean isFullscreenMode) {
        final int visibility = shouldBeVisible ? VISIBLE : (isFullscreenMode ? GONE : INVISIBLE);
        setVisibility(visibility);
        final SettingsValues currentSettingsValues = Settings.getInstance().getCurrent();
        mVoiceKey.setVisibility(currentSettingsValues.mShowsVoiceInputKey ? VISIBLE : GONE);
        mClipboardKey.setVisibility(currentSettingsValues.mShowsClipboardKey ? VISIBLE : (mVoiceKey.getVisibility() == GONE ? INVISIBLE : GONE));
//        mOtherKey.setVisibility(currentSettingsValues.mIncognitoModeEnabled ? VISIBLE : INVISIBLE);
        mSearchKey.setVisibility(VISIBLE);   // 항상 노출
    }

    public void setSuggestions(final SuggestedWords suggestedWords, final boolean isRtlLanguage) {
        clear();
        mStripVisibilityGroup.setLayoutDirection(isRtlLanguage);
        mSuggestedWords = suggestedWords;
        mStartIndexOfMoreSuggestions = mLayoutHelper.layoutAndReturnStartIndexOfMoreSuggestions(
                getContext(), mSuggestedWords, mSuggestionsStrip, this);
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

    private final MoreKeysPanel.Controller mMoreSuggestionsController =
            new MoreKeysPanel.Controller() {
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
            AudioAndHapticFeedbackManager.getInstance().performHapticAndAudioFeedback(
                    Constants.NOT_A_CODE, this);
            return true;
        }
        AudioAndHapticFeedbackManager.getInstance().performHapticAndAudioFeedback(
                Constants.NOT_A_CODE, this);
        return showMoreSuggestions();
    }

    boolean showMoreSuggestions() {
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
        builder.layout(mSuggestedWords, mStartIndexOfMoreSuggestions, maxWidth,
                (int) (maxWidth * layoutHelper.mMinMoreSuggestionsWidth),
                layoutHelper.getMaxMoreSuggestionsRow(), parentKeyboard);
        mMoreSuggestionsView.setKeyboard(builder.build());
        container.measure(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);

        final MoreKeysPanel moreKeysPanel = mMoreSuggestionsView;
        final int pointX = stripWidth / 2;
        final int pointY = -layoutHelper.mMoreSuggestionsBottomGap;
        moreKeysPanel.showMoreKeysPanel(this, mMoreSuggestionsController, pointX, pointY,
                mMoreSuggestionsListener);
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
    private final GestureDetector.OnGestureListener mMoreSuggestionsSlidingListener =
            new GestureDetector.SimpleOnGestureListener() {
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
        if (Math.abs(x - mOriginX) >= mMoreSuggestionsModalTolerance
                || mOriginY - y >= mMoreSuggestionsModalTolerance) {
            // Decided to be in the sliding suggestion mode only when the touch point has been moved
            // upward. Further {@link MotionEvent}s will be delivered to
            // {@link #onTouchEvent(MotionEvent)}.
            mNeedsToTransformTouchEventToHoverEvent =
                    AccessibilityUtils.Companion.getInstance().isTouchExplorationEnabled();
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
        AudioAndHapticFeedbackManager.getInstance().performHapticAndAudioFeedback(
                Constants.CODE_UNSPECIFIED, this);
        if (view == mVoiceKey) {
            mListener.onCodeInput(Constants.CODE_SHORTCUT,
                    Constants.SUGGESTION_STRIP_COORDINATE, Constants.SUGGESTION_STRIP_COORDINATE,
                    false /* isKeyRepeat */);
            return;
        }
        if (view == mClipboardKey) {
            mListener.onCodeInput(Constants.CODE_CLIPBOARD,
                    Constants.SUGGESTION_STRIP_COORDINATE, Constants.SUGGESTION_STRIP_COORDINATE,
                    false /* isKeyRepeat */);
            return;
        }

        if (view == mSearchKey) {          // 🔍 또는 ❌
            if (mInSearchMode) {
                exitSearchMode();          // ❌ 눌림
            } else {
                enterSearchMode();         // 🔍 눌림
            }
            return;
        }
        if (view == mSendKey) {            // 전송 버튼
            dispatchSearchQuery();
            mCopyKey.setVisibility(VISIBLE);  // 복사 버튼 노출
//            exitSearchMode();
//            mSearchInput.setText("");
            return;
        }

        if (view == mCopyKey) {                   // ⧉ 복사 버튼
            if (mSearchPanel != null) {
                String answer = mSearchPanel.getAnswerText();
                if (!answer.isEmpty()) {
                    ClipboardManager cb = (ClipboardManager)
                            getContext().getSystemService(Context.CLIPBOARD_SERVICE);
                    cb.setPrimaryClip(ClipData.newPlainText("answer", answer));

                    // ▼ 여기 한 줄 추가
                    Toast.makeText(getContext(), "복사되었습니다", Toast.LENGTH_SHORT).show();
                    // 선택사항: 피드백
                    AudioAndHapticFeedbackManager.getInstance()
                            .performHapticAndAudioFeedback(Constants.NOT_A_CODE, this);
                }
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

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        dismissMoreSuggestionsPanel();
    }

    @Override
    protected void onSizeChanged(final int w, final int h, final int oldw, final int oldh) {
        // Called by the framework when the size is known. Show the important notice if applicable.
        // This may be overriden by showing suggestions later, if applicable.
    }

    /**
     * 검색 모드 시 타이핑한 문자열을 보여줄 EditText
     */
    public EditText getSearchInput() {
        return mSearchInput;
    }
}
