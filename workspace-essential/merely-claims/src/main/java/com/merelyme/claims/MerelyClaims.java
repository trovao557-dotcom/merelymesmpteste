package com.merelyme.claims;

import me.clip.placeholderapi.PlaceholderAPI;
import org.bukkit.*;
import org.bukkit.command.*;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.*;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.*;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.lang.reflect.Method;
import java.text.DecimalFormat;
import java.util.*;
import java.util.logging.Level;

public final class MerelyClaims extends JavaPlugin implements Listener, CommandExecutor {
    private static final int[] REWARD_SLOTS={1,2,3,4,5,6,7,10,11,12,13,14,15,16,19,20,21,22,23,24,25,28,29,30,31,32,33,34,39,41};
    private static final int PAGE_SIZE=REWARD_SLOTS.length;
    private static final String POINT_META="merely_claim_bonus_points";
    private static final String MONEY_META="merely_claim_bonus_money";

    private final List<Milestone> milestones=new ArrayList<>();
    private final Map<UUID,PlayerState> states=new HashMap<>();
    private final Map<UUID,Long> lastPoints=new HashMap<>();
    private final Map<UUID,Double> lastMoney=new HashMap<>();
    private final Set<UUID> showingBoost=new HashSet<>();
    private File dataFile;
    private YamlConfiguration data;
    private Object economy;
    private Method getBalance,depositPlayer;
    private String prefix;

    @Override public void onEnable(){
        saveDefaultConfig();prefix=colour(getConfig().getString("messages.prefix","&5&lCLAIM &8» "));
        buildMilestones();dataFile=new File(getDataFolder(),"players.yml");loadData();loadStates();hookVault();
        Objects.requireNonNull(getCommand("claim")).setExecutor(this);getServer().getPluginManager().registerEvents(this,this);
        Bukkit.getScheduler().runTaskTimer(this,this::tick,20L,20L);for(Player player:Bukkit.getOnlinePlayers())captureLater(player);
        getLogger().info("MerelyClaims ready with "+milestones.size()+" playtime milestones up to 1,000 hours.");
    }

    @Override public void onDisable(){saveStates();for(Player player:Bukkit.getOnlinePlayers())if(showingBoost.contains(player.getUniqueId()))player.sendActionBar("");showingBoost.clear();}
    @Override public boolean onCommand(CommandSender sender,Command command,String label,String[] args){if(!(sender instanceof Player player)){sender.sendMessage("Players only.");return true;}open(player,0);return true;}

    private void buildMilestones(){
        List<Integer> hours=new ArrayList<>();
        for(int h=1;h<=40;h++)hours.add(h);
        for(int h=42;h<=100;h+=2)hours.add(h);
        for(int h=105;h<=300;h+=5)hours.add(h);
        for(int h=310;h<=500;h+=10)hours.add(h);
        for(int h=525;h<=1000;h+=25)hours.add(h);
        if(hours.size()!=150)throw new IllegalStateException("Expected 150 milestones, found "+hours.size());
        for(int i=0;i<hours.size();i++)milestones.add(new Milestone(i+1,hours.get(i),reward(i+1,hours.get(i))));
    }

    private Reward reward(int level,int hours){
        int points=hours<100?150:hours<500?250:hours<2000?350:500;
        points=switch(hours){case 1->100;case 3->175;case 5->300;case 7->350;case 12->400;case 24->500;case 35->600;case 48->700;case 72->850;case 100->1000;case 150->1200;case 200->1500;case 250->1750;case 300->2000;case 500->2500;case 750->2750;case 1000->3000;case 1500->3500;case 2000->4000;case 2500->4250;case 3000->4500;case 4000->4750;case 5000->5000;default->points;};
        Map<String,Integer> keys=new LinkedHashMap<>();
        // Exactly 60% of every 30-level page contains a crate key. Keys start
        // very easy and improve with playtime, with Emerald available at 70h.
        int pagePosition=(level-1)%PAGE_SIZE;
        if(pagePosition%5==0||pagePosition%5==2||pagePosition%5==4){
            String key;
            if(hours<25)key="coal";
            else if(hours<50)key=level%2==0?"iron":"coal";
            else if(hours<70)key="iron";
            else if(hours<120)key=(hours==70||level%3==0)?"emerald":"gold";
            else if(hours<250)key=level%3==0?"gold":"emerald";
            else if(hours<500)key="emerald";
            else key=level%4==0?"special":"emerald";
            int amount=hours>=750&&level%10==0?2:1;
            keys.put(key,amount);
        }
        String tag=switch(hours){case 100->"dedicated";case 500->"veteran";case 1000->"elite";case 2500->"legend";case 5000->"timeless";default->null;};
        List<BoostGrant> boosts=new ArrayList<>();
        switch(hours){
            case 200->boosts.add(new BoostGrant(Boost.MONEY,30));case 500->boosts.add(new BoostGrant(Boost.POINTS,30));case 1000->boosts.add(new BoostGrant(Boost.MONEY,60));
            case 2000->boosts.add(new BoostGrant(Boost.POINTS,60));case 3000->{boosts.add(new BoostGrant(Boost.MONEY,60));boosts.add(new BoostGrant(Boost.POINTS,60));}
            case 4000->boosts.add(new BoostGrant(Boost.MONEY,120));case 5000->{boosts.add(new BoostGrant(Boost.MONEY,180));boosts.add(new BoostGrant(Boost.POINTS,180));}
        }
        return new Reward(points,keys,tag,boosts);
    }

