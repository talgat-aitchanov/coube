-- Tariff configuration: history-preserving (insert-only) by design.
CREATE TABLE tariff_config
(
    id             BIGSERIAL PRIMARY KEY,
    base_rate      DECIMAL(12, 4) NOT NULL,
    urgent_rate    DECIMAL(6, 4)  NOT NULL,
    effective_from TIMESTAMPTZ    NOT NULL,
    effective_to   TIMESTAMPTZ,
    created_at     TIMESTAMPTZ    NOT NULL DEFAULT NOW()
);

-- Invariant: at most one active tariff at a time.
-- DB enforces this so application queries need no DISTINCT.
CREATE UNIQUE INDEX one_active_tariff
    ON tariff_config ((1)) WHERE effective_to IS NULL;

CREATE TABLE cargo_surcharge
(
    id             BIGSERIAL PRIMARY KEY,
    tariff_id      BIGINT        NOT NULL REFERENCES tariff_config (id) ON DELETE CASCADE,
    cargo_type     VARCHAR(20)   NOT NULL CHECK (cargo_type IN ('FRAGILE', 'OVERSIZED', 'STANDARD')),
    surcharge_rate DECIMAL(6, 4) NOT NULL,
    effective_from TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    effective_to   TIMESTAMPTZ,
    created_at     TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    UNIQUE (tariff_id, cargo_type)
);

-- Audit trail of every calculation served.
CREATE TABLE delivery_calculation
(
    id                   BIGSERIAL PRIMARY KEY,
    tariff_id            BIGINT REFERENCES tariff_config (id),
    distance_km          DECIMAL(8, 2)  NOT NULL,
    weight_ton           DECIMAL(8, 2)  NOT NULL,
    cargo_type           VARCHAR(20)    NOT NULL,
    is_urgent            BOOLEAN        NOT NULL,
    base_price           DECIMAL(14, 2) NOT NULL,
    urgent_surcharge     DECIMAL(14, 2) NOT NULL,
    cargo_type_surcharge DECIMAL(14, 2) NOT NULL,
    total_price          DECIMAL(14, 2) NOT NULL,
    currency             VARCHAR(3)     NOT NULL DEFAULT 'KZT',
    calculated_at        TIMESTAMPTZ    NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_delivery_calculation_calculated_at
    ON delivery_calculation (calculated_at DESC);

-- Seed: initial active tariff (KZT) with three cargo-type surcharges.
WITH new_tariff AS (
    INSERT INTO tariff_config (base_rate, urgent_rate, effective_from)
        VALUES (8.0000, 0.2000, NOW())
        RETURNING id
)
INSERT INTO cargo_surcharge (tariff_id, cargo_type, surcharge_rate)
SELECT id, 'STANDARD', 0.0000
FROM new_tariff
UNION ALL
SELECT id, 'FRAGILE', 0.1000
FROM new_tariff
UNION ALL
SELECT id, 'OVERSIZED', 0.2500
FROM new_tariff;
