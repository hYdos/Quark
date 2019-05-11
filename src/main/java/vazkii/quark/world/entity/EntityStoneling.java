package vazkii.quark.world.entity;

import java.util.List;

import net.minecraft.entity.EntityCreature;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.ai.EntityAIBase;
import net.minecraft.entity.ai.EntityAIWanderAvoidWater;
import net.minecraft.entity.ai.RandomPositionGenerator;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.SoundEvents;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.datasync.DataParameter;
import net.minecraft.network.datasync.DataSerializers;
import net.minecraft.network.datasync.EntityDataManager;
import net.minecraft.pathfinding.Path;
import net.minecraft.pathfinding.PathNavigate;
import net.minecraft.pathfinding.PathPoint;
import net.minecraft.util.DamageSource;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.EnumSkyBlock;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraft.world.storage.loot.LootContext;
import vazkii.quark.world.feature.Stonelings;

public class EntityStoneling extends EntityCreature {

	public static final ResourceLocation CARRY_LOOT_TABLE = new ResourceLocation("quark", "entities/stoneling_carry");
	public static final ResourceLocation LOOT_TABLE = new ResourceLocation("quark", "entities/stoneling");

	private static final DataParameter<ItemStack> CARRYING_ITEM = EntityDataManager.createKey(EntityStoneling.class, DataSerializers.ITEM_STACK);

	private static final String TAG_CARRYING_ITEM = "carryingItem";
	private static final String TAG_STARTLED = "startled";

	boolean startled;

	public EntityStoneling(World worldIn) {
		super(worldIn);
		setSize(0.5F, 1F);
	}

	@Override
	protected void entityInit() {
		super.entityInit();

		dataManager.register(CARRYING_ITEM, ItemStack.EMPTY);
	}

	@Override
	protected void initEntityAI() {
		tasks.addTask(1, new EntityAIWanderAvoidWater(this, 0.2D, 1F));
		tasks.addTask(2, new EntityAIRunAwayWhenStartled(this));
	}
	
	@Override
	protected void applyEntityAttributes() {
		super.applyEntityAttributes();
        getEntityAttribute(SharedMonsterAttributes.MAX_HEALTH).setBaseValue(8.0D);
        getEntityAttribute(SharedMonsterAttributes.KNOCKBACK_RESISTANCE).setBaseValue(1.0D);
	}

	@Override
	public void onUpdate() {
		super.onUpdate();

		if(!world.isRemote) {
			if(dataManager.get(CARRYING_ITEM).isEmpty()) {
				List<ItemStack> items = world.getLootTableManager().getLootTableFromLocation(CARRY_LOOT_TABLE).generateLootForPools(rand, new LootContext.Builder((WorldServer) world).build());
				if(!items.isEmpty())
					dataManager.set(CARRYING_ITEM, items.get(0));
			}
			
			if(!startled) {
				AxisAlignedBB aabb = getEntityBoundingBox().grow(4);
				List<EntityPlayer> playersAround = world.getEntitiesWithinAABB(EntityPlayer.class, aabb, 
						player -> !player.isSneaking() && !player.isCreative());
				if(playersAround.size() > 0)
					startle();
			} else if(navigator.getPath() != null && 
					navigator.getPath().getFinalPathPoint().distanceTo(new PathPoint((int) posX, (int) posY, (int) posZ)) < 2) {
				if(world instanceof WorldServer) {
					WorldServer ws = (WorldServer) world;
					ws.spawnParticle(EnumParticleTypes.CLOUD, posX, posY, posZ, 40, 0.5, 0.5, 0.5, 0.1);
					ws.spawnParticle(EnumParticleTypes.EXPLOSION_NORMAL, posX, posY, posZ, 20, 0.5, 0.5, 0.5, 0);
				}
				setDead();
			}
		}
	}
	
	@Override
	protected void damageEntity(DamageSource damageSrc, float damageAmount) {
		super.damageEntity(damageSrc, damageAmount);
		
		if(damageSrc.getTrueSource() instanceof EntityPlayer)
			startle();
	}
	
	private void startle() {
		if(!startled) {
			startled = true;
			world.playSound(null, posX, posY, posZ, SoundEvents.ENTITY_GHAST_SCREAM, SoundCategory.NEUTRAL, 1.0F, 1.0F);
		}
	}
	
