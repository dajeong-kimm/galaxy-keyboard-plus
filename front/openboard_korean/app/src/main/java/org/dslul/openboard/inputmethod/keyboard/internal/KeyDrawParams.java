/*
 * Copyright (C) 2012 The Android Open Source Project
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

package org.dslul.openboard.inputmethod.keyboard.internal;

import android.content.Context;
import android.graphics.Typeface;

import androidx.core.content.res.ResourcesCompat;

import org.dslul.openboard.inputmethod.latin.R;
import org.dslul.openboard.inputmethod.latin.utils.ResourceUtils;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public final class KeyDrawParams {
    @Nonnull
    public Typeface mTypeface = Typeface.DEFAULT;

    private static final String FALLBACK_FONT_FAMILY = "samsung_one"; // system 에 없으면 아래에서 ttf 로더 사용

    public int mLetterSize;
    public int mLabelSize;
    public int mLargeLetterSize;
    public int mHintLetterSize;
    public int mShiftedLetterHintSize;
    public int mHintLabelSize;
    public int mPreviewTextSize;

    public int mTextColor;
    public int mTextInactivatedColor;
    public int mTextShadowColor;
    public int mFunctionalTextColor;
    public int mHintLetterColor;
    public int mHintLabelColor;
    public int mShiftedLetterHintInactivatedColor;
    public int mShiftedLetterHintActivatedColor;
    public int mPreviewTextColor;

    public float mHintLabelVerticalAdjustment;
    public float mLabelOffCenterRatio;
    public float mHintLabelOffCenterRatio;

    public int mAnimAlpha;

    public KeyDrawParams() {
        /*
         * 우선 system-font 에 같은 family 가 있으면 그대로 쓰고,
         * 없으면 앱 리소스(res/font/)에 넣어 둔 TTF 파일을 불러온다.
         *  - res/font/nanum_bold.ttf  (ex)
         *  - res/values/fonts.xml     (optional)
         */
        final Typeface system = Typeface.create(FALLBACK_FONT_FAMILY, Typeface.NORMAL);
        if (system != null) {
            mTypeface = system;
        } // else → updateParams()에서 Context 통해 로드
    }

    private KeyDrawParams(@Nonnull final KeyDrawParams copyFrom) {
        mTypeface = copyFrom.mTypeface;

        mLetterSize = copyFrom.mLetterSize;
        mLabelSize = copyFrom.mLabelSize;
        mLargeLetterSize = copyFrom.mLargeLetterSize;
        mHintLetterSize = copyFrom.mHintLetterSize;
        mShiftedLetterHintSize = copyFrom.mShiftedLetterHintSize;
        mHintLabelSize = copyFrom.mHintLabelSize;
        mPreviewTextSize = copyFrom.mPreviewTextSize;

        mTextColor = copyFrom.mTextColor;
        mTextInactivatedColor = copyFrom.mTextInactivatedColor;
        mTextShadowColor = copyFrom.mTextShadowColor;
        mFunctionalTextColor = copyFrom.mFunctionalTextColor;
        mHintLetterColor = copyFrom.mHintLetterColor;
        mHintLabelColor = copyFrom.mHintLabelColor;
        mShiftedLetterHintInactivatedColor = copyFrom.mShiftedLetterHintInactivatedColor;
        mShiftedLetterHintActivatedColor = copyFrom.mShiftedLetterHintActivatedColor;
        mPreviewTextColor = copyFrom.mPreviewTextColor;

        mHintLabelVerticalAdjustment = copyFrom.mHintLabelVerticalAdjustment;
        mLabelOffCenterRatio = copyFrom.mLabelOffCenterRatio;
        mHintLabelOffCenterRatio = copyFrom.mHintLabelOffCenterRatio;

        mAnimAlpha = copyFrom.mAnimAlpha;
    }

    public void updateParams(final int keyHeight, @Nullable final KeyVisualAttributes attr) {
        if (attr == null) {
            return;
        }

        if (attr.mTypeface != null) {
            mTypeface = attr.mTypeface;
        } else {
            // KeyVisualAttributes 쪽(Typeface)도 없고, 아직 DEFAULT라면
            // 시스템에 설치된 동일 family 글꼴을 써본다.
            if (mTypeface == Typeface.DEFAULT) {
                mTypeface = Typeface.create(FALLBACK_FONT_FAMILY, Typeface.NORMAL);
            }
        }

//        mLetterSize = selectTextSizeFromDimensionOrRatio(keyHeight,
//                attr.mLetterSize, attr.mLetterRatio, mLetterSize);
//        mLabelSize = selectTextSizeFromDimensionOrRatio(keyHeight,
//                attr.mLabelSize, attr.mLabelRatio, mLabelSize);
        final float GLOBAL_SCALE = 0.90f; // 15 % 키우기(원하면 0.90f 등으로 조정)

        mLetterSize = (int)(GLOBAL_SCALE * selectTextSizeFromDimensionOrRatio(
                keyHeight, attr.mLetterSize, attr.mLetterRatio, mLetterSize));

        mLabelSize = (int)(GLOBAL_SCALE * selectTextSizeFromDimensionOrRatio(
                keyHeight, attr.mLabelSize, attr.mLabelRatio, mLabelSize));

        mLargeLetterSize = selectTextSize(keyHeight, attr.mLargeLetterRatio, mLargeLetterSize);
        mHintLetterSize = selectTextSize(keyHeight, attr.mHintLetterRatio, mHintLetterSize);
        mShiftedLetterHintSize = selectTextSize(keyHeight,
                attr.mShiftedLetterHintRatio, mShiftedLetterHintSize);
        mHintLabelSize = selectTextSize(keyHeight, attr.mHintLabelRatio, mHintLabelSize);
        mPreviewTextSize = selectTextSize(keyHeight, attr.mPreviewTextRatio, mPreviewTextSize);

        mTextColor = selectColor(attr.mTextColor, mTextColor);
        mTextInactivatedColor = selectColor(attr.mTextInactivatedColor, mTextInactivatedColor);
        mTextShadowColor = selectColor(attr.mTextShadowColor, mTextShadowColor);
        mFunctionalTextColor = selectColor(attr.mFunctionalTextColor, mFunctionalTextColor);
        mHintLetterColor = selectColor(attr.mHintLetterColor, mHintLetterColor);
        mHintLabelColor = selectColor(attr.mHintLabelColor, mHintLabelColor);
        mShiftedLetterHintInactivatedColor = selectColor(
                attr.mShiftedLetterHintInactivatedColor, mShiftedLetterHintInactivatedColor);
        mShiftedLetterHintActivatedColor = selectColor(
                attr.mShiftedLetterHintActivatedColor, mShiftedLetterHintActivatedColor);
        mPreviewTextColor = selectColor(attr.mPreviewTextColor, mPreviewTextColor);

        mHintLabelVerticalAdjustment = selectFloatIfNonZero(
                attr.mHintLabelVerticalAdjustment, mHintLabelVerticalAdjustment);
        mLabelOffCenterRatio = selectFloatIfNonZero(
                attr.mLabelOffCenterRatio, mLabelOffCenterRatio);
        mHintLabelOffCenterRatio = selectFloatIfNonZero(
                attr.mHintLabelOffCenterRatio, mHintLabelOffCenterRatio);
    }

    @Nonnull
    public KeyDrawParams mayCloneAndUpdateParams(final int keyHeight,
            @Nullable final KeyVisualAttributes attr) {
        if (attr == null) {
            return this;
        }
        final KeyDrawParams newParams = new KeyDrawParams(this);
        newParams.updateParams(keyHeight, attr);
        return newParams;
    }

    private static int selectTextSizeFromDimensionOrRatio(final int keyHeight,
            final int dimens, final float ratio, final int defaultDimens) {
        if (ResourceUtils.isValidDimensionPixelSize(dimens)) {
            return dimens;
        }
        if (ResourceUtils.isValidFraction(ratio)) {
            return (int)(keyHeight * ratio);
        }
        return defaultDimens;
    }

    private static int selectTextSize(final int keyHeight, final float ratio,
            final int defaultSize) {
        if (ResourceUtils.isValidFraction(ratio)) {
            return (int)(keyHeight * ratio);
        }
        return defaultSize;
    }

    private static int selectColor(final int attrColor, final int defaultColor) {
        if (attrColor != 0) {
            return attrColor;
        }
        return defaultColor;
    }

    private static float selectFloatIfNonZero(final float attrFloat, final float defaultFloat) {
        if (attrFloat != 0) {
            return attrFloat;
        }
        return defaultFloat;
    }
}
