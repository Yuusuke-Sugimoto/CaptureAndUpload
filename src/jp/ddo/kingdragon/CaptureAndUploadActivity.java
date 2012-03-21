package jp.ddo.kingdragon;

import android.app.Activity;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.provider.MediaStore;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.Toast;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.charset.Charset;


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

public class CaptureAndUploadActivity extends Activity {
    // 定数の宣言
    public static final int REQUEST_IMAGE = 0;

    public static final int NOTE_UPLOAD = 0;

    // 変数の宣言
    private Handler mHandler;

    private File destination;
    private Button captureButton;
    private ImageView imageView;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);

        mHandler = new Handler();

        File baseDir = new File(Environment.getExternalStorageDirectory(), "CaptureAndUpload/Temps");
        try {
            if(!baseDir.exists() && !baseDir.mkdirs()) {
                Toast.makeText(getApplicationContext(), "保存用ディレクトリの作成に失敗しました。", Toast.LENGTH_SHORT).show();
                finish();
            }
        }
        catch(Exception e) {
            e.printStackTrace();
        }
        destination = new File(Environment.getExternalStorageDirectory(), "CaptureAndUpload/Temps/image.jpg");

        captureButton = (Button)findViewById(R.id.capture);
        captureButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent mIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
                mIntent.putExtra(MediaStore.EXTRA_OUTPUT, Uri.fromFile(destination));
                startActivityForResult(mIntent, REQUEST_IMAGE);
            }
        });

        imageView = (ImageView)findViewById(R.id.image);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if(requestCode == REQUEST_IMAGE && resultCode == Activity.RESULT_OK) {
            try {
                FileInputStream mInput = new FileInputStream(destination);
                BitmapFactory.Options mOptions = new BitmapFactory.Options();
                mOptions.inSampleSize = 5;

                Bitmap userImage = BitmapFactory.decodeStream(mInput, null, mOptions);
                imageView.setImageBitmap(userImage);

                UploadTask mUploadTask = new UploadTask();
                mUploadTask.execute(destination);
            }
            catch(Exception e) {
                e.printStackTrace();
            }
        }
    }

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
            final String showMsg = retString;
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    Toast.makeText(getApplicationContext(), showMsg, Toast.LENGTH_SHORT).show();
                }
            });

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