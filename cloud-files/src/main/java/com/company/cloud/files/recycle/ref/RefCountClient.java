package com.company.cloud.files.recycle.ref;

/**
 * 引用计数客户端（R-C07）：彻底删除一个文件引用时递减该 sha256 的引用计数。
 *
 * <p><b>契约冻结说明：</b>B 组（传输）的 HTTP 引用计数接口尚未交付，
 * 本接口为组间冻结契约——C 组只负责"减一"，计数归 0 后的物理清理由 B 组负责。
 * B 组接口交付后新增 HttpRefCountClient 实现并标记 @Primary 替换本地实现。
 */
public interface RefCountClient {

    /**
     * 递减指定内容哈希的引用计数（彻底删除一个文件引用时调用）。
     *
     * @param sha256   内容哈希
     * @param fileSize 被删文件字节数（供 B 组统计/清理参考）
     */
    void decrement(String sha256, long fileSize);
}
