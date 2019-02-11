CREATE TABLE wallet (
  id SERIAL PRIMARY KEY,
  tx_id INT REFERENCES transaction(id) NOT NULL,
  address VARCHAR(42) UNIQUE,
  public_key VARCHAR UNIQUE,
  type VARCHAR(7) NOT NULL
);