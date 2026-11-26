package com.divinis.city;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.WallSignBlock;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.entity.SignText;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Where a part of the city is drawn: block coordinates relative to an origin, turned by a rotation. A building is
 * always drawn facing north (its front at z = 0, the street at z < 0); the canvas turns it, and every block state
 * with it, to face wherever it stands. y = 0 is the street level (the ground block is at y = -1).
 */
final class Canvas {
    /** Send to clients and update neighbouring shapes (fences, panes, walls join), but no block updates. */
    static final int FLAGS = Block.UPDATE_CLIENTS;

    final ServerLevel level;
    private final BlockPos origin;
    private final Rotation rot;

    Canvas(ServerLevel level, BlockPos origin, Rotation rot) {
        this.level = level;
        this.origin = origin;
        this.rot = rot;
    }

    /** A canvas at x, y, z of this one, turned further by r. */
    Canvas at(int x, int y, int z, Rotation r) {
        return new Canvas(level, pos(x, y, z), rot.getRotated(r));
    }

    Canvas at(int x, int y, int z) {
        return at(x, y, z, Rotation.NONE);
    }

    BlockPos pos(int x, int y, int z) {
        return origin.offset(new BlockPos(x, y, z).rotate(rot));
    }

    /** A direction of this canvas in the world. */
    Direction dir(Direction d) {
        return d.getAxis().isVertical() ? d : rot.rotate(d);
    }

    void set(int x, int y, int z, BlockState s) {
        level.setBlock(pos(x, y, z), s.rotate(rot), FLAGS);
    }

    void set(int x, int y, int z, Block b) {
        set(x, y, z, b.defaultBlockState());
    }

    BlockState get(int x, int y, int z) {
        return level.getBlockState(pos(x, y, z));
    }

    boolean isAir(int x, int y, int z) {
        return get(x, y, z).isAir();
    }

    void setIfAir(int x, int y, int z, BlockState s) {
        if (isAir(x, y, z)) set(x, y, z, s);
    }

    void fill(int x0, int y0, int z0, int x1, int y1, int z1, BlockState s) {
        for (int x = Math.min(x0, x1); x <= Math.max(x0, x1); x++)
            for (int y = Math.min(y0, y1); y <= Math.max(y0, y1); y++)
                for (int z = Math.min(z0, z1); z <= Math.max(z0, z1); z++) set(x, y, z, s);
    }

    void fill(int x0, int y0, int z0, int x1, int y1, int z1, Block b) {
        fill(x0, y0, z0, x1, y1, z1, b.defaultBlockState());
    }

    void clear(int x0, int y0, int z0, int x1, int y1, int z1) {
        fill(x0, y0, z0, x1, y1, z1, Blocks.AIR.defaultBlockState());
    }

    /** A wall sign with glowing text; facing is the side it is read from (canvas direction). */
    void sign(int x, int y, int z, Direction facing, BlockState sign, String... lines) {
        BlockPos p = pos(x, y, z);
        level.setBlock(p, sign.setValue(WallSignBlock.FACING, dir(facing)), Block.UPDATE_ALL);
        if (level.getBlockEntity(p) instanceof SignBlockEntity be) {
            SignText text = new SignText();
            for (int i = 0; i < lines.length && i < 4; i++) text = text.setMessage(i, Component.literal(lines[i]));
            be.setText(text.setHasGlowingText(true), true);
            be.setText(text.setHasGlowingText(true), false);
            be.setWaxed(true);
        }
    }
}
