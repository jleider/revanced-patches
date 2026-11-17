"""OAuth login. Never dump page content, trace, exceptions, or credential screens."""
import os
import re
import sys
import time

from driver import PKG, account_picker, connect, home, tap, webview_page


class LoginFailure(Exception):
    pass


REDIRECT_FAILURE = ('Reddit says Invalid or missing redirect_uri parameter; '
                    'set the OAuth app redirect URI to http://redditsync/auth at '
                    'https://old.reddit.com/prefs/apps')


def bad_redirect(page):
    return page.get_by_text(re.compile(r'Invalid or missing redirect_uri|redirect_uri parameter', re.I)).count() > 0


def main():
    username, password = os.environ.get('SYNC_TEST_USER'), os.environ.get('SYNC_TEST_PASS')
    if not username or not password:
        raise LoginFailure('set SYNC_TEST_USER and SYNC_TEST_PASS in the environment')
    d = connect()
    d.app_stop(PKG)
    home(d)
    account_picker(d)
    if d(text=username).exists:
        print('PASS login (already logged in)')
        d.press('back')
        return
    tap(d, text='Add account')
    with webview_page(d) as page:
        # CSS locators pierce Reddit's open shadow roots.
        user = page.locator('input[name="username"], input[autocomplete="username"]').first
        submitted = False
        deadline = time.monotonic() + 60
        consent = page.locator('button, input[type="submit"], a').filter(has_text=re.compile(r'^\s*(Allow|Accept)\s*$', re.I))
        # Old Reddit uses an input with value=Allow, modern Reddit a button.
        consent = consent.or_(page.locator('input[type="submit"][value="Allow" i], input[type="submit"][value="Accept" i]')).first
        while time.monotonic() < deadline:
            if bad_redirect(page):
                raise LoginFailure(REDIRECT_FAILURE)
            if consent.is_visible():
                consent.click()
                break
            if not submitted and user.is_visible():
                user.fill(username)
                page.locator('input[type="password"]').first.fill(password)
                page.get_by_role('button', name=re.compile(r'^log\s*in$', re.I)).click()
                submitted = True
            page.wait_for_timeout(250)
        else:
            raise LoginFailure('authorize page never appeared after login (check credentials or Reddit challenge)')
        deadline = time.monotonic() + 30
        while time.monotonic() < deadline:
            if bad_redirect(page):
                raise LoginFailure(REDIRECT_FAILURE)
            if not d(className='android.webkit.WebView').exists:
                break
            page.wait_for_timeout(250)
    home(d)
    account_picker(d)
    if not d(text=username).wait(timeout=15):
        raise LoginFailure('account picker does not show the test account')
    d.press('back')
    print('PASS login (account picker shows test account)')


if __name__ == '__main__':
    try:
        main()
    except LoginFailure as e:
        # Only our fixed messages are safe: library errors can include filled values.
        print('FAIL login: ' + str(e))
        sys.exit(1)
    except Exception:
        print('FAIL login: UI/CDP operation failed (details suppressed to protect credentials)')
        sys.exit(1)
