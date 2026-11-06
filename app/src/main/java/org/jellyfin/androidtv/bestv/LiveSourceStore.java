/* Independent live sources, atomic successful caches and parsers. GPL-2.0. */
package org.jellyfin.androidtv.bestv;

import org.json.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.text.*;
import java.util.*;
import java.util.regex.*;
import java.util.zip.GZIPInputStream;
import javax.xml.parsers.SAXParserFactory;
import org.xml.sax.*;
import org.xml.sax.helpers.DefaultHandler;

/** No Android dependency: parsing, persistence and source isolation are also exercised on the JVM. */
public final class LiveSourceStore {
    public static final String BUILTIN="builtin";
    public static final class Source {
        public String id,name,url,epg;
        public boolean enabled;
        public long updated;
        public List<JSONObject> channels=new ArrayList<>();
        public Source(String id,String name,String url,String epg,boolean enabled){this.id=id;this.name=name;this.url=url;this.epg=epg;this.enabled=enabled;}
        public boolean builtin(){return BUILTIN.equals(id);}
        public boolean jellyfin(){return id.startsWith("jellyfin-");}
        JSONObject json()throws Exception{return new JSONObject().put("id",id).put("name",name).put("url",url).put("epg",epg).put("enabled",enabled).put("updated",updated).put("channels",new JSONArray(channels));}
        static Source from(JSONObject j)throws Exception{Source s=new Source(j.getString("id"),j.getString("name"),j.optString("url"),j.optString("epg"),j.optBoolean("enabled",true));s.updated=j.optLong("updated");JSONArray a=j.optJSONArray("channels");if(a!=null)for(int i=0;i<a.length();i++)s.channels.add(a.getJSONObject(i));return s;}
        public Source copy()throws Exception{Source s=new Source(id,name,url,epg,enabled);s.updated=updated;for(JSONObject ch:channels)s.channels.add(new JSONObject(ch.toString()));return s;}
    }
    public static final class Program {
        public final String channel,title,description;
        public final List<String> channelAliases;
        public final long start,end;
        public Program(String ch,String t,String d,long s,long e){this(ch,t,d,s,e,Collections.emptyList());}
        private Program(String ch,String t,String d,long s,long e,List<String> a){channel=ch;title=t;description=d;start=s;end=e;channelAliases=Collections.unmodifiableList(a);}
        public boolean current(long now){return start<=now&&now<end;}
    }
    public static final class Download {public final String text,url;Download(String t,String u){text=t;url=u;}}
    private final File file;
    private final LinkedHashMap<String,Source> sources=new LinkedHashMap<>();
    private JSONObject favorites=new JSONObject(),history=new JSONObject();
    private String active=BUILTIN;
    /** Published only after a successful atomic write. UI reads never acquire the disk writer lock. */
    private static final class ReadState {
        final Map<String,Source> sources;final Map<String,Set<String>> favorites;final Map<String,String> history;final String active;
        ReadState(Map<String,Source> s,Map<String,Set<String>> f,Map<String,String> h,String a){sources=s;favorites=f;history=h;active=a;}
    }
    private volatile ReadState readable;
    private void publish(){
        Map<String,Set<String>> f=new HashMap<>();Iterator<String> keys=favorites.keys();while(keys.hasNext()){String k=keys.next();Set<String> codes=new HashSet<>();JSONArray a=favorites.optJSONArray(k);if(a!=null)for(int i=0;i<a.length();i++)codes.add(a.optString(i));f.put(k,Collections.unmodifiableSet(codes));}
        Map<String,String> h=new HashMap<>();keys=history.keys();while(keys.hasNext()){String k=keys.next();h.put(k,history.optString(k));}
        readable=new ReadState(Collections.unmodifiableMap(new LinkedHashMap<>(sources)),Collections.unmodifiableMap(f),Collections.unmodifiableMap(h),active);
    }
    private static Source descriptor(Source s){if(s==null)return null;Source d=new Source(s.id,s.name,s.url,s.epg,s.enabled);d.updated=s.updated;return d;}
    public Source metadata(String id){return descriptor(readable.sources.get(id));}
    public List<Source> allMetadata(){List<Source> a=new ArrayList<>();for(Source s:readable.sources.values())a.add(descriptor(s));return a;}
    public int channelCount(String id){Source s=readable.sources.get(id);return s==null?0:s.channels.size();}

