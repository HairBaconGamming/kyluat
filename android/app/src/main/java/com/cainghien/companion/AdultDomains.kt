package com.cainghien.companion

import android.content.Context

/** Danh sách tên miền người lớn, sinh từ nguồn dùng chung bởi tools/sync_adult_list.py. */
object AdultDomains {
    fun load(context: Context): Set<String> =
        context.resources.openRawResource(R.raw.adult_domains).bufferedReader().use { reader ->
            reader.readLines()
                .map { it.trim().lowercase() }
                .filter { it.isNotEmpty() && !it.startsWith("#") }
                .toSet()
        }
}
