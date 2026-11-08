/* Background-only frosting for JellyLive overlays. GPL-2.0. */
package org.jellyfin.androidtv.bestv;

import android.app.Activity;
import android.graphics.*;
import android.graphics.drawable.*;
import android.os.*;
import android.view.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Captures only our own window. Text and focus are drawn later, at full opacity. */
public final class TvGlass {
    private static final ExecutorService WORK=Executors.newSingleThreadExecutor(r->{Thread t=new Thread(r,"JellyLive-glass");t.setDaemon(true);return t;});
    private TvGlass(){}
    public static void apply(Activity activity,TvUi ui,View panel,float x,float y,float width,float height){
        panel.setBackground(ui.glass(TvUi.PANEL));
        panel.setClipToOutline(true);
        panel.setOutlineProvider(new ViewOutlineProvider(){@Override public void getOutline(View view,Outline outline){outline.setRoundRect(0,0,view.getWidth(),view.getHeight(),ui.px(height>=540?0:9));}});
        View root=activity.findViewById(android.R.id.content);
        if(root==null||root.getWidth()==0||root.getHeight()==0)return;
        final int divisor=6;
        Bitmap shot=Bitmap.createBitmap(Math.max(1,root.getWidth()/divisor),Math.max(1,root.getHeight()/divisor),Bitmap.Config.ARGB_8888);
        Canvas canvas=new Canvas(shot);canvas.scale(shot.getWidth()/(float)root.getWidth(),shot.getHeight()/(float)root.getHeight());
        float opacity=panel.getAlpha();panel.setAlpha(0);root.draw(canvas);panel.setAlpha(opacity);
        SurfaceView video=findSurface(root);
        Runnable finish=()->WORK.execute(()->{
            int left=Math.max(0,Math.min(shot.getWidth()-1,Math.round(ui.px(x)/(float)divisor)));
            int top=Math.max(0,Math.min(shot.getHeight()-1,Math.round(ui.px(y)/(float)divisor)));
            int w=Math.max(1,Math.min(shot.getWidth()-left,Math.round(ui.px(width)/(float)divisor)));
            int h=Math.max(1,Math.min(shot.getHeight()-top,Math.round(ui.px(height)/(float)divisor)));
            Bitmap crop=Bitmap.createBitmap(shot,left,top,w,h).copy(Bitmap.Config.ARGB_8888,true);
            if(crop!=shot)shot.recycle();
            blur(crop,2);
            activity.runOnUiThread(()->{
                if(activity.isFinishing()||!panel.isAttachedToWindow()){crop.recycle();return;}
                BitmapDrawable image=new BitmapDrawable(activity.getResources(),crop);image.setFilterBitmap(true);
                panel.setBackground(new LayerDrawable(new Drawable[]{image,ui.glass(TvUi.PANEL)}));
            });
        });
        if(Build.VERSION.SDK_INT>=24&&video!=null&&video.getHolder().getSurface().isValid()){
            Bitmap pixels=Bitmap.createBitmap(Math.max(1,video.getWidth()/divisor),Math.max(1,video.getHeight()/divisor),Bitmap.Config.ARGB_8888);
            try {PixelCopy.request(video,pixels,result->{
                if(result==PixelCopy.SUCCESS){int[] vr=new int[2],rr=new int[2];video.getLocationInWindow(vr);root.getLocationInWindow(rr);Canvas c=new Canvas(shot);c.drawBitmap(pixels,null,new RectF((vr[0]-rr[0])/(float)divisor,(vr[1]-rr[1])/(float)divisor,(vr[0]-rr[0]+video.getWidth())/(float)divisor,(vr[1]-rr[1]+video.getHeight())/(float)divisor),new Paint(Paint.FILTER_BITMAP_FLAG));}
                pixels.recycle();if(result==PixelCopy.SUCCESS)finish.run();else shot.recycle();
            },new Handler(Looper.getMainLooper()));return;}catch(IllegalArgumentException ignored){pixels.recycle();}
        }
        // SurfaceView pixels are inaccessible before API 24: keep a transparent tint.
        if(video!=null){shot.recycle();return;}
        finish.run();
    }
    private static SurfaceView findSurface(View view){if(view instanceof SurfaceView&&view.isShown())return (SurfaceView)view;if(view instanceof ViewGroup){ViewGroup group=(ViewGroup)view;for(int i=0;i<group.getChildCount();i++){SurfaceView found=findSurface(group.getChildAt(i));if(found!=null)return found;}}return null;}
    private static void blur(Bitmap bitmap,int radius){
        int w=bitmap.getWidth(),h=bitmap.getHeight();int[] source=new int[w*h],target=new int[w*h];bitmap.getPixels(source,0,w,0,0,w,h);
        for(int pass=0;pass<3;pass++){for(int y=0;y<h;y++)for(int x=0;x<w;x++){int red=0,green=0,blue=0,count=0;for(int yy=Math.max(0,y-radius);yy<=Math.min(h-1,y+radius);yy++)for(int xx=Math.max(0,x-radius);xx<=Math.min(w-1,x+radius);xx++){int p=source[yy*w+xx];red+=(p>>16)&255;green+=(p>>8)&255;blue+=p&255;count++;}target[y*w+x]=Color.rgb(red/count,green/count,blue/count);}int[] swap=source;source=target;target=swap;}
        bitmap.setPixels(source,0,w,0,0,w,h);
    }
}
