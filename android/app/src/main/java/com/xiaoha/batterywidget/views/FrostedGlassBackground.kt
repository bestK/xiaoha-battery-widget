package com.xiaoha.batterywidget.views

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.view.View
import android.view.animation.LinearInterpolator
import com.xiaoha.batterywidget.R
import kotlin.random.Random

class FrostedGlassBackground @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val spots = mutableListOf<LightSpot>()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val lightSpotDrawable: Drawable? = context.getDrawable(R.drawable.light_spot)
    private val random = Random(System.currentTimeMillis())

    init {
        // 设置背景色（半透明白色）
        setBackgroundColor(Color.parseColor("#BBFFFFFF"))
        
        // 初始化5个光斑
        repeat(5) {
            addNewSpot()
        }
    }

    private fun addNewSpot() {
        val spot = LightSpot(
            x = random.nextFloat() * width,
            y = random.nextFloat() * height,
            size = random.nextInt(100, 300).toFloat(),
            duration = random.nextInt(3000, 8000),
            delay = random.nextInt(0, 2000)
        )
        spots.add(spot)
        animateSpot(spot)
    }

    private fun animateSpot(spot: LightSpot) {
        // 创建位置动画
        val xAnimator = ValueAnimator.ofFloat(spot.x, random.nextFloat() * width).apply {
            duration = spot.duration.toLong()
            startDelay = spot.delay.toLong()
            interpolator = LinearInterpolator()
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.REVERSE
            addUpdateListener { animation ->
                spot.x = animation.animatedValue as Float
                invalidate()
            }
        }

        val yAnimator = ValueAnimator.ofFloat(spot.y, random.nextFloat() * height).apply {
            duration = spot.duration.toLong()
            startDelay = spot.delay.toLong()
            interpolator = LinearInterpolator()
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.REVERSE
            addUpdateListener { animation ->
                spot.y = animation.animatedValue as Float
                invalidate()
            }
        }

        // 创建大小动画
        val sizeAnimator = ValueAnimator.ofFloat(spot.size, spot.size * 1.5f).apply {
            duration = (spot.duration * 0.7f).toLong()
            startDelay = spot.delay.toLong()
            interpolator = LinearInterpolator()
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.REVERSE
            addUpdateListener { animation ->
                spot.currentSize = animation.animatedValue as Float
                invalidate()
            }
        }

        // 启动动画
        xAnimator.start()
        yAnimator.start()
        sizeAnimator.start()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        spots.forEach { spot ->
            lightSpotDrawable?.let { drawable ->
                val left = spot.x - spot.currentSize / 2
                val top = spot.y - spot.currentSize / 2
                val right = spot.x + spot.currentSize / 2
                val bottom = spot.y + spot.currentSize / 2
                drawable.setBounds(
                    left.toInt(),
                    top.toInt(),
                    right.toInt(),
                    bottom.toInt()
                )
                drawable.draw(canvas)
            }
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w != oldw || h != oldh) {
            spots.clear()
            repeat(5) {
                addNewSpot()
            }
        }
    }

    private data class LightSpot(
        var x: Float,
        var y: Float,
        val size: Float,
        var currentSize: Float = size,
        val duration: Int,
        val delay: Int
    )
}
