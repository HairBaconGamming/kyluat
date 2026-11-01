package com.cainghien.companion

import android.animation.ArgbEvaluator
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.text.Editable
import android.text.InputFilter
import android.text.InputType
import android.text.TextWatcher
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

enum class Tone { ACCENT, NEUTRAL, WARN, DANGER }

enum class Variant { PRIMARY, GHOST, QUIET, DANGER }

object Ui {
    val ACCENT = Color.parseColor("#2F6F5E")
    val ACCENT_SOFT = Color.parseColor("#EDF3F0")
    val ACCENT_LINE = Color.parseColor("#D5E4DD")
    val TEXT = Color.parseColor("#10151A")
    val TEXT_2 = Color.parseColor("#5C666F")
    val TEXT_3 = Color.parseColor("#8B949B")
    val MUTED = TEXT_2
    val LINE = Color.parseColor("#E8EBED")
    val LINE_SOFT = Color.parseColor("#F1F3F4")
    val BG = Color.parseColor("#F5F6F7")
    val SURFACE = Color.WHITE
    val SURFACE_2 = Color.parseColor("#F7F8F9")
    val RING_TRACK = Color.parseColor("#EEF0F2")
    val TRACK_OFF = Color.parseColor("#D7DCE0")
    val STEP_OFF = Color.parseColor("#E4E7E9")
    val DISABLED_BG = Color.parseColor("#ECEFF1")
    val DISABLED_FG = Color.parseColor("#A6ADB3")
    val WARN = Color.parseColor("#96610A")
    val WARN_SOFT = Color.parseColor("#FDF4E7")
    val WARN_LINE = Color.parseColor("#F0DFC2")
    val DANGER = Color.parseColor("#B42318")
    val DANGER_SOFT = Color.parseColor("#FCEEEE")
    val DANGER_LINE = Color.parseColor("#F0D3CF")
    val CHEVRON = Color.parseColor("#C2C8CC")

    val MEDIUM = Typeface.create("sans-serif-medium", Typeface.NORMAL)

    fun dp(context: Context, value: Int): Int =
        (value * context.resources.displayMetrics.density).toInt()

    fun dp(context: Context, value: Float): Int =
        (value * context.resources.displayMetrics.density).toInt()

    fun rounded(color: Int, radiusDp: Int, context: Context, strokeColor: Int? = null): GradientDrawable {
        val drawable = GradientDrawable()
        drawable.shape = GradientDrawable.RECTANGLE
        drawable.setColor(color)
        drawable.cornerRadius = dp(context, radiusDp).toFloat()
        if (strokeColor != null) drawable.setStroke(dp(context, 1), strokeColor)
        return drawable
    }

    fun oval(color: Int, context: Context, strokeColor: Int? = null): GradientDrawable {
        val drawable = GradientDrawable()
        drawable.shape = GradientDrawable.OVAL
        drawable.setColor(color)
        if (strokeColor != null) drawable.setStroke(dp(context, 1), strokeColor)
        return drawable
    }

    fun ripple(base: GradientDrawable, highlight: Int): RippleDrawable =
        RippleDrawable(ColorStateList.valueOf(highlight), base, null)

    fun toneColors(tone: Tone): Triple<Int, Int, Int> = when (tone) {
        Tone.ACCENT -> Triple(ACCENT_SOFT, ACCENT_LINE, ACCENT)
        Tone.NEUTRAL -> Triple(Color.parseColor("#F2F4F5"), Color.parseColor("#E6E9EB"), TEXT_2)
        Tone.WARN -> Triple(WARN_SOFT, WARN_LINE, WARN)
        Tone.DANGER -> Triple(DANGER_SOFT, DANGER_LINE, DANGER)
    }

    fun icon(context: Context, res: Int, color: Int, sizeDp: Int): ImageView {
        val view = ImageView(context)
        val drawable: Drawable? = try {
            context.getDrawable(res)
        } catch (error: Exception) {
            null
        }
        view.setImageDrawable(drawable)
        view.imageTintList = ColorStateList.valueOf(color)
        view.scaleType = ImageView.ScaleType.FIT_CENTER
        val size = dp(context, sizeDp)
        view.layoutParams = ViewGroup.LayoutParams(size, size)
        return view
    }

    fun label(
        context: Context,
        text: String,
        sizeSp: Float = 14.5f,
        color: Int = TEXT,
        bold: Boolean = false,
    ): TextView {
        val view = TextView(context)
        view.text = text
        view.textSize = sizeSp
        view.setTextColor(color)
        view.setTypeface(if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT, Typeface.NORMAL)
        view.setLineSpacing(0f, 1.15f)
        return view
    }

