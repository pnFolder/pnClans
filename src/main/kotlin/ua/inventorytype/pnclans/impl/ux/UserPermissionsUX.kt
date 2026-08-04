package ua.inventorytype.pnclans.impl.ux

import ua.inventorytype.pnclans.impl.clan.ClanService
import ua.inventorytype.pnclans.impl.inventory.BaseGui

/**
 * User Permissions GUI dynamically loaded from menus.yml (userPermissionsMenu).
 */
class UserPermissionsUX(clanService: ClanService) : BaseGui(clanService) {

    init {
        val config = clanService.plugin.configService.menus.userPermissionsMenu
        loadFromConfig(guiConfig = config)
    }
}
