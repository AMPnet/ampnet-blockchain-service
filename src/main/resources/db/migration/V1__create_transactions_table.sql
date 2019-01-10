CREATE TABLE transaction (
  id SERIAL PRIMARY KEY,
  hash VARCHAR(66) NOT NULL,
  from_address VARCHAR(42) NOT NULL,
  to_address VARCHAR(42) NOT NULL,
  state VARCHAR(7) NOT NULL,
  type VARCHAR(18) NOT NULL,
  amount BIGINT
);