    fun eyebrow(context: Context, text: String, color: Int = TEXT_3): TextView {
        val view = label(context, text.uppercase(), 11f, color, true)
        view.letterSpacing = 0.1f
        return view
    }

    fun screen(context: Context): LinearLayout {
        val view = LinearLayout(context)
        view.orientation = LinearLayout.VERTICAL
        view.setBackgroundColor(BG)
        view.setPadding(dp(context, 16), dp(context, 8), dp(context, 16), dp(context, 16))
        return view
    }

    fun scroll(child: View): ScrollView {
        val scroll = ScrollView(child.context)
        scroll.isFillViewport = true
        scroll.setBackgroundColor(BG)
        scroll.addView(child)
        return scroll
    }

    fun card(context: Context, radiusDp: Int = 16, padDp: Int = 14, marginDp: Int = 11): LinearLayout {
        val view = LinearLayout(context)
        view.orientation = LinearLayout.VERTICAL
        view.background = rounded(SURFACE, radiusDp, context, LINE)
        view.setPadding(dp(context, padDp), dp(context, padDp), dp(context, padDp), dp(context, padDp))
        view.elevation = dp(context, 1).toFloat()
        val params = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        params.bottomMargin = dp(context, marginDp)
        view.layoutParams = params
        return view
    }

    fun hero(context: Context): LinearLayout = card(context, radiusDp = 20, padDp = 15, marginDp = 12).apply {
        elevation = dp(context, 3).toFloat()
    }

    fun group(context: Context): UiGroup = UiGroup(context)

    fun section(context: Context, text: String): TextView {
        val view = eyebrow(context, text)
        val params = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        params.topMargin = dp(context, 5)
        params.bottomMargin = dp(context, 7)
        params.leftMargin = dp(context, 3)
        view.layoutParams = params
        return view
    }

    fun row(context: Context, leading: View? = null): UiRow = UiRow(context, leading)

    fun tile(context: Context, iconRes: Int, tone: Tone = Tone.ACCENT, sizeDp: Int = 36): UiTile =
        UiTile(context, sizeDp, iconRes, tone)

    fun chevron(context: Context): ImageView {
        val view = icon(context, R.drawable.ic_chevron_right, CHEVRON, 16)
        val params = LinearLayout.LayoutParams(dp(context, 16), dp(context, 16))
        params.leftMargin = dp(context, 4)
        view.layoutParams = params
        return view
    }

    fun pill(context: Context, text: String, tone: Tone = Tone.ACCENT, dot: Boolean = true): UiPill =
        UiPill(context).apply { set(text, tone, dot) }

    fun button(context: Context, text: String, variant: Variant = Variant.PRIMARY, big: Boolean = false): UiButton =
        UiButton(context, variant).apply {
            this.text = text
            this.big = big
            restyle()
        }

    fun segmented(context: Context, labels: List<String>, selected: Int, onSelect: (Int) -> Unit): UiSegmented =
        UiSegmented(context, labels, onSelect).apply { select(selected, false) }

    fun ring(context: Context, sizeDp: Int, strokeDp: Float, progress: Float, color: Int = ACCENT): UiRing =
        UiRing(context, sizeDp, strokeDp).apply {
            arcColor = color
            this.progress = progress
        }

    fun ringStack(context: Context, sizeDp: Int, strokeDp: Float, color: Int = ACCENT): RingStack =
        RingStack(context, sizeDp, strokeDp, color)

    fun stat(context: Context, label: String): UiStat = UiStat(context, label)

    fun steps(context: Context, total: Int, done: Int): UiSteps = UiSteps(context, total).apply { setDone(done) }

    fun dividerText(context: Context, text: String): LinearLayout {
        val row = LinearLayout(context)
        row.orientation = LinearLayout.HORIZONTAL
        row.gravity = Gravity.CENTER_VERTICAL
        val line = View(context)
        line.setBackgroundColor(Color.parseColor("#E4E7E9"))
        val lineParams = LinearLayout.LayoutParams(0, dp(context, 1), 1f)
        row.addView(line, lineParams)
        val label = eyebrow(context, text)
        label.setPadding(dp(context, 9), 0, dp(context, 9), 0)
        row.addView(label)
        val right = View(context)
        right.setBackgroundColor(Color.parseColor("#E4E7E9"))
        row.addView(right, LinearLayout.LayoutParams(0, dp(context, 1), 1f))
        val params = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        params.topMargin = dp(context, 13)
        params.bottomMargin = dp(context, 13)
        row.layoutParams = params
        return row
    }

