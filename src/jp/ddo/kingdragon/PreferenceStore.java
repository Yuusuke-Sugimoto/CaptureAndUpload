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