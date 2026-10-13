package com.iloapps.nomaddashboard.core.travel.safety

/** Text behind the "Your embassy" settings section. Citizenship is sensitive: everything here is computed locally. */
object CitizenshipPresentation {
    const val EmbassySectionFooter =
        "Embassy and consulate locations come from Wikidata (CC0) and are bundled with the app, so they work offline. " +
            "Phone lines come from each foreign ministry's official website. When your country has no mission where you are, " +
            "EU consular protection or Nordic cooperation may let another country's mission help; that is not a legal guarantee. " +
            "The data can be incomplete or out of date, so check the official site before you rely on it."

    /**
     * Settings > Safety > Your embassy status line: who the card is for and whether their foreign
     * ministry's emergency line is bundled.
     */
    fun embassyStatus(codes: List<String>, countryNames: CountryNames): String {
        if (codes.isEmpty()) return "No citizenship chosen. The Your Embassy card is off."

        val withLine = codes.filter { ConsularContactsDirectory.contactsFor(it) != null }
        val countries = countryNames.list(codes)

        return when (withLine.size) {
            codes.size -> "$countries · verified consular emergency line included"
            0 -> "$countries · no verified consular line yet, embassy locations still work"
            else -> "$countries · verified consular line for ${countryNames.list(withLine)}"
        }
    }
}