    fun header(context: Context, title: String, subtitle: String? = null): LinearLayout {
        val box = LinearLayout(context)
        box.orientation = LinearLayout.VERTICAL
        box.setPadding(0, dp(context, 6), 0, dp(context, 12))
        if (subtitle != null) {
            val top = eyebrow(context, subtitle)
            top.setPadding(dp(context, 1), 0, 0, dp(context, 3))
            box.addView(top)
        }
        val head = label(context, title, 23f, TEXT, true)
        head.letterSpacing = -0.01f
        box.addView(head)
        return box
    }

    fun backHeader(context: Context, title: String, subtitle: String?, onBack: () -> Unit): LinearLayout {
        val row = LinearLayout(context)
        row.orientation = LinearLayout.HORIZONTAL
        row.gravity = Gravity.CENTER_VERTICAL
        row.setPadding(0, dp(context, 6), 0, dp(context, 12))
        val back = FrameLayout(context)
        back.background = ripple(rounded(SURFACE, 10, context, LINE), 0x14000000)
        back.isClickable = true
        back.setOnClickListener { onBack() }
        back.addView(icon(context, R.drawable.ic_chevron_left, TEXT_2, 17), FrameLayout.LayoutParams(
            dp(context, 17), dp(context, 17), Gravity.CENTER
        ))
        row.addView(back, LinearLayout.LayoutParams(dp(context, 33), dp(context, 33)))
        val box = LinearLayout(context)
        box.orientation = LinearLayout.VERTICAL
        box.setPadding(dp(context, 11), 0, 0, 0)
        box.addView(label(context, title, 19f, TEXT, true))
        if (subtitle != null) box.addView(label(context, subtitle, 12f, TEXT_3))
        row.addView(box, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        return row
    }

    fun note(context: Context, text: String, iconRes: Int = R.drawable.ic_info, tone: Tone = Tone.NEUTRAL): LinearLayout {
        val row = LinearLayout(context)
        row.orientation = LinearLayout.HORIZONTAL
        val colors = toneColors(tone)
        val glyph = icon(context, iconRes, colors.third, 17)
        row.addView(glyph, LinearLayout.LayoutParams(dp(context, 17), dp(context, 17)).apply {
            topMargin = dp(context, 1)
            rightMargin = dp(context, 8)
        })
        row.addView(label(context, text, 12f, TEXT_3), LinearLayout.LayoutParams(
            0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f
        ))
        return row
    }

    fun warnBox(context: Context, text: String): LinearLayout {
        val box = LinearLayout(context)
        box.orientation = LinearLayout.HORIZONTAL
        box.background = rounded(WARN_SOFT, 12, context, WARN_LINE)
        box.setPadding(dp(context, 12), dp(context, 11), dp(context, 12), dp(context, 11))
        box.addView(icon(context, R.drawable.ic_shield_alert, WARN, 18), LinearLayout.LayoutParams(
            dp(context, 18), dp(context, 18)
        ).apply { rightMargin = dp(context, 9); topMargin = dp(context, 1) })
        box.addView(label(context, text, 12f, Color.parseColor("#7A5410")), LinearLayout.LayoutParams(
            0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f
        ))
        val params = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        params.bottomMargin = dp(context, 12)
        box.layoutParams = params
        return box
    }

    fun quote(context: Context, text: String): TextView {
        val view = TextView(context)
        view.text = text
        view.textSize = 13f
        view.setTextColor(Color.parseColor("#3F474E"))
        view.setLineSpacing(0f, 1.35f)
        view.setTextIsSelectable(false)
        val drawable = GradientDrawable()
        drawable.shape = GradientDrawable.RECTANGLE
        drawable.setColor(SURFACE_2)
        drawable.cornerRadius = dp(context, 10).toFloat()
        drawable.setStroke(dp(context, 1), LINE)
        view.background = drawable
        view.setPadding(dp(context, 12), dp(context, 11), dp(context, 12), dp(context, 11))
        view.isLongClickable = false
        return view
    }

    fun field(context: Context, hint: String, numeric: Boolean = false): EditText {
        val view = EditText(context)
        view.hint = hint
        view.textSize = 14f
        view.setHintTextColor(TEXT_3)
        view.setTextColor(TEXT)
        view.background = rounded(SURFACE, 11, context, Color.parseColor("#DFE4E7"))
        view.setPadding(dp(context, 12), dp(context, 11), dp(context, 12), dp(context, 11))
        if (numeric) view.inputType = InputType.TYPE_CLASS_NUMBER
        view.setSingleLine(true)
        return view
    }

    fun codeInput(context: Context, length: Int = 6, onCode: (String) -> Unit): UiCodeInput =
        UiCodeInput(context, length, onCode)

    fun nav(context: Context, items: List<Pair<String, Int>>, selected: Int, onSelect: (Int) -> Unit): UiNav =
        UiNav(context, items, onSelect).apply { highlight(selected) }

    @Suppress("DEPRECATION")
    fun chrome(activity: Activity) {
        activity.window.statusBarColor = BG
        activity.window.navigationBarColor = SURFACE
        activity.window.decorView.systemUiVisibility =
            View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
    }

    fun match(context: Context, topMarginDp: Int = 0, bottomMarginDp: Int = 0): LinearLayout.LayoutParams =
        LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply {
            topMargin = dp(context, topMarginDp)
            bottomMargin = dp(context, bottomMarginDp)
        }

    fun weight(context: Context, weight: Float, leftMarginDp: Int = 0): LinearLayout.LayoutParams =
        LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, weight).apply {
            leftMargin = dp(context, leftMarginDp)
        }

