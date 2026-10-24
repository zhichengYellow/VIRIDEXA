#!/bin/bash
set -euo pipefail

MYSQL_PWD="${MYSQL_ROOT_PASSWORD}" mysql --protocol=socket -uroot <<SQL
CREATE USER IF NOT EXISTS 'viridexa_ro'@'%' IDENTIFIED BY '${MYSQL_APP_PASSWORD}';
GRANT SELECT ON viridexa.* TO 'viridexa_ro'@'%';

CREATE USER IF NOT EXISTS 'viridexa_migrator'@'%' IDENTIFIED BY '${MYSQL_MIGRATOR_PASSWORD}';
GRANT ALL PRIVILEGES ON viridexa.* TO 'viridexa_migrator'@'%';

CREATE USER IF NOT EXISTS 'viridexa_importer'@'%' IDENTIFIED BY '${MYSQL_IMPORTER_PASSWORD}';
GRANT SELECT ON viridexa.* TO 'viridexa_importer'@'%';
FLUSH PRIVILEGES;
SQL
