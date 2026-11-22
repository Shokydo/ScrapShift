package shift;

import com.google.gson.*;
import com.sun.net.httpserver.HttpServer;
import org.bukkit.entity.Player;
import org.bukkit.command.CommandSender;
import org.bukkit.event.player.PlayerResourcePackStatusEvent;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.*;
import java.util.zip.*;

final class PackService implements AutoCloseable {
    static final String OFFICIAL="https://cdn.modrinth.com/data/zweEwTUF/versions/THaIiqkz/MirRPLethalCompany.zip";
    static final String DIGEST="e507ea1d9d9062d97c643e7047750991ea09b38d271c296377cf831e4a192687e9c58622e07d3830e472cf64d6089bc9d689e3d867146f17d93a6094ac63890e";
    static final String ENDPOINT="/scrapshift/";
    static final UUID OLD_ID=UUID.fromString("75f62f41-16fa-4682-8b01-0cdc33d07cc9");
    final ScrapShift plugin; final Map<UUID,String> hosts=new HashMap<>();
    byte[] basePack;
    record Payload(byte[] bytes,byte[] hash,String path,UUID id){}
    volatile Payload payload;
    final Map<UUID,UUID> sent=new HashMap<>(); final Map<UUID,String> delivery=new HashMap<>(); final Map<UUID,String> states=new HashMap<>(); int models; boolean importedCreatures; String importIssue=""; HttpServer http; ExecutorService executor;
    PackService(ScrapShift plugin){this.plugin=plugin;}
    void start() throws IOException {
        Path dir=plugin.getDataFolder().toPath();Files.createDirectories(dir);byte[] pack;
        try(InputStream template=Objects.requireNonNull(plugin.getResource("ScrapShift-base.zip"))){pack=template.readAllBytes();}
        Path original=dir.resolve("MirRPLethalCompany.zip");
        if(Files.isRegularFile(original))try{pack=merge(pack,original);}catch(IOException|RuntimeException e){importIssue=e.toString();}
        if(models==0&&plugin.getConfig().getBoolean("mirrp-auto-download",true)){
            for(String source:List.of(OFFICIAL,OFFICIAL.replace("cdn.modrinth.com","cdn-raw.modrinth.com"))){
                Path temp=null;
                try{
                    URLConnection remote=URI.create(source).toURL().openConnection();remote.setConnectTimeout(6500);remote.setReadTimeout(8500);remote.setRequestProperty("User-Agent","ScrapShift/2.4 (Minecraft resource pack)");
                    byte[] received;try(InputStream in=remote.getInputStream()){received=bounded(in,32*1024*1024);}
                    if(!HexFormat.of().formatHex(digest("SHA-512",received)).equals(DIGEST))throw new IOException("MirRP official ZIP checksum mismatch");
                    temp=Files.createTempFile(dir,"mirrp-download-",".zip");Files.write(temp,received);byte[] combined=merge(pack,temp);
                    Files.move(temp,original,StandardCopyOption.REPLACE_EXISTING);pack=combined;importIssue="";break;
                }catch(Exception e){models=0;importIssue=e.toString();plugin.getLogger().warning("MirRP download from "+source+": "+importIssue);}
                finally{if(temp!=null)try{Files.deleteIfExists(temp);}catch(IOException ignored){}}
            }
        }else if(models==0&&importIssue.isBlank())importIssue="MirRPLethalCompany.zip отсутствует, а автозагрузка выключена.";
        if(models>0)plugin.getLogger().info("MirRP imported into the combined resource pack ("+models+" models).");
        if(models==0)plugin.getLogger().warning("MIRRP_NOT_IMPORTED: "+importIssue+" Put official MirRPLethalCompany.zip v1.1 in plugins/ScrapShift/ and restart. Serving HUD-only fallback.");
        try(ZipInputStream z=new ZipInputStream(new ByteArrayInputStream(pack))){for(ZipEntry e;(e=z.getNextEntry())!=null;)if(e.getName().equals("LETHAL-COMPANY-MC-CREDITS.txt")){importedCreatures=true;break;}}
        if(importedCreatures)plugin.getLogger().info("Loaded 6 original animated monster models from Lethal Company MC v2.1 (Marioisawesome82).");
        basePack=pack;publish(pack);
        if(endpoint(external(),"").isEmpty()&&plugin.getConfig().getBoolean("resource-pack-http",true))try {
            int port=plugin.getConfig().getInt("resource-pack-http-port",8127);
            http=HttpServer.create(new InetSocketAddress("0.0.0.0",port),8);
            executor=Executors.newFixedThreadPool(2,r->{Thread t=new Thread(r,"ScrapShift resource pack");t.setDaemon(true);return t;});
            http.setExecutor(executor);
            http.createContext(ENDPOINT,x->{try(x){
                Payload current=payload;byte[] bytes=current.bytes();
                if(!x.getRequestURI().getPath().equals(current.path())){x.sendResponseHeaders(404,-1);return;}
                if(x.getRequestMethod().equals("HEAD")){x.getResponseHeaders().set("Content-Length",Integer.toString(bytes.length));x.sendResponseHeaders(200,-1);return;}
                if(!x.getRequestMethod().equals("GET")){x.getResponseHeaders().set("Allow","GET, HEAD");x.sendResponseHeaders(405,-1);return;}
                x.getResponseHeaders().set("Content-Type","application/zip");x.getResponseHeaders().set("Cache-Control","public, max-age=31536000, immutable");
                x.sendResponseHeaders(200,bytes.length);x.getResponseBody().write(bytes);
            }});
            http.start();plugin.getLogger().info("Resource pack HTTP port "+http.getAddress().getPort()+"; route "+payload.path()+". Allow inbound traffic or configure a public HTTPS URL.");
        }catch(IOException|IllegalArgumentException e){close();plugin.getLogger().warning("Could not start resource-pack HTTP server: "+e.getMessage());}
    }
    String external(){return plugin.getConfig().getString("resource-pack-url", "").trim();}
    String endpoint(String configured,String path){
        try{URI u=URI.create(configured);if(u.getHost()!=null&&u.getUserInfo()==null&&("http".equalsIgnoreCase(u.getScheme())||"https".equalsIgnoreCase(u.getScheme())))return configured+path;}catch(IllegalArgumentException ignored){}return "";
    }
    void retry(Player p){UUID old=sent.remove(p.getUniqueId());if(old!=null)p.removeResourcePack(old);org.bukkit.Bukkit.getScheduler().runTaskLater(plugin,()->{if(p.isOnline())offer(p);},5);}

