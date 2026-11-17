"""Native selectors via uiautomator2; Sync's debug WebView via Playwright CDP."""
from contextlib import contextmanager
import os
import functools
from pathlib import Path
import re
import subprocess
import time

import uiautomator2 as u2
from playwright.sync_api import sync_playwright

PKG = 'com.laurencedawson.reddit_sync'
ROOT = Path(__file__).resolve().parents[2]   # sync-for-reddit/
SHOTS = Path(os.environ.get('SHOTS', str(Path(os.environ.get('TMPDIR', '/tmp')) / 'sync-shots')))


@functools.lru_cache(maxsize=None)
def _require_emulator(serial):
    if not re.fullmatch(r'emulator-\d+', serial):
        raise RuntimeError('ANDROID_SERIAL must explicitly select an emulator')
    for prop in ('ro.kernel.qemu', 'ro.boot.qemu'):
        result = subprocess.run(['adb', '-s', serial, 'shell', 'getprop', prop],
                                capture_output=True, text=True, check=True)
        if result.stdout.strip() == '1':
            return serial
    raise RuntimeError('selected device is not an emulator')


def require_emulator():
    return _require_emulator(os.environ.get('ANDROID_SERIAL', ''))


def connect():
    return u2.connect(require_emulator())


def wait(d, timeout=20, **selector):
    node = d(**selector)
    if not node.wait(timeout=timeout):
        raise RuntimeError('native element did not appear')
    return node


def tap(d, timeout=20, **selector):
    wait(d, timeout, **selector).click()


def set_text(d, value, timeout=20, **selector):
    wait(d, timeout, **selector).set_text(value)


def screenshot(d, name):
    """Call only on screens without credentials; use fixed artifact names."""
    SHOTS.mkdir(parents=True, exist_ok=True)
    path = SHOTS / name
    d.screenshot(str(path))
    return path


def home(d):
    d.shell(['am', 'start', '-W', '-n', PKG + '/.ui.activities.HomeActivity.Default'])


def account_picker(d):
    # Toolbar's direct ImageView child is the avatar: no id or description in Sync.
    # Scope by toolbar id and child type instead of screen coordinates.
    toolbar = wait(d, resourceId=PKG + ':id/toolbar')
    toolbar.child(className='android.widget.ImageView', clickable=True).click()
    wait(d, text='Add account')


@contextmanager
def webview_page(d, timeout=30):
    """Attach only to this app's PID; remove our ephemeral adb forward on exit."""
    def adb(*args):
        return subprocess.check_output(['adb', '-s', d.serial, *args], text=True).strip()

    deadline = time.monotonic() + timeout
    port = None
    try:
        while time.monotonic() < deadline:
            pids = d.shell(['pidof', PKG]).output.split()
            sockets = d.shell(['cat', '/proc/net/unix']).output
            matches = re.findall(r'@?(webview_devtools_remote_(\d+))\b', sockets)
            socket = next((name for name, pid in matches if pid in pids), None)
            if socket:
                port = adb('forward', 'tcp:0', 'localabstract:' + socket)
                break
            time.sleep(0.25)
        if not port:
            raise RuntimeError('Sync WebView devtools socket missing; install verify_patch.sh --debug build')
        with sync_playwright() as pw:
            # WebView rejects Browser.setDownloadBehavior/context management.
            browser = pw.chromium.connect_over_cdp('http://127.0.0.1:' + port, timeout=timeout * 1000, no_defaults=True)
            try:
                while time.monotonic() < deadline:
                    pages = [p for c in browser.contexts for p in c.pages]
                    page = next((p for p in pages if 'reddit.com' in p.url), None)
                    if page:
                        page.set_default_timeout(timeout * 1000)
                        yield page
                        return
                    time.sleep(0.25)
                raise RuntimeError('Reddit page did not appear in Sync WebView')
            finally:
                browser.close()  # CDP disconnect; does not stop Sync.
    finally:
        if port:
            adb('forward', '--remove', 'tcp:' + port)
