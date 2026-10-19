#!/usr/bin/env python3
"""Two-boot IE2 readiness probe for Purpur/Folia 26.3.

The production IE2 artifact is used unchanged on Purpur. Folia receives a
throwaway copy with only the plugin.yml admission marker injected; the source
artifact remains undeclared until broader gameplay certification is accepted.
"""
from __future__ import annotations

import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import sys
import time
import urllib.request
import zipfile

MC_VERSION = "26.3"
PURPUR_BUILD = "2646"
FOLIA_BUILD = 3
USER_AGENT = "InfinityExpansion2-26.3-readiness/1.0"
STARTUP_TIMEOUT = 300
SHUTDOWN_TIMEOUT = 90


def sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as handle:
        for chunk in iter(lambda: handle.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def get_json(url: str):
    request = urllib.request.Request(url, headers={"User-Agent": USER_AGENT})
    with urllib.request.urlopen(request, timeout=60) as response:
        return json.load(response)


def download(url: str, target: Path) -> None:
    request = urllib.request.Request(url, headers={"User-Agent": USER_AGENT})
    with urllib.request.urlopen(request, timeout=120) as response, target.open("wb") as out:
        shutil.copyfileobj(response, out)
    if not target.is_file() or target.stat().st_size == 0:
        raise RuntimeError(f"empty download: {url}")


def descriptor(jar: Path) -> str:
    with zipfile.ZipFile(jar) as archive:
        return archive.read("plugin.yml").decode("utf-8")


def plugin_version(jar: Path) -> str:
    text = descriptor(jar)
    match = re.search(r"(?m)^version:\s*['\"]?([^'\"\r\n]+)", text)
    if not match:
        raise RuntimeError(f"plugin.yml version missing in {jar}")
    return match.group(1).strip()


def inject_folia(source: Path, target: Path) -> None:
    original = descriptor(source)
    if re.search(r"(?mi)^['\"]?folia-supported['\"]?\s*:", original):
        raise RuntimeError("production IE2 descriptor unexpectedly declares Folia support")

    with zipfile.ZipFile(source, "r") as src, zipfile.ZipFile(target, "w") as dst:
        for info in src.infolist():
            data = src.read(info.filename)
            if info.filename == "plugin.yml":
                text = data.decode("utf-8")
                if not text.endswith("\n"):
                    text += "\n"
                text += "folia-supported: true\n"
                data = text.encode("utf-8")
            dst.writestr(info, data)

    probe = descriptor(target)
    if len(re.findall(r"(?mi)^['\"]?folia-supported['\"]?\s*:\s*true\s*$", probe)) != 1:
        raise RuntimeError("disposable Folia descriptor must contain exactly one true declaration")
    if sha256(source) == sha256(target):
        raise RuntimeError("disposable Folia probe must differ from production artifact")


def resolve_server(software: str) -> tuple[str, str]:
    if software == "purpur":
        meta = get_json(f"https://api.purpurmc.org/v2/purpur/{MC_VERSION}")
        builds = {str(value) for value in meta.get("builds", {}).get("all", [])}
        if PURPUR_BUILD not in builds:
            raise RuntimeError(f"Purpur {MC_VERSION} build {PURPUR_BUILD} is unavailable")
        return PURPUR_BUILD, f"https://api.purpurmc.org/v2/purpur/{MC_VERSION}/{PURPUR_BUILD}/download"

    builds = get_json(f"https://fill.papermc.io/v3/projects/folia/versions/{MC_VERSION}/builds")
    selected = next((entry for entry in builds if int(entry.get("id", -1)) == FOLIA_BUILD), None)
    if selected is None:
        raise RuntimeError(f"Folia {MC_VERSION} build {FOLIA_BUILD} is unavailable")
    url = selected.get("downloads", {}).get("server:default", {}).get("url")
    if not url:
        raise RuntimeError("Folia server download URL is missing")
    return str(FOLIA_BUILD), url


def write_server_files(work: Path) -> None:
    (work / "plugins" / "bStats").mkdir(parents=True, exist_ok=True)
    (work / "plugins" / "bStats" / "config.yml").write_text("enabled: false\n", encoding="utf-8")
    (work / "eula.txt").write_text("eula=true\n", encoding="utf-8")
    (work / "server.properties").write_text(
        "\n".join(
            [
                "online-mode=false",
                "level-name=ie2-readiness-world",
                "max-players=2",
                "spawn-protection=0",
                "view-distance=2",
                "simulation-distance=2",
                "pause-when-empty-seconds=-1",
                "enable-query=false",
                "enable-rcon=false",
                "motd=IE2 26.3 readiness",
                "",
            ]
        ),
        encoding="utf-8",
    )


def wait_for(log: Path, process: subprocess.Popen, token: str, timeout: int) -> str:
    deadline = time.monotonic() + timeout
    last = ""
    while time.monotonic() < deadline:
        if log.exists():
            last = log.read_text(encoding="utf-8", errors="replace")
            if token in last:
                return last
        status = process.poll()
        if status is not None:
            raise RuntimeError(f"server exited with {status} before {token!r}\n{last[-12000:]}")
        time.sleep(1)
    raise RuntimeError(f"timed out waiting for {token!r}\n{last[-12000:]}")


def stop_server(process: subprocess.Popen, label: str, log: Path) -> None:
    if process.poll() is None:
        assert process.stdin is not None
        process.stdin.write("stop\n")
        process.stdin.flush()
    try:
        status = process.wait(timeout=SHUTDOWN_TIMEOUT)
    except subprocess.TimeoutExpired:
        process.terminate()
        try:
            process.wait(timeout=10)
        except subprocess.TimeoutExpired:
            process.kill()
            process.wait(timeout=10)
        raise RuntimeError(f"{label}: server did not stop cleanly")
    if status != 0:
        text = log.read_text(encoding="utf-8", errors="replace") if log.exists() else ""
        raise RuntimeError(f"{label}: server exited with {status}\n{text[-12000:]}")


def validate_log(text: str, core_version: str, ie2_version: str, second: bool) -> None:
    required = [
        f"Enabling Slimefun v{core_version}",
        f"Enabling InfinityExpansion2 v{ie2_version}",
        "InfinityExpansion2 Doctor",
        "Slimefun Upgrade Readiness",
        "Stopping server",
    ]
    missing = [token for token in required if token not in text]
    if missing:
        raise RuntimeError(f"missing readiness evidence: {missing}")

    forbidden = [
        r"Error occurred while enabling InfinityExpansion2",
        r"Could not load .*InfinityExpansion",
        r"Accessing .* off owning region",
        r"Thread access violation",
        r"UnsupportedOperationException:.*Folia",
        r"IllegalStateException:.*owning region",
        r"IllegalStateException:.*region thread",
    ]
    matches = [pattern for pattern in forbidden if re.search(pattern, text, flags=re.IGNORECASE)]
    if matches:
        raise RuntimeError(f"threading/startup failure markers found: {matches}")

    if second and not re.search(r"previous shutdown\s+Clean", text, flags=re.IGNORECASE):
        raise RuntimeError("second boot did not report a clean previous Slimefun shutdown")


def run_cycle(
    work: Path,
    label: str,
    core_version: str,
    ie2_version: str,
    second: bool,
) -> None:
    log = work / f"{label}.console.log"
    with log.open("w", encoding="utf-8") as output:
        process = subprocess.Popen(
            [
                "java",
                "-Xms512M",
                "-Xmx2G",
                "-DcentralRepository=https://repo.maven.apache.org/maven2/",
                "-Dterminal.jline=false",
                "-Dterminal.ansi=false",
                "-jar",
                "server.jar",
                "--nogui",
            ],
            cwd=work,
            stdin=subprocess.PIPE,
            stdout=output,
            stderr=subprocess.STDOUT,
            text=True,
            bufsize=1,
        )

        try:
            wait_for(log, process, "Done (", STARTUP_TIMEOUT)
            current = log.read_text(encoding="utf-8", errors="replace")
            if f"Enabling InfinityExpansion2 v{ie2_version}" not in current:
                raise RuntimeError(f"{label}: IE2 did not enable\n{current[-12000:]}")
            assert process.stdin is not None
            process.stdin.write("infinityexpansion2 doctor status\n")
            process.stdin.write("sf doctor upgrade\n")
            process.stdin.flush()
            time.sleep(6)
        finally:
            stop_server(process, label, log)

    validate_log(log.read_text(encoding="utf-8", errors="replace"), core_version, ie2_version, second)


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--software", choices=("purpur", "folia"), required=True)
    parser.add_argument("--core", type=Path, required=True)
    parser.add_argument("--ie2", type=Path, required=True)
    parser.add_argument("--work", type=Path, required=True)
    args = parser.parse_args()

    core = args.core.resolve()
    ie2 = args.ie2.resolve()
    if not core.is_file() or not ie2.is_file():
        raise SystemExit("core and IE2 JARs must exist")

    work = args.work.resolve()
    if work.exists():
        shutil.rmtree(work)
    work.mkdir(parents=True)
    write_server_files(work)

    core_version = plugin_version(core)
    ie2_version = plugin_version(ie2)
    build, server_url = resolve_server(args.software)
    download(server_url, work / "server.jar")

    shutil.copy2(core, work / "plugins" / "Slimefun-Legacy-candidate.jar")
    runtime_ie2 = work / "plugins" / "SF_InfinityExpansion-candidate.jar"
    if args.software == "folia":
        inject_folia(ie2, runtime_ie2)
    else:
        if re.search(r"(?mi)^['\"]?folia-supported['\"]?\s*:", descriptor(ie2)):
            raise RuntimeError("production IE2 artifact must remain Folia-undeclared during readiness testing")
        shutil.copy2(ie2, runtime_ie2)

    evidence = {
        "software": args.software,
        "minecraft": MC_VERSION,
        "server_build": build,
        "core_version": core_version,
        "ie2_version": ie2_version,
        "core_sha256": sha256(core),
        "ie2_production_sha256": sha256(ie2),
        "ie2_runtime_sha256": sha256(runtime_ie2),
        "folia_descriptor_injected": args.software == "folia",
        "java": subprocess.check_output(["java", "-version"], stderr=subprocess.STDOUT, text=True).splitlines()[0],
    }
    (work / "runtime-build.json").write_text(json.dumps(evidence, indent=2) + "\n", encoding="utf-8")

    run_cycle(work, "first", core_version, ie2_version, False)
    run_cycle(work, "second", core_version, ie2_version, True)

    evidence["result"] = "PASS"
    evidence["cycles"] = 2
    (work / "smoke-result.json").write_text(json.dumps(evidence, indent=2) + "\n", encoding="utf-8")
    print(json.dumps(evidence, indent=2))
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except Exception as exc:
        print(f"IE2 26.3 readiness FAILED: {exc}", file=sys.stderr)
        raise
