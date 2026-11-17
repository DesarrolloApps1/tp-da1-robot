package com.vigia.data.contract

import kotlinx.serialization.json.Json

// Configuración única para leer y escribir mensajes del contrato
val ContractJson: Json = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
    explicitNulls = true
}
