package com.kunyo.dbzmeditation;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.Mth;

/** Slow geometric realization-sigil used only by permanent stat breakthroughs. */
public final class BreakthroughSigilParticle extends TextureSheetParticle {
    private final float peakScale;
    private final float spin;

    private BreakthroughSigilParticle(
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
        this.yd = 0.001D;
        this.zd = 0.0D;
        this.lifetime = 54;
        this.peakScale = 1.28F + random.nextFloat() * 0.18F;
        this.quadSize = 0.0F;
        this.alpha = 0.0F;
        this.roll = random.nextFloat() * Mth.TWO_PI;
        this.oRoll = this.roll;
        this.spin =
            (random.nextBoolean() ? 1.0F : -1.0F)
                * (0.006F + random.nextFloat() * 0.003F);
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
        float grow = smooth(t / 0.22F);
        float fade = 1.0F - smooth((t - 0.66F) / 0.34F);
        float breathe =
            0.94F
                + 0.06F
                    * Mth.sin(t * Mth.TWO_PI * 1.35F);

        this.quadSize = this.peakScale * grow * breathe;
        this.alpha = 0.68F * grow * fade;
        this.roll += this.spin;
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
            return new BreakthroughSigilParticle(
                level, x, y, z,
                red, green, blue,
                sprites
            );
        }
    }
}
