package org.jellyfin.androidtv.bestv;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.drawable.Drawable;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.ImageSpan;
import android.widget.TextView;

/** Shared vector artwork. Icon positions follow the existing text layout without a font dependency. */
public final class TvIcons extends Drawable {
    private final char icon;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private static final String SYMBOLS="⌄‹›Ⅱ⌕▣▱△▷◇☆⚙✓";
    private static final String[] LABELS={"展开","返回","下一项","暂停","搜索","媒体库","媒体图片","提示","播放","直播源","收藏","设置","已完成"};

    private TvIcons(char icon,int color,int size){this.icon=icon;paint.setColor(color);paint.setStrokeWidth(1.8f);paint.setStrokeCap(Paint.Cap.ROUND);paint.setStrokeJoin(Paint.Join.ROUND);setBounds(0,0,size,size);}
    public static void text(TextView view,CharSequence value){
        String raw=value==null?"":value.toString();SpannableString text=new SpannableString(raw);
        StringBuilder description=new StringBuilder();
        for(int i=0;i<raw.length();i++){
            int kind=SYMBOLS.indexOf(raw.charAt(i));
            if(kind>=0){text.setSpan(new ImageSpan(new TvIcons(raw.charAt(i),view.getCurrentTextColor(),Math.round(view.getTextSize())),ImageSpan.ALIGN_BASELINE),i,i+1,Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);description.append(LABELS[kind]);}
            else description.append(raw.charAt(i));
        }
        view.setText(text);if(!description.toString().equals(raw))view.setContentDescription(description.toString());
    }
    private void line(Canvas c,float... xy){Path p=new Path();p.moveTo(xy[0],xy[1]);for(int i=2;i<xy.length;i+=2)p.lineTo(xy[i],xy[i+1]);c.drawPath(p,paint);}
    @Override public void draw(Canvas c){
        c.save();c.translate(getBounds().left,getBounds().top);c.scale(getBounds().width()/24f,getBounds().height()/24f);paint.setStyle(Paint.Style.STROKE);
        switch(icon){
            case '⌄':line(c,5,9,12,16,19,9);break;
            case '‹':line(c,15,5,8,12,15,19);break;
            case '›':line(c,9,5,16,12,9,19);break;
            case 'Ⅱ':line(c,8,5,8,19);line(c,16,5,16,19);break;
            case '⌕':c.drawCircle(10,10,6,paint);line(c,15,15,21,21);break;
            case '▣':c.drawRoundRect(3,4,21,20,2,2,paint);line(c,7,4,7,20);line(c,17,4,17,20);line(c,3,9,7,9);line(c,3,15,7,15);line(c,17,9,21,9);line(c,17,15,21,15);break;
            case '▱':c.drawRoundRect(3,3,21,21,2,2,paint);c.drawCircle(8,8,1.5f,paint);line(c,4,19,10,13,14,16,17,11,21,16);break;
            case '△':line(c,12,3,22,21,2,21,12,3);line(c,12,9,12,14);c.drawCircle(12,18,.6f,paint);break;
            case '▷':line(c,7,4,20,12,7,20,7,4);break;
            case '◇':c.drawRoundRect(3,6,21,20,2,2,paint);line(c,8,2,12,6,16,2);line(c,8,23,16,23);break;
            case '☆':{Path p=new Path();for(int i=0;i<10;i++){double a=-Math.PI/2+i*Math.PI/5;float r=i%2==0?10:4.6f,x=12+r*(float)Math.cos(a),y=12+r*(float)Math.sin(a);if(i==0)p.moveTo(x,y);else p.lineTo(x,y);}p.close();c.drawPath(p,paint);break;}
            case '⚙':c.drawCircle(12,12,6.5f,paint);c.drawCircle(12,12,2.5f,paint);for(int i=0;i<8;i++){c.save();c.rotate(i*45,12,12);line(c,12,2,12,5);c.restore();}break;
            case '✓':line(c,4,12,9,17,20,6);break;
        }
        c.restore();
    }
    @Override public void setAlpha(int alpha){paint.setAlpha(alpha);invalidateSelf();}
    @Override public void setColorFilter(ColorFilter filter){paint.setColorFilter(filter);invalidateSelf();}
    @Override public int getOpacity(){return PixelFormat.TRANSLUCENT;}
}
