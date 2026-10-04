// 拾图桥接口（规格 §6.2）：只暴露原子文件操作，不提供通用 shell 执行。
package com.landslide.shitu;

import com.landslide.shitu.shizuku.RemoteFile;

interface IShituService {
    // 分页列举，避免 Binder 1MB 事务上限；afterPath 为上一页最后一条的绝对路径
    List<RemoteFile> listFiles(String path, boolean recursive, int maxDepth, int maxCount, String afterPath);
    RemoteFile stat(String path);
    boolean exists(String path);
    void mkdirs(String path);
    boolean move(String srcPath, String dstPath);
    boolean copy(String srcPath, String dstPath);
    boolean delete(String path);
    String describeEnvironment();
}
