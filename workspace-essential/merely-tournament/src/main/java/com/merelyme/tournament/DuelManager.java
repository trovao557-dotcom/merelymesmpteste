package com.merelyme.tournament;

import net.kyori.adventure.text.Component;
import org.bukkit.*;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.*;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.*;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.io.File;
import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;

final class DuelManager implements Listener, CommandExecutor, TabCompleter {
    private enum Mode { ONE_V_ONE("1v1", 10, 1), TWO_V_TWO("2v2", 15, 2); final String label; final int reward, teamSize; Mode(String label,int reward,int teamSize){this.label=label;this.reward=reward;this.teamSize=teamSize;} }
    private enum State { COUNTDOWN, FIGHTING, FINISHED }
    private enum SaturdayPhase { CLOSED, OPEN, RUNNING }
    private enum HeadAction { INVITE_1V1, CHALLENGE_2V2, INVITE_PARTNER }
    private record DuelParty(Mode mode,UUID leader,List<UUID> players,boolean randomTeam) {
        DuelParty(Mode mode,UUID leader,List<UUID> players){this(mode,leader,players,false);}
        DuelParty { players=List.copyOf(players); }
    }
    private record DuelInvite(UUID challenger,UUID target,Mode mode,long expiresAt,long wager) {}
    private record PartnerInvite(UUID leader,UUID target,long expiresAt) {}
    private record HeadView(HeadAction action,int page,Map<Integer,UUID> slots) {}

    private static final String PREFIX="§8[§d§lDUEL§8] §r";
    private static final String MAIN_TITLE="§f\uF804\uE209";
    private static final String TWO_TITLE="§f\uF804\uE20A";
    private static final String HEAD_TITLE="§f\uF804\uE20B";
    private final MerelyTournament plugin;
    private final Deque<DuelParty> queue1=new ArrayDeque<>(),queue2=new ArrayDeque<>();
    private final Deque<UUID> soloTwoQueue=new ArrayDeque<>();
    private final Map<UUID,DuelParty> queuedByPlayer=new HashMap<>();
    private final Map<UUID,UUID> selectedPartners=new HashMap<>();
    private final Map<UUID,UUID> partnerLeaders=new HashMap<>();
    private final Map<UUID,PartnerInvite> partnerInvites=new HashMap<>();
    private final Map<UUID,DuelInvite> invites=new HashMap<>();
    private final Map<UUID,HeadView> headViews=new HashMap<>();
    private final Map<UUID,DuelMatch> matchesByPlayer=new HashMap<>();
    private final Map<UUID,DuelPreparation> preparationsByPlayer=new HashMap<>();
    private final Set<UUID> restoring=new HashSet<>();
    private final Set<UUID> recentDuelDeaths=new HashSet<>();
    private final Map<UUID,DuelMatch> spectatorMatches=new HashMap<>();
    private final Map<UUID,UUID> spectatorTargets=new HashMap<>();
    private final Set<UUID> saturdayRegisteredLeaders=new LinkedHashSet<>();
    private final Set<DuelMatch> saturdayMatches=new HashSet<>();
    private final List<DuelParty> saturdayRoundWinners=new ArrayList<>();
    private final AtomicLong ids=new AtomicLong();
    private SaturdayPhase saturdayPhase=SaturdayPhase.CLOSED;
    private int saturdayPendingMatches;
    private LocalDate lastSaturdayRun;

    DuelManager(MerelyTournament plugin){
        this.plugin=plugin;
        PluginCommand command=Objects.requireNonNull(plugin.getCommand("duel"));
        command.setPermission(null);command.setExecutor(this);command.setTabCompleter(this);
        PluginCommand twoCommand=Objects.requireNonNull(plugin.getCommand("duel2v2"));
        twoCommand.setPermission(null);twoCommand.setExecutor(this);twoCommand.setTabCompleter(this);
        PluginCommand wagerCommand=Objects.requireNonNull(plugin.getCommand("wagerduel"));
        wagerCommand.setPermission(null);wagerCommand.setExecutor(this);wagerCommand.setTabCompleter(this);
        Bukkit.getPluginManager().registerEvents(this,plugin);
        String last=plugin.duelSystemString("last-saturday-2v2","");try{if(!last.isBlank())lastSaturdayRun=LocalDate.parse(last);}catch(Exception ignored){}
        new BukkitRunnable(){@Override public void run(){purgeExpiredAndInvalid();saturdayTick();}}.runTaskTimer(plugin,200L,200L);
    }

    boolean hasActiveMatches(){return !matchesByPlayer.isEmpty()||!preparationsByPlayer.isEmpty();}
    boolean isInMatch(UUID id){return matchesByPlayer.containsKey(id)||preparationsByPlayer.containsKey(id)||restoring.contains(id);}
    boolean isHandlingDeath(UUID id){return recentDuelDeaths.contains(id);}
    World arenaWorld(UUID id){DuelMatch match=matchesByPlayer.get(id);return match!=null&&match.state!=State.FINISHED?match.world:null;}
    boolean isSpectatable(UUID id){DuelMatch match=matchesByPlayer.get(id);return match!=null&&match.state!=State.FINISHED;}
    boolean isSpectating(UUID id){return spectatorMatches.containsKey(id);}
    List<String> spectatablePlayers(){return matchesByPlayer.entrySet().stream().filter(e->e.getValue().state!=State.FINISHED).map(e->Bukkit.getPlayer(e.getKey())).filter(Objects::nonNull).map(Player::getName).distinct().sorted(String.CASE_INSENSITIVE_ORDER).toList();}

    boolean startSpectating(Player viewer,Player target){
        UUID viewerId=viewer.getUniqueId();DuelMatch match=matchesByPlayer.get(target.getUniqueId());
        if(match==null||match.state==State.FINISHED){viewer.sendMessage(PREFIX+"§cThat player is not in a live 1v1 or 2v2 arena.");return true;}
        if(isInMatch(viewerId)){viewer.sendMessage(PREFIX+"§cFighters cannot spectate another arena.");return true;}
        if(spectatorMatches.containsKey(viewerId)){viewer.sendMessage(PREFIX+"§cYou are already watching a match. Use §f/unwatch §cfirst.");return true;}
        if(plugin.duelHasStoredState(viewerId)){viewer.sendMessage(PREFIX+"§cYour previous inventory still needs recovery before you can spectate.");plugin.duelRestoreWhenReady(viewerId);return true;}
        if(!plugin.duelSnapshot(viewer)){viewer.sendMessage(PREFIX+"§cYour inventory could not be stored safely, so spectator mode was cancelled.");return true;}
        Location destination=target.getLocation().clone().add(0,2,0);destination.getChunk().load(true);viewer.closeInventory();viewer.getInventory().clear();viewer.getInventory().setArmorContents(new ItemStack[4]);viewer.getInventory().setItemInOffHand(null);viewer.setGameMode(GameMode.SPECTATOR);viewer.setInvulnerable(true);spectatorMatches.put(viewerId,match);spectatorTargets.put(viewerId,target.getUniqueId());
        if(!plugin.duelTeleport(viewer,destination)||viewer.getWorld()!=match.world){spectatorMatches.remove(viewerId);spectatorTargets.remove(viewerId);plugin.duelRestore(viewer);viewer.sendMessage(PREFIX+"§cThe arena teleport failed, so your items were restored.");return true;}
        viewer.setSpectatorTarget(target);if(match.bar!=null)match.bar.addPlayer(viewer);viewer.sendMessage(PREFIX+"§aWatching §f"+target.getName()+"§a. Use §f/unwatch §ato leave and restore your items.");return true;
    }

    void stopSpectating(Player viewer,String message){
        DuelMatch match=spectatorMatches.remove(viewer.getUniqueId());spectatorTargets.remove(viewer.getUniqueId());
        if(match==null){viewer.sendMessage(PREFIX+"§7You are not watching a duel.");return;}
        viewer.setSpectatorTarget(null);if(match.bar!=null)match.bar.removePlayer(viewer);plugin.duelRestore(viewer);if(message!=null&&!message.isBlank())viewer.sendMessage(PREFIX+"§7"+message);
    }

    private void switchSpectatorsFrom(DuelMatch match,UUID removed){
        Player replacement=match.alivePlayers().stream().map(Bukkit::getPlayer).filter(Objects::nonNull).findFirst().orElse(null);if(replacement==null)return;
        for(Map.Entry<UUID,DuelMatch> entry:new HashMap<>(spectatorMatches).entrySet())if(entry.getValue()==match&&Objects.equals(spectatorTargets.get(entry.getKey()),removed)){Player viewer=Bukkit.getPlayer(entry.getKey());if(viewer!=null){spectatorTargets.put(entry.getKey(),replacement.getUniqueId());viewer.setSpectatorTarget(replacement);}}
    }
    private void returnSpectators(DuelMatch match,String message){for(UUID id:spectatorMatches.entrySet().stream().filter(e->e.getValue()==match).map(Map.Entry::getKey).toList()){Player viewer=Bukkit.getPlayer(id);spectatorMatches.remove(id);spectatorTargets.remove(id);if(viewer!=null){viewer.setSpectatorTarget(null);if(match.bar!=null)match.bar.removePlayer(viewer);plugin.duelRestore(viewer);viewer.sendMessage(PREFIX+"§7"+message);}}}
    private void returnAllSpectators(String message){for(DuelMatch match:new HashSet<>(spectatorMatches.values()))returnSpectators(match,message);spectatorMatches.clear();spectatorTargets.clear();}

    void shutdown(){
        saturdayPhase=SaturdayPhase.CLOSED;returnAllSpectators("The match system is reloading.");queue1.clear();queue2.clear();soloTwoQueue.clear();queuedByPlayer.clear();invites.clear();partnerInvites.clear();selectedPartners.clear();partnerLeaders.clear();headViews.clear();saturdayRegisteredLeaders.clear();saturdayMatches.clear();
        for(DuelPreparation prep:new HashSet<>(preparationsByPlayer.values()))abortPreparation(prep,"Server reloading.",false);
        for(DuelMatch match:new HashSet<>(matchesByPlayer.values()))finish(match,null,"Server reloading.",false);
        preparationsByPlayer.clear();matchesByPlayer.clear();restoring.clear();
    }

