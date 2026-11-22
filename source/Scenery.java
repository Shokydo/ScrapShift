package shift;

import org.bukkit.Material;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.type.Ladder;
import org.bukkit.block.data.type.Stairs;
import static org.bukkit.Material.*;
import static shift.ScrapShift.Pt;

/** The three author-built route profiles, layered over the 100x100 terrain. */
final class Scenery {
    private final ScrapShift game;
    Scenery(ScrapShift game){this.game=game;}
    void b(int x,int y,int z,Material m){game.set(new Pt(x,z),y,m);}
    void box(int x1,int y1,int z1,int x2,int y2,int z2,Material m){
        for(int x=x1;x<=x2;x++)for(int z=z1;z<=z2;z++)for(int y=y1;y<=y2;y++)b(x,y,z,m);
    }
    void post(int x,int z,int h,Material material,Material lamp){for(int y=1;y<h;y++)b(x,y,z,material);b(x,h,z,lamp);}
    void steps(int x,int y,int z,Material type,BlockFace face){Stairs s=(Stairs)type.createBlockData();s.setFacing(face);game.block(new Pt(x,z),y).setBlockData(s,false);}
    void route(int n){
        // The landing gangway is the visual anchor for every destination; the path beyond it changes.
        for(int x=199;x<=206;x++){b(x,0,12,n==0?SPRUCE_PLANKS:n==1?CUT_RED_SANDSTONE:POLISHED_ANDESITE);b(x,0,11,GRAY_CONCRETE);b(x,0,13,GRAY_CONCRETE);}
        switch(n){case 0->quarry(); case 1->canyon(); default->plateau();}
        facade(n);
    }
    void quarry(){
        // Flooded cut, solid banks and timber-and-steel bridge deck.
        for(int x=211;x<=220;x++)for(int z=-17;z<=17;z++){
            int lip=x==211||x==220?1:0;
            for(int y=-6;y<=0;y++)b(x,y,z,y==-6?TUFF:y<=-3?((lip==1||Math.abs(z)==17)?MOSSY_COBBLESTONE:WATER):AIR);
            for(int y=1;y<=4;y++)b(x,y,z,AIR);
        }
        for(int x=207;x<=224;x++)for(int z=10;z<=14;z++){
            b(x,0,z,(z==10||z==14)?STRIPPED_SPRUCE_LOG:(x%4==0?WAXED_WEATHERED_COPPER_GRATE:SPRUCE_PLANKS));
            if(z==10||z==14)b(x,1,z,SPRUCE_FENCE);
            else box(x,1,z,x,3,z,AIR);
        }
        for(int x:new int[]{210,215,220})for(int z:new int[]{10,14})box(x,-5,z,x,0,z,STRIPPED_SPRUCE_LOG);
        for(int x:new int[]{207,224})for(int z:new int[]{10,14})post(x,z,3,SPRUCE_FENCE,OCHRE_FROGLIGHT);
        for(int x=222;x<=233;x++)for(int z=3;z<=12;z++)if(Math.abs(z-12)<2||Math.abs(x-231)<2)b(x,0,z,(x+z)%3==0?ANDESITE:COARSE_DIRT);
        shed(202,-12,7,6,SPRUCE_PLANKS,OXIDIZED_COPPER);
        shed(223,-15,5,4,STRIPPED_SPRUCE_LOG,WAXED_EXPOSED_CUT_COPPER);
        for(int x=201;x<207;x++){b(x,0,-2,COARSE_DIRT);b(x,1,-3,x%2==0?BARREL:MOSS_BLOCK);}
        for(int x=225;x<232;x++)for(int z=-5;z<-2;z++)if((x+z)%2==0)b(x,1,z,COBBLED_DEEPSLATE_WALL);
    }
    void canyon(){
        // Retaining wall and raised utility catwalk, with a lit service arch as the through-route.
        box(211,1,-17,215,6,17,CUT_RED_SANDSTONE);
        box(211,1,-11,215,3,-7,AIR);
        box(211,4,-11,215,4,-7,POLISHED_BLACKSTONE_BRICKS);
        for(int z=-16;z<=16;z+=4)if(Math.abs(z+9)>3)box(210,1,z,210,6,z,CHISELED_RED_SANDSTONE);
        for(int x=211;x<=215;x++){b(x,4,-9,OCHRE_FROGLIGHT);for(int z=-17;z<=17;z++)if(Math.abs(z)%5==0)b(x,7,z,IRON_BARS);}
        for(int z=-11;z<=12;z++)for(int x=205;x<=207;x++)b(x,0,z,x==206?SMOOTH_RED_SANDSTONE:YELLOW_TERRACOTTA);
        for(int x=206;x<=228;x++)for(int z=-10;z<=-8;z++){b(x,0,z,Math.floorMod(x,4)==0?WAXED_COPPER_GRATE:SMOOTH_STONE);if(x>=216)box(x,1,z,x,3,z,AIR);}
        for(int z=-9;z<=0;z++)for(int x=227;x<=229;x++)b(x,0,z,SMOOTH_STONE);
        for(int x=216;x<=228;x++){b(x,5,-7,WAXED_EXPOSED_COPPER);b(x,5,-6,WAXED_WEATHERED_COPPER);}
        for(int x:new int[]{216,227})box(x,1,-6,x,4,-6,WAXED_WEATHERED_CUT_COPPER);
        for(int z=-14;z<=-11;z++)box(219,1,z,226,4,z,TERRACOTTA);
        tank(224,7,4,7,WAXED_EXPOSED_CUT_COPPER);tank(232,11,3,5,IRON_BLOCK);
        shed(201,15,7,3,RED_TERRACOTTA,DEEPSLATE_TILE_SLAB);
        for(int z=-12;z<=10;z+=5)post(204,z,4,POLISHED_DEEPSLATE_WALL,OCHRE_FROGLIGHT);
        for(int x=216;x<=231;x+=5)post(x,-12,4,POLISHED_DEEPSLATE_WALL,OCHRE_FROGLIGHT);
        for(int s=0;s<5;s++)for(int z=4;z<=6;z++)steps(205+s,s+1,z,POLISHED_BLACKSTONE_BRICK_STAIRS,BlockFace.EAST);
        box(210,5,4,211,5,6,POLISHED_BLACKSTONE_BRICKS);
    }
    void plateau(){
        for(int x=200;x<=233;x++)for(int z=-17;z<=17;z++){
            boolean track=(Math.abs(z-13)<=1&&x<=229)||(Math.abs(x-228)<=1&&z>=-1&&z<=14)||(z>=-1&&z<=1&&x>=228);
            if(track){b(x,0,z,POLISHED_ANDESITE);box(x,1,z,x,3,z,AIR);}
            else if((x+z)%3!=0)b(x,0,z,SNOW_BLOCK);
        }
        for(int x=205;x<=230;x+=5)for(int z:new int[]{11,15}){post(x,z,3,ANDESITE_WALL,PEARLESCENT_FROGLIGHT);b(x,0,z,SMOOTH_STONE);}
        for(int z=1;z<=10;z+=4)post(230,z,4,ANDESITE_WALL,PEARLESCENT_FROGLIGHT);
        for(int x=207;x<=224;x++)for(int z=2;z<=7;z++){
            int h=Math.max(1,4-Math.abs(z-4)-Math.abs(x-215)/6);
            for(int y=1;y<=h;y++)b(x,y,z,y==h?SNOW_BLOCK:PACKED_ICE);
        }
        shed(217,15,8,3,LIGHT_GRAY_CONCRETE,POLISHED_DEEPSLATE_SLAB);
        shed(205,-15,8,6,GRAY_CONCRETE,WAXED_OXIDIZED_CUT_COPPER);
        tank(229,-9,3,6,SMOOTH_QUARTZ);
        for(int x=224;x<=234;x++)for(int z=-3;z<=3;z++)if(x%4==0){b(x,4,z,IRON_BLOCK);if(z==0)b(x,4,z,PEARLESCENT_FROGLIGHT);}
    }
    void shed(int x,int z,int width,int depth,Material wall,Material roof){
        for(int dx=0;dx<width;dx++)for(int dz=0;dz<depth;dz++){
            boolean side=dx==0||dx==width-1||dz==0||dz==depth-1;
            b(x+dx,0,z+dz,SMOOTH_STONE);b(x+dx,4,z+dz,roof);
            for(int y=1;y<=3;y++)b(x+dx,y,z+dz,!side||(dz==depth-1&&dx==width/2&&y<3)?AIR:y==2&&dx%3==1?GRAY_STAINED_GLASS:wall);
        }
        for(int dx=1;dx<width-1;dx++)b(x+dx,1,z+1,dx%2==0?BARREL:SMOKER);
    }
    void tank(int cx,int cz,int r,int h,Material skin){
        for(int dx=-r;dx<=r;dx++)for(int dz=-r;dz<=r;dz++){
            double q=Math.hypot(dx,dz);if(q>r+.2)continue;
            for(int y=1;y<=h;y++)if(q>=r-1.15||y==1||y==h)b(cx+dx,y,cz+dz,y==2||y==h?POLISHED_DEEPSLATE:skin);
        }
        for(int y=2;y<=h;y++){Ladder ladder=(Ladder)LADDER.createBlockData();ladder.setFacing(BlockFace.WEST);game.block(new Pt(cx-r-1,cz),y).setBlockData(ladder,false);}
    }
    void facade(int n){
        Material skin=new Material[]{MOSSY_STONE_BRICKS,POLISHED_BLACKSTONE_BRICKS,POLISHED_ANDESITE}[n];
        Material trim=new Material[]{POLISHED_TUFF,WAXED_EXPOSED_CUT_COPPER,LIGHT_GRAY_CONCRETE}[n];
        for(int z=-18;z<=18;z++)for(int y=1;y<=10;y++){
            boolean window=y>=5&&y<=7&&Math.abs(z)>3&&Math.abs(z)%6>=2&&Math.abs(z)%6<=4;
            b(235,y,z,window?GRAY_STAINED_GLASS:y==3||y==9?trim:skin);
            if(z%6==0&&Math.abs(z)>3)b(234,y,z,POLISHED_DEEPSLATE);
        }
        box(231,4,-3,235,4,3,POLISHED_DEEPSLATE);
        box(234,1,-1,234,3,2,POLISHED_DEEPSLATE);
        box(233,3,-1,233,3,2,YELLOW_TERRACOTTA);
        for(int z=-2;z<=2;z++)b(232,4,z,z%2==0?OCHRE_FROGLIGHT:WAXED_COPPER_GRATE);
        for(int z:new int[]{-16,16})for(int y=1;y<=12;y++)b(233,y,z,y%4==0?IRON_BLOCK:WAXED_WEATHERED_COPPER);
        game.portalDoor(ScrapShift.FACTORY,"§6ГЛАВНЫЙ ВХОД",BlockFace.WEST);
    }
    void marketGround(){
        // A coastal basin, with a solid rock foundation beneath the dock and warehouse.
        for(int x=132;x<=256;x++)for(int z=-47;z<=60;z++){
            int edge=Math.min(Math.min(x-132,256-x),Math.min(z+47,60-z));
            double ridge=Math.max(0,(19-edge)/19.0);
            int h=(int)Math.round(game.softNoise(21,x,z,30)*3+ridge*ridge*(14+game.softNoise(22,x,z,19)*5)-1);
            boolean apron=x>=177&&x<=245&&z>=-17&&z<=34;
            boolean harbor=x<175&&z>=-24&&z<=26&&edge>10;
            if(apron)h=0;else if(harbor)h=-4;
            for(int y=-13;y<=h;y++)b(x,y,z,y==h?(apron?((x+3*z)%11==0?COBBLED_DEEPSLATE:GRAVEL):h>=2?BASALT:TUFF):DEEPSLATE);
            if(harbor)for(int y=h+1;y<=-1;y++)b(x,y,z,WATER);
        }
    }
    void marketDetail(){
        // Fenced loading apron, sea wall, service buildings, and distinct container rows.
        for(int x=178;x<=243;x++)for(int z=-13;z<=29;z++){
            if(x<=196&&Math.abs(z-12)<=2)b(x,0,z,WAXED_WEATHERED_COPPER_GRATE);
            else if((x>=193&&x<=234&&z>=1&&z<=24))b(x,0,z,(x==193||z==1||z==24)?YELLOW_TERRACOTTA:SMOOTH_STONE);
            if(z==-13||z==29||x==243){b(x,1,z,POLISHED_DEEPSLATE);b(x,2,z,IRON_BARS);}
        }
        for(int z=-25;z<=26;z++){b(174,0,z,CRACKED_DEEPSLATE_BRICKS);b(174,-1,z,DEEPSLATE_BRICKS);}
        for(int x=181;x<=240;x+=12)for(int z:new int[]{-9,26})post(x,z,5,POLISHED_DEEPSLATE_WALL,OCHRE_FROGLIGHT);
        shed(229,-13,10,8,DEEPSLATE_BRICKS,WAXED_WEATHERED_COPPER);
        tank(239,23,3,8,POLISHED_BASALT);tank(239,10,3,7,WAXED_EXPOSED_CUT_COPPER);
        for(int x:new int[]{193,208,222})container(x,-8,x==208?WAXED_EXPOSED_CUT_COPPER:WAXED_WEATHERED_CUT_COPPER);
        container(206,24,RED_TERRACOTTA);container(219,24,POLISHED_BLACKSTONE);
        for(int x=198;x<=226;x++)for(int z=6;z<=19;z++)b(x,7,z,(x%5==0||z==6||z==19)?POLISHED_DEEPSLATE:WAXED_WEATHERED_COPPER);
        for(int x=193;x<=200;x++)for(int z=8;z<=16;z++)b(x,6,z,(z==8||z==16)?YELLOW_TERRACOTTA:DEEPSLATE_TILES);
        for(int z:new int[]{7,17})post(195,z,6,POLISHED_DEEPSLATE_WALL,OCHRE_FROGLIGHT);
        for(int x=199;x<=216;x++)for(int z=6;z<=19;z++){
            if(x%4==0||z==6||z==19)b(x,6,z,x%4==0&&z%3==0?OCHRE_FROGLIGHT:POLISHED_DEEPSLATE);
            if(x%4==0)for(int y=1;y<=5;y++){b(x,y,6,POLISHED_DEEPSLATE);b(x,y,19,POLISHED_DEEPSLATE);}
            if((z==7||z==18)&&x%3==0)b(x,1,z,BARREL);
            if((z==10||z==14)&&x%4<2)b(x,0,z,YELLOW_TERRACOTTA);
        }
        box(216,3,8,216,3,16,WAXED_EXPOSED_CUT_COPPER);
        for(int z:new int[]{10,14})b(216,2,z,REDSTONE_LAMP);
    }
    void container(int x,int z,Material skin){
        for(int dx=0;dx<8;dx++)for(int dz=0;dz<3;dz++)for(int y=1;y<=3;y++)
            if(y==3||dz==0||dz==2||dx==0||dx==7)b(x+dx,y,z+dz,dx==0||dx==7?POLISHED_DEEPSLATE:skin);
    }
    void shipDetails(){
        for(int x=-3;x<=4;x++)for(int z=-5;z<=5;z++)if(x==-3||x==4||Math.abs(z)==5){
            b(x,0,z,WAXED_WEATHERED_COPPER_GRATE);
            if(x==-3||x==4||z<0||x<0||x>1)b(x,1,z,BAMBOO_FENCE);
        }
        for(int side:new int[]{-1,1})for(int z:new int[]{-2,2}){
            b(side*2,-2,z,POLISHED_BLACKSTONE);b(side*2,-3,z,MAGMA_BLOCK);
            b(side*2,4,z,WAXED_EXPOSED_CUT_COPPER);
        }
        b(0,5,-1,POLISHED_DEEPSLATE);b(0,6,-1,IRON_BARS);b(0,7,-1,END_ROD);
    }
    void tree(Pt p,int ground,int seed){
        java.util.Random r=new java.util.Random((long)p.x()*92821+p.z()*68917L);
        int kind=seed%10,h=kind<3?21+r.nextInt(8):kind<8?24+r.nextInt(9):31+r.nextInt(8);
        Material log=kind<3?BIRCH_LOG:SPRUCE_LOG, leaves=kind<3?BIRCH_LEAVES:SPRUCE_LEAVES;
        var leaf=(org.bukkit.block.data.type.Leaves)leaves.createBlockData();leaf.setPersistent(true);
        for(int y=1;y<=h;y++)game.set(p,ground+y,log);
        int start=kind<3?h/2:h/3;
        for(int y=start;y<h-1;y+=2+r.nextInt(3)){
            int dir=r.nextInt(4);int dx=dir==0?1:dir==1?-1:0,dz=dir==2?1:dir==3?-1:0;
            int len=kind<3?2+r.nextInt(3):Math.max(1,5-(y-start)/4)+r.nextInt(2);
            var branch=(org.bukkit.block.data.Orientable)log.createBlockData();branch.setAxis(dx==0?org.bukkit.Axis.Z:org.bukkit.Axis.X);
            for(int j=1;j<=len;j++)game.block(p.add(dx*j,dz*j),ground+y+(kind<3?j/2:0)).setBlockData(branch,false);
            Pt tip=p.add(dx*len,dz*len);int ty=ground+y+(kind<3?len/2:0);
            if(kind<3){for(int j=1;j<=2;j++)game.set(tip,ty+j,log);ty+=2;}
            canopy(tip,ty,kind<3?2:Math.min(3,len),kind<3?2:1,leaf,r);
        }
        canopy(p,ground+h,kind<3?2:1,2,leaf,r);
    }
    void canopy(Pt p,int y,int radius,int rise,org.bukkit.block.data.BlockData leaf,java.util.Random r){
        for(int dy=-1;dy<=rise;dy++)for(int dx=-radius;dx<=radius;dx++)for(int dz=-radius;dz<=radius;dz++){
            int d=dx*dx+dz*dz+dy*dy;if(d>radius*radius+1||(d>=radius*radius&&r.nextBoolean()))continue;
            var b=game.block(p.add(dx,dz),y+dy);if(b.getType().isAir())b.setBlockData(leaf,false);
        }
    }
    void path(int x,int z){
        b(x,-1,z,TUFF);b(x,0,z,Math.floorMod(x+z*3,6)==0?ANDESITE:COARSE_DIRT);box(x,1,z,x,3,z,AIR);
    }
    void loft(Pt c){
        for(int dx=-3;dx<=3;dx++)for(int dz=-3;dz<=3;dz++){
            if(Math.abs(dx)<2&&Math.abs(dz)<2)continue;
            boolean shaft=dx==3&&dz==0;
            if(!shaft)game.set(c.add(dx,dz),5,WAXED_WEATHERED_COPPER_GRATE);
            if((Math.abs(dx)==2&&Math.abs(dz)<=2||Math.abs(dz)==2&&Math.abs(dx)<=2)&&!(dx==2&&dz==0))game.set(c.add(dx,dz),6,IRON_BARS);
        }
        Pt shaft=c.add(3,0),support=c.add(4,0);
        for(int y=1;y<=7;y++)game.set(support,y,POLISHED_DEEPSLATE);
        for(int y=1;y<=6;y++){Ladder ladder=(Ladder)LADDER.createBlockData();ladder.setFacing(BlockFace.WEST);game.block(shaft,y).setBlockData(ladder,false);}
        for(int z:new int[]{-3,3}){game.set(c.add(-3,z),7,YELLOW_CONCRETE);game.set(c.add(3,z),7,IRON_BARS);}
    }
    void room(Pt c,int theme,int roof){
        Material pipe=new Material[]{WAXED_WEATHERED_COPPER,WAXED_EXPOSED_CUT_COPPER,IRON_BLOCK}[game.layout];
        // Overhead utility rails keep the floor lanes open.
        for(int dx=-3;dx<=3;dx++)for(int dz:new int[]{-2,2})game.set(c.add(dx,dz),roof-1,pipe);
        for(int side:new int[]{-1,1})for(int dz:new int[]{-3,3}){
            Pt corner=c.add(side*3,dz);
            for(int y=3;y<roof;y++)game.set(corner,y,y==roof-1?IRON_BLOCK:pipe);
            Pt shelf=c.add(side*2,dz);
            if(theme%3==0){game.set(shelf,1,BLAST_FURNACE);game.set(shelf,2,COPPER_GRATE);}
            if(theme%3==1){game.set(shelf,1,BARREL);game.set(shelf,2,IRON_TRAPDOOR);}
            if(theme%3==2){game.set(shelf,1,POLISHED_TUFF);game.set(shelf,2,LIGHT_BLUE_STAINED_GLASS);game.set(shelf,3,IRON_BARS);}
        }
        if(roof>=6)for(int dx=-2;dx<=2;dx++){
            game.set(c.add(dx,3),roof-2,WAXED_WEATHERED_COPPER_GRATE);
            game.set(c.add(dx,3),roof-1,IRON_BARS);
        }
    }
}
