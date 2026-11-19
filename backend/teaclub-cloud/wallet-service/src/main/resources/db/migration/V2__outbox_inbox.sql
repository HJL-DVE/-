-- ============================================================
-- Outbox / Inbox 通用表（各业务服务库均需建立）
--
-- 说明：此脚本不在各服务的 Flyway 路径下自动执行（避免与业务 V1 冲突），
-- 由运维在需要 Outbox/Inbox 的服务库中手工执行，或复制到该服务
-- src/main/resources/db/migration/V2__outbox_inbox.sql
-- ============================================================

CREATE TABLE IF NOT EXISTS sys_outbox_message (
  event_id    VARCHAR(64)  NOT NULL PRIMARY KEY COMMENT '事件唯一ID',
  event_type  VARCHAR(64)  NOT NULL COMMENT '事件类型',
  biz_no      VARCHAR(64)  NULL COMMENT '业务单号',
  routing_key VARCHAR(128) NOT NULL COMMENT '路由键',
  payload     TEXT         NOT NULL COMMENT '事件载荷JSON',
  version     INT          NOT NULL DEFAULT 1 COMMENT '契约版本',
  trace_id    VARCHAR(64)  NULL COMMENT '链路ID',
  occurred_at DATETIME(3)  NOT NULL COMMENT '事实发生时间',
  status      VARCHAR(16)  NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/SENT/FAILED',
  attempts    INT          NOT NULL DEFAULT 0 COMMENT '投递次数',
  created_at  DATETIME(3)  NOT NULL COMMENT '创建时间',
  last_error  VARCHAR(512) NULL COMMENT '最后错误',
  KEY idx_status_created (status, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='事务发件箱';

CREATE TABLE IF NOT EXISTS sys_inbox_message (
  event_id    VARCHAR(64)  NOT NULL PRIMARY KEY COMMENT '事件唯一ID（幂等键）',
  event_type  VARCHAR(64)  NULL COMMENT '事件类型',
  trace_id    VARCHAR(64)  NULL COMMENT '链路ID',
  received_at DATETIME(3)  NOT NULL COMMENT '接收时间',
  status      VARCHAR(16)  NOT NULL DEFAULT 'PROCESSED' COMMENT '处理状态'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='消费收件箱（幂等）';
