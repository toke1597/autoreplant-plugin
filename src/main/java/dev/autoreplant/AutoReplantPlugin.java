package dev.autoreplant;

import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.block.Block;
import org.bukkit.block.data.Ageable;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Set;

/** Fabric 모드 Auto Replant 를 Paper 플러그인으로 옮긴 것. 다 자란 작물을 캐면 씨앗을 하나 써서 다시 심는다. */
public final class AutoReplantPlugin extends JavaPlugin implements Listener {

    // 다시 심을 때 나는 "뽁" 소리. 다른 소리로 바꾸려면 여기만 고치면 된다.
    private static final Sound REPLANT_SOUND = Sound.ENTITY_ITEM_PICKUP;
    private static final float REPLANT_SOUND_PITCH = 1.4f;

    // 모드의 CropBlock / NetherWartBlock 에 해당하는 블록 (줄기·피처 작물 제외)
    private static final Set<Material> CROPS = Set.of(
            Material.WHEAT, Material.CARROTS, Material.POTATOES, Material.BEETROOTS,
            Material.TORCHFLOWER_CROP, Material.NETHER_WART);

    @Override
    public void onEnable() {
        getServer().getPluginManager().registerEvents(this, this);
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        Material crop = block.getType();
        Player player = event.getPlayer();
        if (!isMatureCrop(block.getBlockData()) || !player.hasPermission("autoreplant.use")) return;
        // 블록이 실제로 부서지고 드롭이 생긴 다음 틱에 심는다.
        getServer().getScheduler().runTask(this, () -> tryReplant(player, block, crop));
    }

    private static boolean isMatureCrop(BlockData data) {
        return CROPS.contains(data.getMaterial()) && data instanceof Ageable age && age.getAge() >= age.getMaximumAge();
    }

    private static void tryReplant(Player player, Block block, Material crop) {
        BlockData seedling = crop.createBlockData();
        if (!block.getType().isAir() || !seedling.isSupported(block)) {
            return;
        }

        // 밀 -> 밀 씨앗, 당근 -> 당근처럼 작물 블록을 심는 아이템
        Material seed = seedling.getPlacementMaterial();
        if (player.getGameMode() != GameMode.CREATIVE && !consumeSeed(player, block, seed)) {
            return;
        }

        block.setBlockData(seedling);
        Location center = block.getLocation().add(0.5, 0.25, 0.5);
        block.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, center, 15, 0.3, 0.2, 0.3, 0.0);
        block.getWorld().playSound(center, REPLANT_SOUND, SoundCategory.BLOCKS, 0.8f, REPLANT_SOUND_PITCH);
    }

    /**
     * 인벤토리에서 씨앗을 하나 줄인다. 인벤토리에 없으면 방금 떨어진 드롭에서 하나 가져온다
     * (처음 수확할 때 씨앗이 아직 바닥에 있어서 인벤토리에 없는 경우).
     */
    private static boolean consumeSeed(Player player, Block block, Material seed) {
        PlayerInventory inventory = player.getInventory();
        for (int i = 0; i < inventory.getSize(); i++) {
            ItemStack stack = inventory.getItem(i);
            if (stack != null && stack.getType() == seed) {
                stack.subtract();
                return true;
            }
        }

        Location center = block.getLocation().add(0.5, 0.5, 0.5);
        for (Item drop : block.getWorld().getNearbyEntitiesByType(Item.class, center, 1.5)) {
            ItemStack stack = drop.getItemStack();
            if (stack.getType() != seed) continue;
            stack.subtract();
            if (stack.isEmpty()) {
                drop.remove();
            } else {
                drop.setItemStack(stack);
            }
            return true;
        }
        return false;
    }
}
