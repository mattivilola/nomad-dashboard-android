package com.iloapps.nomaddashboard.core.travel.safety

// Bundled, offline emergency phone numbers.
//
// Safety-critical data: a wrong number is worse than none, so a country is only listed when its
// numbers agree across an official source and at least one independent compilation. Countries
// that could not be verified are deliberately absent (the lookup returns null), e.g. Laos (3-digit
// vs 4-digit forms) and Guatemala (no unified number; fire/ambulance split across volunteer
// brigades).
//
// Verified 2026-10
//
// Sources:
//  - EU 112: https://digital-strategy.ec.europa.eu/en/policies/112
//  - Austria: https://www.oesterreich.gv.at/en/themen/notfaelle_unfaelle_und_kriminalitaet/notrufnummern
//  - France: https://www.service-public.fr (numeros d'urgence 15/17/18/112)
//  - Switzerland: https://www.ch.ch/en/emergencies-and-danger/
//  - Greece: https://www.visitgreece.gr/before-you-travel/health-safety
//  - Czech Republic: https://portal.gov.cz/en/sluzby-vs/112-150-155-and-158-S2824
//  - Croatia: https://hakom.hr/en/112-single-european-emergency-call-number/184
//  - Slovenia: https://www.gov.si/teme/klic-v-sili/
//  - Luxembourg: https://guichet.public.lu/en/citoyens/sante/services-urgence/appel-urgence.html
//  - Belgium: https://securitecivile.be/en/what-do-emergency-centres-112-do
//  - Ireland: https://www.gov.ie/en/service/89da6-how-to-contact-emergency-services-in-ireland
//  - United Kingdom: https://www.gov.uk/guidance/999-and-112-the-uks-national-emergency-numbers
//  - Slovakia: https://www.slovensko.sk (national emergency numbers) and UNHCR Slovakia help pages
//  - Georgia: https://112.gov.ge
//  - Albania: Albanian 112 service paper (Transport Community) and ITU E.129 database
//  - Turkey: Ministry of Interior unification of emergency lines under 112 (2021)
//  - Ukraine: https://mvs.gov.ua/en/news (112 introduced; 101-104 remain in service)
//  - Hungary, Poland, Norway, Germany, Bosnia and Herzegovina, Montenegro, North Macedonia, Serbia,
//    Moldova: government travel advice (Canada, UK, Sweden, Australia) cross-checked against
//    https://en.wikipedia.org/wiki/List_of_emergency_telephone_numbers
//  - Americas: https://www.argentina.gob.ar/tema/emergencias (AR), https://www.gob.ec/ecu-911 (EC),
//    https://www.gub.uy (UY), Ley 7566 (CR), Ley 70 de 2019 (PA), IFT/SESNSP 911 migration (MX),
//    CRT Resolucion 087 de 1997 (CO), Infobae/PNP/SAMU listings (PE), Carabineros/Bomberos/SAMU (CL)
//  - Asia-Pacific: https://www.triplezero.gov.au (AU), https://www.govt.nz/browse/law-crime-and-justice/crimes-and-emergencies/111-emergency-service/ (NZ),
//    https://www.scdf.gov.sg (SG), DILG Unified 911 / EO 56 (PH), UK FCDO and Canada travel advice (KH, NP, TW),
//    Vietnam police portal (VN), Kominfo 112 call centre (ID), Sri Lanka Police emergency list (LK),
//    MHA ERSS https://www.mha.gov.in/en/commoncontent/emergency-response-support-system-erss (IN)
//  - Middle East / Africa: https://u.ae/en/information-and-services/justice-safety-and-the-law/handling-emergencies (AE),
//    Public Security Directorate https://jordan.gov.jo (JO), Western Cape Government emergency numbers (ZA)
//  - Morocco: ANRT, the telecoms regulator, https://www.anrt.ma/infos-pratiques/que-sont-les-numeros-speciaux
//    (177 Gendarmerie Royale, 19 Police, 15 Pompiers; reachable from mobile and fixed lines). The
//    Protection Civile (15) also dispatches ambulances. UK FCDO and Canada travel advice list
//    190 (police) and 150 (ambulance/fire) instead; those are alternatives and deliberately not stored.
//  - Rural and highway police (stored in `other`, only where a distinct short number is officially
//    advertised; checked 2026-10 against the official page plus independent listings):
//    Spain, Guardia Civil 062: "La Guardia Civil tiene su propio telefono de emergencias, el 062",
//    https://web.guardiacivil.es/es/colaboracion/planturismoseguro/ServicioAtencionTuristaExtranjero/
//    Brazil, Policia Rodoviaria Federal 191: "O usuario devera entrar em contato pelo telefone 191",
//    https://www.gov.br/pt-br/servicos/comunicar-a-prf-de-ocorrencia-que-possa-alterar-fluidez-ou-seguranca-viaria
//    Thailand, Highway Police 1193: Thai government portal emergency list ("1193 for Highway Police"),
//    https://www.thailand.go.th/issue-focus-detail/009_152
//    Deliberately not stored: France (Gendarmerie uses 17, same as police), Italy (Carabinieri and
//    police both reached via 112), Turkey (Jandarma 156 merged into 112 in 2021), Greece (10400 is the
//    ELPA breakdown service), Portugal (GNR has no separate emergency number; 112), Romania (no
//    separate Jandarmeria emergency number; 112), Argentina (Gendarmeria only has an 0800 complaint
//    line), Mexico (088 is the Guardia Nacional citizen-attention line, emergencies are 911),
//    Colombia (#767 is the INVIAS road information line).
//  - Cross-check for all rows: https://en.wikipedia.org/wiki/List_of_emergency_telephone_numbers

