"""mitmdump addon: record every host the app connects to, without decrypting anything.

Run with `--ignore-hosts '.*'` so TLS passes through untouched (no CA on the device). Every upstream
connection (TLS passthrough or plain HTTP) is recorded in server_connect. One line per connection is
appended to $HOSTLOG: "<epoch> <scheme> <host>:<port>".
"""
import os
import time

LOG = os.environ.get("HOSTLOG", "/tmp/sync-hosts.log")


def _write(scheme, host, port):
    with open(LOG, "a") as f:
        f.write(f"{time.time():.3f} {scheme} {host}:{port}\n")


def server_connect(data):
    # Fires for every upstream connection, including ignored (passthrough) TLS hosts.
    address = data.server.address
    if address:
        _write("conn", address[0], address[1])
