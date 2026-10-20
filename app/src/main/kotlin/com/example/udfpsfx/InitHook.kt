package com.example.udfpsfx

import android.animation.ValueAnimator
import android.content.Context
import android.database.Cursor
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.net.Uri
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.FrameLayout
import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage

private const val TAG = "UdfpsFx"
private const val BUILD = "1.0.0"
private const val VIEW_TAG = "udfpsfx_icon"
private const val MODULE_PKG = "com.faroquetech.fingerflux"
private const val SETTINGS_URI = "content://com.faroquetech.fingerflux.settings/settings"

/** Frame animations extracted from the Fingerprint Catalog app (drawable-nodpi/fpa_<name>_NN.webp). */
val ANIM_NAMES = arrayOf(
    "buble", "cosmos", "creeper", "dust", "firework", "flare", "genshin", "halo", "magic",
    "originos", "originosblue", "oxygenos", "ripple", "spark", "stardust", "swirl", "transparency"
)

data class LiveSettings(
    val catalogIndex: Int = 1,
    val showIcon: Boolean = true,
    val haptic: Boolean = true,
    val size: Int = 90,
    val duration: Long = 900L,
    val animIndex: Int = 1,  // 0 = none, 1..17 = ANIM_NAMES[index-1]
    val hideBg: Boolean = false,
    val iconScale: Int = 180   // lock screen icon size, % of the stock icon
)

private fun readSettings(context: Context): LiveSettings {
    return try {
        val cursor: Cursor = context.contentResolver.query(Uri.parse(SETTINGS_URI), null, null, null, null)
            ?: return LiveSettings()
        cursor.use {
            val values = mutableMapOf<String, String>()
            val ki = it.getColumnIndex("key")
            val vi = it.getColumnIndex("value")
            while (it.moveToNext()) {
                if (ki >= 0 && vi >= 0) values[it.getString(ki)] = it.getString(vi)
            }
            LiveSettings(
                catalogIndex = (values["catalog_index"]?.toIntOrNull() ?: 1).coerceIn(1, 24),
                showIcon = values["ripple"]?.toBoolean() ?: true,
                haptic = values["haptic"]?.toBoolean() ?: true,
                size = (values["size"]?.toIntOrNull() ?: 90).coerceIn(10, 100),
                duration = (values["duration"]?.toLongOrNull() ?: 900L).coerceIn(400L, 2000L),
                animIndex = (values["anim_index"]?.toIntOrNull() ?: 1).coerceIn(0, ANIM_NAMES.size),
                hideBg = values["hide_bg"]?.toBoolean() ?: false,
                iconScale = (values["icon_scale"]?.toIntOrNull() ?: 180).coerceIn(60, 220)
            )
        }
    } catch (t: Throwable) {
        Log.e(TAG, "readSettings failed", t)
        LiveSettings()
    }
}

private fun moduleContext(host: Context): Context? {
    return try {
        host.createPackageContext(
            MODULE_PKG,
            Context.CONTEXT_IGNORE_SECURITY or Context.CONTEXT_INCLUDE_CODE
        )
    } catch (t: Throwable) {
        Log.e(TAG, "createPackageContext failed", t)
        null
    }
}

