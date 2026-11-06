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
class Player { static final int STATE_READY=3 }
class Channel { String code; String optString(String key){code} }
''')
def methods = ['isLiveRoute','validPlayback','resetIdleTimers','cancelPreviewSwitch','schedulePreview','back'].collect{method(it)}.join('\n')
def hostClass = loader.parseClass('''
class InteractionHost {
    boolean dead=false,resumed=true,openingChannel=false,drawerVisible=false,controlsVisible=false
    String route='home',returnFromFull='home',guideReturn='home',guideFullReturn='home',requestedCode='a',liveErrorTitle='',lastHomeFocus='preview'
    int playGeneration=5
    long lastInputAt=0
    static final long HOME_IDLE_MS=30000,DRAWER_IDLE_MS=10000
    def source=[id:'builtin']
    def editor=[busy:false]
    def player=new FakePlayer()
    def handler=new FakeHandler()
    def dialog=null
    Deque<String> backStack=new ArrayDeque<>()
    List<String> events=[]
    Runnable previewSwitch
    Runnable hideControls={hideControls()} as Runnable
''' + 'Runnable hideIdleDrawer='+initializer('hideIdleDrawer')+'\nRunnable homeIdle='+initializer('homeIdle')+'\n'+methods+'''
    void resetControlsTimer(){handler.removeCallbacks(hideControls);if(resumed&&controlsVisible&&(dialog==null||!dialog.isShowing()))handler.postDelayed(hideControls,5000)}
    void hideControls(){controlsVisible=false;events.add('hide-controls')}
    void hideDrawer(){drawerVisible=false;events.add('hide-drawer')}
    void render(boolean restore){events.add('render:'+route)}
    void rememberFocus(){events.add('remember-focus')}
    void exitDialog(){events.add('exit-confirm')}
    void toast(String value){events.add('toast')}
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
def output=[javaSyntaxParsed:true,scope:'Production method bodies executed in Groovy with fake platform/clock; not Android compilation or TV execution',checks:results.size(),results:results]
// Print results; no local/private reports are added to the repository.
println(JsonOutput.toJson(output))
