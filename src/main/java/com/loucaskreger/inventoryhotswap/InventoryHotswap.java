package com.loucaskreger.inventoryhotswap;

import org.lwjgl.glfw.GLFW;

import com.loucaskreger.inventoryhotswap.config.Config;
import com.loucaskreger.inventoryhotswap.config.HudTypes;
import com.mojang.blaze3d.platform.InputConstants;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.fabricmc.fabric.api.event.client.player.ClientHotbarScrollEvents;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.KeyMapping.Category;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.ItemStack;

public class InventoryHotswap implements ClientModInitializer {

    public static final String MOD_ID = "inventoryhotswap";

    private static final int[] ROW_OFFSETS = { 0, 27, 18, 9 };
    private static final int[] INVENTORY_ROW_OFFSETS_TOP_TO_BOTTOM = { 9, 18, 27 };
    private static final int[] FULL_BAR_OFFSETS_TOP_TO_BOTTOM = { 9, 18, 27, 0 };
    private static final int[] VISUAL_ROW_FOR_SELECTION = { 3, 2, 1, 0 };
    private static final int TEXTURE_SIZE = 256;
    private static final int HOTBAR_WIDTH = 182;
    private static final int HOTBAR_HEIGHT = 22;
    private static final int PANEL_WIDTH = 22;
    private static final int HOTBAR_SELECTION_WIDTH = 24;
    private static final int HOTBAR_SELECTION_HEIGHT = 23;
    private static final int LARGE_SELECTION_WIDTH = 184;
    private static final int LARGE_SELECTION_HEIGHT = 24;
    private static final int ITEM_X_OFFSET = 2;
    private static final int ITEM_Y_OFFSET = 3;
    private static final int VERTICAL_BAR_X_OFFSET = -1;
    private static final int VERTICAL_SELECTION_X_OFFSET = -2;
    private static final int FULL_BAR_X_OFFSET = -1;
    private static final int FULL_SELECTION_X_OFFSET = -2;

    public static int accumulatedScrollDelta = 0;
    private static int currentIndex;

    public static boolean renderStatusBars = true;
    public static boolean renderPushedStatusBars = false;
    public static boolean renderMountInfo = true;
    public static boolean heldItemTooltips = true;
    private static boolean renderEntireBar = false;
    public static boolean wasKeyDown = false;

    public static final int HEIGHT = 66;

    private static final Minecraft MC = Minecraft.getInstance();

    private static final Identifier VERTICAL_BAR_TEXTURE = Identifier.fromNamespaceAndPath(MOD_ID,
            "textures/gui/verticalbar.png");
    private static final Identifier LARGE_BAR_SELECTION_TEXTURE = Identifier.fromNamespaceAndPath(MOD_ID,
            "textures/gui/largebarselection.png");
    private static final Identifier HOTBAR_TEXTURE = Identifier.fromNamespaceAndPath("minecraft",
            "textures/gui/sprites/hud/hotbar.png");
    private static final Identifier HOTBAR_SELECTION_TEXTURE = Identifier.fromNamespaceAndPath("minecraft",
            "textures/gui/sprites/hud/hotbar_selection.png");
    private static final Identifier HUD_ELEMENT_ID = Identifier.fromNamespaceAndPath(MOD_ID, "hud_overlay");
    private static final String KEY_VERTICAL_SCROLL = "key." + MOD_ID + ".verticalscroll";
    private static final String KEY_FULL_BAR_SCROLL = "key." + MOD_ID + ".fullBarVerticalScroll";

    private static final Category CATEGORY = Category.register(
            Identifier.fromNamespaceAndPath(MOD_ID, "keys"));

    public static final KeyMapping verticalScroll = KeyMappingHelper.registerKeyMapping(new KeyMapping(
            KEY_VERTICAL_SCROLL, InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_LEFT_ALT, CATEGORY));

    private static final KeyMapping fullBarVerticalScroll = KeyMappingHelper.registerKeyMapping(
            new KeyMapping(KEY_FULL_BAR_SCROLL, InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_Z,
                    CATEGORY));

    @Override
    public void onInitializeClient() {
        Config.init();
        registerHudElements();
        registerScrollEvents();
        registerClientTick();
    }

