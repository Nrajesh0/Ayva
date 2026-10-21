#!/usr/bin/env python3
"""Expose JUnit counts and failed case names in the Actions job summary, without test data or logs."""
import os
from pathlib import Path
import re
import xml.etree.ElementTree as ET


def summarize(directory):
    counts = dict(tests=0, failures=0, errors=0, skipped=0)
    failed = []
    reports = sorted(Path(directory).glob('TEST-*.xml'))
    for report in reports:
        root = ET.parse(report).getroot()
        for key in counts:
            counts[key] += int(root.get(key, 0))
        for case in root.findall('testcase'):
            if case.find('failure') is not None or case.find('error') is not None:
                # Include identifiers only; failure messages can contain fixture data or environment values.
                identity = case.get('classname', root.get('name', '')) + '.' + case.get('name', '')
                failed.append(re.sub(r'[^\w .():/-]', '_', identity)[:300])
    lines = ['## Android unit tests', '']
    if not reports:
        lines.append('No JUnit XML reports were produced. Inspect the compilation or test-runner failure.')
    else:
        lines.append(', '.join(f'{value} {key}' for key, value in counts.items()) + '.')
        if failed:
            lines += ['', 'Failed cases:', ''] + [f'- `{name}`' for name in failed[:100]]
            if len(failed) > 100:
                lines += [f'- {len(failed) - 100} more; see the uploaded verification reports.']
    return '\n'.join(lines) + '\n'


def main():
    summary = summarize('app/build/test-results/testDebugUnitTest')
    target = os.environ.get('GITHUB_STEP_SUMMARY')
    if target:
        with open(target, 'a') as stream:
            stream.write(summary)
    else:
        print(summary, end='')


if __name__ == '__main__':
    main()
