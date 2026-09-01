package com.kunyo.dbzmeditation;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.Mth;

/** A custom full-bright, camera-facing anime ki fragment. */
public class KiGlowParticle extends TextureSheetParticle {

    private final float peakScale;
    private final float growTicks;
    private final float fadeTicks;
    private final float spin;

    protected KiGlowParticle(
        ClientLevel level,
        double x, double y, double z,
        double red, double green, double blue
    ) {
        super(level, x, y, z);

        this.setSize(0.01F, 0.01F);
        this.hasPhysics = false;
        this.gravity = 0.0F;
        this.xd = 0.0D;
        this.yd = 0.0035D;
        this.zd = 0.0D;

        this.setColor(
            Mth.clamp((float) red, 0.0F, 1.0F),
            Mth.clamp((float) green, 0.0F, 1.0F),
            Mth.clamp((float) blue, 0.0F, 1.0F)
        );

        this.alpha = 0.0F;
        this.quadSize = 0.0F;

        this.peakScale =
            0.130F + this.random.nextFloat() * 0.050F;

        this.lifetime =
            16 + this.random.nextInt(8);

        this.growTicks =
            this.lifetime * 0.30F;

        this.fadeTicks =
            this.lifetime * 0.45F;

        this.roll = this.random.nextFloat() * Mth.TWO_PI;
        this.oRoll = this.roll;
        this.spin =
            (this.random.nextBoolean() ? 1.0F : -1.0F)
                * (0.010F + this.random.nextFloat() * 0.012F);
    }

    @Override
    public void tick() {
        this.oRoll = this.roll;
        super.tick();

        if (this.removed) {
            return;
        }

        this.roll += this.spin;

        if (this.age <= this.growTicks) {
            float grow =
                this.age / Math.max(1.0F, this.growTicks);

            float eased =
                easeOut(grow);

            this.quadSize = this.peakScale * eased;
            this.alpha = 0.56F * eased;
        } else if (this.age >= this.lifetime - this.fadeTicks) {
            float fade =
                (this.lifetime - this.age)
                    / Math.max(1.0F, this.fadeTicks);

            float clampedFade =
                Math.max(0.0F, fade);

            this.quadSize = this.peakScale * (0.85F + 0.15F * clampedFade);
            this.alpha = 0.56F * clampedFade;
        } else {
            this.quadSize = this.peakScale;
            this.alpha = 0.56F;
        }
    }

    private static float easeOut(float t) {
        float clamped =
            Mth.clamp(t, 0.0F, 1.0F);

        return 1.0F - (1.0F - clamped) * (1.0F - clamped);
    }

    @Override
    public ParticleRenderType getRenderType() {
        return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
    }

    @Override
    public int getLightColor(float partialTick) {
        // Always render at full brightness so the glow reads as light being
        // emitted by the meditator, not light bouncing off them.
        return 15728880;
    }

    public static final class Provider
        implements ParticleProvider<SimpleParticleType> {

        private final SpriteSet spriteSet;

        public Provider(SpriteSet spriteSet) {
            this.spriteSet = spriteSet;
        }

        @Override
        public Particle createParticle(
            SimpleParticleType type,
            ClientLevel level,
            double x, double y, double z,
            double xSpeed, double ySpeed, double zSpeed
        ) {
            KiGlowParticle particle =
                new KiGlowParticle(
                    level,
                    x, y, z,
                    xSpeed, ySpeed, zSpeed
                );

            particle.pickSprite(this.spriteSet);
            return particle;
        }
    }
}
