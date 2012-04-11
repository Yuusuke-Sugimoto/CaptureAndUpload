package jp.ddo.kingdragon;

import android.app.Activity;
import android.content.ContentResolver;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.media.ExifInterface;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.DisplayMetrics;
import android.widget.ImageView;

import java.io.File;

public class BlankActivity extends Activity {
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.blank);

        try {
            ContentResolver resolver = getContentResolver();
            Uri imageUri = getIntent().getData();

            // 画像の向きを検出する
            Cursor mCursor = resolver.query(imageUri, null, null, null, null);
            mCursor.moveToFirst();
            File mFile = new File(mCursor.getString(mCursor.getColumnIndex(MediaStore.MediaColumns.DATA)));
            ExifInterface mExifInterface = new ExifInterface(mFile.getAbsolutePath());
            int orientation = mExifInterface.getAttributeInt(ExifInterface.TAG_ORIENTATION, 0);

            /***
             * 画像を回転させる
             * 参考:Androidでカメラ撮影し画像を保存する方法 - DRY（日本やアメリカで働くエンジニア日記）
             *      http://d.hatena.ne.jp/ke-16/20110712/1310433427
             *
             * 画像のサイズを画面サイズに合わせる
             * 参考:Android: Bitmapを画面サイズにリサイズする | 自転車で通勤しましょ♪ブログ
             *      http://319ring.net/blog/archives/1504
             */
            Bitmap srcBitmap = MediaStore.Images.Media.getBitmap(resolver, imageUri);
            int srcWidth  = srcBitmap.getWidth();
            int srcHeight = srcBitmap.getHeight();

            // 画像を正しい向きに修正するためにパラメータを設定
            Matrix mMatrix = new Matrix();
            switch(orientation) {
            case 0:
            case 1:
            default:
                mMatrix.postRotate(0);

                break;
            case 3:
                mMatrix.postRotate(180);

                break;
            case 6:
                mMatrix.postRotate(90);

                break;
            case 8:
                mMatrix.postRotate(270);

                break;
            }
            DisplayMetrics metrics = new DisplayMetrics();
            getWindowManager().getDefaultDisplay().getMetrics(metrics);
            float screenWidth  = (float)metrics.widthPixels;
            float screenHeight = (float)metrics.heightPixels;

            float widthScale  = screenWidth / srcHeight;
            float heightScale = screenHeight / srcWidth;

            if(widthScale > heightScale) {
                mMatrix.postScale(heightScale, heightScale);
            }
            else {
                mMatrix.postScale(widthScale, widthScale);
            }

            Bitmap mBitmap = Bitmap.createBitmap(srcBitmap, 0, 0, srcWidth, srcHeight, mMatrix, true);

            ImageView mImageView = (ImageView)findViewById(R.id.image);
            mImageView.setImageBitmap(mBitmap);

            Intent mIntent = new Intent(BlankActivity.this, DialogActivity.class);
            mIntent.setData(imageUri);
            startActivityForResult(mIntent, CaptureAndUploadActivity.REQUEST_INPUT_COMMENT);
        }
        catch(Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        setResult(resultCode, data);

        finish();
    }
}