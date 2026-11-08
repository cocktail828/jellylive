/* JellyLive library filter sheet and sorting dialog. GPL-2.0. */
package org.jellyfin.androidtv.bestv

import android.app.Activity
import android.app.AlertDialog
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.StateListDrawable
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind

/**
 * Both methods show the dialog before returning. The caller owns OnDismissListener and focus restoration.
 * Selection and Reset take effect immediately; closing either dialog keeps the current choices.
 */
object LibraryBrowseDialogs {
    private const val SHEET = 0xff171d32.toInt()
    private const val SELECTED = 0xff514383.toInt()
    private const val SELECTED_STROKE = 0xff9a88ff.toInt()

    private data class Facets(val genres: List<String>, val years: List<Int>, val resolutions: List<Int>, val regions: List<String>)
    private data class Selection(val button: Button, val selected: (LibraryBrowse.Query) -> Boolean)

    fun filters(
        activity: Activity,
        ui: TvUi,
        items: List<BaseItemDto>,
        query: LibraryBrowse.Query,
        onChanged: (LibraryBrowse.Query) -> Unit,
    ): AlertDialog {
        val panel = FrameLayout(activity).apply { background = ui.glass(SHEET) }
        val dialog = AlertDialog.Builder(activity).create().apply { setView(panel, 0, 0, 0, 0) }
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        val selection = LibraryBrowseSelection(query, onChanged)
        val selections = mutableListOf<Selection>()
        ui.title(panel, "筛选", 22f, 25f, 200f, 32f)
        val body = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
        val scroll = ScrollView(activity).apply { isFillViewport = false; isHorizontalScrollBarEnabled = false; addView(body) }
        ui.at(panel, scroll, 22f, 58f, 296f, 382f)
        body.addView(ui.text("正在加载筛选条件…",14,TvUi.MUTED), LinearLayout.LayoutParams(-1,ui.px(38f)))
        fun refreshSelections() { selections.forEach { it.button.isSelected = it.selected(selection.current) } }
        fun edit(value: LibraryBrowse.Query) { if(selection.select(value)) refreshSelections() }

        fun <T> group(
            title: String,
            values: List<T>,
            columns: Int,
            label: (T) -> String,
            selected: (LibraryBrowse.Query, T?) -> Boolean,
            update: (LibraryBrowse.Query, T?) -> LibraryBrowse.Query,
        ) {
            val heading = ui.text(title, 14, TvUi.MUTED)
            body.addView(heading, LinearLayout.LayoutParams(-1, ui.px(22f)).apply {
                topMargin = ui.px(if (body.childCount == 0) 0f else 10f)
                bottomMargin = ui.px(8f)
            })
            val choices: List<T?> = listOf(null) + values
            val space = 7f
            val width = (296f - space * (columns - 1)) / columns
            val height = if (ui.fontScale > 1f) 44f else 35f
            val grid = FrameLayout(activity)
            choices.forEachIndexed { index, value ->
                val button = ui.button(if (value == null) "全部" else label(value)) { edit(update(selection.current, value)) }
                button.background = choiceBackground(ui)
                button.setTextSize(TypedValue.COMPLEX_UNIT_PX, 13f * ui.scale * ui.fontScale)
                button.maxLines = 2
                button.ellipsize = TextUtils.TruncateAt.END
                selections += Selection(button) { selected(it, value) }
                ui.at(grid, button, index % columns * (width + space), index / columns * (height + space), width, height)
            }
            val rows = (choices.size + columns - 1) / columns
            body.addView(grid, LinearLayout.LayoutParams(-1, ui.px(rows * (height + space) - space)))
        }

        val reset=ui.button(panel, "重置", 22f, 479f, 142f, 38f) { if(selection.resetFilters()) refreshSelections() }
        val close=ui.button(panel, "关闭", 176f, 479f, 142f, 38f) { dialog.dismiss() }
        refreshSelections()
        show(dialog, ui, 340f, 540f, Gravity.END or Gravity.CENTER_VERTICAL)
        TvGlass.apply(activity,ui,panel,620f,0f,340f,540f)
        cancelOnDetach(dialog, scope)
        close.requestFocus()

        scope.launch {
            val facets = withContext(Dispatchers.Default) {
                // Facets describe the whole current category, never only the displayed page
                // or the remaining matches of another selected condition.
                val all = LibraryBrowse.select(items, query.copy(
                    state = LibraryBrowse.State.ALL, genre = null, year = null, resolution = null, region = null,
                ))
                Facets(
                    all.flatMap { it.genres.orEmpty() }.filter { it.isNotBlank() }.distinct().sorted(),
                    all.mapNotNull { LibraryMetadata.year(it) }.distinct().sortedDescending(),
                    all.mapNotNull { LibraryBrowse.resolution(it) }.distinct().sortedDescending(),
                    all.flatMap { LibraryBrowse.regions(it) }.distinct().sorted(),
                )
            }
            if (!dialog.isShowing) return@launch
            body.removeAllViews()
            group("观看状态", LibraryBrowse.State.entries.filter { it == LibraryBrowse.State.UNSEEN || it == LibraryBrowse.State.FINISHED }, 3,
                { it.label }, { q, value -> q.state == (value ?: LibraryBrowse.State.ALL) },
                { q, value -> q.copy(state = value ?: LibraryBrowse.State.ALL) })
            if (facets.genres.isNotEmpty()) group("类型", facets.genres, 3,
                { it }, { q, value -> q.genre == value }, { q, value -> q.copy(genre = value) })
            if (facets.resolutions.isNotEmpty()) group("分辨率", facets.resolutions, 3,
                ::resolutionLabel, { q, value -> q.resolution == value }, { q, value -> q.copy(resolution = value) })
            if (facets.years.isNotEmpty()) group("上映年份", facets.years, 3,
                { it.toString() }, { q, value -> q.year == value }, { q, value -> q.copy(year = value) })
            if (facets.regions.isNotEmpty()) group("地区", facets.regions, 2,
                { it }, { q, value -> q.region == value }, { q, value -> q.copy(region = value) })
            refreshSelections()
            if (dialog.currentFocus === close) selections.firstOrNull { it.selected(selection.current) }?.button?.requestFocus()
        }
        return dialog
    }