    fun clockText(totalSeconds: Int): String {
        val safe = totalSeconds.coerceAtLeast(0)
        return "%d:%02d".format(safe / 60, safe % 60)
    }
}

@SuppressLint("AppCompatCustomView")
class UiButton(context: Context, private val variant: Variant = Variant.PRIMARY) : TextView(context) {
    var big: Boolean = false
        set(value) {
            field = value
            restyle()
        }

    init {
        gravity = Gravity.CENTER
        isClickable = true
        isFocusable = true
        isSingleLine = true
        typeface = Ui.MEDIUM
        restyle()
    }

    override fun setEnabled(enabled: Boolean) {
        super.setEnabled(enabled)
        restyle()
    }

    fun restyle() {
        val ctx = context
        textSize = if (big) 15f else 14f
        val padV = Ui.dp(ctx, if (big) 15 else 12)
        setPadding(Ui.dp(ctx, 17), padV, Ui.dp(ctx, 17), padV)
        val enabled = isEnabled
        val background = when {
            !enabled -> Ui.DISABLED_BG
            variant == Variant.PRIMARY -> Ui.ACCENT
            else -> Ui.SURFACE
        }
        val foreground = when {
            !enabled -> Ui.DISABLED_FG
            variant == Variant.PRIMARY -> Color.WHITE
            variant == Variant.DANGER -> Ui.DANGER
            variant == Variant.QUIET -> Ui.TEXT_2
            else -> Color.parseColor("#3F474E")
        }
        val stroke = when {
            !enabled -> null
            variant == Variant.DANGER -> Ui.DANGER_LINE
            variant == Variant.GHOST -> Ui.LINE
            else -> null
        }
        val base = Ui.rounded(background, if (big) 13 else 11, ctx, stroke)
        val highlight = if (variant == Variant.PRIMARY && enabled) 0x33FFFFFF else 0x14000000
        this.background = Ui.ripple(base, highlight)
        setTextColor(foreground)
        setTypeface(Ui.MEDIUM, if (enabled) Typeface.BOLD else Typeface.NORMAL)
    }
}

class UiToggle(context: Context) : View(context) {
    var onChange: ((Boolean) -> Unit)? = null
    var checked: Boolean = false
        private set
    private var fraction = 0f
    private var animator: ValueAnimator? = null
    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val thumbPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val trackRect = RectF()
    private val evaluator = ArgbEvaluator()
    private val inset = Ui.dp(context, 2.5f).toFloat()

    init {
        isClickable = true
        isFocusable = true
        setOnClickListener { setChecked(!checked, animate = true, notify = true) }
    }

    fun setChecked(value: Boolean, animate: Boolean = false, notify: Boolean = false) {
        checked = value
        animator?.cancel()
        animator = null
        if (animate) {
            animator = ValueAnimator.ofFloat(fraction, if (value) 1f else 0f).apply {
                duration = 160
                addUpdateListener {
                    fraction = it.animatedValue as Float
                    invalidate()
                }
                start()
            }
        } else {
            fraction = if (value) 1f else 0f
            invalidate()
        }
        if (notify) onChange?.invoke(value)
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        setMeasuredDimension(Ui.dp(context, 46), Ui.dp(context, 26))
    }

