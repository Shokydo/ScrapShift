"""Merge the official 1.21.11 Toon Shader without overwriting Scrap Shift HUD shaders."""
from hashlib import sha512
from pathlib import Path
from zipfile import ZipFile, ZIP_DEFLATED
import sys
src, dest = map(Path, sys.argv[1:3])
assert sha512(src.read_bytes()).hexdigest() == 'b3f6620e72aa9641159dd2877b75d58a273ef169b8d91d3031a9bce95a91eebdba8654e7a44ba9bb6bb8ae4555dca965d14dbd9d725d2efa56eb65afc094e521'
with ZipFile(src) as toon, ZipFile(dest,'a',ZIP_DEFLATED) as pack:
    names=set(pack.namelist())
    for f in toon.namelist():
        if f.endswith('/') or not f.startswith(('assets/toon/shaders/post/','assets/minecraft/post_effect/')): continue
        if f in names: raise ValueError(f'Duplicate pack entry: {f}')
        override=Path(__file__).with_name("post_shader") / Path(f).name
        pack.writestr(f,override.read_bytes() if f.startswith("assets/toon/shaders/post/") and override.is_file() else toon.read(f))
    pack.writestr('TOON-SHADER-LICENSE.txt',Path(__file__).with_name('TOON-SHADER-LICENSE.txt').read_bytes())
