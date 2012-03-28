package jp.ddo.kingdragon;

import android.app.Activity;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.hardware.Camera;
import android.media.ExifInterface;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.view.Display;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.View.OnTouchListener;
import android.view.Window;
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
    // 定数の宣言
    //リクエストコード
    public static final int REQUEST_IMAGE = 0;

    // 変数の宣言
    // capturing - 撮影中かどうか
    // true:撮影中 false:非撮影中
    private boolean capturing;
    // focused - オートフォーカス済みかどうか
    // true:オートフォーカス済み false:オートフォーカス前
    private boolean focused;
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
         * タイトルバーを消す
         * 参考:ウィンドウタイトルバーを非表示にするには - 逆引きAndroid入門
         *      http://www.adakoda.com/android/000155.html
         *
         * ステータスバーを消す
         * 参考:タイトルバーやステータスバーを非表示にする方法 - [Androidアプリ/Android] ぺんたん info
         *      http://pentan.info/android/app/status_bar_hidden.html
         *
         * スリープを無効にする
         * 参考:画面をスリープ状態にさせないためには - 逆引きAndroid入門
         *      http://www.adakoda.com/android/000207.html
         */
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN | WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        setContentView(R.layout.main);

        /***
         * Keep Aliveを無効にする
         * 参考:Broken pipe exception - Twitter4J J | Google グループ
         *      http://groups.google.com/group/twitter4j-j/browse_thread/thread/56b18baac1846ab2?pli=1
         */
        System.setProperty("http.keepAlive", "false");
        System.setProperty("https.keepAlive", "false");

        capturing = false;
        focused = false;

        // 保存用ディレクトリの作成
        baseDir = new File(Environment.getExternalStorageDirectory(), "CaptureAndUpload");
        try {
            if(!baseDir.exists() && !baseDir.mkdirs()) {
                Toast.makeText(getApplicationContext(), getResources().getString(R.string.error_make_directory_failed), Toast.LENGTH_SHORT).show();
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
                    capturing = true;
                    captureButton.setEnabled(false);
                    if(focused) {
                        // オートフォーカス済みであればそのまま撮影
                        mCamera.takePicture(null, null, null, CaptureAndUploadActivity.this);
                    }
                    else {
                        // オートフォーカス前であればオートフォーカス後に撮影
                        mCamera.autoFocus(new Camera.AutoFocusCallback() {
                            @Override
                            public void onAutoFocus(boolean success, Camera camera) {
                                mCamera.stopPreview();
                                Thread mThread = new Thread(new Runnable() {
                                    @Override
                                    public void run() {
                                        try {
                                            Thread.sleep(300);
                                        }
                                        catch(Exception e) {
                                            e.printStackTrace();
                                        }
                                        mCamera.takePicture(null, null, null, CaptureAndUploadActivity.this);
                                    }
                                });
                                mThread.start();
                            }
                        });
                    }
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
                mCamera.autoFocus(null);
                focused = true;

                return(false);
            }
        });
    }

    @Override
    public void onResume() {
        super.onResume();

        if(mCamera == null) {
            try {
                mCamera = Camera.open();
            }
            catch(Exception e) {
                Toast.makeText(getApplicationContext(), getResources().getString(R.string.error_launch_camera_failed), Toast.LENGTH_SHORT).show();
                finish();
            }
        }
        surfaceCreated(preview.getHolder());
        surfaceChanged(preview.getHolder(), 0, 0, 0);
        mCamera.startPreview();
    }

    @Override
    public void onPause() {
        super.onPause();

        if(mCamera != null) {
            mCamera.stopPreview();
            mCamera.release();
        }
    }

    @Override
    public void onPictureTaken(byte[] data, Camera camera) {
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyyMMddkkmmss");
        String fileName = "CAndU_" + dateFormat.format(new Date()) + ".jpg";
        File destFile = new File(baseDir, fileName);

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
            UploadTask upTask = new UploadTask(destFile);
            upTask.execute();
        }
        catch(FileNotFoundException e) {
            getContentResolver().delete(destUri, null, null);
            e.printStackTrace();
        }
        catch(IOException e) {
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
            ExifInterface ei = new ExifInterface(destFile.getAbsolutePath());
            WindowManager wm = (WindowManager)getSystemService(Context.WINDOW_SERVICE);
            Display mDisplay = wm.getDefaultDisplay();
            switch(mDisplay.getRotation()) {
            case Surface.ROTATION_0:
                ei.setAttribute(ExifInterface.TAG_ORIENTATION, "6");

                break;
            case Surface.ROTATION_90:
                ei.setAttribute(ExifInterface.TAG_ORIENTATION, "1");

                break;
            case Surface.ROTATION_270:
                ei.setAttribute(ExifInterface.TAG_ORIENTATION, "3");

                break;
            default:
                break;
            }
            ei.saveAttributes();
        }
        catch(IOException e) {
            e.printStackTrace();
        }

        mCamera.startPreview();
        capturing = false;
        focused = false;
        captureButton.setEnabled(true);
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
        mCamera.stopPreview();

        Camera.Parameters params = mCamera.getParameters();
        List<Camera.Size> previewSizes = params.getSupportedPreviewSizes();
        Camera.Size selected = previewSizes.get(0);
        for(int i = 1; i < previewSizes.size(); i++) {
            Camera.Size temp = previewSizes.get(i);
            if(selected.width * selected.height < temp.width * temp.height) {
                // 一番大きなプレビューサイズを選択
                selected = temp;
            }
        }
        params.setPreviewSize(selected.width, selected.height);

        List<Camera.Size> pictureSizes = params.getSupportedPictureSizes();
        selected = pictureSizes.get(0);
        for(int i = 1; i < pictureSizes.size(); i++) {
            Camera.Size temp = pictureSizes.get(i);
            if(selected.width * selected.height < temp.width * temp.height) {
                // 一番大きな画像サイズを選択
                selected = temp;
            }
        }
        params.setPictureSize(selected.width, selected.height);

        mCamera.setParameters(params);

        WindowManager wm = (WindowManager)getSystemService(Context.WINDOW_SERVICE);
        Display mDisplay = wm.getDefaultDisplay();
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

        mCamera.startPreview();
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        try {
            mCamera.setPreviewDisplay(preview.getHolder());
        }
        catch(Exception e) {
            e.printStackTrace();
        }
    }

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
        public void onPreExecute() {
            super.onPreExecute();

            NotificationManager manager = (NotificationManager)getSystemService(Context.NOTIFICATION_SERVICE);
            Intent launchIntent = new Intent(getApplicationContext(), CaptureAndUploadActivity.class);
            PendingIntent contentIntent = PendingIntent.getActivity(getApplicationContext(), 0, launchIntent, Intent.FLAG_ACTIVITY_CLEAR_TOP);

            String message = getResources().getString(R.string.main_uploading) + " - " + mFile.getName();
            Notification note = new Notification(android.R.drawable.ic_menu_info_details, message, System.currentTimeMillis());
            note.setLatestEventInfo(getApplicationContext(), message, message, contentIntent);
            note.flags |= Notification.FLAG_AUTO_CANCEL;

            noteID = (int)(Math.random() * 16777216);
            manager.notify(noteID, note);
        }

        @Override
        public String doInBackground(Void... params) {
            String retString = "";

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
                                retString = getResources().getString(R.string.error_missing_parameters);
                                break;
                            default:
                                retString = getResources().getString(R.string.error_upload_failed) + " - " + mFile.getName();
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

            NotificationManager manager = (NotificationManager)getSystemService(Context.NOTIFICATION_SERVICE);
            Intent launchIntent = new Intent(getApplicationContext(), CaptureAndUploadActivity.class);
            PendingIntent contentIntent = PendingIntent.getActivity(getApplicationContext(), 0, launchIntent, Intent.FLAG_ACTIVITY_CLEAR_TOP);

            String message = getResources().getString(R.string.main_upload_success) + " - " + mFile.getName();
            Notification note = new Notification(android.R.drawable.ic_menu_info_details, message, System.currentTimeMillis());
            note.setLatestEventInfo(getApplicationContext(), message, message, contentIntent);
            note.flags |= Notification.FLAG_AUTO_CANCEL;

            manager.notify(noteID, note);
        }
    }
}
