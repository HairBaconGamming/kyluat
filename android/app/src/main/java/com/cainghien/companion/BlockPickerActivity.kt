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
import java.util.concurrent.Executors

class BlockPickerActivity : Activity() {
    private data class AppEntry(
        val label: String,
        val packageName: String,
        val added: Boolean,
        val blocked: Boolean,
    )

    private lateinit var prefs: Prefs
    private lateinit var adapter: AppAdapter
    private lateinit var loadingBox: LinearLayout
    private lateinit var emptyLabel: TextView
    private lateinit var list: ListView
    private lateinit var doneButton: UiButton
    private val executor = Executors.newSingleThreadExecutor()
    private val iconCache = LruCache<String, Drawable>(80)
    private val apps = mutableListOf<AppEntry>()
    private val shown = mutableListOf<AppEntry>()
    private var query = ""
    private var destroyed = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Prefs(this)
        Ui.chrome(this)

        val page = Ui.screen(this)
        page.addView(Ui.backHeader(this, "Thêm app khác", "Chọn app bạn muốn Kỷ luật chặn") { finish() })

        val search = Ui.field(this, "Tìm ứng dụng…")
        page.addView(search, Ui.match(this, bottomMarginDp = 11))

        loadingBox = buildLoading()
        emptyLabel = Ui.label(this, "Không có kết quả", 13f, Ui.TEXT_3).apply {
            gravity = Gravity.CENTER
            visibility = View.GONE
            setPadding(0, Ui.dp(this@BlockPickerActivity, 26), 0, 0)
        }
        list = ListView(this)
        list.divider = null
        list.dividerHeight = 0
        list.setSelector(android.R.color.transparent)
        list.setBackgroundColor(Color.TRANSPARENT)
        list.clipToPadding = false
        list.visibility = View.GONE
        adapter = AppAdapter()
        list.adapter = adapter
        list.setOnItemClickListener { _, _, position, _ -> toggle(position) }

        val body = FrameLayout(this)
        val fill = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
        )
        body.addView(list, fill)
        body.addView(emptyLabel, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP
        ))
        body.addView(loadingBox, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP
        ))
        page.addView(body, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))

        doneButton = Ui.button(this, "Xong", Variant.PRIMARY, big = true)
        doneButton.setOnClickListener { finish() }
        page.addView(doneButton, Ui.match(this))

        page.addView(Ui.note(
            this,
            "Cuộc gọi, Tin nhắn, Cài đặt, màn hình chính và Kỷ luật không bao giờ bị chặn, kể cả khi bạn chọn.",
            R.drawable.ic_shield_check,
        ).apply { setPadding(Ui.dp(this@BlockPickerActivity, 2), Ui.dp(this@BlockPickerActivity, 9), 0, 0) })

        setContentView(page)

        search.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                query = s?.toString()?.trim()?.lowercase() ?: ""
                applyFilter()
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
            rightMargin = Ui.dp(this@BlockPickerActivity, 12)
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
        val skip = Apps.neverBlocked(this) + Prefs.SOCIAL_LABELS.keys
        val current = prefs.extraPackages
        val blocked = prefs.socialPackages
        executor.execute {
            val collected = mutableListOf<AppEntry>()
            try {
                val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
                val resolved = manager.queryIntentActivities(intent, 0)
                val seen = mutableSetOf<String>()
                for (info in resolved) {
                    val appPackage = info.activityInfo.packageName
                    if (appPackage in skip || !seen.add(appPackage)) continue
                    val label = try {
                        info.loadLabel(manager).toString()
                    } catch (error: Exception) {
                        appPackage
                    }
                    collected.add(
                        AppEntry(label, appPackage, current.contains(appPackage), blocked.contains(appPackage))
                    )
                }
            } catch (error: Exception) {
            }
            sort(collected)
            runOnUiThread {
                if (destroyed) return@runOnUiThread
                apps.clear()
                apps.addAll(collected)
                loadingBox.visibility = View.GONE
                list.visibility = View.VISIBLE
                applyFilter()
                updateDone()
            }
        }
    }

    private fun sort(target: MutableList<AppEntry>) {
        target.sortWith(compareByDescending<AppEntry> { it.added }.thenBy { it.label.lowercase() })
    }

    private fun applyFilter() {
        shown.clear()
        shown.addAll(
            apps.filter {
                query.isEmpty() ||
                    it.label.lowercase().contains(query) ||
                    it.packageName.lowercase().contains(query)
            }
        )
        emptyLabel.visibility = if (shown.isEmpty()) View.VISIBLE else View.GONE
        emptyLabel.text = if (apps.isEmpty()) "Không đọc được danh sách ứng dụng" else "Không có kết quả"
        adapter.notifyDataSetChanged()
    }

    private fun toggle(position: Int) {
        val entry = shown.getOrNull(position) ?: return
        val add = !entry.added
        prefs.setExtraPackage(entry.packageName, add)
        val index = apps.indexOfFirst { it.packageName == entry.packageName }
        if (index >= 0) apps[index] = entry.copy(added = add, blocked = add)
        sort(apps)
        applyFilter()
        updateDone()
    }

    private fun updateDone() {
        val count = prefs.extraPackages.size
        doneButton.text = if (count == 0) "Xong" else "Xong · $count app bạn thêm"
    }

    private fun iconFor(packageName: String): Drawable? {
        iconCache.get(packageName)?.let { return it }
        val drawable = Apps.icon(this, packageName) ?: return null
        iconCache.put(packageName, drawable)
        return drawable
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
            addView(container)
        }

        fun bind(entry: AppEntry) {
            title.text = entry.label
            subtitle.text = when {
                !entry.added -> "Chạm để thêm vào danh sách chặn"
                entry.blocked -> "Đang bị chặn"
                else -> "Đã thêm · đang tắt chặn ở tab Bảo vệ"
            }
            subtitle.setTextColor(if (entry.added && entry.blocked) Ui.ACCENT else Ui.TEXT_3)
            container.background = Ui.rounded(
                if (entry.added) Ui.ACCENT_SOFT else Ui.SURFACE, 12, context,
                if (entry.added) Ui.ACCENT_LINE else Ui.LINE,
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
            check.visibility = if (entry.added) View.VISIBLE else View.GONE
        }
    }

    private inner class AppAdapter : BaseAdapter() {
        override fun getCount(): Int = shown.size

        override fun getItem(position: Int): Any = shown[position]

        override fun getItemId(position: Int): Long = position.toLong()

        override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
            val view = convertView as? ItemRow ?: ItemRow(this@BlockPickerActivity)
            view.bind(shown[position])
            return view
        }
    }
}