    override fun onDetachedFromWindow() {
        animator?.cancel()
        super.onDetachedFromWindow()
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return
        trackPaint.color = evaluator.evaluate(fraction, Ui.TRACK_OFF, Ui.ACCENT) as Int
        trackRect.set(0f, 0f, w, h)
        canvas.drawRoundRect(trackRect, h / 2f, h / 2f, trackPaint)
        val thumbRadius = h / 2f - inset
        val travel = (w - inset * 2 - thumbRadius * 2).coerceAtLeast(0f)
        thumbPaint.color = Color.WHITE
        canvas.drawCircle(inset + thumbRadius + fraction * travel, h / 2f, thumbRadius, thumbPaint)
    }
}

class UiRing(context: Context, private val sizeDp: Int, private val strokeDp: Float) : View(context) {
    var progress: Float = 0f
        set(value) {
            field = value.coerceIn(0f, 1f)
            invalidate()
        }
    var arcColor: Int = Ui.ACCENT
        set(value) {
            field = value
            invalidate()
        }
    var trackColor: Int = Ui.RING_TRACK
        set(value) {
            field = value
            invalidate()
        }

    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val arcPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val rect = RectF()

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val size = Ui.dp(context, sizeDp)
        setMeasuredDimension(size, size)
    }

    override fun onDraw(canvas: Canvas) {
        val stroke = Ui.dp(context, strokeDp).toFloat()
        trackPaint.strokeWidth = stroke
        arcPaint.strokeWidth = stroke
        trackPaint.color = trackColor
        arcPaint.color = arcColor
        val inset = stroke / 2f + 1f
        rect.set(inset, inset, width - inset, height - inset)
        canvas.drawArc(rect, 0f, 360f, false, trackPaint)
        if (progress > 0f) canvas.drawArc(rect, -90f, 360f * progress, false, arcPaint)
    }
}

class RingStack(context: Context, private val sizeDp: Int, private val strokeDp: Float, color: Int = Ui.ACCENT) :
    FrameLayout(context) {
    private val ring = UiRing(context, sizeDp, strokeDp).apply { arcColor = color }
    private val center = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER
    }

    val value: TextView = Ui.label(context, "0:00", 33f, Ui.TEXT, true).apply {
        gravity = Gravity.CENTER
        letterSpacing = -0.03f
    }
    val caption: TextView = Ui.label(context, "", 11.5f, Ui.TEXT_3).apply { gravity = Gravity.CENTER }

    init {
        addView(ring, LayoutParams(Ui.dp(context, sizeDp), Ui.dp(context, sizeDp)))
        center.addView(value)
        center.addView(caption)
        addView(center, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        layoutParams = LinearLayout.LayoutParams(Ui.dp(context, sizeDp), Ui.dp(context, sizeDp))
    }

    fun setProgress(fraction: Float) {
        ring.progress = fraction
    }

    fun setArcColor(color: Int) {
        ring.arcColor = color
    }

    fun setValueSize(sp: Float) {
        value.textSize = sp
    }
}

class UiBar(context: Context, heightDp: Int = 5) : FrameLayout(context) {
    private val fill: View = View(context).apply { background = Ui.rounded(Ui.ACCENT, 2, context) }
    private var fraction = 0f

    init {
        background = Ui.rounded(Ui.RING_TRACK, 2, context)
        addView(fill, LayoutParams(0, LayoutParams.MATCH_PARENT))
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(context, heightDp)
        )
    }

    fun setProgress(part: Float, color: Int = Ui.ACCENT) {
        fraction = part.coerceIn(0f, 1f)
        fill.background = Ui.rounded(color, 2, context)
        applyWidth()
    }

    private fun applyWidth() {
        val update = Runnable {
            val total = width
            if (total <= 0) return@Runnable
            fill.layoutParams = (fill.layoutParams as LayoutParams).apply {
                width = (total * fraction).toInt().coerceAtLeast(0)
            }
        }
        if (width > 0) update.run() else post(update)
    }
}

class UiStat(context: Context, label: String) : LinearLayout(context) {
    val value: TextView = Ui.label(context, "—", 18f, Ui.TEXT, true).apply {
        letterSpacing = -0.02f
    }
    private val fill: View = View(context).apply { background = Ui.rounded(Ui.ACCENT, 2, context) }
    private val track: FrameLayout = FrameLayout(context).apply {
        background = Ui.rounded(Ui.RING_TRACK, 2, context)
    }
    private var fraction = 0f

