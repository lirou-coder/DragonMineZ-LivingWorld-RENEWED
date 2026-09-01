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
 * Large, soft ki bloom used only by the Transcendent rear aura.
 *
 * This deliberately restores the visual language of the original meditation
 * core: a dedicated radial glow texture, exact form color, a quick bloom, a
 * short hold, and a long fade. It is visually distinct from the pixel sparks
 * that trace the player's rising helix.
 */
public final class TranscendentCoreParticle extends TextureSheetParticle {
    private final float peakScale;
    private final float growTicks;
    private final float fadeTicks;

    private TranscendentCoreParticle(
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
        this.setSize(0.01F, 0.01F);
        this.hasPhysics = false;
        this.gravity = 0.0F;
        this.xd = 0.0D;
        this.yd = 0.004D;
        this.zd = 0.0D;
        this.lifetime = 40 + this.random.nextInt(12);
        this.peakScale = 1.18F + this.random.nextFloat() * 0.42F;
        this.growTicks = this.lifetime * 0.24F;
        this.fadeTicks = this.lifetime * 0.34F;
        this.quadSize = 0.0F;
        this.alpha = 0.0F;
        this.setColor(
            Mth.clamp((float)red, 0.0F, 1.0F),
            Mth.clamp((float)green, 0.0F, 1.0F),
            Mth.clamp((float)blue, 0.0F, 1.0F)
        );
        this.pickSprite(sprites);
    }

    @Override
    public void tick() {
        super.tick();
        if (this.removed) {
            return;
        }

        if (this.age <= this.growTicks) {
            float grow = this.age / Math.max(1.0F, this.growTicks);
            float eased = easeOut(grow);
            this.quadSize = this.peakScale * eased;
            this.alpha = 0.90F * eased;
        } else if (this.age >= this.lifetime - this.fadeTicks) {
            float fade =
                (this.lifetime - this.age)
                    / Math.max(1.0F, this.fadeTicks);
            float clampedFade = Math.max(0.0F, fade);
            this.quadSize =
                this.peakScale * (0.85F + 0.15F * clampedFade);
            this.alpha = 0.90F * clampedFade;
        } else {
            this.quadSize = this.peakScale;
            this.alpha = 0.90F;
        }
    }

    private static float easeOut(float value) {
        float t = Mth.clamp(value, 0.0F, 1.0F);
        return 1.0F - (1.0F - t) * (1.0F - t);
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
            TranscendentCoreParticle particle =
                new TranscendentCoreParticle(
                    level,
                    x,
                    y,
                    z,
                    red,
                    green,
                    blue,
                    sprites
                );
            return particle;
        }
    }
}
