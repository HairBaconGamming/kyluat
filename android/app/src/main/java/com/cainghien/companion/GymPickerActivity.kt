package com.cainghien.companion

import android.app.Activity
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.LruCache
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import java.util.concurrent.Executors

class GymPickerActivity : Activity() {
    private data class AppEntry(
        val label: String,
        val packageName: String,
        val suggested: Boolean,
    )

    private sealed class Row {
        data class Header(val title: String) : Row()
        data class Item(val entry: AppEntry) : Row()
    }

    private lateinit var prefs: Prefs
    private lateinit var adapter: AppAdapter
    private lateinit var body: FrameLayout
    private lateinit var loadingBox: LinearLayout
    private lateinit var list: ListView
    private val executor = Executors.newSingleThreadExecutor()
    private val iconCache = LruCache<String, Drawable.ConstantState>(80)
    private val allApps = mutableListOf<AppEntry>()
    private val rows = mutableListOf<Row>()
    private var query = ""
    private var destroyed = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Prefs(this)
        Ui.chrome(this)

        val page = Ui.screen(this)
        page.addView(Ui.backHeader(this, "Chọn app tập", "Kỷ luật mở app này và đếm 10 phút liên tục") { finish() })

        val search = Ui.field(this, "Tìm ứng dụng…")
        page.addView(search, Ui.match(this, bottomMarginDp = 11))

