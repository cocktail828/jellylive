/* JellyLive independent live launcher; implements ui-design/designs v1. GPL-2.0. */
package org.jellyfin.androidtv.bestv;

import android.app.*;
import android.animation.ObjectAnimator;
import android.os.*;
import android.content.*;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.text.*;
import android.util.TypedValue;
import android.view.*;
import android.view.animation.LinearInterpolator;
import android.widget.*;
import androidx.media3.common.*;
import androidx.media3.datasource.*;
import androidx.media3.exoplayer.*;
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory;
import androidx.media3.ui.PlayerView;
import org.json.*;
import java.io.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.*;

public final class LiveActivity extends Activity {
    private static Thread backgroundThread(Runnable work,String name){return new Thread(()->{android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_BACKGROUND);work.run();},name);}
    private final ScheduledExecutorService worker=Executors.newSingleThreadScheduledExecutor(r->backgroundThread(r,"JellyLive-refresh"));
    private final ExecutorService diskWorker=Executors.newSingleThreadExecutor(r->backgroundThread(r,"JellyLive-storage"));
    private final ExecutorService streamWorker=Executors.newSingleThreadExecutor();
    private final Handler handler=new Handler(Looper.getMainLooper());
    private android.content.SharedPreferences prefs;
    private JSONObject config;
    private volatile BestvApi api,backgroundApi;
    private LiveSourceStore store;
    private LiveSourceStore.Source source;
    private volatile ConcurrentMap<String,List<LiveSourceStore.Program>> schedules=new ConcurrentHashMap<>();
    private volatile ConcurrentMap<String,Long> scheduleAt=new ConcurrentHashMap<>();
    private final Set<String> epgPending=new HashSet<>(),failedChannels=new HashSet<>(),refreshing=new HashSet<>();
    private TvUi ui;
    private FrameLayout root,stage,page,videoShell,videoFrame,controls,drawer,errorPanel,settingsPanel;
    private GridView favoriteGrid;
    private final List<Button> settingsTabs=new ArrayList<>();
    private final Map<String,String> settingsFocus=new HashMap<>(),favoriteFocus=new HashMap<>();
    private final Map<String,int[]> favoriteScroll=new HashMap<>();
    private ObjectAnimator startupRotation;
    private Player.Listener playbackListener;
    private String renderedRoute="",resumeCode="",resumeSource="",guideChannel="",guideSourceId="",guideFullReturn="home";
    private Runnable previewSwitch;
    private boolean playerSuspended,resumePlayback,homeFocusFromInput;
    private static final long HOME_IDLE_MS=30000, DRAWER_IDLE_MS=10000;
    private PlayerView video;
    private TextView videoMessage,sourceStatus;
    private ExoPlayer player;
    private ListView list;
    private LinearLayout sourceRowsHost;
    private List<JSONObject> shown=new ArrayList<>();
    private Button groupButton,previewButton,aspectAction,audioAction,subtitleAction;
    private boolean playerReleasedForLibrary;
    private View drawerShade;
    private Button recoveryRetry,recoverySources;
    private View lastRecoveryAction;
    private String sourceScope="";
    private boolean favoritesOnly,suppressPreviewSelection;
    private final Map<JSONObject,LiveSourceStore.Source> channelOwners=new IdentityHashMap<>();
    private final Map<String,String> channelFocus=new HashMap<>();
    private final Map<String,int[]> channelScroll=new HashMap<>();
    private Button[] tiles;
    private String route="home",group="全部频道",playingCode="",playingSource="",requestedCode="",lastConfig="",lastHomeFocus="channel-list",returnFromFull="home",guideReturn="home",settingsCategory="播放设置";
    private volatile int playGeneration;
    private int screenGeneration,guidePage,restorePosition=-1,restoreTop;
    private boolean resumed,started,recoveryUsed,controlsVisible,drawerVisible,confirmFromVideo,syncingJellyfin,openingChannel,pendingJellyfinAdd;
    private volatile boolean dead;
    private AlertDialog dialog;
    private final LiveConfirmGesture favoriteGesture=new LiveConfirmGesture();
    private View confirmAnchor;
    private boolean confirmShowsInfo;
    private TextView favoriteFeedback;
    private final Runnable removeFavoriteFeedback=()->{if(favoriteFeedback!=null){TvUi.detach(favoriteFeedback);favoriteFeedback=null;}};
    private final Runnable holdFavorite=()->{
        if(favoriteGesture.poll(android.os.SystemClock.uptimeMillis(),playingSource,playingCode,route.equals("full")&&!drawerVisible&&(dialog==null||!dialog.isShowing())&&getWindow().getDecorView().hasWindowFocus()))savePlayingFavorite();
    };
    private final Map<String,String> focusMemory=new HashMap<>();
    private final Map<String,int[]> listMemory=new HashMap<>();
    private final Deque<String> backStack=new ArrayDeque<>();
    private EditorDraft editor;
    private EditText sourceName,sourceUrl,sourceEpg,searchInput;
    private TextView editorStatus;
    private Button detectButton,saveButton;
    private String liveErrorTitle="",liveErrorDetail="";
    private String sourceErrorReason="";
    private String searchQuery="";
    private long searchGeneration,lastInputAt;
    private final Runnable hideControls=()->hideControls();
    private final Runnable hideIdleDrawer=()->{if(resumed&&route.equals("full")&&drawerVisible&&(dialog==null||!dialog.isShowing()))hideDrawer();};
    private final Runnable homeIdle=new Runnable(){public void run(){
        if(dead||!resumed||!route.equals("home"))return;
        if(dialog!=null&&dialog.isShowing()||openingChannel||previewSwitch!=null||player==null||player.getPlaybackState()!=Player.STATE_READY||!player.isPlaying()||!liveErrorTitle.isEmpty()){
            handler.postDelayed(this,1000);return;
        }
        long remaining=HOME_IDLE_MS-(android.os.SystemClock.uptimeMillis()-lastInputAt);
        if(remaining>0){handler.postDelayed(this,remaining);return;}
        rememberFocus();returnFromFull="home";route="full";render(false);
    }};
    private final Runnable hideMessage=()->{if(videoMessage!=null)videoMessage.setVisibility(View.GONE);};
    private final Runnable minuteTick=new Runnable(){public void run(){if(dead)return;updateRows();if(isLiveRoute())ensureEpg(currentChannel());if(resumed)handler.postDelayed(this,60000);}};
    private static final class EditorDraft {
        String id="",name="",url="",epg="",status="检测状态：待检测。未填写节目单也可正常观看。";
        boolean busy;
        List<JSONObject> tested;
        String signature="";
        EditorDraft(){}
        EditorDraft(JSONObject j){id=j.optString("id");name=j.optString("name");url=j.optString("url");epg=j.optString("epg");}
        JSONObject json()throws Exception{return new JSONObject().put("id",id).put("name",name).put("url",url).put("epg",epg);}
    }
    @Override public void onCreate(Bundle state){
        super.onCreate(state);getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,WindowManager.LayoutParams.FLAG_FULLSCREEN);getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        prefs=getSharedPreferences("bestv_live",MODE_PRIVATE);loadConfig();
        root=new FrameLayout(this);root.setBackgroundColor(0xff101326);setContentView(root);ui=new TvUi(this);
        showStartup();
        worker.execute(()->{
            String problem="";LiveSourceStore loaded;
            try{loaded=new LiveSourceStore(new File(getFilesDir(),"live"));}
            catch(Exception e){problem="上次缓存无法读取，请重新获取来源。";File f=new File(getFilesDir(),"live/live-sources.json");f.renameTo(new File(f.getParentFile(),"live-sources.corrupt"));try{loaded=new LiveSourceStore(f.getParentFile());}catch(Exception impossible){onUi(()->showFatal());return;}}
            try{LiveSourceStore.Source initial=loaded.current();final LiveSourceStore ready=loaded;final String warning=problem;onUi(()->{store=ready;source=initial;finishStartup(state,warning);});}catch(Exception e){onUi(()->showFatal());}
        });
    }
    private void finishStartup(Bundle state,String cacheProblem){
        if(state!=null){pendingJellyfinAdd=state.getBoolean("pending_jellyfin_add",false);route=restoreRoute(state.getString("route","home"));group=state.getString("group","全部频道");sourceScope=state.getString("source_scope","");favoritesOnly=state.getBoolean("favorites_only",false);returnFromFull=restoreRoute(state.getString("return_full","home"));searchQuery=state.getString("query","");lastHomeFocus=state.getString("home_focus","channel-list");String saved=state.getString("editor");try{if(saved!=null)editor=new EditorDraft(new JSONObject(saved));}catch(Exception ignored){}if(route.equals("editor")&&editor==null)editor=new EditorDraft();}
        if(state!=null){
            settingsCategory=state.getString("settings_category","播放设置");guideReturn=state.getString("guide_return","home");guideFullReturn=state.getString("guide_full_return","home");guideChannel=state.getString("guide_channel","");guideSourceId=state.getString("guide_source","");guidePage=state.getInt("guide_page",0);
            resumeSource=state.getString("resume_source","");resumeCode=state.getString("resume_code","");resumePlayback=state.getBoolean("resume_playback",false);
            ArrayList<String> savedStack=state.getStringArrayList("back_stack");if(savedStack!=null)backStack.addAll(savedStack);
            restoreStringMap(state,"favorite_focus",favoriteFocus);restoreStringMap(state,"settings_focus",settingsFocus);restoreStringMap(state,"page_focus",focusMemory);
            try{JSONObject positions=new JSONObject(state.getString("favorite_scroll","{}"));Iterator<String> keys=positions.keys();while(keys.hasNext()){String key=keys.next();JSONArray value=positions.optJSONArray(key);if(value!=null)favoriteScroll.put(key,new int[]{value.optInt(0),value.optInt(1)});}}catch(Exception ignored){}
        }
        if(!sourceScope.isEmpty()&&!sourceScope.equals(LiveSourceStore.BUILTIN))sourceScope=LiveFilter.OTHERS;
        if(favoritesOnly)sourceScope="";
        video=new PlayerView(this);video.setUseController(false);video.setFocusable(false);video.setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);
        createLivePlayer();
        if(startupRotation!=null){startupRotation.cancel();startupRotation=null;}
        root.removeAllViews();stage=ui.canvas();FrameLayout.LayoutParams stageParams=new FrameLayout.LayoutParams(ui.px(960),ui.px(540),Gravity.CENTER);root.addView(stage,stageParams);
        videoShell=new FrameLayout(this);videoShell.setBackgroundColor(Color.BLACK);videoShell.setClipToOutline(true);videoShell.addView(video,new FrameLayout.LayoutParams(-1,-1));stage.addView(videoShell);videoShell.setVisibility(View.GONE);
        render(false);
        if(source.channels.isEmpty()&&source.builtin()&&!config.optString("user_id").isEmpty()&&state==null)showOnboarding();
        if(!cacheProblem.isEmpty())toast(cacheProblem);
        worker.scheduleWithFixedDelay(()->onUi(()->{if(resumed)refreshSource(source.id,false);}),6,6,TimeUnit.HOURS);
        if(resumed)resumeReady();
    }
    private void createLivePlayer(){
        player=new ExoPlayer.Builder(this).setRenderersFactory(new DefaultRenderersFactory(this).setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER)).build();
        playerReleasedForLibrary=false;video.setPlayer(player);applyPlayerSettings();
    }
    private void releaseLivePlayerForLibrary(){
        if(player==null||playerReleasedForLibrary)return;
        suspendPlayback();handler.removeCallbacks(homeIdle);handler.removeCallbacks(hideControls);handler.removeCallbacks(hideIdleDrawer);
        video.setPlayer(null);player.release();playerReleasedForLibrary=true;
    }
    private void restoreStringMap(Bundle state,String key,Map<String,String> target){try{JSONObject values=new JSONObject(state.getString(key,"{}"));Iterator<String> names=values.keys();while(names.hasNext()){String name=names.next();target.put(name,values.optString(name));}}catch(Exception ignored){}}
    private void showStartup(){
        FrameLayout loading=ui.canvas();loading.setBackgroundColor(0xff101326);root.addView(loading,new FrameLayout.LayoutParams(ui.px(960),ui.px(540),Gravity.CENTER));
        ImageView logo=new ImageView(this);logo.setImageResource(org.jellyfin.androidtv.R.drawable.jellylive_mark);ui.at(loading,logo,310,199,90,90);
        ui.text(loading,"JellyLive",40,TvUi.INK,422,214,270,54);ui.text(loading,"正在准备直播…",14,TvUi.MUTED,360,316,260,32);
        if(Build.VERSION.SDK_INT<26||android.animation.ValueAnimator.areAnimatorsEnabled()){startupRotation=ObjectAnimator.ofFloat(logo,View.ROTATION,0f,360f);startupRotation.setDuration(2000);startupRotation.setRepeatCount(android.animation.ValueAnimator.INFINITE);startupRotation.setInterpolator(new LinearInterpolator());startupRotation.start();}
    }
    private void showFatal(){if(startupRotation!=null){startupRotation.cancel();startupRotation=null;}TextView t=new TextView(this);t.setText("直播配置存储不可用，请检查电视存储空间后重试。");t.setTextColor(Color.WHITE);t.setBackgroundColor(0xff101326);setContentView(t);}
    private void loadConfig(){
        try{config=BestvApi.normalize(new JSONObject(prefs.getString("config","{}")));
            if(config.optString("user_id").trim().isEmpty())try(InputStream in=getAssets().open("bestv-bootstrap.json")){config=BestvApi.normalize(new JSONObject(LiveSourceStore.read(in,1024*1024)));prefs.edit().putString("config",config.toString()).apply();}catch(FileNotFoundException ignored){}
            api=new BestvApi(config);backgroundApi=new BestvApi(config);lastConfig=config.toString();
        }catch(Exception e){config=new JSONObject();}
    }
    private void onUi(Runnable r){runOnUiThread(()->{if(!dead&&!isFinishing())r.run();});}
    private void toast(String s){Toast.makeText(this,s,Toast.LENGTH_LONG).show();}
    private boolean isLiveRoute(){return route.equals("home")||route.equals("full");}
    private void resetIdleTimers(){
        handler.removeCallbacks(homeIdle);handler.removeCallbacks(hideIdleDrawer);handler.removeCallbacks(hideControls);
        if(!resumed||dialog!=null&&dialog.isShowing())return;
        if(route.equals("home"))handler.postDelayed(homeIdle,Math.max(1,HOME_IDLE_MS-(android.os.SystemClock.uptimeMillis()-lastInputAt)));
        if(route.equals("full")&&drawerVisible)handler.postDelayed(hideIdleDrawer,ui.timeout((int)DRAWER_IDLE_MS));
        resetControlsTimer();
    }
    private void cancelPreviewSwitch(){if(previewSwitch!=null){handler.removeCallbacks(previewSwitch);previewSwitch=null;}}
    private void schedulePreview(JSONObject channel){
        cancelPreviewSwitch();if(!homeFocusFromInput||suppressPreviewSelection)return;final String id=owner(channel).id;
        previewSwitch=()->{previewSwitch=null;if(resumed&&route.equals("home")&&owner(channel).id.equals(id))play(channel,true);};
        handler.postDelayed(previewSwitch,150);
    }
    private void suspendPlayback(){
        cancelPreviewSwitch();if(player==null||playerSuspended)return;
        resumePlayback=player.getMediaItemCount()>0||openingChannel;
        resumeSource=source.id;resumeCode=requestedCode.isEmpty()?playingCode:requestedCode;
        playGeneration++;openingChannel=false;playerSuspended=true;
        if(playbackListener!=null){player.removeListener(playbackListener);playbackListener=null;}
        player.stop();player.clearMediaItems();JellyLiveBridge.releaseLiveStreams();
        removeVideoError();
    }
    private void immersive(){root.setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN|View.SYSTEM_UI_FLAG_HIDE_NAVIGATION|View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY|View.SYSTEM_UI_FLAG_LAYOUT_STABLE|View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN|View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);}
    private void rememberFocus(){
        rememberChannelFilter();
        if(favoriteGrid!=null){int n=favoriteGrid.getSelectedItemPosition();if(n>=0&&n<shown.size())favoriteFocus.put(source.id,shown.get(n).optString("ChannelCode"));View selected=favoriteGrid.getChildAt(n-favoriteGrid.getFirstVisiblePosition());favoriteScroll.put(source.id,new int[]{Math.max(0,n),selected==null?0:selected.getTop()});}
        rememberSettingsFocus();
        View f=getCurrentFocus();if(f!=null&&f.getTag() instanceof String)focusMemory.put(route,(String)f.getTag());
        if(list!=null&&list.getParent()!=null){int selected=list.getSelectedItemPosition();View row=selected<0?null:list.getChildAt(selected-list.getFirstVisiblePosition());listMemory.put(route,new int[]{Math.max(0,selected),row==null?0:row.getTop()});}
        if(route.equals("home")&&f!=null&&f.getTag() instanceof String){String t=(String)f.getTag();if(t.equals("preview")||t.equals("channel-list")||t.equals("group"))lastHomeFocus=t;}
        captureEditor();
    }
    private static String restoreRoute(String saved){return "channels".equals(saved)?"home":saved;}
    private void navigate(String target){rememberFocus();backStack.push(route);route=restoreRoute(target);render(true);}
    private void home(){rememberFocus();backStack.clear();route="home";render(true);}
    private void back(){
        if(dialog!=null&&dialog.isShowing()){dialog.dismiss();return;}
        if(drawerVisible){hideDrawer();return;}
        if(controlsVisible){hideControls();return;}
        if(route.equals("full")){route=restoreRoute(returnFromFull);render(true);return;}
        if(route.equals("guide")){route=restoreRoute(guideReturn);if(route.equals("full"))returnFromFull=guideFullReturn;render(true);return;}
        if(route.equals("home")){exitDialog();return;}
        if(route.equals("editor")&&editor!=null&&editor.busy){toast("正在检测或保存，请稍候。");return;}
        rememberFocus();route=backStack.isEmpty()?"home":restoreRoute(backStack.pop());render(true);
    }
    private void render(boolean restore){
        cancelFavoriteGesture();route=restoreRoute(route);
        boolean enteringHome=route.equals("home")&&!renderedRoute.equals("home");renderedRoute=route;
        screenGeneration++;cancelPreviewSwitch();handler.removeCallbacks(hideControls);handler.removeCallbacks(hideIdleDrawer);handler.removeCallbacks(homeIdle);controlsVisible=false;drawerVisible=false;
        if(page!=null)TvUi.detach(page);page=ui.canvas();if(isLiveRoute())page.setBackgroundColor(Color.TRANSPARENT);FrameLayout.LayoutParams p=new FrameLayout.LayoutParams(ui.px(960),ui.px(540),Gravity.CENTER);root.addView(page,p);
        videoFrame=null;list=null;favoriteGrid=null;settingsPanel=null;settingsTabs.clear();groupButton=null;previewButton=null;tiles=null;sourceStatus=null;sourceRowsHost=null;sourceName=null;sourceUrl=null;sourceEpg=null;editorStatus=null;searchInput=null;controls=null;drawer=null;
        if(!isLiveRoute())suspendPlayback();else if(resumed&&player.getMediaItemCount()>0)player.play();
        videoShell.setVisibility(isLiveRoute()?View.VISIBLE:View.GONE);
        switch(route){case "full":renderFull();break;case "sources":renderSources();break;case "editor":renderEditor();break;case "favorites":renderFavorites();break;case "guide":renderGuide();break;case "search":renderSearch();break;case "settings":renderSettings();break;case "onboarding":renderOnboarding();break;case "source-error":renderSourceError();break;default:route="home";renderHome();}
        if(isLiveRoute()&&!liveErrorTitle.isEmpty())showVideoError(liveErrorTitle,liveErrorDetail);
        immersive();final int gen=screenGeneration;
        page.post(()->{if(gen!=screenGeneration||dead||drawerVisible)return;if(route.equals("favorites")){restoreFavoritePosition();return;}String desired=restore?focusMemory.get(route):null;if(desired==null){if(route.equals("sources"))desired="source-refresh";else if(route.equals("editor"))desired="editor-name";else if(route.equals("settings"))desired="settings-"+settingsCategoryIndex();else if(route.equals("source-error"))desired="source-retry";}View f=desired==null?null:page.findViewWithTag(desired);if(f!=null&&f.isFocusable()&&f.isShown())f.requestFocus();else if(route.equals("home"))focusHomeDefault();else if(route.equals("full")){if(errorPanel!=null){View q=firstFocusable(errorPanel);if(q!=null)q.requestFocus();}else videoFrame.requestFocus();}else{View first=firstFocusable(page);if(first!=null)first.requestFocus();}if(list!=null&&restore){int[] a=listMemory.get(route);if(a!=null&&list.hasFocus())list.setSelectionFromTop(Math.min(a[0],shown.size()-1),a[1]);}});
        if(resumed&&started&&isLiveRoute()&&player.getMediaItemCount()==0&&!openingChannel&&!source.channels.isEmpty()&&(prefs.getBoolean("auto_play",true)||resumePlayback&&resumeSource.equals(source.id)))restoreLastChannel();
        if(enteringHome)lastInputAt=android.os.SystemClock.uptimeMillis();resetIdleTimers();
    }
    private View firstFocusable(View v){if(v.getVisibility()!=View.VISIBLE||!v.isEnabled())return null;if(v.isFocusable())return v;if(v instanceof ViewGroup)for(int i=0;i<((ViewGroup)v).getChildCount();i++){View f=firstFocusable(((ViewGroup)v).getChildAt(i));if(f!=null)return f;}return null;}
    private void tag(View v,String t){v.setTag(t);}
    private void attachVideo(float x,float y,float w,float h,boolean focus){
        FrameLayout.LayoutParams host=new FrameLayout.LayoutParams(ui.px(w),ui.px(h));host.leftMargin=ui.px(x);host.topMargin=ui.px(y);videoShell.setLayoutParams(host);videoShell.setBackground(ui.surface(Color.BLACK,false));
        videoFrame=new FrameLayout(this);videoFrame.setId(View.generateViewId());videoFrame.setFocusable(focus);videoFrame.setFocusableInTouchMode(focus);tag(videoFrame,"video");ui.at(page,videoFrame,x,y,w,h);
        if(videoMessage==null){videoMessage=ui.text("",14,TvUi.INK);videoMessage.setGravity(Gravity.CENTER);videoMessage.setBackgroundColor(0xc0101629);FrameLayout.LayoutParams m=new FrameLayout.LayoutParams(-1,ui.px(48),Gravity.BOTTOM);videoShell.addView(videoMessage,m);videoMessage.setVisibility(View.GONE);}
        if(focus){videoFrame.setOnClickListener(v->showControls());videoFrame.setOnTouchListener((v,e)->{if(e.getAction()==MotionEvent.ACTION_UP)v.performClick();return true;});}
    }
    private android.graphics.drawable.StateListDrawable previewBorder(){
        android.graphics.drawable.StateListDrawable d=new android.graphics.drawable.StateListDrawable();GradientDrawable f=new GradientDrawable();f.setColor(Color.TRANSPARENT);f.setCornerRadius(ui.px(10));f.setStroke(ui.px(2),Color.WHITE);GradientDrawable idle=new GradientDrawable();idle.setColor(Color.TRANSPARENT);idle.setCornerRadius(ui.px(10));idle.setStroke(ui.px(.5f),0xff414866);d.addState(new int[]{android.R.attr.state_focused},f);d.addState(new int[]{},idle);return d;
    }
    private void renderHome(){
        attachVideo(50,32,526,358,false);
        previewButton=ui.button(page,"",50,32,526,358,()->enterFull());tag(previewButton,"preview");previewButton.setContentDescription("直播预览，确定全屏观看");previewButton.setOnFocusChangeListener((v,f)->{if(f)lastHomeFocus="preview";});
        android.graphics.drawable.StateListDrawable border=new android.graphics.drawable.StateListDrawable();GradientDrawable focused=new GradientDrawable();focused.setColor(Color.TRANSPARENT);focused.setCornerRadius(ui.px(10));focused.setStroke(ui.px(2),Color.WHITE);GradientDrawable idle=new GradientDrawable();idle.setColor(Color.TRANSPARENT);idle.setCornerRadius(ui.px(10));idle.setStroke(ui.px(.5f),0xff414866);border.addState(new int[]{android.R.attr.state_focused},focused);border.addState(new int[]{},idle);previewButton.setBackground(border);
        FrameLayout panel=new FrameLayout(this);panel.setBackground(ui.surface(0xff14192e,false));ui.at(page,panel,592,32,320,358);
        groupButton=makeGroupButton(page,596,40,308,31);tag(groupButton,"group");groupButton.setOnFocusChangeListener((v,f)->{if(f)lastHomeFocus="group";});groupButton.setGravity(Gravity.CENTER_VERTICAL|Gravity.START);groupButton.setBackground(ui.focusBackground(0xff14192e));
        filteredChannels(false);list=makeChannelList(596,81.5f,308,303,false);tag(list,"channel-list");
        tiles=new Button[4];String[] names={"☆  收藏","◇  直播源","▣  媒体库","⚙  设置"};
        Runnable[] clicks={()->navigate("favorites"),()->navigate("sources"),()->openLibrary(false),()->navigate("settings")};
        for(int i=0;i<4;i++){final int index=i;tiles[i]=ui.button(page,names[i],50+i*219,413,203,88,clicks[i]);tiles[i].setTextSize(TypedValue.COMPLEX_UNIT_PX,18*ui.scale*ui.fontScale);tag(tiles[i],"tile-"+i);tiles[i].setOnKeyListener((v,key,e)->{if(e.getAction()!=KeyEvent.ACTION_DOWN)return false;if(key==KeyEvent.KEYCODE_DPAD_UP){if("channel-list".equals(lastHomeFocus))focusList(false);else if("group".equals(lastHomeFocus))groupButton.requestFocus();else focusPreview();return true;}if(key==KeyEvent.KEYCODE_DPAD_LEFT&&index==0||key==KeyEvent.KEYCODE_DPAD_RIGHT&&index==3||key==KeyEvent.KEYCODE_DPAD_DOWN)return true;return false;});}
        previewButton.setOnKeyListener((v,key,e)->{if(e.getAction()!=KeyEvent.ACTION_DOWN)return false;if(key==KeyEvent.KEYCODE_DPAD_RIGHT){lastHomeFocus="channel-list";focusList(true);return true;}if(key==KeyEvent.KEYCODE_DPAD_DOWN){lastHomeFocus="preview";tiles[0].requestFocus();return true;}if(key==KeyEvent.KEYCODE_DPAD_UP||key==KeyEvent.KEYCODE_DPAD_LEFT)return true;return false;});
        groupButton.setOnKeyListener((v,key,e)->{if(e.getAction()!=KeyEvent.ACTION_DOWN)return false;if(key==KeyEvent.KEYCODE_DPAD_DOWN){focusList(false);return true;}if(key==KeyEvent.KEYCODE_DPAD_LEFT){focusPreview();return true;}if(key==KeyEvent.KEYCODE_DPAD_RIGHT)return true;if(key==KeyEvent.KEYCODE_DPAD_UP)return true;return false;});
        if(shown.isEmpty()){ui.text(page,source.channels.isEmpty()?"暂无频道\n请在直播源中获取或添加来源":"本分组暂无频道",14,TvUi.MUTED,622,150,280,110);if(source.channels.isEmpty())showVideoEmpty();}
    }
    private void focusHomeDefault(){
        homeFocusFromInput=false;cancelPreviewSwitch();
        if(errorPanel!=null){focusRecovery();return;}
        if(list!=null&&!shown.isEmpty())focusList(true);
        else focusPreview();
    }
    private void focusPreview(){if(errorPanel!=null){focusRecovery();}else if(previewButton!=null)previewButton.requestFocus();}
    private LiveSourceStore.Source owner(JSONObject channel){LiveSourceStore.Source found=channelOwners.get(channel);return found==null?source:found;}
    private String channelKey(JSONObject channel){return channel==null?"":LiveFilter.key(owner(channel).id,channel.optString("ChannelCode"));}
    private boolean isPlayingChannel(JSONObject channel){return channel!=null&&playingSource.equals(owner(channel).id)&&playingCode.equals(channel.optString("ChannelCode"));}
    private String filterKey(){return (drawerVisible?"drawer":route)+"/"+sourceScope+"/"+favoritesOnly;}
    private void rememberChannelFilter(){if(list==null)return;int at=list.getSelectedItemPosition();if(at>=0&&at<shown.size()){channelFocus.put(filterKey(),channelKey(shown.get(at)));View row=list.getChildAt(at-list.getFirstVisiblePosition());channelScroll.put(filterKey(),new int[]{at,row==null?0:row.getTop()});}}
    private String sourceScopeName(){return favoritesOnly?"收藏":sourceScope.isEmpty()?"全部频道":sourceScope.equals(LiveSourceStore.BUILTIN)?"电视直播源":"其他直播源";}
    private void filteredChannels(boolean favorites){
        if(!sourceScope.isEmpty()&&!sourceScope.equals(LiveFilter.OTHERS)){LiveSourceStore.Source selected=store.metadata(sourceScope);if(selected==null||!selected.enabled)sourceScope="";}
        shown=new ArrayList<>();channelOwners.clear();
        for(LiveSourceStore.Source s:store.channelSnapshots())for(JSONObject channel:s.channels){channelOwners.put(channel,s);if(LiveFilter.includes(sourceScope,favorites||favoritesOnly,s.id,s.enabled,store.favorite(s.id,channel.optString("ChannelCode"))))shown.add(channel);}
    }
    private void applyChannelFilter(String scope,boolean favorites){
        rememberChannelFilter();cancelPreviewSwitch();sourceScope=scope;favoritesOnly=favorites;homeFocusFromInput=false;boolean wasDrawer=drawerVisible;
        suppressPreviewSelection=true;render(false);if(wasDrawer)showDrawer();page.post(()->suppressPreviewSelection=false);
    }
    private ListView makeChannelList(float x,float y,float w,float h,boolean full){
        ListView l=new ListView(this);l.setId(View.generateViewId());l.setFocusable(true);l.setFocusableInTouchMode(true);l.setBackgroundColor(Color.TRANSPARENT);l.setDivider(new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT));l.setDividerHeight(ui.px(full?3:4));GradientDrawable selection=new GradientDrawable();selection.setColor(Color.TRANSPARENT);selection.setCornerRadius(ui.px(8));selection.setStroke(ui.px(2),Color.WHITE);l.setSelector(selection);l.setDrawSelectorOnTop(true);l.setPadding(ui.px(1),ui.px(1),ui.px(1),ui.px(1));l.setClipToPadding(false);
        l.setAdapter(new BaseAdapter(){public int getCount(){return shown.size();}public Object getItem(int n){return shown.get(n);}public long getItemId(int n){return n;}
            public View getView(int n,View old,ViewGroup parent){
                JSONObject ch=shown.get(n);FrameLayout row=new FrameLayout(LiveActivity.this);
                android.graphics.drawable.StateListDrawable bg=new android.graphics.drawable.StateListDrawable();bg.addState(new int[]{android.R.attr.state_selected},ui.surface(TvUi.ACCENT,true));bg.addState(new int[]{},full?new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT):ui.surface(0xff1b2038,false));row.setBackground(bg);
                row.setLayoutParams(new AbsListView.LayoutParams(-1,ui.px(ui.largeText()?58:full?45:47)));
                TextView number=ui.text(ch.optString("ChannelNo",String.valueOf(n+1)),12,TvUi.INK);number.setGravity(Gravity.CENTER);number.setSingleLine();number.setTextScaleX(Math.min(1f,ui.px(26)/Math.max(1f,number.getPaint().measureText(number.getText().toString()))));
                if(!full){GradientDrawable badge=new GradientDrawable();badge.setColor(new int[]{0xff50578d,0xff346d70,0xff3b668b}[n%3]);badge.setCornerRadius(ui.px(20));number.setBackground(badge);}
                ui.at(row,number,full?10:9,9,28,28);
                float left=full?46:47;TextView title=ui.text(ch.optString("ChannelName"),full?15:14,TvUi.INK);title.setTypeface(null,Typeface.BOLD);title.setSingleLine();title.setEllipsize(TextUtils.TruncateAt.END);ui.at(row,title,left,full?1.5f:3,w-left-22,ui.largeText()?31:22);
                TextView sub=ui.text(programmeText(ch),12,TvUi.MUTED);sub.setSingleLine();sub.setEllipsize(TextUtils.TruncateAt.END);ui.at(row,sub,left,ui.largeText()?35:full?24.5f:26,w-left-22,ui.largeText()?21:17);
                ui.at(row,ui.text(isPlayingChannel(ch)?"▷":"",14,0xffb5a6ff),w-20,8,16,26);return row;
            }
        });
        ui.at(page,l,x,y,w,h);
        l.setOnItemClickListener((p,v,n,id)->{JSONObject c=shown.get(n);cancelPreviewSwitch();play(c,true);if(full)hideDrawer();});
        l.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener(){public void onItemSelected(AdapterView<?> p,View v,int n,long id){if(l.hasFocus()){lastHomeFocus="channel-list";focusMemory.put(route,"channel-list");if(!full&&route.equals("home")&&n>=0&&n<shown.size())schedulePreview(shown.get(n));}}public void onNothingSelected(AdapterView<?> p){}});
        l.setOnKeyListener((v,key,e)->{if(e.getAction()!=KeyEvent.ACTION_DOWN)return false;int n=l.getSelectedItemPosition();if(key==KeyEvent.KEYCODE_DPAD_LEFT){if(full){hideDrawer();return true;}if(previewButton!=null){if(errorPanel!=null){focusRecovery();}else focusPreview();}else if(groupButton!=null)groupButton.requestFocus();return true;}if(key==KeyEvent.KEYCODE_DPAD_UP&&n<=0){rememberChannelFilter();if(groupButton!=null)groupButton.requestFocus();return true;}if(key==KeyEvent.KEYCODE_DPAD_DOWN&&n>=shown.size()-1){if(tiles!=null)tiles[1].requestFocus();else if(groupButton!=null)groupButton.requestFocus();return true;}if(key==KeyEvent.KEYCODE_DPAD_RIGHT){rememberChannelFilter();if(groupButton!=null)groupButton.requestFocus();return true;}return false;});
        return l;
    }
    private void focusList(boolean current){
        if(list==null)return;if(shown.isEmpty()){if(groupButton!=null)groupButton.requestFocus();return;}
        int target=list.getSelectedItemPosition();if(current||target<0){
            List<String> keys=new ArrayList<>();for(JSONObject ch:shown)keys.add(channelKey(ch));
            String fallback=LiveFilter.key(playingSource.isEmpty()?source.id:playingSource,playingCode.isEmpty()?requestedCode:playingCode);
            if(playingCode.isEmpty()&&requestedCode.isEmpty()){String code=source.id.equals(resumeSource)?resumeCode:store.last(source.id);if(code.isEmpty()&&source.builtin())code=prefs.getString("last_channel","");fallback=LiveFilter.key(source.id,code);}
            target=LiveFilter.focus(keys,channelFocus.get(filterKey()),fallback);
        }
        list.requestFocus();int[] scroll=channelScroll.get(filterKey());if(scroll!=null&&(current||scroll[0]==target))list.setSelectionFromTop(target,scroll[1]);else list.setSelection(target);
    }
    private JSONObject findChannel(String code){for(JSONObject c:source.channels)if(code.equals(c.optString("ChannelCode")))return c;return null;}
    private JSONObject requestedChannel(){JSONObject c=findChannel(requestedCode);return c==null?currentChannel():c;}
    private JSONObject currentChannel(){JSONObject c=playingSource.equals(source.id)?findChannel(playingCode):null;if(c==null)c=findChannel(requestedCode);if(c==null&&!source.channels.isEmpty())c=source.channels.get(0);return c;}
    private void restoreLastChannel(){String code=source.id.equals(resumeSource)?resumeCode:store.last(source.id);if(code.isEmpty()&&source.builtin())code=prefs.getString("last_channel","");JSONObject c=findChannel(code);if(c==null&&!source.channels.isEmpty())c=source.channels.get(0);if(c!=null)play(c,true);}
    private void play(JSONObject ch,boolean fresh){
        if(playerReleasedForLibrary)return;
        if(ch==null||!resumed||!isLiveRoute())return;
        cancelFavoriteGesture();cancelPreviewSwitch();LiveSourceStore.Source targetSource=owner(ch);
        if(!targetSource.id.equals(source.id)){source=targetSource;requestedCode="";failedChannels.clear();final String selectedId=source.id;diskTask(()->store.activate(selectedId),()->{});}
        String code=ch.optString("ChannelCode");final String id=source.id;final boolean builtin=source.builtin();final int gen=++playGeneration;final BestvApi activeApi=api;
        requestedCode=code;resumeSource=id;resumeCode=code;playerSuspended=false;resumePlayback=true;
        if(fresh)recoveryUsed=false;
        if(fresh&&id.equals(playingSource)&&code.equals(playingCode)&&player.getPlaybackState()==Player.STATE_READY){openingChannel=false;bindPlaybackListener(gen,id,code);removeVideoError();player.play();return;}
        openingChannel=true;
        removeVideoError();message("正在打开频道…");
        if(source.jellyfin()){
            JellyLiveBridge.playbackUrl(this,source.url,code,new JellyLiveBridge.Callback<String>(){
                public void success(String url){if(!validPlayback(gen,id,code))return;try{LiveSourceStore.validateUrl(url);openingChannel=false;installStream(url,id,code,gen,JellyLiveBridge.mediaDataSourceFactory(LiveActivity.this));}catch(Exception e){openingChannel=false;showVideoError("Jellyfin 直播暂不可用","可返回独立直播，或检查媒体库连接。");}}
                public void failure(String message){if(validPlayback(gen,id,code)){openingChannel=false;showVideoError("Jellyfin 直播暂不可用",message);}}
            });return;
        }
        streamWorker.execute(()->{if(gen!=playGeneration||dead)return;try{String url=builtin?activeApi.play(code):ch.getString("Url");LiveSourceStore.validateUrl(url);Map<String,String> headers=new HashMap<>();JSONObject h=ch.optJSONObject("Headers");if(h!=null){Iterator<String> keys=h.keys();while(keys.hasNext()){String k=keys.next();headers.put(k,h.optString(k));}}
                onUi(()->{if(!validPlayback(gen,id,code))return;
                    DefaultHttpDataSource.Factory http=new DefaultHttpDataSource.Factory().setUserAgent("JellyLive/0.1 AndroidTV").setConnectTimeoutMs(12000).setReadTimeoutMs(20000).setDefaultRequestProperties(headers);
                    openingChannel=false;installStream(url,id,code,gen,new DefaultDataSource.Factory(this,http));ensureEpg(ch);
                });
            }catch(Exception e){onUi(()->{if(!validPlayback(gen,id,code))return;openingChannel=false;failedChannels.add(code);if(player.getPlaybackState()==Player.STATE_READY&&!playingSource.isEmpty()&&(!id.equals(playingSource)||!playingCode.equals(code))){
                    for(LiveSourceStore.Source previous:store.channelSnapshots())if(previous.id.equals(playingSource)){source=previous;break;}
                    requestedCode=playingCode;resumeCode=playingCode;resumeSource=playingSource;final String restored=playingSource;diskTask(()->store.activate(restored),()->{});bindPlaybackListener(gen,playingSource,playingCode);if(videoMessage!=null)videoMessage.setVisibility(View.GONE);toast("无法打开此频道，继续播放原频道。");return;}showVideoError(failedChannels.size()>=source.channels.size()?"当前来源的频道暂不可用":"无法打开此频道",builtin&&config.optString("user_id").isEmpty()?"请先导入机顶盒配置，或添加第三方直播源。":"请检查网络、授权或来源；也可选择其他频道。");});}
        });
    }
    private boolean validPlayback(int generation,String id,String code){return !dead&&!playerReleasedForLibrary&&resumed&&isLiveRoute()&&generation==playGeneration&&source.id.equals(id)&&requestedCode.equals(code);}
    private void bindPlaybackListener(int generation,String id,String code){
        if(playbackListener!=null)player.removeListener(playbackListener);
        playbackListener=new Player.Listener(){
            private boolean current(){MediaItem item=player.getCurrentMediaItem();return validPlayback(generation,id,code)&&item!=null&&item.mediaId.equals(id+":"+code);}
            @Override public void onPlaybackStateChanged(int state){
                if(state!=Player.STATE_READY||!current())return;
                playingSource=id;playingCode=code;failedChannels.remove(code);removeVideoError();
                diskWorker.execute(()->{try{store.remember(id,code);}catch(Exception ignored){}});
                if(videoMessage!=null)videoMessage.setVisibility(View.GONE);
                updateRows();if(controlsVisible)updateControlLabels();ensureEpg(findChannel(code));
            }
            @Override public void onTracksChanged(Tracks tracks){if(current()&&controlsVisible)updateControlLabels();}
            @Override public void onPlayerError(PlaybackException error){
                if(!current())return;
                JSONObject channel=findChannel(code);if(!recoveryUsed&&channel!=null){recoveryUsed=true;play(channel,false);return;}
                failedChannels.add(code);showVideoError(failedChannels.size()>=source.channels.size()?"当前来源的频道暂不可用":"此频道暂时无法播放","可重试此频道，或选择其他频道、检查直播源。");
            }
        };
        player.addListener(playbackListener);
    }
    private void installStream(String url,String id,String code,int generation,DataSource.Factory data){
        if(!validPlayback(generation,id,code))return;
        if(playbackListener!=null){player.removeListener(playbackListener);playbackListener=null;}
        MediaItem item=new MediaItem.Builder().setUri(url).setMediaId(id+":"+code).build();
        player.setMediaSource(new DefaultMediaSourceFactory(data).createMediaSource(item));bindPlaybackListener(generation,id,code);player.prepare();player.play();
    }
    private void message(String text){if(videoMessage==null)return;videoMessage.setText(text);videoMessage.setVisibility(View.VISIBLE);}
    private void showVideoEmpty(){showVideoError("还没有可用频道", "添加第三方直播源，或配置内置机顶盒来源。");}
    private void removeVideoError(){liveErrorTitle="";liveErrorDetail="";if(videoFrame!=null)videoFrame.setVisibility(View.VISIBLE);if(errorPanel!=null){boolean focused=errorPanel.hasFocus();TvUi.detach(errorPanel);errorPanel=null;recoveryRetry=null;recoverySources=null;lastRecoveryAction=null;if(previewButton!=null){previewButton.setVisibility(View.VISIBLE);if(focused)focusPreview();}else if(focused&&route.equals("full")&&videoFrame!=null&&!drawerVisible)videoFrame.requestFocus();}}
    private void showVideoError(String title,String detail){
        if(!isLiveRoute())return;
        if(videoShell==null){liveErrorTitle=title;liveErrorDetail=detail;return;}removeVideoError();liveErrorTitle=title;liveErrorDetail=detail;if(videoMessage!=null)videoMessage.setVisibility(View.GONE);
        errorPanel=new FrameLayout(this);errorPanel.setBackgroundColor(0xf0101629);videoShell.addView(errorPanel,new FrameLayout.LayoutParams(-1,-1));
        float w=route.equals("full")?960:route.equals("favorites")?478:526;float h=route.equals("full")?540:route.equals("favorites")?269:358;
        float mid=h/2;TextView warning=ui.text("△",40,0xffbba8ff);warning.setGravity(Gravity.CENTER);ui.at(errorPanel,warning,w/2-20,mid-83,40,40);
        TextView heading=ui.text(title,23,TvUi.INK);heading.setGravity(Gravity.CENTER);heading.setMaxLines(2);ui.at(errorPanel,heading,20,mid-40,w-40,45);
        TextView description=ui.text(detail,13,TvUi.MUTED);description.setGravity(Gravity.CENTER);description.setMaxLines(2);ui.at(errorPanel,description,22,mid-4,w-44,38);
        recoveryRetry=ui.button(errorPanel,"重试",w/2-150,mid+44,130,42,()->play(requestedChannel(),true));recoverySources=ui.button(errorPanel,"检查直播源",w/2-5,mid+44,155,42,()->navigate("sources"));
        recoveryRetry.setNextFocusLeftId(recoveryRetry.getId());recoveryRetry.setNextFocusRightId(recoverySources.getId());recoverySources.setNextFocusLeftId(recoveryRetry.getId());recoverySources.setNextFocusRightId(recoverySources.getId());
        for(Button action:new Button[]{recoveryRetry,recoverySources}){action.setOnFocusChangeListener((v,f)->{if(f)lastRecoveryAction=v;});action.setOnKeyListener((v,key,event)->{if(event.getAction()!=KeyEvent.ACTION_DOWN)return false;if(key==KeyEvent.KEYCODE_DPAD_DOWN&&tiles!=null){tiles[1].requestFocus();return true;}return key==KeyEvent.KEYCODE_DPAD_UP||key==KeyEvent.KEYCODE_DPAD_DOWN;});}
        if(!drawerVisible)recoveryRetry.requestFocus();
        if(previewButton!=null)previewButton.setVisibility(View.GONE);
        if(videoFrame!=null)videoFrame.setVisibility(View.GONE);
    }
    private void focusRecovery(){View target=lastRecoveryAction!=null&&lastRecoveryAction.isAttachedToWindow()?lastRecoveryAction:recoveryRetry;if(target!=null)target.requestFocus();}
    private void enterFull(){
        if(errorPanel!=null){videoRecoveryActions();return;}
        if(player.getMediaItemCount()==0&&!openingChannel&&!source.channels.isEmpty())restoreLastChannel();
        rememberFocus();returnFromFull=route;route="full";render(false);
    }
    private void startFullChannel(JSONObject channel,String origin){
        if(channel==null)return;rememberFocus();returnFromFull=origin;route="full";openingChannel=true;render(false);play(channel,true);
    }
    private void videoRecoveryActions(){showActions("直播预览",new String[]{"重试当前频道","检查直播源","进入全屏"},n->{if(n==0)play(requestedChannel(),true);else if(n==1)navigate("sources");else{rememberFocus();returnFromFull=route;route="full";render(false);}});}
    private void renderFull(){attachVideo(0,0,960,540,true);tag(videoFrame,"full-video");if(source.channels.isEmpty())showVideoEmpty();}
    private void showControls(){
        if(!route.equals("full")||drawerVisible)return;if(controlsVisible){hideControls();return;}controlsVisible=true;renderControls();
    }
    private void renderControls(){
        if(controls!=null)TvUi.detach(controls);controls=new FrameLayout(this);controls.setBackground(ui.glass(TvUi.PANEL));ui.at(page,controls,PlaybackLayout.BAR_X,PlaybackLayout.BAR_Y,PlaybackLayout.BAR_WIDTH,PlaybackLayout.BAR_HEIGHT);
        ui.protectControls(controls);
        JSONObject channel=currentChannel();TextView channelName=ui.text(controls,channel==null?"暂无频道":channel.optString("ChannelNo")+"  "+channel.optString("ChannelName"),15,TvUi.INK,16,32,530,30);channelName.setSingleLine();channelName.setEllipsize(TextUtils.TruncateAt.END);tag(channelName,"control-name");
        View track=new View(this);track.setBackgroundColor(0xff4b5268);ui.at(controls,track,16,15,864,3);
        View fill=new View(this);fill.setBackgroundColor(0xffb9a6ff);tag(fill,"control-progress");ui.at(controls,fill,16,15,0,3);
        aspectAction=ui.quietButton(aspectLabel(),"画面比例",()->selectAspect());ui.at(controls,aspectAction,PlaybackLayout.actionX(0),32,96,30);
        audioAction=ui.quietButton(MediaTracks.value(player,C.TRACK_TYPE_AUDIO),"音轨",()->selectTrack(C.TRACK_TYPE_AUDIO));ui.at(controls,audioAction,PlaybackLayout.actionX(1),32,96,30);
        subtitleAction=ui.quietButton(MediaTracks.value(player,C.TRACK_TYPE_TEXT),"字幕",()->selectTrack(C.TRACK_TYPE_TEXT));ui.at(controls,subtitleAction,PlaybackLayout.actionX(2),32,96,30);
        Button[] actions={aspectAction,audioAction,subtitleAction};for(int i=0;i<3;i++){Button action=actions[i];action.setNextFocusLeftId(actions[Math.max(0,i-1)].getId());action.setNextFocusRightId(actions[Math.min(2,i+1)].getId());action.setNextFocusDownId(action.getId());action.setNextFocusUpId(action.getId());}
        updateControlLabels();aspectAction.requestFocus();resetControlsTimer();TvGlass.apply(this,ui,controls,32,436,896,72);
    }
    private void updateControlLabels(){
        if(controls==null)return;JSONObject channel=currentChannel();TextView name=controls.findViewWithTag("control-name");if(name!=null)name.setText(channel==null?"暂无频道":channel.optString("ChannelNo")+"  "+channel.optString("ChannelName"));
        LiveSourceStore.Program now=nowProgram(channel);float progress=now==null||now.end<=now.start?0:Math.max(0,Math.min(1,(System.currentTimeMillis()-now.start)/(float)(now.end-now.start)));View fill=controls.findViewWithTag("control-progress");if(fill!=null){FrameLayout.LayoutParams params=(FrameLayout.LayoutParams)fill.getLayoutParams();params.width=ui.px(864*progress);fill.setLayoutParams(params);}
        if(aspectAction!=null)ui.value(aspectAction,"画面比例",aspectLabel());if(audioAction!=null)ui.value(audioAction,"音轨",MediaTracks.value(player,C.TRACK_TYPE_AUDIO));if(subtitleAction!=null)ui.value(subtitleAction,"字幕",MediaTracks.value(player,C.TRACK_TYPE_TEXT));
    }
    private String aspectLabel(){int mode=video.getResizeMode();return mode==androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_ZOOM?"填充":mode==androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FILL?"拉伸":"原始";}
    private void selectAspect(){
        int mode=video.getResizeMode();int selected=mode==androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_ZOOM?1:mode==androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FILL?2:0;
        liveOptions("画面比例",new String[]{"原始","填充","拉伸"},null,selected,false,168,168,36,n->{prefs.edit().putString("video_aspect",new String[]{"fit","zoom","stretch"}[n]).putBoolean("fill_video",n==1).apply();applyVideoAspect();updateControlLabels();if(aspectAction!=null)aspectAction.announceForAccessibility(aspectAction.getContentDescription());});
    }
    private void liveOptions(String title,String[] names,String[] details,int selected,boolean below,float min,float max,float rowHeight,TvOptions.Selected action){
        cancelFavoriteGesture();View anchor=getCurrentFocus();handler.removeCallbacks(homeIdle);handler.removeCallbacks(hideControls);handler.removeCallbacks(hideIdleDrawer);
        AlertDialog popup=TvOptions.show(this,ui,anchor,title,names,details,selected,below,min,max,rowHeight,action);dialog=popup;
        popup.setOnKeyListener((d,key,event)->key==KeyEvent.KEYCODE_MENU);
        popup.setOnDismissListener(d->{if(dialog==popup)dialog=null;if(anchor!=null&&anchor.isAttachedToWindow())anchor.requestFocus();lastInputAt=android.os.SystemClock.uptimeMillis();resetIdleTimers();});
    }
    private void resetControlsTimer(){handler.removeCallbacks(hideControls);if(resumed&&controlsVisible&&(dialog==null||!dialog.isShowing()))handler.postDelayed(hideControls,ui.timeout(5000));}
    private void hideControls(){cancelFavoriteGesture();controlsVisible=false;handler.removeCallbacks(hideControls);if(controls!=null){TvUi.detach(controls);controls=null;}if(route.equals("full")&&videoFrame!=null&&!drawerVisible)videoFrame.requestFocus();}
    private void showDrawer(){
        if(!route.equals("full")||drawerVisible)return;
        cancelFavoriteGesture();hideControls();drawerVisible=true;
        int alpha=OverlayContrast.drawerBaseAlpha(prefs.getInt("drawer_transparency",35));int tint=(alpha<<24)|0x00111629;
        drawerShade=new View(this);drawerShade.setBackground(new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,new int[]{0x00111629,tint}));ui.at(page,drawerShade,632,0,48,540);
        drawer=new FrameLayout(this);drawer.setBackgroundColor(tint);ui.at(page,drawer,PlaybackLayout.DRAWER_X,0,PlaybackLayout.DRAWER_WIDTH,540);
        int guardAlpha=OverlayContrast.drawerGuardAlpha(alpha);
        View guard=new View(this);guard.setBackgroundColor((guardAlpha<<24)|0x00101629);ui.at(drawer,guard,12,68,222,441);
        groupButton=makeGroupButton(drawer,16,30,214,31);tag(groupButton,"drawer-group");
        filteredChannels(false);list=makeChannelList(696,76,214,432,true);TvUi.detach(list);ui.at(drawer,list,16,76,214,432);tag(list,"drawer-list");
        groupButton.setOnKeyListener((v,key,event)->{if(event.getAction()!=KeyEvent.ACTION_DOWN)return false;if(key==KeyEvent.KEYCODE_DPAD_DOWN){focusList(false);return true;}if(key==KeyEvent.KEYCODE_DPAD_LEFT){hideDrawer();return true;}return key==KeyEvent.KEYCODE_DPAD_RIGHT||key==KeyEvent.KEYCODE_DPAD_UP;});
        focusList(true);resetIdleTimers();
    }
    private void hideDrawer(){if(!drawerVisible)return;rememberChannelFilter();drawerVisible=false;handler.removeCallbacks(hideIdleDrawer);TvUi.detach(drawer);if(drawerShade!=null)TvUi.detach(drawerShade);drawerShade=null;drawer=null;list=null;groupButton=null;if(errorPanel!=null){focusRecovery();}else if(videoFrame!=null)videoFrame.requestFocus();}
    private Button makeGroupButton(FrameLayout parent,float x,float y,float width,float height){FrameLayout host=new FrameLayout(this);ui.at(parent,host,x,y,width,height);Button button=ui.button(host,"",0,0,width,height,()->chooseGroup());button.setContentDescription("频道分组："+sourceScopeName());ui.text(host,favoritesOnly?"☆":"◇",14,0xffc4b7ff,11,0,20,height);ui.text(host,sourceScopeName(),14,TvUi.INK,34,0,width-65,height);ui.text(host,"⌄",14,TvUi.MUTED,width-25,0,14,height);return button;}
    private void chooseGroup(){
        int selected=favoritesOnly?1:sourceScope.isEmpty()?0:sourceScope.equals(LiveSourceStore.BUILTIN)?2:3;
        liveOptions(null,new String[]{"全部频道","收藏","电视直播源","其他直播源"},null,selected,true,drawerVisible?214:308,drawerVisible?214:308,36,n->applyChannelFilter(n==2?LiveSourceStore.BUILTIN:n==3?LiveFilter.OTHERS:"",n==1));
    }
    private interface DiskTask{void run()throws Exception;}
    private void diskTask(DiskTask task,Runnable success){diskWorker.execute(()->{try{task.run();onUi(success);}catch(Exception e){onUi(()->toast("更改没有保存，请检查存储后重试。"));}});}
    private void renderFavorites(){
        ui.header(page,"我的收藏",source.name+" · "+favoriteCount()+" 个收藏频道",()->back());
        shown=new ArrayList<>();for(JSONObject ch:source.channels)if(store.favorite(source.id,ch.optString("ChannelCode")))shown.add(ch);
        if(shown.isEmpty()){favoriteFocus.remove(source.id);favoriteScroll.remove(source.id);TextView empty=ui.text(page,"暂无收藏频道",20,TvUi.MUTED,50,107,862,343);empty.setGravity(Gravity.CENTER);return;}
        GridView grid=new GridView(this);favoriteGrid=grid;tag(grid,"favorite-grid");grid.setId(View.generateViewId());grid.setNumColumns(ui.largeText()?3:4);grid.setHorizontalSpacing(ui.px(20));grid.setVerticalSpacing(ui.px(16));grid.setStretchMode(GridView.STRETCH_COLUMN_WIDTH);grid.setClipToPadding(false);grid.setPadding(ui.px(2),ui.px(2),ui.px(2),ui.px(2));GradientDrawable selector=new GradientDrawable();selector.setColor(Color.TRANSPARENT);selector.setCornerRadius(ui.px(8));selector.setStroke(ui.px(2),Color.WHITE);grid.setSelector(selector);grid.setDrawSelectorOnTop(true);grid.setLongClickable(false);
        grid.setAdapter(new BaseAdapter(){public int getCount(){return shown.size();}public Object getItem(int n){return shown.get(n);}public long getItemId(int n){return n;}public View getView(int n,View old,ViewGroup parent){TextView card=ui.text(shown.get(n).optString("ChannelName"),18,TvUi.INK);card.setGravity(Gravity.CENTER);card.setTypeface(null,Typeface.BOLD);card.setMaxLines(2);card.setEllipsize(TextUtils.TruncateAt.END);card.setPadding(ui.px(12),ui.px(6),ui.px(12),ui.px(6));android.graphics.drawable.StateListDrawable bg=new android.graphics.drawable.StateListDrawable();GradientDrawable selected=ui.surface(TvUi.ACCENT,true);selected.setStroke(ui.px(.5f),0xff363e5d);bg.addState(new int[]{android.R.attr.state_selected},selected);bg.addState(new int[]{},ui.surface(0xff1b2038,false));card.setBackground(bg);card.setLayoutParams(new AbsListView.LayoutParams(-1,ui.px(73)));return card;}});
        ui.at(page,grid,50,107,862,343);
        grid.setOnItemClickListener((p,v,n,id)->{rememberFocus();showFavoriteActions(shown.get(n));});
        grid.setOnKeyListener((v,key,event)->{if(event.getAction()!=KeyEvent.ACTION_DOWN)return false;int n=grid.getSelectedItemPosition(),columns=grid.getNumColumns();if(key==KeyEvent.KEYCODE_DPAD_UP&&n<columns){rememberFocus();return true;}if(key==KeyEvent.KEYCODE_DPAD_DOWN&&n+columns>=shown.size()){if((n/columns+1)*columns<shown.size())grid.setSelection(shown.size()-1);return true;}if(key==KeyEvent.KEYCODE_DPAD_LEFT&&n%columns==0||key==KeyEvent.KEYCODE_DPAD_RIGHT&&(n%columns==columns-1||n==shown.size()-1))return true;return false;});
        ui.footer(page,"方向键移动 · 确认 / 点击打开操作菜单 · 返回主页");
    }
    private int favoriteCount(){int count=0;for(JSONObject ch:source.channels)if(store.favorite(source.id,ch.optString("ChannelCode")))count++;return count;}
    private void restoreFavoritePosition(){
        if(favoriteGrid==null||shown.isEmpty()){View back=page.findViewWithTag("back");if(back!=null)back.requestFocus();return;}
        GridView grid=favoriteGrid;int selected=0;String code=favoriteFocus.get(source.id);for(int i=0;i<shown.size();i++)if(shown.get(i).optString("ChannelCode").equals(code))selected=i;
        grid.requestFocus();int[] position=favoriteScroll.get(source.id);final int target=selected,top=position==null?0:position[1];
        grid.post(()->{if(grid==favoriteGrid&&route.equals("favorites"))grid.setSelectionFromTop(target,top);});
    }
    private void showFavoriteActions(JSONObject channel){
        View anchor=getCurrentFocus();final String id=source.id;final int index=favoriteGrid==null?0:favoriteGrid.getSelectedItemPosition();
        FrameLayout panel=new FrameLayout(this);panel.setBackground(ui.surface(0xff1b2038,false));ui.title(panel,channel.optString("ChannelName"),16,12,228,24);
        AlertDialog menu=new AlertDialog.Builder(this).create();menu.setView(panel,0,0,0,0);dialog=menu;
        Button playButton=ui.button(panel,"▷  播放",16,41,228,35,()->{menu.dismiss();startFullChannel(channel,"favorites");});
        ui.button(panel,"节目单",16,82,228,35,()->{menu.dismiss();openGuideFor(channel,"favorites");});
        ui.button(panel,"移除",16,123,228,35,()->{menu.dismiss();diskTask(()->{if(store.favorite(id,channel.optString("ChannelCode")))store.toggleFavorite(id,channel.optString("ChannelCode"));},()->{
            if(!source.id.equals(id))return;toast("已移除");if(!route.equals("favorites"))return;
            List<JSONObject> remaining=new ArrayList<>();for(JSONObject c:source.channels)if(store.favorite(id,c.optString("ChannelCode")))remaining.add(c);
            if(!remaining.isEmpty())favoriteFocus.put(id,remaining.get(Math.min(Math.max(0,index),remaining.size()-1)).optString("ChannelCode"));else favoriteFocus.remove(id);
            render(true);
        });});
        menu.setOnDismissListener(d->{if(anchor!=null&&anchor.isAttachedToWindow())anchor.requestFocus();lastInputAt=android.os.SystemClock.uptimeMillis();resetIdleTimers();});
        handler.removeCallbacks(homeIdle);handler.removeCallbacks(hideIdleDrawer);menu.show();
        if(menu.getWindow()!=null){menu.getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT));menu.getWindow().setLayout(ui.px(260),ui.px(176));}
        playButton.requestFocus();
    }
    private void showOnboarding(){route="onboarding";render(false);}
    private void renderOnboarding(){
        ui.title(page,"JellyLive",64,64,400,52);ui.text(page,"电视，由你掌控",14,0xffb6a8ff,64,110,400,32);ui.title(page,"打开，就是好节目。",64,162,600,60);ui.text(page,"正在获取内置直播源，无需先连接媒体库。",16,TvUi.MUTED,64,224,740,44);
        ProgressBar progress=new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);progress.setIndeterminate(true);ui.at(page,progress,64,294,700,12);sourceStatus=ui.text(page,"正在连接并同步频道…",14,TvUi.INK,64,320,720,58);
        ui.button(page,"＋ 手动添加直播源",64,405,190,40,()->editSource(null));ui.button(page,"先进入主页",270,405,160,40,()->home());ui.footer(page,"获取中可以先浏览应用，也可以手动添加直播源。");
    }
    private void refreshSource(String id,boolean explicit){
        if(refreshing.contains(id)||dead)return;final LiveSourceStore.Source snapshot=store.metadata(id);if(snapshot==null)return;
        if(snapshot.builtin()&&config.optString("user_id").isEmpty()){if(explicit)showActions("内置直播源需要自己的机顶盒资料",new String[]{"导入或填写机顶盒配置","添加第三方直播源"},n->{if(n==0)openBoxConfig();else editSource(null);});return;}
        if(snapshot.jellyfin()){refreshJellyfinSource(snapshot,explicit);return;}
        refreshing.add(id);if(sourceStatus!=null&&snapshot.builtin())sourceStatus.setText("正在后台获取频道… 当前播放继续。");final BestvApi activeApi=backgroundApi;
        worker.execute(()->{try{
            List<JSONObject> loaded=snapshot.builtin()?activeApi.channels():loadM3u(snapshot);
            LiveSourceStore.Source latest=store.metadata(id);if(latest==null||!latest.url.equals(snapshot.url)||!latest.enabled||(snapshot.builtin()&&activeApi!=backgroundApi))throw new IOException("来源已更改");
            store.update(id,loaded);final LiveSourceStore.Source ready=store.get(id);
            onUi(()->{
                refreshing.remove(id);boolean current=id.equals(source.id);if(current){source=ready;if(isLiveRoute()){boolean wasEmpty=player.getMediaItemCount()==0&&!openingChannel;updateChannelCacheOnScreen();if(wasEmpty&&prefs.getBoolean("auto_play",true))restoreLastChannel();}}
                if(!current&&isLiveRoute())updateChannelCacheOnScreen();if(route.equals("onboarding")&&current)home();if(sourceStatus!=null&&snapshot.builtin())sourceStatus.setText(sourceSummary(LiveSourceStore.BUILTIN));updateSourceRows();if(explicit)toast("频道已更新，当前播放未中断。");
            });
        }catch(Exception e){onUi(()->{
            refreshing.remove(id);String reason=networkReason(e);if(sourceStatus!=null&&snapshot.builtin())sourceStatus.setText("更新失败："+reason+"；保留原频道。");
            android.util.Log.w("JellyLive","Source refresh failed: "+e.getClass().getSimpleName());
            if(route.equals("onboarding")&&id.equals(source.id))showSourceError(reason,source);else toast(snapshot.name+"更新失败："+reason+"；已保留缓存和当前播放。");
        });}});
    }
    private void refreshJellyfinSource(LiveSourceStore.Source snapshot,boolean explicit){
        refreshing.add(snapshot.id);
        JellyLiveBridge.loadSource(new JellyLiveBridge.Callback<JellyLiveBridge.Snapshot>(){
            public void success(JellyLiveBridge.Snapshot value){
                if(dead)return;refreshing.remove(snapshot.id);
                if(!snapshot.url.equals(value.getServerKey())){toast("当前连接与保存的 Jellyfin 来源不同，请重新选择来源。");return;}
                saveJellyfinSnapshot(value,explicit,snapshot);
            }
            public void failure(String reason){if(dead)return;refreshing.remove(snapshot.id);toast(reason+"；保留已缓存频道和当前播放。");}
        });
    }
    private void syncJellyfinSource(boolean explicit){
        if(syncingJellyfin)return;
        if(!JellyLiveBridge.isConnected()){if(explicit)openLibrary(true);return;}
        syncingJellyfin=true;final LiveSourceStore.Source expected=store.metadata("jellyfin-"+JellyLiveBridge.serverKey());
        JellyLiveBridge.loadSource(new JellyLiveBridge.Callback<JellyLiveBridge.Snapshot>(){
            public void success(JellyLiveBridge.Snapshot value){if(dead)return;syncingJellyfin=false;saveJellyfinSnapshot(value,explicit,expected);}
            public void failure(String reason){if(dead)return;syncingJellyfin=false;toast(reason);}
        });
    }
    private void saveJellyfinSnapshot(JellyLiveBridge.Snapshot value,boolean explicit,LiveSourceStore.Source expected){
        final String id="jellyfin-"+value.getServerKey();
        diskWorker.execute(()->{try{
            LiveSourceStore.Source current=store.metadata(id);
            if(expected!=null&&(current==null||!current.url.equals(expected.url)||current.enabled!=expected.enabled))return;
            if(expected==null&&!explicit&&current==null)return;
            LiveSourceStore.Source s=new LiveSourceStore.Source(id,value.getServerName()+" · Jellyfin 直播",value.getServerKey(),"",current==null||current.enabled);s.channels=value.getChannels();s.updated=System.currentTimeMillis();
            if(s.channels.isEmpty()){onUi(()->toast("服务器没有提供可访问的直播频道；未添加空来源，已有缓存保留。"));return;}
            store.save(s);
            final LiveSourceStore.Source ready=store.get(id);
            onUi(()->{
                if(id.equals(source.id)&&ready!=null){source=ready;updateChannelCacheOnScreen();if(source.channels.isEmpty()){if(explicit)toast("服务器没有提供直播频道，请选择其他来源。");}else if(resumed&&started&&isLiveRoute()&&player.getMediaItemCount()==0)restoreLastChannel();}
                if(explicit)toast(s.channels.isEmpty()?"服务器没有提供直播频道，独立直播仍可使用。":"已获取 "+s.channels.size()+" 个 Jellyfin 直播频道，已加入频道分组。");
                // An automatic refresh changes data only; never rebuild a page or move the user's focus.
                if(isLiveRoute())updateChannelCacheOnScreen();updateSourceRows();
            });
        }catch(Exception e){onUi(()->toast("Jellyfin 直播来源更新失败，保留原频道。"));}});
    }
    private List<JSONObject> loadM3u(LiveSourceStore.Source s)throws Exception{LiveSourceStore.Download d=LiveSourceStore.download(s.url,8*1024*1024);return LiveSourceStore.parseM3u(d.text,d.url);}
    private String networkReason(Exception e){if(e instanceof java.net.UnknownHostException)return "无法解析服务器地址";if(e instanceof java.net.SocketTimeoutException)return "连接超时";if(e instanceof javax.net.ssl.SSLException)return "安全连接失败";if(e instanceof java.net.ConnectException)return "无法连接服务器";String m=e.getMessage();if(m!=null&&(m.startsWith("来源请求失败 HTTP ")||m.startsWith("内容不是")||m.startsWith("M3U 中")||m.startsWith("这是视频流")||m.startsWith("后台返回错误码 ")||m.startsWith("无法保存直播配置")||m.startsWith("来源缓存过大")||m.startsWith("响应内容过大")||m.startsWith("来源内容过大")||m.startsWith("频道数量超过")||m.startsWith("节目数量超过")))return m;return "网络、来源格式或账号授权不可用";}
    private void updateChannelCacheOnScreen(){
        if(list==null||!isLiveRoute())return;
        final ListView target=list;final int generation=screenGeneration;
        Runnable apply=new Runnable(){public void run(){
            if(dead||target!=list||generation!=screenGeneration)return;
            if(android.os.SystemClock.uptimeMillis()-lastInputAt<200){handler.postDelayed(this,200);return;}
            rememberChannelFilter();homeFocusFromInput=false;suppressPreviewSelection=true;filteredChannels(false);((BaseAdapter)target.getAdapter()).notifyDataSetChanged();
            if(!shown.isEmpty()){List<String> keys=new ArrayList<>();for(JSONObject channel:shown)keys.add(channelKey(channel));int at=LiveFilter.focus(keys,channelFocus.get(filterKey()),LiveFilter.key(playingSource,playingCode));int[] position=channelScroll.get(filterKey());target.setSelectionFromTop(at,position==null?0:position[1]);}
            target.post(()->suppressPreviewSelection=false);
        }};handler.post(apply);
    }
    private String sourceSummary(String id){try{LiveSourceStore.Source s=store.metadata(id);return store.channelCount(id)+" 个频道 · "+(s.updated==0?"尚未成功更新":new SimpleDateFormat("MM-dd HH:mm",Locale.ROOT).format(new Date(s.updated))+" 更新")+(id.equals(source.id)?" · 正在使用":"");}catch(Exception e){return "来源不可用";}}
    private void showSourceError(String reason,LiveSourceStore.Source s){sourceErrorReason=reason;route="source-error";render(false);}
    private void renderSourceError(){
        LiveSourceStore.Source s=source;String reason=sourceErrorReason;ui.header(page,"直播源暂时无法更新","",()->home());ui.text(page,"△",40,0xffc5b3ff,64,124,80,60);ui.title(page,s.channels.isEmpty()?"连接没有成功，可以添加自己的来源。":"连接没有成功，先用上次的频道。",64,202,830,60);ui.text(page,reason+"\n"+(s.channels.isEmpty()?"当前没有成功缓存。":sourceSummary(s.id)),15,TvUi.MUTED,64,269,810,62);
        Button retry=ui.button(page,"重新获取",64,365,140,40,()->{showOnboarding();refreshSource(s.id,true);});tag(retry,"source-retry");if(!s.channels.isEmpty())ui.button(page,"使用缓存频道",222,365,160,40,()->home());ui.button(page,"手动添加来源",s.channels.isEmpty()?222:400,365,165,40,()->editSource(null));ui.footer(page,"直播与媒体库相互独立；返回可先进入主页。");retry.requestFocus();
    }
    private void renderSources(){
        ui.header(page,"直播源","管理频道来源，已启用的频道会显示在分组列表中",()->back());
        tag(ui.button(page,"＋ 添加直播源",732,65,178,32,()->chooseSourceType()),"source-add");ui.heading(page,"电视直播源",15,48,102,600,28);
        FrameLayout builtin=new FrameLayout(this);builtin.setBackground(ui.surface(TvUi.PANEL,false));ui.at(page,builtin,48,134,864,90);
        ui.text(builtin,"◇",30,0xffc7b8ff,16,24,36,36);ui.heading(builtin,"内置直播源",17,66,19,400,28);sourceStatus=ui.text(builtin,sourceSummary(LiveSourceStore.BUILTIN),13,TvUi.MUTED,66,51,400,24);
        tag(ui.button(builtin,"刷新频道",547,26,122,38,()->refreshSource(LiveSourceStore.BUILTIN,true)),"source-refresh");ui.button(builtin,"机顶盒配置",682,26,160,38,()->openBoxConfig());
        ui.heading(page,"第三方直播源",15,48,236,126,28);tag(ui.text(page,"",12,TvUi.MUTED,175,236,300,28),"source-count");
        ScrollView scroll=new ScrollView(this);LinearLayout rows=new LinearLayout(this);rows.setOrientation(LinearLayout.VERTICAL);scroll.addView(rows);ui.at(page,scroll,48,274,864,176);sourceRowsHost=rows;updateSourceRows();
    }
    private void chooseSourceType(){liveOptions(null,new String[]{"M3U 直播源","Jellyfin 服务器"},new String[]{"添加播放列表与可选节目单","读取服务端已配置的直播频道"},-1,true,300,300,45,n->{if(n==0)editSource(null);else{pendingJellyfinAdd=!JellyLiveBridge.isConnected();syncJellyfinSource(true);}});}
    private void updateSourceRows(){
        if(sourceRowsHost==null)return;Set<String> visible=new HashSet<>();
        for(LiveSourceStore.Source sourceEntry:store.allMetadata())if(!sourceEntry.builtin()&&(!sourceEntry.jellyfin()||store.channelCount(sourceEntry.id)>0)){
            String key="source-"+sourceEntry.id;visible.add(key);FrameLayout card=sourceRowsHost.findViewWithTag(key);
            if(card==null){card=new FrameLayout(this);tag(card,key);card.setBackground(ui.surface(TvUi.PANEL,false));LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(-1,ui.px(66));params.bottomMargin=ui.px(10);sourceRowsHost.addView(card,params);
                ui.text(card,"◇",28,0xffbdafd9,16,15,36,36);tag(ui.heading(card,"",16,66,8,430,25),"source-title");tag(ui.text(card,"",12,TvUi.MUTED,66,36,430,22),"source-summary");tag(ui.text(card,"",12,0xffb6a8ff,501,21,78,22),"source-kind");tag(ui.text(card,"",12,0xffa5dbbf,596,21,78,22),"source-enabled");tag(ui.button(card,"管理",750,14,96,38,()->{}),key+"-manage");}
            TextView name=card.findViewWithTag("source-title"),summary=card.findViewWithTag("source-summary"),kind=card.findViewWithTag("source-kind"),enabled=card.findViewWithTag("source-enabled");
            name.setText(sourceEntry.name);name.setSingleLine();name.setEllipsize(TextUtils.TruncateAt.END);summary.setText(sourceSummary(sourceEntry.id));summary.setSingleLine();summary.setEllipsize(TextUtils.TruncateAt.END);kind.setText(sourceEntry.jellyfin()?"Jellyfin":"M3U");enabled.setText(sourceEntry.enabled?"已启用":"已停用");Button manage=card.findViewWithTag(key+"-manage");manage.setOnClickListener(v->sourceActions(sourceEntry));
        }
        for(int i=sourceRowsHost.getChildCount()-1;i>=0;i--){View child=sourceRowsHost.getChildAt(i);if(!visible.contains(String.valueOf(child.getTag())))sourceRowsHost.removeViewAt(i);}
        TextView count=page.findViewWithTag("source-count");if(count!=null)count.setText(visible.size()+" 个来源");
        if(visible.isEmpty()){FrameLayout empty=new FrameLayout(this);empty.setBackground(ui.surface(TvUi.PANEL,false));tag(empty,"empty");sourceRowsHost.addView(empty,new LinearLayout.LayoutParams(-1,ui.px(143)));ui.text(empty,"◇",31,0xff82779c,414,15,36,36).setGravity(Gravity.CENTER);ui.text(empty,"暂无第三方直播源",16,TvUi.INK,0,57,864,30).setGravity(Gravity.CENTER);ui.text(empty,"支持 M3U，以及提供直播频道的 Jellyfin 服务器",13,TvUi.MUTED,0,88,864,30).setGravity(Gravity.CENTER);}
    }
    private void sourceActions(LiveSourceStore.Source sourceEntry){showActions(sourceEntry.name,new String[]{"刷新频道","编辑来源",sourceEntry.enabled?"停用来源":"启用来源","删除来源"},n->{if(n==0)refreshSource(sourceEntry.id,true);else if(n==1){if(sourceEntry.jellyfin())openLibrary(true);else editSource(sourceEntry);}else if(n==2)changeSourceAvailability(sourceEntry,false);else confirmDelete(sourceEntry);});}
    private void changeSourceAvailability(LiveSourceStore.Source selected,boolean deleting){
        final boolean active=selected.id.equals(source.id);
        String replacement=null;
        if(active){for(LiveSourceStore.Source candidate:store.allMetadata())if(!candidate.id.equals(selected.id)&&candidate.enabled){replacement=candidate.id;break;}if(replacement==null){toast("请先添加其他可用来源。");return;}}
        final String fallback=replacement;
        diskWorker.execute(()->{try{
            if(active)store.activate(fallback);
            try {if(deleting)store.delete(selected.id);else store.setEnabled(selected.id,!selected.enabled);}
            catch(Exception error){if(active)store.activate(selected.id);throw error;}
            final LiveSourceStore.Source ready=store.current();
            onUi(()->{
                if(active){source=ready;resumeSource=ready.id;resumeCode=store.last(ready.id);requestedCode="";playingCode="";playingSource="";failedChannels.clear();liveErrorTitle="";liveErrorDetail="";}
                if(route.equals("sources")){rememberFocus();render(true);}toast(deleting?"来源已删除":"来源状态已更新");
            });
        }catch(Exception error){onUi(()->toast("来源更改没有保存，请重试。"));}});
    }
    private void confirmDelete(LiveSourceStore.Source s){View anchor=getCurrentFocus();dialog=new AlertDialog.Builder(this).setTitle("删除直播源？").setMessage(s.name+"\n该来源的频道、收藏和观看记录将被移除。").setNegativeButton("取消",null).setPositiveButton("删除",(d,w)->changeSourceAvailability(s,true)).create();dialog.setOnShowListener(d->dialog.getButton(AlertDialog.BUTTON_NEGATIVE).requestFocus());dialog.setOnDismissListener(d->{if(anchor!=null)anchor.requestFocus();});dialog.show();}
    private void editSource(LiveSourceStore.Source s){editor=new EditorDraft();if(s!=null){editor.id=s.id;editor.name=s.name;editor.url=s.url;editor.epg=s.epg;}navigate("editor");}
    private void captureEditor(){if(editor!=null&&sourceName!=null){editor.name=sourceName.getText().toString();editor.url=sourceUrl.getText().toString();editor.epg=sourceEpg.getText().toString();}}
    private void renderEditor(){
        if(editor==null)editor=new EditorDraft();ui.header(page,editor.id.isEmpty()?"添加第三方直播源":"编辑第三方直播源","输入来源信息，检测成功后保存到频道列表",()->back());
        ui.text(page,"来源名称",14,TvUi.MUTED,64,99,700,24);sourceName=ui.input(page,"填写来源名称",editor.name,64,125,688,false);tag(sourceName,"editor-name");
        ui.text(page,"M3U 播放列表地址",14,TvUi.MUTED,64,181,700,24);sourceUrl=ui.input(page,"HTTP(S) 播放列表地址",editor.url,64,207,688,false);sourceUrl.setInputType(android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_VARIATION_URI);tag(sourceUrl,"editor-url");
        ui.text(page,"XMLTV 节目单地址（可选）",14,TvUi.MUTED,64,263,700,24);sourceEpg=ui.input(page,"可留空",editor.epg,64,289,688,false);sourceEpg.setInputType(android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_VARIATION_URI);tag(sourceEpg,"editor-epg");
        editorStatus=ui.text(page,editor.status,13,TvUi.MUTED,64,348,830,30);detectButton=ui.button(page,"检测来源",64,400,125,40,()->testEditor(false));tag(detectButton,"editor-detect");saveButton=ui.button(page,"保存来源",204,400,125,40,()->testEditor(true));tag(saveButton,"editor-save");ui.button(page,"取消",344,400,90,40,()->back());setEditorBusy(editor.busy);ui.footer(page,"确认字段打开电视键盘 · 检测失败保留输入 · 未填写节目单也可观看");
    }
    private void setEditorBusy(boolean busy){if(detectButton!=null){detectButton.setEnabled(!busy);saveButton.setEnabled(!busy);sourceName.setEnabled(!busy);sourceUrl.setEnabled(!busy);sourceEpg.setEnabled(!busy);}}
    private void testEditor(boolean save){
        captureEditor();final EditorDraft draft=editor;if(draft.busy)return;draft.name=draft.name.trim();draft.url=draft.url.trim();draft.epg=draft.epg.trim();try{if(draft.name.isEmpty())throw new IOException("请填写来源名称");LiveSourceStore.validateUrl(draft.url);if(!draft.epg.isEmpty())LiveSourceStore.validateUrl(draft.epg);}catch(Exception e){editorStatus.setText(e.getMessage());return;}
        draft.busy=true;setEditorBusy(true);draft.status=save?"正在检测并保存…":"正在检测来源…";editorStatus.setText(draft.status);
        worker.execute(()->{try{String signature=draft.name+"\n"+draft.url+"\n"+draft.epg;List<JSONObject> channels=draft.tested!=null&&draft.signature.equals(signature)?draft.tested:loadM3u(new LiveSourceStore.Source("",draft.name,draft.url,draft.epg,true));
                String epgNote="";if(!draft.epg.isEmpty())try{LiveSourceStore.Download epg=LiveSourceStore.download(draft.epg,24*1024*1024);List<LiveSourceStore.Program> programs=LiveSourceStore.parseXmltv(epg.text);epgNote=programs.isEmpty()?"；节目单没有可用节目":"；节目单 "+programs.size()+" 条";}catch(Exception e){epgNote="；节目单暂不可用，直播仍可保存";}
                draft.tested=channels;draft.signature=signature;
                if(save){String id=draft.id.isEmpty()?UUID.randomUUID().toString():draft.id;LiveSourceStore.Source s=new LiveSourceStore.Source(id,draft.name,draft.url,draft.epg,true);s.channels=channels;s.updated=System.currentTimeMillis();store.save(s);draft.id=id;}
                final LiveSourceStore.Source savedCurrent=save?store.current():null;final String note=epgNote;onUi(()->{draft.busy=false;draft.status="检测成功："+channels.size()+" 个频道"+note;if(editor==draft&&route.equals("editor")){setEditorBusy(false);editorStatus.setText(draft.status);if(save){if(savedCurrent!=null&&savedCurrent.id.equals(source.id))source=savedCurrent;editor=null;if(!backStack.isEmpty()&&backStack.peek().equals("sources"))backStack.pop();route="sources";render(true);toast("来源已保存，已启用频道可在分组中查看。");}else detectButton.requestFocus();}});
            }catch(Exception e){onUi(()->{draft.busy=false;draft.status="检测或保存失败："+networkReason(e)+"。输入已保留。";if(editor==draft&&route.equals("editor")){setEditorBusy(false);editorStatus.setText(draft.status);detectButton.requestFocus();}});}
        });
    }
    private void releaseJellyfinPlayback(){
        if(source!=null&&source.jellyfin()){openingChannel=false;playGeneration++;JellyLiveBridge.releaseLiveStreams();if(player!=null){player.stop();player.clearMediaItems();}playingCode="";playingSource="";}
    }
    private void openBoxConfig(){suspendPlayback();startActivity(new Intent(this,BoxConfigActivity.class));}
    private void openLibrary(boolean connect){openLibrary(connect,null,null);}
    private void openLibrary(boolean connect,String itemId,String query){rememberFocus();releaseLivePlayerForLibrary();try{Intent i=new Intent().setClassName(this,"org.jellyfin.androidtv.bestv.JellyLibraryActivity");i.putExtra("connect",connect);if(itemId!=null)i.putExtra("item_id",itemId);if(query!=null)i.putExtra("search_query",query);startActivity(i);}catch(Exception e){toast("媒体库暂时无法打开");if(playerReleasedForLibrary)createLivePlayer();if(isLiveRoute())restoreLastChannel();}}
    private static final String[] SETTINGS_CATEGORIES={"播放设置","显示与操作","Jellyfin 连接","内置源配置","关于 JellyLive"};
    private int settingsCategoryIndex(){for(int i=0;i<SETTINGS_CATEGORIES.length;i++)if(SETTINGS_CATEGORIES[i].equals(settingsCategory))return i;return 0;}
    private void rememberSettingsFocus(){if(settingsPanel==null||!settingsPanel.hasFocus())return;View focused=getCurrentFocus();if(focused!=null&&focused.getTag() instanceof String)settingsFocus.put((String)settingsPanel.getTag(),(String)focused.getTag());}
    private void focusSettingsPanel(){
        if(settingsPanel==null)return;String key=settingsFocus.get(settingsCategory);View target=key==null?null:settingsPanel.findViewWithTag(key);
        if(target==null||!target.isFocusable()||!target.isEnabled()||!target.isShown())target=firstFocusable(settingsPanel);
        if(target!=null)target.requestFocus();
    }
    private void selectSettingsCategory(String category){
        if(category.equals(settingsCategory)&&settingsPanel!=null)return;rememberSettingsFocus();settingsCategory=category;renderSettingsPanel(false);
        for(int i=0;i<settingsTabs.size();i++)settingsTabs.get(i).setBackground(ui.focusBackground(SETTINGS_CATEGORIES[i].equals(category)?0xff36305c:TvUi.TILE));
    }
    private void renderSettings(){
        ui.header(page,"设置","让观看更适合你的电视",()->back());
        for(int i=0;i<SETTINGS_CATEGORIES.length;i++){
            String category=SETTINGS_CATEGORIES[i];Button tab=ui.button(page,category,48,109+i*56,210,38,()->selectSettingsCategory(category));tag(tab,"settings-"+i);settingsTabs.add(tab);
            tab.setBackground(ui.focusBackground(category.equals(settingsCategory)?0xff36305c:TvUi.TILE));tab.setOnFocusChangeListener((v,focused)->{if(focused)selectSettingsCategory(category);});
        }
        for(int i=0;i<settingsTabs.size();i++){Button tab=settingsTabs.get(i);tab.setNextFocusUpId(settingsTabs.get(Math.max(0,i-1)).getId());tab.setNextFocusDownId(settingsTabs.get(Math.min(settingsTabs.size()-1,i+1)).getId());tab.setNextFocusLeftId(tab.getId());tab.setNextFocusRightId(tab.getId());}
        renderSettingsPanel(false);ui.footer(page,"方向键选择本页分类与右侧选项 · 返回恢复主页位置");
    }
    private void renderSettingsPanel(boolean restore){
        rememberSettingsFocus();if(settingsPanel!=null)TvUi.detach(settingsPanel);
        FrameLayout panel=new FrameLayout(this);settingsPanel=panel;tag(panel,settingsCategory);panel.setBackground(ui.surface(TvUi.PANEL,false));ui.at(page,panel,265,109,647,ui.largeText()?355:341);ui.heading(panel,settingsCategory,18,22,16,594,ui.largeText()?42:34);
        if(settingsCategory.equals("播放设置")){
            settingRow(panel,"启动行为",prefs.getBoolean("auto_play",true)?"主页自动续播":"主页不自动播放",48,()->showActions("启动行为",new String[]{"主页自动续播","主页不自动播放"},n->{prefs.edit().putBoolean("auto_play",n==0).apply();renderSettingsPanel(true);}));
            settingRow(panel,"画面比例",aspectLabel(),88,()->showActions("画面比例",new String[]{"原始","填充","拉伸"},n->{prefs.edit().putString("video_aspect",new String[]{"fit","zoom","stretch"}[n]).putBoolean("fill_video",n==1).apply();applyVideoAspect();renderSettingsPanel(true);}));
            settingRow(panel,"默认音轨",languageLabel(prefs.getString("preferred_audio","auto")),128,()->showActions("默认音轨",new String[]{"跟随媒体","中文","英语"},n->{prefs.edit().putString("preferred_audio",new String[]{"auto","zh","en"}[n]).apply();applyPlayerSettings();renderSettingsPanel(true);}));
            settingRow(panel,"默认字幕",languageLabel(prefs.getString("preferred_subtitle","off")),168,()->showActions("默认字幕",new String[]{"关闭","中文","英语"},n->{prefs.edit().putString("preferred_subtitle",new String[]{"off","zh","en"}[n]).apply();applyPlayerSettings();renderSettingsPanel(true);}));
            ui.text(panel,"解码方式",14,TvUi.INK,22,ui.largeText()?220:208,300,ui.largeText()?42:38);ui.text(panel,"自动",14,0xffc5b8ff,314,ui.largeText()?220:208,280,ui.largeText()?42:38);
            settingRow(panel,"剧集自动播放下一集",prefs.getBoolean("auto_next_episode",true)?"开启":"关闭",288,()->showActions("剧集自动播放下集",new String[]{"开启","关闭"},n->{prefs.edit().putBoolean("auto_next_episode",n==0).apply();renderSettingsPanel(true);}));
            settingRow(panel,"侧栏透明度",prefs.getInt("drawer_transparency",35)+"%",248,()->{String[] values=new String[17];for(int i=0;i<values.length;i++)values[i]=(i*5)+"%";showActions("频道侧栏透明度",values,n->{prefs.edit().putInt("drawer_transparency",n*5).apply();renderSettingsPanel(true);});});
        }else if(settingsCategory.equals("显示与操作")){
            settingRow(panel,"字号",prefs.getBoolean("large_text",false)?"大字号":"普通",59,()->showActions("字号",new String[]{"普通","大字号"},n->{prefs.edit().putBoolean("large_text",n==1).apply();ui=new TvUi(this);render(true);}));
            String[][] info={{"焦点样式","白色描边 · 紫色背景"},{"首页频道","上下移动即切换预览"},{"收藏操作","点击打开操作菜单"}};
            for(int i=0;i<info.length;i++){ui.text(panel,info[i][0],14,TvUi.INK,22,109+i*50,280,38);ui.text(panel,info[i][1],14,0xffc5b8ff,314,109+i*50,280,38);}
        }else if(settingsCategory.equals("Jellyfin 连接")){ui.text(panel,"媒体库按需连接，与独立直播相互独立。\n连接、登录、切换账号和媒体播放在媒体库中完成。",15,TvUi.MUTED,22,65,594,100);ui.button(panel,"连接或管理 Jellyfin",22,197,220,40,()->openLibrary(true));}
        else if(settingsCategory.equals("内置源配置")){ui.text(panel,"已有内置或保存的配置无需重新填写。\n支持机顶盒 ADB 只读导入、手动修改及 JSON 文件导入导出。",15,TvUi.MUTED,22,65,594,110);ui.button(panel,"打开机顶盒配置",22,197,220,40,()->openBoxConfig());}
        else{ui.text(panel,"JellyLive "+org.jellyfin.androidtv.BuildConfig.VERSION_NAME+"\n基于 Jellyfin Android TV / Media3，GPL-2.0。\n直播来源、授权与可用性以真实服务为准。\n不包含回看、时移或录制。",15,TvUi.MUTED,22,65,594,160);}
        for(int i=0;i<panel.getChildCount();i++){View child=panel.getChildAt(i);if(child.isFocusable()&&child.getTag()==null)tag(child,"setting-action-"+i);}
        linkSettingsOptions(panel);
        if(restore){String category=settingsCategory;panel.post(()->{if(panel==settingsPanel&&route.equals("settings")&&category.equals(settingsCategory))focusSettingsPanel();});}
    }
    /** Keep vertical motion within the current column, including gaps occupied by static text. */
    private void linkSettingsOptions(FrameLayout panel){
        List<View> actions=new ArrayList<>();collectSettingsActions(panel,actions);
        actions.sort((a,b)->Integer.compare(settingsTop(a,panel),settingsTop(b,panel)));
        int tab=settingsTabs.get(settingsCategoryIndex()).getId();
        for(int i=0;i<actions.size();i++){View action=actions.get(i);action.setNextFocusUpId(actions.get(Math.max(0,i-1)).getId());action.setNextFocusDownId(actions.get(Math.min(actions.size()-1,i+1)).getId());action.setNextFocusLeftId(tab);action.setNextFocusRightId(action.getId());}
    }
    private void collectSettingsActions(ViewGroup group,List<View> actions){
        for(int i=0;i<group.getChildCount();i++){View child=group.getChildAt(i);if(child.getVisibility()!=View.VISIBLE||!child.isEnabled())continue;if(child.isFocusable()){if(child.getId()==View.NO_ID)child.setId(View.generateViewId());actions.add(child);}else if(child instanceof ViewGroup)collectSettingsActions((ViewGroup)child,actions);}
    }
    private int settingsTop(View view,ViewGroup panel){int top=0;for(View current=view;current!=panel&&current!=null;current=current.getParent() instanceof View?(View)current.getParent():null){ViewGroup.LayoutParams params=current.getLayoutParams();if(params instanceof ViewGroup.MarginLayoutParams)top+=((ViewGroup.MarginLayoutParams)params).topMargin;}return top;}
    private void settingRow(FrameLayout panel,String name,String value,int y,Runnable action){
        FrameLayout row=new FrameLayout(this);ui.at(panel,row,22,y+(ui.largeText()?12:0),594,ui.largeText()?42:38);Button button=ui.button(row,"",0,0,594,ui.largeText()?42:38,action);tag(button,"setting-option-"+y);button.setContentDescription(name+"，"+value);button.setOnFocusChangeListener((v,focused)->{if(focused)settingsFocus.put(settingsCategory,(String)v.getTag());});
        TextView title=ui.text(row,name,14,TvUi.INK,0,0,300,ui.largeText()?42:38);title.setSingleLine();title.setEllipsize(TextUtils.TruncateAt.END);TextView current=ui.text(row,value,14,0xffc5b8ff,300,0,262,ui.largeText()?42:38);current.setSingleLine();current.setEllipsize(TextUtils.TruncateAt.END);ui.text(row,"›",14,TvUi.MUTED,574,0,20,ui.largeText()?42:38);
    }
    private String languageLabel(String code){return code.equals("zh")?"中文":code.equals("en")?"英语":code.equals("off")?"关闭":"跟随媒体";}
    private void applyVideoAspect(){if(video==null)return;String aspect=prefs.getString("video_aspect",prefs.getBoolean("fill_video",false)?"zoom":"fit");video.setResizeMode(aspect.equals("zoom")?androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_ZOOM:aspect.equals("stretch")?androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FILL:androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT);}
    private void applyPlayerSettings(){
        applyVideoAspect();
        if(player!=null){String audio=prefs.getString("preferred_audio","auto"),subtitle=prefs.getString("preferred_subtitle","off");player.setTrackSelectionParameters(player.getTrackSelectionParameters().buildUpon().setPreferredAudioLanguage(audio.equals("auto")?null:audio).setPreferredTextLanguage(subtitle.equals("off")?null:subtitle).setTrackTypeDisabled(C.TRACK_TYPE_TEXT,subtitle.equals("off")).build());}
    }
    private void selectTrack(int type){
        List<MediaTracks.Choice> choices=MediaTracks.choices(player,type);boolean text=type==C.TRACK_TYPE_TEXT;int offset=text?1:0;
        if(choices.isEmpty()){toast("当前频道尚无可选"+(text?"字幕":"音轨"));return;}
        String[] names=new String[choices.size()+offset],details=new String[names.length];int selected=text&&player.getTrackSelectionParameters().disabledTrackTypes.contains(type)?0:-1;
        if(text){names[0]="关闭";details[0]="不显示字幕";}
        for(int i=0;i<choices.size();i++){MediaTracks.Choice choice=choices.get(i);names[i+offset]=choice.name;details[i+offset]=choice.detail;if(choice.selected()&&!(text&&player.getTrackSelectionParameters().disabledTrackTypes.contains(type)))selected=i+offset;}
        liveOptions(text?"字幕":"音轨",names,details,selected,false,140,260,29,n->{if(!MediaTracks.select(player,type,n<offset?null:choices.get(n-offset))){toast("轨道已变化，请重新选择");return;}updateControlLabels();Button action=text?subtitleAction:audioAction;if(action!=null)action.announceForAccessibility(action.getContentDescription());});
    }
    private void showActions(String title,String[] names,java.util.function.IntConsumer action){
        cancelFavoriteGesture();
        View anchor=getCurrentFocus();if(dialog!=null&&dialog.isShowing())dialog.dismiss();dialog=new AlertDialog.Builder(this).setTitle(title).setItems(names,(d,n)->action.accept(n)).setNegativeButton("取消",null).create();dialog.setOnKeyListener((d,key,event)->isLiveRoute()&&key==KeyEvent.KEYCODE_MENU);dialog.setOnDismissListener(d->{if(anchor!=null&&anchor.isAttachedToWindow())anchor.requestFocus();lastInputAt=android.os.SystemClock.uptimeMillis();resetIdleTimers();});handler.removeCallbacks(hideControls);handler.removeCallbacks(hideIdleDrawer);handler.removeCallbacks(homeIdle);dialog.show();
    }
    private void exitDialog(){View anchor=getCurrentFocus();dialog=new AlertDialog.Builder(this).setTitle("退出 JellyLive？").setNegativeButton("继续观看",null).setPositiveButton("退出",(d,w)->finish()).create();dialog.setOnShowListener(d->dialog.getButton(AlertDialog.BUTTON_NEGATIVE).requestFocus());dialog.setOnDismissListener(d->{if(anchor!=null)anchor.requestFocus();lastInputAt=android.os.SystemClock.uptimeMillis();resetIdleTimers();});handler.removeCallbacks(homeIdle);dialog.show();}
    private List<LiveSourceStore.Program> channelPrograms(JSONObject ch){if(ch==null)return Collections.emptyList();String id=owner(ch).id+"/"+ch.optString("ChannelCode");List<LiveSourceStore.Program> p=schedules.get(id);return p==null?Collections.emptyList():p;}
    private LiveSourceStore.Program nowProgram(JSONObject ch){long now=System.currentTimeMillis();for(LiveSourceStore.Program p:channelPrograms(ch))if(p.current(now))return p;return null;}
    private String programmeText(JSONObject ch){LiveSourceStore.Program now=nowProgram(ch);return now==null?"暂无节目信息":now.title;}
    private String nextProgram(JSONObject ch){LiveSourceStore.Program now=nowProgram(ch);if(now!=null)for(LiveSourceStore.Program p:channelPrograms(ch))if(p.start>=now.end)return "    下一节目  "+p.title;return "";}
    private String time(long n){return new SimpleDateFormat("HH:mm",Locale.ROOT).format(new Date(n));}
    private void ensureEpg(JSONObject ch){
        if(ch==null||dead||owner(ch).jellyfin())return;final LiveSourceStore.Source epgSource=owner(ch);final String id=epgSource.id,code=ch.optString("ChannelCode"),key=id+"/"+code;final boolean builtin=epgSource.builtin();final String epg=epgSource.epg;final BestvApi activeApi=backgroundApi;
        String pendingKey=builtin?key:id+"/xmltv";if((schedules.containsKey(key)&&System.currentTimeMillis()-scheduleAt.getOrDefault(key,0L)<1200000L)||epgPending.contains(pendingKey)||(!builtin&&epg.isEmpty()))return;epgPending.add(pendingKey);
        final ConcurrentMap<String,List<LiveSourceStore.Program>> data=schedules;final ConcurrentMap<String,Long> ages=scheduleAt;final List<JSONObject> channelSnapshot=epgSource.channels;
        worker.execute(()->{try{Map<String,List<LiveSourceStore.Program>> loaded=new HashMap<>();if(builtin)loaded.put(key,LiveSourceStore.bestvPrograms(code,activeApi.schedule(code)));else{LiveSourceStore.Download d=LiveSourceStore.download(epg,24*1024*1024);List<LiveSourceStore.Program> programs=LiveSourceStore.parseXmltv(d.text);Map<String,List<LiveSourceStore.Program>> index=new HashMap<>();for(LiveSourceStore.Program p:programs){index.computeIfAbsent(p.channel,k->new ArrayList<>()).add(p);for(String alias:p.channelAliases)if(!alias.isEmpty()&&!alias.equals(p.channel))index.computeIfAbsent(alias,k->new ArrayList<>()).add(p);}LiveSourceStore.Source s=store.get(id);if(s!=null)for(JSONObject c:s.channels){List<LiveSourceStore.Program> matched=index.get(c.optString("EpgId"));if(matched==null)matched=index.get(c.optString("ChannelName"));loaded.put(id+"/"+c.optString("ChannelCode"),matched==null?Collections.emptyList():matched);}}
                data.putAll(loaded);for(String k:loaded.keySet())ages.put(k,System.currentTimeMillis());
                onUi(()->{epgPending.remove(pendingKey);if(data!=schedules)return;updateRows();if(controlsVisible)updateControlLabels();if(route.equals("guide"))refreshGuidePreservingFocus();});
            }catch(Exception e){
                if(builtin){data.putIfAbsent(key,Collections.emptyList());ages.put(key,System.currentTimeMillis());}else for(JSONObject c:channelSnapshot){String k=id+"/"+c.optString("ChannelCode");data.putIfAbsent(k,Collections.emptyList());ages.put(k,System.currentTimeMillis());}
                onUi(()->{epgPending.remove(pendingKey);if(source.id.equals(id)&&data==schedules&&route.equals("guide"))refreshGuidePreservingFocus();});
            }
        });
    }
    private void updateRows(){if(list==null)return;int first=list.getFirstVisiblePosition();for(int i=0;i<list.getChildCount();i++){View child=list.getChildAt(i);int n=first+i;if(child instanceof FrameLayout&&n<shown.size()){FrameLayout row=(FrameLayout)child;JSONObject c=shown.get(n);if(row.getChildCount()>=4){((TextView)row.getChildAt(2)).setText(programmeText(c));TvIcons.text(((TextView)row.getChildAt(3)),isPlayingChannel(c)?"▷":"");}}}}
    private void openGuide(){openGuideFor(null,route);}
    private void openGuideFor(JSONObject channel,String origin){
        rememberFocus();guideReturn=origin;if(origin.equals("full"))guideFullReturn=returnFromFull;guideChannel=channel==null?"":channel.optString("ChannelCode");guideSourceId=channel==null?"":owner(channel).id;route="guide";guidePage=0;
        if(channel!=null)for(int i=0;i<owner(channel).channels.size();i++)if(owner(channel).channels.get(i).optString("ChannelCode").equals(guideChannel)){guidePage=i/6;focusMemory.put("guide","guide-channel-"+(i%6));break;}
        render(channel!=null);
    }
    private final Runnable guideRefresh=()->{if(!dead&&route.equals("guide")){if(dialog!=null&&dialog.isShowing()||android.os.SystemClock.uptimeMillis()-lastInputAt<200){handler.postDelayed(this.guideRefresh,200);return;}rememberFocus();render(true);}};
    private void refreshGuidePreservingFocus(){handler.removeCallbacks(guideRefresh);handler.postDelayed(guideRefresh,200);}
    private void renderGuide(){
        if(guideChannel.isEmpty())filteredChannels(false);else {LiveSourceStore.Source selected=source;for(LiveSourceStore.Source candidate:store.channelSnapshots())if(candidate.id.equals(guideSourceId)){selected=candidate;break;}shown=new ArrayList<>(selected.channels);for(JSONObject ch:shown)channelOwners.put(ch,selected);}
        guidePage=Math.min(guidePage,Math.max(0,(shown.size()-1)/6));
        ui.header(page,"电视节目单",new SimpleDateFormat("MM 月 dd 日",Locale.CHINA).format(new Date())+" · "+(guideChannel.isEmpty()?sourceScopeName():shown.isEmpty()?source.name:owner(shown.get(0)).name),()->back());
        ui.button(page,"上一组",686,72,105,32,()->{if(guidePage>0){guidePage--;render(false);}});ui.button(page,"下一组",803,72,109,32,()->{if((guidePage+1)*6<shown.size()){guidePage++;render(false);}});
        long now=System.currentTimeMillis();Calendar clock=Calendar.getInstance();clock.setTimeInMillis(now);clock.set(Calendar.MINUTE,0);clock.set(Calendar.SECOND,0);clock.set(Calendar.MILLISECOND,0);long start=clock.getTimeInMillis(),end=start+3*3600000L;
        for(int i=0;i<3;i++)ui.text(page,time(start+i*3600000L),14,TvUi.MUTED,191+i*240,96,210,26);
        boolean any=false,pending=false;int count=Math.min(6,shown.size()-guidePage*6);for(int i=0;i<count;i++){JSONObject ch=shown.get(guidePage*6+i);float y=126+i*54;int row=i;Button channel=ui.button(page,ch.optString("ChannelName"),48,y,134,48,()->startFullChannel(ch,"guide"));tag(channel,"guide-channel-"+row);
            List<LiveSourceStore.Program> programs=channelPrograms(ch);for(LiveSourceStore.Program p:programs){if(p.end<=start||p.start>=end)continue;any=true;float x=191+Math.max(0,(p.start-start)/(float)(end-start))*721,w=(Math.min(p.end,end)-Math.max(p.start,start))/(float)(end-start)*721;Button cell=ui.button(page,p.title+"\n"+time(p.start)+"—"+time(p.end),x,y,Math.max(12,w-4),48,()->programDetail(ch,p));cell.setTextSize(TypedValue.COMPLEX_UNIT_PX,12*ui.scale*ui.fontScale);cell.setEllipsize(TextUtils.TruncateAt.END);tag(cell,"guide-program-"+row+"-"+p.start);}
            String key=owner(ch).id+"/"+ch.optString("ChannelCode");if(!schedules.containsKey(key)){ensureEpg(ch);pending|=(epgPending.contains(key)||epgPending.contains(owner(ch).id+"/xmltv"));}if(programs.isEmpty())ui.text(page,(epgPending.contains(key)||epgPending.contains(owner(ch).id+"/xmltv"))?"节目单加载中…":"暂无节目信息，确认左侧频道仍可观看",12,TvUi.MUTED,200,y,712,48);
        }
        if(any){View marker=new View(this);marker.setBackgroundColor(0xffb5a1ff);ui.at(page,marker,191+(now-start)/(float)(end-start)*721,122,1.5f,Math.max(1,count)*54);marker.setFocusable(false);}
        if(count==0)ui.text(page,"没有频道。请先添加或获取直播源。",17,TvUi.MUTED,64,220,810,80);
        ui.footer(page,pending?"正在加载真实节目单；无节目单也可正常播放。":"方向键浏览实际节目时间 · 确认查看详情 · 不包含预约、回看或录制");
    }
    private void programDetail(JSONObject channel,LiveSourceStore.Program program){
        View anchor=getCurrentFocus();FrameLayout panel=new FrameLayout(this);panel.setBackground(ui.surface(0xff1b2038,false));AlertDialog detail=new AlertDialog.Builder(this).create();detail.setView(panel,0,0,0,0);dialog=detail;
        TextView heading=ui.title(panel,program.title,16,20,240,28);heading.setSingleLine();heading.setEllipsize(TextUtils.TruncateAt.END);
        String status=program.current(System.currentTimeMillis())?"正在播出":program.start>System.currentTimeMillis()?"即将播出":"已播出";
        TextView badge=ui.text(panel,status,12,0xffb6a8ff,269,18,75,22);badge.setGravity(Gravity.CENTER);badge.setBackground(ui.surface(0xff2b2850,false));
        ui.text(panel,channel.optString("ChannelName")+" · "+new SimpleDateFormat("MM-dd",Locale.CHINA).format(new Date(program.start))+" "+time(program.start)+"—"+time(program.end),13,TvUi.MUTED,16,53,328,24);
        TextView text=ui.text(program.description.isEmpty()?"暂无节目简介":program.description,14,TvUi.MUTED);text.setGravity(Gravity.TOP);text.setLineSpacing(ui.px(5),1);ScrollView scroll=new ScrollView(this);scroll.addView(text);ui.at(panel,scroll,16,86,328,54);
        Button watch=ui.button(panel,"▷ 观看该频道",16,144,150,38,()->{detail.dismiss();startFullChannel(channel,route);});ui.button(panel,"关闭",178,144,90,38,()->detail.dismiss());
        detail.setOnDismissListener(d->{if(dialog==detail)dialog=null;if(anchor!=null&&anchor.isAttachedToWindow())anchor.requestFocus();lastInputAt=android.os.SystemClock.uptimeMillis();resetIdleTimers();});detail.show();if(detail.getWindow()!=null){detail.getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT));detail.getWindow().getDecorView().setPadding(0,0,0,0);detail.getWindow().setLayout(ui.px(360),ui.px(206));}watch.requestFocus();
    }
    private void renderSearch(){
        ui.header(page,"搜索直播","仅查询直播频道和已加载节目单",()->back());searchInput=ui.input(page,"频道名或节目名",searchQuery,50,100,350,false);tag(searchInput,"search-input");ui.button(page,"搜索",396,100,90,40,()->runSearch());ui.button(page,"清除",496,100,90,40,()->{searchInput.setText("");runSearch();});
        String[] keys={"A B C D E F","G H I J K L","M N O P Q R","S T U V W X","Y Z 0 1 2 3","4 5 6 7 8 9"};for(int i=0;i<keys.length;i++){String[] letters=keys[i].split(" ");for(int j=0;j<letters.length;j++){String letter=letters[j];ui.button(page,letter,32+j*58,159+i*39,50,31,()->{searchInput.append(letter);runSearch();});}}
        ui.button(page,"中文 / 拼音",32,405,130,38,()->{searchInput.requestFocus();((android.view.inputmethod.InputMethodManager)getSystemService(INPUT_METHOD_SERVICE)).showSoftInput(searchInput,android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT);});ui.button(page,"退格",178,405,90,38,()->{String q=searchInput.getText().toString();if(!q.isEmpty())searchInput.setText(q.substring(0,q.offsetByCodePoints(q.length(),-1)));runSearch();});
        FrameLayout results=new FrameLayout(this);ui.at(page,results,415,158,497,306);tag(results,"search-results");searchInput.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence s,int a,int c,int d){}public void onTextChanged(CharSequence s,int start,int before,int count){searchQuery=s.toString();long gen=++searchGeneration;handler.postDelayed(()->{if(gen==searchGeneration&&route.equals("search"))runSearch();},250);}public void afterTextChanged(Editable e){}});runSearch();ui.footer(page,"确认输入框调用系统输入法 · 查询词和返回位置保留 · 仅搜索真实可用数据");
    }
    private static final class LiveSearchHit {
        final LiveSourceStore.Source source;final JSONObject channel;final LiveSourceStore.Program program;
        LiveSearchHit(LiveSourceStore.Source s,JSONObject c,LiveSourceStore.Program p){source=s;channel=c;program=p;}
    }
    private void runSearch(){
        if(searchInput==null)return;searchQuery=searchInput.getText().toString();final String q=searchQuery.trim().toLowerCase(Locale.ROOT);final long generation=++searchGeneration;
        FrameLayout results=page.findViewWithTag("search-results");if(results==null)return;results.removeAllViews();ScrollView scroll=new ScrollView(this);LinearLayout rows=new LinearLayout(this);rows.setOrientation(LinearLayout.VERTICAL);scroll.addView(rows);results.addView(scroll,new FrameLayout.LayoutParams(-1,-1));
        if(q.isEmpty()){rows.addView(ui.text("输入频道或节目名称开始搜索。",15,TvUi.MUTED));return;}
        rows.addView(ui.text("正在搜索…",14,TvUi.MUTED));final List<LiveSourceStore.Source> snapshots=store.channelSnapshots();final String scope=sourceScope;final boolean favorites=favoritesOnly;final ConcurrentMap<String,List<LiveSourceStore.Program>> programs=schedules;
        diskWorker.execute(()->{
            List<LiveSearchHit> channelHits=new ArrayList<>(),programHits=new ArrayList<>();
            for(LiveSourceStore.Source snapshot:snapshots)for(JSONObject ch:snapshot.channels){
                if(!LiveFilter.includes(scope,favorites,snapshot.id,snapshot.enabled,store.favorite(snapshot.id,ch.optString("ChannelCode"))))continue;
                if(channelHits.size()<12&&ch.optString("ChannelName").toLowerCase(Locale.ROOT).contains(q))channelHits.add(new LiveSearchHit(snapshot,ch,null));
                List<LiveSourceStore.Program> epg=programs.get(snapshot.id+"/"+ch.optString("ChannelCode"));if(epg!=null&&programHits.size()<12)for(LiveSourceStore.Program p:epg)if(p.title.toLowerCase(Locale.ROOT).contains(q)){programHits.add(new LiveSearchHit(snapshot,ch,p));if(programHits.size()==12)break;}
            }
            onUi(()->{
                if(generation!=searchGeneration||!route.equals("search")||!scope.equals(sourceScope)||favorites!=favoritesOnly)return;
                for(LiveSearchHit hit:channelHits)channelOwners.put(hit.channel,hit.source);for(LiveSearchHit hit:programHits)channelOwners.put(hit.channel,hit.source);
                rows.removeAllViews();addSearchSection(rows,"频道",channelHits);addSearchSection(rows,"节目（已加载节目单）",programHits);
            });
        });
    }
    private void addSearchSection(LinearLayout rows,String heading,List<LiveSearchHit> hits){
        rows.addView(ui.text(heading+(hits.size()==12?" · 前 12 条":" · "+hits.size()),17,TvUi.INK),new LinearLayout.LayoutParams(-1,ui.px(34)));
        if(hits.isEmpty()){rows.addView(ui.text("没有匹配结果，可修改或清除查询词。",12,TvUi.MUTED));return;}
        for(LiveSearchHit hit:hits){String title=hit.program==null?hit.channel.optString("ChannelName")+" · "+hit.source.name:hit.program.title+" · "+hit.channel.optString("ChannelName")+"  "+time(hit.program.start);Button b=ui.button(title,()->{if(hit.program==null)startFullChannel(hit.channel,"search");else programDetail(hit.channel,hit.program);});rows.addView(b,new LinearLayout.LayoutParams(-1,ui.px(42)));}
    }
    private JSONObject actualPlayingChannel(){
        if(player==null||player.getCurrentMediaItem()==null||!player.getCurrentMediaItem().mediaId.equals(playingSource+":"+playingCode))return null;
        for(LiveSourceStore.Source s:store.channelSnapshots())if(s.id.equals(playingSource))for(JSONObject channel:s.channels)if(channel.optString("ChannelCode").equals(playingCode))return channel;
        return null;
    }
    private void cancelFavoriteGesture(){handler.removeCallbacks(holdFavorite);favoriteGesture.cancel();if(confirmAnchor!=null)confirmAnchor.setPressed(false);confirmAnchor=null;confirmFromVideo=false;}
    private void savePlayingFavorite(){
        JSONObject channel=actualPlayingChannel();if(channel==null)return;
        final String id=favoriteGesture.source,code=favoriteGesture.channel,name=channel.optString("ChannelName");
        if(!id.equals(playingSource)||!code.equals(playingCode))return;
        diskWorker.execute(()->{try{store.toggleFavorite(id,code);boolean saved=store.favorite(id,code);onUi(()->favoriteResult((saved?"已收藏 · ":"已取消收藏 · ")+name));}catch(Exception e){onUi(()->favoriteResult("收藏未保存，请重试"));}});
    }
    private void favoriteResult(String text){
        handler.removeCallbacks(removeFavoriteFeedback);removeFavoriteFeedback.run();favoriteFeedback=ui.text(text,12,TvUi.INK);favoriteFeedback.setGravity(Gravity.CENTER);favoriteFeedback.setPadding(ui.px(16),ui.px(9),ui.px(16),ui.px(9));favoriteFeedback.setMaxWidth(ui.px(800));favoriteFeedback.setBackground(ui.surface(0xe6171c32,false));favoriteFeedback.setFocusable(false);
        FrameLayout.LayoutParams p=new FrameLayout.LayoutParams(-2,-2,Gravity.BOTTOM|Gravity.CENTER_HORIZONTAL);p.bottomMargin=ui.px(controlsVisible?136:32);root.addView(favoriteFeedback,p);favoriteFeedback.announceForAccessibility(text);handler.postDelayed(removeFavoriteFeedback,2000);
    }
    private boolean confirm(int key){return key==KeyEvent.KEYCODE_DPAD_CENTER||key==KeyEvent.KEYCODE_ENTER||key==KeyEvent.KEYCODE_NUMPAD_ENTER;}
    @Override public boolean dispatchKeyEvent(KeyEvent event){
        if(event.getKeyCode()==KeyEvent.KEYCODE_MENU&&isLiveRoute()){if(event.getAction()==KeyEvent.ACTION_DOWN)cancelFavoriteGesture();return true;}
        if(event.getAction()==KeyEvent.ACTION_DOWN||event.getAction()==KeyEvent.ACTION_UP){homeFocusFromInput=true;lastInputAt=android.os.SystemClock.uptimeMillis();resetIdleTimers();}
        if((player==null||source==null||store==null)&&event.getKeyCode()!=KeyEvent.KEYCODE_BACK)return true;
        int key=event.getKeyCode();
        if(confirm(key)&&event.getAction()==KeyEvent.ACTION_UP&&favoriteGesture.consumesRelease()){
            handler.removeCallbacks(holdFavorite);View anchor=confirmAnchor;if(anchor!=null)anchor.setPressed(false);confirmAnchor=null;
            LiveConfirmGesture.Release release=favoriteGesture.release(event.isCanceled());
            if(release==LiveConfirmGesture.Release.SHORT&&route.equals("full")&&!drawerVisible&&(dialog==null||!dialog.isShowing())){if(confirmShowsInfo)showControls();else if(anchor!=null&&anchor.isAttachedToWindow())anchor.performClick();}
            return true;
        }
        if(event.getAction()==KeyEvent.ACTION_DOWN&&!confirm(key))cancelFavoriteGesture();
        if(dialog!=null&&dialog.isShowing())return super.dispatchKeyEvent(event);
        if(controlsVisible&&event.getAction()==KeyEvent.ACTION_DOWN)resetControlsTimer();
        if(event.getAction()==KeyEvent.ACTION_DOWN){
            if(errorPanel!=null&&getCurrentFocus()==recoverySources&&key==KeyEvent.KEYCODE_DPAD_RIGHT&&!drawerVisible){if(route.equals("home")){focusList(true);return true;}if(route.equals("full")){showDrawer();return true;}}
            if(route.equals("settings")&&key==KeyEvent.KEYCODE_DPAD_LEFT&&settingsPanel!=null&&settingsPanel.hasFocus()){rememberSettingsFocus();settingsTabs.get(settingsCategoryIndex()).requestFocus();return true;}
            if(route.equals("settings")&&key==KeyEvent.KEYCODE_DPAD_RIGHT&&settingsCategoryIndex()<settingsTabs.size()&&settingsTabs.get(settingsCategoryIndex()).hasFocus()){focusSettingsPanel();return true;}
        }
        if(route.equals("full")&&!drawerVisible&&errorPanel==null&&confirm(key)){
            if(event.getAction()==KeyEvent.ACTION_DOWN){
                if(event.getRepeatCount()==0){confirmShowsInfo=!controlsVisible;confirmAnchor=getCurrentFocus();if(confirmAnchor!=null)confirmAnchor.setPressed(true);favoriteGesture.begin(playingSource,actualPlayingChannel()==null?"":playingCode,android.os.SystemClock.uptimeMillis(),ViewConfiguration.getLongPressTimeout());handler.postDelayed(holdFavorite,ViewConfiguration.getLongPressTimeout());}
                return true;
            }
        }
        return super.dispatchKeyEvent(event);
    }
    @Override public boolean dispatchTouchEvent(MotionEvent event){if(event.getActionMasked()==MotionEvent.ACTION_DOWN||event.getActionMasked()==MotionEvent.ACTION_MOVE||event.getActionMasked()==MotionEvent.ACTION_UP){homeFocusFromInput=true;lastInputAt=android.os.SystemClock.uptimeMillis();resetIdleTimers();}return super.dispatchTouchEvent(event);}
    @Override public boolean dispatchGenericMotionEvent(MotionEvent event){homeFocusFromInput=true;lastInputAt=android.os.SystemClock.uptimeMillis();resetIdleTimers();return super.dispatchGenericMotionEvent(event);}
    @Override public boolean onKeyDown(int key,KeyEvent e){
        if(dialog!=null&&dialog.isShowing())return super.onKeyDown(key,e);
        if(route.equals("full")&&!drawerVisible&&!controlsVisible&&errorPanel==null){if(key==KeyEvent.KEYCODE_DPAD_RIGHT){showDrawer();return true;}if(key==KeyEvent.KEYCODE_DPAD_LEFT){openGuide();return true;}if(key==KeyEvent.KEYCODE_DPAD_UP||key==KeyEvent.KEYCODE_CHANNEL_UP||key==KeyEvent.KEYCODE_DPAD_DOWN||key==KeyEvent.KEYCODE_CHANNEL_DOWN){if(!source.channels.isEmpty()){JSONObject c=requestedChannel();int n=c==null?0:source.channels.indexOf(c);boolean next=key==KeyEvent.KEYCODE_DPAD_UP||key==KeyEvent.KEYCODE_CHANNEL_UP;play(source.channels.get((n+(next?1:source.channels.size()-1))%source.channels.size()),true);}return true;}}
        return super.onKeyDown(key,e);
    }
    @Override public void onBackPressed(){back();}
    @Override public void onWindowFocusChanged(boolean focus){super.onWindowFocusChanged(focus);if(focus&&root!=null)immersive();if(!focus)cancelFavoriteGesture();}
    @Override protected void onResume(){
        super.onResume();resumed=true;if(player==null||store==null)return;if(playerReleasedForLibrary)createLivePlayer();resumeReady();
    }
    private void resumeReady(){
        String saved=prefs.getString("config","{}");boolean configChanged=!saved.equals(lastConfig);if(configChanged){loadConfig();schedules=new ConcurrentHashMap<>();scheduleAt=new ConcurrentHashMap<>();if(source.builtin()){playGeneration++;player.stop();player.clearMediaItems();playingCode="";playingSource="";}}
        if(!started){started=true;if(isLiveRoute()&&!source.channels.isEmpty()&&(prefs.getBoolean("auto_play",true)||resumePlayback&&resumeSource.equals(source.id)))restoreLastChannel();refreshSource(source.id,false);}
        else if(configChanged){refreshSource(source.id,true);if(isLiveRoute()&&!source.channels.isEmpty())restoreLastChannel();}
        else if(isLiveRoute()&&player.getMediaItemCount()>0)player.play();
        else if(isLiveRoute()&&!openingChannel&&(prefs.getBoolean("auto_play",true)||resumePlayback&&resumeSource.equals(source.id)))restoreLastChannel();
        if(route.equals("sources")){boolean requested=pendingJellyfinAdd;pendingJellyfinAdd=false;if(JellyLiveBridge.isConnected())syncJellyfinSource(requested);}
        lastInputAt=android.os.SystemClock.uptimeMillis();resetIdleTimers();
        handler.removeCallbacks(minuteTick);handler.postDelayed(minuteTick,60000);
    }
    @Override protected void onPause(){cancelFavoriteGesture();resumed=false;suspendPlayback();handler.removeCallbacks(homeIdle);handler.removeCallbacks(hideIdleDrawer);handler.removeCallbacks(hideControls);super.onPause();handler.removeCallbacks(minuteTick);}
    @Override protected void onSaveInstanceState(Bundle state){rememberFocus();super.onSaveInstanceState(state);state.putBoolean("pending_jellyfin_add",pendingJellyfinAdd);state.putString("route",route);state.putString("group",group);state.putString("source_scope",sourceScope);state.putBoolean("favorites_only",favoritesOnly);state.putString("return_full",returnFromFull);state.putString("query",searchQuery);state.putString("home_focus",lastHomeFocus);if(editor!=null)try{state.putString("editor",editor.json().toString());}catch(Exception ignored){}
        state.putString("settings_category",settingsCategory);state.putString("guide_return",guideReturn);state.putString("guide_full_return",guideFullReturn);state.putString("guide_channel",guideChannel);state.putString("guide_source",guideSourceId);state.putInt("guide_page",guidePage);state.putStringArrayList("back_stack",new ArrayList<>(backStack));
        state.putString("resume_source",resumeSource);state.putString("resume_code",resumeCode);state.putBoolean("resume_playback",resumePlayback);
        state.putString("favorite_focus",new JSONObject(favoriteFocus).toString());state.putString("settings_focus",new JSONObject(settingsFocus).toString());state.putString("page_focus",new JSONObject(focusMemory).toString());
        try{JSONObject positions=new JSONObject();for(Map.Entry<String,int[]> entry:favoriteScroll.entrySet())positions.put(entry.getKey(),new JSONArray().put(entry.getValue()[0]).put(entry.getValue()[1]));state.putString("favorite_scroll",positions.toString());}catch(Exception ignored){}
    }
    @Override protected void onDestroy(){dead=true;playGeneration++;if(startupRotation!=null)startupRotation.cancel();handler.removeCallbacksAndMessages(null);worker.shutdownNow();diskWorker.shutdownNow();streamWorker.shutdownNow();JellyLiveBridge.releaseLiveStreams();if(player!=null&&!playerReleasedForLibrary)player.release();super.onDestroy();}
}
