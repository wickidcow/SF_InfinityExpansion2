package net.guizhanss.infinityexpansion2.implementation.items.storage;

import java.lang.reflect.Modifier;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Match the public no-argument JVM contract used by Networks' lazy storage adapter. */
class StorageUnitTransportApiTest {
    @Test
    void transportSlotAccessorsArePublicOnTheCompiledKotlinClass() throws Exception {
        // Avoid initializing item-registration singletons; inspect the compiled
        // class, not a source substring which misses inherited Kotlin visibility.
        Class<?> storage = Class.forName(StorageUnit.class.getName(), false,
            StorageUnitTransportApiTest.class.getClassLoader());
        for (String name : new String[]{"getInputSlots", "getOutputSlots"}) {
            var method = storage.getMethod(name);
            assertTrue(Modifier.isPublic(method.getModifiers()), name);
            assertFalse(Modifier.isStatic(method.getModifiers()), name);
            assertEquals(int[].class, method.getReturnType(), name);
            assertEquals(0, method.getParameterCount(), name);
        }
        assertEquals(int.class, storage.getMethod("getCapacity").getReturnType());
    }
}
