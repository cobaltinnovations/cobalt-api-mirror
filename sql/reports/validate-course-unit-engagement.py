#!/usr/bin/env python3
"""Read-only performance and dwell reconciliation checks using psql connection settings."""
import argparse
import csv
import datetime
from decimal import Decimal, InvalidOperation
import json
from pathlib import Path
import subprocess
import tempfile
import time
import uuid

ROOT = Path(__file__).resolve().parents[2]
QUERY = ROOT / 'sql/reports/course-unit-engagement-by-account-query.sql'


def report_query():
    # Bind the parity-tested operational query using psql's escaped literal variables.
    return (QUERY.read_text()
            .replace('?::TEXT', ":'institution_id'::TEXT", 1)
            .replace('?::TIMESTAMP', "NULLIF(:'start_local', '')::TIMESTAMP", 1)
            .replace('?::TIMESTAMP', "NULLIF(:'end_local', '')::TIMESTAMP", 1)
            .replace('?::TEXT', ":'report_time_zone'::TEXT", 1))


def csv_query(path):
    """Represent the actual CSV's public identity/time columns as read-only values."""
    columns = ['account_id', 'course_id', 'course_module_id', 'course_unit_id',
               'foreground_page_time_seconds']
    values = []
    with path.open(newline='', encoding='utf-8-sig') as source:
        reader = csv.DictReader(source)
        if not set(columns).issubset(reader.fieldnames or []):
            raise ValueError('CSV must contain the Course Unit Engagement identity and foreground seconds columns')
        for line, row in enumerate(reader, start=2):
            try:
                identities = [str(uuid.UUID(row[column])) for column in columns[:4]]
                seconds = Decimal(row['foreground_page_time_seconds'])
                if not seconds.is_finite() or seconds < 0:
                    raise ValueError('invalid foreground duration')
            except (ValueError, TypeError, InvalidOperation) as error:
                raise ValueError(f'Invalid report CSV data on line {line}') from error
            values.append('(' + ', '.join("'" + value + "'::UUID" for value in identities) +
                          ', ' + format(seconds, 'f') + '::NUMERIC)')
    if not values:
        return 'SELECT ' + ', '.join('NULL::UUID AS ' + column for column in columns[:4]) + \
               ', NULL::NUMERIC AS foreground_page_time_seconds WHERE FALSE'
    return 'SELECT * FROM (VALUES ' + ',\n'.join(values) + ') csv_rows(' + ', '.join(columns) + ')'