class InitHook : IXposedHookLoadPackage {
    override fun handleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam) {
        if (lpparam.packageName != "com.android.systemui") return
        Log.i(TAG, "SystemUI loaded – installing classic Xposed hooks")

        val candidates = listOf(
            "com.android.systemui.biometrics.UdfpsController",
            "com.android.systemui.biometrics.AuthRippleController",
            "com.android.systemui.biometrics.UdfpsControllerOverlay"
        )

        var installed = 0
        for (className in candidates) {
            val target = try {
                XposedHelpers.findClass(className, lpparam.classLoader)
            } catch (t: Throwable) {
                Log.w(TAG, "Class not found: $className")
                continue
            }
            Log.i(TAG, "Found $className")

            for (method in target.declaredMethods) {
                val name = method.name
                val isDown = name.equals("onFingerDown", true) ||
                        name.equals("onPointerDown", true) ||
                        (name.contains("finger", true) && name.contains("down", true)) ||
                        (name.contains("pointer", true) && name.contains("down", true))
                val isUp = name.equals("onFingerUp", true) ||
                        name.equals("onPointerUp", true) ||
                        (name.contains("finger", true) && name.contains("up", true)) ||
                        (name.contains("pointer", true) && name.contains("up", true))
                if (!isDown && !isUp) continue

                try {
                    XposedBridge.hookMethod(method, object : XC_MethodHook() {
                        override fun afterHookedMethod(param: MethodHookParam) {
                            try {
                                if (isDown) {
                                    Log.d(TAG, "DOWN via $name")
                                    val context = findContext(param.thisObject) ?: run {
                                        Log.w(TAG, "no context")
                                        return
                                    }
                                    val settings = readSettings(context)
                                    if (!settings.showIcon) return
                                    iconFor(param.thisObject, context, settings)?.let { (view, parent) ->
                                        parent.post {
                                            view.startAnim()
                                            Log.d(TAG, "anim started")
                                        }
                                    } ?: Log.w(TAG, "overlay/sensor not found")
                                } else {
                                    Log.d(TAG, "UP via $name")
                                    findIconView(param.thisObject)?.stopAnim()
                                }
                            } catch (t: Throwable) {
                                Log.e(TAG, "handler error", t)
                            }
                        }
                    })
                    installed++
                    Log.i(TAG, "HOOKED ${target.simpleName}.$name")
                } catch (t: Throwable) {
                    Log.w(TAG, "hook failed $name", t)
                }
            }
        }
        // Lock screen icon (Android 14+). The binder lambda below applies state/tint/padding to the
        // foreground icon view every time the icon changes, so we swap the image right after it.
        Log.i(TAG, "build $BUILD")
        var fgHooked = false
        try {
            val emitCls = XposedHelpers.findClass(
                "com.android.systemui.keyguard.ui.binder.DeviceEntryIconViewBinder\$bind\$4\$1\$1\$1",
                lpparam.classLoader)
            for (m in emitCls.declaredMethods) {
                if (m.name != "emit") continue
                XposedBridge.hookMethod(m, object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        try {
                            val fg = getField(param.thisObject, "\$fgIconView") as? android.widget.ImageView ?: return
                            val vm = param.args.getOrNull(0) ?: return
                            val type = (getField(vm, "type") as? Enum<*>)?.name
                            val aod = getField(vm, "useAodVariant") as? Boolean ?: false
                            applyLockIcon(fg, type == "FINGERPRINT" && !aod)
                        } catch (t: Throwable) {
                            Log.e(TAG, "lock icon emit error", t)
                        }
                    }
                })
                installed++; fgHooked = true
                Log.i(TAG, "HOOKED DeviceEntryIconViewBinder foreground emit")
            }
        } catch (t: Throwable) {
            Log.w(TAG, "foreground emit class not found", t)
        }

        // Optional: hide the black circle (background view), re-applied whenever the binder updates it.
        try {
            val bgCls = XposedHelpers.findClass(
                "com.android.systemui.keyguard.ui.binder.DeviceEntryIconViewBinder\$bind\$2\$1\$1\$1",
                lpparam.classLoader)
            for (m in bgCls.declaredMethods) {
                if (m.name != "emit") continue
                XposedBridge.hookMethod(m, object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        try {
                            val bg = getField(param.thisObject, "\$bgView") as? android.widget.ImageView ?: return
                            if (readSettings(bg.context).hideBg) bg.alpha = 0f
                        } catch (t: Throwable) {
                            Log.e(TAG, "bg emit error", t)
                        }
                    }
                })
                installed++
                Log.i(TAG, "HOOKED DeviceEntryIconViewBinder background emit")
            }
        } catch (t: Throwable) {
            Log.w(TAG, "background emit class not found", t)
        }

        // Fallback if the binder lambda is missing: hook getIconState instead.
        if (!fgHooked) try {
            val iconCls = XposedHelpers.findClass(
                "com.android.systemui.keyguard.ui.view.DeviceEntryIconView", lpparam.classLoader)
            for (m in iconCls.declaredMethods) {
                if (m.name != "getIconState") continue
                XposedBridge.hookMethod(m, object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        try {
                            val view = param.thisObject as? FrameLayout ?: return
                            val fg = getField(view, "iconView") as? android.widget.ImageView ?: return
                            val type = (param.args.getOrNull(0) as? Enum<*>)?.name
                            val aod = param.args.getOrNull(1) as? Boolean ?: false
                            applyLockIcon(fg, type == "FINGERPRINT" && !aod)
                        } catch (t: Throwable) {
                            Log.e(TAG, "lock icon error", t)
                        }
                    }
                })
                installed++
                Log.i(TAG, "HOOKED DeviceEntryIconView.getIconState (fallback)")
            }
        } catch (t: Throwable) {
            Log.w(TAG, "DeviceEntryIconView not found", t)
        }

        if (installed == 0) Log.e(TAG, "NO methods hooked")
        else Log.i(TAG, "Total hooks: $installed")
    }

    private val origDrawables = java.util.WeakHashMap<android.widget.ImageView, android.graphics.drawable.Drawable>()
    private val ourDrawables = java.util.WeakHashMap<android.widget.ImageView, NoTintDrawable>()

    /**
     * Swaps the image of the stock lock screen foreground icon for the chosen Catalog icon while the
     * FINGERPRINT state is shown, and puts the original back for LOCK/UNLOCK/AOD.
     */
    private fun applyLockIcon(iconView: android.widget.ImageView, isFingerprint: Boolean) {
        val ctx = iconView.context
        val settings = readSettings(ctx)
        val current = iconView.drawable

        if (!(settings.showIcon && isFingerprint)) {
            val orig = origDrawables[iconView]
            if (orig != null && current is NoTintDrawable) iconView.setImageDrawable(orig)
            if (orig != null) { iconView.scaleX = 1f; iconView.scaleY = 1f }
            return
        }

        val modCtx = moduleContext(ctx) ?: return
        var ours = ourDrawables[iconView]
        if (ours == null || ours.catalogIndex != settings.catalogIndex) {
            val id = modCtx.resources.getIdentifier(
                "icon_%02d".format(settings.catalogIndex), "drawable", MODULE_PKG)
            if (id == 0) { Log.w(TAG, "No resource for icon ${settings.catalogIndex}"); return }
            val raw = BitmapFactory.decodeResource(modCtx.resources, id) ?: return
            val bmp = cropToContent(raw)
            ours = NoTintDrawable(ctx.resources, bmp, settings.catalogIndex)
            ourDrawables[iconView] = ours
        }
        if (current !== ours) {
            if (current != null && current !is NoTintDrawable) origDrawables[iconView] = current
            iconView.setImageDrawable(ours)
        }
        iconView.imageTintList = null
        // Make every icon fill the whole view: drop SystemUI's padding and let the scaled view
        // draw outside its parents' bounds so it isn't clipped.
        iconView.setPadding(0, 0, 0, 0)
        iconView.scaleType = android.widget.ImageView.ScaleType.FIT_CENTER
        iconView.adjustViewBounds = false
        var p = iconView.parent
        var depth = 0
        while (p is ViewGroup && depth < 4) {
            p.clipChildren = false
            p.clipToPadding = false
            p = p.parent
            depth++
        }
        val s = settings.iconScale / 100f
        iconView.scaleX = s
        iconView.scaleY = s
        Log.d(TAG, "lock icon -> catalog #${settings.catalogIndex}")
    }

    /** Trims transparent margins so all catalog icons render at the same visual size, returned as a square. */
    private fun cropToContent(src: Bitmap): Bitmap {
        try {
            val w = src.width; val h = src.height
            val px = IntArray(w * h)
            src.getPixels(px, 0, w, 0, 0, w, h)
            var minX = w; var minY = h; var maxX = -1; var maxY = -1
            for (y in 0 until h) for (x in 0 until w) {
                if ((px[y * w + x] ushr 24) > 12) {
                    if (x < minX) minX = x; if (x > maxX) maxX = x
                    if (y < minY) minY = y; if (y > maxY) maxY = y
                }
            }
            if (maxX < 0) return src
            val cw = maxX - minX + 1; val ch = maxY - minY + 1
            if (cw >= w - 2 && ch >= h - 2) return src
            val side = maxOf(cw, ch)
            val out = Bitmap.createBitmap(side, side, Bitmap.Config.ARGB_8888)
            android.graphics.Canvas(out).drawBitmap(
                src, android.graphics.Rect(minX, minY, maxX + 1, maxY + 1),
                android.graphics.Rect((side - cw) / 2, (side - ch) / 2, (side - cw) / 2 + cw, (side - ch) / 2 + ch), null)
            return out
        } catch (t: Throwable) {
            return src
        }
    }

    private fun findContext(root: Any): Context? {
        if (root is View) return root.context
        for (name in arrayOf("mContext", "context", "mOverlay", "overlay", "mOverlayController", "overlayController")) {
            val value = getField(root, name) ?: continue
            when (value) {
                is Context -> return value
                is View -> return value.context
                else -> (getField(value, "mContext") as? Context)?.let { return it }
            }
        }
        return null
    }

    private fun iconFor(controller: Any, hostContext: Context, settings: LiveSettings): Pair<IconView, ViewGroup>? {
        val overlayCtl = getField(controller, "mOverlayController")
            ?: getField(controller, "mOverlay")
            ?: getField(controller, "overlayController")
            ?: getField(controller, "overlay")

        val direct = overlayCtl?.let { ctl ->
            sequenceOf(
                "overlayTouchView", "mOverlayTouchView", "mView", "overlayView",
                "mUdfpsTouchOverlay", "udfpsTouchOverlay", "touchOverlay"
            ).mapNotNull { getField(ctl, it) as? ViewGroup }.firstOrNull()
        }

        val pair = direct?.let { c -> findSensorRectIn(c)?.let { c to it } }
            ?: findViewAndSensorRect(controller) ?: return null

        val overlay = pair.first
        val sensor = pair.second

        var view = overlay.findViewWithTag<IconView>(VIEW_TAG)
        if (view == null) {
            view = IconView(overlay.context).apply {
                tag = VIEW_TAG
                visibility = View.GONE
            }
            overlay.addView(
                view,
                FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
            )
        }

        val loc = IntArray(2)
        overlay.getLocationOnScreen(loc)
        view.cx = sensor.exactCenterX() - loc[0]
        view.cy = sensor.exactCenterY() - loc[1]
        view.maxR = maxOf(sensor.width().toFloat(), sensor.height().toFloat()) * (settings.size / 100f)
        val modCtx = moduleContext(hostContext) ?: hostContext
        view.configure(modCtx, settings)
        return view to overlay
    }

    private fun findIconView(root: Any): IconView? {
        val seen = java.util.Collections.newSetFromMap(java.util.IdentityHashMap<Any, Boolean>())
        val queue = java.util.ArrayDeque<Any>()
        queue.add(root)
        var checked = 0
        while (queue.isNotEmpty() && checked++ < 160) {
            val obj = queue.removeFirst()
            if (!seen.add(obj)) continue
            if (obj is ViewGroup) {
                obj.findViewWithTag<IconView>(VIEW_TAG)?.let { return it }
                for (i in 0 until obj.childCount) queue.add(obj.getChildAt(i))
            }
            var c: Class<*>? = obj.javaClass
            while (c != null && c != Any::class.java) {
                for (f in runCatching { c!!.declaredFields.toList() }.getOrDefault(emptyList())) {
                    if (java.lang.reflect.Modifier.isStatic(f.modifiers)) continue
                    if (f.type.isPrimitive || f.type.name.startsWith("java.")) continue
                    runCatching {
                        f.isAccessible = true
                        val value = f.get(obj) ?: return@runCatching
                        if (value is ViewGroup) queue.add(value)
                        else if (!value.javaClass.name.startsWith("android.") &&
                            !value.javaClass.name.startsWith("java.")) queue.add(value)
                    }
                }
                c = c.superclass
            }
        }
        return null
    }

    private fun findViewAndSensorRect(root: Any): Pair<ViewGroup, Rect>? {
        val seen = java.util.Collections.newSetFromMap(java.util.IdentityHashMap<Any, Boolean>())
        val queue = java.util.ArrayDeque<Any>()
        queue.add(root)
        var checked = 0
        while (queue.isNotEmpty() && checked++ < 240) {
            val obj = queue.removeFirst()
            if (!seen.add(obj)) continue
            if (obj is ViewGroup) {
                findSensorRectIn(obj)?.let { return obj to it }
                for (i in 0 until obj.childCount) queue.add(obj.getChildAt(i))
            }
            var c: Class<*>? = obj.javaClass
            while (c != null && c != Any::class.java) {
                for (f in runCatching { c!!.declaredFields.toList() }.getOrDefault(emptyList())) {
                    if (java.lang.reflect.Modifier.isStatic(f.modifiers)) continue
                    if (f.type.isPrimitive || f.type.name.startsWith("java.")) continue
                    runCatching {
                        f.isAccessible = true
                        val value = f.get(obj) ?: return@runCatching
                        if (value is ViewGroup) queue.add(value)
                        else if (!value.javaClass.name.startsWith("android.") &&
                            !value.javaClass.name.startsWith("java.")) queue.add(value)
                    }
                }
                c = c.superclass
            }
        }
        return null
    }

    private fun findSensorRectIn(root: Any): Rect? {
        for (name in arrayOf("sensorRect", "mSensorRect", "sensorBounds", "mSensorBounds", "mUdfpsOverlayParams")) {
            val value = getField(root, name)
            when (value) {
                is Rect -> return Rect(value)
                else -> if (value != null) {
                    (getField(value, "sensorBounds") as? Rect)?.let { return Rect(it) }
                    (getField(value, "sensorRect") as? Rect)?.let { return Rect(it) }
                }
            }
        }
        val params = getField(root, "mOverlayParams") ?: getField(root, "overlayParams")
            ?: getField(root, "mUdfpsOverlayParams")
        for (name in arrayOf("sensorRect", "mSensorRect", "sensorBounds", "mSensorBounds")) {
            (params?.let { getField(it, name) } as? Rect)?.let { return Rect(it) }
        }
        return null
    }

    private fun getField(obj: Any, name: String): Any? {
        var c: Class<*>? = obj.javaClass
        while (c != null) {
            try {
                return c.getDeclaredField(name).apply { isAccessible = true }.get(obj)
            } catch (_: NoSuchFieldException) {
                c = c.superclass
            }
        }
        return null
    }
}

