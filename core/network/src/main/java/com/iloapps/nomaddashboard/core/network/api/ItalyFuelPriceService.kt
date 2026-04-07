package com.iloapps.nomaddashboard.core.network.api

import okhttp3.ResponseBody
import retrofit2.http.GET

interface ItalyFuelPriceService {
    @GET("images/exportCSV/anagrafica_impianti_attivi.csv")
    suspend fun stationCatalog(): ResponseBody

    @GET("images/exportCSV/prezzo_alle_8.csv")
    suspend fun dailyPrices(): ResponseBody
}