    private void open(Player player,int requestedPage){
        long minutes=playtime(player);int pages=(milestones.size()+PAGE_SIZE-1)/PAGE_SIZE,page=Math.max(0,Math.min(pages-1,requestedPage));
        ClaimHolder holder=new ClaimHolder(page);Inventory inv=Bukkit.createInventory(holder,54,"§f\uF804\uE208");holder.inventory=inv;fill(inv);
        int start=page*PAGE_SIZE;for(int i=0;i<PAGE_SIZE&&start+i<milestones.size();i++){Milestone milestone=milestones.get(start+i);inv.setItem(REWARD_SLOTS[i],milestoneItem(player,milestone,minutes));}
        inv.setItem(47,item(Material.ARROW,"§fPrevious Page",page>0?List.of("§7Click to go back."):List.of("§8First page.")));inv.setItem(48,item(Material.BARRIER,"§cClose",List.of()));
        inv.setItem(50,item(Material.CLOCK,"§d§l150 PLAYTIME REWARDS",List.of("§7Page §f"+(page+1)+"§7/§f"+pages,"§7Final milestone: §f1,000 hours")));
        int ready=ready(player,minutes).size();inv.setItem(49,item(ready>0?Material.HOPPER:Material.GRAY_DYE,"§a§lCLAIM ALL",List.of("§7Ready now: §f"+ready,ready>0?"§aClick to collect every unlocked reward.":"§8Nothing ready yet.")));
        inv.setItem(51,item(Material.ARROW,"§fNext Page",page<pages-1?List.of("§7Click to continue."):List.of("§8Last page.")));player.openInventory(inv);
    }

