#!/usr/bin/env python3
"""Protect existing releases while allowing an unpublished version to retry after CI repairs."""
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


def should_publish(event, current, previous, target_tag_exists=True):
    if event in ('release', 'workflow_dispatch'):
        return True
    return event == 'push' and previous is not None and (current != previous or not target_tag_exists)


def main():
    current = version(Path('app/build.gradle.kts').read_text())
    event = os.environ.get('GITHUB_EVENT_NAME', '')
    before = os.environ.get('AYVA_BEFORE_SHA', '')
    previous = None
    if event == 'push' and re.fullmatch(r'[0-9a-fA-F]{40}', before):
        result = subprocess.run(['git', 'show', f'{before}:app/build.gradle.kts'], capture_output=True, text=True)
        if result.returncode == 0:
            previous = version(result.stdout)
    target_tag_exists = True
    if event == 'push' and previous == current:
        # A failed version-bump run never created its tag. Let the next repair
        # publish that pending version, without replacing an existing release.
        tag = subprocess.run(['git', 'show-ref', '--verify', '--quiet', f'refs/tags/v{current[0]}'])
        if tag.returncode not in (0, 1):
            raise RuntimeError('Cannot establish whether the target release tag exists')
        target_tag_exists = tag.returncode == 0
    output = f'publish={str(should_publish(event, current, previous, target_tag_exists)).lower()}\n'
    target = os.environ.get('GITHUB_OUTPUT')
    if target:
        with open(target, 'a') as stream:
            stream.write(output)
    print(output, end='')


if __name__ == '__main__':
    main()
