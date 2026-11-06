/* Modified 2026-11-02: read-only network ADB importer for user's own AHYD box. GPL-2.0. */
package org.jellyfin.androidtv.bestv;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Base64;
import org.json.JSONObject;
import java.io.*;
import java.net.*;
import java.nio.*;
import java.nio.charset.StandardCharsets;
import java.math.BigInteger;
import java.security.*;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.*;
import java.util.*;
import java.util.regex.*;
import javax.crypto.Cipher;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.*;

public final class AdbImporter {
    private static final int CNXN=0x4e584e43,AUTH=0x48545541,OPEN=0x4e45504f,OKAY=0x59414b4f,WRTE=0x45545257,CLSE=0x45534c43;
    private static KeyPair keys(Context context)throws Exception{
        SharedPreferences p=context.getSharedPreferences("bestv_adb",Context.MODE_PRIVATE);String pri=p.getString("private",null),pub=p.getString("public",null);KeyFactory f=KeyFactory.getInstance("RSA");
        if(pri!=null&&pub!=null)return new KeyPair(f.generatePublic(new X509EncodedKeySpec(Base64.decode(pub,Base64.DEFAULT))),f.generatePrivate(new PKCS8EncodedKeySpec(Base64.decode(pri,Base64.DEFAULT))));
        KeyPairGenerator g=KeyPairGenerator.getInstance("RSA");g.initialize(2048);KeyPair k=g.generateKeyPair();p.edit().putString("private",Base64.encodeToString(k.getPrivate().getEncoded(),Base64.NO_WRAP)).putString("public",Base64.encodeToString(k.getPublic().getEncoded(),Base64.NO_WRAP)).apply();return k;
    }
    private static byte[] little(BigInteger value,int size){byte[] out=new byte[size],b=value.toByteArray();for(int i=0;i<size&&i<b.length;i++)out[i]=b[b.length-1-i];return out;}
    private static byte[] publicKey(KeyPair k){RSAPublicKey p=(RSAPublicKey)k.getPublic();BigInteger n=p.getModulus(),mod=BigInteger.ONE.shiftLeft(32);ByteBuffer b=ByteBuffer.allocate(524).order(ByteOrder.LITTLE_ENDIAN);b.putInt(64).putInt(n.modInverse(mod).negate().mod(mod).intValue()).put(little(n,256)).put(little(BigInteger.ONE.shiftLeft(4096).mod(n),256)).putInt(p.getPublicExponent().intValue());return (Base64.encodeToString(b.array(),Base64.NO_WRAP)+" bestv@android\0").getBytes(StandardCharsets.UTF_8);}
    private static final class Connection implements AutoCloseable {
        Socket socket;DataInputStream in;OutputStream out;KeyPair key;
        Connection(String host,int port,KeyPair key)throws Exception{this.key=key;socket=new Socket();socket.connect(new InetSocketAddress(host,port),5000);socket.setSoTimeout(18000);in=new DataInputStream(socket.getInputStream());out=socket.getOutputStream();send(CNXN,0x01000000,4096,"host::\0".getBytes(StandardCharsets.UTF_8));int tries=0;
            while(true){Packet p=read();if(p.command==CNXN)break;if(p.command!=AUTH||p.a!=1)throw new IOException("ADB 握手失败");if(tries++==0){byte[] prefix={0x30,0x21,0x30,0x09,0x06,0x05,0x2b,0x0e,0x03,0x02,0x1a,0x05,0x00,0x04,0x14};ByteArrayOutputStream raw=new ByteArrayOutputStream();raw.write(prefix);raw.write(p.body);Cipher rsa=Cipher.getInstance("RSA/ECB/PKCS1Padding");rsa.init(Cipher.ENCRYPT_MODE,key.getPrivate());send(AUTH,2,0,rsa.doFinal(raw.toByteArray()));}else if(tries==2)send(AUTH,3,0,publicKey(key));else throw new IOException("请在机顶盒上允许此电视的 ADB 调试授权，再重试");}}
        void send(int command,int a,int b,byte[] body)throws IOException{int sum=0;for(byte v:body)sum+=v&255;ByteBuffer h=ByteBuffer.allocate(24).order(ByteOrder.LITTLE_ENDIAN);h.putInt(command).putInt(a).putInt(b).putInt(body.length).putInt(sum).putInt(command^0xffffffff);out.write(h.array());out.write(body);out.flush();}
        Packet read()throws IOException{byte[] header=new byte[24];in.readFully(header);ByteBuffer b=ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN);int command=b.getInt(),a=b.getInt(),remote=b.getInt(),length=b.getInt(),sum=b.getInt(),magic=b.getInt();if(length<0||length>1024*1024||magic!=(command^0xffffffff))throw new IOException("ADB 数据无效");byte[] data=new byte[length];in.readFully(data);return new Packet(command,a,remote,data);}
        String shell(String command)throws Exception{send(OPEN,1,0,("shell:"+command+"\0").getBytes(StandardCharsets.UTF_8));ByteArrayOutputStream bytes=new ByteArrayOutputStream();while(true){Packet p=read();if(p.command==WRTE){bytes.write(p.body);send(OKAY,1,p.a,new byte[0]);if(bytes.size()>2*1024*1024)throw new IOException("设备响应过大");}else if(p.command==CLSE){send(CLSE,1,p.a,new byte[0]);return bytes.toString("UTF-8").trim();}else if(p.command!=OKAY)throw new IOException("ADB 读取失败");}}
        public void close()throws IOException{if(socket!=null)socket.close();}
    }
    private static final class Packet{int command,a,b;byte[] body;Packet(int c,int a,int b,byte[] d){command=c;this.a=a;this.b=b;body=d;}}
    private static String shell(String host,int port,KeyPair k,String cmd)throws Exception{try(Connection c=new Connection(host,port,k)){return c.shell(cmd);}}
    private static Properties properties(String text)throws Exception{Properties p=new Properties();p.load(new StringReader(text));return p;}
    public static JSONObject read(Context context,String ip,int port)throws Exception{
        if(!ip.matches("[0-9]{1,3}(\\.[0-9]{1,3}){3}")||port<1||port>65535)throw new IOException("请填写有效的 IPv4 地址和端口");InetAddress.getByName(ip);KeyPair key=keys(context);
        if(!shell(ip,port,key,"id").contains("uid=0"))throw new IOException("ADB shell 非 root，无法读取 App 私有配置；请导入 JSON 或手动录入");
        if(!shell(ip,port,key,"pm path com.bestv.ott.baseservices").startsWith("package:"))throw new IOException("该设备未找到原百视通 App");
        String app="/data/data/com.bestv.ott.baseservices/";JSONObject terminal=new JSONObject(shell(ip,port,key,"cat "+app+"files/terminal.json"));
        Properties user=properties(shell(ip,port,key,"cat "+app+"files/cus_config/user.properties")),sys=properties(shell(ip,port,key,"cat "+app+"files/cus_config/sys.properties")),auth=properties(shell(ip,port,key,"cat "+app+"files/cus_config/bestvauth.properties"));
        String mac=shell(ip,port,key,"cat /sys/class/net/eth0/address").replace(":","").toUpperCase(Locale.ROOT);if(!mac.matches("[A-F0-9]{12}"))throw new IOException("无法读取设备 MAC");
        Matcher m=Pattern.compile("versionName=([^\\r\\n]+)").matcher(shell(ip,port,key,"dumpsys package com.bestv.ott.baseservices"));String version=m.find()?m.group(1).trim():"";if(!version.contains("AHYD"))throw new IOException("自动导入目前适配安徽移动版；其他地区请手动配置");
        String sn=sys.getProperty("BESTV_OEM_SN","");if(sn.isEmpty())sn=sys.getProperty("BESTV_OEM_SN_PREFIX","AHYDFF")+mac;
        String xml=shell(ip,port,key,"cat "+app+"shared_prefs/BesTVPrefsFile.xml");DocumentBuilderFactory factory=DocumentBuilderFactory.newInstance();factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl",true);Document doc=factory.newDocumentBuilder().parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));String cp="";NodeList nodes=doc.getDocumentElement().getChildNodes();for(int i=0;i<nodes.getLength();i++){Node n=nodes.item(i);if(n instanceof Element&&"CpCode".equals(((Element)n).getAttribute("name")))cp=n.getTextContent();}
        JSONObject c=new JSONObject();c.put("user_id",terminal.optString("UserID"));c.put("user_account",user.getProperty("UserAccount",""));c.put("user_password",user.getProperty("UserPassword",""));c.put("mac",mac);c.put("stb_id",sn);c.put("tvid","BESTVOEM$"+sys.getProperty("BESTV_TERMINAL_TYPE","AHYDFF_BESTVINSIDE")+"$"+sn);c.put("ip_address",ip);c.put("product_model",shell(ip,port,key,"getprop ro.product.model"));c.put("tv_profile","FirmwareVersion/"+version);c.put("cp_code",cp);c.put("user_group",terminal.optString("UserGroup"));c.put("login_url",auth.getProperty("login_url",terminal.optString("AAASrvAddress").replaceAll("/+$","")+"/OttService/Login"));return BestvApi.normalize(c);
    }
}
