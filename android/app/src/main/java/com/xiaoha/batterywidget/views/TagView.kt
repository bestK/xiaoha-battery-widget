package com.xiaoha.batterywidget.views

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.util.AttributeSet
import android.util.TypedValue
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.xiaoha.batterywidget.R

class TagView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {

    private lateinit var textView: TextView
    private var closeIcon: ImageView? = null
    
    // 标签文本
    var text: CharSequence
        get() = textView.text
        set(value) {
            if (::textView.isInitialized) {
                textView.text = value
            }
        }
    
    // 是否可关闭
    var closeable: Boolean = false
        set(value) {
            field = value
            closeIcon?.visibility = if (value) View.VISIBLE else View.GONE
        }
    
    // 标签类型（不同类型对应不同颜色）
    var type: TagType = TagType.NORMAL
        set(value) {
            field = value
            if (::textView.isInitialized) {
                updateColors()
            }
        }

    init {
        // 设置默认样式
        orientation = HORIZONTAL
        gravity = Gravity.CENTER
        layoutParams = LayoutParams(
            LayoutParams.WRAP_CONTENT,
            dpToPx(24)  // 默认高度24dp
        ).apply {
            gravity = Gravity.CENTER_VERTICAL
        }
        
        // 设置默认内边距和最小宽度
        val horizontalPadding = dpToPx(8)
        setPadding(horizontalPadding, 0, horizontalPadding, 0)
        minimumWidth = dpToPx(48)  // 默认最小宽度48dp
        
        // 设置背景
        background = context.getDrawable(R.drawable.tag_background)
        
        // 添加文本视图
        textView = TextView(context).apply {
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            layoutParams = LayoutParams(
                LayoutParams.WRAP_CONTENT,
                LayoutParams.MATCH_PARENT
            ).apply {
                gravity = Gravity.CENTER
            }
            gravity = Gravity.CENTER
            includeFontPadding = false
        }
        addView(textView)
        
        // 添加关闭图标（如果需要）
        closeIcon = ImageView(context).apply {
            layoutParams = LayoutParams(dpToPx(16), dpToPx(16)).apply {
                marginStart = dpToPx(4)
            }
            visibility = View.GONE
            setImageResource(android.R.drawable.ic_menu_close_clear_cancel)
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            setOnClickListener {
                onCloseClick?.invoke()
            }
        }
        addView(closeIcon)

        // 读取自定义属性（允许在XML中覆盖默认值）
        val typedArray = context.obtainStyledAttributes(attrs, R.styleable.TagView)
        try {
            text = typedArray.getString(R.styleable.TagView_tagText) ?: ""
            val typeOrdinal = typedArray.getInt(R.styleable.TagView_tagType, 0)
            type = TagType.values()[typeOrdinal]
            
            // 读取margin（如果在XML中设置了的话）
            val margin = typedArray.getDimensionPixelSize(
                R.styleable.TagView_android_layout_margin,
                dpToPx(8)  // 默认margin 8dp
            )
            (layoutParams as? MarginLayoutParams)?.setMargins(margin, margin, margin, margin)
        } finally {
            typedArray.recycle()
        }
        
        // 设置背景
        background = context.getDrawable(R.drawable.tag_background)
        
        // 添加文本视图
        textView = TextView(context).apply {
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            layoutParams = LayoutParams(
                LayoutParams.WRAP_CONTENT,
                LayoutParams.MATCH_PARENT
            ).apply {
                gravity = Gravity.CENTER
            }
            gravity = Gravity.CENTER
            includeFontPadding = false  // 移除字体内边距
        }
        addView(textView)
        
        // 添加关闭图标（如果需要）
        closeIcon = ImageView(context).apply {
            layoutParams = LayoutParams(dpToPx(16), dpToPx(16)).apply {
                marginStart = dpToPx(4)
            }
            visibility = View.GONE
            setImageResource(android.R.drawable.ic_menu_close_clear_cancel)
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            setOnClickListener {
                onCloseClick?.invoke()
            }
        }
        addView(closeIcon)
        
        // 设置默认颜色
        updateColors()
    }
    
    // 关闭按钮点击回调
    var onCloseClick: (() -> Unit)? = null
    
    private fun updateColors() {
        val colors = type.getColors()
        
        // 设置背景颜色
        background?.setTintList(ColorStateList.valueOf(colors.backgroundColor))
        
        // 设置文本颜色
        textView.setTextColor(colors.textColor)
        
        // 设置边框颜色
        background?.setTint(colors.borderColor)
        
        // 设置关闭图标颜色
        closeIcon?.setColorFilter(colors.textColor)
    }
    
    private fun dpToPx(dp: Int): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            dp.toFloat(),
            resources.displayMetrics
        ).toInt()
    }
    
    // 标签类型枚举
    enum class TagType {
        NORMAL,
        SUCCESS,
        WARNING,
        DANGER,
        INFO;
        
        fun getColors(): TagColors {
            return when (this) {
                NORMAL -> TagColors(
                    backgroundColor = Color.parseColor("#F0F2F5"),
                    textColor = Color.parseColor("#909399"),
                    borderColor = Color.parseColor("#E4E7ED")
                )
                SUCCESS -> TagColors(
                    backgroundColor = Color.parseColor("#F0F9EB"),
                    textColor = Color.parseColor("#67C23A"),
                    borderColor = Color.parseColor("#E1F3D8")
                )
                WARNING -> TagColors(
                    backgroundColor = Color.parseColor("#FDF6EC"),
                    textColor = Color.parseColor("#E6A23C"),
                    borderColor = Color.parseColor("#FAECD8")
                )
                DANGER -> TagColors(
                    backgroundColor = Color.parseColor("#FEF0F0"),
                    textColor = Color.parseColor("#F56C6C"),
                    borderColor = Color.parseColor("#FDE2E2")
                )
                INFO -> TagColors(
                    backgroundColor = Color.parseColor("#F4F4F5"),
                    textColor = Color.parseColor("#909399"),
                    borderColor = Color.parseColor("#E9E9EB")
                )
            }
        }
    }
    
    // 标签颜色数据类
    data class TagColors(
        val backgroundColor: Int,
        val textColor: Int,
        val borderColor: Int
    )
}
