/* Shared remote-first box configuration workspace. GPL-2.0. */
package org.jellyfin.androidtv.bestv;

import android.app.*;
import android.os.*;
import android.content.*;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.view.*;
import android.view.inputmethod.InputMethodManager;
import android.widget.*;
import org.json.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;

public final class BoxConfigActivity extends Activity {
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private final List<View> actions=new ArrayList<>();
    private final Map<String,View> fieldActions=new LinkedHashMap<>();
    private final Handler main=new Handler(Looper.getMainLooper());
    private android.content.SharedPreferences prefs;
    private JSONObject draft;
    private String original,route="menu",group="device",editKey="",editParent="edit",pendingExport,notice="",ipValue="",portValue="5555";
    private TvUi ui;private FrameLayout root,page;private TextView status;private TvFormField field,ip,port;
    private Future<?> request;private int requestGeneration,menuIndex;private boolean busy,direct;private AlertDialog confirmation;
    private static final String[][] GROUPS={{"device","设备标识","TVID、STBID、IP、型号与版本"},{"account","账号与授权","绑定账号、密码、认证密钥及用户分组"},{"service","服务地址","登录地址、节目单地址"},{"channel","渠道参数","客户端渠道与标识"}};
    private static final String[][] KEYS={{"tvid","stb_id","ip_address","product_model","tv_profile"},{"user_account","user_password","permanent_key","user_group","cp_code"},{"login_url","epg_url"},{"channel_code","app_code"}};
    @Override public void onCreate(Bundle state){
        super.onCreate(state);getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,WindowManager.LayoutParams.FLAG_FULLSCREEN);
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        WindowManager.LayoutParams params=getWindow().getAttributes();params.dimAmount=.84f;getWindow().setAttributes(params);getWindow().addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
        prefs=getSharedPreferences("bestv_live",MODE_PRIVATE);ui=new TvUi(this);
        try{draft=BestvApi.normalize(new JSONObject(prefs.getString("config","{}")));}catch(Exception e){draft=new JSONObject();}original=draft.toString();
        String action=getIntent().getStringExtra("action");direct=action!=null&&!action.isEmpty();
        if(state!=null){try{draft=new JSONObject(state.getString("draft",original));}catch(Exception ignored){}original=state.getString("original",original);route=state.getString("route","menu");group=state.getString("group","device");editKey=state.getString("edit_key","");editParent=state.getString("edit_parent","edit");ipValue=state.getString("ip","");portValue=state.getString("port","5555");pendingExport=state.getString("export");menuIndex=state.getInt("menu_index",0);}
        else if(direct)route=action.equals("import")?"import":action.equals("edit")?"edit":"menu";
        render();if(state==null&&"file".equals(action))main.post(this::importFile);else if(state==null&&"export".equals(action))main.post(this::exportConfig);
    }
    private boolean dirty(){return !draft.toString().equals(original);}
    private String origin(){return "settings".equals(getIntent().getStringExtra("origin"))?"设置":"直播源";}
    private void put(String key,String value){try{draft.put(key,value);}catch(JSONException ignored){}}
    private String fieldName(String key){for(String[] f:BestvApi.FIELDS)if(f[0].equals(key))return f[1];return key;}
    private String value(String key){String v=draft.optString(key);return (key.equals("user_password")||key.equals("permanent_key"))&&!v.isEmpty()?"••••••••":v.isEmpty()?"未填写":v;}
    private void cancelRequest(){requestGeneration++;if(request!=null)request.cancel(true);request=null;busy=false;}
    private void capture(){if(field!=null&&route.equals("field"))put(editKey,field.getText().toString());if(ip!=null)ipValue=ip.getText().toString();if(port!=null)portValue=port.getText().toString();}
    private void show(String next){capture();cancelRequest();route=next;render();}
    private void render(){
        actions.clear();fieldActions.clear();field=null;ip=null;port=null;status=null;
        root=new FrameLayout(this);root.setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN|View.SYSTEM_UI_FLAG_HIDE_NAVIGATION|View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);setContentView(root);
        page=route.equals("menu")?new FrameLayout(this):ui.canvas();root.addView(page,new FrameLayout.LayoutParams(ui.px(960),ui.px(540),Gravity.CENTER));
        switch(route){case "menu":menu();break;case "import":importPage();break;case "edit":editPage();break;case "advanced":advanced();break;case "group":groupPage();break;case "field":fieldPage();break;}
        linkColumn();if(!actions.isEmpty()){View target=actions.get(Math.min(route.equals("menu")?menuIndex:0,actions.size()-1));target.setFocusableInTouchMode(true);FrameLayout current=root;current.post(()->{if(root==current&&!isFinishing())target.requestFocus();});}
    }
    private void header(String title,String subtitle){ui.heading(page,title,23,48,46,740,42);ui.text(page,"JellyLive",15,0xffb9b0ef,810,46,102,40);ui.text(page,subtitle,14,TvUi.MUTED,50,92,840,38);}
    private Button action(FrameLayout host,String title,float x,float y,float w,Runnable click){Button b=ui.button(host,title,x,y,w,Math.max(44,32*ui.fontScale),()->{if(!busy)click.run();});actions.add(b);return b;}
    private Button listRow(LinearLayout rows,String title,String desc,Runnable click){
        float h=Math.max(48,40*ui.fontScale),w=rows.getTag()!=null?512:844;boolean fieldValue=route.equals("group")&&!title.startsWith("按安徽版");
        FrameLayout row=new FrameLayout(this);rows.addView(row,new LinearLayout.LayoutParams(-1,ui.px(h+8)));Button b=ui.button(row,"",0,0,w,h,()->{if(!busy)click.run();});actions.add(b);
        TextView name=ui.heading(row,title,16,16,0,fieldValue?230:w-65,h);name.setSingleLine();name.setEllipsize(android.text.TextUtils.TruncateAt.END);
        if(fieldValue){TextView value=ui.text(row,desc,14,0xffc5b8ff,260,0,w-310,h);value.setGravity(Gravity.CENTER_VERTICAL|Gravity.END);value.setSingleLine();value.setEllipsize(android.text.TextUtils.TruncateAt.END);}
        ui.text(row,"›",18,TvUi.INK,w-30,0,20,h);b.setContentDescription(title+(fieldValue?"，"+desc:""));return b;
    }
    private LinearLayout scrollRows(FrameLayout host,float x,float y,float w,float h){ScrollView scroll=new ScrollView(this);scroll.setFocusable(false);LinearLayout rows=new LinearLayout(this);rows.setOrientation(LinearLayout.VERTICAL);scroll.addView(rows);ui.at(host,scroll,x,y,w,h);return rows;}
    private void menu(){
        FrameLayout panel=new FrameLayout(this);panel.setBackground(ui.surface(TvUi.PANEL,false));ui.at(page,panel,200,70,560,400);ui.heading(panel,"机顶盒配置",20,24,14,510,40);
        ui.text(panel,notice.isEmpty()?(draft.optString("user_id").isEmpty()?"尚未配置，导入或填写设备资料。":"已保存配置，无需重复填写。"):notice,14,TvUi.MUTED,24,55,512,ui.largeText()?48:30);
        float top=ui.largeText()?98:92;LinearLayout rows=scrollRows(panel,24,top,524,370-top);rows.setTag("menu");
        String[] names={"从机顶盒导入","导入配置文件","手动修改","导出配置文件"};String[] desc={"读取设备资料，核对后再保存。","选择已有的 JSON 配置文件。","常用信息与高级参数分步查看。","文件包含账号资料，请妥善保存。"};
        for(int i=0;i<names.length;i++){final int n=i;Button b=listRow(rows,names[i],desc[i],()->{menuIndex=n;notice="";if(n==0)show("import");else if(n==1)importFile();else if(n==2)show("edit");else exportConfig();});b.setOnFocusChangeListener((v,focused)->{if(focused)menuIndex=n;});}
        ui.text(panel,"导出文件包含账号资料，请妥善保存。",13,TvUi.MUTED,24,374,512,26);
    }
    private void editField(String key,String parent){editKey=key;editParent=parent;show("field");}
    private Button fieldButton(String key,float y){ui.heading(page,fieldName(key),15,50,y,540,35);Button b=action(page,value(key),50,y+36,540,()->editField(key,"edit"));b.setGravity(Gravity.CENTER_VERTICAL|Gravity.START);fieldActions.put(key,b);return b;}
    private void editPage(){
        header("机顶盒配置",notice.isEmpty()?"常用信息":notice);fieldButton("user_id",137);fieldButton("mac",247);
        ui.heading(page,"其他参数",17,640,140,260,35);ui.text(page,"设备、服务与认证参数按类别查看。",14,TvUi.MUTED,640,182,260,76);
        action(page,"高级参数",640,266,260,()->show("advanced"));ui.text(page,"修改暂存为草稿，保存后才会生效。",14,TvUi.MUTED,640,338,260,65);
        status=ui.text(page,"",14,0xfff2b4bb,50,368,540,40);
        Button test=action(page,"测试登录与频道",50,418,250,this::test);Button save=action(page,"保存配置",314,418,250,this::save);
        test.setNextFocusRightId(save.getId());save.setNextFocusLeftId(test.getId());
    }
    private void advanced(){
        header("高级参数",origin()+" / 机顶盒配置 / 手动修改");LinearLayout rows=scrollRows(page,50,140,856,355);
        for(String[] g:GROUPS)listRow(rows,g[1],g[2],()->{group=g[0];show("group");});
    }
    private void groupPage(){
        int index=0;for(int i=0;i<GROUPS.length;i++)if(group.equals(GROUPS[i][0]))index=i;
        header(GROUPS[index][1],origin()+" / 机顶盒配置 / 高级参数");LinearLayout rows=scrollRows(page,50,140,856,355);
        for(String key:KEYS[index])fieldActions.put(key,listRow(rows,fieldName(key),value(key),()->editField(key,"group")));
        if(index==0)listRow(rows,"按安徽版规则补全 TVID / STBID","根据 MAC 补入草稿，核对后再保存。",()->{String mac=draft.optString("mac").replace(":","").replace("-","").toUpperCase(Locale.ROOT);if(!mac.matches("[0-9A-F]{12}")){notice="MAC 格式无效，请先修改常用信息。";show("edit");return;}put("stb_id","AHYDFF"+mac);put("tvid","BESTVOEM$AHYDFF_BESTVINSIDE$AHYDFF"+mac);notice="已补入草稿，尚未保存。";render();});
    }
    private void fieldPage(){
        ui.heading(page,"编辑 "+fieldName(editKey),23,48,46,740,42);ui.text(page,"JellyLive",15,0xffb9b0ef,810,46,102,40);TextView summary=ui.text(page,"修改暂存为草稿",14,TvUi.MUTED,50,92,840,32);TextView label=ui.heading(page,fieldName(editKey),15,50,134,650,32);
        field=new TvFormField(this,ui,"",draft.optString(editKey),editKey.equals("user_password")||editKey.equals("permanent_key"));ui.at(page,field,50,174,630,48);actions.add(field);
        Button done=action(page,"完成",700,174,194,()->{field.finishEditing();show(editParent);});field.setNextFocusRightId(done.getId());field.setNextFocusDownId(done.getId());done.setNextFocusLeftId(field.getId());
        field.setImeOptions(android.view.inputmethod.EditorInfo.IME_ACTION_DONE);field.setOnEditorActionListener((v,a,e)->{field.finishEditing();show(editParent);return true;});
        ScrollView sc=new ScrollView(this);FrameLayout content=new FrameLayout(this);List<View> children=new ArrayList<>();for(int i=0;i<page.getChildCount();i++)children.add(page.getChildAt(i));for(View v:children){page.removeView(v);content.addView(v);}sc.addView(content,new FrameLayout.LayoutParams(ui.px(960),ui.px(510)));ui.at(page,sc,0,0,960,510);
        new TvImeForm(ui,page,sc,content,Arrays.asList(field),Arrays.asList(label),summary,done);
        field.post(()->{if(field!=null)field.beginEditing();});
    }
    private void importPage(){
        header("从机顶盒导入","机顶盒配置");TextView ipLabel=ui.heading(page,"机顶盒 IP",15,50,131,540,30);ip=new TvFormField(this,ui,"输入原机顶盒的 IP",ipValue,false);ui.at(page,ip,50,160,540,48);actions.add(ip);
        TextView portLabel=ui.heading(page,"ADB 端口",15,50,232,540,30);port=new TvFormField(this,ui,"5555",portValue,false);ui.at(page,port,50,261,540,48);port.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);actions.add(port);
        ui.heading(page,"读取前准备",17,640,131,260,35);ui.text(page,"与电视连接同一局域网。\n机顶盒保持开机。\n\n开启网络 ADB，并允许读取配置。\n\n仅适配安徽移动百视通。\n需要 root shell 读取权限。",14,TvUi.MUTED,640,175,260,Math.max(180,180*ui.fontScale));
        status=ui.text(page,notice,14,0xfff2b4bb,50,211,540,24);
        Button read=action(page,"读取配置",50,395,250,this::readBox);Button cancel=ui.button(page,"取消",314,395,130,44,()->{cancelRequest();returnToEntry();});actions.add(cancel);cancel.setTag("cancel");read.setNextFocusRightId(cancel.getId());cancel.setNextFocusLeftId(read.getId());
        // Reuse the IME layout contract, with the same system keyboard as the connection form.
        ScrollView sc=new ScrollView(this);FrameLayout content=new FrameLayout(this);List<View> children=new ArrayList<>();for(int i=0;i<page.getChildCount();i++)children.add(page.getChildAt(i));for(View v:children){page.removeView(v);content.addView(v);}sc.addView(content,new ScrollView.LayoutParams(ui.px(960),ui.px(510)));ui.at(page,sc,0,0,960,510);
        TextView summary=ui.text(content,"机顶盒配置",14,TvUi.MUTED,50,92,540,28);
        new TvImeForm(ui,page,sc,content,Arrays.asList(ip,port),Arrays.asList(ipLabel,portLabel),summary,read);
        page.getViewTreeObserver().addOnGlobalLayoutListener(()->{if(ip!=null)ip.keyboardLayout();if(port!=null)port.keyboardLayout();});
    }
    private void linkColumn(){
        for(int i=0;i<actions.size();i++){View v=actions.get(i);v.setNextFocusUpId(actions.get(Math.max(0,i-1)).getId());v.setNextFocusDownId(actions.get(Math.min(actions.size()-1,i+1)).getId());if(v.getNextFocusLeftId()==View.NO_ID)v.setNextFocusLeftId(v.getId());if(v.getNextFocusRightId()==View.NO_ID)v.setNextFocusRightId(v.getId());}
    }
    private interface Work {JSONObject run()throws Exception;}
    private void runWork(Work work,java.util.function.Consumer<JSONObject> success,String message){
        if(busy)return;capture();busy=true;final int stamp=++requestGeneration;final View focused=getCurrentFocus();
        for(View v:actions)if(v!=focused&&!"cancel".equals(v.getTag())){v.setEnabled(false);v.setFocusable(false);}if(status!=null)status.setText(message);
        request=worker.submit(()->{try{JSONObject result=work.run();main.post(()->{if(stamp!=requestGeneration||isFinishing()||isDestroyed())return;busy=false;for(View v:actions){v.setEnabled(true);v.setFocusable(true);}success.accept(result);});}
            catch(Exception e){main.post(()->{if(stamp!=requestGeneration||isFinishing()||isDestroyed())return;busy=false;for(View v:actions){v.setEnabled(true);v.setFocusable(true);}if(status==null){notice="操作失败，请检查所选文件或配置后重试。";render();return;}status.setText(route.equals("import")?"无法连接机顶盒，请检查 IP、端口和调试授权后重试。":"操作失败，请检查配置和网络后重试。");if(ip!=null)ip.requestFocus();});}});
    }
    private void readBox(){
        if(actions.stream().anyMatch(v->"ime-done".equals(v.getTag()))){if(ip!=null)ip.finishEditing();if(port!=null)port.finishEditing();return;}
        capture();int number;try{number=Integer.parseInt(portValue);if(number<1||number>65535)throw new NumberFormatException();}catch(Exception e){status.setText("请输入 1–65535 范围内的端口。");port.requestFocus();return;}
        if(ipValue.trim().isEmpty()){status.setText("请填写机顶盒 IP。");ip.requestFocus();return;}String address=ipValue.trim();runWork(()->AdbImporter.read(this,address,number),result->{draft=result;notice="读取成功，尚未保存";show("edit");},"正在读取配置…");
    }
    private void test(){try{final JSONObject snapshot=new JSONObject(draft.toString());runWork(()->new JSONObject().put("count",new BestvApi(snapshot).channels().size()),result->status.setText("登录成功，获取 "+result.optInt("count")+" 个频道。尚未保存。"),"正在测试登录与频道…");}catch(Exception ignored){status.setText("配置格式无效");}}
    private void save(){
        for(String key:new String[]{"user_id","mac","tvid","stb_id","tv_profile"}){if(draft.optString(key).trim().isEmpty()){missing(key,"请填写"+fieldName(key));return;}}
        if(!draft.optString("mac").replace(":","").replace("-","").matches("(?i)[0-9a-f]{12}")){missing("mac","MAC 格式无效，请检查后保存。");return;}
        prefs.edit().putString("config",draft.toString()).apply();original=draft.toString();setResult(RESULT_OK);finish();
    }
    private void missing(String key,String message){notice=message;if(key.equals("user_id")||key.equals("mac"))show("edit");else {group="device";show("group");}View target=fieldActions.get(key);if(target!=null)target.requestFocus();if(status!=null)status.setText(message);}
    private void importFile(){
        if(busy)return;cancelRequest();try{startActivityForResult(new Intent(Intent.ACTION_OPEN_DOCUMENT).setType("*/*").addCategory(Intent.CATEGORY_OPENABLE),51);}catch(ActivityNotFoundException e){notice="电视没有文件选择器，请从机顶盒导入或手动修改。";render();}
    }
    private void exportConfig(){
        if(busy)return;capture();pendingExport=draft.toString();String name="bestv-live-"+new java.text.SimpleDateFormat("yyyyMMdd-HHmmss",Locale.ROOT).format(new Date())+".json";
        try{startActivityForResult(new Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("application/json").putExtra(Intent.EXTRA_TITLE,name),52);}
        catch(ActivityNotFoundException e){final String snapshot=pendingExport;pendingExport=null;runWork(()->{File folder=getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS);if(folder==null||(!folder.isDirectory()&&!folder.mkdirs()))throw new IOException();try(OutputStream out=new FileOutputStream(new File(folder,name))){out.write(snapshot.getBytes(java.nio.charset.StandardCharsets.UTF_8));}return new JSONObject();},result->{notice="已导出到应用 Documents 目录，可用文件管理器复制。";render();},"正在导出配置…");}
    }
    @Override protected void onActivityResult(int code,int result,Intent data){
        super.onActivityResult(code,result,data);
        if(result!=RESULT_OK||data==null||data.getData()==null){pendingExport=null;if(direct&&route.equals("menu")&&!dirty()){finish();return;}notice=code==51?"已取消导入，当前配置未改变。":"已取消导出，当前配置未改变。";render();return;}
        final android.net.Uri uri=data.getData();
        if(code==51)runWork(()->{try(InputStream in=getContentResolver().openInputStream(uri);ByteArrayOutputStream out=new ByteArrayOutputStream()){if(in==null)throw new IOException();byte[] bytes=new byte[4096];int n;while((n=in.read(bytes))>=0){out.write(bytes,0,n);if(out.size()>1024*1024)throw new IOException();}return BestvApi.normalize(new JSONObject(out.toString("UTF-8")));}},value->{draft=value;notice="读取成功，尚未保存";show("edit");},"正在读取配置文件…");
        else if(code==52){final String snapshot=pendingExport;pendingExport=null;if(snapshot==null){notice="导出已中断，请重试。";render();return;}runWork(()->{try(OutputStream out=getContentResolver().openOutputStream(uri,"wt")){if(out==null)throw new IOException();out.write(snapshot.getBytes(java.nio.charset.StandardCharsets.UTF_8));}return new JSONObject();},value->{notice="配置已导出，文件包含账号资料。";render();},"正在导出配置…");}
    }
    private void returnToEntry(){if(direct)leave();else show("menu");}
    private void leave(){capture();cancelRequest();if(!dirty()){finish();return;}if(confirmation!=null)return;
        FrameLayout panel=new FrameLayout(this);panel.setBackground(ui.surface(TvUi.PANEL,false));ui.heading(panel,"放弃本次修改？",20,24,16,392,40);ui.text(panel,"尚未保存的内容将丢失。\n已生效的配置不会改变。",14,TvUi.MUTED,24,58,392,55);
        AlertDialog popup=new AlertDialog.Builder(this).create();confirmation=popup;popup.setView(panel,0,0,0,0);Button keep=ui.button(panel,"继续编辑",24,127,180,44,()->popup.dismiss());Button discard=ui.button(panel,"放弃修改",216,127,180,44,()->{popup.dismiss();finish();});keep.setNextFocusRightId(discard.getId());discard.setNextFocusLeftId(keep.getId());keep.setNextFocusLeftId(keep.getId());discard.setNextFocusRightId(discard.getId());popup.setOnDismissListener(d->confirmation=null);popup.show();popup.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));popup.getWindow().setLayout(ui.px(440),ui.px(190));keep.setFocusableInTouchMode(true);keep.post(keep::requestFocus);
    }
    @Override public void onBackPressed(){if(confirmation!=null){confirmation.dismiss();return;}if(field!=null&&field.isEditing()){field.finishEditing();return;}if(ip!=null&&ip.isEditing()){ip.finishEditing();return;}if(port!=null&&port.isEditing()){port.finishEditing();return;}
        capture();cancelRequest();if(route.equals("field"))show(editParent);else if(route.equals("group"))show("advanced");else if(route.equals("advanced"))show("edit");else if(route.equals("menu"))leave();else returnToEntry();}
    @Override protected void onSaveInstanceState(Bundle state){capture();super.onSaveInstanceState(state);state.putString("draft",draft.toString());state.putString("original",original);state.putString("route",route);state.putString("group",group);state.putString("edit_key",editKey);state.putString("edit_parent",editParent);state.putString("ip",ipValue);state.putString("port",portValue);state.putString("export",pendingExport);state.putInt("menu_index",menuIndex);}
    @Override protected void onDestroy(){cancelRequest();main.removeCallbacksAndMessages(null);worker.shutdownNow();if(confirmation!=null)confirmation.dismiss();super.onDestroy();}
}
