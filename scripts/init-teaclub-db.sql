-- TeaClub 数据库初始化脚本（一次性执行，仅创建库与专用账号）
-- 使用方式：将 <请替换为强密码> 替换为实际密码后再执行，或改用环境变量注入
-- 注意：本文件不得提交任何真实密码到仓库
-- 执行方式：mysql -u root -p < scripts/init-teaclub-db.sql

CREATE DATABASE IF NOT EXISTS `teaclub` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- 项目专用低权限账号（不直接使用 root，降低误操作与泄露风险）
CREATE USER IF NOT EXISTS 'teaclub'@'localhost' IDENTIFIED BY '<请替换为强密码>';
CREATE USER IF NOT EXISTS 'teaclub'@'127.0.0.1' IDENTIFIED BY '<请替换为强密码>';
ALTER USER 'teaclub'@'localhost' IDENTIFIED BY '<请替换为强密码>';
ALTER USER 'teaclub'@'127.0.0.1' IDENTIFIED BY '<请替换为强密码>';

-- 仅授予 teaclub 库权限，不授予全局权限
GRANT ALL PRIVILEGES ON `teaclub`.* TO 'teaclub'@'localhost';
GRANT ALL PRIVILEGES ON `teaclub`.* TO 'teaclub'@'127.0.0.1';
FLUSH PRIVILEGES;
