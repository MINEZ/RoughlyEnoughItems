/*
 * This file is licensed under the MIT License, part of Roughly Enough Items.
 * Copyright (c) 2018, 2019, 2020, 2021, 2022, 2023 shedaniel
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package me.shedaniel.rei.impl.client.registry.display;

import com.google.common.base.Stopwatch;
import com.google.common.collect.Multimap;
import com.google.common.collect.Multimaps;
import com.mojang.serialization.DynamicOps;
import dev.architectury.event.EventResult;
import dev.architectury.event.events.client.ClientTickEvent;
import me.shedaniel.rei.api.client.plugins.REIClientPlugin;
import me.shedaniel.rei.api.client.registry.category.CategoryRegistry;
import me.shedaniel.rei.api.client.registry.display.DisplayCategory;
import me.shedaniel.rei.api.client.registry.display.DisplayRegistry;
import me.shedaniel.rei.api.client.registry.display.DynamicDisplayGenerator;
import me.shedaniel.rei.api.client.registry.display.reason.DisplayAdditionReason;
import me.shedaniel.rei.api.client.registry.display.visibility.DisplayVisibilityPredicate;
import me.shedaniel.rei.api.common.category.CategoryIdentifier;
import me.shedaniel.rei.api.common.display.Display;
import me.shedaniel.rei.api.common.plugins.PluginManager;
import me.shedaniel.rei.api.common.registry.ReloadStage;
import me.shedaniel.rei.impl.client.gui.widget.favorites.history.DisplayHistoryManager;
import me.shedaniel.rei.impl.client.ClientHelperImpl;
import me.shedaniel.rei.impl.common.InternalLogger;
import me.shedaniel.rei.impl.common.plugins.ReloadManagerImpl;
import me.shedaniel.rei.impl.common.registry.displays.AbstractDisplayRegistry;
import me.shedaniel.rei.impl.common.registry.displays.DisplayConsumerImpl;
import me.shedaniel.rei.impl.common.registry.displays.DisplaysHolderImpl;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.crafting.display.RecipeDisplayEntry;
import net.minecraft.world.item.crafting.display.RecipeDisplayId;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

public class DisplayRegistryImpl extends AbstractDisplayRegistry<REIClientPlugin, DisplayRegistryImpl.ClientDisplaysHolder> implements DisplayRegistry, DisplayConsumerImpl, DisplayGeneratorsRegistryImpl {
    public static final Object SYNCED = new Object();

    /** 标记本地回退配方，并保存用于内容去重的键。 */
    public record ClientFallbackOrigin(RecipeDisplayId id, @Nullable Tag key) {
    }
    private final Map<CategoryIdentifier<?>, List<DynamicDisplayGenerator<?>>> displayGenerators = new ConcurrentHashMap<>();
    private final List<DynamicDisplayGenerator<?>> globalDisplayGenerators = new ArrayList<>();
    private final List<DisplayVisibilityPredicate> visibilityPredicates = new ArrayList<>();
    private final Queue<Runnable> jobs = new ConcurrentLinkedQueue<>();
    private final ServerRecipeBook serverRecipeBook = new ServerRecipeBook();
    private long lastAddWarning = -1;
    
    public DisplayRegistryImpl() {
        super(ClientDisplaysHolder::new);
        
        int[] tick = {0};
        ClientTickEvent.CLIENT_POST.register(instance -> {
            if (tick[0]++ % 20 == 0 && !PluginManager.areAnyReloading() && ReloadManagerImpl.countRunningReloadTasks() == 0 && Minecraft.getInstance().getConnection() != null) {
                runJobs();
            }
        });
    }
    
    public void addJob(Runnable job) {
        this.jobs.add(job);
    }

    private void runJobs() {
        Runnable job;
        while ((job = this.jobs.poll()) != null) {
            try {
                job.run();
            } catch (Throwable throwable) {
                InternalLogger.getInstance().error("Failed to run job", throwable);
            }
        }
    }

    public void updateServerRecipeBook() {
        Minecraft client = Minecraft.getInstance();
        var connection = client.getConnection();
        if (connection == null || client.player == null || client.player.connection != connection) return;
        serverRecipeBook.update(connection, client.player.getRecipeBook());
        addJob(() -> {
            if (Minecraft.getInstance().getConnection() == connection && synchronizeServerRecipes()) {
                ClientRecipeFallback.injectInto(this);
            }
        });
    }

    public void clearServerRecipeBook() {
        serverRecipeBook.clear();
        jobs.clear();
    }

    private boolean synchronizeServerRecipes() {
        if (!ClientHelperImpl.getInstance().canUsePackets()) {
            return serverRecipeBook.synchronize(Minecraft.getInstance().getConnection(), this::removeRecipes, this::addRecipes);
        }
        return false;
    }
    
    @Override
    public void acceptPlugin(REIClientPlugin plugin) {
        plugin.registerDisplays(this);
    }
    
    @Override
    public boolean add(Display display, @Nullable Object origin) {
        if (!PluginManager.areAnyReloading()) {
            if (lastAddWarning < 0 || System.currentTimeMillis() - lastAddWarning > 5000) {
                InternalLogger.getInstance().warn("Detected runtime DisplayRegistry modification, this can be extremely dangerous!");
                InternalLogger.getInstance().debug("Detected runtime DisplayRegistry modification, this can be extremely dangerous!", new Throwable());
            }
            lastAddWarning = System.currentTimeMillis();
        }
        
        return DisplayValidator.validate(display) && super.add(display, origin);
    }
    
    @Override
    public List<DynamicDisplayGenerator<?>> globalDisplayGenerators() {
        return globalDisplayGenerators;
    }
    
    @Override
    public Map<CategoryIdentifier<?>, List<DynamicDisplayGenerator<?>>> categoryDisplayGenerators() {
        return displayGenerators;
    }
    
    @Override
    public void registerVisibilityPredicate(DisplayVisibilityPredicate predicate) {
        visibilityPredicates.add(predicate);
        visibilityPredicates.sort(Comparator.reverseOrder());
        InternalLogger.getInstance().debug("Added display visibility predicate: %s [%.2f priority]", predicate, predicate.getPriority());
    }
    
    @Override
    public boolean isDisplayVisible(Display display) {
        DisplayCategory<Display> category = (DisplayCategory<Display>) CategoryRegistry.getInstance().get(display.getCategoryIdentifier()).getCategory();
        return isDisplayVisible(category, display);
    }
    
    @Override
    public boolean isDisplayVisible(DisplayCategory<?> category, Display display) {
        if (category == null) throw new NullPointerException("Failed to resolve category: " + display.getCategoryIdentifier());
        for (DisplayVisibilityPredicate predicate : visibilityPredicates) {
            try {
                EventResult result = predicate.handleDisplay(category, display);
                if (result.interruptsFurtherEvaluation()) {
                    return result.isEmpty() || result.isTrue();
                }
            } catch (Throwable throwable) {
                InternalLogger.getInstance().error("Failed to check if the display is visible!", throwable);
            }
        }
        
        return true;
    }
    
    @Override
    public List<DisplayVisibilityPredicate> getVisibilityPredicates() {
        return Collections.unmodifiableList(visibilityPredicates);
    }
    
    @Override
    public void startReload() {
        super.startReload();
        serverRecipeBook.startReload();
        this.displayGenerators.clear();
        this.visibilityPredicates.clear();
    }
    
    @Override
    public void endReload() {
        // 先恢复服务端配方，再填充本地回退；完整重载不能丢掉自定义配方。
        synchronizeServerRecipes();
        // Synthesize recipe displays from the client's own data packs for servers that
        // do not sync recipe data (no-op unless the local-recipes fallback is active).
        ClientRecipeFallback.injectInto(this);

        InternalLogger.getInstance().debug("Found %d displays", size());
        
        for (CategoryIdentifier<?> identifier : getAll().keySet()) {
            if (CategoryRegistry.getInstance().tryGet(identifier).isEmpty()) {
                InternalLogger.getInstance().error("Found displays registered for unknown registry", new IllegalStateException(identifier.toString()));
            }
        }
        
        removeFailedDisplays();
        this.cache().endReload();
        InternalLogger.getInstance().debug("%d displays registration have completed", size());
        
        runJobs();
    }
    
    public void addRecipes(List<RecipeDisplayEntry> entries) {
        var connection = Minecraft.getInstance().getConnection();
        if (connection != null) {
            removeFallbackRecipesWithKeys(ServerRecipeBook.keys(entries,
                    connection.registryAccess().createSerializationContext(NbtOps.INSTANCE)));
        }
        Stopwatch stopwatch = Stopwatch.createStarted();
        int lastSize = size();
        if (!fillers().isEmpty()) {
            for (RecipeDisplayEntry entry : entries) {
                try {
                    for (Display display : tryFillDisplay(entry.display(), DisplayAdditionReason.RECIPE_MANAGER, DisplayAdditionReason.withId(entry.id()))) {
                        add(display, entry);
                    }
                } catch (Throwable e) {
                    InternalLogger.getInstance().error("Failed to fill display for recipe: %s [%s]", entry.display(), entry.id(), e);
                }
            }
        }
        InternalLogger.getInstance().debug("Filled %d displays from vanilla server in %s", size() - lastSize, stopwatch.stop());
    }

    /** 填充本地回退配方；与服务端已有配方按内容去重，不共用两侧的编号。 */
    public void addFallbackRecipes(List<RecipeDisplayEntry> entries, Set<RecipeDisplayId> excludeIds) {
        Stopwatch stopwatch = Stopwatch.createStarted();
        int lastSize = size();
        var connection = Minecraft.getInstance().getConnection();
        DynamicOps<Tag> ops = connection == null ? NbtOps.INSTANCE : connection.registryAccess().createSerializationContext(NbtOps.INSTANCE);
        Set<Tag> serverKeys = ClientHelperImpl.getInstance().canUsePackets()
                ? Set.of() : serverRecipeBook.keys(connection, ops);
        Set<RecipeDisplayId> existingIds = new HashSet<>();
        for (Object origin : holder().origins().values()) {
            if (origin instanceof ClientFallbackOrigin fallback) existingIds.add(fallback.id());
        }
        if (!fillers().isEmpty()) {
            for (RecipeDisplayEntry entry : entries) {
                if (excludeIds.contains(entry.id()) || existingIds.contains(entry.id())) {
                    continue;
                }
                try {
                    Tag key = ServerRecipeBook.key(entry.display(), ops).orElse(null);
                    if (key != null && serverKeys.contains(key)) continue;
                    ClientFallbackOrigin origin = new ClientFallbackOrigin(entry.id(), key);
                    for (Display display : tryFillDisplay(entry.display(), DisplayAdditionReason.RECIPE_MANAGER, DisplayAdditionReason.withId(entry.id()))) {
                        add(display, origin);
                    }
                } catch (Throwable e) {
                    InternalLogger.getInstance().error("Failed to fill local fallback display for recipe: %s [%s]", entry.display(), entry.id(), e);
                }
            }
        }
        InternalLogger.getInstance().info("[Local Recipes] Filled %d local fallback displays in %s", size() - lastSize, stopwatch.stop());
    }

    public void removeRecipes(Set<RecipeDisplayId> ids) {
        List<Display> toRemove = new LinkedList<>();
        WeakHashMap<Display, Object> origins = this.holder().origins();
        for (Map.Entry<Display, Object> entry : origins.entrySet()) {
            if (entry.getValue() instanceof RecipeDisplayEntry displayEntry) {
                if (ids.contains(displayEntry.id())) {
                    toRemove.add(entry.getKey());
                }
            }
        }
        
        for (Display display : toRemove) {
            this.holder().remove(display);
        }
    }
    
    public void removeSyncedRecipes() {
        List<Display> toRemove = new LinkedList<>();
        WeakHashMap<Display, Object> origins = this.holder().origins();
        for (Map.Entry<Display, Object> entry : origins.entrySet()) {
            if (entry.getValue() == SYNCED) {
                toRemove.add(entry.getKey());
            }
        }
        
        for (Display display : toRemove) {
            this.holder().remove(display);
        }
    }

    public void removeFallbackRecipes() {
        List<Display> toRemove = new LinkedList<>();
        WeakHashMap<Display, Object> origins = this.holder().origins();
        for (Map.Entry<Display, Object> entry : origins.entrySet()) {
            if (entry.getValue() instanceof ClientFallbackOrigin) {
                toRemove.add(entry.getKey());
            }
        }

        for (Display display : toRemove) {
            this.holder().remove(display);
        }
    }

    private void removeFallbackRecipesWithKeys(Set<Tag> keys) {
        if (keys.isEmpty()) {
            return;
        }
        List<Display> toRemove = new LinkedList<>();
        WeakHashMap<Display, Object> origins = this.holder().origins();
        for (Map.Entry<Display, Object> entry : origins.entrySet()) {
            if (entry.getValue() instanceof ClientFallbackOrigin origin && origin.key() != null && keys.contains(origin.key())) {
                toRemove.add(entry.getKey());
            }
        }
        InternalLogger.getInstance().info("[Local Recipes] Removing %d overlapping fallback displays superseded by server recipe add", toRemove.size());

        for (Display display : toRemove) {
            this.holder().remove(display);
        }
    }

    private void removeFailedDisplays() {
        Multimap<CategoryIdentifier<?>, Display> failedDisplays = Multimaps.newListMultimap(new HashMap<>(), ArrayList::new);
        for (List<Display> displays : getAll().values()) {
            for (Display display : displays) {
                if (!DisplayValidator.validate(display)) {
                    failedDisplays.put(display.getCategoryIdentifier(), display);
                }
            }
        }
        
        InternalLogger.getInstance().debug("Removing %d failed displays" + (!failedDisplays.isEmpty() ? ":" : ""), failedDisplays.size());
        failedDisplays.asMap().entrySet().stream()
                .sorted(Comparator.comparing(entry -> entry.getKey().toString()))
                .forEach(entry -> {
                    InternalLogger.getInstance().debug("- %s: %d failed display" + (entry.getValue().size() == 1 ? "" : "s"), entry.getKey(), entry.getValue().size());
                    for (Display display : entry.getValue()) {
                        this.holder().remove(display);
                    }
                });
    }
    
    @Override
    public void postStage(ReloadStage stage) {
        if (stage != ReloadStage.END) return;
        InternalLogger.getInstance().debug("Registered displays report (%d displays, %d cached / %d not cached)" + (size() > 0 ? ":" : ""),
                size(), cache().cachedSize(), cache().notCachedSize());
        getAll().entrySet().stream()
                .sorted(Comparator.comparing(entry -> entry.getKey().toString()))
                .forEach(entry -> {
                    InternalLogger.getInstance().debug("- %s: %d display" + (entry.getValue().size() == 1 ? "" : "s"), entry.getKey(), entry.getValue().size());
                });
    }
    
    public DisplayCache cache() {
        return holder().cache;
    }
    
    public static class ClientDisplaysHolder extends DisplaysHolderImpl.ByKey {
        private final DisplayCache cache = new DisplayCacheImpl(false);
        
        @Override
        public void add(Display display, @Nullable Object origin) {
            super.add(display, origin);
            this.cache.add(display);
        }
        
        @Override
        public boolean remove(Display display) {
            if (super.remove(display)) {
                this.cache.remove(display);
                return true;
            }
            
            return false;
        }
        
        @Override
        @Nullable
        public Object getDisplayOrigin(Display display) {
            Object origin = super.getDisplayOrigin(display);
            if (origin != null) {
                return origin;
            }
            
            return DisplayHistoryManager.INSTANCE.getPossibleOrigin(this, display);
        }
        
        @Override
        protected boolean checkCategory(CategoryIdentifier<?> key) {
            return CategoryRegistry.getInstance().tryGet(key).isPresent();
        }
        
        private WeakHashMap<Display, Object> origins() {
            return this.originsMap;
        }
    }
}
