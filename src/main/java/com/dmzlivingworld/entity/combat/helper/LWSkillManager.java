package com.dmzlivingworld.entity.combat.helper;

import com.dragonminez.common.init.MainEffects;
import com.dragonminez.common.init.MainSounds;
import com.dragonminez.common.init.entities.ki.*;
import com.dmzlivingworld.entity.combat.LivingWorldSagasEntity;
import com.dmzlivingworld.entity.AmbientFighterEntity;
import com.dmzlivingworld.world.FighterCombatTechniquePolicy;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Map;

public class LWSkillManager {
    public static final int KAMEHAMEHA_CAST_TICKS = 37;
    public static final int ASSAULT_RAIN_CAST_TICKS = 30;
    public static final int ASSAULT_RAIN_FIRE_TICKS = 40;
    public static final int BLASTER_METEOR_CAST_TICKS = 30;
    public static final int BLASTER_METEOR_FIRE_TICKS = 50;
    public static final int DRAGON_FIST_WINDUP = 5;
    public static final int DRAGON_FIST_RUSH_TICKS = 20;

    @FunctionalInterface
    public interface KiAction {
        void execute(LivingWorldSagasEntity user, LivingEntity target, float damage);
    }

    private static final Map<Integer, KiAction> REGISTRY = new HashMap<>();

