import com.github.javaparser.*
import com.github.javaparser.ast.body.*
import groovy.json.JsonOutput

// Execute selected production Java method bodies in a Groovy host with a fake clock,
// scheduler and platform objects. This checks interaction decisions, not Android rendering.
def file = new File(args ? args[0] : 'app/src/main/java/org/jellyfin/androidtv/bestv/LiveActivity.java')
def parser = new JavaParser(new ParserConfiguration().setLanguageLevel(ParserConfiguration.LanguageLevel.JAVA_17))
def parsed = parser.parse(file)
assert parsed.successful : parsed.problems
def type = parsed.result.get().getClassByName('LiveActivity').get()
def method = { String name ->
    def node = type.getMethodsByName(name)[0].clone()
    node.setPrivate(false).setPublic(true)
    node.toString().replace('JSONObject', 'Channel')
}
def initializer = { String name -> type.getFieldByName(name).get().variables[0].initializer.get().toString() }
def loader = new GroovyClassLoader(getClass().classLoader)
loader.parseClass(new File('app/src/main/java/org/jellyfin/androidtv/bestv/LiveFilter.java').text.replace('package org.jellyfin.androidtv.bestv;', ''))
def clock = loader.parseClass('package android.os; class SystemClock { static long now=0; static long uptimeMillis(){now} }')
loader.parseClass('''
class FakeHandler {
    Map<Runnable,Long> tasks=new IdentityHashMap<>()
    void removeCallbacks(Runnable task){tasks.remove(task)}
    void postDelayed(Runnable task,long delay){tasks[task]=android.os.SystemClock.now+delay}
    void advance(long delta){
        long until=android.os.SystemClock.now+delta
        int loops=0
        while(!tasks.isEmpty()){
            def entry=tasks.entrySet().min{it.value}
            if(entry.value>until)break
            assert ++loops<1000
            android.os.SystemClock.now=entry.value
            Runnable task=entry.key;tasks.remove(task);task.run()
        }
        android.os.SystemClock.now=until
    }
}
class FakeUi { int multiplier=1; int timeout(int normal){normal*multiplier} }
class FakePlayer {
    int state=3
    boolean playing=true
    int getPlaybackState(){state}
    boolean isPlaying(){playing}
}
class FakeDialog {
    boolean showing=true
    boolean dismissed=false
    boolean isShowing(){showing}
    void dismiss(){showing=false;dismissed=true}
}
class View {
    boolean focused=false,attached=true
    boolean isAttachedToWindow(){attached}
    void requestFocus(){focused=true}
}
class FakeList extends View {
    int selected=-1,top=0
    int getSelectedItemPosition(){selected}
    void setSelection(int value){selected=value}
    void setSelectionFromTop(int value,int position){selected=value;top=position}
}
class FakeSource {
    String id='builtin'
    boolean builtin(){id=='builtin'}
}
class FakeStore { String saved=''; String last(String id){saved} }
class FakePrefs { String legacy=''; String getString(String key,String fallback){legacy ?: fallback} }
class Player { static final int STATE_READY=3 }
class Channel { String code; String optString(String key){code} }
''')
def methods = ['isLiveRoute','validPlayback','resetIdleTimers','cancelPreviewSwitch','schedulePreview','back','focusHomeDefault','focusList','focusPreview','focusRecovery','restoreRoute'].collect{method(it)}.join('\n')
def hostClass = loader.parseClass('''
class InteractionHost {
    boolean dead=false,resumed=true,openingChannel=false,drawerVisible=false,controlsVisible=false,playerReleasedForLibrary=false
    String route='home',returnFromFull='home',guideReturn='home',guideFullReturn='home',requestedCode='a',liveErrorTitle='',lastHomeFocus='channel-list',playingSource='',playingCode='',resumeSource='',resumeCode=''
    int playGeneration=5
    long lastInputAt=0
    static final long HOME_IDLE_MS=30000,DRAWER_IDLE_MS=10000
    boolean homeFocusFromInput=true,suppressPreviewSelection=false
    Map channelFocus=[:],channelScroll=[:]
    def source=new FakeSource()
    def store=new FakeStore()
    def prefs=new FakePrefs()
    def list=new FakeList()
    def previewButton=new View()
    def errorPanel=null,groupButton=null,lastRecoveryAction=null,recoveryRetry=new View()
    List<Channel> shown=[]
    def editor=[busy:false]
    def ui=new FakeUi()
    def player=new FakePlayer()
    def handler=new FakeHandler()
    def dialog=null
    Deque<String> backStack=new ArrayDeque<>()
    List<String> events=[]
    Runnable previewSwitch
    Runnable hideControls={hideControls()} as Runnable
''' + 'Runnable hideIdleDrawer='+initializer('hideIdleDrawer')+'\nRunnable homeIdle='+initializer('homeIdle')+'\n'+methods+'''
    View firstFocusable(View view){view}
    void resetControlsTimer(){handler.removeCallbacks(hideControls);if(resumed&&controlsVisible&&(dialog==null||!dialog.isShowing()))handler.postDelayed(hideControls,5000)}
    void hideControls(){controlsVisible=false;events.add('hide-controls')}
    void hideDrawer(){drawerVisible=false;events.add('hide-drawer')}
    void render(boolean restore){events.add('render:'+route)}
    boolean settingsBack(){false} // Android settings focus itself is covered by the native driver.
    void rememberFocus(){events.add('remember-focus')}
    void exitDialog(){events.add('exit-confirm')}
    void toast(String value){events.add('toast')}
    def owner(Channel channel){source}
    String channelKey(Channel channel){LiveFilter.key(source.id,channel.code)}
    String filterKey(){"home/all/false"}
    Channel findChannel(String code){new Channel(code:code)}
    void play(Channel channel,boolean fresh){events.add('play:'+channel.code)}
}
''')
def results=[:]
def check={String name,Closure test -> clock.now=0;test();results[name]=true}
def fresh={hostClass.newInstance()}
def fakeDialog={loader.loadClass('FakeDialog').newInstance()}
def channel={String code -> loader.loadClass('Channel').newInstance(code:code)}

