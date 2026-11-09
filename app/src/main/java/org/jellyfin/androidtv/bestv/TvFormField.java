/* Remote-first edit field. GPL-2.0. */
package org.jellyfin.androidtv.bestv;

import android.app.Activity;
import android.graphics.Rect;
import android.text.InputType;
import android.util.TypedValue;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;

/** D-pad browses fields; only explicit confirmation gives arrow keys to the text editor. */
public final class TvFormField extends EditText {
    private boolean editing, keyboardSeen;
    public TvFormField(Activity activity,TvUi ui,String hint,String value,boolean secret){
        super(activity);setId(View.generateViewId());setSingleLine(true);setTextColor(TvUi.INK);setHintTextColor(TvUi.MUTED);
        setTextSize(TypedValue.COMPLEX_UNIT_PX,17*ui.scale*ui.fontScale);setPadding(ui.px(14),0,ui.px(14),0);
        setBackground(ui.focusBackground(0xff20263e));setInputType(InputType.TYPE_CLASS_TEXT|(secret?InputType.TYPE_TEXT_VARIATION_PASSWORD:InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS));
        setHint(hint);setText(value);setShowSoftInputOnFocus(false);setCursorVisible(false);
        setOnClickListener(v->beginEditing());
        setOnFocusChangeListener((v,has)->{if(!has)finishEditing();});
    }
    public boolean isEditing(){return editing;}
    public void beginEditing(){editing=true;keyboardSeen=false;setShowSoftInputOnFocus(true);setCursorVisible(true);requestFocus();setSelection(length());((InputMethodManager)getContext().getSystemService(Activity.INPUT_METHOD_SERVICE)).showSoftInput(this,InputMethodManager.SHOW_IMPLICIT);}
    public void finishEditing(){editing=false;setShowSoftInputOnFocus(false);setCursorVisible(false);((InputMethodManager)getContext().getSystemService(Activity.INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(getWindowToken(),0);}
    @Override public boolean onKeyDown(int key,KeyEvent event){
        if(!editing&&(key==KeyEvent.KEYCODE_DPAD_CENTER||key==KeyEvent.KEYCODE_ENTER)){if(event.getRepeatCount()==0)beginEditing();return true;}
        if(!editing){int direction=key==KeyEvent.KEYCODE_DPAD_UP?View.FOCUS_UP:key==KeyEvent.KEYCODE_DPAD_DOWN?View.FOCUS_DOWN:key==KeyEvent.KEYCODE_DPAD_LEFT?View.FOCUS_LEFT:key==KeyEvent.KEYCODE_DPAD_RIGHT?View.FOCUS_RIGHT:0;
            if(direction!=0){View target=focusSearch(direction);if(target!=null)target.requestFocus();return true;}}
        return super.onKeyDown(key,event);
    }
    @Override public boolean onKeyPreIme(int key,KeyEvent event){if(key==KeyEvent.KEYCODE_BACK&&event.getAction()==KeyEvent.ACTION_UP&&editing){finishEditing();return true;}return super.onKeyPreIme(key,event);}
    public void keyboardLayout(){if(!editing)return;Rect visible=new Rect();getWindowVisibleDisplayFrame(visible);boolean shown=getResources().getDisplayMetrics().heightPixels-visible.bottom>getResources().getDisplayMetrics().heightPixels/6;if(shown)keyboardSeen=true;else if(keyboardSeen)finishEditing();}
}
