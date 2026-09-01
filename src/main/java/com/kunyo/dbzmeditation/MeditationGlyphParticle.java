package com.kunyo.dbzmeditation;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.Minecraft;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/** A custom anime ki sigil that follows a curved diagonal meditation path. */
public final class MeditationGlyphParticle extends TextureSheetParticle {
    private final SpriteSet sprites;
    private final Vec3 start;
    private final Vec3 end;
    private final Vec3 curlSide;
    private final Vec3 curlUp;
    private final float phase;
    private final float baseSize;
    private final float spin;

    private MeditationGlyphParticle(
        ClientLevel level,
        double x,
        double y,
        double z,
        double deltaX,
        double deltaY,
        double deltaZ,
        SpriteSet sprites
    ) {
        super(level, x, y, z);
        this.sprites = sprites;
        this.start = new Vec3(x, y, z);
        this.end = start.add(deltaX, deltaY, deltaZ);

        Vec3 direction = end.subtract(start);
        if (direction.lengthSqr() < 1.0E-6D) {
            direction = new Vec3(0.0D, 1.0D, 0.0D);
        }
        direction = direction.normalize();

        Vec3 reference =
            Math.abs(direction.y) < 0.90D
                ? new Vec3(0.0D, 1.0D, 0.0D)
                : new Vec3(1.0D, 0.0D, 0.0D);

        Vec3 side = direction.cross(reference);
        if (side.lengthSqr() < 1.0E-6D) {
            side = new Vec3(1.0D, 0.0D, 0.0D);
        }
        this.curlSide = side.normalize();
        this.curlUp = curlSide.cross(direction).normalize();
        this.phase = level.random.nextFloat() * Mth.TWO_PI;
        this.baseSize = 0.130F + level.random.nextFloat() * 0.045F;

        this.lifetime = 36 + level.random.nextInt(10);
        this.hasPhysics = false;
        this.gravity = 0.0F;
        this.friction = 1.0F;
        this.rCol = 0.72F;
        this.gCol = 0.48F;
        this.bCol = 1.0F;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            float[] rgb = DMZClientAuraColor.getRgb(mc.player, 1.0F);
            this.rCol = Mth.clamp(rgb[0], 0.0F, 1.0F);
            this.gCol = Mth.clamp(rgb[1], 0.0F, 1.0F);
            this.bCol = Mth.clamp(rgb[2], 0.0F, 1.0F);
        }
        this.alpha = 0.0F;
        this.quadSize = baseSize;
        this.roll = level.random.nextFloat() * Mth.TWO_PI;
        this.oRoll = this.roll;
        this.spin =
            (level.random.nextBoolean() ? 1.0F : -1.0F)
                * (0.0035F + level.random.nextFloat() * 0.0045F);
        this.pickSprite(sprites);
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
        float eased = t * t * (3.0F - 2.0F * t);
        Vec3 line = start.lerp(end, eased);

        double curl =
            Math.sin(Math.PI * t)
                * 0.15D;
        double angle =
            phase
                + t * Mth.TWO_PI * 1.35D;

        Vec3 offset =
            curlSide.scale(Math.cos(angle) * curl)
                .add(curlUp.scale(Math.sin(angle) * curl));

        Vec3 position = line.add(offset);
        this.setPos(position.x, position.y, position.z);
        this.roll += this.spin;

        float envelope = Mth.sin((float)Math.PI * t);
        this.alpha = 0.82F * (float)Math.pow(Math.max(0.0F, envelope), 0.60D);
        this.quadSize = baseSize * (0.88F + envelope * 0.20F);
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
            double deltaX,
            double deltaY,
            double deltaZ
        ) {
            return new MeditationGlyphParticle(
                level,
                x,
                y,
                z,
                deltaX,
                deltaY,
                deltaZ,
                sprites
            );
        }
    }
}
