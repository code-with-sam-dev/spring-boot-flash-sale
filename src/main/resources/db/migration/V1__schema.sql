CREATE TABLE products (
    id          BIGINT PRIMARY KEY,
    name        TEXT    NOT NULL,
    price_cents INTEGER NOT NULL,
    stock       INTEGER NOT NULL CHECK (stock >= 0)
);

CREATE TABLE orders (
    id           BIGSERIAL PRIMARY KEY,
    product_id   BIGINT      NOT NULL REFERENCES products (id),
    customer     TEXT        NOT NULL,
    amount_cents INTEGER     NOT NULL,
    status       TEXT        NOT NULL,
    payment_ref  TEXT,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);