    public LiveSourceStore(File folder)throws Exception{
        if(!folder.isDirectory()&&!folder.mkdirs())throw new IOException("无法创建直播缓存目录");file=new File(folder,"live-sources.json");
        if(file.isFile())try(InputStream in=new FileInputStream(file)){
            JSONObject j=new JSONObject(read(in,16*1024*1024));active=j.optString("active",BUILTIN);
            favorites=j.optJSONObject("favorites");if(favorites==null)favorites=new JSONObject();history=j.optJSONObject("history");if(history==null)history=new JSONObject();
            JSONArray a=j.optJSONArray("sources");if(a!=null)for(int i=0;i<a.length();i++){Source s=Source.from(a.getJSONObject(i));sources.put(s.id,s);}
        }catch(Exception e){throw new IOException("直播缓存损坏，请在直播源页面重新获取",e);}
        if(!sources.containsKey(BUILTIN))sources.put(BUILTIN,new Source(BUILTIN,"内置直播源","","",true));
        Source current=sources.get(active);if(current==null||!current.enabled)active=firstEnabled();publish();
    }
    /** Channel snapshots share read-only published lists; writers replace them atomically. */
    public List<Source> channelSnapshots(){List<Source> result=new ArrayList<>();for(Source s:readable.sources.values()){Source d=descriptor(s);d.channels=Collections.unmodifiableList(s.channels);result.add(d);}return result;}
    public List<Source> all()throws Exception{List<Source> a=new ArrayList<>();for(Source s:readable.sources.values())a.add(s.copy());return a;}
    public Source get(String id)throws Exception{Source s=readable.sources.get(id);return s==null?null:s.copy();}
    public Source current()throws Exception{ReadState r=readable;Source s=r.sources.get(r.active);return s==null?null:s.copy();}
    public String activeId(){return readable.active;}
    private String firstEnabled(){for(Source s:sources.values())if(s.enabled)return s.id;return BUILTIN;}
    private void persist()throws Exception{
        JSONArray a=new JSONArray();for(Source s:sources.values())a.put(s.json());
        byte[] bytes=new JSONObject().put("sources",a).put("active",active).put("favorites",favorites).put("history",history).toString().getBytes(StandardCharsets.UTF_8);
        if(bytes.length>16*1024*1024)throw new IOException("来源缓存过大，原配置已保留");
        File temp=new File(file.getParentFile(),file.getName()+".tmp");
        try(FileOutputStream out=new FileOutputStream(temp)){out.write(bytes);out.getFD().sync();}
        if(!temp.renameTo(file)){temp.delete();throw new IOException("无法保存直播配置，原配置已保留");}publish();
    }
    public synchronized void save(Source source)throws Exception{
        Source before=sources.get(source.id);sources.put(source.id,source.copy());
        try{persist();}catch(Exception e){if(before==null)sources.remove(source.id);else sources.put(source.id,before);throw e;}
    }
    /** Only a complete nonempty successful fetch replaces the channel cache. */
    public synchronized void update(String id,List<JSONObject> channels)throws Exception{
        if(channels.isEmpty())throw new IOException("来源没有可用频道，保留上次成功缓存");Source s=get(id);if(s==null)throw new IOException("来源已删除");s.channels=channels;s.updated=System.currentTimeMillis();save(s);
    }
    public synchronized void activate(String id)throws Exception{Source s=sources.get(id);if(s==null||!s.enabled)throw new IOException("该来源未启用");String old=active;active=id;try{persist();}catch(Exception e){active=old;throw e;}}
    public synchronized void setEnabled(String id,boolean enabled)throws Exception{
        Source s=get(id);if(s==null)return;if(s.builtin())throw new IOException("内置来源不能删除或停用");if(id.equals(active)&&!enabled)throw new IOException("请先选择其他来源，再停用当前来源");s.enabled=enabled;save(s);
    }
    public synchronized void delete(String id)throws Exception{
        if(BUILTIN.equals(id))throw new IOException("内置来源不能删除");if(id.equals(active))throw new IOException("请先选择其他来源，再删除当前来源");Source old=sources.remove(id);Object f=favorites.remove(id),h=history.remove(id);
        try{persist();}catch(Exception e){if(old!=null)sources.put(id,old);if(f!=null)favorites.put(id,f);if(h!=null)history.put(id,h);throw e;}
    }
    public boolean favorite(String source,String code){Set<String> a=readable.favorites.get(source);return a!=null&&a.contains(code);}
    public synchronized void toggleFavorite(String source,String code)throws Exception{Object old=favorites.opt(source);boolean found=favorite(source,code);JSONArray a=new JSONArray(),prev=favorites.optJSONArray(source);if(prev!=null)for(int i=0;i<prev.length();i++)if(!code.equals(prev.optString(i)))a.put(prev.optString(i));if(!found)a.put(code);favorites.put(source,a);try{persist();}catch(Exception e){if(old==null)favorites.remove(source);else favorites.put(source,old);throw e;}}
    public String last(String source){String h=readable.history.get(source);return h==null?"":h;}
    public synchronized void remember(String source,String code)throws Exception{if(code.equals(history.optString(source)))return;Object old=history.opt(source);history.put(source,code);try{persist();}catch(Exception e){if(old==null)history.remove(source);else history.put(source,old);throw e;}}

