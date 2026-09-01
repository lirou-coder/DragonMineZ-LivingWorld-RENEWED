package com.dmzlivingworld.client.particle;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.*;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.Mth;

/** Sharp inward-moving anime streak. Velocity is supplied by the server toward the focus point. */
public final class KiTrainingDrawParticle extends TextureSheetParticle {
    private KiTrainingDrawParticle(ClientLevel l,double x,double y,double z,double vx,double vy,double vz,SpriteSet s){
        super(l,x,y,z,vx,vy,vz);
        hasPhysics=false; gravity=0; friction=1.0F; lifetime=10;
        // Particle's base constructor adds vanilla velocity jitter. Re-apply the exact server
        // vector so gather/launch streams fly as coherent Ki lanes instead of random sparks.
        xd=vx; yd=vy; zd=vz;
        quadSize=.14F+random.nextFloat()*.04F; alpha=.76F; setColor(.55F,.86F,1F); pickSprite(s);
    }
    @Override public void tick(){ super.tick(); if(removed)return; float t=age/(float)lifetime; alpha=.76F*(1F-t); quadSize*=.98F; }
    @Override public int getLightColor(float p){ return 0x00F000F0; }
    @Override public ParticleRenderType getRenderType(){return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;}
    public static final class Provider implements ParticleProvider<SimpleParticleType>{private final SpriteSet s;public Provider(SpriteSet s){this.s=s;}public Particle createParticle(SimpleParticleType t,ClientLevel l,double x,double y,double z,double vx,double vy,double vz){return new KiTrainingDrawParticle(l,x,y,z,vx,vy,vz,s);}}
}
