# 忽略警告
#-ignorewarning

# 混淆保护自己项目的部分代码以及引用的第三方jar包
#-libraryjars libs/xxxxxxxxx.jar

# 不混淆这个包下的类
-keep class com.hjq.demo.http.api.** {
    <fields>;
}
-keep class com.hjq.demo.http.response.** {
    <fields>;
}
-keep class com.hjq.demo.http.model.** {
    <fields>;
}

# 不混淆被 Log 注解的方法信息
-keepclassmembernames class ** {
    @com.hjq.demo.aop.Log <methods>;
}

-dontwarn com.xuhao.didi.socket.client.**
-dontwarn com.xuhao.didi.socket.common.**
-dontwarn com.xuhao.didi.socket.server.**
-dontwarn com.xuhao.didi.core.**

-keep class com.xuhao.didi.socket.client.** { *; }
-keep class com.xuhao.didi.socket.common.** { *; }
-keep class com.xuhao.didi.socket.server.** { *; }
-keep class com.xuhao.didi.core.** { *; }

-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}
-keep class com.xuhao.didi.socket.client.sdk.client.OkSocketOptions$* {
    *;
}
-keep class com.xuhao.didi.socket.server.impl.OkServerOptions$* {
    *;
}


# 保留 com.android.wd.uart 包中的所有类、方法和字段
-keep class com.android.wd.uart.** { *; }

# 保留 com.szanfu 包中的所有类、方法和字段
-keep class com.szanfu.** { *; }

# 保留 AIDL 接口类和 Stub 类
-keep class com.android.wd.uart.**.Stub { *; }
-keep class com.android.wd.uart.**.AidlInterface { *; }

# 保留 TransOrder 类及其所有方法和字段
-keep class com.hjq.demo.model.TransOrder { *; }

# 保留 LitePalSupport 类
-keep class org.litepal.crud.LitePalSupport { *; }


