package jp.ddo.kingdragon;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.ContentValues;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.hardware.Camera;
import android.media.ExifInterface;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.provider.MediaStore;
import android.view.Display;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;
import android.view.View.OnTouchListener;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.Toast;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.Charset;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.ResponseHandler;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.mime.MultipartEntity;
import org.apache.http.entity.mime.content.FileBody;
import org.apache.http.entity.mime.content.StringBody;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.util.EntityUtils;

public class CaptureAndUploadActivity extends Activity implements SurfaceHolder.Callback, Camera.PictureCallback {
    // 変数の宣言
    // mHandler - 他スレッドからのUIの更新に使用
    private Handler mHandler;

    // capturing - 撮影中かどうか
    // true:撮影中 false:非撮影中
    private boolean capturing;
    // focusing - オートフォーカス中かどうか
    // true:オートフォーカス中 false:非オートフォーカス中
    private boolean focusing;
    // uploading - アップロード中かどうか
    // true:アップロード中 false:非アップロード中
    private boolean uploading;

    // baseDir - 保存用ディレクトリ
    private File baseDir;

    // captureButton - 撮影ボタン
    private Button captureButton;
    // preview - プレビュー部分
    private SurfaceView preview;
    // mCamera - カメラのインスタンス
    private Camera mCamera;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        /***
         * スリープを無効にする
         * 参考:画面をスリープ状態にさせないためには - 逆引きAndroid入門
         *      http://www.adakoda.com/android/000207.html
         */
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        setContentView(R.layout.main);

        /***
         * Keep Aliveを無効にする
         * 参考:Broken pipe exception - Twitter4J J | Google グループ
         *      http://groups.google.com/group/twitter4j-j/browse_thread/thread/56b18baac1846ab2?pli=1
         */
        System.setProperty("http.keepAlive", "false");
        System.setProperty("https.keepAlive", "false");

        capturing = false;
        focusing = false;
        uploading = false;

        // 保存用ディレクトリの作成
        baseDir = new File(Environment.getExternalStorageDirectory(), "CaptureAndUpload");
        try {
            if(!baseDir.exists() && !baseDir.mkdirs()) {
                Toast.makeText(getApplicationContext(), getString(R.string.error_make_directory_failed), Toast.LENGTH_SHORT).show();
                finish();
            }
        }
        catch(Exception e) {
            e.printStackTrace();
        }

