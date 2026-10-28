import importlib.util
import subprocess
import unittest
from pathlib import Path
from unittest.mock import patch

MODULE = Path(__file__).resolve().parents[1] / "scripts/pipeline/bootstrap_admin.py"
spec = importlib.util.spec_from_file_location("bootstrap_admin", MODULE)
bootstrap = importlib.util.module_from_spec(spec)
spec.loader.exec_module(bootstrap)


class BootstrapAdminTests(unittest.TestCase):
    def test_password_byte_bounds(self):
        for value in ("a" * 12, "é" * 6, "a" * 72):
            bootstrap.validate_password(value)
        for value in ("a" * 11, "a" * 73, "é" * 37):
            with self.assertRaises(ValueError):
                bootstrap.validate_password(value)
        for value in ("a" * 11 + "\n", "a" * 11 + "\x00"):
            with self.assertRaises(ValueError):
                bootstrap.validate_password(value)

    def test_generated_sql_locks_checks_identity_and_handles_failure(self):
        sql = bootstrap.create_sql("admin@example.org", "Root Admin", "$2b$12$" + "a" * 53, "bootstrap_admin_" + "a" * 24)
        self.assertIn("GET_LOCK(", sql)
        self.assertIn("RELEASE_LOCK(", sql)
        self.assertIn("DECLARE EXIT HANDLER FOR SQLEXCEPTION", sql)
        self.assertIn("ROLLBACK", sql)
        self.assertIn("matching_admins = 1", sql)
        self.assertIn("RESIGNAL", sql)
        self.assertIn("DROP PROCEDURE IF EXISTS bootstrap_admin_", sql)
        self.assertNotIn("admin@example.org", sql)

    def test_each_invocation_uses_a_unique_procedure_name(self):
        first = bootstrap.create_sql("a@b.co", "Root", "$2b$12$" + "a" * 53)
        second = bootstrap.create_sql("a@b.co", "Root", "$2b$12$" + "a" * 53)
        self.assertNotEqual(first.split("CREATE PROCEDURE ")[1].split("(")[0], second.split("CREATE PROCEDURE ")[1].split("(")[0])

    @patch.object(bootstrap, "getpass")
    @patch.object(bootstrap, "run_sql")
    def test_same_existing_admin_does_not_prompt_or_write(self, run_sql, getpass):
        run_sql.return_value = "1:61646D696E406578616D706C652E6F7267:526F6F742041646D696E"
        self.assertEqual(bootstrap.main(["--email", "admin@example.org", "--display-name", "Root Admin"]), 0)
        getpass.assert_not_called()
        run_sql.assert_called_once()

    @patch.object(bootstrap, "getpass")
    @patch.object(bootstrap, "run_sql", return_value="1:DEADBEEF:CAFE")
    def test_different_existing_admin_refused(self, run_sql, getpass):
        with self.assertRaises(SystemExit):
            bootstrap.main(["--email", "admin@example.org", "--display-name", "Root Admin"])
        getpass.assert_not_called()

    @patch.object(bootstrap.subprocess, "run")
    def test_hash_helper_failure_does_not_leak_output(self, run):
        run.return_value = subprocess.CompletedProcess([], 1, stdout="secret-output", stderr="secret-error")
        with self.assertRaisesRegex(RuntimeError, "Password hash helper unavailable") as error:
            bootstrap.hash_password("a" * 12)
        self.assertNotIn("secret", str(error.exception))


if __name__ == "__main__":
    unittest.main()
