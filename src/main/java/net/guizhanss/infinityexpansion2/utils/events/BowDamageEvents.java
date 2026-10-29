package net.guizhanss.infinityexpansion2.utils.events;

import com.google.common.base.Functions;
import java.util.HashMap;
import java.util.Map;
import org.bukkit.damage.DamageSource;
import org.bukkit.damage.DamageType;
import org.bukkit.entity.Entity;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;

/**
 * Keeps Infinity Bow's existing cancellable preflight without a for-removal constructor.
 * Paper's current constructor still requires its deprecated DamageModifier enum. This one
 * visible compatibility reference is deliberately not suppressed or hidden with reflection.
 * Replacing this notification with entity.damage would change cancellation/knockback order.
 */
public final class BowDamageEvents {
    private BowDamageEvents() { throw new IllegalStateException("Utility class"); }

    /** Paper requires an immediate entity whenever a causing entity is supplied. */
    public static DamageSource explosionSource(Entity damager) {
        return DamageSource.builder(DamageType.EXPLOSION)
            .withDirectEntity(damager).withCausingEntity(damager).build();
    }

    public static EntityDamageByEntityEvent splash(Entity damager, Entity target, DamageSource source, double damage) {
        var base = EntityDamageEvent.DamageModifier.BASE;
        return new EntityDamageByEntityEvent(damager, target, EntityDamageEvent.DamageCause.ENTITY_EXPLOSION,
            source, new HashMap<>(Map.of(base, damage)),
            new HashMap<>(Map.of(base, Functions.constant(-0.0))), false);
    }
}
