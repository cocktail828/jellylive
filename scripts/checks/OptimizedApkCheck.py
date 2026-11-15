"""Check actual delivery artifacts without reading or printing local configuration values."""
import argparse
import hashlib
import json
import struct
from pathlib import Path
from zipfile import ZipFile


def classes(data):
    def word(offset):
        return struct.unpack_from('<I', data, offset)[0]

    strings = []
    for index in range(word(56)):
        offset = word(word(60) + index * 4)
        while data[offset] & 128:
            offset += 1
        offset += 1
        strings.append(data[offset:data.index(b'\0', offset)].decode('utf8', errors='replace'))
    types = [strings[word(word(68) + index * 4)] for index in range(word(64))]
    return [types[word(word(100) + index * 32)] for index in range(word(96))]


def inspect(path):
    with ZipFile(path) as archive:
        entries = archive.infolist()
        names = [entry.filename for entry in entries]
        defined = [name for entry in entries if entry.filename.endswith('.dex') for name in classes(archive.read(entry))]
        digest = lambda entry: hashlib.sha256(archive.read(entry)).hexdigest()
        licenses = {}
        for entry in entries:
            if entry.filename.startswith('res/') and entry.filename.endswith('.json'):
                value = json.loads(archive.read(entry))
                if isinstance(value, dict) and 'licenses' in value and 'libraries' in value:
                    licenses = value['licenses']
        return dict(bytes=path.stat().st_size, crc_ok=archive.testzip() is None,
                    duplicate_entries=len(names) != len(set(names)),
                    duplicate_classes=len(defined) != len(set(defined)), classes=defined,
                    dex_bytes=sum(entry.compress_size for entry in entries if entry.filename.endswith('.dex')),
                    resource_bytes=sum(entry.compress_size for entry in entries if entry.filename.startswith('res/') or entry.filename == 'resources.arsc'),
                    native={entry.filename: digest(entry) for entry in entries if entry.filename.startswith('lib/')},
                    assets={entry.filename: digest(entry) for entry in entries if entry.filename.startswith('assets/')}, licenses=licenses)


if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('--baseline', required=True, type=Path)
    parser.add_argument('--apk', required=True, type=Path)
    parser.add_argument('--mapping', type=Path)
    args = parser.parse_args()
    baseline, actual = inspect(args.baseline), inspect(args.apk)
    tooling = lambda name: name.startswith('androidx.compose.ui.tooling.') and not name.startswith('androidx.compose.ui.tooling.preview.')
    origins = [name[1:-1].replace('/', '.') for name in actual['classes']]
    if args.mapping:
        origins += [line.split(' -> ')[0] for line in args.mapping.read_text().splitlines() if line and not line[0].isspace() and not line.startswith('#') and ' -> ' in line]
    abis = {name.split('/')[1] for name in actual['native']}
    profiles = {'assets/dexopt/baseline.prof', 'assets/dexopt/baseline.profm'}
    checks = {
        'archive CRC valid': actual['crc_ok'],
        'no duplicate entries or classes': not actual['duplicate_entries'] and not actual['duplicate_classes'],
        'all four ABIs retained': abis == {'armeabi-v7a', 'arm64-v8a', 'x86', 'x86_64'},
        'native libraries byte-identical': actual['native'] == baseline['native'],
        'local assets byte-identical': {k: v for k, v in actual['assets'].items() if k not in profiles} == {k: v for k, v in baseline['assets'].items() if k not in profiles},
        'original license bodies retained': bool(actual['licenses']) and all(actual['licenses'].get(key, {}).get('content') == value['content'] for key, value in baseline['licenses'].items() if value.get('content')),
        'APK smaller than clean baseline': actual['bytes'] < baseline['bytes'],
        'DEX payload reduced': actual['dex_bytes'] < baseline['dex_bytes'],
        'resource payload reduced': actual['resource_bytes'] < baseline['resource_bytes'],
        'class definitions reduced': len(actual['classes']) < len(baseline['classes']),
        'full Compose preview tooling absent': not any(tooling(name) for name in origins),
    }
    for name, ok in checks.items():
        print(('PASS ' if ok else 'FAIL ') + name)
    print(json.dumps(dict(baseline_bytes=baseline['bytes'], apk_bytes=actual['bytes'], dex_bytes=actual['dex_bytes'],
                          resource_bytes=actual['resource_bytes'], classes=len(actual['classes']),
                          checks=len(checks), failures=sum(not ok for ok in checks.values()))))
    raise SystemExit(0 if all(checks.values()) else 1)
