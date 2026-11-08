// Appurrge, GPL-3.0-or-later. Based on Batch Uninstaller by Sarbajit Saha.
package com.appurrge

import android.animation.ValueAnimator
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.TypedValue
import android.view.MotionEvent
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

/**
 * A draggable scroll thumb on the right edge of a vertical list. It shows while scrolling and fades
 * out; grab it and drag to jump through the list, with a bubble showing [popupText] for the position.
 * Replaces a library (which needed AppCompat) with ~100 lines.
 */
class FastScroller(private val list: RecyclerView, private val popupText: (Int) -> String) :
    RecyclerView.ItemDecoration(), RecyclerView.OnItemTouchListener {

    private val metrics = list.resources.displayMetrics
    private val density = metrics.density
    private val thumbWidth = 6 * density
    private val thumbHeight = (48 * density).toInt()
    private val grabWidth = 36 * density // touch area at the right edge, wider than the drawn thumb
    private val grabSlop = 12 * density
    private val green = list.context.getColor(R.color.colorPrimary)
    private val thumbPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = green }
    private val bubblePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = green }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 20f, metrics)
        isFakeBoldText = true
        textAlign = Paint.Align.CENTER
    }

    private var dragging = false
    private var dragFraction = 0f
    private var label = ""
    private var visibility = 0f // 0..1, animated
    private var fade: ValueAnimator? = null
    private val hide = Runnable { fadeTo(0f) }

    init {
        list.addItemDecoration(this)
        list.addOnItemTouchListener(this)
        list.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                if (dy != 0 && !dragging) show()
            }
        })
    }

    private val trackTop get() = list.paddingTop.toFloat()
    private val trackHeight get() = list.height - list.paddingTop - list.paddingBottom

    /** Thumb top relative to the track, or null if the whole list already fits on screen. */
    private fun thumbTop(): Float? =
        if (dragging) {
            ScrollMath.thumbTopFor(dragFraction, trackHeight, thumbHeight)
        } else {
            ScrollMath.thumbTop(
                list.computeVerticalScrollOffset(), list.computeVerticalScrollRange(),
                list.computeVerticalScrollExtent(), trackHeight, thumbHeight
            )
        }

    // ---- drawing

    override fun onDrawOver(c: Canvas, parent: RecyclerView, state: RecyclerView.State) {
        if (visibility <= 0f) return
        val top = trackTop + (thumbTop() ?: return)
        val right = parent.width - 2 * density
        thumbPaint.alpha = (255 * visibility).toInt()
        c.drawRoundRect(right - thumbWidth, top, right, top + thumbHeight, thumbWidth / 2, thumbWidth / 2, thumbPaint)
        if (dragging) drawBubble(c, parent, right - thumbWidth, top)
    }

    private fun drawBubble(c: Canvas, parent: RecyclerView, thumbLeft: Float, thumbTop: Float) {
        val h = 44 * density
        val w = maxOf(textPaint.measureText(label) + 32 * density, 56 * density)
        val right = thumbLeft - 12 * density
        val cy = (thumbTop + thumbHeight / 2).coerceIn(h / 2, parent.height - h / 2)
        c.drawRoundRect(right - w, cy - h / 2, right, cy + h / 2, 12 * density, 12 * density, bubblePaint)
        val fm = textPaint.fontMetrics
        c.drawText(label, right - w / 2, cy - (fm.ascent + fm.descent) / 2, textPaint)
    }

    // ---- touch

    override fun onInterceptTouchEvent(rv: RecyclerView, e: MotionEvent): Boolean {
        if (e.actionMasked == MotionEvent.ACTION_DOWN && e.x >= rv.width - grabWidth) {
            val top = trackTop + (thumbTop() ?: return false)
            if (e.y >= top - grabSlop && e.y <= top + thumbHeight + grabSlop) {
                dragging = true
                rv.parent?.requestDisallowInterceptTouchEvent(true) // keep pull-to-refresh out of the way
                show()
                dragTo(e.y)
            }
        }
        return dragging
    }

    override fun onTouchEvent(rv: RecyclerView, e: MotionEvent) {
        when (e.actionMasked) {
            MotionEvent.ACTION_MOVE -> if (dragging) dragTo(e.y)
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> if (dragging) {
                dragging = false
                show()
            }
        }
    }

    override fun onRequestDisallowInterceptTouchEvent(disallowIntercept: Boolean) {}

    private fun dragTo(y: Float) {
        dragFraction = ScrollMath.fraction(y - trackTop, trackHeight, thumbHeight)
        val position = ScrollMath.position(dragFraction, list.adapter?.itemCount ?: 0)
        (list.layoutManager as? LinearLayoutManager)?.scrollToPositionWithOffset(position, 0)
        label = popupText(position)
        list.invalidate()
    }

    // ---- show / fade

    private fun show() {
        fade?.cancel()
        visibility = 1f
        list.invalidate()
        list.removeCallbacks(hide)
        if (!dragging) list.postDelayed(hide, 1200)
    }

    private fun fadeTo(target: Float) {
        fade?.cancel()
        fade = ValueAnimator.ofFloat(visibility, target).apply {
            duration = 200
            addUpdateListener {
                visibility = it.animatedValue as Float
                list.invalidate()
            }
            start()
        }
    }
}
