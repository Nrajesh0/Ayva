#!/usr/bin/env python3
"""Build pinned ChatScript in scratch space. Does not change the app or input checkout."""
import argparse
from concurrent.futures import ThreadPoolExecutor
from pathlib import Path
import re
import shutil
import subprocess

PIN = "9f5eec4736ba22bd992a6498c1e0052e2a795125"
FLAGS = ["NOMAIN", "ANDROID", "DISCARDSERVER", "DISCARDCLIENT", "DISCARDPOSTGRES", "DISCARDMONGO",
         "DISCARDMYSQL", "DISCARDMICROSOFTSQL", "DISCARD_TEXT_COMPRESSION", "DISCARD_JAPANESE",
         "DISCARDJSONOPEN", "DISCARDJAVASCRIPT", "DISCARDWEBSOCKET", "DISCARDTCPOPEN"]


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("checkout", type=Path)
    parser.add_argument("ndk", type=Path)
    parser.add_argument("output", type=Path, help="New, empty scratch directory")
    args = parser.parse_args()
    checkout, output = args.checkout.resolve(), args.output.resolve()
    revision = subprocess.check_output(["git", "rev-parse", "HEAD"], cwd=checkout, text=True).strip()
    if revision != PIN:
        raise SystemExit(f"Expected source revision {PIN}, got {revision}")
    if output.exists() and any(output.iterdir()):
        raise SystemExit("Output must be empty; existing work is never removed")
    output.mkdir(parents=True, exist_ok=True)
    source = output / "source"
    source.mkdir()
    # Export committed source, not local experiment edits or object files.
    archive = output / "source.tar"
    with archive.open("wb") as stream:
        subprocess.run(["git", "archive", PIN, "SRC"], cwd=checkout, stdout=stream, check=True)
    shutil.unpack_archive(str(archive), str(source))
    subprocess.run(["git", "apply", str(Path(__file__).with_name("restricted.patch").resolve())], cwd=source, check=True)
    src = source / "SRC"
    files = re.search(r"^SOURCES=(.+)$", (src / "Makefile").read_text(), re.M).group(1).split()
    files = [name for name in files if not name.startswith("duktape/")]
    toolchain = args.ndk.resolve() / "toolchains/llvm/prebuilt/linux-x86_64/bin"
    for abi, triple in [("arm64-v8a", "aarch64-linux-android24"), ("x86_64", "x86_64-linux-android24")]:
        destination = output / abi
        destination.mkdir()
        compiler = str(toolchain / f"{triple}-clang++")

        def compile_one(name):
            obj = destination / (name.replace("/", "_") + ".o")
            command = [compiler, "-std=c++11", "-O1", "-fPIC", "-funsigned-char", "-Wno-writable-strings",
                       *[f"-D{flag}=1" for flag in FLAGS], "-I" + str(src), "-I" + str(src / "evserver"),
                       "-I" + str(src / "include"), "-c", str(src / name), "-o", str(obj)]
            with obj.with_suffix(".log").open("w") as log:
                result = subprocess.run(command, stdout=log, stderr=subprocess.STDOUT)
            if result.returncode:
                raise RuntimeError(f"Compile failed: {abi}/{name}; see {obj.with_suffix('.log')}")
            return str(obj)

        with ThreadPoolExecutor(max_workers=2) as pool:
            objects = list(pool.map(compile_one, files))
        library = destination / "librun_chatscript_probe.so"
        command = [compiler, "-shared", "-static-libstdc++", "-Wl,-z,max-page-size=16384",
                   "-Wl,--no-undefined", *objects, "-o", str(library)]
        with (destination / "link.log").open("w") as log:
            subprocess.run(command, stdout=log, stderr=subprocess.STDOUT, check=True)
        print(f"Built {library} ({library.stat().st_size} bytes)", flush=True)
        worker = destination / "librun_dialogue.so"
        subprocess.run([compiler, "-std=c++11", "-fPIE", "-pie", "-static-libstdc++", "-funsigned-char",
                        *[f"-D{flag}=1" for flag in FLAGS], "-I" + str(src),
                        str(Path(__file__).with_name("worker.cpp").resolve()), *objects,
                        "-Wl,-z,max-page-size=16384", "-Wl,--no-undefined", "-o", str(worker)], check=True)
        subprocess.run([str(toolchain / "llvm-strip"), "--strip-unneeded", str(worker)], check=True)
        smoke = Path(__file__).with_name("smoke.cpp").resolve()
        subprocess.run([compiler, "-std=c++11", "-fPIE", "-pie", "-static-libstdc++", "-funsigned-char",
                        *[f"-D{flag}=1" for flag in FLAGS], "-I" + str(src), str(smoke),
                        "-L" + str(destination), "-lrun_chatscript_probe", "-Wl,-rpath,$ORIGIN",
                        "-Wl,-z,max-page-size=16384", "-o", str(destination / "run_smoke")], check=True)


    for abi, triple in [("armeabi-v7a", "armv7a-linux-androideabi24"), ("x86", "i686-linux-android24")]:
        destination = output / abi
        destination.mkdir()
        subprocess.run([str(toolchain / f"{triple}-clang++"), "-fPIE", "-pie", "-static-libstdc++",
                        str(Path(__file__).with_name("unsupported.cpp").resolve()),
                        "-Wl,-z,max-page-size=16384", "-o", str(destination / "librun_dialogue.so")], check=True)
    shutil.copyfile(args.ndk / "toolchains/llvm/prebuilt/linux-x86_64/NOTICE", output / "LLVM-NOTICE.txt")


if __name__ == "__main__":
    main()
