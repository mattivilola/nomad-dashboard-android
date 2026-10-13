package com.iloapps.nomaddashboard.core.travel.safety

internal object MissionFixtures {
    // Coordinates are real city centres so distances can be sanity-checked.
    val rabat = MissionCoordinate(34.0209, -6.8416)
    val casablanca = MissionCoordinate(33.5731, -7.5898)
    val agadir = MissionCoordinate(30.4278, -9.5981)
    val marrakesh = MissionCoordinate(31.6295, -7.9811)
    val amman = MissionCoordinate(31.9539, 35.9106)

    fun mission(
        id: String,
        sending: String,
        kind: DiplomaticMissionKind,
        name: String,
        at: MissionCoordinate,
        host: String = "MA",
    ) = DiplomaticMission(
        id = id,
        sendingCountry = sending,
        hostCountry = host,
        kind = kind,
        name = name,
        latitude = at.latitude,
        longitude = at.longitude,
    )

    fun ids(matches: List<DiplomaticMissionMatch>): List<String> = matches.map { it.mission.id }
}
