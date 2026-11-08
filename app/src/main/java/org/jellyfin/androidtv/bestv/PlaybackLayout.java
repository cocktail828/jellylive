/* Shared production geometry for design figures 09, 10, 19 and their states. GPL-2.0. */
package org.jellyfin.androidtv.bestv;

/** All coordinates are half of the 1920x1080 design. No Android dependency. */
public final class PlaybackLayout {
    private PlaybackLayout() {}
    public static final float BAR_X=32, BAR_Y=436, BAR_WIDTH=896, BAR_HEIGHT=72;
    public static final float PROGRESS_X=16, PROGRESS_Y=15, PROGRESS_WIDTH=864, PROGRESS_HEIGHT=3;
    public static final float ACTION_Y=32, ACTION_WIDTH=96, ACTION_HEIGHT=30, ACTION_GAP=8;
    public static final float DRAWER_X=680, DRAWER_WIDTH=280, DRAWER_CONTENT_X=16, DRAWER_CONTENT_WIDTH=214;
    public static float actionX(int index){if(index<0||index>2)throw new IllegalArgumentException("action index");return 576+index*104;}
    public static String value(String role,String value){
        if(value==null||value.trim().isEmpty()||java.util.Arrays.asList("关闭","默认","自动","跟随媒体","跟随频道","不可用","off","none","default","auto").contains(value.toLowerCase(java.util.Locale.ROOT)))return role;
        return value;
    }
    public static String time(long ms){long seconds=Math.max(0,ms)/1000;return String.format(java.util.Locale.ROOT,"%02d:%02d:%02d",seconds/3600,seconds/60%60,seconds%60);}
}
