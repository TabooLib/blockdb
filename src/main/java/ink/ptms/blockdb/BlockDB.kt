package ink.ptms.blockdb

import org.bukkit.Chunk
import org.bukkit.Location
import org.bukkit.block.Block
import org.bukkit.util.Vector

val Chunk.dataContainerMap: Map<Vector, BlockDataContainer>
    get() = BlockFactory.getWorld(world.name).getRegionByChunk(x, z).getBlocksInChunk(x, z)

var Block.isAllowPistonMove: Boolean
    set(it) {
        val dataContainer = getDataContainer() ?: createDataContainer()
        dataContainer[GeneralKey.PISTON_MOVE.key] = Data(it)
    }
    get() {
        val dataContainer = getDataContainer()
        return dataContainer == null || dataContainer[GeneralKey.PISTON_MOVE.key, Data(true)].toBoolean()
    }

fun Block.createDataContainer(): BlockDataContainer {
    return location.createDataContainer()
}

fun Block.getDataContainer(): BlockDataContainer? {
    return location.getDataContainer()
}

fun Block.hasDataContainer(): Boolean {
    return location.hasDataContainer()
}

fun Block.deleteDataContainer() {
    location.deleteDataContainer()
}

fun Location.createDataContainer(): BlockDataContainer {
    return BlockFactory.getWorld(world.name).getRegionByBlock(blockX, blockZ).createBlock(blockX, blockY, blockZ)
}

fun Location.getDataContainer(): BlockDataContainer? {
    return BlockFactory.getWorld(world.name).getRegionByBlock(blockX, blockZ).getBlock(blockX, blockY, blockZ)
}

fun Location.hasDataContainer(): Boolean {
    return BlockFactory.getWorld(world.name).getRegionByBlock(blockX, blockZ).hasBlock(blockX, blockY, blockZ)
}

fun Location.deleteDataContainer() {
    BlockFactory.getWorld(world.name).getRegionByBlock(blockX, blockZ).delBlock(blockX, blockY, blockZ)
}