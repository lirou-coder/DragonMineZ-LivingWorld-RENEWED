package com.dmzlivingworld.client.screen;

import com.dmzlivingworld.network.LWNetwork;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Explicit confirmation barrier for the irreversible world NPC reset. */
public final class ResetNpcDataConfirmScreen extends Screen implements LivingWorldScreenMarker {
    private final Screen parent;
    public ResetNpcDataConfirmScreen(Screen parent) { super(Component.literal("ARE YOU SURE?")); this.parent = parent; }
    @Override public void render(GuiGraphics g, int mx, int my, float partial) {
        renderBackground(g); int w=500,h=190,x=(width-w)/2,y=(height-h)/2;
        LivingWorldGuiStyle.drawPanel(g,x,y,w,h);
        g.drawCenteredString(font,"ARE YOU SURE?",width/2,y+16,0xFFFF5555);
        String text="Reseting the NPC data will reset the training and reference power from all NPCs so they are recalculated, but all dead NPCs will be deleted and all relationships with NPCs and factions will also be reset. Continue?";
        int yy=y+47; for(var line:font.split(Component.literal(text),w-30)){g.drawCenteredString(font,line,width/2,yy,LivingWorldGuiStyle.TEXT);yy+=12;}
        LivingWorldGuiStyle.drawButton(g,font,x+110,y+h-34,110,20,"Cancel",mx,my,true,false,false);
        LivingWorldGuiStyle.drawButton(g,font,x+w-220,y+h-34,110,20,"Reset",mx,my,true,false,false);
    }
    @Override public boolean mouseClicked(double mx,double my,int button){if(button!=0)return super.mouseClicked(mx,my,button);int w=500,h=190,x=(width-w)/2,y=(height-h)/2;
        if(mx>=x+110&&mx<x+220&&my>=y+h-34&&my<y+h-14){Minecraft.getInstance().setScreen(parent);return true;}
        if(mx>=x+w-220&&mx<x+w-110&&my>=y+h-34&&my<y+h-14){LWNetwork.resetNpcData();Minecraft.getInstance().setScreen(parent);return true;}return super.mouseClicked(mx,my,button);}
}
