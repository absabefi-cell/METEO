package com.mouad.meteo

import android.annotation.SuppressLint
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.MapTileProviderBasic
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.tileprovider.tilesource.XYTileSource
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.TilesOverlay
import java.net.URL
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private val cle = BuildConfig.OWM_KEY
    private val ar = Locale.getDefault().language == "ar"
    private val langue = if (ar) "ar" else "fr"

    private lateinit var carte: MapView
    private var couche: TilesOverlay? = null
    private lateinit var actuel: TextView
    private lateinit var listeJours: LinearLayout

    private fun t(fr: String, arabe: String) = if (ar) arabe else fr
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Configuration.getInstance().userAgentValue = packageName

        val champ = EditText(this).apply {
            hint = t("Ville", "المدينة")
            setText("Casablanca")
            setSingleLine()
        }
        val bouton = Button(this).apply { text = t("Chercher", "بحث") }

        actuel = TextView(this).apply {
            textSize = 20f
            setPadding(0, dp(16), 0, dp(16))
        }

        carte = MapView(this).apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            controller.setZoom(6.0)
            controller.setCenter(GeoPoint(33.57, -7.59))
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(320))
            setOnTouchListener { v, _ ->
                v.parent.requestDisallowInterceptTouchEvent(true)
                false
            }
        }

        val couches = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        listOf(
            t("Pluie", "أمطار") to "precipitation_new",
            t("Nuages", "غيوم") to "clouds_new",
            t("Temp.", "حرارة") to "temp_new",
            t("Vent", "رياح") to "wind_new"
        ).forEach { (nom, id) ->
            couches.addView(Button(this).apply {
                text = nom
                textSize = 12f
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                setOnClickListener { changerCouche(id) }
            })
        }

        val titreJours = TextView(this).apply {
            text = t("Prévisions 16 jours", "توقعات 16 يومًا")
            textSize = 20f
            setTypeface(null, Typeface.BOLD)
            setPadding(0, dp(20), 0, dp(8))
        }
        listeJours = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }

        val credits = TextView(this).apply {
            text = "© OpenStreetMap · OpenWeatherMap · Open-Meteo"
            textSize = 11f
            setPadding(0, dp(16), 0, 0)
        }

        val contenu = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(32), dp(16), dp(32))
            addView(champ)
            addView(bouton)
            addView(actuel)
            addView(carte)
            addView(couches)
            addView(titreJours)
            addView(listeJours)
            addView(credits)
        }
        setContentView(ScrollView(this).apply { addView(contenu) })

        changerCouche("precipitation_new")

        bouton.setOnClickListener {
            val ville = champ.text.toString().trim()
            if (ville.isNotEmpty()) charger(ville)
        }
        charger("Casablanca")
    }

    private fun changerCouche(id: String) {
        couche?.let { carte.overlays.remove(it) }
        val source = XYTileSource(
            id, 0, 18, 256, ".png?appid=$cle",
            arrayOf("https://tile.openweathermap.org/map/$id/")
        )
        val nouvelle = TilesOverlay(MapTileProviderBasic(applicationContext, source), this).apply {
            loadingBackgroundColor = Color.TRANSPARENT
            loadingLineColor = Color.TRANSPARENT
        }
        carte.overlays.add(nouvelle)
        couche = nouvelle
        carte.invalidate()
    }

    private fun charger(ville: String) {
        actuel.text = "..."
        listeJours.removeAllViews()
        lifecycleScope.launch {
            try {
                val json = withContext(Dispatchers.IO) {
                    val v = URLEncoder.encode(ville, "UTF-8")
                    JSONObject(URL("https://api.openweathermap.org/data/2.5/weather?q=$v&units=metric&lang=$langue&appid=$cle").readText())
                }
                val main = json.getJSONObject("main")
                val desc = json.getJSONArray("weather").getJSONObject(0).getString("description")
                val vent = Math.round(json.getJSONObject("wind").getDouble("speed") * 3.6)

                actuel.text = "${json.getString("name")}   ${Math.round(main.getDouble("temp"))}°C\n$desc\n" +
                    t("Ressenti", "الإحساس") + " : ${Math.round(main.getDouble("feels_like"))}°C   " +
                    t("Humidité", "الرطوبة") + " : ${main.getInt("humidity")} %\n" +
                    t("Vent", "الرياح") + " : $vent " + t("km/h", "كم/س")

                val coord = json.getJSONObject("coord")
                val lat = coord.getDouble("lat")
                val lon = coord.getDouble("lon")
                carte.controller.setZoom(7.0)
                carte.controller.animateTo(GeoPoint(lat, lon))
                afficherJours(lat, lon)
            } catch (e: Exception) {
                actuel.text = t("Erreur : ", "خطأ: ") + e.message
            }
        }
    }

    private suspend fun afficherJours(lat: Double, lon: Double) {
        val json = withContext(Dispatchers.IO) {
            JSONObject(URL(
                "https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon" +
                "&daily=weather_code,temperature_2m_max,temperature_2m_min,precipitation_probability_max" +
                "&timezone=auto&forecast_days=16"
            ).readText())
        }
        val d = json.getJSONObject("daily")
        val dates = d.getJSONArray("time")
        val codes = d.getJSONArray("weather_code")
        val max = d.getJSONArray("temperature_2m_max")
        val min = d.getJSONArray("temperature_2m_min")
        val pluie = d.getJSONArray("precipitation_probability_max")
        val lecture = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val affichage = SimpleDateFormat("EEE d MMM", Locale(langue))

        for (i in 0 until dates.length()) {
            val jour = affichage.format(lecture.parse(dates.getString(i))!!)
            val (icone, texte) = decrire(codes.optInt(i, -1))
            val p = if (pluie.isNull(i)) "-" else "${pluie.getInt(i)} %"
            listeJours.addView(TextView(this).apply {
                textSize = 16f
                setPadding(0, dp(6), 0, dp(6))
                text = "$icone  $jour   ${Math.round(max.optDouble(i))}° / ${Math.round(min.optDouble(i))}°   💧 $p\n        $texte"
            })
        }
    }

    private fun decrire(code: Int): Pair<String, String> = when (code) {
        0 -> "☀️" to t("Ensoleillé", "مشمس")
        1, 2 -> "🌤️" to t("Peu nuageux", "غائم جزئيًا")
        3 -> "☁️" to t("Couvert", "غائم")
        45, 48 -> "🌫️" to t("Brouillard", "ضباب")
        in 51..57 -> "🌦️" to t("Bruine", "رذاذ")
        in 61..67, in 80..82 -> "🌧️" to t("Pluie", "أمطار")
        in 71..77, 85, 86 -> "❄️" to t("Neige", "ثلوج")
        in 95..99 -> "⛈️" to t("Orage", "عاصفة رعدية")
        else -> "🌡️" to "-"
    }

    override fun onResume() {
        super.onResume()
        carte.onResume()
    }

    override fun onPause() {
        super.onPause()
        carte.onPause()
    }
}
