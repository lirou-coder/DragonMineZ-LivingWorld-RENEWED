package com.kunyo.dbzmeditation;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.Mth;

/** A short-lived custom anime impact flare for meditation events. */
public final class AnimeKiBurstParticle extends TextureSheetParticle {
    private final float peakScale;
    private final float spin;

    private AnimeKiBurstParticle(
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
        this.friction = 0.92F;

        double angle = random.nextDouble() * Math.PI * 2.0D;
        double speed = 0.008D + random.nextDouble() * 0.017D;
        this.xd = Math.cos(angle) * speed;
        this.yd = 0.004D + random.nextDouble() * 0.014D;
        this.zd = Math.sin(angle) * speed;

        this.lifetime = 12 + random.nextInt(7);
        this.peakScale = 0.30F + random.nextFloat() * 0.18F;
        this.quadSize = 0.0F;
        this.alpha = 0.0F;
        this.roll = random.nextFloat() * Mth.TWO_PI;
        this.oRoll = this.roll;
        this.spin =
            (random.nextBoolean() ? 1.0F : -1.0F)
                * (0.025F + random.nextFloat() * 0.030F);
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
        if (this.removed) {
            return;
        }

        float t = this.age / (float)this.lifetime;
        float grow = smoothUnit(t / 0.22F);
        float fade = 1.0F - smoothUnit((t - 0.35F) / 0.65F);
        float envelope = grow * fade;
        this.alpha = 0.52F * envelope;
        this.quadSize =
            this.peakScale
                * grow
                * (0.62F + 0.38F * Mth.sin((float)Math.PI * t));
        this.roll += this.spin * (1.0F - t * 0.45F);
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
            double red,
            double green,
            double blue
        ) {
            return new AnimeKiBurstParticle(
                level,
                x,
                y,
                z,
                red,
                green,
                blue,
                sprites
            );
        }
    }
}
