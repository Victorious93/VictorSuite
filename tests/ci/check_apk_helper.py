"""Assert that each APK contains the helper required by TermuxApiEmbed.

Usage: python3 probe_apk_helper.py APP.apk [APP.apk ...]
"""
import pathlib
import sys
import zipfile

failed = 0
for filename in sys.argv[1:]:
    with zipfile.ZipFile(filename) as apk:
        names = set(apk.namelist())
        abis = sorted({name.split('/')[1] for name in names if name.startswith('lib/') and name.endswith('.so')})
        print(pathlib.Path(filename).name)
        if not abis:
            print('  FAIL: APK contains no native ABI libraries')
            failed += 1
        for abi in abis:
            expected = f'lib/{abi}/libtermux-api-helper.so'
            if expected not in names or apk.getinfo(expected).file_size == 0:
                print(f'  FAIL: required native helper missing or empty: {expected}')
                failed += 1
            else:
                print(f'  PASS: {expected}')
if len(sys.argv) < 2:
    raise SystemExit('Pass at least one APK path')
raise SystemExit(1 if failed else 0)
