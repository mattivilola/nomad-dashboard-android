<!-- Source: Claude Docs brief "Nomad Dashboard: macOS feature brief for Android parity" (12 Oct 2026). Android parity work tracks this spec; see docs/status.md for delivery state. -->


# Nomad Dashboard: macOS feature brief for Android parity

2026-10-12 · @Matti Vilola
This brief lists everything added to the macOS app since v0.16.0, with the exact rules, thresholds and data sources, so the Android app can match it feature for feature. Features marked In progress are being built on macOS now; their rules are settled unless noted.

## At a glance

Fourteen feature areas changed. Released means shipped in a macOS release; On main means merged and waiting for the next release; In progress means being built now.
| Area | What changed | macOS status |
|---|---|---|
| Glance overview | Location-led header, verdict strip, one-line clocks, 4 tappable vital tiles, up to 3 ranked heads-up items | Released v0.17.0 |
| Heads-up engine | Ranked, profile-aware alerts across data, alerts, weather, holidays, fuel | Released v0.17.0, extended since |
| Compact cards | Sparklines, status-only color, one button style, collapsible cards, turned-off row, shared formatters | Released v0.17.0 |
| Emergency numbers | Bundled, verified numbers for 77 countries incl. Morocco | Released v0.17.0 |
| Travel profiles | Nomad vs Road trip & RV: card order and heads-up ranking; optional menu bar heads-up | Released v0.17.1 |
| Settings | Sidebar with 7 pages grouped for travellers; deep links from cards | Released v0.17.1 |
| Your Embassy | Nearest own mission, consular 24/7 line, EU/Nordic fallback, trip registration | Released v0.17.1 |
| Daylight | Sunrise/sunset line; before-dark heads-up in Road trip profile | On main |
| Rural police | Guardia Civil (ES), Federal Highway Police (BR), Highway Police (TH) | On main |
| Stays and visa days | Schengen 90/180, per-country stay allowance, tax-year days | In progress |
| Arrival briefing | One-time "Welcome to" card on crossing a border | In progress |
| Local rhythm | Ramadan, Eids, Lunar New Year, Holy Week and more, with visitor guidance | In progress |
| UV and air quality | WHO UV bands and European AQI heads-up | In progress |
| Siri, Shortcuts, widgets | Voice/automation answers and Glance, Safety, Daylight widgets | In progress |


## Product principles

Every feature below follows these rules; Android should apply them the same way.
- Answer first, details on scroll. The first screen answers "can I work and travel safely here right now?"; cards below hold the detail.
- Color means status only. Teal = good, sand = caution, coral = attention, neutral text otherwise. Never color a value by its metric or category (no diesel-teal, no Public-teal).
- Offline and on-device by default. Emergency numbers, embassy locations, consular lines, sunrise/sunset and holiday-period dates are bundled or computed locally and work with no signal.
- Sensitive data stays local. Citizenship, visited countries and location never leave the device and are never in analytics.
- Safety data must be verified. A wrong emergency or consular number is worse than none: every entry comes from an official source; unverifiable countries are left out and the UI says "listed" / "nearest known".
- Never truncate key values. Show fewer metrics rather than cut ones; units are drawn smaller than numbers, passed explicitly (never parsed out of strings).
- Don't remove features. Content may move into a menu, a collapsed card or Settings, but every action stays reachable.

## Glance overview

