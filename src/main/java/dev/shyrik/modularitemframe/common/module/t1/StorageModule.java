package dev.shyrik.modularitemframe.common.module.t1;

import alexiil.mc.lib.attributes.Simulation;
import alexiil.mc.lib.attributes.item.impl.DirectFixedItemInv;
import dev.shyrik.modularitemframe.ModularItemFrame;
import dev.shyrik.modularitemframe.api.ModuleBase;
import dev.shyrik.modularitemframe.api.util.InventoryHelper;
import dev.shyrik.modularitemframe.api.util.ItemHelper;
import dev.shyrik.modularitemframe.client.FrameRenderer;
import dev.shyrik.modularitemframe.client.helper.ItemRenderHelper;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.block.BlockState;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.model.json.ModelTransformation;
import net.minecraft.client.resource.language.I18n;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;

public class StorageModule extends ModuleBase {

    public static final Identifier ID = new Identifier(ModularItemFrame.MOD_ID, "module_t1_storage");
    public static final Identifier BG_LOC = new Identifier(ModularItemFrame.MOD_ID, "block/module_t1_storage");

    private static final String NBT_LAST = "lastclick";
    private static final String NBT_LASTSTACK = "laststack";
    private static final String NBT_INVENTORY= "inventory";

    private DirectFixedItemInv inventory = new DirectFixedItemInv(1);

    private long lastClick;
    private ItemStack lastStack = ItemStack.EMPTY;

    @Override
    public Identifier getId() {
        return ID;
    }

    @Override
    public Identifier frontTexture() {
        return BG_LOC;
    }

    @Override
    @Environment(EnvType.CLIENT)
    public String getModuleName() {
        return I18n.translate("modularitemframe.module.storage");
    }

    @Override
    @Environment(EnvType.CLIENT)
    public void specialRendering(FrameRenderer renderer, MatrixStack matrixStack, float partialTicks, VertexConsumerProvider buffer, int combinedLight, int combinedOverlay) {
        ItemRenderHelper.renderOnFrame(lastStack, blockEntity.blockFacing(), 0, 0.1F, ModelTransformation.Mode.FIXED, matrixStack, buffer, combinedLight, combinedOverlay);
    }

    @Override
    public void onBlockClicked(World world, BlockPos pos, PlayerEntity player) {
        if (!world.isClient) {
            ItemStack attempt = inventory.attemptAnyExtraction(1, Simulation.SIMULATE);
            if (!attempt.isEmpty()) {
                int amount = player.isSneaking() ? attempt.getMaxCount() : 1;
                ItemStack extract = inventory.extract(amount);
                extract = InventoryHelper.givePlayer(player, extract);
                if (!extract.isEmpty()) ItemHelper.ejectStack(world, pos, blockEntity.blockFacing(), extract);
                lastStack = inventory.attemptAnyExtraction(1, Simulation.SIMULATE);
                blockEntity.markDirty();
            }
        }
    }

    @Override
    public ActionResult onUse(World world, BlockPos pos, BlockState state, PlayerEntity player, Hand hand, Direction facing, BlockHitResult hit) {
        if (!world.isClient) {
            ItemStack held = player.getStackInHand(hand);
            if (lastStack.isEmpty() || ItemStack.areItemsEqual(lastStack, held)) {
                long time = world.getTime();

                if (time - lastClick <= 8L && !player.isSneaking() && !lastStack.isEmpty())
                    InventoryHelper.giveAllPossibleStacks(inventory, player.inventory, lastStack, held);
                else if (!held.isEmpty()) {
                    ItemStack heldCopy = held.copy();
                    heldCopy.setCount(1);
                    if (inventory.insert(heldCopy).isEmpty()) {
                        held.decrement(1);

                        lastStack = heldCopy;
                        lastClick = time;
                    }
                }
                blockEntity.markDirty();
            }
        }
        return ActionResult.SUCCESS;
    }

    @Override
    public void onFrameUpgradesChanged() {
        int newCapacity = (int)Math.pow(2, blockEntity.getCapacityUpCount());
        DirectFixedItemInv tmp = new DirectFixedItemInv(newCapacity);
        for (int slot = 0; slot < inventory.getSlotCount(); slot++) {
            if (slot < tmp.getSlotCount())
                tmp.setInvStack(slot, inventory.getInvStack(slot), Simulation.ACTION);
            else
                ItemHelper.ejectStack(blockEntity.getWorld(), blockEntity.getPos(), blockEntity.blockFacing(), inventory.getInvStack(slot));
        }
        inventory = tmp;
        blockEntity.markDirty();
    }

    @Override
    public void onRemove(World world, BlockPos pos, Direction facing, PlayerEntity player) {
        super.onRemove(world, pos, facing, player);
        for( int slot = 0; slot < inventory.getSlotCount(); slot++) {
            ItemHelper.ejectStack(world, pos, blockEntity.blockFacing(), inventory.getInvStack(slot));
        }
    }

    @Override
    public CompoundTag toTag() {
        CompoundTag tag = super.toTag();
        tag.put(NBT_INVENTORY, inventory.toTag());
        tag.putLong(NBT_LAST, lastClick);
        tag.put(NBT_LASTSTACK, lastStack.toTag(new CompoundTag()));
        return tag;
    }

    @Override
    public void fromTag(CompoundTag tag) {
        super.fromTag(tag);
        if (tag.contains(NBT_INVENTORY)) inventory.fromTag(tag.getCompound(NBT_INVENTORY));
        if (tag.contains(NBT_LAST)) lastClick = tag.getLong(NBT_LAST);
        if (tag.contains(NBT_LASTSTACK)) lastStack = ItemStack.fromTag(tag.getCompound(NBT_LASTSTACK));
    }
}