def reconciliation_query(report_sql):
    """Compare exported totals/rollups with independently grouped dashboard inputs."""
    ctes = [f'''comparison_params AS (
        SELECT i.institution_id, COALESCE(i.time_zone, :'report_time_zone') AS time_zone,
            NULLIF(:'start_date', '')::DATE AS start_date,
            NULLIF(:'end_date', '')::DATE AS end_date
        FROM institution i WHERE i.institution_id = :'institution_id'
    ), report_rows AS MATERIALIZED ({report_sql}),
    dashboard_base AS MATERIALIZED (
        SELECT mv.account_id, c.course_id, cm.course_module_id, cu.course_unit_id,
            mv.dwell_time_seconds
        FROM comparison_params p
        JOIN mv_analytics_dwell_time mv ON mv.institution_id = p.institution_id
        JOIN account a ON a.account_id = mv.account_id
        JOIN course_unit cu ON cu.course_unit_id = mv.course_unit_id
        JOIN course_module cm ON cm.course_module_id = cu.course_module_id
        JOIN course c ON c.course_id = cm.course_id
        WHERE mv.page_view_type = 'PAGE_VIEW_COURSE_UNIT'
            AND a.role_id = 'PATIENT' AND a.test_account = FALSE
            AND (p.start_date IS NULL OR
                (mv.page_viewed_at AT TIME ZONE p.time_zone) >= p.start_date::TIMESTAMP)
            AND (p.end_date IS NULL OR
                (mv.page_viewed_at AT TIME ZONE p.time_zone) < (p.end_date + 1)::TIMESTAMP)
            AND EXISTS (SELECT 1 FROM institution_course ic
                WHERE ic.institution_id = p.institution_id AND ic.course_id = c.course_id)
    ), dashboard_objects AS (
        SELECT DISTINCT c.course_id, cm.course_module_id, cu.course_unit_id
        FROM comparison_params p
        JOIN institution_course ic ON ic.institution_id = p.institution_id
        JOIN course c ON c.course_id = ic.course_id
        JOIN course_module cm ON cm.course_id = c.course_id
        JOIN course_unit cu ON cu.course_module_id = cm.course_module_id
    )''']
    checks = []
    for level, column in [('unit', 'course_unit_id'), ('module', 'course_module_id'), ('course', 'course_id')]:
        # Modules/courses must first be summed per account, then summarized.
        ctes += [f'''new_{level}_accounts AS (
            SELECT account_id, {column} AS object_id,
                SUM(foreground_page_time_seconds) AS seconds
            FROM report_rows GROUP BY account_id, {column}
        ), dashboard_{level}_accounts AS (
            SELECT account_id, {column} AS object_id,
                SUM(dwell_time_seconds) AS seconds
            FROM dashboard_base GROUP BY account_id, {column}
        ), {level}_account_check AS (
            SELECT '{level}_per_account_seconds'::TEXT AS check_name,
                COUNT(*) AS compared_rows,
                COUNT(*) FILTER (WHERE n.account_id IS NULL) AS missing_in_new,
                COUNT(*) FILTER (WHERE d.account_id IS NULL) AS missing_in_dashboard,
                COUNT(*) FILTER (WHERE n.seconds IS DISTINCT FROM d.seconds) AS mismatch_count,
                COALESCE(MAX(ABS(n.seconds - d.seconds)), 0) AS max_seconds_delta
            FROM new_{level}_accounts n FULL JOIN dashboard_{level}_accounts d
                ON d.account_id = n.account_id AND d.object_id = n.object_id
        ), new_{level}_stats AS (
            SELECT object_id, COUNT(*) AS accounts, AVG(seconds) AS mean_seconds,
                PERCENTILE_CONT(0.5) WITHIN GROUP (ORDER BY seconds) AS median_seconds
            FROM new_{level}_accounts GROUP BY object_id
        ), dashboard_{level}_stats AS (
            SELECT object_id, COUNT(*) AS accounts, AVG(seconds) AS mean_seconds,
                PERCENTILE_CONT(0.5) WITHIN GROUP (ORDER BY seconds) AS median_seconds
            FROM dashboard_{level}_accounts GROUP BY object_id
        ), {level}_stats_check AS (
            SELECT '{level}_dashboard_count_mean_median'::TEXT AS check_name,
                COUNT(*) AS compared_rows,
                COUNT(*) FILTER (WHERE COALESCE(n.accounts, 0) <> COALESCE(d.accounts, 0)
                    OR COALESCE(n.mean_seconds, 0) <> COALESCE(d.mean_seconds, 0)
                    OR COALESCE(n.median_seconds, 0) <> COALESCE(d.median_seconds, 0)) AS mismatch_count,
                0::BIGINT AS missing_in_new, 0::BIGINT AS missing_in_dashboard,
                COALESCE(MAX(GREATEST(
                    ABS(COALESCE(n.mean_seconds, 0) - COALESCE(d.mean_seconds, 0)),
                    ABS(COALESCE(n.median_seconds, 0) - COALESCE(d.median_seconds, 0)))), 0) AS max_seconds_delta
            FROM (SELECT DISTINCT {column} AS object_id FROM dashboard_objects) o
            LEFT JOIN new_{level}_stats n ON n.object_id = o.object_id
            LEFT JOIN dashboard_{level}_stats d ON d.object_id = o.object_id
        )''']
        checks += [f'''SELECT check_name, compared_rows, mismatch_count, missing_in_new,
            missing_in_dashboard, max_seconds_delta FROM {level}_account_check''',
                   f'''SELECT check_name, compared_rows, mismatch_count, missing_in_new,
            missing_in_dashboard, max_seconds_delta FROM {level}_stats_check''']
    checks += ['''SELECT 'unique_account_unit_rows'::TEXT AS check_name,
        (SELECT COUNT(*) FROM report_rows) AS compared_rows,
        COUNT(*) AS mismatch_count, 0::BIGINT AS missing_in_new,
        0::BIGINT AS missing_in_dashboard, 0::NUMERIC AS max_seconds_delta
        FROM (SELECT account_id, course_unit_id FROM report_rows
              GROUP BY account_id, course_unit_id HAVING COUNT(*) <> 1) duplicates''']
    ctes += ['all_checks AS (' + '\nUNION ALL\n'.join(checks) + ')']
    return 'WITH ' + ',\n'.join(ctes) + '''
    SELECT JSONB_BUILD_OBJECT(
        'institution_found', EXISTS (SELECT 1 FROM comparison_params),
        'report_rows', (SELECT COUNT(*) FROM report_rows),
        'checks', (SELECT JSONB_AGG(TO_JSONB(c) ORDER BY check_name) FROM all_checks c))
    '''


