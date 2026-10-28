#!/usr/bin/env python3
"""Package an already built, pinned ChatScript checkout and Android workers into Run.

The checkout must first compile production.top as RAWDATA/RUN/run.top using its
restricted host compiler. See README.md. No network access or downloads occur here.
"""
import argparse
import hashlib
import json
from pathlib import Path
import shutil
import subprocess
import zipfile
from build_android import PIN


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('checkout', type=Path)
    parser.add_argument('android_build', type=Path)
    parser.add_argument('repository', type=Path)
    args = parser.parse_args()
    source = args.checkout.resolve()
    if subprocess.check_output(['git', 'rev-parse', 'HEAD'], cwd=source, text=True).strip() != PIN:
        raise SystemExit('Unexpected upstream revision')
    if (source / 'RAWDATA/RUN/run.top').read_bytes() != Path(__file__).with_name('production.top').read_bytes():
        raise SystemExit('Compile the current production.top first')
    script = (source / 'TOPIC/BUILD1/script1.txt').read_text(encoding='utf-8-sig')
    if '~run_meaning' not in script or 'RUNUNKNOWN' not in script:
        raise SystemExit('Compiled meaning topic missing')
    app = args.repository / 'app/src/main'
    assets = app / 'assets/conversation'
    assets.mkdir(parents=True, exist_ok=True)
    archive = assets / 'native-v1.zip'
    with zipfile.ZipFile(archive, 'w', zipfile.ZIP_DEFLATED, compresslevel=9) as z:
        for part in ['DICT/ENGLISH', 'LIVEDATA/ENGLISH', 'LIVEDATA/SYSTEM', 'TOPIC/BUILD0', 'TOPIC/BUILD1']:
            for path in sorted((source / part).rglob('*')):
                if path.is_file() and path.suffix != '.json' and not path.name.startswith('map'):
                    info = zipfile.ZipInfo(str(path.relative_to(source)), (2026, 1, 1, 0, 0, 0))
                    info.compress_type = zipfile.ZIP_DEFLATED
                    z.writestr(info, path.read_bytes())
    digest = lambda path: hashlib.sha256(path.read_bytes()).hexdigest()
    (assets / 'native-v1.sha256').write_text(digest(archive) + '\n')
    manifest = {'upstream': 'https://github.com/ChatScript/ChatScript', 'revision': PIN,
                'ndk': '27.2.12479018', 'protocol': 'RUNNATIVE1', 'assetsSha256': digest(archive), 'workers': {}}
    for abi in ['arm64-v8a', 'x86_64', 'armeabi-v7a', 'x86']:
        dest = app / 'jniLibs' / abi / 'librun_dialogue.so'
        dest.parent.mkdir(parents=True, exist_ok=True)
        shutil.copyfile(args.android_build / abi / dest.name, dest)
        manifest['workers'][abi] = digest(dest)
    (assets / 'native-v1.json').write_text(json.dumps(manifest, indent=2) + '\n')
    licenses = app / 'assets/licenses/chatscript'
    licenses.mkdir(parents=True, exist_ok=True)
    shutil.copyfile(source / 'license.txt', licenses / 'ChatScript.txt')
    shutil.copyfile(args.android_build / 'LLVM-NOTICE.txt', licenses / 'LLVM-NOTICE.txt')
    # The existing app notice includes the Princeton WordNet terms used by English data.
    wordnet = (app / 'assets/dictionary/LICENSE.txt').read_text().split('WordNet Release', 1)[1]
    (licenses / 'WordNet.txt').write_text('WordNet Release' + wordnet)
    print(f'Packaged {archive.stat().st_size:,} bytes of static assets; two isolated workers and two 32-bit compatibility placeholders')


if __name__ == '__main__':
    main()
