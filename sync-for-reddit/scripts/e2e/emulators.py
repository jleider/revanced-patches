"""Resource-sized, explicitly owned headless emulator pool and feature shard runner."""
import concurrent.futures
import fcntl
import json
import os
from pathlib import Path
import re
import shutil
import socket
import subprocess
import sys
import time

ROOT = Path(__file__).resolve().parents[2]
STATE = ROOT / 'out/emulators'
GIB = 1024 ** 3


def hardware_count():
    override = os.environ.get('SYNC_EMULATORS')
    if override is not None:
        if not override.isdigit() or not 1 <= int(override) <= 8:
            raise RuntimeError('SYNC_EMULATORS must be an integer from 1 to 8')
        return int(override)
    cores = os.cpu_count() or 2
    try:
        if sys.platform == 'darwin':
            total = int(subprocess.check_output(['sysctl', '-n', 'hw.memsize']))
            vm = subprocess.check_output(['vm_stat'], text=True)
            page = int(re.search(r'page size of (\d+)', vm)[1])
            available = sum(int(re.search(rf'Pages {name}:\s+(\d+)', vm)[1])
                            for name in ('free', 'inactive', 'speculative')) * page
        else:
            mem = dict(re.findall(r'^(\w+):\s+(\d+)', Path('/proc/meminfo').read_text(), re.M))
            total, available = int(mem['MemTotal']) * 1024, int(mem['MemAvailable']) * 1024
        disk = shutil.disk_usage(ROOT).free
        count = max(1, min(4, cores // 2, int((total - 4 * GIB) // (3 * GIB)),
                           int((available - 2 * GIB) // (3 * GIB)), int(disk // (4 * GIB))))
    except (OSError, ValueError, TypeError, subprocess.CalledProcessError):
        count = 1  # conservative if hardware queries are restricted
    value = os.environ.get('SYNC_EMULATORS', str(count))
    if not value.isdigit() or not 1 <= int(value) <= 8:
        raise RuntimeError('SYNC_EMULATORS must be an integer from 1 to 8')
    return int(value)


def adb(serial, *args, **kw):
    if not re.fullmatch(r'emulator-\d+', serial) or serial == 'emulator-5554':
        raise RuntimeError('refusing unowned/protected serial')
    return subprocess.run(['adb', '-s', serial, *args], check=True, **kw)


def load():
    path = STATE / 'pool.json'
    return json.loads(path.read_text()) if path.exists() else []


def save(pool):
    (STATE / 'pool.json').write_text(json.dumps(pool))


def guard(serial):
    if not re.fullmatch(r'emulator-\d+', serial) or serial == 'emulator-5554':
        raise RuntimeError('refusing unowned/protected serial')
    env = dict(os.environ, ANDROID_SERIAL=serial)
    subprocess.run(['bash', '-c', 'source "$SYNC_DIR/env.sh"; sync_require_emulator'], env=env, check=True)


def down():
    remaining = []
    for item in load():
        # PID plus live process command protects against stale state/PID reuse.
        cmd = subprocess.run(['ps', '-p', str(item['pid']), '-o', 'command='], capture_output=True, text=True).stdout
        if 'sync34' in cmd and f"-port {item['port']}" in cmd:
            try:
                guard(item['serial'])
                adb(item['serial'], 'emu', 'kill', stdout=subprocess.DEVNULL)
            except subprocess.CalledProcessError:
                remaining.append(item)
    save(remaining)
    if remaining:
        raise RuntimeError('could not stop all owned emulators; retry down')


def up(count):
    pool = load()
    if pool:
        for item in pool:
            adb(item['serial'], 'get-state', stdout=subprocess.DEVNULL)
        if len(pool) >= count:
            return pool[:count]
    apk = ROOT / 'out/sync-patched-debug.apk'
    if not apk.exists() or not (ROOT / 'out/appstate/baseline.tgz').exists():
        raise RuntimeError('debug APK and saved app-state baseline are required')
    try:
        for port in range(5556, 5680, 2):
            if len(pool) == count:
                break
            sockets = []
            try:
                for p in (port, port + 1):
                    sock = socket.socket()
                    sockets.append(sock)
                    sock.bind(('127.0.0.1', p))
            except OSError:
                continue
            finally:
                for sock in sockets:
                    sock.close()
            serial = f'emulator-{port}'
            with (STATE / f'{serial}.log').open('ab') as log:
                proc = subprocess.Popen(['emulator', '-avd', 'sync34', '-read-only', '-no-window',
                                         '-no-audio', '-no-snapshot-save', '-memory', '3072',
                                         '-cores', '2', '-port', str(port)], stdout=log, stderr=log, start_new_session=True)
            pool.append(dict(serial=serial, port=port, pid=proc.pid))
            save(pool)  # record ownership before boot/install, including interrupted startup
        if len(pool) != count:
            raise RuntimeError('no free emulator ports')
        def prepare(item):
            serial = item['serial']
            env = dict(os.environ, ANDROID_SERIAL=serial)
            deadline = time.monotonic() + 240
            while time.monotonic() < deadline:
                cmd = subprocess.run(['ps', '-p', str(item['pid']), '-o', 'command='], capture_output=True, text=True).stdout
                if 'sync34' not in cmd:
                    raise RuntimeError(f'{serial} exited during boot (see out/emulators/{serial}.log)')
                result = subprocess.run(['adb', '-s', serial, 'shell', 'getprop', 'sys.boot_completed'],
                                        capture_output=True, text=True, timeout=10)
                if result.stdout.strip() == '1':
                    break
                time.sleep(1)
            else:
                raise RuntimeError(f'{serial} boot timed out')
            # Keep the common state-changing guard, with an explicit serial.
            subprocess.run(['bash', '-c', 'source "$SYNC_DIR/env.sh"; sync_require_emulator'], env=env, check=True)
            adb(serial, 'root', stdout=subprocess.DEVNULL)
            adb(serial, 'wait-for-device', timeout=30)
            adb(serial, 'install', '-r', str(apk), stdout=subprocess.DEVNULL)
            subprocess.run([str(ROOT / 'scripts/e2e/app_state.sh'), 'restore'], env=env, check=True,
                           stdout=subprocess.DEVNULL)
        with concurrent.futures.ThreadPoolExecutor(max_workers=count) as executor:
            list(executor.map(prepare, pool))
        return pool
    except BaseException:
        down()
        raise


def run(names):
    sys.path.insert(0, str(Path(__file__).parent))
    import features
    names = names or list(features.SCENARIOS)
    unknown = set(names) - features.SCENARIOS.keys()
    if unknown:
        raise RuntimeError(f'unknown scenario(s): {sorted(unknown)}')
    count = min(hardware_count(), len(names))
    pool = up(count)
    def shard(index):
        serial = pool[index]['serial']
        log = STATE / f'{serial}-features.log'
        env = dict(os.environ, ANDROID_SERIAL=serial, SYNC_FEATURE_WORKER='1', PYTHONUNBUFFERED='1',
                   SHOTS=str(STATE / serial), SYNC_HOSTLOG=str(STATE / f'{serial}-hosts.log'))
        with log.open('w') as output:
            result = subprocess.run([str(ROOT / 'scripts/e2e/features.sh'), *names[index::count]],
                                    env=env, stdout=output, stderr=subprocess.STDOUT)
        return result.returncode, log.read_text()
    started = time.monotonic()
    with concurrent.futures.ThreadPoolExecutor(max_workers=count) as executor:
        results = list(executor.map(shard, range(count)))
    totals = {kind: 0 for kind in ('PASS', 'FAIL', 'SKIP')}
    for code, output in results:
        for line in output.splitlines():
            if re.match(r'^\d+ passed,', line):
                continue
            print(line)
            for kind in totals:
                if line.startswith(kind + ' '):
                    totals[kind] += 1
    print(f"{totals['PASS']} passed, {totals['FAIL']} failed" +
          (f", {totals['SKIP']} skipped (inconclusive)" if totals['SKIP'] else ''))
    print(f'TIME features: {time.monotonic() - started:.1f}s ({count} shards)')
    return int(any(code for code, _ in results))


if __name__ == '__main__':
    STATE.mkdir(parents=True, exist_ok=True)
    with (STATE / 'lock').open('w') as lock:
        fcntl.flock(lock, fcntl.LOCK_EX)  # one owner of the pool for an entire suite
        try:
            action = sys.argv[1] if len(sys.argv) > 1 else ''
            if action == 'up':
                print(' '.join(item['serial'] for item in up(hardware_count())))
            elif action == 'down':
                down()
            elif action == 'run':
                sys.exit(run(sys.argv[2:]))
            else:
                sys.exit('usage: emulators.sh up|down|run [scenario ...]')
        except (RuntimeError, subprocess.SubprocessError) as e:
            sys.exit(f'FAIL: {e}')