    private void openMain(Player player){
        Inventory inv=Bukkit.createInventory(null,27,MAIN_TITLE);
        inv.setItem(4,item(Material.NETHER_STAR,"§d§lMERELYME DUELS","§7Private arena • Tournament kit • Safe inventory","§fDaily Glory: §d"+plugin.duelDailyEarned(player.getUniqueId())+"§7/§d"+plugin.duelDailyCap()));
        inv.setItem(10,item(Material.IRON_SWORD,"§a§lSTART 1v1","§7Join the random 1v1 queue","§fWin reward: §d"+plugin.getConfig().getInt("duels.rewards.1v1",10)+" Glory"));
        inv.setItem(12,item(Material.PLAYER_HEAD,"§e§lINVITE A PLAYER TO PARTY","§7Create your 2v2 party with an online player","§7They must accept the party invite"));
        inv.setItem(14,item(Material.DIAMOND_SWORD,"§b§lOPEN 2v2","§7Invite a teammate or let the server choose one","§fWin reward: §d"+plugin.getConfig().getInt("duels.rewards.2v2",15)+" Glory each"));
        inv.setItem(16,item(Material.TOTEM_OF_UNDYING,"§6§lCHALLENGE A 2v2 PARTY","§7Invite another prepared party leader"));
        inv.setItem(20,item(Material.GOLD_INGOT,"§6§lWAGERED 1v1","§7Both players stake the same money.","§7Winner receives the full pot.","§f/wager <player> <amount>"));
        inv.setItem(22,item(Material.IRON_SWORD,"§c§lCHALLENGE A PLAYER TO 1v1","§7Choose an online player for a direct duel","§7or use §f/duel <player>"));
        DuelInvite incoming=validInvite(player.getUniqueId());
        if(incoming!=null)inv.setItem(18,item(Material.LIME_DYE,"§a§lACCEPT "+incoming.mode.label+" INVITE","§7From: §f"+name(incoming.challenger),incoming.wager>0?"§6Stake: §e$"+money(incoming.wager):"§7No money stake","§7Click to accept"));
        if(queuedByPlayer.containsKey(player.getUniqueId()))inv.setItem(26,item(Material.BARRIER,"§c§lLEAVE QUEUE","§7You are queued for §f"+queuedByPlayer.get(player.getUniqueId()).mode.label));
        fill(inv);player.openInventory(inv);
    }

    private void openTwo(Player player){
        Inventory inv=Bukkit.createInventory(null,27,TWO_TITLE);
        UUID leader=partyLeader(player.getUniqueId()),partner=partyMate(player.getUniqueId());String partnerName=partner==null?"None":name(partner);
        String party=leader==null?"No party":name(leader)+" + "+name(selectedPartners.get(leader));
        inv.setItem(4,item(Material.DIAMOND_SWORD,"§b§l2v2 DUELS","§7Party: §f"+party,"§fDaily Glory: §d"+plugin.duelDailyEarned(player.getUniqueId())+"§7/§d"+plugin.duelDailyCap()));
        inv.setItem(10,item(Material.PLAYER_HEAD,"§d§lINVITE A TEAMMATE","§7Teammate: §f"+partnerName,"§7The invited player must accept"));
        inv.setItem(12,item(Material.LIME_DYE,"§a§lQUEUE WITH TEAMMATE","§7Requires an accepted teammate","§fWin reward: §d"+plugin.getConfig().getInt("duels.rewards.2v2",15)+" Glory each"));
        inv.setItem(14,item(Material.WIND_CHARGE,"§e§lJOIN WITHOUT A TEAM","§7The server chooses your teammate and opponents","§7Can face another solo pair or a prepared party"));
        inv.setItem(16,item(Material.TOTEM_OF_UNDYING,"§6§lCHALLENGE A PARTY","§7Every complete online party appears here","§7Either member may send or accept the duel"));
        inv.setItem(22,item(Material.NETHER_STAR,"§b§lSATURDAY 2v2 TOURNAMENT",saturdayStatus(),saturdayPhase==SaturdayPhase.OPEN?"§eClick to join or leave with your party":"§7Use /2v2 tournament for details"));
        PartnerInvite partnerInvite=validPartnerInvite(player.getUniqueId());
        if(partnerInvite!=null)inv.setItem(18,item(Material.LIME_DYE,"§a§lACCEPT TEAM INVITE","§7From: §f"+name(partnerInvite.leader),"§7Click to form a 2v2 team"));
        DuelInvite duelInvite=validInvite(player.getUniqueId());
        if(duelInvite!=null&&duelInvite.mode==Mode.TWO_V_TWO)inv.setItem(19,item(Material.DIAMOND_SWORD,"§a§lACCEPT 2v2 CHALLENGE","§7From party: §f"+partyNamesFor(duelInvite.challenger),"§7Either teammate may accept"));
        if(leader!=null){
            if(leader.equals(player.getUniqueId())){inv.setItem(20,item(Material.RED_DYE,"§c§lKICK TEAMMATE","§7Remove §f"+partnerName+" §7from the party"));inv.setItem(24,item(Material.GOLDEN_HELMET,"§6§lMAKE PARTY LEADER","§7Transfer leadership to §f"+partnerName));}
            else inv.setItem(20,item(Material.BARRIER,"§c§lLEAVE PARTY","§7Leave and disband this 2v2 party"));
        }
        inv.setItem(22,item(Material.ARROW,"§fBack to Duels"));
        if(queuedByPlayer.containsKey(player.getUniqueId())||soloTwoQueue.contains(player.getUniqueId()))inv.setItem(26,item(Material.BARRIER,"§c§lLEAVE QUEUE","§7Click to leave your current queue"));
        fill(inv);player.openInventory(inv);
    }

    private void openHeads(Player viewer,HeadAction action,int page){
        List<Player> candidates=headCandidates(viewer,action);
        int pages=Math.max(1,(candidates.size()+44)/45);page=Math.max(0,Math.min(page,pages-1));
        Inventory inv=Bukkit.createInventory(null,54,HEAD_TITLE);
        Map<Integer,UUID> slots=new HashMap<>();int from=page*45,to=Math.min(candidates.size(),from+45);
        for(int index=from;index<to;index++){Player target=candidates.get(index);int slot=index-from;inv.setItem(slot,head(target,action));slots.put(slot,target.getUniqueId());}
        if(page>0)inv.setItem(45,item(Material.ARROW,"§ePrevious page"));
        inv.setItem(49,item(Material.BARRIER,"§cBack","§7Page "+(page+1)+"/"+pages));
        if(page+1<pages)inv.setItem(53,item(Material.ARROW,"§eNext page"));
        headViews.put(viewer.getUniqueId(),new HeadView(action,page,slots));fill(inv);viewer.openInventory(inv);
    }

    private List<Player> headCandidates(Player viewer,HeadAction action){
        if(action==HeadAction.CHALLENGE_2V2){
            UUID ownLeader=partyLeader(viewer.getUniqueId());Set<UUID> seen=new HashSet<>();List<Player> parties=new ArrayList<>();
            for(Player online:Bukkit.getOnlinePlayers()){UUID leader=partyLeader(online.getUniqueId());if(leader==null||leader.equals(ownLeader)||!seen.add(leader)||!hasPreparedPair(leader))continue;Player representative=Bukkit.getPlayer(leader);if(representative!=null)parties.add(representative);}
            parties.sort(Comparator.comparing(p->partyNamesFor(p.getUniqueId()),String.CASE_INSENSITIVE_ORDER));return parties;
        }
        return Bukkit.getOnlinePlayers().stream().filter(p->!p.getUniqueId().equals(viewer.getUniqueId())).filter(p->{
            if(action==HeadAction.INVITE_PARTNER)return partyLeader(p.getUniqueId())==null&&!queuedByPlayer.containsKey(p.getUniqueId())&&!soloTwoQueue.contains(p.getUniqueId());
            return true;
        }).filter(p->!isBusy(p.getUniqueId())&&!plugin.duelHasStoredState(p.getUniqueId())).sorted(Comparator.comparing(Player::getName,String.CASE_INSENSITIVE_ORDER)).collect(java.util.stream.Collectors.toList());
    }

    private ItemStack head(Player target,HeadAction action){
        ItemStack stack=new ItemStack(Material.PLAYER_HEAD);SkullMeta meta=(SkullMeta)stack.getItemMeta();meta.setOwningPlayer(target);
        String verb=switch(action){case INVITE_1V1->"Invite to 1v1";case CHALLENGE_2V2->"Challenge their party";case INVITE_PARTNER->"Invite as your teammate";};
        meta.setDisplayName("§f§l"+(action==HeadAction.CHALLENGE_2V2?partyNamesFor(target.getUniqueId()):target.getName()));meta.setLore(List.of("§d"+verb,"§7Click to select"));stack.setItemMeta(meta);return stack;
    }

    @EventHandler public void onMenu(InventoryClickEvent event){
        if(!(event.getWhoClicked() instanceof Player player))return;String title=event.getView().getTitle();
        if(!title.equals(MAIN_TITLE)&&!title.equals(TWO_TITLE)&&!title.equals(HEAD_TITLE)){DuelMatch match=matchesByPlayer.get(player.getUniqueId());if((preparationsByPlayer.containsKey(player.getUniqueId())||(match!=null&&match.state!=State.FIGHTING)))event.setCancelled(true);return;}
        event.setCancelled(true);if(event.getRawSlot()<0||event.getRawSlot()>=event.getView().getTopInventory().getSize())return;
        if(title.equals(MAIN_TITLE)){
            switch(event.getRawSlot()){
                case 10->queue(player,Mode.ONE_V_ONE);
                case 12->openHeads(player,HeadAction.INVITE_PARTNER,0);
                case 14->openTwo(player);
                case 16->openHeads(player,HeadAction.CHALLENGE_2V2,0);
                case 18->{DuelInvite invite=validInvite(player.getUniqueId());if(invite!=null)accept(player,name(invite.challenger));else openMain(player);}
                case 20->{player.closeInventory();player.sendMessage(PREFIX+"§6Wagered 1v1: §f/wager <player> <amount> §8• §7Example: §f/wager Steve 25k");}
                case 22->openHeads(player,HeadAction.INVITE_1V1,0);
                case 26->{leaveQueue(player.getUniqueId(),true);openMain(player);}
            }return;
        }
        if(title.equals(TWO_TITLE)){
            switch(event.getRawSlot()){
                case 10->openHeads(player,HeadAction.INVITE_PARTNER,0);
                case 12->queue(player,Mode.TWO_V_TWO);
                case 14->queueSoloTwo(player);
                case 16->openHeads(player,HeadAction.CHALLENGE_2V2,0);
                case 18->{PartnerInvite invite=validPartnerInvite(player.getUniqueId());if(invite!=null)acceptPartner(player,name(invite.leader));else openTwo(player);}
                case 19->{DuelInvite invite=validInvite(player.getUniqueId());if(invite!=null&&invite.mode==Mode.TWO_V_TWO)accept(player,name(invite.challenger));else openTwo(player);}
                case 20->{if(partyLeader(player.getUniqueId())!=null&&partyLeader(player.getUniqueId()).equals(player.getUniqueId()))kickPartyMember(player);else leaveParty(player);openTwo(player);}
                case 22->{toggleSaturdayRegistration(player);openTwo(player);}
                case 24->{promotePartyMember(player);openTwo(player);}
                case 26->{leaveQueue(player.getUniqueId(),true);openTwo(player);}
            }return;
        }
        HeadView view=headViews.get(player.getUniqueId());if(view==null)return;int slot=event.getRawSlot();
        if(slot==45){openHeads(player,view.action,view.page-1);return;}if(slot==49){if(view.action==HeadAction.INVITE_1V1)openMain(player);else openTwo(player);return;}if(slot==53){openHeads(player,view.action,view.page+1);return;}
        UUID selected=view.slots.get(slot);Player target=selected==null?null:Bukkit.getPlayer(selected);if(target==null){player.sendMessage(PREFIX+"§cThat player is no longer online.");openHeads(player,view.action,view.page);return;}
        switch(view.action){case INVITE_1V1->invite(player,target,Mode.ONE_V_ONE);case CHALLENGE_2V2->invite(player,target,Mode.TWO_V_TWO);case INVITE_PARTNER->invitePartner(player,target);}player.closeInventory();
    }

