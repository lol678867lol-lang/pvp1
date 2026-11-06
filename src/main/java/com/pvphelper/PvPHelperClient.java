package com.pvphelper;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.*;
import net.minecraft.registry.entry.RegistryKey;
import net.minecraft.text.Text;
import java.util.HashSet;
import java.util.Set;

public class PvPHelperClient implements ClientModInitializer {
    public static boolean enabled = false;
    private static String betterInvMsg = "";
    private static ItemEntity bestLoot = null;
    private static double bestLootDist = 0;
    private static String bestLootArrow = "";
    private static boolean showHitten = false;
    private static boolean isFalling = false;
    private static final Set<ItemEntity> glowing = new HashSet<>();

    @Override
    public void onInitializeClient() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
            dispatcher.register(ClientCommandManager.literal("invtracker")
                .then(ClientCommandManager.literal("on").executes(ctx -> {
                    enabled = true;
                    ctx.getSource().sendFeedback(Text.literal("§a[InvTracker] AN - HITTEN + Glow + Radar aktiv!"));
                    return 1;
                }))
                .then(ClientCommandManager.literal("off").executes(ctx -> {
                    enabled = false;
                    showHitten = false; bestLoot = null; betterInvMsg = "";
                    clearGlow();
                    ctx.getSource().sendFeedback(Text.literal("§c[InvTracker] AUS"));
                    return 1;
                }))
                .executes(ctx -> {
                    ctx.getSource().sendFeedback(Text.literal("§e[InvTracker] Status: " + (enabled ? "§aAN" : "§cAUS")));
                    return 1;
                })
            );
        });

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (!enabled || client.player == null || client.world == null) return;
            PlayerEntity p = client.player;
            float cooldown = p.getAttackCooldownProgress(0);
            boolean enemyNear = false;
            for (LivingEntity e : client.world.getEntitiesByClass(LivingEntity.class, p.getBoundingBox().expand(3.5), en -> en != p && en.isAlive() && !en.isSpectator())) {
                enemyNear = true; break;
            }
            showHitten = cooldown >= 1.0f && enemyNear;
            isFalling = !p.isOnGround() && p.getVelocity().y < -0.1;
            betterInvMsg = checkInventory(p);
            bestLoot = findBestLoot(p);
            if (bestLoot != null) {
                bestLootDist = p.distanceTo(bestLoot);
                bestLootArrow = getArrow(p, bestLoot);
            }
            // Glow nur bei Änderung setzen - kein Flackern / Lag
            Set<ItemEntity> shouldGlow = new HashSet<>();
            if (bestLoot != null) shouldGlow.add(bestLoot);
            for (ItemEntity ie : client.world.getEntitiesByClass(ItemEntity.class, p.getBoundingBox().expand(20), e -> true)) {
                if (ie != bestLoot && isBetter(ie.getStack(), getEquippedFor(p, ie.getStack()))) shouldGlow.add(ie);
            }
            for (ItemEntity ie : new HashSet<>(glowing)) {
                if (!shouldGlow.contains(ie)) { ie.setGlowing(false); glowing.remove(ie); }
            }
            for (ItemEntity ie : shouldGlow) {
                if (!glowing.contains(ie)) { ie.setGlowing(true); glowing.add(ie); }
            }
        });

        HudRenderCallback.EVENT.register((DrawContext ctx, RenderTickCounter tick) -> {
            if (!enabled) return;
            MinecraftClient client = MinecraftClient.getInstance();
            if (client.player == null) return;
            int w = ctx.getScaledWindowWidth();
            int h = ctx.getScaledWindowHeight();
            if (showHitten) {
                ctx.drawCenteredTextWithShadow(client.textRenderer, Text.literal("HITTEN!"), w / 2, h / 2 - 50, 0x00FF00);
            }
            if (!betterInvMsg.isEmpty()) ctx.drawTextWithShadow(client.textRenderer, Text.literal("↑ " + betterInvMsg), 10, 10, 0xFFFF00);
            if (bestLoot != null) {
                String name = bestLoot.getStack().getName().getString();
                ctx.drawCenteredTextWithShadow(client.textRenderer, Text.literal("↑ " + name), w / 2, 30, 0x00FF00);
                ctx.drawCenteredTextWithShadow(client.textRenderer, Text.literal((int)bestLootDist + " Bloecke - " + bestLootArrow), w / 2, 42, 0xFFFFFF);
                ctx.drawCenteredTextWithShadow(client.textRenderer, Text.literal(bestLootArrow), w / 2, 60, 0xFFFF00);
            }
            if (isFalling) ctx.drawCenteredTextWithShadow(client.textRenderer, Text.literal("FALLEN -> MACE / SPEAR"), w / 2, h - 60, 0xFFAA00);
            else ctx.drawCenteredTextWithShadow(client.textRenderer, Text.literal("BODEN -> SCHWERT / AXT"), w / 2, h - 60, 0x00AAFF);
            drawHotbarHighlight(ctx, client);
        });
    }

    private void clearGlow() {
        for (ItemEntity ie : glowing) { try { ie.setGlowing(false); } catch (Exception ignored) {} }
        glowing.clear();
    }

    private void drawHotbarHighlight(DrawContext ctx, MinecraftClient client) {
        PlayerEntity p = client.player; if (p == null) return;
        int w = ctx.getScaledWindowWidth(); int h = ctx.getScaledWindowHeight();
        int startX = w / 2 - 91; int y = h - 22;
        for (int i = 0; i < 9; i++) {
            ItemStack s = p.getInventory().getStack(i);
            boolean highlight = false;
            if (s.getItem() instanceof MaceItem || s.getItem() == Items.TRIDENT) highlight = isFalling;
            if (s.getItem() instanceof SwordItem || s.getItem() instanceof AxeItem) highlight = !isFalling;
            if (highlight) {
                int x = startX + i * 20;
                int color = (System.currentTimeMillis() / 200 % 2 == 0) ? 0xFFFFFF00 : 0xFFFFAA00;
                ctx.drawBorder(x, y, 20, 20, color);
            }
        }
    }

    private String checkInventory(PlayerEntity p) {
        for (int slot = 0; slot < 36; slot++) {
            ItemStack inv = p.getInventory().getStack(slot);
            if (inv.getItem() instanceof ArmorItem) {
                ItemStack equipped = getArmorForType(p, inv);
                if (!equipped.isEmpty() && isBetter(inv, equipped)) return "Bessere Ruestung Slot " + slot + ": " + inv.getName().getString();
            }
            if (inv.getItem() instanceof SwordItem || inv.getItem() instanceof AxeItem) {
                ItemStack hand = p.getMainHandStack();
                if (!hand.isEmpty() && hand != inv && isBetter(inv, hand)) return "Besseres Schwert Slot " + slot + ": " + inv.getName().getString();
            }
        }
        return "";
    }

    private ItemStack getArmorForType(PlayerEntity p, ItemStack inv) {
        if (inv.getItem() instanceof ArmorItem a) {
            return switch (a.getSlotType()) {
                case HEAD -> p.getInventory().getArmorStack(3);
                case CHEST -> p.getInventory().getArmorStack(2);
                case LEGS -> p.getInventory().getArmorStack(1);
                case FEET -> p.getInventory().getArmorStack(0);
                default -> ItemStack.EMPTY;
            };
        }
        return ItemStack.EMPTY;
    }

    private ItemEntity findBestLoot(PlayerEntity p) {
        ItemEntity best = null; int bestScore = -1;
        for (ItemEntity e : p.getWorld().getEntitiesByClass(ItemEntity.class, p.getBoundingBox().expand(20), ent -> true)) {
            ItemStack ground = e.getStack();
            ItemStack eq = getEquippedFor(p, ground);
            if (isBetter(ground, eq)) {
                int score = ground.getItem() instanceof ArmorItem ? getArmorScore(ground) : getWeaponScore(ground);
                int finalScore = score - (int)(p.distanceTo(e) * 2);
                if (finalScore > bestScore) { bestScore = finalScore; best = e; }
            }
        }
        return best;
    }

    private ItemStack getEquippedFor(PlayerEntity p, ItemStack ground) {
        if (ground.getItem() instanceof ArmorItem) return getArmorForType(p, ground);
        if (ground.getItem() instanceof SwordItem || ground.getItem() instanceof AxeItem) return p.getMainHandStack();
        return ItemStack.EMPTY;
    }

    private String getArrow(PlayerEntity p, ItemEntity target) {
        double dx = target.getX() - p.getX();
        double dz = target.getZ() - p.getZ();
        // Minecraft Yaw: 0 = Süden (+Z), 90 = Westen (-X). Ziel-Yaw korrekt berechnen:
        double targetYaw = Math.toDegrees(Math.atan2(-dx, dz));
        double diff = targetYaw - p.getYaw();
        while (diff > 180) diff -= 360;
        while (diff < -180) diff += 360;
        if (diff >= -22.5 && diff < 22.5) return "↑";
        if (diff >= 22.5 && diff < 67.5) return "↗";
        if (diff >= 67.5 && diff < 112.5) return "→";
        if (diff >= 112.5 && diff < 157.5) return "↘";
        if (diff >= 157.5 || diff < -157.5) return "↓";
        if (diff >= -157.5 && diff < -112.5) return "↙";
        if (diff >= -112.5 && diff < -67.5) return "←";
        return "↖";
    }

    // --- VERGLEICH: PROT 4 + DURA ZÄHLT ---
    public static boolean isBetter(ItemStack neu, ItemStack alt) {
        if (alt == null || alt.isEmpty()) return true;
        if (neu.getItem() instanceof ArmorItem && alt.getItem() instanceof ArmorItem) return getArmorScore(neu) > getArmorScore(alt);
        if ((neu.getItem() instanceof SwordItem || neu.getItem() instanceof AxeItem) && (alt.getItem() instanceof SwordItem || alt.getItem() instanceof AxeItem)) return getWeaponScore(neu) > getWeaponScore(alt);
        return false;
    }

    public static int getArmorScore(ItemStack s) {
        int prot = getEnchant(s, Enchantments.PROTECTION);
        int duraLeft = s.getMaxDamage() - s.getDamage();
        int mat = getMaterialValue(s.getItem());
        // Prot 4 gewinnt IMMER gegen Prot 3, egal welches Material + Dura
        return prot * 1000 + Math.min(duraLeft, 500) + mat;
    }

    public static int getWeaponScore(ItemStack s) {
        float dmg = 0;
        if (s.getItem() instanceof SwordItem sw) dmg = 4 + sw.getAttackDamage();
        if (s.getItem() instanceof AxeItem ax) dmg = 5 + ax.getAttackDamage();
        if (dmg == 0) dmg = getMaterialValue(s.getItem());
        int sharp = getEnchant(s, Enchantments.SHARPNESS);
        int duraLeft = s.getMaxDamage() - s.getDamage();
        return (int)(dmg * 100) + sharp * 50 + Math.min(duraLeft / 10, 50);
    }

    private static int getMaterialValue(Item item) {
        String n = item.toString().toLowerCase();
        if (n.contains("netherite")) return 40;
        if (n.contains("diamond")) return 30;
        if (n.contains("iron")) return 20;
        if (n.contains("gold")) return 15;
        if (n.contains("stone")) return 10;
        if (n.contains("wood")) return 5;
        if (n.contains("leather")) return 5;
        if (n.contains("chain")) return 12;
        return 0;
    }

    private static int getEnchant(ItemStack s, RegistryKey<Enchantment> key) {
        try {
            var enchants = EnchantmentHelper.getEnchantments(s);
            for (var entry : enchants.getEnchantmentEntries()) {
                if (entry.getKey().matchesKey(key)) {
                    return entry.getIntValue();
                }
            }
        } catch (Exception ignored) {}
        return 0;
    }
}