    fun sort(
        activity: Activity,
        ui: TvUi,
        query: LibraryBrowse.Query,
        onChanged: (LibraryBrowse.Query) -> Unit,
    ): AlertDialog {
        val panel = FrameLayout(activity).apply { background = ui.glass(SHEET) }
        val dialog = AlertDialog.Builder(activity).create().apply { setView(panel, 0, 0, 0, 0) }
        val selection = LibraryBrowseSelection(query, onChanged)
        val sortButtons = mutableListOf<Pair<LibraryBrowse.Sort, Button>>()
        lateinit var ascending: Button
        lateinit var descending: Button

        fun refresh() {
            sortButtons.forEach { (sort, button) -> button.isSelected = selection.current.sort == sort }
            TvIcons.text(ascending, directionLabel(selection.current.sort, true))
            TvIcons.text(descending, directionLabel(selection.current.sort, false))
            ascending.isSelected = selection.current.ascending
            descending.isSelected = !selection.current.ascending
        }

        ui.title(panel, "排序", 22f, 25f, 280f, 32f)
        listOf(LibraryBrowse.Sort.NAME,LibraryBrowse.Sort.ADDED,LibraryBrowse.Sort.YEAR,LibraryBrowse.Sort.RATING,LibraryBrowse.Sort.DURATION).forEachIndexed { index, option ->
            val button = ui.button(panel, option.label, 22f, 71f + index * 46f, 296f, 38f) {
                if (selection.selectSort(option)) refresh()
            }
            button.background = choiceBackground(ui)
            sortButtons += option to button
        }
        ui.text(panel, "排列方向", 14, TvUi.MUTED, 22f, 322f, 296f, 24f)
        ascending = ui.button(panel, "", 22f, 358f, 142f, 38f) { if (selection.setAscending(true)) refresh() }
        descending = ui.button(panel, "", 176f, 358f, 142f, 38f) { if (selection.setAscending(false)) refresh() }
        ascending.background = choiceBackground(ui)
        descending.background = choiceBackground(ui)
        val close=ui.button(panel, "关闭", 22f, 479f, 296f, 38f) { dialog.dismiss() }
        refresh()
        show(dialog, ui, 340f, 540f, Gravity.END or Gravity.CENTER_VERTICAL)
        TvGlass.apply(activity,ui,panel,620f,0f,340f,540f)
        sortButtons.firstOrNull { it.first == query.sort }?.second?.requestFocus() ?: close.requestFocus()
        return dialog
    }

    private fun choiceBackground(ui: TvUi) = StateListDrawable().apply {
        addState(intArrayOf(android.R.attr.state_focused), ui.surface(TvUi.TILE, true))
        addState(intArrayOf(android.R.attr.state_pressed), ui.surface(TvUi.TILE, true))
        addState(intArrayOf(android.R.attr.state_selected), ui.surface(SELECTED, false).apply { setStroke(ui.px(1f), SELECTED_STROKE) })
        addState(intArrayOf(), ui.surface(TvUi.TILE, false))
    }

    private fun resolutionLabel(value: Int) = if (value == 2160) "4K" else "${value}p"

    private fun directionLabel(sort: LibraryBrowse.Sort, ascending: Boolean) = when (sort) {
        LibraryBrowse.Sort.NAME -> if (ascending) "名称正序" else "名称倒序"
        LibraryBrowse.Sort.RATING -> if (ascending) "从低到高" else "从高到低"
        LibraryBrowse.Sort.YEAR -> if (ascending) "从旧到新" else "从新到旧"
        LibraryBrowse.Sort.DURATION -> if(ascending) "从短到长" else "从长到短"
        LibraryBrowse.Sort.ADDED, LibraryBrowse.Sort.WATCHED -> if (ascending) "从早到晚" else "从晚到早"
    }

    private fun show(dialog: AlertDialog, ui: TvUi, width: Float, height: Float, gravity: Int) {
        dialog.show()
        dialog.window?.apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            decorView.setPadding(0, 0, 0, 0)
            decorView.systemUiVisibility = ui.activity.window.decorView.systemUiVisibility
            addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            setDimAmount(.18f)
            setGravity(gravity)
            setLayout(ui.px(width), ui.px(height))
        }
    }

    /** Keep the caller's OnDismissListener available for its dialog/focus bookkeeping. */
    private fun cancelOnDetach(dialog: AlertDialog, scope: CoroutineScope) {
        dialog.window?.decorView?.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(view: View) = Unit
            override fun onViewDetachedFromWindow(view: View) { scope.cancel() }
        })
    }
}
