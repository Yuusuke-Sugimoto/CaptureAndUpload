package jp.ddo.kingdragon;

import android.content.Context;
import android.preference.PreferenceManager;

public class PreferenceStore {
    /***
     * コンストラクタ
     * インスタンスが生成されないようにprivate宣言しておく
     */
    private PreferenceStore() {}

    /***
     * "通知を表示する"が有効かどうかを調べる
     *
     * @param context
     *     SharedPreferences取得用のコンテキスト
     * @return 有効ならばtrue、無効または未設定ならばfalseを返す
     */
    public static boolean isStatusEnable(Context context) {
        return(PreferenceManager.getDefaultSharedPreferences(context).getBoolean("setting_show_status", false));
    }

    /***
     * "撮影時の向き"の設定値を取得する
     *
     * @param context
     *     SharedPreferences取得用のコンテキスト
     * @return "撮影時の向き"の設定値 未設定ならば-1を返す
     */
    public static int getRotationSetting(Context context) {
        return(Integer.parseInt(PreferenceManager.getDefaultSharedPreferences(context).getString("setting_rotation", "-1")));
    }

    /***
     * 設定済の投稿者名を取得する
     *
     * @param context
     *     SharedPreferences取得用のコンテキスト
     * @return 設定済の投稿者名 設定されていなければ空の文字列を返す
     */
    public static String getContributor(Context context) {
        return(PreferenceManager.getDefaultSharedPreferences(context).getString("setting_contributor", ""));
    }

    /***
     * "コメントを投稿する"が有効かどうかを調べる
     *
     * @param context
     *     SharedPreferences取得用のコンテキスト
     * @return 有効ならばtrue、無効または未設定ならばfalseを返す
     */
    public static boolean isCommentEnable(Context context) {
        return(PreferenceManager.getDefaultSharedPreferences(context).getBoolean("setting_use_comment", false));
    }

    /***
     * 設定済のパスワードを取得する
     *
     * @param context
     *     SharedPreferences取得用のコンテキスト
     * @return 設定済のパスワード 設定されていなければ空の文字列を返す
     */
    public static String getPasswd(Context context) {
        return(PreferenceManager.getDefaultSharedPreferences(context).getString("setting_passwd", ""));
    }
}