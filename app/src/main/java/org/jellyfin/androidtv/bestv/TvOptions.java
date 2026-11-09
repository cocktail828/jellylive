/* Compact, anchored remote-control option menus. GPL-2.0. */
package org.jellyfin.androidtv.bestv;

import android.app.*;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.text.TextUtils;
import android.view.*;
import android.widget.*;

public final class TvOptions {
    public interface Selected {void select(int index);}
    private TvOptions(){}
    public static AlertDialog show(Activity activity,TvUi ui,View anchor,String title,String[] names,String[] details,int selected,boolean below,float minWidth,float maxWidth,float rowHeight,Selected chosen){
        boolean sourceTypes=title==null&&names.length==2&&names[0].startsWith("M3U");
        boolean groupMenu=title==null&&names.length==4&&names[0].equals("全部频道");
        if(groupMenu)rowHeight=40;
        rowHeight=Math.max(rowHeight,27f*ui.fontScale);
        if(ui.largeText()&&!groupMenu&&!sourceTypes)maxWidth=Math.max(maxWidth,310f);
        float width=minWidth;
        TextView measure=ui.text("",13,TvUi.INK);
        for(int i=0;i<names.length;i++){float measured=measure.getPaint().measureText(names[i]);if(!sourceTypes&&details!=null&&i<details.length&&!details[i].isEmpty())measured+=ui.px(20)+measure.getPaint().measureText(details[i]);width=Math.max(width,measured/ui.scale+45);}
        width=Math.min(maxWidth,width);
        float heading=groupMenu||sourceTypes?7:title==null||title.isEmpty()?10:title.equals("倍速")?40:32;
        float titleHeight=Math.max(27,21*ui.fontScale);if(title!=null&&!title.isEmpty())heading=Math.max(heading,titleHeight+8);
        float height=Math.min(400,heading+names.length*rowHeight+(groupMenu?5:sourceTypes?6:9));
        FrameLayout panel=new FrameLayout(activity);panel.setBackground(ui.glass(TvUi.PANEL));
        AlertDialog popup=new AlertDialog.Builder(activity).create();popup.setView(panel,0,0,0,0);popup.setCanceledOnTouchOutside(true);
        if(title!=null&&!title.isEmpty())ui.heading(panel,title,15,13,4,width-26,titleHeight);
        ScrollView scroll=new ScrollView(activity);scroll.setVerticalScrollBarEnabled(false);LinearLayout rows=new LinearLayout(activity);rows.setOrientation(LinearLayout.VERTICAL);scroll.addView(rows);ui.at(panel,scroll,8,heading,width-16,height-heading-8);
        View guard=new View(activity);guard.setBackgroundColor(OverlayContrast.OPTION_GUARD);ui.at(panel,guard,4,heading,width-8,height-heading-4);scroll.bringToFront();
        Button focused=null;
        for(int i=0;i<names.length;i++){
            final int index=i;FrameLayout row=new FrameLayout(activity);Button action=ui.button("",()->{popup.dismiss();chosen.select(index);});rows.addView(row,new LinearLayout.LayoutParams(-1,ui.px(rowHeight)));ui.at(row,action,0,1,width-16,rowHeight-2);
            android.graphics.drawable.StateListDrawable background=new android.graphics.drawable.StateListDrawable();background.addState(new int[]{android.R.attr.state_focused},ui.surface(TvUi.TILE,true));background.addState(new int[]{android.R.attr.state_selected},ui.surface(0xff36305c,false));background.addState(new int[]{},new ColorDrawable(Color.TRANSPARENT));action.setBackground(background);action.setSelected(i==selected);
            String detail=details!=null&&i<details.length?details[i]:"";
            float detailWidth=detail.isEmpty()?0:Math.min(width*.52f,measure.getPaint().measureText(detail)/ui.scale+4);
            if(sourceTypes){
                action.getLayoutParams().height=ui.px(41);ui.text(row,"◇",16,TvUi.INK,8,7,18,28);
                ui.text(row,names[i],14,TvUi.INK,35,0,width-60,23);
                TextView subtitle=ui.text(row,detail,12,TvUi.MUTED,35,21,width-60,20);subtitle.setSingleLine();subtitle.setEllipsize(TextUtils.TruncateAt.END);
            }else if(groupMenu){
                ui.text(row,new String[]{"◇","☆","▷","◇"}[i],15,TvUi.INK,10,0,20,rowHeight-4);
                TextView label=ui.text(row,names[i],14,TvUi.INK,35,0,width-84,rowHeight-4);label.setSingleLine();label.setEllipsize(TextUtils.TruncateAt.END);
                ui.text(row,i==selected?"✓":"",14,TvUi.INK,width-39,0,18,rowHeight-4);
                action.getLayoutParams().height=ui.px(rowHeight-4);
            }else{
                ui.text(row,i==selected?"✓":"",13,TvUi.INK,5,0,17,rowHeight);
                TextView name=ui.text(row,names[i],13,TvUi.INK,24,0,Math.max(32,width-58-detailWidth),rowHeight);name.setSingleLine();name.setEllipsize(TextUtils.TruncateAt.END);name.setMarqueeRepeatLimit(-1);
                action.setOnFocusChangeListener((v,f)->{name.setSelected(false);name.setEllipsize(TextUtils.TruncateAt.END);if(f&&!ui.reduceMotion())name.postDelayed(()->{if(action.hasFocus()){name.setEllipsize(TextUtils.TruncateAt.MARQUEE);name.setSelected(true);}},1200);});
                if(ui.reduceMotion())action.setOnKeyListener((v,key,event)->{if(event.getAction()!=KeyEvent.ACTION_DOWN||(key!=KeyEvent.KEYCODE_DPAD_LEFT&&key!=KeyEvent.KEYCODE_DPAD_RIGHT))return false;int end=Math.max(0,(int)Math.ceil(name.getPaint().measureText(names[index]))-name.getWidth());if(end==0)return false;name.setEllipsize(null);int next=Math.max(0,Math.min(end,name.getScrollX()+(key==KeyEvent.KEYCODE_DPAD_RIGHT?ui.px(40):-ui.px(40))));if(next==name.getScrollX())return false;name.scrollTo(next,0);return true;});
                if(detailWidth>0){TextView trailing=ui.text(row,detail,12,TvUi.INK,width-24-detailWidth,0,detailWidth,rowHeight);trailing.setGravity(Gravity.CENTER_VERTICAL|Gravity.END);trailing.setSingleLine();trailing.setEllipsize(TextUtils.TruncateAt.END);}
            }
            action.setContentDescription(names[i]+(detail.isEmpty()?"":"，"+detail)+(i==selected?"，当前选中":""));
            if(i==Math.max(0,selected))focused=action;
        }
        int[] position=new int[2];if(anchor!=null)anchor.getLocationInWindow(position);
        float anchorRight=anchor==null?928:(position[0]+anchor.getWidth())/ui.scale;
        float x=Math.max(48,Math.min(912-width,anchorRight-width));
        float y=below?(anchor==null?72:position[1]/ui.scale+anchor.getHeight()/ui.scale+(sourceTypes?7:groupMenu&&minWidth<300?8:5)):426-height;
        y=Math.max(27,Math.min(513-height,y));
        popup.show();Window window=popup.getWindow();if(window!=null){window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));window.getDecorView().setPadding(0,0,0,0);window.getDecorView().setSystemUiVisibility(activity.getWindow().getDecorView().getSystemUiVisibility());window.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);window.setGravity(Gravity.TOP|Gravity.LEFT);WindowManager.LayoutParams attributes=window.getAttributes();attributes.x=ui.px(x);attributes.y=ui.px(y);window.setAttributes(attributes);window.setLayout(ui.px(width),ui.px(height));}
        TvGlass.apply(activity,ui,panel,x,y,width,height);
        if(focused!=null){Button target=focused;target.setFocusableInTouchMode(true);target.requestFocus();target.post(()->{if(popup.isShowing())target.requestFocus();});}return popup;
    }
}