        body = FrameLayout(this)
        loadingBox = buildLoading()
        list = ListView(this)
        list.divider = null
        list.dividerHeight = 0
        list.setSelector(android.R.color.transparent)
        list.setBackgroundColor(Color.TRANSPARENT)
        list.clipToPadding = false
        list.visibility = View.GONE
        adapter = AppAdapter()
        list.adapter = adapter
        list.setOnItemClickListener { _, _, position, _ ->
            val row = rows.getOrNull(position)
            if (row is Row.Item) {
                prefs.gymPackage = row.entry.packageName
                Toast.makeText(this, "Đã chọn ${row.entry.label}", Toast.LENGTH_SHORT).show()
                finish()
            }
        }
        val bodyParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)
        body.addView(list, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
        ))
        body.addView(loadingBox, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP
        ))
        page.addView(body, bodyParams)

        page.addView(Ui.note(
            this,
            "Không thấy app cần tìm? Danh sách chỉ gồm app đã cài trên máy.",
            R.drawable.ic_search,
        ).apply { setPadding(Ui.dp(this@GymPickerActivity, 2), Ui.dp(this@GymPickerActivity, 9), 0, 0) })

        setContentView(page)

        search.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                query = s?.toString()?.trim()?.lowercase() ?: ""
                if (allApps.isNotEmpty()) rebuild(query)
            }
        })
        loadApps()
    }

    private fun buildLoading(): LinearLayout {
        val box = LinearLayout(this)
        box.orientation = LinearLayout.HORIZONTAL
        box.gravity = Gravity.CENTER_VERTICAL
        box.background = Ui.rounded(Ui.SURFACE, 14, this, Ui.LINE)
        box.elevation = Ui.dp(this, 1).toFloat()
        box.setPadding(Ui.dp(this, 14), Ui.dp(this, 14), Ui.dp(this, 14), Ui.dp(this, 14))
        val spinner = ProgressBar(this)
        spinner.indeterminateTintList = ColorStateList.valueOf(Ui.ACCENT)
        box.addView(spinner, LinearLayout.LayoutParams(Ui.dp(this, 20), Ui.dp(this, 20)).apply {
            rightMargin = Ui.dp(this@GymPickerActivity, 12)
        })
        val labels = LinearLayout(this)
        labels.orientation = LinearLayout.VERTICAL
        labels.addView(Ui.label(this, "Đang xử lý…", 14f, Ui.TEXT, true))
        labels.addView(Ui.label(this, "Đang đọc danh sách ứng dụng đã cài trên máy", 12f, Ui.TEXT_3))
        box.addView(labels, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        return box
    }

    private fun loadApps() {
        val manager = packageManager
        val selfPackage = packageName
        executor.execute {
            val collected = mutableListOf<AppEntry>()
            try {
                val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
                val resolved = manager.queryIntentActivities(intent, 0)
                val seen = mutableSetOf<String>()
                for (info in resolved) {
                    val appPackage = info.activityInfo.packageName
                    if (appPackage == selfPackage || !seen.add(appPackage)) continue
                    val label = try {
                        info.loadLabel(manager).toString()
                    } catch (error: Exception) {
                        appPackage
                    }
                    collected.add(AppEntry(label, appPackage, isFitness(label, appPackage)))
                }
                collected.sortBy { it.label.lowercase() }
            } catch (error: Exception) {
            }
            runOnUiThread {
                if (destroyed) return@runOnUiThread
                allApps.clear()
                allApps.addAll(collected)
                loadingBox.visibility = View.GONE
                list.visibility = View.VISIBLE
                rebuild(query)
            }
        }
    }

    private fun isFitness(label: String, packageName: String): Boolean {
        val known = setOf(
            "com.google.android.apps.fitness",
            "com.hevyapp.hevy",
            "io.strongapp.strong",
            "com.sec.android.app.shealth",
            "com.strava",
            "com.jefit.jefit",
            "com.myfitnesspal.android",
            "com.nike.ntc",
        )
        if (packageName in known) return true
        val text = "$label $packageName".lowercase()
        return listOf(
            "fitness", "gym", "workout", "training", "health", "strong", "hevy",
            "strava", "yoga", "exercise", "thể hình", "tập luyện", "the hinh",
        ).any { text.contains(it) }
    }

    private fun rebuild(search: String) {
        rows.clear()
        if (allApps.isEmpty()) {
            rows.add(Row.Header("Không đọc được danh sách ứng dụng"))
            adapter.notifyDataSetChanged()
            return
        }
        val filtered = allApps.filter {
            search.isEmpty() || it.label.lowercase().contains(search) || it.packageName.lowercase().contains(search)
        }
        val suggested = filtered.filter { it.suggested }
        val others = filtered.filter { !it.suggested }
        if (suggested.isNotEmpty()) {
            rows.add(Row.Header("Gợi ý cho việc tập"))
            suggested.forEach { rows.add(Row.Item(it)) }
        }
        if (others.isNotEmpty()) {
            rows.add(Row.Header(if (search.isEmpty()) "Tất cả ứng dụng" else "Kết quả tìm kiếm"))
            others.forEach { rows.add(Row.Item(it)) }
        }
        if (rows.isEmpty()) {
            rows.add(Row.Header(if (search.isEmpty()) "Chưa tìm thấy ứng dụng nào" else "Không có kết quả"))
        }
        adapter.notifyDataSetChanged()
    }

    private fun iconFor(packageName: String): Drawable? {
        iconCache.get(packageName)?.let { return it.newDrawable(resources).mutate() }
        val drawable = Apps.icon(this, packageName) ?: return null
        drawable.constantState?.let { iconCache.put(packageName, it) }
        return drawable.mutate()
    }

    override fun onDestroy() {
        destroyed = true
        executor.shutdownNow()
        super.onDestroy()
    }

    private inner class ItemRow(context: Activity) : LinearLayout(context) {
        private val container: LinearLayout = LinearLayout(context)
        private val icon: ImageView = ImageView(context)
        private val title: TextView = Ui.label(context, "", 14f, Ui.TEXT, true)
        private val subtitle: TextView = Ui.label(context, "", 12f, Ui.TEXT_3)
        private val check: UiTile = Ui.tile(context, R.drawable.ic_check, Tone.ACCENT, 24)
        private val chevron: ImageView = Ui.chevron(context)
        private val pad = Ui.dp(context, 9)

        init {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 0, 0, Ui.dp(context, 7))
            container.orientation = LinearLayout.HORIZONTAL
            container.gravity = Gravity.CENTER_VERTICAL
            container.setPadding(
                Ui.dp(context, 11), Ui.dp(context, 9), Ui.dp(context, 11), Ui.dp(context, 9)
            )
            icon.scaleType = ImageView.ScaleType.FIT_CENTER
            container.addView(icon, LinearLayout.LayoutParams(Ui.dp(context, 38), Ui.dp(context, 38)))
            val labels = LinearLayout(context)
            labels.orientation = LinearLayout.VERTICAL
            labels.setPadding(Ui.dp(context, 11), 0, 0, 0)
            title.isSingleLine = true
            labels.addView(title)
            subtitle.isSingleLine = true
            labels.addView(subtitle)
            container.addView(labels, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            container.addView(check, LinearLayout.LayoutParams(Ui.dp(context, 24), Ui.dp(context, 24)))
            container.addView(chevron)
            addView(container)
        }

        fun bind(entry: AppEntry, selected: Boolean) {
            title.text = entry.label
            subtitle.text = when {
                selected -> "Đang dùng cho buổi tập"
                entry.suggested -> "Gợi ý cho việc tập"
                else -> "Ứng dụng đã cài"
            }
            subtitle.setTextColor(if (selected) Ui.ACCENT else Ui.TEXT_3)
            container.background = Ui.rounded(
                if (selected) Ui.ACCENT_SOFT else Ui.SURFACE, 12, context,
                if (selected) Ui.ACCENT_LINE else Ui.LINE,
            )
            val drawable = iconFor(entry.packageName)
            if (drawable == null) {
                icon.setPadding(pad, pad, pad, pad)
                icon.imageTintList = ColorStateList.valueOf(Ui.TEXT_3)
                icon.setImageResource(R.drawable.ic_grid)
            } else {
                icon.setPadding(0, 0, 0, 0)
                icon.imageTintList = null
                icon.setImageDrawable(drawable)
            }
            check.visibility = if (selected) View.VISIBLE else View.GONE
            chevron.visibility = if (selected) View.GONE else View.VISIBLE
        }
    }

    private inner class AppAdapter : BaseAdapter() {
        override fun getCount(): Int = rows.size

        override fun getItem(position: Int): Any = rows[position]

        override fun getItemId(position: Int): Long = position.toLong()

        override fun getViewTypeCount(): Int = 2

        override fun getItemViewType(position: Int): Int = if (rows[position] is Row.Header) 0 else 1

        override fun isEnabled(position: Int): Boolean = rows[position] is Row.Item

        override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
            val row = rows[position]
            if (row is Row.Header) {
                val header = Ui.eyebrow(this@GymPickerActivity, row.title, Ui.TEXT_3)
                header.setPadding(
                    Ui.dp(this@GymPickerActivity, 3), Ui.dp(this@GymPickerActivity, 13),
                    0, Ui.dp(this@GymPickerActivity, 6),
                )
                return header
            }
            val entry = (row as Row.Item).entry
            val view = convertView as? ItemRow ?: ItemRow(this@GymPickerActivity)
            view.bind(entry, entry.packageName == prefs.gymPackage)
            return view
        }
    }
}
