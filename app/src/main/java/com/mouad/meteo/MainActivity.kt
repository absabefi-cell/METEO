package com.mouad.meteo

import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.URL
import java.net.URLEncoder
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private val cle = BuildConfig.OWM_KEY
    private val langue = if (Locale.getDefault().language == "ar") "ar" else "fr"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val champ = EditText(this).apply {
            hint = if (langue == "ar") "المدينة" else "Ville"
            setText("Casablanca")
        }
        val bouton = Button(this).apply {
            text = if (langue == "ar") "بحث" else "Chercher"
        }
        val resultat = TextView(this).apply {
            textSize = 22f
            setPadding(0, 40, 0, 0)
        }

        val ecran = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 96, 48, 48)
            addView(champ)
            addView(bouton)
            addView(resultat)
        }
        setContentView(ecran)

        bouton.setOnClickListener {
            val ville = champ.text.toString().trim()
            if (ville.isEmpty()) return@setOnClickListener
            resultat.text = "..."
            lifecycleScope.launch {
                resultat.text = try {
                    chercherMeteo(ville)
                } catch (e: Exception) {
                    "Erreur : ${e.message}"
                }
            }
        }
    }

    private suspend fun chercherMeteo(ville: String): String = withContext(Dispatchers.IO) {
        val v = URLEncoder.encode(ville, "UTF-8")
        val url = "https://api.openweathermap.org/data/2.5/weather?q=$v&units=metric&lang=$langue&appid=$cle"
        val json = JSONObject(URL(url).readText())

        val nom = json.getString("name")
        val main = json.getJSONObject("main")
        val temp = Math.round(main.getDouble("temp"))
        val ressenti = Math.round(main.getDouble("feels_like"))
        val humidite = main.getInt("humidity")
        val desc = json.getJSONArray("weather").getJSONObject(0).getString("description")
        val vent = Math.round(json.getJSONObject("wind").getDouble("speed") * 3.6)

        if (langue == "ar")
            "$nom\n$temp°C\n$desc\nالإحساس: $ressenti°C\nالرطوبة: $humidite%\nالرياح: $vent كم/س"
        else
            "$nom\n$temp°C\n$desc\nRessenti : $ressenti°C\nHumidité : $humidite %\nVent : $vent km/h"
    }
}