    private void invitePartner(Player leader,Player partner){
        if(leader.equals(partner)){leader.sendMessage(PREFIX+"§cYou cannot invite yourself.");return;}
        if(!canCreateDuel(leader)||!canCreateDuel(partner))return;
        if(partyLeader(leader.getUniqueId())!=null){leader.sendMessage(PREFIX+"§cYou already have a 2v2 party. Use §f/2v2 kick §cor §f/2v2 leaveparty §cfirst.");return;}
        if(partyLeader(partner.getUniqueId())!=null){leader.sendMessage(PREFIX+"§cThat player already belongs to a 2v2 party.");return;}
        long expiry=System.currentTimeMillis()+plugin.getConfig().getLong("duels.invite-seconds",60)*1000L;
        partnerInvites.put(partner.getUniqueId(),new PartnerInvite(leader.getUniqueId(),partner.getUniqueId(),expiry));
        leader.sendMessage(PREFIX+"§aTeam invite sent to §f"+partner.getName()+"§a.");
        partner.sendMessage(PREFIX+"§d"+leader.getName()+" §finvited you as their 2v2 teammate. Use §a/2v2 accept "+leader.getName()+" §for §c/2v2 decline§f.");
    }

    private void acceptPartner(Player partner,String leaderName){
        PartnerInvite invite=validPartnerInvite(partner.getUniqueId());
        if(invite==null){partner.sendMessage(PREFIX+"§cYou have no active 2v2 team invite.");return;}
        if(leaderName!=null&&!leaderName.isBlank()&&!name(invite.leader).equalsIgnoreCase(leaderName)){partner.sendMessage(PREFIX+"§cThat player did not send your active team invite.");return;}
        Player leader=Bukkit.getPlayer(invite.leader);if(leader==null){partnerInvites.remove(partner.getUniqueId());partner.sendMessage(PREFIX+"§cThe party leader went offline.");return;}
        if(!canCreateDuel(leader)||!canCreateDuel(partner))return;
        if(partyLeader(leader.getUniqueId())!=null||partyLeader(partner.getUniqueId())!=null){partnerInvites.remove(partner.getUniqueId());partner.sendMessage(PREFIX+"§cOne of you already belongs to a 2v2 party.");return;}
        selectedPartners.put(leader.getUniqueId(),partner.getUniqueId());partnerLeaders.put(partner.getUniqueId(),leader.getUniqueId());partnerInvites.remove(partner.getUniqueId());
        leader.sendMessage(PREFIX+"§a"+partner.getName()+" accepted. Your 2v2 party is ready.");partner.sendMessage(PREFIX+"§aYou joined §f"+leader.getName()+"§a's 2v2 party.");
    }

    private void declinePartner(Player partner){PartnerInvite removed=partnerInvites.remove(partner.getUniqueId());if(removed==null){partner.sendMessage(PREFIX+"§7You have no active team invite.");return;}partner.sendMessage(PREFIX+"§cTeam invite declined.");Player leader=Bukkit.getPlayer(removed.leader);if(leader!=null)leader.sendMessage(PREFIX+"§c"+partner.getName()+" declined your team invite.");}
    private UUID partyLeader(UUID member){if(selectedPartners.containsKey(member))return member;return partnerLeaders.get(member);}
    private UUID partyMate(UUID member){UUID leader=partyLeader(member);if(leader==null)return null;return leader.equals(member)?selectedPartners.get(leader):leader;}
    private List<UUID> partyMembers(UUID member){UUID leader=partyLeader(member);if(leader==null)return List.of();UUID partner=selectedPartners.get(leader);return partner==null?List.of():List.of(leader,partner);}
    private void clearPairing(UUID id){UUID partner=selectedPartners.remove(id);if(partner!=null)partnerLeaders.remove(partner,id);UUID leader=partnerLeaders.remove(id);if(leader!=null)selectedPartners.remove(leader,id);}
    private boolean hasPreparedPair(UUID member){UUID leader=partyLeader(member);if(leader==null)return false;UUID partner=selectedPartners.get(leader);return partner!=null&&leader.equals(partnerLeaders.get(partner))&&Bukkit.getPlayer(leader)!=null&&Bukkit.getPlayer(partner)!=null&&!isBusy(leader)&&!isBusy(partner);}
    private boolean validPartyForStart(DuelParty party,boolean saturdayTournament){
        if(!saturdayTournament)return validParty(party);
        if(party==null||party.mode!=Mode.TWO_V_TWO||party.players.size()!=2)return false;
        for(UUID id:party.players){Player player=Bukkit.getPlayer(id);if(player==null||isBusy(id)||inCombat(id)||plugin.duelHasStoredState(id))return false;}
        UUID leader=partyLeader(party.leader),partner=leader==null?null:selectedPartners.get(leader);return leader!=null&&partner!=null&&party.players.containsAll(List.of(leader,partner));
    }

