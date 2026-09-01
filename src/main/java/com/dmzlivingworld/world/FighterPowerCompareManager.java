package com.dmzlivingworld.world;

import com.dmzlivingworld.LivingWorldMod;
import com.dmzlivingworld.config.LivingWorldConfig;
import com.dmzlivingworld.entity.AmbientFighterEntity;
import com.dmzlivingworld.entity.FighterAlignment;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Rare friendly power comparisons: two peaceful fighters flare their Ki and may reveal learned forms without starting a fight. */
@Mod.EventBusSubscriber(modid = LivingWorldMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class FighterPowerCompareManager {
    private static final Map<UUID, Session> SESSIONS = new HashMap<>();
    private static final String DEBUG_SUBJECTS = "LWDebugPowerCompareSubjects";
    private static final String NEXT = "LWNextPowerCompare";
    private static final class Session {
        final UUID a, b;
        final net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> dimension;
        final long start;
        long end;
        final boolean aWasForm, bWasForm;
        final int aBasePower, bBasePower;
        boolean formsTried, charging, aBoosted, bBoosted, openingReplySpoken, chargeReplySpoken;
        long readyAt, chargeStarted, openingReplyAt, chargeReplyAt;
        Session(AmbientFighterEntity a, AmbientFighterEntity b, long now) {
            this.a=a.getUUID(); this.b=b.getUUID(); this.dimension=a.level().dimension(); this.start=now;
            this.end=now+360L+a.getRandom().nextInt(121);
            this.aWasForm = a.isRacialFormActive(); this.bWasForm = b.isRacialFormActive();
            this.aBasePower = a.getPermanentBattlePower(); this.bBasePower = b.getPermanentBattlePower();
            this.openingReplyAt = now + 52L + a.getRandom().nextInt(35);
        }
    }
    private FighterPowerCompareManager() {}

    @SubscribeEvent
    public static void onTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        MinecraftServer server=event.getServer(); long now=server.overworld().getGameTime();
        // Existing scenes must always finish. The live config only controls whether a new
        // comparison may begin; otherwise a toggle can freeze temporary BP/state forever.
        if (now%5L==0L) tickSessions(server,now);
        if (!LivingWorldConfig.npcSocializing()) return;
        if (now%400L==0L) tryStart(server,now);
    }

    private static void tryStart(MinecraftServer server,long now) {
        for(ServerPlayer player:server.getPlayerList().getPlayers()) {
            if(!(player.level() instanceof ServerLevel level)) continue;
            List<AmbientFighterEntity> nearby=level.getEntitiesOfClass(AmbientFighterEntity.class,player.getBoundingBox().inflate(28.0D),FighterPowerCompareManager::available);
            for(AmbientFighterEntity a:nearby) {
                if(now<a.getPersistentData().getLong(NEXT) || SESSIONS.containsKey(a.getUUID()) || a.getRandom().nextFloat()>0.018F) continue;
                AmbientFighterEntity b=nearby.stream().filter(x->x!=a && !SESSIONS.containsKey(x.getUUID()) && compatible(a,x) && a.distanceToSqr(x)<9.0D*9.0D)
                        .findAny().orElse(null);
                if(b==null) continue;
                start(a,b,now);
                return;
            }
        }
    }

    private static void start(AmbientFighterEntity a,AmbientFighterEntity b,long now) {
        long next=now+18000L+a.getRandom().nextInt(24001);
        a.getPersistentData().putLong(NEXT,next); b.getPersistentData().putLong(NEXT,next+b.getRandom().nextInt(1200));
        a.setSocialLifeActivity(true); b.setSocialLifeActivity(true);
        a.getLookControl().setLookAt(b,35,35); b.getLookControl().setLookAt(a,35,35);
        a.speak(pick(a, "Hold up. Let me feel your Ki for a second.", "You've changed. Show me where your power's at.",
                "Wait. Your Ki feels different today.", "Let's compare without wrecking the place.",
                "I want to see how far you've come.", "Power check. Just for a moment."),82);
        Session s=new Session(a,b,now); SESSIONS.put(a.getUUID(),s); SESSIONS.put(b.getUUID(),s);
    }

    private static void tickSessions(MinecraftServer server,long now) {
        java.util.Set<Session> unique=new java.util.HashSet<>(SESSIONS.values());
        for(Session s:unique) {
            ServerLevel level=server.getLevel(s.dimension);
            AmbientFighterEntity a=level!=null&&level.getEntity(s.a) instanceof AmbientFighterEntity f?f:null;
            AmbientFighterEntity b=level!=null&&level.getEntity(s.b) instanceof AmbientFighterEntity f?f:null;
            if(a==null||b==null||!availableDuring(a)||!availableDuring(b)) { finish(a,b,s,false); continue; }
            a.getLookControl().setLookAt(b,35,35); b.getLookControl().setLookAt(a,35,35);
            if (!s.openingReplySpoken && now >= s.openingReplyAt) {
                s.openingReplySpoken = true;
                b.speak(pick(b, "Fine. Just don't blame me if your scouter regrets it.", "All right. No punches—just power.",
                        "Deal. Keep your hands to yourself.", "Okay. One flare, then we're done.",
                        "You're curious too, huh?", "Fine. Let's get a clean reading."),82);
            }

            if (!s.charging) {
                if (a.distanceToSqr(b) > 3.2D * 3.2D) {
                    if (now - s.start > 180L) { finish(a,b,s,false); continue; }
                    if (a.getNavigation().isDone() || now % 20L == 0L) a.getNavigation().moveTo(b, 0.90D);
                    if (b.getNavigation().isDone() || now % 20L == 0L) b.getNavigation().moveTo(a, 0.90D);
                    continue;
                }
                a.getNavigation().stop(); b.getNavigation().stop();
                // Give the dialogue a readable beat before either fighter powers up.
                if (s.readyAt == 0L) { s.readyAt = Math.max(now + 40L, s.openingReplyAt + 30L); continue; }
                if (now < s.readyAt) continue;
                a.setSocialLifeActivity(false); b.setSocialLifeActivity(false);
                a.setSocialPowerDisplay(true); b.setSocialPowerDisplay(true);
                a.flareAura(240); b.flareAura(240);
                s.charging = true; s.chargeStarted = now; s.end = now + 220L + a.getRandom().nextInt(101);
                s.chargeReplyAt = now + 50L + a.getRandom().nextInt(28);
                if (a.getRandom().nextFloat() < 0.38F) {
                    a.beginSocialPowerDisplayBoost((int)Math.min(Integer.MAX_VALUE - 1L,
                            Math.round(s.aBasePower * (1.04D + a.getRandom().nextDouble() * 0.08D))), s.end + 40L);
                    s.aBoosted=true;
                }
                if (b.getRandom().nextFloat() < 0.38F) {
                    b.beginSocialPowerDisplayBoost((int)Math.min(Integer.MAX_VALUE - 1L,
                            Math.round(s.bBasePower * (1.04D + b.getRandom().nextDouble() * 0.08D))), s.end + 40L);
                    s.bBoosted=true;
                }
                a.speak(pick(a, "There. That's more like it.", "Don't hold it down now.", "That's the level I wanted to feel.",
                        "Good. Let it breathe.", "There you are.", "Now that's an honest reading."),74);
                continue;
            }

            a.getNavigation().stop(); b.getNavigation().stop();
            if (!s.chargeReplySpoken && now >= s.chargeReplyAt) {
                s.chargeReplySpoken = true;
                b.speak(pick(b, "Wasn't planning to.", "Then keep up.", "I'm not suppressing any more than I need to.",
                        "Watch closely.", "You asked for it.", "Try not to blink."),74);
            }
            if(!s.formsTried && now-s.chargeStarted>=60L) {
                s.formsTried=true;
                // A racial transformation captures its own permanent base. Drop the purely
                // presentational comparison flare first so it cannot be captured as real power.
                if (s.aBoosted) { a.endSocialPowerDisplayBoost(); s.aBoosted = false; }
                if (s.bBoosted) { b.endSocialPowerDisplayBoost(); s.bBoosted = false; }
                if(a.getRacialSkillLevel()>0 && a.getRandom().nextFloat()<0.48F) a.beginAwakening();
                if(b.getRacialSkillLevel()>0 && b.getRandom().nextFloat()<0.48F) b.beginAwakening();
            }
            if(now>=s.end) finish(a,b,s,true);
        }
    }

    private static void finish(AmbientFighterEntity a,AmbientFighterEntity b,Session s,boolean comment) {
        SESSIONS.remove(s.a); SESSIONS.remove(s.b);
        if(a!=null) {
            a.setSocialLifeActivity(false); a.setSocialPowerDisplay(false);
            if(!s.aWasForm && a.isRacialFormActive()) a.stopRacialForm();
            a.endSocialPowerDisplayBoost();
        }
        if(b!=null) {
            b.setSocialLifeActivity(false); b.setSocialPowerDisplay(false);
            if(!s.bWasForm && b.isRacialFormActive()) b.stopRacialForm();
            b.endSocialPowerDisplayBoost();
        }
        if(comment&&a!=null&&b!=null) {
            AmbientFighterEntity stronger=a.getBattlePower()>=b.getBattlePower()?a:b, weaker=stronger==a?b:a;
            stronger.speak(pick(stronger, "Heh. That felt good.", "Not bad. I had to push harder than I expected.",
                    "You're closer than last time.", "That was worth checking.", "The gap isn't what it used to be.", "Good pressure."),76);
            weaker.speak(pick(weaker, "Okay, okay. I felt that difference.", "Good. Now I know where the gap is.",
                    "That's useful. I know what I need to work on.", "Still ahead of me. For now.",
                    "I felt where I started falling behind.", "Next time, that reading changes."),76);
        }
    }

    private static String pick(AmbientFighterEntity fighter, String... lines) {
        return lines[fighter.getRandom().nextInt(lines.length)];
    }

    private static boolean available(AmbientFighterEntity f) {
        return f.isAlive()&&!f.isCaptive()&&!f.isDefeated()&&!f.isMeditating()&&!f.isTransforming()&&!f.isKaiokenActive()&&!f.isRacialFormActive()
                &&!FighterSpecialItemManager.hasActiveMightFruit(f)
                &&f.getTarget()==null&&!f.isSocialLifeActivity()&&!f.isSocialPlayerApproach()&&!f.isSocialPowerDisplay()&&!f.isSanctionedMatchParticipant()
                &&f.getAlignment()!=FighterAlignment.BAD;
    }
    private static boolean availableDuring(AmbientFighterEntity f) {
        long lastDamage=f.getPersistentData().getLong("LWLastDamageTime");
        boolean recentlyHurt=lastDamage>0L&&f.level().getGameTime()-lastDamage<=45L;
        return f.isAlive()&&!f.isCaptive()&&!f.isDefeated()&&!f.isMeditating()&&!recentlyHurt&&f.getTarget()==null;
    }
    private static boolean compatible(AmbientFighterEntity a,AmbientFighterEntity b) {
        if(a.getFighterName().equals(b.getRivalName())||b.getFighterName().equals(a.getRivalName())) return false;
        if(FactionManager.areEnemies(a,b)) return false;
        return a.getAlignment()!=FighterAlignment.BAD&&b.getAlignment()!=FighterAlignment.BAD;
    }

    /** Forces one peaceful power comparison nearby for quick operator testing. */
    public static int forceDebug(ServerPlayer player) {
        if (player == null || !(player.level() instanceof ServerLevel level)) return 0;
        long now = level.getServer().overworld().getGameTime();
        java.util.List<AmbientFighterEntity> nearby = new java.util.ArrayList<>(level.getEntitiesOfClass(
                AmbientFighterEntity.class, player.getBoundingBox().inflate(40.0D), FighterPowerCompareManager::available));
        while (nearby.size() < 2) {
            AmbientFighterEntity spawned = AmbientFighterSpawner.spawnNearPlayer(player, FighterAlignment.NEUTRAL,
                    com.dmzlivingworld.entity.FighterRank.TRAINED, true);
            if (spawned == null || nearby.contains(spawned)) break;
            nearby.add(spawned);
        }
        if (nearby.size() < 2) return 0;
        AmbientFighterEntity a = nearby.get(0);
        AmbientFighterEntity b = nearby.stream().skip(1).filter(other -> compatible(a, other)).findFirst().orElse(null);
        if (b == null) return 0;
        start(a, b, now);
        player.getPersistentData().putString(DEBUG_SUBJECTS, a.getFighterName() + " vs " + b.getFighterName());
        return 1;
    }

    public static String debugSubjects(ServerPlayer player) {
        return player == null ? "" : player.getPersistentData().getString(DEBUG_SUBJECTS);
    }

    public static int runtimeEntries(){return new java.util.HashSet<>(SESSIONS.values()).size();}
    public static void clearRuntime(MinecraftServer server) {
        if (server != null) {
            for (Session session : new java.util.HashSet<>(SESSIONS.values())) {
                ServerLevel level = server.getLevel(session.dimension);
                AmbientFighterEntity a = level != null && level.getEntity(session.a) instanceof AmbientFighterEntity fighter ? fighter : null;
                AmbientFighterEntity b = level != null && level.getEntity(session.b) instanceof AmbientFighterEntity fighter ? fighter : null;
                finish(a, b, session, false);
            }
        }
        SESSIONS.clear();
    }
    public static void clearRuntime(){SESSIONS.clear();}
}
