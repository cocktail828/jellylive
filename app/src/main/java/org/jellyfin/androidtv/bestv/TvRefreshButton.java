package org.jellyfin.androidtv.bestv;

import android.animation.ValueAnimator;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.view.View;
import android.widget.FrameLayout;

/** Bare vector refresh arrow with a TV-sized transparent target and a quiet focus underline. */
final class TvRefreshButton extends View {
    private final TvUi ui;private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);private ValueAnimator rotation;private float angle;
    TvRefreshButton(TvUi ui,Runnable click){super(ui.activity);this.ui=ui;setId(View.generateViewId());setFocusable(true);if(android.os.Build.VERSION.SDK_INT>=26)setDefaultFocusHighlightEnabled(false);setClickable(true);setOnClickListener(v->click.run());setBackgroundColor(android.graphics.Color.TRANSPARENT);}
    void setBusy(boolean busy){if(rotation!=null){rotation.cancel();rotation=null;}angle=0;if(busy&&!ui.reduceMotion()){rotation=ValueAnimator.ofFloat(0,360);rotation.setDuration(1200);rotation.setRepeatCount(ValueAnimator.INFINITE);rotation.setInterpolator(new android.view.animation.LinearInterpolator());rotation.addUpdateListener(a->{angle=(Float)a.getAnimatedValue();invalidate();});rotation.start();}invalidate();}
    @Override protected void drawableStateChanged(){super.drawableStateChanged();invalidate();}
    @Override protected void onDetachedFromWindow(){if(rotation!=null){rotation.cancel();rotation=null;}super.onDetachedFromWindow();}
    @Override protected void onDraw(Canvas c){super.onDraw(c);float scale=ui.scale;c.save();c.scale(scale,scale);float cx=getWidth()/scale/2,cy=getHeight()/scale/2;paint.setColor(hasFocus()?TvUi.INK:0xffc5b8ff);paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(1.5f);paint.setStrokeCap(Paint.Cap.ROUND);paint.setStrokeJoin(Paint.Join.ROUND);c.save();c.rotate(angle,cx,cy);c.drawArc(new RectF(cx-7,cy-7,cx+7,cy+7),45,285,false,paint);Path arrow=new Path();arrow.moveTo(cx+7,cy-9);arrow.lineTo(cx+7,cy-3);arrow.lineTo(cx+1,cy-3);c.drawPath(arrow,paint);c.restore();if(hasFocus())c.drawLine(cx-11,getHeight()/scale-5,cx+11,getHeight()/scale-5,paint);c.restore();}
}
