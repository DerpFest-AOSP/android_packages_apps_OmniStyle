/*
 * SPDX-FileCopyrightText: DerpFest AOSP
 * SPDX-License-Identifier: Apache-2.0
 */
package org.omnirom.omnistyle

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.provider.Settings
import android.util.Log
import android.util.LruCache

/** One installed header pack. [key] matches the value Settings stores for a daylight pack. */
data class HeaderPack(
    val label: String,
    val packageName: String,
    /** Activity name for a DaylightHeaderPack1 entry. Null loads daylight_header.xml. */
    val configName: String?,
) {
    val key: String
        get() = if (configName == null) packageName else "$packageName/$configName"
}

data class HeaderEntry(
    val image: String,
    val name: String?,
    val hour: Int,
    val day: Int,
    val month: Int,
)

class LoadedPack(
    val pack: HeaderPack,
    val resources: Resources?,
    val creator: String?,
    val headers: List<HeaderEntry>,
)

/** Header currently applied by the static provider, stored as "package/image". */
data class AppliedHeader(
    val packageName: String,
    val image: String,
    /** Pack whose config actually contains [image], when one could be found. */
    val packKey: String?,
)

object HeaderRepository {
    private const val TAG = "OmniStyleHeaders"
    private const val ACTION_HEADER_PACK = "org.omnirom.DaylightHeaderPack"
    private const val ACTION_HEADER_PACK_1 = "org.omnirom.DaylightHeaderPack1"
    private const val PROVIDER_STATIC = "static"

    private val thumbCache = object : LruCache<String, Bitmap>(
        (Runtime.getRuntime().maxMemory() / 8).toInt()
    ) {
        override fun sizeOf(key: String, value: Bitmap) = value.allocationByteCount
    }

    fun queryPacks(context: Context): List<HeaderPack> {
        val packageManager = context.packageManager
        // Keyed by label, as the previous picker did, so a repeated label collapses to one row.
        val byLabel = LinkedHashMap<String, HeaderPack>()
        query(packageManager, ACTION_HEADER_PACK).forEach { info ->
            val packageName = info.activityInfo.packageName
            val label = info.activityInfo.loadLabel(packageManager)?.toString()?.takeIf { it.isNotBlank() }
                ?: packageName
            byLabel[label] = HeaderPack(label, packageName, null)
        }
        query(packageManager, ACTION_HEADER_PACK_1).forEach { info ->
            val packageName = info.activityInfo.packageName
            val label = info.activityInfo.loadLabel(packageManager)?.toString()?.takeIf { it.isNotBlank() }
                ?: packageName
            byLabel[label] = HeaderPack(label, packageName, info.activityInfo.name)
        }
        return byLabel.values.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.label })
    }

    fun loadPack(context: Context, pack: HeaderPack): LoadedPack {
        return try {
            val resources = context.packageManager.getResourcesForApplication(pack.packageName)
            val infos = ArrayList<HeaderUtils.DaylightHeaderInfo>()
            val creator = HeaderUtils.loadHeaders(resources, pack.configName, infos)
            val headers = infos.mapNotNull { info ->
                val image = info.mImage ?: return@mapNotNull null
                HeaderEntry(
                    image = image,
                    name = info.mName?.takeIf { it.isNotBlank() },
                    hour = info.mHour,
                    day = info.mDay,
                    month = info.mMonth,
                )
            }
            LoadedPack(pack, resources, creator?.takeIf { it.isNotBlank() }, headers)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load header pack ${pack.key}", e)
            LoadedPack(pack, null, null, emptyList())
        }
    }

    fun resourcesFor(context: Context, packageName: String): Resources? {
        return try {
            context.packageManager.getResourcesForApplication(packageName)
        } catch (e: PackageManager.NameNotFoundException) {
            null
        }
    }

    /**
     * Which pack to open, and which pack holds the applied image.
     * A requested key wins for the screen. Several packs share this app's package, so the
     * package name alone does not identify the image.
     */
    fun initialSelection(
        context: Context,
        packs: List<HeaderPack>,
        preferredKey: String?,
        applied: AppliedHeader?,
    ): Pair<String?, String?> {
        val appliedPack = if (applied == null) {
            null
        } else {
            packContaining(context, packs, applied.packageName, applied.image)
        }
        val preferred = preferredKey?.let { key -> packs.firstOrNull { it.key == key }?.key }
        val selected = preferred ?: appliedPack ?: packs.firstOrNull()?.key
        return selected to appliedPack
    }

    fun packContaining(
        context: Context,
        packs: List<HeaderPack>,
        packageName: String,
        image: String,
    ): String? {
        for (pack in packs) {
            if (pack.packageName != packageName) continue
            if (loadPack(context, pack).headers.any { it.image == image }) return pack.key
        }
        return null
    }

    /** Decodes a header drawable down to about [reqWidth] pixels. Safe to call off the main thread. */
    fun loadThumbnail(resources: Resources, packageName: String, image: String, reqWidth: Int): Bitmap? {
        if (reqWidth <= 0) return null
        val cacheKey = "$packageName/$image@$reqWidth"
        thumbCache.get(cacheKey)?.let { return it }

        val resId = resources.getIdentifier(image, "drawable", packageName)
        if (resId == 0) return null

        val bitmap = try {
            decodeBitmap(resources, resId, reqWidth) ?: drawDrawable(resources, resId, reqWidth)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to decode $packageName/$image", e)
            null
        }
        bitmap?.let { thumbCache.put(cacheKey, it) }
        return bitmap
    }

    fun appliedHeader(context: Context): AppliedHeader? {
        val resolver = context.contentResolver
        val provider = Settings.System.getString(
            resolver, Settings.System.STATUS_BAR_CUSTOM_HEADER_PROVIDER
        )
        if (provider != PROVIDER_STATIC) return null
        val value = Settings.System.getString(
            resolver, Settings.System.STATUS_BAR_CUSTOM_HEADER_IMAGE
        ) ?: return null
        val separator = value.indexOf('/')
        if (separator <= 0 || separator == value.lastIndex) return null
        return AppliedHeader(
            packageName = value.substring(0, separator),
            image = value.substring(separator + 1),
            packKey = null,
        )
    }

    fun applyHeader(context: Context, packageName: String, image: String) {
        val resolver = context.contentResolver
        Settings.System.putString(
            resolver,
            Settings.System.STATUS_BAR_CUSTOM_HEADER_IMAGE,
            "$packageName/$image",
        )
        Settings.System.putString(
            resolver,
            Settings.System.STATUS_BAR_CUSTOM_HEADER_PROVIDER,
            PROVIDER_STATIC,
        )
    }

    private fun query(packageManager: PackageManager, action: String) =
        packageManager.queryIntentActivities(Intent(action), 0)

    private fun decodeBitmap(resources: Resources, resId: Int, reqWidth: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeResource(resources, resId, bounds)
        if (bounds.outWidth <= 0) return null
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= reqWidth) sample *= 2
        return BitmapFactory.decodeResource(
            resources,
            resId,
            BitmapFactory.Options().apply {
                inSampleSize = sample
                inScaled = false
            },
        )
    }

    private fun drawDrawable(resources: Resources, resId: Int, reqWidth: Int): Bitmap? {
        val drawable = resources.getDrawable(resId, null) ?: return null
        val width = drawable.intrinsicWidth.takeIf { it > 0 } ?: reqWidth
        val height = drawable.intrinsicHeight.takeIf { it > 0 } ?: (reqWidth / 3).coerceAtLeast(1)
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, canvas.width, canvas.height)
        drawable.draw(canvas)
        return bitmap
    }
}
