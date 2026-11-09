import java.util.*;
import org.jellyfin.androidtv.bestv.LiveQuality;
public final class LiveQualityCheck {
    private static int count;
    private static void verify(String name,boolean value){if(!value)throw new AssertionError(name);count++;}
    public static void main(String[] args){
        verify("observed CCTV1 pair matches",LiveQuality.sameStation("CCTV-1","CCTV-1高清"));
        verify("normalizes explicit HD marker",LiveQuality.sameStation("ＣＣＴＶ－１ HD","CCTV1"));
        verify("separates CCTV1 and CCTV10",!LiveQuality.sameStation("CCTV-1高清","CCTV-10高清"));
        verify("preserves sports plus",!LiveQuality.sameStation("CCTV-5","CCTV-5+高清"));
        verify("preserves region",!LiveQuality.sameStation("CCTV-4亚洲高清","CCTV-4欧洲高清"));
        verify("preserves language",!LiveQuality.sameStation("新闻 English HD","新闻 中文高清"));
        verify("does not merge dedicated UHD stations",!LiveQuality.sameStation("CCTV4K","CCTV8K"));
        verify("does not guess unmarked definition",LiveQuality.label("CCTV-1").equals("画质"));
        verify("known HD remains HD",LiveQuality.label("CCTV-1高清").equals("高清"));
        verify("only explicit SD gets SD",LiveQuality.label("新闻 SD").equals("标清"));
        verify("keeps supplied pixel label",LiveQuality.label("新闻 1080p").equals("1080p"));
        verify("actual 4K label",LiveQuality.label("新闻 4K").equals("4K"));
        verify("missing name is not another empty station",!LiveQuality.sameStation("",""));
        List<String> names=Arrays.asList("CCTV-1","CCTV-1高清","CCTV-1 4K"),codes=Arrays.asList("plain","hd","uhd");
        verify("defaults to real HD",LiveQuality.preferred(names,codes,"plain",null)==1);
        verify("does not force 4K from display size",LiveQuality.preferred(names,codes,"uhd",null)==1);
        verify("successful manual choice wins",LiveQuality.preferred(names,codes,"hd","plain")==0);
        verify("removed remembered choice falls back",LiveQuality.preferred(names,codes,"plain","removed")==1);
        verify("no synthetic HD entry",LiveQuality.preferred(Arrays.asList("CCTV-1"),Arrays.asList("plain"),"plain",null)==0);
        verify("empty list yields no choice",LiveQuality.preferred(Collections.emptyList(),Collections.emptyList(),"",null)==-1);
        System.out.println(count+" source-backed live quality checks passed");
    }
}
