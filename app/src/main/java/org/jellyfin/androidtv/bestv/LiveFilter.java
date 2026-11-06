package org.jellyfin.androidtv.bestv;

import java.util.List;

/** Display scope is independent of the source currently playing. */
public final class LiveFilter {
    public static boolean includes(String scope,boolean favorites,String id,boolean enabled,boolean favorite){
        return enabled&&(scope.isEmpty()||scope.equals(id))&&(!favorites||favorite);
    }
    public static String key(String source,String code){return source+"/"+code;}
    public static int focus(List<String> keys,String remembered,String playing){
        int at=keys.indexOf(remembered);if(at<0)at=keys.indexOf(playing);return at<0?0:at;
    }
}
