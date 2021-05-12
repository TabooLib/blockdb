package ink.ptms.blockdb

import io.izzel.taboolib.kotlin.Tasks
import io.izzel.taboolib.kotlin.asMap
import io.izzel.taboolib.module.db.local.Local
import io.izzel.taboolib.module.inject.TFunction
import io.izzel.taboolib.module.inject.TListener
import io.izzel.taboolib.module.inject.TSchedule
import io.izzel.taboolib.util.Files
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.block.Block
import org.bukkit.entity.FallingBlock
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.block.BlockPistonEvent
import org.bukkit.event.block.BlockPistonExtendEvent
import org.bukkit.event.block.BlockPistonRetractEvent
import org.bukkit.event.entity.EntityChangeBlockEvent
import org.bukkit.event.world.ChunkLoadEvent
import org.bukkit.util.Vector
import java.io.*
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import kotlin.collections.ArrayList
import kotlin.collections.HashMap

@TListener
object BlockFactory : Listener {

    val worlds = ConcurrentHashMap<String, World>()

    private val fallingBlocksMap = ConcurrentHashMap<UUID, DataContainer>()
    private val fallingBlocksCache = Local.get("TabooLib").get("cache/blockdb_0.yml")!!

    fun getWorld(world: String): World {
        return worlds.computeIfAbsent(world) { World(world) }
    }

    fun regionKey(x: Int, z: Int): Long {
        return x.toLong() and 134217727L or (z.toLong() and 134217727L shl 27)
    }

    fun locationKey(x: Int, y: Int, z: Int): Long {
        return x.toLong() and 134217727L or (z.toLong() and 134217727L shl 27) or (y.toLong() shl 54)
    }

    fun fromLocationKey(key: Long): Vector {
        return Vector(fromLocationKeyX(key), fromLocationKeyY(key), fromLocationKeyZ(key))
    }

    fun fromLocationKeyX(packed: Long): Int {
        return (packed shl 37 shr 37).toInt()
    }

    fun fromLocationKeyY(packed: Long): Int {
        return (packed ushr 54).toInt()
    }

    fun fromLocationKeyZ(packed: Long): Int {
        return (packed shl 10 shr 37).toInt()
    }

    private fun Int.roundToRegion() = this shr 5

    @TFunction.Load
    private fun load() {
        fallingBlocksCache.get("data")?.asMap()?.forEach {
            fallingBlocksMap[UUID.fromString(it.key)] = DataContainer.fromJson(it.value.toString())
        }
    }

    @TFunction.Cancel
    private fun unload() {
        fallingBlocksCache.set("data", fallingBlocksMap.map { it.key.toString() to it.value.toJson() })
    }

    @TSchedule(period = 6000, async = true)
    @TFunction.Cancel
    private fun save() {
        worlds.forEach { (_, world) -> world.save() }
    }

    @TSchedule(period = 1200, async = true)
    private fun check() {
        worlds.forEach { (_, world) -> world.regions.forEach { (_, region) -> region.blocks.values.removeIf { it.isEmpty() } } }
    }

    @TSchedule
    private fun active() {
        Bukkit.getWorlds().forEach { world ->
            world.loadedChunks.forEach { chunk ->
                getWorld(world.name).getRegion((chunk.x * 16) shr 5, (chunk.z * 16) shr 5)
            }
        }
    }