    static {
        // 1. KAMEHAMEHA
        REGISTRY.put(1, (user, target, dmg) -> {
            KiWaveEntity kame = new KiWaveEntity(user.level(), user);
            kame.setupKiHame(user, dmg, user.getKiBlastSpeed(), user.getCurrentPoolSkillSize(), 37);
        });

        // 2. GALICK GUN
        REGISTRY.put(2, (user, target, dmg) -> {
            KiWaveEntity galick = new KiWaveEntity(user.level(), user);
            galick.setupKiGalickGun(user, dmg, user.getKiBlastSpeed(), user.getCurrentPoolSkillSize(), 37);
        });

        // 3. MAKANKOSAPPO
        REGISTRY.put(3, (user, target, dmg) -> {
            KiLaserEntity makkanko = new KiLaserEntity(user.level(), user);
            makkanko.setupKiMakkankosanpo(user, dmg, user.getKiBlastSpeed() * 2.0F, 37);
        });

        // 4. KI LASER
        REGISTRY.put(4, (user, target, dmg) -> {
            KiLaserEntity laser = new KiLaserEntity(user.level(), user);
            laser.setupKiLaser(user, dmg, user.getKiBlastSpeed() * 3.0F, user.getCurrentPoolColorMain(), user.getCurrentPoolColorBorder(), 0);
        });

        // 5. KI EXPLOSION
        REGISTRY.put(5, (user, target, dmg) -> {
            KiExplosionEntity explosion = new KiExplosionEntity(user.level(), user);
            explosion.setupKiExplosion(user, dmg, user.getCurrentPoolColorMain(), user.getCurrentPoolColorBorder(), 37);
        });

        // 6. KI BARRIER
        REGISTRY.put(6, (user, target, dmg) -> {
            KiBarrierEntity barrier = new KiBarrierEntity(user.level(), user);
            barrier.setupKiBarrier(user, user.getCurrentPoolColorMain(), user.getCurrentPoolColorBorder(), 37);
            barrier.setKiDamage(dmg);
        });

        // 7. OOZARU ROAR
        REGISTRY.put(7, (user, target, dmg) -> {
            user.playSound(MainSounds.OOZARU_GROWL_PLAYER.get(), 2.0F, 0.8F + user.getRandom().nextFloat() * 0.4F);
            if (!user.level().isClientSide && user.level() instanceof ServerLevel serverLevel) {
                double range = 8.0D;
                serverLevel.sendParticles(ParticleTypes.EXPLOSION, user.getX(), user.getY() + (user.getBbHeight() / 2.0), user.getZ(), 100, range / 1.5, range / 1.5, range / 1.5, 0.2D);
                AABB roarBox = user.getBoundingBox().inflate(range);
                for (LivingEntity entity : serverLevel.getEntitiesOfClass(LivingEntity.class, roarBox)) {
                    if (entity != user && entity.isAlive()) {
                        entity.invulnerableTime = 0;
                        entity.hurt(user.damageSources().mobAttack(user), dmg);
                        entity.addEffect(new MobEffectInstance(MainEffects.STUN.get(), 40, 0, false, false, true));
                        Vec3 push = new Vec3(entity.getX() - user.getX(), 0.5D, entity.getZ() - user.getZ()).normalize().scale(3.5D);
                        entity.setDeltaMovement(push);
                        entity.hasImpulse = true;
                    }
                }
            }
        });

        // 8. GENERIC KI WAVE
        REGISTRY.put(8, (user, target, dmg) -> {
            KiWaveEntity wave = new KiWaveEntity(user.level(), user);
            wave.setupKiWave(user, dmg, user.getKiBlastSpeed(), user.getCurrentPoolColorMain(), user.getCurrentPoolColorBorder(), user.getCurrentPoolColorOutline(), user.getCurrentPoolSkillSize(), 37);
        });

        // 9. OOZARU BEAM
        REGISTRY.put(9, (user, target, dmg) -> {
            KiWaveEntity oozaru = new KiWaveEntity(user.level(), user);
            oozaru.setupKiOozaru(user, dmg, user.getKiBlastSpeed(), user.getCurrentPoolColorMain(), user.getCurrentPoolColorBorder(), user.getCurrentPoolSkillSize(), 37);
        });

        // 10. KI VOLLEY
        REGISTRY.put(10, (user, target, dmg) -> {
            KiBlastEntity volley = new KiBlastEntity(user.level(), user);
            volley.setupKiVolley(user, dmg, user.getKiBlastSpeed(), user.getCurrentPoolColorMain(), 37);
        });

        // 11. KI SMALL
        REGISTRY.put(11, (user, target, dmg) -> {
            KiBlastEntity small = new KiBlastEntity(user.level(), user);
            small.setupKiSmall(user, dmg, user.getKiBlastSpeed(), user.getCurrentPoolColorMain());
            small.shootFromRotation(user, user.getXRot(), user.getYRot(), 0.0F, user.getKiBlastSpeed(), 1.0F);
            user.playSound(MainSounds.KIBLAST_ATTACK.get(), 1.0F, 1.0F + (user.getRandom().nextFloat() * 0.2F));
        });

        // 12. ENE HURRICANE
        REGISTRY.put(12, (user, target, dmg) -> {
            SPBlueHurricaneEntity hurricane = new SPBlueHurricaneEntity(user.level(), user);
            hurricane.setupHurricane(user, dmg, user.getKiBlastSpeed(), 30);
        });

        // 13. TRIPLE LASER
        REGISTRY.put(13, (user, target, dmg) -> {
            KiLaserEntity triple = new KiLaserEntity(user.level(), user);
            triple.setupKiLaser(user, dmg, user.getKiBlastSpeed() * 3.0F, user.getCurrentPoolColorMain(), user.getCurrentPoolColorBorder(), 0);
        });

        // 14. KIENZAN
        REGISTRY.put(14, (user, target, dmg) -> {
            KiDiskEntity disk = new KiDiskEntity(user.level(), user);
            disk.setupKiDisk(user, dmg, user.getKiBlastSpeed() * 1.2F, user.getCurrentPoolColorMain(), user.getCurrentPoolSkillSize(), 30);
        });

        // 15. DEATH BALL
        REGISTRY.put(15, (user, target, dmg) -> {
            KiBlastEntity ball = new KiBlastEntity(user.level(), user);
            ball.setupKiDeathBall(user, dmg, user.getKiBlastSpeed() * 0.7F, user.getCurrentPoolColorMain(), user.getCurrentPoolColorBorder(), 60);
        });

        // 16. MASENKO
        REGISTRY.put(16, (user, target, dmg) -> {
            KiWaveEntity masenko = new KiWaveEntity(user.level(), user);
            masenko.setupKiMasenko(user, dmg, user.getKiBlastSpeed(), user.getCurrentPoolSkillSize(), 40);
        });

        // 17. BIG BANG
        REGISTRY.put(17, (user, target, dmg) -> {
            KiBlastEntity bigbang = new KiBlastEntity(user.level(), user);
            bigbang.setupKiBlast(user, dmg, user.getKiBlastSpeed(), user.getCurrentPoolColorMain(), user.getCurrentPoolSkillSize(), 30);
        });

        // 18. FINAL FLASH
        REGISTRY.put(18, (user, target, dmg) -> {
            KiWaveEntity ff = new KiWaveEntity(user.level(), user);
            ff.setupFinalFlash(user, dmg, user.getKiBlastSpeed(), user.getCurrentPoolSkillSize(), 40);
        });

        // 19. MAJIN CANDY
        REGISTRY.put(19, (user, target, dmg) -> {
            SPMajinCandyEntity candy = new SPMajinCandyEntity(user.level(), user);
            candy.setupCandyBeam(user, dmg, user.getKiBlastSpeed(), 35);
        });

        REGISTRY.put(20, (user, target, dmg) -> {
            KiBlastEntity airVolley = new KiBlastEntity(user.level(), user);
            airVolley.setupKiAirVolley(user, dmg, user.getKiBlastSpeed(), user.getCurrentPoolColorMain(), user.getCurrentPoolColorOutline(), 30);
        });

        // 21. DOUBLE SUNDAY (Raditz)
        REGISTRY.put(21, (user, target, dmg) -> {
            KiWaveEntity doubleSunday = new KiWaveEntity(user.level(), user);
            doubleSunday.setupDoubleSunday(user, dmg, user.getKiBlastSpeed(), user.getCurrentPoolColorMain(), user.getCurrentPoolColorBorder(), user.getCurrentPoolColorOutline(), user.getCurrentPoolSkillSize(), 40);
        });
        REGISTRY.put(22, (user, target, dmg) -> {
            if (user.distanceTo(target) <= 4.0D) {
                target.invulnerableTime = 0;
                target.hurt(user.damageSources().mobAttack(user), dmg);
                user.spawnPunchParticles(target);
            }
        });
        REGISTRY.put(23, (user, target, dmg) -> {
            SPDragonFistEntity attack = new SPDragonFistEntity(user.level(), user);
            attack.setupDragonFist(user, dmg, 1.0F, DRAGON_FIST_RUSH_TICKS);
        });
        REGISTRY.put(24, (user, target, dmg) -> {
            KiWaveEntity wave = new KiWaveEntity(user.level(), user);
            wave.setupKiHame(user, dmg, user.getKiBlastSpeed(), user.getCurrentPoolSkillSize(),
                    0xFFE3E3, 0xFF2A2A, 0xB00020, KAMEHAMEHA_CAST_TICKS);
        });
        REGISTRY.put(26, (user, target, dmg) -> {
            KiLaserEntity laser = new KiLaserEntity(user.level(), user);
            laser.setupKiDodonpa(user, dmg, user.getKiBlastSpeed() * 3.0F, 0);
        });
        REGISTRY.put(27, (user, target, dmg) -> {
            KiBlastEntity blast = new KiBlastEntity(user.level(), user);
            blast.setupKiBlast(user, dmg, user.getKiBlastSpeed(), user.getCurrentPoolColorMain(),
                    user.getCurrentPoolColorBorder(), user.getCurrentPoolColorOutline(), user.getCurrentPoolSkillSize(), 30);
        });
        REGISTRY.put(28, (user, target, dmg) -> {
            KiBlastEntity ball = new KiBlastEntity(user.level(), user);
            ball.setupKiDeathBall(user, dmg, user.getKiBlastSpeed() * .7F,
                    user.getCurrentPoolColorMain(), user.getCurrentPoolColorBorder(), 60);
        });
        REGISTRY.put(29, (user, target, dmg) -> {
            KiBlastEntity rain = new KiBlastEntity(user.level(), user);
            rain.setupAssaultRain(user, dmg, user.getKiBlastSpeed(), user.getCurrentPoolColorMain(),
                    user.getCurrentPoolColorBorder(), user.getCurrentPoolColorOutline(),
                    .8F * user.getCurrentPoolSkillSize(), ASSAULT_RAIN_CAST_TICKS, ASSAULT_RAIN_FIRE_TICKS);
        });
        REGISTRY.put(30, (user, target, dmg) -> {
            KiBlastEntity meteor = new KiBlastEntity(user.level(), user);
            meteor.setupBlasterMeteor(user, dmg, user.getKiBlastSpeed(), user.getCurrentPoolColorMain(),
                    user.getCurrentPoolColorBorder(), user.getCurrentPoolColorOutline(),
                    BLASTER_METEOR_CAST_TICKS, BLASTER_METEOR_FIRE_TICKS);
        });
        REGISTRY.put(25, (user, target, dmg) -> {
            if (target.distanceTo(user) <= 16.0D && user.hasLineOfSight(target)) {
                target.addEffect(new MobEffectInstance(MainEffects.STUN.get(), 30, 0, false, false, true));
                target.getPersistentData().putLong("dmz_taiyoken_blind_until", user.level().getGameTime() + 60L);
            }
        });
        REGISTRY.put(31, (user, target, dmg) -> {
            if (user.distanceTo(target) > 4.0D) user.teleportTo(target.getX(), target.getY(), target.getZ());
            target.invulnerableTime = 0;
            target.hurt(user.damageSources().mobAttack(user), dmg);
            user.spawnPunchParticles(target);
        });
        REGISTRY.put(32, (user, target, dmg) -> {
            for (int i = 0; i < 3; i++) {
                KiBlastEntity ball = new KiBlastEntity(user.level(), user);
                ball.setupKiDeathBall(user, dmg / 3.0F, user.getKiBlastSpeed(),
                        user.getCurrentPoolColorMain(), user.getCurrentPoolColorBorder(), 20);
            }
        });
    }

