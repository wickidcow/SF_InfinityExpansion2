package net.guizhanss.infinityexpansion2.utils.events;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import org.bukkit.damage.DamageSource;
import org.bukkit.entity.Entity;
import org.bukkit.entity.HumanEntity;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@SuppressWarnings({"deprecation", "removal"}) // Old constructor behavior is the test oracle only.
class GameplayEventCompatibilityTest {
    @ParameterizedTest @ValueSource(doubles={0.0,1.0,12.5,32767.0})
    void bowPreflightPreservesCancellationDamageAndNoncriticalSemantics(double damage) {
        var attacker=mock(Entity.class); var victim=mock(Entity.class); var source=mock(DamageSource.class);
        var old=new EntityDamageByEntityEvent(attacker,victim,EntityDamageEvent.DamageCause.ENTITY_EXPLOSION,source,damage);
        var current=BowDamageEvents.splash(attacker,victim,source,damage);
        assertSame(attacker,current.getDamager()); assertSame(victim,current.getEntity());
        assertSame(source,current.getDamageSource()); assertEquals(old.getCause(),current.getCause());
        assertEquals(old.isCritical(),current.isCritical()); assertFalse(current.isCritical());
        for(var modifier:EntityDamageEvent.DamageModifier.values()) {
            assertEquals(old.isApplicable(modifier),current.isApplicable(modifier));
            assertEquals(old.getOriginalDamage(modifier),current.getOriginalDamage(modifier));
        }
        assertEquals(old.getDamage(),current.getDamage()); assertEquals(old.getFinalDamage(),current.getFinalDamage());
        old.setDamage(damage+3.5); current.setDamage(damage+3.5);
        assertEquals(old.getFinalDamage(),current.getFinalDamage());
        old.setCancelled(true); current.setCancelled(true); assertEquals(old.isCancelled(),current.isCancelled());
        verifyNoInteractions(attacker,victim,source);
    }

    @ParameterizedTest @ValueSource(ints={-1,0,1,19,20,25})
    void hungerChangeRetainsClampingNoItemAndCancellation(int level) {
        var player=mock(HumanEntity.class);
        var old=new FoodLevelChangeEvent(player,level);
        var current=new FoodLevelChangeEvent(player,level,null);
        assertSame(player,current.getEntity()); assertNull(current.getItem());
        assertEquals(old.getFoodLevel(),current.getFoodLevel());
        old.setCancelled(true); current.setCancelled(true); assertEquals(old.isCancelled(),current.isCancelled());
        old.setFoodLevel(7); current.setFoodLevel(7); assertEquals(old.getFoodLevel(),current.getFoodLevel());
        verifyNoInteractions(player);
    }
}