    public static void validateUrl(String address)throws IOException{
        try{URI u=new URI(address);if(!("http".equalsIgnoreCase(u.getScheme())||"https".equalsIgnoreCase(u.getScheme()))||u.getHost()==null||u.getUserInfo()!=null)throw new Exception();}
        catch(Exception e){throw new IOException("请输入有效的 HTTP(S) 地址");}
    }
    public static Download download(String address,int limit)throws Exception{
        validateUrl(address);String current=address;
        for(int redirect=0;redirect<6;redirect++){
            HttpURLConnection c=(HttpURLConnection)new URL(current).openConnection(Proxy.NO_PROXY);c.setInstanceFollowRedirects(false);c.setConnectTimeout(12000);c.setReadTimeout(20000);c.setRequestProperty("User-Agent","JellyLive/0.1 AndroidTV");c.setRequestProperty("Accept-Encoding","gzip");
            try{int status=c.getResponseCode();if(status>=300&&status<400){String loc=c.getHeaderField("Location");if(loc==null)throw new IOException("来源重定向缺少地址");current=new URL(new URL(current),loc).toString();validateUrl(current);continue;}
                if(status<200||status>=300)throw new IOException("来源请求失败 HTTP "+status);
                try(InputStream raw=c.getInputStream();PushbackInputStream head=new PushbackInputStream(raw,2)){
                    int a=head.read(),b=head.read();if(b>=0)head.unread(b);if(a>=0)head.unread(a);
                    InputStream content=(a==0x1f&&b==0x8b)?new GZIPInputStream(head):head;
                    return new Download(read(content,limit),current);
                }
            }finally{c.disconnect();}
        }throw new IOException("来源重定向次数过多");
    }
    public static String read(InputStream in,int limit)throws Exception{ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] b=new byte[8192];int n;while((n=in.read(b))!=-1){out.write(b,0,n);if(out.size()>limit)throw new IOException("来源内容过大");}return new String(out.toByteArray(),StandardCharsets.UTF_8);}
    private static final Pattern ATTR=Pattern.compile("([\\w-]+)\\s*=\\s*(?:\"([^\"]*)\"|'([^']*)'|([^\\s,]+))");
    private static Map<String,String> attributes(String line){Map<String,String>a=new HashMap<>();Matcher m=ATTR.matcher(line);while(m.find())a.put(m.group(1).toLowerCase(Locale.ROOT),m.group(2)!=null?m.group(2):m.group(3)!=null?m.group(3):m.group(4));return a;}
    private static int unquotedComma(String line){char quote=0;for(int i=0;i<line.length();i++){char c=line.charAt(i);if(quote!=0){if(c==quote)quote=0;}else if(c=='\''||c=='"')quote=c;else if(c==',')return i;}return -1;}
    private static String hash(String s)throws Exception{return BestvApi.hex(MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8))).substring(0,24);}
    public static List<JSONObject> parseM3u(String text,String base)throws Exception{
        if(text.startsWith("\ufeff"))text=text.substring(1);if(!text.trim().startsWith("#EXTM3U"))throw new IOException("内容不是有效的 M3U 播放列表");
        if(Pattern.compile("(?m)^#EXT-X-(?:TARGETDURATION|STREAM-INF|MEDIA-SEQUENCE|VERSION)").matcher(text).find())throw new IOException("这是视频流地址，请填写包含频道的 M3U 播放列表");
        List<JSONObject> result=new ArrayList<>();Set<String> ids=new HashSet<>();Map<String,String>a=new HashMap<>();String title="",group="";JSONObject headers=new JSONObject();
        for(String raw:text.split("\\r?\\n")){
            String line=raw.trim();if(line.isEmpty())continue;
            if(line.startsWith("#EXTINF:")){a=attributes(line);int comma=unquotedComma(line);title=comma<0?"":line.substring(comma+1).trim();group=a.getOrDefault("group-title","");headers=new JSONObject();continue;}
            if(line.startsWith("#EXTGRP:")){group=line.substring(8).trim();continue;}
            if(line.startsWith("#EXTVLCOPT:http-user-agent=")){headers.put("User-Agent",line.substring(line.indexOf('=')+1));continue;}
            if(line.startsWith("#EXTVLCOPT:http-referrer=")){headers.put("Referer",line.substring(line.indexOf('=')+1));continue;}
            if(line.startsWith("#"))continue;
            try{
                int pipe=line.indexOf('|');String address=pipe<0?line:line.substring(0,pipe);
                if(pipe>=0)for(String item:line.substring(pipe+1).split("&")){int eq=item.indexOf('=');if(eq<=0)continue;String k=URLDecoder.decode(item.substring(0,eq),"UTF-8"),v=URLDecoder.decode(item.substring(eq+1),"UTF-8");if(k.equalsIgnoreCase("User-Agent")||k.equalsIgnoreCase("Referer")||k.equalsIgnoreCase("Origin"))headers.put(k,v);}
                String url=new URI(base).resolve(address).toString();validateUrl(url);String name=title.isEmpty()?a.getOrDefault("tvg-name",""):title;if(name.isEmpty())name="频道 "+(result.size()+1);
                String epg=a.getOrDefault("tvg-id","");String id=hash(epg.isEmpty()?group+"\n"+name:epg);if(ids.contains(id))id=hash(id+url);if(!ids.add(id))continue;
                result.add(new JSONObject().put("ChannelCode",id).put("ChannelName",name).put("ChannelNo",a.getOrDefault("tvg-chno",Integer.toString(result.size()+1))).put("CategoryName",group.isEmpty()?"未分组":group).put("Url",url).put("Logo",a.getOrDefault("tvg-logo","")).put("EpgId",epg).put("Headers",new JSONObject(headers.toString())));
            }catch(URISyntaxException|IOException ignored){/* Unsupported stream schemes are omitted, never launched. */}
            finally{title="";a=new HashMap<>();headers=new JSONObject();group="";}
            if(result.size()>20000)throw new IOException("频道数量超过上限");
        }
        if(result.isEmpty())throw new IOException("M3U 中没有可播放的 HTTP(S) 频道");return result;
    }
    public static List<Program> parseXmltv(String xml)throws Exception{
        // Reject DTDs before SAX so Android parsers cannot expand entities or make external requests.
        if(xml.toUpperCase(Locale.ROOT).contains("<!DOCTYPE")||xml.toUpperCase(Locale.ROOT).contains("<!ENTITY"))throw new IOException("节目单不能包含外部实体或 DTD");
        Map<String,List<String>> aliases=new HashMap<>();List<Program> list=new ArrayList<>();SAXParserFactory f=SAXParserFactory.newInstance();f.setNamespaceAware(false);
        XMLReader reader=f.newSAXParser().getXMLReader();reader.setEntityResolver((publicId,systemId)->{throw new SAXException("不允许外部实体");});
        reader.setContentHandler(new DefaultHandler(){String channel="",title="",desc="",capture="",definition="";long start,end;StringBuilder value=new StringBuilder();
            @Override public void startElement(String uri,String local,String q,Attributes a)throws SAXException{if(q.equals("channel"))definition=a.getValue("id");if(q.equals("programme")){channel=a.getValue("channel");start=xmlTime(a.getValue("start"));end=xmlTime(a.getValue("stop"));title="";desc="";}if(q.equals("title")||q.equals("desc")||(q.equals("display-name")&&definition!=null&&!definition.isEmpty())){capture=q;value.setLength(0);}}
            @Override public void characters(char[] ch,int s,int length){if(!capture.isEmpty()&&value.length()<32768)value.append(ch,s,length);}
            @Override public void endElement(String uri,String local,String q)throws SAXException{if(q.equals(capture)){if(q.equals("title")&&title.isEmpty())title=value.toString().trim();if(q.equals("desc")&&desc.isEmpty())desc=value.toString().trim();if(q.equals("display-name"))aliases.computeIfAbsent(definition,k->new ArrayList<>()).add(value.toString().trim());capture="";}if(q.equals("channel"))definition="";if(q.equals("programme")&&channel!=null&&start>0&&end>start&&!title.isEmpty()){list.add(new Program(channel,title,desc,start,end,aliases.computeIfAbsent(channel,k->new ArrayList<>())));if(list.size()>150000)throw new SAXException("节目数量超过上限");}}
        });
        reader.parse(new InputSource(new StringReader(xml)));list.sort(Comparator.comparingLong(p->p.start));return list;
    }
    static long xmlTime(String raw){if(raw==null)return 0;Matcher m=Pattern.compile("^(\\d{14})(?:\\s*([+-]\\d{4}|Z))?").matcher(raw.trim());if(!m.find())return 0;try{SimpleDateFormat f=new SimpleDateFormat("yyyyMMddHHmmss Z",Locale.ROOT);f.setLenient(false);f.setTimeZone(TimeZone.getTimeZone("UTC"));return f.parse(m.group(1)+" "+(m.group(2)==null||m.group(2).equals("Z")?"+0000":m.group(2))).getTime();}catch(Exception e){return 0;}}
    public static List<Program> bestvPrograms(String code,JSONArray a){List<Program> p=new ArrayList<>();for(int i=0;i<a.length();i++){JSONObject j=a.optJSONObject(i);if(j==null)continue;String title=first(j,"ProgramName","Name","Title","EventName");long start=bestvTime(first(j,"StartTime","StartDateTime","BeginTime")),end=bestvTime(first(j,"EndTime","EndDateTime","StopTime"));if(start>0&&end>start&&!title.isEmpty())p.add(new Program(code,title,first(j,"Description","Introduce","ProgramDesc"),start,end));}p.sort(Comparator.comparingLong(v->v.start));return p;}
    private static String first(JSONObject j,String... keys){for(String k:keys){String v=j.optString(k,"");if(!v.isEmpty())return v;}return "";}
    private static long bestvTime(String s){try{if(s.matches("\\d{10}|\\d{13}")){long n=Long.parseLong(s);return s.length()==10?n*1000:n;}SimpleDateFormat f=new SimpleDateFormat(s.matches("\\d{14}")?"yyyyMMddHHmmss":"yyyy-MM-dd HH:mm:ss",Locale.ROOT);f.setLenient(false);f.setTimeZone(TimeZone.getTimeZone("Asia/Shanghai"));return f.parse(s).getTime();}catch(Exception e){return 0;}}
}
