CREATE TABLE wallet (
  id SERIAL PRIMARY KEY,
  hash VARCHAR(66) NOT NULL,
  address VARCHAR(42),
  public_key VARCHAR,
  type VARCHAR(7) NOT NULL
);