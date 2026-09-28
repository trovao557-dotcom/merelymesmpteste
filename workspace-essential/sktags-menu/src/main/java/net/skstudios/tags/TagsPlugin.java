package net.skstudios.tags;

import org.bukkit.*;
import org.bukkit.command.*;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.*;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.*;
import java.util.regex.*;

public final class TagsPlugin extends JavaPlugin implements Listener,CommandExecutor,TabCompleter {
    private static final int[] TAG_SLOTS={10,11,12,13,14,15,16,19,20,21,22,23,24,25,28,29,30,31,32,33,34,37,38,39,40,41,42,43};
    private static final String PREFIX="&#00B5FF&lTAGS &8» ";
    private final Map<String,Tag> tags=new LinkedHashMap<>();
    private TagStore store;
    private YamlConfiguration settings;
    private File settingsFile;
    private String permissionPrefix,placeholderPrefix,placeholderIdentifier,placeholderFormat;
    private List<String> removeWords;

    @Override public void onEnable(){settingsFile=new File(getDataFolder(),"settings.yml");if(!settingsFile.exists())saveResource("settings.yml",false);store=new TagStore(this);store.load();loadSettings();for(String name:List.of("tags","sktags")){PluginCommand command=getCommand(name);if(command!=null){command.setExecutor(this);command.setTabCompleter(this);}}Bukkit.getPluginManager().registerEvents(this,this);if(Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI"))new TagPlaceholders(this).register();getLogger().info("SKTags ready with owned-tags menu - "+tags.size()+" tags.");}
    @Override public void onDisable(){store.save(true);}

    private void loadSettings(){settings=YamlConfiguration.loadConfiguration(settingsFile);permissionPrefix=settings.getString("permission-prefix","sksmp.tag.");placeholderPrefix=settings.getString("placeholder.prefix","sksmp");placeholderIdentifier=settings.getString("placeholder.identifier","tag");placeholderFormat=settings.getString("placeholder.format"," &#%hex%&l[%name%]");removeWords=settings.getStringList("remove-words");if(removeWords.isEmpty())removeWords=List.of("off","remove","none");tags.clear();ConfigurationSection root=settings.getConfigurationSection("tags");if(root==null)return;for(String id:root.getKeys(false)){ConfigurationSection section=root.getConfigurationSection(id);if(section==null)continue;tags.put(id.toLowerCase(Locale.ROOT),new Tag(section.getString("name",id.toUpperCase(Locale.ROOT)),section.getString("hex","FFFFFF").replace("#","")));}}
    String placeholderPrefix(){return placeholderPrefix;}
    String placeholderIdentifier(){return placeholderIdentifier;}
    String renderTag(UUID id){String selected=store.get(id);Tag tag=selected==null?null:tags.get(selected);return tag==null?"":placeholderFormat.replace("%hex%",tag.hex).replace("%name%",tag.name);}

    @Override public boolean onCommand(@NotNull CommandSender sender,@NotNull Command command,@NotNull String label,@NotNull String[] args){if(command.getName().equalsIgnoreCase("sktags")){if(!sender.hasPermission("sktags.admin")){send(sender,"no-permission",Map.of());return true;}loadSettings();send(sender,"reloaded",Map.of("count",String.valueOf(tags.size())));return true;}if(!(sender instanceof Player player)){send(sender,"players-only",Map.of());return true;}if(!player.hasPermission("sktags.use")){deny(player,"no-permission",Map.of());return true;}if(args.length==0){open(player,0);return true;}String id=args[0].toLowerCase(Locale.ROOT);if(removeWords.contains(id)){remove(player);return true;}equip(player,id);return true;}

    private void open(Player player,int requestedPage){List<String> owned=tags.keySet().stream().filter(id->player.hasPermission(permissionPrefix+id)).toList();int pages=Math.max(1,(owned.size()+TAG_SLOTS.length-1)/TAG_SLOTS.length),page=Math.max(0,Math.min(pages-1,requestedPage));TagHolder holder=new TagHolder(page);Inventory inv=Bukkit.createInventory(holder,54,"§8YOUR TAGS §7["+(page+1)+"/"+pages+"]");holder.inventory=inv;ItemStack filler=item(Material.BLACK_STAINED_GLASS_PANE," ",List.of(),false);for(int i=0;i<54;i++)inv.setItem(i,filler);String selected=store.get(player.getUniqueId());ItemStack profile=new ItemStack(Material.PLAYER_HEAD);SkullMeta skull=(SkullMeta)profile.getItemMeta();skull.setOwningPlayer(player);skull.setDisplayName("§b§l"+player.getName());skull.setLore(List.of("§7Owned tags: §f"+owned.size(),"§7Equipped: "+(selected==null?"§cNone":display(tags.get(selected)))));profile.setItemMeta(skull);inv.setItem(4,profile);int start=page*TAG_SLOTS.length;for(int i=0;i<TAG_SLOTS.length&&start+i<owned.size();i++){String id=owned.get(start+i);Tag tag=tags.get(id);boolean equipped=id.equals(selected);inv.setItem(TAG_SLOTS[i],item(Material.NAME_TAG,"§8["+display(tag)+"§8]",List.of("§7Tag ID: §f"+id,"",equipped?"§a✔ EQUIPPED":"§eClick to equip"),equipped));holder.bySlot.put(TAG_SLOTS[i],id);}if(owned.isEmpty())inv.setItem(22,item(Material.BARRIER,"§c§lNO TAGS OWNED",List.of("§7Unlock tags in the Point Shop,","§7tournaments and playtime rewards."),false));inv.setItem(47,item(Material.ARROW,"§fPrevious Page",List.of("§7Go to the previous page."),false));inv.setItem(49,item(Material.BARRIER,"§c§lUNEQUIP TAG",List.of("§7Remove your current tag."),false));inv.setItem(51,item(Material.ARROW,"§fNext Page",List.of("§7Go to the next page."),false));player.openInventory(inv);}

    @EventHandler public void onClick(InventoryClickEvent event){if(!(event.getInventory().getHolder() instanceof TagHolder holder)||!(event.getWhoClicked() instanceof Player player))return;event.setCancelled(true);int slot=event.getRawSlot();if(slot==47){open(player,holder.page-1);return;}if(slot==51){open(player,holder.page+1);return;}if(slot==49){remove(player);open(player,holder.page);return;}String id=holder.bySlot.get(slot);if(id!=null){equip(player,id);open(player,holder.page);}}
    private void equip(Player player,String id){Tag tag=tags.get(id);if(tag==null){deny(player,"unknown-tag",Map.of("tag",id));return;}if(!player.hasPermission(permissionPrefix+id)){deny(player,"not-owned",Map.of("tag",stripCodes(tag.name)));return;}store.set(player.getUniqueId(),id);store.save(false);send(player,"equipped",Map.of("tag",stripCodes(tag.name),"hex",tag.hex,"coloured",display(tag)));player.playSound(player.getLocation(),Sound.ENTITY_PLAYER_LEVELUP,.8f,1.6f);}
    private void remove(Player player){store.clear(player.getUniqueId());store.save(false);send(player,"removed",Map.of());player.playSound(player.getLocation(),Sound.ENTITY_ITEM_BREAK,.7f,1.2f);}
    private void deny(Player player,String key,Map<String,String> values){send(player,key,values);player.playSound(player.getLocation(),Sound.ENTITY_VILLAGER_NO,.7f,1f);}
    private void send(CommandSender sender,String key,Map<String,String> values){String text=settings.getString("messages."+key,key);for(Map.Entry<String,String> entry:values.entrySet())text=text.replace("%"+entry.getKey()+"%",entry.getValue());sender.sendMessage(colour(PREFIX+text));}
    private String display(Tag tag){return tag==null?"§cNone":colour("&#"+tag.hex+"&l"+tag.name);}
    private ItemStack item(Material material,String name,List<String> lore,boolean glow){ItemStack item=new ItemStack(material);ItemMeta meta=item.getItemMeta();meta.setDisplayName(name);meta.setLore(lore);if(glow){meta.addEnchant(Enchantment.UNBREAKING,1,true);meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);}item.setItemMeta(meta);return item;}
    private String stripCodes(String text){return ChatColor.stripColor(colour(text));}
    private String colour(String text){Matcher matcher=Pattern.compile("&#([A-Fa-f0-9]{6})").matcher(text);StringBuffer out=new StringBuffer();while(matcher.find()){StringBuilder replacement=new StringBuilder("§x");for(char c:matcher.group(1).toCharArray())replacement.append('§').append(c);matcher.appendReplacement(out,Matcher.quoteReplacement(replacement.toString()));}matcher.appendTail(out);return out.toString().replace('&','§');}
    @Override public List<String> onTabComplete(@NotNull CommandSender sender,@NotNull Command command,@NotNull String alias,@NotNull String[] args){if(command.getName().equalsIgnoreCase("sktags"))return args.length==1?List.of("reload"):List.of();if(!(sender instanceof Player player)||args.length!=1)return List.of();String prefix=args[0].toLowerCase(Locale.ROOT);List<String> result=new ArrayList<>();for(String id:tags.keySet())if(player.hasPermission(permissionPrefix+id)&&id.startsWith(prefix))result.add(id);for(String word:removeWords)if(word.startsWith(prefix))result.add(word);return result;}
    private record Tag(String name,String hex){}
    private static final class TagHolder implements InventoryHolder{final int page;final Map<Integer,String> bySlot=new HashMap<>();Inventory inventory;TagHolder(int page){this.page=page;}@Override public Inventory getInventory(){return inventory;}}
}
