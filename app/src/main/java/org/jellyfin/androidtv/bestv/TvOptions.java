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
        float width=minWidth;
        TextView measure=ui.text("",13,TvUi.INK);
        for(int i=0;i<names.length;i++){float measured=measure.getPaint().measureText(names[i]);if(details!=null&&i<details.length&&!details[i].isEmpty())measured+=ui.px(20)+measure.getPaint().measureText(details[i]);width=Math.max(width,measured/ui.scale+45);}
        width=Math.min(maxWidth,width);
        float heading=title==null||title.isEmpty()?10:40;
        float height=Math.min(400,heading+names.length*rowHeight+10);
        FrameLayout panel=new FrameLayout(activity);panel.setBackground(ui.glass(TvUi.PANEL));
        AlertDialog popup=new AlertDialog.Builder(activity).create();popup.setView(panel,0,0,0,0);popup.setCanceledOnTouchOutside(true);
        if(heading>10)ui.title(panel,title,13,7,width-26,27);
        ScrollView scroll=new ScrollView(activity);scroll.setVerticalScrollBarEnabled(false);LinearLayout rows=new LinearLayout(activity);rows.setOrientation(LinearLayout.VERTICAL);scroll.addView(rows);ui.at(panel,scroll,8,heading,width-16,height-heading-8);
        Button focused=null;
        for(int i=0;i<names.length;i++){
            final int index=i;FrameLayout row=new FrameLayout(activity);Button action=ui.button("",()->{popup.dismiss();chosen.select(index);});rows.addView(row,new LinearLayout.LayoutParams(-1,ui.px(rowHeight)));ui.at(row,action,0,1,width-16,rowHeight-2);
            String detail=details!=null&&i<details.length?details[i]:"";
            float detailWidth=detail.isEmpty()?0:Math.min(width*.52f,measure.getPaint().measureText(detail)/ui.scale+4);
            ui.text(row,i==selected?"✓":"",13,TvUi.INK,5,0,17,rowHeight);
            TextView name=ui.text(row,names[i],13,TvUi.INK,24,0,width-48-detailWidth,rowHeight);name.setSingleLine();name.setEllipsize(TextUtils.TruncateAt.END);
            if(detailWidth>0){TextView trailing=ui.text(row,detail,12,TvUi.MUTED,width-24-detailWidth,0,detailWidth,rowHeight);trailing.setGravity(Gravity.CENTER_VERTICAL|Gravity.END);trailing.setSingleLine();trailing.setEllipsize(TextUtils.TruncateAt.END);}
            action.setContentDescription(names[i]+(detail.isEmpty()?"":"，"+detail)+(i==selected?"，当前选中":""));
            if(i==Math.max(0,selected))focused=action;
        }
        int[] position=new int[2];if(anchor!=null)anchor.getLocationInWindow(position);
        float anchorRight=anchor==null?928:(position[0]+anchor.getWidth())/ui.scale;
        float x=Math.max(16,Math.min(944-width,anchorRight-width));
        float y=below?(anchor==null?72:position[1]/ui.scale+anchor.getHeight()/ui.scale+6):426-height;
        y=Math.max(12,Math.min(528-height,y));
        popup.show();Window window=popup.getWindow();if(window!=null){window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));window.getDecorView().setPadding(0,0,0,0);window.getDecorView().setSystemUiVisibility(activity.getWindow().getDecorView().getSystemUiVisibility());window.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);window.setGravity(Gravity.TOP|Gravity.LEFT);WindowManager.LayoutParams attributes=window.getAttributes();attributes.x=ui.px(x);attributes.y=ui.px(y);window.setAttributes(attributes);window.setLayout(ui.px(width),ui.px(height));}
        TvGlass.apply(activity,ui,panel,x,y,width,height);
        if(focused!=null)focused.requestFocus();return popup;
    }
}
