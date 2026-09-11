"""Verify the deliverable, not just the Gradle task's exit status."""
import json
from pathlib import Path
import zipfile

apks = list(Path('app/build/outputs/apk/debug').glob('*.apk'))
assert len(apks) == 1, f'Expected one main APK, got {apks}'
model = 'usr/share/rime-data/wanxiang-lts-zh-hans.gram'
with zipfile.ZipFile(apks[0]) as apk:
    assert 'lib/arm64-v8a/librime.so' in apk.namelist(), 'Embedded Rime library missing'
    descriptor = json.loads(apk.read('assets/descriptor.json'))
    assert model not in descriptor['files'], 'Optional model must not be in the installation descriptor'
    assert not any(path.endswith('.gram') for path in apk.namelist()), 'Optional model must not be bundled'
    assert 'assets/usr/share/rime-data/luna_pinyin.schema.yaml' in apk.namelist()
    assert not any('/wanxiang.schema.yaml' in path for path in apk.namelist()), 'Unexpected Wanxiang scheme'
    assert 'assets/handwriting/handwriting-zh_CN.model' not in apk.namelist()
print(f'Verified {apks[0]}: embedded Rime; handwriting and Wanxiang models download on demand')
