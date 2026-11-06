import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.util.concurrent.atomic.AtomicReference;
import okhttp3.*;
import org.jellyfin.sdk.api.client.*;
import org.jellyfin.sdk.api.okhttp.OkHttpFactory;
import org.jellyfin.sdk.api.client.util.ApiSerializer;
import org.jellyfin.sdk.model.*;
import org.jellyfin.sdk.model.api.BaseItemDto;
import org.jellyfin.androidtv.bestv.*;
import org.jellyfin.androidtv.util.sdk.*;

/** Real HTTP hops and real SDK DTOs; no device or external service required. */
public class LibraryImageCheck {
 static void check(boolean ok,String name){if(!ok)throw new AssertionError(name);System.out.println("PASS "+name);}
 static BaseItemDto item(String json){return ApiSerializer.INSTANCE.getJson().decodeFromString(BaseItemDto.Companion.serializer(),"{\"Id\":\"00000000-0000-0000-0000-000000000001\",\"Type\":\"Movie\""+(json.length()>2?","+json.substring(1):"}"));}
 public static void main(String[] args)throws Exception {
  HttpServer a=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0),b=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
  String origin="http://127.0.0.1:"+a.getAddress().getPort(),other="http://127.0.0.1:"+b.getAddress().getPort();
  AtomicReference<String> received=new AtomicReference<>(),legacyHeader=new AtomicReference<>();
  a.createContext("/",e->{if(e.getRequestURI().getPath().equals("/redirect")){e.getResponseHeaders().add("Location",other+"/image");e.sendResponseHeaders(302,-1);}else if(e.getRequestURI().getPath().equals("/local")){e.getResponseHeaders().add("Location",origin+"/image");e.sendResponseHeaders(302,-1);}else{received.set(e.getRequestHeaders().getFirst("Authorization"));legacyHeader.set(e.getRequestHeaders().getFirst("X-Emby-Authorization"));e.sendResponseHeaders(204,-1);}e.close();});
  b.createContext("/",e->{received.set(e.getRequestHeaders().getFirst("Authorization"));legacyHeader.set(e.getRequestHeaders().getFirst("X-Emby-Authorization"));e.sendResponseHeaders(204,-1);e.close();});a.start();b.start();
  LegacyAuthorizationCompatibility legacy=new LegacyAuthorizationCompatibility();
  OkHttpClient base=new OkHttpClient.Builder().addInterceptor(legacy).addNetworkInterceptor(legacy.getNetworkHeaders()).build();
  try {
   HttpClientOptions options=null;for(var ctor:HttpClientOptions.class.getDeclaredConstructors())if(ctor.getParameterCount()==7){ctor.setAccessible(true);options=(HttpClientOptions)ctor.newInstance(false,0L,0L,0L,null,31,null);break;}
   OkHttpFactory factory=new OkHttpFactory(base);ClientInfo info=new ClientInfo("JellyLive","test");DeviceInfo device=new DeviceInfo("fixture","Fixture");
   ApiClient api=factory.create(origin,"fixture-a",info,device,options,factory);OkHttpClient images=LibraryImageHttp.INSTANCE.client(api,factory,options);
   for(String path:new String[]{"/image","/local"}) {try(okhttp3.Response r=images.newCall(new Request.Builder().url(origin+path).build()).execute()){check(r.code()==204 && received.get()!=null && received.get().contains("fixture-a"),"current session on "+path);}}
   try(okhttp3.Response r=images.newCall(new Request.Builder().url(origin+"/redirect").build()).execute()){check(received.get()==null && legacyHeader.get()==null,"redirect strips authorization");}
   try(okhttp3.Response r=images.newCall(new Request.Builder().url(other+"/image").header("X-Emby-Token","stale").build()).execute()){check(received.get()==null && legacyHeader.get()==null,"external images have no server authorization");}
   api.update(other,"fixture-b",info,device);
   try(okhttp3.Response r=images.newCall(new Request.Builder().url(other+"/image").build()).execute()){check(received.get()!=null&&received.get().contains("fixture-b"),"same Coil client follows new server session");}
   try(okhttp3.Response r=images.newCall(new Request.Builder().url(origin+"/image").build()).execute()){check(received.get()==null,"old server no longer receives credentials");}
   check(LibraryMetadata.INSTANCE.year(item("{\"PremiereDate\":\"2021-05-03T00:00:00Z\"}"))==2021,"premiere year fallback");
   check(LibraryMetadata.INSTANCE.year(item("{\"ProductionYear\":2020,\"PremiereDate\":\"2021-05-03T00:00:00Z\"}"))==2020,"production year preferred");
   check(LibraryMetadata.INSTANCE.year(item("{}"))==null,"unknown year stays absent");
   check("8.6".equals(LibraryMetadata.INSTANCE.rating(item("{\"CommunityRating\":8.6}"))),"real community rating");
   check(LibraryMetadata.INSTANCE.rating(item("{\"CommunityRating\":0}"))==null,"unknown rating stays absent");
  } finally {a.stop(0);b.stop(0);base.dispatcher().executorService().shutdownNow();base.connectionPool().evictAll();}
 }
}
