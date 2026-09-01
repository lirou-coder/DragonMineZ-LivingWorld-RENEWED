package com.dmzlivingworld.client.particle;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.*;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.Mth;

/** Flat luminous anime pulse/flare used at the harmless control-release beat. */
public final class KiTrainingPulseParticle extends TextureSheetParticle {
    private final float max;
    private KiTrainingPulseParticle(ClientLevel l,double x,double y,double z,double r,double g,double b,SpriteSet s){super(l,x,y,z);hasPhysics=false;gravity=0;lifetime=12+random.nextInt(4);max=.46F+random.nextFloat()*.18F;quadSize=.05F;alpha=.9F;setColor(Mth.clamp((float)r,0,1),Mth.clamp((float)g,0,1),Mth.clamp((float)b,0,1));pickSprite(s);roll=random.nextFloat()*Mth.TWO_PI;oRoll=roll;}
    @Override public void tick(){oRoll=roll;super.tick();if(removed)return;float t=age/(float)lifetime;quadSize=max*Mth.sqrt(Mth.clamp(t,0,1));alpha=.82F*(1F-t);roll+=.035F;}
    @Override public int getLightColor(float p){return 0x00F000F0;}
    @Override public ParticleRenderType getRenderType(){return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;}
    public static final class Provider implements ParticleProvider<SimpleParticleType>{private final SpriteSet s;public Provider(SpriteSet s){this.s=s;}public Particle createParticle(SimpleParticleType t,ClientLevel l,double x,double y,double z,double r,double g,double b){return new KiTrainingPulseParticle(l,x,y,z,r,g,b,s);}}
}
