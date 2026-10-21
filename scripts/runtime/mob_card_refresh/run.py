#!/usr/bin/env python3
"""Run the shutdown-on-completion card probe in a new, isolated Paper directory."""
import argparse
import hashlib
import json
from pathlib import Path
import shutil
import subprocess
import sys
import zipfile


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    for name in ("runtime", "paper-source", "core", "ie2", "java-home", "kotlin-stdlib", "kotlin-reflect"):
        parser.add_argument("--" + name, required=True, type=Path)
    parser.add_argument("--timeout", type=int, default=240)
    args = parser.parse_args()
    for artifact in (args.core, args.ie2, args.kotlin_stdlib, args.kotlin_reflect):
        if not artifact.is_file():
            parser.error(f"Required input file is missing: {artifact}")
    runtime = args.runtime.resolve()
    if runtime.exists() and any(runtime.iterdir()):
        parser.error("--runtime must be absent or empty; never reuse a live server directory")
    runtime.mkdir(parents=True, exist_ok=True)
    for name in ("server.jar", "libraries", "versions", "cache"):
        source = args.paper_source / name
        if source.is_dir():
            shutil.copytree(source, runtime / name)
        elif source.is_file():
            shutil.copy2(source, runtime / name)
        elif name == "server.jar":
            parser.error("--paper-source must contain server.jar and its cached runtime assets")
    # Use the plugin's normal Libby cache layout; do not alter the candidate or classloader.
    for artifact, name in ((args.kotlin_stdlib, "kotlin-stdlib"), (args.kotlin_reflect, "kotlin-reflect")):
        target = runtime / "libraries/org/jetbrains/kotlin" / name / "2.3.21" / (name + "-2.3.21.jar")
        target.parent.mkdir(parents=True, exist_ok=True)
        shutil.copy2(artifact, target)
    plugins = runtime / "plugins"
    plugins.mkdir()
    shutil.copy2(args.core, plugins / "Slimefun.jar")
    shutil.copy2(args.ie2, plugins / "InfinityExpansion2.jar")
    (runtime / "eula.txt").write_text("eula=true\n")
    (runtime / "server.properties").write_text(
        "server-ip=127.0.0.1\nserver-port=0\nonline-mode=false\nenforce-secure-profile=false\n"
        "level-type=minecraft:flat\ngenerate-structures=false\nview-distance=2\nsimulation-distance=2\n"
        "spawn-protection=0\nmax-tick-time=120000\nenable-rcon=false\nenable-query=false\nenable-status=false\n"
    )
    data = plugins / "InfinityExpansion2"
    data.mkdir()
    with zipfile.ZipFile(args.ie2) as jar:
        cards = jar.read("mob-simulation.yml").decode()
    cards += """
probe_config_neutral:
  enabled: true
  name: "Probe Config Neutral"
  guide-group: neutral
  texture: IRON_CHESTPLATE
  energy: 75
  experience: 1
  drops:
    - item: DIRT
      chance: 1
  recipe:
    pattern:
      - " A "
      - " X "
      - "   "
    ingredient:
      A:
        item: DIRT
        amount: 1
"""
    (data / "mob-simulation.yml").write_text(cards)
    classes = runtime / "probe-classes"
    classes.mkdir()
    classpath = list((runtime / "libraries").rglob("*.jar"))
    classpath += [args.core.resolve(), args.ie2.resolve(), args.kotlin_stdlib.resolve()]
    source = Path(__file__).with_name("NativeMobCardRefreshProbe.java").resolve()
    arguments = ["--release", "21", "-cp", ":".join(map(str, classpath)), "-d", str(classes), str(source)]
    argfile = runtime / "javac.args"
    # Argfiles prevent long runtime classpaths from exceeding the process argument limit.
    argfile.write_text("\n".join(json.dumps(argument) for argument in arguments))
    subprocess.run([str(args.java_home / "bin/javac"), "@" + str(argfile)], check=True)
    with zipfile.ZipFile(plugins / "NativeMobCardRefreshProbe.jar", "w", zipfile.ZIP_DEFLATED) as jar:
        for compiled in classes.rglob("*.class"):
            jar.write(compiled, compiled.relative_to(classes))
        jar.writestr("plugin.yml", 'name: NativeMobCardRefreshProbe\nversion: 1.0\nmain: verification.NativeMobCardRefreshProbe\napi-version: "1.21"\ndepend: [Slimefun, InfinityExpansion2]\n')
    hashes = {name: hashlib.sha256(path.read_bytes()).hexdigest() for name, path in {
        "core": args.core, "ie2": args.ie2, "server": runtime / "server.jar", "probe_source": source,
        "kotlin_stdlib": args.kotlin_stdlib, "kotlin_reflect": args.kotlin_reflect,
    }.items()}
    (runtime / "inputs.json").write_text(json.dumps(hashes, indent=2) + "\n")
    command = [str(args.java_home / "bin/java"), "-Xms256M", "-Xmx768M", "-XX:ActiveProcessorCount=2",
               "-Dterminal.jline=false", "-Dterminal.ansi=false", "-Djavax.net.ssl.trustStore=/etc/ssl/certs/java/cacerts", "-jar", "server.jar", "--nogui"]
    with (runtime / "console.log").open("w") as output:
        process = subprocess.Popen(command, cwd=runtime, stdout=output, stderr=subprocess.STDOUT, stdin=subprocess.DEVNULL)
        try:
            code = process.wait(timeout=args.timeout)
        except subprocess.TimeoutExpired:
            process.terminate()
            try:
                process.wait(timeout=20)
            except subprocess.TimeoutExpired:
                process.kill()
                process.wait()
            print("Native card probe timed out; inspect", runtime / "console.log")
            return 1
    result = plugins / "NativeMobCardRefreshProbe/results.txt"
    if result.exists():
        print(result.read_text())
    else:
        print("Native probe produced no result; inspect", runtime / "console.log")
        return 1
    return 0 if code == 0 and "failures: 0" in result.read_text() else 1


if __name__ == "__main__":
    sys.exit(main())