check('back_closes_dialog_before_fullscreen') {
    def h=fresh();h.route='full';h.dialog=fakeDialog();h.back()
    assert h.dialog.dismissed && h.route=='full' && h.events.empty
}
check('back_closes_drawer_before_info_or_exit') {
    def h=fresh();h.route='full';h.drawerVisible=true;h.controlsVisible=true;h.back()
    assert h.events==['hide-drawer'] && h.route=='full' && h.controlsVisible
}
check('back_hides_info_bar_then_returns_to_origin') {
    def h=fresh();h.route='full';h.returnFromFull='favorites';h.controlsVisible=true;h.back()
    assert h.events==['hide-controls'] && h.route=='full'
    h.back();assert h.route=='favorites' && h.events[-1]=='render:favorites'
}
check('bare_fullscreen_back_never_opens_drawer') {
    def h=fresh();h.route='full';h.liveErrorTitle='unavailable';h.back()
    assert h.route=='home' && !h.drawerVisible && h.events==['render:home']
}
check('guide_round_trip_restores_original_fullscreen_return') {
    def h=fresh();h.route='full';h.returnFromFull='guide';h.guideReturn='full';h.guideFullReturn='home'
    h.back();assert h.route=='guide'
    h.back();assert h.route=='full' && h.returnFromFull=='home'
    h.back();assert h.route=='home'
}
check('home_back_requires_exit_confirmation') {
    def h=fresh();h.back();assert h.events==['exit-confirm']
}
check('request_guard_rejects_old_or_foreign_callbacks') {
    def h=fresh();assert h.validPlayback(5,'builtin','a')
    assert !h.validPlayback(4,'builtin','a')
    assert !h.validPlayback(5,'other','a')
    assert !h.validPlayback(5,'builtin','b')
    h.route='full';assert h.validPlayback(5,'builtin','a')
    for(route in ['favorites','settings','channels','guide','search']) {h.route=route;assert !h.validPlayback(5,'builtin','a')}
    h.route='home';h.resumed=false;assert !h.validPlayback(5,'builtin','a')
    h.resumed=true;h.dead=true;assert !h.validPlayback(5,'builtin','a')
}
check('preview_debounce_plays_only_final_selection') {
    def h=fresh();h.schedulePreview(channel('a'));h.handler.advance(100);h.schedulePreview(channel('b'))
    h.handler.advance(149);assert h.events.empty
    h.handler.advance(1);assert h.events==['play:b']
}
check('preview_cancel_and_page_exit_do_not_play') {
    def h=fresh();h.schedulePreview(channel('b'));h.cancelPreviewSwitch();h.handler.advance(500);assert h.events.empty
    h.schedulePreview(channel('c'));h.route='favorites';h.handler.advance(500);assert h.events.empty
}
check('home_idle_expands_current_playback_at_any_focus') {
    for(focus in ['preview','channel-list','tile-0','tile-3']) {
        clock.now=0;def h=fresh();h.lastHomeFocus=focus;h.resetIdleTimers();h.handler.advance(29999);assert h.route=='home'
        h.handler.advance(1);assert h.route=='full' && h.returnFromFull=='home'
        assert h.events==['remember-focus','render:full']
    }
}
check('home_input_restarts_idle_deadline') {
    def h=fresh();h.resetIdleTimers();h.handler.advance(20000);h.lastInputAt=clock.now;h.resetIdleTimers()
    h.handler.advance(29999);assert h.route=='home';h.handler.advance(1);assert h.route=='full'
}
check('home_idle_waits_for_pending_playback_or_error') {
    for(reason in ['opening','not-ready','error','paused']) {
        clock.now=0;def h=fresh();h.resetIdleTimers()
        if(reason=='opening')h.openingChannel=true
        if(reason=='not-ready')h.player.state=2
        if(reason=='error')h.liveErrorTitle='failure'
        if(reason=='paused')h.player.playing=false
        h.handler.advance(35000);assert h.route=='home' && h.events.empty
    }
}
check('leaving_home_cancels_idle_deadline') {
    def h=fresh();h.resetIdleTimers();h.route='settings';h.resetIdleTimers();h.handler.advance(60000);assert h.events.empty
}
check('info_bar_hides_after_five_seconds_without_exit') {
    def h=fresh();h.route='full';h.controlsVisible=true;h.resetIdleTimers();h.handler.advance(4999);assert h.controlsVisible
    h.handler.advance(1);assert !h.controlsVisible && h.route=='full'
}
check('drawer_hides_after_ten_seconds_and_input_restarts_timer') {
    def h=fresh();h.route='full';h.drawerVisible=true;h.resetIdleTimers();h.handler.advance(9000);h.lastInputAt=clock.now;h.resetIdleTimers()
    h.handler.advance(9999);assert h.drawerVisible;h.handler.advance(1);assert !h.drawerVisible && h.route=='full'
}
check('dialog_pauses_overlay_idle_and_close_restarts_timer') {
    def h=fresh();h.route='full';h.drawerVisible=true;h.controlsVisible=true;h.resetIdleTimers()
    h.dialog=fakeDialog();h.resetIdleTimers();h.handler.advance(20000);assert h.drawerVisible && h.controlsVisible
    h.dialog.dismiss();h.lastInputAt=clock.now;h.resetIdleTimers();h.handler.advance(5000);assert !h.controlsVisible && h.drawerVisible
    h.handler.advance(5000);assert !h.drawerVisible
}
check('background_cancels_idle_overlay_actions') {
    def h=fresh();h.route='full';h.drawerVisible=true;h.controlsVisible=true;h.resetIdleTimers()
    h.resumed=false;h.resetIdleTimers();h.handler.advance(60000);assert h.events.empty
}
// Use the production focus methods to verify startup without Android rendering.
def focusHost={
    def h=fresh();h.requestedCode='';h.shown=['a','b','c'].collect{channel(it)};h
}
check('home_default_focuses_playing_channel_before_requested_or_history') {
    def h=focusHost();h.playingSource='builtin';h.playingCode='b';h.requestedCode='c';h.store.saved='a'
    h.focusHomeDefault();assert h.list.focused && h.list.selected==1 && !h.previewButton.focused
}
check('home_default_focuses_pending_startup_channel') {
    def h=focusHost();h.requestedCode='c';h.store.saved='b'
    h.focusHomeDefault();assert h.list.focused && h.list.selected==2
}
check('home_default_focuses_saved_channel_without_playback') {
    def h=focusHost();h.store.saved='b'
    h.focusHomeDefault();assert h.list.selected==1
}
check('home_default_prefers_restored_channel_for_current_source') {
    def h=focusHost();h.resumeSource='builtin';h.resumeCode='c';h.store.saved='b'
    h.focusHomeDefault();assert h.list.selected==2
}
check('home_default_supports_legacy_last_channel') {
    def h=focusHost();h.prefs.legacy='c'
    h.focusHomeDefault();assert h.list.selected==2
}
check('home_default_ignores_other_source_playback_and_falls_back_if_missing') {
    def h=focusHost();h.playingSource='other';h.playingCode='c';h.store.saved='missing'
    h.focusHomeDefault();assert h.list.selected==0
}
check('home_playback_error_defaults_to_recovery_before_channel_list') {
    def h=focusHost();h.store.saved='b';h.errorPanel=loader.loadClass('View').newInstance()
    h.focusHomeDefault();assert h.recoveryRetry.focused && !h.list.focused
}
check('home_default_with_no_channels_focuses_recovery') {
    def h=focusHost();h.shown=[];h.errorPanel=loader.loadClass('View').newInstance()
    h.focusHomeDefault();assert h.recoveryRetry.focused && !h.list.focused
}
check('returning_to_list_preserves_selected_channel') {
    def h=focusHost();h.list.selected=2;h.store.saved='b'
    h.focusList(false);assert h.list.selected==2
}
check('returning_from_group_restores_channel_and_exact_scroll_offset') {
    def h=focusHost();h.list.selected=2;h.channelScroll['home/all/false']=[2,-7] as int[]
    h.focusList(false);assert h.list.selected==2 && h.list.top==-7
}
check('programmatic_home_focus_does_not_trigger_preview_or_override_autoplay') {
    def h=focusHost();h.schedulePreview(channel('a'));h.focusHomeDefault();h.schedulePreview(channel('a'))
    h.handler.advance(200);assert h.events.empty && h.previewSwitch==null
    h.homeFocusFromInput=true;h.schedulePreview(channel('b'));h.handler.advance(150)
    assert h.events==['play:b']
}

