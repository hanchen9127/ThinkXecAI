-- Sample e-commerce schema used by the optional read-only database query tool.

CREATE TABLE t_role (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    code        VARCHAR(50) NOT NULL UNIQUE,
    name        VARCHAR(100) NOT NULL,
    description TEXT,
    created_at  TIMESTAMP NOT NULL DEFAULT now()
);

CREATE TABLE t_app_user (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    username       VARCHAR(50) NOT NULL UNIQUE,
    password_hash  VARCHAR(255) NOT NULL,
    email          VARCHAR(255) UNIQUE,
    phone          VARCHAR(20),
    nickname       VARCHAR(100),
    status         VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at     TIMESTAMP NOT NULL DEFAULT now(),
    updated_at     TIMESTAMP NOT NULL DEFAULT now()
);

CREATE TABLE t_user_role (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id     UUID NOT NULL REFERENCES t_app_user(id) ON DELETE CASCADE,
    role_id     UUID NOT NULL REFERENCES t_role(id) ON DELETE CASCADE,
    created_at  TIMESTAMP NOT NULL DEFAULT now(),
    UNIQUE (user_id, role_id)
);

CREATE TABLE t_product_category (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    parent_id   UUID REFERENCES t_product_category(id) ON DELETE SET NULL,
    name        VARCHAR(100) NOT NULL,
    sort_order  INT NOT NULL DEFAULT 0,
    created_at  TIMESTAMP NOT NULL DEFAULT now()
);

CREATE TABLE t_product (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    category_id     UUID REFERENCES t_product_category(id) ON DELETE SET NULL,
    sku             VARCHAR(64) NOT NULL UNIQUE,
    name            VARCHAR(200) NOT NULL,
    description     TEXT,
    price           NUMERIC(12, 2) NOT NULL,
    stock_quantity  INT NOT NULL DEFAULT 0,
    status          VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at      TIMESTAMP NOT NULL DEFAULT now(),
    updated_at      TIMESTAMP NOT NULL DEFAULT now()
);

CREATE TABLE t_product_category_relation (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    product_id   UUID NOT NULL REFERENCES t_product(id) ON DELETE CASCADE,
    category_id  UUID NOT NULL REFERENCES t_product_category(id) ON DELETE CASCADE,
    created_at   TIMESTAMP NOT NULL DEFAULT now(),
    UNIQUE (product_id, category_id)
);

CREATE TABLE t_order_header (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    order_no      VARCHAR(64) NOT NULL UNIQUE,
    user_id       UUID REFERENCES t_app_user(id) ON DELETE SET NULL,
    status        VARCHAR(20) NOT NULL DEFAULT 'CREATED',
    total_amount  NUMERIC(12, 2) NOT NULL DEFAULT 0,
    currency      VARCHAR(10) NOT NULL DEFAULT 'USD',
    created_at    TIMESTAMP NOT NULL DEFAULT now(),
    updated_at    TIMESTAMP NOT NULL DEFAULT now()
);

CREATE TABLE t_order_item (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    order_id      UUID NOT NULL REFERENCES t_order_header(id) ON DELETE CASCADE,
    product_id    UUID REFERENCES t_product(id) ON DELETE SET NULL,
    product_name  VARCHAR(200) NOT NULL,
    unit_price    NUMERIC(12, 2) NOT NULL,
    quantity      INT NOT NULL,
    subtotal      NUMERIC(12, 2) NOT NULL,
    created_at    TIMESTAMP NOT NULL DEFAULT now()
);

CREATE TABLE t_payment (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    order_id    UUID NOT NULL REFERENCES t_order_header(id) ON DELETE CASCADE,
    payment_no  VARCHAR(64) NOT NULL UNIQUE,
    method      VARCHAR(20) NOT NULL,
    amount      NUMERIC(12, 2) NOT NULL,
    status      VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    paid_at     TIMESTAMP,
    created_at  TIMESTAMP NOT NULL DEFAULT now()
);

CREATE TABLE t_shipment (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    order_id      UUID NOT NULL REFERENCES t_order_header(id) ON DELETE CASCADE,
    carrier       VARCHAR(50),
    tracking_no   VARCHAR(100),
    status        VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    shipped_at    TIMESTAMP,
    delivered_at  TIMESTAMP,
    created_at    TIMESTAMP NOT NULL DEFAULT now()
);

CREATE TABLE t_comment_topic (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name        VARCHAR(100) NOT NULL UNIQUE,
    created_at  TIMESTAMP NOT NULL DEFAULT now()
);

CREATE TABLE t_comment (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    product_id  UUID REFERENCES t_product(id) ON DELETE CASCADE,
    user_id     UUID REFERENCES t_app_user(id) ON DELETE SET NULL,
    content     TEXT NOT NULL,
    rating      INT NOT NULL CHECK (rating BETWEEN 1 AND 5),
    created_at  TIMESTAMP NOT NULL DEFAULT now()
);

CREATE TABLE t_comment_topic_mapping (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    comment_id  UUID NOT NULL REFERENCES t_comment(id) ON DELETE CASCADE,
    topic_id    UUID NOT NULL REFERENCES t_comment_topic(id) ON DELETE CASCADE,
    created_at  TIMESTAMP NOT NULL DEFAULT now(),
    UNIQUE (comment_id, topic_id)
);

CREATE TABLE t_comment_summary_daily (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    product_id     UUID NOT NULL REFERENCES t_product(id) ON DELETE CASCADE,
    summary_date   DATE NOT NULL,
    comment_count  INT NOT NULL DEFAULT 0,
    avg_rating     NUMERIC(3, 2),
    created_at     TIMESTAMP NOT NULL DEFAULT now(),
    UNIQUE (product_id, summary_date)
);

CREATE TABLE t_system_kv (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    k           VARCHAR(100) NOT NULL UNIQUE,
    v           TEXT,
    created_at  TIMESTAMP NOT NULL DEFAULT now(),
    updated_at  TIMESTAMP NOT NULL DEFAULT now()
);

CREATE INDEX t_user_role_user_id_idx ON t_user_role(user_id);
CREATE INDEX t_user_role_role_id_idx ON t_user_role(role_id);
CREATE INDEX t_product_category_id_idx ON t_product(category_id);
CREATE INDEX t_order_header_user_id_idx ON t_order_header(user_id);
CREATE INDEX t_order_item_order_id_idx ON t_order_item(order_id);
CREATE INDEX t_order_item_product_id_idx ON t_order_item(product_id);
CREATE INDEX t_payment_order_id_idx ON t_payment(order_id);
CREATE INDEX t_shipment_order_id_idx ON t_shipment(order_id);
CREATE INDEX t_comment_product_id_idx ON t_comment(product_id);
CREATE INDEX t_comment_topic_mapping_comment_id_idx ON t_comment_topic_mapping(comment_id);
CREATE INDEX t_comment_summary_daily_product_id_idx ON t_comment_summary_daily(product_id);
