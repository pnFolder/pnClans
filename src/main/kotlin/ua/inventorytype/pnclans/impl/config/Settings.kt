package ua.inventorytype.pnclans.impl.config

import com.charleskorn.kaml.YamlComment
import kotlinx.serialization.Serializable

@Serializable
class Settings {

    @YamlComment("Количество денег, необходимое для создания клана (0 — бесплатно)")
    val createClanCost: Double = 1000.0

}