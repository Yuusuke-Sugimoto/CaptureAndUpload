package jp.ddo.kingdragon;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.database.Cursor;
import android.hardware.Camera;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.preference.PreferenceManager;
import android.provider.MediaStore;
import android.view.Display;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;
import android.view.View.OnTouchListener;
import android.view.WindowManager;
import android.view.animation.RotateAnimation;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
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

public class CaptureAndUploadActivity extends Activity
                                      implements SurfaceHolder.Callback, Camera.PictureCallback,
                                                 SensorEventListener {
    // 定数の宣言
    // アップロード先
    public static final String DESTINATION_ADDRESS = "http://kingdragon.ddo.jp/test1234/";
    // リクエストコード
    public static final int REQUEST_IMAGE_FROM_GALLERY = 0;
    public static final int REQUEST_CALL_SETTING = 1;
    public static final int REQUEST_INPUT_COMMENT = 2;

    // 変数の宣言
    // mSensorManager - センサマネージャ
    private SensorManager mSensorManager;
    // mAccelerometer - 加速度センサ
    private Sensor mAccelerometer;
    // mMagneticField - 地磁気センサ
    private Sensor mMagneticField;
    
    // capturing - 撮影中かどうか
    // true:撮影中 false:非撮影中
    private boolean capturing;
    // focusing - オートフォーカス中かどうか
    // true:オートフォーカス中 false:非オートフォーカス中
    private boolean focusing;
    // uploading - アップロード中かどうか
    // true:アップロード中 false:非アップロード中
    private boolean uploading;
    // launched - カメラが起動したかどうか
    // true:起動済 false:起動前
    private boolean launched;

    // baseDir - 保存用ディレクトリ
    private File baseDir;

    // captureButton - 撮影ボタン
    private ImageButton captureButton;
    // preview - プレビュー部分
    private SurfaceView preview;
    // mCamera - カメラのインスタンス
    private Camera mCamera;
    private int rotation;
    
    // numOfTasks - 現在のタスク数
    private int numOfTasks;
    
    // 配列の宣言
    // magneticValues - 地磁気センサによって読み取られた値が格納される
    private float[] magneticValues;
    // accelValues - 加速度センサによって読み取られた値が格納される
    private float[] accelValues;
    // degrees - 現在の各方向の傾きが格納される
    private int[] degrees;

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
        launched = false;
        rotation = 90;
        numOfTasks = 0;
        magneticValues = null;
        accelValues = null;
        degrees = new int[3];

        // 保存用ディレクトリの作成
        baseDir = new File(Environment.getExternalStorageDirectory(), "CaptureAndUpload");
        try {
            if(!baseDir.exists() && !baseDir.mkdirs()) {
                Toast.makeText(CaptureAndUploadActivity.this, getString(R.string.error_make_directory_failed), Toast.LENGTH_SHORT).show();

                finish();
            }
        }
        catch(Exception e) {
            Toast.makeText(CaptureAndUploadActivity.this, getString(R.string.error_make_directory_failed), Toast.LENGTH_SHORT).show();
            e.printStackTrace();

            finish();
        }

        captureButton = (ImageButton)findViewById(R.id.capture);
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
                if(launched && !focusing) {
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

        // 設定情報にデフォルト値をセットする
        PreferenceManager.setDefaultValues(CaptureAndUploadActivity.this, R.xml.preference, false);
        
        // 傾きを検出するための設定
        mSensorManager = (SensorManager)getSystemService(SENSOR_SERVICE);
        mAccelerometer = mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        mMagneticField = mSensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);
    }

    @Override
    public void onResume() {
        super.onResume();
        
        mSensorManager.registerListener(CaptureAndUploadActivity.this, mAccelerometer, SensorManager.SENSOR_DELAY_UI);
        mSensorManager.registerListener(CaptureAndUploadActivity.this, mMagneticField, SensorManager.SENSOR_DELAY_UI);

        surfaceChanged(preview.getHolder(), 0, 0, 0);
    }

    @Override
    public void onPause() {
        super.onPause();

        mSensorManager.unregisterListener(CaptureAndUploadActivity.this);
        
        if(mCamera != null) {
            mCamera.stopPreview();
            mCamera.release();
            mCamera = null;
        }
        launched = false;
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        switch(requestCode) {
        case CaptureAndUploadActivity.REQUEST_IMAGE_FROM_GALLERY:
            // ギャラリーから画像を取得した場合
            if(resultCode == Activity.RESULT_OK) {
                postPicture(data.getData());
            }

            break;
        case CaptureAndUploadActivity.REQUEST_INPUT_COMMENT:
            if(resultCode == Activity.RESULT_OK) {
                String comment = data.getStringExtra("comment");
                Uri imageUri = data.getData();
                postPicture(comment, imageUri);
            }
            this.onResume();

            break;
        default:
            break;
        }
    }

    /***
     * オプションメニューを作成
     */
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        boolean returnBool = super.onCreateOptionsMenu(menu);

        getMenuInflater().inflate(R.menu.menu, menu);

        return(returnBool);
    }

    /***
     * オプションメニューの項目が選択された際の動作を設定
     */
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        boolean returnBool = super.onOptionsItemSelected(item);

        Intent mIntent;
        switch(item.getItemId()) {
        case R.id.menu_call_gallery:
            // ギャラリーを開く
            mIntent = new Intent();
            mIntent.setType("image/*");
            mIntent.setAction(Intent.ACTION_PICK);
            startActivityForResult(mIntent, CaptureAndUploadActivity.REQUEST_IMAGE_FROM_GALLERY);

            break;
        case R.id.menu_call_browser:
            // アップロード先を開く
            Uri mUri = Uri.parse(CaptureAndUploadActivity.DESTINATION_ADDRESS);
            mIntent = new Intent(Intent.ACTION_VIEW, mUri);
            startActivity(mIntent);

            break;
        case R.id.menu_setting:
            // 設定画面を開く
            mIntent = new Intent(CaptureAndUploadActivity.this, SettingActivity.class);
            startActivity(mIntent);

            break;
        default:
            break;
        }

        return(returnBool);
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
        String fileName = "CAndU_" + dateFormat.format(new Date()) + ".jpg";
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
        catch(Exception e) {
            isSaveSucceed = false;
            getContentResolver().delete(destUri, null, null);
            e.printStackTrace();
        }

        if(isSaveSucceed) {
            postPicture(destUri);
        }

        capturing = false;
        mCamera.cancelAutoFocus();
        if(!PreferenceStore.isCommentEnable(CaptureAndUploadActivity.this)) {
            mCamera.startPreview();
        }
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
            params.setRotation(90);
            mCamera.setParameters(params);

            // 画面の表示方向を変更
            mCamera.setDisplayOrientation(90);

            mCamera.setPreviewDisplay(preview.getHolder());
            mCamera.cancelAutoFocus();
            mCamera.startPreview();
            
            launched = true;
        }
        catch(Exception e) {
            Toast.makeText(CaptureAndUploadActivity.this, getString(R.string.error_launch_camera_failed), Toast.LENGTH_SHORT).show();

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
     * センサの精度が変更された際に呼び出される
     */
    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {}

    /***
     * センサの値が変化した際に呼び出される
     */
    @Override
    public void onSensorChanged(SensorEvent event) {
        switch(event.sensor.getType()) {
        case Sensor.TYPE_MAGNETIC_FIELD:
            magneticValues = event.values.clone();
            
            break;
        case Sensor.TYPE_ACCELEROMETER:
            accelValues = event.values.clone();
            
            break;
        default:
            break;
        }
        
        float[] radians = new float[3];
        if(magneticValues != null && accelValues != null) {
            float[] inR = new float[16];
            float[] outR = new float[16];
            SensorManager.getRotationMatrix(inR, null, accelValues, magneticValues);
            SensorManager.remapCoordinateSystem(inR, SensorManager.AXIS_X, SensorManager.AXIS_Z, outR);
            SensorManager.getOrientation(outR, radians);
        }
        for(int i = 0; i < event.values.length; i++) {
            degrees[i] = (int)Math.floor(Math.toDegrees(radians[i]));
        }

        if(degrees != null) {
            if(Math.abs(degrees[2]) <= 20) {
                // 縦
                if(rotation != 90) {
                    changeRotation(90);
                }
            }
            else if(degrees[2] >= -110 && degrees[2] <= -70) {
                // 左に90度傾けた状態
                if(rotation != 0) {
                    changeRotation(0);
                }
            }
            else if(Math.abs(degrees[2]) >= 160) {
                // 逆さ
                if(rotation != 270) {
                    changeRotation(270);
                }
            }
            else if(degrees[2] >= 70 && degrees[2] <= 110) {
                // 右に90度傾けた状態
                if(rotation != 180) {
                    changeRotation(180);
                }
            }
        }
    }
    
    public void changeRotation(int inputRotation) {
        // Exif情報に書き込む向きを設定
        Camera.Parameters params = mCamera.getParameters();
        params.setRotation(inputRotation);
        mCamera.setParameters(params);
        
        // ボタンを回転
        RotateAnimation animation = new RotateAnimation((450 - rotation) % 360, (450 - inputRotation) % 360, captureButton.getWidth() / 2, captureButton.getHeight() / 2);
        animation.setDuration(200);
        animation.setFillAfter(true);
        captureButton.startAnimation(animation);
        
        rotation = inputRotation;
    }

    /***
     * 画像を投稿する
     * "コメントを投稿する"が有効であれば先にコメントを取得する
     *
     * @param inputUri
     *     投稿する画像のUri
     */
    public void postPicture(Uri inputUri) {
        if(inputUri != null) {
            if(PreferenceStore.isCommentEnable(CaptureAndUploadActivity.this)) {
                Intent mIntent = new Intent(CaptureAndUploadActivity.this, BlankActivity.class);
                mIntent.setData(inputUri);
                startActivityForResult(mIntent, CaptureAndUploadActivity.REQUEST_INPUT_COMMENT);
            }
            else {
                postPicture("", inputUri);
            }
        }
        else {
            Toast.makeText(CaptureAndUploadActivity.this, getString(R.string.error_image_empty), Toast.LENGTH_SHORT).show();
        }
    }


    /***
     * コメントと画像を投稿する
     *
     * @param inputComment
     *     投稿するコメント
     * @param inputUri
     *     投稿する画像のUri
     */
    public void postPicture(String inputComment, Uri inputUri) {
        if(inputUri != null) {
            Toast.makeText(CaptureAndUploadActivity.this, getString(R.string.main_uploading), Toast.LENGTH_LONG).show();
            numOfTasks++;
            TextView progress = (TextView)findViewById(R.id.progress);
            progress.setText(getString(R.string.main_uploading) + "\n" + getString(R.string.main_remain_task) + numOfTasks + getString(R.string.unit));
            progress.setVisibility(View.VISIBLE);
            
            UploadTask upTask = new UploadTask(inputComment, inputUri);
            upTask.execute();
        }
        else {
            Toast.makeText(CaptureAndUploadActivity.this, getString(R.string.error_image_empty), Toast.LENGTH_SHORT).show();
        }
    }

    /***
     * AsyncTaskによる非同期処理
     * 参考:Asynctaskを使って非同期処理を行う | Tech Booster
     *      http://techbooster.org/android/application/1339/
     */
    class UploadTask extends AsyncTask<Void, Void, String> {
        // 変数の宣言
        private int noteID;
        private String contributor;
        private String comment;
        private String passwd;
        private File image;

        public UploadTask(String inputComment, Uri inputUri) {
            contributor = PreferenceStore.getContributor(CaptureAndUploadActivity.this);
            if(contributor.length() == 0) {
                contributor = getString(R.string.setting_no_name);
            }
            comment = inputComment;
            passwd = PreferenceStore.getPasswd(CaptureAndUploadActivity.this);
            if(passwd.length() == 0) {
                passwd = getString(R.string.setting_no_passwd);
            }
            ContentResolver resolver = getContentResolver();
            Cursor mCursor = resolver.query(inputUri, null, null, null, null);
            mCursor.moveToFirst();
            image = new File(mCursor.getString(mCursor.getColumnIndex(MediaStore.MediaColumns.DATA)));
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

            if(PreferenceStore.isStatusEnable(CaptureAndUploadActivity.this)) {
                // アップロード中であることを通知領域に表示する
                NotificationManager manager = (NotificationManager)getSystemService(Context.NOTIFICATION_SERVICE);
                Intent launchIntent = new Intent(CaptureAndUploadActivity.this, CaptureAndUploadActivity.class);
                PendingIntent contentIntent = PendingIntent.getActivity(CaptureAndUploadActivity.this, 0, launchIntent, Intent.FLAG_ACTIVITY_CLEAR_TOP);
    
                String noteMessage = getString(R.string.main_uploading) + " - " + image.getName();
                Notification note = new Notification(android.R.drawable.stat_sys_upload, noteMessage, System.currentTimeMillis());
                note.setLatestEventInfo(CaptureAndUploadActivity.this, noteMessage, noteMessage, contentIntent);
                note.flags |= Notification.FLAG_AUTO_CANCEL;
    
                noteID = (int)(Math.random() * 16777216);
                manager.notify(noteID, note);
            }

            if(image != null) {
                DefaultHttpClient client = new DefaultHttpClient();
                try {
                    /***
                     * POSTでのファイルのアップロード
                     * 参考:【Android】サーバへのFormっぽいファイルアップロード | cozzbox
                     *      http://www.cozzbox.com/wordpress/archives/217
                     */
                    String url = CaptureAndUploadActivity.DESTINATION_ADDRESS + "uploadFileCheck.php";
                    HttpPost request = new HttpPost(url);
                    MultipartEntity entity = new MultipartEntity();

                    entity.addPart("contributor", new StringBody(contributor, "text/plain", Charset.forName("UTF-8")));
                    entity.addPart("comment", new StringBody(comment, "text/plain", Charset.forName("UTF-8")));
                    entity.addPart("passwd", new StringBody(passwd, "text/plain", Charset.forName("UTF-8")));
                    entity.addPart("passwd_conf", new StringBody(passwd, "text/plain", Charset.forName("UTF-8")));
                    entity.addPart("file", new FileBody(image));
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
                            default:
                                retString = getString(R.string.error_upload_failed);

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

            if(PreferenceStore.isStatusEnable(CaptureAndUploadActivity.this)) {
                NotificationManager manager = (NotificationManager)getSystemService(Context.NOTIFICATION_SERVICE);
                Intent launchIntent = new Intent(CaptureAndUploadActivity.this, CaptureAndUploadActivity.class);
                PendingIntent contentIntent = PendingIntent.getActivity(CaptureAndUploadActivity.this, 0, launchIntent, Intent.FLAG_ACTIVITY_CLEAR_TOP);
    
                if(!result.equals(getString(R.string.error_upload_failed))) {
                    // アップロードが完了したらトーストと通知を更新する
                    Toast.makeText(CaptureAndUploadActivity.this, getString(R.string.main_upload_finish), Toast.LENGTH_SHORT).show();
    
                    String noteMessage = getString(R.string.main_upload_finish) + " - " + image.getName();
                    Notification note = new Notification(android.R.drawable.checkbox_on_background, noteMessage, System.currentTimeMillis());
                    note.setLatestEventInfo(CaptureAndUploadActivity.this, noteMessage, noteMessage, contentIntent);
                    note.flags |= Notification.FLAG_AUTO_CANCEL;
    
                    manager.notify(noteID, note);
                }
                else {
                    // アップロードに失敗したらトーストと通知を更新する
                    Toast.makeText(CaptureAndUploadActivity.this, getString(R.string.error_upload_failed), Toast.LENGTH_SHORT).show();
    
                    String noteMessage = getString(R.string.error_upload_failed) + " - " + image.getName();
                    Notification note = new Notification(android.R.drawable.ic_delete, noteMessage, System.currentTimeMillis());
                    note.setLatestEventInfo(CaptureAndUploadActivity.this, noteMessage, noteMessage, contentIntent);
                    note.flags |= Notification.FLAG_AUTO_CANCEL;
    
                    manager.notify(noteID, note);
                }
            }
            numOfTasks--;
            TextView progress = (TextView)findViewById(R.id.progress);
            if(numOfTasks > 0) {
                progress.setText(getString(R.string.main_uploading) + "\n" + getString(R.string.main_remain_task) + numOfTasks + getString(R.string.unit));
            }
            else {
                numOfTasks = 0;
                progress.setText("");
                progress.setVisibility(View.INVISIBLE);
            }
            
            uploading = false;
        }
    }
}
