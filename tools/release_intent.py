#!/usr/bin/env python3
"""Keep ordinary source/build-infrastructure pushes from replacing an existing release."""
import os
from pathlib import Path
import re
import subprocess


def version(text):
    name = re.search(r'\bversionName\s*=\s*"([^"]+)"', text)
    code = re.search(r'\bversionCode\s*=\s*(\d+)', text)
    if not name or not code:
        raise ValueError('App version metadata is missing')
    return name.group(1), int(code.group(1))


def should_publish(event, current, previous):
    if event in ('release', 'workflow_dispatch'):
        return True
    return event == 'push' and previous is not None and current != previous


def main():
    current = version(Path('app/build.gradle.kts').read_text())
    event = os.environ.get('GITHUB_EVENT_NAME', '')
    before = os.environ.get('AYVA_BEFORE_SHA', '')
    previous = None
    if event == 'push' and re.fullmatch(r'[0-9a-fA-F]{40}', before):
        result = subprocess.run(['git', 'show', f'{before}:app/build.gradle.kts'], capture_output=True, text=True)
        if result.returncode == 0:
            previous = version(result.stdout)
    output = f'publish={str(should_publish(event, current, previous)).lower()}\n'
    target = os.environ.get('GITHUB_OUTPUT')
    if target:
        with open(target, 'a') as stream:
            stream.write(output)
    print(output, end='')


if __name__ == '__main__':
    main()
