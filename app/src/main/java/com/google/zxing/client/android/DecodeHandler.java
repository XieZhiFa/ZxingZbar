/*
 * Copyright (C) 2010 ZXing authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.zxing.client.android;

import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Rect;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.preference.PreferenceManager;
import android.text.TextUtils;
import android.util.Log;

import com.google.zxing.BinaryBitmap;
import com.google.zxing.DecodeHintType;
import com.google.zxing.MultiFormatReader;
import com.google.zxing.PlanarYUVLuminanceSource;
import com.google.zxing.ReaderException;
import com.google.zxing.Result;
import com.google.zxing.common.HybridBinarizer;

import net.sourceforge.zbar.Image;
import net.sourceforge.zbar.ImageScanner;
import net.sourceforge.zbar.Symbol;
import net.sourceforge.zbar.SymbolSet;

import java.io.ByteArrayOutputStream;
import java.util.Map;

final class DecodeHandler extends Handler {

    private static final String TAG = DecodeHandler.class.getSimpleName();

    private final CaptureActivity activity;
    private final MultiFormatReader multiFormatReader;
    private boolean running = true;

    DecodeHandler(CaptureActivity activity, Map<DecodeHintType, Object> hints) {
        multiFormatReader = new MultiFormatReader();
        multiFormatReader.setHints(hints);
        this.activity = activity;
    }

    @Override
    public void handleMessage(Message message) {
        if (message == null || !running) {
            return;
        }
        switch (message.what) {
            case R.id.decode:
                decode((byte[]) message.obj, message.arg1, message.arg2);
                break;
            case R.id.quit:
                running = false;
                Looper.myLooper().quit();
                break;
        }
    }

    /**
     * Decode the data within the viewfinder rectangle, and time how long it took. For efficiency,
     * reuse the same reader objects from one decode to the next.
     *
     * @param data   The YUV preview frame.
     * @param width  The width of the preview frame.
     * @param height The height of the preview frame.
     */
    private void decode(byte[] data, int width, int height) {
        long start = System.currentTimeMillis();


        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(activity);
        String resultQRcode = null;

        if (DecoderMode.readPref(prefs) == DecoderMode.Zxing) { //如果扫描模式是Zxing
//            // --- add java进行数组的转换 速度很慢
//            byte[] rotatedData = new byte[data.length];
//            for (int y = 0; y < height; y++) {
//                for (int x = 0; x < width; x++)
//                    rotatedData[x * height + height - y - 1] = data[x + y * width];
//            }
//            int tmp = width;
//            width = height;
//            height = tmp;
//            data = rotatedData;
//            Log.d(TAG, "数组转换用时: " + (System.currentTimeMillis() - start));
//            //--- end


            /*
              因为相机传感器捕获的数据是横向的, 所以需要将数据进行90度的旋转, 用java进行转换在红米三手机测试大概需要 600ms左右
              因此换了C语言, 只需要 35ms左右 速度快了接近 20倍
             */
            data = DecodeHandlerJni.dataHandler(data, data.length, width, height);
            //Log.d(TAG, "数组转换用时: " + (System.currentTimeMillis() - start));
            int tmp = width;
            width = height;
            height = tmp;

            Result rawResult = null;
            PlanarYUVLuminanceSource source = activity.getCameraManager().buildLuminanceSource(data, width, height);
            if (source != null) {
                BinaryBitmap bitmap = new BinaryBitmap(new HybridBinarizer(source));
                try {
                    rawResult = multiFormatReader.decodeWithState(bitmap);
                } catch (ReaderException re) {
                    // continue
                } finally {
                    multiFormatReader.reset();
                }
            }
            if (rawResult != null) {
                resultQRcode = rawResult.getText();
            }

        } else {
            Image barcode = new Image(width, height, "Y800");
            barcode.setData(data);
            Rect rect = activity.getCameraManager().getFramingRectInPreview();
            if (rect != null) {
                /*
                    zbar 解码库,不需要将数据进行旋转,因此设置裁剪区域是的x为 top, y为left
                    设置了裁剪区域,解码速度快了近5倍左右
                 */
                barcode.setCrop(rect.top, rect.left, rect.width(), rect.height());    // 设置截取区域，也就是你的扫描框在图片上的区域.
            }
            ImageScanner mImageScanner = new ImageScanner();
            int result = mImageScanner.scanImage(barcode);
            if (result != 0) {
                SymbolSet symSet = mImageScanner.getResults();
                for (Symbol sym : symSet)
                    resultQRcode = sym.getData();
            }
        }

        long end = System.currentTimeMillis();
        Log.d(TAG, "Found barcode in " + (end - start) + " ms");


        Handler handler = activity.getHandler();
        if (!TextUtils.isEmpty(resultQRcode)) {   // 非空表示识别出结果了。
            if (handler != null) {
                Log.d(TAG, "解码成功: " + resultQRcode);
                Message message = Message.obtain(handler, R.id.decode_succeeded, resultQRcode);
                message.sendToTarget();
            }
        } else {
            if (handler != null) {
                Message message = Message.obtain(handler, R.id.decode_failed);
                message.sendToTarget();
            }
        }

    }

    private static void bundleThumbnail(PlanarYUVLuminanceSource source, Bundle bundle) {
        int[] pixels = source.renderThumbnail();
        int width = source.getThumbnailWidth();
        int height = source.getThumbnailHeight();
        Bitmap bitmap = Bitmap.createBitmap(pixels, 0, width, width, height, Bitmap.Config.ARGB_8888);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        bitmap.compress(Bitmap.CompressFormat.JPEG, 50, out);
        bundle.putByteArray(DecodeThread.BARCODE_BITMAP, out.toByteArray());
        bundle.putFloat(DecodeThread.BARCODE_SCALED_FACTOR, (float) width / source.getWidth());
    }

}
