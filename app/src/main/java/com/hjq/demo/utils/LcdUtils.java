package com.hjq.demo.utils;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.os.Environment;
import android.os.RemoteException;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.util.Log;

import com.hjq.demo.entity.LcdInfo;
import com.huawei.hms.mlsdk.common.BitmapUtils;
import com.szanfu.sdk.api.system.ISystemBinderService;
import com.xuhao.didi.core.iocore.AbsReader;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.Arrays;
import java.util.Locale;

public class LcdUtils {

    private static final String TAG = "LcdUtils";
    private static LcdUtils instance;
    private ISystemBinderService mSystemBinderService;

    /**
     * 私有构造方法，防止外部实例化
     */
    private LcdUtils() {
    }


    /**
     * 获取单例实例
     */
    public static LcdUtils getInstance() {
        if (instance == null) {
            synchronized (LcdUtils.class) {
                if (instance == null) {
                    instance = new LcdUtils();
                }
            }
        }
        return instance;
    }

    /**
     * 初始化 Service
     */
    public void init(ISystemBinderService service) {
        this.mSystemBinderService = service;
    }

    /**
     * 初始化 LCD 屏幕
     *
     * @return int 初始化结果，0 表示成功，其他值表示失败
     */
    public int lcdInit() {
        if (mSystemBinderService == null) {
            Log.e(TAG, "lcdInit: Service is null");
            return -1; // 返回 -1 表示未绑定服务
        }

        try {
            int result = mSystemBinderService.lcdInit();
            Log.e(TAG, "lcdInit Result: " + result);
            return result;
        } catch (RemoteException e) {
            e.printStackTrace();
            Log.e(TAG, "lcdInit Error: " + e.getMessage());
            return -2; // 返回 -2 表示 RemoteException 异常
        }
    }

    private LcdInfo cachedLcdInfo = null;

    /**
     * 获取 LCD 屏幕属性信息
     *
     * @return LcdInfo 对象，包含宽度、高度、类型及描述信息；若获取失败则返回 null
     */
    public LcdInfo lcdGetDevLcdStatus() {
        Log.e(TAG, "lcdGetDevLcdStatus: ");

        // 如果缓存数据存在，直接返回
        if (cachedLcdInfo != null) {
            Log.e(TAG, "Returning cached LcdInfo: " + cachedLcdInfo.toString());
            return cachedLcdInfo;
        }

        if (mSystemBinderService == null) return null;

        if (lcdInit() != 0) {
            return null;
        }

        try {
            int[] pixelWidthMax = new int[1];
            int[] pixelHeightMax = new int[1];
            int[] lcdType = new int[1];

            int result = mSystemBinderService.lcdGetDevLcdStatus(pixelWidthMax, pixelHeightMax, lcdType);
            Log.e(TAG, "lcdGetDevLcdStatus: result-->" + result);

            if (result == 0) {
                int width = pixelWidthMax[0];
                int height = pixelHeightMax[0];
                int type = lcdType[0];

                String typeDescription;
                switch (type) {
                    case 1:
                        ////width=128, height=32, type=1
                        typeDescription = "LCD_TYPE_BLACK_WRITE_ST7539";
                        break;
                    case 2:
                        //Width: 320, Height: 170, Type: 2
                        typeDescription = "LCD_TYPE_COLOR_SCREEN_JD9853";
                        break;
                    default:
                        typeDescription = "Unknown LCD Type";
                        break;
                }

                cachedLcdInfo = new LcdInfo(width, height, type, typeDescription);
                Log.e(TAG, "LcdInfo: " + cachedLcdInfo.toString());
                return cachedLcdInfo;
            } else {
                Log.e(TAG, "lcdGetDevLcdStatus Error: " + result);
                return null;
            }

        } catch (RemoteException e) {
            e.printStackTrace();
            Log.e(TAG, "lcdGetDevLcdStatus Error: " + e.getMessage());
        }

        return null;
    }


