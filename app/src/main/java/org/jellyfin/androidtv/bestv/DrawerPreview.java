package org.jellyfin.androidtv.bestv;

import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

/** The settings sample uses the same fill and glyph protection as the live drawer. */
final class DrawerPreview extends FrameLayout {
    private final View backdrop;
    DrawerPreview(TvUi ui,float width,float height){
        super(ui.activity);setFocusable(false);setDescendantFocusability(FOCUS_BLOCK_DESCENDANTS);
        setBackground(ui.surface(0xff101629,false));setClipToOutline(true);
        ImageView scene=new ImageView(ui.activity);scene.setImageResource(org.jellyfin.androidtv.R.drawable.settings_preview_scene);scene.setScaleType(ImageView.ScaleType.CENTER_CROP);ui.at(this,scene,0,0,width,height);
        float drawerWidth=259,x=width-drawerWidth;backdrop=new View(ui.activity);ui.at(this,backdrop,x,0,drawerWidth,height);backdrop.setTag("preview-drawer-fill");
        float titleY=12,firstY=45,nextY=firstY+38*ui.fontScale;
        sample(ui,"频道列表",14,TvUi.MUTED,x+20,titleY,drawerWidth-35,30);
        View selected=new View(ui.activity);selected.setBackgroundColor(0xff36305c);ui.at(this,selected,x+12,firstY,drawerWidth-24,36*ui.fontScale);
        sample(ui,"01  新闻频道",16,TvUi.INK,x+20,firstY,drawerWidth-35,36*ui.fontScale);
        sample(ui,"02  纪实频道",16,TvUi.INK,x+20,nextY,drawerWidth-35,36*ui.fontScale);
        if(nextY+63*ui.fontScale<height)sample(ui,"节目名称与频道信息",13,TvUi.MUTED,x+20,nextY+37*ui.fontScale,drawerWidth-35,27*ui.fontScale);
    }
    private void sample(TvUi ui,String value,int size,int color,float x,float y,float w,float h){TextView text=ui.overlayText(value,size,color);text.setSingleLine();text.setEllipsize(android.text.TextUtils.TruncateAt.END);text.setGravity(Gravity.CENTER_VERTICAL);ui.at(this,text,x,y,w,h);}
    void setTransparency(int transparency){backdrop.setBackgroundColor(OverlayContrast.drawerColor(transparency));}
}
