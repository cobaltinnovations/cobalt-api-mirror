"""Opt-in checks for the read-only validation runner on a disposable PostgreSQL DB."""
import argparse
import datetime
import importlib.util
import os
from pathlib import Path
import subprocess
import tempfile
import unittest
import uuid

HERE = Path(__file__).resolve().parent
spec = importlib.util.spec_from_file_location('engagement_validation', HERE / 'validate-course-unit-engagement.py')
validation = importlib.util.module_from_spec(spec)
spec.loader.exec_module(validation)


def fixture_command(sql):
    subprocess.run(['psql', '-X', '-q', '-w', '-v', 'ON_ERROR_STOP=1', '-A', '-t'],
                   input=sql, text=True, check=True, capture_output=True)


@unittest.skipUnless(os.environ.get('COBALT_REPORT_VALIDATION_TEST') == '1',
                     'Set COBALT_REPORT_VALIDATION_TEST=1 and psql settings for a disposable database')
class ValidationRunnerTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.schema = 'engagement_validation_' + uuid.uuid4().hex
        cls.original_options = os.environ.get('PGOPTIONS')
        fixture_command(f'CREATE SCHEMA {cls.schema}')
        os.environ['PGOPTIONS'] = ((cls.original_options or '') +
                                   f' -c search_path={cls.schema},pg_catalog').strip()
        fixture = (validation.ROOT / 'src/test/resources/reports/course-unit-engagement-fixture.sql').read_text()
        fixture_command(fixture.replace('TEMP TABLE', 'TABLE').replace(' ON COMMIT DROP', ''))
        # The existing Java fixture deliberately includes an invalid cross-tenant
        # event. Start with valid data, then test detection of that discrepancy.
        fixture_command("DELETE FROM mv_analytics_dwell_time WHERE institution_id = 'BEHAVIOR_BRIDGE' "
                        "AND account_id = '00000000-0000-0000-0000-000000000004'")

    @classmethod
    def tearDownClass(cls):
        fixture_command(f'DROP SCHEMA {cls.schema} CASCADE')
        if cls.original_options is None:
            os.environ.pop('PGOPTIONS', None)
        else:
            os.environ['PGOPTIONS'] = cls.original_options

    def setUp(self):
        self.args = argparse.Namespace(institution='BEHAVIOR_BRIDGE', time_zone='America/New_York', start_date=None, end_date=None,
                                       statement_timeout='30s', runs=1, plan_output=None)
        self.query = validation.report_query()

    def test_correct_totals_and_dashboard_rollups_pass(self):
        result = validation.reconcile(self.args, self.query)
        self.assertEqual('PASS', result['status'])
        self.assertEqual(5, result['report_rows'])
        self.assertEqual(7, len(result['checks']))
        self.assertTrue(all(check['mismatch_count'] == 0 for check in result['checks']))

    def test_inclusive_local_date_window_passes(self):
        self.args.start_date = datetime.date(2026, 3, 23)
        self.args.end_date = datetime.date(2026, 3, 23)
        result = validation.reconcile(self.args, self.query)
        self.assertEqual('PASS', result['status'])
        self.assertEqual(4, result['report_rows'])

    def test_fallback_report_timezone_matches_dashboard_date_bounds(self):
        fixture_command("UPDATE institution SET time_zone = NULL WHERE institution_id = 'BEHAVIOR_BRIDGE'")
        try:
            self.args.time_zone = 'America/Los_Angeles'
            self.args.start_date = datetime.date(2026, 3, 22)
            self.args.end_date = datetime.date(2026, 3, 22)
            result = validation.reconcile(self.args, self.query)
            self.assertEqual('PASS', result['status'])
            self.assertEqual(4, result['report_rows'])
        finally:
            fixture_command("UPDATE institution SET time_zone = 'America/New_York' WHERE institution_id = 'BEHAVIOR_BRIDGE'")

    def test_actual_downloaded_csv_can_be_reconciled(self):
        with tempfile.TemporaryDirectory(prefix='engagement-csv-test-') as folder:
            target = Path(folder) / 'download.csv'
            with target.open('w') as output:
                validation.run_psql(self.args, self.query, output)
            result = validation.reconcile(self.args, validation.csv_query(target))
            self.assertEqual('PASS', result['status'])
            self.assertEqual(5, result['report_rows'])

    def test_wrong_dwell_totals_are_detected(self):
        wrong = f'''SELECT account_id, course_id, course_module_id, course_unit_id,
            foreground_page_time_seconds * 2 AS foreground_page_time_seconds
            FROM ({self.query}) original'''
        result = validation.reconcile(self.args, wrong)
        self.assertEqual('FAIL', result['status'])
        self.assertTrue(any(check['max_seconds_delta'] > 0 for check in result['checks']))

    def test_duplicate_export_rows_are_detected(self):
        duplicate = f'SELECT * FROM ({self.query}) first_copy UNION ALL SELECT * FROM ({self.query}) second_copy'
        result = validation.reconcile(self.args, duplicate)
        self.assertEqual('FAIL', result['status'])
        check = next(check for check in result['checks'] if check['check_name'] == 'unique_account_unit_rows')
        self.assertEqual(5, check['mismatch_count'])

    def test_missing_export_rows_are_detected(self):
        missing = f'''SELECT * FROM ({self.query}) original
            WHERE course_unit_id <> '00000000-0000-0000-0000-000000000011'::UUID'''
        result = validation.reconcile(self.args, missing)
        self.assertEqual('FAIL', result['status'])
        self.assertTrue(any(check['missing_in_new'] > 0 for check in result['checks']))

    def test_mismatched_tenant_data_is_not_silently_hidden(self):
        fixture_command("""INSERT INTO mv_analytics_dwell_time VALUES
            ('BEHAVIOR_BRIDGE', '00000000-0000-0000-0000-000000000004',
             '00000000-0000-0000-0000-000000000011', '00000000-0000-0000-0000-000000000004',
             'PAGE_VIEW_COURSE_UNIT', 1000, '2026-03-23T04:00:12Z')""")
        try:
            result = validation.reconcile(self.args, self.query)
            self.assertEqual('FAIL', result['status'])
        finally:
            fixture_command("DELETE FROM mv_analytics_dwell_time WHERE institution_id = 'BEHAVIOR_BRIDGE' "
                            "AND account_id = '00000000-0000-0000-0000-000000000004'")

    def test_performance_mode_executes_csv_and_explain_analyze(self):
        result = validation.performance(self.args, self.query)
        self.assertEqual(5, result['csv_runs'][0]['rows'])
        self.assertGreater(result['csv_runs'][0]['csv_bytes'], 0)
        self.assertGreater(result['csv_runs'][0]['wall_ms'], 0)
        self.assertIn('server_execution_ms', result)
        self.assertIn('Temp Written Blocks', result['root_buffers'])


if __name__ == '__main__':
    unittest.main()
