package ink.ptms.blockdb

import ink.ptms.blockdb.BlockFactory.createDataContainer
import ink.ptms.blockdb.BlockFactory.deleteDataContainer
import ink.ptms.blockdb.BlockFactory.getDataContainer
import ink.ptms.blockdb.BlockFactory.hasDataContainer
import ink.ptms.blockdb.BlockFactory.isAllowPistonMove
import ink.ptms.blockdb.event.BlockDataDeleteEvent
import io.izzel.taboolib.Version
import io.izzel.taboolib.kotlin.Tasks
import io.izzel.taboolib.module.db.local.Local
import io.izzel.taboolib.module.inject.TFunction
import io.izzel.taboolib.module.inject.TListener
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.block.Block
import org.bukkit.block.data.Waterlogged
import org.bukkit.block.data.type.Cake
import org.bukkit.entity.*
import org.bukkit.event.*
import org.bukkit.event.block.*
import org.bukkit.event.entity.EntityBreakDoorEvent
import org.bukkit.event.entity.EntityChangeBlockEvent
import org.bukkit.event.entity.EntityDropItemEvent
import org.bukkit.event.entity.EntityExplodeEvent
import org.bukkit.event.player.PlayerInteractEvent
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import kotlin.collections.ArrayList

/**
 * blockdb
 * ink.ptms.blockdb.BlockRecycle
 *
 * @author sky
 * @since 2021/5/13 5:59 下午
 */
@TListener
class BlockRecover : Listener {

    private val fallingBlocksMap = ConcurrentHashMap<UUID, DataContainer>()
    private val fallingBlocksCache = Local.get("TabooLib").get("cache/blockdb_0.yml")!!

    @TFunction.Init
    private fun load() {
        fallingBlocksCache.getKeys(false).forEach {
            val uniqueId = UUID.fromString(it)
            if (Bukkit.getEntity(uniqueId) != null) {
                fallingBlocksMap[uniqueId] = DataContainer.fromJson(fallingBlocksCache[it].toString())
            } else {
                fallingBlocksCache.set(it, null)
            }
        }
    }

