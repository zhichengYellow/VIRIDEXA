#!/usr/bin/env python3
"""Regression tests for mysqldump check-control filtering."""
from __future__ import annotations

import importlib.util
from pathlib import Path
import unittest

spec = importlib.util.spec_from_file_location("database_backup", Path(__file__).with_name("database-backup.py"))
backup = importlib.util.module_from_spec(spec)
assert spec and spec.loader
spec.loader.exec_module(backup)


class CheckControlFilterTests(unittest.TestCase):
    def test_strips_known_disable_and_restore_comment_lines_case_insensitively(self) -> None:
        source = (
            b"/*!40014 SET @OLD_UNIQUE_CHECKS=@@UNIQUE_CHECKS, UNIQUE_CHECKS=0 */;\n"
            b"/*!40014 SET @OLD_FOREIGN_KEY_CHECKS=@@FOREIGN_KEY_CHECKS, FOREIGN_KEY_CHECKS=0 */;\n"
            b"/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;\n"
            b"/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;\n"
            b"/*!40014 set @old_unique_checks = @@unique_checks, unique_checks = 0 */;\n"
            b"INSERT INTO `sample` (`id`) VALUES (1);\n"
        )
        result = backup.filter_sql(source, "viridexa_test", ddl=False)
        self.assertEqual(result, b"INSERT INTO `sample` (`id`) VALUES (1);\n")

    def test_rejects_unrecognized_disabling_forms(self) -> None:
        for statement in (
            b"SET FOREIGN_KEY_CHECKS=0;\n",
            b"/*!40100 SET @@SESSION.UNIQUE_CHECKS = 0 */;\n",
            b"/*!40014 SET @OLD_FOREIGN_KEY_CHECKS=@@FOREIGN_KEY_CHECKS, FOREIGN_KEY_CHECKS=1-1 */;\n",
            b"SET FOREIGN_KEY_CHECKS=0; -- trailing comment\n",
            b"/*!40100 SET UNIQUE_CHECKS =\n",
        ):
            with self.subTest(statement=statement), self.assertRaisesRegex(RuntimeError, "unsupported"):
                backup.filter_sql(statement, "viridexa_test", ddl=False)

    def test_preserves_insert_text_that_looks_like_a_control(self) -> None:
        insert = (b"INSERT INTO `sample` (`id`, `note`) VALUES "
                  b"(1, '/*!40014 SET FOREIGN_KEY_CHECKS=0 */; UNIQUE_CHECKS=0');\n")
        self.assertEqual(backup.filter_sql(insert, "viridexa_test", ddl=False), insert)

    def test_preserves_unrelated_session_settings(self) -> None:
        source = b"/*!40101 SET @OLD_SQL_MODE=@@SQL_MODE, SQL_MODE='STRICT_TRANS_TABLES' */;\n"
        self.assertEqual(backup.filter_sql(source, "viridexa_test", ddl=True), source)


if __name__ == "__main__":
    unittest.main()