    public static void execute(int id, LivingWorldSagasEntity user, LivingEntity target) {
        if (user instanceof AmbientFighterEntity fighter
                && !FighterCombatTechniquePolicy.canUse(fighter, policyId(id))) return;
        KiAction action = REGISTRY.get(id);
        if (action != null) {
            float damage = getCalculatedDamage(id, user);
            action.execute(user, target, damage);
        }
    }

    private static String policyId(int id) {
        return switch (id) {
            case 7 -> "rage_scream";
            case 31 -> "dimensional_punch";
            default -> "skill_" + id;
        };
    }

    private static final float VOLLEY_HIT_DIVISOR = 8.0F;

    private static final float SINGLE_IMPACT_HIT_DIVISOR = 4.0F;

    public static float getCalculatedDamage(int id, LivingWorldSagasEntity user) {
        float kiDmg = user.getKiBlastDamage();
        float meleeDmg = (float) user.getAttributeValue(Attributes.ATTACK_DAMAGE);

        LivingWorldSagasEntity.KiSkillType type = LivingWorldSagasEntity.KiSkillType.fromId(id);
        float mult = type != null ? type.getTier().getDamageMultiplier() : LivingWorldSagasEntity.Tier.MEDIUM.getDamageMultiplier();

        return switch (id) {
            case 6, 25 -> 0.0F;
            case 7, 12, 19, 22, 23 -> meleeDmg * mult;
            case 13 -> kiDmg * mult / 3.0F;             // Triple Laser: 3 instances (ticks 10/20/30)
            case 10, 20, 29, 30 -> kiDmg * mult / VOLLEY_HIT_DIVISOR;
            case 11 -> kiDmg * mult / SINGLE_IMPACT_HIT_DIVISOR; // Basic ki blast: single concentrated impact
            default -> kiDmg * mult;                    // every other ki skill: single ki-scaled hit
        };
    }

    public static int getCastDuration(int id) {
        return switch (id) {
            case 4 -> 10;
            case 11 -> 12;
            case 12, 14, 17 -> 30;
            case 23 -> DRAGON_FIST_WINDUP + DRAGON_FIST_RUSH_TICKS + 2;
            case 24 -> 60;
            case 25 -> 30;
            case 26 -> 18;
            case 27 -> 30;
            case 28 -> 60;
            case 29 -> ASSAULT_RAIN_CAST_TICKS + ASSAULT_RAIN_FIRE_TICKS + KiBlastEntity.ASSAULT_RAIN_DELAY;
            case 30 -> BLASTER_METEOR_CAST_TICKS + BLASTER_METEOR_FIRE_TICKS;
            case 31, 32 -> 40;
            case 13, 16, 18, 21 -> 40;
            case 19 -> 35;
            case 15 -> 60;
            default -> 60;
        };
    }
}

