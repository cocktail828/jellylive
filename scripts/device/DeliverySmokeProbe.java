package org.jellyfin.androidtv.bestvlive.probe;

import android.app.*;
import android.content.*;
import android.graphics.*;
import android.os.*;
import android.view.*;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.*;
import org.json.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;
import java.util.function.*;

/** Runs against the optimized APK through public Android views; no product-private reflection. */
public final class DeliverySmokeProbe extends Instrumentation {
    private Activity live, library;
    private Bundle arguments;
    private File output;
    private final JSONArray checks=new JSONArray();
    private String error="";
    private int failures;
    private volatile String toast="";
    @Override public void onCreate(Bundle arguments){super.onCreate(arguments);this.arguments=arguments;start();}
    private <T>T main(Supplier<T> action){Object[] value=new Object[1];Throwable[] failure=new Throwable[1];runOnMainSync(()->{try{value[0]=action.get();}catch(Throwable t){failure[0]=t;}});if(failure[0]!=null)throw new RuntimeException(failure[0]);return (T)value[0];}
    private void save(){try{JSONObject result=new JSONObject().put("failures",failures).put("error",error).put("checks",checks).put("scope","Installed optimized APK; public Android views, real keys, real configured server; no private reflection");try(FileWriter writer=new FileWriter(new File(output,"report.json"))){writer.write(result.toString(2));}}catch(Exception e){throw new RuntimeException(e);}}
    private void check(String name,boolean value){try{checks.put(new JSONObject().put("name",name).put("passed",value));if(!value)failures++;save();}catch(JSONException e){throw new RuntimeException(e);}}
    private void require(String name,boolean value){check(name,value);if(!value)throw new IllegalStateException(name);}
    private boolean waitFor(BooleanSupplier condition,int seconds){long deadline=SystemClock.uptimeMillis()+seconds*1000L;do{if(condition.getAsBoolean())return true;SystemClock.sleep(200);}while(SystemClock.uptimeMillis()<deadline);return false;}
    private View root(Activity activity){return activity.getWindow().getDecorView();}
    private View find(View view,Predicate<View> predicate){if(view.getVisibility()!=View.VISIBLE)return null;if(predicate.test(view))return view;if(view instanceof ViewGroup){ViewGroup group=(ViewGroup)view;for(int i=0;i<group.getChildCount();i++){View result=find(group.getChildAt(i),predicate);if(result!=null)return result;}}return null;}
    private View tag(Activity activity,String value){return main(()->find(root(activity),v->value.equals(v.getTag())));}
    private View description(Activity activity,String value){return main(()->find(root(activity),v->v instanceof Button&&v.getContentDescription()!=null&&v.getContentDescription().toString().startsWith(value)));}
    private View text(Activity activity,Predicate<String> predicate){return main(()->find(root(activity),v->v instanceof TextView&&predicate.test(((TextView)v).getText().toString())));}
    private void key(int code){sendKeyDownUpSync(code);waitForIdleSync();SystemClock.sleep(150);}
    private void click(View view){if(view==null)throw new IllegalStateException("Missing control");require("visible control accepts focus",main(()->{view.setFocusableInTouchMode(true);return view.isEnabled()&&view.requestFocus();}));key(KeyEvent.KEYCODE_DPAD_CENTER);}
    private boolean accessibilityText(String value){AccessibilityNodeInfo node=getUiAutomation().getRootInActiveWindow();if(node==null)return false;try{return !node.findAccessibilityNodeInfosByText(value).isEmpty();}finally{node.recycle();}}
    private int windowId(){AccessibilityNodeInfo node=getUiAutomation().getRootInActiveWindow();if(node==null)return -1;try{return node.getWindowId();}finally{node.recycle();}}
    private void option(Activity activity,String action,String name)throws Exception{int previous=windowId();toast="";click(description(activity,action));boolean optional=action.equals("音轨")||action.equals("字幕");require(name+" opens popup or reports absent track",waitFor(()->windowId()>=0&&windowId()!=previous||optional&&toast.contains(action),5));if(windowId()!=previous){require(name+" popup text visible",accessibilityText(action));shot(name);key(KeyEvent.KEYCODE_BACK);}else require(name+" absent-track message visible",optional&&toast.contains(action));}
    private void shot(String name)throws Exception{Bitmap bitmap=getUiAutomation().takeScreenshot();require("4K screenshot "+name,bitmap!=null&&bitmap.getWidth()==3840&&bitmap.getHeight()==2160);try(FileOutputStream stream=new FileOutputStream(new File(output,name+".png"))){bitmap.compress(Bitmap.CompressFormat.PNG,100,stream);}bitmap.recycle();}
    private boolean focusSafe(Activity activity){return main(()->{View focus=activity.getCurrentFocus();if(focus==null)return false;Rect visible=new Rect();int[] position=new int[2];focus.getLocationOnScreen(position);return focus.isShown()&&focus.getGlobalVisibleRect(visible)&&visible.width()==focus.getWidth()&&visible.height()==focus.getHeight()&&position[0]>=0&&position[1]>=0&&visible.right<=3840&&visible.bottom<=2160;});}
    private boolean changingFrames(Rect region,int wakeKey){return waitFor(()->{key(wakeKey);Bitmap a=getUiAutomation().takeScreenshot();SystemClock.sleep(500);Bitmap b=getUiAutomation().takeScreenshot();if(a==null||b==null){if(a!=null)a.recycle();if(b!=null)b.recycle();return false;}int lit=0,changed=0,total=0;for(int y=region.top;y<region.bottom;y+=35)for(int x=region.left;x<region.right;x+=35){int first=a.getPixel(x,y),second=b.getPixel(x,y);if(Color.red(first)+Color.green(first)+Color.blue(first)>80)lit++;if(Math.abs(Color.red(first)-Color.red(second))+Math.abs(Color.green(first)-Color.green(second))+Math.abs(Color.blue(first)-Color.blue(second))>35)changed++;total++;}a.recycle();b.recycle();return lit>total/5&&changed>total/40;},25);}
    private void liveVideo()throws Exception{
        View preview=tag(live,"preview");require("live preview exists",preview!=null);
        main(()->{ListView list=(ListView)find(root(live),v->"channel-list".equals(v.getTag()));int selected=0;for(int i=0;i<list.getAdapter().getCount();i++){Object item=list.getAdapter().getItem(i);if(item instanceof JSONObject){String name=((JSONObject)item).optString("ChannelName");if(name.contains("CCTV-1")&&name.contains("高清")){selected=i;break;}}}list.setSelection(selected);list.requestFocus();return null;});
        waitForIdleSync();key(KeyEvent.KEYCODE_DPAD_CENTER);
        Rect region=new Rect();main(()->{preview.getGlobalVisibleRect(region);return null;});
        region.inset(region.width()/5,region.height()/5);
        boolean moving=changingFrames(region,KeyEvent.KEYCODE_DPAD_LEFT);
        require("live video contains changing decoded frames",moving);shot("live-home");
        click(tag(live,"group"));require("live group choices render",waitFor(()->accessibilityText("全部频道"),5));shot("live-group");key(KeyEvent.KEYCODE_BACK);
        click(tag(live,"preview"));require("live fullscreen entered",waitFor(()->tag(live,"full-video")!=null,5));key(KeyEvent.KEYCODE_DPAD_CENTER);
        require("live playback controls visible",description(live,"画面比例")!=null);
        click(description(live,"画面比例"));require("live aspect popup renders",waitFor(()->accessibilityText("原始"),5));shot("live-aspect");key(KeyEvent.KEYCODE_BACK);
        for(String action:new String[]{"音轨","字幕"})option(live,action,"live-option-"+Arrays.asList("音轨","字幕").indexOf(action));
        for(int i=0;i<4&&tag(live,"group")==null;i++)key(KeyEvent.KEYCODE_BACK);
        require("Back returns to live home",tag(live,"group")!=null);
    }
    private void login()throws Exception{
        Context context=getTargetContext();SharedPreferences auth=context.getSharedPreferences("authentication",0);
        String serverId=auth.getString("last_server_id",""),userId=auth.getString("last_user_id","");
        JSONObject stored=new JSONObject(new String(Files.readAllBytes(new File(context.getFilesDir(),"authentication_store.json").toPath()),StandardCharsets.UTF_8));
        JSONObject server=stored.getJSONObject("servers").getJSONObject(serverId),user=server.getJSONObject("users").getJSONObject(userId);
        JSONObject preset;try(InputStream stream=context.getAssets().open("jellyfin-bootstrap.json")){ByteArrayOutputStream bytes=new ByteArrayOutputStream();byte[] buffer=new byte[1024];int count;while((count=stream.read(buffer))!=-1)bytes.write(buffer,0,count);preset=new JSONObject(bytes.toString("UTF-8"));}
        require("login fixture matches current server and account",server.getString("address").replaceAll("/+$","").equals(preset.getString("address").replaceAll("/+$",""))&&user.getString("name").equals(preset.getString("username")));
        library=startActivitySync(new Intent().setClassName(context.getPackageName(),"org.jellyfin.androidtv.bestv.JellyLibraryActivity").putExtra("connect",true).putExtra("add_library",true).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        require("new library form opens",waitFor(()->text(library,s->s.equals("检测并连接"))!=null,8));
        main(()->{List<EditText> fields=new ArrayList<>();collectEdits(root(library),fields);if(fields.size()!=3)throw new IllegalStateException("Expected three login fields");for(EditText field:fields)if(field.length()!=0)throw new IllegalStateException("New form contains old values");fields.get(0).setText(preset.optString("address"));fields.get(1).setText(preset.optString("username"));fields.get(2).setText(preset.optString("password"));return null;});
        click(text(library,s->s.equals("检测并连接")));
        require("fresh login succeeds through product UI",waitFor(()->tag(library,"library-entry-movies")!=null,40));
        require("login preserves selected server and account",serverId.equals(auth.getString("last_server_id",""))&&userId.equals(auth.getString("last_user_id","")));
        require("media home focus visible in 4K",focusSafe(library));shot("media-home");
    }
    private void collectEdits(View view,List<EditText> fields){if(view instanceof EditText)fields.add((EditText)view);if(view instanceof ViewGroup){ViewGroup group=(ViewGroup)view;for(int i=0;i<group.getChildCount();i++)collectEdits(group.getChildAt(i),fields);}}
    private String clock(){View view=text(library,s->s.matches("\\d{2}:\\d{2}:\\d{2} / \\d{2}:\\d{2}:\\d{2}"));return view==null?"":main(()->((TextView)view).getText().toString());}
    private long seconds(String clock){if(clock.isEmpty())return -1;String[] pieces=clock.split(" / ")[0].split(":");return Long.parseLong(pieces[0])*3600+Long.parseLong(pieces[1])*60+Long.parseLong(pieces[2]);}
    private void media()throws Exception{
        click(tag(library,"library-entry-movies"));require("media catalog and toolbar load",waitFor(()->tag(library,"grid-filter")!=null&&main(()->find(root(library),v->v.getTag() instanceof String&&v.getTag().toString().startsWith("grid-item-"))!=null),35));
        for(String action:new String[]{"grid-filter","grid-sort"}){click(tag(library,action));shot(action);key(KeyEvent.KEYCODE_BACK);require("Back restores "+action,main(()->library.getCurrentFocus()!=null&&action.equals(library.getCurrentFocus().getTag())));}
        // The known Main10 sample cannot play on this Waydroid decoder; choose the other actual film.
        View card=main(()->find(root(library),v->v.getTag() instanceof String&&v.getTag().toString().startsWith("grid-item-")&&v.getContentDescription()!=null&&!v.getContentDescription().toString().contains("猩球崛起")&&!v.getContentDescription().toString().contains("星球崛起")));
        require("compatible film card exists",card!=null);click(card);
        require("media details render",waitFor(()->text(library,s->s.equals("播放")||s.startsWith("继续播放"))!=null,20));shot("media-detail");
        click(text(library,s->s.equals("播放")||s.startsWith("继续播放")));
        key(KeyEvent.KEYCODE_MEDIA_PLAY);
        require("media negotiates and playback position becomes available",waitFor(()->{key(KeyEvent.KEYCODE_DPAD_DOWN);return seconds(clock())>0;},35));
        long start=seconds(clock());SystemClock.sleep(2000);key(KeyEvent.KEYCODE_DPAD_DOWN);
        require("media position advances",waitFor(()->{key(KeyEvent.KEYCODE_DPAD_DOWN);return seconds(clock())>=start+1;},35));
        require("media video contains changing decoded frames",changingFrames(new Rect(768,432,3072,1512),KeyEvent.KEYCODE_DPAD_DOWN));
        require("media playback focus visible in 4K",focusSafe(library));shot("media-playing");
        for(String action:new String[]{"画面比例","倍速","音轨","字幕"}){key(KeyEvent.KEYCODE_DPAD_DOWN);option(library,action,"media-option-"+Arrays.asList("画面比例","倍速","音轨","字幕").indexOf(action));}
        key(KeyEvent.KEYCODE_MEDIA_PAUSE);key(KeyEvent.KEYCODE_DPAD_DOWN);long paused=seconds(clock());SystemClock.sleep(1300);require("media pause works",seconds(clock())==paused);
        key(KeyEvent.KEYCODE_MEDIA_PLAY);require("media resumes after pause",waitFor(()->{key(KeyEvent.KEYCODE_DPAD_DOWN);return seconds(clock())>paused;},10));
        for(int i=0;i<3&&text(library,s->s.equals("播放")||s.startsWith("继续播放"))==null;i++)key(KeyEvent.KEYCODE_BACK);
        require("media exits fullscreen to detail",text(library,s->s.equals("播放")||s.startsWith("继续播放"))!=null);
    }
    @Override public void onStart(){
        output=new File(getTargetContext().getExternalFilesDir("tv-regression"),"delivery-smoke");output.mkdirs();
        getUiAutomation().setOnAccessibilityEventListener(event->{if(event.getEventType()==android.view.accessibility.AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED&&getTargetContext().getPackageName().contentEquals(event.getPackageName()==null?"":event.getPackageName()))toast=event.getText().toString();});
        try{
            if(!"true".equals(arguments.getString("mediaOnly"))){
                live=startActivitySync(new Intent().setClassName(getTargetContext().getPackageName(),"org.jellyfin.androidtv.bestv.LiveActivity").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                require("optimized launcher starts",waitFor(()->tag(live,"group")!=null,25));
                require("live channel catalog retained",main(()->{View view=find(root(live),v->"channel-list".equals(v.getTag()));return view instanceof ListView&&((ListView)view).getAdapter().getCount()>0;}));
                liveVideo();
            }
            login();media();
        }catch(Throwable failure){failures++;error=failure.toString();try{shot("failure");}catch(Exception ignored){}}
        finally{main(()->{if(library!=null&&!library.isDestroyed())library.finish();return null;});save();}
        Bundle result=new Bundle();result.putInt("failures",failures);result.putString("error",error);finish(failures==0?0:1,result);
    }
}
