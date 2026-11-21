package com.teaclub.cloud.product.storage;

import java.io.InputStream;

/**
 * 文件存储抽象：商品图片的落盘与访问地址生成。
 *
 * <p>实现类通过 {@code teaclub.storage.type} 二选一装配，
 * 上层控制器不感知具体存储介质。
 */
public interface FileStorageService {

    /**
     * 保存文件。
     *
     * @param in          文件流
     * @param originalName 原始文件名（仅用于取扩展名）
     * @param contentType MIME 类型
     * @param sizeBytes   文件字节数
     * @return 可直接写入数据库的访问地址
     */
    String upload(InputStream in, String originalName, String contentType, long sizeBytes);

    /** 当前实现名称，用于健康检查与日志 */
    String type();
}
