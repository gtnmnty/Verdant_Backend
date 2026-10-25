-- M-33: notification messages and audit details are built from user input (appointment notes, stylist bios,
-- review text) and could exceed varchar(255). The INSERT then failed inside a listener that swallowed the
-- error, so the notification or audit row was silently lost. Both columns become text; the services still
-- cap the length (NotificationService 1000, AuditLogService 4000) so a single entry stays readable.
-- varchar -> text needs no table rewrite in PostgreSQL. Titles stay varchar and are truncated in code.
ALTER TABLE notifications ALTER COLUMN message TYPE TEXT;
ALTER TABLE audit_logs    ALTER COLUMN detail  TYPE TEXT;
