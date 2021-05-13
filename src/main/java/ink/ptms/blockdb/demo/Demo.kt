package ink.ptms.blockdb.demo

import ink.ptms.adyeshach.api.AdyeshachAPI
import ink.ptms.adyeshach.boot.Plugin
import ink.ptms.adyeshach.common.entity.EntityTypes
import ink.ptms.blockdb.BlockFactory.createDataContainer
import ink.ptms.blockdb.BlockFactory.dataContainerMap
import ink.ptms.blockdb.BlockFactory.getDataContainer
import ink.ptms.blockdb.Data
import io.izzel.taboolib.kotlin.Serializer
import org.bukkit.Bukkit
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.block.BlockBreakEvent
import org.bukkit.event.block.BlockPlaceEvent
import org.bukkit.event.player.PlayerToggleSneakEvent
import org.bukkit.inventory.ItemStack

/**
 * blockdb
 * ink.ptms.blockdb.demo.Demo
 *
 * @author sky
 * @since 2021/5/12 9:54 下午
 */
class Demo : Plugin(), Listener {

    override fun onEnable() {
        Bukkit.getPluginManager().registerEvents(this, plugin)
    }

    @EventHandler
    fun e(e: PlayerToggleSneakEvent) {
        if (e.isSneaking) {
            val manager = AdyeshachAPI.getEntityManagerPrivateTemporary(e.player)
            e.player.chunk.dataContainerMap.forEach {
                manager.create(EntityTypes.SHULKER, it.key.toLocation(e.player.world).add(0.5, 0.0, 0.5)) { npc ->
                    npc.id = "demo:debug"
                    npc.setGlowing(true)
                    npc.setInvisible(true)
                }
            }
        } else {
            AdyeshachAPI.getEntityManagerPrivateTemporary(e.player).getEntities().forEach {
                if (it.id == "demo:debug") {
                    it.delete()
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun e(e: BlockPlaceEvent) {
        if (!e.itemInHand.isSimilar(ItemStack(e.itemInHand.type))) {
            val dataContainer = e.blockPlaced.getDataContainer() ?: e.blockPlaced.createDataContainer()
            dataContainer["demo.item"] = Data(Serializer.fromItemStack(e.itemInHand.clone().also {
                it.amount = 1
            }))
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun e(e: BlockBreakEvent) {
        val dataContainer = e.block.getDataContainer() ?: return
        if (dataContainer.containsKey("demo.item")) {
            e.isDropItems = false
            e.block.world.dropItem(e.block.location.add(0.5, 0.5, 0.5), Serializer.toItemStack(dataContainer["demo.item"]!!.toString()))
            dataContainer.remove("demo.item")
        }
    }
}