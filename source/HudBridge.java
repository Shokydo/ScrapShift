package shift;

import kr.toxicity.hud.api.BetterHud;
import kr.toxicity.hud.api.bukkit.event.PluginReloadedEvent;
import kr.toxicity.hud.api.plugin.ReloadState;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

final class HudBridge implements Listener {
    record Vitals(double health,double stamina){}
    final Map<UUID,Vitals> values=new ConcurrentHashMap<>();
    final ScrapShift plugin; boolean ready;
    HudBridge(ScrapShift p){plugin=p;}
    void install() throws IOException {
        var better=Bukkit.getPluginManager().getPlugin("BetterHud");
        if(better==null||!better.isEnabled())throw new IOException("BetterHud-bukkit 1.14.1 is required");
        Path root=better.getDataFolder().toPath(), dest=root.resolve("packs/scrapshift");
        for(String f:List.of("huds/scrapshift.yml","images/scrapshift.yml","layouts/scrapshift.yml","assets/scrap_health_shell.png","assets/scrap_health_fill.png","assets/scrap_stamina_shell.png","assets/scrap_stamina_fill.png")){
            Path path=dest.resolve(f);Files.createDirectories(path.getParent());try(InputStream in=Objects.requireNonNull(plugin.getResource("betterhud/"+f))){Files.copy(in,path,StandardCopyOption.REPLACE_EXISTING);}
        }
        File file=root.resolve("config.yml").toFile();YamlConfiguration cfg=YamlConfiguration.loadConfiguration(file);
        if(!Files.exists(root.resolve("scrapshift-config-backup.yml"))&&file.isFile())Files.copy(file.toPath(),root.resolve("scrapshift-config-backup.yml"));
        cfg.set("default-hud",List.of("scrapshift_status"));cfg.set("enable-self-host",false);cfg.set("force-update",false);cfg.set("remove-default-hotbar",false);cfg.set("pack-type","none");cfg.set("merge-boss-bar",true);cfg.set("merge-with-external-resources",false);cfg.save(file);
        var listeners=BetterHud.getInstance().getListenerManager();
        listeners.addListener("scrapshift_health", config->event->player->values.getOrDefault(player.uuid(),new Vitals(1,1)).health());
        listeners.addListener("scrapshift_stamina",config->event->player->values.getOrDefault(player.uuid(),new Vitals(1,1)).stamina());
        Bukkit.getPluginManager().registerEvents(this,plugin);
    }
    @EventHandler public void reload(PluginReloadedEvent e){
        if(!(e.getState() instanceof ReloadState.Success ok))return;
        Bukkit.getScheduler().runTask(plugin,()->{
            if(BetterHud.getInstance().getHudManager().getHud("scrapshift_status")==null){plugin.getLogger().warning("BetterHud did not load scrapshift_status. See BetterHud errors above.");return;}
            try {
                plugin.packs.publishHud(ok.resourcePack());ready=true;
                for(var p:Bukkit.getOnlinePlayers())if(plugin.playing(p))plugin.packs.offer(p);
            }catch(IOException ex){plugin.getLogger().warning("Unable to merge BetterHud resource pack: "+ex.getMessage());}
        });
    }
}
