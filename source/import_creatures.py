"""Add original Lethal Company MC v2.1 monster models to the Scrap Shift pack."""
from pathlib import Path
from zipfile import ZipFile, ZIP_DEFLATED
import json, re, sys

archive, pack = map(Path, sys.argv[1:3])
root = 'Resource Pack/lethal resources/assets/minecraft/'
families = re.compile(r'(?:bracken(?:_2|_walk_[12])?|hoard_bug(?:_step[12]|_atk_[123]|_guard)?|coil_head(?:_walk_[123])?|thumper(?:2|_midwalk1?)?|jester(?:_head|_wind_[1-8]|_open)?|nut_cracker(?:_walk|_reload)?)')
sounds = {'bracken', 'bracken_mad', 'coil_1', 'coil_2', 'coil_3', 'coil_4', 'jester_melody', 'jester_end', 'thumper_roar', 'thumper_step1', 'thumper_step2'}
files = {}
def js(name, doc): files[name] = json.dumps(doc, ensure_ascii=False).encode()
with ZipFile(archive) as src:
    prefix = root+'models/item/'
    for path in src.namelist():
        if not path.startswith(prefix) or not path.endswith('.json'): continue
        name = path[len(prefix):-5]
        if not families.fullmatch(name): continue
        model = json.loads(src.read(path))
        for key, ref in model['textures'].items():
            ref = ref.removeprefix('minecraft:')
            if not re.fullmatch(r'item/[a-z0-9_]+', ref): raise ValueError(ref)
            dest = f'assets/scrapshift/textures/item/pmc/{ref.removeprefix("item/")}.png'
            files[dest] = src.read(root+f'textures/{ref}.png')
            if root+f'textures/{ref}.png.mcmeta' in src.namelist():
                files[dest+'.mcmeta'] = src.read(root+f'textures/{ref}.png.mcmeta')
            model['textures'][key] = 'scrapshift:item/pmc/'+ref.removeprefix('item/')
        js(f'assets/scrapshift/models/item/pmc/{name}.json',model)
        js(f'assets/scrapshift/items/pmc_{name}.json',{'model':{'type':'minecraft:model','model':f'scrapshift:item/pmc/{name}'}})
    # Visible helmet for servers without the optional MirRP models.
    helmet=json.loads(src.read(prefix+'helmet.json'))
    for key in helmet['textures']:
        helmet['textures'][key]='scrapshift:item/pmc/helmet'
    files['assets/scrapshift/textures/item/pmc/helmet.png']=src.read(root+'textures/item/helmet.png')
    js('assets/scrapshift/models/item/pmc/helmet.json',helmet)
    js('assets/scrapshift/items/lethal_helmet.json',{'model':{'type':'minecraft:model','model':'scrapshift:item/pmc/helmet'}})
    events={}
    for path in src.namelist():
        if not path.startswith(root+'sounds/') or not path.endswith('.ogg'):continue
        name = Path(path).stem
        if name not in sounds:continue
        files[f'assets/scrapshift/sounds/creatures/{name}.ogg']=src.read(path)
        events[f'creature.{name}']={'sounds':[f'scrapshift:creatures/{name}']}
    if events.keys()!={f'creature.{n}' for n in sounds}:raise ValueError('Missing original sounds')
    js('assets/scrapshift/sounds.json',events)
files['LETHAL-COMPANY-MC-CREDITS.txt']=b'Monster models, textures and sounds from Lethal Company MC v2.1 by Marioisawesome82.\nhttps://www.planetminecraft.com/project/lethal-company-6213100/\nRuntime behaviours are Scrap Shift adaptations, not the original datapack.\n'
with ZipFile(pack,'a',ZIP_DEFLATED) as dest:
    if set(dest.namelist()).intersection(files):raise ValueError('Refusing to overwrite existing pack entries')
    for name,data in files.items():dest.writestr(name,data)
print('Imported',sum(n.startswith('assets/scrapshift/items/') for n in files),'original model frames and',len(sounds),'sounds')
