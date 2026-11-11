package org.jellyfin.androidtv.bestv;

/** Source colors used by both renderers, including the no-PixelCopy fallback. */
public final class OverlayContrast {
    private OverlayContrast(){}
    public static final int SECONDARY=0xffd7dcec, BASE_ALPHA=166;
    public static final int HUD_GUARD=0x5c080d1c, OPTION_GUARD=0x38080d1c;
    public static int drawerBaseAlpha(int transparency){return Math.round(255*(100-Math.max(0,Math.min(80,transparency)))/100f);}
    public static final int DRAWER_TEXT_EDGE=0xff080c18;
    public static int drawerColor(int transparency){return (drawerBaseAlpha(transparency)<<24)|0x00111629;}
    /** Readability belongs to glyph outlines; no second scrim may override user transparency. */
    public static int drawerGuardAlpha(int baseAlpha){return 0;}
}