    private ZoneId saturdayZone(){return ZoneId.of(plugin.getConfig().getString("timezone","Europe/Lisbon"));}
    private void saturdayTick(){
        ZonedDateTime now=ZonedDateTime.now(saturdayZone());DayOfWeek day=DayOfWeek.valueOf(plugin.getConfig().getString("two-v-two-tournament.day","SATURDAY").toUpperCase(Locale.ROOT));int open=plugin.getConfig().getInt("two-v-two-tournament.registration-open-hour",12),start=plugin.getConfig().getInt("two-v-two-tournament.start-hour",16);
        if(saturdayPhase==SaturdayPhase.CLOSED&&now.getDayOfWeek()==day&&now.getHour()>=open&&now.getHour()<start&&!now.toLocalDate().equals(lastSaturdayRun))openSaturdayTournament(false);
        if(saturdayPhase==SaturdayPhase.OPEN&&now.getDayOfWeek()==day&&now.getHour()>=start&&!now.toLocalDate().equals(lastSaturdayRun)){lastSaturdayRun=now.toLocalDate();plugin.duelSetSystemString("last-saturday-2v2",lastSaturdayRun.toString());startSaturdayTournament(null);}
    }
    private String saturdayStatus(){return switch(saturdayPhase){case CLOSED->"§7Closed §8• §fEvery Saturday at §e"+plugin.getConfig().getInt("two-v-two-tournament.start-hour",16)+":00 Portugal time";case OPEN->"§aRegistration open §8• §f"+saturdayRegisteredLeaders.size()+" teams";case RUNNING->"§6Running §8• §f"+saturdayPendingMatches+" live matches";};}
    private void openSaturdayTournament(boolean manual){if(saturdayPhase==SaturdayPhase.RUNNING)return;saturdayPhase=SaturdayPhase.OPEN;saturdayRegisteredLeaders.clear();Bukkit.broadcastMessage(PREFIX+"§b§lSATURDAY 2v2 §8• §fRegistration is open! Form a party and use §e/2v2 tournament join§f.");}
    private void toggleSaturdayRegistration(Player player){if(saturdayPhase!=SaturdayPhase.OPEN){player.sendMessage(PREFIX+"§bSaturday 2v2: "+saturdayStatus());return;}UUID leader=partyLeader(player.getUniqueId());if(leader==null||!hasPreparedPair(leader)){player.sendMessage(PREFIX+"§cCreate a complete party first with §f/2v2 invite <player>§c.");return;}if(saturdayRegisteredLeaders.remove(leader)){message(new DuelParty(Mode.TWO_V_TWO,leader,partyMembers(leader)),"§cYour party left the Saturday 2v2 tournament.");return;}int maximum=plugin.getConfig().getInt("two-v-two-tournament.maximum-teams",8);if(saturdayRegisteredLeaders.size()>=maximum){player.sendMessage(PREFIX+"§cThe Saturday tournament is full.");return;}saturdayRegisteredLeaders.add(leader);message(new DuelParty(Mode.TWO_V_TWO,leader,partyMembers(leader)),"§aYour party joined the Saturday 2v2 tournament. §7("+saturdayRegisteredLeaders.size()+"/"+maximum+" teams)");}
    private void leaveSaturdayTournament(Player player){UUID leader=partyLeader(player.getUniqueId());if(leader!=null&&saturdayPhase==SaturdayPhase.OPEN&&saturdayRegisteredLeaders.remove(leader))message(new DuelParty(Mode.TWO_V_TWO,leader,partyMembers(leader)),"§cYour party left the Saturday tournament.");else player.sendMessage(PREFIX+"§7Your party is not registered.");}
    private void startSaturdayTournament(CommandSender sender){
        if(saturdayPhase==SaturdayPhase.RUNNING){if(sender!=null)sender.sendMessage(PREFIX+"§cThe Saturday tournament is already running.");return;}if(!plugin.duelSystemAvailable()){if(sender!=null)sender.sendMessage(PREFIX+"§cWait until the weekly tournament finishes.");return;}
        List<DuelParty> teams=new ArrayList<>();for(UUID leader:new ArrayList<>(saturdayRegisteredLeaders)){List<UUID> members=partyMembers(leader);DuelParty party=members.size()==2?new DuelParty(Mode.TWO_V_TWO,leader,members):null;if(validPartyForStart(party,true))teams.add(party);}
        int minimum=plugin.getConfig().getInt("two-v-two-tournament.minimum-teams",2);if(teams.size()<minimum){saturdayPhase=SaturdayPhase.CLOSED;Bukkit.broadcastMessage(PREFIX+"§cSaturday 2v2 cancelled: need at least "+minimum+" complete online teams.");return;}
        saturdayPhase=SaturdayPhase.RUNNING;saturdayRegisteredLeaders.clear();Collections.shuffle(teams);for(DuelParty team:teams)leaveQueuesFor(team.players);Bukkit.broadcastMessage(PREFIX+"§b§lSATURDAY 2v2 STARTING §8• §f"+teams.size()+" teams");startSaturdayRoundWhenReady(teams,0);
    }
    private void startSaturdayRoundWhenReady(List<DuelParty> teams,int attempt){
        if(saturdayPhase!=SaturdayPhase.RUNNING)return;List<DuelParty> valid=teams.stream().filter(t->t.players.stream().allMatch(id->Bukkit.getPlayer(id)!=null)).toList();if(valid.size()<2){if(valid.size()==1)finishSaturdayTournament(valid.getFirst());else stopSaturdayTournament("No complete teams remained online.");return;}
        boolean ready=valid.stream().flatMap(t->t.players.stream()).allMatch(id->!restoring.contains(id)&&!plugin.duelHasStoredState(id));if(!ready){if(attempt<120){Bukkit.getScheduler().runTaskLater(plugin,()->startSaturdayRoundWhenReady(valid,attempt+1),10L);return;}stopSaturdayTournament("Inventories could not be prepared for the next round.");return;}
        List<DuelParty> shuffled=new ArrayList<>(valid);Collections.shuffle(shuffled);saturdayRoundWinners.clear();if(shuffled.size()%2==1){DuelParty bye=shuffled.removeLast();saturdayRoundWinners.add(bye);message(bye,"§aYour party received a bye to the next round.");}saturdayPendingMatches=shuffled.size()/2;for(int i=0;i<shuffled.size();i+=2)begin(shuffled.get(i),shuffled.get(i+1),true);
    }
    private void saturdayMatchFinished(DuelMatch match,DuelParty winners,String reason){
        saturdayMatches.remove(match);if(saturdayPhase!=SaturdayPhase.RUNNING)return;if(winners==null){stopSaturdayTournament("A match ended without a winning team: "+reason);return;}DuelParty losers=winners==match.first?match.second:match.first;boolean finalMatch=saturdayPendingMatches==1&&saturdayRoundWinners.isEmpty();int placement=plugin.getConfig().getInt(finalMatch?"two-v-two-tournament.rewards.finalist-per-player":"two-v-two-tournament.rewards.participant-per-player",finalMatch?600:100);for(UUID id:losers.players)plugin.duelAddGlory(id,placement,finalMatch?"Saturday 2v2 Finalist":"Saturday 2v2 Participant");saturdayRoundWinners.add(winners);Bukkit.broadcastMessage(PREFIX+"§b"+teamNames(winners)+" §fadvance in the Saturday 2v2 tournament!");saturdayPendingMatches=Math.max(0,saturdayPendingMatches-1);if(saturdayPendingMatches==0){List<DuelParty> next=new ArrayList<>(saturdayRoundWinners);Bukkit.getScheduler().runTaskLater(plugin,()->startSaturdayRoundWhenReady(next,0),100L);}
    }
    private void finishSaturdayTournament(DuelParty champion){saturdayPhase=SaturdayPhase.CLOSED;saturdayMatches.clear();int reward=plugin.getConfig().getInt("two-v-two-tournament.rewards.champion-per-player",1000);for(UUID id:champion.players){plugin.duelAddGlory(id,reward,"Saturday 2v2 Champion");Player p=Bukkit.getPlayer(id);if(p!=null)p.sendTitle("§b§l2v2 CHAMPIONS","§d+"+reward+" Glory",10,100,20);}Bukkit.broadcastMessage(PREFIX+"§b§l"+teamNames(champion)+" WON THE SATURDAY 2v2 TOURNAMENT!");saturdayRoundWinners.clear();saturdayPendingMatches=0;}
    private void stopSaturdayTournament(String reason){if(saturdayPhase==SaturdayPhase.CLOSED)return;saturdayPhase=SaturdayPhase.CLOSED;for(DuelMatch match:new HashSet<>(saturdayMatches))finish(match,null,"Saturday tournament stopped",false);saturdayMatches.clear();saturdayRoundWinners.clear();saturdayPendingMatches=0;saturdayRegisteredLeaders.clear();Bukkit.broadcastMessage(PREFIX+"§cSaturday 2v2 stopped: "+reason);}
    private void kickPartyMember(Player actor){UUID leader=partyLeader(actor.getUniqueId());if(leader==null){actor.sendMessage(PREFIX+"§7You do not have a 2v2 party.");return;}if(!leader.equals(actor.getUniqueId())){actor.sendMessage(PREFIX+"§cOnly the party leader can kick the teammate.");return;}UUID partner=selectedPartners.get(leader);leaveQueuesFor(partyMembers(leader));clearPairing(leader);actor.sendMessage(PREFIX+"§cThe teammate was removed from your 2v2 party.");Player removed=Bukkit.getPlayer(partner);if(removed!=null)removed.sendMessage(PREFIX+"§cYou were removed from "+actor.getName()+"'s 2v2 party.");}
    private void promotePartyMember(Player actor){UUID leader=partyLeader(actor.getUniqueId());if(leader==null){actor.sendMessage(PREFIX+"§7You do not have a 2v2 party.");return;}if(!leader.equals(actor.getUniqueId())){actor.sendMessage(PREFIX+"§cOnly the party leader can transfer leadership.");return;}UUID partner=selectedPartners.remove(leader);if(partner==null)return;partnerLeaders.remove(partner);selectedPartners.put(partner,leader);partnerLeaders.put(leader,partner);actor.sendMessage(PREFIX+"§e"+name(partner)+" is now the 2v2 party leader.");Player promoted=Bukkit.getPlayer(partner);if(promoted!=null)promoted.sendMessage(PREFIX+"§aYou are now the 2v2 party leader.");}
    private void leaveParty(Player player){UUID leader=partyLeader(player.getUniqueId());if(leader==null){player.sendMessage(PREFIX+"§7You do not have a 2v2 party.");return;}List<UUID> members=partyMembers(leader);leaveQueuesFor(members);clearPairing(player.getUniqueId());message(new DuelParty(Mode.TWO_V_TWO,leader,members),"§cThe 2v2 party was disbanded.");}

    private void queue(Player leader,Mode mode){
        if(!canCreateDuel(leader))return;DuelParty party=partyFor(leader,mode);if(party==null)return;
        leaveQueuesFor(party.players);soloTwoQueue.removeAll(party.players);Deque<DuelParty> queue=mode==Mode.ONE_V_ONE?queue1:queue2;DuelParty opponent=null;
        for(Iterator<DuelParty> it=queue.iterator();it.hasNext();){DuelParty candidate=it.next();if(validParty(candidate)&&Collections.disjoint(candidate.players,party.players)){opponent=candidate;it.remove();removeQueued(candidate);break;}it.remove();removeQueued(candidate);}
        if(opponent==null&&mode==Mode.TWO_V_TWO){
            List<UUID> solos=takeReadySolos(2);
            if(solos.size()==2)opponent=randomParty(solos);
            else returnSolos(solos);
        }
        if(opponent==null){queue.addLast(party);for(UUID id:party.players)queuedByPlayer.put(id,party);message(party,"§aQueued for §f"+mode.label+"§a. Use §f/duel leave §ato cancel.");}
        else begin(opponent,party);
    }

    private void queueSoloTwo(Player player){
        if(!canCreateDuel(player))return;
        if(partyLeader(player.getUniqueId())!=null){player.sendMessage(PREFIX+"§cLeave your current party with §f/2v2 leaveparty §cbefore joining the random queue.");return;}
        leaveQueue(player.getUniqueId(),false);soloTwoQueue.remove(player.getUniqueId());soloTwoQueue.addLast(player.getUniqueId());
        player.sendMessage(PREFIX+"§aJoined the random 2v2 queue. The server will choose your teammate and opponents.");
        tryStartSoloTwo();
    }

    private void tryStartSoloTwo(){
        List<UUID> ready=takeReadySolos(4);
        if(ready.size()>=2){
            DuelParty prepared=null;
            for(Iterator<DuelParty> it=queue2.iterator();it.hasNext();){DuelParty candidate=it.next();if(validParty(candidate)&&Collections.disjoint(candidate.players,ready)){prepared=candidate;it.remove();removeQueued(candidate);break;}it.remove();removeQueued(candidate);}
            if(prepared!=null){
                DuelParty solos=randomParty(ready.subList(0,2));
                returnSolos(ready.subList(2,ready.size()));
                begin(prepared,solos);
                return;
            }
        }
        if(ready.size()<4){returnSolos(ready);return;}
        DuelParty first=randomParty(ready.subList(0,2));
        DuelParty second=randomParty(ready.subList(2,4));
        begin(first,second);
    }

    private List<UUID> takeReadySolos(int maximum){
        List<UUID> ready=new ArrayList<>();
        while(!soloTwoQueue.isEmpty()&&ready.size()<maximum){
            UUID id=soloTwoQueue.removeFirst();Player player=Bukkit.getPlayer(id);
            if(player!=null&&!isBusy(id)&&!inCombat(id)&&!plugin.duelHasStoredState(id)&&plugin.duelSystemAvailable()&&!ready.contains(id)&&partyLeader(id)==null)ready.add(id);
        }
        return ready;
    }

    private void returnSolos(Collection<UUID> players){for(UUID id:players)if(!soloTwoQueue.contains(id))soloTwoQueue.addLast(id);}
    private DuelParty randomParty(List<UUID> players){List<UUID> pair=List.copyOf(players);return new DuelParty(Mode.TWO_V_TWO,pair.getFirst(),pair,true);}

    private void invite(Player challenger,Player target,Mode mode){invite(challenger,target,mode,0);}
    private void invite(Player challenger,Player target,Mode mode,long wager){
        if(!canCreateDuel(challenger)||challenger.equals(target)){if(challenger.equals(target))challenger.sendMessage(PREFIX+"§cYou cannot duel yourself.");return;}
        DuelParty party=partyFor(challenger,mode);if(party==null)return;
        if(isBusy(target.getUniqueId())){challenger.sendMessage(PREFIX+"§cThat player is already busy.");return;}
        if(mode==Mode.TWO_V_TWO&&!hasPreparedPair(target.getUniqueId())){challenger.sendMessage(PREFIX+"§cThat player must prepare a 2v2 party first with §f/2v2§c.");return;}
        long expiry=System.currentTimeMillis()+plugin.getConfig().getLong("duels.invite-seconds",60)*1000L;
        DuelParty targetParty=mode==Mode.TWO_V_TWO?partyFor(target,mode):null;if(mode==Mode.TWO_V_TWO&&targetParty==null)return;
        Collection<UUID> recipients=targetParty==null?List.of(target.getUniqueId()):targetParty.players;for(UUID recipient:recipients)invites.put(recipient,new DuelInvite(challenger.getUniqueId(),recipient,mode,expiry,wager));
        challenger.sendMessage(PREFIX+"§aSent a §f"+mode.label+" §ainvite to §f"+target.getName()+(wager>0?" §awith a §e$"+money(wager)+" §astake.":"§a."));
        if(mode==Mode.ONE_V_ONE)target.sendMessage(PREFIX+"§d"+challenger.getName()+" §finvited you to a §d1v1 duel"+(wager>0?" §fwith a §e$"+money(wager)+" §fstake each":"")+"§f. Use §a/duel accept "+challenger.getName()+" §for §c/duel decline§f.");
        else for(Player recipient:online(recipients))recipient.sendMessage(PREFIX+"§d"+partyNamesFor(challenger.getUniqueId())+" §finvited your party to a §d2v2 duel§f. Either teammate can use §a/2v2 accept§f.");
    }

