package com.example.entity;

import com.example.TemplateMod;
import com.example.block.DiscoJukeboxBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrowableItemProjectile;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

public class DiscoBallEntity extends ThrowableItemProjectile {

	public DiscoBallEntity(EntityType<? extends DiscoBallEntity> entityType, Level level) {
		super(entityType, level);
	}

	public DiscoBallEntity(Level level, LivingEntity owner, ItemStack itemStack) {
		super(TemplateMod.DISCO_BALL_ENTITY_TYPE, owner, level, itemStack);
	}

	public DiscoBallEntity(Level level, double x, double y, double z, ItemStack itemStack) {
		super(TemplateMod.DISCO_BALL_ENTITY_TYPE, x, y, z, level, itemStack);
	}

	@Override
	protected Item getDefaultItem() {
		return TemplateMod.DISCO_BALL_ITEM;
	}

	@Override
	protected double getDefaultGravity() {
		return 0.05;
	}

	@Override
	protected void onHit(HitResult hitResult) {
		super.onHit(hitResult);
		if (this.level() instanceof ServerLevel serverLevel) {
			tryPlaceJukebox(serverLevel, hitResult);
			this.discard();
		}
	}

	private void tryPlaceJukebox(ServerLevel level, HitResult hitResult) {
		BlockHitResult blockHit;
		if (hitResult instanceof BlockHitResult bh) {
			blockHit = bh;
		} else {
			Vec3 loc = hitResult.getLocation();
			BlockPos pos = BlockPos.containing(loc);
			blockHit = new BlockHitResult(loc, Direction.UP, pos, false);
		}

		Player player = this.getOwner() instanceof Player p ? p : null;
		BlockPlaceContext context = new BlockPlaceContext(
			level,
			player,
			InteractionHand.MAIN_HAND,
			new ItemStack(TemplateMod.DISCO_JUKEBOX_ITEM),
			blockHit
		);

		if (TemplateMod.DISCO_JUKEBOX_ITEM instanceof BlockItem jukeboxBlockItem) {
			InteractionResult result = jukeboxBlockItem.place(context);
			if (result.consumesAction()) {
				BlockPos pos = context.getClickedPos();
				if (level.getBlockState(pos).is(TemplateMod.DISCO_JUKEBOX_BLOCK)
					&& level.getBlockEntity(pos) instanceof DiscoJukeboxBlockEntity) {
					DiscoJukeboxBlockEntity.removeOtherDiscoJukeboxes(
						level.getServer(),
						level,
						pos
					);
				}

				level.sendParticles(
					ParticleTypes.NOTE,
					pos.getX() + 0.5,
					pos.getY() + 1.2,
					pos.getZ() + 0.5,
					8,
					0.3,
					0.2,
					0.3,
					0.5
				);
			}
		}
	}
}
