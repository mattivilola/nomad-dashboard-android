package com.iloapps.nomaddashboard.feature.visited

import android.content.Context
import androidx.annotation.RawRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.iloapps.nomaddashboard.feature.visited.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

internal data class VisitedMapPolygonShape(
    val outerRing: List<VisitedMapCoordinate>,
    val holes: List<List<VisitedMapCoordinate>>,
)

internal data class VisitedMapCountryShape(
    val countryCode: String,
    val polygons: List<VisitedMapPolygonShape>,
    val bounds: VisitedMapBounds,
)

internal data class VisitedMapCountryStyle(
    val fillColor: Color,
    val strokeColor: Color,
    val strokeWidth: Float,
)

@Composable
internal fun rememberVisitedCountryShapes(): List<VisitedMapCountryShape>? {
    val context = LocalContext.current
    var shapes by remember(context) { mutableStateOf<List<VisitedMapCountryShape>?>(null) }

    LaunchedEffect(context) {
        shapes = VisitedCountryShapesLoader.load(context)
    }

    return shapes
}

internal object VisitedCountryShapesLoader {
    @Volatile
    private var cachedShapes: List<VisitedMapCountryShape>? = null

    suspend fun load(context: Context): List<VisitedMapCountryShape> {
        cachedShapes?.let { return it }

        val loaded = withContext(Dispatchers.Default) {
            parseCountryShapes(context = context, resourceId = R.raw.world_country_shapes)
        }
        cachedShapes = loaded
        return loaded
    }

    private fun parseCountryShapes(
        context: Context,
        @RawRes resourceId: Int,
    ): List<VisitedMapCountryShape> {
        val rawJson = context.resources.openRawResource(resourceId)
            .bufferedReader()
            .use { it.readText() }
        val rootObject = JSONObject(rawJson)
        val features = rootObject.optJSONArray("features") ?: return emptyList()

        return buildList(features.length()) {
            for (index in 0 until features.length()) {
                val feature = features.optJSONObject(index) ?: continue
                val countryCode = feature.countryCode() ?: continue
                val geometry = feature.optJSONObject("geometry") ?: continue
                val polygons = geometry.polygons() ?: continue
                val bounds = polygons.mapNotNull { polygon ->
                    VisitedMapBounds.fromCoordinates(polygon.outerRing)
                }.reduceOrNull(VisitedMapBounds::union) ?: continue

                add(
                    VisitedMapCountryShape(
                        countryCode = countryCode,
                        polygons = polygons,
                        bounds = bounds,
                    ),
                )
            }
        }
    }
}

private fun JSONObject.countryCode(): String? {
    val properties = optJSONObject("properties") ?: return null
    val candidates = listOf("ISO_A2", "ISO_A2_EH", "WB_A2", "POSTAL")
    return candidates.asSequence()
        .mapNotNull { key -> properties.optString(key).trim().takeIf { it.length == 2 && it != "-99" } }
        .firstOrNull()
        ?.uppercase()
}

private fun JSONObject.polygons(): List<VisitedMapPolygonShape>? {
    val geometryType = optString("type")
    val coordinates = optJSONArray("coordinates") ?: return null

    return when (geometryType) {
        "Polygon" -> listOfNotNull(coordinates.toPolygonShape())
        "MultiPolygon" -> buildList(coordinates.length()) {
            for (index in 0 until coordinates.length()) {
                val polygonCoordinates = coordinates.optJSONArray(index) ?: continue
                polygonCoordinates.toPolygonShape()?.let(::add)
            }
        }
        else -> null
    }?.takeIf(List<VisitedMapPolygonShape>::isNotEmpty)
}

private fun JSONArray.toPolygonShape(): VisitedMapPolygonShape? {
    val outerRing = optJSONArray(0)?.toRingCoordinates() ?: return null
    val holes = buildList {
        for (index in 1 until length()) {
            optJSONArray(index)?.toRingCoordinates()?.let(::add)
        }
    }
    return VisitedMapPolygonShape(
        outerRing = outerRing,
        holes = holes,
    )
}

private fun JSONArray.toRingCoordinates(): List<VisitedMapCoordinate>? {
    val coordinates = buildList(length()) {
        for (index in 0 until length()) {
            val point = optJSONArray(index) ?: continue
            val longitude = point.optDouble(0, Double.NaN)
            val latitude = point.optDouble(1, Double.NaN)
            if (latitude.isNaN() || longitude.isNaN()) {
                continue
            }
            add(VisitedMapCoordinate(latitude = latitude, longitude = longitude))
        }
    }

    if (coordinates.size < 3) {
        return null
    }

    return coordinates.trimClosingCoordinate()
}

private fun List<VisitedMapCoordinate>.trimClosingCoordinate(): List<VisitedMapCoordinate> =
    if (size > 1 && first() == last()) dropLast(1) else this
