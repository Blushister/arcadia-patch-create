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
        CHUTE,
        DROPS,
        GLOBAL
    }

    private static final int ROOT_BELT_SLOT = 10;
    private static final int ROOT_FLUID_SLOT = 11;
    private static final int ROOT_FACTORY_SLOT = 12;
    private static final int ROOT_CHUTE_SLOT = 13;
    private static final int ROOT_DROPS_SLOT = 14;
    private static final int ROOT_GLOBAL_SLOT = 15;
    private static final int ROOT_MASTER_SLOT = 16;
    private static final int ROOT_DEBUG_SLOT = 17;

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
        super(MenuType.GENERIC_9x4, containerId);
        this.container = new SimpleContainer(36);
        this.player = playerInventory.player;

        for (int row = 0; row < 4; row++) {
            for (int column = 0; column < 9; column++) {
                addSlot(new LockedSlot(container, column + row * 9, 8 + column * 18, 18 + row * 18));
            }
        }

        int playerInventoryY = 104;
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
        return true;
    }

    @Override
    public void clicked(int slotId, int button, ClickType clickType, Player player) {
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return;
        }
        if (slotId < 0 || slotId >= this.slots.size()) {
            return;
        }

        Slot slot = this.slots.get(slotId);
        if (slot.container != this.container) {
            return;
        }

        int containerSlot = slot.getSlotIndex();
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
            case CHUTE -> handleChutePage(slot, button, clickType, player);
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
            case ROOT_CHUTE_SLOT   -> { page = Page.CHUTE;   message = "Opened Chute page"; }
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

    private void handleChutePage(int slot, int button, ClickType clickType, ServerPlayer player) {
        switch (slot) {
            case PRIMARY_SLOT -> PatchRuntime.setChutePatchEnabled(!PatchRuntime.isChutePatchConfiguredEnabled());
            case SECONDARY_SLOT -> page = Page.GLOBAL;
            case TERTIARY_SLOT -> AdminDebugReporter.sendToPlayer(player);
            default -> {
                return;
            }
        }

        player.displayClientMessage(Component.literal(buildActionMessage(slot)).withStyle(ChatFormatting.GRAY), true);
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
        boolean reverse = button == 1 || clickType == ClickType.PICKUP_ALL;

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

        boolean reverse = button == 1 || clickType == ClickType.PICKUP_ALL;
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
                case CHUTE -> "Chute patch: " + onOff(PatchRuntime.isChutePatchConfiguredEnabled());
                case DROPS -> "Create drops: " + onOff(PatchRuntime.isCreatePhysicalItemsFastDespawnConfiguredEnabled());
                case GLOBAL -> "Master switch: " + onOff(PatchRuntime.isMasterPatchEnabled());
                case ROOT -> "Panel refreshed";
            };
            case SECONDARY_SLOT -> switch (page) {
                case FACTORY, CHUTE -> "Opened Global page";
                case GLOBAL -> "Global throttle: " + PatchRuntime.describeGlobalThrottleMode();
                case DROPS -> "Create drops delay: " + formatCreateDropDespawnTicks();
                default -> "Panel refreshed";
            };
            case TERTIARY_SLOT -> switch (page) {
                case FACTORY, CHUTE, DROPS -> "Debug dump sent to chat";
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
            case CHUTE -> refreshChute();
            case DROPS -> refreshDrops();
            case GLOBAL -> refreshGlobal();
        }
    }

    private void refreshRoot() {
        container.setItem(ROOT_BELT_SLOT, makeNavItem(
            Items.GREEN_CONCRETE,
            "Belt",
            "Configured: " + onOff(PatchRuntime.isBeltPatchConfiguredEnabled()),
            "Effective: " + onOff(PatchRuntime.isBeltPatchEnabled()),
            "Skips: " + formatCount(PatchRuntime.getBeltSkips()),
            "Left click: open page"
        ));
        container.setItem(ROOT_FLUID_SLOT, makeNavItem(
            Items.WATER_BUCKET,
            "Fluid",
            "Configured: " + onOff(PatchRuntime.isFluidPatchConfiguredEnabled()),
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
        container.setItem(ROOT_CHUTE_SLOT, makeNavItem(
            Items.HOPPER,
            "Chute",
            "Configured: " + onOff(PatchRuntime.isChutePatchConfiguredEnabled()),
            "Effective: " + onOff(PatchRuntime.isChutePatchEnabled()),
            "Probe skips: " + formatCount(PatchRuntime.getChuteProbeSkips()),
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
            "Effective: " + onOff(PatchRuntime.isFluidPatchEnabled()),
            "Left click: toggle"
        ));
        container.setItem(STATUS_A_SLOT, makeInfoItem(
            Items.LECTERN,
            "Fluid Status",
            "Skips: " + formatCount(PatchRuntime.getFluidSkips()),
            "Failures: " + formatCount(PatchRuntime.getFluidInspectionFailures()),
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

    private void refreshChute() {
        setBack();
        container.setItem(PRIMARY_SLOT, makeActionItem(
            PatchRuntime.isChutePatchConfiguredEnabled() ? Items.HOPPER : Items.CAULDRON,
            "Toggle Chute Patch",
            "Configured: " + onOff(PatchRuntime.isChutePatchConfiguredEnabled()),
            "Effective: " + onOff(PatchRuntime.isChutePatchEnabled()),
            "Left click: toggle"
        ));
        container.setItem(SECONDARY_SLOT, makeActionItem(
            throttleIcon(),
            "Open Global Throttle",
            "Mode: " + PatchRuntime.describeGlobalThrottleMode(),
            "Interval: " + PatchRuntime.resolveGlobalInterval(player.level().getServer()),
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
            Items.LECTERN,
            "Chute Status",
            "Probe skips: " + formatCount(PatchRuntime.getChuteProbeSkips()),
            "Master: " + onOff(PatchRuntime.isMasterPatchEnabled()),
            "Uses global throttle"
        ));
        setSectionHelp("Chute page", "Chute uses the shared global throttle profile");
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
            "Chute: " + onOff(PatchRuntime.isChutePatchEnabled())
        ));
        container.setItem(STATUS_C_SLOT, makeInfoItem(
            Items.LECTERN,
            "Global Status 2",
            "Drops: " + onOff(PatchRuntime.isCreatePhysicalItemsFastDespawnEnabled()),
            "Factory interval: " + PatchRuntime.resolveGlobalInterval(player.level().getServer()),
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
