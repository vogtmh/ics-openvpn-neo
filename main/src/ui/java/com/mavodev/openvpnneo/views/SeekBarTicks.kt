/*
 * Copyright (c) 2012-2016 Arne Schwabe
 * Distributed under the GNU GPL v2 with additional terms. For full terms see the file doc/LICENSE.txt
 */
package com.mavodev.openvpnneo.views

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import androidx.appcompat.widget.AppCompatSeekBar
import androidx.core.content.ContextCompat

class SeekBarTicks : AppCompatSeekBar {
    private lateinit var mTickPaint: Paint
    private val tickHeightRatio = 0.6f

    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs) {
        initTicks(context)
    }

    constructor(context: Context, attrs: AttributeSet?, defStyle: Int) : super(context, attrs, defStyle) {
        initTicks(context)
    }

    private fun initTicks(context: Context) {
        mTickPaint = Paint()
        mTickPaint.color = ContextCompat.getColor(context, android.R.color.black)
    }

    @Synchronized
    override fun onDraw(canvas: Canvas) {
        drawTicks(canvas)
        super.onDraw(canvas)
    }

    private fun drawTicks(canvas: Canvas) {
        val available = width - paddingLeft - paddingRight
        val availableHeight = height - paddingBottom - paddingTop

        val extrapadding = ((availableHeight - availableHeight * tickHeightRatio) / 2).toInt()

        val tickSpacing = available / max

        for (i in 1 until max) {
            val x = (paddingLeft + i * tickSpacing).toFloat()
            canvas.drawLine(
                x, (paddingTop + extrapadding).toFloat(),
                x, (height - paddingBottom - extrapadding).toFloat(), mTickPaint
            )
        }
    }
}
