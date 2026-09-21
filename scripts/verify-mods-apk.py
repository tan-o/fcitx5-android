"""Verify the deliverable, not just the Gradle task's exit status."""
import json
import os
import subprocess
from pathlib import Path
import zipfile

apks = list(Path('app/build/outputs/apk/release').glob('*.apk'))
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


# Check the compiled manifest, not just the Gradle variant name.
build_tools = Path(os.environ['ANDROID_HOME']) / 'build-tools' / '36.1.0'
badging = subprocess.check_output([str(build_tools / 'aapt'), 'dump', 'badging', str(apks[0])], text=True)
assert 'application-debuggable' not in badging, 'Release APK must not be debuggable'
assert "package: name='org.fcitx.fcitx5.android'" in badging, 'Unexpected release application ID'
verification = subprocess.run([str(build_tools / 'apksigner'), 'verify', str(apks[0])], capture_output=True, text=True)
if verification.returncode == 0:
    signing = 'signed'
    info = 'Release APK; debuggable=false; arm64-v8a; signature verified.\n'
else:
    assert apks[0].name.endswith('-unsigned.apk'), verification.stderr
    signing = 'unsigned'
    info = ('Release APK; debuggable=false; arm64-v8a; UNSIGNED, cannot be installed yet.\n'
            'Configure the existing signing key using GitHub Actions secrets:\n'
            'SIGN_KEY_BASE64, SIGN_KEY_PWD, SIGN_KEY_ALIAS. No new key was generated.\n')
    print('::warning::Release compiled without a signing key; artifact is explicitly labelled unsigned.')
Path('apks/BUILD-INFO.txt').write_text(info)
if os.environ.get('GITHUB_OUTPUT'):
    with open(os.environ['GITHUB_OUTPUT'], 'a') as output:
        output.write(f'signing={signing}\n')
if os.environ.get('GITHUB_STEP_SUMMARY'):
    with open(os.environ['GITHUB_STEP_SUMMARY'], 'a') as summary:
        summary.write(info)
print(info)
