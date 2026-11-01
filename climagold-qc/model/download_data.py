"""Download pinned public dataset mirrors and verify the exact training input hashes."""
import argparse, hashlib, json, urllib.request
from pathlib import Path
p=argparse.ArgumentParser();p.add_argument('--out',type=Path,required=True);a=p.parse_args()
a.out.mkdir(parents=True,exist_ok=True)
hashes=json.loads((Path(__file__).parent/'evaluation.json').read_text())['sources_sha256']
for name,expected in hashes.items():
    root=('akshaybahadur21/Alphabet-Recognition-EMNIST/48668d4bb4b993d03a19634d50cfb65cd1b8a8d2/gzip/'
          if name.startswith('emnist') else 'fgnt/mnist/ae608a02b796c9ee908c082585a5f006fe05258e/')
    data=urllib.request.urlopen('https://raw.githubusercontent.com/'+root+name,timeout=120).read()
    if hashlib.sha256(data).hexdigest()!=expected: raise ValueError('Checksum mismatch: '+name)
    (a.out/name).write_bytes(data)
    print(name, 'verified')