    private static void registerHudElements() {
        HudElementRegistry.replaceElement(VanillaHudElements.HOTBAR,
                vanilla -> (graphics, delta) -> {
                    if (CustomHudRenderer.renderHotbar) {
                        vanilla.extractRenderState(graphics, delta);
                    }
                });

        wrapPushElement(VanillaHudElements.INFO_BAR, true, true);
        wrapPushElement(VanillaHudElements.EXPERIENCE_LEVEL, true, false);

        HudElementRegistry.replaceElement(VanillaHudElements.HELD_ITEM_TOOLTIP,
                vanilla -> (graphics, delta) -> {
                    if (heldItemTooltips) {
                        vanilla.extractRenderState(graphics, delta);
                    }
                });

        wrapStatusElement(VanillaHudElements.HEALTH_BAR);
        wrapStatusElement(VanillaHudElements.ARMOR_BAR);
        wrapStatusElement(VanillaHudElements.FOOD_BAR);
        wrapStatusElement(VanillaHudElements.AIR_BAR);

        HudElementRegistry.replaceElement(VanillaHudElements.MOUNT_HEALTH,
                vanilla -> (graphics, delta) -> {
                    if (renderMountInfo) {
                        if (renderPushedStatusBars) {
                            graphics.pose().pushMatrix();
                            graphics.pose().translate(0, -HEIGHT);
                            vanilla.extractRenderState(graphics, delta);
                            graphics.pose().popMatrix();
                            return;
                        }

                        vanilla.extractRenderState(graphics, delta);
                    }
                });

        HudElementRegistry.attachElementAfter(VanillaHudElements.HOTBAR, HUD_ELEMENT_ID,
                InventoryHotswap::renderHudOverlay);
    }

    private static void wrapPushElement(Identifier elementId, boolean respectsExpToggle, boolean respectsStatusToggle) {
        HudElementRegistry.replaceElement(elementId,
                vanilla -> (graphics, delta) -> {
                    if (respectsExpToggle && !CustomHudRenderer.renderExp) {
                        return;
                    }

                    if (respectsStatusToggle && !renderStatusBars) {
                        return;
                    }

                    if (renderPushedStatusBars) {
                        graphics.pose().pushMatrix();
                        graphics.pose().translate(0, -HEIGHT);
                        vanilla.extractRenderState(graphics, delta);
                        graphics.pose().popMatrix();
                        return;
                    }

                    vanilla.extractRenderState(graphics, delta);
                });
    }

    private static void wrapStatusElement(Identifier elementId) {
        HudElementRegistry.replaceElement(elementId,
                vanilla -> (graphics, delta) -> {
                    if (!renderStatusBars) {
                        return;
                    }

                    if (renderPushedStatusBars) {
                        graphics.pose().pushMatrix();
                        graphics.pose().translate(0, -HEIGHT);
                        vanilla.extractRenderState(graphics, delta);
                        graphics.pose().popMatrix();
                        return;
                    }

                    vanilla.extractRenderState(graphics, delta);
                });
    }

    private static void registerScrollEvents() {
        ClientHotbarScrollEvents.ALLOW.register((inventory, oldSelectedSlot, newSelectedSlot, horizontal, vertical) -> {
            if (MC.screen != null) {
                return true;
            }

            return !onMouseScroll(vertical);
        });
    }

    private static void renderHudOverlay(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
        if (!wasKeyDown) {
            return;
        }

        LocalPlayer player = MC.player;
        if (player == null) {
            return;
        }

        int selectedSlot = player.getInventory().getSelectedSlot();
        int centerX = graphics.guiWidth() / 2;
        int hotbarX = centerX - 90;
        int hotbarY = graphics.guiHeight() - 22;

        if (renderEntireBar) {
            renderStackedHotbars(graphics, player, hotbarX, hotbarY);
        } else {
            renderVerticalColumn(graphics, player, selectedSlot, hotbarX, hotbarY);
        }
    }

