package org.jellyfin.androidtv.bestv;

/** One favorite per physical hold; cancellation still consumes the matching release. */
public final class LiveConfirmGesture {
    public enum Release { NONE, SHORT, LONG, CANCELED }
    private boolean active,held,consume;
    private long deadline;
    public String source="",channel="";
    public void begin(String source,String channel,long now,long timeout){this.source=source;this.channel=channel;deadline=now+timeout;active=true;held=false;consume=true;}
    public boolean poll(long now,String source,String channel,boolean allowed){
        if(!active||held||!allowed||this.source.isEmpty()||this.channel.isEmpty()||!this.source.equals(source)||!this.channel.equals(channel)||now<deadline)return false;
        held=true;return true;
    }
    public boolean consumesRelease(){return consume;}
    public void cancel(){active=false;}
    public Release release(boolean canceled){
        Release result=!consume?Release.NONE:canceled||!active?Release.CANCELED:held?Release.LONG:Release.SHORT;
        active=false;held=false;consume=false;return result;
    }
}