        captureButton = (Button)findViewById(R.id.capture);
        captureButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if(!capturing) {
                    // 撮影中でなければ撮影
                    capturing = true;
                    captureButton.setEnabled(false);
                    mCamera.takePicture(null, null, null, CaptureAndUploadActivity.this);
                }
            }
        });

        preview = (SurfaceView)findViewById(R.id.preview);
        preview.getHolder().addCallback(CaptureAndUploadActivity.this);
        preview.getHolder().setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);
        preview.setOnTouchListener(new OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                // 画面がタッチされたらオートフォーカスを実行
                if(!focusing) {
                    // オートフォーカス中でなければオートフォーカスを実行
                    // フラグを更新
                    focusing = true;

                    captureButton.setEnabled(false);
                    mCamera.autoFocus(new Camera.AutoFocusCallback() {
                        @Override
                        public void onAutoFocus(boolean success, Camera camera) {
                            focusing = false;
                            captureButton.setEnabled(true);
                        }
                    });
                }

                return(false);
            }
        });
    }

    @Override
    public void onResume() {
        super.onResume();

        surfaceChanged(preview.getHolder(), 0, 0, 0);
    }

    @Override
    public void onPause() {
        super.onPause();

        if(mCamera != null) {
            mCamera.stopPreview();
            mCamera.release();
            mCamera = null;
        }
    }

    /***
     * バックボタンが押された際に本当に終了するかどうかを尋ねる
     */
    @Override
    public void onBackPressed() {
        AlertDialog.Builder builder = new AlertDialog.Builder(CaptureAndUploadActivity.this);
        builder.setIcon(android.R.drawable.ic_dialog_alert);
        builder.setTitle(getString(R.string.main_exit_title));
        builder.setMessage(getString(R.string.main_exit_message));
        builder.setPositiveButton(getString(R.string.yes), new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                // ポジティブボタンが押されたら終了する
                finish();
            }
        });
        builder.setNegativeButton(getString(R.string.no), null);
        builder.setCancelable(true);
        builder.create().show();
    }

    /***
     * 写真撮影時のコールバックメソッド
     */
    @Override
    public void onPictureTaken(byte[] data, Camera camera) {
        Boolean isSaveSucceed = true;

        // ファイル名を生成
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyyMMddkkmmss");
        String fileName = "tottepost_" + dateFormat.format(new Date()) + ".jpg";
        File destFile = new File(baseDir, fileName);

        // 生成したファイル名で新規ファイルを登録
        ContentValues values = new ContentValues();
        values.put(MediaStore.Images.Media.TITLE, fileName);
        values.put("_data", destFile.getAbsolutePath());
        values.put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg");
        Uri destUri = getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);

        try {
            FileOutputStream fos = new FileOutputStream(destFile.getAbsolutePath());
            fos.write(data);
            fos.flush();
            fos.close();
        }
        catch(FileNotFoundException e) {
            isSaveSucceed = false;
            getContentResolver().delete(destUri, null, null);
            e.printStackTrace();
        }
        catch(IOException e) {
            isSaveSucceed = false;
            getContentResolver().delete(destUri, null, null);
            e.printStackTrace();
        }

        try {
            /***
             * 画像の向きを書き込む
             * 参考:AndroidでExif情報編集 – Android | team-hiroq
             *      http://team-hiroq.com/blog/android/android_jpeg_exif.html
             *
             *      [AIR][Android] CameraUIで撮影した写真の回転が、機種によってバラバラなのをExifで補整する！  |    R o m a t i c A : Blog  : Archive
             *      http://blog.romatica.com/2011/04/04/air-for-android-cameraui-exif/
             *
             * 画面の向きを検出する
             * 参考:Androidアプリ開発メモ027：画面の向き: ぷ～ろぐ
             *      http://into.cocolog-nifty.com/pulog/2011/10/android027-9b2b.html
             */
            ExifInterface mExifInterface = new ExifInterface(destFile.getAbsolutePath());
            WindowManager wm = (WindowManager)getSystemService(Context.WINDOW_SERVICE);
            Display mDisplay = wm.getDefaultDisplay();
            switch(mDisplay.getRotation()) {
            case Surface.ROTATION_0:
                mExifInterface.setAttribute(ExifInterface.TAG_ORIENTATION, "6");

                break;
            case Surface.ROTATION_90:
                mExifInterface.setAttribute(ExifInterface.TAG_ORIENTATION, "1");

                break;
            case Surface.ROTATION_270:
                mExifInterface.setAttribute(ExifInterface.TAG_ORIENTATION, "3");

                break;
            default:
                break;
            }
            mExifInterface.saveAttributes();
        }
        catch(IOException e) {
            isSaveSucceed = false;
            e.printStackTrace();
        }

        if(isSaveSucceed) {
            UploadTask upTask = new UploadTask(destFile);
            upTask.execute();
        }

        capturing = false;
        mCamera.cancelAutoFocus();
        mCamera.startPreview();
        captureButton.setEnabled(true);
    }

    /***
     * SurfaceViewのサイズなどが変更された際に呼び出される
     */
    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
        try {
            if(mCamera != null) {
                mCamera.stopPreview();
            }
            else {
                mCamera = Camera.open();
            }

            // 各種パラメータの設定
            Camera.Parameters params = mCamera.getParameters();
            // 保存する画像サイズを決定
            List<Camera.Size> pictureSizes = params.getSupportedPictureSizes();
            Camera.Size picSize = pictureSizes.get(0);
            for(int i = 1; i < pictureSizes.size(); i++) {
                Camera.Size temp = pictureSizes.get(i);
                if(picSize.width * picSize.height > 2048 * 1232 || picSize.width * picSize.height < temp.width * temp.height) {
                    // 2048x1232以下で一番大きな画像サイズを選択
                    picSize = temp;
                }
            }
            params.setPictureSize(picSize.width, picSize.height);

            // 画像サイズを元にプレビューサイズを決定
            List<Camera.Size> previewSizes = params.getSupportedPreviewSizes();
            Camera.Size preSize = previewSizes.get(0);
            for(int i = 1; i < previewSizes.size(); i++) {
                Camera.Size temp = previewSizes.get(i);
                if(preSize.width * preSize.height < temp.width * temp.height) {
                    if(Math.abs((double)picSize.width / (double)picSize.height - (double)preSize.width / (double)preSize.height)
                       >= Math.abs((double)picSize.width / (double)picSize.height - (double)temp.width / (double)temp.height)) {
                        // 一番保存サイズの比に近くてかつ一番大きなプレビューサイズを選択
                        preSize = temp;
                    }
                }
            }
            params.setPreviewSize(preSize.width, preSize.height);

            // プレビューサイズを元にSurfaceViewのサイズを決定
            // プレビューサイズとSurfaceViewのサイズで縦横の関係が逆になっている
            WindowManager manager = (WindowManager)getSystemService(WINDOW_SERVICE);
            Display mDisplay = manager.getDefaultDisplay();
            ViewGroup.LayoutParams lParams = preview.getLayoutParams();
            lParams.width  = mDisplay.getWidth();
            lParams.height = mDisplay.getHeight();
            if((double)preSize.width / (double)preSize.height
               < (double)mDisplay.getHeight() / (double)mDisplay.getWidth()) {
                // 横の長さに合わせる
                lParams.height = preSize.width * mDisplay.getWidth() / preSize.height;
            }
            else if((double)preSize.width / (double)preSize.height
                    > (double)mDisplay.getHeight() / (double)mDisplay.getWidth()) {
                // 縦の長さに合わせる
                lParams.width  = preSize.height * mDisplay.getHeight() / preSize.width;
            }
            preview.setLayoutParams(lParams);

            mCamera.setParameters(params);

            switch(mDisplay.getRotation()) {
            case Surface.ROTATION_0:
                mCamera.setDisplayOrientation(90);

                break;
            case Surface.ROTATION_90:
                mCamera.setDisplayOrientation(0);

                break;
            case Surface.ROTATION_270:
                mCamera.setDisplayOrientation(180);

                break;
            default:
                break;
            }

            mCamera.setPreviewDisplay(preview.getHolder());
            mCamera.startPreview();
        }
        catch(Exception e) {
            Toast.makeText(getApplicationContext(), getString(R.string.error_launch_camera_failed), Toast.LENGTH_SHORT).show();

            finish();
        }
    }

    /***
     * SurfaceViewが生成された際に呼び出される
     */
    @Override
    public void surfaceCreated(SurfaceHolder holder) {}

    /***
     * SurfaceViewが破棄される際に呼び出される
     */
    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {}

    /***
     * AsyncTaskによる非同期処理
     * 参考:Asynctaskを使って非同期処理を行う | Tech Booster
     *      http://techbooster.org/android/application/1339/
     */
    class UploadTask extends AsyncTask<Void, Void, String> {
        // 変数の宣言
        private int noteID;
        private File mFile;

        public UploadTask(File inputFile) {
            mFile = inputFile;
        }

        @Override
        public String doInBackground(Void... params) {
            String retString = "";

            while(uploading) {
                // 他のファイルをアップロード中であれば待機する
                try {
                    Thread.sleep(500);
                }
                catch(Exception e) {
                    e.printStackTrace();
                }
            }
            uploading = true;

            // アップロード中であることを通知領域に表示する
            NotificationManager manager = (NotificationManager)getSystemService(Context.NOTIFICATION_SERVICE);
            Intent launchIntent = new Intent(getApplicationContext(), CaptureAndUploadActivity.class);
            PendingIntent contentIntent = PendingIntent.getActivity(getApplicationContext(), 0, launchIntent, Intent.FLAG_ACTIVITY_CLEAR_TOP);

            String message = getString(R.string.main_uploading) + " - " + mFile.getName();
            Notification note = new Notification(android.R.drawable.ic_menu_info_details, message, System.currentTimeMillis());
            note.setLatestEventInfo(getApplicationContext(), message, message, contentIntent);
            note.flags |= Notification.FLAG_AUTO_CANCEL;

            noteID = (int)(Math.random() * 16777216);
            manager.notify(noteID, note);

            if(mFile != null) {
                DefaultHttpClient client = new DefaultHttpClient();
                try {
                    /***
                     * POSTでのファイルのアップロード
                     * 参考:【Android】サーバへのFormっぽいファイルアップロード | cozzbox
                     *      http://www.cozzbox.com/wordpress/archives/217
                     */
                    String url = "http://kingdragon.ddo.jp/test1234/uploadFileCheck.php";
                    HttpPost request = new HttpPost(url);
                    MultipartEntity entity = new MultipartEntity();

                    entity.addPart("contributor", new StringBody("ななしさん", "text/plain", Charset.forName("UTF-8")));
                    entity.addPart("comment", new StringBody("", "text/plain", Charset.forName("UTF-8")));
                    entity.addPart("passwd", new StringBody("testtest", "text/plain", Charset.forName("UTF-8")));
                    entity.addPart("passwd_conf", new StringBody("testtest", "text/plain", Charset.forName("UTF-8")));
                    entity.addPart("file", new FileBody(mFile));
                    request.setEntity(entity);

                    retString = client.execute(request, new ResponseHandler<String>() {
                        @Override
                        public String handleResponse(HttpResponse response) throws ClientProtocolException, IOException {
                            String retString = "";

                            int status = response.getStatusLine().getStatusCode();
                            switch(status) {
                            case HttpStatus.SC_OK:
                                retString = EntityUtils.toString(response.getEntity(), "UTF-8");
                                break;
                            case HttpStatus.SC_NOT_FOUND:
                                retString = getString(R.string.error_missing_parameters);
                                break;
                            default:
                                retString = getString(R.string.error_upload_failed) + " - " + mFile.getName();
                                break;
                            }

                            return(retString);
                        }
                    });
                }
                catch(Exception e) {
                    e.printStackTrace();
                }
                finally {
                    client.getConnectionManager().shutdown();
                }
            }

            return(retString);
        }

        @Override
        public void onPostExecute(String result) {
            super.onPostExecute(result);

            // アップロードが完了したら通知を更新する
            NotificationManager manager = (NotificationManager)getSystemService(Context.NOTIFICATION_SERVICE);
            Intent launchIntent = new Intent(getApplicationContext(), CaptureAndUploadActivity.class);
            PendingIntent contentIntent = PendingIntent.getActivity(getApplicationContext(), 0, launchIntent, Intent.FLAG_ACTIVITY_CLEAR_TOP);

            String message = getString(R.string.main_upload_success) + " - " + mFile.getName();
            Notification note = new Notification(android.R.drawable.ic_menu_info_details, message, System.currentTimeMillis());
            note.setLatestEventInfo(getApplicationContext(), message, message, contentIntent);
            note.flags |= Notification.FLAG_AUTO_CANCEL;

            manager.notify(noteID, note);
            uploading = false;
        }
    }
}