    /**
     * 玩家破坏方块
     * 生存、创造模式均有效
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    private fun e(e: BlockBreakEvent) {
        e.block.delete(e, BlockDataDeleteEvent.Reason.BREAK)
    }

    /**
     * 物理事件
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    private fun e(e: BlockPhysicsEvent) {
        if (e.changedType.isLegacyAir() && (Version.isBefore(Version.v1_13) || e.sourceBlock.type.isLegacyAir())) {
            e.block.deleteDataContainer()
        }
    }

    /**
     * 方块火焰被烧毁
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    private fun e(e: BlockBurnEvent) {
        e.block.delete(e, BlockDataDeleteEvent.Reason.BURN)
    }

    /**
     * 方块自然消退
     * 冰、雪融化
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    private fun e(e: BlockFadeEvent) {
        e.block.delete(e, BlockDataDeleteEvent.Reason.FADE)
    }

    /**
     * 树叶自然消退
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    private fun e(e: LeavesDecayEvent) {
        e.block.delete(e, BlockDataDeleteEvent.Reason.LEAVES_DECAY)
    }

    /**
     * 方块爆炸破坏方块
     * TNT、床
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    private fun e(e: BlockExplodeEvent) {
        e.blockList().toList().forEach {
            it.delete(e, BlockDataDeleteEvent.Reason.BLOCK_EXPLODE) {
                e.blockList().remove(it)
            }
        }
    }

    /**
     * 实体爆炸破坏方块
     * 爬行者、魔影水晶
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    private fun e(e: EntityExplodeEvent) {
        e.blockList().toList().forEach {
            it.delete(e, BlockDataDeleteEvent.Reason.ENTITY_EXPLODE) {
                e.blockList().remove(it)
            }
        }
    }

    /**
     * 液体流动
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    private fun e(e: BlockFromToEvent) {
        if (!Version.isAfter(Version.v1_13) || e.toBlock.blockData !is Waterlogged) {
            e.toBlock.delete(e, BlockDataDeleteEvent.Reason.LIQUID)
        }
    }

    /**
     * 玩家吃蛋糕
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    private fun e(e: PlayerInteractEvent) {
        if (e.action == Action.RIGHT_CLICK_BLOCK && e.clickedBlock?.type == Material.CAKE && e.clickedBlock!!.data.toInt() == 5) {
            e.clickedBlock!!.delete(e, BlockDataDeleteEvent.Reason.CAKE)
        }
    }

    /**
     * 实体修改方块
     * 魔影人破坏方块（数据会保存在魔影人身上）
     * 蠹虫破坏方块（直接销毁数据）
     * 方块坠落（数据转移）
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    private fun e(e: EntityChangeBlockEvent) {
        if ((e.entity is Zombie || e.entity is Silverfish || e.entity is Wither || e.entity is EnderDragon) && e.to.isLegacyAir()) {
            e.block.delete(
                e, when (e.entity) {
                    is Zombie -> BlockDataDeleteEvent.Reason.ZOMBIE_BREAK_DOOR
                    is Silverfish -> BlockDataDeleteEvent.Reason.SILVERFISH
                    is Wither -> BlockDataDeleteEvent.Reason.WITHER
                    is EnderDragon -> BlockDataDeleteEvent.Reason.ENDER_DRAGON
                    else -> error("out of case")
                }
            )
        }
        if (e.entity is FallingBlock || e.entity is Enderman) {
            if (e.to.isLegacyAir()) {
                val reason = when (e.entity) {
                    is FallingBlock -> BlockDataDeleteEvent.Reason.FALLING_BLOCK
                    is Enderman -> BlockDataDeleteEvent.Reason.ENDERMAN
                    else -> error("out of case")
                }
                if (e.block.delete(e, reason, delete = false)) {
                    val dataContainer = e.block.getDataContainer()
                    if (dataContainer != null) {
                        fallingBlocksMap[e.entity.uniqueId] = dataContainer
                        fallingBlocksCache.set(e.entity.uniqueId.toString(), dataContainer.toJson())
                        e.block.deleteDataContainer()
                    }
                }
            } else {
                val dataContainer = fallingBlocksMap.remove(e.entity.uniqueId)
                if (dataContainer != null) {
                    e.block.createDataContainer().merge(dataContainer)
                    fallingBlocksCache.set(e.entity.uniqueId.toString(), null)
                }
            }
        }
    }

    /**
     * 活塞推出方块
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    private fun e(e: BlockPistonExtendEvent) {
        e.check(e.blocks)
    }

    /**
     * 胡塞收回方块
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    private fun e(e: BlockPistonRetractEvent) {
        e.check(e.blocks)
    }

    private fun BlockPistonEvent.check(blocks: List<Block>) {
        if (blocks.all { it.isAllowPistonMove }) {
            val loc = ArrayList<Pair<Location, BlockDataContainer>>()
            blocks.forEach { block ->
                val dataContainer = block.getDataContainer()
                if (dataContainer != null) {
                    if (block.type.isSolid) {
                        loc.add(block.getRelative(direction).location to dataContainer)
                    } else if (!block.delete(this, BlockDataDeleteEvent.Reason.PISTON, delete = false)) {
                        return
                    }
                }
            }
            blocks.forEach { it.deleteDataContainer() }
            loc.forEach { it.first.createDataContainer().merge(it.second) }
        } else {
            isCancelled = true
        }
    }

    private fun Block.delete(event: Event, reason: BlockDataDeleteEvent.Reason, delete: Boolean = true, cancel: (() -> Unit)? = null): Boolean {
        return if (location.hasDataContainer()) {
            if (BlockDataDeleteEvent(location, reason, event).call().isCancelled) {
                if (cancel == null) {
                    (event as? Cancellable)?.isCancelled = true
                } else {
                    cancel()
                }
                false
            } else {
                if (delete) {
                    deleteDataContainer()
                }
                true
            }
        } else {
            true
        }
    }

    private fun Material.isLegacyAir(): Boolean {
        return if (Version.isAfter(Version.v1_15)) isAir else this == Material.AIR
    }
}