package jp.ddo.kingdragon;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.os.Bundle;
import android.preference.EditTextPreference;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.Preference.OnPreferenceChangeListener;
import android.preference.PreferenceActivity;
import android.widget.ListView;

public class SettingActivity extends PreferenceActivity implements OnSharedPreferenceChangeListener {
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        addPreferencesFromResource(R.xml.preference);

        EditTextPreference mEditTextPref = (EditTextPreference)findPreference("setting_passwd");
        mEditTextPref.setOnPreferenceChangeListener(new OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(Preference preference, Object newValue) {
                // 新しいパスワードが0文字または6文字以上16文字以内でなければ変更しない
                boolean returnBool = false;

                String inputString = (String)newValue;
                if(inputString.length() == 0 || inputString.length() >= 6 && inputString.length() <= 16) {
                    returnBool = true;
                }
                else {
                    // 変更されなかったことをダイアログで通知する
                    showDialog(CaptureAndUploadActivity.DIALOG_PASSWD_NOT_CHANGED);
                }

                return(returnBool);
            }
        });
    }

    @Override
    public void onResume() {
        super.onResume();

        getPreferenceScreen().getSharedPreferences().registerOnSharedPreferenceChangeListener(this);

        updateUI();
    }

    @Override
    public void onPause() {
        super.onPause();

        getPreferenceScreen().getSharedPreferences().unregisterOnSharedPreferenceChangeListener(this);
    }

    /***
     * ダイアログを生成する
     */
    @Override
    protected Dialog onCreateDialog(int id) {
        Dialog retDialog = super.onCreateDialog(id);

        AlertDialog.Builder builder;

        switch(id) {
        case CaptureAndUploadActivity.DIALOG_PASSWD_NOT_CHANGED:
            builder = new AlertDialog.Builder(SettingActivity.this);
            builder.setIcon(android.R.drawable.ic_dialog_alert);
            builder.setTitle(getString(R.string.error_title));
            builder.setMessage(getString(R.string.error_illegal_length_passwd));
            builder.setPositiveButton(getString(R.string.ok), null);
            builder.setCancelable(true);
            retDialog = builder.create();

            break;
        default:
            retDialog = null;

            break;
        }

        return(retDialog);
    }

    /***
     * 設定が変更された際に呼び出される
     */
    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        updateUI();
    }

    /***
     * 各項目の表示を更新する
     */
    public void updateUI() {
        ListPreference mListPref = (ListPreference)findPreference("setting_rotation");
        mListPref.setSummary(mListPref.getEntry());

        EditTextPreference mEditTextPref = (EditTextPreference)findPreference("setting_contributor");
        if(mEditTextPref.getText() != null && mEditTextPref.getText().length() != 0) {
            mEditTextPref.setSummary(mEditTextPref.getText());
        }
        else {
            mEditTextPref.setSummary(getString(R.string.setting_no_name));
        }

        mEditTextPref = (EditTextPreference)findPreference("setting_passwd");
        if(mEditTextPref.getText() != null && mEditTextPref.getText().length() != 0) {
            mEditTextPref.setSummary(getString(R.string.setting_configured));
        }
        else {
            mEditTextPref.setSummary(getString(R.string.setting_not_configured));
        }

        ListView mListView = SettingActivity.this.getListView();
        mListView.invalidateViews();
    }
}