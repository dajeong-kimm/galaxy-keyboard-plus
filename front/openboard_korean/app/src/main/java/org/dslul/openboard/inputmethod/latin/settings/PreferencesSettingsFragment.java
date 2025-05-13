/*
 * Copyright (C) 2014 The Android Open Source Project
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

package org.dslul.openboard.inputmethod.latin.settings;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.media.AudioManager;
import android.os.Bundle;
import android.preference.Preference;

import org.dslul.openboard.inputmethod.latin.AudioAndHapticFeedbackManager;
import org.dslul.openboard.inputmethod.latin.R;
import org.dslul.openboard.inputmethod.latin.RichInputMethodManager;

import android.content.Intent;
import android.widget.Toast;
import org.dslul.openboard.inputmethod.latin.auth.AuthCallback;
import org.dslul.openboard.inputmethod.latin.auth.AuthManager;
import org.dslul.openboard.inputmethod.latin.login.KakaoLoginActivity;

public final class PreferencesSettingsFragment extends SubScreenFragment {

    private static final boolean VOICE_IME_ENABLED =
            true;

    private static final int REQUEST_KAKAO_LOGIN = 100;

    @Override
    public void onCreate(final Bundle icicle) {
        super.onCreate(icicle);
        addPreferencesFromResource(R.xml.prefs_screen_preferences);





        final Resources res = getResources();
        final Context context = getActivity();

        // When we are called from the Settings application but we are not already running, some
        // singleton and utility classes may not have been initialized.  We have to call
        // initialization method of these classes here. See {@link LatinIME#onCreate()}.
        RichInputMethodManager.init(context);

        final boolean showVoiceKeyOption = res.getBoolean(
                R.bool.config_enable_show_voice_key_option);
        if (!showVoiceKeyOption) {
            removePreference(Settings.PREF_VOICE_INPUT_KEY);
        }
        if (!AudioAndHapticFeedbackManager.getInstance().hasVibrator()) {
            removePreference(Settings.PREF_VIBRATE_ON);
            removePreference(Settings.PREF_VIBRATION_DURATION_SETTINGS);
        }
        if (!Settings.readFromBuildConfigIfToShowKeyPreviewPopupOption(res)) {
            removePreference(Settings.PREF_POPUP_ON);
        }

        setupKeypressVibrationDurationSettings();
        setupKeypressSoundVolumeSettings();
        setupHistoryRetentionTimeSettings();
        refreshEnablingsOfKeypressSoundAndVibrationAndHistRetentionSettings();

        // 카카오 로그인 preference 가져오기
        Preference kakaoLoginPref = findPreference("kakao_login");
        if (kakaoLoginPref != null) {
            kakaoLoginPref.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
                @Override
                public boolean onPreferenceClick(Preference preference) {
                    AuthManager authManager = AuthManager.getInstance(getActivity());
                    if (authManager.isLoggedIn()) {
                        // 로그인되어 있으면 로그아웃
                        authManager.logout(new AuthCallback() {
                            @Override
                            public void onLoginSuccess(String userId) {
                                // 로그아웃 중에는 호출되지 않음
                            }

                            @Override
                            public void onLoginFailure(String errorMessage) {
                                // 로그아웃 중에는 호출되지 않음
                            }

                            @Override
                            public void onLogoutSuccess() {
                                // 메인 스레드에서 UI 업데이트
                                getActivity().runOnUiThread(new Runnable() {
                                    @Override
                                    public void run() {
                                        Toast.makeText(getActivity(), "로그아웃 성공", Toast.LENGTH_SHORT).show();
                                        updateKakaoLoginStatus(kakaoLoginPref);
                                    }
                                });
                            }
                        });
                    } else {
                        // 로그인되어 있지 않으면 로그인 액티비티 표시
                        Intent intent = new Intent(getActivity(), KakaoLoginActivity.class);
                        startActivityForResult(intent, REQUEST_KAKAO_LOGIN);
                    }
                    return true;
                }
            });

            // 로그인 상태에 따라 preference 요약 업데이트
            updateKakaoLoginStatus(kakaoLoginPref);
        }

    }


    private void updateKakaoLoginStatus(Preference kakaoLoginPref) {
        AuthManager authManager = AuthManager.getInstance(getActivity());
        if (authManager.isLoggedIn()) {
            kakaoLoginPref.setSummary("로그인 계정: " + authManager.getUserId());
            // 직접 하드코딩된 문자열 사용
            kakaoLoginPref.setTitle("카카오 로그아웃");
        } else {
            kakaoLoginPref.setSummary("카카오 로그인 기능 테스트");
            kakaoLoginPref.setTitle("카카오 로그인 테스트");
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_KAKAO_LOGIN) {
            // 로그인 액티비티에서 돌아온 후 preference 요약 업데이트
            Preference kakaoLoginPref = findPreference("kakao_login");
            if (kakaoLoginPref != null) {
                updateKakaoLoginStatus(kakaoLoginPref);
            }
        }
    }


    @Override
    public void onResume() {
        super.onResume();
        final Preference voiceInputKeyOption = findPreference(Settings.PREF_VOICE_INPUT_KEY);
        if (voiceInputKeyOption != null) {
            RichInputMethodManager.getInstance().refreshSubtypeCaches();
            boolean voiceKeyEnabled = VOICE_IME_ENABLED && RichInputMethodManager.getInstance().hasShortcutIme();
            voiceInputKeyOption.setEnabled(voiceKeyEnabled);
            voiceInputKeyOption.setSummary(voiceKeyEnabled
                    ? null : getText(R.string.voice_input_disabled_summary));
        }
    }

    @Override
    public void onSharedPreferenceChanged(final SharedPreferences prefs, final String key) {
        refreshEnablingsOfKeypressSoundAndVibrationAndHistRetentionSettings();
    }

    private void refreshEnablingsOfKeypressSoundAndVibrationAndHistRetentionSettings() {
        final SharedPreferences prefs = getSharedPreferences();
        final Resources res = getResources();
        setPreferenceEnabled(Settings.PREF_VIBRATION_DURATION_SETTINGS,
                Settings.readVibrationEnabled(prefs, res));
        setPreferenceEnabled(Settings.PREF_KEYPRESS_SOUND_VOLUME,
                Settings.readKeypressSoundEnabled(prefs, res));
        setPreferenceEnabled(Settings.PREF_CLIPBOARD_HISTORY_RETENTION_TIME,
                Settings.readClipboardHistoryEnabled(prefs));
    }

    private void setupKeypressVibrationDurationSettings() {
        final SeekBarDialogPreference pref = (SeekBarDialogPreference)findPreference(
                Settings.PREF_VIBRATION_DURATION_SETTINGS);
        if (pref == null) {
            return;
        }
        final SharedPreferences prefs = getSharedPreferences();
        final Resources res = getResources();
        pref.setInterface(new SeekBarDialogPreference.ValueProxy() {
            @Override
            public void writeValue(final int value, final String key) {
                prefs.edit().putInt(key, value).apply();
            }

            @Override
            public void writeDefaultValue(final String key) {
                prefs.edit().remove(key).apply();
            }

            @Override
            public int readValue(final String key) {
                return Settings.readKeypressVibrationDuration(prefs, res);
            }

            @Override
            public int readDefaultValue(final String key) {
                return Settings.readDefaultKeypressVibrationDuration(res);
            }

            @Override
            public void feedbackValue(final int value) {
                AudioAndHapticFeedbackManager.getInstance().vibrate(value);
            }

            @Override
            public String getValueText(final int value) {
                if (value < 0) {
                    return res.getString(R.string.settings_system_default);
                }
                return res.getString(R.string.abbreviation_unit_milliseconds, value);
            }
        });
    }

    private void setupKeypressSoundVolumeSettings() {
        final SeekBarDialogPreference pref = (SeekBarDialogPreference)findPreference(
                Settings.PREF_KEYPRESS_SOUND_VOLUME);
        if (pref == null) {
            return;
        }
        final SharedPreferences prefs = getSharedPreferences();
        final Resources res = getResources();
        final AudioManager am = (AudioManager)getActivity().getSystemService(Context.AUDIO_SERVICE);
        pref.setInterface(new SeekBarDialogPreference.ValueProxy() {
            private static final float PERCENTAGE_FLOAT = 100.0f;

            private float getValueFromPercentage(final int percentage) {
                return percentage / PERCENTAGE_FLOAT;
            }

            private int getPercentageFromValue(final float floatValue) {
                return (int)(floatValue * PERCENTAGE_FLOAT);
            }

            @Override
            public void writeValue(final int value, final String key) {
                prefs.edit().putFloat(key, getValueFromPercentage(value)).apply();
            }

            @Override
            public void writeDefaultValue(final String key) {
                prefs.edit().remove(key).apply();
            }

            @Override
            public int readValue(final String key) {
                return getPercentageFromValue(Settings.readKeypressSoundVolume(prefs, res));
            }

            @Override
            public int readDefaultValue(final String key) {
                return getPercentageFromValue(Settings.readDefaultKeypressSoundVolume(res));
            }

            @Override
            public String getValueText(final int value) {
                if (value < 0) {
                    return res.getString(R.string.settings_system_default);
                }
                return Integer.toString(value);
            }

            @Override
            public void feedbackValue(final int value) {
                am.playSoundEffect(
                        AudioManager.FX_KEYPRESS_STANDARD, getValueFromPercentage(value));
            }
        });
    }

    private void setupHistoryRetentionTimeSettings() {
        final SharedPreferences prefs = getSharedPreferences();
        final Resources res = getResources();
        final SeekBarDialogPreference pref = (SeekBarDialogPreference)findPreference(
                Settings.PREF_CLIPBOARD_HISTORY_RETENTION_TIME);
        if (pref == null) {
            return;
        }
        pref.setInterface(new SeekBarDialogPreference.ValueProxy() {
            @Override
            public void writeValue(final int value, final String key) {
                prefs.edit().putInt(key, value).apply();
            }

            @Override
            public void writeDefaultValue(final String key) {
                prefs.edit().remove(key).apply();
            }

            @Override
            public int readValue(final String key) {
                return Settings.readClipboardHistoryRetentionTime(prefs, res);
            }

            @Override
            public int readDefaultValue(final String key) {
                return Settings.readDefaultClipboardHistoryRetentionTime(res);
            }

            @Override
            public String getValueText(final int value) {
                if (value <= 0) {
                    return res.getString(R.string.settings_no_limit);
                }
                return res.getString(R.string.abbreviation_unit_minutes, value);
            }

            @Override
            public void feedbackValue(final int value) {}
        });
    }
}
