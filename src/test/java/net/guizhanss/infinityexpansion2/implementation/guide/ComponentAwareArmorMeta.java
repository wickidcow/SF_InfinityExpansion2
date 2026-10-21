package net.guizhanss.infinityexpansion2.implementation.guide;

import io.papermc.paper.registry.set.RegistryKeySet;
import java.util.ArrayList;
import org.bukkit.damage.DamageType;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.components.CustomModelDataComponent;
import org.mockbukkit.mockbukkit.inventory.meta.ArmorMetaMock;
import org.mockbukkit.mockbukkit.inventory.meta.components.CustomModelDataComponentMock;

/** Copies the model components omitted by MockBukkit 4.110's metadata copy constructor. */
final class ComponentAwareArmorMeta extends ArmorMetaMock {
    ComponentAwareArmorMeta() {
        super();
    }

    ComponentAwareArmorMeta(ItemMeta source) {
        super(source);
        if (source.hasItemModel()) {
            setItemModel(source.getItemModel());
        }
        if (source.hasCustomModelDataComponent()) {
            CustomModelDataComponent previous = source.getCustomModelDataComponent();
            CustomModelDataComponent copy = CustomModelDataComponentMock.useDefault();
            copy.setFloats(new ArrayList<>(previous.getFloats()));
            copy.setFlags(new ArrayList<>(previous.getFlags()));
            copy.setStrings(new ArrayList<>(previous.getStrings()));
            copy.setColors(new ArrayList<>(previous.getColors()));
            setCustomModelDataComponent(copy);
        }
    }

    @Override
    public ArmorMetaMock clone() {
        return new ComponentAwareArmorMeta(this);
    }

    // Paper 26.3 added these ItemMeta methods after MockBukkit 4.110. Java permits
    // the same test fixture to compile against both APIs without a conditional
    // Kotlin override. Omit @Override because the 1.21.11 interface lacks them.
    // They are outside this fixture's model-copy scope: fail explicitly if used.
    public RegistryKeySet<DamageType> getDamageResistantTypes() {
        throw new UnsupportedOperationException("MockBukkit 4.110 does not model damage-resistant types");
    }

    public void setDamageResistantTypes(RegistryKeySet<DamageType> types) {
        throw new UnsupportedOperationException("MockBukkit 4.110 does not model damage-resistant types");
    }
}