    @EventHandler
    private fun e(e: ChunkLoadEvent) {
        Tasks.task(true) {
            getWorld(e.chunk.world.name).getRegion((e.chunk.x * 16) shr 5, (e.chunk.z * 16) shr 5)
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    private fun e(e: EntityChangeBlockEvent) {
        if (e.entity is FallingBlock) {
            if (e.to == Material.AIR) {
                val dataContainer = e.block.getDataContainer()
                if (dataContainer != null) {
                    fallingBlocksMap[e.entity.uniqueId] = dataContainer
                    e.block.deleteDataContainer()
                }
            } else {
                val dataContainer = fallingBlocksMap.remove(e.entity.uniqueId)
                if (dataContainer != null) {
                    e.block.createDataContainer().merge(dataContainer)
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    private fun e(e: BlockPistonExtendEvent) {
        e.check(e.blocks)
    }

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
                    loc.add(block.getRelative(direction).location to dataContainer)
                    block.deleteDataContainer()
                }
            }
            loc.forEach { it.first.createDataContainer().merge(it.second) }
        } else {
            isCancelled = true
        }
    }

    data class World(val name: String) {

        val regions = ConcurrentHashMap<Long, WorldRegion>()
        val lock = 0

        fun save() {
            regions.forEach { (_, region) ->
                saveRegion(region.x, region.z, System.currentTimeMillis() - region.readTime > TimeUnit.MINUTES.toMillis(10))
            }
        }

        fun getRegionByChunk(chunkX: Int, chunkZ: Int): WorldRegion {
            return getRegion((chunkX * 16) shr 5, (chunkZ * 16) shr 5)
        }

        fun getRegionByBlock(blockX: Int, blockZ: Int): WorldRegion {
            return getRegion(blockX shr 5, blockZ shr 5)
        }

        fun getRegion(regionX: Int, regionZ: Int): WorldRegion {
            val regionKey = regionKey(regionX, regionZ)
            var region = regions[regionKey]
            if (region != null) {
                return region.read()
            }
            region = loadRegion(regionX, regionZ)
            if (region != null) {
                return region.read()
            }
            return WorldRegion(this, regionX, regionZ).also {
                regions[regionKey] = it
            }
        }

        fun loadRegion(regionX: Int, regionZ: Int, reload: Boolean = false): WorldRegion? {
            synchronized(lock) {
                val regionKey = regionKey(regionX, regionZ)
                if (regions.containsKey(regionKey) && !reload) {
                    return regions[regionKey]!!
                }
                val file = File("$name/blockdata/b.${regionX},${regionZ}.bdb")
                if (file.exists()) {
                    ByteArrayInputStream(file.readBytes()).use { byteArrayInputStream ->
                        ObjectInputStream(byteArrayInputStream).use { objectInputStream ->
                            val map = HashMap<Long, BlockDataContainer>()
                            while (true) {
                                try {
                                    val key = objectInputStream.readLong()
                                    val loc = fromLocationKey(key)
                                    map[key] = BlockDataContainer(name, loc.blockX, loc.blockY, loc.blockZ).also {
                                        it.merge(DataContainer.fromJson(objectInputStream.readUTF()))
                                    }
                                } catch (ex: Exception) {
                                    break
                                }
                            }
                            return WorldRegion(this, regionX, regionZ, map).also {
                                regions[regionKey] = it
                            }
                        }
                    }
                }
                return null
            }
        }

        fun saveRegion(regionX: Int, regionZ: Int, unload: Boolean = false) {
            val regionKey = regionKey(regionX, regionZ)
            val region = regions[regionKey]
            if (region != null) {
                val byteArray = region.toByteArray()
                if (region.blocks.isEmpty()) {
                    File("$name/blockdata/b.${regionX},${regionZ}.bdb").delete()
                } else {
                    Files.file("$name/blockdata/b.${regionX},${regionZ}.bdb").writeBytes(byteArray)
                }
            }
            if (unload) {
                regions.remove(regionKey(regionX, regionZ))
            }
        }
    }

    data class WorldRegion(val world: World, val x: Int, val z: Int) {

        val blocks = ConcurrentHashMap<Long, BlockDataContainer>()
        var readTime = System.currentTimeMillis()

        constructor(world: World, x: Int, z: Int, map: Map<Long, BlockDataContainer>) : this(world, x, z) {
            blocks.putAll(map)
        }

        fun read(): WorldRegion {
            readTime = System.currentTimeMillis()
            return this
        }

        fun getBlocksInChunk(chunkX: Int, chunkZ: Int): Map<Vector, BlockDataContainer> {
            val x = (chunkX * 16)..(chunkX * 16 + 16)
            val z = (chunkZ * 16)..(chunkZ * 16 + 16)
            val chunk = HashMap<Vector, BlockDataContainer>()
            blocks.forEach {
                val pos = fromLocationKey(it.key)
                if (pos.blockX in x && pos.blockZ in z) {
                    chunk[pos] = it.value
                }
            }
            return chunk
        }

        fun createBlock(blockX: Int, blockY: Int, blockZ: Int): BlockDataContainer {
            return BlockDataContainer(world.name, blockX, blockY, blockZ).also {
                blocks[locationKey(blockX, blockY, blockZ)] = it
            }
        }

        fun getBlock(blockX: Int, blockY: Int, blockZ: Int): BlockDataContainer? {
            return blocks[locationKey(blockX, blockY, blockZ)]
        }

        fun delBlock(blockX: Int, blockY: Int, blockZ: Int) {
            blocks.remove(locationKey(blockX, blockY, blockZ))
        }

        fun hasBlock(blockX: Int, blockY: Int, blockZ: Int): Boolean {
            return blocks.containsKey(locationKey(blockX, blockY, blockZ))
        }

        fun toByteArray(): ByteArray {
            ByteArrayOutputStream().use { byteArrayOutputStream ->
                ObjectOutputStream(byteArrayOutputStream).use { objectOutputStream ->
                    blocks.forEach { (t, u) ->
                        if (u.isEmpty()) {
                            blocks.remove(t)
                        } else {
                            objectOutputStream.writeLong(t)
                            objectOutputStream.writeUTF(u.toJson())
                        }
                    }
                }
                return byteArrayOutputStream.toByteArray()
            }
        }
    }
}