package com.mythmc.commands.gui;

import com.cryptomorin.xseries.XMaterial;
import com.mythmc.MineBBSTopper;
import com.mythmc.commands.gui.external.CustomInventory;
import com.mythmc.commands.gui.external.RewardSlotData;
import com.mythmc.file.statics.GUIFile;
import com.mythmc.impl.cache.TargetManager;
import com.mythmc.impl.cache.target.PlayerInfo;
import com.mythmc.tools.utils.ColorUtil;
import com.mythmc.tools.utils.ItemUtil;
import com.mythmc.tools.utils.PAPIUtil;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class RewardGUI implements Listener {
    // 存储每个槽位的奖励数据
    // 将 slotData 的类型从 Map<Integer, SlotData> 改为 Map<List<Integer>, SlotData>
    public static Map<List<Integer>, RewardSlotData> slotData;
    public static Map<String, Inventory> cachedRewardGUI = new HashMap<>();

    // 构造函数，初始化奖励槽位数据
    public RewardGUI() {
        initializeSlotData();
    }

    // 初始化槽位数据的方法
    public void initializeSlotData() {
        // 先将 slotData 设为 null，然后创建一个新的 HashMap 实例
        slotData = null;
        slotData = new HashMap<>();
        // 从配置文件中获取菜单项配置
        ConfigurationSection menuItems = GUIFile.rewardMenuItems;
        // 遍历所有菜单项的键
        for (String key : menuItems.getKeys(false)) {
            // 获取每个菜单项的配置
            ConfigurationSection itemSection = menuItems.getConfigurationSection(key);
            // 获取槽位编号，可以是单一的整数或整数列表
            Object slotObj = itemSection.get("slot");
            // 获取该项的命令列表
            List<String> commands = itemSection.getStringList("commands");
            // 获取该项所需的条件值，默认为1000
            int require = itemSection.getInt("require", 1000);
            // 获取该项的标签，默认为100
            int label = itemSection.getInt("label", 100);

            // 创建 SlotData 对象
            RewardSlotData rewardSlotData = new RewardSlotData(commands, require, label);

            // 如果 slotObj 是单个整数，则将该整数转换为单元素列表并存入 slotData
            if (slotObj instanceof Integer) {
                List<Integer> slots = Collections.singletonList((Integer) slotObj);
                // 将 SlotData 存入 slotData 映射中
                RewardGUI.slotData.put(slots, rewardSlotData);
            }
            // 如果 slotObj 是整数列表，则将列表中的每个整数转换为列表并存入 slotData
            else if (slotObj instanceof List) {
                @SuppressWarnings("unchecked")
                List<Integer> slots = (List<Integer>) slotObj;
                // 将 SlotData 存入 slotData 映射中
                RewardGUI.slotData.put(slots, rewardSlotData);
            }
        }
    }

    // 打开奖励菜单的方法
    public static void openMenu(Player player) {
        String playerName = player.getName();

        // 查找缓存，判断是否需要重新创建
        if (cachedRewardGUI.containsKey(playerName)) {
            player.openInventory(cachedRewardGUI.get(playerName));
        } else {
            // 创建一个新的菜单，标题会根据 PlaceholderHook 进行替换
            Inventory rewardGUI = Bukkit.createInventory(null, GUIFile.rewardMenuSize, PAPIUtil.set(player, GUIFile.rewardMenuName));
            // 从配置文件中获取菜单项配置
            ConfigurationSection menuItems = GUIFile.rewardMenuItems;

            // 获取玩家信息
            PlayerInfo playerInfo = TargetManager.getPlayerInfo(player);
            int need = playerInfo.getAmount(); // 获取玩家的记录数量
            String[] rewardData = playerInfo.getRewardData(); // 获取玩家的奖励数据

            // 处理已领取物品
            String[] claimedMat = GUIFile.rewardMenuClaimedMat.split("#");

            // 遍历所有菜单项的键
            for (String key : menuItems.getKeys(false)) {
                // 获取每个菜单项的配置
                ConfigurationSection itemSection = menuItems.getConfigurationSection(key);

                // 创建物品堆实例
                ItemStack itemStack;
                // 获取物品的材料名称，默认为 "BEDROCK"
                String materialName = itemSection.getString("mat", "BEDROCK").toUpperCase();
                // 获取物品的数量，默认为1
                int materialAmount = itemSection.getInt("amount", 1);
                // 获取物品的标签，默认为100
                int label = itemSection.getInt("label", 100);

                // 根据标签和奖励数据判断物品的状态，100为非奖励物品
                itemStack = (label != 100)
                        // 如果标签不等于100
                        ? (rewardData[label - 1].equals("0")
                        // 如果rewardData中的值为"0"，则创建未领取状态的物品堆
                        ? ItemUtil.handleItemStack(player, materialName, materialAmount)
                        // 否则，创建已领取状态的物品堆
                        : new ItemStack(XMaterial.valueOf(claimedMat[0]).parseMaterial()))
                        // 如果标签为100，则创建一个普通物品堆
                        : ItemUtil.handleItemStack(player, materialName, materialAmount);

                // 获取物品的元数据
                ItemMeta itemMeta = itemStack.getItemMeta();

                // 设置物品的显示名称
                itemMeta.setDisplayName(ColorUtil.colorize(PAPIUtil.set(player, itemSection.getString("name", " "))));

                // 设置物品的自定义模型数据（如果不是低版本）
                if (!MineBBSTopper.isLowVersion) {
                    int cmd = (claimedMat.length > 1 && claimedMat[1] != null) ? Integer.parseInt(claimedMat[1]) : 0;
                    itemMeta.setCustomModelData(itemSection.getInt("cmd", cmd));
                }
                // 处理发光逻辑
                if (itemSection.getBoolean("glow", false)) itemMeta = ItemUtil.addGlow(itemMeta);

                // 处理flag
                itemMeta = ItemUtil.addItemFlags(itemMeta, (List<String>) itemSection.getList("flag"));

                // 获取物品的描述信息
                List<String> lore = itemSection.getStringList("lore");
                int size = lore.size();
                for (int i = 0; i < size; i++) {
                    String line = lore.get(i);

                    // 替换描述中的状态占位符
                    if (line.contains("%status%")) {
                        int require = itemSection.getInt("require");
                        String statusReplacement;

                        if (rewardData[label - 1].equals("1")) {
                            statusReplacement = GUIFile.rewardMenuStatusClaim;
                        } else if (need >= require) {
                            statusReplacement = GUIFile.rewardMenuStatusYes
                                    .replace("%count%", String.valueOf(require))
                                    .replace("%current%", String.valueOf(need));
                        } else {
                            statusReplacement = GUIFile.rewardMenuStatusNo
                                    .replace("%count%", String.valueOf(require))
                                    .replace("%current%", String.valueOf(need));
                        }

                        // 替换字符串中的 %status% 占位符
                        line = line.replace("%status%", statusReplacement);
                    }
                    // 更新列表中的该行
                    lore.set(i, line);
                }

                // 设置物品的描述信息，替换描述中的占位符
                itemMeta.setLore(ColorUtil.colorize(PAPIUtil.set(player, lore)));
                itemStack.setItemMeta(itemMeta);

                // 获取槽位编号，并将物品设置到相应的槽位
                Object slotObj = itemSection.get("slot");
                if (slotObj instanceof List) {
                    @SuppressWarnings("unchecked")
                    List<Integer> slots = (List<Integer>) slotObj;
                    for (int slot : slots) {
                        rewardGUI.setItem(slot, itemStack);
                    }
                } else {
                    int slot = itemSection.getInt("slot");
                    rewardGUI.setItem(slot, itemStack);
                }
            }
            // 先移除以前旧的 CustomInventory
            CustomInventory.removeInventoryByPlayerName(playerName);
            // 创建 CustomInventory 实例并保存到 Map 中
            CustomInventory customInventory = new CustomInventory(rewardGUI, "reward", playerName);
            CustomInventory.inventoryMap.put(rewardGUI, customInventory);
            // 打开玩家的菜单界面
            player.openInventory(rewardGUI);
            cachedRewardGUI.put(playerName, rewardGUI);
        }


//    private void countPlayerRecordsAsync(String playerName, DatabaseCallback<Integer> callback) {
//        new BukkitRunnable() {
//            @Override
//            public void run() {
//                int count = DatabaseManager.getDbManager().countPlayerRecords(playerName);
//                runTask(MineBBSTopper.INSTANCE, () -> callback.onQueryDone(count));
//            }
//        }.runTaskAsynchronously(MineBBSTopper.INSTANCE);
//    }


    }
}
