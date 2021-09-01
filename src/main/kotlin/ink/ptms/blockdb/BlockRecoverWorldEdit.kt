package ink.ptms.blockdb

import com.sk89q.worldedit.WorldEdit
import com.sk89q.worldedit.event.extent.EditSessionEvent
import com.sk89q.worldedit.extent.AbstractDelegateExtent
import com.sk89q.worldedit.math.BlockVector3
import com.sk89q.worldedit.util.eventbus.Subscribe
import com.sk89q.worldedit.world.block.BlockStateHolder
import org.bukkit.Bukkit
import taboolib.common.LifeCycle
import taboolib.common.platform.Awake

/**
 * blockdb
 * ink.ptms.blockdb.BlockRecover2
 *
 * @author sky
 * @since 2021/5/13 6:10 下午
 */
class BlockRecoverWorldEdit {

    @Subscribe
    fun e(e: EditSessionEvent) {
        e.world ?: return
        e.extent = object : AbstractDelegateExtent(e.extent) {

            override fun <T : BlockStateHolder<T>> setBlock(pos: BlockVector3, block: T): Boolean {
                BlockFactory.getWorld(e.world!!.name).getRegionByBlock(pos.x, pos.z).delBlock(pos.x, pos.y, pos.z)
                return extent.setBlock(pos, block)
            }
        }
    }

    companion object {

        @Awake(LifeCycle.INIT)
        fun init() {
            if (Bukkit.getPluginManager().getPlugin("WorldEdit") != null) {
                try {
                    WorldEdit.getInstance().eventBus.register(BlockRecoverWorldEdit())
                } catch (ex: NoClassDefFoundError) {
                }
            }
        }
    }
}