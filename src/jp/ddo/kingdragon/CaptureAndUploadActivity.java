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
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.View.OnTouchListener;
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
    // 通知
    public static final int NOTE_UPLOAD = 0;

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
                Toast.makeText(getApplicationContext(), "保存用ディレクトリの作成に失敗しました。", Toast.LENGTH_SHORT).show();
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

        try {
            mCamera = Camera.open();
            mCamera.startPreview();
        }
        catch(Exception e) {
            Toast.makeText(getApplicationContext(), "カメラの起動に失敗しました。", Toast.LENGTH_SHORT).show();
            finish();
        }
    }

    @Override
    public void onPause() {
        super.onPause();

        mCamera.stopPreview();
        mCamera.release();
    }

    @Override
    public void onPictureTaken(byte[] data, Camera camera) {
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyyMMddkkmmss");
        String fileName = "CaptureAndUpload_" + dateFormat.format(new Date()) + ".jpg";
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
            UploadTask upTask = new UploadTask();
            upTask.execute(destFile);
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
            // 画像の向きを書き込み
            ExifInterface ei = new ExifInterface(destFile.getAbsolutePath());
            ei.setAttribute(ExifInterface.TAG_ORIENTATION, "6");
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
        Toast.makeText(getApplicationContext(), selected.width + ", " + selected.height, Toast.LENGTH_SHORT).show();
        for(int i = 1; i < pictureSizes.size(); i++) {
            Camera.Size temp = pictureSizes.get(i);
            if(selected.width * selected.height < temp.width * temp.height) {
                // 一番大きな画像サイズを選択
                selected = temp;
            }
        }
        params.setPictureSize(selected.width, selected.height);

        mCamera.setParameters(params);
        mCamera.setDisplayOrientation(90);

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
    class UploadTask extends AsyncTask<File, Void, String> {
        @Override
        public void onPreExecute() {
            super.onPreExecute();

            NotificationManager manager = (NotificationManager)getSystemService(Context.NOTIFICATION_SERVICE);
            Intent launchIntent = new Intent(getApplicationContext(), CaptureAndUploadActivity.class);
            PendingIntent contentIntent = PendingIntent.getActivity(getApplicationContext(), 0, launchIntent, Intent.FLAG_ACTIVITY_CLEAR_TOP);

            Notification note = new Notification(android.R.drawable.ic_menu_info_details, "ファイルをアップロード中…", System.currentTimeMillis());
            note.setLatestEventInfo(getApplicationContext(), "ファイルをアップロード中…", "ファイルをアップロード中…", contentIntent);
            note.flags |= Notification.FLAG_AUTO_CANCEL;

            manager.notify(NOTE_UPLOAD, note);
        }

        @Override
        public String doInBackground(File... params) {
            String retString = "";
            File mFile = params[0];

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
                    entity.addPart("comment", new StringBody("Androidアプリからの投稿テスト", "text/plain", Charset.forName("UTF-8")));
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
                                retString = "パラメータがありません。";
                                break;
                            default:
                                retString = "ファイルのアップロードに失敗しました。";
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

            Notification note = new Notification(android.R.drawable.ic_menu_info_details, "ファイルのアップロードに成功しました。", System.currentTimeMillis());
            note.setLatestEventInfo(getApplicationContext(), "ファイルのアップロードに成功しました。", "ファイルのアップロードに成功しました。", contentIntent);
            note.flags |= Notification.FLAG_AUTO_CANCEL;

            manager.notify(NOTE_UPLOAD, note);
        }
    }
}
