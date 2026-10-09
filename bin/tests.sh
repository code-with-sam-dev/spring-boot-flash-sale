#!/usr/bin/env bash
# Runs the tests and prints one line per test, from Surefire's own reports.
set -euo pipefail
cd "$(dirname "$0")/.."
source bin/java25.sh >/dev/null 2>&1 || true
./mvnw -q test >/dev/null 2>&1 || true
python3 - <<'PY'
import glob, xml.etree.ElementTree as ET
total = failed = 0
for f in sorted(glob.glob('target/surefire-reports/TEST-*.xml')):
    suite = ET.parse(f).getroot()
    print(suite.get('name').split('.')[-1])
    for case in suite.iter('testcase'):
        total += 1
        bad = case.find('failure') is not None or case.find('error') is not None
        failed += bad
        print(f"  {'FAIL' if bad else 'ok  '}  {case.get('name')}")
print(f"Tests run: {total}, Failures: {failed}")
PY