    private static void renderVerticalColumn(GuiGraphicsExtractor graphics, LocalPlayer player, int selectedSlot,
            int hotbarX, int hotbarY) {
        int x = hotbarX + selectedSlot * 20;
        int panelTop = hotbarY - HEIGHT;
        blitTexture(graphics, HOTBAR_TEXTURE, hotbarX + FULL_BAR_X_OFFSET, hotbarY, HOTBAR_WIDTH, HOTBAR_HEIGHT,
                0, 0, HOTBAR_WIDTH, HOTBAR_HEIGHT);
        renderInventoryRow(graphics, player, hotbarX, hotbarY, 0);
        blitTexture(graphics, VERTICAL_BAR_TEXTURE, x + VERTICAL_BAR_X_OFFSET, panelTop, PANEL_WIDTH, HEIGHT, 0, 0);

        for (int row = 0; row < 3; row++) {
            int inventoryIndex = selectedSlot + INVENTORY_ROW_OFFSETS_TOP_TO_BOTTOM[row];
            ItemStack stack = player.getInventory().getItem(inventoryIndex);
            int itemX = x + ITEM_X_OFFSET;
            int itemY = panelTop + (row * 22) + ITEM_Y_OFFSET;
            graphics.item(stack, itemX, itemY);
            graphics.itemDecorations(MC.font, stack, itemX, itemY);
        }

        int selectorY = panelTop + (VISUAL_ROW_FOR_SELECTION[accumulatedScrollDelta] * 22) - 1;
        blitTexture(graphics, HOTBAR_SELECTION_TEXTURE, x + VERTICAL_SELECTION_X_OFFSET, selectorY,
                HOTBAR_SELECTION_WIDTH, HOTBAR_SELECTION_HEIGHT,
                0, 0, HOTBAR_SELECTION_WIDTH, HOTBAR_SELECTION_HEIGHT);
    }

    private static void renderStackedHotbars(GuiGraphicsExtractor graphics, LocalPlayer player, int hotbarX,
            int hotbarY) {
        int panelTop = hotbarY - HEIGHT;

        for (int row = 0; row < 4; row++) {
            int y = panelTop + (row * 22);
            blitTexture(graphics, HOTBAR_TEXTURE, hotbarX + FULL_BAR_X_OFFSET, y, HOTBAR_WIDTH, HOTBAR_HEIGHT,
                    0, 0, HOTBAR_WIDTH, HOTBAR_HEIGHT);
            renderInventoryRow(graphics, player, hotbarX, y, FULL_BAR_OFFSETS_TOP_TO_BOTTOM[row]);
        }

        int selectorY = panelTop + (VISUAL_ROW_FOR_SELECTION[accumulatedScrollDelta] * 22) - 1;
        blitTexture(graphics, LARGE_BAR_SELECTION_TEXTURE, hotbarX + FULL_SELECTION_X_OFFSET, selectorY,
                LARGE_SELECTION_WIDTH, LARGE_SELECTION_HEIGHT,
                0, 0, TEXTURE_SIZE, TEXTURE_SIZE);
    }

    private static void renderInventoryRow(GuiGraphicsExtractor graphics, LocalPlayer player, int hotbarX, int rowY,
            int slotBase) {
        for (int column = 0; column < 9; column++) {
            int invIndex = slotBase + column;
            ItemStack stack = player.getInventory().getItem(invIndex);
            int x = hotbarX + (column * 20) + ITEM_X_OFFSET;
            int y = rowY + ITEM_Y_OFFSET;
            graphics.item(stack, x, y);
            graphics.itemDecorations(MC.font, stack, x, y);
        }
    }

    private static void blitTexture(GuiGraphicsExtractor graphics, Identifier texture, int x, int y, int width,
            int height, int u, int v, int textureWidth, int textureHeight) {
        graphics.blit(RenderPipelines.GUI_TEXTURED, texture, x, y, (float) u, (float) v, width, height,
                textureWidth, textureHeight);
    }

    private static void blitTexture(GuiGraphicsExtractor graphics, Identifier texture, int x, int y, int width,
            int height, int u, int v) {
        blitTexture(graphics, texture, x, y, width, height, u, v, TEXTURE_SIZE, TEXTURE_SIZE);
    }