    /**
     * 显示字符串
     */
    public void lcdDisplayString(String text, int fontSize, int xPos, int yPos) {
        if (mSystemBinderService == null) return;
        try {
            int result = mSystemBinderService.lcdDisplayString(xPos, yPos, text, fontSize, 0xFFFF, 0x0000);
            if (result != 0) {
                Log.e(TAG, "lcdDisplayString Error: " + result);
            }
        } catch (RemoteException e) {
            e.printStackTrace();
            Log.e(TAG, "lcdDisplayString Error: " + e.getMessage());
        }
    }

    /**
     * 清除屏幕
     */
    public void lcdClearScreen() {
        try {
            int fontColor = (lcdGetDevLcdStatus().getType() == 1) ? 0x0000 : 0xFFFF;  // 黑白屏：白字，彩屏：黑字
            int result = mSystemBinderService.lcdClearScreen(fontColor);
            Log.e(TAG, "lcdClearScreen Result: " + result);
        } catch (RemoteException e) {
            e.printStackTrace();
            Log.e(TAG, e.getMessage());
        }
    }

    /**
     * 显示交易金额，格式：$0000123.45
     */
    public void displayTransactionAmount(double amount) {
        String formattedAmount = String.format("$%08.2f", amount);
        lcdClearScreen();
        lcdDisplayString(formattedAmount, 24, 5, 10);
    }

    /**
     * 绘制图片
     */
    public void lcdDrawPicture(Bitmap bitmap) {
        if (mSystemBinderService == null) return;
        try {
            byte[] bmpData = PrintUtil.getBitmapData(bitmap);
            int result = mSystemBinderService.lcdDrawPicture(0, 0, bmpData, bmpData.length, bitmap.getWidth(), bitmap.getHeight());
            if (result != 0) {
                Log.e(TAG, "lcdDrawPicture Error: " + result);
            }
        } catch (RemoteException e) {
            e.printStackTrace();
            Log.e(TAG, "lcdDrawPicture Error: " + e.getMessage());
        }
    }

    /**
     * 刷新屏幕
     */
    public void lcdRefreshScreen() {
        if (mSystemBinderService == null) return;
        try {
            int result = mSystemBinderService.lcdRefreshScreen();
            if (result != 0) {
                Log.e(TAG, "lcdRefreshScreen Error: " + result);
            }
        } catch (RemoteException e) {
            e.printStackTrace();
            Log.e(TAG, "lcdRefreshScreen Error: " + e.getMessage());
        }
    }

    /**
     * 创建 Bitmap，用于显示文本
     */
    public Bitmap createTextBitmapX(String text, int width, int height, int textSize) {

//        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565);
        Canvas canvas = new Canvas(bitmap);
        canvas.drawColor(Color.WHITE);

        TextPaint textPaint = new TextPaint();
        textPaint.setColor(Color.BLACK);
//        textPaint.setTextSize(textSize);
        textPaint.setTextSize(calculateOptimalFontSize(text, width, height));
        textPaint.setAntiAlias(true);

        StaticLayout sl = new StaticLayout(text, textPaint, width, Layout.Alignment.ALIGN_CENTER, 1.0f, 0.0f, false);
        canvas.translate(0, (height - sl.getHeight()) / 2);
        sl.draw(canvas);

        return bitmap;
    }

    /**
     * 生成文本图片
     *
     * @param text            显示内容
     * @param controlTextSize 是否控制字体大小
     * @param textSize        显示文本大小
     * @return 文本图片
     */
    public Bitmap createTextBitmap(String text, boolean controlTextSize, int textSize) {
        LcdInfo lcdInfo = lcdGetDevLcdStatus();
        if (lcdInfo == null) {
            Log.i(TAG, "createTextBitmap: Error.....");
            return null;
        }
        Log.i(TAG, "lcdInfo: " + lcdInfo.toString());
        int width = lcdInfo.getWidth();
        int height = lcdInfo.getHeight();
        Bitmap bitmap = null;
        switch (lcdInfo.getType()) {
            case 1:
                bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
                break;
            case 2:
                bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565);
                break;
        }
        Canvas canvas = new Canvas(bitmap);
        canvas.drawColor(Color.WHITE);

