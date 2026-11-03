"""Validate and name the single universal release APK for GitHub distribution.

Only files listed in AGP's current output metadata are selected, so stale APKs in
the build directory cannot be published. Retired native dialogue files must not be packaged. All supported native architectures remain in the one download.
"""
import argparse
import hashlib
import json
from pathlib import Path
import re
import shutil
import tempfile
from zipfile import ZipFile

ROOT = Path(__file__).resolve().parents[1]
ABIS = {"arm64-v8a", "armeabi-v7a", "x86", "x86_64"}


def package(source, destination):
    metadata = json.loads((source / "output-metadata.json").read_text())
    if metadata["applicationId"] != "com.focusbyrj.app" or metadata["variantName"] != "release":
        raise ValueError("Expected RUN release output metadata")
    if metadata.get("minSdkVersionForDexing") != 31:
        raise ValueError("Expected an Android 12+ release (minimum API 31)")
    if len(metadata["elements"]) != 1 or metadata["elements"][0].get("filters"):
        raise ValueError("Expected exactly one universal release APK; ABI splits must be disabled")
    item = metadata["elements"][0]
    apk = (source / item["outputFile"]).resolve()
    if apk.parent != source.resolve() or not apk.is_file():
        raise ValueError("Missing APK or output path outside the build directory")
    with ZipFile(apk) as archive:
        packaged_abis = {n.split("/")[1] for n in archive.namelist() if n.startswith("lib/")}
        if packaged_abis != ABIS:
            raise ValueError(f"Wrong native libraries in {apk.name}: {packaged_abis}")
        if any(n.startswith("assets/conversation/") or n.endswith("/librun_dialogue.so") for n in archive.namelist()):
            raise ValueError("Retired native dialogue files found in APK")
    version, code = item["versionName"], item["versionCode"]
    if not re.fullmatch(r"[A-Za-z0-9._-]+", version):
        raise ValueError("Unsafe version filename")
    destination.parent.mkdir(parents=True, exist_ok=True)
    marker = ".run-release-package"
    if destination.exists() and not (destination / marker).is_file():
        raise ValueError("Refusing to replace a directory not created by this packager")
    with tempfile.TemporaryDirectory(prefix="run-release-", dir=destination.parent) as temp:
        staged = Path(temp) / "package"
        staged.mkdir()
        report = {"application_id": metadata["applicationId"], "version": version,
                  "version_code": code, "min_sdk": 31,
                  "native_dialogue_removed": True, "apks": {}}
        target = staged / f"RUN-{version}.apk"
        shutil.copy2(apk, target)
        digest = hashlib.sha256(target.read_bytes()).hexdigest()
        report["apks"]["universal"] = {"file": target.name, "bytes": target.stat().st_size, "sha256": digest}
        print(f"{target.name}: {target.stat().st_size / 1048576:.2f} MiB", flush=True)
        (staged / "SHA256SUMS.txt").write_text(f"{digest}  {target.name}\n")
        (staged / "manifest.json").write_text(json.dumps(report, indent=2) + "\n")
        (staged / "README.md").write_text(f"""## RUN {version}

Install **RUN-{version}.apk** on Android 12 or newer, including POCO X6 Pro.
This single universal APK includes all supported CPU architectures, features,
artwork, dictionary and Kotlin conversation features.
SHA-256 checksums are in SHA256SUMS.txt.
""")
        (staged / marker).touch()
        if destination.exists():
            shutil.rmtree(destination)
        shutil.move(str(staged), str(destination))


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--source", type=Path, default=ROOT / "app/build/outputs/apk/release")
    parser.add_argument("--output", type=Path, default=ROOT / "build/release-apks")
    args = parser.parse_args()
    package(args.source, args.output)
