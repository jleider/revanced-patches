"""Offline regression checks for concurrent discovery and device safety. No adb calls."""
import concurrent.futures
import importlib.util
import os
from pathlib import Path
import sys
import threading
import shutil
import subprocess
import tempfile
import tarfile
import io
import unittest
from unittest.mock import patch

sys.path.insert(0, str(Path(__file__).parent))
import driver
import live
import emulators
import features
from unittest.mock import Mock


class ToolingTests(unittest.TestCase):
    def test_physical_and_missing_serial_rejected_without_adb(self):
        for serial in ('', 'physical-device'):
            with self.subTest(serial=serial), patch.dict(os.environ, ANDROID_SERIAL=serial), patch('subprocess.run') as run:
                with self.assertRaises(RuntimeError):
                    driver.require_emulator()
                run.assert_not_called()

    def test_properties_must_identify_emulator(self):
        driver._require_emulator.cache_clear()
        with patch.dict(os.environ, ANDROID_SERIAL='emulator-5998'), patch('subprocess.run') as run:
            run.return_value.stdout = '0\n'
            with self.assertRaises(RuntimeError):
                driver.require_emulator()
            self.assertEqual(run.call_count, 2)

    def test_singleflight_avoids_duplicate_requests(self):
        calls = []
        @live.singleflight
        def find():
            calls.append(1)
            return 'result'
        with concurrent.futures.ThreadPoolExecutor(max_workers=6) as pool:
            self.assertEqual(list(pool.map(lambda _: find(), range(20))), ['result'] * 20)
        self.assertEqual(len(calls), 1)

    def test_prefetch_runs_concurrently_and_keeps_notfound(self):
        barrier = threading.Barrier(2)
        def found():
            barrier.wait(timeout=2)
            return 'result'
        misses = []
        def missing():
            misses.append(1)
            if len(misses) == 1:
                barrier.wait(timeout=2)
            raise live.NotFound('absent')
        with patch.dict(live.FINDERS, found=found, missing=missing):
            live.prefetch(['found', 'missing'])
            with self.assertRaises(live.NotFound):
                live.FINDERS['missing']()

    def test_pool_rejects_protected_targets_without_adb(self):
        with patch('subprocess.run') as run:
            for serial in ('emulator-5554', 'physical-device'):
                with self.assertRaises(RuntimeError):
                    emulators.adb(serial, 'emu', 'kill')
            run.assert_not_called()

    def test_looping_clip_progresses_but_frozen_timer_fails(self):
        for texts, should_fail in ((['00:03 / 00:04', '00:00 / 00:04'], False),
                                   (['00:02 / 00:04'], True)):
            with self.subTest(texts=texts):
                timer = Mock()
                timer.exists = True
                timer.wait.return_value = True
                def read():
                    return texts.pop(0) if len(texts) > 1 else texts[0]
                timer.get_text.side_effect = read
                context = features.Ctx.__new__(features.Ctx)
                context.d = Mock(return_value=timer)
                if should_fail:
                    with self.assertRaises(features.Fail):
                        context.expect_playing(timeout=0)
                else:
                    context.expect_playing(timeout=0)

    def test_negative_control_second_run_skips_archive_build_and_patch(self):
        # Execute the real shell workflow against an isolated fake toolchain; no device/network.
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            (root / 'scripts/e2e').mkdir(parents=True)
            (root / 'out').mkdir()
            (root / 'bin').mkdir()
            (root / 'out/sync-patched-debug.apk').write_bytes(b'current fixture')
            (root / 'secrets.env').write_text('REDDIT_CLIENT_ID=fixture\n')
            (root / 'env.sh').write_text('SYNC_REPO="$ROOT"\nexport PATH="$ROOT/bin:$PATH"\nsync_require_emulator() { :; }\n')
            script = Path(__file__).resolve().parents[1] / 'negative_control.sh'
            shutil.copy(script, root / 'scripts/negative_control.sh')
            def executable(path, content):
                path.write_text('#!/usr/bin/env bash\nset -eu\n' + content)
                path.chmod(0o755)
            executable(root / 'scripts/e2e/features.sh', 'echo "FAIL $1: fixture regression"; exit 1\n')
            executable(root / 'bin/adb', 'echo install >> "$CACHE_TEST_DIR/installs"\n')
            executable(root / 'bin/gh', 'echo fixture\n')
            executable(root / 'bin/git',
                       'case "$*" in *rev-parse*) echo aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa;; '
                       '*archive*) echo archive >> "$CACHE_TEST_DIR/work"; cat "$CACHE_TEST_DIR/fixture.tar";; esac\n')
            executable(root / 'bin/java',
                       'echo patch >> "$CACHE_TEST_DIR/work"\nwhile [ "$1" != -o ]; do shift; done\nshift\necho fixture > "$1"\n')
            with tarfile.open(root / 'fixture.tar', 'w') as archive:
                for name, data, mode in (
                    ('gradlew', b'#!/usr/bin/env bash\necho build >> "$CACHE_TEST_DIR/work"\n', 0o755),
                    ('patches/build/libs/patches-fixture.rvp', b'fixture', 0o644)):
                    entry = tarfile.TarInfo(name)
                    entry.size, entry.mode = len(data), mode
                    archive.addfile(entry, io.BytesIO(data))
            env = dict(os.environ, CACHE_TEST_DIR=str(root))
            command = ['bash', str(root / 'scripts/negative_control.sh'), 'fixture-ref', 'scenario']
            first = subprocess.run(command, env=env, capture_output=True, text=True)
            self.assertEqual(first.returncode, 0, first.stderr + first.stdout)
            work = (root / 'work').read_text()
            self.assertEqual(work.splitlines(), ['archive', 'build', 'patch'])
            second = subprocess.run(command, env=env, capture_output=True, text=True)
            self.assertEqual(second.returncode, 0, second.stderr + second.stdout)
            self.assertIn('== cached ', second.stdout)
            self.assertEqual((root / 'work').read_text(), work)
            self.assertEqual(len((root / 'installs').read_text().splitlines()), 4)

    def test_invalid_count_rejected(self):
        for value in ('0', '9', 'oops'):
            with patch.dict(os.environ, SYNC_EMULATORS=value):
                with self.assertRaises(RuntimeError):
                    emulators.hardware_count()


if __name__ == '__main__':
    unittest.main()
