package com.example.workplacecamera

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View

class GridOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val paint = Paint().apply {
        color = Color.parseColor("#44FFFFFF")
        strokeWidth = 1.5f
        style = Paint.Style.STROKE
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val width = width.toFloat()
        val height = height.toFloat()

        // Vertical lines (at 1/3 and 2/3)
        canvas.drawLine(width / 3, 0f, width / 3, height, paint)
        canvas.drawLine((width / 3) * 2, 0f, (width / 3) * 2, height, paint)

        // Horizontal lines (at 1/3 and 2/3)
        canvas.drawLine(0f, height / 3, width, height / 3, paint)
        canvas.drawLine(0f, (height / 3) * 2, width, (height / 3) * 2, paint)
    }
}
