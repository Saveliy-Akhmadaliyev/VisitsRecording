-- V2: правки по итогам обсуждения API (см. AGENTS.md, раздел «Backend»):
-- 1) статус CANCELLED вместо OVERDUE — «просрочен» вычисляется на лету;
-- 2) templates.origin_id — группировка версий одного шаблона;
-- 3) сид-пользователи для разработки (admin/admin123, inspector/inspector123).

ALTER TABLE visits DROP CONSTRAINT IF EXISTS visits_status_check;
ALTER TABLE visits ADD CONSTRAINT visits_status_check
    CHECK (status IN ('PLANNED', 'IN_PROGRESS', 'COMPLETED', 'CANCELLED'));

ALTER TABLE templates ADD COLUMN origin_id UUID;
UPDATE templates SET origin_id = id;
ALTER TABLE templates ALTER COLUMN origin_id SET NOT NULL;
ALTER TABLE templates ADD CONSTRAINT fk_templates_origin
    FOREIGN KEY (origin_id) REFERENCES templates (id);

INSERT INTO users (id, full_name, login, password_hash, role, is_active) VALUES
    ('00000000-0000-0000-0000-000000000001', 'Админ Тестов', 'admin',
     '$2a$10$ROXEhG70Nt.qTK5Yc5SYJui17LVLxzscnOb/xrG775dStLBrHomBG', 'ADMIN', TRUE),
    ('00000000-0000-0000-0000-000000000002', 'Инспектор Тестов', 'inspector',
     '$2a$10$yRhz0InHWJ2svsgVIv53gu90lRFzB3KsCMxLhkcWZE6bAUOZsM4Xe', 'INSPECTOR', TRUE);
