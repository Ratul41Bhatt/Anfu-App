package com.hjq.demo.utils;

import android.graphics.Bitmap;
import android.graphics.Bitmap.Config;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.PorterDuff.Mode;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;
import android.graphics.RectF;
import android.os.Build;
import android.text.TextUtils;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.text.DecimalFormat;
import java.util.Arrays;

/**
 * @ClassName: PictrueUtil
 * @Description: 图片处理类, 包括图片缩放，保存,合并等功能
 */
public class BitmapUtil {
    /**
     * 默认高度
     */
    public static int height = 800;
    /**
     * 默认宽度
     */
    public static int width = 600;
    /**
     * 图片 格式 .jpg
     */
    public static final Bitmap.CompressFormat COMPRESS_FORMAT = Bitmap.CompressFormat.JPEG;
    /**
     * Log Tag
     */
    private static final String TAG = BitmapUtil.class.getName();

    /**
     * 放大或缩小图片。0.5表示缩小一倍，2表示放大一倍
     *
     * @param bm
     * @param scale
     * @return
     */
    public static Bitmap scaleImage(Bitmap bm, float scale) {
        if (bm == null || bm.isRecycled())
            return null;
        // 得到新的图片
        Bitmap newbm = null;
        try {
            int width = bm.getWidth();
            int height = bm.getHeight();
            // 取得想要缩放的matrix参数
            Matrix matrix = new Matrix();
            matrix.postScale(scale, scale);
            newbm = Bitmap.createBitmap(bm, 0, 0, width, height, matrix, true);
            bm = null;
        } catch (Exception e) {
            e.printStackTrace();
        } catch (OutOfMemoryError e) {
            e.printStackTrace();
        }
        return newbm;
    }