The top of the dashboard is a fixed sequence of six elements; the old app-name title and summary tiles are gone.
- Header. Headline = device city, else IP city, else "Travel-ready system telemetry". Caption line = device region, country and "IP city, country" side by side; it shortens in three steps so the IP part never gets cut (region+country · IP city, country → country · IP city, country → country · IP city). Refresh button and overflow menu on the right, with "Last refresh 11.30" under them.
- Time tracking row (only when enabled): status dot, "Pending 6h 58m", pause/stop, interruption counter and an Allocate menu listing up to 5 projects plus Other (project names in the menu, never cut).
- Verdict strip. Tinted by overall health level with the data-usage menu (Auto / Normal / Low Data) on the right. Titles: "Ready to work" (detail = network reason, plus " · VPN on"), "Workable", "Needs attention", "Checking conditions", "You're offline" (detail "Saved essentials are available").
- Clocks, one line. "Here <device city> 11:30 · Home <city> 12:30" plus the relative-day text and a settings shortcut. "Here" uses the device city, not the time-zone city.
- Four vital tiles (2×2), each tappable to scroll to and expand its card:
    - Internet: latency value, "96.0 ↓ · 19.2 ↑ Mbps", Wi-Fi name in the label; tinted by network health.
    - Power: battery %, "3h 28m left" / "Charging · 13m to full" / "Plugged in"; tinted by power health.
    - Data: Data Guard "1.2 GB / 5 GB" with a status bar (teal <80%, sand ≥80%, coral at cap); "Unmetered" when not monitoring; a VPN tile instead when Data Guard is off.
    - Weather: temperature + condition symbol, "Clear · 13° / 23°" (today's range); neutral color. Rain and wind are left to heads-up items so nothing repeats.
- Heads-up list: up to 3 ranked items (next section), hidden when empty; then one row of links: Workplaces · Saved essentials · Explore cities.
Verdict thresholds come from the health evaluator: latency >120 ms or jitter >25 ms = attention, ≥60 ms or jitter ≥10 ms or RSSI ≤ −70 dBm = caution; battery <25% or <45 min left = attention, ≤50% or ≤120 min or Low Power Mode = caution.

## Heads-up engine

A pure function turns the snapshot into at most 3 items, sorted by severity (attention > caution > info), then by the active profile's tie-break order; one item per kind. Each item has a title (≤ ~32 chars), a one-line detail, a short menu-bar text, an icon, a severity and the card it scrolls to. Network and battery health are never heads-up items (the verdict covers them).
| Kind | Trigger and severity | Example title · detail | Status |
|---|---|---|---|
| Data cap | ≥80% of cap caution, ≥100% attention (only while Data Guard monitors) | "Data cap at 85%" · "4.2 GB of 5 GB used" | Released |
| Travel advisory, weather alert, regional security | Ready signal at caution = caution; warning/critical = attention; clear/info ignored | "France: exercise a high degree of caution" | Released |
| Strong wind | Max wind now or in next 6 h: ≥40 km/h caution, ≥60 attention | "Wind up to 45 km/h" · "From W around 16:00" | Released |
| Rain soon | First hour in next 6 h with ≥60% chance: info | "Rain likely from 15:00" · "70% chance" | Released |
| Public holiday | Today caution; tomorrow or within 3 days info | "Public holiday today" · "Labour Day · shops may close" | Released |
| Cheapest fuel | Best station per type (diesel first): info | "Diesel 1,429 € · 4.8 km" · "Cheapest within 50 km" | Released |
| Daylight (Road trip only) | ≤60 min to sunset caution, ≤120 info; dark from 30 min after civil dusk: info | "Sunset 18:12 · 45 min of daylight" · "Dusk (civil twilight) ends 18:40"; "Dark until 07:45" | On main |
| Stay allowance | User-set allowance for the current country: ≤14 days left caution, ≤3 attention | "Morocco: 12 days left" · "Allowance 90 days · leave by 3 Mar" | In progress |
| Schengen limit | Non-exempt citizens only: ≤14 remaining caution, ≤3 attention | "Schengen: 10 of 90 days left" · "Leave by 14 Mar" | In progress |
| Tax residence days | Current country, current year: ≥ threshold−30 info, ≥ threshold−7 caution (threshold default 183) | "Spain: 160 days this year" · "183-day tax residence threshold · not tax advice" | In progress |
| UV index | Max UV in remaining daylight: 6–7 info, 8–10 caution, ≥11 attention (WHO bands) | "UV very high (9) from 12:00" · "Seek shade at midday; SPF 30+" | In progress |
| Air quality | European AQI now: ≥60 caution, ≥80 attention; worsening to poor within 12 h: caution | "Air quality poor (AQI 64)" · "Mostly PM10 · consider shorter outdoor activity" | In progress |
| Local rhythm | A period active or starting within 14 days in the current country (wording being finalised) | "Ramadan starts in 5 days" · visitor guidance | In progress |

Tie-break order. Nomad: data cap, stay allowance, Schengen, tax days, advisory, weather alert, security, wind, rain, UV, air quality, holiday, fuel, daylight. Road trip & RV: wind, daylight, UV, air quality, fuel, weather alert, data cap, stay allowance, Schengen, tax days, advisory, security, rain, holiday. Time-relative items re-evaluate every 60 s.

## Dashboard cards

Cards are 20–30% shorter than before and can each collapse to one line; turned-off modules no longer take space.
Layout rules
- Collapsible cards: a chevron folds a card into one ~40 pt line: title · summary · status badge · chevron. Collapsed state is saved. Tapping a tile or heads-up item expands its card first, then scrolls to it.
- Collapsed summaries never repeat the badge: Connectivity "28 ms · 96 ↓ 19 ↑"; Data Guard "1.27 GB left"; Power "72% · 3h 28m"; Time Tracking "Pending 6h 58m"; Travel Context "203.0.113.42 · Nomad Hub"; Local Info "Holiday 24 Oct"; Fuel "Diesel 1,429 € · 4.8 km"; Emergency Care "112 · nearest 2.4 km"; Travel Alerts "Spain + 4 nearby"; Weather "19° Partly Cloudy"; Your Embassy leads with the consular phone number.
- Turned-off row: cards whose feature is off are not rendered; one row reads "Turned off: Local Info, Fuel Prices" with a Settings button that opens the exact settings group. In Customize Layout every card shows so it can be ordered.
- Customize Layout (overflow menu): only then do cards show drag handles and the width toggle.
- Width packing: two adjacent half-width cards share a row and keep their natural heights; a half-width card without a half-width neighbour renders with the full-width layout.
- New cards in saved layouts: a card missing from a saved order is inserted right after its nearest recommended predecessor, so untouched layouts stay "recommended".
- One action style for every in-card button (teal text on a light tinted background); no system-blue links.
- Sparklines (~24 pt) under the value replace the old 120 pt charts; hover shows span and min/max.
- Shared formatters: temperature "19°" everywhere (menu bar too); fuel price in locale currency with 3 decimals and a small "/L" ("1,429 €/L"); data sizes binary (GiB shown as GB); units drawn smaller than numbers.
Card by card
| Card | What it shows now |
|---|---|
| Connectivity | Down · Up · Latency in one row, each with a sparkline; latency tinted by network health; one caption "Jitter 3 ms via 1.1.1.1" |
| Data Guard | "1.27 GB left" in status color, status-tinted bar, "of 5 GB · resets 1 Nov · ~3 days at this pace", "↓ 3.17 GB · ↑ 572 MB", why it is monitoring ("Remembered metered network" / "Turned on manually"), a "Monitor manually" toggle, Remember/Forget/Reset in a menu |
| Power | Battery (health-tinted) · Drain on battery or Adapter watts on power · Time left / To full / Full; sparklines for charge and drain; never repeats its subtitle |
| Time Tracking | Today · Focus · Pending, an allocation bar by project with legend (max 4 + "+N more"), one interruption control with "≈15m focus lost"; allocation moved to the header's Allocate menu |
| Travel Context | 4 rows: Public IP (copy) · Wi-Fi "Nomad Hub · −56 dBm · 720 Mbps" · VPN name · Location "Valencia, Spain · Europe/Madrid" (map) |
| Local Info | Emergency number row first; Public holiday "24 Oct · in 12 days" / name; School break dates / name; one "Prices vs EU" row (level word + "Meals −4% · Groceries −4% · Overall ≈ EU", |
| Fuel Prices | Rows open a map preview; Google Maps behind a ⋯ menu; prices neutral (no fuel-type colors); decorative backdrop only in the header band |
| Emergency Care | Subtitle "Within 25 km · Emergency 112"; hospitals listed nearest first; Public/Private as neutral chips; rows open map preview, ⋯ menu for Google |
| Travel Alerts | Badge = highest level; staleness as a caption "Updated 12 Oct · may be outdated"; level stated once |
| Weather + Surf | Big temperature + condition once, then Feels like · Wind (with m/s) · Rain chance; hourly strip always visible; Tomorrow row; 7-day outlook folds; sunrise/sunset line; surf in one row with 4 equal forecast columns |
| Your Embassy | See Safety section |
| Nomads.com city card | Unchanged: the prominent, linked Nomads.com credit is kept above the data at the source owner's request |


## Travel profiles and menu bar

A profile picks the recommended card order and heads-up ranking; it is switchable in Settings and from the dashboard's overflow menu.
| Profile | Summary shown to the user | Recommended card order |
|---|---|---|
| Nomad (default) | "Puts connection quality, work time and city context first." | Connectivity, Data Guard, Power, Time Tracking, Travel Context, Stays*, Local Info, Fuel, Emergency Care, Your Embassy, Travel Alerts, Weather |
| Road trip & RV | "Puts weather, fuel, emergency care, power and data allowance first." | Weather, Fuel, Emergency Care, Your Embassy, Power, Data Guard, Travel Alerts, Stays*, Connectivity, Local Info, Travel Context, Time Tracking |

* Stays is in progress.
- Switching profile changes the card order only if the user never customized it; otherwise the custom order is kept and Settings offers "Use recommended order". "Reset Dashboard Layout" restores the current profile's order.
- The daylight heads-up only fires in Road trip & RV.
Menu bar status (Android: persistent notification or Quick Settings tile text). First match wins:
- Data Guard usage "1.2/5 GB" when monitoring and the user enabled it.
- Battery ≤ 50%.
- Offline.
- Optional (off by default): the top heads-up item at caution or attention, using its short text ("Wind 45", "Sunset 18:12", "Cap 85%", "UV 9", "12 days left").
- Latency ≥ 60 ms.
- Current temperature "20°".
- Latency fallback, else an app icon.

## Safety

Three offline datasets cover "who do I call here": local emergency numbers, nearby hospitals, and the traveller's own embassy and foreign-ministry line.
Emergency numbers (bundled, 77 countries)
- Model per country: general (call first), optional police, ambulance, fire only when they differ from general, and other services (rural or highway police). Where a country has no all-purpose number, general is the police number (Japan: 110, with ambulance/fire 119).
- Shown in the Emergency Care subtitle, as the first Local Info row, in Saved essentials, and (in progress) the arrival briefing; numbers are selectable/copyable.
- Country = device location, then IP location, then Local Info.
- Morocco (from ANRT, the regulator): police 19, ambulance/fire 15, Gendarmerie 177 (rural areas and highways). UK and Canadian advice list 190/150 instead; those are not stored.
- Rural/highway police added: Spain Guardia Civil 062, Brazil Federal Highway Police 191, Thailand Highway Police 1193. Not added: Italy, Portugal, France, Romania (same as general number), Turkey (Jandarma merged into 112 in 2021), Greece/Mexico/Argentina (breakdown or complaints lines, not police).
- Deliberately missing: Laos and Guatemala (sources disagree); the UI shows nothing rather than a guess.
Emergency Care (nearby hospitals)
- Apple Maps hospital points within 25 km (Android: an equivalent places search).
- Selection guarantees the nearest public and nearest private hospital, but the list is always displayed nearest first. This fixed a bug where a 2.4 km hospital appeared after 6.8 km.
Your Embassy (Released v0.17.1)
- Settings › General › Citizenship: up to 3 countries, stored only on the device.
- Card shows, per citizenship: the foreign ministry's consular emergency line ("24/7 consular line" only when the ministry says so), up to 3 nearest listed missions (name, kind chip, city, distance — "~4 km" when the location is approximate), and links to the trip-registration service and the ministry's mission list.
- Ranking: career missions (embassy, consulate-general, consulate) before honorary consulates; distances within 5 km count as a tie, and the tie goes to the more capable kind.
- Fallback when the citizen's country has no listed mission in the host country: nearest known missions of other EU states (EU consular protection, Directive 2015/637) and, for Nordic citizens, Nordic missions ranked first (Nordic cooperation — a cooperative fallback, not a legal entitlement). Wording always says "listed" / "nearest known".
- At home: "You're home in Finland — embassy details appear when you travel" (hotline still shown).
- Mission row opens a maps search (by name + city when the location is approximate); a ⋯ menu has Show in Maps, Open website, Copy address.
- Footer: "Locations: Wikidata (CC0) · updated <date> · Check details on the official site before you go".
Consular lines (verified on each ministry's own site, 28 countries): AT, BG, CZ, DK, EE, FI, FR, DE, HU, IE, IT, LV, LU, MT, NL, PT, SK, SI, ES, SE, NO, IS, CH, GB, US, CA, AU, NZ. Left out because no single official 24/7 line is published: BE, HR, CY, GR, LT, PL, RO. Example: Finland +358 9 1605 5555 with "Travel notification" (matkustusilmoitus.fi).

## Daylight

Sunrise, sunset and civil twilight are computed on the device from coordinates (NOAA/Meeus equations), with no network, so they work in places with no signal.
- Coordinates: device location, else IP location. Time zone: the chosen location's own zone, then the device zone. Do not trust a single global zone for IP-only locations.
- Weather card line in every profile, also when weather data is unavailable: "Sunrise 07:45 · Sunset 18:12"; "Midnight sun" / "Polar night" near the poles.
- Heads-up (Road trip & RV only): see the heads-up table. The dark detail says "Sunrise tomorrow" in the evening and "Sunrise today" after midnight.
- Accuracy target ±2 min, tested against US Naval Observatory times for Helsinki (June and December), Rabat and Marrakech (15 Jan 2027), Madrid on a daylight-saving change day, Sydney, Auckland and Tromsø polar day/night.
- Morocco returned to GMT (UTC+0) permanently on 20 Sep 2026 (Decree 2.26.530); use the system time-zone database, which already reflects it. Rabat on 15 Jan 2027: sunrise ~07:33, sunset ~17:41.

## Settings structure

Settings is a sidebar of seven pages ordered the way a traveller thinks, replacing one long form and a separate Nomad Preferences window.
| Page | Groups |
|---|---|
| General | Travel profile (+ Use recommended order) · Home time zone · Citizenship · Appearance & startup · Menu bar (top heads-up, Data Guard usage) · Dashboard layout reset |
| Weather & Local | Weather location · Surf spot · Local Info (+ HUD token) · Fuel prices (+ Tankerkönig key) |
| Safety | Travel alerts (advisory, weather alerts, regional security) · Emergency care · Your embassy (status, Choose citizenship) |
| Connection & Data | Data usage mode · Offline essentials · Data Guard · Quiet connection alerts (incl. notification permission) |
| Work | Project time tracking · Workplace diary |
| Privacy & Location | Location permission · External IP location · Wi-Fi names via location · Visited places (+ Open Visited Map) · Anonymous analytics |
| Advanced | Refresh & history · Fuel diagnostics · About and updates |

- Each privacy fact sits as a short footer on the group it concerns, not in one long paragraph.
- Deep links: every "Open Settings" entry point opens the exact group, e.g. the clock button → Home time zone, the turned-off row → the first turned-off card's group, Your Embassy → Citizenship, Visited Map → Visited places. Targets: homeTimeZone, citizenship, weather, surfSpot, localInfo, fuelPrices, travelAlerts, emergencyCare, dataGuard, quietAlerts, timeTracking, workplaceDiary, visitedPlaces.

## In progress on macOS

Five features are being built now; their rules below are settled, and the screens are still being designed, so card layouts may change.
Stays and visa days (model done)
- Built from the app's local record of which country the user was in each day (inferred days between observations count; a day in two countries counts for both).
- Current stay: consecutive days in the current country; one unrecorded day is bridged, two or more end the stay.
- Schengen 90/180: days present in any of the 29 Schengen states in the rolling window today−179…today; entry and exit days count. Gives used, remaining and "must leave by" (simulated forward). Exempt for EU/EEA/Swiss citizens (31 free-movement states); silent when no citizenship is set. Members verified Oct 2026 against the European Commission page: Bulgaria and Romania full members since 1 Jan 2025; Ireland and Cyprus not members.
- Stay allowance: the user sets days per country (1–365); arrival day is day 1; gives remaining days and last permitted day, and shows overstays.
- Tax-year days: days per country per calendar year, threshold 183 by default (30–365), always labelled "not tax advice".
- Card "Stays" appears when visited places are being saved.
Arrival briefing (model done)
- Triggered once when today's country differs from the most recent previous recorded day (history ≤ 30 days old); dismissed with "Got it" and remembered per country; can be turned off. No briefing on first run.
- "Welcome to Morocco" sections, each shown only when data exists: local time and UTC offset vs home time zone; emergency numbers; nearest own embassy and consular line; driving side (flagged when it changes); plug types and voltage (flagged when they change); currency (flagged when it changes); public holiday today/tomorrow; travel advisory at caution or worse; "On a local SIM or roaming? Turn on Data Guard monitoring."
- New bundled tables: 76 left-hand-traffic jurisdictions (54 UN members + 22 territories, each confirmed by two sources); plug types, voltage and frequency for 104 countries where the last IEC World Plugs pages (the live tool is retired) and Wikipedia agree (Brazil and Japan skipped: mixed voltage or frequency); currency from the platform's region data. Holiday and advisory sections only appear once the data refers to the new country, so stale data from the previous country never shows.
Local rhythm (model done)
- Periods that visibly change daily life for a visitor, offline: Ramadan, Eid al-Fitr, Eid al-Adha (Umm al-Qura calendar, marked "expected" because local moon sighting can shift dates a day), Lunar New Year / Tết / Seollal (Chinese calendar, per-country spans), Holy Week (Easter computus), Yom Kippur (Hebrew calendar), Songkran, Golden Week, Ferragosto (fixed), Nyepi (Bali, official decrees 2026–27 only) and Diwali/Tihar/Deepavali (verified tables: India 2026–32, others shorter).
- Active periods and those starting within 14 days are shown for the current country, with short visitor guidance tuned per country group. Example (Morocco, Ramadan): "Shops and offices keep shorter hours and traffic peaks just before sunset, while evenings are lively. Some cafés and restaurants close or screen their windows by day, though hotels and tourist areas mostly keep serving. Be discreet eating, drinking or smoking in public during the day."
- Ramadan 2027 is expected to start around 8 Feb (Umm al-Qura); Lunar New Year 2027 is 6 Feb; Easter 2027 is 28 Mar.
- Countries: Ramadan/Eids in 23 Muslim-majority countries (four guidance tiers: conservative, mixed, relaxed, regional); Holy Week in ES, PT, IT, MT, MX, GT, CO, PE, EC, PH, CR, SV, VE, PY; Lunar New Year in CN, HK, MO, TW, SG, MY, VN, KR.
UV index and air quality (model being built)
- UV from the weather provider, now and hourly; WHO bands as in the heads-up table, limited to remaining daylight.
- Air quality from the Open-Meteo Air Quality API (European AQI and PM2.5, PM10, ozone, NO2; next 12 h). Coordinates rounded to 1 decimal (~11 km) before sending; only fetched when weather location is allowed; can be turned off. EAQI bands: 0–20 good, 20–40 fair, 40–60 moderate, 60–80 poor, 80–100 very poor, >100 extremely poor.
Siri, Shortcuts and widgets (being built)
- Voice and automation answers: emergency number here, dashboard status, nearest embassy, daylight left, switch travel profile. Example: "In Morocco, call 19 for police and 15 for ambulance or fire. The Gendarmerie is 177."
- Widgets: Glance (verdict, place, top heads-up), Safety (emergency number, embassy hotline), Daylight (sunset or "dark until"), each with "Updated 12:40" and a stale state after 6 h. The app writes a compact snapshot to a shared local container; no IP, coordinates or citizenship codes beyond what is shown.

## Data sources and privacy

Only two of the new features send anything off the device: air quality (rounded coordinates) and links the user taps.
| Data | Source | Where it runs | Sent off device |
|---|---|---|---|
| Emergency numbers | Curated table, each entry from an official source (EU 112 site, national police and regulator sites) | Bundled | Nothing |
| Embassy and consulate locations | Wikidata (CC0), 10,181 missions, rebuilt each release by a script | Bundled JSON | Nothing |
| Consular lines and trip registration | Each foreign ministry's own website | Bundled | Nothing |
| Sunrise, sunset, twilight | NOAA/Meeus solar equations | Computed on device | Nothing |
| Local rhythm dates | Islamic Umm al-Qura, Chinese and Hebrew calendars; Easter computus; official decree and holiday tables | Computed or bundled | Nothing |
| Driving side, plug types | Two sources each / IEC World Plugs | Bundled | Nothing |
| Stays, Schengen, tax days | The user's own country-day history | On device | Nothing |
| Citizenship | User setting | On device | Nothing; never in analytics |
| Air quality | Open-Meteo Air Quality API | Network | Coordinates rounded to ~11 km |
| UV index, weather | Existing weather provider | Network | As before |

- Opening a map, website or registration page is an explicit user action.
- Share these datasets rather than rebuilding them: the emergency-number table, consular contacts, the Wikidata mission JSON (and its builder script), the Schengen/EU/Nordic lists, the plug-type and driving-side tables and the local-rhythm tables. Any correction should land in both apps.

## Android implementation notes

Most logic is pure rules over data and ports directly; the platform surfaces map as below.
| macOS surface | Android equivalent |
|---|---|
| Menu bar popover | Main screen; status line in a persistent notification or Quick Settings tile |
| Menu bar text (heads-up option) | Ongoing notification title or Quick Settings tile subtitle |
| Siri and Shortcuts (App Intents) | App Actions / shortcuts and Assistant routines; share the same answer sentences |
| WidgetKit widgets | Glance app widgets (same three: Glance, Safety, Daylight) |
| WeatherKit (weather, UV) | Your Android weather provider; UV from the same source; keep WHO bands |
| Apple Maps hospital search | Places search for hospitals; keep nearest-first display and public/private guarantee |
| Settings sidebar | Settings screens with the same seven groups and deep links |
| Calendars (Umm al-Qura, Chinese, Hebrew) | ICU calendars on Android (android.icu.util) |

- Keep the rule tables identical (thresholds, wording, tie-break orders), ideally generated from one shared spec so the two apps can't drift.
- Background refresh on Android is battery-sensitive: compute daylight, stays and local rhythm on demand; only weather, air quality and alerts need fetching.
- Location: the same order everywhere (device location, then IP location, then local-info country).
- Open questions for the Android team: where Android's persistent status surface lives (notification vs tile), and whether Data Guard can measure per-network usage with Android's network stats APIs.
