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
    private Activity live,library,reviewBox;
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
        if(result!=null&&result.getClass().getName().equals("kotlin.Result")){Method unbox=result.getClass().getDeclaredMethod("unbox-impl");unbox.setAccessible(true);result=unbox.invoke(result);}
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
    private Object invokeAny(Object target,String name,Object...args)throws Exception{
        for(Method method:target.getClass().getMethods())if(method.getName().equals(name)&&method.getParameterTypes().length==args.length){method.setAccessible(true);return method.invoke(target,args);}
        throw new NoSuchMethodException(name);
    }
    private JSONObject modelJson(Object model)throws Exception{
        ClassLoader loader=getTargetContext().getClassLoader();Object companion=model.getClass().getField("Companion").get(null);
        Object serializer=invokeAny(companion,"serializer");Object instance=Class.forName("org.jellyfin.sdk.api.client.util.ApiSerializer",true,loader).getField("INSTANCE").get(null);
        Object json=invokeAny(instance,"getJson");return new JSONObject((String)invokeAny(json,"encodeToString",serializer,model));
    }
    private void playbackAudit()throws Exception{
        library=startActivitySync(new Intent().setClassName(getTargetContext().getPackageName(),"org.jellyfin.androidtv.bestv.JellyLibraryActivity").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        check("playback audit restores actual connection",waitPage("home",30));Object repo=field(library,"repo");List<?> catalog=(List<?>)suspendCall(repo,"catalog",null,true);
        if(catalog.size()!=2)throw new IllegalStateException("This diagnostic requires exactly two movie samples; choose a bounded sample before running on a larger library");
        Object serverRepo=invokeAny(repo,"getServers");Object server=invokeAny(invokeAny(serverRepo,"getCurrentServer"),"getValue");Object version=invokeAny(server,"getServerVersion");
        Object preferences=main(()->call(library,"getPreferences"));ClassLoader loader=getTargetContext().getClassLoader();Object profile=null;
        for(Method method:Class.forName("org.jellyfin.androidtv.util.profile.DeviceProfileKt",true,loader).getMethods())if(method.getName().equals("createDeviceProfile")&&method.getParameterTypes().length==3){profile=method.invoke(null,library,preferences,version);break;}
        if(profile==null)throw new IllegalStateException("No device profile");JSONObject profileJson=modelJson(profile);JSONObject audit=new JSONObject().put("deviceProfile",profileJson);JSONArray items=new JSONArray();
        JSONArray decoders=new JSONArray();for(android.media.MediaCodecInfo info:new android.media.MediaCodecList(android.media.MediaCodecList.REGULAR_CODECS).getCodecInfos())if(!info.isEncoder())for(String type:info.getSupportedTypes())if(type.equalsIgnoreCase("video/hevc")){JSONArray levels=new JSONArray();for(android.media.MediaCodecInfo.CodecProfileLevel level:info.getCapabilitiesForType(type).profileLevels)levels.put(new JSONObject().put("profile",level.profile).put("level",level.level));decoders.put(new JSONObject().put("name",info.getName()).put("profileLevels",levels));}audit.put("hevcDecoders",decoders);
        Object compatibility=Class.forName("org.jellyfin.androidtv.util.sdk.FnMediaCompatibility",true,loader).newInstance();Method supports=compatibility.getClass().getDeclaredMethod("supportsSource",JSONObject.class,JSONObject.class);supports.setAccessible(true);
        Object manager=main(()->call(library,"getPlayback"));String methodName=null;for(Method method:manager.getClass().getDeclaredMethods())if(method.getName().startsWith("getVideoStreamInfoInternal"))methodName=method.getName();
        for(Object brief:catalog){Object id=invokeAny(brief,"getId");Object item=suspendCall(repo,"detail",id);JSONObject data=modelJson(item);Object sources=invokeAny(item,"getMediaSources");Object first=sources instanceof List&&!((List<?>)sources).isEmpty()?((List<?>)sources).get(0):null;
            Object options=Class.forName("org.jellyfin.androidtv.data.compat.VideoOptions",true,loader).newInstance();invokeAny(options,"setItemId",id);invokeAny(options,"setProfile",profile);invokeAny(options,"setMediaSources",sources);if(first!=null)invokeAny(options,"setMediaSourceId",invokeAny(first,"getId"));
            try{Object stream=suspendCall(manager,methodName,options,0L);data.put("resolveResultClass",stream.getClass().getName());data.put("resolvedStream",new JSONObject().put("hasUrl",invokeAny(stream,"getMediaUrl")!=null).put("playMethod",String.valueOf(invokeAny(stream,"getPlayMethod"))).put("source",invokeAny(stream,"getMediaSource")==null?JSONObject.NULL:modelJson(invokeAny(stream,"getMediaSource"))));}
            catch(Exception e){JSONArray errors=new JSONArray();for(Throwable cause=e;cause!=null;cause=cause.getCause())errors.put(cause.getClass().getSimpleName());data.put("resolveErrors",errors);}
            items.put(data);
        }
        for(int index=0;index<items.length();index++){
            JSONObject item=items.getJSONObject(index);JSONObject source=item.getJSONArray("MediaSources").getJSONObject(0);JSONObject control=new JSONObject(profileJson.toString());
            JSONArray codecs=control.getJSONArray("CodecProfiles");for(int i=0;i<codecs.length();i++){JSONObject codec=codecs.getJSONObject(i);if(!codec.optString("Codec").equals("hevc"))continue;JSONArray conditions=codec.optJSONArray("Conditions");if(conditions!=null)for(int j=0;j<conditions.length();j++){JSONObject condition=conditions.getJSONObject(j);if(condition.optString("Property").equals("VideoProfile")&&condition.optString("Condition").equals("EqualsAny"))condition.put("Value",condition.optString("Value")+",main 10");}}
            boolean actual=(Boolean)supports.invoke(compatibility,source,profileJson),allowed=(Boolean)supports.invoke(compatibility,source,control);
            item.put("main10OnlyControl",new JSONObject().put("actualProfileAllows",actual).put("localCopyAllowingMain10Allows",allowed));
            check("playback API returns StreamInfo for movie "+index,item.has("resolvedStream")&&!item.has("resolveErrors"));
            JSONArray streams=source.getJSONArray("MediaStreams");boolean tenBit=false;for(int i=0;i<streams.length();i++)if(streams.getJSONObject(i).optString("Type").equals("Video"))tenBit=streams.getJSONObject(i).optInt("BitDepth")==10;
            if(tenBit){check("Main10 is blocked by actual capability and has no stream URL",!actual&&!item.getJSONObject("resolvedStream").getBoolean("hasUrl"));check("only allowing Main10 in a diagnostic copy removes rejection",allowed);}
            else check("Main8 retains a real stream URL",actual&&item.getJSONObject("resolvedStream").getBoolean("hasUrl"));
        }
        audit.put("items",items);try(FileWriter out=new FileWriter(new File(output,"playback-audit-private.json"))){out.write(audit.toString(2));}
        check("playback audit inspected both real movies",items.length()==2);
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
    private JSONObject displayGeometry(){return main(()->{try{Activity activity=library!=null?library:live;android.util.DisplayMetrics screen=new android.util.DisplayMetrics();WindowManager manager=activity==null?(WindowManager)getTargetContext().getSystemService(Context.WINDOW_SERVICE):activity.getWindowManager();manager.getDefaultDisplay().getRealMetrics(screen);return new JSONObject().put("width",screen.widthPixels).put("height",screen.heightPixels).put("fontScale",(activity==null?getTargetContext():activity).getResources().getConfiguration().fontScale).put("densityDpi",screen.densityDpi);}catch(Exception e){throw new RuntimeException(e);}});}
    private JSONObject focusGeometry(Activity activity){JSONObject info=new JSONObject();try{View v=activity.getCurrentFocus();if(v==null)return info;int[] xy=new int[2];v.getLocationOnScreen(xy);Rect visible=new Rect();v.getGlobalVisibleRect(visible);android.util.DisplayMetrics metrics=activity.getResources().getDisplayMetrics(),real=new android.util.DisplayMetrics();activity.getWindowManager().getDefaultDisplay().getRealMetrics(real);info.put("view",new JSONArray(Arrays.asList(xy[0],xy[1],v.getWidth(),v.getHeight()))).put("visible",visible.flattenToString()).put("resourceDisplay",new JSONArray(Arrays.asList(metrics.widthPixels,metrics.heightPixels))).put("realDisplay",new JSONArray(Arrays.asList(real.widthPixels,real.heightPixels))).put("fontScale",activity.getResources().getConfiguration().fontScale);}catch(Exception e){throw new RuntimeException(e);}return info;}
    private boolean focusedSafe(Activity activity){View v=activity.getCurrentFocus();if(v==null)return false;if(!(v instanceof Button)&&!(v instanceof EditText)&&!(v instanceof ScrollView))return true;Rect visible=new Rect();int[] location=new int[2];v.getLocationOnScreen(location);android.util.DisplayMetrics screen=new android.util.DisplayMetrics();activity.getWindowManager().getDefaultDisplay().getRealMetrics(screen);int width=screen.widthPixels,height=screen.heightPixels;return v.getGlobalVisibleRect(visible)&&visible.width()>=v.getWidth()-2&&visible.height()>=v.getHeight()-2&&location[0]>=width*.05-2&&location[0]+v.getWidth()<=width*.95+2&&location[1]>=height*.05-2&&location[1]+v.getHeight()<=height*.95+2;}
    private void shot(String name)throws Exception{waitForIdleSync();SystemClock.sleep(250);if("true".equals(arguments.getString("configuredReview"))||"true".equals(arguments.getString("connectionReview"))||"true".equals(arguments.getString("settingsReview"))||"true".equals(arguments.getString("mediaReview"))||"true".equals(arguments.getString("liveReview"))||"true".equals(arguments.getString("searchReview"))){Activity target=library!=null&&!library.isFinishing()?library:reviewBox!=null&&!reviewBox.isFinishing()?reviewBox:live;if(target!=null){boolean safe=main(()->focusedSafe(target));if(!safe)layers.put(new JSONObject().put("focusGeometry",main(()->focusGeometry(target))).put("shot",name));check("4K focused control fully visible and safe: "+name,safe);}}Bitmap b=getUiAutomation().takeScreenshot();if(b==null)throw new IllegalStateException("No screenshot");try(FileOutputStream out=new FileOutputStream(new File(output,name+".png"))){b.compress(Bitmap.CompressFormat.PNG,100,out);}b.recycle();}
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
        key(KeyEvent.KEYCODE_DPAD_RIGHT);check("settings right enters options",inSettings());key(KeyEvent.KEYCODE_DPAD_UP);check("settings top edge stays in option column",tagOfFocus(live).equals("auto_play"));stack("settings first option",root(live));shot("05-settings-option");
        String[] positions={"auto_play","aspect","audio","subtitle","auto_next"};
        for(int i=0;i<positions.length;i++){check("settings visits row "+positions[i],tagOfFocus(live).equals(positions[i]));if(i<positions.length-1)key(KeyEvent.KEYCODE_DPAD_DOWN);}
        key(KeyEvent.KEYCODE_DPAD_DOWN);check("settings bottom stays in column",tagOfFocus(live).equals("auto_next"));key(KeyEvent.KEYCODE_DPAD_LEFT);check("settings left returns category",tagOfFocus(live).equals("settings-0"));key(KeyEvent.KEYCODE_DPAD_RIGHT);check("settings restores row",tagOfFocus(live).equals("auto_next"));
        check("player-only settings removed",main(()->root(live).findViewWithTag("aspect")==null&&root(live).findViewWithTag("audio")==null&&root(live).findViewWithTag("subtitle")==null));
        for(int i=1;i<5;i++){focusTag(live,"settings-"+i);key(KeyEvent.KEYCODE_DPAD_RIGHT);check("settings category content reachable "+i,inSettings());key(KeyEvent.KEYCODE_BACK);}
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
    private List<EditText> inputs(View v){List<EditText> out=new ArrayList<>();if(v instanceof EditText)out.add((EditText)v);if(v instanceof ViewGroup)for(int i=0;i<((ViewGroup)v).getChildCount();i++)out.addAll(inputs(((ViewGroup)v).getChildAt(i)));return out;}
    /** Temporary loopback server; all fixtures are synthetic and removed after the phase. */
    private static final class ConnectionFixture implements AutoCloseable {
        static String version="10.11.0";
        final java.net.ServerSocket socket;final ExecutorService worker=Executors.newCachedThreadPool();final UUID serverId=UUID.randomUUID();final UUID userId;
        final java.util.concurrent.atomic.AtomicInteger userReads=new java.util.concurrent.atomic.AtomicInteger();volatile int status=503,delayMs=0;
        ConnectionFixture(UUID user)throws Exception{userId=user;socket=new java.net.ServerSocket(0,8,java.net.InetAddress.getByName("127.0.0.1"));worker.execute(()->{while(!socket.isClosed())try{java.net.Socket client=socket.accept();worker.execute(()->respond(client));}catch(Exception ignored){}});}
        String address(){return "http://127.0.0.1:"+socket.getLocalPort();}
        void respond(java.net.Socket client){try(java.net.Socket ignored=client){client.setSoTimeout(5000);BufferedReader reader=new BufferedReader(new InputStreamReader(client.getInputStream(),java.nio.charset.StandardCharsets.UTF_8));String request=reader.readLine();if(request==null)return;for(String line;(line=reader.readLine())!=null&&!line.isEmpty();){}boolean user=request.toLowerCase(java.util.Locale.ROOT).contains("/users/");if(user){userReads.incrementAndGet();if(delayMs>0)Thread.sleep(delayMs);}int code=user?status:200;String body=user?"{\"Id\":\""+userId+"\",\"Name\":\"测试账号\",\"ServerId\":\""+serverId+"\",\"HasPassword\":true,\"HasConfiguredPassword\":true,\"HasConfiguredEasyPassword\":false,\"EnableAutoLogin\":false}":"{\"Id\":\""+serverId+"\",\"ServerName\":\"测试媒体库\",\"Version\":\""+version+"\",\"ProductName\":\"Jellyfin\",\"StartupWizardCompleted\":true,\"CustomPrefs\":{}}";byte[] bytes=body.getBytes(java.nio.charset.StandardCharsets.UTF_8);OutputStream out=client.getOutputStream();out.write(("HTTP/1.1 "+code+" "+(code==200?"OK":"Unavailable")+"\r\nContent-Type: application/json; charset=utf-8\r\nContent-Length: "+bytes.length+"\r\nConnection: close\r\n\r\n").getBytes(java.nio.charset.StandardCharsets.US_ASCII));out.write(bytes);out.flush();}catch(Exception ignored){}}
        public void close(){try{socket.close();}catch(Exception ignored){}worker.shutdownNow();}
    }
    private Object model(String className,int parameters,Object...values)throws Exception{Class<?> type=Class.forName(className,true,getTargetContext().getClassLoader());for(Constructor<?> constructor:type.getConstructors())if(constructor.getParameterTypes().length==parameters)return constructor.newInstance(values);throw new NoSuchMethodException(className);}
    private void refreshStyleReview()throws Exception{
        live=startActivitySync(new Intent().setClassName(getTargetContext().getPackageName(),"org.jellyfin.androidtv.bestv.LiveActivity").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));check("home loads",waitTag(live,"group",25));main(()->{call(live,"navigate","settings");return null;});focusTag(live,"settings-2");key(KeyEvent.KEYCODE_DPAD_RIGHT);focusTag(live,"media-retry");View retry=tagged(live,"media-retry");check("refresh focus has no system button fill",main(()->!retry.getDefaultFocusHighlightEnabled()));check("refresh background remains transparent",main(()->retry.getBackground() instanceof android.graphics.drawable.ColorDrawable&&Color.alpha(((android.graphics.drawable.ColorDrawable)retry.getBackground()).getColor())==0));shot("refresh-bare");
    }
    private void configuredReview()throws Exception{
        live=startActivitySync(new Intent().setClassName(getTargetContext().getPackageName(),"org.jellyfin.androidtv.bestv.LiveActivity").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));check("home usable before library connection completes",waitTag(live,"group",25));
        Object service=field(live,"libraryConnections");for(int i=0;i<340&&main(()->(Boolean)call(service,"getStartupBusy"));i++)SystemClock.sleep(250);
        Object entry=main(()->call(service,"current"));boolean active=entry!=null&&main(()->(Boolean)call(service,"isActive",entry));check("real configured startup connects without opening library",active);check("real startup uses one to three attempts",main(()->{int n=(Integer)call(service,"getStartupAttempts");return n>=1&&n<=3;}));shot("configured-home");if(!active)return;
        String key=(String)call(entry,"getKey");main(()->{call(live,"navigate","settings");return null;});focusTag(live,"settings-2");key(KeyEvent.KEYCODE_DPAD_RIGHT);check("real connected name/state is visible",main(()->((TextView)field(live,"libraryStatusView")).getText().toString().contains("已连接")));
        focusTag(live,"media-retry");int generation=main(()->(Integer)field(live,"libraryGeneration"));main(()->{View retry=root(live).findViewWithTag("media-retry");retry.performClick();retry.performClick();return null;});check("rapid retry does not duplicate an in-flight request",main(()->(Integer)field(live,"libraryGeneration"))==generation+1);
        for(int i=0;i<120&&main(()->(Boolean)field(live,"libraryBusy"));i++)SystemClock.sleep(200);check("real retry succeeds and preserves selection",main(()->call(service,"currentKey").equals(key)&&Boolean.TRUE.equals(field(live,"libraryOnline"))));check("real retry focus stays on bare icon",tagOfFocus(live).equals("media-retry"));shot("configured-connected");
        focusTag(live,"media-add");ActivityMonitor monitor=addMonitor("org.jellyfin.androidtv.bestv.JellyLibraryActivity",null,false);key(KeyEvent.KEYCODE_DPAD_CENTER);library=waitForMonitorWithTimeout(monitor,5000);removeMonitor(monitor);check("add library opens TV form",library!=null&&waitPage("connection",25));if(library!=null){List<EditText> fields=main(()->inputs(root(library)));check("new library form never receives existing password",fields.size()==3&&main(()->fields.get(2).length()==0));key(KeyEvent.KEYCODE_BACK);SystemClock.sleep(500);check("cancel add restores original settings row",tagOfFocus(live).equals("media-add"));check("cancel add keeps original account",main(()->call(service,"currentKey").equals(key)));library=null;}
        ActivityMonitor browse=addMonitor("org.jellyfin.androidtv.bestv.JellyLibraryActivity",null,false);main(()->{call(live,"openLibrary",false);return null;});library=waitForMonitorWithTimeout(browse,5000);removeMonitor(browse);check("real media home loads with retained session",library!=null&&waitPage("home",30));if(library!=null){shot("configured-media-home");main(()->{library.finish();return null;});library=null;}
    }
    private Object fixtureEntry(Object store,ConnectionFixture fixture)throws Exception{
        Object storedUser=model("org.jellyfin.androidtv.auth.model.AuthenticationStoreUser",4,"测试账号",System.currentTimeMillis(),null,"probe-only-token");Map<UUID,Object> users=new HashMap<>();users.put(fixture.userId,storedUser);
        Object server=model("org.jellyfin.androidtv.auth.model.AuthenticationStoreServer",9,"测试媒体库",fixture.address(),ConnectionFixture.version,null,false,true,System.currentTimeMillis(),System.currentTimeMillis(),users);
        main(()->{call(store,"putServer",fixture.serverId,server);return null;});
        return model("org.jellyfin.androidtv.bestv.LibraryConnections$Entry",5,fixture.serverId.toString(),fixture.userId.toString(),"测试媒体库","测试账号",fixture.address());
    }
    private void connectionReview()throws Exception{
        live=startActivitySync(new Intent().setClassName(getTargetContext().getPackageName(),"org.jellyfin.androidtv.bestv.LiveActivity").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));check("launcher home remains usable during connection",waitTag(live,"group",25));
        Object service=field(live,"libraryConnections");main(()->{call(service,"stopStartup");return null;});
        Object sessions=call(service,"getSessions"),store=call(service,"getStore"),api=call(service,"getApi");Object currentFlow=invokeAny(sessions,"getCurrentSession"),savedSession=invokeAny(currentFlow,"getValue");
        android.content.SharedPreferences auth=getTargetContext().getSharedPreferences("authentication",0);String savedServer=auth.getString("last_server_id",""),savedUser=auth.getString("last_user_id","");
        UUID userId=UUID.randomUUID();ClassLoader loader=getTargetContext().getClassLoader();Object companion=Class.forName("org.jellyfin.androidtv.auth.repository.ServerRepository",true,loader).getField("Companion").get(null);ConnectionFixture.version=String.valueOf(call(companion,"getMinimumServerVersion"));
        try(ConnectionFixture primary=new ConnectionFixture(userId);ConnectionFixture fixture=new ConnectionFixture(userId)){
            primary.status=200;Object original=fixtureEntry(store,primary);Object entry=fixtureEntry(store,fixture);
            try{
                Object bound=suspendCall(sessions,"switchCurrentSession",primary.serverId,userId);check("controlled primary connection activates through production SDK",Boolean.TRUE.equals(bound));if(!Boolean.TRUE.equals(bound)){
                    JSONObject diagnostic=new JSONObject().put("fixtureVersion",ConnectionFixture.version).put("userReads",primary.userReads.get());
                    Object known=suspendCall(field(sessions,"serverRepository"),"getServer",primary.serverId,false);diagnostic.put("versionSupported",known!=null&&Boolean.TRUE.equals(call(known,"getVersionSupported")));
                    try{suspendCall(service,"verifyAndActivate",original);}catch(Exception error){JSONArray causes=new JSONArray();for(Throwable cause=error;cause!=null;cause=cause.getCause())causes.put(new JSONObject().put("type",cause.getClass().getSimpleName()).put("message",String.valueOf(cause.getMessage()).replaceAll("https?://[^ ]+","[fixture]")));diagnostic.put("cause",causes);}layers.put(diagnostic);return;
                }
                Object originalSession=invokeAny(currentFlow,"getValue");String originalKey=(String)call(original,"getKey");Object originalUrl=invokeAny(api,"getBaseUrl");String targetKey="library-"+call(entry,"getKey");
                main(()->{call(live,"navigate","settings");return null;});focusTag(live,"settings-2");key(KeyEvent.KEYCODE_DPAD_RIGHT);for(int i=0;i<100&&main(()->(Boolean)field(live,"libraryBusy"));i++)SystemClock.sleep(200);
                check("library header shows actual name and verified state",main(()->((TextView)field(live,"libraryNameView")).getText().toString().equals(call(original,"getName"))&&((TextView)field(live,"libraryStatusView")).getText().toString().contains("已连接")));
                check("legacy open-library entry removed",main(()->root(live).findViewWithTag("media-open")==null));shot("library-connected");
                focusTag(live,"media-retry");key(KeyEvent.KEYCODE_DPAD_CENTER);key(KeyEvent.KEYCODE_DPAD_CENTER);for(int i=0;i<100&&main(()->(Boolean)field(live,"libraryBusy"));i++)SystemClock.sleep(200);check("retry keeps its focus and account",tagOfFocus(live).equals("media-retry")&&main(()->call(service,"currentKey").equals(originalKey)));
                focusTag(live,"media-switch");key(KeyEvent.KEYCODE_DPAD_CENTER);check("switch opens at current account",main(()->field(live,"settingsSubmenu").equals("media-switch")));focusTag(live,targetKey);key(KeyEvent.KEYCODE_DPAD_CENTER);for(int i=0;i<100&&main(()->(Boolean)field(live,"libraryBusy"));i++)SystemClock.sleep(200);
                check("failed switch preserves current origin and account",main(()->call(service,"currentKey").equals(originalKey))&&Objects.equals(originalUrl,invokeAny(api,"getBaseUrl")));check("failed switch retains target focus",tagOfFocus(live).equals(targetKey));shot("library-switch-failure");
                fixture.status=200;fixture.delayMs=3500;int reads=fixture.userReads.get();key(KeyEvent.KEYCODE_DPAD_CENTER);for(int i=0;i<80&&fixture.userReads.get()==reads;i++)SystemClock.sleep(50);key(KeyEvent.KEYCODE_BACK);SystemClock.sleep(3800);
                check("cancelled verification cannot replace original session",main(()->call(service,"currentKey").equals(originalKey))&&Objects.equals(originalUrl,invokeAny(api,"getBaseUrl")));check("cancel switch returns to original row",tagOfFocus(live).equals("media-switch"));
                fixture.delayMs=0;key(KeyEvent.KEYCODE_DPAD_CENTER);focusTag(live,targetKey);key(KeyEvent.KEYCODE_DPAD_CENTER);for(int i=0;i<150&&main(()->(Boolean)field(live,"libraryBusy"));i++)SystemClock.sleep(200);
                check("same user ID on another server really switches server",main(()->call(service,"currentKey").equals(call(entry,"getKey"))));check("successful switch returns to switch row",tagOfFocus(live).equals("media-switch"));shot("library-switched");
                key(KeyEvent.KEYCODE_DPAD_CENTER);focusTag(live,"library-"+originalKey);key(KeyEvent.KEYCODE_DPAD_CENTER);for(int i=0;i<150&&main(()->(Boolean)field(live,"libraryBusy"));i++)SystemClock.sleep(200);check("original library can be selected again",main(()->call(service,"currentKey").equals(originalKey)));
                // Controlled cold-start selection: production retry loop, actual failing loopback HTTP.
                Object flow=field(sessions,"_currentSession");Object synthetic=model("org.jellyfin.androidtv.auth.repository.Session",3,userId,fixture.serverId,"probe-only-token");Object retryService=model("org.jellyfin.androidtv.bestv.LibraryConnections",0);CountDownLatch completed=new CountDownLatch(1);java.util.concurrent.atomic.AtomicInteger callbacks=new java.util.concurrent.atomic.AtomicInteger();Class<?> observerType=Class.forName("org.jellyfin.androidtv.bestv.LibraryConnections$StartupObserver",true,loader);
                Object observer=Proxy.newProxyInstance(loader,new Class<?>[]{observerType},(proxy,method,args)->{if(method.getName().equals("failure")){callbacks.incrementAndGet();completed.countDown();}if(method.getName().equals("success"))completed.countDown();return null;});
                fixture.status=503;fixture.userReads.set(0);Object restored=invokeAny(currentFlow,"getValue");
                try{main(()->{call(flow,"setValue",synthetic);call(retryService,"startConfigured",getTargetContext(),observer);return null;});check("startup failing connection completes",completed.await(25,TimeUnit.SECONDS));check("startup stops at three real attempts",main(()->(Integer)call(retryService,"getStartupAttempts"))==3&&fixture.userReads.get()==3&&callbacks.get()==1);SystemClock.sleep(1500);check("startup does not schedule a fourth attempt",fixture.userReads.get()==3);check("failed startup leaves active API binding untouched",Objects.equals(originalUrl,invokeAny(api,"getBaseUrl")));}
                finally{main(()->{call(retryService,"close");call(flow,"setValue",restored);return null;});}
                shot("library-restored");
            }finally{
                main(()->{call(live,"cancelLibraryRequest");call(service,"stopStartup");return null;});
                if(savedSession!=null){try{suspendCall(sessions,"switchCurrentSession",invokeAny(savedSession,"getServerId"),invokeAny(savedSession,"getUserId"));}catch(Exception ignored){suspendCall(sessions,"setCurrentSession",(Object)null);}}
                else suspendCall(sessions,"setCurrentSession",(Object)null);
                main(()->{auth.edit().putString("last_server_id",savedServer).putString("last_user_id",savedUser).apply();call(store,"removeServer",fixture.serverId);call(store,"removeServer",primary.serverId);return null;});
            }
        }
    }
    private void tap(View view)throws Exception{
        int[] bounds=main(()->{int[] xy=new int[2];view.getLocationOnScreen(xy);return new int[]{xy[0],xy[1],view.getWidth(),view.getHeight()};});
        long now=SystemClock.uptimeMillis();float x=bounds[0]+bounds[2]/2f,y=bounds[1]+bounds[3]/2f;
        sendPointerSync(MotionEvent.obtain(now,now,MotionEvent.ACTION_DOWN,x,y,0));sendPointerSync(MotionEvent.obtain(now,now+100,MotionEvent.ACTION_UP,x,y,0));waitForIdleSync();
    }
    private void settingsBackReview()throws Exception{
        live=startActivitySync(new Intent().setClassName(getTargetContext().getPackageName(),"org.jellyfin.androidtv.bestv.LiveActivity").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        check("home loads",waitTag(live,"group",25));
        for(int category:new int[]{0,1,4}){
            main(()->{call(live,"navigate","settings");return null;});tap(tagged(live,"settings-"+category));
            check("touch category enters content "+category,inSettings()&&main(()->root(live).isInTouchMode()));
            if(category==1){tap(tagged(live,"font"));check("touch opens font submenu",main(()->"large_text".equals(field(live,"settingsSubmenu"))));key(KeyEvent.KEYCODE_BACK);check("touch Back restores original font row",tagOfFocus(live).equals("font"));}
            key(KeyEvent.KEYCODE_BACK);check("system Back after touch reaches category "+category,tagOfFocus(live).equals("settings-"+category));
            if(category==4)shot("about-no-panel-fill");
            key(KeyEvent.KEYCODE_BACK);check("second system Back after touch leaves settings "+category,main(()->"home".equals(field(live,"route"))));
            if(main(()->"settings".equals(field(live,"route"))))main(()->{call(live,"home");return null;});
        }
        main(()->{call(live,"navigate","settings");return null;});focusTag(live,"settings-1");key(KeyEvent.KEYCODE_DPAD_RIGHT);key(KeyEvent.KEYCODE_BACK);
        check("remote Back still reaches category",tagOfFocus(live).equals("settings-1"));key(KeyEvent.KEYCODE_BACK);check("remote Back still leaves settings",main(()->"home".equals(field(live,"route"))));
        final String[] firstPage={"missing"};
        Application.ActivityLifecycleCallbacks observer=new Application.ActivityLifecycleCallbacks(){
            public void onActivityPostCreated(Activity a,Bundle state){if(a.getClass().getName().endsWith(".JellyLibraryActivity"))firstPage[0]=(String)field(a,"pageName");}
            public void onActivityCreated(Activity a,Bundle state){} public void onActivityStarted(Activity a){} public void onActivityResumed(Activity a){}
            public void onActivityPaused(Activity a){} public void onActivityStopped(Activity a){} public void onActivitySaveInstanceState(Activity a,Bundle state){} public void onActivityDestroyed(Activity a){}
        };
        main(()->{call(live,"navigate","settings");return null;});focusTag(live,"settings-2");key(KeyEvent.KEYCODE_DPAD_RIGHT);
        Object service=field(live,"libraryConnections");Object original=main(()->call(service,"currentKey"));focusTag(live,"media-add");
        ActivityMonitor monitor=addMonitor("org.jellyfin.androidtv.bestv.JellyLibraryActivity",null,false);
        main(()->{live.getApplication().registerActivityLifecycleCallbacks(observer);return null;});
        try{key(KeyEvent.KEYCODE_DPAD_CENTER);library=waitForMonitorWithTimeout(monitor,5000);}finally{removeMonitor(monitor);main(()->{live.getApplication().unregisterActivityLifecycleCallbacks(observer);return null;});}
        check("add opens form immediately without restoration screen","connection".equals(firstPage[0]));
        check("add form is usable",library!=null&&waitPage("connection",25));
        if(library!=null){List<EditText> fields=main(()->inputs(root(library)));check("all new library fields are empty",fields.size()==3&&main(()->fields.stream().allMatch(f->f.length()==0)));shot("add-library-direct-form");
            java.util.concurrent.atomic.AtomicInteger backEvents=new java.util.concurrent.atomic.AtomicInteger();
            Window.Callback originalCallback=main(()->library.getWindow().getCallback());
            main(()->{library.getWindow().setCallback((Window.Callback)Proxy.newProxyInstance(getTargetContext().getClassLoader(),new Class<?>[]{Window.Callback.class},(proxy,method,args)->{if(method.getName().equals("dispatchKeyEvent")&&((KeyEvent)args[0]).getKeyCode()==KeyEvent.KEYCODE_BACK)backEvents.incrementAndGet();try{return method.invoke(originalCallback,args);}catch(InvocationTargetException e){throw e.getCause();}}));return null;});
            key(KeyEvent.KEYCODE_BACK);
            // Android may use the first Back to dismiss transient system navigation bars.
            layers.put(new JSONObject().put("firstBackWindowEvents",backEvents.get()));
            if(backEvents.get()==0)key(KeyEvent.KEYCODE_BACK);
            for(int i=0;i<30&&!(tagOfFocus(live).equals("media-add")&&main(()->live.hasWindowFocus()));i++)SystemClock.sleep(100);
            check("add Back reaches app and closes form",backEvents.get()>0&&main(()->library.isFinishing()||library.isDestroyed()));
            library=null;check("cancel add keeps current library",main(()->Objects.equals(original,call(service,"currentKey"))));layers.put(new JSONObject().put("cancelAddFocus",tagOfFocus(live)).put("liveWindowFocus",main(()->live.hasWindowFocus())));check("cancel add returns to add row",tagOfFocus(live).equals("media-add")&&main(()->live.hasWindowFocus()));}
    }
    private void opacityFocusReview()throws Exception{
        live=startActivitySync(new Intent().setClassName(getTargetContext().getPackageName(),"org.jellyfin.androidtv.bestv.LiveActivity").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));check("home",waitTag(live,"group",25));main(()->{call(live,"navigate","settings");return null;});focusTag(live,"settings-1");key(KeyEvent.KEYCODE_DPAD_RIGHT);focusTag(live,"transparency");key(KeyEvent.KEYCODE_DPAD_CENTER);
        SeekBar bar=main(()->(SeekBar)root(live).findViewWithTag("selected-option"));int[] xy=main(()->{int[] a=new int[2];bar.getLocationOnScreen(a);return a;});int w=main(()->bar.getWidth()),h=main(()->bar.getHeight());long now=SystemClock.uptimeMillis();sendPointerSync(MotionEvent.obtain(now,now,MotionEvent.ACTION_DOWN,xy[0]+w*.5f,xy[1]+h/2f,0));sendPointerSync(MotionEvent.obtain(now,now+100,MotionEvent.ACTION_UP,xy[0]+w*.5f,xy[1]+h/2f,0));waitForIdleSync();
        layers.put(new JSONObject().put("stage","before").put("focus",tagOfFocus(live)).put("memory",main(()->((Map<?,?>)field(live,"settingsFocus")).get("显示与操作"))).put("submenu",main(()->field(live,"settingsSubmenu"))));
        key(KeyEvent.KEYCODE_BACK);layers.put(new JSONObject().put("stage","after").put("focus",tagOfFocus(live)).put("memory",main(()->((Map<?,?>)field(live,"settingsFocus")).get("显示与操作"))).put("submenu",main(()->field(live,"settingsSubmenu"))).put("route",main(()->field(live,"route"))));check("touch Back restores transparency",tagOfFocus(live).equals("transparency"));shot("opacity-back");
    }
    private void drawerOpacityReview()throws Exception{
        int original=main(()->live.getSharedPreferences("bestv_live",0).getInt("drawer_transparency",35));
        main(()->{call(live,"navigate","full");FrameLayout p=(FrameLayout)field(live,"page");View white=new View(live);white.setBackgroundColor(Color.WHITE);p.addView(white,new FrameLayout.LayoutParams(-1,-1));return null;});
        int prior=-1;
        try{
            for(int amount:new int[]{0,35,60,80}){
                final int value=amount;
                main(()->{call(live,"hideDrawer");live.getSharedPreferences("bestv_live",0).edit().putInt("drawer_transparency",value).apply();call(live,"showDrawer");((Handler)field(live,"handler")).removeCallbacks((Runnable)field(live,"hideIdleDrawer"));return null;});
                waitForIdleSync();SystemClock.sleep(180);Bitmap shot=getUiAutomation().takeScreenshot();if(shot==null)throw new IllegalStateException("No opacity screenshot");
                int[] location=main(()->{int[] point=new int[2];((View)field(live,"page")).getLocationOnScreen(point);return point;});
                float scale=main(()->{View p=(View)field(live,"page");return p.getWidth()/960f;});
                int color=shot.getPixel(location[0]+Math.round(694*scale),location[1]+Math.round(400*scale));
                int textArea=shot.getPixel(location[0]+Math.round(900*scale),location[1]+Math.round(505*scale));
                int alpha=Math.round(255*(100-amount)/100f),expected=Math.round(17*alpha/255f+255*(1-alpha/255f));
                check("actual drawer fill follows setting "+amount,Math.abs(Color.red(color)-expected)<=6);
                check("text-area gaps retain selected transparency "+amount,Math.abs(Color.red(textArea)-expected)<=8);
                check("actual drawer brightness changes "+amount,Color.red(color)>prior);prior=Color.red(color);
                if(amount==35||amount==80)try(FileOutputStream out=new FileOutputStream(new File(output,"drawer-opacity-"+amount+".png"))){shot.compress(Bitmap.CompressFormat.PNG,100,out);}shot.recycle();
            }
        }finally{main(()->{live.getSharedPreferences("bestv_live",0).edit().putInt("drawer_transparency",original).apply();call(live,"hideDrawer");call(live,"navigate","settings");return null;});}
    }
    private void settingsReview()throws Exception{
        live=startActivitySync(new Intent().setClassName(getTargetContext().getPackageName(),"org.jellyfin.androidtv.bestv.LiveActivity").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        check("home starts",waitTag(live,"group",25));focusTag(live,"tile-3");key(KeyEvent.KEYCODE_DPAD_CENTER);key(KeyEvent.KEYCODE_DPAD_RIGHT);
        check("settings enters first row",tagOfFocus(live).equals("auto_play"));
        boolean autoplay=main(()->live.getSharedPreferences("bestv_live",0).getBoolean("auto_play",true));
        key(KeyEvent.KEYCODE_DPAD_CENTER);check("single confirm toggles and retains focus",main(()->live.getSharedPreferences("bestv_live",0).getBoolean("auto_play",true)!=autoplay)&&tagOfFocus(live).equals("auto_play"));key(KeyEvent.KEYCODE_DPAD_CENTER);
        for(String expected:new String[]{"auto_next"}){key(KeyEvent.KEYCODE_DPAD_DOWN);check("settings vertical path "+expected,tagOfFocus(live).equals(expected));}
        shot("settings-playback");key(KeyEvent.KEYCODE_DPAD_DOWN);check("bottom stays in column",tagOfFocus(live).equals("auto_next"));key(KeyEvent.KEYCODE_BACK);check("Back row to category",tagOfFocus(live).equals("settings-0"));key(KeyEvent.KEYCODE_DPAD_RIGHT);check("row focus remembered",tagOfFocus(live).equals("auto_next"));
        check("player-only settings removed",main(()->root(live).findViewWithTag("aspect")==null&&root(live).findViewWithTag("audio")==null&&root(live).findViewWithTag("subtitle")==null));
        key(KeyEvent.KEYCODE_BACK);key(KeyEvent.KEYCODE_DPAD_DOWN);key(KeyEvent.KEYCODE_DPAD_RIGHT);check("display first row",tagOfFocus(live).equals("font"));key(KeyEvent.KEYCODE_DPAD_CENTER);check("font chooser opens selected value",tagOfFocus(live).equals("selected-option"));key(KeyEvent.KEYCODE_BACK);check("font chooser Back restores row",tagOfFocus(live).equals("font"));key(KeyEvent.KEYCODE_DPAD_DOWN);key(KeyEvent.KEYCODE_DPAD_CENTER);
        int opacity=main(()->live.getSharedPreferences("bestv_live",0).getInt("drawer_transparency",35));key(KeyEvent.KEYCODE_DPAD_RIGHT);check("opacity changes by five",main(()->live.getSharedPreferences("bestv_live",0).getInt("drawer_transparency",35))==Math.min(80,opacity+5));key(KeyEvent.KEYCODE_DPAD_LEFT);
        check("opacity is a touch-capable slider",main(()->root(live).findViewWithTag("selected-option") instanceof SeekBar));
        main(()->{root(live).findViewWithTag("transparency-plus").performClick();return null;});check("plus click adjusts transparency",main(()->live.getSharedPreferences("bestv_live",0).getInt("drawer_transparency",35))==Math.min(80,opacity+5));
        main(()->{root(live).findViewWithTag("transparency-minus").performClick();return null;});check("minus click adjusts transparency",main(()->live.getSharedPreferences("bestv_live",0).getInt("drawer_transparency",35))==opacity);
        SeekBar slider=main(()->(SeekBar)root(live).findViewWithTag("selected-option"));int[] position=main(()->{int[] xy=new int[2];slider.getLocationOnScreen(xy);return xy;});int sliderWidth=main(()->slider.getWidth()),sliderHeight=main(()->slider.getHeight());long touch=SystemClock.uptimeMillis();float y=position[1]+sliderHeight/2f;
        sendPointerSync(android.view.MotionEvent.obtain(touch,touch,android.view.MotionEvent.ACTION_DOWN,position[0]+sliderWidth*.25f,y,0));sendPointerSync(android.view.MotionEvent.obtain(touch,touch+100,android.view.MotionEvent.ACTION_MOVE,position[0]+sliderWidth*.8f,y,0));sendPointerSync(android.view.MotionEvent.obtain(touch,touch+180,android.view.MotionEvent.ACTION_UP,position[0]+sliderWidth*.8f,y,0));waitForIdleSync();check("drag commits quantized transparency",main(()->slider.getProgress()*5>40));
        main(()->{slider.setProgress(opacity/5);return null;});shot("settings-transparency");key(KeyEvent.KEYCODE_BACK);check("opacity Back returns to row",tagOfFocus(live).equals("transparency"));
        for(int i=2;i<5;i++){focusTag(live,"settings-"+i);key(KeyEvent.KEYCODE_DPAD_RIGHT);check("category content reachable "+i,inSettings());key(KeyEvent.KEYCODE_BACK);check("category Back restored "+i,tagOfFocus(live).equals("settings-"+i));}shot("settings-about");key(KeyEvent.KEYCODE_BACK);drawerOpacityReview();if("true".equals(arguments.getString("quick")))return;
        main(()->{call(live,"navigate","sources");return null;});focusTag(live,"source-box-config");ActivityMonitor monitor=addMonitor("org.jellyfin.androidtv.bestv.BoxConfigActivity",null,false);key(KeyEvent.KEYCODE_DPAD_CENTER);Activity box=waitForMonitorWithTimeout(monitor,5000);reviewBox=box;removeMonitor(monitor);check("source opens shared box workspace",box!=null);if(box==null)return;
        String saved=main(()->box.getSharedPreferences("bestv_live",0).getString("config","{}"));shot("source-box-menu");key(KeyEvent.KEYCODE_DPAD_CENTER);check("device import page opens",main(()->field(box,"route").equals("import")));List<EditText> device=main(()->inputs(root(box)));check("IP and port are navigable",device.size()==2);key(KeyEvent.KEYCODE_DPAD_DOWN);check("D-pad browses without IME editing",main(()->box.getCurrentFocus()==device.get(1)&&!((Boolean)call(device.get(1),"isEditing"))));shot("source-box-import");key(KeyEvent.KEYCODE_BACK);
        key(KeyEvent.KEYCODE_DPAD_DOWN);key(KeyEvent.KEYCODE_DPAD_DOWN);key(KeyEvent.KEYCODE_DPAD_CENTER);check("manual common fields opens",main(()->field(box,"route").equals("edit")));shot("box-common");key(KeyEvent.KEYCODE_DPAD_CENTER);check("confirm field opens single editor",main(()->field(box,"route").equals("field")));SystemClock.sleep(700);shot("box-ime");main(()->{((EditText)field(box,"field")).setText("UI_DRAFT_ONLY");return null;});key(KeyEvent.KEYCODE_BACK);key(KeyEvent.KEYCODE_BACK);check("field retains draft only",main(()->((JSONObject)field(box,"draft")).optString("user_id").equals("UI_DRAFT_ONLY")));key(KeyEvent.KEYCODE_BACK);key(KeyEvent.KEYCODE_BACK);check("dirty exit asks once",main(()->field(box,"confirmation")!=null));check("discard prompt defaults to continue editing",main(()->{AlertDialog d=(AlertDialog)field(box,"confirmation");return d!=null&&d.getCurrentFocus() instanceof Button&&((Button)d.getCurrentFocus()).getText().toString().equals("继续编辑");}));shot("box-discard");key(KeyEvent.KEYCODE_BACK);check("Back keeps editing",!box.isFinishing());key(KeyEvent.KEYCODE_BACK);key(KeyEvent.KEYCODE_DPAD_RIGHT);check("Right selects discard action",main(()->{AlertDialog d=(AlertDialog)field(box,"confirmation");return d!=null&&d.getCurrentFocus() instanceof Button&&((Button)d.getCurrentFocus()).getText().toString().equals("放弃修改");}));key(KeyEvent.KEYCODE_DPAD_CENTER);SystemClock.sleep(700);check("discard closes actual workspace",main(()->box.isFinishing()||box.isDestroyed()));check("discard preserves saved config",main(()->box.getSharedPreferences("bestv_live",0).getString("config","{}").equals(saved)));check("return restores actual source button",tagOfFocus(live).equals("source-box-config")&&main(()->live.hasWindowFocus()));reviewBox=null;
        main(()->{call(live,"navigate","settings");return null;});focusTag(live,"settings-3");key(KeyEvent.KEYCODE_DPAD_RIGHT);focusTag(live,"box-file");ActivityMonitor fileMonitor=addMonitor("org.jellyfin.androidtv.bestv.BoxConfigActivity",null,false);key(KeyEvent.KEYCODE_DPAD_CENTER);Activity fileBox=waitForMonitorWithTimeout(fileMonitor,5000);removeMonitor(fileMonitor);boolean picker=live.getPackageManager().resolveActivity(new Intent(Intent.ACTION_OPEN_DOCUMENT).setType("*/*").addCategory(Intent.CATEGORY_OPENABLE),0)!=null;SystemClock.sleep(1200);layers.put(new JSONObject().put("filePickerAvailable",picker).put("fileActivityCreated",fileBox!=null).put("fileAction",fileBox==null?"":fileBox.getIntent().getStringExtra("action")).put("fileRoute",fileBox==null?"":main(()->field(fileBox,"route"))).put("fileDirect",fileBox!=null&&main(()->(Boolean)field(fileBox,"direct"))).put("fileDirty",fileBox!=null&&main(()->(Boolean)call(fileBox,"dirty"))));reviewBox=fileBox;shot("settings-file-import");key(KeyEvent.KEYCODE_BACK);SystemClock.sleep(1200);check("settings file cancel returns directly to original entry",fileBox!=null&&main(()->fileBox.isFinishing()||fileBox.isDestroyed()));reviewBox=null;check("file cancel restores original settings row",tagOfFocus(live).equals("box-file")&&main(()->live.hasWindowFocus()));key(KeyEvent.KEYCODE_BACK);key(KeyEvent.KEYCODE_BACK);
        library=startActivitySync(new Intent().setClassName(getTargetContext().getPackageName(),"org.jellyfin.androidtv.bestv.JellyLibraryActivity").putExtra("connect",true).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));check("connection form loads",waitPage("connection",25));List<EditText> fields=main(()->inputs(root(library)));check("connection has three fields",fields.size()==3);if(fields.size()!=3)return;
        main(()->fields.get(0).requestFocus());key(KeyEvent.KEYCODE_DPAD_DOWN);check("down reaches username without editing",main(()->library.getCurrentFocus()==fields.get(1)&&!(Boolean)call(fields.get(1),"isEditing")));key(KeyEvent.KEYCODE_DPAD_DOWN);check("down reaches password",main(()->library.getCurrentFocus()==fields.get(2)));shot("connection-form");key(KeyEvent.KEYCODE_DPAD_CENTER);SystemClock.sleep(700);shot("connection-ime");key(KeyEvent.KEYCODE_BACK);check("Back closes IME before page",main(()->field(library,"pageName").equals("connection")));
        String old=main(()->fields.get(0).getText().toString());main(()->{fields.get(2).setText("DUMMY_TEST");fields.get(0).setText("invalid://address");return null;});check("changing address clears foreign password",main(()->fields.get(2).length()==0));List<Button> submit=main(()->buttons(root(library),"检测并连接"));main(()->submit.get(0).requestFocus());key(KeyEvent.KEYCODE_DPAD_CENTER);check("invalid server returns focus to address",main(()->library.getCurrentFocus()==fields.get(0)));shot("connection-validation");main(()->{fields.get(0).setText(old);return null;});key(KeyEvent.KEYCODE_BACK);main(()->{library.finish();return null;});library=null;
    }
    private void mediaReview()throws Exception{
        library=startActivitySync(new Intent().setClassName(getTargetContext().getPackageName(),"org.jellyfin.androidtv.bestv.JellyLibraryActivity").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));check("media home ready",waitPage("home",30));Object repo=field(library,"repo");List<?> catalog=(List<?>)suspendCall(repo,"catalog",null,false);Object chosen=null;
        // Bound to the known fixture library; select a real source supported by the emulator, retaining its true profile.
        if(catalog.size()>20)throw new IllegalStateException("Fixture library unexpectedly large");
        for(Object item:catalog){Object detail=suspendCall(repo,"detail",invokeAny(item,"getId"));JSONObject json=modelJson(detail);JSONArray sources=json.optJSONArray("MediaSources");boolean supported=false;if(sources!=null)for(int i=0;i<sources.length();i++){JSONArray tracks=sources.getJSONObject(i).optJSONArray("MediaStreams");if(tracks!=null)for(int j=0;j<tracks.length();j++){JSONObject track=tracks.getJSONObject(j);String codec=track.optString("Codec");if("Video".equalsIgnoreCase(track.optString("Type"))&&("h264".equalsIgnoreCase(codec)||("hevc".equalsIgnoreCase(codec)&&track.optInt("BitDepth",8)<=8)))supported=true;}}if(supported){chosen=detail;break;}}
        check("supported real fixture found",chosen!=null);if(chosen==null)return;Object sample=chosen;main(()->{long saved=(Long)call(library,"resumeMs",sample);call(library,"play",sample,saved,false);return null;});check("player route opens",waitPage("player",5));for(int i=0;i<160&&!main(()->readyPlayer()||Boolean.TRUE.equals(field(library,"playbackFailure")));i++)SystemClock.sleep(250);check("real stream becomes ready",main(()->readyPlayer()&&!Boolean.TRUE.equals(field(library,"playbackFailure"))));if(!main(()->readyPlayer()))return;
        if(main(()->root(library).isInTouchMode()))key(KeyEvent.KEYCODE_DPAD_UP);
        main(()->{call(library,"showControls",true);return null;});String[] names={"aspectAction","speedAction","audioAction","subtitleAction"};for(int i=0;i<names.length;i++){final int n=i;check("media action equal width "+names[i],main(()->((View)field(library,names[n])).getWidth()==((View)field(library,"aspectAction")).getWidth()));}
        check("ratio accepts D-pad focus",main(()->((View)field(library,"aspectAction")).requestFocus()));key(KeyEvent.KEYCODE_DPAD_RIGHT);check("ratio Right goes to speed",main(()->library.getCurrentFocus()==field(library,"speedAction")));key(KeyEvent.KEYCODE_DPAD_LEFT);key(KeyEvent.KEYCODE_DPAD_CENTER);check("media ratio popup opens",main(()->field(library,"dialog")!=null));shot("media-aspect");key(KeyEvent.KEYCODE_BACK);check("ratio popup Back restores button",main(()->library.getCurrentFocus()==field(library,"aspectAction")));shot("media-four-actions");
        android.content.SharedPreferences display=library.getSharedPreferences("bestv_live",0);String original=display.getString("media_video_aspect",null),liveBefore=display.getString("video_aspect",null);String effective=main(()->(String)call(library,"mediaAspect"));int previous=Arrays.asList("fit","zoom","stretch").indexOf(effective),next=(previous+1)%3;
        main(()->{call(library,"showControls",true);((View)field(library,"aspectAction")).requestFocus();return null;});key(KeyEvent.KEYCODE_DPAD_CENTER);if(previous==2){key(KeyEvent.KEYCODE_DPAD_UP);key(KeyEvent.KEYCODE_DPAD_UP);}else key(KeyEvent.KEYCODE_DPAD_DOWN);key(KeyEvent.KEYCODE_DPAD_CENTER);
        check("ratio selection applies without changing live preference",Objects.equals(display.getString("video_aspect",null),liveBefore)&&Objects.equals(display.getString("media_video_aspect",null),new String[]{"fit","zoom","stretch"}[next]));
        key(KeyEvent.KEYCODE_DPAD_CENTER);if(previous==2){key(KeyEvent.KEYCODE_DPAD_DOWN);key(KeyEvent.KEYCODE_DPAD_DOWN);}else key(KeyEvent.KEYCODE_DPAD_UP);key(KeyEvent.KEYCODE_DPAD_CENTER);
        check("ratio restores initial picture mode",main(()->call(library,"mediaAspect").equals(effective)));if(original==null)display.edit().remove("media_video_aspect").apply();else display.edit().putString("media_video_aspect",original).apply();
        main(()->{call(library,"hidePlayerControls");call(library,"waiting",true);return null;});SystemClock.sleep(400);check("waiting hint delayed",main(()->((View)field(library,"buffering")).getVisibility()!=View.VISIBLE));SystemClock.sleep(650);check("waiting hint visible once after threshold",main(()->((View)field(library,"buffering")).getVisibility()==View.VISIBLE));shot("media-controlled-wait");main(()->{call(library,"waiting",false);return null;});check("waiting hint hides on recovery",main(()->((View)field(library,"buffering")).getVisibility()!=View.VISIBLE));main(()->{call(library,"stopPlayer");return null;});
    }
    private boolean liveReady(){try{Object player=field(live,"player");return player!=null&&((Integer)invokeAny(player,"getPlaybackState"))==3;}catch(Exception e){return false;}}
    private boolean waitLive(String code){for(int i=0;i<160;i++){if(main(()->liveReady()&&field(live,"playingCode").equals(code)))return true;SystemClock.sleep(250);}return false;}
    private View firstFocusable(View view){if(view.isFocusable()&&view.isShown()&&view.isEnabled())return view;if(view instanceof ViewGroup)for(int i=0;i<((ViewGroup)view).getChildCount();i++){View found=firstFocusable(((ViewGroup)view).getChildAt(i));if(found!=null)return found;}return null;}
    private void liveReview()throws Exception{
        live=startActivitySync(new Intent().setClassName(getTargetContext().getPackageName(),"org.jellyfin.androidtv.bestv.LiveActivity").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));check("live home loads",waitTag(live,"group",25));focusTag(live,"group");
        List<JSONObject> channels=main(()->(List<JSONObject>)field(field(live,"source"),"channels"));JSONObject plain=null,hd=null;for(JSONObject item:channels){if(item.optString("ChannelName").equals("CCTV-1"))plain=item;if(item.optString("ChannelName").equals("CCTV-1高清"))hd=item;}
        check("real current-source CCTV1 versions exist",plain!=null&&hd!=null);if(plain==null||hd==null)return;JSONObject requested=plain;String plainCode=plain.optString("ChannelCode"),hdCode=hd.optString("ChannelCode");
        String favorites=main(()->field(field(live,"store"),"favorites").toString());main(()->{call(live,"play",requested,true);return null;});check("default prefers real HD version",waitLive(hdCode));if(!main(()->liveReady()))return;
        main(()->{call(live,"enterFull");if(!(Boolean)field(live,"controlsVisible"))call(live,"showControls");return null;});waitForIdleSync();
        check("quality button displays actual HD",main(()->((Button)field(live,"qualityAction")).getText().toString().equals("高清")));check("four live actions equal width",main(()->{int width=((View)field(live,"qualityAction")).getWidth();for(String name:new String[]{"aspectAction","audioAction","subtitleAction"})if(((View)field(live,name)).getWidth()!=width)return false;return width>0;}));
        main(()->((View)field(live,"qualityAction")).requestFocus());key(KeyEvent.KEYCODE_DPAD_RIGHT);check("live quality Right goes to aspect",main(()->live.getCurrentFocus()==field(live,"aspectAction")));key(KeyEvent.KEYCODE_DPAD_LEFT);key(KeyEvent.KEYCODE_DPAD_CENTER);
        AlertDialog menu=main(()->(AlertDialog)field(live,"dialog"));check("quality menu opens",menu!=null&&menu.isShowing());if(menu==null)return;check("quality menu has exactly actual variants",main(()->{List<Button> rows=buttons(menu.getWindow().getDecorView(),null);rows.removeIf(b->!b.isShown());return rows.size()==2;}));shot("live-quality");key(KeyEvent.KEYCODE_BACK);check("quality Back preserves actual HD and focus",main(()->field(live,"playingCode").equals(hdCode)&&live.getCurrentFocus()==field(live,"qualityAction")));
        key(KeyEvent.KEYCODE_DPAD_CENTER);key(KeyEvent.KEYCODE_DPAD_DOWN);key(KeyEvent.KEYCODE_DPAD_CENTER);check("real alternate version becomes ready",waitLive(plainCode));main(()->{if(!(Boolean)field(live,"controlsVisible"))call(live,"showControls");((View)field(live,"qualityAction")).requestFocus();return null;});check("unmarked actual version does not invent SD label",main(()->((Button)field(live,"qualityAction")).getText().toString().equals("画质")));shot("live-quality-unmarked");
        main(()->{if(!(Boolean)field(live,"controlsVisible"))call(live,"showControls");((View)field(live,"qualityAction")).requestFocus();return null;});key(KeyEvent.KEYCODE_DPAD_CENTER);key(KeyEvent.KEYCODE_DPAD_UP);key(KeyEvent.KEYCODE_DPAD_CENTER);check("manual HD switch becomes ready",waitLive(hdCode));check("source channel list stays intact",main(()->((List<?>)field(field(live,"source"),"channels")).size()==channels.size()));check("quality switch preserves favorites",main(()->field(field(live,"store"),"favorites").toString().equals(favorites)));
        check("controlled stopped-player recovery accepts last real stream",main(()->{try{Field rollback=live.getClass().getDeclaredField("qualityRollback");rollback.setAccessible(true);rollback.set(live,field(live,"activeLiveStream"));Object p=field(live,"player");invokeAny(p,"stop");return (Boolean)call(live,"restoreQuality");}catch(Exception e){throw new RuntimeException(e);}}));check("controlled recovery restores actual HD playback",waitLive(hdCode));main(()->{if(!(Boolean)field(live,"controlsVisible"))call(live,"showControls");((View)field(live,"qualityAction")).requestFocus();return null;});shot("live-quality-recovery");
    }
    private void searchReview()throws Exception{
        library=startActivitySync(new Intent().setClassName(getTargetContext().getPackageName(),"org.jellyfin.androidtv.bestv.JellyLibraryActivity").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));check("search library home loads",waitPage("home",30));if(main(()->root(library).isInTouchMode()))key(KeyEvent.KEYCODE_DPAD_UP);
        main(()->{call(library,"search","",true,null,null);return null;});waitForIdleSync();check("search keeps custom keyboard",main(()->buttons(root(library),"A").size()==1&&buttons(root(library),"空格").size()==1));check("search uses consistent language action",main(()->buttons(root(library),"中文 / 英文").size()==1&&buttons(root(library),"中文／拼音").isEmpty()));
        Button a=main(()->buttons(root(library),"A").get(0));main(()->a.requestFocus());key(KeyEvent.KEYCODE_DPAD_CENTER);Button space=main(()->buttons(root(library),"空格").get(0));main(()->space.requestFocus());key(KeyEvent.KEYCODE_DPAD_CENTER);check("space inserts actual space",main(()->((EditText)field(library,"searchInput")).getText().toString().equals("A ")));
        main(()->buttons(root(library),"删除").get(0).requestFocus());key(KeyEvent.KEYCODE_DPAD_CENTER);check("delete removes one character",main(()->((EditText)field(library,"searchInput")).getText().toString().equals("A")));main(()->buttons(root(library),"清除").get(0).requestFocus());key(KeyEvent.KEYCODE_DPAD_CENTER);check("clear empties query",main(()->((EditText)field(library,"searchInput")).length()==0));shot("search-keyboard");
        Button language=main(()->buttons(root(library),"中文 / 英文").get(0));main(()->language.requestFocus());key(KeyEvent.KEYCODE_DPAD_CENTER);SystemClock.sleep(700);check("language action enters system IME",main(()->(Boolean)call(field(library,"searchInput"),"isEditing")));shot("search-ime");key(KeyEvent.KEYCODE_BACK);SystemClock.sleep(500);check("IME Back preserves search and restores language key",main(()->field(library,"pageName").equals("search")&&library.getCurrentFocus()==language));
        Object repo=field(library,"repo");List<?> catalog=(List<?>)suspendCall(repo,"catalog",null,false);if(!catalog.isEmpty()){String title=(String)invokeAny(catalog.get(0),"getName");main(()->{((EditText)field(library,"searchInput")).setText(title);return null;});View result=null;for(int i=0;i<80&&result==null;i++){result=main(()->firstFocusable((View)field(library,"searchResults")));if(result==null)SystemClock.sleep(250);}check("actual media search yields a focusable result",result!=null);Button f=main(()->buttons(root(library),"F").get(0));main(()->f.requestFocus());key(KeyEvent.KEYCODE_DPAD_RIGHT);check("keyboard Right reaches actual result",main(()->descendant((ViewGroup)field(library,"searchResults"),library.getCurrentFocus())));key(KeyEvent.KEYCODE_DPAD_LEFT);check("result Left returns last key",main(()->library.getCurrentFocus()==f));shot("search-results");}
    }
    private void channelAudit()throws Exception{
        JSONObject cache;try(InputStream in=new FileInputStream(new File(getTargetContext().getFilesDir(),"live/live-sources.json"));ByteArrayOutputStream out=new ByteArrayOutputStream()){byte[] bytes=new byte[4096];int n;while((n=in.read(bytes))>=0)out.write(bytes,0,n);cache=new JSONObject(out.toString("UTF-8"));}
        JSONArray sources=cache.getJSONArray("sources"),report=new JSONArray();JSONArray cctvOne=new JSONArray();for(int si=0;si<sources.length();si++){JSONArray channels=sources.getJSONObject(si).optJSONArray("channels");if(channels!=null)for(int ci=0;ci<channels.length();ci++){JSONObject channel=channels.getJSONObject(ci);String name=channel.optString("ChannelName");if(name.toUpperCase(Locale.ROOT).replace("-","").replace(" ","").matches("^CCTV0?1(?![0-9]).*"))cctvOne.put(new JSONObject().put("name",name).put("fieldNames",channel.names()));}}try(FileWriter out=new FileWriter(new File(output,"cctv1-public-names.json"))){out.write(cctvOne.toString(2));}
        for(int i=0;i<sources.length();i++){JSONObject source=sources.getJSONObject(i);JSONArray channels=source.optJSONArray("channels");Map<String,List<JSONObject>> names=new HashMap<>();Set<String> codes=new HashSet<>();if(channels!=null)for(int j=0;j<channels.length();j++){JSONObject channel=channels.getJSONObject(j);codes.add(channel.optString("ChannelCode"));names.computeIfAbsent(channel.optString("ChannelName").trim(),k->new ArrayList<>()).add(channel);}int duplicates=0;Set<String> differing=new TreeSet<>();for(List<JSONObject> group:names.values())if(group.size()>1){duplicates+=group.size()-1;Iterator<String> keys=group.get(0).keys();while(keys.hasNext()){String key=keys.next();Set<String> values=new HashSet<>();for(JSONObject channel:group)values.add(channel.optString(key));if(values.size()>1)differing.add(key);}}report.put(new JSONObject().put("builtin",source.optString("id").equals("builtin")).put("rows",channels==null?0:channels.length()).put("uniqueCodes",codes.size()).put("duplicateNameRows",duplicates).put("differingFieldNames",new JSONArray(differing)));}
        try(FileWriter out=new FileWriter(new File(output,"channel-audit.json"))){out.write(report.toString(2));}check("channel cache counts audited without exposing identifiers",true);
    }
    @Override public void onStart(){
        String phase=arguments.getString("phase","run").replaceAll("[^a-zA-Z0-9_-]","");output=new File(getTargetContext().getExternalFilesDir("tv-regression"),phase);output.mkdirs();
        String error="";
        try{
            if("true".equals(arguments.getString("settingsBackReview"))){settingsBackReview();}else if("true".equals(arguments.getString("refreshStyleReview"))){refreshStyleReview();}else if("true".equals(arguments.getString("opacityFocusReview"))){opacityFocusReview();}else if("true".equals(arguments.getString("configuredReview"))){configuredReview();}else if("true".equals(arguments.getString("connectionReview"))){connectionReview();}else if("true".equals(arguments.getString("liveReview"))){liveReview();}else if("true".equals(arguments.getString("searchReview"))){searchReview();}else if("true".equals(arguments.getString("channelAudit"))){channelAudit();}else if("true".equals(arguments.getString("settingsReview"))){settingsReview();}else if("true".equals(arguments.getString("mediaReview"))){mediaReview();}else if("true".equals(arguments.getString("playbackAudit"))){playbackAudit();}else if("true".equals(arguments.getString("resumeAudit"))||"true".equals(arguments.getString("resumeVerify"))){resumeAudit();if("true".equals(arguments.getString("resumeVerify")))resumePlayback();}else {
            if(!"true".equals(arguments.getString("mediaOnly"))){live=startActivitySync(new Intent().setClassName(getTargetContext().getPackageName(),"org.jellyfin.androidtv.bestv.LiveActivity").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            check("live home starts",waitTag(live,"group",25));SystemClock.sleep(1000);groupAndSettings();livePlayer();}media();snapshotFallback();returnToLive();}
        }catch(Throwable e){failures++;error=e.toString();try{shot("fatal-state");}catch(Exception ignored){}}
        try{JSONObject report=new JSONObject().put("phase",phase).put("failures",failures).put("checks",checks).put("layers",layers).put("error",error).put("display",displayGeometry()).put("scope","Installed Android APK; real key events, view Z/focus inspection and screenshots; private reflection only chooses test entry points");try(FileWriter out=new FileWriter(new File(output,"report.json"))){out.write(report.toString(2));}}catch(Exception e){error=e.toString();}
        main(()->{if(library!=null&&!library.isDestroyed())library.finish();if(live!=null&&!live.isDestroyed())live.finish();return null;});waitForIdleSync();SystemClock.sleep(700);
        Bundle results=new Bundle();results.putString("report",new File(output,"report.json").toString());results.putInt("failures",failures);results.putString("error",error);finish(failures==0?0:1,results);
    }
}