/**
 * An additional emergency service that does not fit police, ambulance or fire, for example a
 * gendarmerie covering rural areas and highways.
 */
data class EmergencyService(
    val name: String,
    val number: String,
)

/** Phone numbers to call in an emergency in one country. */
data class EmergencyNumbers(
    /** The number to call first. Where a country has no all-purpose number this is the police number. */
    val general: String,
    /** Police, only when it differs from [general]. */
    val police: String? = null,
    /** Ambulance, only when it differs from [general]. */
    val ambulance: String? = null,
    /** Fire brigade, only when it differs from [general]. */
    val fire: String? = null,
    /** Further services that do not fit the fields above, such as a rural gendarmerie. */
    val other: List<EmergencyService> = emptyList(),
) {
    /** "Emergency 112": the number to call first, for card subtitles and summaries. */
    val headline: String
        get() = "Emergency $general"

    /**
     * The other numbers worth knowing, only those that differ from [general]:
     * "Police 110 · Ambulance/Fire 119 · Gendarmerie 177". Null when [general] covers everything.
     */
    val othersText: String?
        get() {
            val parts = mutableListOf<String>()
            police?.let { parts += "Police $it" }
            if (ambulance != null && ambulance == fire) {
                parts += "Ambulance/Fire $ambulance"
            } else {
                ambulance?.let { parts += "Ambulance $it" }
                fire?.let { parts += "Fire $it" }
            }
            other.forEach { parts += "${it.name} ${it.number}" }
            return parts.takeIf { it.isNotEmpty() }?.joinToString(" · ")
        }
}

object EmergencyNumberDirectory {
    /** Emergency numbers for an ISO 3166-1 alpha-2 country code (case-insensitive), or null when unknown. */
    fun numbersFor(countryCode: String?): EmergencyNumbers? {
        val normalized = countryCode?.trim()?.uppercase() ?: return null
        return entries[normalized]
    }

    /** Upper-case ISO 3166-1 alpha-2 codes of every country in the directory. */
    val countryCodes: Set<String>
        get() = entries.keys

    private val europeanSingle = EmergencyNumbers(general = "112")

