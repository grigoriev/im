-- Users and their roles (docs/PLAN.md, section 5). Passwords are Argon2 hashes.
CREATE TABLE app_user (
    id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    username      VARCHAR(32)  NOT NULL UNIQUE,
    display_name  VARCHAR(128) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    role          VARCHAR(16)  NOT NULL CHECK (role IN ('USER', 'ADMIN')),
    enabled       BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at    TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now()
);

-- "Angemeldet bleiben": the persistent remember-me tokens of Spring Security.
CREATE TABLE persistent_logins (
    username  VARCHAR(64) NOT NULL,
    series    VARCHAR(64) PRIMARY KEY,
    token     VARCHAR(64) NOT NULL,
    last_used TIMESTAMP   NOT NULL
);
