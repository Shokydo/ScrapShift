from PIL import Image, ImageDraw
from zipfile import ZipFile, ZIP_DEFLATED
from io import BytesIO
import json, sys
out=sys.argv[1] if len(sys.argv)>1 else 'ScrapShift-pack.zip'
with ZipFile(out,'w',ZIP_DEFLATED) as z:
    def js(path,data): z.writestr(path,json.dumps(data,ensure_ascii=False))
    def png(path,img):
        b=BytesIO();img.save(b,'PNG');z.writestr(path,b.getvalue())
    js('pack.mcmeta',{'pack':{'pack_format':75,'description':'Scrap Shift 3.0.45','min_format':[75,0],'max_format':[75,0]}})
    js('assets/scrapshift/items/invisible.json',{'model':{'type':'minecraft:empty'}})
    for variant,colors in {
        'watcher':['#23242d','#44485c','#666d82','#a92f39','#ff6866','#ffd1ae'],
        'collector':['#332820','#62503c','#88734d','#9a5a2b','#f8bf50','#fff2af']
    }.items():
        for k,color in enumerate(colors):
            img=Image.new('RGBA',(16,16),color); d=ImageDraw.Draw(img)
            if k<4:
                rgba=img.getpixel((0,0)); shade=tuple(max(0,c-12) for c in rgba[:3])+(255,)
                for y in (0,7,15):d.line((0,y,15,y),fill=shade)
                for x,y in [(2,4),(13,11),(8,14),(5,9)]:d.point((x,y),fill=shade)
            png(f'assets/scrapshift/textures/item/mech/{variant}_{k}.png',img)
        def el(lo,hi,k):
            return {'from':lo,'to':hi,'faces':{f:{'uv':[0,0,16,16],'texture':f'#m{k}'} for f in ('north','south','west','east','up','down')}}
        heavy=variant=='collector'
        shapes={
            'head':[
                ([3,4,3],[13,12,13],0),([2,6,3],[14,11,12],1),([3,11,4],[13,13,12],2),
                ([3,5,12],[13,11,14],1),([4,7,13],[12,10,14.25],0),
                *([([5,8,14],[7,10,14.5],4),([9,8,14],[11,10,14.5],4),([5,4,12],[11,6,14.5],2)] if heavy else [([4.5,8,14],[11.5,9.25,14.5],4),([7.5,7.75,14.5],[8.5,9.5,14.65],5),([11,12,7],[12,16,8],2)])
            ],
            'body':[
                ([3,3,5],[13,13,11],0),([2.6,7,4.2],[13.4,13,11.7],1),([4,8,11],[12,12,12.5],2),
                ([5,9,12],[11,10,12.7],3),([7,9,12.7],[9,10,12.85],4),([3.7,4,4],[12.3,6,12],3),
                *([([4,6,2],[7,14,5],2),([9,6,2],[12,14,5],2)] if heavy else [([5,7,3],[11,13,5],2),([6,9,2.8],[10,11,3],3)])
            ],
            'arm':[
                ([6,0,6],[10,9,10],0),([5.1,5,5.1],[10.9,8,10.9],2 if heavy else 1),
                ([6.7,-3,6.7],[9.3,2,9.3],2),([5.7,-4,5.7],[10.3,0,10.3],1),
                ([6,-6,6],[7.2,-3,9.8],0),([8.8,-6,6],[10,-3,9.8],0),([6,6,10.8],[10,7,11],3)
            ],
            'leg':[
                ([5,6,5],[11,13,11],1),([5.5,3,5.5],[10.5,9,10.5],2),([5,1,4.6],[11,6,11.4],0),
                ([5,0,4],[11,3,13 if heavy else 36],1),([5,0,4],[11,1,13 if heavy else 36],2),([6,5,11],[10,6.5,11.6],3)
            ]}
        for part,geometry in shapes.items():
            name=f'{variant}_{part}'
            js(f'assets/scrapshift/models/item/{name}.json',{'textures':{f'm{k}':f'scrapshift:item/mech/{variant}_{k}' for k in range(len(colors))},'elements':[el(*part) for part in geometry]})
            js(f'assets/scrapshift/items/{name}.json',{'model':{'type':'minecraft:model','model':f'scrapshift:item/{name}'}})
    for name,base,light in [('weathered_copper_grate','#252e32','#8c9996'),('copper_grate','#382f28','#a4a69d')]:
        im=Image.new('RGBA',(16,16));d=ImageDraw.Draw(im)
        for x in range(0,16,4):d.line((x,0,x,15),fill=base);d.line((x+1,0,x+1,15),fill=light)
        for y in range(0,16,4):d.line((0,y,15,y),fill=base);d.line((0,y+1,15,y+1),fill=light)
        png(f'assets/minecraft/textures/block/{name}.png',im)
    ladder=Image.new('RGBA',(16,16));d=ImageDraw.Draw(ladder)
    for x in (2,12):
        d.rectangle((x,0,x+2,15),fill='#394246');d.line((x,0,x,15),fill='#86908b')
    for y in (2,7,12):
        d.rectangle((2,y,14,y+2),fill='#465254');d.line((3,y,13,y),fill='#a8aea1');d.point((3,y+1),fill='#ac7950');d.point((13,y+1),fill='#ac7950')
    png('assets/minecraft/textures/block/ladder.png',ladder)
    png('assets/minecraft/textures/gui/sprites/hud/hotbar.png',Image.new('RGBA',(182,22)))
    png('assets/minecraft/textures/gui/sprites/hud/hotbar_selection.png',Image.new('RGBA',(24,23)))
    # Vanilla icons are the fallback until the player builds/loads the optional MirRP layer.
    from pathlib import Path
    old={'board':'comparator','coil':'copper_ingot','reactor':'echo_shard','servo':'iron_ingot','lamp':'blaze_rod','blaster':'crossbow'}
    icons={x[0]:x[-1] for x in json.loads(Path(__file__).with_name('mirrp_items.json').read_text(encoding='utf-8'))}
    for game_id,icon in {**old,**icons}.items():
        if game_id=="lethal_helmet":continue
        model={'model':{'type':'minecraft:model','model':f'minecraft:item/{dict(iron_block="iron_ingot",chest="brick",bell="gold_ingot",lantern="glowstone_dust",clock="clock_00").get(icon,icon)}'}}
        for key in (game_id,'mirrp_'+game_id):js(f'assets/scrapshift/items/{key}.json',model)
        if game_id in ('lamp','pro_lamp','prolamp','pro_flashlight'):
            js(f'assets/scrapshift/items/mirrp_{game_id}_stowed.json',{'model':{'type':'minecraft:select','property':'minecraft:display_context','cases':[{'when':'gui','model':{'type':'minecraft:empty'}}],'fallback':model['model']}})
    # Thin curved visor lips, with a transparent field and no side rails.
    w,h=192,108
    visor=Image.new('RGBA',(w,h));pix=visor.load()
    for x in range(w):
        u=abs((x+.5-w/2)/(w/2))
        top=3.2+3.5*(1-u*u)
        bottom=2.8+6.4*(1-u*u)**2
        for y in range(h):
            top_edge=top-y-.5;bottom_edge=bottom-(h-y-.5)
            v=max(top_edge,bottom_edge)
            if v<=0:continue
            lip=top_edge>=bottom_edge
            alpha=round(min(0.86,0.27+v*.20)*255*(.64+.36*(1-u**4)))
            k=10 if lip else (29 if v<2 else 16)
            pix[x,y]=(k,k,k+2,alpha)
    png('assets/minecraft/textures/misc/pumpkinblur.png',visor.resize((768,432),Image.Resampling.NEAREST))
    js('assets/minecraft/textures/misc/pumpkinblur.png.mcmeta',{'texture':{'blur':False}})
    # Silence the vanilla combat/HUD sprites while retaining the newly drawn central hotbar.
    for row in Path(__file__).with_name('hud_hide_paths.txt').read_text().splitlines():
        path,w0,h0=row.split("\t");png(path,Image.new('RGBA',(int(w0),int(h0))))
    aim=Image.new('RGBA',(15,15));ImageDraw.Draw(aim).rectangle((7,7,8,8),fill=(228,223,199,165));png('assets/minecraft/textures/gui/sprites/hud/crosshair.png',aim)
    # E still uses Minecraft's actual slot positions, but gets a translucent visor console skin.
    ui=Image.new('RGBA',(256,256));d=ImageDraw.Draw(ui)
    d.rounded_rectangle((0,0,175,165),radius=5,fill=(83,98,109,166),outline=(178,168,126,225),width=1)
    d.rectangle((4,5,171,79),outline=(99,123,129,177),fill=(20,31,40,104))
    d.rectangle((26,7,77,77),fill=(13,23,28,173),outline=(127,148,145,200))
    for y in (8,26,44,62):d.rectangle((7,y,24,y+17),outline=(122,145,149,160),fill=(9,16,25,150))
    for y in (16,34):
        for x in (97,115):d.rectangle((x,y,x+17,y+17),outline=(122,145,149,170),fill=(8,15,19,150))
    d.rectangle((153,25,170,42),outline=(139,146,129,150),fill=(8,15,19,140))
    d.polygon([(136,30),(141,30),(141,27),(147,34),(141,40),(141,37),(136,37)],fill=(227,163,62,210))
    d.line((7,81,168,81),fill=(220,151,52,225));d.line((7,140,168,140),fill=(220,151,52,225))
    for row in range(4):
        for k in range(9):
            x=7+k*18;y=83+18*row if row<3 else 141
            live=row==3 and k<4
            d.rectangle((x,y,x+17,y+17),outline=(214,164,83,232) if live else (115,133,136,143),fill=(9,19,29,198) if live else (26,36,44,108))
            if live:d.line((x+1,y+16,x+16,y+16),fill=(183,115,43,221))
    png('assets/minecraft/textures/gui/container/inventory.png',ui)
    shader=Path(__file__).with_name('hud_shader')
    for name in ['rendertype_text','rendertype_text_intensity']:
        z.writestr('assets/minecraft/shaders/core/'+name+'.vsh',(shader/'rendertype_text.vsh').read_bytes())
    z.writestr('LICENSE-BetterHud.txt',(shader/'BetterHud-LICENSE.txt').read_bytes())
    # Layered silhouette and arcs; faint guide pixels lock both glyph advances to 113.
    providers=[{'type':'space','advances':{chr(0xE17F):-113}}]
    import math
    for name,base in [('health',0xE000),('stamina',0xE040)]:
        sheet=Image.new('RGBA',(112*21,95))
        for n in range(21):
            im=Image.new('RGBA',(112,95));d=ImageDraw.Draw(im)
            d.point((111,94),fill=(0,0,0,1))
            if name=='health':
                # Minecraft proportions: square head, six rectangular body parts.
                outline=(234,226,213,248)
                for box0,face in [((48,27,63,42),(52,49,47,160)),((47,44,64,64),(46,45,44,140)),
                                  ((39,44,46,65),(40,39,38,130)),((65,44,72,65),(40,39,38,130)),
                                  ((47,65,55,85),(40,39,38,130)),((56,65,64,85),(40,39,38,130))]:
                    d.rectangle(box0,fill=face,outline=outline,width=1)
                d.line((49,29,61,29),fill=(244,231,204,100))
                d.line((49,46,62,46),fill=(244,231,204,85))
                start,end,box=6,173,(5,18,105,92)
            else:
                d.polygon([(10,14),(17,14),(14,20),(21,20),(9,29),(11,22),(6,22)],fill='#ffc871')
                start,end,box=210,302,(9,10,101,80)
            for i in range(20):
                angle=start+(end-start)*i/20
                col=('#ff8c39' if name=='health' else '#ffc15f') if i<n else (105,70,41,160)
                d.arc(box,angle,angle+(end-start)/20-1.25,fill=col,width=3)
            sheet.paste(im,(112*n,0))
        png(f'assets/scrapshift/textures/font/{name}.png',sheet)
        providers.append({'type':'bitmap','file':f'scrapshift:font/{name}.png','ascent':-16384,'height':95,'chars':[''.join(chr(base+i) for i in range(21))]})
    # Only the frames are glyphs. Actual item-model icons are moved from the GUI item atlas.
    sheet=Image.new('RGBA',(102,34))
    for selected in range(3):
        card=Image.new('RGBA',(34,34));d=ImageDraw.Draw(card)
        edge='#ffc275' if selected==1 else '#ad602d'
        d.rectangle((0,1,33,33),outline=edge,width=2 if selected==1 else 1)
        if selected==2:
            d.line((8,9,25,26),fill='#d47847',width=2)
            d.line((25,9,8,26),fill='#d47847',width=2)
        sheet.paste(card,(selected*34,0))
    png('assets/scrapshift/textures/font/slots.png',sheet)
    providers.append({'type':'bitmap','file':'scrapshift:font/slots.png','ascent':-17408,'height':34,'chars':[chr(0xE200)+chr(0xE240)+chr(0xE280)]})
    # Fixed full-screen visor on a separate HUD layer, independent of armour and flashlight.
    # A faint full-width guide keeps the sprite quad centred for every GUI scale.
    visor_hud=visor.copy(); visor_hud.putalpha(visor_hud.getchannel('A').point(lambda a:a*0.72))
    png('assets/scrapshift/textures/font/visor.png',visor_hud)
    providers.append({'type':'bitmap','file':'scrapshift:font/visor.png','ascent':-18432,'height':108,'chars':[chr(0xE300)]})
    # Single-glyph optical overlays. The font shader expands these to the viewport.
    import random
    optics=Image.new('RGBA',(192*5,108))
    for frame in range(1,5):
        im=Image.new('RGBA',(192,108));d=ImageDraw.Draw(im)
        rng=random.Random(2408+frame)
        for i in range(14-frame*2):
            y=rng.randrange(5,104);x=rng.randrange(-45,175);length=rng.randrange(18,100)
            a=rng.randrange(35,100)*(6-frame)//5
            d.rectangle((x,y,x+length,y+(i%4==0)),fill=(102,255,161,a))
            d.line((x+7,y+2,x+length//2+7,y+2),fill=(52,163,146,a//2))
        d.point((191,107),fill=(0,0,0,1))
        optics.paste(im,(192*frame,0))
    png('assets/scrapshift/textures/font/optics.png',optics)
    providers.append({'type':'bitmap','file':'scrapshift:font/optics.png','ascent':-18432,'height':108,'chars':[' '+''.join(chr(0xE310+i) for i in range(1,5))]})
    slots_lamp=Image.new('RGBA',(38*6,29))
    for frame in range(6):
        selected=frame%2
        card=Image.new('RGBA',(38,29));d=ImageDraw.Draw(card)
        d.rectangle((6,1,32,27),fill=(24,26,22,255),outline='#ffca77' if selected else '#ab6e38')
        if frame>=2:
            # The actual item quad is rendered by the vanilla hotbar, behind this overlay.
            d.rectangle((10,4,29,23),fill=(0,0,0,0))
        d.point((37,28),fill=(0,0,0,1));slots_lamp.paste(card,(frame*38,0))
    png('assets/scrapshift/textures/font/lamp_slot.png',slots_lamp)
    providers.append({'type':'bitmap','file':'scrapshift:font/lamp_slot.png','ascent':-19456,'height':29,'chars':[''.join(chr(0xE400+i) for i in range(6))]})
    providers.append({'type':'space','advances':{chr(0xE3FF):-39,chr(0xE43E):-6}})
    for name,count,start,ascent in [('lamp_charge',10,0xE460,-21504),('ammo_charge',2,0xE440,-20480)]:
        width=80 if count==10 else 140
        strip=Image.new('RGBA',(width*(count+1),14));d=ImageDraw.Draw(strip)
        step=8 if count==10 else 108
        bar=6 if count==10 else 31
        for level in range(count+1):
            for i in range(count):
                x=level*width+(width-((count-1)*step+bar))//2+i*step
                if count==2:
                    filled=i<level
                    tip='#ffc675' if filled else '#8b8b8b'
                    brass='#efa044' if filled else '#666666'
                    shade='#a75c24' if filled else '#444444'
                    d.polygon([(x+15,0),(x+20,1),(x+25,3),(x+25,5),(x+5,5),(x+5,3),(x+10,1)],fill=tip)
                    d.rectangle((x+1,5,x+29,11),fill=brass)
                    d.rectangle((x+2,10,x+28,11),fill=shade)
                    d.rectangle((x,12,x+30,13),fill=brass)
                else:
                    d.rectangle((x,0,x+bar-1,13),fill='#24201b' if i<level else '#100f0e',outline='#e8a34e' if i<level else '#534739')
            d.point((level*width+width-1,13),fill=(255,255,255,1))
        png('assets/scrapshift/textures/font/'+name+'.png',strip)
        providers.append({'type':'bitmap','file':'scrapshift:font/'+name+'.png','ascent':ascent,'height':14,'chars':[''.join(chr(start+i) for i in range(count+1))]})
    width,height=124,5
    cooldown=Image.new('RGBA',(width*17,height*3));d=ImageDraw.Draw(cooldown)
    for frame in range(51):
        x,y=width*(frame%17),height*(frame//17)
        for left,right in [(2,57),(66,121)]:
            d.rectangle((x+left,y+1,x+right,y+3),fill=(30,46,32,190),outline=(93,117,77,220))
        n=round(56*frame/50)
        if n:
            color='#caff9a' if frame==50 else '#83c66f'
            d.line((x+2,y+2,x+1+n,y+2),fill=color)
            d.line((x+122-n,y+2,x+121,y+2),fill=color)
        d.point((x+123,y+4),fill=(255,255,255,1))
    png('assets/scrapshift/textures/font/scan_cooldown.png',cooldown)
    providers.append({'type':'bitmap','file':'scrapshift:font/scan_cooldown.png','ascent':-22528,'height':5,'chars':[''.join(chr(0xE500+row*17+i) for i in range(17)) for row in range(3)]})
    ring=Image.new('RGBA',(128,128));d=ImageDraw.Draw(ring)
    d.rectangle((12,12,115,115),outline=(101,255,77,75),width=2)
    d.rectangle((26,26,101,101),outline=(155,255,120,115),width=2)
    for x,y,sx,sy in [(12,12,1,1),(115,12,-1,1),(12,115,1,-1),(115,115,-1,-1)]:
        d.line((x,y,x+sx*19,y),fill=(168,255,105,145),width=3)
        d.line((x,y,x,y+sy*19),fill=(168,255,105,145),width=3)
    png('assets/scrapshift/textures/item/scan_ring.png',ring)
    js('assets/scrapshift/font/scan_marker.json',{'providers':[{'type':'bitmap','file':'scrapshift:item/scan_ring.png','ascent':36,'height':40,'chars':['\uE550']}]})
    js('assets/scrapshift/models/item/scan_ring.json',{'parent':'minecraft:item/generated','textures':{'layer0':'scrapshift:item/scan_ring'}})
    js('assets/scrapshift/items/scan_ring.json',{'model':{'type':'minecraft:model','model':'scrapshift:item/scan_ring'}})
    js('assets/scrapshift/font/hud.json' ,{'providers':providers})
    z.writestr('assets/minecraft/shaders/core/lightmap.fsh',(shader/'lightmap.fsh').read_bytes())
    z.writestr('assets/minecraft/shaders/core/position_tex_color.vsh',(shader/'position_tex_color.vsh').read_bytes())
