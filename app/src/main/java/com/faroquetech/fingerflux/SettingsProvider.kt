package com.faroquetech.fingerflux

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri

class SettingsProvider : ContentProvider() {
    override fun onCreate() = true

    override fun query(uri: Uri, projection: Array<out String>?, selection: String?,
        selectionArgs: Array<out String>?, sortOrder: String?): Cursor {
        val p = context!!.getSharedPreferences("fingers_print_settings", 0)
        val c = MatrixCursor(arrayOf("key", "value"))
        c.addRow(arrayOf("icon", "catalog"))
        c.addRow(arrayOf("catalog_index", p.getInt("catalog_index", 1).toString()))
        c.addRow(arrayOf("icon_scale", p.getInt("icon_scale", 180).toString()))
        c.addRow(arrayOf("hide_bg", p.getBoolean("hide_bg", false).toString()))
        c.addRow(arrayOf("anim_index", p.getInt("anim_index", 1).toString()))
        c.addRow(arrayOf("ripple", p.getBoolean("ripple", true).toString()))
        c.addRow(arrayOf("haptic", p.getBoolean("haptic", true).toString()))
        c.addRow(arrayOf("duration", p.getInt("duration", 900).toString()))
        c.addRow(arrayOf("size", p.getInt("size", 90).toString()))
        return c
    }

    override fun getType(uri: Uri) = "vnd.android.cursor.dir/vnd.fingerflux.settings"
    override fun insert(uri: Uri, values: ContentValues?) = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?) = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?,
        selectionArgs: Array<out String>?) = 0
}