	@Override
	protected void dropEquipment(boolean wasRecentlyHit, int lootingModifier) {
		super.dropEquipment(wasRecentlyHit, lootingModifier);
		
		ItemStack stack = getCarryingItem();
		if(!stack.isEmpty())
			entityDropItem(stack, 0F);
	}
	
	@Override
	protected ResourceLocation getLootTable() {
		return Stonelings.enableDiamondHeart ? LOOT_TABLE : null;
	}

	public ItemStack getCarryingItem() {
		return dataManager.get(CARRYING_ITEM);
	}

	@Override
	public void readEntityFromNBT(NBTTagCompound compound) {
		super.readEntityFromNBT(compound);

		if(compound.hasKey(TAG_CARRYING_ITEM, 10)) {
			NBTTagCompound itemCmp = compound.getCompoundTag(TAG_CARRYING_ITEM);
			ItemStack stack = new ItemStack(itemCmp);
			dataManager.set(CARRYING_ITEM, stack);
		}

		startled = compound.getBoolean(TAG_STARTLED);
	}

	@Override
	public void writeEntityToNBT(NBTTagCompound compound) {
		super.writeEntityToNBT(compound);

		NBTTagCompound itemCmp = new NBTTagCompound();
		dataManager.get(CARRYING_ITEM).writeToNBT(itemCmp);
		compound.setTag(TAG_CARRYING_ITEM, itemCmp);
		compound.setBoolean(TAG_STARTLED, startled);
	}
	
	@Override
	public boolean getCanSpawnHere() {
		return Stonelings.dimensions.canSpawnHere(world) && posY < Stonelings.maxYLevel && isValidLightLevel() && super.getCanSpawnHere();
	}
	
	// vanilla copy pasta
    protected boolean isValidLightLevel() {
        BlockPos blockpos = new BlockPos(posX, getEntityBoundingBox().minY, posZ);

        if(world.getLightFor(EnumSkyBlock.SKY, blockpos) > rand.nextInt(32))
            return false;
        else {
            int i = world.getLightFromNeighbors(blockpos);

            if (world.isThundering()) {
                int j = world.getSkylightSubtracted();
                world.setSkylightSubtracted(10);
                i = world.getLightFromNeighbors(blockpos);
                world.setSkylightSubtracted(j);
            }

            return i <= rand.nextInt(8);
        }
    }

	private static class EntityAIRunAwayWhenStartled extends EntityAIBase {

		private final EntityStoneling stoneling;
		private final PathNavigate navigation;

		private Path path;

		public EntityAIRunAwayWhenStartled(EntityStoneling stoneling) {
			this.stoneling = stoneling;
			navigation = stoneling.getNavigator();
		}

		@Override
		public void startExecuting() {
			navigation.setPath(path, 0.5);
		}

		@Override
		public boolean shouldExecute() {
			if(!stoneling.startled || !stoneling.onGround)
				return false;

			double avoidRange = 5;
			List<EntityPlayer> list = stoneling.world.getEntitiesWithinAABB(EntityPlayer.class, stoneling.getEntityBoundingBox().grow(avoidRange), e -> true);
			if(list.isEmpty())
				return false;

			EntityPlayer closest = list.get(0);
			Vec3d playerPos = closest.getPositionVector();
			Vec3d vec3d = null;
			int vecTries = 0;
			int pathTries = 0;
			
			do {
				pathTries = 0;
				do {
					vec3d = RandomPositionGenerator.findRandomTargetBlockAwayFrom(stoneling, 100, 7, new Vec3d(closest.posX, closest.posY, closest.posZ));
					vecTries++;
				} while((vec3d == null || vec3d.distanceTo(playerPos) < 30) && vecTries < 50);
				
				if(vec3d != null) {
					path = navigation.getPathToXYZ(vec3d.x, vec3d.y, vec3d.z);
					pathTries++;
				}
			} while(path == null && pathTries < 5);
            
            return path != null;
		}
		
		@Override
		public boolean isInterruptible() {
			return false;
		}

		@Override
		public boolean shouldContinueExecuting() {
			return true;
		}

	}

}