    init {
        orientation = LinearLayout.VERTICAL
        background = Ui.rounded(Ui.SURFACE, 14, context, Ui.LINE)
        elevation = Ui.dp(context, 1).toFloat()
        setPadding(Ui.dp(context, 10), Ui.dp(context, 10), Ui.dp(context, 10), Ui.dp(context, 10))
        addView(Ui.eyebrow(context, label, Ui.TEXT_3).apply { textSize = 10.5f })
        addView(value, Ui.match(context, topMarginDp = 5))
        track.addView(fill, FrameLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT))
        addView(track, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(context, 4)
        ).apply { topMargin = Ui.dp(context, 7) })
    }

    fun setBar(part: Float, color: Int = Ui.ACCENT) {
        fraction = part.coerceIn(0f, 1f)
        fill.background = Ui.rounded(color, 2, context)
        applyWidth()
    }

    fun hideBar() {
        track.visibility = View.GONE
    }

    private fun applyWidth() {
        val update = Runnable {
            val width = track.width
            if (width <= 0) return@Runnable
            fill.layoutParams = (fill.layoutParams as FrameLayout.LayoutParams).apply {
                this.width = (width * fraction).toInt().coerceAtLeast(0)
            }
        }
        if (track.width > 0) update.run() else track.post(update)
    }
}

class UiSteps(context: Context, private val total: Int) : LinearLayout(context) {
    private val bars = mutableListOf<View>()

    init {
        orientation = LinearLayout.HORIZONTAL
        repeat(total) { index ->
            val bar = View(context)
            bar.background = Ui.rounded(Ui.STEP_OFF, 2, context)
            bars.add(bar)
            addView(bar, LinearLayout.LayoutParams(0, Ui.dp(context, 4), 1f).apply {
                if (index < total - 1) rightMargin = Ui.dp(context, 5)
            })
        }
        layoutParams = Ui.match(context, bottomMarginDp = 13)
    }

    fun setDone(count: Int) {
        bars.forEachIndexed { index, bar ->
            bar.background = Ui.rounded(if (index < count) Ui.ACCENT else Ui.STEP_OFF, 2, context)
        }
    }
}

class UiPill(context: Context) : LinearLayout(context) {
    private val dot: View = View(context)
    private val label: TextView = Ui.label(context, "", 11.5f, Ui.ACCENT, true)

    init {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(Ui.dp(context, 9), Ui.dp(context, 4), Ui.dp(context, 10), Ui.dp(context, 4))
        addView(dot, LinearLayout.LayoutParams(Ui.dp(context, 6), Ui.dp(context, 6)).apply {
            rightMargin = Ui.dp(context, 5)
        })
        addView(label)
    }

    fun set(text: String, tone: Tone = Tone.ACCENT, showDot: Boolean = true) {
        val colors = Ui.toneColors(tone)
        label.text = text
        label.setTextColor(colors.third)
        background = Ui.rounded(colors.first, 999, context, colors.second)
        dot.background = Ui.oval(colors.third, context)
        dot.visibility = if (showDot) View.VISIBLE else View.GONE
    }
}

class UiTile(context: Context, sizeDp: Int, iconRes: Int, tone: Tone) : FrameLayout(context) {
    private val image: ImageView = ImageView(context)

    init {
        image.imageTintList = ColorStateList.valueOf(Ui.toneColors(tone).third)
        addView(image, LayoutParams(
            Ui.dp(context, 18), Ui.dp(context, 18), Gravity.CENTER
        ))
        setTone(tone)
        setIcon(iconRes)
        layoutParams = LinearLayout.LayoutParams(Ui.dp(context, sizeDp), Ui.dp(context, sizeDp))
    }

    fun setIcon(res: Int) {
        val drawable: Drawable? = try {
            context.getDrawable(res)
        } catch (error: Exception) {
            null
        }
        image.setImageDrawable(drawable)
    }

    fun setTone(tone: Tone) {
        val colors = Ui.toneColors(tone)
        background = Ui.rounded(colors.first, 10, context, colors.second)
        image.imageTintList = ColorStateList.valueOf(colors.third)
    }

    fun setDrawable(drawable: Drawable?) {
        if (drawable == null) return
        image.setImageDrawable(drawable)
        image.imageTintList = null
        background = null
        (image.layoutParams as LayoutParams).apply {
            width = LayoutParams.MATCH_PARENT
            height = LayoutParams.MATCH_PARENT
        }
    }
}

class UiRow(context: Context, leading: View? = null) : LinearLayout(context) {
    val titleView: TextView = Ui.label(context, "", 14f, Ui.TEXT, true)
    val subView: TextView = Ui.label(context, "", 12f, Ui.TEXT_3)
    val statusView: TextView = Ui.label(context, "", 12f, Ui.TEXT_3, true)
    private val box = LinearLayout(context)

