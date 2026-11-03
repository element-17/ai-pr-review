#!/usr/bin/env python3
"""Refresh a private Mihomo file provider without exposing the subscription URL."""

import os
from pathlib import Path
import subprocess
import sys
import tempfile
import urllib.request

import yaml

ROOT = Path('/opt/ai-pr-review/deploy/github-proxy')
PROVIDER = ROOT / 'provider' / 'subscription.yaml'
MAX_BYTES = 5_000_000


def reload_provider():
    request = (b'PUT /providers/proxies/subscription HTTP/1.1\r\n'
               b'Host: localhost\r\nContent-Length: 0\r\nConnection: close\r\n\r\n')
    result = subprocess.run(
        ['docker', 'exec', '-i', 'deploy-github-proxy-1', 'nc', '-w', '30', '127.0.0.1', '19090'],
        input=request, capture_output=True, timeout=35, check=True,
    )
    if not result.stdout.startswith((b'HTTP/1.1 204 ', b'HTTP/1.1 200 ')):
        raise RuntimeError('Provider reload failed')


def main():
    url = (ROOT / 'subscription.url').read_text().strip()
    request = urllib.request.Request(url, headers={'User-Agent': 'clash.meta'})
    with urllib.request.urlopen(request, timeout=35) as response:
        payload = response.read(MAX_BYTES + 1)
    if len(payload) > MAX_BYTES:
        raise ValueError('Subscription exceeds size limit')
    data = yaml.safe_load(payload)
    if not isinstance(data, dict) or not isinstance(data.get('proxies'), list) or not data['proxies']:
        raise ValueError('Subscription has no nodes')
    # A small server cannot probe dozens of nodes concurrently. Keep a diverse,
    # bounded set; the subscription is re-evaluated at every refresh.
    nodes = []
    seen = set()
    for protocol, limit in [('vless', 3), ('hysteria2', 2), ('ss', 1)]:
        choices = [node for node in data['proxies'] if isinstance(node, dict) and node.get('type') == protocol]
        if choices:
            for index in range(min(limit, len(choices))):
                node = choices[index * len(choices) // min(limit, len(choices))]
                identity = (node.get('server'), node.get('port'))
                if identity not in seen:
                    nodes.append(node)
                    seen.add(identity)
    if not nodes:
        raise ValueError('Subscription has no supported nodes')
    payload = yaml.safe_dump({'proxies': nodes}, allow_unicode=True, sort_keys=False).encode()

    with tempfile.TemporaryDirectory(prefix='github-proxy-check-') as temporary:
        Path(temporary, 'subscription.yaml').write_bytes(payload)
        subprocess.run([str(ROOT / 'mihomo'), '-t', '-d', temporary, '-f', str(ROOT / 'config.yaml')],
                       stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL, timeout=45, check=True)

    old = PROVIDER.read_bytes()
    fd, temporary = tempfile.mkstemp(prefix='.subscription-', dir=PROVIDER.parent)
    try:
        os.fchmod(fd, 0o600)
        os.fchown(fd, 101, 101)
        with os.fdopen(fd, 'wb') as stream:
            stream.write(payload)
        os.replace(temporary, PROVIDER)
        try:
            reload_provider()
        except Exception:
            fd, previous = tempfile.mkstemp(prefix='.subscription-rollback-', dir=PROVIDER.parent)
            os.fchmod(fd, 0o600)
            os.fchown(fd, 101, 101)
            with os.fdopen(fd, 'wb') as stream:
                stream.write(old)
            os.replace(previous, PROVIDER)
            raise
    finally:
        if os.path.exists(temporary):
            os.unlink(temporary)
    print(f'Subscription refreshed: {len(nodes)} selected nodes')


if __name__ == '__main__':
    try:
        main()
    except Exception as error:
        # Exceptions from HTTP libraries can contain the secret subscription URL.
        print(f'Subscription refresh failed: {type(error).__name__}', file=sys.stderr)
        sys.exit(1)
