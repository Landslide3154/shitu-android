package com.landslide.shitu.shizuku

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

/** 跨 Binder 传递的文件信息（与 AIDL 中的 parcelable 声明一一对应）。 */
@Parcelize
data class RemoteFile(
    val path: String,
    val name: String,
    val size: Long,
    val mtimeMillis: Long,
    val isDirectory: Boolean,
    val isSymlink: Boolean,
) : Parcelable
