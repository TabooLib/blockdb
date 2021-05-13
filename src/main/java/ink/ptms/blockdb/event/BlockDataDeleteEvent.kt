package ink.ptms.blockdb.event

import io.izzel.taboolib.module.event.EventCancellable
import org.bukkit.Location
import org.bukkit.event.Event

/**
 * blockdb
 * ink.ptms.blockdb.event.BlockDataDeleteEvent
 *
 * @author sky
 * @since 2021/5/13 8:30 下午
 */
class BlockDataDeleteEvent(val location: Location, val reason: Reason, val bukkitEvent: Event) : EventCancellable<BlockDataDeleteEvent>(true) {

    enum class Reason {

        PHYSICAL,
        BREAK,
        BURN,
        FADE,
        LEAVES_DECAY,
        BLOCK_EXPLODE,
        ENTITY_EXPLODE,
        LIQUID,
        CAKE,
        ZOMBIE_BREAK_DOOR,
        SILVERFISH,
        WITHER,
        ENDER_DRAGON,
        FALLING_BLOCK,
        ENDERMAN,
        PISTON

    }
}