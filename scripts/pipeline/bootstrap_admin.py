#!/usr/bin/env python3
"""One-time local creation of a real administrator. Never run by ingestion/tests."""
import argparse
import getpass
import re
import subprocess
import sys
import secrets
import unicodedata
from pathlib import Path

try:
    from import_candidates import literal
except ModuleNotFoundError:
    sys.path.insert(0, str(Path(__file__).resolve().parent))
    from import_candidates import literal

CONTAINER = "viridexa-db-v1"
DATABASE = "viridexa"
LOCK_NAME = "viridexa.bootstrap_admin.v1"


def validate_password(secret):
    size = len(secret.encode("utf-8"))
    if not 12 <= size <= 72 or any(unicodedata.category(char) == "Cc" for char in secret):
        raise ValueError("Password must be 12–72 UTF-8 bytes")


def create_sql(email, display_name, password_hash, procedure_name=None):
    """Build a single-connection transaction guarded by a MySQL named lock."""
    procedure_name = procedure_name or "bootstrap_admin_" + secrets.token_hex(12)
    if not re.fullmatch(r"bootstrap_admin_[0-9a-f]{24}", procedure_name):
        raise ValueError("Invalid procedure name")
    e, n, h = map(literal, (email, display_name, password_hash))
    lock = literal(LOCK_NAME)
    return f"""DELIMITER //
CREATE PROCEDURE {procedure_name}()
BEGIN
  DECLARE lock_ok INT DEFAULT 0;
  DECLARE admins INT DEFAULT 0;
  DECLARE matching_admins INT DEFAULT 0;
  DECLARE EXIT HANDLER FOR SQLEXCEPTION
  BEGIN
    ROLLBACK;
    IF lock_ok = 1 THEN DO RELEASE_LOCK({lock}); END IF;
    RESIGNAL;
  END;
  SELECT GET_LOCK({lock}, 30) INTO lock_ok;
  IF lock_ok <> 1 THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Could not acquire administrator initialization lock'; END IF;
  SELECT COUNT(*) INTO admins FROM app_user u JOIN user_role ur ON ur.user_id=u.id JOIN app_role r ON r.id=ur.role_id WHERE r.role_key='ADMIN';
  SELECT COUNT(*) INTO matching_admins FROM app_user u JOIN user_role ur ON ur.user_id=u.id JOIN app_role r ON r.id=ur.role_id WHERE r.role_key='ADMIN' AND u.email={e} AND u.display_name={n};
  IF admins = 0 THEN
    START TRANSACTION;
    INSERT INTO app_user(email,display_name,password_hash) VALUES({e},{n},{h});
    INSERT INTO user_role(user_id,role_id) SELECT LAST_INSERT_ID(),id FROM app_role WHERE role_key='ADMIN';
    IF ROW_COUNT() <> 1 THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='ADMIN role is missing'; END IF;
    COMMIT;
    SELECT 'CREATED';
  ELSEIF admins = 1 AND matching_admins = 1 THEN
    SELECT 'ALREADY_INITIALIZED';
  ELSE
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='A different administrator identity already exists';
  END IF;
  DO RELEASE_LOCK({lock});
END//
CALL {procedure_name}()//
DROP PROCEDURE IF EXISTS {procedure_name}//
DELIMITER ;
"""


def run_sql(sql):
    result = subprocess.run(
        ["docker", "exec", "-i", CONTAINER, "sh", "-c",
         'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysql -uroot --batch --skip-column-names ' + DATABASE],
        input=sql, text=True, capture_output=True,
    )
    if result.returncode:
        raise RuntimeError("Administrator initialization failed; no credentials logged")
    return result.stdout.strip()


def hash_password(secret):
    jar = Path(__file__).resolve().parents[2] / "apps/api/target/viridexa-api-0.1.0.jar"
    result = subprocess.run(
        ["java", "-Dloader.main=org.viridexa.api.governance.PasswordHash", "-cp", str(jar),
         "org.springframework.boot.loader.launch.PropertiesLauncher"],
        input=secret + "\n", text=True, capture_output=True,
    )
    hashed = result.stdout.strip()
    if result.returncode or not re.fullmatch(r"\$2[aby]\$\d\d\$[./A-Za-z0-9]{53}", hashed):
        raise RuntimeError("Password hash helper unavailable; build API first")
    return hashed


def execute_bootstrap(email, display_name, password_hash):
    procedure = "bootstrap_admin_" + secrets.token_hex(12)
    try:
        return run_sql(create_sql(email, display_name, password_hash, procedure))
    finally:
        # MySQL clients may stop processing the script after a failing CALL.
        try:
            run_sql("DROP PROCEDURE IF EXISTS " + procedure + ";")
        except RuntimeError:
            pass


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--email", required=True)
    parser.add_argument("--display-name", required=True)
    args = parser.parse_args(argv)
    if len(args.email) > 254 or not re.fullmatch(r"[^\s@]+@[^\s@]+\.[^\s@]+", args.email):
        parser.error("Invalid email")
    if not args.display_name.strip() or len(args.display_name) > 120:
        parser.error("Invalid display name")
    try:
        # This read avoids prompting on ordinary reruns. The locked procedure repeats
        # the identity check and is the authority if another invocation races us.
        state = run_sql("SELECT CONCAT(COUNT(*), ':', COALESCE(MAX(CONCAT(HEX(u.email), ':', HEX(u.display_name))), '')) FROM app_user u JOIN user_role ur ON ur.user_id=u.id JOIN app_role r ON r.id=ur.role_id WHERE r.role_key='ADMIN';")
        expected = args.email.encode().hex().upper() + ":" + args.display_name.encode().hex().upper()
        if state == "1:" + expected:
            print("Administrator already initialized; no changes made.")
            return 0
        if state.split(":", 1)[0] != "0":
            raise RuntimeError("A different administrator identity already exists")
        secret = getpass.getpass("New real administrator password (12–72 UTF-8 bytes): ")
        validate_password(secret)
        if secret != getpass.getpass("Repeat password: "):
            raise RuntimeError("Passwords differ")
        outcome = execute_bootstrap(args.email, args.display_name, hash_password(secret))
        print("Created the supplied real administrator." if "CREATED" in outcome else "Administrator already initialized; no changes made.")
        return 0
    except (RuntimeError, ValueError):
        raise SystemExit("Administrator initialization failed; no credentials logged")


if __name__ == "__main__":
    raise SystemExit(main())
