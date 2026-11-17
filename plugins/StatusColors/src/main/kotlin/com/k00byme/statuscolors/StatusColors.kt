package com.k00byme.statuscolors

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.ShapeDrawable
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.core.graphics.drawable.DrawableCompat
import com.aliucord.annotations.AliucordPlugin
import com.aliucord.entities.Plugin
import com.aliucord.patcher.after
import com.aliucord.patcher.before
import com.discord.views.StatusView
import com.discord.views.user.UserAvatarPresenceView
import java.util.Collections
import java.util.WeakHashMap

private enum class PresenceKind(
    val settingsKey: String,
    val label: String,
    val defaultHex: String,
) {
    ONLINE("onlineColor", "Online", "#3BA55C"),
    IDLE("idleColor", "Idle", "#FAA61A"),
    DND("dndColor", "Do Not Disturb", "#ED4245"),
    OFFLINE("offlineColor", "Offline / Invisible", "#747F8D"),
}

private data class AppliedColor(
    val kind: PresenceKind,
    val applied: Int,
)

@AliucordPlugin(requiresRestart = false)
@Suppress("unused")
class StatusColors : Plugin() {
    private val viewColors = Collections.synchronizedMap(WeakHashMap<View, AppliedColor>())
    private val paintColors = Collections.synchronizedMap(WeakHashMap<Paint, AppliedColor>())

    override fun start(context: Context) {
        settingsTab = SettingsTab(StatusColorsSettings::class.java).withArgs(settings)

        // Avatar presence is used in profiles and beside avatars throughout the app.
        patcher.after<UserAvatarPresenceView>(
            "onMeasure",
            Int::class.javaPrimitiveType!!,
            Int::class.javaPrimitiveType!!,
        ) { param ->
            val avatarPresence = param.thisObject as? View ?: return@after
            val indicator = findIndicatorChild(avatarPresence)
            recolorTree(indicator ?: avatarPresence)
        }

        // Member-list presence uses StatusView rather than the avatar wrapper.
        val statusViewMeasure = runCatching {
            StatusView::class.java.getDeclaredMethod(
                "onMeasure",
                Int::class.javaPrimitiveType!!,
                Int::class.javaPrimitiveType!!,
            )
        }.getOrNull()

        if (statusViewMeasure != null) {
            patcher.after<StatusView>(
                "onMeasure",
                Int::class.javaPrimitiveType!!,
                Int::class.javaPrimitiveType!!,
            ) { param ->
                val statusView = param.thisObject as? View ?: return@after
                recolorTree(statusView)
            }
        }

        // Some Discord builds draw the dot directly with a Paint instead of a background drawable.
        val statusViewDraw = runCatching {
            StatusView::class.java.getDeclaredMethod("onDraw", Canvas::class.java)
        }.getOrNull()

        if (statusViewDraw != null) {
            patcher.before<StatusView>("onDraw", Canvas::class.java) { param ->
                val statusView = param.thisObject as? View ?: return@before
                recolorPaintFields(statusView)
                recolorTree(statusView)
            }
        }
    }

    override fun stop(context: Context) {
        patcher.unpatchAll()
        viewColors.clear()
        paintColors.clear()
    }

    private fun customColor(kind: PresenceKind): Int {
        val hex = settings.getString(kind.settingsKey, kind.defaultHex)
        return runCatching { Color.parseColor(normalizeHex(hex)) }
            .getOrDefault(Color.parseColor(kind.defaultHex))
    }

    private fun normalizeHex(value: String): String {
        val trimmed = value.trim()
        return if (trimmed.startsWith("#")) trimmed else "#$trimmed"
    }

    private fun findIndicatorChild(root: View): View? {
        // Aliucord's current AvatarDecorator accesses this same child as UserAvatarPresenceView.c.
        var type: Class<*>? = root.javaClass
        while (type != null) {
            val currentType = type
            val field = runCatching { currentType.getDeclaredField("c") }.getOrNull()
            if (field != null) {
                val child = runCatching {
                    field.isAccessible = true
                    field.get(root) as? View
                }.getOrNull()
                if (child != null) return child
            }
            type = type.superclass
        }

        val getter = root.javaClass.methods.firstOrNull {
            it.parameterTypes.isEmpty() && it.name == "getC" && View::class.java.isAssignableFrom(it.returnType)
        }
        return runCatching { getter?.invoke(root) as? View }.getOrNull()
    }

    private fun recolorTree(root: View) {
        recolorView(root)
        if (root is ViewGroup) {
            for (index in 0 until root.childCount) {
                recolorTree(root.getChildAt(index))
            }
        }
    }

    private fun recolorView(view: View) {
        val sourceColor = view.background?.let(::readDrawableColor)
            ?: view.backgroundTintList?.defaultColor
            ?: (view as? ImageView)?.imageTintList?.defaultColor
            ?: (view as? ImageView)?.drawable?.let(::readDrawableColor)
            ?: return

        val previous = viewColors[view]
        val kind = if (previous != null && sourceColor == previous.applied) {
            previous.kind
        } else {
            identifyPresence(sourceColor)
        } ?: return

        val newColor = customColor(kind)
        viewColors[view] = AppliedColor(kind, newColor)

        // Keep the original drawable intact; a view tint survives recycled avatar/list cells.
        view.backgroundTintList = ColorStateList.valueOf(newColor)
        if (view is ImageView) {
            view.imageTintList = ColorStateList.valueOf(newColor)
        }
    }

    private fun readDrawableColor(drawable: Drawable): Int? {
        val baseColor = when (drawable) {
            is ColorDrawable -> drawable.color
            is GradientDrawable -> drawable.color?.defaultColor
            is ShapeDrawable -> drawable.paint.color
            else -> null
        }
        if (baseColor != null) return baseColor

        val tint = runCatching { DrawableCompat.getTintList(drawable)?.defaultColor }.getOrNull()
        if (tint != null) return tint

        if (drawable is android.graphics.drawable.LayerDrawable) {
            for (index in 0 until drawable.numberOfLayers) {
                val layerColor = readDrawableColor(drawable.getDrawable(index))
                if (layerColor != null) return layerColor
            }
        }
        return null
    }

    private fun recolorPaintFields(view: View) {
        var type: Class<*>? = view.javaClass
        while (type != null) {
            for (field in type.declaredFields) {
                if (!Paint::class.java.isAssignableFrom(field.type)) continue
                val paint = runCatching {
                    field.isAccessible = true
                    field.get(view) as? Paint
                }.getOrNull() ?: continue
                recolorPaint(paint)
            }
            type = type.superclass
        }
    }

    private fun recolorPaint(paint: Paint) {
        val sourceColor = paint.color
        val previous = paintColors[paint]
        val kind = if (previous != null && sourceColor == previous.applied) {
            previous.kind
        } else {
            identifyPresence(sourceColor)
        } ?: return

        val newColor = customColor(kind)
        paintColors[paint] = AppliedColor(kind, newColor)
        paint.color = newColor
    }

    private fun identifyPresence(color: Int): PresenceKind? {
        val hsv = FloatArray(3)
        Color.colorToHSV(color, hsv)
        val hue = hsv[0]
        val saturation = hsv[1]
        val value = hsv[2]

        if (value < 0.25f) return null
        if (saturation < 0.23f) return PresenceKind.OFFLINE
        return when {
            hue in 82f..175f -> PresenceKind.ONLINE
            hue in 20f..78f -> PresenceKind.IDLE
            hue <= 18f || hue >= 342f -> PresenceKind.DND
            else -> null
        }
    }
}
