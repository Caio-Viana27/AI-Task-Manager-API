-- Emails are unique regardless of letter case, so 'Ana@x.com' and 'ana@x.com' can't both exist.
-- The existing UNIQUE (EMAIL) stays: it serves the WHERE EMAIL = ? lookups.
CREATE UNIQUE INDEX UX_USERS_EMAIL_LOWER ON USERS (LOWER(EMAIL));
