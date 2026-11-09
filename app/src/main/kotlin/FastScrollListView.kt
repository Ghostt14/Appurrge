// Appurrge, GPL-3.0-or-later. Based on Batch Uninstaller by Sarbajit Saha.
package com.appurrge

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.util.TypedValue
import android.view.MotionEvent
import android.widget.AbsListView
import android.widget.ListView

/**
 * The built-in ListView plus a draggable scroll thumb on the right edge. The thumb shows while
 * scrolling and fades out; grab it and drag to jump through the list, with a bubble showing
 * [popupText] for the row you are on. All rows have the same height, which keeps the maths in
 * [ScrollMath] exact in both directions.
 */
class FastScrollListView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) :
    ListView(context, attrs) {

    /** Text for the bubble, given the row index. */
    var popupText: (Int) -> String = { "" }

    private val metrics = resources.displayMetrics
    private val density = metrics.density
    private val thumbWidth = 6 * density
    private val thumbHeight = (48 * density).toInt()
    private val grabWidth = 36 * density // touch area at the right edge, wider than the drawn thumb
    private val grabSlop = 12 * density
    private val green = context.getColor(R.color.colorPrimary)
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
    private var thumbAlpha = 0f // 0..1, animated
    private var fade: ValueAnimator? = null
    private var lastPosition: Float? = null
    private val hide = Runnable { fadeTo(0f) }

    init {
        setOnScrollListener(object : AbsListView.OnScrollListener {
            override fun onScrollStateChanged(view: AbsListView?, scrollState: Int) {}

            override fun onScroll(view: AbsListView?, first: Int, visible: Int, total: Int) {
                val position = scrollPosition()
                // Show only when the list really moved, not when it is merely refilled.
                if (!dragging && position != null && lastPosition != null && position != lastPosition) show()
                lastPosition = position
            }
        })
    }

    // ---- geometry: all in rows, see ScrollMath

    private val trackTop get() = paddingTop.toFloat()
    private val trackHeight get() = height - paddingTop - paddingBottom

    private fun rowHeight(): Int? = getChildAt(0)?.height?.takeIf { it > 0 }

    private fun maxPosition(rowHeight: Int) = ScrollMath.maxPosition(count, trackHeight, rowHeight)

    private fun scrollPosition(): Float? {
        val first = getChildAt(0) ?: return null
        if (first.height <= 0) return null
        return ScrollMath.position(firstVisiblePosition, first.top - paddingTop, first.height)
    }

    /** Thumb top relative to the track, or null if the whole list fits on screen. */
    private fun thumbTop(): Float? {
        if (dragging) return ScrollMath.thumbTop(dragFraction, trackHeight, thumbHeight)
        val rowH = rowHeight() ?: return null
        val max = maxPosition(rowH)
        if (max <= 0f) return null
        val position = scrollPosition() ?: return null
        return ScrollMath.thumbTop(ScrollMath.fraction(position, max), trackHeight, thumbHeight)
    }

    // ---- drawing

    override fun dispatchDraw(canvas: Canvas) {
        super.dispatchDraw(canvas)
        if (thumbAlpha <= 0f) return
        val top = trackTop + (thumbTop() ?: return)
        val right = width - 2 * density
        thumbPaint.alpha = (255 * thumbAlpha).toInt()
        canvas.drawRoundRect(right - thumbWidth, top, right, top + thumbHeight, thumbWidth / 2, thumbWidth / 2, thumbPaint)
        if (dragging) drawBubble(canvas, right - thumbWidth, top)
    }

    private fun drawBubble(canvas: Canvas, thumbLeft: Float, thumbTop: Float) {
        val h = 44 * density
        val w = maxOf(textPaint.measureText(label) + 32 * density, 56 * density)
        val right = thumbLeft - 12 * density
        val cy = (thumbTop + thumbHeight / 2).coerceIn(h / 2, height - h / 2)
        canvas.drawRoundRect(right - w, cy - h / 2, right, cy + h / 2, 12 * density, 12 * density, bubblePaint)
        val fm = textPaint.fontMetrics
        canvas.drawText(label, right - w / 2, cy - (fm.ascent + fm.descent) / 2, textPaint)
    }

    // ---- touch

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        if (ev.actionMasked == MotionEvent.ACTION_DOWN && ev.x >= width - grabWidth) {
            val top = trackTop + (thumbTop() ?: return super.onInterceptTouchEvent(ev))
            if (ev.y >= top - grabSlop && ev.y <= top + thumbHeight + grabSlop) {
                dragging = true
                parent?.requestDisallowInterceptTouchEvent(true) // keep pull-to-refresh out of the way
                show()
                dragTo(ev.y)
                return true
            }
        }
        return super.onInterceptTouchEvent(ev)
    }

    override fun onTouchEvent(ev: MotionEvent): Boolean {
        if (!dragging) return super.onTouchEvent(ev)
        when (ev.actionMasked) {
            MotionEvent.ACTION_MOVE -> dragTo(ev.y)
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                dragging = false
                show()
            }
        }
        return true
    }

    private fun dragTo(y: Float) {
        val rowH = rowHeight() ?: return
        dragFraction = ScrollMath.fractionAt(y - trackTop, trackHeight, thumbHeight)
        val (index, offset) = ScrollMath.target(dragFraction, maxPosition(rowH), rowH)
        setSelectionFromTop(index, -offset)
        label = popupText(index.coerceIn(0, maxOf(0, count - 1)))
        invalidate()
    }

    // ---- show / fade

    private fun show() {
        fade?.cancel()
        thumbAlpha = 1f
        invalidate()
        removeCallbacks(hide)
        if (!dragging) postDelayed(hide, 1200)
    }

    private fun fadeTo(target: Float) {
        fade?.cancel()
        fade = ValueAnimator.ofFloat(thumbAlpha, target).apply {
            duration = 200
            addUpdateListener {
                thumbAlpha = it.animatedValue as Float
                invalidate()
            }
            start()
        }
    }
}
