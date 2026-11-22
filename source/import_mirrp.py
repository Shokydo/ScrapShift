#!/usr/bin/env python3
"""Local compatibility pack builder. The original MirRP assets are not distributed here."""
import argparse,json
from pathlib import Path
from zipfile import ZipFile,ZIP_DEFLATED

parser=argparse.ArgumentParser(description='Build a local Minecraft 1.21.11 MirRP/ScrapShift compatibility resource pack')
parser.add_argument('mirrp',help='Original MirRPLethalCompany.zip 1.1 from Modrinth')
parser.add_argument('-o','--output',default='ScrapShift-MirRP-локальный.zip')
a=parser.parse_args()
if Path(a.mirrp).resolve()==Path(a.output).resolve():parser.error('Choose a different output filename')
rows=json.loads(Path(__file__).with_name('mirrp_items.json').read_text(encoding='utf-8'))
mapping={s[0]:s[4] for s in rows}
mapping.update(lamp='tools/flashlights/flashlight',blaster='tools/dobblebarreledshotgun/dobblebarreled_shotgun')
files={}
def js(k,v):files[k]=json.dumps(v,ensure_ascii=False).encode('utf-8')
js('pack.mcmeta',{'pack':{'pack_format':75,'description':'Scrap Shift - local MirRP model compatibility (load above base pack)','min_format':[75,0],'max_format':[75,0]}})
try:
    with ZipFile(a.mirrp) as src:
        for item,origin in mapping.items():
            data=json.loads(src.read(f'assets/minecraft/models/item/mirrplc/{origin}.json'))
            for key,value in list(data.get('textures',{}).items()):
                if value.startswith('#'):continue
                loc=value.split(':',1)[-1]
                if not loc.startswith('item/mirrplc/'):continue
                png=f'assets/minecraft/textures/{loc}.png'
                safe='item/mirrp_local/'+loc.removeprefix('item/mirrplc/')
                files[f'assets/scrapshift/textures/{safe}.png']=src.read(png)
                if png+'.mcmeta' in src.namelist():files[f'assets/scrapshift/textures/{safe}.png.mcmeta']=src.read(png+'.mcmeta')
                data['textures'][key]='scrapshift:'+safe
            js(f'assets/scrapshift/models/item/mirrp_local/{item}.json',data)
            js(f'assets/scrapshift/items/{item}.json',{'model':{'type':'minecraft:model','model':f'scrapshift:item/mirrp_local/{item}'}})
except (KeyError,ValueError) as e:
    parser.exit(2,f'Could not read MirRP Lethal Company 1.1: {e}\n')
with ZipFile(a.output,'w',ZIP_DEFLATED) as dst:
    for name,data in files.items():dst.writestr(name,data)
print(f'Created {a.output}: {len(mapping)} item models. Load it ABOVE the Scrap Shift base resource pack.')