    void remember(UUID player,String address){if(address!=null&&!address.isBlank())hosts.put(player,address);}
    void offer(Player p){
        String host=hosts.get(p.getUniqueId());String url=endpoint(external(),"");Payload current=payload;String path=current.path();
        if(url.isEmpty()&&http!=null){
            String base=plugin.getConfig().getString("resource-pack-public-base", "").strip().replaceAll("/+$","");
            if(!base.isEmpty())url=endpoint(base,path);
            if(url.isEmpty()&&host!=null){try{
                String parsed=URI.create("http://"+host.strip()).getHost();
                if(parsed!=null)url=new URI("http",null,parsed,http.getAddress().getPort(),path,null,null).toASCIIString();
            }catch(IllegalArgumentException|URISyntaxException ignored){}}
        }
        if(url.isEmpty()){states.put(p.getUniqueId(),"NO_URL");plugin.getLogger().warning("No pack URL for "+p.getName()+". Configure resource-pack-public-base or resource-pack-url.");p.sendMessage("§eРесурспак не отправлен: администратор должен настроить публичный адрес пакета. /scrap pack");return;}
        delivery.put(p.getUniqueId(),url);states.put(p.getUniqueId(),"SENT");
        try{UUID previous=sent.put(p.getUniqueId(),current.id());if(previous!=null&&!previous.equals(current.id()))p.removeResourcePack(previous);p.removeResourcePack(OLD_ID);p.setResourcePack(current.id(),url,current.hash(),"Scrap Shift 3.0.49: "+(models==0?"только HUD — MirRP не импортирован":"HUD и модели MirRP"),plugin.getConfig().getBoolean("resource-pack-required",true));
            if(models==0)p.sendMessage("§eMirRP не импортирован на сервере: пока видны ванильные предметы. /scrap pack");}

        catch(IllegalArgumentException ex){plugin.getLogger().warning("Bad resource-pack URL: "+ex.getMessage());}
    }
    void status(CommandSender s){
        s.sendMessage("§aАвтономный HUD Scrap Shift: отдельные полосы HP и стамины.");
        s.sendMessage(models==0?"§cMirRP не импортирован. Раздаётся только HUD; модели пока ванильные.":"§aMirRP в итоговом паке: "+models+" привязок моделей.");
        if(models==0)s.sendMessage("§eАдминистратору: положите оригинальный MirRPLethalCompany.zip (1.1) в plugins/ScrapShift/ и перезапустите.");
        if(s instanceof Player p)s.sendMessage("§7Статус клиента: "+states.getOrDefault(p.getUniqueId(),"не отправлен")+" · повтор: /scrap pack retry");
        if(s.isOp()){if(s instanceof Player p&&delivery.containsKey(p.getUniqueId()))s.sendMessage("§7URL для клиента: "+delivery.get(p.getUniqueId()));s.sendMessage("§7SHA-1: "+HexFormat.of().formatHex(payload.hash())+" · "+(external().isEmpty()?payload.path():"Внешняя ссылка из config.yml"));if(!importIssue.isBlank())s.sendMessage("§7Причина: "+importIssue);}
    }
    void report(PlayerResourcePackStatusEvent e){if(!e.getID().equals(payload.id()))return;Player p=e.getPlayer();states.put(p.getUniqueId(),e.getStatus().name());switch(e.getStatus()){
      case FAILED_DOWNLOAD, FAILED_RELOAD, INVALID_URL, DISCARDED, DECLINED -> {
        plugin.getLogger().warning("Pack status for "+p.getName()+": "+e.getStatus()+" via "+delivery.getOrDefault(p.getUniqueId(),"unknown URL")+" (MirRP: "+models+", SHA-1: "+HexFormat.of().formatHex(payload.hash())+")");
        p.sendMessage("§eРесурспак не применён: "+e.getStatus()+". Проверьте настройки ресурспаков сервера и используйте /scrap pack retry.");
      }
      default -> {}
    }}
    void forget(UUID player){hosts.remove(player);sent.remove(player);delivery.remove(player);states.remove(player);}
    void publish(byte[] bytes)throws IOException{
        byte[] hash=digest("SHA-1",bytes);String path=ENDPOINT+HexFormat.of().formatHex(hash)+".zip";
        Files.write(plugin.getDataFolder().toPath().resolve("ScrapShift-pack.zip"),bytes);
        payload=new Payload(bytes,hash,path,UUID.nameUUIDFromBytes(hash));
    }
    static byte[] digest(String algorithm,byte[] data){try{return MessageDigest.getInstance(algorithm).digest(data);}catch(Exception e){throw new IllegalStateException(e);}}
    static byte[] bounded(InputStream in,int max)throws IOException {byte[] data=in.readNBytes(max+1);if(data.length>max)throw new IOException("ZIP entry exceeds size limit");return data;}
    record FloorPose(org.joml.Quaternionf rotation,org.joml.Vector3f offset) {}
    final Map<String,FloorPose> floorPoses=new HashMap<>();
    static final float FLOOR_SCALE=.70f;
    static FloorPose floorPose(JsonObject model){
        if(!model.has("elements"))return null;
        var geometry=new ArrayList<org.joml.Vector3f>();
        for(var e:model.getAsJsonArray("elements")){
            var element=e.getAsJsonObject();var lo=element.getAsJsonArray("from");var hi=element.getAsJsonArray("to");
            if(lo==null||hi==null)continue;
            var r=element.getAsJsonObject("rotation");var turn=new org.joml.Quaternionf();var origin=new org.joml.Vector3f();
            if(r!=null&&r.has("origin")){
                var o=r.getAsJsonArray("origin");origin.set(o.get(0).getAsFloat(),o.get(1).getAsFloat(),o.get(2).getAsFloat());
                if(r.has("angle")&&r.has("axis")){float rad=(float)Math.toRadians(r.get("angle").getAsFloat());switch(r.get("axis").getAsString()){case "x"->turn.rotationX(rad);case "y"->turn.rotationY(rad);case "z"->turn.rotationZ(rad);}}
                else turn.rotationXYZ((float)Math.toRadians(r.has("x")?r.get("x").getAsDouble():0),(float)Math.toRadians(r.has("y")?r.get("y").getAsDouble():0),(float)Math.toRadians(r.has("z")?r.get("z").getAsDouble():0));
            }
            for(int k=0;k<8;k++){
                var vertex=new org.joml.Vector3f(((k&1)==0?lo:hi).get(0).getAsFloat(),((k&2)==0?lo:hi).get(1).getAsFloat(),((k&4)==0?lo:hi).get(2).getAsFloat());
                geometry.add(turn.transform(vertex.sub(origin)).add(origin).sub(8,8,8));
            }
        }
        if(geometry.isEmpty())return null;
        FloorPose best=null;float bestHeight=Float.POSITIVE_INFINITY,s=FLOOR_SCALE/16;
        for(var rotation:List.of(new org.joml.Quaternionf(),new org.joml.Quaternionf().rotationX(-(float)Math.PI/2),new org.joml.Quaternionf().rotationZ((float)Math.PI/2))){
            var min=new org.joml.Vector3f(Float.POSITIVE_INFINITY);var max=new org.joml.Vector3f(Float.NEGATIVE_INFINITY);
            for(var v:geometry){var t=rotation.transform(new org.joml.Vector3f(v));min.min(t);max.max(t);}
            float height=max.y-min.y;
            if(height<bestHeight-.05f){bestHeight=height;best=new FloorPose(rotation,new org.joml.Vector3f(-(min.x+max.x)*s/2,-min.y*s+.018f,-(min.z+max.z)*s/2));}
        }
        return best;
    }
    byte[] merge(byte[] base,Path mirrp)throws IOException {
        Map<String,byte[]> output=new LinkedHashMap<>();
        try(ZipInputStream z=new ZipInputStream(new ByteArrayInputStream(base))){ZipEntry e;while((e=z.getNextEntry())!=null)if(!e.isDirectory())output.put(e.getName(),bounded(z,4*1024*1024));}
        Map<String,String> map=new LinkedHashMap<>();
        try(InputStream mapping=Objects.requireNonNull(plugin.getResource("mirrp-models.tsv"));BufferedReader r=new BufferedReader(new InputStreamReader(mapping,StandardCharsets.UTF_8))){r.lines().filter(s->s.contains("\t")).forEach(s->{String[] f=s.split("\t",2);map.put(f[0],f[1]);});}
        try(ZipFile input=new ZipFile(mirrp.toFile())){
            for(var row:map.entrySet()){
                String id=row.getKey(), original=row.getValue();
                JsonObject model=JsonParser.parseString(new String(entry(input,"assets/minecraft/models/item/mirrplc/"+original+".json"),StandardCharsets.UTF_8)).getAsJsonObject();
                FloorPose pose=floorPose(model);if(pose!=null)floorPoses.put("mirrp_"+id,pose);
                JsonObject textures=model.getAsJsonObject("textures");if(textures!=null)for(String k:new ArrayList<>(textures.keySet())){
                    String old=textures.get(k).getAsString().replaceFirst("^minecraft:","");
                    if(!old.startsWith("item/mirrplc/"))continue;
                    String local=old.replaceFirst("^item/mirrplc/","item/mirrp_local/");
                    String oldPath="assets/minecraft/textures/"+old+".png", newPath="assets/scrapshift/textures/"+local+".png";
                    if(!output.containsKey(newPath))output.put(newPath,entry(input,oldPath));
                    if(input.getEntry(oldPath+".mcmeta")!=null)output.put(newPath+".mcmeta",entry(input,oldPath+".mcmeta"));
                    textures.addProperty(k,"scrapshift:"+local);
                }
                output.put("assets/scrapshift/models/item/mirrp_local/"+id+".json",model.toString().getBytes(StandardCharsets.UTF_8));
                byte[] definition=("{\"model\":{\"type\":\"minecraft:model\",\"model\":\"scrapshift:item/mirrp_local/"+id+"\"}}").getBytes(StandardCharsets.UTF_8);
                output.put("assets/scrapshift/items/"+id+".json",definition);
                output.put("assets/scrapshift/items/mirrp_"+id+".json",definition);
                if(id.equals("lamp")||id.equals(ScrapShift.EXTRA[ScrapShift.PRO-6].id())){
                    String entry="{\"model\":{\"type\":\"minecraft:select\",\"property\":\"minecraft:display_context\",\"cases\":[{\"when\":\"gui\",\"model\":{\"type\":\"minecraft:empty\"}}],\"fallback\":"+JsonParser.parseString(new String(definition,StandardCharsets.UTF_8)).getAsJsonObject().get("model")+"}}";
                    output.put("assets/scrapshift/items/mirrp_"+id+"_stowed.json",entry.getBytes(StandardCharsets.UTF_8));
                }
            }
        }
        ByteArrayOutputStream bytes=new ByteArrayOutputStream();
        try(ZipOutputStream out=new ZipOutputStream(bytes)){for(var e:output.entrySet()){ZipEntry ze=new ZipEntry(e.getKey());ze.setTime(0);out.putNextEntry(ze);out.write(e.getValue());out.closeEntry();}}
        models=map.size();return bytes.toByteArray();
    }
    byte[] importCreatures(byte[] base,Path source)throws IOException{
        Map<String,byte[]> out=new LinkedHashMap<>();
        try(ZipInputStream stream=new ZipInputStream(new ByteArrayInputStream(base))){for(ZipEntry e;(e=stream.getNextEntry())!=null;)if(!e.isDirectory())out.put(e.getName(),bounded(stream,4*1024*1024));}
        String root="Resource Pack/lethal resources/assets/minecraft/";
        try(ZipFile zip=new ZipFile(source.toFile())){for(String name:List.of("bracken","hoard_bug")){
            JsonObject obj=JsonParser.parseString(new String(entry(zip,root+"models/item/"+name+".json"),StandardCharsets.UTF_8)).getAsJsonObject();
            var tex=obj.getAsJsonObject("textures");for(String key:new ArrayList<>(tex.keySet())){
                String path=tex.get(key).getAsString().replaceFirst("^minecraft:","");if(!path.matches("item/[a-z0-9_/.-]+")||path.contains(".."))throw new IOException("Unexpected texture reference");
                out.put("assets/scrapshift/textures/pmc/"+path+".png",entry(zip,root+"textures/"+path+".png"));tex.addProperty(key,"scrapshift:pmc/"+path);
            }
            out.put("assets/scrapshift/models/item/pmc/"+name+".json",obj.toString().getBytes(StandardCharsets.UTF_8));
            out.put("assets/scrapshift/items/pmc_"+name+".json",("{\"model\":{\"type\":\"minecraft:model\",\"model\":\"scrapshift:item/pmc/"+name+"\"}}").getBytes(StandardCharsets.UTF_8));
        }}
        ByteArrayOutputStream bytes=new ByteArrayOutputStream();try(ZipOutputStream z=new ZipOutputStream(bytes)){for(var e:out.entrySet()){ZipEntry ze=new ZipEntry(e.getKey());ze.setTime(0);z.putNextEntry(ze);z.write(e.getValue());z.closeEntry();}}return bytes.toByteArray();
    }
    static byte[] entry(ZipFile zip,String name)throws IOException{ZipEntry e=zip.getEntry(name);if(e==null)throw new IOException("Missing "+name);try(InputStream in=zip.getInputStream(e)){return bounded(in,4*1024*1024);}}
    @Override public void close(){if(http!=null){http.stop(0);http=null;}if(executor!=null){executor.shutdownNow();executor=null;}hosts.clear();sent.clear();}
}
