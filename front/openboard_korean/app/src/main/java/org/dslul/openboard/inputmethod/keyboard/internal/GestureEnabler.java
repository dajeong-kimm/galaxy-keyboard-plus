/*
 * Copyright (C) 2013 The Android Open Source Project
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

import org.dslul.openboard.inputmethod.accessibility.AccessibilityUtils;

public final class GestureEnabler {
    /** True if we should handle gesture events. */
    private boolean mShouldHandleGesture;
    private boolean mMainDictionaryAvailable;
    private boolean mGestureHandlingEnabledByInputField;
    private boolean mGestureHandlingEnabledByUser;

    private void updateGestureHandlingMode() {
        mShouldHandleGesture = mMainDictionaryAvailable
                && mGestureHandlingEnabledByInputField
                && mGestureHandlingEnabledByUser
                && !AccessibilityUtils.Companion.getInstance().isTouchExplorationEnabled();
    }

    // Note that this method is called from a non-UI thread.
    public void setMainDictionaryAvailability(final boolean mainDictionaryAvailable) {
        mMainDictionaryAvailable = mainDictionaryAvailable;
        updateGestureHandlingMode();
    }

    public void setGestureHandlingEnabledByUser(final boolean gestureHandlingEnabledByUser) {
        mGestureHandlingEnabledByUser = gestureHandlingEnabledByUser;
        updateGestureHandlingMode();
    }

    public void setPasswordMode(final boolean passwordMode) {
        mGestureHandlingEnabledByInputField = !passwordMode;
        updateGestureHandlingMode();
    }

    public boolean shouldHandleGesture() {
        return mShouldHandleGesture;
    }
}
