package com.dmzlivingworld.world;

import com.dmzlivingworld.LivingWorldMod;
import com.dmzlivingworld.entity.AmbientFighterEntity;
import com.dmzlivingworld.config.LivingWorldConfig;
import com.dmzlivingworld.entity.FighterArchetype;
import com.dmzlivingworld.entity.FighterDialogue;
import com.dmzlivingworld.entity.FighterPersonality;
import com.dmzlivingworld.entity.FighterRank;
import com.dragonminez.common.init.entities.sagas.DBSagasEntity;
import com.dragonminez.common.init.MainEffects;
import com.dragonminez.common.init.entities.ki.AbstractKiProjectile;
import com.dragonminez.common.combat.clash.BeamClashManager;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Tactical choreography layered over DragonMineZ's native saga combat.
 *
 * Living World never replaces DMZ punches, beams, flight or teleports. The director
 * only decides when a native action makes sense, remembers repetition, respects the
 * fighter's equipment/style, and keeps cinematic beats rare enough to avoid scripted
 * or roleplay-heavy combat.
 */
@Mod.EventBusSubscriber(modid = LivingWorldMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class FighterCombatDirector {
    private static final Map<UUID, FightState> STATES = new HashMap<>();
    private static final Map<UUID, PendingSpeech> PENDING_SPEECH = new HashMap<>();
    private static final int THINK_INTERVAL = 10;
    private static final double SOFT_AERIAL_CEILING = 22.0D;
    private static final double HARD_AERIAL_CEILING = 32.0D;
    private static long lastCleanupTime;

    private FighterCombatDirector() {}

    /** Stable per-entity variant: survives reloads because it is derived from UUID. */
    public static int combatVariant(AmbientFighterEntity fighter) {
        int mixed = fighter.getUUID().hashCode();
        mixed = 31 * mixed + fighter.getRace().id() * 17;
        mixed = 31 * mixed + fighter.getArchetype().id() * 43;
        return Math.floorMod(mixed, 8);
    }

    public static String signatureLabel(AmbientFighterEntity fighter) {
        DBSagasEntity.KiSkillType type = signatureType(fighter);
        if (type == null) {
            return switch (fighter.getArchetype()) {
                case BRAWLER -> "Meteor rush";
                case MARTIAL_ARTIST -> "Combination fighting";
                case SPEEDSTER -> "Rapid pursuit";
                case GUARDIAN -> "Counter fighting";
                case KI_SPECIALIST -> "Ki pressure";
            };
        }
        return switch (type) {
            case KI_SMALL -> "Ki shots";
            case KI_VOLLEY -> "Ki volley";
            case GENERIC_KI_WAVE -> "Ki wave";
            case KI_LASER -> "Ki laser";
            case KI_EXPLOSION -> "Ki burst";
            case KI_AIR_VOLLEY -> "Aerial volley";
            case TRIPLE_LASER -> "Triple laser";
            case KI_BARRIER -> "Ki barrier";
            case KAMEHAMEHA -> "Kamehameha";
            case GALICK_GUN -> "Galick Gun";
            case MASENKO -> "Masenko";
            case FINAL_FLASH -> "Final Flash";
            case DOUBLE_SUNDAY -> "Double Sunday";
            case MAKANKOSAPPO -> "Piercing beam";
            case KIENZAN -> "Energy disk";
            case DEATH_BALL -> "Death sphere";
            case BIG_BANG -> "Big Bang";
            default -> "Ki technique";
        };
    }

    public static void configure(AmbientFighterEntity fighter) {
        fighter.getSkillPool().clear();

        FighterRank rank = fighter.getRank();
        FighterArchetype style = fighter.getArchetype();
        int variant = combatVariant(fighter);

        fighter.setEvade(false, 100);
        fighter.setWildSense(false, 120);
        fighter.setZanzoken(false, 120);

        int comboCooldown = switch (rank) {
            case ROOKIE -> 74;
            case TRAINED -> 50;
            case VETERAN -> 34;
        };

        // Different fighters inside the same archetype deliberately receive different
        // combo/passive identities. These are native DBSagasEntity systems.
        switch (style) {
            case BRAWLER -> {
                if (rank == FighterRank.ROOKIE && (variant & 1) == 1) {
                    fighter.setAllowedCombos(comboCooldown,
                            DBSagasEntity.ComboType.BASIC,
                            DBSagasEntity.ComboType.RAPID_KICKS);
                } else {
                    fighter.setAllowedCombos(comboCooldown, DBSagasEntity.ComboType.BASIC);
                }
                fighter.setEvade(rank == FighterRank.VETERAN && variant % 3 == 0, 72);
                fighter.setWildSense(rank == FighterRank.VETERAN && variant == 7, 130);
            }
            case MARTIAL_ARTIST -> {
                if (rank == FighterRank.ROOKIE && variant >= 5) {
                    fighter.setAllowedCombos(comboCooldown,
                            DBSagasEntity.ComboType.BASIC,
                            DBSagasEntity.ComboType.RAPID_KICKS);
                } else {
                    fighter.setAllowedCombos(comboCooldown, DBSagasEntity.ComboType.BASIC);
                }
                fighter.setEvade(true, rank == FighterRank.VETERAN ? 42 : 68);
                fighter.setWildSense(rank == FighterRank.VETERAN || variant == 6, rank == FighterRank.VETERAN ? 105 : 145);
            }
            case KI_SPECIALIST -> {
                fighter.setAllowedCombos(comboCooldown + 16, DBSagasEntity.ComboType.BASIC);
                fighter.setEvade(rank != FighterRank.ROOKIE, rank == FighterRank.VETERAN ? 54 : 88);
                fighter.setZanzoken(rank == FighterRank.VETERAN && variant % 2 == 0, 110);
            }
            case SPEEDSTER -> {
                if (rank == FighterRank.ROOKIE) {
                    fighter.setAllowedCombos(Math.max(26, comboCooldown - 10),
                            DBSagasEntity.ComboType.BASIC,
                            DBSagasEntity.ComboType.RAPID_KICKS);
                } else {
                    fighter.setAllowedCombos(Math.max(26, comboCooldown - 10), DBSagasEntity.ComboType.BASIC);
                }
                fighter.setEvade(true, rank == FighterRank.VETERAN ? 34 : 54);
                fighter.setZanzoken(rank != FighterRank.ROOKIE, rank == FighterRank.VETERAN ? 68 : 108);
                fighter.setWildSense(rank == FighterRank.VETERAN && variant >= 4, 100);
            }
            case GUARDIAN -> {
                fighter.setAllowedCombos(comboCooldown + 8, DBSagasEntity.ComboType.BASIC);
                fighter.setEvade(true, rank == FighterRank.VETERAN ? 46 : 74);
                fighter.setWildSense(rank == FighterRank.VETERAN || variant == 5, rank == FighterRank.VETERAN ? 108 : 150);
                fighter.setZanzoken(rank == FighterRank.VETERAN && variant == 7, 120);
            }
        }

        if (LivingWorldConfig.npcKiMode() != 2) {
            configureKiIdentity(fighter, rank, style, variant);
            FighterTechniqueManager.applyLearnedTechniques(fighter);
        }
        configureSkillColors(fighter, variant);

        configurePowerDamage(fighter);
        fighter.setKiBlastSpeed(rank == FighterRank.VETERAN ? 0.78F : rank == FighterRank.TRAINED ? 0.67F : 0.58F);
        if (rank == FighterRank.VETERAN) {
            fighter.setFlySpeed(style == FighterArchetype.SPEEDSTER ? 0.62D : 0.52D);
        }

        STATES.remove(fighter.getUUID());
        PENDING_SPEECH.remove(fighter.getUUID());
    }

    public static float baseKiDamage(AmbientFighterEntity fighter) {
        float historical = switch (fighter.getRank()) {
            case ROOKIE -> fighter.getArchetype() == FighterArchetype.KI_SPECIALIST ? 7.0F : 5.5F;
            case TRAINED -> fighter.getArchetype() == FighterArchetype.KI_SPECIALIST ? 12.5F : 9.2F;
            case VETERAN -> fighter.getArchetype() == FighterArchetype.KI_SPECIALIST ? 19.0F : 15.5F;
        };
        return (float)(FighterPowerStatScaler.baseKi(fighter, historical) * FighterPassiveSkillManager.kiMultiplier(fighter));
    }

    /** Refresh only BP-derived Ki output without resetting cooldowns, skills or fight state. */
    public static void configurePowerDamage(AmbientFighterEntity fighter) {
        if (fighter != null) fighter.setKiBlastDamage(baseKiDamage(fighter));
    }

    private static void configureKiIdentity(AmbientFighterEntity fighter, FighterRank rank,
                                            FighterArchetype style, int variant) {
        DBSagasEntity.KiSkillType clashBeam = clashBeamFor(fighter, variant);
        DBSagasEntity.KiSkillType finisher = finisherFor(fighter, variant);

        if (style == FighterArchetype.BRAWLER) {
            // Many brawlers are deliberately pure melee. The exceptional ones feel
            // more memorable because Ki is not guaranteed on the archetype.
            if (rank == FighterRank.ROOKIE || variant < 4) return;
            fighter.addKiSkill(DBSagasEntity.KiSkillType.KI_SMALL, rank == FighterRank.VETERAN ? 125 : 160, 0.62F);
            if (rank == FighterRank.VETERAN && variant >= 6) {
                fighter.addKiSkill(finisher, 300, 0.90F);
            }
            return;
        }

        if (style == FighterArchetype.MARTIAL_ARTIST) {
            if (rank == FighterRank.ROOKIE) {
                if (variant == 7) fighter.addKiSkill(DBSagasEntity.KiSkillType.KI_SMALL, 165, 0.56F);
                return;
            }
            switch (variant % 3) {
                case 0 -> fighter.addKiSkill(DBSagasEntity.KiSkillType.KI_SMALL, 118, 0.66F);
                case 1 -> fighter.addKiSkill(clashBeam, 260, 0.78F);
                default -> fighter.addKiSkill(DBSagasEntity.KiSkillType.KI_VOLLEY, 210, 0.68F);
            }
            if (rank == FighterRank.VETERAN) {
                if (variant >= 4 && variant % 3 != 1) fighter.addKiSkill(clashBeam, 285, 0.92F);
                if (variant >= 6) fighter.addKiSkill(finisher, 330, 0.88F);
                else if (variant == 5) fighter.addKiSkill(DBSagasEntity.KiSkillType.KI_AIR_VOLLEY, 245, 0.72F);
            }
            return;
        }

        if (style == FighterArchetype.KI_SPECIALIST) {
            if (rank == FighterRank.ROOKIE) {
                fighter.addKiSkill(DBSagasEntity.KiSkillType.KI_SMALL, 122, 0.64F);
                if (variant >= 6) fighter.addKiSkill(DBSagasEntity.KiSkillType.KI_VOLLEY, 235, 0.60F);
                return;
            }

            // Every specialist gets a distinct pressure tool + a race/UUID-derived beam.
            fighter.addKiSkill((variant & 1) == 0 ? DBSagasEntity.KiSkillType.KI_SMALL : DBSagasEntity.KiSkillType.KI_VOLLEY,
                    102, 0.78F);
            fighter.addKiSkill(clashBeam, rank == FighterRank.VETERAN ? 225 : 275,
                    rank == FighterRank.VETERAN ? 1.02F : 0.88F);

            switch (variant % 4) {
                case 0 -> fighter.addKiSkill(DBSagasEntity.KiSkillType.KI_LASER, 165, 0.72F);
                case 1 -> fighter.addKiSkill(DBSagasEntity.KiSkillType.KI_AIR_VOLLEY, 205, 0.80F);
                case 2 -> fighter.addKiSkill(DBSagasEntity.KiSkillType.KI_EXPLOSION, 245, 0.80F);
                default -> fighter.addKiSkill(DBSagasEntity.KiSkillType.TRIPLE_LASER, 235, 0.76F);
            }
            if (rank == FighterRank.VETERAN) fighter.addKiSkill(finisher, 350, 1.02F);
            return;
        }

        if (style == FighterArchetype.SPEEDSTER) {
            if (rank == FighterRank.ROOKIE) return;
            if (variant % 3 == 0) fighter.addKiSkill(DBSagasEntity.KiSkillType.KI_LASER, 185, 0.66F);
            else if (variant % 3 == 1) fighter.addKiSkill(DBSagasEntity.KiSkillType.KI_SMALL, 120, 0.68F);
            else fighter.addKiSkill(DBSagasEntity.KiSkillType.KI_VOLLEY, 220, 0.66F);
            if (rank == FighterRank.VETERAN) {
                if (variant >= 3) fighter.addKiSkill(clashBeam, 300, 0.86F);
                if (variant >= 6) fighter.addKiSkill(DBSagasEntity.KiSkillType.KI_AIR_VOLLEY, 250, 0.76F);
            }
            return;
        }

        // Guardian: defense is the identity, but veterans can still answer a beam
        // head-on instead of every single guardian having the same safe response.
        if (rank != FighterRank.ROOKIE) {
            fighter.addKiSkill(DBSagasEntity.KiSkillType.KI_BARRIER, rank == FighterRank.VETERAN ? 132 : 185, 0.84F);
            if (variant % 3 == 0) fighter.addKiSkill(clashBeam, 285, 0.78F);
            else if (variant % 3 == 1) fighter.addKiSkill(DBSagasEntity.KiSkillType.KI_SMALL, 132, 0.64F);
            else fighter.addKiSkill(DBSagasEntity.KiSkillType.KI_EXPLOSION, 275, 0.70F);
            if (rank == FighterRank.VETERAN && variant >= 5 && variant % 3 != 0) fighter.addKiSkill(clashBeam, 310, 0.90F);
        }
    }

    private static DBSagasEntity.KiSkillType clashBeamFor(AmbientFighterEntity fighter, int variant) {
        return switch (fighter.getRace()) {
            case HUMAN -> switch (variant % 3) {
                case 0 -> DBSagasEntity.KiSkillType.KAMEHAMEHA;
                case 1 -> DBSagasEntity.KiSkillType.MASENKO;
                default -> DBSagasEntity.KiSkillType.GENERIC_KI_WAVE;
            };
            case SAIYAN -> switch (variant % 3) {
                case 0 -> DBSagasEntity.KiSkillType.GALICK_GUN;
                case 1 -> DBSagasEntity.KiSkillType.KAMEHAMEHA;
                default -> DBSagasEntity.KiSkillType.FINAL_FLASH;
            };
            case NAMEKIAN -> switch (variant % 3) {
                case 0 -> DBSagasEntity.KiSkillType.MASENKO;
                case 1 -> DBSagasEntity.KiSkillType.GENERIC_KI_WAVE;
                default -> DBSagasEntity.KiSkillType.KAMEHAMEHA;
            };
            case MAJIN -> switch (variant % 3) {
                case 0 -> DBSagasEntity.KiSkillType.KAMEHAMEHA;
                case 1 -> DBSagasEntity.KiSkillType.GENERIC_KI_WAVE;
                default -> DBSagasEntity.KiSkillType.DOUBLE_SUNDAY;
            };
            case FROST_DEMON -> switch (variant % 3) {
                case 0 -> DBSagasEntity.KiSkillType.DOUBLE_SUNDAY;
                case 1 -> DBSagasEntity.KiSkillType.GENERIC_KI_WAVE;
                default -> DBSagasEntity.KiSkillType.GALICK_GUN;
            };
            case BIO_ANDROID -> switch (variant % 3) {
                case 0 -> DBSagasEntity.KiSkillType.GENERIC_KI_WAVE;
                case 1 -> DBSagasEntity.KiSkillType.KAMEHAMEHA;
                default -> DBSagasEntity.KiSkillType.MASENKO;
            };
        };
    }

    private static DBSagasEntity.KiSkillType finisherFor(AmbientFighterEntity fighter, int variant) {
        return switch (fighter.getRace()) {
            case HUMAN -> (variant & 1) == 0 ? DBSagasEntity.KiSkillType.KIENZAN : DBSagasEntity.KiSkillType.MASENKO;
            case SAIYAN -> (variant & 1) == 0 ? DBSagasEntity.KiSkillType.BIG_BANG : DBSagasEntity.KiSkillType.FINAL_FLASH;
            case NAMEKIAN -> DBSagasEntity.KiSkillType.MAKANKOSAPPO;
            case MAJIN -> (variant & 1) == 0 ? DBSagasEntity.KiSkillType.KI_EXPLOSION : DBSagasEntity.KiSkillType.KAMEHAMEHA;
            case FROST_DEMON -> DBSagasEntity.KiSkillType.DEATH_BALL;
            case BIO_ANDROID -> DBSagasEntity.KiSkillType.TRIPLE_LASER;
        };
    }

    private static void configureSkillColors(AmbientFighterEntity fighter, int variant) {
        int[][] palettes = {
                {0x67D7FF, 0xE7FAFF, 0x2F72FF},
                {0xB77CFF, 0xF1DBFF, 0x7132D6},
                {0xFF5B7E, 0xFFD6DE, 0xB52143},
                {0xFFD35C, 0xFFF1B0, 0xD88A13},
                {0x70F08B, 0xD8FFE0, 0x239B48},
                {0x56E4DD, 0xD8FFFC, 0x138D9D},
                {0xFF8F4F, 0xFFE0C9, 0xC34B17},
                {0xE9E9FF, 0xFFFFFF, 0x7777D8}
        };
        int[] p = palettes[Math.floorMod(variant, palettes.length)];
        fighter.setSkillColors(p[0], p[1], p[2]);
        fighter.setAuraColor(p[0]);
        fighter.setLightningColor(p[1]);
    }

    public static void reset(AmbientFighterEntity fighter) {
        FightState state = STATES.remove(fighter.getUUID());
        if (state != null || fighter.isCharge()) fighter.setKiCharge(false);
    }

    public static void clearRuntime() {
        STATES.clear();
        PENDING_SPEECH.clear();
        lastCleanupTime = Long.MIN_VALUE;
    }

    public static int runtimeEntries() { return STATES.size() + PENDING_SPEECH.size(); }

    /** Prune abandoned combat UUIDs even after the last active fight has ended/despawned. */
    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        pruneStale(event.getServer().overworld().getGameTime());
    }

    private static void pruneStale(long now) {
        // Long.MIN_VALUE is the deliberate "never swept" sentinel used after a server
        // lifecycle reset. Check it explicitly: subtracting it from a normal world tick
        // overflows negative and would otherwise suppress every future sweep.
        if (lastCleanupTime != Long.MIN_VALUE && now - lastCleanupTime <= 1200L) return;
        STATES.entrySet().removeIf(entry -> now - entry.getValue().lastTouched > 1200L);
        PENDING_SPEECH.entrySet().removeIf(entry -> now - entry.getValue().createdAt > 1200L);
        lastCleanupTime = now;
    }

    /** Called by AmbientFighterEntity when a heavy native DMZ combo produces a launch. */
    public static void onCinematicLaunch(AmbientFighterEntity attacker, AmbientFighterEntity victim) {
        if (attacker == null || victim == null || attacker.level().isClientSide) return;
        FightState state = STATES.computeIfAbsent(attacker.getUUID(), ignored -> new FightState());
        // A launch may be the first director event for this fighter. Stamp it immediately so
        // the low-frequency stale-state sweep cannot discard the new cinematic beat on the
        // same server tick.
        state.lastTouched = attacker.level().getGameTime();
        state.pauseTicks = Math.max(state.pauseTicks, 7 + attacker.getRandom().nextInt(9));
        state.actionCooldown = Math.max(state.actionCooldown, 18);
        if (state.dialogueCooldown <= 0 && attacker.getRandom().nextFloat() < 0.24F) {
            attacker.speak(FighterDialogue.afterLaunch(attacker.getRandom(), attacker.getPersonality()), 38);
            state.dialogueCooldown = 100;
            queueSpeech(victim, FighterDialogue.launchReply(victim.getRandom(), victim.getPersonality()), 20 + victim.getRandom().nextInt(16));
        }
    }

    public static void onVictory(AmbientFighterEntity victor, AmbientFighterEntity defeated) {
        if (victor == null || victor.level().isClientSide) return;
        queueSpeech(victor, FighterDialogue.victory(victor.getRandom(), victor.getAlignment(), victor.getPersonality()), 18);
    }

    public static void tick(AmbientFighterEntity fighter) {
        if (fighter.level().isClientSide || fighter.isDefeated() || fighter.isCaptive() || fighter.isRetreating()) {
            reset(fighter);
            return;
        }

        deliverPendingSpeech(fighter);

        LivingEntity target = fighter.getTarget();
        if (target == null || !target.isAlive()) {
            reset(fighter);
            return;
        }

        long now = fighter.level().getGameTime();
        pruneStale(now);

        FightState state = STATES.computeIfAbsent(fighter.getUUID(), ignored -> new FightState());
        state.lastTouched = now;
        if (!target.getUUID().equals(state.opponent)) {
            state.resetFor(target.getUUID());
            state.combatBaseY = Math.min(fighter.getY(), target.getY());
            stageOpeningCooldowns(fighter);
            startStandoff(fighter, target, state);
        }

        state.fightTicks++;
        if (state.actionCooldown > 0) state.actionCooldown--;
        if (state.dialogueCooldown > 0) state.dialogueCooldown--;

        double distanceSq = fighter.distanceToSqr(target);
        float hp = fighter.getMaxHealth() <= 0.0F ? 1.0F : fighter.getHealth() / fighter.getMaxHealth();
        updateCastTransition(fighter, state);
        handleHealthDialogue(fighter, state, hp);

        // 0.6.10 anti-stall rule: cinematic holds are only cinematic when there is
        // actual space between the fighters. At melee distance, rapidly burn down
        // director cooldown and cancel any pose/charge that would make two entities
        // stand inside each other doing nothing.
        tickCloseQuartersWatchdog(fighter, target, state, distanceSq);
        stabilizeFlightAltitude(fighter, target, state);

        // DMZ's real clash manager supports NPC participants. Once a clash starts,
        // let DMZ freeze/resolve both combatants without our director fighting it.
        if (BeamClashManager.isClashing(fighter.getUUID())) {
            if (!state.clashAnnounced) {
                fighter.speak(fighter.getPersonality() == FighterPersonality.PROUD ? "Don't you dare lose now!" : "HAAAAA!", 46);
                state.clashAnnounced = true;
            }
            return;
        } else {
            state.clashAnnounced = false;
        }

        if (state.standoffTicks > 0) {
            if (distanceSq <= 20.25D) {
                state.standoffTicks = 0;
                state.actionCooldown = Math.min(state.actionCooldown, 3);
                fighter.setLocomotionMode(DBSagasEntity.LocomotionMode.RUN);
            } else {
                state.standoffTicks--;
                holdDramaticPose(fighter, target);
                if (state.standoffTicks == 0) fighter.setLocomotionMode(DBSagasEntity.LocomotionMode.RUN);
                return;
            }
        }

        if (state.pauseTicks > 0) {
            if (distanceSq <= 20.25D) {
                state.pauseTicks = 0;
                state.actionCooldown = Math.min(state.actionCooldown, 3);
            } else {
                state.pauseTicks--;
                holdDramaticPose(fighter, target);
                return;
            }
        }

        if (state.scriptedChargeTicks > 0) {
            tickDramaticCharge(fighter, target, state, distanceSq);
            return;
        }

        if (fighter.isCharge()) {
            state.chargeTicks++;
            // Native or director-started charge ends when the opponent collapses the gap,
            // starts a dangerous cast, or the dramatic beat has lasted long enough.
            boolean threatened = distanceSq < 30.0D && fighter.getPersonality() != FighterPersonality.PROUD;
            if (threatened || target instanceof DBSagasEntity saga && saga.isCasting() || state.chargeTicks > 72) {
                fighter.setKiCharge(false);
                state.chargeTicks = 0;
                state.actionCooldown = Math.max(state.actionCooldown, 10);
            }
        }

        if (fighter.tickCount % THINK_INTERVAL != Math.floorMod(fighter.getId(), THINK_INTERVAL)) return;
        if (fighter.isCasting()) return;

        if (avoidImmediateKiThreat(fighter, target, state)) return;

        int phase = phase(state.fightTicks, hp);
        if (phase >= 1 && !state.escalatedCombos) {
            enableEscalatedCombos(fighter);
            state.escalatedCombos = true;
        }

        // One restrained mid-fight suspense beat is allowed when both fighters have
        // actual breathing room. Close-range combat never waits for this.
        if (!state.suspenseBeatUsed && tryStartSuspenseBeat(fighter, target, state, distanceSq, phase)) {
            return;
        }

        // One transformation-style awakening per capable fighter. It is deliberately
        // dramatic and is much more likely for veterans than trained fighters.
        if (!state.awakeningUsed && shouldAwaken(fighter, state, hp, phase)) {
            state.awakeningUsed = true;
            fighter.speak(fighter.getPersonality() == FighterPersonality.PROUD ? "You've forced me to go further." : "I'm done holding back!", 58);
            if (target instanceof AmbientFighterEntity other) {
                queueSpeech(other, other.getPersonality() == FighterPersonality.PROUD ? "Finally." : "That power...", 30);
            }
            if (fighter.beginAwakening()) {
                state.actionCooldown = 95;
                return;
            }
        }

        // In a long high-level duel, deliberately line up two native clash-capable
        // beams once. DMZ owns the actual collision/clash from this point onward.
        if (!state.clashBeatUsed && tryStartMutualBeamClash(fighter, target, state, distanceSq, phase)) {
            return;
        }

        // First priority is always a response to what the opponent is visibly doing.
        if (target instanceof DBSagasEntity sagaTarget) {
            boolean targetCharging = sagaTarget.isCharge();
            boolean targetCasting = sagaTarget.isCasting();
            boolean wasCharging = state.targetWasCharging;
            boolean wasCasting = state.targetWasCasting;

            if (targetCharging && !wasCharging) {
                reactToCharge(fighter, target, state, distanceSq, phase);
            } else if (targetCasting && !wasCasting) {
                reactToCast(fighter, target, state, distanceSq);
            } else if (!targetCasting && wasCasting) {
                reactToOpponentRecovery(fighter, target, state, distanceSq, phase);
            } else if (!targetCharging && wasCharging) {
                reactAfterCharge(fighter, target, state, distanceSq);
            }

            state.targetWasCharging = targetCharging;
            state.targetWasCasting = targetCasting;
        }

        if (state.actionCooldown > 0 || fighter.isCharge()) return;

        // Once per high-level duel, deliberately break a grounded close exchange into
        // a vertical pursuit. This is not a fake combat system: it only creates the
        // separation, then DMZ's own flight/chase/combo AI takes over.
        if (!state.aerialBreakUsed && tryStartAerialBreak(fighter, target, state, distanceSq, phase)) {
            return;
        }

        // At escalating moments, deliberately create a power-up beat even when it is
        // not numerically necessary. The opponent gets a chance to react to it.
        if (shouldStartPowerBeat(fighter, target, state, distanceSq, phase)) {
            beginDramaticCharge(fighter, target, state, phase);
            return;
        }

        // Let capable fighters turn a launch/elevation difference into aerial pursuit.
        if (phase >= 1 && fighter.canFly() && shouldPursueInAir(fighter, target, state, distanceSq)) {
            fighter.setFlying(true);
            fighter.setFlyingFast(phase >= 2 || fighter.getArchetype() == FighterArchetype.SPEEDSTER);
            fighter.moveTowardsTargetInAir(target);
            state.actionCooldown = 10;
        }

        boolean weaponUser = !fighter.getMainHandItem().isEmpty()
                && (FighterArsenalManager.isSword(fighter.getMainHandItem())
                    || com.dragonminez.common.combat.logic.weapon.WeaponRegistry.getAttributes(fighter.getMainHandItem()) != null);

        // Armed fighters get an actual held-weapon melee action instead of starting DMZ's
        // unarmed/large-form combo choreography while merely displaying the weapon in their hand.
        if (weaponUser && distanceSq <= 20.25D && !fighter.isComboing()) {
            fighter.getLookControl().setLookAt(target, 40.0F, 40.0F);
            fighter.performWeaponCombatStrikeAnimation();
            fighter.doHurtTarget(target);
            state.actionCooldown = switch (fighter.getRank()) {
                case ROOKIE -> 22;
                case TRAINED -> 18;
                case VETERAN -> 14;
            };
            return;
        }

        if (!weaponUser && distanceSq <= 30.0D && fighter.isComboReady()) {
            int combo = chooseCloseCombo(fighter, target, state, phase);
            if (combo >= 0) {
                fighter.startCombo(combo);
                rememberCombo(state, combo);
                state.actionCooldown = phase >= 2 ? 16 : 30;
                return;
            }
        }

        // A fighter carrying a registered melee weapon closes specifically into weapon range
        // instead of behaving like a Ki specialist with a decorative weapon.

        if (weaponUser && distanceSq > 30.0D && distanceSq <= 196.0D) {
            if (fighter.canFly() && (fighter.isFlying() || Math.abs(target.getY() - fighter.getY()) > 2.5D)) {
                fighter.setFlying(true);
                fighter.setFlyingFast(phase >= 1 || fighter.getArchetype() == FighterArchetype.SPEEDSTER);
                fighter.moveTowardsTargetInAir(target);
            } else {
                fighter.setLocomotionMode(DBSagasEntity.LocomotionMode.RUN);
                fighter.getNavigation().moveTo(target, fighter.getArchetype() == FighterArchetype.SPEEDSTER ? 1.34D : 1.20D);
            }
            if (phase >= 1 && fighter.getArchetype() == FighterArchetype.SPEEDSTER
                    && distanceSq >= 64.0D && fighter.isZanzokenReady() && fighter.getRandom().nextFloat() < 0.22F) {
                fighter.performProactiveTeleport(target);
            }
            state.actionCooldown = 6;
            return;
        }

        if (phase >= 1 && distanceSq > 32.0D && fighter.hasSkillReady()) {
            DBSagasEntity.KiSkill skill = chooseReadySkill(fighter, distanceSq, false, state);
            if (skill != null) {
                if (isMajor(skill) && state.dialogueCooldown <= 0 && fighter.getRandom().nextFloat() < 0.30F) {
                    fighter.speak(FighterDialogue.major(fighter.getRandom(), fighter.getPersonality()), 42);
                    state.dialogueCooldown = 105;
                }
                fighter.startSkill(skill);
                applyControlSkillPenalty(skill);
                rememberSkill(state, skill);
                state.majorCastPending = isMajor(skill);
                state.actionCooldown = isMajor(skill) ? 38 : 24;
                return;
            }
        }

        if (shouldChargeNormally(fighter, target, state, distanceSq, phase)) {
            beginDramaticCharge(fighter, target, state, Math.max(1, phase));
        }
    }

    private static void startStandoff(AmbientFighterEntity fighter, LivingEntity target, FightState state) {
        if (!(target instanceof AmbientFighterEntity other)) return;
        // Never freeze two fighters who acquired each other while already face-to-face.
        if (fighter.distanceToSqr(target) < 36.0D) return;
        int base = switch (fighter.getPersonality()) {
            case AGGRESSIVE -> 7;
            case HEROIC -> 13;
            case CAUTIOUS -> 18;
            case CALM -> 20;
            case PROUD -> 25;
        };
        state.standoffTicks = base + fighter.getRandom().nextInt(11);
        state.actionCooldown = Math.max(state.actionCooldown, state.standoffTicks);
        if (fighter.getRandom().nextFloat() < 0.74F && fighter.getSpeech().isEmpty()) {
            fighter.speak(FighterDialogue.opening(fighter.getRandom(), fighter.getAlignment(), fighter.getPersonality()), 52);
            queueSpeech(other, FighterDialogue.openingReply(other.getRandom(), other.getAlignment(), other.getPersonality()),
                    18 + other.getRandom().nextInt(18));
        }
    }

    private static void holdDramaticPose(AmbientFighterEntity fighter, LivingEntity target) {
        fighter.getNavigation().stop();
        fighter.setAttacking(false);
        fighter.setLocomotionMode(DBSagasEntity.LocomotionMode.IDLE);
        fighter.rotateBodyToTarget(target);
    }

    private static void updateCastTransition(AmbientFighterEntity fighter, FightState state) {
        boolean casting = fighter.isCasting();
        if (state.selfWasCasting && !casting && state.majorCastPending) {
            state.pauseTicks = Math.max(state.pauseTicks, 5 + fighter.getRandom().nextInt(7));
            state.majorCastPending = false;
        }
        state.selfWasCasting = casting;
    }

    private static void handleHealthDialogue(AmbientFighterEntity fighter, FightState state, float hp) {
        if (!state.lowHealthLine && hp < 0.32F && state.dialogueCooldown <= 0) {
            fighter.speak(FighterDialogue.lowHealth(fighter.getRandom(), fighter.getPersonality()), 55);
            state.lowHealthLine = true;
            state.dialogueCooldown = 125;
        }
        if (!state.midFightLine && state.fightTicks > 170 && state.dialogueCooldown <= 0
                && fighter.getRandom().nextFloat() < 0.10F) {
            fighter.speak(FighterDialogue.midfight(fighter.getRandom(), fighter.getPersonality()), 46);
            state.midFightLine = true;
            state.dialogueCooldown = 140;
        }
    }

    private static void tickDramaticCharge(AmbientFighterEntity fighter, LivingEntity target,
                                           FightState state, double distanceSq) {
        state.scriptedChargeTicks--;
        fighter.setKiCharge(true);
        holdDramaticPose(fighter, target);

        // Proud fighters will sometimes stubbornly hold the charge under pressure;
        // everyone else aborts when the opponent genuinely closes in.
        boolean closeDanger = distanceSq < 30.25D;
        boolean castDanger = target instanceof DBSagasEntity saga && saga.isCasting()
                && fighter.getPersonality() != FighterPersonality.CALM;
        if (closeDanger || castDanger || state.scriptedChargeTicks <= 0) {
            fighter.setKiCharge(false);
            state.scriptedChargeTicks = 0;
            state.chargeTicks = 0;
            state.actionCooldown = 10 + fighter.getRandom().nextInt(9);
            if (fighter.getRandom().nextFloat() < 0.34F) {
                fighter.speak(FighterDialogue.powerReady(fighter.getRandom(), fighter.getPersonality()), 34);
            }
        }
    }

    private static void beginDramaticCharge(AmbientFighterEntity fighter, LivingEntity target,
                                            FightState state, int phase) {
        int duration = 76 + fighter.getRandom().nextInt(38);
        if (fighter.getArchetype() == FighterArchetype.KI_SPECIALIST) duration += 20;
        if (fighter.getPersonality() == FighterPersonality.PROUD) duration += 18;
        if (phase >= 2) duration += 18;
        state.scriptedChargeTicks = Math.min(150, duration);
        state.chargeTicks = 0;
        state.actionCooldown = state.scriptedChargeTicks;
        fighter.setKiCharge(true);
        fighter.speak(FighterDialogue.powerUp(fighter.getRandom(), fighter.getPersonality()), 48);
        if (target instanceof AmbientFighterEntity other) {
            queueSpeech(other, FighterDialogue.powerUpReply(other.getRandom(), other.getPersonality()), 22 + other.getRandom().nextInt(20));
        }
    }

    private static boolean shouldStartPowerBeat(AmbientFighterEntity fighter, LivingEntity target,
                                                FightState state, double distanceSq, int phase) {
        if (phase <= 0 || fighter.getRank() == FighterRank.ROOKIE || fighter.isComboing()) return false;
        if (distanceSq < 64.0D || target instanceof DBSagasEntity saga && (saga.isCasting() || saga.isCharge())) return false;
        int bit = 1 << Math.min(phase, 3);
        if ((state.powerBeatMask & bit) != 0) return false;

        float chance = switch (fighter.getPersonality()) {
            case PROUD -> 0.62F;
            case CALM -> 0.48F;
            case AGGRESSIVE -> 0.34F;
            case HEROIC -> 0.43F;
            case CAUTIOUS -> 0.38F;
        };
        if (fighter.getArchetype() == FighterArchetype.KI_SPECIALIST) chance += 0.22F;
        if (phase >= 3) chance += 0.12F;
        if (fighter.getRandom().nextFloat() >= chance) return false;

        state.powerBeatMask |= bit;
        return true;
    }

    private static void reactToCharge(AmbientFighterEntity fighter, LivingEntity target,
                                      FightState state, double distanceSq, int phase) {
        if (state.actionCooldown > 0) return;

        // Proud fighters sometimes deliberately allow the opponent to power up.
        if (distanceSq >= 64.0D
                && fighter.getPersonality() == FighterPersonality.PROUD
                && fighter.getRandom().nextFloat() < 0.62F) {
            fighter.speak(FighterDialogue.letThemCharge(fighter.getRandom()), 44);
            state.pauseTicks = 12 + fighter.getRandom().nextInt(13);
            state.actionCooldown = state.pauseTicks;
            return;
        }

        // Ki specialists and calm veterans sometimes answer theatrics with theatrics.
        if (distanceSq > 49.0D
                && (fighter.getArchetype() == FighterArchetype.KI_SPECIALIST || fighter.getPersonality() == FighterPersonality.CALM)
                && fighter.getRank() != FighterRank.ROOKIE
                && fighter.getRandom().nextFloat() < 0.46F) {
            fighter.speak(FighterDialogue.mirrorCharge(fighter.getRandom()), 42);
            state.scriptedChargeTicks = 42 + fighter.getRandom().nextInt(28);
            state.actionCooldown = state.scriptedChargeTicks;
            fighter.setKiCharge(true);
            return;
        }

        if (state.dialogueCooldown <= 0 && fighter.getRandom().nextFloat() < 0.44F) {
            fighter.speak(FighterDialogue.chargeReaction(fighter.getRandom(), fighter.getPersonality()), 43);
            state.dialogueCooldown = 90;
        }

        if (fighter.getArchetype() == FighterArchetype.SPEEDSTER && fighter.isZanzokenReady()) {
            fighter.performProactiveTeleport(target);
            state.actionCooldown = 12;
            if (fighter.isComboReady() && distanceSq <= 100.0D) {
                int combo = DBSagasEntity.ComboType.KI_CHARGE_ATTACK.getId();
                fighter.startCombo(combo);
                rememberCombo(state, combo);
                state.actionCooldown = 36;
            }
            return;
        }

        if (distanceSq <= 64.0D && fighter.isComboReady()
                && (fighter.getPersonality() == FighterPersonality.AGGRESSIVE
                || fighter.getPersonality() == FighterPersonality.HEROIC
                || phase >= 2)) {
            int combo = DBSagasEntity.ComboType.KI_CHARGE_ATTACK.getId();
            fighter.startCombo(combo);
            rememberCombo(state, combo);
            state.actionCooldown = 36;
            return;
        }

        DBSagasEntity.KiSkill punish = chooseReadySkill(fighter, distanceSq, true, state);
        if (punish != null) {
            fighter.startSkill(punish);
            applyControlSkillPenalty(punish);
            rememberSkill(state, punish);
            state.majorCastPending = isMajor(punish);
            state.actionCooldown = 38;
        }
    }

    private static void reactToCast(AmbientFighterEntity fighter, LivingEntity target,
                                    FightState state, double distanceSq) {
        if (state.actionCooldown > 0) return;

        // If the opponent is committing to a major beam, answer with a beam when
        // possible instead of always taking the safe barrier/teleport response.
        if (target instanceof DBSagasEntity sagaTarget && isCastingClashBeam(sagaTarget)
                && distanceSq >= 49.0D && distanceSq <= 900.0D) {
            DBSagasEntity.KiSkill answer = findReadyClashBeam(fighter);
            if (answer != null && fighter.getRandom().nextFloat() < 0.82F) {
                fighter.rotateBodyToTarget(target);
                fighter.speak(fighter.getPersonality() == FighterPersonality.PROUD ? "Then clash with me!" : "I'll meet it head-on!", 44);
                fighter.startSkill(answer);
            applyControlSkillPenalty(answer);
                rememberSkill(state, answer);
                state.majorCastPending = true;
                state.actionCooldown = 70;
                state.clashBeatUsed = true;
                return;
            }
        }

        if (state.dialogueCooldown <= 0 && fighter.getRandom().nextFloat() < 0.42F) {
            fighter.speak(FighterDialogue.castReaction(fighter.getRandom(), fighter.getPersonality()), 40);
            state.dialogueCooldown = 90;
        }

        DBSagasEntity.KiSkill defensive = findReadyByRole(fighter, DBSagasEntity.SkillRole.DEFENSIVE);
        if (defensive != null && distanceSq > 12.0D) {
            fighter.startSkill(defensive);
            applyControlSkillPenalty(defensive);
            rememberSkill(state, defensive);
            state.actionCooldown = 46;
            return;
        }

        if ((fighter.getArchetype() == FighterArchetype.SPEEDSTER || fighter.getRank() == FighterRank.VETERAN)
                && fighter.isZanzokenReady()) {
            fighter.performProactiveTeleport(target);
            state.actionCooldown = 32;
            return;
        }

        if (fighter.getPersonality() == FighterPersonality.AGGRESSIVE && distanceSq <= 49.0D && fighter.isComboReady()) {
            int combo = DBSagasEntity.ComboType.KI_CHARGE_ATTACK.getId();
            fighter.startCombo(combo);
            rememberCombo(state, combo);
            state.actionCooldown = 35;
        }
        // Otherwise DMZ's native evade / Wild Sense system gets the response window.
    }

    private static void reactToOpponentRecovery(AmbientFighterEntity fighter, LivingEntity target,
                                                  FightState state, double distanceSq, int phase) {
        if (state.actionCooldown > 0 || fighter.isCasting() || fighter.isCharge() || fighter.isComboing()) return;

        FighterArchetype style = fighter.getArchetype();
        boolean counterStyle = style == FighterArchetype.SPEEDSTER
                || style == FighterArchetype.MARTIAL_ARTIST
                || fighter.getPersonality() == FighterPersonality.AGGRESSIVE;

        if (counterStyle && distanceSq <= 100.0D && fighter.isComboReady()
                && fighter.getRandom().nextFloat() < 0.52F) {
            if (style == FighterArchetype.SPEEDSTER && distanceSq > 20.0D && fighter.isZanzokenReady()) {
                fighter.performProactiveTeleport(target);
            }
            int combo = chooseCloseCombo(fighter, target, state, Math.max(1, phase));
            fighter.startCombo(combo);
            rememberCombo(state, combo);
            if (state.dialogueCooldown <= 0 && fighter.getRandom().nextFloat() < 0.30F) {
                fighter.speak(fighter.getPersonality() == FighterPersonality.CALM ? "My turn." : "Now!", 30);
                state.dialogueCooldown = 80;
            }
            state.actionCooldown = 24;
            return;
        }

        if (distanceSq > 64.0D && fighter.getArchetype() == FighterArchetype.KI_SPECIALIST
                && fighter.hasSkillReady() && fighter.getRandom().nextFloat() < 0.30F) {
            DBSagasEntity.KiSkill reply = chooseReadySkill(fighter, distanceSq, true, state);
            if (reply != null) {
                fighter.startSkill(reply);
                applyControlSkillPenalty(reply);
                rememberSkill(state, reply);
                state.majorCastPending = isMajor(reply);
                state.actionCooldown = 30;
            }
        }
    }

    private static void reactAfterCharge(AmbientFighterEntity fighter, LivingEntity target,
                                         FightState state, double distanceSq) {
        if (state.actionCooldown > 0 || fighter.isCasting() || fighter.isCharge() || fighter.isComboing()) return;
        if (distanceSq > 81.0D || !fighter.isComboReady()) return;

        boolean pressesOpening = fighter.getPersonality() == FighterPersonality.AGGRESSIVE
                || fighter.getArchetype() == FighterArchetype.SPEEDSTER;
        if (!pressesOpening || fighter.getRandom().nextFloat() >= 0.38F) return;

        int combo = fighter.getArchetype() == FighterArchetype.SPEEDSTER
                ? DBSagasEntity.ComboType.RAPID_KICKS.getId()
                : DBSagasEntity.ComboType.BASIC.getId();
        fighter.startCombo(combo);
        rememberCombo(state, combo);
        state.actionCooldown = 24;
    }

    /**
     * A single quiet beat in the middle of a duel. It only happens with real distance
     * between the actors, so it reads as tension instead of an AI stall.
     */
    private static boolean tryStartSuspenseBeat(AmbientFighterEntity fighter, LivingEntity target,
                                                FightState state, double distanceSq, int phase) {
        if (phase < 1 || state.fightTicks < 130 || state.actionCooldown > 0) return false;
        if (distanceSq < 64.0D || distanceSq > 324.0D) return false;
        if (!(target instanceof AmbientFighterEntity other) || other.getTarget() != fighter) return false;
        if (fighter.getUUID().compareTo(other.getUUID()) >= 0) return false;
        if (fighter.isCasting() || fighter.isCharge() || fighter.isComboing() || fighter.isAwakening()) return false;
        if (other.isCasting() || other.isCharge() || other.isComboing() || other.isAwakening()) return false;

        // Stable per-pair eligibility prevents repeated RNG checks from making the
        // suspense beat effectively guaranteed in every sufficiently long fight.
        int pairRoll = Math.floorMod(fighter.getUUID().hashCode() ^ other.getUUID().hashCode(), 100);
        if (pairRoll >= 12) {
            state.suspenseBeatUsed = true;
            return false;
        }

        FightState otherState = STATES.computeIfAbsent(other.getUUID(), ignored -> new FightState());
        otherState.lastTouched = fighter.level().getGameTime();
        if (otherState.opponent == null || !fighter.getUUID().equals(otherState.opponent)) {
            otherState.resetFor(fighter.getUUID());
            otherState.combatBaseY = Math.min(fighter.getY(), other.getY());
        }

        int duration = 100 + fighter.getRandom().nextInt(101);
        state.suspenseBeatUsed = true;
        otherState.suspenseBeatUsed = true;
        state.pauseTicks = duration;
        otherState.pauseTicks = duration;
        state.actionCooldown = duration;
        otherState.actionCooldown = duration;
        // Some standoffs end with one or both fighters deliberately powering up before they re-engage.
        if (fighter.getRandom().nextFloat() < 0.55F) {
            state.scriptedChargeTicks = 36 + fighter.getRandom().nextInt(35);
            if (fighter.getRandom().nextFloat() < 0.65F) otherState.scriptedChargeTicks = 32 + fighter.getRandom().nextInt(31);
        }

        if (state.dialogueCooldown <= 0) {
            String line = switch (fighter.getPersonality()) {
                case PROUD -> "You're still holding something back.";
                case CALM -> "Your rhythm changed.";
                case AGGRESSIVE -> "What are you waiting for?";
                case HEROIC -> "This isn't over.";
                case CAUTIOUS -> "Something's different...";
            };
            fighter.speak(line, 38);
            queueSpeech(other, switch (other.getPersonality()) {
                case PROUD -> "Then make me show it.";
                case CALM -> "You noticed.";
                case AGGRESSIVE -> "Come find out!";
                case HEROIC -> "Neither am I.";
                case CAUTIOUS -> "Stay ready.";
            }, 12 + other.getRandom().nextInt(10));
            state.dialogueCooldown = 120;
            otherState.dialogueCooldown = 120;
        }
        return true;
    }

    /**
     * Keeps aerial combat in a Dragon Ball-looking band above the encounter instead of
     * allowing two native flight AIs to chase one another upward forever.
     */
    private static void stabilizeFlightAltitude(AmbientFighterEntity fighter, LivingEntity target, FightState state) {
        if (!fighter.isFlying() || fighter.isTransforming() || BeamClashManager.isClashing(fighter.getUUID())) return;
        if (Double.isNaN(state.combatBaseY)) state.combatBaseY = Math.min(fighter.getY(), target.getY());

        double above = fighter.getY() - state.combatBaseY;
        if (above <= SOFT_AERIAL_CEILING) return;

        fighter.setFlyingFast(false);
        var motion = fighter.getDeltaMovement();
        double desiredY = above > HARD_AERIAL_CEILING ? -0.20D : -0.075D;
        if (motion.y > desiredY) {
            fighter.setDeltaMovement(motion.x * 0.96D, desiredY, motion.z * 0.96D);
        }

        // When both actors are above the soft ceiling, the pair is encouraged downward
        // together rather than one repeatedly chasing the other higher.
        if (target.getY() > state.combatBaseY + SOFT_AERIAL_CEILING && above > HARD_AERIAL_CEILING) {
            fighter.setDeltaMovement(fighter.getDeltaMovement().add(0.0D, -0.05D, 0.0D));
        }
    }

    private static boolean avoidImmediateKiThreat(AmbientFighterEntity fighter, LivingEntity target, FightState state) {
        AbstractKiProjectile threat = fighter.level().getEntitiesOfClass(AbstractKiProjectile.class, fighter.getBoundingBox().inflate(8.0D),
                        ki -> ki.isAlive() && ki.getOwner() != null && ki.getOwner() != fighter)
                .stream().min(java.util.Comparator.comparingDouble(fighter::distanceToSqr)).orElse(null);
        if (threat == null) return false;
        EntityOwnerRelation relation = ownerRelation(fighter, threat);
        if (relation == EntityOwnerRelation.FRIENDLY) return false;
        Vec3 away = fighter.position().subtract(threat.position());
        if (away.lengthSqr() < 0.001D && target != null) away = fighter.position().subtract(target.position());
        if (away.lengthSqr() < 0.001D) away = new Vec3(1.0D, 0.0D, 0.0D);
        Vec3 flat = new Vec3(away.x, 0.0D, away.z).normalize();
        Vec3 side = new Vec3(-flat.z, 0.0D, flat.x);
        if (fighter.getRandom().nextBoolean()) side = side.scale(-1.0D);
        double speed = fighter.getRank() == FighterRank.VETERAN ? 0.78D : 0.62D;
        fighter.getNavigation().stop();
        fighter.setDeltaMovement(fighter.getDeltaMovement().add(side.x * speed, fighter.isFlying() ? 0.06D : 0.18D, side.z * speed));
        if (fighter.hasFlightUnlocked() && (fighter.isFlying() || threat.getY() > fighter.getY() + 1.5D)) fighter.setFlying(true);
        state.actionCooldown = Math.max(state.actionCooldown, 12);
        if (state.dialogueCooldown <= 0 && fighter.getRandom().nextFloat() < 0.14F) {
            fighter.speak(fighter.getRandom().nextBoolean() ? "Not walking into that." : "Too close—move!", 38);
            state.dialogueCooldown = 90;
        }
        return true;
    }

    private enum EntityOwnerRelation { FRIENDLY, HOSTILE }
    private static EntityOwnerRelation ownerRelation(AmbientFighterEntity fighter, AbstractKiProjectile projectile) {
        var owner = projectile.getOwner();
        if (owner == fighter) return EntityOwnerRelation.FRIENDLY;
        if (owner instanceof AmbientFighterEntity other) {
            if (other == fighter || FactionManager.areAllies(fighter, other) || FighterNpcSocialManager.bond(fighter, other) >= 6)
                return EntityOwnerRelation.FRIENDLY;
        }
        return EntityOwnerRelation.HOSTILE;
    }

    private static void applyControlSkillPenalty(DBSagasEntity.KiSkill skill) {
        if (skill == null) return;
        // Oozaru Roar, Blue Hurricane and Majin Candy are the native saga skills most able
        // to lock a target down. LW fighters wait roughly three times longer before reusing them.
        if (skill.id == 7 || skill.id == 12 || skill.id == 19)
            skill.currentCooldown = Math.max(skill.currentCooldown, skill.cooldownMax * 3);
    }

    private static void rememberSkill(FightState state, DBSagasEntity.KiSkill skill) {
        if (skill == null) return;
        if (state.lastSkillId == skill.id) state.sameSkillStreak++;
        else state.sameSkillStreak = 1;
        state.lastSkillId = skill.id;
        state.lastSkillTick = state.fightTicks;
    }

    private static void rememberCombo(FightState state, int comboId) {
        state.lastComboId = comboId;
        state.lastComboTick = state.fightTicks;
    }

    private static void stageOpeningCooldowns(AmbientFighterEntity fighter) {
        for (DBSagasEntity.KiSkill skill : fighter.getSkillPool()) {
            if (skill.role == DBSagasEntity.SkillRole.RANGED_TRAVEL
                    || skill.role == DBSagasEntity.SkillRole.HITSCAN
                    || skill.role == DBSagasEntity.SkillRole.AOE_BURST
                    || skill.role == DBSagasEntity.SkillRole.ZONING) {
                skill.currentCooldown = Math.max(skill.currentCooldown, 105);
            }
        }
    }

    private static void enableEscalatedCombos(AmbientFighterEntity fighter) {
        if (fighter.getRank() == FighterRank.ROOKIE) return;
        int cooldown = fighter.getRank() == FighterRank.VETERAN ? 32 : 48;
        int variant = combatVariant(fighter);
        // AIR is intentionally excluded from DMZ's native random combo pool. Living World invokes
        // it explicitly through a very rare aerial gate below, otherwise DMZ's own combat brain can
        // select the stun combo independently and make the configured rarity meaningless.
        switch (fighter.getArchetype()) {
            case BRAWLER -> fighter.setAllowedCombos(cooldown,
                    DBSagasEntity.ComboType.BASIC,
                    variant % 2 == 0 ? DBSagasEntity.ComboType.METEOR_COMBINATION : DBSagasEntity.ComboType.RAPID_KICKS);
            case MARTIAL_ARTIST -> fighter.setAllowedCombos(cooldown,
                    DBSagasEntity.ComboType.BASIC,
                    DBSagasEntity.ComboType.RAPID_KICKS,
                    DBSagasEntity.ComboType.METEOR_COMBINATION);
            case GUARDIAN -> fighter.setAllowedCombos(cooldown + 7,
                    DBSagasEntity.ComboType.BASIC,
                    DBSagasEntity.ComboType.METEOR_COMBINATION);
            case KI_SPECIALIST -> fighter.setAllowedCombos(cooldown + 10,
                    DBSagasEntity.ComboType.BASIC,
                    DBSagasEntity.ComboType.RAPID_KICKS);
            case SPEEDSTER -> fighter.setAllowedCombos(cooldown,
                    DBSagasEntity.ComboType.BASIC,
                    DBSagasEntity.ComboType.RAPID_KICKS);
        }
    }

    private static int chooseCloseCombo(AmbientFighterEntity fighter, LivingEntity target,
                                        FightState state, int phase) {
        FighterArchetype style = fighter.getArchetype();
        int variant = combatVariant(fighter);

        boolean aerial = fighter.canFly() && (fighter.isFlying()
                || target.getY() - fighter.getY() > 2.5D
                || target instanceof DBSagasEntity saga && saga.isFlying());
        int airCombo = DBSagasEntity.ComboType.AIR.getId();
        boolean airRecentlyUsed = state.lastComboId == airCombo && state.fightTicks - state.lastComboTick < 600;
        boolean targetAlreadyStunned = target.hasEffect(MainEffects.STUN.get());
        if (aerial && phase >= 1 && !airRecentlyUsed && !targetAlreadyStunned
                && fighter.getRandom().nextFloat() < 0.015F) {
            return airCombo;
        }

        if (phase <= 0) return DBSagasEntity.ComboType.BASIC.getId();

        if (phase >= 2 && fighter.getRank() != FighterRank.ROOKIE) {
            if ((style == FighterArchetype.BRAWLER || style == FighterArchetype.GUARDIAN)
                    && fighter.getRandom().nextFloat() < 0.68F) {
                return DBSagasEntity.ComboType.METEOR_COMBINATION.getId();
            }
            if (style == FighterArchetype.MARTIAL_ARTIST && variant % 3 == 0
                    && fighter.getRandom().nextFloat() < 0.58F) {
                return DBSagasEntity.ComboType.METEOR_COMBINATION.getId();
            }
        }

        if (style == FighterArchetype.SPEEDSTER || style == FighterArchetype.MARTIAL_ARTIST
                || (style == FighterArchetype.BRAWLER && (variant & 1) == 1)) {
            return DBSagasEntity.ComboType.RAPID_KICKS.getId();
        }
        return DBSagasEntity.ComboType.BASIC.getId();
    }

    private static DBSagasEntity.KiSkill chooseReadySkill(AmbientFighterEntity fighter,
                                                           double distanceSq, boolean fastOnly,
                                                           FightState state) {
        DBSagasEntity.KiSkillType signature = signatureType(fighter);
        DBSagasEntity.KiSkill best = null;
        int bestScore = Integer.MIN_VALUE;

        for (DBSagasEntity.KiSkill skill : fighter.getSkillPool()) {
            if (skill.currentCooldown > 0 || skill.role == DBSagasEntity.SkillRole.DEFENSIVE) continue;
            if (fastOnly && skill.role != DBSagasEntity.SkillRole.PROJECTILE_FAST
                    && skill.role != DBSagasEntity.SkillRole.HITSCAN) continue;

            int score = 0;
            if (FighterArsenalManager.isSword(fighter.getMainHandItem())) score -= 6;
            DBSagasEntity.KiSkillType type = DBSagasEntity.KiSkillType.fromId(skill.id);
            if (type == signature) score += 5; // preference, not repetition lock-in

            if (distanceSq > 225.0D) {
                if (skill.role == DBSagasEntity.SkillRole.RANGED_TRAVEL) score += 7;
                if (skill.role == DBSagasEntity.SkillRole.HITSCAN) score += 6;
                if (skill.role == DBSagasEntity.SkillRole.ZONING) score += 2;
            } else if (distanceSq > 81.0D) {
                if (skill.role == DBSagasEntity.SkillRole.PROJECTILE_FAST) score += 6;
                if (skill.role == DBSagasEntity.SkillRole.RANGED_TRAVEL) score += 5;
                if (skill.role == DBSagasEntity.SkillRole.HITSCAN) score += 4;
                if (skill.role == DBSagasEntity.SkillRole.ZONING) score += 4;
            } else {
                if (skill.role == DBSagasEntity.SkillRole.AOE_BURST) score += 7;
                if (skill.role == DBSagasEntity.SkillRole.PROJECTILE_FAST) score += 5;
                if (skill.role == DBSagasEntity.SkillRole.ZONING) score += 4;
            }

            // Short-term move memory: a signature remains recognizable, but repeating
            // the exact same wave/volley over and over becomes increasingly unattractive.
            if (skill.id == state.lastSkillId) {
                int since = state.fightTicks - state.lastSkillTick;
                if (since < 150) score -= 18;
                if (state.sameSkillStreak >= 2) score -= 28;
            } else if (state.lastSkillId >= 0) {
                score += 2;
            }

            score += fighter.getRandom().nextInt(3); // tiny uncertainty, not move roulette
            if (score > bestScore) {
                bestScore = score;
                best = skill;
            }
        }
        return best;
    }

    private static DBSagasEntity.KiSkillType signatureType(AmbientFighterEntity fighter) {
        int variant = combatVariant(fighter);
        if (fighter.getRank() == FighterRank.VETERAN && variant >= 6) return finisherFor(fighter, variant);
        return switch (fighter.getArchetype()) {
            case BRAWLER -> variant >= 6 ? finisherFor(fighter, variant) : null;
            case MARTIAL_ARTIST -> switch (variant % 3) {
                case 0 -> DBSagasEntity.KiSkillType.KI_SMALL;
                case 1 -> clashBeamFor(fighter, variant);
                default -> DBSagasEntity.KiSkillType.KI_VOLLEY;
            };
            case KI_SPECIALIST -> clashBeamFor(fighter, variant);
            case SPEEDSTER -> switch (variant % 3) {
                case 0 -> DBSagasEntity.KiSkillType.KI_LASER;
                case 1 -> DBSagasEntity.KiSkillType.KI_SMALL;
                default -> DBSagasEntity.KiSkillType.KI_VOLLEY;
            };
            case GUARDIAN -> DBSagasEntity.KiSkillType.KI_BARRIER;
        };
    }

    /**
     * Prevents the one failure mode that most visibly breaks the choreography:
     * both actors occupying melee range while the Living World director is waiting.
     *
     * DMZ's own combat remains authoritative; this watchdog only clears our cinematic
     * locks and, after a brief genuinely-idle window, gives native combat an explicit
     * nudge to resume.
     */
    private static void tickCloseQuartersWatchdog(AmbientFighterEntity fighter, LivingEntity target,
                                                   FightState state, double distanceSq) {
        boolean close = distanceSq <= 12.25D;
        boolean targetBusy = target instanceof DBSagasEntity saga
                && (saga.isCasting() || saga.isComboing() || saga.isCharge() || saga.isTransforming());
        boolean selfBusy = fighter.isCasting() || fighter.isComboing() || fighter.isCharge() || fighter.isTransforming();

        if (!close || selfBusy || targetBusy) {
            state.closeIdleTicks = 0;
            return;
        }

        state.closeIdleTicks++;

        // No multi-second theatrical locks at point-blank range.
        state.standoffTicks = 0;
        state.pauseTicks = 0;
        state.scriptedChargeTicks = 0;
        if (fighter.isCharge()) fighter.setKiCharge(false);
        if (state.actionCooldown > 0) state.actionCooldown = Math.max(0, state.actionCooldown - 2);

        if (state.closeIdleTicks < 7) return;
        state.closeIdleTicks = 0;
        state.actionCooldown = 0;
        fighter.setLocomotionMode(DBSagasEntity.LocomotionMode.RUN);

        int currentPhase = phase(state.fightTicks,
                fighter.getMaxHealth() <= 0.0F ? 1.0F : fighter.getHealth() / fighter.getMaxHealth());

        // Prefer a real native DMZ combo. This makes a stalled face-to-face exchange
        // immediately become combat rather than a synthetic shove.
        if (fighter.isComboReady()) {
            int combo = chooseCloseCombo(fighter, target, state, Math.max(1, currentPhase));
            if (combo < 0) combo = DBSagasEntity.ComboType.BASIC.getId();
            fighter.startCombo(combo);
            rememberCombo(state, combo);
            state.actionCooldown = currentPhase >= 2 ? 12 : 18;
            return;
        }

        // A speed/veteran fighter can break the collision with DMZ's actual teleport.
        if ((fighter.getArchetype() == FighterArchetype.SPEEDSTER || fighter.getRank() == FighterRank.VETERAN)
                && fighter.isZanzokenReady()) {
            fighter.performProactiveTeleport(target);
            state.actionCooldown = 8;
            return;
        }

        // Last resort: a tiny opposing burst creates enough room for native navigation
        // to resume. This is intentionally much smaller than the cinematic launches.
        separateFromTarget(fighter, target, 0.22D, 0.04D);
        state.actionCooldown = 4;
    }

    private static void separateFromTarget(AmbientFighterEntity fighter, LivingEntity target,
                                           double horizontal, double vertical) {
        double dx = fighter.getX() - target.getX();
        double dz = fighter.getZ() - target.getZ();
        double length = Math.sqrt(dx * dx + dz * dz);

        if (length < 0.05D) {
            // Deterministic opposite side-step so two identical AI ticks do not choose
            // exactly the same escape vector.
            double angle = Math.toRadians(Math.floorMod(fighter.getUUID().hashCode(), 360));
            dx = Math.cos(angle);
            dz = Math.sin(angle);
            length = 1.0D;
        }

        fighter.setDeltaMovement(fighter.getDeltaMovement().add(
                dx / length * horizontal,
                vertical,
                dz / length * horizontal));
    }

    /**
     * One deliberately theatrical vertical breakout per capable high-level duel.
     * The director only supplies the launch; DMZ owns the resulting flight pursuit.
     */
    private static boolean tryStartAerialBreak(AmbientFighterEntity fighter, LivingEntity target,
                                               FightState state, double distanceSq, int phase) {
        if (phase < 2 || state.fightTicks < 165 || distanceSq > 49.0D) return false;
        if (!(target instanceof AmbientFighterEntity other)) return false;
        if (!fighter.canFly() || !other.canFly() || fighter.isFlying() || other.isFlying()) return false;
        if (fighter.isCasting() || other.isCasting() || fighter.isComboing() || other.isComboing()) return false;
        if (fighter.getUUID().compareTo(other.getUUID()) >= 0) return false;
        if (fighter.getRandom().nextFloat() > 0.34F) return false;

        FightState otherState = STATES.computeIfAbsent(other.getUUID(), ignored -> new FightState());
        otherState.lastTouched = fighter.level().getGameTime();
        if (otherState.opponent == null || !fighter.getUUID().equals(otherState.opponent)) {
            otherState.resetFor(fighter.getUUID());
        }

        state.aerialBreakUsed = true;
        otherState.aerialBreakUsed = true;
        state.pauseTicks = 0;
        otherState.pauseTicks = 0;
        state.standoffTicks = 0;
        otherState.standoffTicks = 0;

        fighter.setFlying(true);
        other.setFlying(true);
        fighter.setFlyingFast(true);
        other.setFlyingFast(true);

        // Split them vertically and slightly horizontally so the next DMZ chase reads
        // as a sudden skyward exchange instead of synchronized levitation.
        separateFromTarget(fighter, other, 0.34D, 0.72D);
        separateFromTarget(other, fighter, 0.26D, 0.96D);

        fighter.speak(fighter.getPersonality() == FighterPersonality.PROUD
                ? "Keep up." : "We're taking this higher!", 38);
        queueSpeech(other, other.getPersonality() == FighterPersonality.AGGRESSIVE
                ? "Don't run from me!" : "Fine by me.", 14);

        state.actionCooldown = 8;
        otherState.actionCooldown = 8;
        return true;
    }

    private static boolean shouldAwaken(AmbientFighterEntity fighter, FightState state, float hp, int phase) {
        if (fighter.isAwakened() || fighter.isRacialFormActive() || fighter.isAwakening() || phase < 2 || fighter.getRank() == FighterRank.ROOKIE) return false;
        if (fighter.isCasting() || fighter.isComboing() || fighter.isCharge()) return false;
        if (!awakeningEligible(fighter)) return false;

        // Awakening should feel earned by pressure, not like a mandatory checkpoint.
        if (fighter.getRank() == FighterRank.VETERAN) {
            return hp < 0.56F || state.fightTicks > 390;
        }
        return hp < 0.34F && state.fightTicks > 250;
    }

    private static boolean awakeningEligible(AmbientFighterEntity fighter) {
        int mixed = fighter.getUUID().hashCode();
        mixed = 31 * mixed + fighter.getRace().id() * 37;
        mixed = 31 * mixed + fighter.getPersonality().ordinal() * 19;
        int roll = Math.floorMod(mixed, 100);

        int chance = fighter.getRank() == FighterRank.VETERAN ? 58 : 20;
        if (fighter.getPersonality() == FighterPersonality.PROUD) chance += 8;
        if (fighter.getPersonality() == FighterPersonality.CAUTIOUS) chance -= 7;
        return roll < chance;
    }

    private static boolean tryStartMutualBeamClash(AmbientFighterEntity fighter, LivingEntity target,
                                                    FightState state, double distanceSq, int phase) {
        if (phase < 1 || state.fightTicks < 145 || distanceSq < 64.0D || distanceSq > 1225.0D) return false;
        if (!(target instanceof AmbientFighterEntity other) || other.isDefeated() || other.isAwakening()) return false;
        if (fighter.getUUID().compareTo(other.getUUID()) >= 0) return false; // one coordinator only
        if (fighter.isCasting() || other.isCasting() || fighter.isCharge() || other.isCharge()) return false;
        if (other.getTarget() != fighter) return false;

        DBSagasEntity.KiSkill ours = findReadyClashBeam(fighter);
        DBSagasEntity.KiSkill theirs = findReadyClashBeam(other);
        if (ours == null || theirs == null) return false;

        // Once conditions are right this is deliberately quite likely; 0.6.7's job is
        // to overshoot spectacle, not hide the game's clash system behind tiny RNG.
        if (fighter.getRandom().nextFloat() > 0.78F) return false;

        FightState otherState = STATES.computeIfAbsent(other.getUUID(), ignored -> new FightState());
        otherState.lastTouched = fighter.level().getGameTime();
        if (otherState.opponent == null || !fighter.getUUID().equals(otherState.opponent)) {
            otherState.resetFor(fighter.getUUID());
        }

        fighter.getNavigation().stop();
        other.getNavigation().stop();
        fighter.rotateBodyToTarget(other);
        other.rotateBodyToTarget(fighter);
        fighter.speak("Let's settle this in one shot!", 50);
        queueSpeech(other, other.getPersonality() == FighterPersonality.PROUD ? "Don't blink." : "I'm ready!", 18);

        fighter.startSkill(ours);
            applyControlSkillPenalty(ours);
        other.startSkill(theirs);
        applyControlSkillPenalty(theirs);
        rememberSkill(state, ours);
        rememberSkill(otherState, theirs);
        state.majorCastPending = true;
        otherState.majorCastPending = true;
        state.actionCooldown = 110;
        otherState.actionCooldown = 110;
        state.clashBeatUsed = true;
        otherState.clashBeatUsed = true;
        return true;
    }

    private static boolean isCastingClashBeam(DBSagasEntity entity) {
        if (!entity.isCasting()) return false;
        DBSagasEntity.KiSkillType type = DBSagasEntity.KiSkillType.fromId(entity.getSkillType());
        return type != null && type.getRole() == DBSagasEntity.SkillRole.RANGED_TRAVEL;
    }

    private static DBSagasEntity.KiSkill findReadyClashBeam(AmbientFighterEntity fighter) {
        for (DBSagasEntity.KiSkill skill : fighter.getSkillPool()) {
            if (skill.currentCooldown > 0) continue;
            DBSagasEntity.KiSkillType type = DBSagasEntity.KiSkillType.fromId(skill.id);
            if (type != null && type.getRole() == DBSagasEntity.SkillRole.RANGED_TRAVEL) return skill;
        }
        return null;
    }

    private static DBSagasEntity.KiSkill findReadyByRole(AmbientFighterEntity fighter, DBSagasEntity.SkillRole role) {
        for (DBSagasEntity.KiSkill skill : fighter.getSkillPool()) {
            if (skill.currentCooldown <= 0 && skill.role == role) return skill;
        }
        return null;
    }

    private static boolean shouldChargeNormally(AmbientFighterEntity fighter, LivingEntity target,
                                                FightState state, double distanceSq, int phase) {
        if (fighter.isComboing() || distanceSq < 121.0D || state.fightTicks < 70) return false;
        if (target instanceof DBSagasEntity saga && (saga.isCasting() || saga.isCharge())) return false;
        if (phase == 0 && fighter.getArchetype() != FighterArchetype.KI_SPECIALIST) return false;
        if (fighter.getRank() == FighterRank.ROOKIE && fighter.getArchetype() != FighterArchetype.KI_SPECIALIST) return false;
        // Charging may be theatrical, but still remains uncommon outside a real lull.
        return fighter.getRandom().nextFloat() < (fighter.getArchetype() == FighterArchetype.KI_SPECIALIST ? 0.38F : 0.19F);
    }

    private static boolean shouldPursueInAir(AmbientFighterEntity fighter, LivingEntity target,
                                              FightState state, double distanceSq) {
        if (distanceSq > 900.0D) return false;
        if (!Double.isNaN(state.combatBaseY) && fighter.getY() > state.combatBaseY + SOFT_AERIAL_CEILING) {
            return target.getY() + 3.0D < fighter.getY(); // only pursue downward from the ceiling band
        }
        if (target.getY() - fighter.getY() > 3.0D) return true;
        if (target instanceof DBSagasEntity saga && saga.isFlying()
                && (Double.isNaN(state.combatBaseY) || target.getY() <= state.combatBaseY + HARD_AERIAL_CEILING)) return true;
        return fighter.getRank() == FighterRank.VETERAN && fighter.getRandom().nextFloat() < 0.05F;
    }

    private static boolean isMajor(DBSagasEntity.KiSkill skill) {
        return skill.role == DBSagasEntity.SkillRole.RANGED_TRAVEL
                || skill.role == DBSagasEntity.SkillRole.HITSCAN
                || skill.role == DBSagasEntity.SkillRole.AOE_BURST;
    }

    private static int phase(int fightTicks, float healthRatio) {
        if (healthRatio < 0.28F || fightTicks > 520) return 3;
        if (healthRatio < 0.62F || fightTicks > 230) return 2;
        if (fightTicks > 70) return 1;
        return 0;
    }

    private static void queueSpeech(AmbientFighterEntity fighter, String text, int delayTicks) {
        if (fighter == null || text == null || text.isBlank()) return;
        long now = fighter.level().getGameTime();
        PENDING_SPEECH.put(fighter.getUUID(), new PendingSpeech(text, now + Math.max(1, delayTicks), now));
    }

    private static void deliverPendingSpeech(AmbientFighterEntity fighter) {
        PendingSpeech pending = PENDING_SPEECH.get(fighter.getUUID());
        if (pending == null || fighter.level().getGameTime() < pending.dueAt) return;
        if (fighter.getSpeech().isEmpty()) fighter.speak(pending.text, 44);
        PENDING_SPEECH.remove(fighter.getUUID());
    }

    private static final class PendingSpeech {
        private final String text;
        private final long dueAt;
        private final long createdAt;

        private PendingSpeech(String text, long dueAt, long createdAt) {
            this.text = text;
            this.dueAt = dueAt;
            this.createdAt = createdAt;
        }
    }

    private static final class FightState {
        private UUID opponent;
        private int fightTicks;
        private int actionCooldown;
        private int dialogueCooldown;
        private int chargeTicks;
        private int scriptedChargeTicks;
        private int standoffTicks;
        private int pauseTicks;
        private int powerBeatMask;
        private boolean targetWasCharging;
        private boolean targetWasCasting;
        private boolean selfWasCasting;
        private boolean majorCastPending;
        private boolean midFightLine;
        private boolean lowHealthLine;
        private boolean escalatedCombos;
        private boolean awakeningUsed;
        private boolean clashBeatUsed;
        private boolean clashAnnounced;
        private boolean aerialBreakUsed;
        private boolean suspenseBeatUsed;
        private int closeIdleTicks;
        private int lastSkillId = -1;
        private int lastSkillTick = -999;
        private int sameSkillStreak;
        private int lastComboId = -1;
        private int lastComboTick = -999;
        private double combatBaseY = Double.NaN;
        private long lastTouched;

        private void resetFor(UUID newOpponent) {
            opponent = newOpponent;
            fightTicks = 0;
            actionCooldown = 16;
            dialogueCooldown = 35;
            chargeTicks = 0;
            scriptedChargeTicks = 0;
            standoffTicks = 0;
            pauseTicks = 0;
            powerBeatMask = 0;
            targetWasCharging = false;
            targetWasCasting = false;
            selfWasCasting = false;
            majorCastPending = false;
            midFightLine = false;
            lowHealthLine = false;
            escalatedCombos = false;
            awakeningUsed = false;
            clashBeatUsed = false;
            clashAnnounced = false;
            aerialBreakUsed = false;
            suspenseBeatUsed = false;
            closeIdleTicks = 0;
            lastSkillId = -1;
            lastSkillTick = -999;
            sameSkillStreak = 0;
            lastComboId = -1;
            lastComboTick = -999;
            combatBaseY = Double.NaN;
        }
    }
}
