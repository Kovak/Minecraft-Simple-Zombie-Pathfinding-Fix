package net.minecraft.src;

public class EntityAIAttackOnCollide extends EntityAIBase
{
    World worldObj;
    EntityCreature attacker;

    /**
     * An amount of decrementing ticks that allows the entity to attack once the tick reaches 0.
     */
    int attackTick;
    double field_75440_e;
    boolean field_75437_f;

    /** The PathEntity of our entity. */
    PathEntity entityPathEntity;
    Class classTarget;
    private int field_75445_i;
    private double pathedTargetX;
    private double pathedTargetY;
    private double pathedTargetZ;
    private boolean previousPathingOk = false; // Not really needed here, but an idea for future...
    private int fullRangeSearchDelay;

    public EntityAIAttackOnCollide(EntityCreature par1EntityCreature, Class par2Class, double par3, boolean par5)
    {
        this(par1EntityCreature, par3, par5);
        this.classTarget = par2Class;
    }

    public EntityAIAttackOnCollide(EntityCreature par1EntityCreature, double par2, boolean par4)
    {
        this.attacker = par1EntityCreature;
        this.worldObj = par1EntityCreature.worldObj;
        this.field_75440_e = par2;
        this.field_75437_f = par4;
        this.setMutexBits(3);
    }

    /**
     * Returns whether the EntityAIBase should begin execution.
     */
    public boolean shouldExecute()
    {
        EntityLivingBase var1 = this.attacker.getAttackTarget();

        if (var1 == null)
        {
            return false;
        }
        else if (!var1.isEntityAlive())
        {
            return false;
        }
        else if (this.classTarget != null && !this.classTarget.isAssignableFrom(var1.getClass()))
        {
            return false;
        }
        else
        {
            this.entityPathEntity = this.attacker.getNavigator().getPathToEntityLiving(var1);
            return this.entityPathEntity != null;
        }
    }

    /**
     * Returns whether an in-progress EntityAIBase should continue executing
     */
    public boolean continueExecuting()
    {
        EntityLivingBase var1 = this.attacker.getAttackTarget();
        return var1 == null ? false : (!var1.isEntityAlive() ? false : (!this.field_75437_f ? !this.attacker.getNavigator().noPath() : this.attacker.func_110176_b(MathHelper.floor_double(var1.posX), MathHelper.floor_double(var1.posY), MathHelper.floor_double(var1.posZ))));
    }

    /**
     * Execute a one shot task or start executing a continuous task
     */
    public void startExecuting()
    {
        this.attacker.getNavigator().setPath(this.entityPathEntity, this.field_75440_e);
        this.field_75445_i = 0;
    }

    /**
     * Resets the task
     */
    public void resetTask()
    {
        this.attacker.getNavigator().clearPathEntity();
    }

    /**
     * Updates the task
     */
    public void updateTask()
    {
        EntityLivingBase var1 = this.attacker.getAttackTarget();
        this.attacker.getLookHelper().setLookPositionWithEntity(var1, 30.0F, 30.0F);
                double targetDistanceSq = this.attacker.getDistanceSq(var1.posX, var1.boundingBox.minY, var1.posZ);
        if (this.field_75445_i > 0)
            --this.field_75445_i;
        
        if (this.field_75437_f || this.attacker.getEntitySenses().canSee(var1)) {
            boolean repath = false;
            double targetMovement = var1.getDistanceSq(pathedTargetX, pathedTargetY, pathedTargetZ);
            
            // Only do a new path search if enough time has passed from the previous search, and the target has moved:
            if (field_75445_i <= 0) {
                if (targetMovement >= 1.0D) {
                    repath = true;
                }
            }

            // Ensure an occasional pathing if it would not be happening otherwise (e.g. world changed
            // or any other unforeseen/future issue):
            if (!repath && field_75445_i <= 0 && this.attacker.getRNG().nextInt(200) == 0) {
                repath = true;
            }
            if (repath) {
                // If not allowed to do full range search (to reduce load), adjust range now...
                AttributeInstance rangeAttr = null;
                double originalRange = 16.0D; // Just a common default, will be corrected when needed.
                if (fullRangeSearchDelay > 0) {
                    // Adjust range to bit more than current distance to target, or 16, whichever is greater:
                    rangeAttr = this.attacker.func_110148_a(SharedMonsterAttributes.field_111265_b); // field_111265_b
                    originalRange = rangeAttr.func_111126_e();
                    double dist = Math.sqrt(targetDistanceSq);
                    if (dist <= 8.0D)
                        dist = 8.0D;
                    if (dist > originalRange)
                        dist = originalRange;
                    rangeAttr.func_111128_a(dist);
                } else {
                    rangeAttr = this.attacker.func_110148_a(SharedMonsterAttributes.field_111265_b); // field_111265_b
                    originalRange = rangeAttr.func_111126_e();
                }
                // Do the deed:
                previousPathingOk = this.attacker.getNavigator().tryMoveToEntityLiving(var1, this.field_75440_e);

                // If the range was adjusted, restore it now (and decrease the counter):
                if (fullRangeSearchDelay > 0) {
                    fullRangeSearchDelay--;
                    if (originalRange > 40.0D)
                        originalRange = 40.0D;
                    rangeAttr.func_111128_a(originalRange);
                }
                
                pathedTargetX = var1.posX;
                pathedTargetY = var1.boundingBox.minY;
                pathedTargetZ = var1.posZ;
                this.field_75445_i = 4 + this.attacker.getRNG().nextInt(7);
                // A longer delay for longer distances, or if the pathing failed
                if (targetDistanceSq > 256.0D) { // > 16 blocks
                    if (targetDistanceSq > 1024.0D) { // > 32 blocks
                        this.field_75445_i += 8;
                    } else {
                        this.field_75445_i += 16;
                    }
                } else if (!previousPathingOk) {
                    this.field_75445_i += 24;
                }
                
                // If the path search failed with full range or if we're close already,
                // use limited search range for a while:
                if (!previousPathingOk || targetDistanceSq <= 256.0D) {
                    if (fullRangeSearchDelay <= 0) {
                        fullRangeSearchDelay = 4 + this.attacker.getRNG().nextInt(4);
                    }
                }
            }
        }
        
        this.attackTick = Math.max(this.attackTick - 1, 0);
        double var2 = (double) (this.attacker.width * 2.0F * this.attacker.width * 2.0F + var1.width);

//        if (this.attacker.getDistanceSq(var1.posX, var1.boundingBox.minY, var1.posZ) <= var2) {
        if (targetDistanceSq <= var2) {
        {
            if (this.attackTick <= 0)
            {
                this.attackTick = 20;

                if (this.attacker.getHeldItem() != null)
                {
                    this.attacker.swingItem();
                }

                this.attacker.attackEntityAsMob(var1);
            }
        }
    }
    }
}



