#!/usr/bin/env python3
"""Fetch the pinned upstream model used by the APK; never accept a changed payload."""
import hashlib
from pathlib import Path
import urllib.request

url = 'https://github.com/amzxyz/RIME-LMDG/releases/download/LTS/wanxiang-lts-zh-hans.gram'
expected = 'b91d525f118b24871cfc82b47b32921cdbc92a43cea42419a88afe4f780b278c'
target = Path(__file__).resolve().parents[1] / 'app/src/main/assets/usr/share/rime-data/wanxiang-lts-zh-hans.gram'
target.parent.mkdir(parents=True, exist_ok=True)
if target.is_file() and hashlib.file_digest(target.open('rb'), 'sha256').hexdigest() == expected:
    print('Wanxiang model already verified')
else:
    temporary = target.with_suffix('.download')
    digest = hashlib.sha256()
    size = 0
    try:
        with urllib.request.urlopen(url, timeout=60) as source, temporary.open('wb') as output:
            while block := source.read(1024 * 1024):
                size += len(block)
                if size > 450_000_000:
                    raise RuntimeError('Unexpected model size')
                digest.update(block)
                output.write(block)
        if size != 420340780 or digest.hexdigest() != expected:
            raise RuntimeError('Upstream model changed; review and update the pinned digest')
        temporary.replace(target)
        print(f'Wanxiang model verified: {size} bytes, SHA256 {expected}')
    finally:
        temporary.unlink(missing_ok=True)
