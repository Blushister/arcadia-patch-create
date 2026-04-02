package fr.arcadia.arcadiapatchcreate.menu;

import fr.arcadia.arcadiapatchcreate.runtime.PatchRuntime;
import fr.arcadia.arcadiapatchcreate.runtime.PatchRuntime.FactoryGaugeMode;
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
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public class ArcadiaPatchAdminMenu extends AbstractContainerMenu {

    private static final int BELT_SLOT = 10;
    private static final int FLUID_SLOT = 12;
    private static final int FACTORY_MODE_SLOT = 14;
    private static final int FACTORY_MSPT_SLOT = 15;
    private static final int STATUS_SLOT = 16;
    private static final int HELP_SLOT = 22;

    private final SimpleContainer container;
    private final Player player;

    public ArcadiaPatchAdminMenu(int containerId, Inventory playerInventory) {
        super(MenuType.GENERIC_9x3, containerId);
        this.container = new SimpleContainer(27);
        this.player = playerInventory.player;

        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                addSlot(new LockedSlot(container, column + row * 9, 8 + column * 18, 18 + row * 18));
            }
        }

        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                addSlot(new LockedSlot(playerInventory, column + row * 9 + 9, 8 + column * 18, 84 + row * 18));
            }
        }

        for (int column = 0; column < 9; column++) {
            addSlot(new LockedSlot(playerInventory, column, 8 + column * 18, 142));
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
        refresh();
        broadcastChanges();
    }

    private void handleButton(ServerPlayer player, int slot, int button, ClickType clickType) {
        switch (slot) {
            case BELT_SLOT -> PatchRuntime.setBeltPatchEnabled(!PatchRuntime.isBeltPatchEnabled());
            case FLUID_SLOT -> PatchRuntime.setFluidPatchEnabled(!PatchRuntime.isFluidPatchEnabled());
            case FACTORY_MODE_SLOT -> cycleFactoryMode();
            case FACTORY_MSPT_SLOT -> cycleSimulatedMspt(button, clickType);
            case STATUS_SLOT, HELP_SLOT -> {
            }
            default -> {
                return;
            }
        }

        player.displayClientMessage(Component.literal(buildActionMessage(slot)).withStyle(ChatFormatting.GRAY), true);
    }

    private void cycleFactoryMode() {
        FactoryGaugeMode mode = PatchRuntime.getFactoryGaugeMode();
        if (mode == FactoryGaugeMode.OFF) {
            PatchRuntime.setFactoryGaugeMode(FactoryGaugeMode.STATIC);
            PatchRuntime.setFactoryGaugeStaticInterval(2);
        } else if (mode == FactoryGaugeMode.STATIC) {
            PatchRuntime.setFactoryGaugeMode(FactoryGaugeMode.ADAPTIVE);
        } else {
            PatchRuntime.setFactoryGaugeMode(FactoryGaugeMode.OFF);
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

    private String buildActionMessage(int slot) {
        return switch (slot) {
            case BELT_SLOT -> "Belt patch: " + onOff(PatchRuntime.isBeltPatchEnabled());
            case FLUID_SLOT -> "Fluid patch: " + onOff(PatchRuntime.isFluidPatchEnabled());
            case FACTORY_MODE_SLOT -> "Factory Gauge mode: " + PatchRuntime.describeFactoryGaugeMode();
            case FACTORY_MSPT_SLOT -> "Simulated MSPT: " + formatSimulatedMspt();
            default -> "Panel refreshed";
        };
    }

    private void refresh() {
        container.clearContent();
        fillBackground();
        container.setItem(BELT_SLOT, makeToggleItem(
            PatchRuntime.isBeltPatchEnabled() ? Items.GREEN_CONCRETE : Items.RED_CONCRETE,
            "Belt Patch",
            "State: " + onOff(PatchRuntime.isBeltPatchEnabled()),
            "Skips: " + PatchRuntime.getBeltSkips(),
            "Left click to toggle"
        ));
        container.setItem(FLUID_SLOT, makeToggleItem(
            PatchRuntime.isFluidPatchEnabled() ? Items.WATER_BUCKET : Items.BUCKET,
            "Fluid Patch",
            "State: " + onOff(PatchRuntime.isFluidPatchEnabled()),
            "Skips: " + PatchRuntime.getFluidSkips(),
            "Failures: " + PatchRuntime.getFluidInspectionFailures()
        ));
        container.setItem(FACTORY_MODE_SLOT, makeToggleItem(
            switch (PatchRuntime.getFactoryGaugeMode()) {
                case OFF -> Items.BARRIER;
                case STATIC -> Items.CLOCK;
                case ADAPTIVE -> Items.COMPARATOR;
            },
            "Factory Gauge Mode",
            "Mode: " + PatchRuntime.describeFactoryGaugeMode(),
            "Current interval: " + PatchRuntime.resolveFactoryGaugeInterval(player.level().getServer()),
            "Left click to cycle Off -> Static -> Adaptive"
        ));
        container.setItem(FACTORY_MSPT_SLOT, makeToggleItem(
            Items.REPEATER,
            "Factory Gauge Test Load",
            "Simulated MSPT: " + formatSimulatedMspt(),
            "Cycle values: Off / 35 / 45 / 55",
            "Left click forward, right click backward"
        ));
        container.setItem(STATUS_SLOT, makeToggleItem(
            Items.BOOK,
            "Factory Gauge Status",
            "Skips: " + PatchRuntime.getFactoryGaugeSkips(),
            "Forced runs: " + PatchRuntime.getFactoryGaugeForcedRuns(),
            "Adaptive interval follows server MSPT"
        ));
        container.setItem(HELP_SLOT, makeToggleItem(
            Items.WRITABLE_BOOK,
            "Admin Panel Help",
            "This panel changes runtime settings only",
            "Validated patch logic stays unchanged",
            "Commands still exist as fallback"
        ));
    }

    private void fillBackground() {
        for (int i = 0; i < container.getContainerSize(); i++) {
            container.setItem(i, namedStack(Items.GRAY_STAINED_GLASS_PANE, " "));
        }
    }

    private static ItemStack makeToggleItem(net.minecraft.world.item.Item item, String title, String... lines) {
        ItemStack stack = new ItemStack(item);
        StringBuilder builder = new StringBuilder(title);
        for (String line : lines) {
            builder.append(" | ").append(line);
        }
        stack.set(DataComponents.CUSTOM_NAME, Component.literal(builder.toString()).withStyle(ChatFormatting.RESET));
        return stack;
    }

    private static ItemStack namedStack(net.minecraft.world.item.Item item, String title) {
        ItemStack stack = new ItemStack(item);
        stack.set(DataComponents.CUSTOM_NAME, Component.literal(title).withStyle(ChatFormatting.DARK_GRAY));
        return stack;
    }

    private static String onOff(boolean enabled) {
        return enabled ? "ON" : "OFF";
    }

    private static String formatSimulatedMspt() {
        Double simulated = PatchRuntime.getSimulatedMspt();
        if (simulated == null) {
            return "Off";
        }
        return String.format(java.util.Locale.ROOT, "%.0f", simulated);
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