    private void accept(Player target,String challengerName){
        DuelInvite invite=validInvite(target.getUniqueId());if(invite==null){target.sendMessage(PREFIX+"§cYou have no active duel invite.");return;}
        if(challengerName!=null&&!challengerName.isBlank()&&!name(invite.challenger).equalsIgnoreCase(challengerName)){target.sendMessage(PREFIX+"§cThat player did not send your active invite.");return;}
        Player challenger=Bukkit.getPlayer(invite.challenger);if(challenger==null){invites.remove(target.getUniqueId());target.sendMessage(PREFIX+"§cThe challenger went offline.");return;}
        if(!canCreateDuel(target)||!canCreateDuel(challenger))return;
        DuelParty first=partyFor(challenger,invite.mode),second=partyFor(target,invite.mode);if(first==null||second==null)return;
        if(!Collections.disjoint(first.players,second.players)){target.sendMessage(PREFIX+"§cBoth sides contain the same player.");return;}
        Wager wager=null;
        if(invite.wager>0){
            if(plugin.duelMoneyBalance(challenger)<invite.wager||plugin.duelMoneyBalance(target)<invite.wager){target.sendMessage(PREFIX+"§cBoth players need at least §f$"+money(invite.wager)+"§c.");challenger.sendMessage(PREFIX+"§cThe wager could not start because one balance is too low.");return;}
            if(!plugin.duelTakeMoney(challenger,invite.wager)){target.sendMessage(PREFIX+"§cThe wager payment failed.");return;}
            if(!plugin.duelTakeMoney(target,invite.wager)){plugin.duelGiveMoney(challenger.getUniqueId(),invite.wager);target.sendMessage(PREFIX+"§cThe wager payment failed. No money was kept.");return;}
            wager=new Wager(invite.wager,challenger.getUniqueId(),target.getUniqueId());
            message(first,"§6Wager locked: §e$"+money(invite.wager)+" §6each §8• §e$"+money(invite.wager*2)+" §6pot");message(second,"§6Wager locked: §e$"+money(invite.wager)+" §6each §8• §e$"+money(invite.wager*2)+" §6pot");
        }
        for(UUID id:second.players)invites.remove(id);leaveQueuesFor(first.players);leaveQueuesFor(second.players);begin(first,second,false,wager);
    }

    private void decline(Player target){DuelInvite removed=invites.remove(target.getUniqueId());if(removed==null){target.sendMessage(PREFIX+"§7You have no active invite.");return;}if(removed.mode==Mode.TWO_V_TWO)for(UUID member:partyMembers(target.getUniqueId()))invites.remove(member);target.sendMessage(PREFIX+"§cDuel invite declined.");Player challenger=Bukkit.getPlayer(removed.challenger);if(challenger!=null)challenger.sendMessage(PREFIX+"§c"+target.getName()+" declined your duel invite.");}

    private DuelParty partyFor(Player player,Mode mode){
        if(mode==Mode.ONE_V_ONE)return new DuelParty(mode,player.getUniqueId(),List.of(player.getUniqueId()));
        UUID leader=partyLeader(player.getUniqueId());if(leader==null){player.sendMessage(PREFIX+"§cInvite a teammate first with §f/2v2 invite <player>§c, then wait for them to accept.");return null;}
        UUID partner=selectedPartners.get(leader);Player teammate=partner==null?null:Bukkit.getPlayer(partner);
        if(teammate==null||Bukkit.getPlayer(leader)==null||!leader.equals(partnerLeaders.get(partner))){player.sendMessage(PREFIX+"§cBoth party members must be online and ready.");return null;}
        if(isBusy(partner)){player.sendMessage(PREFIX+"§cYour selected teammate is already busy.");return null;}
        return new DuelParty(mode,leader,List.of(leader,partner));
    }

    private boolean canCreateDuel(Player player){
        if(!plugin.duelSystemAvailable()||saturdayPhase==SaturdayPhase.RUNNING){player.sendMessage(PREFIX+"§cDuels pause while a tournament is running.");return false;}
        if(inCombat(player.getUniqueId())){player.closeInventory();player.sendMessage(PREFIX+"§cYou cannot enter a 1v1 or 2v2 while you are in combat.");return false;}
        if(isBusy(player.getUniqueId())){player.sendMessage(PREFIX+"§cYou are already preparing or fighting a duel.");return false;}
        if(plugin.duelHasStoredState(player.getUniqueId())){player.sendMessage(PREFIX+"§cYour previous saved inventory must be restored first. Rejoin or ask an admin for recovery.");plugin.duelRestoreWhenReady(player.getUniqueId());return false;}
        return true;
    }

    private boolean isBusy(UUID id){return matchesByPlayer.containsKey(id)||preparationsByPlayer.containsKey(id)||restoring.contains(id)||spectatorMatches.containsKey(id);}
    private boolean validParty(DuelParty party){if(party==null||!plugin.duelSystemAvailable()||saturdayPhase==SaturdayPhase.RUNNING)return false;for(UUID id:party.players){Player p=Bukkit.getPlayer(id);if(p==null||isBusy(id)||inCombat(id)||plugin.duelHasStoredState(id))return false;}if(party.mode==Mode.TWO_V_TWO&&!party.randomTeam){UUID leader=partyLeader(party.leader),partner=leader==null?null:selectedPartners.get(leader);return leader!=null&&partner!=null&&leader.equals(partnerLeaders.get(partner))&&party.players.containsAll(List.of(leader,partner));}return party.players.size()==party.mode.teamSize;}

    private boolean inCombat(UUID id){
        try{
            Class<?> variables=Class.forName("ch.njol.skript.variables.Variables");
            java.lang.reflect.Method lookup=variables.getMethod("getVariable",String.class,Event.class,boolean.class);
            return lookup.invoke(null,"mmcombat::tagged::"+id,null,false)!=null;
        }catch(ReflectiveOperationException error){
            plugin.getLogger().log(Level.WARNING,"Could not read the existing Skript combat tag; duel entry will use the normal command guard.",error);
            return false;
        }
    }

    private void begin(DuelParty first,DuelParty second){begin(first,second,false,null);}
    private void begin(DuelParty first,DuelParty second,boolean saturdayTournament){begin(first,second,saturdayTournament,null);}
    private void begin(DuelParty first,DuelParty second,boolean saturdayTournament,Wager wager){
        if(!validPartyForStart(first,saturdayTournament)||!validPartyForStart(second,saturdayTournament)){message(first,"§cThe duel could not start because a player is no longer ready.");message(second,"§cThe duel could not start because a player is no longer ready.");refundWager(wager);if(saturdayTournament)stopSaturdayTournament("A registered team was no longer ready.");return;}
        try{plugin.duelValidateKit();}catch(Exception error){message(first,"§cThe duel kit is invalid. Contact an admin.");message(second,"§cThe duel kit is invalid. Contact an admin.");refundWager(wager);plugin.getLogger().log(Level.SEVERE,"Duel kit validation failed",error);return;}
        Player leader=Bukkit.getPlayer(first.leader);World template=plugin.duelLoadTemplate(leader==null?Bukkit.getConsoleSender():leader);if(template==null){refundWager(wager);return;}
        for(Player visitor:new ArrayList<>(template.getPlayers()))plugin.duelTeleport(visitor,plugin.duelMainSpawn());
        template.save(true);File source=template.getWorldFolder();long id=System.currentTimeMillis()*1000L+ids.incrementAndGet();String worldName=(saturdayTournament?"mt_saturday_":"mt_duel_")+id;
        DuelPreparation prep=new DuelPreparation(id,first,second,worldName,saturdayTournament,wager);for(UUID player:prep.players())preparationsByPlayer.put(player,prep);
        List<UUID> snapshotted=new ArrayList<>();
        for(UUID uuid:prep.players()){
            Player player=Bukkit.getPlayer(uuid);if(player==null||plugin.duelHasStoredState(uuid)||!plugin.duelSnapshot(player)){abortPreparation(prep,"Could not safely store every inventory.",true);return;}snapshotted.add(uuid);
        }
        if(!plugin.backupSnapshots("duel",id)){abortPreparation(prep,"Could not create the inventory recovery copy.",true);return;}
        for(UUID uuid:prep.players()){Player player=Bukkit.getPlayer(uuid);if(player==null||!plugin.duelHoldPlayer(player)){abortPreparation(prep,"A player could not be moved into safe preparation.",true);return;}player.sendActionBar(Component.text("§dDuel arena loading §8• §aYour original inventory is safely stored"));}
        message(first,"§eCreating a private "+first.mode.label+" arena...");message(second,"§eCreating a private "+second.mode.label+" arena...");
        Bukkit.getScheduler().runTaskAsynchronously(plugin,()->{
            try{plugin.duelCopyWorld(source.toPath(),source.toPath().resolveSibling(worldName));}
            catch(Exception error){plugin.getLogger().log(Level.SEVERE,"Duel arena clone failed",error);Bukkit.getScheduler().runTask(plugin,()->abortPreparation(prep,"Could not clone the duel arena.",true));return;}
            Bukkit.getScheduler().runTask(plugin,()->{
                if(prep.cancelled||!prep.players().stream().allMatch(uuid->preparationsByPlayer.get(uuid)==prep)){plugin.duelDeleteArena(worldName);return;}
                World arena=plugin.duelLoadArena(worldName);if(arena==null){abortPreparation(prep,"The copied arena did not contain the map.",true);return;}
                startPrepared(prep,arena);
            });
        });
    }

