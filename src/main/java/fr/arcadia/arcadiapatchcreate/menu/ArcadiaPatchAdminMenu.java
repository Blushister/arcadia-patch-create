package fr.arcadia.arcadiapatchcreate.menu;

import fr.arcadia.arcadiapatchcreate.debug.AdminDebugReporter;
import fr.arcadia.arcadiapatchcreate.runtime.PatchRuntime;
import fr.arcadia.arcadiapatchcreate.runtime.PatchRuntime.ThrottleMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;

public class ArcadiaPatchAdminMenu extends AbstractContainerMenu {

    private enum Page {
        ROOT,
        BELT,
        FLUID,
        FACTORY,
        HEAT_JS,
        ITEM_DRAIN,
        DISPATCH,
        CRAFTER,
        DROPS,
        GLOBAL
    }

    private static final int CONTAINER_ROWS = 6;
    private static final int CONTAINER_SIZE = CONTAINER_ROWS * 9;

    // Row 0: read-only health banner. Row 2: per-module pages, grouped by family.
    // Row 4: system controls. Rows 1/3/5 stay empty as separators.
    private static final int HEALTH_MSPT_SLOT = 2;
    private static final int HEALTH_THROTTLE_SLOT = 4;
    private static final int HEALTH_MODULES_SLOT = 6;

    private static final int ROOT_BELT_SLOT = 18;
    private static final int ROOT_FLUID_SLOT = 19;
    private static final int ROOT_ITEM_DRAIN_SLOT = 20;
    private static final int ROOT_DISPATCH_SLOT = 21;
    private static final int ROOT_CRAFTER_SLOT = 22;
    private static final int ROOT_FACTORY_SLOT = 23;
    private static final int ROOT_HEAT_JS_SLOT = 24;
    private static final int ROOT_DROPS_SLOT = 25;

    private static final int ROOT_GLOBAL_SLOT = 38;
    private static final int ROOT_MASTER_SLOT = 40;
    private static final int ROOT_DEBUG_SLOT = 42;

    private static final int BACK_SLOT = 0;
    private static final int PRIMARY_SLOT = 11;
    private static final int SECONDARY_SLOT = 13;
    private static final int TERTIARY_SLOT = 15;
    private static final int STATUS_A_SLOT = 28;
    private static final int STATUS_B_SLOT = 29;
    private static final int STATUS_C_SLOT = 30;
    private static final int HELP_SLOT = 31;

    private final SimpleContainer container;
    private final Player player;
    private Page page = Page.ROOT;

