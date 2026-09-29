package com.jadroid.launcher.core

import kotlinx.serialization.json.Json

/** Shared JSON configuration: Mojang/Fabric/Modrinth payloads change often, so unknown keys are tolerated. */
val JadroidJson: Json = Json {
    ignoreUnknownKeys = true
    isLenient = true
    explicitNulls = false
    coerceInputValues = true
    encodeDefaults = true
    prettyPrint = false
}
