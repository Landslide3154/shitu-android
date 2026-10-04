# 拾图 Shitu · ProGuard 规则（v1 不开启混淆，规则预留给后续）
-keep class com.landslide.shitu.shizuku.ShituUserService { *; }
-keep interface com.landslide.shitu.IShituService { *; }
-keep class com.landslide.shitu.shizuku.RemoteFile { *; }
-dontwarn dev.rikka.shizuku.**
