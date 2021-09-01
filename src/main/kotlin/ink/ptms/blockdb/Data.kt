package ink.ptms.blockdb

import taboolib.common.util.asList
import taboolib.common5.Coerce

/**
 * Chemdah
 * ink.ptms.blockdb.Data
 *
 * @author sky
 * @since 2021/3/2 12:00 上午
 */
open class Data {

    val data: Any
    var changed = false

    protected var lazyCache: Any? = null

    constructor(value: Int) {
        this.data = value
    }

    constructor(value: Float) {
        this.data = value
    }

    constructor(value: Double) {
        this.data = value
    }

    constructor(value: Long) {
        this.data = value
    }

    constructor(value: Short) {
        this.data = value
    }

    constructor(value: Byte) {
        this.data = value
    }

    constructor(value: Boolean) {
        this.data = value
    }

    protected constructor(value: Any) {
        this.data = value
    }

    fun toInt(): Int {
        return Coerce.toInteger(data)
    }

    fun toFloat(): Float {
        return Coerce.toFloat(data)
    }

    fun toDouble(): Double {
        return Coerce.toDouble(data)
    }

    fun toLong(): Long {
        return Coerce.toLong(data)
    }

    fun toShort(): Short {
        return Coerce.toShort(data)
    }

    fun toByte(): Byte {
        return Coerce.toByte(data)
    }

    fun toBoolean(): Boolean {
        return Coerce.toBoolean(data)
    }

    fun asList(): List<String> {
        return data.asList()
    }

    override fun toString(): String {
        return data.toString()
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Data) return false
        if (data != other.data) return false
        return true
    }

    override fun hashCode(): Int {
        return data.hashCode()
    }

    companion object {

        fun unsafeData(any: Any) = Data(any)
    }
}