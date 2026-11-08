package org.jellyfin.androidtv.bestvlive.probe;

import android.app.*;
import android.content.*;
import android.graphics.*;
import android.os.*;
import android.view.*;
import android.widget.*;
import org.json.*;
import java.io.*;
import java.lang.reflect.*;
import java.util.*;
import java.util.function.Supplier;
import java.util.concurrent.*;

/** Runs against the installed debug APK. Uses real Android focus/layout/rendering and key events. */
public final class TvRegressionProbe extends Instrumentation {
    private Bundle arguments;
    private File output;
    private final JSONArray checks=new JSONArray(), layers=new JSONArray();
    private int failures;
    private Activity live,library;
    private List<?> resumeItems=Collections.emptyList();
    @Override public void onCreate(Bundle args){super.onCreate(args);arguments=args;start();}
    private <T>T main(Supplier<T> task){final Object[] result=new Object[1];final Throwable[] error=new Throwable[1];runOnMainSync(()->{try{result[0]=task.get();}catch(Throwable e){error[0]=e;}});if(error[0]!=null)throw new RuntimeException(error[0]);return (T)result[0];}
    private Object field(Object target,String name){try{Field f=target.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(target);}catch(Exception e){throw new RuntimeException(e);}}
    private Object call(Object target,String name,Object...args){try{for(Method m:target.getClass().getDeclaredMethods())if(m.getName().equals(name)&&m.getParameterTypes().length==args.length){m.setAccessible(true);return m.invoke(target,args);}throw new NoSuchMethodException(name);}catch(Exception e){throw new RuntimeException(e);}}
    private void check(String name,boolean passed)throws Exception{JSONObject c=new JSONObject().put("name",name).put("passed",passed);checks.put(c);if(!passed)failures++;persist("running");}
    private void persist(String error){try{JSONObject report=new JSONObject().put("phase",arguments.getString("phase")).put("failures",failures).put("checks",checks).put("layers",layers).put("error",error);try(FileWriter out=new FileWriter(new File(output,"report.json"))){out.write(report.toString(2));}}catch(Exception ignored){}}
    private String catalogProbe(){
        try{
            Object repo=field(library,"repo");ClassLoader loader=getTargetContext().getClassLoader();Class<?> continuation=Class.forName("kotlin.coroutines.Continuation",true,loader);Object context=Class.forName("kotlin.coroutines.EmptyCoroutineContext",true,loader).getField("INSTANCE").get(null);
            CountDownLatch ready=new CountDownLatch(1);Object[] outcome=new Object[1];Object callback=Proxy.newProxyInstance(loader,new Class<?>[]{continuation},(proxy,method,args)->{if(method.getName().equals("getContext"))return context;if(method.getName().equals("resumeWith")){outcome[0]=args[0];ready.countDown();}return null;});
            Method target=repo.getClass().getDeclaredMethod("catalog",java.util.UUID.class,boolean.class,continuation);Object immediate=target.invoke(repo,null,true,callback);
            if(immediate instanceof List)return "items="+((List<?>)immediate).size();
            if(!ready.await(30,TimeUnit.SECONDS))return "timed out";
            if(outcome[0] instanceof List)return "items="+((List<?>)outcome[0]).size();
            Object failed=outcome[0];Field failure=failed.getClass().getDeclaredField("exception");failure.setAccessible(true);Throwable error=(Throwable)failure.get(failed);String type=error.getClass().getName();
            if(error.getClass().getSimpleName().equals("InvalidStatusException"))type+=" status="+error.getClass().getMethod("getStatus").invoke(error);
            type+=" detail="+error.getMessage();if(error.getStackTrace().length>0)type+=" at="+error.getStackTrace()[0];return type;
        }catch(Exception e){return e.getClass().getName()+" / "+(e.getCause()==null?"":e.getCause().getClass().getName());}
    }
    private Object suspendCall(Object target,String name,Object...values)throws Exception{
        ClassLoader loader=getTargetContext().getClassLoader();Class<?> continuation=Class.forName("kotlin.coroutines.Continuation",true,loader);
        Object context=Class.forName("kotlin.coroutines.EmptyCoroutineContext",true,loader).getField("INSTANCE").get(null);
        CountDownLatch ready=new CountDownLatch(1);Object[] outcome=new Object[1];
        Object callback=Proxy.newProxyInstance(loader,new Class<?>[]{continuation},(proxy,method,args)->{if(method.getName().equals("getContext"))return context;if(method.getName().equals("resumeWith")){outcome[0]=args[0];ready.countDown();}return null;});
        Object[] args=Arrays.copyOf(values,values.length+1);args[values.length]=callback;
        Method method=null;for(Method candidate:target.getClass().getDeclaredMethods())if(candidate.getName().equals(name)&&candidate.getParameterTypes().length==args.length){method=candidate;break;}
        if(method==null)throw new NoSuchMethodException(name);method.setAccessible(true);Object result=method.invoke(target,args);
        Object suspended=Class.forName("kotlin.coroutines.intrinsics.IntrinsicsKt",true,loader).getMethod("getCOROUTINE_SUSPENDED").invoke(null);
        if(result==suspended){if(!ready.await(40,TimeUnit.SECONDS))throw new IllegalStateException("Suspend call timed out: "+name);result=outcome[0];}
        Class.forName("kotlin.ResultKt",true,loader).getMethod("throwOnFailure",Object.class).invoke(null,result);return result;
    }
    private void resumeAudit()throws Exception{
        library=startActivitySync(new Intent().setClassName(getTargetContext().getPackageName(),"org.jellyfin.androidtv.bestv.JellyLibraryActivity").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        check("resume audit media home loads",waitPage("home",30));
        Object repo=field(library,"repo");JSONObject audit=new JSONObject();List<?> resumes=Collections.emptyList();
        try{resumes=(List<?>)suspendCall(repo,"resume");audit.put("serverResumeCount",resumes.size());}catch(Exception e){JSONArray errors=new JSONArray();for(Throwable cause=e;cause!=null;cause=cause.getCause()){String type=cause.getClass().getSimpleName();try{type+=" status="+cause.getClass().getMethod("getStatus").invoke(cause);}catch(Exception ignored){}errors.put(type);}audit.put("resumeErrors",errors);}
        resumeItems=resumes;
        List<?> catalog=(List<?>)suspendCall(repo,"catalog",null,true);audit.put("catalogCount",catalog.size());JSONArray items=new JSONArray();
        for(Object item:catalog){Object user=item.getClass().getMethod("getUserData").invoke(item);JSONObject data=new JSONObject().put("type",String.valueOf(item.getClass().getMethod("getType").invoke(item)));
            if(user!=null)for(String name:new String[]{"getPlayed","getPlaybackPositionTicks","getLastPlayedDate"})data.put(name,String.valueOf(user.getClass().getMethod(name).invoke(user)));items.put(data);}
        audit.put("items",items);JSONArray positions=new JSONArray();String prefix=main(()->(String)call(library,"currentPositionPrefix"));
        for(Map.Entry<String,?> entry:getTargetContext().getSharedPreferences("jellylive_library",0).getAll().entrySet())if(entry.getKey().startsWith(prefix))positions.put(entry.getValue());
        audit.put("scopedLocalPositionsMs",positions);try(FileWriter out=new FileWriter(new File(output,"resume-audit.json"))){out.write(audit.toString(2));}
        if("true".equals(arguments.getString("expectResume"))){
            for(int i=0;i<120&&main(()->findPrefix(root(library),"resume-item-"))==null;i++)SystemClock.sleep(250);
            check("cold start restores continue watching cards",main(()->countPrefix(root(library),"resume-item-")>0));
            check("compatible server returns resume data",!resumes.isEmpty()&&!audit.has("resumeErrors"));
        }
        shot("resume-home-before");
    }
    private int countPrefix(View view,String prefix){int count=view.getTag() instanceof String&&((String)view.getTag()).startsWith(prefix)?1:0;if(view instanceof ViewGroup)for(int i=0;i<((ViewGroup)view).getChildCount();i++)count+=countPrefix(((ViewGroup)view).getChildAt(i),prefix);return count;}
    private void resumePlayback()throws Exception{
        View card=null;for(int i=0;i<120&&card==null;i++){card=main(()->findPrefix(root(library),"resume-item-"));if(card==null)SystemClock.sleep(250);}
        check("continue watching cards appear from real saved progress",card!=null);if(card==null)return;
        Set<String> expected=new HashSet<>();for(Object item:resumeItems){Object series=item.getClass().getMethod("getSeriesId").invoke(item);expected.add(String.valueOf(series==null?item.getClass().getMethod("getId").invoke(item):series));}
        check("all distinct server resume titles are shown",main(()->countPrefix(root(library),"resume-item-")>=expected.size())&&!expected.isEmpty());
        // Prefer a title whose server and local positions agree; diagnostic playback
        // must not reset another device's newer progress to an old test position.
        if(!"true".equals(arguments.getString("preferLocal")))for(Object candidate:resumeItems){long remote=((Number)candidate.getClass().getMethod("getUserData").invoke(candidate).getClass().getMethod("getPlaybackPositionTicks").invoke(candidate.getClass().getMethod("getUserData").invoke(candidate))).longValue()/10000;long local=main(()->(Long)call(library,"resumeMs",candidate));if(remote>0&&Math.abs(local-remote)<1000){String wanted="resume-item-"+candidate.getClass().getMethod("getId").invoke(candidate);View matching=tagged(library,wanted);if(matching!=null){card=matching;break;}}}
        View chosen=card;String tag=String.valueOf(card.getTag());Object item=null;for(Object candidate:resumeItems)if(tag.equals("resume-item-"+candidate.getClass().getMethod("getId").invoke(candidate))){item=candidate;break;}
        if(item==null)throw new IllegalStateException("Displayed resume item missing from resolved row");Object selected=item;
        long position=main(()->(Long)call(library,"resumeMs",selected));check("continue watching has positive saved position",position>0);
        focusTag(library,tag);key(KeyEvent.KEYCODE_DPAD_DOWN);check("resume Down reaches media category",tagOfFocus(library).startsWith("library-entry-"));key(KeyEvent.KEYCODE_DPAD_UP);check("category Up returns to resume row",tagOfFocus(library).startsWith("resume-item-"));
        focusTag(library,tag);Object previousJob=field(library,"homeResumeJob");key(KeyEvent.KEYCODE_DPAD_CENTER);check("resume card opens player",waitPage("player",10));
        for(int i=0;i<160&&!main(()->readyPlayer()||Boolean.TRUE.equals(field(library,"playbackFailure")));i++)SystemClock.sleep(250);
        check("resume playback starts at saved position; expected="+position+"; "+playerState(),main(()->{try{Object player=field(library,"player");Method current=player.getClass().getMethod("getCurrentPosition");current.setAccessible(true);return readyPlayer()&&!Boolean.TRUE.equals(field(library,"playbackFailure"))&&(Long)current.invoke(player)>=position-1500;}catch(Exception e){return false;}}));
        shot("resume-player-diagnostic");
        boolean advanced=false;for(int i=0;i<80&&!advanced;i++){advanced=main(()->{try{Object player=field(library,"player");Method current=player.getClass().getMethod("getCurrentPosition");current.setAccessible(true);return (Long)current.invoke(player)>=position+1500;}catch(Exception e){return false;}});if(!advanced)SystemClock.sleep(250);}
        check("resumed playback actually advances",advanced);main(()->{call(library,"hidePlayerControls");return null;});key(KeyEvent.KEYCODE_BACK);check("Back returns directly to media home",waitPage("home",10));
        for(int i=0;i<120&&main(()->root(library).findViewWithTag(tag)==chosen);i++)SystemClock.sleep(250);
        check("Back refreshes home resume job",main(()->field(library,"homeResumeJob")!=previousJob));
        check("Back replaces old resume card with refreshed progress",tagged(library,tag)!=null&&tagged(library,tag)!=chosen);
        shot("resume-home-after-playback");
    }
    private View tagged(Activity a,String tag){return main(()->a.getWindow().getDecorView().findViewWithTag(tag));}
    private String focus(Activity a){return main(()->{View v=a.getCurrentFocus();return v==null?"none":v.getClass().getSimpleName()+":"+v.getTag()+":"+(v instanceof TextView?((TextView)v).getText():"");});}
    private void key(int code){sendKeyDownUpSync(code);waitForIdleSync();SystemClock.sleep(180);}
    private void keepAlive(){key(KeyEvent.KEYCODE_UNKNOWN);}
    private boolean waitTag(Activity a,String tag,int seconds){for(int i=0;i<seconds*4;i++){if(tagged(a,tag)!=null)return true;if(i%4==0)keepAlive();SystemClock.sleep(250);}return false;}
    private boolean waitPage(String name,int seconds){for(int i=0;i<seconds*4;i++){if(main(()->name.equals(field(library,"pageName"))))return true;if(i%4==0)keepAlive();SystemClock.sleep(250);}return false;}
    private void focusTag(Activity a,String tag){if(main(()->a.getWindow().getDecorView().isInTouchMode()))key(KeyEvent.KEYCODE_DPAD_UP);View v=tagged(a,tag);if(v==null)throw new IllegalStateException("Missing tag "+tag);if(!main(()->v.requestFocus()))throw new IllegalStateException("Cannot focus "+tag);waitForIdleSync();SystemClock.sleep(180);}
    private void shot(String name)throws Exception{waitForIdleSync();SystemClock.sleep(250);Bitmap b=getUiAutomation().takeScreenshot();if(b==null)throw new IllegalStateException("No screenshot");try(FileOutputStream out=new FileOutputStream(new File(output,name+".png"))){b.compress(Bitmap.CompressFormat.PNG,100,out);}b.recycle();}
    private void stack(String name,View root)throws Exception{
        JSONArray problems=main(()->{JSONArray out=new JSONArray();scan(root,out);return out;});
        layers.put(new JSONObject().put("surface",name).put("occlusions",problems));
        check(name+": labels are not below raised sibling buttons",problems.length()==0);
    }
    private void scan(View view,JSONArray problems){if(!(view instanceof ViewGroup))return;ViewGroup group=(ViewGroup)view;for(int i=0;i<group.getChildCount();i++){
        View label=group.getChildAt(i);if(label instanceof TextView&&!(label instanceof Button)&&label.getVisibility()==View.VISIBLE&&((TextView)label).getText().length()>0){for(int j=0;j<i;j++){
            View button=group.getChildAt(j);if(button instanceof Button&&button.getVisibility()==View.VISIBLE&&button.getZ()>label.getZ()&&button.getLeft()<label.getRight()&&button.getRight()>label.getLeft()&&button.getTop()<label.getBottom()&&button.getBottom()>label.getTop())try{problems.put(new JSONObject().put("text",((TextView)label).getText()).put("buttonZ",button.getZ()).put("labelZ",label.getZ()).put("animator",button.getStateListAnimator()!=null));}catch(Exception e){throw new RuntimeException(e);}
        }}scan(label,problems);
    }}
    private View root(Activity a){return a.getWindow().getDecorView();}
    private boolean inSettings(){return main(()->{View v=live.getCurrentFocus(),p=(View)field(live,"settingsPanel");return v!=null&&p instanceof ViewGroup&&descendant((ViewGroup)p,v);});}
    private boolean descendant(ViewGroup p,View v){for(View current=v;current!=null;current=current.getParent() instanceof View?(View)current.getParent():null)if(current==p)return true;return false;}
    private String tagOfFocus(Activity a){return main(()->{View v=a.getCurrentFocus();return v==null?"none":String.valueOf(v.getTag());});}
    private View findPrefix(View view,String prefix){if(view.getTag() instanceof String&&((String)view.getTag()).startsWith(prefix))return view;if(view instanceof ViewGroup)for(int i=0;i<((ViewGroup)view).getChildCount();i++){View v=findPrefix(((ViewGroup)view).getChildAt(i),prefix);if(v!=null)return v;}return null;}
    private boolean channelNumbersFit(View view){
        if(view instanceof TextView&&!(view instanceof Button)){
            TextView text=(TextView)view;
            if(text.getText().toString().matches("[0-9]{1,5}")&&text.isShown()){
                if(text.getLayout()==null||text.getLayout().getLineCount()!=1||text.getPaint().measureText(text.getText().toString())>text.getWidth()-text.getPaddingLeft()-text.getPaddingRight()+1)return false;
            }
        }
        if(view instanceof ViewGroup)for(int i=0;i<((ViewGroup)view).getChildCount();i++)if(!channelNumbersFit(((ViewGroup)view).getChildAt(i)))return false;
        return true;
    }
    private void groupAndSettings()throws Exception{
        focusTag(live,"group");stack("home group",root(live));check("channel numbers stay on one line and fit badges",main(()->channelNumbersFit(root(live))));shot("01-home-group");key(KeyEvent.KEYCODE_DPAD_CENTER);
        AlertDialog group=main(()->(AlertDialog)field(live,"dialog"));stack("group selected",group.getWindow().getDecorView());shot("02-group-selected");key(KeyEvent.KEYCODE_DPAD_DOWN);stack("group focused",group.getWindow().getDecorView());shot("03-group-focused");key(KeyEvent.KEYCODE_BACK);
        focusTag(live,"tile-3");key(KeyEvent.KEYCODE_DPAD_CENTER);check("settings opens on selected category",tagOfFocus(live).equals("settings-0"));shot("04-settings-category");
        key(KeyEvent.KEYCODE_DPAD_RIGHT);check("settings right enters options",inSettings());key(KeyEvent.KEYCODE_DPAD_UP);check("settings top edge stays in option column",tagOfFocus(live).equals("setting-option-48"));stack("settings first option",root(live));shot("05-settings-option");
        int[] positions={48,88,128,168,248,288};
        for(int i=0;i<positions.length;i++){check("settings down visits option "+positions[i]+" ("+focus(live)+")",tagOfFocus(live).equals("setting-option-"+positions[i]));if(i<positions.length-1)key(KeyEvent.KEYCODE_DPAD_DOWN);}
        key(KeyEvent.KEYCODE_DPAD_DOWN);check("settings bottom edge stays in option column",tagOfFocus(live).equals("setting-option-288"));key(KeyEvent.KEYCODE_DPAD_RIGHT);check("settings right edge stays on current option",tagOfFocus(live).equals("setting-option-288"));key(KeyEvent.KEYCODE_DPAD_LEFT);check("settings left returns selected category",tagOfFocus(live).equals("settings-0"));key(KeyEvent.KEYCODE_DPAD_RIGHT);check("settings right restores last option",tagOfFocus(live).equals("setting-option-288"));
        key(KeyEvent.KEYCODE_DPAD_UP);check("settings up visits transparency before next episode",tagOfFocus(live).equals("setting-option-248"));focusTag(live,"setting-option-248");key(KeyEvent.KEYCODE_DPAD_CENTER);shot("06-settings-system-dialog");key(KeyEvent.KEYCODE_BACK);key(KeyEvent.KEYCODE_DPAD_LEFT);
        for(int i=1;i<5;i++){focusTag(live,"settings-"+(i-1));key(KeyEvent.KEYCODE_DPAD_DOWN);check("settings category "+i+" reachable",tagOfFocus(live).equals("settings-"+i));focusTag(live,"settings-"+i);key(KeyEvent.KEYCODE_DPAD_RIGHT);check("settings category "+i+" right behavior",i==4?tagOfFocus(live).equals("settings-4"):inSettings());if(i!=4)key(KeyEvent.KEYCODE_DPAD_LEFT);}
        key(KeyEvent.KEYCODE_BACK);
    }
    private void livePlayer()throws Exception{
        main(()->{call(live,"enterFull");return null;});waitForIdleSync();main(()->{call(live,"showControls");return null;});waitForIdleSync();shot("07-live-controls");main(()->{if(!(Boolean)field(live,"controlsVisible"))call(live,"showControls");((View)field(live,"aspectAction")).requestFocus();return null;});key(KeyEvent.KEYCODE_DPAD_CENTER);
        AlertDialog popup=main(()->(AlertDialog)field(live,"dialog"));check("live aspect menu opens",popup!=null&&popup.isShowing());if(popup!=null){stack("live player popover",popup.getWindow().getDecorView());shot("08-live-aspect-selected");key(KeyEvent.KEYCODE_DPAD_DOWN);stack("live popover new focus",popup.getWindow().getDecorView());shot("09-live-aspect-focused");key(KeyEvent.KEYCODE_BACK);}
        if(main(()->(Boolean)field(live,"controlsVisible")))key(KeyEvent.KEYCODE_BACK); // hide controls if screenshot time has not already reached idle
        main(()->{call(live,"showDrawer");return null;});waitForIdleSync();stack("fullscreen group",root(live));shot("10-live-drawer");
        if(main(()->(Boolean)field(live,"drawerVisible")))key(KeyEvent.KEYCODE_BACK);main(()->{if(!(Boolean)field(live,"controlsVisible"))call(live,"showControls");return null;});waitForIdleSync();SystemClock.sleep(6200);check("live controls actually collapse after idle",main(()->!(Boolean)field(live,"controlsVisible")));key(KeyEvent.KEYCODE_BACK);
    }
    private void media()throws Exception{
        Intent intent=new Intent().setClassName(getTargetContext().getPackageName(),"org.jellyfin.androidtv.bestv.JellyLibraryActivity").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        if(live!=null){
            ActivityMonitor monitor=addMonitor("org.jellyfin.androidtv.bestv.JellyLibraryActivity",null,false);
            focusTag(live,"tile-2");key(KeyEvent.KEYCODE_DPAD_CENTER);
            library=waitForMonitorWithTimeout(monitor,15000);removeMonitor(monitor);
            if(library==null)throw new IllegalStateException("Library entry did not open");
            check("library entry releases live decoder",main(()->Boolean.TRUE.equals(field(live,"playerReleasedForLibrary"))));
        }else library=startActivitySync(intent);
        check("media home loads real server",waitPage("home",30));shot("11-library-home");
        if(!main(()->"home".equals(field(library,"pageName")))){check("media home remains usable; catalog probe: "+catalogProbe(),false);return;}
        focusTag(library,"library-entry-movies");key(KeyEvent.KEYCODE_DPAD_CENTER);check("media category loads",waitPage("grid",30));
        View poster=null;for(int i=0;i<40&&poster==null;i++){poster=main(()->findPrefix(root(library),"grid-item-"));if(poster==null)SystemClock.sleep(250);}
        check("real movie poster available",poster!=null);shot("12-library-grid");if(poster==null)return;
        if(!"true".equals(arguments.getString("skipFilter"))){focusTag(library,"grid-filter");key(KeyEvent.KEYCODE_DPAD_CENTER);shot("13-library-filter");key(KeyEvent.KEYCODE_BACK);focusTag(library,"grid-sort");key(KeyEvent.KEYCODE_DPAD_CENTER);shot("14-library-sort");key(KeyEvent.KEYCODE_BACK);}
        View chosen=poster;main(()->chosen.requestFocus());key(KeyEvent.KEYCODE_DPAD_CENTER);check("real media details load",waitPage("movie",30));SystemClock.sleep(1200);shot("15-movie-details");
        int more=main(()->buttons(root(library),"更多").size());check("details have no More action",more==0);
        check("three detail actions have equal widths",main(()->{List<Button> actions=buttons(root(library),null);actions.removeIf(b->{String value=b.getText().toString();return !value.startsWith("播放")&&!value.startsWith("继续播放")&&!value.startsWith("从头播放")&&!value.startsWith("标为");});return actions.size()==3&&actions.get(0).getWidth()>0&&actions.stream().allMatch(b->b.getWidth()==actions.get(0).getWidth());}));
        List<Button> play=main(()->{List<Button> all=buttons(root(library),null);all.removeIf(b->"true".equals(arguments.getString("fromStart"))?!b.getText().toString().equals("从头播放"):!b.getText().toString().startsWith("播放")&&!b.getText().toString().startsWith("继续播放"));return all;});
        if(play.isEmpty()){check("play action available",false);return;}main(()->play.get(0).requestFocus());key(KeyEvent.KEYCODE_DPAD_CENTER);check("media player enters",waitPage("player",5));
        for(int i=0;i<160;i++){if(main(()->readyPlayer()||Boolean.TRUE.equals(field(library,"playbackFailure"))))break;SystemClock.sleep(250);}
        check("media stream prepared without error: "+playerState(),main(()->readyPlayer()&&!Boolean.TRUE.equals(field(library,"playbackFailure"))));
        if(!main(()->readyPlayer()&&!Boolean.TRUE.equals(field(library,"playbackFailure")))){shot("16-media-playback-failure");return;}
        main(()->{call(library,"showControls",true);return null;});waitForIdleSync();shot("16-media-controls");main(()->{call(library,"showControls",true);((View)field(library,"speedAction")).requestFocus();return null;});key(KeyEvent.KEYCODE_DPAD_CENTER);
        AlertDialog speed=main(()->(AlertDialog)field(library,"dialog"));check("media speed menu opens",speed!=null&&speed.isShowing());if(speed!=null){stack("media player popover",speed.getWindow().getDecorView());shot("17-media-speed-selected");key(KeyEvent.KEYCODE_DPAD_DOWN);stack("media popover new focus",speed.getWindow().getDecorView());shot("18-media-speed-focused");key(KeyEvent.KEYCODE_BACK);}
        main(()->{call(library,"showControls",true);((View)field(library,"audioAction")).requestFocus();return null;});key(KeyEvent.KEYCODE_DPAD_CENTER);AlertDialog audio=main(()->(AlertDialog)field(library,"dialog"));if(audio!=null){stack("media audio popover",audio.getWindow().getDecorView());shot("19-media-audio");key(KeyEvent.KEYCODE_BACK);}
        main(()->{call(library,"showControls",true);((View)field(library,"subtitleAction")).requestFocus();return null;});key(KeyEvent.KEYCODE_DPAD_CENTER);AlertDialog sub=main(()->(AlertDialog)field(library,"dialog"));if(sub!=null){stack("media subtitle popover",sub.getWindow().getDecorView());shot("20-media-subtitles");key(KeyEvent.KEYCODE_BACK);}
        main(()->{((View)field(library,"buffering")).setVisibility(View.VISIBLE);return null;});shot("22-buffering-preview");
        check("buffering preview text fits font scale",main(()->{ViewGroup panel=(ViewGroup)field(library,"buffering");for(int i=0;i<panel.getChildCount();i++){View child=panel.getChildAt(i);if(child instanceof TextView){TextView text=(TextView)child;if(text.getLayout()==null||text.getLayout().getHeight()>text.getHeight())return false;}}return true;}));
        main(()->{((View)field(library,"buffering")).setVisibility(View.GONE);call(library,"hidePlayerControls");return null;});check("media controls hide",main(()->!((View)field(library,"controls")).isShown()));
    }
    private String playerState(){return main(()->{try{Object p=field(library,"player");if(p==null)return "no player";StringBuilder out=new StringBuilder();for(String name:new String[]{"getPlaybackState","getPlayWhenReady","getDuration","getBufferedPosition","getTotalBufferedDuration","getVideoFormat"}){Method method=p.getClass().getMethod(name);method.setAccessible(true);out.append(name).append('=').append(method.invoke(p)).append(';');}Method counters=p.getClass().getMethod("getVideoDecoderCounters");counters.setAccessible(true);Object value=counters.invoke(p);if(value!=null)for(String name:new String[]{"decoderInitCount","decoderReleaseCount","queuedInputBufferCount","renderedOutputBufferCount"})out.append(name).append('=').append(value.getClass().getField(name).get(value)).append(';');if(!readyPlayer())for(Map.Entry<Thread,StackTraceElement[]> entry:Thread.getAllStackTraces().entrySet()){String name=entry.getKey().getName();if(name.contains("ExoPlayer")||name.contains("Loader")){out.append(" THREAD ").append(name);int n=0;for(StackTraceElement frame:entry.getValue()){out.append(" <- ").append(frame);if(++n>=12)break;}}}return out.toString();}catch(Exception e){return e.getClass().getSimpleName();}});}
    private boolean readyPlayer(){try{Object p=field(library,"player");if(p==null)return false;Method state=p.getClass().getMethod("getPlaybackState");state.setAccessible(true);return ((Integer)state.invoke(p))==3;}catch(Exception e){throw new RuntimeException(e);}}
    private List<Button> buttons(View v,String text){List<Button> result=new ArrayList<>();if(v instanceof Button&&(text==null||((Button)v).getText().toString().equals(text)))result.add((Button)v);if(v instanceof ViewGroup)for(int i=0;i<((ViewGroup)v).getChildCount();i++)result.addAll(buttons(((ViewGroup)v).getChildAt(i),text));return result;}
    private void returnToLive()throws Exception{
        if(live==null)return;
        for(int i=0;i<6&&!main(()->live.hasWindowFocus());i++){if(main(()->library.isFinishing()||library.isDestroyed()))break;key(KeyEvent.KEYCODE_BACK);}
        for(int i=0;i<40&&!main(()->live.hasWindowFocus());i++)SystemClock.sleep(250);
        check("Back restores live activity",main(()->live.hasWindowFocus()));
        check("live decoder is rebuilt on return",main(()->!Boolean.TRUE.equals(field(live,"playerReleasedForLibrary"))));
        for(int i=0;i<120;i++){if(main(()->{try{Object p=field(live,"player");Method state=p.getClass().getMethod("getPlaybackState");state.setAccessible(true);return ((Integer)state.invoke(p))==3;}catch(Exception e){return false;}}))break;SystemClock.sleep(250);}
        check("live playback is ready after return",main(()->{try{Object p=field(live,"player");Method state=p.getClass().getMethod("getPlaybackState");state.setAccessible(true);return ((Integer)state.invoke(p))==3;}catch(Exception e){return false;}}));
        shot("21-return-live");
    }
    private void snapshotFallback()throws Exception{
        Activity target=library!=null?library:live;
        boolean[] outcome=main(()->{
            ViewGroup host=target.findViewById(android.R.id.content);
            FrameLayout panel=new FrameLayout(target);panel.setAlpha(.75f);host.addView(panel,new ViewGroup.LayoutParams(1,1));panel.layout(0,0,1,1);
            View incompatible=new View(target){@Override protected void onDraw(Canvas canvas){if(!canvas.isHardwareAccelerated())throw new IllegalArgumentException("probe software snapshot rejection");}};
            incompatible.setWillNotDraw(false);host.addView(incompatible,new ViewGroup.LayoutParams(1,1));incompatible.layout(0,0,1,1);
            boolean threw=false;
            try{Class<?> type=Class.forName("org.jellyfin.androidtv.bestv.TvGlass",true,getTargetContext().getClassLoader());for(Method method:type.getDeclaredMethods())if(method.getName().equals("apply")){method.invoke(null,target,field(target,"ui"),panel,0f,0f,1f,1f);break;}}
            catch(Exception failure){threw=true;}
            boolean restored=Math.abs(panel.getAlpha()-.75f)<.001f;
            host.removeView(incompatible);host.removeView(panel);return new boolean[]{!threw,restored};
        });
        check("snapshot rejection does not escape",outcome[0]);check("snapshot rejection restores panel opacity",outcome[1]);
    }
    @Override public void onStart(){
        String phase=arguments.getString("phase","run").replaceAll("[^a-zA-Z0-9_-]","");output=new File(getTargetContext().getExternalFilesDir("tv-regression"),phase);output.mkdirs();
        String error="";
        try{
            if("true".equals(arguments.getString("resumeAudit"))||"true".equals(arguments.getString("resumeVerify"))){resumeAudit();if("true".equals(arguments.getString("resumeVerify")))resumePlayback();}else {
            if(!"true".equals(arguments.getString("mediaOnly"))){live=startActivitySync(new Intent().setClassName(getTargetContext().getPackageName(),"org.jellyfin.androidtv.bestv.LiveActivity").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            check("live home starts",waitTag(live,"group",25));SystemClock.sleep(1000);groupAndSettings();livePlayer();}media();snapshotFallback();returnToLive();}
        }catch(Throwable e){failures++;error=e.toString();try{shot("fatal-state");}catch(Exception ignored){}}
        try{JSONObject report=new JSONObject().put("phase",phase).put("failures",failures).put("checks",checks).put("layers",layers).put("error",error).put("scope","Installed Android APK; real key events, view Z/focus inspection and screenshots; private reflection only chooses test entry points");try(FileWriter out=new FileWriter(new File(output,"report.json"))){out.write(report.toString(2));}}catch(Exception e){error=e.toString();}
        main(()->{if(library!=null&&!library.isDestroyed())library.finish();if(live!=null&&!live.isDestroyed())live.finish();return null;});waitForIdleSync();SystemClock.sleep(700);
        Bundle results=new Bundle();results.putString("report",new File(output,"report.json").toString());results.putInt("failures",failures);results.putString("error",error);finish(failures==0?0:1,results);
    }
}