    private void startPrepared(DuelPreparation prep,World world){
        for(UUID id:prep.players())preparationsByPlayer.remove(id,prep);
        DuelMatch match=new DuelMatch(prep.first,prep.second,world,prep.saturdayTournament,prep.wager);for(UUID id:match.players())matchesByPlayer.put(id,match);if(match.saturdayTournament)saturdayMatches.add(match);
        match.bar=Bukkit.createBossBar(match.saturdayTournament?"§6Saturday 2v2 Tournament §8• §ePrivate Arena":"§d"+match.mode.label+" Duel §8• §ePrivate Arena",match.saturdayTournament?BarColor.YELLOW:BarColor.PURPLE,BarStyle.SEGMENTED_10);
        for(int side=0;side<2;side++){
            List<UUID> team=side==0?match.first.players:match.second.players;
            for(int index=0;index<team.size();index++){
                Player player=Bukkit.getPlayer(team.get(index));if(player==null){finish(match,null,"A fighter disconnected during arena loading.",false);return;}
                Location spawn=teamSpawn(world,side==0,index,team.size());spawn.getChunk().load(true);if(!plugin.duelPrepareFighter(player,spawn)){finish(match,null,"A fighter could not enter the private arena.",false);return;}match.bar.addPlayer(player);
            }
        }
        match.countdownTask=new BukkitRunnable(){int count=3;@Override public void run(){
            if(match.state==State.FINISHED){cancel();return;}if(!allPresent(match)){finish(match,null,"A fighter disconnected during the countdown.",false);cancel();return;}
            if(count>0){match.bar.setTitle("§d"+match.mode.label+" §8• §fFight starts in §e"+count);match.bar.setProgress(count/3.0);for(Player p:online(match.players()))p.sendTitle("§e§l"+count,"§7Get ready",0,22,0);count--;return;}
            match.state=State.FIGHTING;match.startedAt=System.currentTimeMillis();for(Player p:online(match.players()))plugin.duelUnlock(p);match.bar.setColor(BarColor.GREEN);match.bar.setStyle(BarStyle.SOLID);Bukkit.broadcastMessage(PREFIX+"§f"+teamNames(match.first)+" §7vs §f"+teamNames(match.second)+" §7— "+(match.saturdayTournament?"Saturday 2v2 Tournament":match.mode.label));startTimer(match);cancel();
        }}.runTaskTimer(plugin,20L,20L);
    }

    private Location teamSpawn(World world,boolean first,int index,int size){Location base=plugin.duelArenaSpawn(world,first);if(size==1)return base;return base.clone().add(index==0?-.20:.20,0,0);}

    private void startTimer(DuelMatch match){match.timerTask=new BukkitRunnable(){int seconds=0;@Override public void run(){
        if(match.state!=State.FIGHTING){cancel();return;}seconds++;for(UUID id:new ArrayList<>(match.alivePlayers())){Player p=Bukkit.getPlayer(id);if(p==null){eliminate(match,id,null,"disconnect");if(match.state==State.FINISHED){cancel();return;}}else if(p.getWorld()!=match.world){finish(match,null,"A fighter left their private arena unexpectedly.",false);cancel();return;}}
        int shrink=plugin.getConfig().getInt("arena.shrink-after-seconds",300),forced=plugin.getConfig().getInt("arena.forced-damage-after-seconds",420);
        if(seconds==shrink){match.world.getWorldBorder().setSize(plugin.getConfig().getDouble("arena.final-border-size",3),plugin.getConfig().getInt("arena.shrink-duration-seconds",120));match.bar.setColor(BarColor.RED);message(match.first,"§cSudden Death! The border is shrinking.");message(match.second,"§cSudden Death! The border is shrinking.");}
        if(seconds==forced){match.world.getWorldBorder().setDamageAmount(6);message(match.first,"§4The border is fully closed! §cSudden Death now deals triple damage.");message(match.second,"§4The border is fully closed! §cSudden Death now deals triple damage.");}
        if(seconds>=forced&&seconds%2==0)for(Player player:online(match.alivePlayers()))player.damage(12);
        String stage=seconds<shrink?"Border shrinks in "+format(shrink-seconds):seconds<forced?"Border shrinking • "+format(forced-seconds):"Sudden Death • 3x damage";match.bar.setTitle("§d"+match.mode.label+" §8• §f"+stage);match.bar.setProgress(seconds<shrink?Math.max(0,1.0-seconds/(double)Math.max(1,shrink)):Math.max(0,1.0-(seconds-shrink)/(double)Math.max(1,forced-shrink)));
        if(seconds>plugin.getConfig().getInt("duels.maximum-match-seconds",1200)){finish(match,null,"Safety timeout reached.",false);cancel();}
    }}.runTaskTimer(plugin,20L,20L);}

    private void eliminate(DuelMatch match,UUID loser,Player killer,String reason){
        if(match.state==State.FINISHED)return;boolean first=match.aliveFirst.remove(loser);boolean second=match.aliveSecond.remove(loser);if(!first&&!second)return;
        matchesByPlayer.remove(loser,match);restoring.add(loser);plugin.duelMarkRestore(loser,"Duel "+reason);Player lost=Bukkit.getPlayer(loser);if(lost!=null)plugin.duelClearBypass(lost);
        if(killer!=null&&match.isOpponent(loser,killer.getUniqueId()))plugin.duelPlayKillEffect(killer,lost==null?killer.getLocation():lost.getLocation());switchSpectatorsFrom(match,loser);
        if(match.aliveFirst.isEmpty()||match.aliveSecond.isEmpty()){DuelParty winners=match.aliveFirst.isEmpty()?match.second:match.first;finish(match,winners,"defeat",true);}
        else{message(match.first,"§c"+name(loser)+" was eliminated.");message(match.second,"§c"+name(loser)+" was eliminated.");trackRestore(loser,0);}
    }

    private void finish(DuelMatch match,DuelParty winners,String reason,boolean reward){
        if(match.state==State.FINISHED)return;match.state=State.FINISHED;returnSpectators(match,"The match ended. Your inventory was restored.");if(match.countdownTask!=null)match.countdownTask.cancel();if(match.timerTask!=null)match.timerTask.cancel();if(match.bar!=null)match.bar.removeAll();
        for(UUID id:match.players()){matchesByPlayer.remove(id,match);restoring.add(id);plugin.duelMarkRestore(id,"Duel ended: "+reason);Player player=Bukkit.getPlayer(id);if(player!=null){player.setInvulnerable(true);plugin.duelClearBypass(player);}}
        if(match.saturdayTournament){saturdayMatchFinished(match,winners,reason);}
        else if(reward&&winners!=null){int configured=plugin.getConfig().getInt("duels.rewards."+winners.mode.label,winners.mode.reward);for(UUID id:winners.players){int granted=plugin.awardDuelGlory(id,configured,winners.mode.label+" Duel Win");Player p=Bukkit.getPlayer(id);if(p!=null){p.sendTitle("§a§lVICTORY","§d+"+granted+" Glory",5,50,10);if(granted<configured)p.sendMessage(PREFIX+"§eDaily duel limit: §d"+plugin.duelDailyEarned(id)+"§7/§d"+plugin.duelDailyCap()+" Glory§e.");}}Bukkit.broadcastMessage(PREFIX+"§d"+teamNames(winners)+" §fwon a §d"+match.mode.label+" duel§f!");}
        else{message(match.first,"§cDuel stopped: "+reason);message(match.second,"§cDuel stopped: "+reason);}
        if(reward&&winners!=null)payWager(match.wager,winners.leader);else refundWager(match.wager);
        Bukkit.getScheduler().runTaskLater(plugin,()->{for(UUID id:match.players())trackRestore(id,0);},5L);
        Bukkit.getScheduler().runTaskLater(plugin,()->{plugin.duelCleanupWorld(match.world);plugin.duelReloadTemplate();},80L);
    }

    private void abortPreparation(DuelPreparation prep,String reason,boolean notify){
        if(prep.cancelled)return;prep.cancelled=true;refundWager(prep.wager);for(UUID id:prep.players()){preparationsByPlayer.remove(id,prep);restoring.add(id);plugin.duelMarkRestore(id,"Duel preparation stopped");Player p=Bukkit.getPlayer(id);if(p!=null){plugin.duelClearBypass(p);trackRestore(id,0);if(notify)p.sendMessage(PREFIX+"§cDuel stopped safely: "+reason);}}plugin.duelDeleteArena(prep.worldName);plugin.duelReloadTemplate();if(prep.saturdayTournament)stopSaturdayTournament(reason);
    }

    private void trackRestore(UUID id,int attempt){
        if(!plugin.duelHasStoredState(id)){restoring.remove(id);return;}
        if(attempt==0)plugin.duelRestoreWhenReady(id);
        if(attempt<240)Bukkit.getScheduler().runTaskLater(plugin,()->trackRestore(id,attempt+1),10L);
    }

    @EventHandler(priority=EventPriority.HIGHEST) public void onDeath(PlayerDeathEvent event){DuelMatch match=matchesByPlayer.get(event.getEntity().getUniqueId());if(match==null||match.state!=State.FIGHTING)return;UUID id=event.getEntity().getUniqueId();recentDuelDeaths.add(id);Bukkit.getScheduler().runTask(plugin,()->recentDuelDeaths.remove(id));event.setKeepInventory(true);event.setKeepLevel(true);event.getDrops().clear();event.setDroppedExp(0);event.deathMessage(null);Player killer=event.getEntity().getKiller();eliminate(match,id,killer,"defeat");Bukkit.getScheduler().runTaskLater(plugin,()->{Player eliminated=Bukkit.getPlayer(id);if(eliminated!=null&&eliminated.isDead())eliminated.spigot().respawn();},2L);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=false) public void onDamage(EntityDamageEvent event){if(!(event.getEntity() instanceof Player player))return;DuelMatch match=matchesByPlayer.get(player.getUniqueId());if(match!=null&&match.state!=State.FIGHTING)event.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=false) public void onDamageBy(EntityDamageByEntityEvent event){if(!(event.getEntity() instanceof Player victim))return;DuelMatch match=matchesByPlayer.get(victim.getUniqueId());if(match==null)return;Player attacker=attacker(event);boolean blocked=match.state!=State.FIGHTING||attacker==null||matchesByPlayer.get(attacker.getUniqueId())!=match||match.sameTeam(victim.getUniqueId(),attacker.getUniqueId());event.setCancelled(blocked);}
    @EventHandler public void onMove(PlayerMoveEvent event){DuelMatch match=matchesByPlayer.get(event.getPlayer().getUniqueId());if(match!=null&&match.state==State.COUNTDOWN&&event.getTo()!=null&&(event.getFrom().getX()!=event.getTo().getX()||event.getFrom().getZ()!=event.getTo().getZ()))event.setTo(event.getFrom());}
    @EventHandler public void onQuit(PlayerQuitEvent event){UUID id=event.getPlayer().getUniqueId();if(spectatorMatches.containsKey(id)){stopSpectating(event.getPlayer(),"");return;}leaveQueue(id,false);disbandPartyOnQuit(id);invites.remove(id);invites.entrySet().removeIf(e->e.getValue().challenger.equals(id));partnerInvites.remove(id);partnerInvites.entrySet().removeIf(e->e.getValue().leader.equals(id));DuelPreparation prep=preparationsByPlayer.get(id);if(prep!=null){abortPreparation(prep,"A fighter disconnected.",true);return;}DuelMatch match=matchesByPlayer.get(id);if(match!=null)eliminate(match,id,null,"disconnect");}
    @EventHandler public void onJoin(PlayerJoinEvent event){UUID id=event.getPlayer().getUniqueId();if(restoring.contains(id)||plugin.duelHasStoredState(id)){restoring.add(id);trackRestore(id,0);}}
    @EventHandler public void onTeleport(PlayerTeleportEvent event){UUID id=event.getPlayer().getUniqueId();DuelMatch watched=spectatorMatches.get(id);if(watched!=null&&(event.getTo()==null||event.getTo().getWorld()!=watched.world)){event.setCancelled(true);event.getPlayer().sendMessage(PREFIX+"§cUse /unwatch to leave spectator mode.");return;}DuelMatch match=matchesByPlayer.get(id);if(match!=null&&(event.getTo()==null||event.getTo().getWorld()!=match.world)){event.setCancelled(true);event.getPlayer().sendMessage(PREFIX+"§cYou cannot leave the private arena during a duel.");}else if(preparationsByPlayer.containsKey(id)&&event.getCause()!=PlayerTeleportEvent.TeleportCause.PLUGIN)event.setCancelled(true);}
    @EventHandler public void onInteract(PlayerInteractEvent event){if(frozen(event.getPlayer().getUniqueId()))event.setCancelled(true);}
    @EventHandler public void onInteractEntity(PlayerInteractEntityEvent event){if(frozen(event.getPlayer().getUniqueId()))event.setCancelled(true);}
    @EventHandler public void onSwap(PlayerSwapHandItemsEvent event){if(frozen(event.getPlayer().getUniqueId()))event.setCancelled(true);}
    @EventHandler public void onDrag(InventoryDragEvent event){if(event.getWhoClicked() instanceof Player player&&frozen(player.getUniqueId()))event.setCancelled(true);}
    @EventHandler public void onDrop(PlayerDropItemEvent event){if(frozen(event.getPlayer().getUniqueId()))event.setCancelled(true);}
    @EventHandler public void onPickup(PlayerAttemptPickupItemEvent event){if(frozen(event.getPlayer().getUniqueId()))event.setCancelled(true);}
    @EventHandler public void onBreak(BlockBreakEvent event){if(isInMatch(event.getPlayer().getUniqueId()))event.setCancelled(true);}
    @EventHandler public void onPlace(BlockPlaceEvent event){if(isInMatch(event.getPlayer().getUniqueId()))event.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGHEST) public void onCommand(PlayerCommandPreprocessEvent event){if(!isInMatch(event.getPlayer().getUniqueId()))return;String command=event.getMessage().toLowerCase(Locale.ROOT).split(" ")[0];if(!Set.of("/msg","/tell","/reply","/r").contains(command)){event.setCancelled(true);event.getPlayer().sendMessage(PREFIX+"§cCommands are disabled during a duel.");}}

