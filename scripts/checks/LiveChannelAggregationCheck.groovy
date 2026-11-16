import com.github.javaparser.*
import org.json.JSONObject

// Execute production grouping/favourite methods with real JSON and an in-memory source store.
// This verifies channel identity and source boundaries, not Android rendering or decoding.
def parser = new JavaParser(new ParserConfiguration().setLanguageLevel(ParserConfiguration.LanguageLevel.JAVA_17))
def parsed = parser.parse(new File('app/src/main/java/org/jellyfin/androidtv/bestv/LiveActivity.java'))
assert parsed.successful : parsed.problems
def type = parsed.result.get().getClassByName('LiveActivity').get()
def methods = ['aggregateChannels', 'stationFavorite', 'toggleStationFavorite'].collect { name ->
    def node = type.getMethodsByName(name)[0].clone()
    node.setPrivate(false).setPublic(true)
    node.toString().replace('LiveSourceStore.Source', 'FakeSource')
}.join('\n')
def loader = new GroovyClassLoader(getClass().classLoader)
loader.parseClass('''
import org.json.*
import org.jellyfin.androidtv.bestv.LiveQuality
class FakeSource { String id; List<JSONObject> channels=[] }
class FakeStore {
    Set<String> favorites=[] as Set
    boolean favorite(String id,String code){favorites.contains(id+'/'+code)}
    void toggleFavorite(String id,String code){String key=id+'/'+code;if(!favorites.remove(key))favorites.add(key)}
}
class AggregationHost {
    String playingSource='',playingCode=''
    FakeStore store=new FakeStore()
    Map<JSONObject,FakeSource> channelOwners=new IdentityHashMap<>()
''' + methods + '\n}')
def source = { String id, List<List<String>> rows ->
    loader.loadClass('FakeSource').newInstance(id:id,channels:rows.collect { row ->
        new JSONObject().put('ChannelCode',row[0]).put('ChannelName',row[1]).put('ChannelNo',row[2])
    })
}
def h=loader.loadClass('AggregationHost').newInstance()
def tv=source('tv',[['plain','CCTV-1','01'],['hd','CCTV-1高清','02'],['ten','CCTV-10','10'],['plus','CCTV-5+高清','05']])
def grouped=h.aggregateChannels(tv,false)
assert grouped*.optString('ChannelName')==['CCTV-1','CCTV-10','CCTV-5+']
assert grouped*.optString('ChannelCode')==['plain','ten','plus']
assert tv.channels[1].optString('ChannelName')=='CCTV-1高清' // raw quality records stay intact
h.playingSource='tv';h.playingCode='hd'
grouped=h.aggregateChannels(tv,false)
assert grouped[0].optString('ChannelCode')=='hd' && grouped[0].optString('ChannelNo')=='01'
assert h.channelOwners[grouped[0]].is(tv)
def other=source('other',[['foreign','CCTV-1高清','03']])
assert h.aggregateChannels(other,false)[0].optString('ChannelCode')=='foreign'
assert h.channelOwners[h.aggregateChannels(other,false)[0]].is(other)
h.store.favorites.add('tv/plain');h.store.favorites.add('tv/hd');h.store.favorites.add('other/foreign')
assert h.aggregateChannels(tv,true).size()==1
assert h.stationFavorite(tv,grouped[0])
assert !h.toggleStationFavorite(tv,grouped[0])
assert h.store.favorites==['other/foreign'] as Set
assert h.aggregateChannels(tv,true).empty
assert h.toggleStationFavorite(tv,grouped[0])
assert h.store.favorites==['other/foreign','tv/hd'] as Set
assert h.aggregateChannels(tv,true)[0].optString('ChannelName')=='CCTV-1'
assert h.aggregateChannels(source('unknown',[['a','','1'],['b','','2']]),false).size()==2
println 'Production live grouping, current variant, raw data, source boundary and grouped favourites PASS'
