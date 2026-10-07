package net.guizhanss.infinityexpansion2.api.mobsim

import io.github.thebusybiscuit.slimefun4.libraries.dough.collections.RandomizedSet
import org.bukkit.Material
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.Damageable
import java.util.Objects
import kotlin.random.Random

/**
 * This class is used in API call.
 */
data class MobDataCardProps(
    val id: String,
    val name: String,
    val texture: ItemStack,
    val energy: Int,
    val experience: Int,
    val drops: List<Pair<ItemStack, Double>>,
    val recipe: Array<ItemStack?>,
) {

    // Keep the public constructor/API unchanged. Config-backed cards can attach amount ranges
    // internally, while cards registered by other addons continue to use their ItemStack amount.
    private val dropSet = RandomizedSet<Int>()
    private var dropAmountRanges: List<IntRange> = drops.map { (item, _) -> item.amount..item.amount }
    private var dropDamageRanges: List<IntRange?> = drops.map { null }

    init {
        drops.forEachIndexed { index, (_, chance) ->
            dropSet.add(index, chance.toFloat())
        }
    }

    fun getRandomDrop(): ItemStack = getDrop(dropSet.random)

    internal fun configureDropAmountRanges(ranges: List<IntRange>) {
        require(ranges.size == drops.size) { "Drop amount range count must match drop count" }
        require(ranges.all { it.first > 0 && it.last >= it.first }) { "Drop amount ranges must be positive" }
        dropAmountRanges = ranges.toList()
    }

    internal fun configureDropDamageRanges(ranges: List<IntRange?>) {
        require(ranges.size == drops.size) { "Drop damage range count must match drop count" }
        ranges.forEachIndexed { index, range ->
            if (range != null) {
                require(range.first >= 0 && range.last >= range.first) { "Drop damage ranges must be nonnegative" }
                val item = drops[index].first
                val meta = item.itemMeta as? Damageable
                val maximum = if (meta?.hasMaxDamage() == true) meta.maxDamage else item.type.maxDurability.toInt()
                require(meta != null && maximum > 0 && range.last <= maximum) { "Drop damage range exceeds item durability" }
            }
        }
        dropDamageRanges = ranges.toList()
    }

    internal fun getDrop(index: Int): ItemStack {
        val item = drops[index].first.clone()
        val range = dropAmountRanges.getOrElse(index) { item.amount..item.amount }
        item.amount = if (range.first == range.last) {
            range.first
        } else {
            Random.nextLong(range.first.toLong(), range.last.toLong() + 1L).toInt()
        }
        dropDamageRanges.getOrNull(index)?.let { damageRange ->
            val meta = item.itemMeta as Damageable
            meta.damage = if (damageRange.first == damageRange.last) {
                damageRange.first
            } else {
                Random.nextLong(damageRange.first.toLong(), damageRange.last.toLong() + 1L).toInt()
            }
            item.itemMeta = meta
        }
        return item
    }

    internal fun getDropAmountRange(index: Int): IntRange =
        dropAmountRanges.getOrElse(index) {
            val amount = drops[index].first.amount
            amount..amount
        }

    internal fun getDropDamageRange(index: Int): IntRange? = dropDamageRanges.getOrNull(index)

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is MobDataCardProps) return false

        return id == other.id && name == other.name && texture == other.texture && energy == other.energy && experience == other.experience && drops == other.drops && recipe.contentEquals(
            other.recipe
        )
    }

    override fun hashCode() = Objects.hash(id, name, texture, energy, experience, drops, recipe.contentHashCode())

    companion object {

        val EMPTY = MobDataCardProps(
            id = "",
            name = "",
            texture = ItemStack(Material.AIR),
            energy = 0,
            experience = 0,
            drops = emptyList(),
            recipe = emptyArray()
        )
    }
}
