package com.hjq.demo.utils;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * 十进制int与byte[]转换工具类
 */
public class IntToBytesUtils {

    /**
     * 将int转换为大端序字节数组（高位在前）
     * @param value 十进制整数
     * @return 4字节的byte数组
     */
    public static byte[] toBigEndian(int value) {
        return new byte[] {
                (byte) (value >> 24),
                (byte) (value >> 16),
                (byte) (value >> 8),
                (byte) value
        };
    }

    /**
     * 将int转换为小端序字节数组（低位在前）
     * @param value 十进制整数
     * @return 4字节的byte数组
     */
    public static byte[] toLittleEndian(int value) {
        return new byte[] {
                (byte) value,
                (byte) (value >> 8),
                (byte) (value >> 16),
                (byte) (value >> 24)
        };
    }

    /**
     * 使用ByteBuffer将int转换为字节数组（可指定字节序）
     * @param value 十进制整数
     * @param order 字节序：ByteOrder.BIG_ENDIAN 或 ByteOrder.LITTLE_ENDIAN
     * @return 4字节的byte数组
     */
    public static byte[] toByteArray(int value, ByteOrder order) {
        return ByteBuffer.allocate(4)
                .order(order)
                .putInt(value)
                .array();
    }

    public static void main(String[] args) {
        int testValue = 16909060; // 十六进制: 0x01020304

        // 测试大端序
        byte[] bigEndian = toBigEndian(testValue);
        System.out.println("Big Endian: ");
        for (byte b : bigEndian) {
            System.out.printf("0x%02X ", b);
        }

        // 测试小端序
        byte[] littleEndian = toLittleEndian(testValue);
        System.out.println("\nLittle Endian: ");
        for (byte b : littleEndian) {
            System.out.printf("0x%02X ", b);
        }

        // 测试ByteBuffer方法
        byte[] bufferBig = toByteArray(testValue, ByteOrder.BIG_ENDIAN);
        System.out.println("\nByteBuffer Big Endian: ");
        for (byte b : bufferBig) {
            System.out.printf("0x%02X ", b);
        }
    }
}
