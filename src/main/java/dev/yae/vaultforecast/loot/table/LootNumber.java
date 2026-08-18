package dev.yae.vaultforecast.loot.table;

import net.minecraft.util.math.MathHelper;

public interface LootNumber {
    int nextInt(LootRollContext context);

    float nextFloat(LootRollContext context);

    static LootNumber constant(int value) {
        return new LootNumber() {
            @Override
            public int nextInt(LootRollContext context) {
                return value;
            }

            @Override
            public float nextFloat(LootRollContext context) {
                return value;
            }
        };
    }

    static LootNumber uniform(int min, int max) {
        return new LootNumber() {
            @Override
            public int nextInt(LootRollContext context) {
                return MathHelper.nextInt(context.random(), min, max);
            }

            @Override
            public float nextFloat(LootRollContext context) {
                return MathHelper.nextFloat(context.random(), min, max);
            }
        };
    }

    static LootNumber uniformFloat(float min, float max) {
        return new LootNumber() {
            @Override
            public int nextInt(LootRollContext context) {
                return MathHelper.nextInt(context.random(), Math.round(min), Math.round(max));
            }

            @Override
            public float nextFloat(LootRollContext context) {
                return MathHelper.nextFloat(context.random(), min, max);
            }
        };
    }
}
