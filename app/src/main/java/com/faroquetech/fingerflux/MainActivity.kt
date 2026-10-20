package com.faroquetech.fingerflux

import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.udfpsfx.ANIM_NAMES
import com.faroquetech.fingerflux.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {
    private val updateUrl = "https://github.com/FaroqueTech0/FP-Studio/releases/latest"
    private val telegramUrl = "https://t.me/Faroquetech00"
    private val donateUrl = "https://www.paypal.com/paypalme/OmarFaroque95"

    private fun openLink(url: String) {
        try {
            startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url)))
        } catch (t: Throwable) {
            Toast.makeText(this, "Couldn't open the link", Toast.LENGTH_SHORT).show()
        }
    }

    private var _binding: ActivityMainBinding? = null
    private val binding get() = checkNotNull(_binding)

    // Exact names from Fingerprint Catalog
    private val catalogNames = arrayOf(
        "AOSP", "Blue gradient", "Circle", "ColorOS", "ColorOS 2", "ColorOS 3",
        "Cookie", "Creeper", "Huawei", "Hyper", "MIUI", "NothingOS",
        "OriginOS", "OriginOS blue", "Outline", "OxygenOS", "Ring", "Rubin",
        "Spiral", "Star", "Star blue", "Stipple", "White", "Yin Yang"
    )

    private fun restartSystemUi() {
        Thread {
            val ok = try {
                Runtime.getRuntime()
                    .exec(arrayOf("su", "-c", "killall com.android.systemui || pkill -f com.android.systemui"))
                    .waitFor() == 0
            } catch (t: Throwable) { false }
            runOnUiThread {
                Toast.makeText(
                    this,
                    if (ok) "Restarting System UI…" else "Root failed. Run: su -c killall com.android.systemui",
                    Toast.LENGTH_LONG
                ).show()
            }
        }.start()
    }

    private val latestApi = "https://api.github.com/repos/FaroqueTech0/FP-Studio/releases/latest"

    // null = unknown / not checked yet
    private var updateAvailable: Boolean? = null
    private var latestTag: String? = null
    private var latestPageUrl: String? = null

    private fun currentVersion(): String =
        try { packageManager.getPackageInfo(packageName, 0).versionName ?: "0" } catch (t: Throwable) { "0" }

    /** True when [remote] (e.g. "v1.2.0") is newer than [local] (e.g. "1.0.0"). */
    private fun isNewer(remote: String, local: String): Boolean {
        fun parts(v: String) = v.trim().removePrefix("v").removePrefix("V")
            .split('.', '-', '_').map { it.filter { c -> c.isDigit() }.toIntOrNull() ?: 0 }
        val r = parts(remote); val l = parts(local)
        for (i in 0 until maxOf(r.size, l.size)) {
            val x = r.getOrElse(i) { 0 }; val y = l.getOrElse(i) { 0 }
            if (x != y) return x > y
        }
        return false
    }

    /** Checks GitHub for the latest release. Result: 1 = update, 0 = up to date, -1 = no release yet, -2 = error. */
    private fun checkForUpdate(onDone: (Int) -> Unit) {
        Thread {
            var code = -2
            try {
                val conn = java.net.URL(latestApi).openConnection() as java.net.HttpURLConnection
                conn.connectTimeout = 8000
                conn.readTimeout = 8000
                conn.setRequestProperty("Accept", "application/vnd.github+json")
                when (conn.responseCode) {
                    200 -> {
                        val json = org.json.JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
                        val tag = json.optString("tag_name", "")
                        latestTag = tag
                        latestPageUrl = json.optString("html_url", updateUrl).ifBlank { updateUrl }
                        code = if (tag.isNotBlank() && isNewer(tag, currentVersion())) 1 else 0
                    }
                    404 -> code = -1
                }
                conn.disconnect()
            } catch (t: Throwable) { code = -2 }
            updateAvailable = when (code) { 1 -> true; 0, -1 -> false; else -> updateAvailable }
            runOnUiThread { onDone(code) }
        }.start()
    }

    private fun showAbout() {
        val view = layoutInflater.inflate(R.layout.dialog_about, null)
        view.findViewById<TextView>(R.id.aboutVersion).text = "Version ${currentVersion()}"
        val status = view.findViewById<TextView>(R.id.updateStatus)
        val btn = view.findViewById<Button>(R.id.checkUpdateButton)

        fun render(code: Int) {
            btn.isEnabled = true
            status.setTextColor(0xFF9AA8CC.toInt())
            when (code) {
                1 -> {
                    status.text = "Update available: ${latestTag}"
                    status.setTextColor(0xFF35DFFF.toInt())
                    btn.text = "Download update"
                }
                0 -> { status.text = "You're up to date."; btn.text = "Check again" }
                -1 -> { status.text = "No release published yet."; btn.text = "Check again" }
                else -> { status.text = "Couldn't check. Check your internet connection."; btn.text = "Try again" }
            }
        }
        if (updateAvailable == true) render(1)

        btn.setOnClickListener {
            if (updateAvailable == true) {
                openLink(latestPageUrl ?: updateUrl)
            } else {
                btn.isEnabled = false
                btn.text = "Checking…"
                status.text = "Looking for updates…"
                checkForUpdate { code ->
                    render(code)
                    _binding?.root?.findViewById<View>(R.id.updateDot)?.visibility =
                        if (code == 1) View.VISIBLE else View.GONE
                }
            }
        }
        view.findViewById<Button>(R.id.telegramButton).setOnClickListener { openLink(telegramUrl) }
        view.findViewById<Button>(R.id.donateButton).setOnClickListener { openLink(donateUrl) }
        com.google.android.material.dialog.MaterialAlertDialogBuilder(this, R.style.FP_Dialog)
            .setBackground(androidx.core.content.ContextCompat.getDrawable(this, R.drawable.dialog_bg))
            .setView(view)
            .setNegativeButton("Close", null)
            .show()
    }

    private fun askRestartSystemUi() {
        com.google.android.material.dialog.MaterialAlertDialogBuilder(this, R.style.FP_Dialog)
            .setBackground(androidx.core.content.ContextCompat.getDrawable(this, R.drawable.dialog_bg))
            .setTitle("Restart System UI?")
            .setMessage("Restart System UI now to apply the lock screen icon and animation.")
            .setPositiveButton("Restart now") { _, _ -> restartSystemUi() }
            .setNegativeButton("Later", null)
            .show()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        _binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }

        val prefs = getSharedPreferences("fingers_print_settings", MODE_PRIVATE)
        var selectedIcon = 0   // 0-based index into catalogNames
        val ripple = binding.root.findViewById<Switch>(R.id.rippleEnabled)
        val haptic = binding.root.findViewById<Switch>(R.id.hapticEnabled)
        val hideBg = binding.root.findViewById<Switch>(R.id.hideBgEnabled)
        val iconScale = binding.root.findViewById<SeekBar>(R.id.iconScaleSeek)
        val iconScaleLabel = binding.root.findViewById<TextView>(R.id.iconScaleLabel)
        val iconGrid = binding.root.findViewById<GridLayout>(R.id.iconGrid)
        val size = binding.root.findViewById<SeekBar>(R.id.sizeSeek)
        val speed = binding.root.findViewById<SeekBar>(R.id.speedSeek)
        val speedLabel = binding.root.findViewById<TextView>(R.id.speedLabel)
        val sizeLabel = binding.root.findViewById<TextView>(R.id.sizeLabel)
        val previewText = binding.root.findViewById<TextView>(R.id.fingerprintPreview)
        val previewImg = binding.root.findViewById<ImageView>(R.id.fingerprintPreviewImg)
        val status = binding.root.findViewById<TextView>(R.id.statusText)
        val animSpinner = binding.root.findViewById<Spinner>(R.id.animSpinner)
        val animPreview = binding.root.findViewById<ImageView>(R.id.animPreview)
        val animLabels = arrayOf("None") + ANIM_NAMES.map { n ->
            n.replaceFirstChar { it.uppercase() }
        }
        animSpinner.adapter = ArrayAdapter(this, R.layout.spinner_item, animLabels).also {
            it.setDropDownViewResource(R.layout.spinner_dropdown_item)
        }
        animSpinner.setSelection(prefs.getInt("anim_index", 1).coerceIn(0, ANIM_NAMES.size))

        selectedIcon = (prefs.getInt("catalog_index", 1) - 1).coerceIn(0, catalogNames.size - 1)
        ripple.isChecked = prefs.getBoolean("ripple", true)
        haptic.isChecked = prefs.getBoolean("haptic", true)
        hideBg.isChecked = prefs.getBoolean("hide_bg", false)
        iconScale.progress = prefs.getInt("icon_scale", 180).coerceIn(60, 220)
        size.progress = prefs.getInt("size", 90).coerceIn(10, 100)
        speed.progress = (prefs.getInt("duration", 900) - 400).coerceIn(0, 1600)

        fun iconResId(index: Int): Int {
            var id = resources.getIdentifier("icon_%02d".format(index), "drawable", packageName)
            if (id == 0) id = resources.getIdentifier("fp_icon_%02d".format(index), "drawable", packageName)
            return id
        }

        fun updateAnimPreview() {
            val idx = animSpinner.selectedItemPosition
            (animPreview.drawable as? android.graphics.drawable.AnimationDrawable)?.stop()
            if (idx <= 0) { animPreview.setImageDrawable(null); return }
            val ad = android.graphics.drawable.AnimationDrawable()
            val per = ((speed.progress + 400) / 24).coerceAtLeast(16)
            for (i in 1..40) {
                val id = resources.getIdentifier("fpa_${ANIM_NAMES[idx - 1]}_%02d".format(i), "drawable", packageName)
                if (id == 0) break
                ad.addFrame(resources.getDrawable(id, theme), per)
            }
            ad.isOneShot = false
            animPreview.setImageDrawable(ad)
            ad.start()
        }

        fun updatePreview() {
            val idx = selectedIcon + 1
            val name = catalogNames.getOrElse(idx - 1) { "Icon $idx" }
            previewText.text = name
            val resId = iconResId(idx)
            if (resId != 0) {
                previewImg.setImageResource(resId)
                previewImg.visibility = ImageView.VISIBLE
            } else {
                previewImg.setImageDrawable(null)
                previewImg.visibility = ImageView.GONE
            }
            iconScaleLabel.text = "Lock screen icon size: ${iconScale.progress}%"
            previewImg.scaleX = iconScale.progress / 100f
            previewImg.scaleY = iconScale.progress / 100f
            for (i in 0 until iconGrid.childCount) {
                iconGrid.getChildAt(i).setBackgroundResource(
                    if (i == selectedIcon) R.drawable.icon_cell_selected else R.drawable.icon_cell)
            }
            sizeLabel.text = "Touch animation size: ${size.progress}%"
            speedLabel.text = "Animation speed: ${speed.progress + 400} ms"
        }

        val dens = resources.displayMetrics.density
        val gap = (4 * dens).toInt()
        // screen width minus page padding (20+20 dp) and card padding (18+18 dp); 4 columns
        val avail = resources.displayMetrics.widthPixels - (76 * dens).toInt()
        val cell = minOf(avail / 4 - 2 * gap, (72 * dens).toInt())
        val pad = cell / 7
        for (i in 1..catalogNames.size) {
            val iv = ImageView(this)
            iv.setPadding(pad, pad, pad, pad)
            iv.scaleType = ImageView.ScaleType.FIT_CENTER
            val id = iconResId(i)
            if (id != 0) iv.setImageResource(id)
            iv.contentDescription = catalogNames[i - 1]
            iv.layoutParams = GridLayout.LayoutParams().apply {
                width = cell; height = cell; setMargins(gap, gap, gap, gap)
            }
            iv.setOnClickListener { selectedIcon = i - 1; updatePreview() }
            iconGrid.addView(iv)
        }
        iconScale.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar?, p: Int, fromUser: Boolean) = updatePreview()
            override fun onStartTrackingTouch(s: SeekBar?) {}
            override fun onStopTrackingTouch(s: SeekBar?) {}
        })

        size.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar?, p: Int, fromUser: Boolean) = updatePreview()
            override fun onStartTrackingTouch(s: SeekBar?) {}
            override fun onStopTrackingTouch(s: SeekBar?) {}
        })
        speed.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar?, p: Int, fromUser: Boolean) = updatePreview()
            override fun onStartTrackingTouch(s: SeekBar?) {}
            override fun onStopTrackingTouch(s: SeekBar?) {}
        })
        animSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p: AdapterView<*>?, v: android.view.View?, pos: Int, id: Long) = updateAnimPreview()
            override fun onNothingSelected(p: AdapterView<*>?) {}
        }
        updatePreview()
        updateAnimPreview()

        binding.root.findViewById<ImageButton>(R.id.infoButton).setOnClickListener { showAbout() }
        // Silent check at launch: shows a red dot on the info button when a newer release exists.
        checkForUpdate { code ->
            _binding?.root?.findViewById<View>(R.id.updateDot)?.visibility =
                if (code == 1) View.VISIBLE else View.GONE
        }

        binding.root.findViewById<Button>(R.id.saveButton).setOnClickListener {
            val catalogIndex = selectedIcon + 1
            prefs.edit()
                .putString("icon", "catalog")
                .putInt("catalog_index", catalogIndex)
                .putInt("anim_index", animSpinner.selectedItemPosition)
                .putBoolean("ripple", ripple.isChecked)
                .putBoolean("haptic", haptic.isChecked)
                .putBoolean("hide_bg", hideBg.isChecked)
                .putInt("icon_scale", iconScale.progress)
                .putInt("size", size.progress)
                .putInt("duration", speed.progress + 400)
                .apply()
            status.text = "Saved: ${catalogNames[catalogIndex - 1]}"
            Toast.makeText(this, "Saved", Toast.LENGTH_SHORT).show()
            askRestartSystemUi()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        _binding = null
    }
}
