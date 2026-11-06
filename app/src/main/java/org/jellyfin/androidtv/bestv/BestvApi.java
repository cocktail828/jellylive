/* Modified 2026-11-02: standalone BesTV live source. GPL-2.0, see root LICENSE. */
package org.jellyfin.androidtv.bestv;

import org.json.*;
import java.net.*;
import java.io.*;
import java.security.*;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.*;
import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import android.util.Base64;

public final class BestvApi {
    public static final String[][] FIELDS={
        {"user_id","UserID（平台编号）",""},{"user_account","绑定账号",""},{"mac","原机顶盒 MAC",""},
        {"tvid","TVID",""},{"stb_id","STBID",""},{"ip_address","原机顶盒 IP",""},
        {"product_model","原机顶盒型号",""},{"tv_profile","设备版本描述",""},
        {"user_password","账号密码（原配置为空则留空）",""},{"permanent_key","认证密钥（原配置为空则留空）",""},
        {"cp_code","CpCode",""},{"user_group","用户分组",""},
        {"login_url","登录地址","http://ahydaaa-idc.bbtv.cn/tv/OttService/Login"},
        {"epg_url","节目单地址","https://ahydreplayepgidc.bbtv.cn/APPService/index.php"},
        {"channel_code","客户端渠道","AJYDJAK"},{"app_code","客户端标识","AHJK"}};
    private JSONObject config, profile; private long loginAt;
    public BestvApi(JSONObject c) throws Exception {config=normalize(c);}
    public static JSONObject normalize(JSONObject c) throws Exception {
        if(c.has("credentials"))c=c.getJSONObject("credentials");else if(c.has("Credentials"))c=c.getJSONObject("Credentials");
        JSONObject result=new JSONObject();
        for(String[] f:FIELDS){String value=f[2];Iterator<String> keys=c.keys();while(keys.hasNext()){String key=keys.next();if(key.replace("_","").equalsIgnoreCase(f[0].replace("_",""))){value=c.optString(key,"");break;}}result.put(f[0],value);}
        return result;
    }
    private String c(String key){return config.optString(key,"");}
    public static String hex(byte[] bytes){StringBuilder b=new StringBuilder();for(byte x:bytes)b.append(String.format(Locale.ROOT,"%02x",x&255));return b.toString();}
    private static void add(Map<String,String> f,String k,String v){f.put(k,v);}
    public synchronized void login() throws Exception {
        for(String name:new String[]{"user_id","tvid","stb_id","tv_profile"})if(c(name).trim().isEmpty())throw new IOException("请先在设置中填写 "+name);
        String mac=c("mac").replace(":","").replace("-","").toUpperCase(Locale.ROOT);
        if(!mac.matches("[0-9A-F]{12}"))throw new IOException("原机顶盒 MAC 格式无效");
        String key=c("permanent_key");if(key.isEmpty())key=hex(MessageDigest.getInstance("SHA-1").digest(c("user_id").getBytes(StandardCharsets.UTF_8))).toUpperCase(Locale.ROOT);
        byte[] rawKey=key.getBytes(StandardCharsets.UTF_8);if(rawKey.length<24)throw new IOException("认证密钥长度无效");
        SimpleDateFormat date=new SimpleDateFormat("yyyyMMddhhmmss",Locale.ROOT);date.setTimeZone(TimeZone.getTimeZone("Asia/Shanghai"));
        String plain=new SecureRandom().nextInt(100000000)+"$"+c("user_id")+"$"+c("tvid")+"$"+c("ip_address")+"$$"+date.format(new Date())+"$BesTV";
        Cipher cipher=Cipher.getInstance("DESede/ECB/PKCS5Padding");cipher.init(Cipher.ENCRYPT_MODE,new SecretKeySpec(Arrays.copyOf(rawKey,24),"DESede"));
        Map<String,String> f=new LinkedHashMap<>();
        add(f,"UserID",c("user_id"));add(f,"TVID",c("tvid"));add(f,"TV_ID",c("tvid"));add(f,"STBID",c("stb_id"));
        add(f,"TVProfile",c("tv_profile"));add(f,"TV_PROFILE",c("tv_profile"));add(f,"UserGroup",c("user_group"));add(f,"ApkCode",c("app_code"));
        add(f,"BCTIVersion","1201");add(f,"Authenticator",hex(cipher.doFinal(plain.getBytes(StandardCharsets.UTF_8))));
        add(f,"MAC",Base64.encodeToString(mac.getBytes(StandardCharsets.UTF_8),Base64.NO_WRAP)+"\n");add(f,"productModel",c("product_model"));
        add(f,"UserAccount",c("user_account"));add(f,"UserPassword",c("user_password"));add(f,"CpCode",c("cp_code"));add(f,"ChannelCode",c("channel_code"));
        for(String n:new String[]{"OSProfile","ExtendParam","BmsAuthAddress","BmsUserGroup","BmsUserToken","Province","City","AccountIdentity"})add(f,n,"");
        add(f,"OSVerSdk","19");add(f,"OSModel",c("product_model"));add(f,"ApkVersionCode","1016005");add(f,"OSSdkVer","19");add(f,"OSReleaseVer","4.4.2");
        JSONObject p=send(c("login_url"),f,true);
        if(!c("user_id").equals(p.optString("UserID"))||p.optString("UserToken").isEmpty())throw new IOException("登录返回的账号或令牌无效");
        profile=p;loginAt=System.currentTimeMillis();
    }
    private synchronized JSONObject request(Map<String,String> fields,boolean play) throws Exception {
        for(int attempt=0;attempt<2;attempt++){
            if(profile==null||System.currentTimeMillis()-loginAt>3600000L)login();
            Map<String,String> f=new LinkedHashMap<>(fields);add(f,"UserID",c("user_id"));add(f,"UserToken",profile.optString("UserToken"));add(f,"UserGroup",profile.optString("UserGroup"));
            String url=play?profile.optString("PlaySrvAddress").replaceAll("/+$","")+"/OttService/Play":c("epg_url");
            try{return send(url,f,play);}catch(BackendError e){if(attempt==0&&e.expired){profile=null;continue;}throw e;}
        }throw new IOException("重新登录后请求仍失败");
    }
    public List<JSONObject> channels() throws Exception {
        Map<String,String> f=new LinkedHashMap<>();add(f,"controller","live");add(f,"action","QueryCover");add(f,"Version","v2");add(f,"PageSize","200");
        JSONArray cats=request(f,false).getJSONArray("Categorys");List<JSONObject> result=new ArrayList<>();Set<String> seen=new HashSet<>();
        for(int i=0;i<cats.length();i++){JSONObject cat=cats.getJSONObject(i);JSONArray items=cat.optJSONArray("Channels");if(items==null)continue;
            for(int j=0;j<items.length();j++){JSONObject ch=items.optJSONObject(j);if(ch==null)continue;String code=ch.optString("ChannelCode");if(code.isEmpty()||"0".equals(ch.optString("IsShow"))||!seen.add(code))continue;JSONObject copy=new JSONObject(ch.toString());copy.put("CategoryName",cat.optString("Name",cat.optString("CategoryName")));result.add(copy);}}
        return result;
    }
    public String play(String code) throws Exception {
        Map<String,String> f=new LinkedHashMap<>();add(f,"ItemType","2");add(f,"ItemCode",code);add(f,"SupportHLS","1");JSONObject data=request(f,true);
        if(data.optInt("TrySeeTime",0)>0)throw new IOException("此频道只提供试看，请选择已授权频道");String url=data.optString("PlayURL");if(!url.startsWith("http://")&&!url.startsWith("https://"))throw new IOException("没有取得播放地址");return url;
    }
    public JSONArray schedule(String code) throws Exception {
        SimpleDateFormat date=new SimpleDateFormat("yyyy-MM-dd",Locale.ROOT);date.setTimeZone(TimeZone.getTimeZone("Asia/Shanghai"));String today=date.format(new Date());
        Map<String,String> f=new LinkedHashMap<>();add(f,"controller","live");add(f,"action","QueryChannelSchedule");add(f,"Version","1.0");add(f,"ChannelCode",code);add(f,"StartDate",today+" 00:00:00");add(f,"EndDate",today+" 23:59:59");add(f,"PageSize","200");JSONObject data=request(f,false);JSONObject items=data.optJSONObject("Items");JSONArray list=items==null?null:items.optJSONArray(code);return list==null?new JSONArray():list;
    }
    private static final class BackendError extends IOException {final boolean expired;BackendError(String code,String message){super("后台返回错误码 "+code);String m=message.toLowerCase(Locale.ROOT);expired=m.contains("token")||m.contains("过期")||m.contains("失效")||m.contains("重新登录")||m.contains("未登录");}}
    public static JSONObject send(String url,Map<String,String> fields,boolean post) throws Exception {
        StringBuilder form=new StringBuilder();for(Map.Entry<String,String> e:fields.entrySet()){if(form.length()>0)form.append('&');form.append(URLEncoder.encode(e.getKey(),"UTF-8")).append('=').append(URLEncoder.encode(e.getValue(),"UTF-8"));}
        String response=fetch(post?url:url+(url.contains("?")?"&":"?")+form,post?form.toString():null);
        JSONObject envelope=new JSONObject(response).getJSONObject("Response"),header=envelope.getJSONObject("Header");if(!"0".equals(header.optString("RC")))throw new BackendError(header.optString("RC"),header.optString("RM"));return envelope.getJSONObject("Body");
    }
    public static String fetch(String url,String body) throws Exception {
        URL u=new URL(url);if(!u.getProtocol().equals("http")&&!u.getProtocol().equals("https"))throw new IOException("仅支持 HTTP/HTTPS 地址");
        HttpURLConnection con=(HttpURLConnection)u.openConnection(Proxy.NO_PROXY);con.setConnectTimeout(12000);con.setReadTimeout(20000);con.setRequestProperty("User-Agent","BestvLive/0.1 AndroidTV");
        try{if(body!=null){con.setRequestMethod("POST");con.setDoOutput(true);con.setRequestProperty("Content-Type","application/x-www-form-urlencoded");try(OutputStream out=con.getOutputStream()){out.write(body.getBytes(StandardCharsets.UTF_8));}}
            if(con.getResponseCode()<200||con.getResponseCode()>=300)throw new IOException("网络请求失败 HTTP "+con.getResponseCode());
            try(InputStream in=con.getInputStream();ByteArrayOutputStream out=new ByteArrayOutputStream()){byte[] buffer=new byte[8192];int n;while((n=in.read(buffer))>=0){out.write(buffer,0,n);if(out.size()>8*1024*1024)throw new IOException("响应内容过大");}return out.toString("UTF-8");}
        }finally{con.disconnect();}
    }
}
