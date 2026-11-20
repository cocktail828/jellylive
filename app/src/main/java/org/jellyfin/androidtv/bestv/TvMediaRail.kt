package org.jellyfin.androidtv.bestv

import android.view.*
import android.widget.HorizontalScrollView
import android.widget.FrameLayout

/** Identical page following and remote navigation for cast, media episodes and CCTV episodes. */
object TvMediaRail {
    fun bind(ui:TvUi,page:FrameLayout,scroll: HorizontalScrollView,cards: List<View>,perPage: Int,above: List<View>,onPage: (Int)->Unit={}) {
        scroll.isFocusable=false;scroll.isSmoothScrollingEnabled=false;scroll.descendantFocusability=ViewGroup.FOCUS_AFTER_DESCENDANTS
        fun follow(index: Int){
            val offset=ui.px(LibraryDetails.pageOffset(index,perPage))
            scroll.scrollTo(offset,0);scroll.post {if(cards[index].hasFocus())scroll.scrollTo(offset,0)};onPage(index/perPage)
            val start=index/perPage*perPage
            above.forEachIndexed {i,button->button.nextFocusDownId=cards[minOf(start+i*perPage/above.size,cards.lastIndex)].id}
        }
        cards.forEachIndexed {i,card->
            card.nextFocusLeftId=cards[maxOf(0,i-1)].id;card.nextFocusRightId=cards[minOf(cards.lastIndex,i+1)].id
            card.nextFocusUpId=above[minOf(i%perPage*above.size/perPage,above.lastIndex)].id;card.nextFocusDownId=card.id
            val previous=card.onFocusChangeListener
            card.setOnFocusChangeListener {view,focused->previous?.onFocusChange(view,focused);if(focused)follow(i)}
            card.setOnKeyListener {_,key,event->
                if(event.action!=KeyEvent.ACTION_DOWN)false else when(key){
                    KeyEvent.KEYCODE_DPAD_LEFT->{cards[maxOf(0,i-1)].requestFocus();true}
                    KeyEvent.KEYCODE_DPAD_RIGHT->{cards[minOf(cards.lastIndex,i+1)].requestFocus();true}
                    KeyEvent.KEYCODE_DPAD_UP->{page.findViewById<View>(card.nextFocusUpId)?.requestFocus();true}
                    KeyEvent.KEYCODE_DPAD_DOWN->true
                    else->false
                }
            }
        }
        if(cards.isNotEmpty())follow(0)
    }
}
