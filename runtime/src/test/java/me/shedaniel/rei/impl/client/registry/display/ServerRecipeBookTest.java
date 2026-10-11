package me.shedaniel.rei.impl.client.registry.display;

import net.minecraft.SharedConstants;
import net.minecraft.client.ClientRecipeBook;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.crafting.RecipeBookCategories;
import net.minecraft.world.item.crafting.display.*;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class ServerRecipeBookTest {
    private final Object connection = new Object();
    private final ClientRecipeBook book = new ClientRecipeBook();
    private final ServerRecipeBook state = new ServerRecipeBook();
    private final RecordingDisplays displays = new RecordingDisplays();

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void restoresCustomRecipeInputsAndOutputsAfterTheDisplayRegistryReloads() {
        RecipeDisplayEntry recipe = recipe(41, "minez:ore", "minez:ruby");
        publish(recipe);
        synchronize();
        displays.entries.clear();
        state.startReload();
        synchronize();

        RecipeDisplayEntry restored = displays.entries.get(recipe.id());
        assertSame(recipe, restored);
        var display = (ShapelessCraftingRecipeDisplay) restored.display();
        assertEquals("minez:ore", customId(display.ingredients().getFirst()));
        assertEquals("minez:ruby", customId(display.result()));
    }

    @Test
    void repeatedRecipeBookMessagesDoNotAddTheSameServerDisplayAgain() {
        RecipeDisplayEntry recipe = recipe(4, "minez:ore", "minez:ruby");
        publish(recipe);
        synchronize();
        publish(recipe);
        assertFalse(synchronize());
        assertEquals(1, displays.added);
    }

    @Test
    void removedRecipesStayRemovedAfterAnotherReload() {
        RecipeDisplayEntry recipe = recipe(5, "minez:ore", "minez:ruby");
        publish(recipe);
        synchronize();
        book.remove(recipe.id());
        publish();
        synchronize();
        assertTrue(displays.entries.isEmpty());
        state.startReload();
        synchronize();
        assertTrue(displays.entries.isEmpty());
    }

    @Test
    void replacingTheVanillaRecipeBookDropsRecipesMissingFromTheNewBook() {
        RecipeDisplayEntry old = recipe(1, "minez:ore", "minez:ruby");
        RecipeDisplayEntry replacement = recipe(8, "minez:dust", "minez:gem");
        publish(old);
        synchronize();
        book.clear();
        publish(replacement);
        synchronize();
        assertEquals(Set.of(replacement.id()), displays.entries.keySet());
        state.startReload();
        displays.entries.clear();
        synchronize();
        assertEquals(Set.of(replacement.id()), displays.entries.keySet());
    }

    @Test
    void anEmptyReplacementClearsTheSnapshot() {
        publish(recipe(1, "minez:ore", "minez:ruby"));
        synchronize();
        book.clear();
        publish();
        synchronize();
        assertTrue(displays.entries.isEmpty());
        state.startReload();
        assertFalse(synchronize());
    }

    @Test
    void recipeUpdatesReplaceTheOldDisplayUnderTheSameServerId() {
        RecipeDisplayEntry old = recipe(2, "minez:ore", "minez:ruby");
        RecipeDisplayEntry replacement = recipe(2, "minez:dust", "minez:gem");
        publish(old);
        synchronize();
        publish(replacement);
        synchronize();
        assertEquals(1, displays.entries.size());
        assertSame(replacement, displays.entries.get(old.id()));
        assertEquals(2, displays.added);
    }

    @Test
    void queuedWorkUsesTheLatestCompleteSnapshot() {
        RecipeDisplayEntry removed = recipe(1, "minez:ore", "minez:ruby");
        RecipeDisplayEntry retained = recipe(2, "minez:dust", "minez:gem");
        publish(removed, retained);
        book.remove(removed.id());
        publish();
        synchronize();
        assertEquals(Set.of(retained.id()), displays.entries.keySet());
    }

    @Test
    void leavingAndJoiningAnotherServerDoesNotReuseTheOldSnapshot() {
        publish(recipe(1, "minez:ore", "minez:ruby"));
        synchronize();
        state.clear();
        Object nextConnection = new Object();
        state.startReload();
        displays.entries.clear();
        assertFalse(state.synchronize(nextConnection, displays::remove, displays::add));

        ClientRecipeBook nextBook = new ClientRecipeBook();
        RecipeDisplayEntry nextRecipe = recipe(1, "minez:dust", "minez:gem");
        nextBook.add(nextRecipe);
        nextBook.rebuildCollections();
        state.update(nextConnection, nextBook);
        state.synchronize(nextConnection, displays::remove, displays::add);
        assertSame(nextRecipe, displays.entries.get(nextRecipe.id()));
        assertTrue(state.keys(connection, ops()).isEmpty());
    }

    @Test
    void identicalDisplaysHaveTheSameKeyEvenWhenTheirIdsDiffer() {
        RecipeDisplayEntry server = recipe(9, "minez:ore", "minez:ruby");
        RecipeDisplayEntry local = recipe(200, "minez:ore", "minez:ruby");
        assertEquals(key(server), key(local));
    }

    @Test
    void identicalIdsDoNotHideDifferentLocalRecipesOrCraftEngineItems() {
        RecipeDisplayEntry server = recipe(9, "minez:ore", "minez:ruby");
        RecipeDisplayEntry other = recipe(9, "minez:ore", "minez:gem");
        publish(server);
        Set<net.minecraft.nbt.Tag> serverKeys = state.keys(connection, ops());
        assertTrue(serverKeys.contains(key(server)));
        assertFalse(serverKeys.contains(key(other)));
    }

    @Test
    void aCustomItemDoesNotDeduplicateItsPlainVanillaBaseRecipe() {
        RecipeDisplayEntry custom = recipe(9, "minez:ore", "minez:ruby");
        SlotDisplay plain = new SlotDisplay.ItemStackSlotDisplay(new ItemStackTemplate(Items.PAPER));
        RecipeDisplayEntry vanilla = new RecipeDisplayEntry(new RecipeDisplayId(9),
                new ShapelessCraftingRecipeDisplay(List.of(plain), plain, SlotDisplay.Empty.INSTANCE),
                OptionalInt.empty(), RecipeBookCategories.CRAFTING_MISC, Optional.empty());
        assertNotEquals(key(custom), key(vanilla));
    }

    @Test
    void theCapturedSnapshotDoesNotFollowLaterUnpublishedBookMutations() {
        RecipeDisplayEntry recipe = recipe(1, "minez:ore", "minez:ruby");
        publish(recipe);
        book.clear();
        book.rebuildCollections();
        synchronize();
        assertSame(recipe, displays.entries.get(recipe.id()));
        publish();
        synchronize();
        assertTrue(displays.entries.isEmpty());
    }

    private void publish(RecipeDisplayEntry... entries) {
        for (RecipeDisplayEntry entry : entries) book.add(entry);
        book.rebuildCollections();
        state.update(connection, book);
    }

    private boolean synchronize() {
        return state.synchronize(connection, displays::remove, displays::add);
    }

    private static RecipeDisplayEntry recipe(int id, String input, String result) {
        return new RecipeDisplayEntry(new RecipeDisplayId(id),
                new ShapelessCraftingRecipeDisplay(List.of(slot(input)), slot(result), SlotDisplay.Empty.INSTANCE),
                OptionalInt.empty(), RecipeBookCategories.CRAFTING_MISC, Optional.empty());
    }

    private static SlotDisplay slot(String id) {
        CompoundTag tag = new CompoundTag();
        tag.putString("craftengine:id", id);
        var patch = DataComponentPatch.builder().set(DataComponents.CUSTOM_DATA, CustomData.of(tag)).build();
        return new SlotDisplay.ItemStackSlotDisplay(new ItemStackTemplate(Items.PAPER, patch));
    }

    private static String customId(SlotDisplay slot) {
        return ((SlotDisplay.ItemStackSlotDisplay) slot).stack().components()
                .get(DataComponentMap.EMPTY, DataComponents.CUSTOM_DATA)
                .copyTag().getString("craftengine:id").orElseThrow();
    }

    private static com.mojang.serialization.DynamicOps<net.minecraft.nbt.Tag> ops() {
        return RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY).createSerializationContext(NbtOps.INSTANCE);
    }

    private static net.minecraft.nbt.Tag key(RecipeDisplayEntry entry) {
        return ServerRecipeBook.key(entry.display(), ops()).orElseThrow();
    }

    private static final class RecordingDisplays {
        private final Map<RecipeDisplayId, RecipeDisplayEntry> entries = new LinkedHashMap<>();
        private int added;

        private void remove(Set<RecipeDisplayId> ids) {
            ids.forEach(entries::remove);
        }

        private void add(List<RecipeDisplayEntry> entries) {
            added += entries.size();
            for (RecipeDisplayEntry entry : entries) this.entries.put(entry.id(), entry);
        }
    }
}
