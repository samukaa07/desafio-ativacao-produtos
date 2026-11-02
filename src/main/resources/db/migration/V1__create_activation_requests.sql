CREATE TABLE activation_requests
(
    id             UUID PRIMARY KEY,
    external_id    VARCHAR(100)             NOT NULL,
    customer_id    VARCHAR(100)             NOT NULL,
    product_code   VARCHAR(100)             NOT NULL,
    status         VARCHAR(20)              NOT NULL CHECK (status IN ('PENDING', 'PROCESSING', 'COMPLETED', 'FAILED')),
    failure_reason VARCHAR(500),
    created_at     TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at     TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uk_activation_external_id UNIQUE (external_id)
);
CREATE INDEX idx_activation_customer_id ON activation_requests (customer_id);
CREATE INDEX idx_activation_status ON activation_requests (status);
