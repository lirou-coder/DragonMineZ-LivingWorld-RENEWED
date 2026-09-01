package com.kunyo.dbzmeditation;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.Mth;

/**
 * Sharp realization fragments that burst away from the meditation core.
 * Motion is generated client-side so the three particle parameters stay
 * available for exact aura RGB tinting.
 */
public final class BreakthroughShardParticle extends TextureSheetParticle {
    private final float initialScale;
    private final float spin;

    private BreakthroughShardParticle(
        ClientLevel level,
        double x,
        double y,
        double z,
        double red,
        double green,
        double blue,
        SpriteSet sprites
    ) {
        super(level, x, y, z);
        this.hasPhysics = false;
        this.gravity = 0.0F;
        this.friction = 0.91F;

        double azimuth =
            random.nextDouble() * Math.PI * 2.0D;
        double vertical =
            -0.35D + random.nextDouble() * 1.10D;
        double horizontal =
            Math.sqrt(Math.max(0.0D, 1.0D - vertical * vertical));
        double speed =
            0.030D + random.nextDouble() * 0.055D;

        this.xd = Math.cos(azimuth) * horizontal * speed;
        this.yd = vertical * speed;
        this.zd = Math.sin(azimuth) * horizontal * speed;

        this.lifetime = 18 + random.nextInt(10);
        this.initialScale = 0.22F + random.nextFloat() * 0.16F;
        this.quadSize = this.initialScale;
        this.alpha = 0.0F;
        this.roll = random.nextFloat() * Mth.TWO_PI;
        this.oRoll = this.roll;
        this.spin =
            (random.nextBoolean() ? 1.0F : -1.0F)
                * (0.025F + random.nextFloat() * 0.045F);

        this.setColor(
            Mth.clamp((float)red, 0.0F, 1.0F),
            Mth.clamp((float)green, 0.0F, 1.0F),
            Mth.clamp((float)blue, 0.0F, 1.0F)
        );
        this.pickSprite(sprites);
    }

    @Override
    public void tick() {
        this.oRoll = this.roll;
        super.tick();
        if (this.removed) return;

        float t = this.age / (float)this.lifetime;
        float appear = smooth(t / 0.12F);
        float fade = 1.0F - smooth((t - 0.42F) / 0.58F);

        this.alpha = 0.88F * appear * fade;
        this.quadSize =
            this.initialScale
                * (1.0F - 0.48F * t)
                * appear;
        this.roll += this.spin * (1.0F - 0.55F * t);
    }

    private static float smooth(float value) {
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
            double red,
            double green,
            double blue
        ) {
            return new BreakthroughShardParticle(
                level, x, y, z,
                red, green, blue,
                sprites
            );
        }
    }
}
