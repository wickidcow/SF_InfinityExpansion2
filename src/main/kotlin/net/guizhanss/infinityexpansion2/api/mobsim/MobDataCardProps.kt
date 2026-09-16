package net.guizhanss.infinityexpansion2.api.mobsim

import io.github.thebusybiscuit.slimefun4.libraries.dough.collections.RandomizedSet
import org.bukkit.Material
import org.bukkit.inventory.ItemStack
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

    internal fun getDrop(index: Int): ItemStack {
        val item = drops[index].first.clone()
        val range = dropAmountRanges.getOrElse(index) { item.amount..item.amount }
        item.amount = if (range.first == range.last) {
            range.first
        } else {
            Random.nextLong(range.first.toLong(), range.last.toLong() + 1L).toInt()
        }
        return item
    }

    internal fun getDropAmountRange(index: Int): IntRange =
        dropAmountRanges.getOrElse(index) {
            val amount = drops[index].first.amount
            amount..amount
        }

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
