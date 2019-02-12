CREATE TABLE transaction (
  id SERIAL PRIMARY KEY,
  hash VARCHAR(66) NOT NULL UNIQUE,
  from_wallet VARCHAR(66) NOT NULL,
  to_wallet VARCHAR(66) NOT NULL,
  input VARCHAR NOT NULL,
  state VARCHAR(7) NOT NULL,
  type VARCHAR(22) NOT NULL,
  amount NUMERIC,
  created_at TIMESTAMP WITH TIME ZONE NOT NULL,
  processed_at TIMESTAMP WITH TIME ZONE
);