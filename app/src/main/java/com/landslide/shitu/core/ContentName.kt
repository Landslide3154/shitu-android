package com.landslide.shitu.core

import java.math.BigInteger

/**
 * 把文件内容的哈希变成 15 位「看着像乱码」的文件名主体。
 *
 * 为什么用内容而不是随机数：
 * - 同一个文件永远得到同一个名字 → 同一张图重复出现时不会再存第二份，也不用额外的去重逻辑；
 * - 跨次运行、跨设备都一致（配合 Syncthing 这类同步工具更稳）。
 *
 * 字符集刻意去掉了容易看错的 `0 O 1 l I`，剩 57 个字符：
 * A-Z（去掉 O、I）+ a-z（去掉 l）+ 2-9。57^15 ≈ 2.5×10^26，实际不可能撞名。
 */
object ContentName {

    const val LENGTH = 15

    const val ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789"

    /** 哈希字节 → 15 位名字主体；空哈希返回空串（调用方回退到原名）。 */
    fun fromDigest(digest: ByteArray): String {
        if (digest.isEmpty()) return ""
        var value = BigInteger(1, digest)
        val base = BigInteger.valueOf(ALPHABET.length.toLong())
        val chars = CharArray(LENGTH)
        for (i in LENGTH - 1 downTo 0) {
            val (q, r) = value.divideAndRemainder(base)
            chars[i] = ALPHABET[r.toInt()]
            value = q
        }
        return String(chars)
    }
}
