#!/bin/bash
set -euo pipefail

MYSQL_PWD="${MYSQL_ROOT_PASSWORD}" mysql --protocol=socket -uroot <<SQL
CREATE USER IF NOT EXISTS 'viridexa_ro'@'%' IDENTIFIED BY '${MYSQL_READONLY_PASSWORD}';
GRANT SELECT ON nonfood_oil_plants.* TO 'viridexa_ro'@'%';
FLUSH PRIVILEGES;
SQL