        TextPaint textPaint = new TextPaint();
        textPaint.setColor(Color.BLACK);

        if (controlTextSize) {
            textPaint.setTextSize(textSize);
        } else {
            textPaint.setTextSize(calculateOptimalFontSize(text, width, height));
        }
        textPaint.setAntiAlias(true);

        if (lcdInfo.getType() == 2) {
            textPaint.setFakeBoldText(true); // 加粗
            // 设置字体
            Typeface typeface = Typeface.create("sans-serif-medium", Typeface.BOLD);
            textPaint.setTypeface(typeface);
            // 添加阴影效果（偏移量：2px，模糊半径：3px）
            textPaint.setShadowLayer(3f, 2f, 2f, Color.GRAY);
        }


        // 文字对齐 - 居中
        StaticLayout sl = new StaticLayout(text, textPaint, width, Layout.Alignment.ALIGN_CENTER, 1.0f, 0.0f, false);
        // 水平居中，垂直居中
        float translateY = (height - sl.getHeight()) / 2f;
        canvas.translate(0, translateY);
        sl.draw(canvas);

        return bitmap;
    }

    /**
     * 根据屏幕宽高和文本长度计算最佳字体大小（仅限 12, 16, 24, 48, 64）
     *
     * @param text         文本内容
     * @param screenWidth  屏幕宽度
     * @param screenHeight 屏幕高度
     * @return 最佳字体大小
     */
    private int calculateOptimalFontSize(String text, int screenWidth, int screenHeight) {
//        int[] allowedFontSizes = {64, 48, 24, 16, 12};
        int[] allowedFontSizes = {16, 24, 48, 64};

        for (int fontSize : allowedFontSizes) {
            int charWidth = fontSize / 2; // 计算字符宽度，假设宽度为字体大小的一半
            int totalWidth = text.length() * charWidth;
            int totalHeight = fontSize;

            if (totalWidth <= screenWidth && totalHeight <= screenHeight) {
                Log.i(TAG, "Selected Font Size: " + fontSize);
                return fontSize;
            }
        }

        // 如果所有尺寸均不满足，选择最小字体 12
        Log.e(TAG, "Text too large, fallback to minimum font size 12");
        return 12;
    }


    /**
     * 将 Bitmap 保存为 RGB565 格式的 BMP 文件
     *
     * @param bitmap   要保存的 Bitmap 对象 (RGB_565 格式)
     * @param filePath 保存路径
     */
    public void saveBitmapAsBmpFile(Bitmap bitmap, String filePath) {
        try {
            int width = bitmap.getWidth();
            int height = bitmap.getHeight();
            int bytesPerPixel = 2; // RGB565 每像素 2 字节
            int bmpDataSize = width * height * bytesPerPixel;
            int fileSize = 54 + bmpDataSize; // 文件头大小 + 数据大小

            FileOutputStream fos = new FileOutputStream(filePath);
            DataOutputStream dos = new DataOutputStream(fos);

            // BMP 文件头 (14 bytes)
            dos.writeShort(0x4D42); // "BM"
            dos.writeInt(Integer.reverseBytes(fileSize)); // 文件大小
            dos.writeShort(0); // 保留字段1
            dos.writeShort(0); // 保留字段2
            dos.writeInt(Integer.reverseBytes(54)); // 像素数据起始位置

            // DIB Header (40 bytes)
            dos.writeInt(Integer.reverseBytes(40)); // DIB Header 大小
            dos.writeInt(Integer.reverseBytes(width)); // 宽度
            dos.writeInt(Integer.reverseBytes(height)); // 高度 (倒置存储)
            dos.writeShort(Short.reverseBytes((short) 1)); // 颜色平面数 (1)
            dos.writeShort(Short.reverseBytes((short) 16)); // 每像素 16 位 (RGB565)
            dos.writeInt(0); // 无压缩
            dos.writeInt(Integer.reverseBytes(bmpDataSize)); // 图像数据大小
            dos.writeInt(0); // 水平分辨率 (像素/米)
            dos.writeInt(0); // 垂直分辨率 (像素/米)
            dos.writeInt(0); // 调色板颜色数
            dos.writeInt(0); // 重要颜色数

            // 写入像素数据 (RGB565 格式，倒序存储)
            ByteBuffer buffer = ByteBuffer.allocate(bmpDataSize);
            bitmap.copyPixelsToBuffer(buffer);
            byte[] pixelData = buffer.array();

            for (int y = height - 1; y >= 0; y--) {
                for (int x = 0; x < width; x++) {
                    int index = (y * width + x) * bytesPerPixel;
                    byte lowByte = pixelData[index];
                    byte highByte = pixelData[index + 1];
                    dos.writeByte(lowByte);   // 低字节
                    dos.writeByte(highByte);  // 高字节
                }
            }

            dos.close();
            fos.close();
            Log.i(TAG, "BMP file saved: " + filePath);

        } catch (IOException e) {
            e.printStackTrace();
            Log.e(TAG, "Error saving BMP: " + e.getMessage());
        }
    }


    /**
     * 在彩屏设备上显示 BMP 文件
     *
     * @param filePath BMP 文件路径
     */
    public void displayBmpOnLcd(String filePath) {
        Log.i(TAG, "displayBmpOnLcd:filePath " + filePath);
        try {
            FileInputStream fis = new FileInputStream(filePath);
            ByteArrayOutputStream bos = new ByteArrayOutputStream();

            byte[] buffer = new byte[1024];
            int bytesRead;

            while ((bytesRead = fis.read(buffer)) != -1) {
                bos.write(buffer, 0, bytesRead);
            }

            byte[] bmpData = bos.toByteArray();
            fis.close();
            bos.close();

            // 读取 BMP 文件头信息
            int width = ((bmpData[21] & 0xFF) << 24) | ((bmpData[20] & 0xFF) << 16) |
                    ((bmpData[19] & 0xFF) << 8) | (bmpData[18] & 0xFF);

            int height = ((bmpData[25] & 0xFF) << 24) | ((bmpData[24] & 0xFF) << 16) |
                    ((bmpData[23] & 0xFF) << 8) | (bmpData[22] & 0xFF);

            int offset = ((bmpData[13] & 0xFF) << 24) | ((bmpData[12] & 0xFF) << 16) |
                    ((bmpData[11] & 0xFF) << 8) | (bmpData[10] & 0xFF);

            // 截取像素数据
            byte[] pixelData = Arrays.copyOfRange(bmpData, offset, bmpData.length);

            // 翻转像素数据
            int bytesPerPixel = 2; // RGB565 每像素 2 字节
            byte[] flippedData = new byte[pixelData.length];

            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    int srcIndex = (y * width + x) * bytesPerPixel;
                    int destIndex = ((height - y - 1) * width + x) * bytesPerPixel;

                    // 交换像素数据
                    flippedData[destIndex] = pixelData[srcIndex];
                    flippedData[destIndex + 1] = pixelData[srcIndex + 1];
                }
            }

            // 调用设备 API 显示图片
            int result = mSystemBinderService.lcdDrawPicture(0, 0, flippedData, flippedData.length, width, height);
            Log.e(TAG, "Display BMP Result: " + result);

        } catch (IOException | RemoteException e) {
            e.printStackTrace();
            Log.e(TAG, "Error displaying BMP: " + e.getMessage());
        }
    }


    /**
     * 显示 BMP 图片，如果图片不存在则创建并保存
     *
     * @param context  上下文
     * @param fileName BMP 文件名（不包含路径，如 "transaction_in.bmp"）
     * @param text     显示文本
     * @param textSize 文本字体大小
     */
    public void displayBmp(Context context, String fileName, String text, int textSize) {
        String bmpPath = context.getFilesDir() + "/" + fileName;
        File bmpFile = new File(bmpPath);

        if (!bmpFile.exists()) {
            Log.i(TAG, "BMP file not found, creating new BMP file: " + bmpPath);
            Bitmap bitmapColor = createTextBitmap(text, true, textSize);
            saveBitmapAsBmpFile(bitmapColor, bmpPath);
        } else {
            Log.i(TAG, "BMP file already exists, using existing file: " + bmpPath);
        }
        // 显示 BMP 文件
        displayBmpOnLcd(bmpPath);
    }


    /**
     * 输入金额跟随系统语言切换
     *
     * @param context  上下文
     * @param fileName BMP 文件名（不包含路径，如 "transaction_in.bmp"）
     * @param text     显示文本
     * @param textSize 文本字体大小
     */
    public void displayInputAmountsBmp(Context context, String fileName, String text, int textSize) {
        // 1) 解析语言后缀：中文=zh，其它= en（默认英文）
        String lang = resolveLang(context); // "zh" or "en"

        // 2) 在文件名里附加语言后缀：xxx.bmp -> xxx_zh.bmp / xxx_en.bmp
        String localizedName = appendLangSuffix(fileName, lang);

        // 3) 也可以选择按语言分目录存放（更整洁）
        File dir = new File(context.getFilesDir(), "lcd/" + lang);
        if (!dir.exists()) dir.mkdirs();

        String bmpPath = new File(dir, localizedName).getAbsolutePath();
        File bmpFile = new File(bmpPath);

        if (!bmpFile.exists()) {
            Log.i(TAG, "BMP not found, creating: " + bmpPath);
            Bitmap bitmapColor = createTextBitmap(text, true, textSize);
            PrintUtil.saveBitmapToInternalStorage(context, bitmapColor);
            saveBitmapAsBmpFile(bitmapColor, bmpPath);
        } else {
            Log.i(TAG, "BMP exists, using: " + bmpPath);
        }

        // 4) 显示对应语言的 BMP
        displayBmpOnLcd(bmpPath);
    }

    /**
     * 解析当前系统语言，中文→"zh"，其他→"en"（默认英文）
     */
    private String resolveLang(Context context) {
        Locale locale;
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
            locale = context.getResources().getConfiguration().getLocales().get(0);
        } else {
            locale = context.getResources().getConfiguration().locale;
        }
        if (locale != null && "zh".equalsIgnoreCase(locale.getLanguage())) {
            return "zh";
        }
        return "en";
    }

    /**
     * 给文件名加语言后缀：xxx.bmp → xxx_{lang}.bmp
     */
    private String appendLangSuffix(String fileName, String lang) {
        int dot = fileName.lastIndexOf('.');
        if (dot > 0) {
            String name = fileName.substring(0, dot);
            String ext = fileName.substring(dot); // ".bmp"
            return name + "_" + lang + ext;
        }
        return fileName + "_" + lang;
    }


    /**
     * 从 raw 目录中读取 BMP 文件
     *
     * @param context 上下文
     * @param resId   资源 ID（如 R.raw.color_screen）
     * @return BMP 文件的字节数组
     */
    public byte[] readBmpFromRaw(Context context, int resId) {
        Log.e(TAG, "readBmpFromRaw: ");
        try {
            InputStream inputStream = context.getResources().openRawResource(resId);
            ByteArrayOutputStream bos = new ByteArrayOutputStream();

            byte[] buffer = new byte[1024];
            int bytesRead;
            while ((bytesRead = inputStream.read(buffer)) != -1) {
                bos.write(buffer, 0, bytesRead);
            }

            inputStream.close();
            return bos.toByteArray();

        } catch (IOException e) {
            e.printStackTrace();
            Log.e(TAG, "Error reading BMP from raw: " + e.getMessage());
            return null;
        }
    }

    /**
     * 将 raw 资源文件复制到内部存储，并返回路径
     *
     * @param context  上下文
     * @param rawResId raw 资源 ID，例如 R.raw.color_screen
     * @param fileName 保存到内部存储的文件名，例如 "color_screen.bmp"
     * @return 文件路径
     */
    public String copyRawToFile(Context context, int rawResId, String fileName) {
        File file = new File(context.getFilesDir(), fileName);

        if (!file.exists()) {
            try (InputStream inputStream = context.getResources().openRawResource(rawResId);
                 FileOutputStream outputStream = new FileOutputStream(file)) {

                byte[] buffer = new byte[4096];
                int bytesRead;
                while ((bytesRead = inputStream.read(buffer)) != -1) {
                    outputStream.write(buffer, 0, bytesRead);
                }

                Log.i(TAG, "File copied to: " + file.getAbsolutePath());
            } catch (IOException e) {
                e.printStackTrace();
                Log.e(TAG, "Error copying file: " + e.getMessage());
            }
        } else {
            Log.i(TAG, "File already exists: " + file.getAbsolutePath());
        }

        return file.getAbsolutePath();
    }


    /**
     * 读取 .bin 文件为字节数组
     *
     * @param filePath 文件路径
     * @return 字节数组
     */
    /*private byte[] readBinFile( String filePath) {
        try {

            Log.i(TAG, "File Path: " + filePath);
            Log.i(TAG, "File Exists: " + new File(filePath).exists());
            Log.i(TAG, "Can Read: " + new File(filePath).canRead());

            File file = new File(filePath);
            if (!file.exists()) {
                Log.e(TAG, "File not found: " + filePath);
                return null;
            }

            FileInputStream fis = new FileInputStream(file);
            ByteArrayOutputStream bos = new ByteArrayOutputStream();

            byte[] buffer = new byte[4096];
            int bytesRead;
            while ((bytesRead = fis.read(buffer)) != -1) {
                bos.write(buffer, 0, bytesRead);
            }

            fis.close();
            return bos.toByteArray();

        } catch (IOException e) {
            e.printStackTrace();
            Log.e(TAG, "Error reading bin file: " + e.getMessage());
            return null;
        }
    }*/
    private byte[] readBinFile(String filePath) {
        try {
            File file = new File(filePath);
            Log.i(TAG, "File Size: " + file.length());

            Log.i(TAG, "File Path: " + filePath);
            Log.i(TAG, "File Exists: " + new File(filePath).exists());
            Log.i(TAG, "Can Read: " + new File(filePath).canRead());

            if (!file.exists()) {
                Log.e(TAG, "File not found: " + filePath);
                return null;
            }

            FileInputStream fis = new FileInputStream(file);
            FileChannel channel = fis.getChannel();
            ByteBuffer buffer = ByteBuffer.allocate((int) file.length());
            channel.read(buffer);
            channel.close();
            fis.close();
            return buffer.array();

        } catch (IOException e) {
            e.printStackTrace();
            Log.e(TAG, "Error reading bin file: " + e.getMessage());
            return null;
        }
    }


    /**
     * 在彩屏设备上显示 .bin 文件
     *
     * @param filePath .bin 文件路径
     * @param width    图片宽度
     * @param height   图片高度
     */
    public void displayBinOnLcd(String filePath, int width, int height) {
        Log.i(TAG, "displayBinOnLcd: filePath==>" + filePath);
        try {
            byte[] binData = readBinFile(filePath);
            if (binData == null) {
                Log.e(TAG, "Bin data is null");
                return;
            }

            // 显示图片
            int result = mSystemBinderService.lcdDrawPicture(0, 0, binData, binData.length, width, height);
            Log.e(TAG, "lcdDrawPicture Result: " + result);

        } catch (RemoteException e) {
            e.printStackTrace();
            Log.e(TAG, "RemoteException: " + e.getMessage());
        }
    }


}
