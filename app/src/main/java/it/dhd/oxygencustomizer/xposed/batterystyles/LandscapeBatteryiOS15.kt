package it.dhd.oxygencustomizer.xposed.batterystyles

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.BlendMode
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.util.TypedValue
import androidx.core.graphics.PathParser
import it.dhd.oxygencustomizer.R
import it.dhd.oxygencustomizer.xposed.ResourceManager.modRes
import it.dhd.oxygencustomizer.xposed.hooks.systemui.SettingsLibUtilsProvider
import kotlin.math.floor

open class LandscapeBatteryiOS15(private val context: Context, frameColor: Int, private val xposed: Boolean) :
    BatteryDrawable() {

    // Need to load:
    // 1. perimeter shape
    // 2. fill mask (if smaller than perimeter, this would create a fill that
    //    doesn't touch the walls
    private val perimeterPath = Path()
    private val scaledPerimeter = Path()
    private val errorPerimeterPath = Path()
    private val scaledErrorPerimeter = Path()

    // Fill will cover the whole bounding rect of the fillMask, and be masked by the path
    private val fillMask = Path()
    private val scaledFill = Path()

    // Based off of the mask, the fill will interpolate across this space
    private val fillRect = RectF()

    // Top of this rect changes based on level, 100% == fillRect
    private val levelRect = RectF()
    private val levelPath = Path()

    // Updates the transform of the paths when our bounds change
    private val scaleMatrix = Matrix()
    private val padding = Rect()

    // The net result of fill + perimeter paths
    private val unifiedPath = Path()

    // Bolt path (used while charging)
    private val boltPath = Path()
    private val scaledBolt = Path()

    // Plus sign (used for power save mode)
    private val plusPath = Path()
    private val scaledPlus = Path()

    private var intrinsicHeight: Int
    private var intrinsicWidth: Int

    // To implement hysteresis, keep track of the need to invert the interior icon of the battery
    private var invertFillIcon = false

    // Colors can be configured based on battery level (see res/values/arrays.xml)
    private var colorLevels: IntArray

    private var fillColor: Int = Color.WHITE
    private var boltColor: Int = Color.WHITE
    private var backgroundColor: Int = Color.WHITE

    // updated whenever level changes
    private var levelColor: Int = Color.WHITE

    // Dual tone implies that battery level is a clipped overlay over top of the whole shape
    private var dualTone = false

    private var batteryLevel = 0

    private val invalidateRunnable: () -> Unit = {
        invalidateSelf()
    }

    open var criticalLevel: Int = 5

    var charging = false
        set(value) {
            field = value
            postInvalidate()
        }

    override fun setChargingEnabled(charging: Boolean, isFast: Boolean) {
        this.charging = charging
        postInvalidate()
    }

    var powerSaveEnabled = false
        set(value) {
            field = value
            postInvalidate()
        }

    override fun setPowerSavingEnabled(powerSaveEnabled: Boolean) {
        this.powerSaveEnabled = powerSaveEnabled
        postInvalidate()
    }

    var showPercent = false
        set(value) {
            field = value
            postInvalidate()
        }

    override fun setShowPercentEnabled(showPercent: Boolean) {
        this.showPercent = showPercent
        postInvalidate()
    }

    private val fillColorStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).also { p ->
        p.color = frameColor
        p.alpha = 255
        p.isDither = true
        p.strokeWidth = 1f
        p.style = Paint.Style.STROKE
        p.blendMode = BlendMode.SRC
        p.strokeMiter = 5f
        p.strokeJoin = Paint.Join.ROUND
    }

    private val fillColorStrokeProtection = Paint(Paint.ANTI_ALIAS_FLAG).also { p ->
        p.isDither = true
        p.strokeWidth = 5f
        p.style = Paint.Style.STROKE
        p.blendMode = BlendMode.CLEAR
        p.strokeMiter = 5f
        p.strokeJoin = Paint.Join.ROUND
    }

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).also { p ->
        p.color = frameColor
        p.alpha = 255
        p.isDither = true
        p.strokeWidth = 0f
        p.style = Paint.Style.FILL_AND_STROKE
    }

    private val errorPaint = Paint(Paint.ANTI_ALIAS_FLAG).also { p ->
        p.color =
            if (xposed) SettingsLibUtilsProvider.getColorAttrDefaultColor(context, android.R.attr.colorError)
            else getColorAttrDefaultColor(context, android.R.attr.colorError, Color.RED)
        p.alpha = 255
        p.isDither = true
        p.strokeWidth = 0f
        p.style = Paint.Style.FILL_AND_STROKE
        p.blendMode = BlendMode.SRC
    }

    // Only used if dualTone is set to true
    private val dualToneBackgroundFill = Paint(Paint.ANTI_ALIAS_FLAG).also { p ->
        p.color = frameColor
        p.alpha = 85 // ~0.3 alpha by default
        p.isDither = true
        p.strokeWidth = 0f
        p.style = Paint.Style.FILL_AND_STROKE
    }

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).also { p ->
        p.typeface = Typeface.create("sans-serif-condensed", Typeface.BOLD)
        p.textAlign = Paint.Align.CENTER
    }

    init {
        val density = context.resources.displayMetrics.density
        intrinsicHeight = (HEIGHT * density).toInt()
        intrinsicWidth = (WIDTH * density).toInt()

        val res = context.resources
        val levels = res.obtainTypedArray(
            res.getIdentifier(
                "batterymeter_color_levels", "array", context.packageName
            )
        )
        val colors = res.obtainTypedArray(
            res.getIdentifier(
                "batterymeter_color_values", "array", context.packageName
            )
        )
        val n = levels.length()
        colorLevels = IntArray(2 * n)
        for (i in 0 until n) {
            colorLevels[2 * i] = levels.getInt(i, 0)
            if (colors.getType(i) == TypedValue.TYPE_ATTRIBUTE) {
                colorLevels[2 * i + 1] =
                    if (xposed) SettingsLibUtilsProvider.getColorAttrDefaultColor(
                                    colors.getResourceId(i, 0), context
                                )
                    else getColorAttrDefaultColor(context, colors.getResourceId(i, 0), Color.WHITE)
            } else {
                colorLevels[2 * i + 1] = colors.getColor(i, 0)
            }
        }
        levels.recycle()
        colors.recycle()

        loadPaths()
    }

    override fun draw(c: Canvas) {
        c.saveLayer(null, null)
        unifiedPath.reset()
        levelPath.reset()
        levelRect.set(fillRect)
        val fillFraction = batteryLevel / 100f
        val fillTop = if (batteryLevel >= 95) fillRect.right
        else fillRect.right - (fillRect.width() * (1 - fillFraction))

        levelRect.right = floor(fillTop.toDouble()).toFloat()
        //levelPath.addRect(levelRect, Path.Direction.CCW)
        levelPath.addRoundRect(
            levelRect, floatArrayOf(
                4.0f, 4.0f, 4.0f, 4.0f, 4.0f, 4.0f, 4.0f, 4.0f
            ), Path.Direction.CCW
        )

        // The perimeter should never change
        unifiedPath.addPath(scaledPerimeter)
        // If drawing dual tone, the level is used only to clip the whole drawable path
        unifiedPath.op(levelPath, Path.Op.UNION)

        fillPaint.color = levelColor

        // Deal with unifiedPath clipping before it draws
        if (charging) {
            // Clip out the bolt shape
            unifiedPath.op(scaledBolt, Path.Op.DIFFERENCE)
            levelPath.op(scaledBolt, Path.Op.DIFFERENCE)
            if (!invertFillIcon) {
                c.drawPath(scaledBolt, fillPaint)
            }
        }

        // Non dual-tone means we draw the perimeter (with the level fill), and potentially
        // draw the fill again with a critical color
        c.drawPath(unifiedPath, dualToneBackgroundFill)
        fillPaint.color = levelColor
        c.drawPath(levelPath, fillPaint)

        if (charging) {
            val xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_OUT)
            fillColorStrokePaint.xfermode = xfermode

            c.drawPath(scaledBolt, fillColorStrokePaint)

            fillPaint.color = boltColor
            c.drawPath(scaledBolt, fillPaint)
            fillPaint.color = levelColor
        }
        c.restore()
    }

    private fun batteryColorForLevel(level: Int): Int {
        return when {
            charging -> 0xFF34C759.toInt()
            powerSaveEnabled -> 0xFFFFCC0A.toInt()
            level > 20 -> fillColor
            level >= 0 -> 0xFFFF3B30.toInt()
            else -> getColorForLevel(level)
        }
    }

    private fun getColorForLevel(level: Int): Int {
        var thresh: Int
        var color = 0
        var i = 0
        while (i < colorLevels.size) {
            thresh = colorLevels[i]
            color = colorLevels[i + 1]
            if (level <= thresh) {

                // Respect tinting for "normal" level
                return if (i == colorLevels.size - 2) {
                    fillColor
                } else {
                    color
                }
            }
            i += 2
        }
        return color
    }

    /**
     * Alpha is unused internally, and should be defined in the colors passed to {@link setColors}.
     * Further, setting an alpha for a dual tone battery meter doesn't make sense without bounds
     * defining the minimum background fill alpha. This is because fill + background must be equal
     * to the net alpha passed in here.
     */
    override fun setAlpha(alpha: Int) {
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        fillPaint.colorFilter = colorFilter
        fillColorStrokePaint.colorFilter = colorFilter
        dualToneBackgroundFill.colorFilter = colorFilter
    }

    /**
     * Deprecated, but required by Drawable
     */
    @Deprecated(
        "Deprecated in Java",
        ReplaceWith("PixelFormat.OPAQUE", "android.graphics.PixelFormat"),
    )
    override fun getOpacity(): Int {
        return PixelFormat.OPAQUE
    }

    override fun getIntrinsicHeight(): Int {
        return intrinsicHeight
    }

    override fun getIntrinsicWidth(): Int {
        return intrinsicWidth
    }

    /**
     * Set the fill level
     */
    @Suppress("PARAMETER_NAME_CHANGED_ON_OVERRIDE")
    override fun setBatteryLevel(l: Int) {
        invertFillIcon = if (l >= 67) true else if (l <= 33) false else invertFillIcon
        batteryLevel = l
        levelColor = batteryColorForLevel(batteryLevel)
        invalidateSelf()
    }

    fun getBatteryLevel(): Int {
        return batteryLevel
    }

    override fun onBoundsChange(bounds: Rect) {
        super.onBoundsChange(bounds)
        updateSize()
    }

    fun setPadding(left: Int, top: Int, right: Int, bottom: Int) {
        padding.left = left
        padding.top = top
        padding.right = right
        padding.bottom = bottom

        updateSize()
    }

    override fun setColors(fgColor: Int, bgColor: Int, singleToneColor: Int) {
        fillColor = fgColor
        boltColor = singleToneColor

        fillPaint.color = singleToneColor
        fillColorStrokePaint.color = singleToneColor

        backgroundColor = bgColor
        dualToneBackgroundFill.color = bgColor

        // Also update the level color, since fillColor may have changed
        levelColor = batteryColorForLevel(batteryLevel)

        invalidateSelf()
    }

    private fun postInvalidate() {
        unscheduleSelf(invalidateRunnable)
        scheduleSelf(invalidateRunnable, 0)
    }

    private fun updateSize() {
        val b = bounds
        if (b.isEmpty) {
            scaleMatrix.setScale(1f, 1f)
        } else {
            scaleMatrix.setScale((b.right / WIDTH), (b.bottom / HEIGHT))
        }

        perimeterPath.transform(scaleMatrix, scaledPerimeter)
        errorPerimeterPath.transform(scaleMatrix, scaledErrorPerimeter)
        fillMask.transform(scaleMatrix, scaledFill)
        scaledFill.computeBounds(fillRect, true)
        boltPath.transform(scaleMatrix, scaledBolt)
        plusPath.transform(scaleMatrix, scaledPlus)

        // It is expected that this view only ever scale by the same factor in each dimension, so
        // just pick one to scale the strokeWidths
        val scaledStrokeWidth =
            (b.right / WIDTH * PROTECTION_STROKE_WIDTH).coerceAtLeast(PROTECTION_MIN_STROKE_WIDTH)

        fillColorStrokePaint.strokeWidth = scaledStrokeWidth
        fillColorStrokeProtection.strokeWidth = scaledStrokeWidth
    }

    @SuppressLint("RestrictedApi")
    private fun loadPaths() {
        val pathString =
            if (xposed) modRes.getString(R.string.config_landscapeBatteryPerimeteriOS15)
            else context.getString(R.string.config_landscapeBatteryPerimeteriOS15)
        perimeterPath.set(PathParser.createPathFromPathData(pathString))
        perimeterPath.computeBounds(RectF(), true)

        val errorPathString =
            if (xposed) modRes.getString(R.string.config_landscapeBatteryErroriOS15)
            else context.getString(R.string.config_landscapeBatteryErroriOS15)
        errorPerimeterPath.set(PathParser.createPathFromPathData(errorPathString))
        errorPerimeterPath.computeBounds(RectF(), true)

        val fillMaskString =
            if (xposed) modRes.getString(R.string.config_landscapeBatteryFillMaskiOS15)
            else context.getString(R.string.config_landscapeBatteryFillMaskiOS15)
        fillMask.set(PathParser.createPathFromPathData(fillMaskString))
        // Set the fill rect so we can calculate the fill properly
        fillMask.computeBounds(fillRect, true)

        val boltPathString =
            if (xposed) modRes.getString(R.string.config_landscapeBatteryBoltiOS15)
            else context.getString(R.string.config_landscapeBatteryBoltiOS15)
        boltPath.set(PathParser.createPathFromPathData(boltPathString))

        val plusPathString =
            if (xposed) modRes.getString(R.string.config_landscapeBatteryPlusiOS15)
            else context.getString(R.string.config_landscapeBatteryPlusiOS15)
        plusPath.set(PathParser.createPathFromPathData(plusPathString))

        dualTone = false
    }

    companion object {
        private val TAG = LandscapeBatteryiOS15::class.java.simpleName
        private const val WIDTH = 24f
        private const val HEIGHT = 12f
        private const val CRITICAL_LEVEL = 20

        // On a 12x20 grid, how wide to make the fill protection stroke.
        // Scales when our size changes
        private const val PROTECTION_STROKE_WIDTH = 3f

        // Arbitrarily chosen for visibility at small sizes
        private const val PROTECTION_MIN_STROKE_WIDTH = 6f
    }
}