    private val entries: Map<String, EmergencyNumbers> = mapOf(
        // European Union and EEA
        "AT" to EmergencyNumbers(general = "112", police = "133", ambulance = "144", fire = "122"),
        "BE" to EmergencyNumbers(general = "112", police = "101"),
        "BG" to europeanSingle,
        "HR" to EmergencyNumbers(general = "112", police = "192", ambulance = "194", fire = "193"),
        "CY" to europeanSingle,
        "CZ" to EmergencyNumbers(general = "112", police = "158", ambulance = "155", fire = "150"),
        "DK" to europeanSingle,
        "EE" to europeanSingle,
        "FI" to europeanSingle,
        "FR" to EmergencyNumbers(general = "112", police = "17", ambulance = "15", fire = "18"),
        "DE" to EmergencyNumbers(general = "112", police = "110"),
        "GR" to EmergencyNumbers(general = "112", police = "100", ambulance = "166", fire = "199"),
        "HU" to EmergencyNumbers(general = "112", police = "107", ambulance = "104", fire = "105"),
        "IS" to europeanSingle,
        "IE" to europeanSingle,
        "IT" to europeanSingle,
        "LV" to europeanSingle,
        "LI" to EmergencyNumbers(general = "112", police = "117", ambulance = "144", fire = "118"),
        "LT" to europeanSingle,
        "LU" to EmergencyNumbers(general = "112", police = "113"),
        "MT" to europeanSingle,
        "NL" to europeanSingle,
        "NO" to EmergencyNumbers(general = "112", ambulance = "113", fire = "110"),
        "PL" to EmergencyNumbers(general = "112", police = "997", ambulance = "999", fire = "998"),
        "PT" to europeanSingle,
        "RO" to europeanSingle,
        "SK" to EmergencyNumbers(general = "112", police = "158", ambulance = "155", fire = "150"),
        "SI" to EmergencyNumbers(general = "112", police = "113"),
        "ES" to EmergencyNumbers(
            general = "112",
            other = listOf(EmergencyService(name = "Guardia Civil", number = "062")),
        ),
        "SE" to europeanSingle,

        // Other European countries
        "GB" to EmergencyNumbers(general = "999"),
        "CH" to EmergencyNumbers(general = "112", police = "117", ambulance = "144", fire = "118"),
        "TR" to europeanSingle,
        "GE" to europeanSingle,
        "AL" to EmergencyNumbers(general = "112", police = "129", ambulance = "127", fire = "128"),
        "BA" to EmergencyNumbers(general = "112", police = "122", ambulance = "124", fire = "123"),
        "ME" to EmergencyNumbers(general = "112", police = "122", ambulance = "124", fire = "123"),
        "RS" to EmergencyNumbers(general = "112", ambulance = "194", fire = "193"),
        "MK" to EmergencyNumbers(general = "112", police = "192", ambulance = "194", fire = "193"),
        "UA" to EmergencyNumbers(general = "112", police = "102", ambulance = "103", fire = "101"),
        "MD" to europeanSingle,

        // Americas
        "US" to EmergencyNumbers(general = "911"),
        "CA" to EmergencyNumbers(general = "911"),
        "MX" to EmergencyNumbers(general = "911"),
        "CR" to EmergencyNumbers(general = "911"),
        "PA" to EmergencyNumbers(general = "911"),
        "CO" to EmergencyNumbers(general = "123"),
        "PE" to EmergencyNumbers(general = "105", ambulance = "106", fire = "116"),
        "CL" to EmergencyNumbers(general = "133", ambulance = "131", fire = "132"),
        "AR" to EmergencyNumbers(general = "911", fire = "100"),
        "BR" to EmergencyNumbers(
            general = "190",
            ambulance = "192",
            fire = "193",
            other = listOf(EmergencyService(name = "Federal Highway Police", number = "191")),
        ),
        "EC" to EmergencyNumbers(general = "911"),
        "UY" to EmergencyNumbers(general = "911"),

        // Asia-Pacific
        "TH" to EmergencyNumbers(
            general = "191",
            ambulance = "1669",
            fire = "199",
            other = listOf(EmergencyService(name = "Highway Police", number = "1193")),
        ),
        "VN" to EmergencyNumbers(general = "113", ambulance = "115", fire = "114"),
        "ID" to EmergencyNumbers(general = "112", police = "110", fire = "113"),
        "MY" to EmergencyNumbers(general = "999"),
        "SG" to EmergencyNumbers(general = "999", ambulance = "995", fire = "995"),
        "PH" to EmergencyNumbers(general = "911"),
        "JP" to EmergencyNumbers(general = "110", ambulance = "119", fire = "119"),
        "KR" to EmergencyNumbers(general = "112", ambulance = "119", fire = "119"),
        "TW" to EmergencyNumbers(general = "110", ambulance = "119", fire = "119"),
        "IN" to EmergencyNumbers(general = "112"),
        "LK" to EmergencyNumbers(general = "119", ambulance = "1990", fire = "110"),
        "NP" to EmergencyNumbers(general = "100", ambulance = "102", fire = "101"),
        "KH" to EmergencyNumbers(general = "117", ambulance = "119", fire = "118"),
        "AU" to EmergencyNumbers(general = "000"),
        "NZ" to EmergencyNumbers(general = "111"),

        // Middle East and Africa
        "AE" to EmergencyNumbers(general = "999", ambulance = "998", fire = "997"),
        "IL" to EmergencyNumbers(general = "100", ambulance = "101", fire = "102"),
        "JO" to EmergencyNumbers(general = "911"),
        "EG" to EmergencyNumbers(general = "122", ambulance = "123", fire = "180"),
        "ZA" to EmergencyNumbers(general = "112", police = "10111", ambulance = "10177"),
        "MA" to EmergencyNumbers(
            general = "19",
            ambulance = "15",
            fire = "15",
            other = listOf(EmergencyService(name = "Gendarmerie", number = "177")),
        ),
        "KE" to EmergencyNumbers(general = "999"),
        "TZ" to EmergencyNumbers(general = "112"),
    )
}
