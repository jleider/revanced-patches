"""Verify persisted gallery position in both directions, independent of prior cache."""
import json
import sys
import time

from driver import PKG, ROOT, connect, screenshot, wait

COUNTER = r'^\d+ / \d+$'


def main():
    post = json.loads((ROOT / 'tests/fixtures/gallery_comments_endpoint.json').read_text())[0]['data']['children'][0]['data']
    url = f"https://www.reddit.com/r/{post['subreddit']}/comments/{post['id']}?is_gallery=true"
    d = connect()

    def open_gallery():
        d.app_stop(PKG)
        d.shell(['am', 'start', '-W', '-a', 'android.intent.action.VIEW', '-d', url, '-p', PKG])
        wait(d, timeout=20, textMatches=COUNTER)

    def close_gallery():
        d.press('back')
        if not d(textMatches=COUNTER).wait_gone(timeout=10):
            raise RuntimeError('gallery did not close')
        # Activity teardown writes Volley's disk cache asynchronously.
        time.sleep(3)

    for want in (1, 2):
        open_gallery()
        for _ in range(4):
            current = int(wait(d, textMatches=COUNTER).get_text().split(' / ')[0])
            if current == want:
                break
            d.swipe_ext('left' if current < want else 'right', scale=0.7)
            d(textMatches=rf'^{want} / \d+$').wait(timeout=2)
        wait(d, textMatches=rf'^{want} / \d+$')
        close_gallery()
        open_gallery()
        if not d(textMatches=rf'^{want} / \d+$').wait(timeout=15):
            raise RuntimeError(f'closed at image {want}, reopened at a different position')
        screenshot(d, f'gallery-resumed-{want}.png')
        close_gallery()
    print('PASS gallery_position (resumed at image 1 and image 2)')


if __name__ == '__main__':
    try:
        main()
    except Exception as e:
        print('FAIL gallery_position: ' + str(e))
        sys.exit(1)
