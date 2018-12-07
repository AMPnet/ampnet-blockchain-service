DROP DATABASE IF EXISTS blockchain;
CREATE DATABASE blockchain ENCODING 'UTF-8';

DROP DATABASE IF EXISTS blockchain_test;
CREATE DATABASE blockchain_test ENCODING 'UTF-8';

DROP USER IF EXISTS blockchain;
CREATE USER blockchain WITH PASSWORD 'password';

DROP USER IF EXISTS blockchain_test;
CREATE USER blockchain_test WITH PASSWORD 'password';
