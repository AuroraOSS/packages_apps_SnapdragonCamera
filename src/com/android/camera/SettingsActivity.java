/*
 * Copyright (c) 2016, The Linux Foundation. All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are
 * met:
 *     * Redistributions of source code must retain the above copyright
 *       notice, this list of conditions and the following disclaimer.
 *     * Redistributions in binary form must reproduce the above
 *       copyright notice, this list of conditions and the following
 *       disclaimer in the documentation and/or other materials provided
 *       with the distribution.
 *     * Neither the name of The Linux Foundation nor the names of its
 *       contributors may be used to endorse or promote products derived
 *       from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED "AS IS" AND ANY EXPRESS OR IMPLIED
 * WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
 * MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NON-INFRINGEMENT
 * ARE DISCLAIMED.  IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS
 * BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR
 * BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,
 * WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE
 * OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN
 * IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package com.android.camera;

import android.app.ActionBar;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceCategory;
import android.preference.PreferenceGroup;
import android.preference.PreferenceScreen;
import android.preference.SwitchPreference;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Toast;

import com.android.camera.util.CameraUtil;

import org.codeaurora.snapcam.R;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class SettingsActivity extends PreferenceActivity {
    private final int DEVELOPER_MENU_TOUCH_COUNT = 5;
    private SettingsManager mSettingsManager;
    private SharedPreferences mSharedPreferences;
    private boolean mDeveloperMenuEnabled;
    private int privateCounter = 0;
    private SharedPreferences.OnSharedPreferenceChangeListener mSharedPreferenceChangeListener
            = new SharedPreferences.OnSharedPreferenceChangeListener() {
        @Override
        public void onSharedPreferenceChanged(SharedPreferences sharedPreferences,
                                              String key) {
            Preference p = findPreference(key);
            if (p == null) return;
            String value;
            if (p instanceof SwitchPreference) {
                boolean checked = ((SwitchPreference) p).isChecked();
                value = checked ? "on" : "off";
                mSettingsManager.setValue(key, value);
            } else if (p instanceof ListPreference) {
                value = ((ListPreference) p).getValue();
                mSettingsManager.setValue(key, value);
            }
            if (key.equals(SettingsManager.KEY_VIDEO_QUALITY)) {
                updatePreference(SettingsManager.KEY_VIDEO_HIGH_FRAME_RATE);
                updatePreference(SettingsManager.KEY_VIDEO_ENCODER);
            }
            List<String> list = mSettingsManager.getDependentKeys(key);
            if (list != null) {
                for (String dependentKey : list) {
                    updatePreferenceButton(dependentKey);
                }
            }
        }
    };

    private SettingsManager.Listener mListener = new SettingsManager.Listener() {
        @Override
        public void onSettingsChanged(List<SettingsManager.SettingState> settings) {
            Map<String, SettingsManager.Values> map = mSettingsManager.getValuesMap();
            for (SettingsManager.SettingState state : settings) {
                SettingsManager.Values values = map.get(state.key);
                boolean enabled = values.overriddenValue == null;
                Preference pref = findPreference(state.key);
                if (pref != null) {
                    pref.setEnabled(enabled);
                }
                if (pref.getKey().equals(SettingsManager.KEY_QCFA)) {
                    mSettingsManager.updateQcfaPictureSize();
                    updatePreference(SettingsManager.KEY_PICTURE_SIZE);
                }

                if (pref.getKey().equals(SettingsManager.KEY_VIDEO_HDR_VALUE)) {
                    ListPreference autoHdrPref = (ListPreference) findPreference(
                            mSettingsManager.KEY_AUTO_HDR);
                    if (pref.getSummary().equals("enable")) {
                        // when enable the Video HDR, app will disable the AUTO HDR.
                        autoHdrPref.setEnabled(false);
                        autoHdrPref.setValue("disable");
                        mSettingsManager.setValue(mSettingsManager.KEY_AUTO_HDR, "disable");
                    } else {
                        autoHdrPref.setEnabled(true);
                    }
                }
            }
        }
    };

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        ActionBar actionBar = getActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true);
            actionBar.setTitle(getResources().getString(R.string.settings_title));
        }

        final boolean isSecureCamera = getIntent().getBooleanExtra(
                CameraUtil.KEY_IS_SECURE_CAMERA, false);
        if (isSecureCamera) {
            setShowInLockScreen();
        }

        mSettingsManager = SettingsManager.getInstance();
        if (mSettingsManager == null) {
            finish();
            return;
        }

        mSettingsManager.registerListener(mListener);
        addPreferencesFromResource(R.xml.setting_menu_preferences);

        mSharedPreferences = getPreferenceManager().getSharedPreferences();
        mDeveloperMenuEnabled = mSharedPreferences.getBoolean(SettingsManager.KEY_DEVELOPER_MENU, false);
        ;

        filterPreferences();
        initializePreferences();

        mSharedPreferences.registerOnSharedPreferenceChangeListener(mSharedPreferenceChangeListener);

        for (int i = 0; i < getPreferenceScreen().getPreferenceCount(); i++) {
            PreferenceCategory category = (PreferenceCategory) getPreferenceScreen().getPreference(i);
            for (int j = 0; j < category.getPreferenceCount(); j++) {
                Preference pref = category.getPreference(j);
                pref.setOnPreferenceClickListener(preference -> {
                    if (!mDeveloperMenuEnabled) {
                        if (preference.getKey().equals("version_info")) {
                            privateCounter++;
                            if (privateCounter >= DEVELOPER_MENU_TOUCH_COUNT) {
                                mDeveloperMenuEnabled = true;
                                mSharedPreferences.edit().putBoolean(SettingsManager.KEY_DEVELOPER_MENU, true).apply();
                                Toast.makeText(SettingsActivity.this, "Camera developer option is enabled now", Toast.LENGTH_SHORT).show();
                                recreate();
                            }
                        } else {
                            privateCounter = 0;
                        }
                    }

                    if (preference.getKey().equals(SettingsManager.KEY_RESTORE_DEFAULT)) {
                        onRestoreDefaultSettingsClick();
                    }
                    return false;
                });
            }
        }

    }

    private void filterPreferences() {
        String[] categories = {"photo", "video", "general", "developer"};
        Set<String> set = mSettingsManager.getFilteredKeys();

        if (!mDeveloperMenuEnabled) {
            set.add(SettingsManager.KEY_MONO_PREVIEW);
            set.add(SettingsManager.KEY_MONO_ONLY);
            set.add(SettingsManager.KEY_CLEARSIGHT);

            PreferenceGroup developer = (PreferenceGroup) findPreference("developer");
            if (developer != null) {
                PreferenceScreen parent = getPreferenceScreen();
                parent.removePreference(developer);
            }
        }

        CharSequence[] entries = mSettingsManager.getEntries(SettingsManager.KEY_SCENE_MODE);
        List<CharSequence> list = Arrays.asList(entries);
        if (mDeveloperMenuEnabled && !list.contains("HDR")) {
            Preference p = findPreference("pref_camera_hdr_key");
            if (p != null) {
                PreferenceGroup developer = (PreferenceGroup) findPreference("developer");
                developer.removePreference(p);
            }
        }

        for (String key : set) {
            Preference p = findPreference(key);
            if (p == null) continue;

            for (int i = 0; i < categories.length; i++) {
                PreferenceGroup group = (PreferenceGroup) findPreference(categories[i]);
                if (group.removePreference(p)) break;
            }
        }
    }

    private void initializePreferences() {
        updatePreference(SettingsManager.KEY_PICTURE_SIZE);
        updatePreference(SettingsManager.KEY_VIDEO_QUALITY);
        updatePreference(SettingsManager.KEY_EXPOSURE);
        updatePreference(SettingsManager.KEY_VIDEO_HIGH_FRAME_RATE);
        updatePreference(SettingsManager.KEY_VIDEO_ENCODER);
        updatePreference(SettingsManager.KEY_ZOOM);
        updatePreference(SettingsManager.KEY_SWITCH_CAMERA);

        updatePictureSizePreferenceButton();
        updateVideoHDRPreference();

        Map<String, SettingsManager.Values> map = mSettingsManager.getValuesMap();
        Set<Map.Entry<String, SettingsManager.Values>> set = map.entrySet();

        for (Map.Entry<String, SettingsManager.Values> entry : set) {
            String key = entry.getKey();
            Preference p = findPreference(key);
            if (p == null) continue;

            SettingsManager.Values values = entry.getValue();
            boolean disabled = values.overriddenValue != null;
            String value = disabled ? values.overriddenValue : values.value;
            if (p instanceof SwitchPreference) {
                ((SwitchPreference) p).setChecked(isOn(value));
            } else if (p instanceof ListPreference) {
                ListPreference pref = (ListPreference) p;
                pref.setValue(value);
                if (pref.getEntryValues().length == 1) {
                    pref.setEnabled(false);
                }
            }
            if (disabled) p.setEnabled(false);
        }

        try {
            String versionName = getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
            findPreference("version_info").setSummary(versionName);
        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
        }
    }

    private void updateVideoHDRPreference() {
        ListPreference pref = (ListPreference) findPreference(SettingsManager.KEY_VIDEO_HDR_VALUE);
        if (pref == null) {
            return;
        }
        pref.setEnabled(mSettingsManager.isZZHDRSupported());
    }

    private void updatePreferenceButton(String key) {
        Preference pref = findPreference(key);
        if (pref != null) {
            if (pref instanceof ListPreference) {
                ListPreference pref2 = (ListPreference) pref;
                if (pref2.getEntryValues().length == 1) {
                    pref2.setEnabled(false);
                } else {
                    pref2.setEnabled(true);
                }
            } else {
                pref.setEnabled(false);
            }
        }
    }

    private void updatePictureSizePreferenceButton() {
        ListPreference picturePref = (ListPreference) findPreference(
                SettingsManager.KEY_PICTURE_SIZE);
        String sceneMode = mSettingsManager.getValue(SettingsManager.KEY_SCENE_MODE);
        if (sceneMode != null && picturePref != null) {
            int sceneModeInt = Integer.parseInt(sceneMode);
            if (sceneModeInt == SettingsManager.SCENE_MODE_BOKEH_INT) {
                picturePref.setValue("4000x3000");
                mSettingsManager.setValue(SettingsManager.KEY_PICTURE_SIZE, "4000x3000");
            }
            picturePref.setEnabled((sceneModeInt != SettingsManager.SCENE_MODE_DUAL_INT) &&
                    (sceneModeInt != SettingsManager.SCENE_MODE_BOKEH_INT));
        }
    }

    private void updatePreference(String key) {
        ListPreference pref = (ListPreference) findPreference(key);
        if (pref != null) {
            if (mSettingsManager.getEntries(key) != null) {
                pref.setEntries(mSettingsManager.getEntries(key));
                pref.setEntryValues(mSettingsManager.getEntryValues(key));
                int idx = mSettingsManager.getValueIndex(key);
                if (idx < 0) {
                    idx = 0;
                }
                pref.setValueIndex(idx);
            }
        }
    }

    private boolean isOn(String value) {
        return value.equals("on") || value.equals("enable");
    }

    @Override
    protected void onStop() {
        super.onStop();
        mSharedPreferences.unregisterOnSharedPreferenceChangeListener(mSharedPreferenceChangeListener);
        finish();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mSettingsManager.unregisterListener(mListener);
    }

    private void setShowInLockScreen() {
        // Change the window flags so that secure camera can show when locked
        Window win = getWindow();
        WindowManager.LayoutParams params = win.getAttributes();
        params.flags |= WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED;
        win.setAttributes(params);
    }

    private void onRestoreDefaultSettingsClick() {
        new AlertDialog.Builder(this)
                .setMessage(R.string.pref_camera2_restore_default_hint)
                .setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        restoreSettings();
                    }
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void restoreSettings() {
        mSettingsManager.restoreSettings();
        filterPreferences();
        initializePreferences();
    }
}
