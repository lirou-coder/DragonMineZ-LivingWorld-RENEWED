package com.kunyo.dbzmeditation;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/** Custom anime focus-seal nodes that form the meditation ground circle. */
public final class GroundRuneParticle extends TextureSheetParticle {
    private final float peakScale;

    private GroundRuneParticle(
        ClientLevel level,
        double x,
        double y,
        double z,
        double red,
        double green,
        double blue
    ) {
        super(level, x, y, z);
        this.hasPhysics = false;
        this.gravity = 0.0F;
        this.xd = 0.0D;
        this.yd = 0.0D;
        this.zd = 0.0D;
        this.lifetime = 24 + random.nextInt(9);
        this.peakScale = 0.110F + random.nextFloat() * 0.040F;
        this.quadSize = peakScale;
        this.alpha = 0.0F;
        this.roll = random.nextFloat() * Mth.TWO_PI;
        this.oRoll = this.roll;
        this.setColor(
            Mth.clamp((float)red, 0.0F, 1.0F),
            Mth.clamp((float)green, 0.0F, 1.0F),
            Mth.clamp((float)blue, 0.0F, 1.0F)
        );
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

        float t = age / (float)lifetime;
        float envelope = Mth.sin((float)Math.PI * t);
        this.alpha = 0.58F * envelope;
        this.quadSize = peakScale * (0.78F + envelope * 0.22F);
        this.roll += 0.012F;
    }

    /**
     * Render the seal as a true horizontal ground plane instead of Particle's camera-facing
     * billboard. The old billboard looked correct from above but became a filled disc when
     * viewed from the side/below.
     */
    @Override
    public void render(VertexConsumer buffer, Camera camera, float partialTick) {
        Vec3 cam = camera.getPosition();
        float cx = (float)(Mth.lerp(partialTick, this.xo, this.x) - cam.x);
        float cy = (float)(Mth.lerp(partialTick, this.yo, this.y) - cam.y) + 0.006F;
        float cz = (float)(Mth.lerp(partialTick, this.zo, this.z) - cam.z);
        float size = this.getQuadSize(partialTick);
        float angle = Mth.lerp(partialTick, this.oRoll, this.roll);
        float cos = Mth.cos(angle);
        float sin = Mth.sin(angle);
        int light = this.getLightColor(partialTick);

        // Top face.
        emit(buffer, cx, cy, cz, -size, -size, cos, sin, this.getU0(), this.getV0(), light);
        emit(buffer, cx, cy, cz, -size,  size, cos, sin, this.getU0(), this.getV1(), light);
        emit(buffer, cx, cy, cz,  size,  size, cos, sin, this.getU1(), this.getV1(), light);
        emit(buffer, cx, cy, cz,  size, -size, cos, sin, this.getU1(), this.getV0(), light);
        // Reverse winding as well. This keeps the paper-thin horizontal seal visible from below
        // on render paths that cull back faces, without turning it back into a vertical billboard.
        emit(buffer, cx, cy, cz,  size, -size, cos, sin, this.getU1(), this.getV0(), light);
        emit(buffer, cx, cy, cz,  size,  size, cos, sin, this.getU1(), this.getV1(), light);
        emit(buffer, cx, cy, cz, -size,  size, cos, sin, this.getU0(), this.getV1(), light);
        emit(buffer, cx, cy, cz, -size, -size, cos, sin, this.getU0(), this.getV0(), light);
    }

    private void emit(VertexConsumer buffer, float cx, float cy, float cz, float dx, float dz,
                      float cos, float sin, float u, float v, int light) {
        float rx = dx * cos - dz * sin;
        float rz = dx * sin + dz * cos;
        buffer.vertex(cx + rx, cy, cz + rz)
                .uv(u, v)
                .color(this.rCol, this.gCol, this.bCol, this.alpha)
                .uv2(light)
                .endVertex();
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
            GroundRuneParticle particle = new GroundRuneParticle(
                level,
                x,
                y,
                z,
                red,
                green,
                blue
            );
            particle.pickSprite(sprites);
            return particle;
        }
    }
}