check('retired_channel_route_migrates_to_home') {
    def h=fresh();h.route='full';h.returnFromFull='channels';h.back();assert h.route=='home'
    h.route='guide';h.guideReturn='channels';h.back();assert h.route=='home'
    h.route='settings';h.backStack.push('channels');h.back();assert h.route=='home'
}
check('saved_filter_focus_is_source_scoped') {
    def h=focusHost();h.channelFocus['home/all/false']='builtin/c';h.playingSource='builtin';h.playingCode='a';h.focusList(true);assert h.list.selected==2
}
check('filter_rebuild_never_tunes_programmatic_first_row') {
    def h=fresh();h.suppressPreviewSelection=true;h.schedulePreview(channel('a'));h.handler.advance(200);assert h.events.empty
}
check('source_filter_and_favorite_intersection') {
    def policy=loader.loadClass('LiveFilter')
    assert policy.includes('',false,'thirdparty',true,false)
    assert !policy.includes('',false,'disabled',false,true)
    assert policy.includes('builtin',true,'builtin',true,true)
    assert !policy.includes('builtin',true,'thirdparty',true,true)
    assert !policy.includes('builtin',true,'builtin',true,false)
}
check('other_sources_aggregate_without_builtin_or_disabled_sources') {
    def policy=loader.loadClass('LiveFilter')
    assert policy.includes('@other',false,'m3u-one',true,false)
    assert policy.includes('@other',false,'jellyfin-one',true,false)
    assert !policy.includes('@other',false,'builtin',true,false)
    assert !policy.includes('@other',false,'m3u-two',false,false)
    assert policy.includes('',true,'m3u-one',true,true)
    assert !policy.includes('',true,'m3u-one',true,false)
}
check('duplicate_codes_use_source_identity') {
    def policy=loader.loadClass('LiveFilter');assert policy.key('builtin','1')!=policy.key('other','1')
    assert policy.focus(['builtin/1','other/1'],null,'other/1')==1
}

