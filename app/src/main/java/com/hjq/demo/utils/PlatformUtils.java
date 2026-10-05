package com.hjq.demo.utils;
import android.text.TextUtils;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public class PlatformUtils {

    // 私有构造防止外部实例化
    private PlatformUtils() {}

    /**
     * 所有按键设备平台集合
     */
    private static final Set<String> KEYBOARD_PLATFORMS = Collections.unmodifiableSet(
            new HashSet<>(Arrays.asList(
                    DeviceType.A3,
                    DeviceType.A5,
                    DeviceType.A6,
                    DeviceType.A9,
                    DeviceType.A10,
                    DeviceType.A14
            ))
    );

    /**
     * 判断当前平台是否为按键设备
     */
    public static boolean isKeyboardDevice(String platform) {
        if (TextUtils.isEmpty(platform)) {
            return false;
        }
        return KEYBOARD_PLATFORMS.contains(platform);
    }
}
