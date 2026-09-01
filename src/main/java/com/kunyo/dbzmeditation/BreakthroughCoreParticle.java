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
 * Rare breakthrough focal flash: a sharp anime star/core rather than another
 * copy of the normal meditation burst.
 */
public final class BreakthroughCoreParticle extends TextureSheetParticle {
    private final float peakScale;
    private final float spin;

    private BreakthroughCoreParticle(
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
        this.xd = 0.0D;
        this.yd = 0.002D;
        this.zd = 0.0D;
        this.lifetime = 28;
        this.peakScale = 1.05F + random.nextFloat() * 0.16F;
        this.quadSize = 0.0F;
        this.alpha = 0.0F;
        this.roll = random.nextFloat() * Mth.TWO_PI;
        this.oRoll = this.roll;
        this.spin =
            (random.nextBoolean() ? 1.0F : -1.0F)
                * 0.010F;
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
        float grow = smooth(t / 0.18F);
        float fade = 1.0F - smooth((t - 0.50F) / 0.50F);

        this.quadSize =
            this.peakScale
                * grow
                * (1.0F + 0.10F * Mth.sin(t * Mth.PI));
        this.alpha = 0.94F * grow * fade;
        this.roll += this.spin * (1.0F - t);
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
            return new BreakthroughCoreParticle(
                level, x, y, z,
                red, green, blue,
                sprites
            );
        }
    }
}