check('drawer_respects_accessibility_recommended_timeout') {
    def h=fresh();h.route='full';h.drawerVisible=true;h.ui.multiplier=3;h.resetIdleTimers();h.handler.advance(10000)
    assert h.drawerVisible;h.handler.advance(20000);assert !h.drawerVisible
}
check('recovery_restores_last_visible_action') {
    def h=fresh();h.lastRecoveryAction=loader.loadClass('View').newInstance();h.focusRecovery()
    assert h.lastRecoveryAction.focused && !h.recoveryRetry.focused
}
check('stale_recovery_action_falls_back_to_retry') {
    def h=fresh();h.lastRecoveryAction=loader.loadClass('View').newInstance();h.lastRecoveryAction.attached=false;h.focusRecovery()
    assert h.recoveryRetry.focused && !h.lastRecoveryAction.focused
}

check('released_live_player_rejects_callbacks_while_library_is_open') {
    def h=fresh();h.playerReleasedForLibrary=true
    assert !h.validPlayback(5,'builtin','a')
}

def output=[javaSyntaxParsed:true,scope:'Production method bodies executed in Groovy with fake platform/clock; not Android compilation or TV execution',checks:results.size(),results:results]
// Print results; no local/private reports are added to the repository.
println(JsonOutput.toJson(output))