def connection_arguments(args, csv_format=False):
    cmd = ['psql', '-X', '-q', '-w', '-v', 'ON_ERROR_STOP=1']
    cmd += ['--csv', '-P', 'footer=off'] if csv_format else ['-A', '-t']
    values = {
        'institution_id': args.institution,
        'report_time_zone': args.time_zone,
        'start_date': args.start_date.isoformat() if args.start_date else '',
        'end_date': args.end_date.isoformat() if args.end_date else '',
        'start_local': f'{args.start_date}T00:00:00' if args.start_date else '',
        'end_local': f'{args.end_date}T23:59:59.999999' if args.end_date else '',
        'statement_timeout': args.statement_timeout,
    }
    for key, value in values.items():
        cmd += ['-v', f'{key}={value}']
    return cmd


def run_psql(args, sql, output=None):
    transaction = f'''BEGIN TRANSACTION ISOLATION LEVEL REPEATABLE READ READ ONLY;
    SET LOCAL statement_timeout = :'statement_timeout';
    {sql};
    COMMIT;
    '''
    result = subprocess.run(connection_arguments(args, output is not None), input=transaction,
                            text=True, stdout=output or subprocess.PIPE, stderr=subprocess.PIPE)
    if result.returncode:
        raise RuntimeError(result.stderr.strip())
    return result.stdout


def reconcile(args, sql):
    result = json.loads(run_psql(args, reconciliation_query(sql)))
    result['status'] = ('PASS' if result['institution_found'] and
                        all(c['mismatch_count'] == 0 for c in result['checks']) else 'FAIL')
    result['reference'] = 'Dashboard All Users: raw seconds before display formatting'
    return result


def performance(args, sql):
    measurements = []
    # Each run includes psql connection setup, execution, CSV encoding, transfer
    # and local writing. Temporary participant-level CSVs are removed afterward.
    with tempfile.TemporaryDirectory(prefix='course-engagement-benchmark-') as folder:
        target = Path(folder) / 'export.csv'
        for run in range(args.runs):
            start = time.monotonic()
            with target.open('w') as output:
                run_psql(args, sql, output)
            wall_ms = (time.monotonic() - start) * 1000
            with target.open(newline='') as output:
                rows = sum(1 for _ in csv.DictReader(output))
            measurements.append({'run': run + 1, 'wall_ms': round(wall_ms, 2),
                                 'rows': rows, 'csv_bytes': target.stat().st_size})
    plan = json.loads(run_psql(args, 'EXPLAIN (ANALYZE, BUFFERS, TIMING OFF, FORMAT JSON) ' + sql))
    details = plan[0]
    root = details['Plan']
    result = {'csv_runs': measurements, 'server_execution_ms': details['Execution Time'],
              'server_planning_ms': details['Planning Time'],
              'root_buffers': {key: root.get(key, 0) for key in [
                  'Shared Hit Blocks', 'Shared Read Blocks', 'Temp Read Blocks', 'Temp Written Blocks']}}
    if args.plan_output:
        args.plan_output.write_text(json.dumps(plan, indent=2) + '\n')
        result['plan_file'] = str(args.plan_output)
    return result


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('mode', choices=['reconcile', 'performance'])
    parser.add_argument('--institution', default='BEHAVIOR_BRIDGE')
    parser.add_argument('--time-zone', default='America/New_York',
                        help='Fallback report time zone when the institution has none configured')
    parser.add_argument('--start-date', type=datetime.date.fromisoformat)
    parser.add_argument('--end-date', type=datetime.date.fromisoformat)
    parser.add_argument('--statement-timeout', default='120s')
    parser.add_argument('--runs', type=int, default=3)
    parser.add_argument('--plan-output', type=Path)
    parser.add_argument('--report-csv', type=Path, help='Reconcile an actual download instead of executing the report query')
    args = parser.parse_args()
    if args.start_date and args.end_date and args.start_date > args.end_date:
        parser.error('--start-date must be on or before --end-date')
    if not 1 <= args.runs <= 20:
        parser.error('--runs must be between 1 and 20')
    if args.report_csv and args.mode != 'reconcile':
        parser.error('--report-csv is only supported in reconcile mode')
    try:
        query = csv_query(args.report_csv) if args.report_csv else report_query()
        result = reconcile(args, query) if args.mode == 'reconcile' else performance(args, report_query())
    except (OSError, RuntimeError, ValueError) as error:
        parser.exit(1, str(error) + '\n')
    print(json.dumps(result, indent=2))
    return 1 if result.get('status') == 'FAIL' else 0


if __name__ == '__main__':
    raise SystemExit(main())