    /**
     * 获取高>宽的图片，原图片高<宽，则返回顺时针旋转90/270度的图片，否则返回原图片
     *
     * @param bitmap
     * @return
     */
    public static Bitmap getVerticalImage(Bitmap bitmap, boolean backCamera) {
        if (bitmap == null || bitmap.isRecycled())
            return null;
        Bitmap newBitmap = null;
        try {
            int bmpWidth = bitmap.getWidth();
            int bmpHeight = bitmap.getHeight();
            if (bmpWidth > bmpHeight) {
                newBitmap = rotate(bitmap, backCamera ? 90 : -90);
            } else {
                newBitmap = bitmap;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return newBitmap;
    }

    /**
     * 获取高>宽的图片，原图片高<宽，则返回顺时针旋转90度的图片，否则返回原图片
     *
     * @param bitmap
     * @return
     */
    public static Bitmap getVerticalImage(Bitmap bitmap) {
        if (bitmap == null || bitmap.isRecycled())
            return null;
        Bitmap newBitmap = null;
        try {
            int bmpWidth = bitmap.getWidth();
            int bmpHeight = bitmap.getHeight();
            if (bmpWidth > bmpHeight) {
                newBitmap = rotate(bitmap, 90);
            } else {
                newBitmap = bitmap;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return newBitmap;
    }

    /**
     * 旋转图片
     *
     * @param bitmap
     * @param degree 正数表示向右(顺时针), 负数表示向左(逆时针)
     * @return
     */
    private static Bitmap rotate(Bitmap bitmap, int degree) {
        Bitmap resizeBmp = null;
        try {
            int bmpWidth = bitmap.getWidth();
            int bmpHeight = bitmap.getHeight();
            Matrix matrix = new Matrix();
            matrix.setRotate(degree);
            resizeBmp = Bitmap.createBitmap(bitmap, 0, 0, bmpWidth, bmpHeight, matrix, true);
            bitmap = null;
        } catch (OutOfMemoryError oe) {
            oe.printStackTrace();
        }
        return resizeBmp;
    }

    /**
     * @param bitmapSrc
     * @return Bitmap
     * @throws
     * @Title: zoomImage
     * @Description: 缩放图片，默认 为800*600
     */
    public static Bitmap zoomImage(Bitmap bitmapSrc) {
        Bitmap bitmap = null;

        if (bitmapSrc != null) {
            int bitmapHeight = bitmapSrc.getHeight();
            int bitmapWidth = bitmapSrc.getWidth();

            // 依据设定的图片高宽算出比例
            float balance = getBalance(width, height, bitmapHeight, bitmapWidth);

            // 缩放图片动作
            Matrix matrix = new Matrix();
            matrix.postScale(balance, balance);
            try {
                bitmap = Bitmap.createBitmap(bitmapSrc, 0, 0, bitmapWidth, bitmapHeight, matrix, true);
            } catch (OutOfMemoryError e) {
                e.printStackTrace();
            }

        }
        return bitmap;
    }

    /**
     * @param bitmapSrc
     * @param height    高度
     * @param width     宽度
     * @return
     * @throws
     * @Title: zoomImage
     * @Description: 缩放图片
     */
    public static Bitmap zoomImage(Bitmap bitmapSrc, int height, int width) {
        Bitmap bitmap = null;

        if (bitmapSrc != null) {
            int bitmapHeight = bitmapSrc.getHeight();
            int bitmapWidth = bitmapSrc.getWidth();

            // 依据设定的图片高宽算出比例
            float balance = getBalance(width, height, bitmapHeight, bitmapWidth);

            // 缩放图片动作
            Matrix matrix = new Matrix();
            matrix.postScale(balance, balance);
            try {
                bitmap = Bitmap.createBitmap(bitmapSrc, 0, 0, bitmapWidth, bitmapHeight, matrix, true);
            } catch (OutOfMemoryError e) {
                e.printStackTrace();
            }

            if (bitmapSrc != null) {
                bitmapSrc.recycle();
                bitmapSrc = null;
            }
        }

        return bitmap;
    }

    /**
     * @param bitmapSrc
     * @param height    高度
     * @param width     宽度
     * @return
     * @throws
     * @Title: clipImage
     * @Description: 剪切图片
     */
    public static Bitmap clipImage(Bitmap bitmapSrc, int height, int width) {
        Bitmap bitmap = null;
        if (bitmapSrc != null && !bitmapSrc.isRecycled()) {
            int bitmapHeight = bitmapSrc.getHeight();
            int bitmapWidth = bitmapSrc.getWidth();
            if (bitmapHeight < height) {//如果剪切的图片宽高小于指定剪切的宽高
                height = bitmapHeight;
            }
            if (bitmapWidth < width) {
                width = bitmapWidth;
            }
            try {
                bitmap = Bitmap.createBitmap(bitmapSrc, 0, 0, width, height);
            } catch (OutOfMemoryError e) {
                e.printStackTrace();
            } catch (Exception e) {
                e.printStackTrace();
            }

//			if (bitmapSrc != null) {
//				bitmapSrc.recycle();
//				bitmapSrc = null;
//			}
        }
        return bitmap;
    }

    /**
     * @param bitmapHeight 源图片高度
     * @param bitmapWidth  源图片宽度
     * @return float
     * @throws
     * @Title: getBalance
     * @Description: 缩放比例
     */
    public static float getBalance(int width, int height, float bitmapHeight, float bitmapWidth) {
        // 图片比率
        float balanceHeight = width / bitmapHeight;
        float balanceWidth = height / bitmapWidth;
        return Math.min(balanceHeight, balanceWidth);
    }

    /**
     * @param bitmap Bitmap
     * @param path   path+filename
     * @return
     * @throws
     * @Title: saveBitmap
     * @Description: 保存图片文件 到指定目录
     */
    public static boolean saveBitmap(Bitmap bitmap, String path) {
        boolean result = false;

        if (bitmap != null && !TextUtils.isEmpty(path)) {
            // 创建文件
            File file = createFile(path);
            if (file != null) {
                BufferedOutputStream bos = null;
                try {
                    bos = new BufferedOutputStream(new FileOutputStream(file));
                    /* 采用压缩转档方法 */
                    bitmap.compress(COMPRESS_FORMAT, 100, bos);
                    bos.flush();
                    result = true;
                } catch (Exception e) {
                    e.printStackTrace();
                } finally {
                    if (bos != null) {
                        try {
                            bos.close();
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                    if (bitmap != null) {
                        bitmap.recycle();
                        bitmap = null;
                    }
                }
            }
        }

        return result;
    }

    /**
     * @param bitmap_1
     * @param bitmap_2
     * @return Bitmap
     * @throws
     * @Title: composeBitmap
     * @Description: 合并图片
     */
    public static Bitmap composeBitmap(Bitmap bitmap_1, Bitmap bitmap_2) {
        // 产生横向图片;
        Bitmap bitmap = Bitmap.createBitmap(height, width, bitmap_1.getConfig());

        if (bitmap_1 != null && bitmap_2 != null) {
            Canvas canvas = new Canvas(bitmap);

            canvas.drawBitmap(bitmap_1, (height / 2 - bitmap_1.getWidth()) / 2, (width - bitmap_1.getHeight()) / 2, null);
            canvas.drawBitmap(bitmap_2, (height * 3 / 2 - bitmap_2.getWidth()) / 2, (width - bitmap_2.getHeight()) / 2, null);
            // 保存
//            canvas.save(Canvas.ALL_SAVE_FLAG);
            // 存储
            canvas.restore();
            canvas = null;
        }

        return bitmap;
    }

    /**
     * @param path 路径
     * @return File
     * @throws
     * @Title: createFile
     * @Description: 创建目录和文件
     */
    public static File createFile(String path) {
        File file = new File(path);
        // 判断目录是否存在
        if (!file.getParentFile().exists()) {
            if (file.getParentFile().mkdirs()) {
                try {
                    boolean ls = file.createNewFile();
                    if (!ls) {
                        return null;
                    }
                    return file;
                } catch (IOException e) {
                    e.printStackTrace();
                }
            } else {// 目录创建失败
                return null;
            }
        } else {
            try {
                // 删除原先的文件
                if (file.exists()) {
                    file.delete();
                }
                // 创建文件
                boolean ls = file.createNewFile();
                if (!ls) {
                    return null;
                }
                return file;
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return null;
    }

    /**
     * bitmap转换为圆角
     *
     * @param bitmap
     * @return
     */
    public static Bitmap toRoundCorner(Bitmap bitmap) {
        return toRoundCorner(bitmap, 10);
    }

    public static BitmapFactory.Options getScaleBitmapFactoryOption(float scale) {
        BitmapFactory.Options option = new BitmapFactory.Options();
        option.inSampleSize = (int) (1 / scale);
        option.inPurgeable = true;
        option.inInputShareable = true;
        return option;
    }

    /**
     * bitmap转换为圆角 服务端统计的崩溃日志，有相当多的trying to use a recycled
     * bitmap，从这个方法抛出，因此加了异常捕获
     *
     * @param bitmap
     * @param pixels
     * @return
     */
    public static Bitmap toRoundCorner(Bitmap bitmap, int pixels) {
        if (bitmap == null || bitmap.isRecycled())
            return null;
        Bitmap output = null;

        try {
            output = Bitmap.createBitmap(bitmap.getWidth(), bitmap.getHeight(), Config.ARGB_8888);
            Canvas canvas = new Canvas(output);

            final int color = 0xff424242;
            final Paint paint = new Paint();
            final Rect rect = new Rect(0, 0, bitmap.getWidth(), bitmap.getHeight());
            final RectF rectF = new RectF(rect);
            final float roundPx = pixels;

            paint.setAntiAlias(true);
            canvas.drawARGB(0, 0, 0, 0);
            paint.setColor(color);
            canvas.drawRoundRect(rectF, roundPx, roundPx, paint);

            paint.setXfermode(new PorterDuffXfermode(Mode.SRC_IN));
            canvas.drawBitmap(bitmap, rect, rect, paint);
            bitmap.recycle();
            canvas = null;
        } catch (OutOfMemoryError e) {
            e.printStackTrace();
        }
        return output;
    }


    public static Bitmap getBitmapFromDisk(String path, int width, int height) {
        try {
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inJustDecodeBounds = true;
            // 获取这个图片的宽和高
            Bitmap bitmap = BitmapFactory.decodeFile(path, options); //此时返回bm为空
            options.inJustDecodeBounds = false;
            //计算缩放比
            float widthBe = options.outWidth / width;
            float heightBe = options.outHeight / height;
            float balance = widthBe > heightBe ? heightBe : widthBe;
            if (balance <= 0)
                balance = 1;
            options.inSampleSize = (int) balance;
            //重新读入图片，注意这次要把options.inJustDecodeBounds 设为 false哦
            bitmap = BitmapFactory.decodeFile(path, options);
            return bitmap;
        } catch (OutOfMemoryError e) {
            e.printStackTrace();
        }
        return null;
    }

    /**
     * 根据tplBitmap提供的模板形状处理bitmap图片
     *
     * @param bitmap
     * @param tplBitmap
     * @return
     */
    public static Bitmap toTplBitmap(Bitmap bitmap, Bitmap tplBitmap) {
        if (bitmap == null || bitmap.isRecycled() || tplBitmap == null || tplBitmap.isRecycled())
            return null;
        Bitmap output = null;

        try {
            output = Bitmap.createBitmap(bitmap.getWidth(), bitmap.getHeight(), Config.ARGB_8888);
            Canvas canvas = new Canvas(output);

            final int color = 0xff424242;
            final Paint paint = new Paint();
            final Rect rect = new Rect(0, 0, bitmap.getWidth(), bitmap.getHeight());

            paint.setAntiAlias(true);
            canvas.drawARGB(0, 0, 0, 0);
            paint.setColor(color);
            canvas.drawBitmap(tplBitmap, new Rect(0, 0, tplBitmap.getWidth(), tplBitmap.getHeight()), rect, paint);
            paint.setXfermode(new PorterDuffXfermode(Mode.SRC_IN));
            canvas.drawBitmap(bitmap, rect, rect, paint);
            bitmap.recycle();
            canvas = null;
        } catch (OutOfMemoryError e) {
            e.printStackTrace();
        }
        return output;
    }

    public static Bitmap createMyBitmap(byte[] data, int width, int height) {
        int[] colors = convertByteToColor(data);
        if (colors == null) {
            return null;
        }

        Bitmap bmp = Bitmap.createBitmap(colors, 0, width, width, height,
                Config.ARGB_8888);

        Matrix ma = new Matrix();
        ma.postRotate(180);//180度旋转
        //int width = bmp.getWidth();

        Bitmap newbmp = Bitmap.createBitmap(bmp, 0, 0, bmp.getWidth(), bmp.getHeight(), ma, true);

        return newbmp;
    }

    // 将一个byte数转成int
    // 实现这个函数的目的是为了将byte数当成无符号的变量去转化成int
    public static int convertByteToInt(byte data) {

        int heightBit = (int) ((data >> 4) & 0x0F);
        int lowBit = (int) (0x0F & data);
        return heightBit * 16 + lowBit;
    }

    // 将纯RGB数据数组转化成int像素数组
    public static int[] convertByteToColor(byte[] data) {
        int size = data.length;
        if (size == 0) {
            return null;
        }

        int arg = 0;
        if (size % 3 != 0) {
            arg = 1;
        }

        // 一般情况下data数组的长度应该是3的倍数，这里做个兼容，多余的RGB数据用黑色0XFF000000填充
        int[] color = new int[size / 3 + arg];
        int red, green, blue;

        if (arg == 0) {
            for (int i = 0; i < color.length; ++i) {
                red = convertByteToInt(data[i * 3]);
                green = convertByteToInt(data[i * 3 + 1]);
                blue = convertByteToInt(data[i * 3 + 2]);

                // 获取RGB分量值通过按位或生成int的像素值
                color[i] = (red << 16) | (green << 8) | blue | 0xFF000000;
            }
        } else {
            for (int i = 0; i < color.length - 1; ++i) {
                red = convertByteToInt(data[i * 3]);
                green = convertByteToInt(data[i * 3 + 1]);
                blue = convertByteToInt(data[i * 3 + 2]);
                color[i] = (red << 16) | (green << 8) | blue | 0xFF000000;
            }

            color[color.length - 1] = 0xFF000000;
        }

        return color;
    }

    public static byte[] getBitmapData(Bitmap bmp) {
        int bytes = bmp.getByteCount();

        ByteBuffer buf = ByteBuffer.allocate(bytes);
        bmp.copyPixelsToBuffer(buf);

        byte[] byteArray = buf.array();
        return byteArray;
    }

    public static Bitmap decodeSampledBitmapFromFile(String pathName, int reqWidth, int reqHeight) {
        final BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(pathName, options);
        options.inSampleSize = calculateInSampleSize(options, reqWidth, reqHeight);
        options.inJustDecodeBounds = false;
        Bitmap src = BitmapFactory.decodeFile(pathName, options);
        return createScaleBitmap(src, reqWidth, reqHeight);
    }

    /**
     * 缩放图片
     *
     * @param src
     * @param dstWidth
     * @param dstHeight
     * @return
     */
    public static Bitmap createScaleBitmap(Bitmap src, int dstWidth, int dstHeight) {
        if(dstHeight <= 0 || dstWidth <= 0) return null;
        /*如果是放大图片，filter决定是否平滑，如果是缩小图片，filter无影响，我们这里是缩小图片，所以直接设置为false*/
        Bitmap dst = Bitmap.createScaledBitmap(src, dstWidth, dstHeight, false);
        if (src != dst) { // 如果没有缩放，那么不回收
            src.recycle(); // 释放Bitmap的native像素数组
        }

        return dst;
    }

    /**
     * 计算图片压缩比例（采样率）
     * 通常如果图片是针对某种分辨率设计的，直接decode图片不会有什么问题。
     * 但是，如果图片不是特定设计，且比较大的话，容易造成OOM。
     * 假设，一个ImageView大小为512*384，有一张2048*1536的图片需要显示，
     * 那么如果加载整张图片，那就造成了浪费，这时候就需要采用不同的采样率对原图片进行一定的压缩。
     *
     * @param options
     * @param reqWidth
     * @param reqHeight
     * @return
     */
    public static int calculateInSampleSize(BitmapFactory.Options options, int reqWidth, int reqHeight) {
        /*源图片的高度和宽度*/
        final int height = options.outHeight;
        final int width = options.outWidth;
        int inSampleSize = 1;
        if (height > reqHeight || width > reqWidth) {
            /*计算出实际宽高和目标宽高的比率*/
            final int halfHeight = height / 2;
            final int halfWidth = width / 2;
            while ((halfHeight / inSampleSize) > reqHeight && (halfWidth / inSampleSize) > reqWidth) {
                inSampleSize *= 2;
            }
        }
        return inSampleSize;
    }

    /**
     * 纵向拼接两张bitmap
     * <功能详细描述>
     * @param first
     * @param second
     * @param isfinal
     * @return
     */
    public static Bitmap addBitmap(Bitmap first, Bitmap second, boolean isfinal) {
        if(first == null || second == null)
            return null;

        int width = Math.max(first.getWidth(),second.getWidth());
        //int height = first.getHeight() + second.getHeight() + 180;
        int height = first.getHeight() + second.getHeight() + (isfinal==true ? 180:0);
        Bitmap result = Bitmap.createBitmap(width, height, Config.ARGB_8888);
        Canvas canvas = new Canvas(result);
        canvas.drawColor(Color.WHITE);
        canvas.drawBitmap(first, 0, 0, null);
        //为了让手写签名图片在打印纸上横向居中，left向右偏移42(second的宽度是300)，
        //合成后的bitmap宽度是384，所以中心点在384/2 = 192。
        canvas.drawBitmap(second,(width/2 - second.getWidth()/2), first.getHeight(),  null);
        return result;
    }

    public static Bitmap setBitmapBackgroundColor(Bitmap bmp, int color){
        //获取Bitmap的宽高
        int bitmapWidth = bmp.getWidth();
        int bitmapHeight = bmp.getHeight();

        //创建像素数组，长度为宽度*高度
        int[] pixels = new int[bitmapWidth * bitmapHeight];
        //设置所有像素值的颜色
        Arrays.fill(pixels,color);
        //将像素数组应用到bitmap
        bmp.setPixels(pixels,0,bitmapWidth,0,0,bitmapWidth,bitmapHeight);
        return bmp;
    }


}
