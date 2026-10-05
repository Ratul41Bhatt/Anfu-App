package com.hjq.demo.utils;

import android.content.Context;
import android.graphics.Bitmap;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.stream.IntStream;

public class PrintUtil {
    public static final int MAX_BIT_WIDTH = 384;

   /* public static byte[] getBitmapData(Bitmap bm) {
        int w = bm.getWidth();
        int h = bm.getHeight();
        if (w > MAX_BIT_WIDTH)
            w = MAX_BIT_WIDTH;
        int bitW = getPaddingBitWidth(w);
        int pitch = bitW / 8;
        byte[] bits = new byte[pitch * h];

        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int color = bm.getPixel(x, y);
                if ((color & 0xFF) < 128) {
                    bits[y * pitch + x / 8] |= (0x80 >> (x % 8));
                }
            }
        }
        return bits;
    }
*/

    /**
     * 优化打印速度
     * @param bm 打印小票内容
     * @return 图片转byte
     */
    public static byte[] getBitmapData(Bitmap bm) {
        final int srcWidth = Math.min(bm.getWidth(), MAX_BIT_WIDTH);
        final int dstWidth = getPaddingBitWidth(srcWidth);
        final int height = bm.getHeight();
        final int pitch = dstWidth / 8;
        final byte[] bits = new byte[pitch * height];

        final int[] pixels = new int[srcWidth * height];
        bm.getPixels(pixels, 0, srcWidth, 0, 0, srcWidth, height);

        IntStream.range(0, height).parallel().forEach(y -> {
            final int rowOffset = y * pitch;
            final int pixelRowStart = y * srcWidth;

            for (int bytePos = 0; bytePos < pitch; bytePos++) {
                final int startPixel = bytePos * 8;
                byte value = 0;

                for (int bitPos = 0; bitPos < 8; bitPos++) {
                    final int x = startPixel + bitPos;
                    if (x >= srcWidth) break;

                    final int color = pixels[pixelRowStart + x];

                    if ((color & 0x000000FF) < 128) {
                        value |= (0x80 >>> bitPos);
                    }
                }
                bits[rowOffset + bytePos] = value;
            }
        });

        return bits;
    }
    public static byte[] getEmptyData(int width, int height) {
        int w = width;
        if (w > MAX_BIT_WIDTH)
            w = MAX_BIT_WIDTH;
        int bitW = getPaddingBitWidth(w);
        int pitch = bitW / 8;
        return new byte[pitch * height];
    }

    public static int getPaddingBitWidth(int width) {
        return ((width + 7) / 8) * 8;
    }

    //小票保存到本地
    public static void saveBitmapToInternalStorage(Context context, Bitmap bitmap) {
        String timeStamp = new SimpleDateFormat("yyyy_MMdd_HHmmss", Locale.getDefault()).format(new Date());
        String fileName = timeStamp + ".png";
        File file = new File(context.getFilesDir(), fileName);
        try (FileOutputStream out = new FileOutputStream(file)) {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out);
            Log.d("SavedFilePath", "Image saved at: " + file.getAbsolutePath());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
