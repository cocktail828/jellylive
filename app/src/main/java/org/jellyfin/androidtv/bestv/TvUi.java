/* JellyLive design system, based on ui-design/designs. GPL-2.0. */
package org.jellyfin.androidtv.bestv;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.drawable.Drawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;

/** 960 x 540 logical coordinates: half of the supplied 1920 x 1080 drawings. */
public final class TvUi {
    public static final int INK=0xfff6f5ff,MUTED=OverlayContrast.SECONDARY,PANEL=0xff171c32,TILE=0xff242a46,ACCENT=0xff7561ed;
    public final Activity activity;
    public final float scale;
    public final float fontScale;
    public TvUi(Activity a){activity=a;scale=Math.min(a.getResources().getDisplayMetrics().widthPixels/960f,a.getResources().getDisplayMetrics().heightPixels/540f);fontScale=Math.max(.5f,a.getResources().getConfiguration().fontScale)*(a.getSharedPreferences("bestv_live",0).getBoolean("large_text",false)?1.17f:1f);}
    public int px(float n){return Math.round(n*scale);}
    public GradientDrawable surface(int color,boolean focus){
        GradientDrawable d=focus?new GradientDrawable(GradientDrawable.Orientation.TL_BR,new int[]{0xff6652d5,0xff3a327d}):new GradientDrawable();
        if(!focus)d.setColor(color);d.setCornerRadius(px(8));d.setStroke(px(focus?2:0.5f),focus?Color.WHITE:0xff363e5d);return d;
    }
    public StateListDrawable focusBackground(int color){StateListDrawable d=new StateListDrawable();d.addState(new int[]{android.R.attr.state_focused},surface(color,true));d.addState(new int[]{android.R.attr.state_pressed},surface(color,true));d.addState(new int[]{},surface(color,false));return d;}
    public FrameLayout canvas(){FrameLayout f=new FrameLayout(activity);f.setBackground(new GradientDrawable(GradientDrawable.Orientation.TL_BR,new int[]{0xff101326,0xff080c18}));return f;}
    public <T extends View>T at(FrameLayout parent,T v,float x,float y,float w,float h){
        FrameLayout.LayoutParams p=new FrameLayout.LayoutParams(w<0?(int)w:px(w),h<0?(int)h:px(h),Gravity.TOP|Gravity.START);p.leftMargin=px(x);p.topMargin=px(y);parent.addView(v,p);return v;
    }
    public TextView text(String s,int size,int color){TextView t=new TextView(activity);t.setTextColor(color);t.setTextSize(TypedValue.COMPLEX_UNIT_PX,size*scale*fontScale);t.setGravity(Gravity.CENTER_VERTICAL);t.setFontFeatureSettings("kern");t.setIncludeFontPadding(false);TvIcons.text(t,s);return t;}
    public TextView text(FrameLayout p,String s,int size,int color,float x,float y,float w,float h){return at(p,text(s,size,color),x,y,w,h);}
    public TextView title(FrameLayout p,String s,float x,float y,float w,float h){TextView t=text(p,s,21,INK,x,y,w,h);t.setTypeface(null,Typeface.BOLD);return t;}
    public TextView heading(FrameLayout p,String s,int size,float x,float y,float w,float h){TextView t=text(p,s,size,INK,x,y,w,h);t.setTypeface(null,Typeface.BOLD);return t;}
    public Button button(String s,Runnable click){Button b=new Button(activity);
        // Material's default StateListAnimator raises a Button above its later label siblings.
        // These TV controls draw their own focus background and must stay in child draw order.
        b.setStateListAnimator(null);b.setElevation(0);b.setTranslationZ(0);b.setFocusable(true);
        b.setId(View.generateViewId());b.setAllCaps(false);b.setTextColor(INK);b.setTextSize(TypedValue.COMPLEX_UNIT_PX,14*scale*fontScale);b.setTypeface(null,Typeface.BOLD);b.setGravity(Gravity.CENTER);b.setPadding(px(10),0,px(10),0);b.setMinHeight(0);b.setMinimumHeight(0);b.setMinWidth(0);b.setMinimumWidth(0);b.setBackground(focusBackground(TILE));b.setOnClickListener(v->click.run());TvIcons.text(b,s);return b;}
    public Button button(FrameLayout p,String s,float x,float y,float w,float h,Runnable click){return at(p,button(s,click),x,y,w,h);}
    public EditText input(FrameLayout p,String hint,String value,float x,float y,float w,boolean password){
        EditText e=new EditText(activity);e.setId(View.generateViewId());e.setSingleLine(true);e.setTextSize(TypedValue.COMPLEX_UNIT_PX,15*scale*fontScale);e.setTextColor(INK);e.setHintTextColor(MUTED);e.setHint(hint);e.setText(value);e.setPadding(px(14),0,px(14),0);e.setBackground(focusBackground(0xff20263e));e.setInputType(android.text.InputType.TYPE_CLASS_TEXT|(password?android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD:android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS));return at(p,e,x,y,w,40);
    }
    public TextView header(FrameLayout p,String title,String sub,Runnable back){TextView heading=title(p,title,48,28,largeText()?680:650,largeText()?44:34);text(p,sub,12,MUTED,48,largeText()?78:65,690,largeText()?30:23);text(p,"JellyLive",16,0xffb9b0ef,largeText()?770:814,29,largeText()?142:98,34);return heading;}
    /** Older page builders still call this; persistent instruction footers are retired. */
    public void footer(FrameLayout p,String hint){}
    public GradientDrawable glass(int color){GradientDrawable d=new GradientDrawable();d.setColor((color&0x00ffffff)|(OverlayContrast.BASE_ALPHA<<24));d.setCornerRadius(px(9));d.setStroke(px(.5f),0xff59627d);return d;}
    public Button quietButton(String value,String description,Runnable click){
        Button b=button(value,click);b.setTextSize(TypedValue.COMPLEX_UNIT_PX,13*scale*fontScale);b.setTypeface(null,Typeface.NORMAL);b.setPadding(px(4),0,px(4),0);b.setSingleLine();b.setEllipsize(android.text.TextUtils.TruncateAt.END);b.setContentDescription(description);
        b.setBackground(new Drawable(){final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);public void draw(Canvas c){if(isStateful()&&b.hasFocus()){paint.setColor(0xffbba9ef);paint.setStrokeWidth(Math.max(1,px(1)));c.drawLine(px(7),getBounds().bottom-px(4),getBounds().right-px(7),getBounds().bottom-px(4),paint);}}public boolean isStateful(){return true;}protected boolean onStateChange(int[] state){b.setTextColor(b.hasFocus()?INK:MUTED);invalidateSelf();return true;}public void setAlpha(int alpha){}public void setColorFilter(android.graphics.ColorFilter filter){}public int getOpacity(){return android.graphics.PixelFormat.TRANSLUCENT;}});return b;
    }
    public boolean largeText(){return fontScale>1.1f;}
    /** The system may extend transient controls for users who need more time. */
    public int timeout(int normal){
        android.view.accessibility.AccessibilityManager manager=(android.view.accessibility.AccessibilityManager)activity.getSystemService(android.content.Context.ACCESSIBILITY_SERVICE);
        if(manager==null)return normal;
        if(android.os.Build.VERSION.SDK_INT>=29)return manager.getRecommendedTimeoutMillis(normal,android.view.accessibility.AccessibilityManager.FLAG_CONTENT_CONTROLS|android.view.accessibility.AccessibilityManager.FLAG_CONTENT_TEXT);
        return manager.isTouchExplorationEnabled()?Math.max(normal,30000):normal;
    }
    public boolean reduceMotion(){return android.provider.Settings.Global.getFloat(activity.getContentResolver(),android.provider.Settings.Global.ANIMATOR_DURATION_SCALE,1f)==0f;}
    public void value(Button button,String role,String value){TvIcons.text(button,value);String state=role.equals("字幕")&&value.equals("字幕")?"关闭":value;button.setContentDescription(role+"，"+state);}
    /** A continuous scrim, rather than filled individual controls, protects white-video contrast. */
    public void protectControls(FrameLayout panel){View guard=new View(activity);guard.setBackgroundColor(OverlayContrast.HUD_GUARD);at(panel,guard,8,30,880,34);}
    public void tag(View v,String tag){v.setTag(tag);}
    public static void detach(View v){if(v.getParent() instanceof ViewGroup)((ViewGroup)v.getParent()).removeView(v);}
}
