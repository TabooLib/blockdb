package ink.ptms.blockdb

import io.izzel.taboolib.util.Coerce

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

    constructor(value: String) {
        this.data = value
    }

    protected constructor(value: Any) {
        this.data = value
    }

    fun toInt() = Coerce.toInteger(data)

    fun toFloat() = Coerce.toFloat(data)

    fun toDouble() = Coerce.toDouble(data)

    fun toLong() = Coerce.toLong(data)

    fun toShort() = Coerce.toShort(data)

    fun toByte() = Coerce.toByte(data)

    fun toBoolean() = Coerce.toBoolean(data)

    override fun toString() = data.toString()

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