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

package org.dslul.openboard.inputmethod.latin.utils;

import android.content.Context;
import android.content.Intent;

@SuppressWarnings("unused")
public class FeedbackUtils {
    public static boolean isHelpAndFeedbackFormSupported() {
        return false;
    }

    public static void showHelpAndFeedbackForm(Context context) {
    }

    public static int getAboutKeyboardTitleResId() {
        return 0;
    }

    public static Intent getAboutKeyboardIntent(Context context) {
        return null;
    }
}