    init {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(0, Ui.dp(context, 10), 0, Ui.dp(context, 10))
        if (leading != null) {
            addView(leading, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { rightMargin = Ui.dp(context, 11) })
        }
        box.orientation = LinearLayout.VERTICAL
        box.addView(titleView)
        box.addView(subView)
        addView(box, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        statusView.visibility = View.GONE
        addView(statusView)
    }

    fun title(text: String) {
        titleView.text = text
    }

    fun titleColor(color: Int) {
        titleView.setTextColor(color)
    }

    fun subtitle(text: String) {
        subView.text = text
        subView.visibility = if (text.isEmpty()) View.GONE else View.VISIBLE
    }

    fun status(text: String, color: Int = Ui.TEXT_3) {
        statusView.text = text
        statusView.setTextColor(color)
        statusView.visibility = if (text.isEmpty()) View.GONE else View.VISIBLE
    }

    fun trailing(view: View, leftMarginDp: Int = 6) {
        val params = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
        )
        params.leftMargin = Ui.dp(context, leftMarginDp)
        addView(view, params)
    }

    fun makeClickable(onClick: () -> Unit) {
        isClickable = true
        isFocusable = true
        background = Ui.ripple(Ui.rounded(Color.TRANSPARENT, 8, context), 0x14000000)
        setOnClickListener { onClick() }
    }
}

class UiGroup(context: Context) : LinearLayout(context) {
    private var children = 0

    init {
        orientation = LinearLayout.VERTICAL
        background = Ui.rounded(Ui.SURFACE, 16, context, Ui.LINE)
        elevation = Ui.dp(context, 1).toFloat()
        setPadding(Ui.dp(context, 14), Ui.dp(context, 3), Ui.dp(context, 14), Ui.dp(context, 3))
        layoutParams = Ui.match(context, bottomMarginDp = 11)
    }

    override fun addView(child: View?, index: Int, params: ViewGroup.LayoutParams?) {
        if (child != null && children > 0) {
            val line = View(context)
            line.setBackgroundColor(Ui.LINE_SOFT)
            super.addView(line, -1, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Math.max(1, Ui.dp(context, 1))
            ))
        }
        super.addView(child, index, params)
        if (child != null) children += 1
    }

    override fun removeAllViews() {
        super.removeAllViews()
        children = 0
    }
}

