package org.jellyfin.androidtv.bestv;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.text.Layout;
import android.text.TextPaint;
import android.widget.TextView;

/** Protect only glyph edges on bright video, leaving the chosen panel transparency intact. */
final class OutlinedTextView extends TextView {
    private final float edge;
    OutlinedTextView(Context context,float edge){super(context);this.edge=Math.max(1,edge);}
    @Override protected void onDraw(Canvas canvas){
        Layout layout=getLayout();
        if(layout!=null){
            TextPaint paint=getPaint();int color=paint.getColor();Paint.Style style=paint.getStyle();float width=paint.getStrokeWidth();Paint.Join join=paint.getStrokeJoin();
            paint.setColor(OverlayContrast.DRAWER_TEXT_EDGE);paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(edge*2);paint.setStrokeJoin(Paint.Join.ROUND);
            canvas.save();canvas.translate(getCompoundPaddingLeft(),getTotalPaddingTop());layout.draw(canvas);canvas.restore();
            paint.setColor(color);paint.setStyle(style);paint.setStrokeWidth(width);paint.setStrokeJoin(join);
        }
        super.onDraw(canvas);
    }
}
