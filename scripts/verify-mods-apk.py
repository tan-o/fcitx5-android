"""Verify the deliverable, not just the Gradle task's exit status."""
import json
from pathlib import Path
import zipfile

apks = list(Path('app/build/outputs/apk/debug').glob('*.apk'))
assert len(apks) == 1, f'Expected one main APK, got {apks}'
model = 'usr/share/rime-data/wanxiang-lts-zh-hans.gram'
with zipfile.ZipFile(apks[0]) as apk:
    assert 'lib/arm64-v8a/librime.so' in apk.namelist(), 'Embedded Rime library missing'
    assert 'lib/arm64-v8a/libonnxruntime.so' in apk.namelist(), 'Handwriting ONNX Runtime missing'
    assert not any(p.startswith('lib/') and not p.startswith('lib/arm64-v8a/') for p in apk.namelist() if p.endswith('.so')), 'Unexpected non-arm64 library'
    assert not any(p.endswith('.onnx') for p in apk.namelist()), 'Downloadable model weights must not be bundled'
    for name in ('moqi_chaifen_all.json', 'moqi_chaifen_all.txt'):
        asset = 'rime/opencc/fcitx-components/' + name
        assert apk.read('assets/' + asset) == Path('app/src/main/assets', asset).read_bytes(), asset
    assert 'assets/rime/fcitx_components.dict.yaml' not in apk.namelist()
    assert 'assets/rime/lua/fcitx_radical_filter.lua' not in apk.namelist()
    descriptor = json.loads(apk.read('assets/descriptor.json'))
    assert model not in descriptor['files'], 'Optional model must not be in the installation descriptor'
    assert not any(path.endswith('.gram') for path in apk.namelist()), 'Optional model must not be bundled'
    assert 'assets/usr/share/rime-data/luna_pinyin.schema.yaml' in apk.namelist()
    assert not any('/wanxiang.schema.yaml' in path for path in apk.namelist()), 'Unexpected Wanxiang scheme'
    assert 'assets/handwriting/handwriting-zh_CN.model' not in apk.namelist()
print(f'Verified {apks[0]}: embedded Rime; handwriting and Wanxiang models download on demand')

