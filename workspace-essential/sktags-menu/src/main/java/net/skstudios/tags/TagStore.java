package net.skstudios.tags;

import org.bukkit.configuration.file.YamlConfiguration;
import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

final class TagStore {
    private final TagsPlugin plugin;
    private final File file;
    private final Map<UUID,String> worn=new ConcurrentHashMap<>();
    private boolean dirty;
    TagStore(TagsPlugin plugin){this.plugin=plugin;this.file=new File(plugin.getDataFolder(),"data/tags.yml");}
    void load(){worn.clear();if(!file.exists())return;YamlConfiguration yaml=YamlConfiguration.loadConfiguration(file);for(String key:yaml.getKeys(false)){String value=yaml.getString(key,"");if(value==null||value.isBlank())continue;try{worn.put(UUID.fromString(key),value.toLowerCase(Locale.ROOT));}catch(IllegalArgumentException ignored){}}}
    String get(UUID id){return worn.get(id);}
    void set(UUID id,String tag){worn.put(id,tag);dirty=true;}
    void clear(UUID id){if(worn.remove(id)!=null)dirty=true;}
    void save(boolean force){if(!dirty&&!force)return;YamlConfiguration yaml=new YamlConfiguration();for(Map.Entry<UUID,String> entry:new HashMap<>(worn).entrySet())yaml.set(entry.getKey().toString(),entry.getValue());try{File parent=file.getParentFile();if(parent!=null)parent.mkdirs();yaml.save(file);dirty=false;}catch(IOException ex){plugin.getLogger().log(Level.SEVERE,"Could not save data/tags.yml",ex);}}
}
