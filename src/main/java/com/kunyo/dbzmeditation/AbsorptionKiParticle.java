package com.kunyo.dbzmeditation;

import com.dmzlivingworld.entity.AmbientFighterEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * A deliberately readable ENERGY -> PLAYER fragment.
 *
 * The three SimpleParticleType speed channels are used as the vector from the
 * spawn point to the meditation torso anchor. The particle never treats that
 * vector as ordinary velocity: it owns its complete curved path and contracts
 * into the target over time. Multiple fragments launched a few ticks apart
 * therefore read as one living spiral instead of a static line of particles.
 */
public final class AbsorptionKiParticle extends TextureSheetParticle {
    private final Vec3 start;
    private final Vec3 end;
    private final Vec3 curlSide;
    private final Vec3 curlUp;
    private final float phase;
    private final float turns;
    private final float peakScale;
    private final float spin;

    private AbsorptionKiParticle(
        ClientLevel level,
        double x,
        double y,
        double z,
        double targetDeltaX,
        double targetDeltaY,
        double targetDeltaZ,
        SpriteSet sprites
    ) {
        super(level, x, y, z);

        this.start = new Vec3(x, y, z);
        this.end = start.add(targetDeltaX, targetDeltaY, targetDeltaZ);

        Vec3 direction = end.subtract(start);
        if (direction.lengthSqr() < 1.0E-6D) {
            direction = new Vec3(0.0D, 1.0D, 0.0D);
        }
        direction = direction.normalize();

        Vec3 reference =
            Math.abs(direction.y) < 0.88D
                ? new Vec3(0.0D, 1.0D, 0.0D)
                : new Vec3(1.0D, 0.0D, 0.0D);

        Vec3 side = direction.cross(reference);
        if (side.lengthSqr() < 1.0E-6D) {
            side = new Vec3(1.0D, 0.0D, 0.0D);
        }

        this.curlSide = side.normalize();
        this.curlUp = curlSide.cross(direction).normalize();
        this.phase = random.nextFloat() * Mth.TWO_PI;
        this.turns = 1.20F + random.nextFloat() * 0.65F;
        this.peakScale = 0.105F + random.nextFloat() * 0.040F;
        this.spin =
            (random.nextBoolean() ? 1.0F : -1.0F)
                * (0.025F + random.nextFloat() * 0.020F);

        this.hasPhysics = false;
        this.gravity = 0.0F;
        this.xd = 0.0D;
        this.yd = 0.0D;
        this.zd = 0.0D;
        this.lifetime = 25 + random.nextInt(9);
        this.quadSize = peakScale;
        this.alpha = 0.0F;
        this.roll = random.nextFloat() * Mth.TWO_PI;
        this.oRoll = this.roll;

        Minecraft mc = Minecraft.getInstance();
        float[] rgb = sourceColor(level, x, y, z, mc);

        this.setColor(
            Mth.clamp(rgb[0], 0.0F, 1.0F),
            Mth.clamp(rgb[1], 0.0F, 1.0F),
            Mth.clamp(rgb[2], 0.0F, 1.0F)
        );
        this.pickSprite(sprites);
    }

    /**
     * Reciprocal shared-meditation streams inherit the aura of the body they leave.
     * NPC streams therefore keep each fighter's own Ki color, while player streams continue
     * to follow the player's live DMZ aura (including transformations).
     */
    private static float[] sourceColor(ClientLevel level, double x, double y, double z, Minecraft mc) {
        // Decide by the body the fragment was actually spawned from, not by the local player's
        // current color. Shared partners sit close together, so simply asking whether an NPC is
        // nearby is not enough: compare the origin-to-body distances and use the closest source.
        AABB sourceBox = new AABB(x - 2.25D, y - 1.75D, z - 2.25D,
                x + 2.25D, y + 1.75D, z + 2.25D);
        AmbientFighterEntity nearestFighter = level.getEntitiesOfClass(
                        AmbientFighterEntity.class, sourceBox,
                        fighter -> fighter.isAlive() && fighter.isMeditating())
                .stream()
                .min(java.util.Comparator.comparingDouble(f -> f.distanceToSqr(x, y, z)))
                .orElse(null);

        double fighterDistance = nearestFighter == null
                ? Double.POSITIVE_INFINITY : nearestFighter.distanceToSqr(x, y, z);
        double playerDistance = mc.player == null
                ? Double.POSITIVE_INFINITY : mc.player.distanceToSqr(x, y, z);

        if (nearestFighter != null && fighterDistance + 0.08D < playerDistance) {
            int color = nearestFighter.getAuraColor();
            return new float[] {
                    ((color >> 16) & 0xFF) / 255.0F,
                    ((color >> 8) & 0xFF) / 255.0F,
                    (color & 0xFF) / 255.0F
            };
        }
        return mc.player != null
                ? DMZClientAuraColor.getRgb(mc.player, 1.0F)
                : new float[] {1.0F, 1.0F, 1.0F};
    }

    @Override
    public void tick() {
        this.xo = this.x;
        this.yo = this.y;
        this.zo = this.z;
        this.oRoll = this.roll;

        if (++this.age >= this.lifetime) {
            this.remove();
            return;
        }

        float t = this.age / (float)this.lifetime;

        // Ease toward the body: leisurely outside, decisive over the final third.
        float travel =
            1.0F - (float)Math.pow(1.0F - Mth.clamp(t, 0.0F, 1.0F), 1.42D);

        Vec3 line = start.lerp(end, travel);

        // The curl collapses with the trajectory, so the final frames genuinely
        // enter the torso rather than orbiting forever around it.
        double envelope =
            Math.sin(Math.PI * t)
                * (1.0D - 0.58D * t);
        double amplitude = 0.175D * envelope;
        double angle =
            phase
                + t * Mth.TWO_PI * turns;

        Vec3 curl =
            curlSide.scale(Math.cos(angle) * amplitude)
                .add(curlUp.scale(Math.sin(angle) * amplitude));

        Vec3 position = line.add(curl);
        this.setPos(position.x, position.y, position.z);
        this.roll += spin * (1.0F - t * 0.55F);

        float appear = smoothUnit(t / 0.12F);
        float finalFade = 1.0F - 0.58F * smoothUnit((t - 0.90F) / 0.10F);
        this.alpha = 0.78F * appear * finalFade;

        // The fragment compresses into a bright point as it reaches the core.
        this.quadSize =
            peakScale
                * Mth.lerp(smoothUnit(t), 1.08F, 0.46F);
    }

    private static float smoothUnit(float value) {
        float t = Mth.clamp(value, 0.0F, 1.0F);
        return t * t * (3.0F - 2.0F * t);
    }

    @Override
    public int getLightColor(float partialTick) {
        return 0x00F000F0;
    }

    @Override
    public ParticleRenderType getRenderType() {
        return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
    }

    public static final class Provider
        implements ParticleProvider<SimpleParticleType> {

        private final SpriteSet sprites;

        public Provider(SpriteSet sprites) {
            this.sprites = sprites;
        }

        @Override
        public Particle createParticle(
            SimpleParticleType type,
            ClientLevel level,
            double x,
            double y,
            double z,
            double targetDeltaX,
            double targetDeltaY,
            double targetDeltaZ
        ) {
            return new AbsorptionKiParticle(
                level,
                x,
                y,
                z,
                targetDeltaX,
                targetDeltaY,
                targetDeltaZ,
                sprites
            );
        }
    }
}
