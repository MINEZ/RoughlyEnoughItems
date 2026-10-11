package me.shedaniel.rei.reisync;

import me.shedaniel.rei.api.client.gui.compat.GuiGraphics;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.renderer.state.gui.GuiRenderState;
import org.junit.jupiter.api.Test;
import sun.misc.Unsafe;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.*;

class GuiGraphicsScissorTest {
    private static class TestGraphics extends GuiGraphics {
        private TestGraphics() { super(null, new GuiRenderState(), 0, 0); }
        @Override public int guiWidth() { return 800; }
        @Override public int guiHeight() { return 450; }
    }

    private TestGraphics graphics() throws Exception {
        // 无需启动 Minecraft 或创建 GPU 资源，只验证兼容层和原版裁剪栈的配合。
        Field field = Unsafe.class.getDeclaredField("theUnsafe");
        field.setAccessible(true);
        TestGraphics graphics = (TestGraphics) ((Unsafe) field.get(null)).allocateInstance(TestGraphics.class);
        graphics.scissorStack = new GuiGraphicsExtractor.ScissorStack(new ScreenRectangle(0, 0, 800, 450));
        graphics.scissorStack.push(new ScreenRectangle(100, 100, 180, 80));
        return graphics;
    }

    @Test
    void submenuOutsideItsParentUsesTheFullScreenAndRestoresTheParent() throws Exception {
        TestGraphics graphics = graphics();
        var parent = graphics.scissorStack;
        ScreenRectangle submenu = new ScreenRectangle(280, 100, 200, 180);
        graphics.withFreshScissorStack(() -> {
            graphics.scissorStack.push(submenu);
            assertEquals(submenu, graphics.scissorStack.peek());
        });
        assertSame(parent, graphics.scissorStack);
        assertEquals(new ScreenRectangle(100, 100, 180, 80), parent.peek());
    }

    @Test
    void freshScissorsStillClipAtTheScreenEdge() throws Exception {
        TestGraphics graphics = graphics();
        graphics.withFreshScissorStack(() -> {
            graphics.scissorStack.push(new ScreenRectangle(780, 440, 100, 100));
            assertEquals(new ScreenRectangle(780, 440, 20, 10), graphics.scissorStack.peek());
        });
    }

    @Test
    void restoresTheParentWhenTheNestedRendererThrows() throws Exception {
        TestGraphics graphics = graphics();
        var parent = graphics.scissorStack;
        assertThrows(IllegalStateException.class, () -> graphics.withFreshScissorStack(() -> {
            throw new IllegalStateException("模拟子菜单绘制失败。");
        }));
        assertSame(parent, graphics.scissorStack);
    }
}