    public ArcadiaPatchAdminMenu(int containerId, Inventory playerInventory) {
        // A vanilla menu type keeps this panel server-side only: the client renders it with
        // its own chest screen, so no custom screen, packet or asset is ever needed. Six rows
        // leave room for the modules added after 1.4.3.
        super(MenuType.GENERIC_9x6, containerId);
        this.container = new SimpleContainer(CONTAINER_SIZE);
        this.player = playerInventory.player;

        for (int row = 0; row < CONTAINER_ROWS; row++) {
            for (int column = 0; column < 9; column++) {
                addSlot(new LockedSlot(container, column + row * 9, 8 + column * 18, 18 + row * 18));
            }
        }

        int playerInventoryY = 18 + CONTAINER_ROWS * 18 + 14;
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                addSlot(new LockedSlot(playerInventory, column + row * 9 + 9, 8 + column * 18, playerInventoryY + row * 18));
            }
        }

        int hotbarY = playerInventoryY + 58;
        for (int column = 0; column < 9; column++) {
            addSlot(new LockedSlot(playerInventory, column, 8 + column * 18, hotbarY));
        }

        refresh();
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player player) {
        return !(player instanceof ServerPlayer) || player.hasPermissions(2);
    }

    @Override
    public void clicked(int slotId, int button, ClickType clickType, Player player) {
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return;
        }
        if (!serverPlayer.hasPermissions(2)) {
            serverPlayer.closeContainer();
            return;
        }
        if (slotId < 0 || slotId >= this.slots.size()) {
            return;
        }
        if (clickType != ClickType.PICKUP || (button != 0 && button != 1)) {
            return;
        }

        Slot slot = this.slots.get(slotId);
        if (slot.container != this.container) {
            return;
        }

        int containerSlot = slot.getSlotIndex();
        boolean supportedRightClick = page == Page.GLOBAL
            && (containerSlot == SECONDARY_SLOT || containerSlot == TERTIARY_SLOT);
        if (button == 1 && !supportedRightClick) {
            return;
        }
        handleButton(serverPlayer, containerSlot, button, clickType);
        serverPlayer.getServer().execute(() -> {
            refresh();
            broadcastChanges();
        });
    }

    private void handleButton(ServerPlayer player, int slot, int button, ClickType clickType) {
        if (page == Page.ROOT) {
            handleRootButton(player, slot);
            return;
        }

        if (slot == BACK_SLOT) {
            page = Page.ROOT;
            player.displayClientMessage(Component.literal("Returned to the main panel").withStyle(ChatFormatting.GRAY), true);
            return;
        }

        switch (page) {
            case BELT -> handleBeltPage(slot, player);
            case FLUID -> handleFluidPage(slot, player);
            case FACTORY -> handleFactoryPage(slot, button, clickType, player);
            case HEAT_JS -> handleHeatJsPage(slot, player);
            case ITEM_DRAIN -> handleItemDrainPage(slot, player);
            case DISPATCH -> handleDispatchPage(slot, player);
            case CRAFTER -> handleCrafterPage(slot, player);
            case DROPS -> handleDropsPage(slot, button, clickType, player);
            case GLOBAL -> handleGlobalPage(slot, button, clickType, player);
            case ROOT -> {
            }
        }
    }

    private void handleRootButton(ServerPlayer player, int slot) {
        String message;
        switch (slot) {
            case ROOT_BELT_SLOT    -> { page = Page.BELT;    message = "Opened Belt page"; }
            case ROOT_FLUID_SLOT   -> { page = Page.FLUID;   message = "Opened Fluid page"; }
            case ROOT_FACTORY_SLOT -> { page = Page.FACTORY; message = "Opened Factory Gauge page"; }
            case ROOT_HEAT_JS_SLOT -> { page = Page.HEAT_JS; message = "Opened CreateHeatJS page"; }
            case ROOT_ITEM_DRAIN_SLOT -> { page = Page.ITEM_DRAIN; message = "Opened Item Drain page"; }
            case ROOT_DISPATCH_SLOT -> { page = Page.DISPATCH; message = "Opened Behaviour Dispatch page"; }
            case ROOT_CRAFTER_SLOT -> { page = Page.CRAFTER; message = "Opened Crafter Signal page"; }
            case ROOT_DROPS_SLOT   -> { page = Page.DROPS;   message = "Opened Create Drops page"; }
            case ROOT_GLOBAL_SLOT  -> { page = Page.GLOBAL;  message = "Opened Global page"; }
            case ROOT_MASTER_SLOT  -> {
                PatchRuntime.setMasterPatchEnabled(!PatchRuntime.isMasterPatchEnabled());
                message = "Master switch: " + onOff(PatchRuntime.isMasterPatchEnabled());
            }
            case ROOT_DEBUG_SLOT   -> { AdminDebugReporter.sendToPlayer(player); message = "Debug dump sent to chat"; }
            default -> { return; }
        }
        player.displayClientMessage(Component.literal(message).withStyle(ChatFormatting.GRAY), true);
    }

    private void handleBeltPage(int slot, ServerPlayer player) {
        if (slot == PRIMARY_SLOT) {
            PatchRuntime.setBeltPatchEnabled(!PatchRuntime.isBeltPatchConfiguredEnabled());
            player.displayClientMessage(Component.literal("Belt patch: " + onOff(PatchRuntime.isBeltPatchConfiguredEnabled())).withStyle(ChatFormatting.GRAY), true);
        }
    }

    private void handleFluidPage(int slot, ServerPlayer player) {
        if (slot == PRIMARY_SLOT) {
            PatchRuntime.setFluidPatchEnabled(!PatchRuntime.isFluidPatchConfiguredEnabled());
            player.displayClientMessage(Component.literal("Fluid patch: " + onOff(PatchRuntime.isFluidPatchConfiguredEnabled())).withStyle(ChatFormatting.GRAY), true);
        }
    }

    private void handleFactoryPage(int slot, int button, ClickType clickType, ServerPlayer player) {
        switch (slot) {
            case PRIMARY_SLOT -> PatchRuntime.setFactoryGaugeEnabled(!PatchRuntime.isFactoryGaugeConfiguredEnabled());
            case SECONDARY_SLOT -> page = Page.GLOBAL;
            case TERTIARY_SLOT -> AdminDebugReporter.sendToPlayer(player);
            default -> {
                return;
            }
        }

        player.displayClientMessage(Component.literal(buildActionMessage(slot)).withStyle(ChatFormatting.GRAY), true);
    }

    private void handleHeatJsPage(int slot, ServerPlayer player) {
        if (slot == PRIMARY_SLOT) {
            PatchRuntime.setHeatJsPatchEnabled(!PatchRuntime.isHeatJsPatchConfiguredEnabled());
            player.displayClientMessage(Component.literal(buildActionMessage(slot)).withStyle(ChatFormatting.GRAY), true);
        } else if (slot == TERTIARY_SLOT) {
            AdminDebugReporter.sendToPlayer(player);
        }
    }

    private void handleItemDrainPage(int slot, ServerPlayer player) {
        if (slot == PRIMARY_SLOT) {
            PatchRuntime.setItemDrainPatchEnabled(!PatchRuntime.isItemDrainPatchConfiguredEnabled());
            player.displayClientMessage(Component.literal(buildActionMessage(slot)).withStyle(ChatFormatting.GRAY), true);
        } else if (slot == TERTIARY_SLOT) {
            AdminDebugReporter.sendToPlayer(player);
        }
    }

    private void handleDispatchPage(int slot, ServerPlayer player) {
        if (slot == PRIMARY_SLOT) {
            PatchRuntime.setBehaviourDispatchPatchEnabled(!PatchRuntime.isBehaviourDispatchPatchConfiguredEnabled());
            player.displayClientMessage(Component.literal(buildActionMessage(slot)).withStyle(ChatFormatting.GRAY), true);
        } else if (slot == TERTIARY_SLOT) {
            AdminDebugReporter.sendToPlayer(player);
        }
    }

    private void handleCrafterPage(int slot, ServerPlayer player) {
        if (slot == PRIMARY_SLOT) {
            PatchRuntime.setCrafterSignalPatchEnabled(!PatchRuntime.isCrafterSignalPatchConfiguredEnabled());
            player.displayClientMessage(Component.literal(buildActionMessage(slot)).withStyle(ChatFormatting.GRAY), true);
        } else if (slot == TERTIARY_SLOT) {
            AdminDebugReporter.sendToPlayer(player);
        }
    }

    private void handleDropsPage(int slot, int button, ClickType clickType, ServerPlayer player) {
        switch (slot) {
            case PRIMARY_SLOT -> PatchRuntime.setCreatePhysicalItemsFastDespawnEnabled(!PatchRuntime.isCreatePhysicalItemsFastDespawnConfiguredEnabled());
            case SECONDARY_SLOT -> cycleCreateDropDespawn();
            case TERTIARY_SLOT -> AdminDebugReporter.sendToPlayer(player);
            default -> {
                return;
            }
        }

        player.displayClientMessage(Component.literal(buildActionMessage(slot)).withStyle(ChatFormatting.GRAY), true);
    }

    private void handleGlobalPage(int slot, int button, ClickType clickType, ServerPlayer player) {
        switch (slot) {
            case PRIMARY_SLOT -> PatchRuntime.setMasterPatchEnabled(!PatchRuntime.isMasterPatchEnabled());
            case SECONDARY_SLOT -> cycleGlobalMode(button, clickType);
            case TERTIARY_SLOT -> cycleSimulatedMspt(button, clickType);
            case STATUS_A_SLOT -> AdminDebugReporter.sendToPlayer(player);
            default -> {
                return;
            }
        }

        player.displayClientMessage(Component.literal(buildActionMessage(slot)).withStyle(ChatFormatting.GRAY), true);
    }

    private void cycleGlobalMode(int button, ClickType clickType) {
        ThrottleMode mode = PatchRuntime.getGlobalThrottleMode();
        boolean reverse = button == 1;

        if (mode == ThrottleMode.STATIC) {
            int interval = PatchRuntime.getGlobalStaticInterval();
            int nextInterval = reverse ? interval - 1 : interval + 1;
            if (nextInterval >= 2 && nextInterval <= 5) {
                PatchRuntime.setGlobalStaticInterval(nextInterval);
                return;
            }
        }

        if (!reverse) {
            if (mode == ThrottleMode.OFF) {
                PatchRuntime.setGlobalThrottleMode(ThrottleMode.STATIC);
                PatchRuntime.setGlobalStaticInterval(2);
            } else if (mode == ThrottleMode.STATIC) {
                PatchRuntime.setGlobalThrottleMode(ThrottleMode.ADAPTIVE);
            } else {
                PatchRuntime.setGlobalThrottleMode(ThrottleMode.OFF);
            }
        } else {
            if (mode == ThrottleMode.OFF) {
                PatchRuntime.setGlobalThrottleMode(ThrottleMode.ADAPTIVE);
            } else if (mode == ThrottleMode.ADAPTIVE) {
                PatchRuntime.setGlobalThrottleMode(ThrottleMode.STATIC);
                PatchRuntime.setGlobalStaticInterval(5);
            } else {
                PatchRuntime.setGlobalThrottleMode(ThrottleMode.OFF);
            }
        }
    }

    private void cycleSimulatedMspt(int button, ClickType clickType) {
        Double current = PatchRuntime.getSimulatedMspt();
        double[] values = {0.0D, 35.0D, 45.0D, 55.0D};
        int index = 0;
        if (current != null) {
            for (int i = 1; i < values.length; i++) {
                if (Double.compare(current, values[i]) == 0) {
                    index = i;
                    break;
                }
            }
        }

        boolean reverse = button == 1;
        int nextIndex = reverse ? index - 1 : index + 1;
        if (nextIndex < 0) {
            nextIndex = values.length - 1;
        }
        if (nextIndex >= values.length) {
            nextIndex = 0;
        }

        if (nextIndex == 0) {
            PatchRuntime.clearSimulatedMspt();
        } else {
            PatchRuntime.setSimulatedMspt(values[nextIndex]);
        }
    }

    private void cycleCreateDropDespawn() {
        int[] values = {600, 1200, 2400, 6000};
        int current = PatchRuntime.getCreatePhysicalItemsDespawnTicks();
        int index = 1;
        for (int i = 0; i < values.length; i++) {
            if (values[i] == current) {
                index = i;
                break;
            }
        }
        int nextIndex = (index + 1) % values.length;
        PatchRuntime.setCreatePhysicalItemsDespawnTicks(values[nextIndex]);
        PatchRuntime.setCreatePhysicalItemsFastDespawnEnabled(true);
    }

    private String buildActionMessage(int slot) {
        return switch (slot) {
            case PRIMARY_SLOT -> switch (page) {
                case BELT -> "Belt patch: " + onOff(PatchRuntime.isBeltPatchConfiguredEnabled());
                case FLUID -> "Fluid patch: " + onOff(PatchRuntime.isFluidPatchConfiguredEnabled());
                case FACTORY -> "Factory Gauge patch: " + onOff(PatchRuntime.isFactoryGaugeConfiguredEnabled());
                case HEAT_JS -> "CreateHeatJS cache: " + onOff(PatchRuntime.isHeatJsPatchConfiguredEnabled());
                case ITEM_DRAIN -> "Item Drain reuse: " + onOff(PatchRuntime.isItemDrainPatchConfiguredEnabled());
                case DISPATCH -> "Behaviour dispatch: " + onOff(PatchRuntime.isBehaviourDispatchPatchConfiguredEnabled());
                case CRAFTER -> "Crafter signal cache: " + onOff(PatchRuntime.isCrafterSignalPatchConfiguredEnabled());
                case DROPS -> "Create drops: " + onOff(PatchRuntime.isCreatePhysicalItemsFastDespawnConfiguredEnabled());
                case GLOBAL -> "Master switch: " + onOff(PatchRuntime.isMasterPatchEnabled());
                case ROOT -> "Panel refreshed";
            };
            case SECONDARY_SLOT -> switch (page) {
                case FACTORY -> "Opened Global page";
                case GLOBAL -> "Global throttle: " + PatchRuntime.describeGlobalThrottleMode();
                case DROPS -> "Create drops delay: " + formatCreateDropDespawnTicks();
                default -> "Panel refreshed";
            };
            case TERTIARY_SLOT -> switch (page) {
                case FACTORY, HEAT_JS, ITEM_DRAIN, DISPATCH, CRAFTER, DROPS -> "Debug dump sent to chat";
                case GLOBAL -> "Simulated MSPT: " + formatSimulatedMspt();
                default -> "Debug dump sent to chat";
            };
            case STATUS_A_SLOT -> "Debug dump sent to chat";
            default -> "Panel refreshed";
        };
    }

    private void refresh() {
        container.clearContent();
        fillBackground();
        switch (page) {
            case ROOT -> refreshRoot();
            case BELT -> refreshBelt();
            case FLUID -> refreshFluid();
            case FACTORY -> refreshFactory();
            case HEAT_JS -> refreshHeatJs();
            case ITEM_DRAIN -> refreshItemDrain();
            case DISPATCH -> refreshDispatch();
            case CRAFTER -> refreshCrafter();
            case DROPS -> refreshDrops();
            case GLOBAL -> refreshGlobal();
        }
    }

    /**
     * Read-only summary so an operator sees at a glance whether the server is under load and
     * whether a module silently opted out because its bytecode fingerprint did not match.
     */
    private void setHealthBanner() {
        container.setItem(HEALTH_MSPT_SLOT, makeInfoItem(
            Items.CLOCK,
            "Server Health",
            "Current MSPT: " + formatCurrentMspt(),
            "Budget per tick: 50.00",
            "Adaptive steps: 35 / 45 / 55"
        ));
        container.setItem(HEALTH_THROTTLE_SLOT, makeInfoItem(
            Items.REDSTONE_TORCH,
            "Throttle",
            "Mode: " + PatchRuntime.describeGlobalThrottleMode(),
            "Interval: " + PatchRuntime.resolveGlobalInterval(player.level().getServer()),
            "Simulated MSPT: " + formatSimulatedMspt()
        ));
        int unavailable = countUnavailableModules();
        container.setItem(HEALTH_MODULES_SLOT, makeInfoItem(
            unavailable == 0 ? Items.COMPARATOR : Items.BARRIER,
            "Modules",
            "Effective: " + countEffectiveModules() + "/" + MODULE_COUNT,
            "Unavailable: " + unavailable,
            "Master: " + onOff(PatchRuntime.isMasterPatchEnabled())
        ));
    }

    private static final int MODULE_COUNT = 7;

    private int countEffectiveModules() {
        int count = 0;
        if (PatchRuntime.isBeltPatchEnabled()) count++;
        if (PatchRuntime.isFluidPatchEnabled()) count++;
        if (PatchRuntime.isItemDrainPatchEnabled()) count++;
        if (PatchRuntime.isFactoryGaugeEnabled()) count++;
        if (PatchRuntime.isHeatJsPatchEnabled()) count++;
        if (PatchRuntime.isBehaviourDispatchPatchEnabled()) count++;
        if (PatchRuntime.isCrafterSignalPatchEnabled()) count++;
        return count;
    }

    /** Counts modules whose target bytecode was rejected, which no toggle can bring back. */
    private int countUnavailableModules() {
        int count = 0;
        if (!PatchRuntime.isBeltPatchAvailable()) count++;
        if (!PatchRuntime.isFluidPatchAvailable()) count++;
        if (!PatchRuntime.isItemDrainPatchAvailable()) count++;
        if (!PatchRuntime.isHeatJsPatchAvailable()) count++;
        if (!PatchRuntime.isBehaviourDispatchPatchAvailable()) count++;
        if (!PatchRuntime.isCrafterSignalPatchAvailable()) count++;
        return count;
    }

    private void refreshRoot() {
        setHealthBanner();
        container.setItem(ROOT_BELT_SLOT, makeNavItem(
            Items.GREEN_CONCRETE,
            "Belt",
            "Configured: " + onOff(PatchRuntime.isBeltPatchConfiguredEnabled()),
            "Available: " + yesNo(PatchRuntime.isBeltPatchAvailable()),
            "Effective: " + onOff(PatchRuntime.isBeltPatchEnabled()),
            "Skips: " + formatCount(PatchRuntime.getBeltSkips()),
            "Left click: open page"
        ));
        container.setItem(ROOT_FLUID_SLOT, makeNavItem(
            Items.WATER_BUCKET,
            "Fluid",
            "Configured: " + onOff(PatchRuntime.isFluidPatchConfiguredEnabled()),
            "Available: " + yesNo(PatchRuntime.isFluidPatchAvailable()),
            "Effective: " + onOff(PatchRuntime.isFluidPatchEnabled()),
            "Skips: " + formatCount(PatchRuntime.getFluidSkips()),
            "Left click: open page"
        ));
        container.setItem(ROOT_FACTORY_SLOT, makeNavItem(
            Items.BOOK,
            "Factory Gauge",
            "Configured: " + onOff(PatchRuntime.isFactoryGaugeConfiguredEnabled()),
            "Effective: " + onOff(PatchRuntime.isFactoryGaugeEnabled()),
            "Skips: " + formatCount(PatchRuntime.getFactoryGaugeSkips()),
            "Forced: " + formatCount(PatchRuntime.getFactoryGaugeForcedRuns()),
            "Left click: open page"
        ));
        container.setItem(ROOT_HEAT_JS_SLOT, makeNavItem(
            Items.BLAZE_POWDER,
            "CreateHeatJS",
            "Configured: " + onOff(PatchRuntime.isHeatJsPatchConfiguredEnabled()),
            "Available: " + yesNo(PatchRuntime.isHeatJsPatchAvailable()),
            "Effective: " + onOff(PatchRuntime.isHeatJsPatchEnabled()),
            "Cache hits: " + formatCount(PatchRuntime.getHeatJsCacheHits()),
            "Left click: open page"
        ));
        container.setItem(ROOT_ITEM_DRAIN_SLOT, makeNavItem(
            Items.CAULDRON,
            "Item Drain",
            "Configured: " + onOff(PatchRuntime.isItemDrainPatchConfiguredEnabled()),
            "Available: " + yesNo(PatchRuntime.isItemDrainPatchAvailable()),
            "Effective: " + onOff(PatchRuntime.isItemDrainPatchEnabled()),
            "Reuses: " + formatCount(PatchRuntime.getItemDrainReuses()),
            "Left click: open page"
        ));
        container.setItem(ROOT_DISPATCH_SLOT, makeNavItem(
            Items.REPEATER,
            "Behaviour Dispatch",
            "Configured: " + onOff(PatchRuntime.isBehaviourDispatchPatchConfiguredEnabled()),
            "Available: " + yesNo(PatchRuntime.isBehaviourDispatchPatchAvailable()),
            "Effective: " + onOff(PatchRuntime.isBehaviourDispatchPatchEnabled()),
            "Ticks: " + formatCount(PatchRuntime.getBehaviourDispatches()),
            "Left click: open page"
        ));
        container.setItem(ROOT_CRAFTER_SLOT, makeNavItem(
            Items.CRAFTING_TABLE,
            "Crafter Signal",
            "Configured: " + onOff(PatchRuntime.isCrafterSignalPatchConfiguredEnabled()),
            "Available: " + yesNo(PatchRuntime.isCrafterSignalPatchAvailable()),
            "Effective: " + onOff(PatchRuntime.isCrafterSignalPatchEnabled()),
            "Cached: " + formatCount(PatchRuntime.getCrafterSignalReuses()),
            "Left click: open page"
        ));
        container.setItem(ROOT_DROPS_SLOT, makeNavItem(
            Items.CAMPFIRE,
            "Create Drops",
            "Configured: " + onOff(PatchRuntime.isCreatePhysicalItemsFastDespawnConfiguredEnabled()),
            "Effective: " + onOff(PatchRuntime.isCreatePhysicalItemsFastDespawnEnabled()),
            "Delay: " + formatCreateDropDespawnTicks(),
            "Left click: open page"
        ));
        container.setItem(ROOT_GLOBAL_SLOT, makeNavItem(
            Items.COMPASS,
            "Global",
            "Master: " + onOff(PatchRuntime.isMasterPatchEnabled()),
            "Throttle: " + PatchRuntime.describeGlobalThrottleMode(),
            "Simulated MSPT: " + formatSimulatedMspt(),
            "Left click: open page"
        ));
        container.setItem(ROOT_MASTER_SLOT, makeActionItem(
            PatchRuntime.isMasterPatchEnabled() ? Items.LEVER : Items.BARRIER,
            "Master Switch",
            "State: " + onOff(PatchRuntime.isMasterPatchEnabled()),
            "Cuts all patches at once",
            "Keeps per-patch settings saved",
            "Left click: toggle"
        ));
        container.setItem(ROOT_DEBUG_SLOT, makeActionItem(
            Items.WRITABLE_BOOK,
            "Debug Dump",
            "Sends detailed runtime status to chat",
            "Useful when the panel is too small",
            "Left click: send"
        ));
        container.setItem(HELP_SLOT, makeInfoItem(
            Items.NAME_TAG,
            "Panel Home",
            "This page is a launcher",
            "Open a sub menu to read details",
            "Use Master Switch for emergency OFF"
        ));
    }

    private void refreshBelt() {
        setBack();
        container.setItem(PRIMARY_SLOT, makeActionItem(
            PatchRuntime.isBeltPatchConfiguredEnabled() ? Items.GREEN_CONCRETE : Items.RED_CONCRETE,
            "Toggle Belt Patch",
            "Configured: " + onOff(PatchRuntime.isBeltPatchConfiguredEnabled()),
            "Available: " + yesNo(PatchRuntime.isBeltPatchAvailable()),
            "Effective: " + onOff(PatchRuntime.isBeltPatchEnabled()),
            "Left click: toggle"
        ));
        container.setItem(STATUS_A_SLOT, makeInfoItem(
            Items.LECTERN,
            "Belt Status",
            "Skips: " + formatCount(PatchRuntime.getBeltSkips()),
            "Master: " + onOff(PatchRuntime.isMasterPatchEnabled()),
            "Patch is safe on empty belts"
        ));
        setSectionHelp("Belt page", "Only one action here: enable or disable the belt optimization");
    }

    private void refreshFluid() {
        setBack();
        container.setItem(PRIMARY_SLOT, makeActionItem(
            PatchRuntime.isFluidPatchConfiguredEnabled() ? Items.WATER_BUCKET : Items.BUCKET,
            "Toggle Fluid Patch",
            "Configured: " + onOff(PatchRuntime.isFluidPatchConfiguredEnabled()),
            "Available: " + yesNo(PatchRuntime.isFluidPatchAvailable()),
            "Effective: " + onOff(PatchRuntime.isFluidPatchEnabled()),
            "Left click: toggle"
        ));
        container.setItem(STATUS_A_SLOT, makeInfoItem(
            Items.LECTERN,
            "Fluid Status",
            "Skips: " + formatCount(PatchRuntime.getFluidSkips()),
            "Failures: " + formatCount(PatchRuntime.getFluidInspectionFailures()),
            "EnumMap swaps: " + formatCount(PatchRuntime.getFluidMapCompactions()),
            "Master: " + onOff(PatchRuntime.isMasterPatchEnabled())
        ));
        setSectionHelp("Fluid page", "This page only changes the validated idle fluid fast path");
    }

    private void refreshFactory() {
        setBack();
        container.setItem(PRIMARY_SLOT, makeActionItem(
            PatchRuntime.isFactoryGaugeConfiguredEnabled() ? Items.BOOK : Items.WRITABLE_BOOK,
            "Toggle Factory Gauge Patch",
            "Configured: " + onOff(PatchRuntime.isFactoryGaugeConfiguredEnabled()),
            "Effective: " + onOff(PatchRuntime.isFactoryGaugeEnabled()),
            "Left click: toggle"
        ));
        container.setItem(SECONDARY_SLOT, makeActionItem(
            throttleIcon(),
            "Open Global Throttle",
            "Current mode: " + PatchRuntime.describeGlobalThrottleMode(),
            "Current interval: " + PatchRuntime.resolveGlobalInterval(player.level().getServer()),
            "Left click: open Global page"
        ));
        container.setItem(TERTIARY_SLOT, makeActionItem(
            Items.WRITABLE_BOOK,
            "Debug Dump",
            "Send the detailed runtime status to chat",
            "Useful for support and test notes",
            "Left click: send"
        ));
        container.setItem(STATUS_A_SLOT, makeInfoItem(
            Items.BOOK,
            "Factory Gauge Status",
            "Skips: " + formatCount(PatchRuntime.getFactoryGaugeSkips()),
            "Forced runs: " + formatCount(PatchRuntime.getFactoryGaugeForcedRuns()),
            "Uses global throttle when enabled"
        ));
        container.setItem(STATUS_B_SLOT, makeInfoItem(
            Items.LECTERN,
            "Current Runtime",
            "Master: " + onOff(PatchRuntime.isMasterPatchEnabled()),
            "Simulated MSPT: " + formatSimulatedMspt(),
            "Current MSPT: " + formatCurrentMspt()
        ));
        setSectionHelp("Factory page", "Factory Gauge uses the shared global throttle profile");
    }

    private void refreshHeatJs() {
        setBack();
        container.setItem(PRIMARY_SLOT, makeActionItem(
            PatchRuntime.isHeatJsPatchConfiguredEnabled() ? Items.BLAZE_POWDER : Items.GUNPOWDER,
            "Toggle CreateHeatJS Cache",
            "Configured: " + onOff(PatchRuntime.isHeatJsPatchConfiguredEnabled()),
            "Available: " + yesNo(PatchRuntime.isHeatJsPatchAvailable()),
            "Effective: " + onOff(PatchRuntime.isHeatJsPatchEnabled()),
            "Left click: toggle"
        ));
        container.setItem(TERTIARY_SLOT, makeActionItem(
            Items.WRITABLE_BOOK,
            "Debug Dump",
            "Send the detailed runtime status to chat",
            "Left click: send"
        ));
        container.setItem(STATUS_A_SLOT, makeInfoItem(
            Items.LECTERN,
            "CreateHeatJS Status",
            "Cache hits: " + formatCount(PatchRuntime.getHeatJsCacheHits()),
            "Cache misses: " + formatCount(PatchRuntime.getHeatJsCacheMisses()),
            "Invalidations: " + formatCount(PatchRuntime.getHeatJsCacheInvalidations())
        ));
        container.setItem(STATUS_B_SLOT, makeInfoItem(
            Items.BOOK,
            "Fail-open Status",
            "Failures: " + formatCount(PatchRuntime.getHeatJsFailures()),
            "Master: " + onOff(PatchRuntime.isMasterPatchEnabled()),
            "Misses execute original HeatJS logic"
        ));
        setSectionHelp("CreateHeatJS page", "Caches recipe IDs only; each heat check still gets a fresh context");
    }

    private void refreshItemDrain() {
        setBack();
        container.setItem(PRIMARY_SLOT, makeActionItem(
            PatchRuntime.isItemDrainPatchConfiguredEnabled() ? Items.CAULDRON : Items.BUCKET,
            "Toggle Item Drain Reuse",
            "Configured: " + onOff(PatchRuntime.isItemDrainPatchConfiguredEnabled()),
            "Available: " + yesNo(PatchRuntime.isItemDrainPatchAvailable()),
            "Effective: " + onOff(PatchRuntime.isItemDrainPatchEnabled()),
            "Left click: toggle"
        ));
        container.setItem(TERTIARY_SLOT, makeActionItem(
            Items.WRITABLE_BOOK,
            "Debug Dump",
            "Send the detailed runtime status to chat",
            "Left click: send"
        ));
        container.setItem(STATUS_A_SLOT, makeInfoItem(
            Items.LECTERN,
            "Item Drain Status",
            "Positive captures: " + formatCount(PatchRuntime.getItemDrainCaptures()),
            "Simulation reuses: " + formatCount(PatchRuntime.getItemDrainReuses()),
            "Fallbacks: " + formatCount(PatchRuntime.getItemDrainFallbacks())
        ));
        container.setItem(STATUS_B_SLOT, makeInfoItem(
            Items.BOOK,
            "Safety Scope",
            "Master: " + onOff(PatchRuntime.isMasterPatchEnabled()),
            "Standard positive recipes only",
            "Real execution always calls Create"
        ));
        setSectionHelp("Item Drain page", "Reuses one immediate positive simulation lookup inside a guarded frame");
    }

    private void refreshDispatch() {
        setBack();
        container.setItem(PRIMARY_SLOT, makeActionItem(
            PatchRuntime.isBehaviourDispatchPatchConfiguredEnabled() ? Items.REPEATER : Items.COMPARATOR,
            "Toggle Behaviour Dispatch",
            "Configured: " + onOff(PatchRuntime.isBehaviourDispatchPatchConfiguredEnabled()),
            "Available: " + yesNo(PatchRuntime.isBehaviourDispatchPatchAvailable()),
            "Effective: " + onOff(PatchRuntime.isBehaviourDispatchPatchEnabled()),
            "Left click: toggle"
        ));
        container.setItem(TERTIARY_SLOT, makeActionItem(
            Items.WRITABLE_BOOK,
            "Debug Dump",
            "Send the detailed runtime status to chat",
            "Left click: send"
        ));
        container.setItem(STATUS_A_SLOT, makeInfoItem(
            Items.LECTERN,
            "Dispatch Status",
            "Direct ticks: " + formatCount(PatchRuntime.getBehaviourDispatches()),
            "Skips the Consumer indirection",
            "Same behaviours, same order"
        ));
        container.setItem(STATUS_B_SLOT, makeInfoItem(
            Items.BOOK,
            "Safety Scope",
            "Master: " + onOff(PatchRuntime.isMasterPatchEnabled()),
            "Verified once per session",
            "Falls back to Create on any doubt"
        ));
        setSectionHelp("Behaviour Dispatch page", "Ticks Create behaviours directly, without the Consumer hop");
    }

    private void refreshCrafter() {
        setBack();
        container.setItem(PRIMARY_SLOT, makeActionItem(
            PatchRuntime.isCrafterSignalPatchConfiguredEnabled() ? Items.CRAFTING_TABLE : Items.REDSTONE,
            "Toggle Crafter Signal Cache",
            "Configured: " + onOff(PatchRuntime.isCrafterSignalPatchConfiguredEnabled()),
            "Available: " + yesNo(PatchRuntime.isCrafterSignalPatchAvailable()),
            "Effective: " + onOff(PatchRuntime.isCrafterSignalPatchEnabled()),
            "Left click: toggle"
        ));
        container.setItem(TERTIARY_SLOT, makeActionItem(
            Items.WRITABLE_BOOK,
            "Debug Dump",
            "Send the detailed runtime status to chat",
            "Left click: send"
        ));
        container.setItem(STATUS_A_SLOT, makeInfoItem(
            Items.LECTERN,
            "Crafter Signal Status",
            "Cached reads: " + formatCount(PatchRuntime.getCrafterSignalReuses()),
            "World reads: " + formatCount(PatchRuntime.getCrafterSignalReads()),
            "Invalidations: " + formatCount(PatchRuntime.getCrafterSignalInvalidations())
        ));
        container.setItem(STATUS_B_SLOT, makeInfoItem(
            Items.BOOK,
            "Safety Scope",
            "Master: " + onOff(PatchRuntime.isMasterPatchEnabled()),
            "Dropped on any neighbour update",
            "Forced refresh every 20 ticks"
        ));
        setSectionHelp("Crafter Signal page", "Caches the redstone read, invalidated the moment a neighbour changes");
    }

    private void refreshDrops() {
        setBack();
        container.setItem(PRIMARY_SLOT, makeActionItem(
            PatchRuntime.isCreatePhysicalItemsFastDespawnConfiguredEnabled() ? Items.CAMPFIRE : Items.SOUL_CAMPFIRE,
            "Toggle Create Drops",
            "Configured: " + onOff(PatchRuntime.isCreatePhysicalItemsFastDespawnConfiguredEnabled()),
            "Effective: " + onOff(PatchRuntime.isCreatePhysicalItemsFastDespawnEnabled()),
            "Left click: toggle"
        ));
        container.setItem(SECONDARY_SLOT, makeActionItem(
            Items.CLOCK,
            "Cycle Despawn Delay",
            "Current delay: " + formatCreateDropDespawnTicks(),
            "Values: 30s / 60s / 120s / 300s",
            "Left click: next value"
        ));
        container.setItem(TERTIARY_SLOT, makeActionItem(
            Items.WRITABLE_BOOK,
            "Debug Dump",
            "Send the detailed runtime status to chat",
            "Useful to confirm counters and state",
            "Left click: send"
        ));
        container.setItem(STATUS_A_SLOT, makeInfoItem(
            Items.LECTERN,
            "Create Drops Status",
            "Marked items: " + formatCount(PatchRuntime.getCreatePhysicalItemMarks()),
            "Master: " + onOff(PatchRuntime.isMasterPatchEnabled()),
            "Delay: " + formatCreateDropDespawnTicks()
        ));
        setSectionHelp("Create Drops page", "The merge-safe timer now keeps the earliest deadline when stacks combine");
    }

    private void refreshGlobal() {
        setBack();
        container.setItem(PRIMARY_SLOT, makeActionItem(
            PatchRuntime.isMasterPatchEnabled() ? Items.LEVER : Items.BARRIER,
            "Master Switch",
            "State: " + onOff(PatchRuntime.isMasterPatchEnabled()),
            "Cuts all patches at once",
            "Keeps per-patch settings saved",
            "Left click: toggle"
        ));
        container.setItem(SECONDARY_SLOT, makeActionItem(
            throttleIcon(),
            "Global Throttle",
            "Mode: " + PatchRuntime.describeGlobalThrottleMode(),
            "Interval: " + PatchRuntime.resolveGlobalInterval(player.level().getServer()),
            "Left click: next mode / +1 static",
            "Right click: previous mode / -1 static"
        ));
        container.setItem(TERTIARY_SLOT, makeActionItem(
            Items.REPEATER,
            "Simulated MSPT",
            "Value: " + formatSimulatedMspt(),
            "Cycle: Off / 35 / 45 / 55",
            "Left click: next",
            "Right click: previous"
        ));
        container.setItem(STATUS_A_SLOT, makeActionItem(
            Items.WRITABLE_BOOK,
            "Debug Dump",
            "Send the detailed runtime status to chat",
            "Useful in production support",
            "Left click: send"
        ));
        container.setItem(STATUS_B_SLOT, makeInfoItem(
            Items.BOOK,
            "Global Status",
            "Current MSPT: " + formatCurrentMspt(),
            "Belt: " + onOff(PatchRuntime.isBeltPatchEnabled()),
            "Fluid: " + onOff(PatchRuntime.isFluidPatchEnabled()),
            "Factory: " + onOff(PatchRuntime.isFactoryGaugeEnabled())
        ));
        container.setItem(STATUS_C_SLOT, makeInfoItem(
            Items.LECTERN,
            "Global Status 2",
            "HeatJS: " + onOff(PatchRuntime.isHeatJsPatchEnabled()),
            "Item Drain: " + onOff(PatchRuntime.isItemDrainPatchEnabled()),
            "Drops: " + onOff(PatchRuntime.isCreatePhysicalItemsFastDespawnEnabled()),
            "Simulated MSPT: " + formatSimulatedMspt()
        ));
        setSectionHelp("Global page", "Use this page for production toggles and emergency control");
    }

    private void setBack() {
        container.setItem(BACK_SLOT, makeActionItem(
            Items.ARROW,
            "Back",
            "Return to the main panel",
            "Left click: go back"
        ));
    }

    private void setSectionHelp(String title, String... lines) {
        container.setItem(HELP_SLOT, makeInfoItem(Items.NAME_TAG, title, lines));
    }

    private Item throttleIcon() {
        return switch (PatchRuntime.getGlobalThrottleMode()) {
            case OFF -> Items.BARRIER;
            case STATIC -> Items.CLOCK;
            case ADAPTIVE -> Items.COMPARATOR;
        };
    }

    private void fillBackground() {
        for (int i = 0; i < container.getContainerSize(); i++) {
            container.setItem(i, namedStack(Items.GRAY_STAINED_GLASS_PANE, " "));
        }
    }

    private static ItemStack makeNavItem(Item item, String title, String... lines) {
        return makeItem(item, ChatFormatting.AQUA, title, lines);
    }

    private static ItemStack makeActionItem(Item item, String title, String... lines) {
        return makeItem(item, ChatFormatting.GREEN, title, lines);
    }

    private static ItemStack makeInfoItem(Item item, String title, String... lines) {
        return makeItem(item, ChatFormatting.GOLD, title, lines);
    }

    private static ItemStack makeItem(Item item, ChatFormatting titleColor, String title, String... lines) {
        ItemStack stack = new ItemStack(item);
        stack.set(DataComponents.CUSTOM_NAME, Component.literal(title).withStyle(titleColor));
        List<Component> loreLines = new ArrayList<>();
        for (String line : lines) {
            loreLines.add(Component.literal(line).withStyle(ChatFormatting.GRAY));
        }
        stack.set(DataComponents.LORE, new ItemLore(loreLines));
        return stack;
    }

    private static ItemStack namedStack(Item item, String title) {
        ItemStack stack = new ItemStack(item);
        stack.set(DataComponents.CUSTOM_NAME, Component.literal(title).withStyle(ChatFormatting.DARK_GRAY));
        return stack;
    }

    private String formatCurrentMspt() {
        return String.format(Locale.ROOT, "%.2f", PatchRuntime.getCurrentMspt(player.level().getServer()));
    }

    private static String onOff(boolean enabled) {
        return enabled ? "ON" : "OFF";
    }

    private static String yesNo(boolean available) {
        return available ? "YES" : "NO";
    }

    private static String formatSimulatedMspt() {
        Double simulated = PatchRuntime.getSimulatedMspt();
        if (simulated == null) {
            return "Off";
        }
        return String.format(Locale.ROOT, "%.0f", simulated);
    }

    private static String formatCreateDropDespawnTicks() {
        return (PatchRuntime.getCreatePhysicalItemsDespawnTicks() / 20) + "s";
    }

    private static String formatCount(long value) {
        if (value >= 1_000_000_000L) {
            return String.format(Locale.ROOT, "%.1fB", value / 1_000_000_000.0D);
        }
        if (value >= 1_000_000L) {
            return String.format(Locale.ROOT, "%.1fM", value / 1_000_000.0D);
        }
        if (value >= 1_000L) {
            return String.format(Locale.ROOT, "%.1fk", value / 1_000.0D);
        }
        return Long.toString(value);
    }

    private static final class LockedSlot extends Slot {

        private LockedSlot(net.minecraft.world.Container container, int slot, int x, int y) {
            super(container, slot, x, y);
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return false;
        }

        @Override
        public boolean mayPickup(Player player) {
            return false;
        }
    }
}
