/* Owner-editable box credentials. GPL-2.0. */
package org.jellyfin.androidtv.bestv;
import android.app.*;import android.os.*;import android.content.*;import android.graphics.Color;import android.view.*;import android.widget.*;import android.text.InputType;import org.json.*;import java.io.*;import java.util.*;import java.util.concurrent.*;
public final class BoxConfigActivity extends Activity {
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private android.content.SharedPreferences prefs;private JSONObject config;private String pendingExport;
    private AlertDialog settingsDialog;private Map<String,EditText> fields;private TextView configStatus;
    @Override public void onCreate(Bundle state){super.onCreate(state);getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,WindowManager.LayoutParams.FLAG_FULLSCREEN);prefs=getSharedPreferences("bestv_live",MODE_PRIVATE);try{config=BestvApi.normalize(new JSONObject(prefs.getString("config","{}")));}catch(Exception e){config=new JSONObject();}TvUi ui=new TvUi(this);android.widget.FrameLayout page=ui.canvas();setContentView(page);ui.header(page,"内置源机顶盒配置","导入、修改与导出自己的设备资料",()->finish());showSettings();if(state!=null){pendingExport=state.getString("pending_export");try{String draft=state.getString("settings_draft");if(draft!=null)fill(BestvApi.normalize(new JSONObject(draft)));}catch(Exception ignored){}}}
    private int dp(int x){return Math.round(getResources().getDisplayMetrics().density*x);}
    private TextView label(String s,int size){TextView t=new TextView(this);t.setText(s);t.setTextColor(Color.WHITE);t.setTextSize(size);t.setPadding(dp(10),dp(5),dp(10),dp(5));return t;}
    private Button button(String text,Runnable action){Button b=new TvUi(this).button(text,action);b.setMinimumHeight(dp(44));return b;}
    private interface Task{void run()throws Exception;}
    private void background(Task task){worker.execute(()->{try{task.run();}catch(Exception e){runOnUiThread(()->{if(!isDestroyed()&&configStatus!=null)configStatus.setText("操作失败，请检查配置和网络。");});}});}
    private void message(String s){runOnUiThread(()->{if(!isDestroyed()&&configStatus!=null)configStatus.setText(s);});}
    private EditText input(LinearLayout box,String label,String value,boolean secret){box.addView(label(label,15));EditText e=new EditText(this);e.setSingleLine(true);e.setTextColor(Color.WHITE);e.setText(value);e.setSelectAllOnFocus(true);e.setInputType(secret?InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_PASSWORD:InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);box.addView(e,new LinearLayout.LayoutParams(-1,dp(46)));return e;}
    private JSONObject collect() throws Exception {JSONObject c=new JSONObject();for(String[] f:BestvApi.FIELDS){String value=fields.get(f[0]).getText().toString();c.put(f[0],f[0].equals("user_password")||f[0].equals("permanent_key")?value:value.trim());}return c;}
    private void fill(JSONObject c){if(fields==null)showSettings();for(String[] f:BestvApi.FIELDS)fields.get(f[0]).setText(c.optString(f[0],f[2]));configStatus.setText("已导入，尚未保存。可修改后测试、保存。");}
    private void showSettings(){
        ScrollView scroll=new ScrollView(this);
        LinearLayout form=new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(dp(18),dp(8),dp(18),dp(16));
        form.setBackgroundColor(Color.rgb(24,30,40));
        scroll.addView(form);
        form.addView(label("已有内置或保存的配置时无需重新填写。更换机顶盒建议直接导入。",16));
        LinearLayout imports=new LinearLayout(this);
        imports.addView(button("从机顶盒导入",()->importBox()),new LinearLayout.LayoutParams(0,-2,1));
        imports.addView(button("导入配置文件",()->{
            try{startActivityForResult(new Intent(Intent.ACTION_OPEN_DOCUMENT).setType("*/*").addCategory(Intent.CATEGORY_OPENABLE),51);}
            catch(ActivityNotFoundException e){configStatus.setText("电视没有文件选择器，请使用从机顶盒导入或手动录入。");}
        }),new LinearLayout.LayoutParams(0,-2,1));
        imports.addView(button("导出配置文件",()->exportConfig()),new LinearLayout.LayoutParams(0,-2,1));
        form.addView(imports);
        fields=new LinkedHashMap<>();
        form.addView(label("常用信息",18));
        for(String[] f:BestvApi.FIELDS){
            if(!f[0].equals("user_id")&&!f[0].equals("mac"))continue;
            fields.put(f[0],input(form,f[1],config.optString(f[0],f[2]),false));
        }
        form.addView(label("设备标识、版本和服务地址已预填，通常无需修改。",15));
        LinearLayout advanced=new LinearLayout(this);
        advanced.setOrientation(LinearLayout.VERTICAL);
        advanced.setVisibility(View.GONE);
        Button toggle=button("展开高级参数",()->{});
        toggle.setOnClickListener(v->{boolean show=advanced.getVisibility()!=View.VISIBLE;advanced.setVisibility(show?View.VISIBLE:View.GONE);toggle.setText(show?"收起高级参数":"展开高级参数");});
        form.addView(toggle);
        for(String[] f:BestvApi.FIELDS){
            if(fields.containsKey(f[0]))continue;
            fields.put(f[0],input(advanced,f[1],config.optString(f[0],f[2]),f[0].equals("user_password")||f[0].equals("permanent_key")));
        }
        advanced.addView(label("账号密码、认证密钥：原配置为空时保持为空。用户分组、设备参数及服务地址随导入保存。",15));
        advanced.addView(button("按安徽版规则补全 TVID / STBID",()->{
            String mac=fields.get("mac").getText().toString().replace(":", "").replace("-", "").toUpperCase(Locale.ROOT);
            if(!mac.matches("[0-9A-F]{12}")){configStatus.setText("MAC 格式无效");return;}
            fields.get("stb_id").setText("AHYDFF"+mac);
            fields.get("tvid").setText("BESTVOEM$AHYDFF_BESTVINSIDE$AHYDFF"+mac);
            configStatus.setText("已补全，请核对并测试。");
        }));
        form.addView(advanced);
        configStatus=label("导入后可修改；点击保存才生效。导出的是当前填写值。",15);
        form.addView(configStatus);
        form.addView(button("测试登录与频道",()->{
            try{JSONObject draft=collect();configStatus.setText("正在测试……");background(()->{List<JSONObject> items=new BestvApi(draft).channels();runOnUiThread(()->configStatus.setText("登录成功，获取 "+items.size()+" 个可见频道。尚未保存。"));});}
            catch(Exception e){configStatus.setText("配置格式无效");}
        }));
        form.addView(button("保存配置并进入直播",()->{
            try{JSONObject draft=collect();new BestvApi(draft);config=draft;prefs.edit().putString("config",draft.toString()).apply();setResult(RESULT_OK);settingsDialog.dismiss();finish();}
            catch(Exception e){configStatus.setText("配置格式无效");}
        }));
        settingsDialog=new AlertDialog.Builder(this).setTitle("直播设置").setView(scroll).setNegativeButton("取消",null).create();
        settingsDialog.setOnDismissListener(d->{if(!isFinishing())finish();});
        settingsDialog.show();
        settingsDialog.getWindow().setLayout(Math.min(dp(760),getResources().getDisplayMetrics().widthPixels-dp(40)),getResources().getDisplayMetrics().heightPixels-dp(40));
    }
    private void exportConfig(){
        try{
            pendingExport=collect().toString(2);
            String name="bestv-live-"+new java.text.SimpleDateFormat("yyyyMMdd-HHmmss",Locale.ROOT).format(new Date())+".json";
            Intent intent=new Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("application/json").putExtra(Intent.EXTRA_TITLE,name);
            try{startActivityForResult(intent,52);}
            catch(ActivityNotFoundException e){
                String data=pendingExport;pendingExport=null;
                background(()->{
                    File folder=getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS);
                    if(folder==null)throw new IOException("存储不可用，无法导出配置");
                    if(!folder.isDirectory()&&!folder.mkdirs())throw new IOException("无法创建导出目录");
                    File file=new File(folder,name);
                    try(OutputStream out=new FileOutputStream(file)){out.write(data.getBytes(java.nio.charset.StandardCharsets.UTF_8));}
                    exportMessage("已导出到：\n"+file.getAbsolutePath()+"\n可通过电视文件管理复制。配置包含账号资料，请妥善保存。");
                });
            }
        }catch(Exception e){exportMessage("无法导出，请检查当前填写值。");}
    }
    private void exportMessage(String text){message(text);runOnUiThread(()->{if(configStatus!=null)configStatus.setText(text);});}
    private void importBox(){LinearLayout form=new LinearLayout(this);form.setOrientation(LinearLayout.VERTICAL);EditText ip=input(form,"机顶盒 IP","",false);EditText port=input(form,"ADB 端口","5555",false);new AlertDialog.Builder(this).setTitle("从机顶盒只读导入").setMessage("仅适配安徽移动百视通。盒子需开机、网络 ADB 已授权且 shell 为 root；不安装软件。").setView(form).setNegativeButton("取消",null).setPositiveButton("读取",(d,w)->{configStatus.setText("正在读取，请留意机顶盒上的调试授权提示……");background(()->{JSONObject c=AdbImporter.read(this,ip.getText().toString().trim(),Integer.parseInt(port.getText().toString()));runOnUiThread(()->fill(c));});}).show();}
    @Override protected void onActivityResult(int request,int result,Intent data){
        super.onActivityResult(request,result,data);
        if(request==51&&result==RESULT_OK&&data!=null&&data.getData()!=null){
            background(()->{
                try(InputStream in=getContentResolver().openInputStream(data.getData());ByteArrayOutputStream out=new ByteArrayOutputStream()){
                    if(in==null)throw new IOException("无法读取配置文件");
                    byte[] b=new byte[4096];int n;
                    while((n=in.read(b))>=0){out.write(b,0,n);if(out.size()>1024*1024)throw new IOException("配置文件过大");}
                    JSONObject imported=BestvApi.normalize(new JSONObject(out.toString("UTF-8")));
                    runOnUiThread(()->fill(imported));
                }
            });
        }else if(request==52){
            String snapshot=pendingExport;pendingExport=null;
            if(result!=RESULT_OK||data==null||data.getData()==null){exportMessage("已取消导出，当前配置未改变。");return;}
            if(snapshot==null){exportMessage("导出已中断，请重试。");return;}
            background(()->{
                try(OutputStream out=getContentResolver().openOutputStream(data.getData(),"wt")){
                    if(out==null)throw new IOException("无法写入所选文件");
                    out.write(snapshot.getBytes(java.nio.charset.StandardCharsets.UTF_8));
                }
                exportMessage("配置已导出，可在其他设备导入。文件包含账号资料，请妥善保存。");
            });
        }
    }

    @Override protected void onSaveInstanceState(Bundle state){super.onSaveInstanceState(state);if(pendingExport!=null)state.putString("pending_export",pendingExport);try{state.putString("settings_draft",collect().toString());}catch(Exception ignored){}}
    @Override protected void onDestroy(){worker.shutdownNow();super.onDestroy();}
}
