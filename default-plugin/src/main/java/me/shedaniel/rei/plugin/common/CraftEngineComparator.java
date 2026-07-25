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

package me.shedaniel.rei.plugin.common;

import me.shedaniel.rei.api.common.entry.comparison.EntryComparator;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import org.jetbrains.annotations.ApiStatus;

import java.util.Optional;

/**
 * A global {@link EntryComparator} that distinguishes items produced by
 * <a href="https://github.com/Xiao-MoMi/craft-engine">CraftEngine</a>.
 * <p>
 * CraftEngine items reuse a vanilla base material (for example {@code minecraft:nether_brick})
 * and store their real identity inside the {@code minecraft:custom_data} component under the
 * flat string key {@code "craftengine:id"} (e.g. {@code "minez:rescue_radio"}).
 * <p>
 * In the {@link me.shedaniel.rei.api.common.entry.comparison.ComparisonContext#FUZZY FUZZY}
 * context REI normally treats every stack sharing the same {@link net.minecraft.world.item.Item}
 * as equal, which makes a CraftEngine item match every vanilla recipe of its base material
 * (and vice versa). Mixing the CraftEngine id into the fuzzy hash makes those stacks compare
 * unequal, so recipe lookups (the "U" usages / "R" recipes views) only return recipes that
 * actually involve the specific CraftEngine item.
 * <p>
 * In the EXACT context the full component map (which already contains the custom data) is
 * compared, so this comparator deliberately contributes nothing there and leaves exact
 * behaviour byte-for-byte identical to upstream.
 */
@ApiStatus.Internal
public final class CraftEngineComparator {
    /**
     * The flat key CraftEngine writes into the {@code minecraft:custom_data} compound.
     * Mirrors {@code net.momirealms.craftengine.core.item.processor.IdProcessor#CRAFT_ENGINE_ID}.
     * REI must not depend on CraftEngine, so the literal is duplicated here on purpose.
     */
    public static final String CRAFT_ENGINE_ID = "craftengine:id";
    
    public static final EntryComparator<ItemStack> INSTANCE = (context, stack) -> {
        // Only refine fuzzy matching; exact matching already accounts for the whole component map.
        if (context.isExact()) {
            return 0L;
        }
        CustomData customData = stack.get(DataComponents.CUSTOM_DATA);
        if (customData == null) {
            return 0L;
        }
        CompoundTag tag = customData.copyTag();
        Optional<String> id = tag.getString(CRAFT_ENGINE_ID);
        // Plain vanilla stacks have no CraftEngine id -> constant 0, so their hashing is unchanged.
        return id.isPresent() ? id.get().hashCode() : 0L;
    };
    
    private CraftEngineComparator() {
    }
}
