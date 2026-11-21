-- ============================================================
-- V2: 门店旗舰店标记
--
-- 背景：一个品牌可有多家门店，需要指定唯一「旗舰店」作为
--       默认门店（后台登录后默认上下文、小程序默认浏览门店）。
--
-- 设计：
--   is_flagship  0/1，品牌内最多一家为 1（应用层保证，非数据库约束
--                的唯一索引 —— MySQL 8 支持函数索引但不便于跨版本）
--   store_sort   门店列表排序号，数值小的靠前；旗舰店恒排最前
-- ============================================================

ALTER TABLE sys_store
    ADD COLUMN is_flagship TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否旗舰店：0否 1是（品牌内唯一）'
        AFTER status,
    ADD COLUMN store_sort INT NOT NULL DEFAULT 100 COMMENT '门店排序号，越小越靠前'
        AFTER is_flagship;

-- 既有门店默认排序按 id 递增，保持原有观感
UPDATE sys_store SET store_sort = id WHERE store_sort = 100;

-- 指定 TeaClud 桂林电子科技大学店 为旗舰店（store_code = s005）
UPDATE sys_store SET is_flagship = 1, store_sort = 1 WHERE store_code = 's005';

-- 其余门店取消旗舰标记（幂等：重复执行结果一致）
UPDATE sys_store SET is_flagship = 0 WHERE store_code <> 's005';