class UiSegmented(context: Context, labels: List<String>, private val onSelect: (Int) -> Unit) :
    LinearLayout(context) {
    var selected: Int = 0
        private set
    private val items = mutableListOf<TextView>()

    init {
        orientation = LinearLayout.HORIZONTAL
        background = Ui.rounded(Ui.SURFACE_2, 11, context, Ui.LINE_SOFT)
        setPadding(Ui.dp(context, 3), Ui.dp(context, 3), Ui.dp(context, 3), Ui.dp(context, 3))
        labels.forEachIndexed { index, text ->
            val item = TextView(context)
            item.text = text
            item.textSize = 13.5f
            item.gravity = Gravity.CENTER
            item.typeface = Ui.MEDIUM
            item.setPadding(0, Ui.dp(context, 8), 0, Ui.dp(context, 8))
            item.isClickable = true
            item.setOnClickListener { select(index, true) }
            items.add(item)
            addView(item, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        }
        layoutParams = Ui.match(context)
    }

    fun select(index: Int, notify: Boolean = true) {
        selected = index
        items.forEachIndexed { position, item ->
            val active = position == index
            item.background = if (active) {
                Ui.rounded(Ui.SURFACE, 8, context)
            } else {
                null
            }
            if (active) item.elevation = Ui.dp(context, 1).toFloat() else item.elevation = 0f
            item.setTextColor(if (active) Ui.TEXT else Ui.TEXT_2)
            item.setTypeface(Ui.MEDIUM, if (active) Typeface.BOLD else Typeface.NORMAL)
        }
        if (notify) onSelect(index)
    }
}

class UiNav(context: Context, items: List<Pair<String, Int>>, private val onSelect: (Int) -> Unit) :
    LinearLayout(context) {
    private val icons = mutableListOf<ImageView>()
    private val labels = mutableListOf<TextView>()
    var selected: Int = 0
        private set

    init {
        orientation = LinearLayout.HORIZONTAL
        setBackgroundColor(Ui.SURFACE)
        gravity = Gravity.CENTER_VERTICAL
        setPadding(0, Ui.dp(context, 5), 0, Ui.dp(context, 7))
        items.forEachIndexed { index, item ->
            val cell = LinearLayout(context)
            cell.orientation = LinearLayout.VERTICAL
            cell.gravity = Gravity.CENTER
            cell.isClickable = true
            cell.isFocusable = true
            cell.background = Ui.ripple(Ui.rounded(Color.TRANSPARENT, 10, context), 0x14000000)
            cell.setPadding(0, Ui.dp(context, 4), 0, 0)
            val icon = ImageView(context)
            icon.setImageDrawable(context.getDrawable(item.second))
            cell.addView(icon, LinearLayout.LayoutParams(Ui.dp(context, 21), Ui.dp(context, 21)))
            val label = Ui.label(context, item.first, 11f, Ui.TEXT_3)
            label.gravity = Gravity.CENTER
            label.setPadding(0, Ui.dp(context, 3), 0, 0)
            cell.addView(label)
            cell.setOnClickListener { select(index) }
            icons.add(icon)
            labels.add(label)
            addView(cell, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        }
    }

    fun select(index: Int) {
        apply(index)
        onSelect(index)
    }

    fun highlight(index: Int) {
        apply(index)
    }

    private fun apply(index: Int) {
        selected = index
        icons.forEachIndexed { position, icon ->
            val active = position == index
            icon.imageTintList = ColorStateList.valueOf(if (active) Ui.ACCENT else Ui.TEXT_3)
        }
        labels.forEachIndexed { position, label ->
            val active = position == index
            label.setTextColor(if (active) Ui.ACCENT else Ui.TEXT_3)
            label.setTypeface(Ui.MEDIUM, if (active) Typeface.BOLD else Typeface.NORMAL)
        }
    }
}

@SuppressLint("AppCompatCustomView")
class UiCodeInput(
    context: Context,
    private val length: Int,
    private val onCode: (String) -> Unit,
) : LinearLayout(context) {
    private val boxes = mutableListOf<EditText>()
    private var syncing = false

    init {
        orientation = LinearLayout.HORIZONTAL
        repeat(length) { index ->
            val box = EditText(context)
            box.inputType = InputType.TYPE_CLASS_NUMBER
            box.filters = arrayOf(InputFilter.LengthFilter(1))
            box.gravity = Gravity.CENTER
            box.textSize = 19f
            box.setTypeface(Typeface.DEFAULT, Typeface.BOLD)
            box.setTextColor(Ui.TEXT)
            box.isCursorVisible = true
            box.background = Ui.rounded(Ui.SURFACE, 10, context, Color.parseColor("#DFE4E7"))
            box.setPadding(0, Ui.dp(context, 10), 0, Ui.dp(context, 10))
            box.addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                override fun afterTextChanged(s: Editable?) {
                    if (syncing) return
                    val text = s?.toString() ?: ""
                    if (text.isNotEmpty() && index < length - 1) {
                        boxes.getOrNull(index + 1)?.requestFocus()
                    } else if (text.isEmpty() && index > 0 && boxes.getOrNull(index - 1)?.text?.isNotEmpty() == true) {
                        boxes[index - 1].requestFocus()
                    }
                    publish()
                }
            })
            box.setOnKeyListener { _, keyCode, event ->
                if (keyCode == KeyEvent.KEYCODE_DEL && event.action == KeyEvent.ACTION_UP &&
                    box.text.isNullOrEmpty() && index > 0
                ) {
                    val previous = boxes[index - 1]
                    syncing = true
                    previous.setText("")
                    syncing = false
                    previous.requestFocus()
                    publish()
                    true
                } else {
                    false
                }
            }
            box.setOnFocusChangeListener { _, focused ->
                box.background = if (focused) {
                    Ui.rounded(Ui.SURFACE, 10, context, Ui.ACCENT)
                } else {
                    Ui.rounded(Ui.SURFACE, 10, context, Color.parseColor("#DFE4E7"))
                }
            }
            boxes.add(box)
            addView(box, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                if (index < length - 1) rightMargin = Ui.dp(context, 5)
            })
        }
    }

    fun code(): String = boxes.joinToString("") { it.text?.toString() ?: "" }

    fun isComplete(): Boolean = code().length == length

    fun clear() {
        syncing = true
        boxes.forEach { it.setText("") }
        syncing = false
        boxes.firstOrNull()?.requestFocus()
    }

    fun focusFirst() {
        boxes.firstOrNull()?.requestFocus()
    }

    private fun publish() {
        onCode(code())
    }
}
