package ink.ptms.blockdb

import com.destroystokyo.paper.event.block.BlockDestroyEvent
import io.izzel.taboolib.kotlin.Tasks
import io.izzel.taboolib.kotlin.asMap
import io.izzel.taboolib.kotlin.warning
import io.izzel.taboolib.module.db.local.Local
import io.izzel.taboolib.module.inject.TFunction
import io.izzel.taboolib.module.inject.TListener
import io.izzel.taboolib.module.inject.TSchedule
import io.izzel.taboolib.util.Files
import io.izzel.taboolib.util.IO
import org.bukkit.Bukkit
import org.bukkit.Chunk
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.block.Block
import org.bukkit.entity.FallingBlock
import org.bukkit.event.Cancellable
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.block.*
import org.bukkit.event.entity.EntityChangeBlockEvent
import org.bukkit.event.entity.EntityExplodeEvent
import org.bukkit.event.world.ChunkLoadEvent
import org.bukkit.event.world.WorldSaveEvent
import org.bukkit.util.Vector
import java.io.*
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream
import kotlin.collections.ArrayList
import kotlin.collections.HashMap

@TListener
object BlockFactory : Listener {

    val worlds = ConcurrentHashMap<String, World>()

    val Chunk.dataContainerMap: Map<Vector, BlockDataContainer>
        get() = getWorld(world.name).getRegionByChunk(x, z).getBlocksInChunk(x, z)

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
        return getWorld(world.name).getRegionByBlock(blockX, blockZ).createBlock(blockX, blockY, blockZ)
    }

    fun Location.getDataContainer(): BlockDataContainer? {
        return getWorld(world.name).getRegionByBlock(blockX, blockZ).getBlock(blockX, blockY, blockZ)
    }

    fun Location.hasDataContainer(): Boolean {
        return getWorld(world.name).getRegionByBlock(blockX, blockZ).hasBlock(blockX, blockY, blockZ)
    }

    fun Location.deleteDataContainer() {
        getWorld(world.name).getRegionByBlock(blockX, blockZ).delBlock(blockX, blockY, blockZ)
    }

    fun Location.getNearbyDataContainers(range: Double): Map<Vector, BlockDataContainer> {
        val map = HashMap<Vector, BlockDataContainer>()
        val world = getWorld(world.name)
        world.regions.forEach { (_, region) ->
            region.blocks.forEach { (_, block) ->
                val vector = Vector(block.blockX, block.blockY, block.blockZ)
                if (vector.distance(toVector()) < range) {
                    map[vector] = block
                }
            }
        }
        return map
    }

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

    @TSchedule(period = 3000, async = true)
    @TFunction.Cancel
    private fun save() {
        worlds.forEach { (_, world) -> world.save() }
    }

    @TSchedule(period = 1000, async = true)
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

    @EventHandler
    private fun e(e: WorldSaveEvent) {
        save()
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
                    ZipFile(file).use { zipFile ->
                        ByteArrayInputStream(IO.readFully(zipFile.getInputStream(zipFile.getEntry("data")))).use { byteArrayInputStream ->
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
                    try {
                        FileOutputStream(Files.file("$name/blockdata/b.${regionX},${regionZ}.bdb")).use { fileOutputStream ->
                            ZipOutputStream(fileOutputStream).use { zipOutputStream ->
                                zipOutputStream.putNextEntry(ZipEntry("data"))
                                zipOutputStream.write(byteArray)
                                zipOutputStream.flush()
                                zipOutputStream.closeEntry()
                            }
                        }
                    } catch (t: Throwable) {
                        t.printStackTrace()
                    }
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
            val x = chunkX * 16 to chunkX * 16 + 16
            val z = chunkZ * 16 to chunkZ * 16 + 16
            val chunk = HashMap<Vector, BlockDataContainer>()
            blocks.forEach {
                val pos = fromLocationKey(it.key)
                if (pos.blockX >= x.first && pos.blockX < x.second && pos.blockZ > z.first && pos.blockZ < z.second) {
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