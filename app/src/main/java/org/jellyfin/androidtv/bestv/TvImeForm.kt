package org.jellyfin.androidtv.bestv

import android.graphics.Rect
import android.view.View
import android.view.Gravity
import android.view.ViewTreeObserver
import android.widget.*

/** The keyboard is the system TV IME. Only the app's form above it is rearranged. */
internal class TvImeForm(
    private val ui: TvUi, private val page: FrameLayout, private val scroll: ScrollView,
    private val content: FrameLayout, private val fields: List<EditText>, private val labels: List<TextView>,
    private val summary: TextView, private val submit: Button,
) : ViewTreeObserver.OnGlobalLayoutListener, ViewTreeObserver.OnGlobalFocusChangeListener, View.OnAttachStateChangeListener {
    private data class Original(val view: View,val params: FrameLayout.LayoutParams,val visibility: Int)
    private val originals=(0 until content.childCount).map {content.getChildAt(it)}.map {Original(it,FrameLayout.LayoutParams(it.layoutParams as FrameLayout.LayoutParams),it.visibility)}
    private val pageParams=(page.layoutParams as? FrameLayout.LayoutParams)?.let {FrameLayout.LayoutParams(it)}
    private val summaryText=summary.text
    private val submitText=submit.text
    private val submitTag=submit.tag
    private var observed: ViewTreeObserver?=null
    private var compact=false
    private var active: EditText?=null
    init {page.addOnAttachStateChangeListener(this);attach()}
    private fun detach(){observed?.takeIf {it.isAlive}?.let {it.removeOnGlobalLayoutListener(this);it.removeOnGlobalFocusChangeListener(this)};observed=null}
    private fun attach(){detach();observed=page.viewTreeObserver;observed?.addOnGlobalLayoutListener(this);observed?.addOnGlobalFocusChangeListener(this)}
    override fun onViewAttachedToWindow(view: View){attach()}
    override fun onViewDetachedFromWindow(view: View){detach()}
    override fun onGlobalFocusChanged(old: View?,new: View?){if(new in fields)onGlobalLayout()}
    override fun onGlobalLayout(){
        fields.filterIsInstance<TvFormField>().forEach {it.keyboardLayout()}
        val visible=Rect();page.getWindowVisibleDisplayFrame(visible)
        val height=ui.activity.resources.displayMetrics.heightPixels
        val keyboard=height-visible.bottom>ui.px(100f)
        val field=fields.firstOrNull {it.hasFocus()} ?: active
        if(keyboard&&field!=null){
            if(compact&&active===field)return
            compact=true;active=field;submit.text="完成";submit.tag="ime-done"
            pageParams?.takeIf {it.height>0}?.let {page.layoutParams=FrameLayout.LayoutParams(it).apply {gravity=Gravity.TOP or Gravity.CENTER_HORIZONTAL}}
            originals.forEach {original->original.view.visibility=if((original.params.topMargin<ui.px(60f))||original.view===summary||original.view===field||original.view===labels[fields.indexOf(field)]||original.view===submit)View.VISIBLE else View.GONE}
            val label=labels[fields.indexOf(field)]
            fun move(view: View,x: Float,y: Float,w: Float,h: Float){view.layoutParams=FrameLayout.LayoutParams(ui.px(w),ui.px(h)).apply {leftMargin=ui.px(x);topMargin=ui.px(y)}}
            move(summary,50f,94f,844f,32f);move(label,50f,134f,650f,32f)
            move(field,50f,174f,630f,48f);move(submit,700f,174f,194f,48f)
            scroll.post {scroll.scrollTo(0,0)}
        }else if(!keyboard&&compact){
            compact=false;submit.text=submitText;submit.tag=submitTag
            pageParams?.takeIf {it.height>0}?.let {page.layoutParams=FrameLayout.LayoutParams(it)}
            originals.forEach {it.view.layoutParams=FrameLayout.LayoutParams(it.params);it.view.visibility=it.visibility};summary.text=summaryText
            field?.post {field.requestRectangleOnScreen(Rect(0,0,field.width,field.height),true)}
        }
    }
}
