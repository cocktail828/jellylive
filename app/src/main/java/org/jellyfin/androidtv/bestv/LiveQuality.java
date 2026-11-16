/* Source-backed live quality policy; no inferred stream resolutions. GPL-2.0. */
package org.jellyfin.androidtv.bestv;

import java.text.Normalizer;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class LiveQuality {
    private LiveQuality(){}
    private static final Pattern SUFFIX=Pattern.compile("(?i)[\\s._\\-\\[(（【]*(超高清|高清|超清|标清|2160P|1080P|720P|8K|4K|UHD|FHD|HD|SD)[\\s\\])）】]*$");
    private static String normalized(String name){return Normalizer.normalize(name==null?"":name,Normalizer.Form.NFKC).trim();}
    /** Strip only an explicit trailing quality marker; retain +, region and language qualifiers. */
    public static String station(String name){
        String value=normalized(name);Matcher match=SUFFIX.matcher(value);
        if(match.find()&&match.start()>0){String base=value.substring(0,match.start()).trim();if(!base.equalsIgnoreCase("CCTV"))value=base;}
        return value.replaceAll("[\\s\\p{Z}]+","").replaceFirst("(?i)^CCTV[-_]+(?=[0-9])","CCTV").toLowerCase(Locale.ROOT);
    }
    /** Display the station once, leaving the quality labels in the actual variant menu. */
    public static String displayName(String name){
        String value=normalized(name);Matcher match=SUFFIX.matcher(value);
        if(match.find()&&match.start()>0){String base=value.substring(0,match.start()).trim();if(!base.equalsIgnoreCase("CCTV"))value=base;}
        if(value.matches("(?i)^CCTV[-_\\s]*[48]K$"))return value;
        Matcher cctv=Pattern.compile("(?i)^CCTV[-_\\s]*([0-9]+)(.*)$").matcher(value);
        return cctv.matches()?"CCTV-"+cctv.group(1)+cctv.group(2).trim():value;
    }
    /** Caller scopes these groups to one source. Empty names never become one channel. */
    public static List<List<Integer>> groups(List<String> names){
        java.util.LinkedHashMap<String,List<Integer>> groups=new java.util.LinkedHashMap<>();
        for(int i=0;i<names.size();i++){String key=station(names.get(i));if(key.isEmpty())key="unknown:"+i;groups.computeIfAbsent(key,k->new java.util.ArrayList<>()).add(i);}
        return new java.util.ArrayList<>(groups.values());
    }
    public static boolean sameStation(String first,String second){String key=station(first);return !key.isEmpty()&&key.equals(station(second));}
    public static String label(String name){
        Matcher match=SUFFIX.matcher(normalized(name));if(!match.find())return "画质";
        String token=match.group(1).toUpperCase(Locale.ROOT);
        switch(token){case "HD":case "高清":return "高清";case "SD":case "标清":return "标清";case "1080P":return "1080p";case "720P":return "720p";case "2160P":return "2160p";default:return token;}
    }
    public static int rank(String name){String label=label(name);return label.equals("高清")?0:label.equals("画质")?2:1;}
    /** A remembered successful choice wins; otherwise prefer a real HD entry, not a synthetic one. */
    public static int preferred(List<String> names,List<String> codes,String requested,String remembered){
        if(names.isEmpty())return -1;
        int selected=remembered==null?-1:codes.indexOf(remembered);if(selected>=0)return selected;
        for(int i=0;i<names.size();i++)if(label(names.get(i)).equals("高清"))return i;
        for(int i=0;i<names.size();i++){String label=label(names.get(i));if(label.equals("FHD")||label.equals("1080p")||label.equals("超清"))return i;}
        selected=codes.indexOf(requested);return selected<0?0:selected;
    }
}