class IconView(context: Context) : View(context) {
    var cx = 0f
    var cy = 0f
    var maxR = 100f
    private var iconBitmap: Bitmap? = null
    private var progress = 0f
    private var durationMs = 900L
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

    // Catalog frame animation (plays once per touch, then the icon keeps pulsing)
    private var frames: List<Bitmap> = emptyList()
    private var loadedAnim = -1
    private var frameProgress = -1f
    private val frameAnim = ValueAnimator.ofFloat(0f, 1f).apply {
        interpolator = android.view.animation.LinearInterpolator()
        addUpdateListener { frameProgress = it.animatedValue as Float; invalidate() }
        addListener(object : android.animation.AnimatorListenerAdapter() {
            override fun onAnimationEnd(a: android.animation.Animator) { frameProgress = -1f; invalidate() }
        })
    }

    private val anim = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = durationMs
        repeatCount = ValueAnimator.INFINITE
        repeatMode = ValueAnimator.REVERSE
        interpolator = AccelerateDecelerateInterpolator()
        addUpdateListener {
            progress = it.animatedValue as Float
            invalidate()
        }
    }

    fun configure(moduleCtx: Context, settings: LiveSettings) {
        durationMs = settings.duration
        anim.duration = durationMs
        loadIcon(moduleCtx, settings.catalogIndex)
        frameAnim.duration = durationMs
        loadFrames(moduleCtx, settings.animIndex)
    }

    private fun loadFrames(moduleCtx: Context, animIndex: Int) {
        if (animIndex == loadedAnim) return
        loadedAnim = animIndex
        frames = emptyList()
        if (animIndex <= 0) return
        try {
            val name = ANIM_NAMES[animIndex - 1]
            val list = ArrayList<Bitmap>()
            for (i in 1..40) {
                val id = moduleCtx.resources.getIdentifier("fpa_${name}_%02d".format(i), "drawable", MODULE_PKG)
                if (id == 0) break
                BitmapFactory.decodeResource(moduleCtx.resources, id)?.let { list.add(it) }
            }
            frames = list
            Log.d(TAG, "Loaded animation $name: ${list.size} frames")
        } catch (t: Throwable) {
            Log.e(TAG, "loadFrames failed", t)
        }
    }

    private fun loadIcon(moduleCtx: Context, catalogIndex: Int) {
        iconBitmap = null
        try {
            var resId = moduleCtx.resources.getIdentifier(
                "icon_%02d".format(catalogIndex), "drawable", MODULE_PKG)
            if (resId == 0) {
                resId = moduleCtx.resources.getIdentifier(
                    "fp_icon_%02d".format(catalogIndex), "drawable", MODULE_PKG)
            }
            if (resId != 0) {
                iconBitmap = BitmapFactory.decodeResource(moduleCtx.resources, resId)
                Log.d(TAG, "Loaded icon $catalogIndex")
            } else {
                Log.w(TAG, "No resource for icon $catalogIndex")
            }
        } catch (t: Throwable) {
            Log.e(TAG, "loadIcon failed", t)
        }
        invalidate()
    }

    fun startAnim() {
        visibility = VISIBLE
        if (!anim.isRunning) anim.start()
        if (frames.isNotEmpty()) { frameAnim.cancel(); frameAnim.start() }
        invalidate()
    }

    fun stopAnim() {
        anim.cancel()
        frameAnim.cancel()
        frameProgress = -1f
        progress = 0f
        visibility = GONE
    }

    private fun drawFrame(canvas: Canvas) {
        val list = frames
        if (list.isEmpty() || frameProgress < 0f) return
        val f = list[(frameProgress * list.size).toInt().coerceIn(0, list.size - 1)]
        // Frames are larger than the sensor (glow/particles); fit the longest side to ~2.6x the sensor radius.
        val s = maxR * 2.6f / maxOf(f.width, f.height)
        val w = f.width * s
        val h = f.height * s
        paint.alpha = 255
        canvas.drawBitmap(f, null, RectF(cx - w / 2f, cy - h / 2f, cx + w / 2f, cy + h / 2f), paint)
    }

    override fun onDraw(canvas: Canvas) {
        drawFrame(canvas)
        val bmp = iconBitmap
        if (bmp == null) {
            paint.alpha = 200
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 4f
            paint.color = 0xFF00E5FF.toInt()
            val scale = 0.85f + 0.30f * progress
            canvas.drawCircle(cx, cy, maxR * scale, paint)
            return
        }
        val scale = 0.85f + 0.30f * progress
        paint.alpha = (180 + 75 * progress).toInt().coerceIn(140, 255)
        paint.style = Paint.Style.FILL
        val size = maxR * 1.25f * scale
        val left = cx - size / 2f
        val top = cy - size / 2f
        canvas.drawBitmap(bmp, null, RectF(left, top, left + size, top + size), paint)
    }
}

/** Catalog icon as a drawable that ignores the tint SystemUI applies, so the icon keeps its own colors. */
class NoTintDrawable(res: android.content.res.Resources, bmp: Bitmap, val catalogIndex: Int) :
    android.graphics.drawable.BitmapDrawable(res, bmp) {
    override fun setTintList(tint: android.content.res.ColorStateList?) {}
    override fun setTint(tintColor: Int) {}
    override fun setTintMode(tintMode: android.graphics.PorterDuff.Mode?) {}
    override fun setColorFilter(colorFilter: android.graphics.ColorFilter?) {}
    override fun mutate(): android.graphics.drawable.Drawable = this
}
