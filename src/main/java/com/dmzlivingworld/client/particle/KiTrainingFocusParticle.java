package com.dmzlivingworld.client.particle;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.*;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.Mth;

/** Dense anime energy core used while an NPC compresses Ki between their hands. */
public final class KiTrainingFocusParticle extends TextureSheetParticle {
    private final float base;
    private KiTrainingFocusParticle(ClientLevel level,double x,double y,double z,double r,double g,double b,SpriteSet sprites){
        super(level,x,y,z); hasPhysics=false; gravity=0; lifetime=10+random.nextInt(5);
        float gather = Mth.clamp((float)r, 0.20F, 1.0F);
        base=(.17F+random.nextFloat()*.08F) * (0.85F + gather * 1.45F);
        // Server-side shell/lane placement already provides motion. Client random drift made the
        // launch lane wobble away from its intended axis, so keep each sample anchored.
        xd=yd=zd=0.0D;
        setColor(Mth.clamp((float)r,0,1),Mth.clamp((float)g,0,1),Mth.clamp((float)b,0,1)); pickSprite(sprites);
    }
    @Override public void tick(){ super.tick(); if(removed)return; float t=age/(float)lifetime; float envelope=Mth.sin((float)Math.PI*Mth.clamp(t,0,1)); alpha=.84F*(1F-t*.55F); quadSize=base*(.7F+.55F*envelope); }
    @Override public int getLightColor(float p){ return 0x00F000F0; }
    @Override public ParticleRenderType getRenderType(){ return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT; }
    public static final class Provider implements ParticleProvider<SimpleParticleType>{ private final SpriteSet s; public Provider(SpriteSet s){this.s=s;} public Particle createParticle(SimpleParticleType t,ClientLevel l,double x,double y,double z,double r,double g,double b){return new KiTrainingFocusParticle(l,x,y,z,r,g,b,s);} }
}
