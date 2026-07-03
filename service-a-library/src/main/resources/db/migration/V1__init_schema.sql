CREATE TABLE users (
    id              BIGSERIAL PRIMARY KEY,
    username        VARCHAR(50)  NOT NULL UNIQUE,
    email           VARCHAR(150) NOT NULL UNIQUE,
    password_hash   VARCHAR(255) NOT NULL,
    role            VARCHAR(20)  NOT NULL DEFAULT 'USER',
    created_at      TIMESTAMP    NOT NULL DEFAULT now(),
    updated_at      TIMESTAMP    NOT NULL DEFAULT now()
);

CREATE TABLE books (
    id                  BIGSERIAL PRIMARY KEY,
    title               VARCHAR(255) NOT NULL,
    author              VARCHAR(255) NOT NULL,
    isbn                VARCHAR(20)  NOT NULL UNIQUE,
    publication_year    INTEGER      NOT NULL,
    genre               VARCHAR(100) NOT NULL,
    total_copies        INTEGER      NOT NULL DEFAULT 1,
    available_copies    INTEGER      NOT NULL DEFAULT 1,
    created_at          TIMESTAMP    NOT NULL DEFAULT now(),
    updated_at          TIMESTAMP    NOT NULL DEFAULT now(),
    CONSTRAINT chk_available_copies CHECK (available_copies >= 0 AND available_copies <= total_copies)
);

CREATE INDEX idx_books_author ON books (author);
CREATE INDEX idx_books_genre ON books (genre);

-- The initial admin user is NOT seeded here on purpose: hardcoding a bcrypt hash in a
-- migration means committing a password hash to source control. Instead, an
-- AdminUserSeeder (CommandLineRunner) creates it at application startup using the
-- real PasswordEncoder bean, from ADMIN_USERNAME/ADMIN_PASSWORD env vars. See
-- com.library.config.AdminUserSeeder.
