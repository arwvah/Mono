package com.mono.fitness.util

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import com.mono.fitness.data.Activity
import com.mono.fitness.data.ActivityPoint
import java.io.File
import java.io.FileOutputStream

/**
 * Strava-style share card: black/white route silhouette + stat block, no map tiles.
 */
object ShareCardExporter {

    fun render(
        activity: Activity,
        points: List<ActivityPoint>,
        width: Int = 1080,
        height: Int = 1350
    ): Bitmap {
        // Transparent canvas — route is a B/W silhouette (no map tiles), stats on a white block.
        val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        canvas.drawColor(Color.TRANSPARENT)

        val pad = width * 0.08f
        val mapBottom = height * 0.58f
        val routePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            style = Paint.Style.STROKE
            strokeWidth = width * 0.012f
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }
        val routeGlow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            style = Paint.Style.STROKE
            strokeWidth = width * 0.022f
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }
        val startPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            style = Paint.Style.FILL
        }
        val startRing = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            style = Paint.Style.FILL
        }

        if (points.size >= 2) {
            val lats = points.map { it.latitude }
            val lngs = points.map { it.longitude }
            val minLat = lats.min()
            val maxLat = lats.max()
            val minLng = lngs.min()
            val maxLng = lngs.max()
            val latSpan = (maxLat - minLat).coerceAtLeast(1e-6).toFloat()
            val lngSpan = (maxLng - minLng).coerceAtLeast(1e-6).toFloat()
            val usableW = width - 2 * pad
            val usableH = mapBottom - pad * 2
            val scale = minOf(usableW / lngSpan, usableH / latSpan) * 0.9f
            val pathW = lngSpan * scale
            val pathH = latSpan * scale
            val ox = (width - pathW) / 2f
            val oy = pad + (usableH - pathH) / 2f

            fun mapX(lng: Double): Float =
                ox + ((lng - minLng).toFloat() / lngSpan * pathW)
            fun mapY(lat: Double): Float =
                oy + ((maxLat - lat).toFloat() / latSpan * pathH)

            val path = Path()
            points.forEachIndexed { i, p ->
                val x = mapX(p.longitude)
                val y = mapY(p.latitude)
                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            // White understroke so silhouette reads on dark share targets
            canvas.drawPath(path, routeGlow)
            canvas.drawPath(path, routePaint)
            val first = points.first()
            val r = width * 0.018f
            canvas.drawCircle(mapX(first.longitude), mapY(first.latitude), r * 1.35f, startRing)
            canvas.drawCircle(mapX(first.longitude), mapY(first.latitude), r, startPaint)
        } else {
            val t = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.BLACK
                textSize = width * 0.05f
                textAlign = Paint.Align.CENTER
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            }
            canvas.drawText("No GPS track", width / 2f, mapBottom / 2f, t)
        }

        // Stat block (opaque white card at bottom — Strava-style)
        val blockTop = mapBottom
        val blockPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            style = Paint.Style.FILL
        }
        val blockRadius = width * 0.04f
        canvas.drawRoundRect(
            0f, blockTop, width.toFloat(), height.toFloat(),
            blockRadius, blockRadius, blockPaint
        )

        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            textSize = width * 0.055f
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        }
        val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            alpha = 140
            textSize = width * 0.032f
        }
        val valuePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            textSize = width * 0.048f
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        }

        var y = blockTop + pad * 0.9f
        val title = activity.title.ifBlank { activity.type.replaceFirstChar { it.titlecase() } }
        canvas.drawText(title, pad, y, titlePaint)
        y += width * 0.045f
        canvas.drawText(Formatters.date(activity.startTimeMillis), pad, y, labelPaint)

        y += width * 0.08f
        val cols = listOf(
            "Distance" to Formatters.distanceKm(activity.distanceMeters),
            "Pace" to Formatters.paceFromActivity(activity.distanceMeters, activity.movingTimeMillis),
            "Time" to Formatters.duration(activity.durationMillis),
            "Elev" to Formatters.elevation(activity.elevationGainMeters)
        )
        val colW = (width - 2 * pad) / 2f
        cols.forEachIndexed { i, (label, value) ->
            val col = i % 2
            val row = i / 2
            val cx = pad + col * colW
            val cy = y + row * width * 0.12f
            canvas.drawText(label, cx, cy, labelPaint)
            canvas.drawText(value, cx, cy + width * 0.05f, valuePaint)
        }

        val brand = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            alpha = 100
            textSize = width * 0.028f
            textAlign = Paint.Align.RIGHT
        }
        canvas.drawText("Mono", width - pad, height - pad * 0.6f, brand)

        return bmp
    }

    fun saveToGallery(context: Context, bitmap: Bitmap, displayName: String): Uri? {
        val name = if (displayName.endsWith(".png")) displayName else "$displayName.png"
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, name)
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/Mono")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
        }
        val resolver = context.contentResolver
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return null
        resolver.openOutputStream(uri)?.use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            values.clear()
            values.put(MediaStore.Images.Media.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
        }
        return uri
    }

    fun share(context: Context, bitmap: Bitmap, fileName: String = "mono_share.png") {
        val dir = File(context.cacheDir, "share").also { it.mkdirs() }
        val file = File(dir, fileName)
        FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        val uri = FileProvider.getUriForFile(
            context,
            context.packageName + ".fileprovider",
            file
        )
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Share activity"))
    }
}
