package shift;

import java.util.*;
import java.nio.file.*;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.data.type.Switch;
import org.bukkit.boss.*;
import org.bukkit.command.*;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.block.*;
import org.bukkit.event.entity.*;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.*;

import org.bukkit.generator.ChunkGenerator;
import org.bukkit.inventory.*;
import org.bukkit.scoreboard.*;
import org.bukkit.util.Transformation;
import org.joml.Vector3f;
import org.joml.Quaternionf;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

public final class ScrapShift extends org.bukkit.plugin.java.JavaPlugin implements Listener {
  record Pt(int x,int z) { Pt add(int a,int b){return new Pt(x+a,z+b);} }
  static final int Y=64, X=-8192, LAND=188, ORBIT=-4096, SECTOR=2048;
  static final int REMOTE=157, MARKET=LAND+4*SECTOR, SHIP_GATE=4, CABIN_LEFT=-1, CABIN_RIGHT=2, CABIN_HALF_LENGTH=3, SITE_X0=128, SITE_X1=377, SITE_Z0=-125, SITE_Z1=124;
  static final Pt BUY=new Pt(MARKET+28,12);
  static final String[] SITES={"61-MARCH","68-ARTIFICE","20-ADAMANCE"};
  static final String[] PLANTS={"Сборочный цех","Плавильный комплекс","Холодный склад"};
  final Set<Integer> built=new HashSet<>(); int district, layout, buildShift, cycles;
  record Cargo(int x,int y,int z,double dx,double dz,ItemStack stack){}
  record FloorLoot(Interaction hit,Entity model,ItemStack stack,Chunk chunk){}
  final Map<Chunk,Integer> lootTickets=new HashMap<>();
  final Map<UUID,FloorLoot> floorLoot=new HashMap<>();
  final Map<UUID,Cargo> shipCargo=new LinkedHashMap<>();
  final ArrayDeque<ItemStack> cargoOverflow=new ArrayDeque<>();
  static final Pt CHARGE=new Pt(2,-3);
  int day, bank, baseQuota,dayLimit,mobHits,difficultyTier; double quotaMultiplier; boolean flight, company, pendingBuyer, shot; int shipOffset=ORBIT, shipZ, shipTurn, shipLift, landingLift, landingTurn, factoryTurn; Pt factoryOrigin; Pt pad; int padDistrict; boolean orbitBuild;
  NamespacedKey ammoKey;
  static final int BLASTER_CAPACITY=2;
  final Set<Pt> doors=new HashSet<>();
  final Scenery scenery=new Scenery(this);
  static final Pt START=new Pt(X,0), PILOT=new Pt(0,-3), SELL=new Pt(-1,1), EXIT=START.add(-6,0), YARD=new Pt(206,0), OUT=new Pt(204,0), FACTORY=new Pt(234,0);
  final List<Rig> rigs=new ArrayList<>();
  enum Creature {
    BRACKEN("bracken_2",.94f,1.20f,.25,3,"bracken_mad","bracken_walk_1","bracken_walk_2"),
    HOARDER("hoard_bug",.56f,.45f,.24,2,null,"hoard_bug_step1","hoard_bug_step2"),
    COIL("coil_head",.79f,1.15f,.30,3,null,"coil_head_walk_1","coil_head_walk_2","coil_head_walk_3"),
    THUMPER("thumper",.75f,.45f,.32,4,"thumper_roar","thumper_midwalk","thumper2","thumper_midwalk1"),
    JESTER("jester",.82f,.70f,.24,3,"jester_melody"),
    NUTCRACKER("nut_cracker",.83f,.83f,.21,4,null,"nut_cracker_walk","nut_cracker");
    final String idle,notice;final String[] walk;final float scale,up;final double speed,damage;
    Creature(String idle,float scale,float up,double speed,double damage,String notice,String... walk){this.idle=idle;this.scale=scale;this.up=up;this.speed=speed;this.damage=damage;this.notice=notice;this.walk=walk;}
  }
  static final class Rig {
    final Zombie mob;final List<ItemDisplay> parts;final Creature creature;String frame;boolean wasTargeted;int chaseTicks,watchTicks; long nextAttack;
    Rig(Zombie mob,List<ItemDisplay> parts,Creature creature){this.mob=mob;this.parts=parts;this.creature=creature;this.frame=creature==null?null:creature.idle;}
  }
  Scoreboard hidden; final Map<UUID,Scoreboard> oldBoards=new HashMap<>();
  final Random rng=new Random();
  final Set<Pt> floor=new HashSet<>();
  final Set<Chunk> tickets=new HashSet<>();
  final Set<UUID> dead=new HashSet<>();
  static final int PRO=32, INHALER=33, STUN=34, COMEDY=35, KNIFE=37, SHOVEL=38, STOP_SIGN=30;
  static final int INHALER_DOSES=3;
  final NamespacedKey dosesKey=new NamespacedKey("scrapshift","inhaler_doses");
  final Map<UUID,Long> itemUseTimes=new HashMap<>();
  final Map<UUID,Long> cursedMasks=new HashMap<>();
  final Map<Block,org.bukkit.block.data.BlockData> mutedFixtures=new HashMap<>();
  record Thing(String id,String name,int price,int slots){}
  static final Thing[] EXTRA={
    new Thing("big_bolt","Большой болт",21,1),
    new Thing("bottles","Ящик бутылок",33,2),
    new Thing("brass_bell","Латунный колокол",38,1),
    new Thing("cash_register","Кассовый аппарат",52,2),
    new Thing("clock","Часы",28,1),
    new Thing("clown_horn","Клоунский гудок",26,1),
    new Thing("cola","Банка колы",17,1),
    new Thing("cookie_mold_pan","Форма для печенья",22,1),
    new Thing("fancy_lamp","Настольная лампа",43,2),
    new Thing("flask","Колба",22,1),
    new Thing("gift_box","Подарочная коробка",27,1),
    new Thing("golden_cup","Золотой кубок",48,1),
    new Thing("gold_bar","Золотой слиток",66,2),
    new Thing("hairdryer","Фен",29,1),
    new Thing("jar_of_pickles","Банка огурцов",31,1),
    new Thing("large_axle","Большая ось",44,2),
    new Thing("magic_7_ball","Магический шар",37,1),
    new Thing("painting","Картина",41,2),
    new Thing("perfume_bottle","Флакон духов",34,1),
    new Thing("remote","Пульт",19,1),
    new Thing("ring","Кольцо",45,1),
    new Thing("robot","Игрушечный робот",39,2),
    new Thing("rubber_ducky","Резиновая уточка",25,1),
    new Thing("small_fox","Игрушечная лиса",28,1),
    new Thing("stop_sign","Знак «Стоп»",36,2),
    new Thing("toilet_paper","Рулон бумаги",14,1),
    new Thing("pro_flashlight","Профессиональный фонарь",0,1),
    new Thing("inhalator","Ингалятор",0,1),
    new Thing("stun_grenade","Оглушающая граната",0,1),
    new Thing("comedy","Маска комедии",33,1),
    new Thing("lethal_helmet","Шлем сотрудника",32,1),
    new Thing("knife","Нож",24,1),
    new Thing("shovel","Лопата",27,2)
  };
  static final class Breath {double level=100;int pause;boolean locked;long motion;}
  final Map<UUID,Breath> breath=new HashMap<>(); long breathTick;
  static final String[] NAMES={"Плата связи","Медная катушка","Реакторный блок","Сервопривод","Фонарь","Импульсник"};
  static final String[] IDS={"board","coil","reactor","servo","lamp","blaster"};
  static final int[] PRICES={16,22,42,35,0,0};
  static final int[] SCRAP=java.util.stream.IntStream.range(0,6+EXTRA.length).filter(i->i>=6&&price(i)>0).toArray();
  String destination="";
  record MoonTerrain(byte[] cells,Material[] palette){}
  final MoonTerrain[] moonTerrain=new MoonTerrain[3];
  void loadMoonTerrain(){String[] maps={"march","artifice"};for(int i=0;i<maps.length;i++)try(var in=getResource("moons/"+maps[i]+".bin")){
    if(in==null)continue;byte[] b=in.readAllBytes();int j=0;while(j+1<b.length&&(b[j]!=10||b[j+1]!=10))j++;
    if(b.length-j-2!=125000)throw new java.io.IOException("Unexpected terrain size");
    String[] names=new String(b,0,j,java.nio.charset.StandardCharsets.UTF_8).split("\n");Material[] palette=new Material[names.length+1];
    for(int k=0;k<names.length;k++)palette[k+1]=Material.matchMaterial(names[k]);
    moonTerrain[i]=new MoonTerrain(Arrays.copyOfRange(b,j+2,b.length),palette);
  }catch(Exception ex){getLogger().warning("Terrain import: "+ex.getMessage());}}
  int moonIndex(int x,int z){return x<SITE_X0||x>SITE_X1||z<SITE_Z0||z>SITE_Z1?-1:2*((x-SITE_X0)*250+z-SITE_Z0);}
  record Moon(int sx,int sy,int sz,int shift,int x0,int y0,int z0,int x1,int y1,int z1) {
    boolean entrance(Block b){return b.getX()>=x0+shift&&b.getX()<=x1+shift&&b.getY()>=y0&&b.getY()<=y1&&b.getZ()>=z0&&b.getZ()<=z1;}
  }
  final Moon[] moons={new Moon(62,67,33,16384,-57,66,6,-56,68,9),new Moon(40,70,-5,24576,92,67,114,95,69,115),new Moon(95,77,46,32768,15,74,51,16,76,52)};
  static final int COMPANY_SHIFT=40960, LOBBY_SHIFT=57344;
  static final Pt COMPANY_SHIP=new Pt(COMPANY_SHIFT+17,41), LOBBY_SHIP=new Pt(LOBBY_SHIFT,-4);
  static final int COMPANY_Y=51,LOBBY_Y=13;
  static final Pt COMPANY_BELL=new Pt(COMPANY_SHIFT+11,15);
  Villager buyer;
  void buyerNpc(){
    if(buyer!=null&&buyer.isValid())return;
    Location at=new Location(w,COMPANY_SHIFT+9.5,50,12.5,0,0);
    buyer=w.spawn(at,Villager.class,v->{v.setAI(false);v.setSilent(true);v.setInvulnerable(true);v.setPersistent(false);v.setGravity(false);v.setCustomNameVisible(false);v.addScoreboardTag("scrap_ship");});
  }
  final Map<UUID,Location> factoryReturns=new HashMap<>();
  void installMaps(){
    var root=Bukkit.getWorldContainer().toPath().resolve("scrap_shift");
    try(var manifest=new java.io.BufferedReader(new java.io.InputStreamReader(java.util.Objects.requireNonNull(getResource("moon-regions/index.txt")),java.nio.charset.StandardCharsets.UTF_8))){
      for(String name;(name=manifest.readLine())!=null;){
        int k=name.indexOf('_');var dir=root.resolve(name.substring(0,k));var target=dir.resolve(name.substring(k+1));
        if(java.nio.file.Files.exists(target))continue;
        java.nio.file.Files.createDirectories(dir);
        var resource=getResource("moon-regions/"+name);
        if(resource==null){if(name.startsWith("entities_"))continue;throw new java.io.IOException("Missing "+target+". Install the full Scrap Shift distribution with the five maps.");}
        var tmp=java.nio.file.Files.createTempFile(dir,"moon-",".part");
        try(var in=resource){
          java.nio.file.Files.copy(in,tmp,java.nio.file.StandardCopyOption.REPLACE_EXISTING);
          java.nio.file.Files.move(tmp,target,java.nio.file.StandardCopyOption.ATOMIC_MOVE);
        }finally{java.nio.file.Files.deleteIfExists(tmp);}
      }
    }catch(java.io.IOException ex){throw new IllegalStateException("Cannot install original moon regions",ex);}
  }
  void copyShip(int x,int y,int z){
    Moon m=moons[0];
    for(int a=0;a<18;a++)for(int b=0;b<12;b++)for(int c=0;c<12;c++){
      Block from=w.getBlockAt(m.sx+m.shift+a,m.sy+b,m.sz+c);Block to=w.getBlockAt(x+a,y+b,z+c);
      to.setBlockData(from.getBlockData(),false);
    }
  }
  static final class VoidTerrain extends ChunkGenerator {
    @Override public boolean shouldGenerateNoise(){return false;}
    @Override public boolean shouldGenerateSurface(){return false;}
    @Override public boolean shouldGenerateCaves(){return false;}
    @Override public boolean shouldGenerateDecorations(){return false;}
    @Override public boolean shouldGenerateMobs(){return false;}
    @Override public boolean shouldGenerateStructures(){return false;}
  }
  World w; final Map<UUID,BossBar> locationBars=new HashMap<>(); NamespacedKey key; PackService packs;
  static final NamespacedKey FATIGUE=new NamespacedKey("scrapshift","exhaustion");
  static final int SLOT_FIRST=0,SLOT_END=4,LAMP_SLOT=4;
  static final NamespacedKey BATTERY=new NamespacedKey("scrapshift","battery");
  static final Key HUD=Key.key("scrapshift","hud");
  final List<Integer> moonOrder=new ArrayList<>(List.of(0,1,2));
  void loadRoute(){List<Integer> stored=getConfig().getIntegerList("progress.moon-order");if(stored.size()==3&&new HashSet<>(stored).equals(Set.of(0,1,2))){moonOrder.clear();moonOrder.addAll(stored);}else{shuffleRoute();getConfig().set("progress.moon-order",moonOrder);saveConfig();}}
  void shuffleRoute(){Collections.shuffle(moonOrder,rng);}
  long campaignEpoch;
  boolean active,wipePending,firstLaunchDone; int quota; long roundStarted;
  int ambienceBeat;
  @Override public void onEnable(){
    saveDefaultConfig();packs=new PackService(this);
    try{packs.start();}catch(java.io.IOException e){throw new IllegalStateException("Could not prepare Scrap Shift resource pack",e);}
    key=new NamespacedKey(this,"cargo"); ammoKey=new NamespacedKey(this,"charge");
    installMaps();loadRoute();
    dayLimit=Math.clamp(getConfig().getInt("day-limit",3),1,30); mobHits=Math.clamp(getConfig().getInt("mob-health",2),1,50); quotaMultiplier=getConfig().getDouble("quota-multiplier",2); if(!Double.isFinite(quotaMultiplier)||quotaMultiplier<1||quotaMultiplier>100)quotaMultiplier=2; difficultyTier=Math.max(0,getConfig().getInt("progress.difficulty-tier",0)); baseQuota=Math.max(1,getConfig().getInt("quota",100)); quota=Math.max(1,getConfig().getInt("progress.quota",baseQuota)); day=Math.max(1,Math.min(dayLimit,getConfig().getInt("progress.day",1))); bank=Math.max(0,getConfig().getInt("progress.bank",0)); company=getConfig().getBoolean("progress.company",false); pendingBuyer=!company&&getConfig().getBoolean("progress.pending-buyer",false); cycles=getConfig().getInt("progress.cycles",0);firstLaunchDone=getConfig().getBoolean("progress.first-launch-done",day>1||cycles>0||pendingBuyer||company);
    w=new WorldCreator("scrap_shift").generator(new VoidTerrain()).generateStructures(false).createWorld();
    if(w==null) throw new IllegalStateException("Cannot create scrap_shift");
    rule(false,"doMobSpawning","spawn_mobs"); rule(false,"doDaylightCycle","advance_time");
    rule(false,"doWeatherCycle","advance_weather"); rule(true,"keepInventory","keep_inventory");
    rule(false,"fallDamage","fall_damage"); rule(true,"doImmediateRespawn","immediate_respawn");
    w.setTime(18000); w.setStorm(false); w.setDifficulty(Difficulty.NORMAL); w.setPVP(true);
    // The replacement lobby already includes its authored ship and void regions.
    moveShip(LOBBY_SHIP,0,LOBBY_Y-Y);gate(shipOffset,false);w.setSpawnLocation(cabin());
    if(company){moveShip(COMPANY_SHIP,2,COMPANY_Y-Y);buyerNpc();gate(shipOffset,false);w.setTime(14300);}
    readCargo();
    hidden=Bukkit.getScoreboardManager().getNewScoreboard();
    hidden.registerNewTeam("scrap_crew").setOption(Team.Option.NAME_TAG_VISIBILITY,Team.OptionStatus.NEVER);
    Bukkit.getPluginManager().registerEvents(this,this);
    for(Entity e:w.getEntities())removeMarchZombie(e);
    for(Player p:Bukkit.getOnlinePlayers()) enter(p);
    Bukkit.getScheduler().runTaskTimer(this,this::tick,1,20);
    Bukkit.getScheduler().runTaskTimer(this,()->{if(active)w.setTime(moonClock(Math.max(0,System.nanoTime()-roundStarted)));},1,1);
    Bukkit.getScheduler().runTaskTimer(this,this::animate,1,1);
    Bukkit.getScheduler().runTaskTimer(this,this::lamps,1,3);
    Bukkit.getScheduler().runTaskTimer(this,this::drainLamps,5,5);
    Bukkit.getScheduler().runTaskTimer(this,this::stamina,4,4);
    Bukkit.getScheduler().runTaskTimer(this,this::ambience,10,10);
  }
  @Override public void onDisable(){removeSettingsHologram();restoreShuttleLights(); if(w!=null){for(Player p:w.getPlayers())if(aboard(p)&&!p.isDead()&&p.getGameMode()!=GameMode.SPECTATOR)unload(p);persist();}
    for(Player p:Bukkit.getOnlinePlayers())fatigue(p,false); if(packs!=null)packs.close(); locationBars.values().forEach(BossBar::removeAll);locationBars.clear(); for(Player p:Bukkit.getOnlinePlayers()){removeGauges(p);restoreBoard(p);} if(w!=null){clearLights();erasePad();eraseFactory();clean();for(Entity e:w.getEntities())if(e.getScoreboardTags().contains("scrap_ship")||e.getScoreboardTags().contains("scrap_cargo"))e.remove();release();} }
  void rule(boolean value,String... names){
    for(String n:names)if(w.isGameRule(n)&&w.setGameRuleValue(n,Boolean.toString(value)))return;
    getLogger().warning("Game rule not found: "+Arrays.toString(names));
  }
  Location loc(Pt p){return new Location(w,p.x+.5,Y+1,p.z+.5);}
  static Pt rotate(Pt p,int turn){return switch(Math.floorMod(turn,4)){case 1->new Pt(-p.z,p.x);case 2->new Pt(-p.x,-p.z);case 3->new Pt(p.z,-p.x);default->p;};}
  Pt translated(Pt origin,Pt delta,int turn){Pt q=rotate(delta,turn);return origin.add(q.x,q.z);}
  Pt shipPoint(Pt p){return translated(new Pt(shipOffset,shipZ),p,shipTurn);}
  Pt shipRelative(int x,int z){return rotate(new Pt(x-shipOffset,z-shipZ),-shipTurn);}
  double turnAxis(double x,double z,int turn){return switch(Math.floorMod(turn,4)){case 1->-z;case 2->-x;case 3->z;default->x;};}
  Location cabin(){return arrival(new Location(w,9.5,2,6.5,-90,0));}
  Location berthPose(Location l){double x=l.getX()-shipOffset-.5,z=l.getZ()-shipZ-.5;
    return new Location(w,.5+turnAxis(x,z,-shipTurn),l.getY()-Y-shipLift,.5+turnAxis(z,-x,-shipTurn),l.getYaw()-shipTurn*90,l.getPitch());}
  Location berthPose(Player p){return berthPose(p.getLocation());}
  Map<UUID,Location> crewPos(Set<UUID> saved){Map<UUID,Location> poses=new HashMap<>();for(Player p:w.getPlayers())if(saved.contains(p.getUniqueId())&&!p.isDead()&&aboard(p))poses.put(p.getUniqueId(),berthPose(p));return poses;}
  Location arrival(Location pose){if(pose==null)return cabin();double x=pose.getX()-.5,z=pose.getZ()-.5;
    return new Location(w,shipOffset+.5+turnAxis(x,z,shipTurn),Y+shipLift+pose.getY(),shipZ+.5+turnAxis(z,-x,shipTurn),org.bukkit.Location.normalizeYaw(pose.getYaw()+shipTurn*90),pose.getPitch());}
  Block shipBlock(int x,int y,int z){Pt p=shipPoint(new Pt(x,z));return w.getBlockAt(p.x,Y+shipLift+y,p.z);}
  boolean onShipBlock(Block b,int x,int y,int z){Pt p=shipPoint(new Pt(x,z));return b.getX()==p.x&&b.getY()==Y+shipLift+y&&b.getZ()==p.z;}
  void recall(Player operator){
    if(!active||!aboard(operator)){operator.sendMessage("§7Телепортер работает на борту во время вылазки.");return;}
    Player target=w.getPlayers().stream().filter(p->p!=operator&&!p.isDead()&&p.getGameMode()!=GameMode.SPECTATOR&&p.getGameMode()!=GameMode.CREATIVE&&!dead.contains(p.getUniqueId())&&!aboard(p)).min(Comparator.comparingDouble(p->p.getLocation().distanceSquared(operator.getLocation()))).orElse(null);
    if(target==null){operator.sendMessage("§7Нет членов экипажа вне корабля.");return;}
    target.teleport(cabin());target.removePotionEffect(org.bukkit.potion.PotionEffectType.BLINDNESS);
    target.playSound(target.getLocation(),Sound.ENTITY_ENDERMAN_TELEPORT,.8f,1);operator.sendMessage("§aНа борт возвращён игрок: "+target.getName());
  }
  org.bukkit.block.data.BlockData turned(org.bukkit.block.data.BlockData data,int turn){
    var d=data.clone();d.rotate(switch(turn){case 1->org.bukkit.block.structure.StructureRotation.CLOCKWISE_90;case 2->org.bukkit.block.structure.StructureRotation.CLOCKWISE_180;case 3->org.bukkit.block.structure.StructureRotation.COUNTERCLOCKWISE_90;default->org.bukkit.block.structure.StructureRotation.NONE;});return d;
  }
  Pt real(Pt p){return p.add(p.x>=120?buildShift:orbitBuild?ORBIT:0,0);}
  Block block(Pt p,int y){return w.getBlockAt(real(p).x,Y+y,p.z);}
  void set(Pt p,int y,Material m){block(p,y).setType(m,false);}
  void button(Pt p){set(p,1,Material.STONE_BUTTON); Switch s=(Switch)block(p,1).getBlockData(); s.setAttachedFace(org.bukkit.block.data.FaceAttachable.AttachedFace.FLOOR);block(p,1).setBlockData(s,false);}
  void ship(){
    // Clear the envelope of the older, larger hull before rebuilding the compact shuttle.
    for(int x=-8;x<=8;x++)for(int z=-13;z<=13;z++)for(int y=-7;y<=16;y++)set(new Pt(x,z),y,Material.AIR);
    for(int x=CABIN_LEFT-1;x<=CABIN_RIGHT+1;x++)for(int z=-SHIP_GATE;z<=SHIP_GATE;z++){
      Pt p=new Pt(x,z);boolean shell=x<CABIN_LEFT||x>CABIN_RIGHT||Math.abs(z)==SHIP_GATE;
      set(p,-1,Material.POLISHED_DEEPSLATE);set(p,0,x==0?Material.IRON_BLOCK:Material.POLISHED_DEEPSLATE);
      for(int y=1;y<=3;y++)if(shell)set(p,y,y==2&&z==-SHIP_GATE&&x>=CABIN_LEFT&&x<=CABIN_RIGHT?Material.TINTED_GLASS:y==1?Material.POLISHED_BASALT:Material.GRAY_CONCRETE);
      set(p,4,x==0&&z==0?Material.SEA_LANTERN:Material.IRON_BLOCK);
    }
    for(int z:new int[]{-2,2})for(int x:new int[]{CABIN_LEFT-1,CABIN_RIGHT+1})for(int y=1;y<=3;y++)set(new Pt(x,z),y,Material.IRON_BLOCK);
    for(int y=1;y<=2;y++)set(new Pt(CABIN_LEFT,1),y,Material.RED_TERRACOTTA);
    Pt suit=new Pt(CABIN_RIGHT,1);var banner=(org.bukkit.block.data.Directional)Material.ORANGE_WALL_BANNER.createBlockData();banner.setFacing(org.bukkit.block.BlockFace.WEST);block(suit,2).setBlockData(banner,false);
    set(new Pt(0,-2),4,Material.OCHRE_FROGLIGHT);set(new Pt(0,2),4,Material.OCHRE_FROGLIGHT);
    set(new Pt(-1,-3),1,Material.POLISHED_BLACKSTONE);set(new Pt(-1,-3),2,Material.CYAN_STAINED_GLASS);
    set(new Pt(1,-3),1,Material.POLISHED_BLACKSTONE);set(new Pt(1,-3),2,Material.GREEN_STAINED_GLASS);
    scenery.shipDetails();
    set(PILOT,0,Material.ORANGE_TERRACOTTA);
    var launchButton=(Switch)Material.STONE_BUTTON.createBlockData();launchButton.setAttachedFace(org.bukkit.block.data.FaceAttachable.AttachedFace.WALL);launchButton.setFacing(org.bukkit.block.BlockFace.SOUTH);block(PILOT,1).setBlockData(launchButton,false);
    for(int dx:new int[]{-1,2})for(int y=1;y<=3;y++)set(new Pt(dx,SHIP_GATE),y,y==2?Material.YELLOW_TERRACOTTA:Material.POLISHED_DEEPSLATE);
    for(int dx=0;dx<=1;dx++)set(new Pt(dx,SHIP_GATE),4,Material.POLISHED_BLACKSTONE);terminal(CHARGE,Material.LODESTONE,Material.CYAN_CONCRETE,"§bЗАРЯДКА");set(SELL,0,Material.LIME_CONCRETE);
  }
  void yard(){
    for(int cx=12;cx<=15;cx++)for(int cz=-2;cz<=1;cz++)w.getChunkAt(cx+buildShift/16,cz).addPluginChunkTicket(this);
    for(int x=199;x<=241;x++)for(int z=-18;z<=18;z++){
      Pt p=new Pt(x,z); boolean road=Math.abs(z)<=3;
      set(p,0,road?(Math.abs(z)==3?Material.YELLOW_TERRACOTTA:Material.GRAY_CONCRETE):((x*17+z*27)%5==0?Material.COARSE_DIRT:Material.GRAVEL));
      set(p,-1,Material.TUFF);set(p,-2,Material.STONE);
      if(Math.abs(z)==18||x==199){set(p,1,Material.COBBLED_DEEPSLATE);set(p,2,Material.IRON_BARS);set(p,3,Material.IRON_BARS);}
      if(x>=235){for(int y=1;y<=9;y++){
        boolean gate=x==235&&Math.abs(z)<=2&&y<=4;
        boolean window=x==235&&Math.abs(z)>4&&Math.abs(z)<15&&(y==5||y==6)&&z%5!=0;
        set(p,y,gate?(y==4?Material.OCHRE_FROGLIGHT:Material.BLACK_CONCRETE):window?Material.TINTED_GLASS:y==1||y==8?Material.WAXED_EXPOSED_CUT_COPPER:Material.POLISHED_TUFF);
      }}
    }
    for(int x=202;x<=207;x++)for(int z=-2;z<=2;z++)set(new Pt(x,z),0,Math.abs(z)==2?Material.WAXED_EXPOSED_CUT_COPPER:Material.IRON_BLOCK);
    for(int x=210;x<=230;x+=5)for(int z:new int[]{-5,5}){Pt p=new Pt(x,z);for(int y=1;y<=3;y++)set(p,y,Material.POLISHED_DEEPSLATE_WALL);set(p,4,Material.OCHRE_FROGLIGHT);}
    for(int x=211;x<=221;x++)for(int z=8;z<=12;z++)for(int y=1;y<=3;y++)set(new Pt(x,z),y,x%3==0?Material.WAXED_EXPOSED_CUT_COPPER:Material.RED_TERRACOTTA);
    for(int x=220;x<=229;x++)for(int z=-12;z<=-8;z++)for(int y=1;y<=3;y++)set(new Pt(x,z),y,x%3==0?Material.IRON_BLOCK:Material.GRAY_TERRACOTTA);
    for(int z:new int[]{-13,13})for(int x=236;x<240;x++)for(int y=10;y<16;y++)set(new Pt(x,z),y,y==15?Material.COPPER_GRATE:Material.DEEPSLATE_TILES);

  }
  void clearOldLander(){
    for(int x=LAND-8;x<=LAND+8;x++)for(int z=-13;z<=13;z++)for(int y=-7;y<=16;y++)set(new Pt(x,z),y,Material.AIR);
  }
  void mirrorShip(int n,Pt berth){
    int center=berth.x, shiftZ=berth.z, turn=n==3?0:landingTurn;
    for(int x=-8;x<=8;x++)for(int z=-13;z<=13;z++)for(int y=-7;y<=16;y++){
      Pt p=new Pt(x,z), q=translated(berth,p,turn);Block src=block(p.add(ORBIT,0),y);
      if(!src.getType().isAir()||(n==3&&y>=1&&y<=16))block(q,y+(n==3?0:landingLift)).setBlockData(turned(src.getBlockData(),turn),false);
    }
    if(n==3){
      for(int z=SHIP_GATE+1;z<=13;z++)scenery.path(center,z);
      for(int x=center;x<202;x++)for(int z=11;z<=13;z++)scenery.path(x,z);
    }

  }
  void gate(int offset,boolean open){
    for(int a=5;a<=6;a++)for(int b=2;b<=3;b++){Block block=shipBlock(a,b,5);if(block.getBlockData() instanceof org.bukkit.block.data.type.Door door){door.setOpen(open);block.setBlockData(door,false);}}
  }
  void closeShipDoors(){
    for(int x=0;x<=17;x++)for(int z=0;z<=11;z++)for(int y=0;y<=11;y++){
      Block b=shipBlock(x,y,z);
      if(b.getType()==Material.MANGROVE_DOOR&&b.getBlockData() instanceof org.bukkit.block.data.type.Door door&&door.isOpen()){
        door.setOpen(false);b.setBlockData(door,false);
      }
    }
  }
  boolean toggleShipDoor(Block clicked,Player player){
    if(clicked.getType()!=Material.MANGROVE_DOOR||!(clicked.getBlockData() instanceof org.bukkit.block.data.type.Door door))return false;
    Pt local=shipRelative(clicked.getX(),clicked.getZ());int y=clicked.getY()-Y-shipLift;
    if(local.x<0||local.x>17||local.z<0||local.z>11||y<0||y>11)return false;
    boolean open=!door.isOpen();
    if((local.x==5||local.x==6)&&local.z==5&&y>=2&&y<=3)gate(shipOffset,open);
    else{Block lower=door.getHalf()==org.bukkit.block.data.Bisected.Half.TOP?clicked.getRelative(org.bukkit.block.BlockFace.DOWN):clicked;
      for(Block part:List.of(lower,lower.getRelative(org.bukkit.block.BlockFace.UP)))if(part.getType()==Material.MANGROVE_DOOR&&part.getBlockData() instanceof org.bukkit.block.data.type.Door d){d.setOpen(open);part.setBlockData(d,false);}}
    w.playSound(clicked.getLocation(),open?Sound.BLOCK_WOODEN_DOOR_OPEN:Sound.BLOCK_WOODEN_DOOR_CLOSE,.8f,1);return true;
  }
  Pt factoryLocal(Pt q){return translated(factoryOrigin,q,factoryTurn);}
  Pt factoryWorld(Pt q){return factoryLocal(q).add(SECTOR*(district+1),0);}
  final Map<Block,org.bukkit.block.data.BlockData> factoryUndo=new LinkedHashMap<>();
  void factoryBlock(Pt q,int y,Material material){Block b=block(factoryLocal(q),y);factoryUndo.putIfAbsent(b,b.getBlockData());b.setType(material,false);}
  void eraseFactory(){for(var e:factoryUndo.entrySet())e.getKey().setBlockData(e.getValue(),false);factoryUndo.clear();}
  void placeFactory(int n){
    factoryTurn=rng.nextInt(4);factoryOrigin=new Pt(rng.nextInt(226,280),rng.nextInt(-23,24));
    buildShift=SECTOR*(n+1);
    try{
      Material skin=new Material[]{Material.DEEPSLATE_BRICKS,Material.POLISHED_BLACKSTONE_BRICKS,Material.POLISHED_BASALT}[n];
      for(int x=-3;x<=36;x++)for(int z=-20;z<=20;z++){
        if(x<0&&(z< -3||z>4))continue;
        Pt q=new Pt(x,z), p=factoryLocal(q);int h=terrainHeight(n,p.x,p.z);
        for(int y=Math.min(h,-1);y<=0;y++)factoryBlock(q,y,y==0?Material.DEEPSLATE_TILES:Material.TUFF);
        int roof=13+(x>=16&&x<=27&&Math.abs(z)<=12?4:0);
        boolean wall=x==0||x==36||Math.abs(z)==20,rib=x%6==0&&Math.abs(z)==20||x==0&&z%5==0;
        for(int y=1;y<=roof+1;y++){
          Material m=Material.AIR;
          if(x>=0){
            if(y==roof)m=Material.DEEPSLATE_TILES;
            else if(y<roof&&(wall||y<=h)){m=rib?Material.POLISHED_BLACKSTONE:y==3||y==10?Material.WAXED_WEATHERED_CUT_COPPER:skin;
              if(wall&&!rib&&y>=5&&y<=8&&(x==0?Math.abs(z)>3&&z%5!=0:x%6>=2&&x%6<=4))m=Material.TINTED_GLASS;}
            if(y==roof+1&&(x==0||x==36||Math.abs(z)==20))m=Material.POLISHED_BLACKSTONE_BRICK_WALL;
          }
          factoryBlock(q,y,m);
        }
      }
      // Raised loading roof and dark industrial exhaust stacks within the footprint.
      for(int x=16;x<=28;x++)for(int z=-13;z<=13;z++)if(x==16||x==28||Math.abs(z)==13)
        for(int y=13;y<=17;y++)factoryBlock(new Pt(x,z),y,skin);
      for(int zz:new int[]{-15,15})for(int x=29;x<=32;x++)for(int z=zz-1;z<=zz+1;z++)for(int y=13;y<=23;y++)
        factoryBlock(new Pt(x,z),y,y==23?Material.POLISHED_BLACKSTONE:y%5==0?Material.WAXED_EXPOSED_CUT_COPPER:Material.DEEPSLATE_TILES);
      for(int x=-22;x<=-1;x++)for(int z=-13;z<=14;z++){
        Pt q=new Pt(x,z), p=factoryLocal(q);int h=terrainHeight(n,p.x,p.z);
        int margin=Math.min(x+22,Math.min(z+13,14-z));
        int level=Math.clamp(h,-Math.max(0,5-margin),Math.max(0,5-margin));
        for(int y=Math.min(h,level)-1;y<=Math.max(h,level)+3;y++)
          factoryBlock(q,y,y<level?Material.TUFF:y>level?Material.AIR:margin<3?Material.GRAVEL:(Math.floorMod(x+z,7)==0?Material.CRACKED_DEEPSLATE_TILES:Material.DEEPSLATE_TILES));
      }
      for(int z=0;z<=1;z++){
        Pt q=factoryLocal(new Pt(0,z));var door=(org.bukkit.block.data.type.Door)Material.IRON_DOOR.createBlockData();door.setFacing(org.bukkit.block.BlockFace.WEST);door.setHinge(z==0?org.bukkit.block.data.type.Door.Hinge.LEFT:org.bukkit.block.data.type.Door.Hinge.RIGHT);
        block(q,1).setBlockData(turned(door,factoryTurn),false);door.setHalf(org.bukkit.block.data.Bisected.Half.TOP);block(q,2).setBlockData(turned(door,factoryTurn),false);
      }
    }finally{buildShift=0;}
  }
  void startSite(int n){
    if(n==3){clearOldLander();scenery.marketGround();market();return;}
    // On existing 2.x maps erase the old fixed landing, tunnel and the lit road.
    for(int x=143;x<=241;x++)for(int z=-27;z<=27;z++)for(int y=-7;y<=17;y++)set(new Pt(x,z),y,Material.AIR);
  }
  void finishSite(int n){
    if(n==3) {scenery.marketDetail();mirrorShip(n,new Pt(LAND,0));}
    built.add(n);
  }
  final Map<Block,org.bukkit.block.data.BlockData> padUndo=new LinkedHashMap<>();
  Pt choosePad(int n){
    Pt best=factoryLocal(new Pt(-55,0));int bestScore=Integer.MIN_VALUE;
    for(int i=0;i<750;i++){
      Pt p=factoryOrigin.add(rng.nextInt(-70,71),rng.nextInt(-70,71));
      if(p.x<SITE_X0+35||p.x>SITE_X1-35||p.z<SITE_Z0+35||p.z>SITE_Z1-35)continue;
      double d=Math.hypot(p.x-factoryOrigin.x,p.z-factoryOrigin.z);if(d<42||d>65)continue;
      Pt fp=rotate(new Pt(p.x-factoryOrigin.x,p.z-factoryOrigin.z),-factoryTurn);if(fp.x>=-38&&fp.x<=52&&Math.abs(fp.z)<=36)continue;
      int low=100,high=-100;for(int x=-3;x<=4;x++)for(int z=-5;z<=5;z++){
        Pt q=translated(p,new Pt(x,z),landingTurn);int h=terrainHeight(n,q.x,q.z);low=Math.min(low,h);high=Math.max(high,h);
      }
      int score=low*4-(high-low)*5+rng.nextInt(8);if(score>bestScore){best=p;bestScore=score;}
    }return best;
  }
  void rememberPad(Pt p,int y){Block b=block(p,y);padUndo.putIfAbsent(b,b.getBlockData());}
  void padBlock(Pt p,int y,Material m){rememberPad(p,y);set(p,y,m);}
  Pt landShip(int n){
    landingTurn=rng.nextInt(4);pad=choosePad(n);padDistrict=n;buildShift=SECTOR*(n+1);
    try{
      int high=0;for(int dx=-3;dx<=4;dx++)for(int dz=-5;dz<=5;dz++){
        Pt p=translated(pad,new Pt(dx,dz),landingTurn);high=Math.max(high,terrainHeight(n,p.x,p.z));
      }
      landingLift=Math.max(3,high+1);
      for(int dx=-3;dx<=4;dx++)for(int dz=-5;dz<=5;dz++){
        Pt p=translated(pad,new Pt(dx,dz),landingTurn);int h=terrainHeight(n,p.x,p.z);
        for(int y=Math.min(h,landingLift-3);y<=landingLift+7;y++){
          rememberPad(p,y);if(y>=landingLift)set(p,y,Material.AIR);
          else if(block(p,y).getType().isAir())set(p,y,Material.TUFF);
        }
      }
      mirrorShip(n,pad);
      int previous=landingLift;
      for(int z=6;z<=18;z++){
        Pt a=translated(pad,new Pt(0,z),landingTurn),b=translated(pad,new Pt(1,z),landingTurn);
        int h=Math.max(terrainHeight(n,a.x,a.z),terrainHeight(n,b.x,b.z));int level=Math.clamp(h,previous-1,previous+1);
        for(Pt p:List.of(a,b)){
          int base=terrainHeight(n,p.x,p.z);
          for(int y=Math.min(base,level);y<=Math.max(base,level)+2;y++)padBlock(p,y,y==level?Material.GRAVEL:y<level?Material.TUFF:Material.AIR);
        }
        previous=level;if(z>=8&&level==h)break;
      }
      return pad.add(buildShift,0);
    }finally{buildShift=0;}
  }
  void erasePad(){pad=null;for(var entry:padUndo.entrySet())entry.getKey().setBlockData(entry.getValue(),false);padUndo.clear();}
  void site(int n){if(built.contains(n))return;buildShift=SECTOR*(n+1);try{startSite(n);if(n<3)terrain(n,SITE_X0,SITE_X1,SITE_Z0,SITE_Z1);finishSite(n);}finally{buildShift=0;}}
  void prepareSite(int n,Runnable complete){
    if(built.contains(n)){complete.run();return;}
    buildShift=SECTOR*(n+1);try{startSite(n);}finally{buildShift=0;}
    new org.bukkit.scheduler.BukkitRunnable(){int x=SITE_X0,z=SITE_Z0;boolean sweep=n==0||!getConfig().getBoolean("terrain-v298."+n);@Override public void run(){
      boolean done=false;buildShift=SECTOR*(n+1);
      try{if(sweep)clearSurface(x,Math.min(SITE_X1,x+15),z,Math.min(SITE_Z1,z+31));else terrain(n,x,Math.min(SITE_X1,x+15),z,Math.min(SITE_Z1,z+31));z+=32;if(z>SITE_Z1){z=SITE_Z0;x+=16;}if(x>SITE_X1){if(sweep){sweep=false;x=SITE_X0;z=SITE_Z0;}else{cancel();finishSite(n);getConfig().set("terrain-v298."+n,true);persist();done=true;}}}
      catch(RuntimeException ex){cancel();flight=false;getLogger().log(java.util.logging.Level.SEVERE,"Landing terrain generation failed",ex);say("§cПосадочная зона не загрузилась. Повторите вылет.");}
      finally{buildShift=0;}if(done)complete.run();
    }}.runTaskTimer(this,1,1);
  }
  void clearSurface(int x0,int x1,int z0,int z1){
    for(int x=x0;x<=x1;x++)for(int z=z0;z<=z1;z++){
      int wx=x+buildShift;
      for(int y=w.getHighestBlockYAt(wx,z);y>=Y-8;y--){Block b=w.getBlockAt(wx,y,z);if(!b.getType().isAir())b.setType(Material.AIR,false);}
    }
  }
  void terrain(int n,int minX,int maxX,int minZ,int maxZ){
    Material top=new Material[]{Material.PODZOL,Material.BROWN_TERRACOTTA,Material.PACKED_ICE}[n];
    Material rock=new Material[]{Material.DEEPSLATE,Material.BLACKSTONE,Material.DEEPSLATE}[n];
    Material soil=new Material[]{Material.TUFF,Material.GRAY_TERRACOTTA,Material.BASALT}[n];
    // Generate a solid 250 x 250 terrain mass with no patterned pillars.
    for(int x=minX;x<=maxX;x++)for(int z=minZ;z<=maxZ;z++){
      int h=terrainHeight(n,x,z);
      Pt p=new Pt(x,z);Material surface=h>=6?rock:top;int idx=moonIndex(x,z);MoonTerrain map=moonTerrain[n];
      if(map!=null&&idx>=0&&Math.min(Math.min(x-SITE_X0,SITE_X1-x),Math.min(z-SITE_Z0,SITE_Z1-z))>=28){Material sampled=map.palette[Byte.toUnsignedInt(map.cells[idx+1])];if(sampled!=null)surface=sampled;}
      for(int y=-8;y<=h;y++)set(p,y,y==h?surface:soil);
      int noise=Math.floorMod(x*71+z*119+n*23,101);
      if(n==0&&h<10&&x>SITE_X0+46&&x<SITE_X1-46&&z>SITE_Z0+46&&z<SITE_Z1-46&&!(x>=193&&x<=247&&Math.abs(z)<=24)&&Math.floorMod(x,21)==Math.floorMod(z*3,9)&&Math.floorMod(z,21)==Math.floorMod(x*3,9)&&noise<63)scenery.tree(p,h,noise);

    }
  }
  int terrainHeight(int n,int x,int z){
    int edge=Math.min(Math.min(x-SITE_X0,SITE_X1-x),Math.min(z-SITE_Z0,SITE_Z1-z));
    double border=Math.clamp((29-edge)/26.0,0,1);
    double hills=softNoise(n,x,z,39)*12+softNoise(n+13,x,z,17)*3;int idx=moonIndex(x,z);if(moonTerrain[n]!=null&&idx>=0)hills=Byte.toUnsignedInt(moonTerrain[n].cells[idx])-16;
    return Math.clamp((int)Math.round(hills+border*border*(44+softNoise(n+4,x,z,24)*12)), -5,61);

  }
  double softNoise(int seed,int x,int z,int size){
    int gx=Math.floorDiv(x,size),gz=Math.floorDiv(z,size);double u=Math.floorMod(x,size)/(double)size,v=Math.floorMod(z,size)/(double)size;
    u=u*u*(3-2*u);v=v*v*(3-2*v);
    double a=heightSeed(seed,gx,gz)*(1-u)+heightSeed(seed,gx+1,gz)*u,b=heightSeed(seed,gx,gz+1)*(1-u)+heightSeed(seed,gx+1,gz+1)*u;
    return a*(1-v)+b*v;
  }
  double heightSeed(int seed,int x,int z){long k=x*341873128712L+z*132897987541L+seed*78491771L;k=(k^(k>>>30))*0xbf58476d1ce4e5b9L;k=(k^(k>>>27))*0x94d049bb133111ebL;return ((k^(k>>>31))>>>11)*0x1.0p-52-1;}
  void market(){
    for(int x=198;x<=226;x++)for(int z=6;z<=19;z++){
      Pt p=new Pt(x,z);set(p,0,Math.abs(z-12)<=1?Material.IRON_BLOCK:Material.DEEPSLATE_TILES);
      if(x>=217||z==6||z==19){for(int y=1;y<=5;y++)set(p,y,x==217&&z>=10&&z<=14&&y==2?Material.TINTED_GLASS:y==4?Material.YELLOW_CONCRETE:Material.POLISHED_TUFF);}
      if(x==217&&Math.abs(z-12)<=2)set(p,1,Material.IRON_BLOCK);
    }
    for(int x=202;x<217;x+=5){set(new Pt(x,9),1,Material.IRON_BARS);set(new Pt(x,9),2,Material.IRON_BARS);set(new Pt(x,9),3,Material.OCHRE_FROGLIGHT);}
    terminal(new Pt(216,12),Material.BELL,Material.YELLOW_CONCRETE,"§eСКУПЩИК · ПОЗВОНИТЬ");
    set(new Pt(218,12),1,Material.AIR);set(new Pt(218,12),2,Material.AIR);set(new Pt(218,12),3,Material.AIR);
    Location at=loc(new Pt(218+buildShift,12));at.setYaw(90);w.spawn(at,Villager.class,v->{v.setAI(false);v.setSilent(true);v.setInvulnerable(true);v.setPersistent(false);v.setCustomNameVisible(false);v.addScoreboardTag("scrap_ship");});
  }
  void terminal(Pt p,Material object,Material pad,String text){set(p,0,pad);set(p,1,object);
    if(block(p,1).getBlockData() instanceof org.bukkit.block.data.Directional data){data.setFacing(org.bukkit.block.BlockFace.SOUTH);block(p,1).setBlockData(data,false);}
  }
  void portalDoor(Pt left,String title,org.bukkit.block.BlockFace facing){
    for(int offset=0;offset<=1;offset++){
      Pt p=left.add(0,offset);set(p,0,Material.POLISHED_BLACKSTONE);set(p,3,Material.WAXED_EXPOSED_CUT_COPPER);
      var data=(org.bukkit.block.data.type.Door)Material.IRON_DOOR.createBlockData();data.setFacing(facing);data.setHinge(offset==0?org.bukkit.block.data.type.Door.Hinge.LEFT:org.bukkit.block.data.type.Door.Hinge.RIGHT);
      block(p,1).setBlockData(data,false);data.setHalf(org.bukkit.block.data.Bisected.Half.TOP);block(p,2).setBlockData(data,false);
    }
  }
  boolean portalBlock(Pt click,Pt base){return click.equals(base)||click.equals(base.add(0,1));}
  final Map<UUID,Long> portalCooldown=new HashMap<>();
  boolean factorySide(Location l){return l.getWorld()==w&&l.getBlockX()>=X-10&&l.getBlockX()<X+175;}
  void crossDoor(Player p,boolean entering){
    if(entering==factorySide(p.getLocation()))return;
    UUID id=p.getUniqueId();long now=System.nanoTime();if(portalCooldown.containsKey(id)&&now<portalCooldown.get(id))return;
    Location original=p.getLocation(),dst=entering?loc(START):factoryReturns.get(id);
    if(entering)dst.setYaw(-90);
    else if(dst==null||dst.getWorld()!=w||Math.abs(dst.getBlockX()-moons[district].shift)>1024||factorySide(dst))dst=doorReturn();
    if(!p.teleport(dst))return;
    portalCooldown.put(id,now+700_000_000L);
    if(entering)factoryReturns.put(id,original);else{factoryReturns.remove(id);p.removePotionEffect(org.bukkit.potion.PotionEffectType.BLINDNESS);}

    p.playSound(dst,Sound.BLOCK_IRON_DOOR_OPEN,.65f,.78f);
    p.sendActionBar(Component.text(entering?PLANTS[layout]:SITES[district]));
  }
  Location doorReturn(){Moon m=moons[district];int x=(m.x0+m.x1)/2+m.shift,z=(m.z0+m.z1)/2;
    for(int r=1;r<=6;r++)for(int a=-r;a<=r;a++)for(int b=-r;b<=r;b++)for(int y=m.y0+2;y>=m.y0-3;y--){
      if(Math.max(Math.abs(a),Math.abs(b))!=r)continue;
      Block f=w.getBlockAt(x+a,y-1,z+b),feet=w.getBlockAt(x+a,y,z+b),head=w.getBlockAt(x+a,y+1,z+b);
      if(f.getType().isSolid()&&feet.isPassable()&&head.isPassable()&&!feet.isLiquid()&&!head.isLiquid())return new Location(w,x+a+.5,y,z+b+.5);
    }return cabin();
  }
  void clean(){captureLooseCargo();rigs.clear();for(UUID id:new ArrayList<>(floorLoot.keySet()))if(!shipCargo.containsKey(id))removeLoot(id);for(Entity e:w.getEntities()) if(e.getLocation().getBlockX()<14000&&!(e instanceof Player)&&!e.getScoreboardTags().contains("scrap_ship")&&!e.getScoreboardTags().contains("scrap_cargo"))e.remove();}
  void release(){for(Chunk c:tickets)if(!lootTickets.containsKey(c))c.removePluginChunkTicket(this);tickets.clear();}
  void pinLoot(Chunk chunk){if(lootTickets.merge(chunk,1,Integer::sum)==1&&!tickets.contains(chunk))chunk.addPluginChunkTicket(this);}
  void unpinLoot(Chunk chunk){if(lootTickets.computeIfPresent(chunk,(k,n)->n<=1?null:n-1)==null&&!tickets.contains(chunk))chunk.removePluginChunkTicket(this);}
  void pin(Pt p){Chunk c=block(p,0).getChunk();if(tickets.add(c))c.addPluginChunkTicket(this);}
  void rect(int x,int z,int r){for(int i=-r;i<=r;i++)for(int j=-r;j<=r;j++)floor.add(new Pt(x+i,z+j));}
  record Cell(Pt point,int y){}
  final Set<Cell> lowerCells=new HashSet<>();
  void lower(Pt q,int y,Material material){lowerCells.add(new Cell(q,y));set(q,y,material);}
  void serviceLevel(Pt c){
    for(int x=-3;x<=3;x++)for(int z=-3;z<=3;z++)for(int y=-7;y<=-1;y++){
      boolean wall=Math.abs(x)==3||Math.abs(z)==3;
      lower(c.add(x,z),y,y==-7?Material.DEEPSLATE_TILES:y==-1||wall?Material.DEEPSLATE_BRICKS:Material.AIR);
    }
    Pt shaft=c.add(-2,1),back=c.add(-3,1);
    for(int y=-6;y<=1;y++){lower(back,y,Material.POLISHED_DEEPSLATE);lower(shaft,y,Material.LADDER);var ladder=(org.bukkit.block.data.type.Ladder)Material.LADDER.createBlockData();ladder.setFacing(org.bukkit.block.BlockFace.EAST);block(shaft,y).setBlockData(ladder,false);}
    for(int x:new int[]{-1,1})lower(c.add(x,0),-2,Material.OCHRE_FROGLIGHT);
    for(int x=-1;x<=2;x++)lower(c.add(x,-2),-6,Material.WAXED_EXPOSED_COPPER_GRATE);
    spawnLoot(loc(c).add(1,-7,0),item(SCRAP[rng.nextInt(SCRAP.length)]),false);
  }
  void serviceTunnel(Pt from,Pt to){
    var path=new LinkedHashSet<Pt>();Pt t=from;
    while(t.x!=to.x){t=t.add(Integer.compare(to.x,t.x),0);path.add(t);}
    while(t.z!=to.z){t=t.add(0,Integer.compare(to.z,t.z));path.add(t);}
    for(Pt q:path)for(int[] d:new int[][]{{1,0},{-1,0},{0,1},{0,-1}}){Pt wall=q.add(d[0],d[1]);if(path.contains(wall)||Math.max(Math.abs(wall.x-from.x),Math.abs(wall.z-from.z))<=2||Math.max(Math.abs(wall.x-to.x),Math.abs(wall.z-to.z))<=2)continue;for(int y=-7;y<=-3;y++)lower(wall,y,Material.DEEPSLATE_BRICKS);}
    int step=0;for(Pt q:path){lower(q,-7,Material.DEEPSLATE_TILES);lower(q,-3,++step%17==0?Material.OCHRE_FROGLIGHT:Material.DEEPSLATE_BRICKS);for(int y=-6;y<=-4;y++)lower(q,y,Material.AIR);}
  }
  final Set<Pt> factoryScanWalls=new HashSet<>();
  final Set<Block> factoryLights=new HashSet<>();
  void station(){
    factoryLights.clear();factoryScanWalls.clear();
    clean();release();doors.clear();
    for(Cell cell:lowerCells)set(cell.point,cell.y,Material.AIR);lowerCells.clear();
    Set<Pt> old=new HashSet<>(floor); floor.clear();
    List<Pt> rooms=new ArrayList<>(); rooms.add(new Pt(0,0));
    int[][] dirs={{1,0},{-1,0},{0,1},{0,-1}};
    int tier=Math.min(8,difficultyTier);
    int spacing=18+2*Math.min(3,tier);
    int count=new int[]{15,17,14}[layout]+2*tier;
    while(rooms.size()<count){Pt a=rooms.get(rng.nextInt(rooms.size())); int[] d=dirs[rng.nextInt(4)]; Pt b=a.add(d[0],d[1]);
      if(b.x<0||b.x>(layout==2?4:5)+tier/2||Math.abs(b.z)>(layout==2?4:layout==1?2:3)+tier/3||rooms.contains(b))continue;
      rooms.add(b);
      for(int j=0;j<=spacing;j++){int x=X+a.x*spacing+j*d[0],z=a.z*spacing+j*d[1];rect(x,z,1);}
    }
    var loops=new ArrayList<Pt>();
    for(Pt a:rooms)for(int[] d:new int[][]{{1,0},{0,1}})if(rooms.contains(a.add(d[0],d[1]))){Pt c=new Pt(X+a.x*spacing+(spacing/2)*d[0],a.z*spacing+(spacing/2)*d[1]);if(!floor.contains(c))loops.add(new Pt(a.x*2+d[0],a.z*2+d[1]));}
    Collections.shuffle(loops,rng);for(int n=0;n<Math.min(new int[]{5,2,7}[layout],loops.size());n++){Pt v=loops.get(n);int dx=Math.floorMod(v.x,2),dz=Math.floorMod(v.z,2);int ax=Math.floorDiv(v.x,2),az=Math.floorDiv(v.z,2);for(int j=0;j<=spacing;j++)rect(X+ax*spacing+j*dx,az*spacing+j*dz,1);}
    Map<Pt,Integer> height=new HashMap<>();int index=0;
    for(Pt r:rooms){Pt c=new Pt(X+r.x*spacing,r.z*spacing);int radius=(layout==2?7:6)+Math.min(3,tier),rx=radius+(index%4==2?1:0),rz=radius+(index%4==3?1:0);
      int ceiling=hasLoft(index)?11:index%4==2?7:index%3==1?6:5;
      for(int dx=-rx;dx<=rx;dx++)for(int dz=-rz;dz<=rz;dz++){Pt q=c.add(dx,dz);floor.add(q);height.merge(q,ceiling,Math::max);}index++;}
    Set<Pt> walls=new HashSet<>();
    for(Pt p:floor){pin(p);for(int[] d:dirs){Pt q=p.add(d[0],d[1]);if(!floor.contains(q))walls.add(q);}}
    for(Pt p:old)for(int dx=-1;dx<=1;dx++)for(int dz=-1;dz<=1;dz++){Pt q=p.add(dx,dz);if(!floor.contains(q)&&!walls.contains(q))for(int y=-2;y<=12;y++)set(q,y,Material.AIR);}
    factoryScanWalls.addAll(walls);
    for(Pt p:floor){int roof=height.getOrDefault(p,4);boolean lane=Math.floorMod(p.x-X,spacing)==0||Math.floorMod(p.z,spacing)==0;
      set(p,-1,Material.DEEPSLATE_TILES);
      set(p,0,lane?Material.WAXED_WEATHERED_COPPER_GRATE:Material.DEEPSLATE_TILES);
      for(int y=1;y<=12;y++)set(p,y,y==roof?Material.DEEPSLATE_BRICKS:Material.AIR);
    }
    for(Pt p:walls){pin(p);for(int y=0;y<=12;y++)set(p,y,y==1?new Material[]{Material.POLISHED_TUFF,Material.POLISHED_BLACKSTONE_BRICKS,Material.POLISHED_ANDESITE}[layout]:y==3?new Material[]{Material.WAXED_WEATHERED_CUT_COPPER,Material.WAXED_EXPOSED_CUT_COPPER,Material.LIGHT_BLUE_TERRACOTTA}[layout]:Material.DEEPSLATE_BRICKS);}
    // Close vertical seams where a corridor or a low room borders a taller room.
    // Fill above the lower roof, leaving the traversable doorway under it intact.
    for(Pt p:floor){
      int roof=height.getOrDefault(p,4), seam=roof;
      for(int[] d:dirs){Pt q=p.add(d[0],d[1]);if(floor.contains(q))seam=Math.max(seam,height.getOrDefault(q,4));}
      for(int y=roof+1;y<seam;y++)set(p,y,Material.DEEPSLATE_BRICKS);
    }
    // Separate the exterior portal from the normal doors in generated rooms.
    for(int x=X-6;x<=X-3;x++)for(int z=-1;z<=2;z++){
      Pt q=new Pt(x,z);set(q,0,Material.DEEPSLATE_TILES);set(q,4,Material.DEEPSLATE_BRICKS);
      for(int y=1;y<=3;y++)set(q,y,(z==-1||z==2||x==X-6)?Material.DEEPSLATE_BRICKS:Material.AIR);
    }
    portalDoor(EXIT,"§bВЫХОД ВО ДВОР",org.bukkit.block.BlockFace.EAST);
    List<Integer> eligible=new ArrayList<>();
    for(int i=1;i<rooms.size();i++)if(i!=2&&i!=3&&i!=6&&i!=9)eligible.add(i);
    Collections.shuffle(eligible,rng);
    Set<Integer> groundLoot=new HashSet<>(eligible.subList(0,Math.min(eligible.size(),5+tier*2)));
    List<Integer> upperLoot=new ArrayList<>(List.of(2,6,10,13));Collections.shuffle(upperLoot,rng);
    var serviceRooms=new ArrayList<Pt>();
    int monsterIndex=0;
    for(int i=0;i<rooms.size();i++){
      Pt r=rooms.get(i),c=new Pt(X+r.x*spacing,r.z*spacing);
      furnish(c,i+layout*2,height.getOrDefault(c,5));
      if(hasLoft(i)){scenery.loft(c);if(upperLoot.subList(0,2).contains(i))spawnLoot(loc(c.add(1,-3)).add(0,5,0),item(SCRAP[rng.nextInt(SCRAP.length)]),false);}
      if(i==4||i==11){serviceLevel(c);serviceRooms.add(c);}
      if(i==0||i==3||i==6||i==9||i==12){int h=hasLoft(i)?9:height.getOrDefault(c,5)-1;for(int x:new int[]{-2,2}){Pt fixture=c.add(x,0);set(fixture,h,Material.OCHRE_FROGLIGHT);factoryLights.add(block(fixture,h));}}
      if(i==2)placeCargo(c.add(0,2),item(5));
      if(i==3)placeCargo(c.add(0,2),item(PRO));
      if(i==6)placeCargo(c.add(0,2),item(INHALER));
      if(i==9)placeCargo(c.add(0,2),item(STUN));
      if(i%(layout==1?2:4)==1){for(int[] d:dirs){Pt q=c.add(6*d[0],6*d[1]); if(floor.contains(q)&&floor.contains(c.add(8*d[0],8*d[1]))){installDoor(q,d);break;}}}
      if(i%5==4)for(int dx=-2;dx<=2;dx++)set(c.add(dx,2),1,Material.POLISHED_DEEPSLATE_SLAB);
      if(groundLoot.contains(i))placeCargo(c.add(rng.nextBoolean()?-1:2,-2),item(SCRAP[rng.nextInt(SCRAP.length)]));
      if(i==4||i==10||(day>1&&(i==7||i==12||(layout==1&&i==15)))||i>=rooms.size()-tier*2){
        spawnRig(c.add(1,1),Creature.values()[(layout*2+monsterIndex++)%Creature.values().length]);
      }
    }
    if(serviceRooms.size()==2)serviceTunnel(serviceRooms.get(0),serviceRooms.get(1));
  }
  boolean hasLoft(int i){return i==2||i==6||i==10||i==13;}
  void installDoor(Pt p,int[] axis){
    if(!doors.add(p))return;for(int v:new int[]{-1,1})for(int y=1;y<=2;y++)set(p.add(v*axis[1],v*axis[0]),y,Material.IRON_BLOCK);
    var d=(org.bukkit.block.data.type.Door)Material.IRON_DOOR.createBlockData();d.setFacing(axis[0]!=0?org.bukkit.block.BlockFace.EAST:org.bukkit.block.BlockFace.SOUTH);
    block(p,1).setBlockData(d,false);d.setHalf(org.bukkit.block.data.Bisected.Half.TOP);block(p,2).setBlockData(d,false);
  }
  void placeCargo(Pt p,ItemStack stack){spawnLoot(loc(p),stack,false);}
  UUID spawnLoot(Location floor,ItemStack stack,boolean cargo){
    Block support=floor.clone().subtract(0,1,0).getBlock();
    double top=support.getBoundingBox().getMaxY();
    if(top>=floor.getBlockY()-1&&top<=floor.getBlockY())floor.setY(top);
    String tag=cargo?"scrap_cargo":"scrap_floor";
    Chunk chunk=floor.getChunk();pinLoot(chunk);
    // Use the same fixed, horizontal display indoors, outdoors and on the ship.
    float yaw=rng.nextFloat()*(float)(Math.PI*2);
    NamespacedKey modelKey=stack.getItemMeta().getItemModel();
    PackService.FloorPose pose=packs.models>0&&modelKey!=null?packs.floorPoses.get(modelKey.getKey()):null;
    Quaternionf direction=new Quaternionf().rotationY(yaw);
    Quaternionf rotation=new Quaternionf(direction).mul(pose==null?new Quaternionf().rotationX(-(float)Math.PI/2):pose.rotation());
    Vector3f offset=pose==null?new Vector3f(0,.035f,0):direction.transform(new Vector3f(pose.offset()));
    int depth=w.getNearbyEntities(floor,.35,.75,.35,e->e instanceof Interaction&&floorLoot.containsKey(e.getUniqueId())).size();
    offset.add(0,Math.min(depth,11)*.018f,0);
    ItemDisplay visual=w.spawn(floor,ItemDisplay.class,d->{
      d.setPersistent(false);d.setItemStack(stack.clone());d.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.NONE);
      d.setBillboard(Display.Billboard.FIXED);
      d.setTransformation(new Transformation(offset,rotation,new Vector3f(PackService.FLOOR_SCALE),new Quaternionf()));
      // Zero display dimensions disable frustum-box culling for the flattened prop.
      d.setDisplayWidth(0);d.setDisplayHeight(0);d.setViewRange(1.0f);d.addScoreboardTag(tag);
    });
    Interaction hit=w.spawn(floor.clone().add(0,.015,0),Interaction.class,e->{e.setPersistent(false);e.setInteractionWidth(.8f);e.setInteractionHeight(.55f);e.setResponsive(true);e.addScoreboardTag(tag);});
    floorLoot.put(hit.getUniqueId(),new FloorLoot(hit,visual,stack.clone(),chunk));return hit.getUniqueId();
  }
  void removeLoot(UUID id){for(var key:new ArrayList<>(scanMarks.keySet()))if(key.loot.equals(id))removeScan(key);FloorLoot loot=floorLoot.remove(id);if(loot!=null){loot.hit.remove();loot.model.remove();unpinLoot(loot.chunk);}}
  void furnish(Pt c,int i,int roof){
    int theme=i%5;
    Material trim=switch(theme){case 0->Material.YELLOW_TERRACOTTA;case 1->Material.WAXED_EXPOSED_CUT_COPPER;case 2->Material.GRAY_CONCRETE;case 3->Material.RED_TERRACOTTA;default->Material.POLISHED_TUFF;};
    for(int x=-3;x<=3;x++)for(int z=-3;z<=3;z++)if(Math.abs(x)==3||Math.abs(z)==3)set(c.add(x,z),0,trim);
    set(c,roof,Material.DEEPSLATE_BRICKS);
    for(int x:new int[]{-3,3})for(int z:new int[]{-3,3}){
      Pt p=c.add(x,z);
      switch(theme){
        case 0 -> {set(p,1,Material.POLISHED_DEEPSLATE_WALL);set(p,2,Material.IRON_BARS);set(p,3,Material.IRON_BARS);}
        case 1 -> {set(p,1,Material.BARREL);set(p,2,Material.BARREL);}
        case 2 -> {set(p,1,Material.IRON_BLOCK);set(p,2,Material.GRAY_STAINED_GLASS);set(p,3,Material.WAXED_EXPOSED_CUT_COPPER);}
        case 3 -> {set(p,1,Material.WAXED_WEATHERED_COPPER);set(p,2,Material.COPPER_GRATE);set(p,3,Material.WAXED_WEATHERED_COPPER);}
        default -> {set(p,1,Material.SMOOTH_STONE);set(p,2,Material.IRON_BARS);set(p,3,Material.SMOOTH_STONE);}
      }
    }
    if(theme==3)for(int x=-1;x<=1;x++)for(int z=-1;z<=1;z++){set(c.add(x,z),-2,Material.TUFF);set(c.add(x,z),-1,Material.DEEPSLATE_TILES);set(c.add(x,z),0,Material.WAXED_COPPER_GRATE);}
    scenery.room(c,theme,roof);

  }
  void applyMobHealth(Zombie mob,boolean fresh){
    var max=mob.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH);
    double fraction=fresh?1:mob.getHealth()/max.getValue();
    max.setBaseValue(11.0*(mobHits+Math.min(8,difficultyTier)));
    mob.getAttribute(org.bukkit.attribute.Attribute.ARMOR).setBaseValue(0);
    mob.setHealth(Math.clamp(Math.ceil(fraction*(mobHits+Math.min(8,difficultyTier)))*11,1,max.getValue()));
  }
  void spawnRig(Pt p,Creature creature){
    String variant=creature.ordinal()%2==0?"watcher":"collector";
    Zombie m=w.spawn(loc(p),Zombie.class);m.setCustomNameVisible(false);m.setInvisible(true);m.setSilent(packs.importedCreatures);
    m.setPersistent(false);m.setRemoveWhenFarAway(false);m.setCanPickupItems(false);m.setAdult();
    applyMobHealth(m,true);
    EntityEquipment eq=m.getEquipment();eq.clear();
    // A non-rendering item makes the zombie immune to daylight; only the rig is visible with the pack.
    eq.setHelmet(model(Material.CARVED_PUMPKIN,"invisible",null));eq.setHelmetDropChance(0);
    List<ItemDisplay> parts=new ArrayList<>();
    if(packs.importedCreatures){
      m.getAttribute(org.bukkit.attribute.Attribute.MOVEMENT_SPEED).setBaseValue(creature.speed);
      m.getAttribute(org.bukkit.attribute.Attribute.ATTACK_DAMAGE).setBaseValue(creature.damage);
      parts.add(w.spawn(loc(p),ItemDisplay.class,d->{d.setItemStack(model(Material.PAPER,"pmc_"+creature.idle,null));d.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.NONE);d.setPersistent(false);d.setTeleportDuration(2);d.setViewRange(.85f);d.setDisplayWidth(4);d.setDisplayHeight(4);
        d.setTransformation(new Transformation(new Vector3f(0,creature.up,0),new Quaternionf(),new Vector3f(creature.scale,creature.scale,creature.scale),new Quaternionf()));}));
      rigs.add(new Rig(m,parts,creature));return;
    }
    for(String part:new String[]{"body","head","arm","arm","leg","leg"})parts.add(w.spawn(loc(p),ItemDisplay.class,d->{
      d.setItemStack(model(Material.PAPER,variant+"_"+part,null));d.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.NONE);
      d.setPersistent(false);d.setTeleportDuration(2);d.setInterpolationDuration(2);d.setViewRange(0.8f);d.setDisplayWidth(3);d.setDisplayHeight(3);
    }));
    rigs.add(new Rig(m,parts,null));
  }
  void animate(){
    for(Player p:w.getPlayers()){Breath state=breath.get(p.getUniqueId());if(state!=null&&state.locked){if(p.isSprinting())p.setSprinting(false);fatigue(p,state.level<=0);}}
    for(Iterator<Rig> itr=rigs.iterator();itr.hasNext();){Rig rig=itr.next();Zombie m=rig.mob;
      if(!m.isValid()||m.isDead()){for(ItemDisplay d:rig.parts)d.remove();itr.remove();continue;}
      Location l=m.getLocation();l.setPitch(0);
      if(rig.creature!=null){animateCreature(rig,l);continue;}
      double speed=m.getVelocity().lengthSquared();float stride=speed>0.003?0.55f:0.07f;
      float step=(float)Math.sin(m.getTicksLived()*0.43)*stride;
      for(int i=0;i<6;i++){ItemDisplay d=rig.parts.get(i);if(!d.isValid())continue;d.teleport(l);
        float sx=switch(i){case 2->-.34f;case 3->.34f;case 4->-.15f;case 5->.15f;default->0;};
        float sy=switch(i){case 0->1.25f;case 1->1.77f;case 2,3->1.39f;default->.64f;};
        float pitch=switch(i){case 2,5->step;case 3,4->-step;default->0;};
        d.setInterpolationDelay(0);d.setTransformation(new Transformation(new Vector3f(sx,sy,0),new Quaternionf().rotationX(pitch),new Vector3f(1,1,1),new Quaternionf()));
      }
    }
  }
  void creatureSound(Location at,String key,float volume){w.playSound(at,"scrapshift:creature."+key,SoundCategory.HOSTILE,volume,1);}
  boolean watched(Zombie mob){
    Location target=mob.getEyeLocation();
    for(Player p:w.getNearbyPlayers(target,16)){
      if(p.isDead()||p.getGameMode()==GameMode.SPECTATOR)continue;
      Location eye=p.getEyeLocation();var delta=target.toVector().subtract(eye.toVector());
      if(delta.lengthSquared()>.01&&eye.getDirection().dot(delta.normalize())>.96&&p.hasLineOfSight(mob))return true;
    }return false;
  }
  void animateCreature(Rig rig,Location at){
    ItemDisplay d=rig.parts.getFirst();if(!d.isValid())return;
    d.teleport(at);Zombie m=rig.mob;Creature c=rig.creature;int age=m.getTicksLived();
    if(age%3!=0)return;
    boolean chasing=m.getTarget() instanceof Player;
    boolean moving=m.getVelocity().getX()*m.getVelocity().getX()+m.getVelocity().getZ()*m.getVelocity().getZ()>.0025;
    if(chasing&&!rig.wasTargeted&&c.notice!=null)creatureSound(at,c.notice,.58f);
    rig.wasTargeted=chasing;rig.chaseTicks=chasing?rig.chaseTicks+3:0;
    double pace=c.speed;
    Player target=m.getTarget() instanceof Player p?p:null;
    switch(c){
      case COIL->{if(watched(m)){pace=0;m.getPathfinder().stopPathfinding();moving=false;}}
      case BRACKEN->{
        boolean observed=watched(m);rig.watchTicks=observed?Math.min(60,rig.watchTicks+3):Math.max(0,rig.watchTicks-3);
        if(observed&&rig.watchTicks<36){pace=0;m.getPathfinder().stopPathfinding();moving=false;}
        else pace=chasing?(rig.watchTicks>=36?.34:.29):.18;
      }
      case HOARDER->{
        if(age%18==0){
          FloorLoot stash=null;double best=64;
          for(FloorLoot loot:floorLoot.values())if(!shipCargo.containsKey(loot.hit.getUniqueId())){
            double dist=at.distanceSquared(loot.hit.getLocation());if(dist<best){best=dist;stash=loot;}
          }
          if(stash!=null){
            Location nest=stash.hit.getLocation();Player intruder=null;
            for(Player p:w.getNearbyPlayers(nest,3))if(!p.isDead()&&p.getGameMode()!=GameMode.SPECTATOR&&m.hasLineOfSight(p)){intruder=p;break;}
            if(intruder!=null)m.setTarget(intruder);
            else{m.setTarget(null);if(best>1.5)m.getPathfinder().moveTo(nest,.9);}
          }
        }
      }
      case THUMPER->pace=chasing?.21+Math.min(.22,rig.chaseTicks*.0025):.22;
      case JESTER->{if(chasing){pace=rig.chaseTicks<48?0:.42+Math.min(.10,(rig.chaseTicks-48)*.001);if(pace==0)m.getPathfinder().stopPathfinding();}}
      case NUTCRACKER->{
        if(target!=null&&m.hasLineOfSight(target)&&at.distanceSquared(target.getLocation())<=100){
          pace=0;m.getPathfinder().stopPathfinding();
          Breath motion=breath.get(target.getUniqueId());long now=System.nanoTime();
          if(motion!=null&&breathTick-motion.motion<=8&&now>=rig.nextAttack){rig.nextAttack=now+2_400_000_000L;
            w.playSound(at,Sound.ENTITY_GENERIC_EXPLODE,.38f,1.65f);target.damage(5,m);
          }
        }
      }
    }
    m.getAttribute(org.bukkit.attribute.Attribute.MOVEMENT_SPEED).setBaseValue(pace);
    String frame=c.idle;float up=c.up;
    if(c==Creature.JESTER&&chasing){frame=rig.chaseTicks<48?"jester_wind_"+(1+Math.min(7,rig.chaseTicks/6)):"jester_head";if(rig.chaseTicks>=48)up=.64f;if(rig.chaseTicks==48)creatureSound(at,"jester_end",.65f);}
    else if(moving&&c.walk.length>0){frame=c.walk[(age/6)%c.walk.length];
      if(age%12==0&&c==Creature.COIL)creatureSound(at,"coil_"+(1+(age/12)%4),.32f);
      if(age%12==0&&c==Creature.THUMPER)creatureSound(at,"thumper_step"+(1+(age/12)%2),.35f);}
    if(!frame.equals(rig.frame)){rig.frame=frame;d.setItemStack(model(Material.PAPER,"pmc_"+frame,null));d.setTransformation(new Transformation(new Vector3f(0,up,0),new Quaternionf(),new Vector3f(c.scale,c.scale,c.scale),new Quaternionf()));}
  }
  void hideBoard(Player p){oldBoards.putIfAbsent(p.getUniqueId(),p.getScoreboard());hidden.getTeam("scrap_crew").addEntry(p.getName());p.setScoreboard(hidden);}
  void restoreBoard(Player p){Scoreboard old=oldBoards.remove(p.getUniqueId());if(old!=null&&p.getScoreboard()==hidden)p.setScoreboard(old);}
  ItemStack model(Material m,String id,String name){ItemStack s=new ItemStack(m);ItemMeta meta=s.getItemMeta();meta.setItemModel(new NamespacedKey("scrapshift",id));if(name!=null)meta.setDisplayName(name);s.setItemMeta(meta);return s;}
  static int price(int i){return i<0?0:i<6?PRICES[i]:EXTRA[i-6].price;}
  boolean lamp(int i){return i==4||i==PRO;}
  int weight(int i){return i>=6?EXTRA[i-6].slots:i==2||i==3||i==5?2:1;}
  ItemStack item(int i){String name=i<6?NAMES[i]:EXTRA[i-6].name;String id=i<6?IDS[i]:EXTRA[i-6].id;
    ItemStack s=model(lamp(i)?Material.BLAZE_ROD:Material.PAPER,"mirrp_"+id,"§f"+name);ItemMeta m=s.getItemMeta();if(i==5)m.getPersistentDataContainer().set(ammoKey,PersistentDataType.INTEGER,BLASTER_CAPACITY);
    String text=lamp(i)?"ПКМ: фонарь":i==5?"ПКМ: выстрел":i==INHALER?"ПКМ: заряд бодрости":i==STUN?"ПКМ: бросить гранату":meleeTool(i)?"ЛКМ: удар · Цена: "+price(i):switch(id){case "brass_bell","clown_horn","cash_register","rubber_ducky","robot","hairdryer","small_fox","bottles","remote","clock","magic_7_ball","gift_box","comedy"->"ПКМ: использовать · Цена: "+price(i);default->"Цена: "+price(i);};
    m.setLore(List.of("§7Слоты: "+weight(i)+" · "+text));m.getPersistentDataContainer().set(key,PersistentDataType.INTEGER,i);s.setItemMeta(m);return s;}
  int id(ItemStack s){if(s==null||!s.hasItemMeta())return -1;Integer i=s.getItemMeta().getPersistentDataContainer().get(key,PersistentDataType.INTEGER);return i!=null&&i>=0&&i<6+EXTRA.length?i:-1;}
  ItemStack nativeModel(ItemStack input){ItemStack s=input.clone();int i=id(s);if(i>=0){ItemMeta m=s.getItemMeta();m.setItemModel(new NamespacedKey("scrapshift","mirrp_"+(i<6?IDS[i]:EXTRA[i-6].id)));s.setItemMeta(m);}return s;}
  int used(Player p){int n=0;for(int a=SLOT_FIRST;a<SLOT_END;a++){ItemStack s=p.getInventory().getItem(a);int i=id(s);if(i>=0)n+=weight(i)*s.getAmount();}return n;}
  NamespacedKey visorModel(){return new NamespacedKey("scrapshift",packs!=null&&packs.models>0?"mirrp_lethal_helmet":"lethal_helmet");}
  static final NamespacedKey CAMERA_OVERLAY=NamespacedKey.minecraft("misc/pumpkinblur");
  final Map<UUID,net.kyori.adventure.bossbar.BossBar> visorLayers=new HashMap<>();
  void showVisor(Player p){visorLayers.computeIfAbsent(p.getUniqueId(),id->{
    var layer=net.kyori.adventure.bossbar.BossBar.bossBar(Component.text("\uE300").font(HUD),0,net.kyori.adventure.bossbar.BossBar.Color.WHITE,net.kyori.adventure.bossbar.BossBar.Overlay.PROGRESS);
    p.showBossBar(layer);return layer;
  });}
  void equipMask(Player p){
    ItemStack mask=model(Material.CARVED_PUMPKIN,visorModel().getKey(),"§fМаска исследователя");
    mask.setData(io.papermc.paper.datacomponent.DataComponentTypes.EQUIPPABLE,
      io.papermc.paper.datacomponent.item.Equippable.equippable(EquipmentSlot.HEAD).cameraOverlay(CAMERA_OVERLAY).swappable(false).dispensable(false).damageOnHurt(false).build());
    p.getInventory().setHelmet(mask);
    showVisor(p);
  }
  void ensureMask(Player p){ItemStack item=p.getInventory().getHelmet();var wearable=item==null?null:item.getData(io.papermc.paper.datacomponent.DataComponentTypes.EQUIPPABLE);if(item==null||item.getType()!=Material.CARVED_PUMPKIN||!visorModel().equals(item.getItemMeta().getItemModel())||wearable==null||!CAMERA_OVERLAY.equals(wearable.cameraOverlay())||wearable.slot()!=EquipmentSlot.HEAD)equipMask(p);showVisor(p);}
  void reset(Player p){cursedMasks.remove(p.getUniqueId());liveBattery.remove(p.getUniqueId());fatigue(p,false);breath.remove(p.getUniqueId());p.setSprinting(false);p.removeScoreboardTag("scrap_lamp");p.getInventory().clear();equipMask(p);p.setGameMode(GameMode.ADVENTURE);p.setHealth(20);p.setFoodLevel(20);p.setSaturation(10);p.getActivePotionEffects().forEach(e->p.removePotionEffect(e.getType()));p.getInventory().setHeldItemSlot(SLOT_FIRST);p.getInventory().setItem(LAMP_SLOT,item(4));p.setLevel(0);p.setExp(0);p.setTotalExperience(0);}
  final Map<UUID,net.kyori.adventure.bossbar.BossBar> gaugesByPlayer=new HashMap<>();
  final Map<UUID,net.kyori.adventure.bossbar.BossBar> chargeBars=new HashMap<>();
  void chargeHud(Player p,int glyph){
    var layer=chargeBars.get(p.getUniqueId());
    if(glyph==0){if(layer!=null){p.hideBossBar(layer);chargeBars.remove(p.getUniqueId());}return;}
    var name=Component.text(String.valueOf((char)glyph)).font(HUD);
    if(layer==null){layer=net.kyori.adventure.bossbar.BossBar.bossBar(name,0,net.kyori.adventure.bossbar.BossBar.Color.WHITE,net.kyori.adventure.bossbar.BossBar.Overlay.PROGRESS);chargeBars.put(p.getUniqueId(),layer);p.showBossBar(layer);}
    else if(!layer.name().equals(name))layer.name(name);
  }
  void removeGauges(Player p){scannerCooldownHud(p,-1);chargeHud(p,0);var optic=opticalLayers.remove(p.getUniqueId());if(optic!=null)p.hideBossBar(optic);var visor=visorLayers.remove(p.getUniqueId());if(visor!=null)p.hideBossBar(visor);var gauge=gaugesByPlayer.remove(p.getUniqueId());if(gauge!=null)p.hideBossBar(gauge);}
  void enter(Player p){removeGauges(p);var gauge=net.kyori.adventure.bossbar.BossBar.bossBar(Component.empty(),1,net.kyori.adventure.bossbar.BossBar.Color.WHITE,net.kyori.adventure.bossbar.BossBar.Overlay.PROGRESS);gaugesByPlayer.put(p.getUniqueId(),gauge);p.showBossBar(gauge);p.teleport(cabin());reset(p);hideBoard(p);locationHud(p);showVisor(p);if(active&&dead.contains(p.getUniqueId()))p.setGameMode(GameMode.SPECTATOR);packs.offer(p);help(p);if(canConfigure(p))p.sendMessage(Component.text("[Настройки игры в шатле]",net.kyori.adventure.text.format.NamedTextColor.GOLD).clickEvent(net.kyori.adventure.text.event.ClickEvent.runCommand("/scrap settings")));}
  void help(Player p){p.sendMessage("§6SCRAP SHIFT §7| §fКнопка — запуск, задний шлюз — выход, квота — после "+dayLimit+" дн.. §7Слоты: 1–4. F: сканер. Фонарь: 5 и ПКМ, Q: на пол. Груз ускоряет расход выносливости. Не забудь вернуться на корабль!");}
  void say(String s){for(Player p:w.getPlayers())p.sendMessage(s);}
  void persist(){captureLooseCargo();getConfig().set("progress.first-launch-done",firstLaunchDone);getConfig().set("progress.moon-order",moonOrder);getConfig().set("progress.day",day);getConfig().set("progress.difficulty-tier",difficultyTier);getConfig().set("progress.bank",bank);getConfig().set("progress.quota",quota);getConfig().set("progress.company",company);getConfig().set("progress.pending-buyer",pendingBuyer);getConfig().set("progress.cycles",cycles);
    List<Map<String,Object>> data=new ArrayList<>();for(Cargo c:shipCargo.values())data.add(Map.of("x",c.x,"y",c.y,"z",c.z,"dx",c.dx,"dz",c.dz,"item",c.stack.clone()));getConfig().set("progress.cargo",data);getConfig().set("progress.cargo-overflow",new ArrayList<>(cargoOverflow));saveConfig();}
  void readCargo(){
    for(Map<?,?> row:getConfig().getMapList("progress.cargo"))if(row.get("x") instanceof Number x&&row.get("z") instanceof Number z&&row.get("item") instanceof ItemStack s&&id(s)>=0){
      Location near=arrival(new Location(w,Math.clamp(x.intValue(),1,16)+.5+cargoOffset(row.get("dx")),2,Math.clamp(z.intValue(),1,10)+.5+cargoOffset(row.get("dz"))));
      if(!dropLoot(near,nativeModel(s),true))cargoOverflow.add(nativeModel(s));
    }
    List<?> saved=getConfig().getList("progress.cargo-overflow",List.of());for(Object o:saved)if(o instanceof ItemStack s&&id(s)>=0)cargoOverflow.add(nativeModel(s));
    freeCargoSpace();
  }
  void freeCargoSpace(){while(!cargoOverflow.isEmpty()&&dropLoot(cabin(),cargoOverflow.peek(),true))cargoOverflow.remove();}
  static double cargoOffset(Object value){return value instanceof Number n&&Double.isFinite(n.doubleValue())?Math.clamp(n.doubleValue(),-.499999,.499999):0;}
  Location cargoLocation(Cargo c){return arrival(new Location(w,c.x+.5+c.dx,2,c.z+.5+c.dz));}
  void putCargo(Cargo c){UUID hit=spawnLoot(cargoLocation(c),c.stack,true);shipCargo.put(hit,c);}
  void moveShip(int next){moveShip(new Pt(next,0),0,0);}
  void moveShip(Pt next,int turn,int lift){restoreShuttleLights();captureLooseCargo();List<Cargo> load=new ArrayList<>(shipCargo.values());for(UUID id:shipCargo.keySet())removeLoot(id);shipCargo.clear();shipOffset=next.x;shipZ=next.z;shipTurn=turn;shipLift=lift;closeShipDoors();for(Cargo c:load){if(!dropLoot(cargoLocation(c),c.stack,true))cargoOverflow.add(c.stack);}
    freeCargoSpace();}
  boolean cargoDeck(int x,int z){return x>=1&&x<=16&&z>=1&&z<=10;}
  boolean onCargoDeck(Location position){
    Location local=berthPose(position);
    return cargoDeck(local.getBlockX(),local.getBlockZ())&&local.getY()>=1.9&&local.getY()<5;
  }
  void captureLooseCargo(){
    for(var entry:floorLoot.entrySet()){
      UUID id=entry.getKey();if(shipCargo.containsKey(id))continue;
      FloorLoot loot=entry.getValue();Location position=loot.hit.getLocation();if(!onCargoDeck(position))continue;
      Location local=berthPose(position);int x=local.getBlockX(),z=local.getBlockZ();
      shipCargo.put(id,new Cargo(x,position.getBlockY(),z,cargoOffset(local.getX()-x-.5),cargoOffset(local.getZ()-z-.5),loot.stack.clone()));
      loot.hit.addScoreboardTag("scrap_cargo");loot.model.addScoreboardTag("scrap_cargo");
    }
  }
  boolean validDrop(int x,int y,int z){if(y<=w.getMinHeight())return false;Block floor=w.getBlockAt(x,y-1,z),feet=w.getBlockAt(x,y,z);
    return !floor.isPassable()&&(feet.getType().isAir()||feet.isPassable()&&!feet.isLiquid()&&!feet.getType().name().endsWith("_DOOR"));
  }
  boolean dropLoot(Location at,ItemStack s,boolean aboard){
    int px=at.getBlockX(),pz=at.getBlockZ(),py=at.getBlockY();
    for(int r=0;r<=(aboard?7:3);r++)for(int dx=-r;dx<=r;dx++)for(int dz=-r;dz<=r;dz++){
      if(Math.max(Math.abs(dx),Math.abs(dz))!=r)continue;int x=px+dx,z=pz+dz;
      Pt local=shipRelative(x,z);if(aboard&&!cargoDeck(local.x,local.z))continue;
      for(int dy=0;dy<=(aboard?0:4);dy++){
        int y=aboard?Y+shipLift+2:py-dy;if(!validDrop(x,y,z))continue;
        double fx=at.getX()-px,fz=at.getZ()-pz;
        Location l=new Location(w,x+fx,y,z+fz);
        if(aboard){
          Pt ex=rotate(new Pt(1,0),-shipTurn),ez=rotate(new Pt(0,1),-shipTurn);
          double localDx=(fx-.5)*ex.x+(fz-.5)*ez.x,localDz=(fx-.5)*ex.z+(fz-.5)*ez.z;
          putCargo(new Cargo(local.x,y,local.z,cargoOffset(localDx),cargoOffset(localDz),s.clone()));
        }else spawnLoot(l,s,false);return true;
      }
    }return false;
  }
  void unload(Player p){for(int i=SLOT_FIRST;i<SLOT_END;i++){ItemStack s=p.getInventory().getItem(i);int k=id(s);if(k>=0&&k!=4){if(!dropLoot(p.getLocation(),s,true))cargoOverflow.add(s.clone());p.getInventory().setItem(i,null);}}}
  void fade(Player p){p.playSound(p.getLocation(),Sound.BLOCK_IRON_DOOR_CLOSE,1,.65f);}
  void refreshMask(Player p){
    var old=visorLayers.remove(p.getUniqueId());if(old!=null)p.hideBossBar(old);
    equipMask(p);
  }
  void departureSound(Player p){if(!p.isDead())refreshMask(p);p.playSound(p.getLocation(),Sound.BLOCK_IRON_DOOR_CLOSE,1,.68f);p.playSound(p.getLocation(),Sound.ENTITY_FIREWORK_ROCKET_LAUNCH,.38f,.55f);}
  void weather(int kind){
    w.setStorm(kind!=0);w.setThundering(kind==2);
    w.setWeatherDuration(Integer.MAX_VALUE);w.setThunderDuration(Integer.MAX_VALUE);
  }
  void launch(){
    if(active||flight||company)return;flight=true;firstLaunchDone=true;removeSettingsHologram();gate(shipOffset,false);persist();
    Set<UUID> saved=new HashSet<>();for(Player p:w.getPlayers()){if(!p.isDead()&&p.getGameMode()!=GameMode.SPECTATOR)saved.add(p.getUniqueId());departureSound(p);}
    if(pendingBuyer)travelToBuyer(saved);else travel(saved);
  }
  void routeTo(String name){destination=name;for(Player p:w.getPlayers())locationHud(p);}
  void travel(Set<UUID> saved){
    final long epoch=campaignEpoch;
    district=moonOrder.get((day-1)%moonOrder.size());layout=district;routeTo(SITES[district]);
    Moon m=moons[district];
    Bukkit.getScheduler().runTaskLater(this,()->{
      if(epoch!=campaignEpoch)return;
      var loads=new ArrayList<java.util.concurrent.CompletableFuture<Chunk>>();
      for(int x=(m.sx+m.shift)>>4;x<=(m.sx+m.shift+17)>>4;x++)for(int z=m.sz>>4;z<=(m.sz+11)>>4;z++)loads.add(w.getChunkAtAsync(x,z));
      java.util.concurrent.CompletableFuture.allOf(loads.toArray(java.util.concurrent.CompletableFuture[]::new)).whenComplete((ignored,error)->Bukkit.getScheduler().runTask(this,()->{
        if(epoch!=campaignEpoch)return;
        if(error!=null){flight=false;gate(shipOffset,false);getLogger().severe("Moon loading failed: "+error);return;}
        var poses=crewPos(saved);factoryReturns.clear();station();w.setTime(0);dead.clear();moveShip(new Pt(m.sx+m.shift,m.sz),0,m.sy-Y);
        arriveCrew(saved,poses);weather(rng.nextInt(3));active=true;flight=false;roundStarted=System.nanoTime();gate(shipOffset,false);persist();
        say("§6День "+day+"/"+dayLimit+" · "+SITES[district]+" · Найдите вход на завод.");
      }));
    },35);
  }
  void arriveCrew(Set<UUID> saved,Map<UUID,Location> poses){for(Player p:w.getPlayers())if(!p.isDead()){
    if(!saved.contains(p.getUniqueId()))reset(p);p.teleport(arrival(poses.get(p.getUniqueId())));equipMask(p);p.playSound(p.getLocation(),Sound.BLOCK_IRON_DOOR_OPEN,.72f,.8f);
  }}
  boolean insideCabin(int x,int z){return x>=3&&x<=14&&(Math.abs(z-6)<=1||(x>=9&&z>=3&&z<=8));}
  boolean aboard(Player p){Location l=berthPose(p);return insideCabin(l.getBlockX(),l.getBlockZ())&&l.getY()>=2&&l.getY()<5;}
  void end(){if(!active||flight||wipePending)return;long epoch=campaignEpoch;active=false;flight=true;weather(0);gate(shipOffset,false);
    Set<UUID> saved=new HashSet<>();for(Player p:w.getPlayers())if(!p.isDead()&&aboard(p)&&!dead.contains(p.getUniqueId()))saved.add(p.getUniqueId());
    for(Player p:w.getPlayers())if(!p.isDead()){if(!saved.contains(p.getUniqueId())){reset(p);p.teleport(cabin());}departureSound(p);}
    clean();release();
    routeTo("ШАТЛ");
    Bukkit.getScheduler().runTaskLater(this,()->{
      if(epoch!=campaignEpoch)return;
      var poses=crewPos(saved);
      if(day<dayLimit)day++;else pendingBuyer=true;
      moveShip(LOBBY_SHIP,0,LOBBY_Y-Y);w.setTime(18000);dead.clear();factoryReturns.clear();portalCooldown.clear();
      arriveCrew(saved,poses);flight=false;roundStarted=0;destination="";gate(shipOffset,false);persist();tick();
      say("§6Шатл · Следующая остановка: "+nextDestination()+". Нажмите кнопку, когда будете готовы.");
    },35);
  }
  String nextDestination(){return pendingBuyer?"71-GORDION":SITES[moonOrder.get((day-1)%moonOrder.size())];}
  void travelToBuyer(Set<UUID> saved){
    long epoch=campaignEpoch;routeTo("71-GORDION");
    Bukkit.getScheduler().runTaskLater(this,()->{
      if(epoch!=campaignEpoch)return;
      var poses=crewPos(saved);company=true;pendingBuyer=false;moveShip(COMPANY_SHIP,2,COMPANY_Y-Y);buyerNpc();dead.clear();arriveCrew(saved,poses);flight=false;w.setTime(14300);gate(shipOffset,false);persist();
      say("§6Пункт скупки. Вынесите лом с корабля и позвоните в колокол у окна. Кнопка завершает сдачу.");
    },35);
  }
  int sell(Player p){int credits=0;for(int a=SLOT_FIRST;a<SLOT_END;a++){ItemStack s=p.getInventory().getItem(a);int i=id(s);if(price(i)>0){credits+=price(i)*s.getAmount();p.getInventory().setItem(a,null);}}return credits;}
  void advanceQuota(boolean win){
    if(!win){quota=baseQuota;difficultyTier=0;return;}
    int next=(int)Math.min(1_000_000_000L,Math.ceil(quota*quotaMultiplier));
    if(next>quota&&difficultyTier<Integer.MAX_VALUE)difficultyTier++;
    quota=next;
  }
  void skipQuota(){
    campaignEpoch++;wipePending=false;active=false;flight=false;weather(0);
    Set<UUID> saved=new HashSet<>();for(Player p:w.getPlayers())if(!p.isDead()&&aboard(p)&&!dead.contains(p.getUniqueId()))saved.add(p.getUniqueId());
    var poses=crewPos(saved);gate(shipOffset,false);clean();clearLights();if(buyer!=null){buyer.remove();buyer=null;}
    moveShip(LOBBY_SHIP,0,LOBBY_Y-Y);w.setTime(18000);dead.clear();factoryReturns.clear();portalCooldown.clear();
    company=false;pendingBuyer=false;day=1;bank=0;roundStarted=0;destination="";advanceQuota(true);cycles++;shuffleRoute();
    arriveCrew(saved,poses);gate(shipOffset,false);persist();tick();say("§eКвота пропущена. Новый цикл: "+quota+" · дней: "+dayLimit);
  }
  void settle(){if(!company||flight||active)return;long epoch=campaignEpoch;flight=true;routeTo("ШАТЛ");boolean win=bank>=quota;gate(shipOffset,false);say((win?"§aКвота выполнена: ":"§cКвота провалена: ")+bank+"/"+quota);
    for(Player p:w.getPlayers())if(!p.isDead())departureSound(p);
    Bukkit.getScheduler().runTaskLater(this,()->{if(epoch!=campaignEpoch)return;Set<UUID> saved=new HashSet<>();for(Player p:w.getPlayers())if(!p.isDead()&&aboard(p))saved.add(p.getUniqueId());var poses=crewPos(saved);clean();if(buyer!=null){buyer.remove();buyer=null;}moveShip(LOBBY_SHIP,0,LOBBY_Y-Y);w.setTime(18000);arriveCrew(saved,poses);advanceQuota(win);day=1;bank=0;company=false;flight=false;cycles++;shuffleRoute();persist();say("§eНовая квота: "+quota+". Нажмите ПКМ по кнопке для посадки.");},40);
  }
  final Set<Block> lampBlocks=new HashSet<>();
  void clearLights(){for(Block b:lampBlocks)if(b.getType()==Material.LIGHT)b.setType(Material.AIR,false);lampBlocks.clear();}
  final Map<UUID,Integer> liveBattery=new HashMap<>();
  int lampCharge(Player p){return liveBattery.getOrDefault(p.getUniqueId(),battery(p.getInventory().getItem(LAMP_SLOT)));}
  int lampCapacity(int i){return i==PRO?3600:2400;}
  int battery(ItemStack s){int i=id(s);return !lamp(i)?0:s.getItemMeta().getPersistentDataContainer().getOrDefault(BATTERY,PersistentDataType.INTEGER,lampCapacity(i));}
  ItemStack battery(ItemStack s,int amount){ItemMeta m=s.getItemMeta();m.getPersistentDataContainer().set(BATTERY,PersistentDataType.INTEGER,Math.clamp(amount,0,lampCapacity(id(s))));s.setItemMeta(m);return s;}
  final Map<UUID,net.kyori.adventure.bossbar.BossBar> opticalLayers=new HashMap<>();
  static final long SCAN_COOLDOWN=5_000_000_000L;
  final Map<UUID,net.kyori.adventure.bossbar.BossBar> scannerCooldownBars=new HashMap<>();
  void scannerCooldownHud(Player p,int frame){
    UUID id=p.getUniqueId();var layer=scannerCooldownBars.get(id);
    if(frame<0){if(layer!=null){p.hideBossBar(layer);scannerCooldownBars.remove(id);}return;}
    var text=Component.text(String.valueOf((char)(0xE500+frame))).font(HUD);
    if(layer==null){layer=net.kyori.adventure.bossbar.BossBar.bossBar(text,0,net.kyori.adventure.bossbar.BossBar.Color.WHITE,net.kyori.adventure.bossbar.BossBar.Overlay.PROGRESS);scannerCooldownBars.put(id,layer);p.showBossBar(layer);}
    else if(!layer.name().equals(text))layer.name(text);
  }
  void optics(Player p){
    UUID id=p.getUniqueId();long age=System.nanoTime()-scans.getOrDefault(id,Long.MIN_VALUE/2);
    scannerCooldownHud(p,!flight&&!p.isDead()&&p.getGameMode()!=GameMode.SPECTATOR&&age>=0&&age<SCAN_COOLDOWN+350_000_000L?(int)Math.min(50,age*50/SCAN_COOLDOWN):-1);
    int frame=age>=0&&age<600_000_000L?1+(int)Math.min(3,age/150_000_000L):0;
    var layer=opticalLayers.get(id);
    if(frame==0){if(layer!=null){p.hideBossBar(layer);opticalLayers.remove(id);}return;}
    var text=Component.text(String.valueOf((char)(0xE310+frame))).font(HUD);
    if(layer==null){layer=net.kyori.adventure.bossbar.BossBar.bossBar(text,0,net.kyori.adventure.bossbar.BossBar.Color.WHITE,net.kyori.adventure.bossbar.BossBar.Overlay.PROGRESS);opticalLayers.put(id,layer);p.showBossBar(layer);}else if(!layer.name().equals(text))layer.name(text);
  }
  boolean lampOn(Player p){return !p.isDead()&&p.getGameMode()!=GameMode.SPECTATOR&&p.getInventory().getHeldItemSlot()==LAMP_SLOT&&lamp(id(p.getInventory().getItemInMainHand()))&&p.getScoreboardTags().contains("scrap_lamp")&&lampCharge(p)>0;}
  boolean nearFactoryLight(Player p){
    Location eye=p.getEyeLocation();
    for(Block fixture:factoryLights){
      Location center=fixture.getLocation().add(.5,.5,.5);
      double distance=eye.distanceSquared(center);
      if(distance>49||fixture.getType()!=Material.OCHRE_FROGLIGHT)continue;
      if(distance<.0001)return true;
      var ray=w.rayTraceBlocks(eye,center.subtract(eye).toVector(),Math.sqrt(distance),FluidCollisionMode.NEVER,true);
      if(ray==null||fixture.equals(ray.getHitBlock()))return true;
    }
    return false;
  }
  void lamps(){
    Map<Block,Integer> wanted=new HashMap<>();
    for(Player p:w.getPlayers()){
      if(active&&!flight&&!p.isDead()&&p.getGameMode()!=GameMode.SPECTATOR&&factorySide(p.getLocation())){
        Block ambient=p.getLocation().getBlock();
        if(ambient.getType().isAir()||lampBlocks.contains(ambient))wanted.merge(ambient,5,Math::max);
      }
      if(!flight&&lampOn(p)){
        Location eye=p.getEyeLocation();var dir=eye.getDirection();
        boolean pro=id(p.getInventory().getItem(LAMP_SLOT))==PRO;double range=5;
        var ray=w.rayTraceBlocks(eye,dir,range,FluidCollisionMode.NEVER,true);
        double end=ray==null?range:Math.max(0,ray.getHitPosition().distance(eye.toVector())-.18);
        for(double d:new double[]{Math.min(2.5,end),end}){
          if(d<.15)continue;
          Block block=eye.clone().add(dir.clone().multiply(d)).getBlock();
          if(block.getType().isAir()||lampBlocks.contains(block))wanted.merge(block,d<3.5?7:pro?10:9,Math::max);
        }
      }
      optics(p);
    }
    for(var entry:scanMarks.entrySet()){
      ScanKey key=entry.getKey();ScanMark mark=entry.getValue();Player viewer=Bukkit.getPlayer(key.viewer);FloorLoot loot=floorLoot.get(key.loot);
      if(viewer==null)continue;
      boolean visible=playing(viewer)&&!viewer.isDead()&&!flight&&loot!=null&&viewer.getLocation().distanceSquared(loot.hit.getLocation())<=196&&scanVisible(viewer,loot);
      if(visible){
        Location front=scanForeground(viewer,loot);
        if(mark.glow.getLocation().distanceSquared(front)>.0016)mark.glow.teleport(front);
        viewer.showEntity(this,mark.glow);viewer.showEntity(this,mark.label);
      }
      else{viewer.hideEntity(this,mark.glow);viewer.hideEntity(this,mark.label);}
    }
    for(Block b:lampBlocks)if(!wanted.containsKey(b)&&b.getType()==Material.LIGHT)b.setType(Material.AIR,false);
    for(var e:wanted.entrySet()){
      Block b=e.getKey();if(!b.getType().isAir()&&!lampBlocks.contains(b))continue;
      if(b.getBlockData() instanceof org.bukkit.block.data.Levelled old&&b.getType()==Material.LIGHT&&old.getLevel()==e.getValue())continue;
      var light=(org.bukkit.block.data.Levelled)Material.LIGHT.createBlockData();light.setLevel(e.getValue());b.setBlockData(light,false);
    }
    lampBlocks.clear();lampBlocks.addAll(wanted.keySet());
  }

  static final long CYCLE_NANOS=360_000_000_000L;
  static long moonClock(long elapsed){return Math.floorMod(elapsed,CYCLE_NANOS)*24000L/CYCLE_NANOS;}
  String clockText(long ticks,long elapsed){
    long minutes=(360+ticks*1440/24000)%1440;
    boolean daytime=ticks<12000;
    long phaseNanos=Math.floorMod(elapsed,CYCLE_NANOS/2),left=(CYCLE_NANOS/2-phaseNanos+999_999_999L)/1_000_000_000L;
    return String.format(java.util.Locale.ROOT,"%02d:%02d §7· %s через %d:%02d",minutes/60,minutes%60,daytime?"ночь":"рассвет",left/60,left%60);
  }
  void drainLamps(){
    if(flight)return;
    for(Player p:w.getPlayers())if(lampOn(p)){
      ItemStack item=p.getInventory().getItem(LAMP_SLOT);int left=lampCharge(p)-(id(item)==PRO?9:30);
      liveBattery.put(p.getUniqueId(),Math.max(0,left));
      if(left<=0){p.removeScoreboardTag("scrap_lamp");p.playSound(p.getLocation(),Sound.BLOCK_LEVER_CLICK,.5f,.6f);}
    }
  }
  void locationHud(Player p){
    var bar=locationBars.computeIfAbsent(p.getUniqueId(),id->{var created=Bukkit.createBossBar("",BarColor.BLUE,BarStyle.SOLID);created.addPlayer(p);return created;});
    boolean ship=aboard(p)||onCargoDeck(p.getLocation());
    String title;
    if(ship){
      title="§6ШАТЛ"+(flight&&!destination.isEmpty()?" §7· Курс: §f"+destination:!active&&!company?" §7· Далее: §f"+nextDestination():"");
      if(active&&!flight){long elapsed=Math.max(0,System.nanoTime()-roundStarted);title+=" §7| §f"+clockText(moonClock(elapsed),elapsed);}
      title+=" §7| §6Квота: §f"+bank+"/"+quota;
    }else if(active&&factorySide(p.getLocation()))title="§fЗАВОД · "+SITES[district];
    else if(active){
      long t=moonClock(Math.max(0,System.nanoTime()-roundStarted));long minutes=(360+t*1440/24000)%1440;
      title="§f"+SITES[district]+" §7· "+String.format(Locale.ROOT,"%02d:%02d",minutes/60,minutes%60);
    }else if(company)title="§f71-GORDION · Пункт скупки";
    else title="§6ШАТЛ";
    if(!title.equals(bar.getTitle()))bar.setTitle(title);
  }
  void tick(){
    updateSettingsHologram();
    for(Player player:w.getPlayers())locationHud(player);
    for(Player p:w.getPlayers())if(!p.isDead()){
      ensureMask(p);
      if(p.getLocation().getY()<w.getMinHeight()+6){p.teleport(cabin());if(active&&!dead.contains(p.getUniqueId()))p.setHealth(0);}

    }
  }
  void ambience(){
    ambienceBeat++;
    for(Player p:w.getPlayers()){
      if(p.isDead()||p.getGameMode()==GameMode.SPECTATOR)continue;
      Location at=p.getLocation();double x=at.getX();
      if(flight&&aboard(p)){
        if(ambienceBeat%2==0){p.playSound(at,Sound.BLOCK_PISTON_EXTEND,.23f,.55f);for(int side:new int[]{-1,1})for(int z:new int[]{-2,2}){
          Pt q=shipPoint(new Pt(side*2,z));Location plume=new Location(w,q.x+.5,Y+shipLift-3,q.z+.5);p.spawnParticle(Particle.FLAME,plume,9,.28,.35,.28,.025);p.spawnParticle(Particle.SMOKE,plume,8,.42,.45,.42,.018);
        }}continue;
      }
      if(!active||aboard(p))continue;
      if(x>X-10&&x<X+175){
        if(ambienceBeat%4==0)p.spawnParticle(Particle.ASH,at.clone().add(0,1.4,0),8,3.1,1.1,3.1,.009);
        if(ambienceBeat%14==0)p.playSound(at,Sound.AMBIENT_CAVE,.32f,.72f);
        if(ambienceBeat%5==0){Location spark=at.clone().add(.5,2.4,-.6);p.spawnParticle(Particle.ELECTRIC_SPARK,spark,3,.7,.25,.7,.02);}continue;
      }
      if(x<SECTOR*(district+1)+SITE_X0||x>SECTOR*(district+1)+SITE_X1||at.getZ()<SITE_Z0||at.getZ()>SITE_Z1)continue;
      Location air=at.clone().add(0,2,0);
      switch(district){
        case 0 -> {p.spawnParticle(Particle.WHITE_ASH,air,14,5,1.5,5,.018);if(ambienceBeat%12==0)p.playSound(at,Sound.ENTITY_BAT_AMBIENT,.25f,.62f);}
        case 1 -> {p.spawnParticle(Particle.DUST,air,12,5,1.2,5,0,new Particle.DustOptions(Color.fromRGB(135,85,61),.68f));if(ambienceBeat%11==0)p.playSound(at,Sound.BLOCK_FIRE_AMBIENT,.24f,.58f);}
        default -> {p.spawnParticle(Particle.SNOWFLAKE,air,30,6,2.7,6,.032);if(ambienceBeat%10==0)p.playSound(at,Sound.BLOCK_POWDER_SNOW_STEP,.22f,.66f);}
      }
    }
  }
  void stamina(){breathTick+=4;for(Player p:w.getPlayers()){
    if(p.isDead()||p.getGameMode()==GameMode.SPECTATOR||p.getGameMode()==GameMode.CREATIVE){chargeHud(p,0);continue;}ensureMask(p);Breath b=breath.computeIfAbsent(p.getUniqueId(),u->new Breath());
    boolean sprint=(p.isSprinting()||(p.getCurrentInput().isSprint()&&p.getCurrentInput().isForward()))&&breathTick-b.motion<=8;
    if(sprint&&!b.locked){b.level=Math.max(0,b.level-(7+2*used(p))*.2);b.pause=6;
      if(b.level<=0){b.locked=true;fatigue(p,true);p.setSprinting(false);p.playSound(p.getLocation(),Sound.ENTITY_PLAYER_BREATH,.6f,.8f);}
    }else{if(b.pause>0)b.pause--;else b.level=Math.min(100,b.level+3.0);if(b.level>0&&p.getFoodLevel()<=1)fatigue(p,false);if(b.locked&&b.level>=30){b.locked=false;fatigue(p,false);}}
    if(b.locked&&p.isSprinting())p.setSprinting(false);
    if(flight){chargeHud(p,0);continue;}
    var max=p.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH);
    int hp=(int)Math.ceil(20*Math.clamp(p.getHealth()/(max==null?20:max.getValue()),0,1));
    int sp=(int)Math.ceil(20*b.level/100);
    var held=p.getInventory().getItemInMainHand();int heldId=id(held);
    chargeHud(p,lamp(heldId)?0xE460+(int)Math.ceil(10.0*lampCharge(p)/lampCapacity(heldId)):heldId==5?0xE440+ammo(held):0);
    StringBuilder hud=new StringBuilder();
    int lampId=id(p.getInventory().getItem(LAMP_SLOT));
    hud.append((char)(0xE400+(lampId==PRO?4:lampId==4?2:0)+(p.getInventory().getHeldItemSlot()==LAMP_SLOT?1:0))).append((char)0xE3FF)
      .append((char)(0xE000+hp)).append((char)0xE17F).append((char)(0xE040+sp)).append((char)0xE17F);
    for(int slot=SLOT_FIRST;slot<SLOT_END;slot++)hud.append((char)(0xE200+(slotBlocked(p,slot)?128:slot==p.getInventory().getHeldItemSlot()?64:0)));
    var text=Component.text().append(Component.text("5",net.kyori.adventure.text.format.TextColor.color(0xEA9517)).font(Key.key("minecraft","default")))
      .append(Component.text("\uE43E"+hud).font(HUD)).build();
    var gauge=gaugesByPlayer.get(p.getUniqueId());if(gauge!=null&&!gauge.name().equals(text))gauge.name(text);
  }}
  @EventHandler public void hunger(FoodLevelChangeEvent e){if(e.getEntity() instanceof Player p&&playing(p)&&e.getFoodLevel()<1)e.setFoodLevel(1);}
  void fatigue(Player p,boolean apply){
    var speed=p.getAttribute(org.bukkit.attribute.Attribute.MOVEMENT_SPEED);if(speed==null)return;
    var modifier=speed.getModifier(Key.key("scrapshift","exhaustion"));
    if(modifier!=null)speed.removeModifier(modifier);
    if(apply){if(p.getFoodLevel()!=1){p.setSaturation(0);p.setFoodLevel(1);}}
    else if(p.getFoodLevel()<=1){p.setFoodLevel(20);p.setSaturation(0);}
  }
  @EventHandler(ignoreCancelled=true) public void movement(PlayerMoveEvent e){if(!playing(e.getPlayer())||e.getPlayer().getGameMode()==GameMode.CREATIVE||e.getTo()==null||e.getTo().getWorld()!=w)return;int base=SECTOR*(district+1);int x=e.getFrom().getBlockX()-base,z=e.getFrom().getBlockZ();
    if(flight&&aboard(e.getPlayer())){Location t=berthPose(e.getTo());if(!insideCabin(t.getBlockX(),t.getBlockZ())||t.getY()<2||t.getY()>=5){e.setCancelled(true);return;}}
    double dx=e.getTo().getX()-e.getFrom().getX(),dz=e.getTo().getZ()-e.getFrom().getZ();if(dx*dx+dz*dz>0.0004)breath.computeIfAbsent(e.getPlayer().getUniqueId(),u->new Breath()).motion=breathTick;}
  static final double JUMP_COST=12;
  boolean payJump(Breath breath){
    if(breath.locked||breath.level<JUMP_COST)return false;
    breath.level-=JUMP_COST;breath.pause=Math.max(breath.pause,7);
    if(breath.level<=0)breath.locked=true;
    return true;
  }
  boolean againstStep(Player p,Location from){
    var box=p.getBoundingBox().clone().shift(from.getX()-p.getLocation().getX(),from.getY()-p.getLocation().getY(),from.getZ()-p.getLocation().getZ());
    var probe=new org.bukkit.util.BoundingBox(box.getMinX()-.20,from.getY()+.04,box.getMinZ()-.20,box.getMaxX()+.20,from.getY()+1.25,box.getMaxZ()+.20);
    for(int x=Location.locToBlock(probe.getMinX());x<=Location.locToBlock(probe.getMaxX());x++)
      for(int y=Location.locToBlock(probe.getMinY());y<=Location.locToBlock(probe.getMaxY());y++)
        for(int z=Location.locToBlock(probe.getMinZ());z<=Location.locToBlock(probe.getMaxZ());z++){
          Block b=w.getBlockAt(x,y,z);if(!b.isPassable()&&b.getBoundingBox().overlaps(probe))return true;
        }
    return false;
  }
  @EventHandler(ignoreCancelled=true) public void jump(com.destroystokyo.paper.event.player.PlayerJumpEvent e){
    Player p=e.getPlayer();if(!playing(p)||p.getGameMode()==GameMode.SPECTATOR||p.getGameMode()==GameMode.CREATIVE)return;
    Breath b=breath.computeIfAbsent(p.getUniqueId(),u->new Breath());
    if(!payJump(b)){if(!againstStep(p,e.getFrom()))e.setCancelled(true);}else if(b.locked){p.setSprinting(false);fatigue(p,true);}
  }
  boolean marchExterior(Location l){int x=l.getBlockX()-moons[0].shift,z=l.getBlockZ();return l.getWorld()==w&&x>=-1024&&x<1024&&z>=-1024&&z<1024;}
  void removeMarchZombie(Entity e){if(e instanceof Zombie&&marchExterior(e.getLocation()))e.remove();}
  @EventHandler public void loadedEntities(org.bukkit.event.world.EntitiesLoadEvent e){if(e.getWorld()==w)for(Entity entity:e.getEntities())removeMarchZombie(entity);}
  @EventHandler(ignoreCancelled=true) public void marchSpawn(CreatureSpawnEvent e){if(e.getEntity() instanceof Zombie&&marchExterior(e.getLocation()))e.setCancelled(true);}
  @EventHandler public void sprint(PlayerToggleSprintEvent e){if(!playing(e.getPlayer())||e.getPlayer().getGameMode()==GameMode.CREATIVE||!e.isSprinting())return;Breath b=breath.get(e.getPlayer().getUniqueId());if(b!=null&&b.locked){e.setCancelled(true);e.getPlayer().setSprinting(false);fatigue(e.getPlayer(),b.level<=0);}}
  boolean playing(Player p){return p.getWorld()==w;}
  void restartCampaign(){
    campaignEpoch++;wipePending=false;weather(0);cursedMasks.clear();
    active=false;flight=false;company=false;pendingBuyer=false;destination="";roundStarted=0;day=1;bank=0;quota=baseQuota;cycles=0;difficultyTier=0;firstLaunchDone=false;
    gate(shipOffset,false);clearLights();clean();release();erasePad();eraseFactory();
    for(UUID id:new ArrayList<>(shipCargo.keySet()))removeLoot(id);
    shipCargo.clear();cargoOverflow.clear();dead.clear();factoryReturns.clear();portalCooldown.clear();
    if(buyer!=null){buyer.remove();buyer=null;}
    shuffleRoute();district=moonOrder.getFirst();layout=district;
    moveShip(LOBBY_SHIP,0,LOBBY_Y-Y);gate(shipOffset,false);w.setTime(18000);
    for(Player p:w.getPlayers())if(!p.isDead()){p.teleport(cabin());reset(p);showVisor(p);}
    persist();tick();say("§6Кампания перезапущена. Нажмите кнопку на корабле для вылета.");
  }
  boolean adminItems(CommandSender s,String[] args){
    if(!s.hasPermission("scrapshift.give")){s.sendMessage("§cНет права scrapshift.give.");return true;}
    List<String> ids=new ArrayList<>(Arrays.asList(IDS));for(var extra:EXTRA)ids.add(extra.id());
    if(args[0].equalsIgnoreCase("items")){s.sendMessage("§eПредметы: §f"+String.join(", ",ids));return true;}
    if(args.length<2||args.length>3){s.sendMessage("§e/scrap give <id> [игрок] · /scrap items");return true;}
    int i=ids.indexOf(args[1].toLowerCase(Locale.ROOT));Player p=args.length==3?Bukkit.getPlayerExact(args[2]):s instanceof Player self?self:null;
    if(i<0){s.sendMessage("§cНеизвестный предмет. Список: /scrap items");return true;}
    if(p==null||!playing(p)||p.isDead()||p.getGameMode()==GameMode.SPECTATOR){s.sendMessage("§cНужен живой игрок на карте Scrap Shift.");return true;}
    if(!store(p,item(i))){s.sendMessage("§cНет подходящего свободного слота.");return true;}
    s.sendMessage("§aВыдано: "+ids.get(i)+" → "+p.getName());return true;
  }
  boolean adminCampaign(CommandSender s,String[] args){
    if(!s.hasPermission("scrapshift.campaign")){s.sendMessage("§cНет права scrapshift.campaign.");return true;}
    String cmd=args[0].toLowerCase(Locale.ROOT);
    if(cmd.equals("skip")){if(args.length!=1||flight){s.sendMessage("§c/scrap skip доступна вне перелёта.");return true;}skipQuota();return true;}
    String range=switch(cmd){case "mobhealth"->"1–50 попаданий";case "quota"->"1–1000000000";case "multiquota"->"1–100";default->"1–30 дней";};
    if(args.length==1){String value=switch(cmd){case "mobhealth"->mobHits+" (сейчас с учётом сложности: "+(mobHits+Math.min(8,difficultyTier))+")";case "quota"->baseQuota+" (текущая: "+quota+")";case "multiquota"->String.valueOf(quotaMultiplier);default->String.valueOf(dayLimit);};s.sendMessage("§e"+cmd+": "+value+" · /scrap "+cmd+" <"+range+">");return true;}
    try{
      if(args.length!=2)throw new NumberFormatException();
      if(cmd.equals("multiquota")){double v=Double.parseDouble(args[1].replace(',','.'));if(!Double.isFinite(v)||v<1||v>100)throw new NumberFormatException();quotaMultiplier=v;getConfig().set("quota-multiplier",v);}
      else{int v=Integer.parseInt(args[1]);int max=cmd.equals("mobhealth")?50:cmd.equals("quota")?1_000_000_000:30;if(v<1||v>max)throw new NumberFormatException();
        switch(cmd){case "mobhealth"->{mobHits=v;getConfig().set("mob-health",v);for(Rig r:rigs)if(r.mob.isValid()&&!r.mob.isDead())applyMobHealth(r.mob,false);}
          case "quota"->{baseQuota=v;if(difficultyTier==0)quota=v;getConfig().set("quota",v);}
          default->{dayLimit=v;day=Math.min(day,v);getConfig().set("day-limit",v);}}
      }
      persist();tick();s.sendMessage("§aНастройка сохранена: "+cmd+" = "+args[1]);
    }catch(NumberFormatException ex){s.sendMessage("§c/scrap "+cmd+" <"+range+">");}
    return true;
  }
  Interaction settingsHit;
  final List<TextDisplay> settingsLabels=new ArrayList<>();
  static final double SETTINGS_X=57348.04,SETTINGS_Z=2.0,SETTINGS_Y=16.03;
  static final String[] SETTINGS_KEYS={"quota","multiquota","daylimit","mobhealth"};
  static final String[] SETTINGS_NAMES={"Квота","Множ.","Дни","Хиты"};
  boolean settingsShuttle(){return !active&&!flight&&!company&&shipOffset==LOBBY_SHIP.x;}
  boolean firstShuttle(){return settingsShuttle()&&!firstLaunchDone&&!pendingBuyer&&cycles==0&&day==1;}
  boolean canConfigure(Player p){return playing(p)&&p.hasPermission("scrapshift.campaign")&&firstShuttle()&&p.getGameMode()!=GameMode.SPECTATOR;}
  void removeSettingsHologram(){
    if(settingsHit!=null){settingsHit.remove();settingsHit=null;}
    for(TextDisplay label:settingsLabels)label.remove();settingsLabels.clear();
  }
  TextDisplay wallLabel(double z,double y){
    return w.spawn(new Location(w,SETTINGS_X,y,z,-90,0),TextDisplay.class,t->{
      t.setPersistent(false);t.setBillboard(Display.Billboard.FIXED);t.setViewRange(.5f);t.setShadowed(false);
      t.setTransformation(new Transformation(new Vector3f(),new Quaternionf(),new Vector3f(.48f),new Quaternionf()));
      t.setDefaultBackground(false);t.setBackgroundColor(Color.fromARGB(180,21,20,16));t.setBrightness(new Display.Brightness(15,15));
    });
  }
  double settingValue(int row){return switch(row){case 0->baseQuota;case 1->quotaMultiplier;case 2->dayLimit;default->mobHits;};}
  void updateSettingsHologram(){
    if(!firstShuttle()){removeSettingsHologram();return;}
    Location base=new Location(w,SETTINGS_X,SETTINGS_Y,SETTINGS_Z);
    if(!base.isChunkLoaded())return;
    if(settingsHit==null||!settingsHit.isValid()||settingsLabels.size()!=17||settingsLabels.stream().anyMatch(t->!t.isValid())){
      removeSettingsHologram();
      settingsHit=w.spawn(base,Interaction.class,h->{h.setPersistent(false);h.setInteractionWidth(2.2f);h.setInteractionHeight(1.44f);h.setResponsive(true);});
      TextDisplay title=wallLabel(SETTINGS_Z,SETTINGS_Y+1.53);title.text(Component.text("НАСТРОЙКИ ШАТЛА · ПКМ",net.kyori.adventure.text.format.NamedTextColor.GOLD));settingsLabels.add(title);
      for(int row=0;row<4;row++){
        double y=SETTINGS_Y+1.11-row*.35;
        settingsLabels.add(wallLabel(SETTINGS_Z+.34,y));settingsLabels.add(wallLabel(SETTINGS_Z-.19,y));
        settingsLabels.add(wallLabel(SETTINGS_Z-.73,y));settingsLabels.add(wallLabel(SETTINGS_Z+.83,y));
      }
    }
    var title=Component.text("НАСТРОЙКИ ШАТЛА · ПКМ",net.kyori.adventure.text.format.NamedTextColor.GOLD);
    if(!settingsLabels.getFirst().text().equals(title))settingsLabels.getFirst().text(title);
    double[] limits={1000,5,12,12};
    for(int row=0;row<4;row++){
      double value=settingValue(row);int fill=Math.clamp((int)Math.round(10*value/limits[row]),1,10);
      String number=row==1?String.format(Locale.ROOT,"%.2f",value):Integer.toString((int)value);
      var gold=net.kyori.adventure.text.format.NamedTextColor.GOLD;
      Component[] cells={Component.text("−",gold),Component.text("■".repeat(fill),gold)
        .append(Component.text("□".repeat(10-fill),net.kyori.adventure.text.format.NamedTextColor.GRAY)),
        Component.text("+",gold),Component.text(SETTINGS_NAMES[row]+": "+number)};
      for(int col=0;col<cells.length;col++){TextDisplay label=settingsLabels.get(1+row*4+col);if(!label.text().equals(cells[col]))label.text(cells[col]);}
    }
  }
  void adjustWallSetting(Player p){
    if(!canConfigure(p)){p.sendMessage("§cМенять настройки может только администратор до первого вылета.");return;}
    Location eye=p.getEyeLocation();var aim=eye.getDirection();if(Math.abs(aim.getX())<.001)return;
    double d=(SETTINGS_X-eye.getX())/aim.getX();if(d<0||d>5)return;
    double x=eye.getZ()+aim.getZ()*d-SETTINGS_Z,y=eye.getY()+aim.getY()*d;
    int row=(int)Math.floor((SETTINGS_Y+1.40-y)/.35);if(row<0||row>3||Math.abs(x)>1.1)return;
    // Visual right along this wall is world -Z.
    double u=-x;double[] increment={25,.25,1,1},limit={1_000_000_000d,100,30,50}, sliderMax={1000,5,12,12};
    double current=settingValue(row),v;
    if(u<-.55||u>.97)return;
    if(u<-.22||u>.60)v=current+(u>.60?1:-1)*increment[row];
    else v=Math.round(((u+.22)/.82)*sliderMax[row]/increment[row])*increment[row];
    v=Math.clamp(v,1,limit[row]);
    adminCampaign(p,new String[]{SETTINGS_KEYS[row],row==1?Double.toString(v):Integer.toString((int)v)});
    p.playSound(p.getLocation(),Sound.UI_BUTTON_CLICK,.55f,1.2f);
  }
  @Override public boolean onCommand(CommandSender s,Command c,String label,String[] args){if(args.length==1&&args[0].equalsIgnoreCase("settings")){s.sendMessage("§eНастройки находятся на стене первого шатла: ПКМ по строке панели.");return true;}if(args.length>0&&Set.of("mobhealth","quota","multiquota","skip","daylimit").contains(args[0].toLowerCase(Locale.ROOT)))return adminCampaign(s,args);if(args.length>0&&(args[0].equalsIgnoreCase("give")||args[0].equalsIgnoreCase("items")))return adminItems(s,args);if(args.length==1&&args[0].equalsIgnoreCase("restart")){if(!s.hasPermission("scrapshift.restart")){s.sendMessage("§cНет права scrapshift.restart.");return true;}restartCampaign();if(!(s instanceof Player p)||!playing(p))s.sendMessage("§aКампания перезапущена.");return true;}if(args.length==1&&args[0].equalsIgnoreCase("pack")){packs.status(s);return true;}if(args.length==2&&args[0].equalsIgnoreCase("pack")&&args[1].equalsIgnoreCase("retry")&&s instanceof Player p){packs.retry(p);return true;}if(s instanceof Player p){help(p);p.sendMessage(active?"§7Рейс начат. На складе: "+bank+"/"+quota:"§7Корабль готов к запуску.");}return true;}
  @EventHandler public void packStatus(PlayerResourcePackStatusEvent e){packs.report(e);if(e.getStatus()==PlayerResourcePackStatusEvent.Status.SUCCESSFULLY_LOADED){Player p=e.getPlayer();Bukkit.getScheduler().runTask(this,()->{if(p.isOnline()&&!p.isDead()&&playing(p))refreshMask(p);});}}
  @EventHandler(priority=EventPriority.MONITOR) public void login(PlayerLoginEvent e){if(e.getResult()==PlayerLoginEvent.Result.ALLOWED)packs.remember(e.getPlayer().getUniqueId(),e.getHostname());}
  @EventHandler public void xp(PlayerExpChangeEvent e){if(playing(e.getPlayer()))e.setAmount(0);}
  @EventHandler(priority=EventPriority.HIGHEST) public void join(PlayerJoinEvent e){
    Player p=e.getPlayer();equipMask(p);p.updateInventory();
    Bukkit.getScheduler().runTask(this,()->{if(p.isOnline()){enter(p);p.updateInventory();}});
  }
  @EventHandler public void quit(PlayerQuitEvent e){
    Player p=e.getPlayer();UUID id=p.getUniqueId();itemUseTimes.remove(id);cursedMasks.remove(id);
    var locationBar=locationBars.remove(id);if(locationBar!=null)locationBar.removeAll();removeGauges(p);fatigue(p,false);packs.forget(id);scans.remove(id);clearScan(p);breath.remove(id);portalCooldown.remove(id);factoryReturns.remove(id);restoreBoard(p);
    if(!playing(p)){liveBattery.remove(id);return;}
    if(w.getPlayers().stream().noneMatch(other->!other.getUniqueId().equals(id)&&survivingCrew(other))){
      liveBattery.remove(id);p.removeScoreboardTag("scrap_lamp");
      if(p.getGameMode()!=GameMode.CREATIVE)p.getInventory().clear();
      long epoch=campaignEpoch;
      Bukkit.getScheduler().runTask(this,()->{if(epoch==campaignEpoch)restartCampaign();});
      return;
    }
    if(p.getGameMode()==GameMode.CREATIVE){liveBattery.remove(id);return;}
    boolean running=active||flight||company||pendingBuyer||day>1||cycles>0;
    if(running&&p.getGameMode()!=GameMode.SPECTATOR&&dead.add(id)){
      boolean on=onCargoDeck(p.getLocation());
      for(int slot=SLOT_FIRST;slot<=LAMP_SLOT;slot++){
        ItemStack stack=p.getInventory().getItem(slot);int k=id(stack);if(k<0||k==4&&slot!=LAMP_SLOT)continue;
        if(lamp(k))stack=battery(nativeModel(stack),lampCharge(p));
        if(!dropLoot(p.getLocation(),stack,on)&&on)cargoOverflow.add(stack.clone());
      }
      p.getInventory().clear();p.removeScoreboardTag("scrap_lamp");if(on)persist();
    }
    liveBattery.remove(id);
    if(!running)return;
    if(active&&!flight){checkCrewWipe();return;}
    long epoch=campaignEpoch;
    Bukkit.getScheduler().runTask(this,()->{
      if(epoch==campaignEpoch&&w.getPlayers().stream().noneMatch(this::survivingCrew))restartCampaign();
    });
  }
  boolean shuttleControl(Block b){
    if(b==null)return false;
    return b.getType()==Material.STONE_BUTTON&&onShipBlock(b,14,3,6)
        ||b.getType()==Material.LEVER&&onShipBlock(b,14,3,5)
        ||b.getType()==Material.IRON_TRAPDOOR&&onShipBlock(b,9,3,7);
  }
  @EventHandler public void use(PlayerInteractEvent e){Player p=e.getPlayer();if(!playing(p))return;
    if(p.getGameMode()==GameMode.CREATIVE&&(e.getAction()!=Action.RIGHT_CLICK_BLOCK||!shuttleControl(e.getClickedBlock())))return;
    e.setCancelled(true);if(flight)return;
    if(e.getHand()!=EquipmentSlot.HAND||p.getGameMode()==GameMode.SPECTATOR||(e.getAction()!=Action.RIGHT_CLICK_AIR&&e.getAction()!=Action.RIGHT_CLICK_BLOCK))return;
    Block b=e.getClickedBlock();
    if(b!=null&&toggleShipDoor(b,p))return;
    if(b!=null&&Tag.WOODEN_DOORS.isTagged(b.getType())){
      e.setUseInteractedBlock(Event.Result.ALLOW);e.setUseItemInHand(Event.Result.DENY);return;
    }
    if(b!=null&&active&&moons[district].entrance(b)){crossDoor(p,true);return;}
    if(b!=null&&company&&b.getType()==Material.BELL&&b.getX()==COMPANY_BELL.x&&b.getY()==51&&b.getZ()==COMPANY_BELL.z){int credits=sell(p);bank+=credits;p.playSound(b.getLocation(),Sound.BLOCK_BELL_USE,.65f,1);p.sendMessage(credits==0?"§7У вас нет лома для продажи.":"§aПродано на "+credits+" · квота "+bank+"/"+quota);persist();return;}
    if(b!=null&&b.getType()==Material.LEVER&&onShipBlock(b,14,3,5)){recall(p);return;}
    if(b!=null&&b.getType()==Material.IRON_TRAPDOOR&&onShipBlock(b,9,3,7)){
      ItemStack held=p.getInventory().getItemInMainHand();
      if(id(held)==5){p.playSound(p.getLocation(),Sound.ITEM_CROSSBOW_LOADING_END,.6f,.85f);charge(held,BLASTER_CAPACITY);p.getInventory().setItemInMainHand(held);p.sendActionBar(Component.text("Оружие заряжено"));}
      else{ItemStack torch=p.getInventory().getItem(LAMP_SLOT);if(lamp(id(torch)))liveBattery.put(p.getUniqueId(),lampCapacity(id(torch)));p.sendActionBar(Component.text("Фонарь заряжен"));}
      p.playSound(p.getLocation(),Sound.BLOCK_BEACON_POWER_SELECT,1,1.3f);return;
    }
    if(b!=null&&(b.getY()==Y+1||b.getY()==Y+2||b.getY()==Y+shipLift+3)){Pt pos=new Pt(b.getX(),b.getZ());Material type=b.getType();
      if(type==Material.STONE_BUTTON&&b.getY()==Y+shipLift+3&&pos.equals(shipPoint(new Pt(14,6)))){if(active&&aboard(p))end();else if(company){if(bank<quota&&!p.isSneaking())p.sendMessage("§eКвота не выполнена. Чтобы улететь всё равно, нажмите пульт с Shift.");else settle();}else launch();return;}
      if(type==Material.IRON_DOOR&&active&&(b.getY()==Y+1||b.getY()==Y+2)&&!doors.contains(pos)&&portalBlock(pos,EXIT)){crossDoor(p,false);return;}
    }
    if(b!=null&&b.getType()==Material.IRON_DOOR){Pt q=new Pt(b.getX(),b.getZ());if(doors.contains(q)){var dd=(org.bukkit.block.data.type.Door)b.getBlockData();boolean open=!dd.isOpen();for(int y=1;y<=2;y++){var d=(org.bukkit.block.data.type.Door)block(q,y).getBlockData();d.setOpen(open);block(q,y).setBlockData(d,false);}p.playSound(b.getLocation(),open?Sound.BLOCK_IRON_DOOR_OPEN:Sound.BLOCK_IRON_DOOR_CLOSE,.7f,1);return;}}
    if(id(p.getInventory().getItemInMainHand())==5){fire(p);return;}
    int held=id(p.getInventory().getItemInMainHand());
    if(useSpecialItem(p,held))return;
    if(lamp(held)){if(lampCharge(p)<=0){p.sendActionBar(Component.text("Фонарь разряжен"));return;}if(!p.addScoreboardTag("scrap_lamp"))p.removeScoreboardTag("scrap_lamp");lamps();p.playSound(p.getLocation(),Sound.BLOCK_LEVER_CLICK,0.6f,1);}
  }
  String itemCode(int i){return i<0?"":i<6?IDS[i]:EXTRA[i-6].id;}
  boolean meleeTool(int i){return i==KNIFE||i==SHOVEL||i==STOP_SIGN;}
  boolean itemUseReady(Player p,long millis){
    long now=System.nanoTime();UUID id=p.getUniqueId();if(now<itemUseTimes.getOrDefault(id,Long.MIN_VALUE))return false;
    itemUseTimes.put(id,now+millis*1_000_000L);return true;
  }
  void itemNoise(Player p,Sound sound,float volume,float pitch,double radius){
    Location at=p.getLocation();w.playSound(at,sound,SoundCategory.PLAYERS,volume,pitch);
    if(!active||onCargoDeck(at))return;
    for(Rig rig:rigs)if(rig.mob.isValid()&&!rig.mob.isDead()&&rig.mob.getLocation().distanceSquared(at)<=radius*radius&&factorySide(at)==factorySide(rig.mob.getLocation())&&rig.mob.getTarget()==null)rig.mob.setTarget(p);
  }
  void restoreShuttleLights(){
    for(var e:mutedFixtures.entrySet())if(e.getKey().getType()==Material.GRAY_CONCRETE||e.getKey().getType()==e.getValue().getMaterial())e.getKey().setBlockData(e.getValue(),false);
    mutedFixtures.clear();
  }
  void toggleShuttleLights(Player p){
    if(!mutedFixtures.isEmpty()){restoreShuttleLights();p.sendActionBar(Component.text("Свет в шатле включён"));return;}
    for(int x=1;x<17;x++)for(int z=1;z<11;z++)for(int y=2;y<=7;y++){
      Block b=shipBlock(x,y,z);
      if(switch(b.getType()){case SEA_LANTERN, OCHRE_FROGLIGHT, VERDANT_FROGLIGHT, PEARLESCENT_FROGLIGHT, GLOWSTONE, SHROOMLIGHT, LANTERN, SOUL_LANTERN, REDSTONE_LAMP, LIGHT->true;default->false;}){
        if(lampBlocks.contains(b))continue;
        mutedFixtures.put(b,b.getBlockData());
        if(b.getBlockData() instanceof org.bukkit.block.data.Levelled dim&&b.getType()==Material.LIGHT){dim.setLevel(0);b.setBlockData(dim,false);}
        else if(b.getBlockData() instanceof org.bukkit.block.data.Lightable fixture){fixture.setLit(false);b.setBlockData(fixture,false);}
        else b.setType(Material.GRAY_CONCRETE,false);
      }
    }
    p.sendActionBar(Component.text(mutedFixtures.isEmpty()?"Нет светильников для переключения":"Свет в шатле выключен"));
  }
  boolean useSpecialItem(Player p,int i){
    if(i==INHALER){
      if(!itemUseReady(p,450))return true;
      Breath bt=breath.computeIfAbsent(p.getUniqueId(),u->new Breath());
      bt.level=100;bt.locked=false;bt.pause=0;fatigue(p,false);
      var item=p.getInventory().getItemInMainHand();var meta=item.getItemMeta();
      int left=meta.getPersistentDataContainer().getOrDefault(dosesKey,PersistentDataType.INTEGER,INHALER_DOSES)-1;
      if(left<=0)p.getInventory().setItemInMainHand(null);
      else{meta.getPersistentDataContainer().set(dosesKey,PersistentDataType.INTEGER,left);item.setItemMeta(meta);p.getInventory().setItemInMainHand(item);}
      p.addPotionEffect(new org.bukkit.potion.PotionEffect(org.bukkit.potion.PotionEffectType.SPEED,70,0,false,false,false));
      p.playSound(p.getLocation(),Sound.ITEM_BOTTLE_EMPTY,.7f,1.4f);p.sendActionBar(Component.text("Ингалятор: "+Math.max(0,left)+" доз."));return true;
    }
    if(i==STUN){
      if(!active||onCargoDeck(p.getLocation())||!itemUseReady(p,700))return true;
      Location eye=p.getEyeLocation();var dir=eye.getDirection();var hit=w.rayTraceBlocks(eye,dir,8,FluidCollisionMode.NEVER,true);
      Location burst=hit==null?eye.clone().add(dir.multiply(8)):hit.getHitPosition().toLocation(w).subtract(dir.multiply(.18));
      p.getInventory().setItemInMainHand(null);p.playSound(p.getLocation(),Sound.ENTITY_SNOWBALL_THROW,.7f,.8f);
      long epoch=campaignEpoch,raid=roundStarted;
      Bukkit.getScheduler().runTaskLater(this,()->{
        if(epoch!=campaignEpoch||!active||raid!=roundStarted)return;
        w.spawnParticle(Particle.FLASH,burst,1);w.playSound(burst,Sound.ENTITY_FIREWORK_ROCKET_BLAST,1f,1.35f);
        for(Entity near:w.getNearbyEntities(burst,5,5,5))if(near instanceof LivingEntity living&&near.getLocation().distanceSquared(burst)<=25){
          Location eyes=living.getEyeLocation();var delta=eyes.toVector().subtract(burst.toVector());double distance=delta.length();
          if(distance>.02&&w.rayTraceBlocks(burst,delta,distance,FluidCollisionMode.NEVER,true)!=null)continue;
          if(living instanceof Zombie)living.addPotionEffect(new org.bukkit.potion.PotionEffect(org.bukkit.potion.PotionEffectType.SLOWNESS,100,9,false,true,true));
          else if(living instanceof Player other&&other.getGameMode()==GameMode.ADVENTURE)other.addPotionEffect(new org.bukkit.potion.PotionEffect(org.bukkit.potion.PotionEffectType.BLINDNESS,35,0,false,false,true));
        }
      },28L);return true;
    }
    return switch(itemCode(i)){
      case "brass_bell","clown_horn","cash_register","rubber_ducky","robot","hairdryer","small_fox","bottles"->{
        if(itemUseReady(p,550)){Sound sound=switch(itemCode(i)){
          case "brass_bell"->Sound.BLOCK_BELL_USE;case "clown_horn"->Sound.ENTITY_GOAT_SCREAMING_AMBIENT;
          case "cash_register"->Sound.ENTITY_EXPERIENCE_ORB_PICKUP;case "rubber_ducky"->Sound.ENTITY_CHICKEN_AMBIENT;
          case "robot"->Sound.BLOCK_NOTE_BLOCK_BIT;case "hairdryer"->Sound.ENTITY_BLAZE_AMBIENT;
          case "bottles"->Sound.BLOCK_GLASS_PLACE;default->Sound.ENTITY_FOX_AMBIENT;};
          itemNoise(p,sound,1.0f,itemCode(i).equals("clown_horn")?.68f:1.25f,itemCode(i).equals("clown_horn")?15:8);
        }yield true;
      }
      case "remote"->{if(itemUseReady(p,500)){toggleShuttleLights(p);p.playSound(p.getLocation(),Sound.BLOCK_LEVER_CLICK,.65f,1);}yield true;}
      case "clock"->{if(itemUseReady(p,500)){if(!active||factorySide(p.getLocation()))p.sendActionBar(Component.text("Часы тикают"));else{long t=moonClock(Math.max(0,System.nanoTime()-roundStarted));long min=(360+t*1440/24000)%1440;p.sendActionBar(Component.text(String.format(Locale.ROOT,"%02d:%02d",min/60,min%60)));}itemNoise(p,Sound.BLOCK_NOTE_BLOCK_HAT,.45f,.8f,4);}yield true;}
      case "magic_7_ball"->{if(itemUseReady(p,700)){String[] answers={"Да","Нет","Возможно","Спроси позже"};p.sendActionBar(Component.text(answers[rng.nextInt(answers.length)]));}yield true;}
      case "gift_box"->{if(itemUseReady(p,550)){boolean ship=onCargoDeck(p.getLocation());ItemStack reward=item(SCRAP[rng.nextInt(SCRAP.length)]);if(dropLoot(p.getLocation(),reward,ship)){p.getInventory().setItemInMainHand(null);p.playSound(p.getLocation(),Sound.ENTITY_ITEM_PICKUP,.8f,1.35f);if(ship)persist();}else p.sendActionBar(Component.text("Здесь негде открыть подарок"));}yield true;}
      case "comedy"->{
        if(!active||onCargoDeck(p.getLocation())||!itemUseReady(p,700))yield true;
        long token=System.nanoTime();UUID id=p.getUniqueId();cursedMasks.put(id,token);
        p.playSound(p.getLocation(),Sound.ENTITY_WITCH_AMBIENT,.9f,.65f);
        p.sendActionBar(Component.text("Маска тянется к лицу. Убери её из руки!"));
        long epoch=campaignEpoch,raid=roundStarted;
        Bukkit.getScheduler().runTaskLater(this,()->{
          if(!cursedMasks.remove(id,token)||!p.isOnline()||p.isDead()||!active||epoch!=campaignEpoch||raid!=roundStarted||onCargoDeck(p.getLocation())||id(p.getInventory().getItemInMainHand())!=COMEDY)return;
          Location place=p.getLocation();p.getInventory().setItemInMainHand(null);p.setHealth(0);
          if(wipePending)return;
          Zombie masked=w.spawn(place,Zombie.class,z->{z.setPersistent(false);z.setAdult();z.setCanPickupItems(false);z.getEquipment().clear();z.getEquipment().setHelmet(model(Material.CARVED_PUMPKIN,"mirrp_comedy",null));z.getEquipment().setHelmetDropChance(0);applyMobHealth(z,true);});
          masked.setRemoveWhenFarAway(false);
        },65L);yield true;
      }
      default->false;
    };
  }
  @EventHandler(priority=EventPriority.LOWEST,ignoreCancelled=true) public void meleeScrap(EntityDamageByEntityEvent e){
    if(!(e.getDamager() instanceof Player p)||!playing(p)||shot||e.getCause()!=EntityDamageEvent.DamageCause.ENTITY_ATTACK||!(e.getEntity() instanceof Zombie))return;
    int i=id(p.getInventory().getItemInMainHand());if(!meleeTool(i)||!active||flight||onCargoDeck(p.getLocation())||!itemUseReady(p,i==KNIFE?300:750))return;
    e.setDamage(i==KNIFE?4:7);itemNoise(p,Sound.ENTITY_PLAYER_ATTACK_STRONG,.7f,i==KNIFE?1.5f:.8f,9);
  }
  int ammo(ItemStack s){return Math.clamp(s.getItemMeta().getPersistentDataContainer().getOrDefault(ammoKey,PersistentDataType.INTEGER,0),0,BLASTER_CAPACITY);}
  void charge(ItemStack s,int n){ItemMeta m=s.getItemMeta();m.getPersistentDataContainer().set(ammoKey,PersistentDataType.INTEGER,n);s.setItemMeta(m);}
  void fire(Player p){if(!active||flight||(aboard(p)||onCargoDeck(p.getLocation()))||p.hasCooldown(Material.PAPER))return;ItemStack s=p.getInventory().getItemInMainHand();int n=ammo(s);if(n==0){p.playSound(p.getLocation(),Sound.BLOCK_LEVER_CLICK,.44f,.75f);p.sendMessage("§7Нет заряда. Зарядка на корабле.");return;}charge(s,n-1);p.getInventory().setItemInMainHand(s);p.setCooldown(Material.PAPER,12);w.playSound(p.getLocation(),Sound.ENTITY_GENERIC_EXPLODE,SoundCategory.PLAYERS,.54f,1.65f);
    w.playSound(p.getLocation(),Sound.ENTITY_FIREWORK_ROCKET_BLAST,SoundCategory.PLAYERS,.62f,.76f);
    w.playSound(p.getLocation(),Sound.ITEM_CROSSBOW_SHOOT,SoundCategory.PLAYERS,.38f,.62f);
    Location eye=p.getEyeLocation();var hit=w.rayTrace(eye,eye.getDirection(),19,FluidCollisionMode.NEVER,true,.25,e->e instanceof Zombie||e instanceof Player other&&other!=p&&!other.isDead()&&other.getGameMode()!=GameMode.SPECTATOR);
    if(hit!=null&&hit.getHitEntity() instanceof LivingEntity target){
      if(target instanceof Player other&&(aboard(other)||onCargoDeck(other.getLocation())))return;
      shot=true;try{if(target instanceof Zombie)target.setNoDamageTicks(0);target.damage(11,p);}finally{shot=false;}
    }
  }
  @EventHandler(ignoreCancelled=true) public void creatureMelee(EntityDamageByEntityEvent e){
    if(!(e.getDamager() instanceof Zombie mob)||mob.getWorld()!=w||!(e.getEntity() instanceof Player))return;
    if(e.getCause()!=EntityDamageEvent.DamageCause.ENTITY_ATTACK)return;
    for(Rig r:rigs)if(r.mob.equals(mob)){
      if(r.creature==Creature.COIL&&watched(mob)||r.creature==Creature.JESTER&&r.chaseTicks<48||r.creature==Creature.BRACKEN&&r.watchTicks>0&&r.watchTicks<36)e.setCancelled(true);
      return;
    }
  }
  @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void blasterOnlyPvp(EntityDamageByEntityEvent e){
    if(!(e.getEntity() instanceof Player victim)||victim.getWorld()!=w)return;
    Entity attacker=e.getDamager();
    if(attacker instanceof Player&&!shot||attacker instanceof Projectile projectile&&projectile.getShooter() instanceof Player)e.setCancelled(true);
  }
  @EventHandler public void protectMob(EntityDamageEvent e){
    if(e.getEntity().getWorld()!=w||!(e.getEntity() instanceof Zombie)||shot)return;
    if(e instanceof EntityDamageByEntityEvent hit&&hit.getDamager() instanceof Player p&&meleeTool(id(p.getInventory().getItemInMainHand()))&&active&&!flight&&!onCargoDeck(p.getLocation())&&e.getCause()==EntityDamageEvent.DamageCause.ENTITY_ATTACK&&e.getDamage()==(id(p.getInventory().getItemInMainHand())==KNIFE?4:7))return;
    e.setCancelled(true);
  }
  @EventHandler public void collectLoot(PlayerInteractEntityEvent e){
    Player p=e.getPlayer();if(!playing(p))return;
    Entity target=e.getRightClicked();
    if(target instanceof Player&&e.getHand()==EquipmentSlot.HAND&&p.getGameMode()!=GameMode.SPECTATOR&&p.getGameMode()!=GameMode.CREATIVE&&id(p.getInventory().getItemInMainHand())==5){e.setCancelled(true);fire(p);return;}
    if(settingsHit!=null&&settingsHit.equals(target)){
      e.setCancelled(true);
      if(e.getHand()==EquipmentSlot.HAND&&p.getGameMode()!=GameMode.SPECTATOR&&p.getLocation().distanceSquared(target.getLocation())<=25)adjustWallSetting(p);
      return;
    }
    if(target instanceof Villager){e.setCancelled(true);return;}
    UUID hit=target.getUniqueId();FloorLoot loot=floorLoot.get(hit);if(loot==null)return;
    e.setCancelled(true);boolean cargo=shipCargo.containsKey(hit);
    if(e.getHand()!=EquipmentSlot.HAND||flight||(!active&&!company&&!cargo)||p.getGameMode()==GameMode.SPECTATOR)return;
    if(store(p,loot.stack)){removeLoot(hit);if(cargo){shipCargo.remove(hit);freeCargoSpace();persist();}}
  }
  boolean slotBlocked(Player p,int slot){return slot>SLOT_FIRST&&slot<SLOT_END&&isHeavy(p.getInventory().getItem(slot-1));}
  boolean isHeavy(ItemStack item){int i=id(item);return i>=0&&weight(i)==2;}
  boolean store(Player p,ItemStack s){
    int i=id(s);if(i<0)return false;
    if(lamp(i)){if(lamp(id(p.getInventory().getItem(LAMP_SLOT)))){p.sendMessage("§7Освободите слот 5: выберите фонарь и нажмите Q.");return false;}liveBattery.remove(p.getUniqueId());p.getInventory().setItem(LAMP_SLOT,s.clone());p.sendActionBar(Component.text("Фонарь · слот 5"));p.playSound(p.getLocation(),Sound.ENTITY_ITEM_PICKUP,.5f,1);return true;}
    for(int a=SLOT_FIRST;a<SLOT_END;a++){
      if(p.getInventory().getItem(a)!=null||slotBlocked(p,a))continue;
      if(weight(i)==2&&(a+1>=SLOT_END||p.getInventory().getItem(a+1)!=null||slotBlocked(p,a+1)))continue;
      ItemStack one=s.clone();one.setAmount(1);p.getInventory().setItem(a,one);
      if(slotBlocked(p,p.getInventory().getHeldItemSlot()))p.getInventory().setHeldItemSlot(a);
      p.playSound(p.getLocation(),Sound.ENTITY_ITEM_PICKUP,.45f,1);return true;
    }
    p.sendMessage("§7Не хватает соседних свободных слотов.");return false;
  }
  @EventHandler public void pickup(EntityPickupItemEvent e){if(e.getEntity().getWorld()!=w)return;e.setCancelled(true);
    if(e.getItem().getScoreboardTags().contains("scrap_loot_visual"))return;
    if(!(e.getEntity() instanceof Player p)||!active||flight||p.getGameMode()==GameMode.SPECTATOR)return;
    ItemStack s=e.getItem().getItemStack();if(id(s)>=0&&store(p,s)){if(s.getAmount()==1)e.getItem().remove();else{s.setAmount(s.getAmount()-1);e.getItem().setItemStack(s);}}
  }
  @EventHandler public void mergeLoot(ItemMergeEvent e){if(e.getEntity().getScoreboardTags().contains("scrap_loot_visual")||e.getTarget().getScoreboardTags().contains("scrap_loot_visual"))e.setCancelled(true);}
  @EventHandler public void hopperLoot(org.bukkit.event.inventory.InventoryPickupItemEvent e){if(e.getItem().getScoreboardTags().contains("scrap_loot_visual"))e.setCancelled(true);}
  @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true) public void extinguishStowedLamp(PlayerItemHeldEvent e){
    Player p=e.getPlayer();
    if(playing(p)&&e.getPreviousSlot()!=e.getNewSlot())cursedMasks.remove(p.getUniqueId());
    if(playing(p)&&e.getPreviousSlot()==LAMP_SLOT&&e.getNewSlot()!=LAMP_SLOT&&p.removeScoreboardTag("scrap_lamp"))lamps();
  }
  @EventHandler public void held(PlayerItemHeldEvent e){int slot=e.getNewSlot();if(playing(e.getPlayer())&&e.getPlayer().getGameMode()!=GameMode.CREATIVE&&(slot<SLOT_FIRST||slot>LAMP_SLOT||slotBlocked(e.getPlayer(),slot)))e.setCancelled(true);}
  final Map<UUID,Long> scans=new HashMap<>();
  record ScanKey(UUID viewer,UUID loot){}
  record ScanMark(TextDisplay glow,TextDisplay label){}
  boolean scanVisible(Player viewer,FloorLoot loot){
    if(viewer.hasLineOfSight(loot.hit))return true;
    Location eye=viewer.getEyeLocation(),target=loot.hit.getLocation().add(0,.16,0);
    var ray=target.toVector().subtract(eye.toVector());double length=ray.length();
    if(length<.01||length>14.5)return false;
    var step=ray.multiply(1/length);Location point=eye.clone();
    int solidCover=0;Set<Block> crossed=new HashSet<>();
    for(double d=.10;d<length;d+=.10){
      point.add(step.getX()*.10,step.getY()*.10,step.getZ()*.10);
      Block b=point.getBlock();if(b.isPassable()||!b.getBoundingBox().contains(point.toVector())||!crossed.add(b))continue;
      String name=b.getType().name();
      if(factoryScanWalls.contains(new Pt(b.getX(),b.getZ()))||name.endsWith("_DOOR"))return false;
      if(name.contains("GLASS")||name.equals("BARREL")||name.endsWith("_CHEST")||name.equals("CHEST")||name.endsWith("_FENCE")||name.endsWith("_WALL")||name.equals("IRON_BARS"))continue;
      boolean alongX=!b.getRelative(1,0,0).isPassable()&&!b.getRelative(-1,0,0).isPassable();
      boolean alongZ=!b.getRelative(0,0,1).isPassable()&&!b.getRelative(0,0,-1).isPassable();
      boolean low=b.getRelative(0,1,0).isPassable()||b.getRelative(0,2,0).isPassable();
      if((!low&&(alongX||alongZ))||++solidCover>1)return false;
    }
    return !crossed.isEmpty();
  }
  Location scanForeground(Player viewer,FloorLoot loot){
    Location anchor=loot.model.getLocation().add(0,.47,0);
    var toward=viewer.getEyeLocation().toVector().subtract(anchor.toVector());
    double distance=toward.length();
    if(distance>.01)anchor.add(toward.multiply(Math.min(.80,distance*.46)/distance));
    return anchor;
  }
  final Map<ScanKey,ScanMark> scanMarks=new HashMap<>();
  void removeScan(ScanKey key){ScanMark mark=scanMarks.remove(key);if(mark!=null){mark.glow.remove();mark.label.remove();}}
  void clearScan(Player p){for(var key:new ArrayList<>(scanMarks.keySet()))if(key.viewer.equals(p.getUniqueId()))removeScan(key);}

  @EventHandler public void swap(PlayerSwapHandItemsEvent e){
    Player p=e.getPlayer();if(!playing(p)||p.getGameMode()==GameMode.CREATIVE)return;e.setCancelled(true);
    if(flight||p.getGameMode()==GameMode.SPECTATOR)return;
    long now=System.nanoTime();if(now-scans.getOrDefault(p.getUniqueId(),now-SCAN_COOLDOWN)<SCAN_COOLDOWN)return;scans.put(p.getUniqueId(),now);optics(p);
    int found=0;p.playSound(p.getLocation(),Sound.BLOCK_AMETHYST_BLOCK_CHIME,.8f,1.3f);
    for(Entity nearby:p.getNearbyEntities(14,6,14)){
      FloorLoot loot=floorLoot.get(nearby.getUniqueId());if(loot==null||loot.hit.getLocation().distanceSquared(p.getLocation())>196||!scanVisible(p,loot))continue;
      found++;ScanKey key=new ScanKey(p.getUniqueId(),nearby.getUniqueId());ItemStack item=loot.stack;int value=price(id(item));
      removeScan(key);
      TextDisplay glow=w.spawn(scanForeground(p,loot),TextDisplay.class,t->{
        t.setVisibleByDefault(false);t.setPersistent(false);
        t.setTeleportDuration(2);
        t.text(Component.text("\uE550").font(Key.key("scrapshift","scan_marker")));
        t.setBillboard(Display.Billboard.CENTER);t.setSeeThrough(true);t.setShadowed(false);
        t.setDefaultBackground(false);t.setBackgroundColor(Color.fromARGB(0,0,0,0));
        t.setBrightness(new Display.Brightness(15,15));t.setDisplayWidth(2);t.setDisplayHeight(2);
        t.setTransformation(new Transformation(new Vector3f(0,-.36f,0),new Quaternionf(),new Vector3f(.85f),new Quaternionf()));
      });
      TextDisplay label=w.spawn(loot.hit.getLocation().add(0,1.15,0),TextDisplay.class,t->{
        t.setVisibleByDefault(false);t.setPersistent(false);t.text(item.displayName().color(net.kyori.adventure.text.format.NamedTextColor.GREEN).append(Component.newline()).append(Component.text(value>0?"Стоимость: "+value+" кр.":"Снаряжение",net.kyori.adventure.text.format.NamedTextColor.GREEN)));
        t.setBillboard(Display.Billboard.CENTER);t.setSeeThrough(true);t.setViewRange(.55f);t.setDefaultBackground(false);t.setBackgroundColor(Color.fromARGB(87,18,64,13));t.setTextOpacity((byte)185);t.setBrightness(new Display.Brightness(15,15));
      });
      p.showEntity(this,glow);p.showEntity(this,label);
      ScanMark mark=new ScanMark(glow,label);scanMarks.put(key,mark);
      Bukkit.getScheduler().runTaskLater(this,()->{if(scanMarks.get(key)==mark)removeScan(key);},75);
    }
    if(found==0)p.sendActionBar(Component.text("Сканер: рядом нет видимых предметов"));
  }
  @EventHandler public void click(InventoryClickEvent e){if(e.getWhoClicked().getWorld()==w&&e.getWhoClicked().getGameMode()!=GameMode.CREATIVE)e.setCancelled(true);}
  @EventHandler public void drag(InventoryDragEvent e){if(e.getWhoClicked().getWorld()==w&&e.getWhoClicked().getGameMode()!=GameMode.CREATIVE)e.setCancelled(true);}
  @EventHandler public void drop(PlayerDropItemEvent e){Player p=e.getPlayer();if(!playing(p)||p.getGameMode()==GameMode.CREATIVE)return;ItemStack s=e.getItemDrop().getItemStack();boolean on=onCargoDeck(p.getLocation());int k=id(s);
    if(flight||k<0||(!on&&!active&&!company)){e.setCancelled(true);return;}
    if(lamp(k)){s=nativeModel(s);battery(s,liveBattery.getOrDefault(p.getUniqueId(),battery(s)));}
    if(!dropLoot(p.getLocation(),s,on)){e.setCancelled(true);p.sendActionBar(Component.text("Здесь некуда положить предмет"));return;}
    e.getItemDrop().remove();if(lamp(k)){liveBattery.remove(p.getUniqueId());p.removeScoreboardTag("scrap_lamp");lamps();}if(on)persist();
  }
  @EventHandler public void breakBlock(BlockBreakEvent e){if(playing(e.getPlayer())&&e.getPlayer().getGameMode()!=GameMode.CREATIVE)e.setCancelled(true);}
  @EventHandler public void place(BlockPlaceEvent e){if(playing(e.getPlayer())&&e.getPlayer().getGameMode()!=GameMode.CREATIVE)e.setCancelled(true);}
  boolean survivingCrew(Player p){return !p.isDead()&&p.getHealth()>0&&p.getGameMode()!=GameMode.SPECTATOR&&p.getGameMode()!=GameMode.CREATIVE&&!dead.contains(p.getUniqueId());}
  void checkCrewWipe(){
    if(!active||flight||wipePending||w.getPlayers().stream().anyMatch(this::survivingCrew))return;
    wipePending=true;long epoch=campaignEpoch;gate(shipOffset,false);
    say("§cВесь экипаж погиб. §7Возвращение в шатл…");
    Bukkit.getScheduler().runTaskLater(this,()->{
      if(epoch==campaignEpoch&&wipePending)restartCampaign();
    },60L);
  }
  @EventHandler public void death(PlayerDeathEvent e){
    Player p=e.getEntity();if(!playing(p))return;e.setKeepInventory(true);e.getDrops().clear();e.setDroppedExp(0);
    boolean crewDeath=active&&p.getGameMode()!=GameMode.SPECTATOR&&dead.add(p.getUniqueId());
    if(crewDeath){for(int a=SLOT_FIRST;a<SLOT_END;a++){ItemStack s=p.getInventory().getItem(a);int i=id(s);if(i>=0&&i!=4)dropLoot(p.getLocation(),s,aboard(p));}}
    p.getInventory().clear();if(crewDeath)checkCrewWipe();
  }
  @EventHandler public void respawn(PlayerRespawnEvent e){Player p=e.getPlayer();if(!playing(p))return;e.setRespawnLocation(cabin());Bukkit.getScheduler().runTask(this,()->{if(!p.isOnline())return;if(active&&dead.contains(p.getUniqueId())){equipMask(p);p.setGameMode(GameMode.SPECTATOR);}else reset(p);});}
  @EventHandler public void mobDeath(EntityDeathEvent e){if(e.getEntity().getWorld()==w&&!(e.getEntity() instanceof Player)){e.getDrops().clear();e.setDroppedExp(0);}}
}
