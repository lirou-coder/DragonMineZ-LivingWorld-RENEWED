package com.dmzlivingworld.world;

import com.dmzlivingworld.LivingWorldMod;
import com.dmzlivingworld.entity.AmbientFighterEntity;
import com.dragonminez.common.init.MainEntities;
import com.dragonminez.common.init.entities.sagas.SagaSaibamanEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.entity.EntityLeaveLevelEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.List;
import java.util.UUID;

/**
 * Rare field-researcher specialization. Scientists remain ordinary progressing LW fighters, but
 * can deploy real native Dragon Mine Z Saibaman variants whose physical stats scale from the
 * scientist's current permanent development rather than staying at saga-default strength forever.
 */
@Mod.EventBusSubscriber(modid = LivingWorldMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class FighterScientistManager {
    private static final String ROLLED = "LWScientistRollV1";
    private static final String SCIENTIST = "LWScientist";
    private static final String NEXT_DEPLOY = "LWScientistNextDeploy";
    private static final String NEXT_DEATH_REACTION = "LWScientistNextSpecimenDeathReaction";
    private static final String OWNER = "LWScientistOwner";
    private static final String EXPIRES = "LWScientistMinionExpires";
    private static final String MASTER_BP = "LWScientistMasterBP";
    private static final String FORMULA_PROGRESS = "LWScientistFormulaProgress";
    private static final String RESEARCH_SESSIONS = "LWScientistResearchSessions";
    private static final String SEEDS = "LWScientistSaibamanSeeds";
    private static final String LAST_SEED_TICK = "LWScientistLastSeedTick";
    private static final int BASE_MAX_MINIONS = 2;
    private static final int MAX_FORMULA_PROGRESS = 30;
    private static final int SAIBAMAN_VARIANT_COUNT = 6;
    private static final String RESEARCH_INSIGHT = "LWScientistResearchInsight";
    private static final String LAST_SPAWN_ERROR = "LWScientistLastSpawnError";
    private static final String LAST_LIFECYCLE = "LWScientistLastLifecycle";
    private static final String SPAWNED_AT = "LWScientistSpawnedAt";
    private static final String BASE_NAME = "LWScientistBaseName";
    private static final String TITLE = "LWScientistTitle";

    private FighterScientistManager() {}

    public static void initialize(AmbientFighterEntity fighter) {
        if (fighter == null || fighter.level().isClientSide || WorldMenaceManager.isWorldMenace(fighter)) return;
        CompoundTag legacy = fighter.getLegacyData();
        if (!legacy.getBoolean(ROLLED)) {
            legacy.putBoolean(ROLLED, true);
            // Deliberately rare: enough to discover over a long world, nowhere near a common archetype.
            long seed = fighter.getUUID().getMostSignificantBits() ^ Long.rotateLeft(fighter.getUUID().getLeastSignificantBits(), 21);
            int roll = Math.floorMod((int)(seed ^ (seed >>> 32)), 1000);
            legacy.putBoolean(SCIENTIST, roll < 30); // ~3%
        }
        if (!legacy.getBoolean(SCIENTIST)) return;
        ensureSeedStock(fighter);
        applyResearcherIdentity(fighter);
        applyResearcherAppearance(fighter);
    }

    public static boolean isScientist(AmbientFighterEntity fighter) {
        return fighter != null && !WorldMenaceManager.isWorldMenace(fighter) && fighter.getLegacyData().getBoolean(SCIENTIST);
    }

    public static boolean isScientist(CompoundTag legacy) {
        return legacy != null && legacy.getBoolean(SCIENTIST);
    }


    public static int currentMinions(AmbientFighterEntity fighter) {
        if (!isScientist(fighter) || !(fighter.level() instanceof ServerLevel level)) return 0;
        return (int) ownedMinions(level, fighter, 128.0D).stream().filter(Entity::isAlive).count();
    }

    public static int maxMinions() { return 4; } // absolute research cap for compatibility/debug UI

    public static int maxMinions(AmbientFighterEntity fighter) {
        if (!isScientist(fighter)) return 0;
        int p = formulaProgress(fighter);
        return BASE_MAX_MINIONS + (p >= 12 ? 1 : 0) + (p >= 24 ? 1 : 0);
    }

    public static int maxSeeds(AmbientFighterEntity fighter) {
        return isScientist(fighter) ? 3 + formulaProgress(fighter) / 3 : 0;
    }

    public static int availableSeeds(AmbientFighterEntity fighter) {
        if (!isScientist(fighter)) return 0;
        refreshSeeds(fighter);
        return Math.max(0, Math.min(maxSeeds(fighter), fighter.getLegacyData().getInt(SEEDS)));
    }


    public static String potencyRange(AmbientFighterEntity fighter) {
        if (!isScientist(fighter)) return "Unknown";
        int formula = formulaProgress(fighter);
        int bp = Math.max(1, fighter.getPermanentBattlePower());
        double low = 0.24D + formula * 0.008D, high = 0.40D + formula * 0.010D;
        int lowBp = Math.max(1200, (int)Math.round(bp * low));
        int highBp = Math.max(lowBp, (int)Math.round(bp * high));
        return lowBp + "–" + highBp + " PL";
    }

    public static String potencyPercentRange(AmbientFighterEntity fighter) {
        if (!isScientist(fighter)) return "Unknown";
        int formula = formulaProgress(fighter);
        double low = (0.24D + formula * 0.008D) * 100.0D;
        double high = (0.40D + formula * 0.010D) * 100.0D;
        return formatPercent(low) + "–" + formatPercent(high) + " of the Scientist's Power Level";
    }

    private static String formatPercent(double value) {
        double rounded = Math.rint(value);
        if (Math.abs(value - rounded) < 0.05D) return Integer.toString((int)rounded) + "%";
        return String.format(java.util.Locale.ROOT, "%.1f%%", value);
    }
    public static java.util.List<String> scienceProfileLines(AmbientFighterEntity fighter) {
        if (!isScientist(fighter)) return java.util.List.of();
        refreshSeeds(fighter);
        int active = currentMinions(fighter), maxActive = maxMinions(fighter);
        int seeds = availableSeeds(fighter), capacity = maxSeeds(fighter), formula = formulaProgress(fighter);
        long cooldown = Math.max(0L, fighter.getLegacyData().getLong(NEXT_DEPLOY) - fighter.level().getGameTime());
        java.util.List<String> out = new java.util.ArrayList<>();
        out.add("## Saibaman Research");
        out.add("* Active specimens: " + active + " / " + maxActive);
        out.add("* Viable specimens: " + seeds + " / " + capacity);
        out.add("* Research: " + researchStage(formula));
        out.add("* Formula potency: " + potencyPercentRange(fighter));
        out.add("* Expected specimen Power Level: " + potencyRange(fighter));
        out.add(cooldown <= 0 ? "+ Deployment: READY" : ". Deployment ready in " + Math.max(1L, (cooldown + 19L) / 20L) + " sec");
        return out;
    }

    public static int formulaProgress(AmbientFighterEntity fighter) {
        return fighter == null ? 0 : Math.max(0, Math.min(MAX_FORMULA_PROGRESS, fighter.getLegacyData().getInt(FORMULA_PROGRESS)));
    }

    public static int researchSessions(AmbientFighterEntity fighter) {
        return fighter == null ? 0 : Math.max(0, fighter.getLegacyData().getInt(RESEARCH_SESSIONS));
    }

    /** Scientist-only ordinary-life work. R19 turns the old six-step cap into a long research career. */
    public static void completeResearchSession(AmbientFighterEntity fighter) {
        if (!isScientist(fighter)) return;
        CompoundTag legacy = fighter.getLegacyData();
        int sessions = Math.min(9999, legacy.getInt(RESEARCH_SESSIONS) + 1);
        legacy.putInt(RESEARCH_SESSIONS, sessions);

        int formula = formulaProgress(fighter);
        if (formula < MAX_FORMULA_PROGRESS) {
            int insight = Math.max(0, legacy.getInt(RESEARCH_INSIGHT)) + 1;
            // Occasional strong session keeps research from feeling mechanically identical.
            if (fighter.getRandom().nextFloat() < 0.28F) insight++;
            int needed = 2 + formula / 8; // early discoveries arrive quickly; late refinement takes work
            if (insight >= needed) {
                insight -= needed;
                formula++;
                legacy.putInt(FORMULA_PROGRESS, formula);
                fighter.recordLegacyEvent("Advanced Saibaman research to refinement " + formula);
                if (fighter.getSpeech().isEmpty() && (formula == 6 || formula == 12 || formula == 18 || formula == 24 || formula == 30))
                    fighter.speak("The cultivation model just opened up another possibility.", 72);
            }
            legacy.putInt(RESEARCH_INSIGHT, insight);
        }
        if (fighter.getRandom().nextFloat() < 0.68F)
            legacy.putInt(SEEDS, Math.min(maxSeeds(fighter), availableSeeds(fighter) + 1));
    }

    private static int unlockedVariantCount(AmbientFighterEntity fighter) {
        return Math.max(1, Math.min(SAIBAMAN_VARIANT_COUNT, 1 + formulaProgress(fighter) / 6));
    }

    private static String researchStage(int p) {
        if (p >= 30) return "mastered cultivation program";
        if (p >= 24) return "advanced adaptive specimens";
        if (p >= 18) return "high-output cultivation";
        if (p >= 12) return "stable field program";
        if (p >= 6) return "developing variants";
        return "experimental cultivation";
    }

    public static boolean forceScientist(AmbientFighterEntity fighter) {
        if (fighter == null || WorldMenaceManager.isWorldMenace(fighter)) return false;
        fighter.getLegacyData().putBoolean(ROLLED, true);
        fighter.getLegacyData().putBoolean(SCIENTIST, true);
        ensureSeedStock(fighter);
        applyResearcherIdentity(fighter);
        applyResearcherAppearance(fighter);
        FighterDailyRoutineManager.invalidatePlan(fighter);
        return true;
    }

    private static void applyResearcherIdentity(AmbientFighterEntity fighter) {
        if (fighter == null || !isScientist(fighter)) return;
        CompoundTag legacy = fighter.getLegacyData();
        String base = legacy.getString(BASE_NAME);
        if (base.isBlank()) {
            base = fighter.getFighterName();
            if (base.startsWith("Dr. ")) base = base.substring(4);
            else if (base.startsWith("Prof. ")) base = base.substring(6);
            if (base.isBlank()) base = "Researcher";
            legacy.putString(BASE_NAME, base);
        }
        String title = legacy.getString(TITLE);
        if (title.isBlank()) {
            title = Math.floorMod(fighter.getUUID().hashCode(), 3) == 0 ? "Prof." : "Dr.";
            legacy.putString(TITLE, title);
        }
        String wanted = title + " " + base;
        if (!wanted.equals(fighter.getFighterName())) fighter.setFighterName(wanted);
    }

    private static void applyResearcherAppearance(AmbientFighterEntity fighter) {
        CompoundTag legacy = fighter.getLegacyData();
        // A dedicated green/purple scouter signature makes the rare role readable without adding
        // a fake item or replacing the fighter's race/model. Existing native accessory renderer is reused.
        int desired = Math.floorMod(fighter.getUUID().hashCode(), 2) == 0
                ? FighterSpecialItemManager.SCOUTER_GREEN : FighterSpecialItemManager.SCOUTER_PURPLE;
        if (fighter.getCosmeticAccessoryId() != desired) {
            fighter.setCosmeticAccessoryId(desired);
            legacy.putInt("CosmeticAccessoryId", desired);
            legacy.putString("CosmeticAccessory", "Research scouter");
        }
    }

    @SubscribeEvent
    public static void onLivingTick(LivingEvent.LivingTickEvent event) {
        if (!(event.getEntity() instanceof Mob minion) || !(minion.level() instanceof ServerLevel level)) return;
        CompoundTag pd = minion.getPersistentData();
        if (!pd.hasUUID(OWNER)) return;
        minion.setPersistenceRequired();
        long expires = pd.getLong(EXPIRES);
        if (expires > 0L && level.getGameTime() >= expires) {
            minion.discard();
            return;
        }
        // Keep Dragon Mine Z's live battle-power capability synchronized with the Scientist's
        // persisted specimen value. Ki Sense reads this capability directly, so it is the one
        // canonical value shared by world sensing and the Living World profile.
        if (minion instanceof SagaSaibamanEntity saibaman && pd.getBoolean("LWScientistPersistentSpecimen")) {
            int intendedBp = Math.max(1, pd.getInt("LWScientistScaledBP"));
            if (!pd.getBoolean("LWScientistBpSyncPrimed") && saibaman.tickCount >= 5) {
                // Force one post-tracking dirty update. Native Saibamen begin at 1200 PL in their
                // constructor, and Ki Sense may perform its first client scan before the Scientist's
                // generated value has reached that client. Dirtying the synced BP once after spawn
                // guarantees the same live value reaches both Ki Sense and the profile panel.
                int temporary = intendedBp == Integer.MAX_VALUE ? intendedBp - 1 : intendedBp + 1;
                saibaman.setBattlePower(temporary);
                saibaman.setBattlePower(intendedBp);
                pd.putBoolean("LWScientistBpSyncPrimed", true);
            } else if (saibaman.tickCount % 40 == Math.floorMod(saibaman.getUUID().hashCode(), 40)
                    && saibaman.getBattlePower() != intendedBp) {
                saibaman.setBattlePower(intendedBp);
            }
        }

        // An owned specimen is persistent against vanilla distance-despawn for its LW-owned deployment lifetime.
        // If its master is loaded and has a combat target, keep the native minion focused on it.
        Entity owner = level.getEntity(pd.getUUID(OWNER));
        if (owner instanceof AmbientFighterEntity master && isScientist(master)) {
            LivingEntity target = master.getTarget();
            if (target != null && target.isAlive() && minion.canAttack(target)) minion.setTarget(target);
        }
    }

    @SubscribeEvent
    public static void onOwnedSpecimenLeave(EntityLeaveLevelEvent event) {
        if (event.getLevel().isClientSide() || !(event.getEntity() instanceof Mob minion)) return;
        CompoundTag pd = minion.getPersistentData();
        if (!pd.getBoolean("LWScientistPersistentSpecimen") || !pd.hasUUID(OWNER)
                || !(event.getLevel() instanceof ServerLevel level)) return;
        long spawnedAt = pd.getLong(SPAWNED_AT);
        long age = spawnedAt <= 0L ? Long.MAX_VALUE : Math.max(0L, level.getGameTime() - spawnedAt);
        Entity owner = level.getEntity(pd.getUUID(OWNER));
        if (!(owner instanceof AmbientFighterEntity master) || !isScientist(master)) return;
        String reason = minion.getRemovalReason() == null ? "unknown" : minion.getRemovalReason().name().toLowerCase(java.util.Locale.ROOT);
        Entity.RemovalReason removal = minion.getRemovalReason();
        boolean killed = minion.isDeadOrDying() || minion.getHealth() <= 0.0F
                || removal == Entity.RemovalReason.KILLED;
        if (killed) {
            master.recordLegacyEvent("Lost Saibaman specimen " + minion.getUUID().toString().substring(0, 8) + " in combat");
            long now = level.getGameTime();
            long nextReaction = master.getLegacyData().getLong(NEXT_DEATH_REACTION);
            if (now >= nextReaction && master.getSpeech().isEmpty() && master.getRandom().nextFloat() < 0.46F) {
                LivingEntity killer = minion.getLastHurtByMob();
                String targetNote = killer == null ? "" : " " + killer.getName().getString() + " exceeded its tolerance.";
                String[] reactions = {
                        "Specimen loss confirmed. Updating the failure model.",
                        "Interesting. That phenotype collapsed faster than projected.",
                        "Tch. Mark that batch unstable; I need to adjust the growth medium.",
                        "So much for that survival curve. The next culture gets a different stress profile.",
                        "That death is data too. Annoying data, but data.",
                        "I was afraid that trait would fail under real combat load."
                };
                master.speak(reactions[master.getRandom().nextInt(reactions.length)] + targetNote, 76);
                master.getLegacyData().putLong(NEXT_DEATH_REACTION, now + 240L + master.getRandom().nextInt(361));
            }
        }
        // Only an actual DISCARD shortly after insertion is treated as a failed deployment.
        // UNLOADED_TO_CHUNK / CHANGED_DIMENSION are continuity events, not specimen loss; refunding
        // those would duplicate stock when the exact same native entity later loads again.
        boolean unexpectedDiscard = removal == Entity.RemovalReason.DISCARDED;
        if (!killed && unexpectedDiscard && age <= 200L && !pd.getBoolean("LWScientistLifecycleRefunded")) {
            pd.putBoolean("LWScientistLifecycleRefunded", true);
            ensureSeedStock(master);
            master.getLegacyData().putInt(SEEDS, Math.min(maxSeeds(master), availableSeeds(master) + 1));
            String msg = "specimen " + minion.getUUID().toString().substring(0, 8)
                    + " left after " + age + " ticks (" + reason + "); stock refunded";
            master.getLegacyData().putString(LAST_LIFECYCLE, msg);
            master.getLegacyData().putString(LAST_SPAWN_ERROR, "Post-spawn lifecycle failure: " + msg + ".");
        } else {
            master.getLegacyData().putString(LAST_LIFECYCLE, "specimen left after " + age + " ticks (" + reason + ")");
        }
    }

    @SubscribeEvent
    public static void onSpecimenInteract(PlayerInteractEvent.EntityInteract event) {
        if (event.getHand() != InteractionHand.MAIN_HAND) return;
        if (!(event.getEntity() instanceof ServerPlayer player) || !player.isShiftKeyDown()) return;
        if (!(event.getTarget() instanceof SagaSaibamanEntity specimen)) return;
        CompoundTag pd = specimen.getPersistentData();
        if (!pd.getBoolean("LWScientistPersistentSpecimen") || !pd.hasUUID(OWNER)) return;
        FighterInspectionManager.inspectScientistSpecimen(player, specimen);
        event.setCancellationResult(InteractionResult.SUCCESS);
        event.setCanceled(true);
    }

    public static void tick(AmbientFighterEntity fighter) {
        if (!isScientist(fighter) || !(fighter.level() instanceof ServerLevel level) || fighter.isDefeated() || fighter.isCaptive()) return;
        if (fighter.tickCount % 20 != Math.floorMod(fighter.getUUID().hashCode(), 20)) return;
        long now = level.getGameTime();
        refreshSeeds(fighter);
        List<Mob> minions = ownedMinions(level, fighter, 96.0D);
        boolean sanctionedSpar = fighter.isSanctionedMatchParticipant() || SparManager.isFighterInSpar(fighter);
        for (Mob minion : minions) {
            if (!minion.isAlive()) continue;
            if (minion.getPersistentData().getLong(EXPIRES) > 0L && now >= minion.getPersistentData().getLong(EXPIRES)) {
                minion.discard();
                continue;
            }
            // A sanctioned spar is training, not a Scientist deployment trial. Existing specimens
            // remain alive/persistent but are never ordered onto the sparring partner.
            if (sanctionedSpar) {
                if (minion.getTarget() == fighter.getTarget()) minion.setTarget(null);
                continue;
            }
            LivingEntity target = fighter.getTarget();
            if (target != null && target.isAlive() && minion.canAttack(target)) minion.setTarget(target);
        }

        if (sanctionedSpar) return;
        LivingEntity target = fighter.getTarget();
        if (target == null || !target.isAlive() || now < fighter.getLegacyData().getLong(NEXT_DEPLOY)) return;
        long active = minions.stream().filter(Entity::isAlive).count();
        if (active >= maxMinions(fighter) || availableSeeds(fighter) <= 0) return;
        // Scientists don't flood every fight. Deployment is a recognizable occasional combat tool.
        double deployChance = Math.min(0.88D, 0.58D + formulaProgress(fighter) * 0.010D);
        if (fighter.getRandom().nextDouble() >= deployChance) {
            fighter.getLegacyData().putLong(NEXT_DEPLOY, now + 300L + fighter.getRandom().nextInt(301));
            return;
        }
        Mob spawned = summonScaledSaibaman(fighter, target);
        if (spawned != null) fighter.getLegacyData().putInt(SEEDS, Math.max(0, availableSeeds(fighter) - 1));
        int recovery = Math.max(420, 900 - formulaProgress(fighter) * 12);
        fighter.getLegacyData().putLong(NEXT_DEPLOY, now + recovery + fighter.getRandom().nextInt(401));
        if (spawned != null && fighter.getSpeech().isEmpty() && fighter.getRandom().nextFloat() < 0.58F) {
            String[] lines = {
                    "Live trial. Let's see whether the stress-response model survives contact.",
                    "Deploying a specimen. I want real combat telemetry, not another controlled sample.",
                    "Good. An uncontrolled variable. Exactly what the culture needs for validation.",
                    "Specimen released. Watching reaction latency and damage tolerance.",
                    "Let's see whether the latest cultivation ratio actually improved combat stability."
            };
            fighter.speak(lines[fighter.getRandom().nextInt(lines.length)], 68);
        }
    }

    private static void ensureSeedStock(AmbientFighterEntity fighter) {
        if (fighter == null) return;
        CompoundTag d = fighter.getLegacyData();
        if (!d.contains(SEEDS)) d.putInt(SEEDS, Math.max(1, Math.min(2, maxSeeds(fighter))));
        if (!d.contains(LAST_SEED_TICK)) d.putLong(LAST_SEED_TICK, fighter.level().getGameTime());
    }

    private static void refreshSeeds(AmbientFighterEntity fighter) {
        if (!isScientist(fighter)) return;
        CompoundTag d = fighter.getLegacyData();
        ensureSeedStock(fighter);
        long now = fighter.level().getGameTime(), last = d.getLong(LAST_SEED_TICK);
        long interval = Math.max(6000L, 12_000L - formulaProgress(fighter) * 180L); // research improves cultivation throughput
        if (now <= last || now - last < interval) return;
        long gained = Math.min(4L, (now - last) / interval);
        if (gained > 0) {
            d.putInt(SEEDS, Math.min(maxSeeds(fighter), d.getInt(SEEDS) + (int)gained));
            d.putLong(LAST_SEED_TICK, last + gained * interval);
        }
    }

    public static int debugAddSpecimens(AmbientFighterEntity fighter, int count) {
        if (!forceScientist(fighter)) return 0;
        ensureSeedStock(fighter);
        int before = availableSeeds(fighter);
        int after = Math.min(maxSeeds(fighter), before + Math.max(1, count));
        fighter.getLegacyData().putInt(SEEDS, after);
        return Math.max(0, after - before);
    }

    public static String diagnosticStatus(AmbientFighterEntity fighter) {
        if (!isScientist(fighter)) return "Not a Scientist.";
        long cooldown = Math.max(0L, fighter.getLegacyData().getLong(NEXT_DEPLOY) - fighter.level().getGameTime());
        return "specimens " + availableSeeds(fighter) + "/" + maxSeeds(fighter)
                + " • active " + currentMinions(fighter) + "/" + maxMinions(fighter)
                + " • deployment " + (cooldown <= 0 ? "ready" : Math.max(1L, (cooldown + 19L) / 20L) + "s")
                + " • auto-deploy: " + deploymentGate(fighter)
                + " • last spawn: " + lastSpawnError(fighter)
                + " • lifecycle: " + lastLifecycle(fighter);
    }

    private static String deploymentGate(AmbientFighterEntity fighter) {
        if (fighter == null || !isScientist(fighter)) return "not a Scientist";
        if (!fighter.isAlive() || fighter.isDefeated() || fighter.isCaptive()) return "Scientist unavailable";
        if (fighter.isSanctionedMatchParticipant() || SparManager.isFighterInSpar(fighter))
            return "sanctioned spar — specimens withheld; real combat only";
        LivingEntity target = fighter.getTarget();
        if (target == null || !target.isAlive()) return "waiting for a live combat target";
        long cooldown = Math.max(0L, fighter.getLegacyData().getLong(NEXT_DEPLOY) - fighter.level().getGameTime());
        if (cooldown > 0L) return "cooldown " + Math.max(1L, (cooldown + 19L) / 20L) + "s";
        int active = currentMinions(fighter), cap = maxMinions(fighter);
        if (active >= cap) return "active specimen cap " + active + "/" + cap;
        int seeds = availableSeeds(fighter);
        if (seeds <= 0) return "no viable specimens";
        return "eligible now; deployment roll checks during combat";
    }

    public static String lastLifecycle(AmbientFighterEntity fighter) {
        if (fighter == null) return "no Scientist";
        String status = fighter.getLegacyData().getString(LAST_LIFECYCLE);
        return status.isBlank() ? "no abnormal post-spawn removal recorded" : status;
    }

    public static String lastSpawnError(AmbientFighterEntity fighter) {
        if (fighter == null) return "No scientist fighter was supplied.";
        String error = fighter.getLegacyData().getString(LAST_SPAWN_ERROR);
        return error.isBlank() ? "No spawn-stage failure was recorded." : error;
    }

    private static void spawnError(AmbientFighterEntity fighter, String error) {
        if (fighter != null) fighter.getLegacyData().putString(LAST_SPAWN_ERROR, error == null ? "Unknown spawn failure." : error);
    }

    public static Mob forceSummon(AmbientFighterEntity fighter) {
        if (!forceScientist(fighter)) { spawnError(fighter, "The selected fighter could not be converted to Scientist."); return null; }
        fighter.getLegacyData().remove(LAST_SPAWN_ERROR);
        Mob spawned = summonScaledSaibaman(fighter, fighter.getTarget());
        if (spawned != null) {
            fighter.getLegacyData().putLong(NEXT_DEPLOY, fighter.level().getGameTime() + 100L);
            fighter.getLegacyData().putInt(SEEDS, Math.max(0, availableSeeds(fighter) - 1));
        }
        return spawned;
    }

    private static Mob summonScaledSaibaman(AmbientFighterEntity master, LivingEntity target) {
        if (!(master.level() instanceof ServerLevel level)) {
            spawnError(master, "Scientist is not currently on a server level.");
            return null;
        }
        int variants = unlockedVariantCount(master);
        EntityType<? extends SagaSaibamanEntity> type;
        try {
            type = nativeSaibamanType(master.getRandom().nextInt(Math.max(1, variants)));
        } catch (Throwable t) {
            spawnError(master, "DMZ Saibaman registry lookup failed: " + t.getClass().getSimpleName() + ".");
            return null;
        }
        if (type == null) {
            spawnError(master, "DMZ returned no Saibaman EntityType.");
            return null;
        }

        Entity raw;
        try { raw = type.create(level); }
        catch (Throwable t) {
            spawnError(master, "DMZ Saibaman EntityType.create failed: " + t.getClass().getSimpleName() + ".");
            return null;
        }
        if (!(raw instanceof SagaSaibamanEntity minion)) {
            spawnError(master, raw == null ? "DMZ Saibaman EntityType.create returned null."
                    : "DMZ created " + raw.getClass().getSimpleName() + " instead of SagaSaibamanEntity.");
            return null;
        }

        BlockPos origin = master.blockPosition();
        double angle = master.getRandom().nextDouble() * Math.PI * 2.0D;
        double radius = 2.2D + master.getRandom().nextDouble() * 2.4D;
        BlockPos rough = BlockPos.containing(master.getX() + Math.cos(angle) * radius, master.getY(), master.getZ() + Math.sin(angle) * radius);
        // R39 restores the last proven native spawn contract from R37. Keep R38's broader second search as an
        // additive safety attempt, but never hard-abort merely because terrain probing found no candidate: the
        // original implementation falls back beside the Scientist and lets native entity placement resolve it.
        BlockPos safe = AmbientFighterSpawner.findSafeGroundAround(level, rough, master.getRandom(), 1, 6, 12);
        if (safe == null) safe = AmbientFighterSpawner.findSafeGroundAround(level, origin, master.getRandom(), 1, 12, 28);
        if (safe == null) safe = origin.above();
        minion.moveTo(safe.getX() + 0.5D, safe.getY(), safe.getZ() + 0.5D, master.getYRot(), 0.0F);

        // Complete the normal native Mob initialization before insertion. Any exception is diagnostic,
        // but not fatal: SagaSaibamanEntity already has a valid constructor/default brain.
        try { minion.finalizeSpawn(level, level.getCurrentDifficultyAt(safe), MobSpawnType.EVENT, null, null); }
        catch (Throwable t) { master.getLegacyData().putString(LAST_SPAWN_ERROR, "Native finalizeSpawn warning: " + t.getClass().getSimpleName() + "."); }

        int masterBp = Math.max(1, master.getPermanentBattlePower());
        int formula = formulaProgress(master);
        double lowRatio = 0.24D + formula * 0.008D;
        double spread = 0.16D + formula * 0.002D;
        double ratio = lowRatio + master.getRandom().nextDouble() * spread;
        int minionBp = Math.max(1200, (int)Math.round(masterBp * ratio));

        // Ownership/lifetime tags must exist before the EntityJoin event so every observer sees a fully
        // identified LW specimen. DMZ's join hook normally rewrites default saga stats, so R32 explicitly
        // marks the entity configured and then reapplies the Scientist scaling AFTER insertion as well.
        minion.setPersistenceRequired();
        minion.getPersistentData().putUUID(OWNER, master.getUUID());
        minion.getPersistentData().putBoolean("LWScientistPersistentSpecimen", true);
        minion.getPersistentData().putLong(EXPIRES, level.getGameTime() + 20L * (600L + formula * 20L));
        minion.getPersistentData().putInt(MASTER_BP, masterBp);
        minion.getPersistentData().putInt("LWScientistScaledBP", minionBp);
        minion.getPersistentData().putBoolean("LWScientistBpSyncPrimed", false);
        minion.getPersistentData().putLong(SPAWNED_AT, level.getGameTime());
        minion.getPersistentData().putBoolean("dmz_stats_configured", true);

        // Seed the intended stats before insertion so Ki Sense/EntityJoin observers never get a
        // window where this Scientist-owned specimen advertises the stock Saibaman power level.
        // The established post-insertion reapply remains below because DMZ may configure saga
        // entities during EntityJoin.
        applyScaledStats(master, minion, masterBp, minionBp, ratio, formula);

        boolean added;
        try { added = level.addFreshEntity(minion); }
        catch (Throwable t) {
            spawnError(master, "Server rejected Saibaman during addFreshEntity: " + t.getClass().getSimpleName() + ".");
            return null;
        }
        if (!added || minion.isRemoved()) {
            spawnError(master, "Server refused the native Saibaman entity at " + safe.getX() + ", " + safe.getY() + ", " + safe.getZ() + ".");
            return null;
        }

        applyScaledStats(master, minion, masterBp, minionBp, ratio, formula);
        if (target != null && target.isAlive() && minion.canAttack(target)) minion.setTarget(target);
        master.getLegacyData().remove(LAST_SPAWN_ERROR);
        master.getLegacyData().putString(LAST_LIFECYCLE, "spawned " + minion.getUUID().toString().substring(0, 8) + " and inserted successfully");
        return minion;
    }

    private static void applyScaledStats(AmbientFighterEntity master, SagaSaibamanEntity minion,
                                         int masterBp, int minionBp, double ratio, int formula) {
        minion.setBattlePower(minionBp);
        if (minion.getAttribute(Attributes.MAX_HEALTH) != null) {
            double hp = Math.max(80.0D, master.getMaxHealth() * (0.24D + ratio * 0.30D));
            minion.getAttribute(Attributes.MAX_HEALTH).setBaseValue(hp);
            minion.setHealth((float)hp);
        }
        if (minion.getAttribute(Attributes.ATTACK_DAMAGE) != null) {
            double attack = Math.max(15.0D, master.getAttributeValue(Attributes.ATTACK_DAMAGE) * (0.36D + ratio * 0.45D));
            minion.getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(attack);
        }
        minion.setKiBlastDamage((float)Math.max(minion.getKiBlastDamage(), master.getKiBlastDamage() * (0.30D + ratio * 0.52D)));
        if (minion.getAttribute(Attributes.MOVEMENT_SPEED) != null) {
            double speed = Math.max(0.20D, Math.min(0.38D, 0.20D + formula * 0.003D + ratio * 0.07D));
            minion.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(speed);
        }
        minion.setAiTierById(masterBp >= 100_000 ? 3 : masterBp >= 15_000 ? 2 : 1);
    }


    @SuppressWarnings("unchecked")
    private static EntityType<? extends SagaSaibamanEntity> nativeSaibamanType(int index) {
        return switch (Math.max(0, Math.min(5, index))) {
            case 0 -> MainEntities.SAGA_SAIBAMAN.get();
            case 1 -> MainEntities.SAGA_SAIBAMAN2.get();
            case 2 -> MainEntities.SAGA_SAIBAMAN3.get();
            case 3 -> MainEntities.SAGA_SAIBAMAN4.get();
            case 4 -> MainEntities.SAGA_SAIBAMAN5.get();
            default -> MainEntities.SAGA_SAIBAMAN6.get();
        };
    }

    private static List<Mob> ownedMinions(ServerLevel level, AmbientFighterEntity master, double radius) {
        UUID id = master.getUUID();
        return level.getEntitiesOfClass(Mob.class, master.getBoundingBox().inflate(radius), mob ->
                mob.getPersistentData().hasUUID(OWNER) && id.equals(mob.getPersistentData().getUUID(OWNER)));
    }
}
