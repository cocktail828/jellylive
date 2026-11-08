/* Shared actual Media3 track labels and selection. GPL-2.0. */
package org.jellyfin.androidtv.bestv;
import androidx.media3.common.*;
import java.util.*;

public final class MediaTracks {
    private MediaTracks(){}
    public static final class Choice {
        public final Tracks.Group group;public final int index;public final String name,detail,language;
        Choice(Tracks.Group group,int index){this.group=group;this.index=index;Format f=group.getTrackFormat(index);language=f.language;name=name(f,index,group.getType());detail=detail(f,group.getType());}
        public boolean selected(){return group.isTrackSelected(index);}
    }
    public static List<Choice> choices(Player player,int type){List<Choice> result=new ArrayList<>();for(Tracks.Group group:player.getCurrentTracks().getGroups())if(group.getType()==type)for(int i=0;i<group.length;i++)if(group.isTrackSupported(i))result.add(new Choice(group,i));return result;}
    public static String value(Player player,int type){String role=type==C.TRACK_TYPE_TEXT?"字幕":"音轨";if(player==null||player.getTrackSelectionParameters().disabledTrackTypes.contains(type))return role;for(Choice c:choices(player,type))if(c.selected())return PlaybackLayout.value(role,c.name);return role;}
    public static void select(Player player,int type,Choice choice){TrackSelectionParameters.Builder builder=player.getTrackSelectionParameters().buildUpon().clearOverridesOfType(type).setTrackTypeDisabled(type,choice==null);if(choice!=null){builder.setOverrideForType(new TrackSelectionOverride(choice.group.getMediaTrackGroup(),choice.index));if(choice.language!=null){if(type==C.TRACK_TYPE_AUDIO)builder.setPreferredAudioLanguage(choice.language);else builder.setPreferredTextLanguage(choice.language);}}player.setTrackSelectionParameters(builder.build());}
    private static String name(Format f,int index,int type){
        if(f.label!=null&&!f.label.trim().isEmpty())return f.label;
        String language=f.language;if(language==null||language.equals("und")||language.isEmpty())return (type==C.TRACK_TYPE_TEXT?"字幕 ":"音轨 ")+(index+1);
        switch(language.toLowerCase(Locale.ROOT)){case "zh":case "zho":case "chi":case "zh-cn":case "zh-hans":return type==C.TRACK_TYPE_TEXT?"简体中文":"普通话";case "zh-tw":case "zh-hant":return "繁体中文";case "en":case "eng":return "English";case "ja":case "jpn":return "日本語";case "ko":case "kor":return "한국어";default:return Locale.forLanguageTag(language).getDisplayLanguage(Locale.SIMPLIFIED_CHINESE);}
    }
    private static String detail(Format f,int type){String mime=f.sampleMimeType==null?"":f.sampleMimeType;String codec=mime.contains("mp4a")?"AAC":mime.contains("eac3")?"E-AC3":mime.contains("ac3")?"AC3":mime.contains("dts")?"DTS":mime.contains("opus")?"Opus":mime.contains("flac")?"FLAC":mime.contains("subrip")?"SRT":mime.contains("vtt")?"WebVTT":mime.contains("ssa")?"ASS":mime.contains("ttml")?"TTML":"";
        if(type==C.TRACK_TYPE_TEXT)return codec.isEmpty()?"字幕":codec;
        String channels=f.channelCount==1?"单声道":f.channelCount==2?"双声道":f.channelCount==6?"5.1":f.channelCount==8?"7.1":f.channelCount>0?f.channelCount+" 声道":"";
        return codec+(codec.isEmpty()||channels.isEmpty()?"":" · ")+channels;
    }
}
