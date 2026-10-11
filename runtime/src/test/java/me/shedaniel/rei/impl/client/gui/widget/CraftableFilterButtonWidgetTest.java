package me.shedaniel.rei.impl.client.gui.widget;

import me.shedaniel.math.Rectangle;
import me.shedaniel.rei.api.client.gui.compat.GuiGraphics;
import net.minecraft.SharedConstants;
import net.minecraft.client.renderer.state.gui.GuiRenderState;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.Test;
import sun.misc.Unsafe;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.*;

class CraftableFilterButtonWidgetTest {
    private static class RecordingGraphics extends GuiGraphics {
        private ItemStack rendered;
        private int x;
        private int y;
        private int calls;

        private RecordingGraphics() { super(null, new GuiRenderState(), 0, 0); }

        @Override
        public void renderItem(ItemStack stack, int x, int y) {
            this.rendered = stack;
            this.x = x;
            this.y = y;
            calls++;
        }
    }

    @Test
    void defersTheRealItemStackUntilMinecraftHasBoundItsComponents() throws Exception {
        var renderer = assertDoesNotThrow(() -> CraftableFilterButtonWidget.createIconRenderer(new Rectangle(10, 20, 20, 20)));
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();

        // 使用原版注册表的真实未绑定状态，复现启动日志中的异常。
        var holder = Items.CRAFTING_TABLE.builtInRegistryHolder();
        assertFalse(holder.areComponentsBound());
        var failure = assertThrows(NullPointerException.class, () -> new ItemStack(Items.CRAFTING_TABLE));
        assertEquals("Components not bound yet", failure.getMessage());

        Field field = Unsafe.class.getDeclaredField("theUnsafe");
        field.setAccessible(true);
        RecordingGraphics graphics = (RecordingGraphics) ((Unsafe) field.get(null)).allocateInstance(RecordingGraphics.class);
        assertDoesNotThrow(() -> renderer.render(graphics, 0, 0, 0));
        assertEquals(0, graphics.calls);

        // 只绑定测试图标的组件，无需启动客户端或创建 GPU 资源。
        holder.bindComponents(DataComponentMap.EMPTY);
        renderer.render(graphics, 0, 0, 0);
        assertSame(Items.CRAFTING_TABLE, graphics.rendered.getItem());
        assertEquals(12, graphics.x);
        assertEquals(22, graphics.y);
        ItemStack first = graphics.rendered;
        renderer.render(graphics, 0, 0, 0);
        assertSame(first, graphics.rendered);
        assertEquals(2, graphics.calls);
    }
}