    private ItemStack milestoneItem(Player player,Milestone milestone,long minutes){
        boolean claimed=state(player).claimed.contains(milestone.level),available=minutes>=milestone.hours*60L;
        List<String> lore=new ArrayList<>();lore.add("§c§lREQUIREMENT:");lore.add("§7│ §f"+milestone.hours+" Hours of Playtime");lore.add("");lore.addAll(rewardLore(milestone.reward));lore.add("");
        lore.add(claimed?"§a✔ ALREADY REDEEMED":available?"§e➜ CLICK TO REDEEM":"§cLOCKED §8• §7"+formatMinutes(milestone.hours*60L-minutes)+" remaining");
        Material icon=claimed?Material.COAL_BLOCK:milestoneMaterial((milestone.level-1)/PAGE_SIZE);
        ItemStack head=item(icon,(claimed?"§a":available?"§d":"§7")+"§lLEVEL "+milestone.level,lore);ItemMeta meta=head.getItemMeta();if(available&&!claimed){meta.addEnchant(org.bukkit.enchantments.Enchantment.UNBREAKING,1,true);meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);}head.setItemMeta(meta);return head;
    }

    private List<String> rewardLore(Reward reward){List<String> lore=new ArrayList<>();lore.add("§fRewards:");lore.add("§8• §b"+reward.points+" Points");reward.keys.forEach((key,amount)->lore.add("§8• §6"+amount+"x "+prettyKey(key)+" Key"));if(reward.tag!=null)lore.add("§8• §dExclusive ["+pretty(reward.tag).toUpperCase(Locale.ROOT)+"] Tag");for(BoostGrant boost:reward.boosts)lore.add("§8• "+boost.type.colour+boost.type.label+" §7for §f"+boost.minutes+"m");return lore;}

    @EventHandler public void onClick(InventoryClickEvent event){
        if(!(event.getInventory().getHolder() instanceof ClaimHolder holder)||!(event.getWhoClicked() instanceof Player player))return;event.setCancelled(true);int raw=event.getRawSlot();if(raw<0||raw>=event.getInventory().getSize())return;
        if(raw==48){player.closeInventory();return;}if(raw==47){open(player,holder.page-1);return;}if(raw==51){open(player,holder.page+1);return;}long minutes=playtime(player);if(raw==49){claimAll(player,minutes);open(player,holder.page);return;}
        for(int i=0;i<REWARD_SLOTS.length;i++)if(raw==REWARD_SLOTS[i]){int index=holder.page*PAGE_SIZE+i;if(index<milestones.size())claimOne(player,milestones.get(index),minutes);open(player,holder.page);return;}
    }

    private void claimOne(Player player,Milestone milestone,long minutes){PlayerState state=state(player);if(state.claimed.contains(milestone.level))return;if(minutes<milestone.hours*60L){player.sendMessage(prefix+"§cThat milestone is still locked.");return;}state.claimed.add(milestone.level);if(!saveStates()){state.claimed.remove(milestone.level);player.sendMessage(message("reward-error"));return;}deliver(player,milestone.reward);player.sendMessage(message("claimed").replace("%level%",String.valueOf(milestone.level)).replace("%hours%",String.valueOf(milestone.hours)));player.playSound(player.getLocation(),Sound.ENTITY_PLAYER_LEVELUP,0.8f,1.35f);}

    private void claimAll(Player player,long minutes){
        List<Milestone> ready=ready(player,minutes);if(ready.isEmpty()){player.sendMessage(message("none-ready"));return;}PlayerState state=state(player);for(Milestone milestone:ready)state.claimed.add(milestone.level);
        if(!saveStates()){for(Milestone milestone:ready)state.claimed.remove(milestone.level);player.sendMessage(message("reward-error"));return;}int points=0;Map<String,Integer> keys=new LinkedHashMap<>();List<String> tags=new ArrayList<>();List<BoostGrant> boosts=new ArrayList<>();
        for(Milestone milestone:ready){Reward reward=milestone.reward;points+=reward.points;reward.keys.forEach((k,v)->keys.merge(k,v,Integer::sum));if(reward.tag!=null)tags.add(reward.tag);boosts.addAll(reward.boosts);}deliver(player,new Reward(points,keys,null,boosts));for(String tag:tags)grantTag(player,tag);
        player.sendMessage(message("claimed-all").replace("%amount%",String.valueOf(ready.size())).replace("%points%",String.valueOf(points)));player.playSound(player.getLocation(),Sound.UI_TOAST_CHALLENGE_COMPLETE,1f,1f);
    }

    private List<Milestone> ready(Player player,long minutes){PlayerState state=state(player);return milestones.stream().filter(m->minutes>=m.hours*60L&&!state.claimed.contains(m.level)).toList();}
    private void deliver(Player player,Reward reward){markBonus(player,POINT_META);Bukkit.dispatchCommand(Bukkit.getConsoleSender(),"points give "+player.getName()+" "+reward.points);reward.keys.forEach((key,amount)->Bukkit.dispatchCommand(Bukkit.getConsoleSender(),"keys give "+player.getName()+" "+key+" "+amount));if(reward.tag!=null)grantTag(player,reward.tag);for(BoostGrant boost:reward.boosts)activate(player,boost);Bukkit.getScheduler().runTaskLater(this,()->{Long value=readPoints(player);if(value!=null)lastPoints.put(player.getUniqueId(),value);},2L);}
    private void grantTag(Player player,String tag){Bukkit.dispatchCommand(Bukkit.getConsoleSender(),"lp user "+player.getName()+" permission set sksmp.tag.claim_"+tag+" true");player.sendMessage(prefix+"§dUnlocked tag: §f["+pretty(tag).toUpperCase(Locale.ROOT)+"] §8• §7Use §f/tags claim_"+tag);}
    private void activate(Player player,BoostGrant grant){PlayerState state=state(player);long now=System.currentTimeMillis(),duration=grant.minutes*60_000L;if(grant.type==Boost.MONEY)state.moneyUntil=Math.max(now,state.moneyUntil)+duration;else state.pointsUntil=Math.max(now,state.pointsUntil)+duration;saveStates();captureLater(player);player.sendMessage(prefix+grant.type.colour+grant.type.label+" §aactivated for §f"+grant.minutes+" minutes§a.");}

    private void tick(){long now=System.currentTimeMillis();for(Player player:Bukkit.getOnlinePlayers()){PlayerState state=state(player);boolean money=state.moneyUntil>now,points=state.pointsUntil>now;if(!money&&!points){removeBar(player);continue;}updateBar(player,state,now);tickBonuses(player,state,now);}if(now%60_000L<1000L)saveStates();}
    private void tickBonuses(Player player,PlayerState state,long now){UUID id=player.getUniqueId();Long currentPoints=readPoints(player);if(currentPoints!=null){Long previous=lastPoints.putIfAbsent(id,currentPoints);if(previous!=null){long gain=currentPoints-previous;if(gain>0&&state.pointsUntil>now){markBonus(player,POINT_META);Bukkit.dispatchCommand(Bukkit.getConsoleSender(),"points give "+player.getName()+" "+gain);lastPoints.put(id,currentPoints+gain);}else lastPoints.put(id,currentPoints);}}Double currentMoney=readMoney(player);if(currentMoney!=null){Double previous=lastMoney.putIfAbsent(id,currentMoney);if(previous!=null){double gain=currentMoney-previous;if(gain>0.009&&state.moneyUntil>now){double bonus=Math.round(gain*40.0)/100.0;if(bonus>0&&depositMoney(player,bonus)){markBonus(player,MONEY_META);lastMoney.put(id,currentMoney+bonus);}else lastMoney.put(id,currentMoney);}else lastMoney.put(id,currentMoney);}}}
    private void updateBar(Player player,PlayerState state,long now){long money=Math.max(0,(state.moneyUntil-now+999)/1000),points=Math.max(0,(state.pointsUntil-now+999)/1000);String title;if(money>0&&points>0)title="§d§lPERSONAL BOOST §8• §a+40% Money §f"+shortTime(money)+" §8• §b2x Points §f"+shortTime(points);else if(money>0)title="§d§lPERSONAL BOOST §8• §a+40% Money §8• §f"+shortTime(money);else title="§d§lPERSONAL BOOST §8• §b2x Points §8• §f"+shortTime(points);player.sendActionBar(title);showingBoost.add(player.getUniqueId());}
    private void removeBar(Player player){if(showingBoost.remove(player.getUniqueId()))player.sendActionBar("");}
    private void markBonus(Player player,String key){player.setMetadata(key,new FixedMetadataValue(this,System.currentTimeMillis()+1800L));}

    @EventHandler public void onJoin(PlayerJoinEvent event){Player player=event.getPlayer();captureLater(player);Bukkit.getScheduler().runTaskLater(this,()->{if(!player.isOnline())return;int amount=ready(player,playtime(player)).size();if(amount>0)player.sendMessage(message("available-on-join").replace("%amount%",String.valueOf(amount)));},60L);}
    @EventHandler public void onQuit(PlayerQuitEvent event){UUID id=event.getPlayer().getUniqueId();lastPoints.remove(id);lastMoney.remove(id);removeBar(event.getPlayer());saveStates();}
    private void captureLater(Player player){Bukkit.getScheduler().runTaskLater(this,()->{if(!player.isOnline())return;Long points=readPoints(player);Double money=readMoney(player);if(points!=null)lastPoints.put(player.getUniqueId(),points);if(money!=null)lastMoney.put(player.getUniqueId(),money);},5L);}
    private long playtime(Player player){try{String raw=PlaceholderAPI.setPlaceholders(player,"%skcore_playtime_minutes%");return Long.parseLong(ChatColor.stripColor(raw).replace(",","").trim());}catch(Exception ignored){return player.getStatistic(Statistic.PLAY_ONE_MINUTE)/20L/60L;}}
    private Long readPoints(Player player){try{String raw=PlaceholderAPI.setPlaceholders(player,"%skcore_points%");return Long.parseLong(ChatColor.stripColor(raw).replace(",","").trim());}catch(Exception ignored){return null;}}
    private void hookVault(){try{Class<?> type=Class.forName("net.milkbowl.vault.economy.Economy");@SuppressWarnings({"rawtypes","unchecked"})RegisteredServiceProvider<?> registration=Bukkit.getServicesManager().getRegistration((Class)type);if(registration==null)return;economy=registration.getProvider();getBalance=type.getMethod("getBalance",OfflinePlayer.class);depositPlayer=type.getMethod("depositPlayer",OfflinePlayer.class,double.class);}catch(Exception ex){getLogger().warning("Vault hook unavailable: "+ex.getMessage());}}
    private Double readMoney(Player player){if(economy==null)return null;try{return ((Number)getBalance.invoke(economy,player)).doubleValue();}catch(Exception ignored){return null;}}
    private boolean depositMoney(Player player,double amount){try{depositPlayer.invoke(economy,player,amount);return true;}catch(Exception ex){getLogger().log(Level.WARNING,"Could not pay personal money boost to "+player.getName(),ex);return false;}}

    private void loadStates(){states.clear();for(String raw:data.getKeys(false)){try{UUID id=UUID.fromString(raw);PlayerState state=new PlayerState();state.claimed.addAll(data.getIntegerList(raw+".claimed"));state.moneyUntil=data.getLong(raw+".money-until");state.pointsUntil=data.getLong(raw+".points-until");states.put(id,state);}catch(IllegalArgumentException ignored){}}}
    private void loadData(){File marker=new File(getDataFolder(),"reset-on-next-start.txt");if(!marker.isFile()){data=YamlConfiguration.loadConfiguration(dataFile);return;}data=new YamlConfiguration();try{data.save(dataFile);if(!marker.delete())getLogger().warning("Claim reset completed, but the one-time reset marker could not be removed.");getLogger().info("All claimed playtime rewards and personal boosts were reset once.");}catch(Exception ex){getLogger().log(Level.SEVERE,"Could not reset claim progress",ex);data=YamlConfiguration.loadConfiguration(dataFile);}}
    private boolean saveStates(){try{for(Map.Entry<UUID,PlayerState> entry:states.entrySet()){String root=entry.getKey().toString();PlayerState state=entry.getValue();data.set(root+".claimed",state.claimed.stream().sorted().toList());data.set(root+".money-until",state.moneyUntil);data.set(root+".points-until",state.pointsUntil);}data.save(dataFile);return true;}catch(Exception ex){getLogger().log(Level.SEVERE,"Could not save claim progress",ex);return false;}}
    private PlayerState state(Player player){return states.computeIfAbsent(player.getUniqueId(),id->new PlayerState());}
    private String message(String key){return prefix+colour(getConfig().getString("messages."+key,key));}
    private String pretty(String id){String[] parts=id.split("[_-]");StringJoiner out=new StringJoiner(" ");for(String part:parts)out.add(part.isEmpty()?part:Character.toUpperCase(part.charAt(0))+part.substring(1));return out.toString();}
    private String prettyKey(String id){return id.equalsIgnoreCase("netherite")?"Special":pretty(id);}
    private Material milestoneMaterial(int page){return switch(page){case 0->Material.IRON_BLOCK;case 1->Material.GOLD_BLOCK;case 2->Material.EMERALD_BLOCK;case 3->Material.BEACON;default->Material.NETHERITE_BLOCK;};}
    private String formatMinutes(long minutes){minutes=Math.max(0,minutes);return minutes/60+"h "+minutes%60+"m";}
    private String shortTime(long seconds){return String.format(Locale.ROOT,"%02d:%02d:%02d",seconds/3600,(seconds%3600)/60,seconds%60);}
    private String colour(String text){if(text==null)return"";java.util.regex.Matcher matcher=java.util.regex.Pattern.compile("&#([A-Fa-f0-9]{6})").matcher(text);StringBuffer out=new StringBuffer();while(matcher.find()){StringBuilder replacement=new StringBuilder("§x");for(char c:matcher.group(1).toCharArray())replacement.append('§').append(c);matcher.appendReplacement(out,java.util.regex.Matcher.quoteReplacement(replacement.toString()));}matcher.appendTail(out);return out.toString().replace('&','§');}
    private ItemStack item(Material material,String name,List<String> lore){ItemStack item=new ItemStack(material);ItemMeta meta=item.getItemMeta();meta.setDisplayName(name);meta.setLore(lore);item.setItemMeta(meta);return item;}
    private void fill(Inventory inv){ItemStack pane=item(Material.BLACK_STAINED_GLASS_PANE," ",List.of());for(int i=0;i<inv.getSize();i++)inv.setItem(i,pane);}

    private record Milestone(int level,int hours,Reward reward){}
    private record Reward(int points,Map<String,Integer> keys,String tag,List<BoostGrant> boosts){}
    private record BoostGrant(Boost type,int minutes){}
    private enum Boost{MONEY("+40% Money","§a"),POINTS("2x Points","§b");final String label,colour;Boost(String label,String colour){this.label=label;this.colour=colour;}}
    private static final class PlayerState{final Set<Integer> claimed=new HashSet<>();long moneyUntil,pointsUntil;}
    private static final class ClaimHolder implements InventoryHolder{final int page;Inventory inventory;ClaimHolder(int page){this.page=page;}@Override public Inventory getInventory(){return inventory;}}
}
