package com.kunyo.dbzmeditation;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.Mth;

/** A custom hand-drawn anime ki flame with coherent upward sway. */
public final class KiAuraWispParticle extends TextureSheetParticle {
    private final SpriteSet sprites;
    private final float peakScale;
    private final double baseXd;
    private final double baseZd;
    private final float swayPhase;
    private final float rollCenter;

    private KiAuraWispParticle(
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
        this.sprites = sprites;
        this.hasPhysics = false;
        this.gravity = 0.0F;
        this.friction = 0.96F;
        this.baseXd = (random.nextDouble() - 0.5D) * 0.004D;
        this.baseZd = (random.nextDouble() - 0.5D) * 0.004D;
        this.xd = baseXd;
        this.yd = 0.019D + random.nextDouble() * 0.012D;
        this.zd = baseZd;
        this.lifetime = 20 + random.nextInt(9);
        this.peakScale = 0.23F + random.nextFloat() * 0.10F;
        this.quadSize = peakScale * 0.65F;
        this.alpha = 0.0F;
        this.swayPhase = random.nextFloat() * Mth.TWO_PI;
        this.rollCenter = (random.nextFloat() - 0.5F) * 0.14F;
        this.roll = rollCenter;
        this.oRoll = rollCenter;
        this.setColor(
            Mth.clamp((float)red, 0.0F, 1.0F),
            Mth.clamp((float)green, 0.0F, 1.0F),
            Mth.clamp((float)blue, 0.0F, 1.0F)
        );
        this.setSpriteFromAge(sprites);
    }

    @Override
    public void tick() {
        float motionPhase = swayPhase + age * 0.34F;
        this.xd = baseXd + Mth.sin(motionPhase) * 0.0015D;
        this.zd = baseZd + Mth.cos(motionPhase * 0.83F) * 0.0015D;
        this.oRoll = this.roll;
        super.tick();
        if (removed) {
            return;
        }

        float t = age / (float)lifetime;
        float envelope = Mth.sin((float)Math.PI * t);
        this.alpha = 0.40F * envelope;
        this.quadSize = peakScale * (0.70F + envelope * 0.30F);
        this.roll =
            rollCenter
                + Mth.sin(swayPhase + age * 0.29F) * 0.075F;
        this.setSpriteFromAge(sprites);
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
            return new KiAuraWispParticle(
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