    private boolean frozen(UUID id){DuelMatch match=matchesByPlayer.get(id);return restoring.contains(id)||preparationsByPlayer.containsKey(id)||(match!=null&&match.state!=State.FIGHTING);}
    boolean isActivelyProtected(UUID id){return matchesByPlayer.containsKey(id)||preparationsByPlayer.containsKey(id)||spectatorMatches.containsKey(id);}
    private Player attacker(EntityDamageByEntityEvent event){if(event.getDamager() instanceof Player p)return p;if(event.getDamager() instanceof Projectile projectile&&projectile.getShooter() instanceof Player p)return p;return null;}
    private boolean allPresent(DuelMatch match){return match.players().stream().allMatch(id->{Player p=Bukkit.getPlayer(id);return p!=null&&p.getWorld()==match.world;});}

    private boolean handleTwoCommand(Player player,String[] args){
        if(args.length==0){openTwo(player);return true;}
        String sub=args[0].toLowerCase(Locale.ROOT);
        switch(sub){
            case "invite","teammate","partner"->{if(args.length<2){openHeads(player,HeadAction.INVITE_PARTNER,0);return true;}Player target=Bukkit.getPlayerExact(args[1]);if(target==null)player.sendMessage(PREFIX+"§cPlayer not found.");else invitePartner(player,target);}
            case "accept"->{PartnerInvite partnerInvite=validPartnerInvite(player.getUniqueId());if(partnerInvite!=null)acceptPartner(player,args.length>1?args[1]:"");else accept(player,args.length>1?args[1]:"");}
            case "decline"->{if(validPartnerInvite(player.getUniqueId())!=null)declinePartner(player);else decline(player);}
            case "queue","start"->queue(player,Mode.TWO_V_TWO);
            case "solo","random"->queueSoloTwo(player);
            case "challenge"->{if(args.length<2){openHeads(player,HeadAction.CHALLENGE_2V2,0);return true;}Player target=Bukkit.getPlayerExact(args[1]);if(target==null)player.sendMessage(PREFIX+"§cPlayer not found.");else invite(player,target,Mode.TWO_V_TWO);}
            case "kick"->{UUID mate=partyMate(player.getUniqueId());if(args.length>1&&mate!=null&&!name(mate).equalsIgnoreCase(args[1]))player.sendMessage(PREFIX+"§cThat player is not your teammate.");else kickPartyMember(player);}
            case "leader","promote"->{UUID mate=partyMate(player.getUniqueId());if(args.length>1&&mate!=null&&!name(mate).equalsIgnoreCase(args[1]))player.sendMessage(PREFIX+"§cThat player is not your teammate.");else promotePartyMember(player);}
            case "party"->{UUID leader=partyLeader(player.getUniqueId());if(leader==null)player.sendMessage(PREFIX+"§7You do not have a 2v2 party.");else player.sendMessage(PREFIX+"§dParty: §f"+partyNamesFor(player.getUniqueId())+" §8• §7Leader: §f"+name(leader));}
            case "leaveparty","disband"->leaveParty(player);
            case "leave","cancel"->leaveQueue(player.getUniqueId(),true);
            case "menu"->openTwo(player);
            case "tournament","tourney"->{String action=args.length>1?args[1].toLowerCase(Locale.ROOT):"status";switch(action){case "join"->toggleSaturdayRegistration(player);case "leave"->leaveSaturdayTournament(player);case "open"->{if(player.hasPermission("merelytournament.admin"))openSaturdayTournament(true);else player.sendMessage(PREFIX+"§cYou do not have permission.");}case "start"->{if(player.hasPermission("merelytournament.admin"))startSaturdayTournament(player);else player.sendMessage(PREFIX+"§cYou do not have permission.");}case "stop"->{if(player.hasPermission("merelytournament.admin"))stopSaturdayTournament("Stopped by "+player.getName());else player.sendMessage(PREFIX+"§cYou do not have permission.");}default->player.sendMessage(PREFIX+"§bSaturday 2v2 §8• "+saturdayStatus()+" §8• §f/2v2 tournament join|leave");}}
            default->player.sendMessage(PREFIX+"§e/2v2 §8• §f/2v2 invite <player> §8• §f/2v2 accept §8• §f/2v2 queue §8• §f/2v2 solo §8• §f/2v2 challenge <player> §8• §f/2v2 tournament join §8• §f/2v2 party §8• §f/2v2 leaveparty");
        }
        return true;
    }

    @Override public boolean onCommand(CommandSender sender,Command command,String label,String[] args){
        if(!(sender instanceof Player player)){sender.sendMessage("Players only.");return true;}
        if(command.getName().equalsIgnoreCase("duel2v2"))return handleTwoCommand(player,args);
        if(command.getName().equalsIgnoreCase("wagerduel"))return wagerCommand(player,args,0);
        if(args.length==0){openMain(player);return true;}
        String sub=args[0].toLowerCase(Locale.ROOT);
        if(Bukkit.getPlayerExact(args[0])!=null){invite(player,Bukkit.getPlayerExact(args[0]),Mode.ONE_V_ONE);return true;}
        switch(sub){
            case "queue","start"->{if(args.length<2){player.sendMessage(PREFIX+"§e/duel queue <1v1|2v2>");return true;}Mode mode=parseMode(args[1]);if(mode==null)player.sendMessage(PREFIX+"§cChoose 1v1 or 2v2.");else queue(player,mode);}
            case "invite","challenge"->{if(args.length<2){openHeads(player,HeadAction.INVITE_1V1,0);return true;}Player target=Bukkit.getPlayerExact(args[1]);if(target==null){player.sendMessage(PREFIX+"§cPlayer not found.");return true;}Mode mode=args.length>2?parseMode(args[2]):Mode.ONE_V_ONE;if(mode==null)player.sendMessage(PREFIX+"§cChoose 1v1 or 2v2.");else invite(player,target,mode);}
            case "wager","bet"->wagerCommand(player,args,1);
            case "accept"->accept(player,args.length>1?args[1]:"");
            case "decline"->decline(player);
            case "partner","teammate"->{if(args.length<2){openHeads(player,HeadAction.INVITE_PARTNER,0);return true;}Player target=Bukkit.getPlayerExact(args[1]);if(target==null)player.sendMessage(PREFIX+"§cPlayer not found.");else invitePartner(player,target);}
            case "leave","cancel"->leaveQueue(player.getUniqueId(),true);
            case "menu"->openMain(player);
            default->player.sendMessage(PREFIX+"§e/duel §8• §f/duel <player> §8• §f/wager <player> <amount> §8• §f/duel queue <1v1|2v2> §8• §f/duel invite <player> [1v1|2v2] §8• §f/duel accept");
        }return true;
    }

    private boolean wagerCommand(Player challenger,String[] args,int offset){
        if(args.length<offset+2){challenger.sendMessage(PREFIX+"§eUse: §f/wager <player> <amount> §8• §7Example: §f/wager Steve 25k");return true;}
        Player target=Bukkit.getPlayerExact(args[offset]);if(target==null){challenger.sendMessage(PREFIX+"§cPlayer not found.");return true;}
        long amount=parseMoney(args[offset+1]);long minimum=plugin.getConfig().getLong("duels.wager.minimum",1000),maximum=plugin.getConfig().getLong("duels.wager.maximum",10000000);
        if(amount<minimum||amount>maximum){challenger.sendMessage(PREFIX+"§cChoose a stake from §f$"+money(minimum)+" §cto §f$"+money(maximum)+"§c.");return true;}
        if(plugin.duelMoneyBalance(challenger)<amount){challenger.sendMessage(PREFIX+"§cYou need §f$"+money(amount)+" §cto create this wager.");return true;}
        invite(challenger,target,Mode.ONE_V_ONE,amount);return true;
    }

