package com.teaclub.cloud.core.config;

import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import org.apache.ibatis.reflection.MetaObject;

import java.time.LocalDateTime;

/**
 * 自动填充 createTime / updateTime。
 *
 * <p>由单体 backend 的 `common.config.MyMetaObjectHandler` 迁移而来。
 *
 * <p>实体上的 {@code @TableField(fill = FieldFill.INSERT)} / `INSERT_UPDATE`
 * 必须配合本处理器才会生效，否则插入时时间列为 null。
 */
public class MyMetaObjectHandler implements MetaObjectHandler {

    @Override
    public void insertFill(MetaObject metaObject) {
        LocalDateTime now = LocalDateTime.now();
        strictInsertFill(metaObject, "createTime", LocalDateTime.class, now);
        strictInsertFill(metaObject, "updateTime", LocalDateTime.class, now);
    }

    @Override
    public void updateFill(MetaObject metaObject) {
        strictUpdateFill(metaObject, "updateTime", LocalDateTime.class, LocalDateTime.now());
    }
}
