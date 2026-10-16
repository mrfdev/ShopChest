#!/usr/bin/env python3
"""Run only newly created, loopback-only Paper fixtures. Never deploy to a source server."""
import argparse
import hashlib
import json
from pathlib import Path
import queue
import re
import shutil
import socket
import sqlite3
import subprocess
import tempfile
import threading
import time
import zipfile


def sha256(path):
    digest = hashlib.sha256()
    with path.open('rb') as source:
        for chunk in iter(lambda: source.read(1024 * 1024), b''):
            digest.update(chunk)
    return digest.hexdigest()


def require(condition, message):
    if not condition:
        raise AssertionError(message)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--paper-jar', type=Path, required=True)
    parser.add_argument('--dependencies', type=Path, required=True,
                        help='Read-only source of Vault, CMI, CMILib, LuckPerms, WorldEdit, WorldGuard JARs')
    parser.add_argument('--java-home', type=Path, required=True)
    args = parser.parse_args()
    root = Path(__file__).resolve().parents[2]
    properties = dict(line.split('=', 1) for line in (root / 'gradle.properties').read_text().splitlines()
                      if line and not line.startswith('#') and '=' in line)
    require(properties['shopchestPaperChannel'] == 'STABLE', 'Only STABLE is allowed')
    require(properties['shopchestPaperVersion'] == '26.3', 'Only Minecraft 26.3 is allowed')
    require(sha256(args.paper_jar) == properties['shopchestPaperSha256'], 'Paper SHA-256 mismatch')
    artifact_name = (f"1MB-ShopChest-v{properties['shopchestVersion']}-"
                     f"{int(properties['shopchestBuild']):03}-j25-26.3.jar")
    artifact = root / 'plugin/build/libs' / artifact_name
    require(artifact.is_file(), 'Build the release JAR first')
    wanted = {'Vault', 'CMI', 'CMILib', 'LuckPerms', 'WorldEdit', 'WorldGuard'}
    dependencies = {}
    for source in args.dependencies.glob('*.jar'):
        with zipfile.ZipFile(source) as jar:
            if 'plugin.yml' not in jar.namelist():
                continue
            descriptor = jar.read('plugin.yml').decode()
            match = re.search(r'^name:\s*[\'"]?([\w-]+)', descriptor, re.M)
            if match and match[1] in wanted:
                require(match[1] not in dependencies, f'Duplicate {match[1]} JAR')
                dependencies[match[1]] = source
    require(dependencies.keys() == wanted, f'Missing dependency JARs: {wanted - dependencies.keys()}')
    servers = root / 'servers'
    servers.mkdir(exist_ok=True)
    server = Path(tempfile.mkdtemp(prefix='paper-26.3-stable-', dir=servers))
    print(f'Isolated server and evidence: {server}', flush=True)
    plugins = server / 'plugins'
    plugins.mkdir()
    shutil.copy2(args.paper_jar, server / 'paper.jar')
    for jar in [artifact, *dependencies.values(),
                root / 'plugin/build/paper-smoke/PaperStableSmoke.jar',
                root / 'plugin/build/paper-smoke/ShopTransferSmoke.jar']:
        shutil.copy2(jar, plugins / jar.name)
    with socket.socket() as probe:
        probe.bind(('127.0.0.1', 0))
        port = probe.getsockname()[1]
    (server / 'server.properties').write_text(
        f'server-ip=127.0.0.1\nserver-port={port}\n'
        'online-mode=false\nwhite-list=true\nenforce-whitelist=true\n'
        'enable-query=false\nenable-rcon=false\nview-distance=2\nsimulation-distance=2\n'
        'level-name=stable-fixture\nlevel-type=minecraft:flat\ngenerate-structures=false\n'
        'generator-settings={"layers":[{"block":"minecraft:bedrock","height":1}],"biome":"minecraft:plains"}\n'
        'max-players=1\npause-when-empty-seconds=-1\nspawn-protection=0\n')
    (server / 'eula.txt').write_text('eula=true\n')
    for folder, config in {
        'CMI': 'Economy:\n  Enabled: true\n',
        'bStats': 'enabled: false\n',
        'ShopChest': 'storefront-discovery:\n  location-scope: GLOBAL\n',
    }.items():
        (plugins / folder).mkdir()
        (plugins / folder / 'config.yml').write_text(config)
    (server / 'config').mkdir()
    (server / 'config/paper-global.yml').write_text('spark:\n  enabled: false\n')
    receipt = {'paper_sha256': sha256(server / 'paper.jar'), 'artifact': artifact_name,
               'artifact_sha256': sha256(artifact), 'port': port,
               'dependencies': {n: {'file': p.name, 'sha256': sha256(p)} for n, p in dependencies.items()},
               'runs': []}
    java = args.java_home / 'bin/java'
    receipt['java'] = subprocess.run([str(java), '-version'], capture_output=True, text=True, check=True).stderr
    require(f'version "{properties["shopchestRuntimeJavaVersion"]}' in receipt['java'], 'Wrong runtime Java')

    def run(label, action, failure=None):
        healthy = failure is None
        with socket.socket() as probe:
            probe.bind(('127.0.0.1', port))
        log = server / f'verification-{label}.log'
        incoming = queue.Queue()
        with log.open('w') as output:
            process = subprocess.Popen([str(java), '-Xms512M', '-Xmx2G', '-jar', 'paper.jar', '--nogui'],
                                       cwd=server, stdin=subprocess.PIPE, stdout=subprocess.PIPE,
                                       stderr=subprocess.STDOUT, text=True, bufsize=1)
            def read():
                for line in process.stdout:
                    output.write(line)
                    output.flush()
                    incoming.put(line)
                incoming.put(None)
            reader = threading.Thread(target=read, daemon=True)
            reader.start()

            def wait(marker, timeout=120):
                end = time.monotonic() + timeout
                observed = []
                while time.monotonic() < end:
                    try:
                        line = incoming.get(timeout=1)
                    except queue.Empty:
                        continue
                    require(line is not None, f'{label}: exited before {marker}; see {log}')
                    observed.append(line)
                    require('_SMOKE_FAIL' not in line, f'{label}: fixture failed; see {log}')
                    if marker in line:
                        return ''.join(observed)
                raise AssertionError(f'{label}: timeout waiting for {marker}; see {log}')

            def command(value, marker):
                process.stdin.write(value + '\n')
                process.stdin.flush()
                return wait(marker)

            try:
                startup = wait('Done (', timeout=300)
                if healthy and 'shops in already loaded chunks' not in startup:
                    wait('shops in already loaded chunks')
                if failure and failure not in startup:
                    wait(failure)
                print(f'{label}: server ready', flush=True)
                if healthy:
                    status = command('shops debug status', '=== End ShopChest support report ===')
                    for value in [f"build {properties['shopchestBuild']} | target Java 25 / Paper 26.3",
                                  properties['shopchestPaperApiVersion'], 'Economy: CMIEconomy',
                                  'initialized yes | connection valid | schema 2']:
                        require(value in status, f'{label}: missing {value}; see {log}')
                    command('shops info', 'Open the ShopChest player guide:')
                    command(f'stablesmoke {action}', 'STABLE_SMOKE_PASS')
                    command('transfersmoke', 'TRANSFER_SMOKE_PASS')
                    command('shops reload', 'Successfully reloaded 2 shop/s.')
                    command('stablesmoke verify', 'STABLE_SMOKE_PASS')
                    audit = command('shops admin audit all', 'Dry run only:')
                    require('Ready: 2' in audit and 'Known issues: 0' in audit, f'{label}: audit failed')
                    command('shops admin advertise currency status', 'No AFK Shrine Token')
                    print(f'{label}: metadata, persistence, displays, transfers and audit passed', flush=True)
            finally:
                if process.poll() is None:
                    process.stdin.write('stop\n')
                    process.stdin.flush()
                    try:
                        process.wait(timeout=60)
                    except subprocess.TimeoutExpired:
                        process.kill()
                        process.wait(timeout=10)
                        raise AssertionError(f'{label}: clean shutdown timed out')
                reader.join(timeout=5)
        require(process.returncode == 0, f'{label}: process exit {process.returncode}')
        text = log.read_text()
        require('All RegionFile I/O tasks to complete' in text, f'{label}: missing completed world shutdown')
        if healthy:
            require('[ShopChest] Disabling ShopChest' in text and 'Shutdown completed.' in text,
                    f'{label}: missing plugin/database shutdown')
            errors = [line for line in text.splitlines() if 'ERROR' in line or 'Exception' in line]
            require(not errors, f'{label}: unexpected errors: {errors[:8]}; see {log}')
            dbpath = plugins / 'ShopChest/shops.db'
            with sqlite3.connect(dbpath.as_uri() + '?mode=ro', uri=True) as db:
                require(db.execute('PRAGMA integrity_check').fetchone()[0] == 'ok', 'SQLite integrity failed')
                rows = db.execute('SELECT * FROM shopchest_shops ORDER BY id').fetchall()
                require(len(rows) == 2, 'Expected two persisted shops')
                if 'shops' in receipt:
                    require(rows == receipt['shops'], 'Persisted shop rows changed across restart')
                receipt['shops'] = rows
        else:
            require(failure in text, 'Expected dependency failure was not detected')
            require('[ShopChest] Disabling ShopChest' in text, 'Dependency failure did not disable ShopChest')
            require('Asynchronous' not in text and 'Could not pass event' not in text,
                    'Dependency failure violated the scheduler contract')
        receipt['runs'].append({'label': label, 'exit': process.returncode, 'log': str(log)})
        (server / 'verification.json').write_text(json.dumps(receipt, indent=2) + '\n')
        print(f'{label}: clean shutdown passed', flush=True)

    run('first-start', 'seed')
    run('restart', 'verify')
    # A directory in place of the fixture DB makes SQLite connection fail deterministically.
    dbpath = plugins / 'ShopChest/shops.db'
    saved_db = plugins / 'ShopChest/shops.db.successful-fixture'
    dbpath.rename(saved_db)
    dbpath.mkdir()
    try:
        run('unavailable-database', None, failure='No database access. Disabling ShopChest')
    finally:
        dbpath.rmdir()
        saved_db.rename(dbpath)
    # Dependency failure uses only the disposable fixture, never source plugin directories.
    disabled = server / 'plugins-disabled'
    disabled.mkdir()
    for source in list(plugins.glob('*.jar')):
        if source.name not in {artifact.name, dependencies['Vault'].name}:
            source.rename(disabled / source.name)
    run('missing-economy', None, failure='Could not find any Vault economy dependency!')
    print(f'PASS: {server / "verification.json"}', flush=True)


if __name__ == '__main__':
    main()
