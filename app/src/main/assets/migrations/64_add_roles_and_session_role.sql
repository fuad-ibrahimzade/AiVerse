-- 角色系统：新增 roles 表，会话可绑定角色（roleId），由角色身份接手原 00 号提示词。
-- roles 各列与 RoleEntity 严格一致（非空类型 → NOT NULL，无 Kotlin 默认值参与 schema，故不写 DEFAULT）。
CREATE TABLE IF NOT EXISTS `roles` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, `description` TEXT NOT NULL, `identityPrompt` TEXT NOT NULL, `tags` TEXT NOT NULL, `avatarKind` TEXT NOT NULL, `avatarValue` TEXT NOT NULL, `enabled` INTEGER NOT NULL, `isBuiltin` INTEGER NOT NULL, `sortOrder` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, PRIMARY KEY(`id`));
-- 会话绑定角色：null 表示默认角色（role_default_builtin）。
ALTER TABLE `chat_sessions` ADD COLUMN `roleId` TEXT;
-- 内置默认角色：身份正文留空，由启动 seeder 从原 00 号内容（内置/全局层/项目层）写入。
INSERT INTO `roles` (`id`, `name`, `description`, `identityPrompt`, `tags`, `avatarKind`, `avatarValue`, `enabled`, `isBuiltin`, `sortOrder`, `createdAt`, `updatedAt`) VALUES ('role_default_builtin', '默认角色', '', '', '', 'NONE', '', 1, 1, 0, 0, 0);
