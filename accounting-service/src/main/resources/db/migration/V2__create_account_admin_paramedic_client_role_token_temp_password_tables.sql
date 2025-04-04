CREATE TABLE IF NOT EXISTS role
(
    id   BIGINT PRIMARY KEY GENERATED ALWAYS AS IDENTITY,
    name VARCHAR(50) NOT NULL
);

CREATE TABLE IF NOT EXISTS account
(
    id                    BIGINT PRIMARY KEY GENERATED ALWAYS AS IDENTITY,
    account_id            UUID        NOT NULL,
    email                 TEXT        NOT NULL,
    password_hash         TEXT,
    is_soft_deleted       BOOLEAN     NOT NULL DEFAULT FALSE,
    is_two_factor_enabled BOOLEAN     NOT NULL DEFAULT TRUE,
    blocked_until         TIMESTAMP,
    first_name            VARCHAR(50),
    last_name             VARCHAR(50),
    phone_number          VARCHAR(15),
    created_at            TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at            TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    provider              VARCHAR(50) NOT NULL DEFAULT 'INTERNAL',
    role_id               BIGINT      NOT NULL,
    CONSTRAINT fk_account_role FOREIGN KEY (role_id) REFERENCES role (id)
);

CREATE TABLE IF NOT EXISTS temporary_password
(
    id                      BIGINT PRIMARY KEY GENERATED ALWAYS AS IDENTITY,
    account_id              BIGINT NOT NULL UNIQUE,
    temporary_password_hash TEXT,
    expiration_date         TIMESTAMP,
    CONSTRAINT fk_temp_password_account FOREIGN KEY (account_id) REFERENCES account (id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS refresh_tokens
(
    id          BIGINT PRIMARY KEY GENERATED ALWAYS AS IDENTITY,
    token       TEXT,
    expiry_date TIMESTAMP,
    created_at  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    account_id  BIGINT    NOT NULL UNIQUE,
    CONSTRAINT fk_refresh_token_account FOREIGN KEY (account_id) REFERENCES account (id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS admin
(
    id             BIGINT PRIMARY KEY GENERATED ALWAYS AS IDENTITY,
    account_id     BIGINT  NOT NULL UNIQUE,
    is_super_admin BOOLEAN NOT NULL DEFAULT FALSE,
    CONSTRAINT fk_admin_account FOREIGN KEY (account_id) REFERENCES account (id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS paramedic
(
    id              BIGINT PRIMARY KEY GENERATED ALWAYS AS IDENTITY,
    account_id      BIGINT NOT NULL UNIQUE,
    bonus_policy_id UUID   NOT NULL,
    CONSTRAINT fk_paramedic_account FOREIGN KEY (account_id) REFERENCES account (id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS client
(
    id         BIGINT PRIMARY KEY GENERATED ALWAYS AS IDENTITY,
    account_id BIGINT NOT NULL UNIQUE,
    stripe_id  TEXT UNIQUE,
    CONSTRAINT fk_client_account FOREIGN KEY (account_id) REFERENCES account (id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS subscriptions
(
    id                     BIGINT PRIMARY KEY GENERATED ALWAYS AS IDENTITY,
    stripe_subscription_id TEXT   NOT NULL UNIQUE,
    status                 VARCHAR(50),
    start_date             TIMESTAMP,
    end_date               TIMESTAMP,
    client_id              BIGINT NOT NULL,
    CONSTRAINT fk_subscription_client FOREIGN KEY (client_id) REFERENCES client (id) ON DELETE CASCADE
);
