package com.dmzlivingworld.client.particle;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.Mth;

/**
 * Full-bright inner mass for Living World's Ki-control ball. The established FOCUS particles
 * still draw the moving outer circle/shell; this centered layer gives that shell a readable,
 * growing energy orb instead of leaving the middle visually hollow.
 */
public final class KiTrainingCoreParticle extends TextureSheetParticle {
    private final float base;

    private KiTrainingCoreParticle(ClientLevel level, double x, double y, double z,
                                   double scaleSignal, double ignoredG, double ignoredB, SpriteSet sprites) {
        super(level, x, y, z);
        hasPhysics = false;
        gravity = 0.0F;
        lifetime = 8 + random.nextInt(4);
        float scale = Mth.clamp((float)scaleSignal, 0.05F, 1.0F);
        base = 0.18F + scale * 0.72F;
        xd = yd = zd = 0.0D;
        setColor(0.54F + scale * 0.18F, 0.86F + scale * 0.10F, 1.0F);
        alpha = 0.74F - scale * 0.08F;
        pickSprite(sprites);
    }

    @Override
    public void tick() {
        super.tick();
        if (removed) return;
        float t = age / (float) lifetime;
        float breathe = 0.96F + 0.08F * Mth.sin((float)Math.PI * t);
        quadSize = base * breathe;
        alpha = 0.74F * (1.0F - t * 0.50F);
    }

    @Override public int getLightColor(float partialTick) { return 0x00F000F0; }
    @Override public ParticleRenderType getRenderType() { return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT; }

    public static final class Provider implements ParticleProvider<SimpleParticleType> {
        private final SpriteSet sprites;
        public Provider(SpriteSet sprites) { this.sprites = sprites; }
        @Override
        public Particle createParticle(SimpleParticleType type, ClientLevel level, double x, double y, double z,
                                       double xSpeed, double ySpeed, double zSpeed) {
            return new KiTrainingCoreParticle(level, x, y, z, xSpeed, ySpeed, zSpeed, sprites);
        }
    }
}