    public static boolean onMouseScroll(double scrollDelta) {
        if (!wasKeyDown) {
            return false;
        }

        int direction = (int) Math.signum(scrollDelta);
        if (direction == 0) {
            return false;
        }

        int adjustedDirection = Config.INSTANCE.inverted ? -direction : direction;
        accumulatedScrollDelta = Math.floorMod(accumulatedScrollDelta + adjustedDirection, 4);
        return true;
    }

    public static boolean isSwapModifierActive() {
        return MC.screen == null && verticalScroll.isDown();
    }

    public static boolean handleHotbarSelectionKey(KeyEvent keyEvent) {
        if (!isSwapModifierActive()) {
            return false;
        }

        for (int hotbarSlot = 0; hotbarSlot < 3; hotbarSlot++) {
            if (MC.options.keyHotbarSlots[hotbarSlot].matches(keyEvent)) {
                accumulatedScrollDelta = mapNumberKeyToRow(hotbarSlot + 1);
                return true;
            }
        }

        return false;
    }

    private static void registerClientTick() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            MultiPlayerGameMode interactionManager = client.gameMode;
            LocalPlayer player = client.player;
            if (player == null || interactionManager == null) {
                return;
            }

            if (wasKeyDown) {
                if (MC.options.keyHotbarSlots[0].consumeClick()) {
                    accumulatedScrollDelta = mapNumberKeyToRow(1);
                }
                if (MC.options.keyHotbarSlots[1].consumeClick()) {
                    accumulatedScrollDelta = mapNumberKeyToRow(2);
                }
                if (MC.options.keyHotbarSlots[2].consumeClick()) {
                    accumulatedScrollDelta = mapNumberKeyToRow(3);
                }
            }

            if (verticalScroll.consumeClick() && MC.screen == null) {
                HudTypes config = Config.INSTANCE.type;
                heldItemTooltips = false;

                if ((Config.INSTANCE.sneakToSwapRows && player.isShiftKeyDown()) || fullBarVerticalScroll.isDown()) {
                    renderEntireBar = true;
                    if (config == HudTypes.OVERLAY) {
                        config = HudTypes.INVISIBLE;
                    }
                }

                switch (config) {
                    case PUSHED -> {
                        renderPushedStatusBars = true;
                        CustomHudRenderer.renderExp = true;
                        renderMountInfo = true;
                    }
                    case INVISIBLE -> {
                        renderStatusBars = false;
                        CustomHudRenderer.renderExp = false;
                        renderMountInfo = false;
                    }
                    case OVERLAY -> {
                    }
                }

                CustomHudRenderer.renderHotbar = false;
                wasKeyDown = true;
            } else if (wasKeyDown && !verticalScroll.isDown()) {
                if (accumulatedScrollDelta != 0) {
                    currentIndex = player.getInventory().getSelectedSlot();
                    if (renderEntireBar) {
                        for (int i = 0; i < 9; i++) {
                            interactionManager.handleContainerInput(
                                    player.containerMenu.containerId,
                                    getIndex(i),
                                    i,
                                    ContainerInput.SWAP,
                                    player);
                        }
                    } else {
                        interactionManager.handleContainerInput(
                                player.containerMenu.containerId,
                                getIndex(currentIndex),
                                currentIndex,
                                ContainerInput.SWAP,
                                player);
                    }
                }

                clear();
            }

            ItemStack selectedStack = player.getInventory().getItem(getIndex(player.getInventory().getSelectedSlot()));
            if (selectedStack.isEmpty()) {
                heldItemTooltips = true;
            }
        });
    }

    private static void clear() {
        wasKeyDown = false;
        accumulatedScrollDelta = 0;
        CustomHudRenderer.renderHotbar = true;
        CustomHudRenderer.renderExp = true;
        renderStatusBars = true;
        renderMountInfo = true;
        renderPushedStatusBars = false;
        heldItemTooltips = true;
        renderEntireBar = false;
    }

    private static int getIndex(int baseIndex) {
        return baseIndex + ROW_OFFSETS[accumulatedScrollDelta];
    }

    private static int mapNumberKeyToRow(int keyNumber) {
        if (!Config.INSTANCE.inverted) {
            return keyNumber;
        }

        return 4 - keyNumber;
    }
}
