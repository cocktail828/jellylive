import org.jellyfin.androidtv.bestv.OverlayContrast;

/** Worst case white-video compositing of the colors actually used by the Android renderers. */
public final class OverlayContrastCheck {
    static double[] color(int value){return new double[]{(value>>16)&255,(value>>8)&255,value&255};}
    static double[] composite(int foreground,double[] background){double a=((foreground>>>24)&255)/255.;double[] out=color(foreground);for(int i=0;i<3;i++)out[i]=out[i]*a+background[i]*(1-a);return out;}
    static double luminance(double[] value){double sum=0;double[] weight={.2126,.7152,.0722};for(int i=0;i<3;i++){double v=value[i]/255;sum+=weight[i]*(v<=.04045?v/12.92:Math.pow((v+.055)/1.055,2.4));}return sum;}
    static double ratio(int text,double[] background){double a=luminance(color(text)),b=luminance(background);return (Math.max(a,b)+.05)/(Math.min(a,b)+.05);}
    static int checks=0;
    static void verify(String name,double ratio){if(ratio<4.5)throw new AssertionError(name+": "+ratio);checks++;System.out.printf(java.util.Locale.ROOT,"PASS %s %.3f:1%n",name,ratio);}
    public static void main(String[] args){
        double[] base=composite((OverlayContrast.BASE_ALPHA<<24)|0x171c32,color(0xffffffff));
        verify("HUD secondary on white video",ratio(OverlayContrast.SECONDARY,composite(OverlayContrast.HUD_GUARD,base)));
        verify("track detail on white video",ratio(0xfff6f5ff,composite(OverlayContrast.OPTION_GUARD,base)));
        verify("popover heading on white video",ratio(0xfff6f5ff,base));
        verify("next episode secondary on white video",ratio(OverlayContrast.SECONDARY,composite(OverlayContrast.OPTION_GUARD,base)));
        verify("drawer white text against glyph edge",ratio(0xfff6f5ff,color(OverlayContrast.DRAWER_TEXT_EDGE)));
        verify("drawer secondary against glyph edge",ratio(OverlayContrast.SECONDARY,color(OverlayContrast.DRAWER_TEXT_EDGE)));
        double last=-1;
        for(int transparency=0;transparency<=80;transparency+=5){double brightness=luminance(composite(OverlayContrast.drawerColor(transparency),color(0xffffffff)));if(brightness<=last)throw new AssertionError("Drawer setting must change the rendered fill: "+transparency);if(OverlayContrast.drawerGuardAlpha(OverlayContrast.drawerBaseAlpha(transparency))!=0)throw new AssertionError("A second scrim overrides transparency");last=brightness;checks++;}
        if(OverlayContrast.drawerColor(-20)!=OverlayContrast.drawerColor(0)||OverlayContrast.drawerColor(100)!=OverlayContrast.drawerColor(80))throw new AssertionError("Transparency clamps");checks++;
        System.out.println(checks+" actual overlay contrast checks passed");
    }
}