    @Override public List<String> onTabComplete(CommandSender sender,Command command,String alias,String[] args){
        String input=args.length==0?"":args[args.length-1].toLowerCase(Locale.ROOT);
        if(command.getName().equalsIgnoreCase("wagerduel")){if(args.length==1)return filter(Bukkit.getOnlinePlayers().stream().map(Player::getName).toList(),input);if(args.length==2)return filter(List.of("1k","5k","10k","25k","50k","100k"),input);return List.of();}
        if(command.getName().equalsIgnoreCase("duel2v2")){
            if(args.length==1)return filter(List.of("invite","accept","decline","queue","solo","challenge","tournament","party","kick","leader","leaveparty","leave","menu"),input);
            if(args.length==2&&Set.of("tournament","tourney").contains(args[0].toLowerCase(Locale.ROOT))){List<String> options=new ArrayList<>(List.of("status","join","leave"));if(sender.hasPermission("merelytournament.admin"))options.addAll(List.of("open","start","stop"));return filter(options,input);}
            if(args.length==2&&Set.of("invite","accept","challenge","partner","teammate").contains(args[0].toLowerCase(Locale.ROOT)))return filter(Bukkit.getOnlinePlayers().stream().map(Player::getName).toList(),input);
            if(args.length==2&&Set.of("kick","leader","promote").contains(args[0].toLowerCase(Locale.ROOT))&&sender instanceof Player player){UUID mate=partyMate(player.getUniqueId());return mate==null?List.of():filter(List.of(name(mate)),input);}
            return List.of();
        }
        if(args.length==1){List<String> values=new ArrayList<>(List.of("queue","invite","wager","partner","accept","decline","leave","menu"));values.addAll(Bukkit.getOnlinePlayers().stream().map(Player::getName).toList());return filter(values,input);}
        if(args.length==2&&args[0].equalsIgnoreCase("wager"))return filter(Bukkit.getOnlinePlayers().stream().map(Player::getName).toList(),input);
        if(args.length==3&&args[0].equalsIgnoreCase("wager"))return filter(List.of("1k","5k","10k","25k","50k","100k"),input);
        if(args.length==2&&(args[0].equalsIgnoreCase("queue")||args[0].equalsIgnoreCase("start")))return filter(List.of("1v1","2v2"),input);
        if(args.length==2&&(args[0].equalsIgnoreCase("invite")||args[0].equalsIgnoreCase("challenge")||args[0].equalsIgnoreCase("partner")||args[0].equalsIgnoreCase("teammate")))return filter(Bukkit.getOnlinePlayers().stream().map(Player::getName).sorted(String.CASE_INSENSITIVE_ORDER).toList(),input);
        if(args.length==2&&args[0].equalsIgnoreCase("accept")){DuelInvite invite=sender instanceof Player p?validInvite(p.getUniqueId()):null;return invite==null?List.of():filter(List.of(name(invite.challenger)),input);}
        if(args.length==3&&(args[0].equalsIgnoreCase("invite")||args[0].equalsIgnoreCase("challenge")))return filter(List.of("1v1","2v2"),input);
        return List.of();
    }

    private Mode parseMode(String value){return switch(value.toLowerCase(Locale.ROOT)){case "1v1","1","solo"->Mode.ONE_V_ONE;case "2v2","2","duo"->Mode.TWO_V_TWO;default->null;};}
    private List<String> filter(Collection<String> values,String prefix){return values.stream().distinct().filter(v->v.toLowerCase(Locale.ROOT).startsWith(prefix)).sorted(String.CASE_INSENSITIVE_ORDER).toList();}
    private DuelInvite validInvite(UUID target){DuelInvite invite=invites.get(target);if(invite!=null&&invite.expiresAt<System.currentTimeMillis()){invites.remove(target);return null;}return invite;}
    private PartnerInvite validPartnerInvite(UUID target){PartnerInvite invite=partnerInvites.get(target);if(invite!=null&&invite.expiresAt<System.currentTimeMillis()){partnerInvites.remove(target);return null;}return invite;}
    private void purgeExpiredAndInvalid(){invites.entrySet().removeIf(e->e.getValue().expiresAt<System.currentTimeMillis());partnerInvites.entrySet().removeIf(e->e.getValue().expiresAt<System.currentTimeMillis());purgeQueue(queue1);purgeQueue(queue2);soloTwoQueue.removeIf(id->Bukkit.getPlayer(id)==null||isBusy(id)||plugin.duelHasStoredState(id));selectedPartners.entrySet().removeIf(e->{boolean invalid=!e.getKey().equals(partnerLeaders.get(e.getValue()));if(invalid)partnerLeaders.remove(e.getValue(),e.getKey());return invalid;});restoring.removeIf(id->!plugin.duelHasStoredState(id));tryStartSoloTwo();}
    private void purgeQueue(Deque<DuelParty> queue){for(Iterator<DuelParty> it=queue.iterator();it.hasNext();){DuelParty party=it.next();if(!validParty(party)){it.remove();removeQueued(party);message(party,"§cYou left the duel queue because the party is no longer ready.");}}}
    private void leaveQueue(UUID id,boolean notify){boolean removedSolo=soloTwoQueue.remove(id);DuelParty party=queuedByPlayer.get(id);if(party==null){if(notify){Player p=Bukkit.getPlayer(id);if(p!=null)p.sendMessage(PREFIX+(removedSolo?"§cLeft the random 2v2 queue.":"§7You are not in a duel queue."));}return;}leaveQueueForParty(party,notify);}
    private void leaveQueueForParty(DuelParty party,boolean notify){queue1.remove(party);queue2.remove(party);removeQueued(party);if(notify)message(party,"§cLeft the duel queue.");}
    private void leaveQueuesFor(Collection<UUID> players){Set<DuelParty> queued=new HashSet<>();for(UUID id:players){DuelParty party=queuedByPlayer.get(id);if(party!=null)queued.add(party);}for(DuelParty party:queued)leaveQueueForParty(party,false);}
    private void disbandPartyOnQuit(UUID quitter){UUID leader=partyLeader(quitter);if(leader==null)return;List<UUID> members=partyMembers(leader);leaveQueuesFor(members);clearPairing(quitter);for(UUID id:members)if(!id.equals(quitter)){Player remaining=Bukkit.getPlayer(id);if(remaining!=null)remaining.sendMessage(PREFIX+"§cYour 2v2 party was disbanded because §f"+name(quitter)+" §cleft the server.");}}
    private void removeQueued(DuelParty party){for(UUID id:party.players)queuedByPlayer.remove(id,party);}
    private void message(DuelParty party,String text){for(Player p:online(party.players))p.sendMessage(PREFIX+text);}
    private List<Player> online(Collection<UUID> ids){return ids.stream().map(Bukkit::getPlayer).filter(Objects::nonNull).toList();}
    private String teamNames(DuelParty party){return party.players.stream().map(this::name).collect(java.util.stream.Collectors.joining(" + "));}
    private String partyNamesFor(UUID member){List<UUID> members=partyMembers(member);return members.isEmpty()?name(member):members.stream().map(this::name).collect(java.util.stream.Collectors.joining(" + "));}
    private String name(UUID id){String name=Bukkit.getOfflinePlayer(id).getName();return name==null?id.toString().substring(0,8):name;}
    private String format(long seconds){Duration d=Duration.ofSeconds(Math.max(0,seconds));long m=d.toMinutes(),s=d.minusMinutes(m).toSeconds();return m+"m "+s+"s";}
    private long parseMoney(String input){try{String value=input.trim().toLowerCase(Locale.ROOT).replace(",","");long multiplier=1;if(value.endsWith("k")){multiplier=1000;value=value.substring(0,value.length()-1);}else if(value.endsWith("m")){multiplier=1000000;value=value.substring(0,value.length()-1);}double parsed=Double.parseDouble(value);if(parsed<=0||parsed*multiplier>Long.MAX_VALUE)return -1;return Math.round(parsed*multiplier);}catch(Exception ignored){return -1;}}
    private String money(long amount){return String.format(Locale.US,"%,d",amount);}
    private void refundWager(Wager wager){if(wager==null||wager.resolved)return;wager.resolved=true;plugin.duelGiveMoney(wager.first,wager.amount);plugin.duelGiveMoney(wager.second,wager.amount);for(UUID id:List.of(wager.first,wager.second)){Player player=Bukkit.getPlayer(id);if(player!=null)player.sendMessage(PREFIX+"§aYour §e$"+money(wager.amount)+" §awager was refunded.");}}
    private void payWager(Wager wager,UUID winner){if(wager==null||wager.resolved)return;wager.resolved=true;long pot=wager.amount*2;plugin.duelGiveMoney(winner,pot);Player player=Bukkit.getPlayer(winner);if(player!=null){player.sendTitle("§6§lWAGER WON","§e+$"+money(pot),5,55,10);player.sendMessage(PREFIX+"§6You won the full §e$"+money(pot)+" §6pot!");}}
    private ItemStack item(Material material,String name,String... lore){ItemStack stack=new ItemStack(material);ItemMeta meta=stack.getItemMeta();meta.setDisplayName(name);if(lore.length>0)meta.setLore(Arrays.asList(lore));stack.setItemMeta(meta);return stack;}
    private void fill(Inventory inv){ItemStack pane=item(Material.GRAY_STAINED_GLASS_PANE," ");for(int i=0;i<inv.getSize();i++)if(inv.getItem(i)==null)inv.setItem(i,pane);}

    private static final class DuelPreparation{
        final long id;final DuelParty first,second;final String worldName;final boolean saturdayTournament;final Wager wager;boolean cancelled;
        DuelPreparation(long id,DuelParty first,DuelParty second,String worldName,boolean saturdayTournament,Wager wager){this.id=id;this.first=first;this.second=second;this.worldName=worldName;this.saturdayTournament=saturdayTournament;this.wager=wager;}
        List<UUID> players(){List<UUID> all=new ArrayList<>(first.players);all.addAll(second.players);return all;}
    }
    private static final class DuelMatch{
        final DuelParty first,second;final Mode mode;final World world;final boolean saturdayTournament;final Wager wager;final Set<UUID> aliveFirst,aliveSecond;State state=State.COUNTDOWN;long startedAt;BossBar bar;BukkitTask countdownTask,timerTask;
        DuelMatch(DuelParty first,DuelParty second,World world,boolean saturdayTournament,Wager wager){this.first=first;this.second=second;this.mode=first.mode;this.world=world;this.saturdayTournament=saturdayTournament;this.wager=wager;this.aliveFirst=new LinkedHashSet<>(first.players);this.aliveSecond=new LinkedHashSet<>(second.players);}
        List<UUID> players(){List<UUID> all=new ArrayList<>(first.players);all.addAll(second.players);return all;}
        Set<UUID> alivePlayers(){Set<UUID> all=new LinkedHashSet<>(aliveFirst);all.addAll(aliveSecond);return all;}
        boolean sameTeam(UUID a,UUID b){return aliveFirst.contains(a)&&aliveFirst.contains(b)||aliveSecond.contains(a)&&aliveSecond.contains(b);}
        boolean isOpponent(UUID a,UUID b){return first.players.contains(a)&&second.players.contains(b)||second.players.contains(a)&&first.players.contains(b);}
    }
    private static final class Wager{final long amount;final UUID first,second;boolean resolved;Wager(long amount,UUID first,UUID second){this.amount=amount;this.first=first;this.second=second;}}
}
