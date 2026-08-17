-- One database per service. They share a Postgres instance here to keep the
-- demo to a single container, but nothing crosses the boundary: no service ever
-- reads another's tables, and each owns its own Flyway migrations.
CREATE DATABASE orders;
CREATE DATABASE inventory;
CREATE DATABASE payments;
