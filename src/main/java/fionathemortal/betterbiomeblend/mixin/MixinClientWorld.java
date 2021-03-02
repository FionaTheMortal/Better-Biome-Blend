package fionathemortal.betterbiomeblend.mixin;

import java.util.function.Supplier;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import fionathemortal.betterbiomeblend.BetterBiomeBlendClient;
import fionathemortal.betterbiomeblend.BiomeColorType;
import fionathemortal.betterbiomeblend.ColorChunk;
import fionathemortal.betterbiomeblend.ColorChunkCache;
import net.minecraft.client.color.world.BiomeColors;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.registry.RegistryKey;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.profiler.Profiler;
import net.minecraft.world.dimension.DimensionType;
import net.minecraft.world.MutableWorldProperties;
import net.minecraft.world.World;
import net.minecraft.world.level.ColorResolver;

@Mixin(ClientWorld.class)
public abstract class MixinClientWorld extends World
{
	protected MixinClientWorld(
		MutableWorldProperties worldInfo, 
		RegistryKey<World> dimension, 
		DimensionType dimensionType,
		Supplier<Profiler> profiler, 
		boolean isRemote, 
		boolean isDebug, 
		long seed) 
	{
		super(worldInfo, dimension, dimensionType, profiler, isRemote, isDebug, seed);
	}

	private final ColorChunkCache waterColorCache   = new ColorChunkCache(1024);
	private final ColorChunkCache grassColorCache   = new ColorChunkCache(1024);
	private final ColorChunkCache foliageColorCache = new ColorChunkCache(1024);
	
	private final ColorChunkCache rawWaterColorCache   = new ColorChunkCache(256);
	private final ColorChunkCache rawGrassColorCache   = new ColorChunkCache(256);
	private final ColorChunkCache rawFoliageColorCache = new ColorChunkCache(256);
	
	private final ThreadLocal<ColorChunk> threadLocalWaterChunk   = ThreadLocal.withInitial(() -> { ColorChunk chunk = new ColorChunk(); chunk.acquire(); return chunk; });
	private final ThreadLocal<ColorChunk> threadLocalGrassChunk   = ThreadLocal.withInitial(() -> { ColorChunk chunk = new ColorChunk(); chunk.acquire(); return chunk; });
	private final ThreadLocal<ColorChunk> threadLocalFoliageChunk = ThreadLocal.withInitial(() -> { ColorChunk chunk = new ColorChunk(); chunk.acquire(); return chunk; });
	
	@Inject(method = "reloadColor", at = @At("HEAD"))
   	public void
   	onReloadColor(CallbackInfo ci)
   	{
		waterColorCache.invalidateAll();
		grassColorCache.invalidateAll();
		foliageColorCache.invalidateAll();
		
		rawWaterColorCache.invalidateAll();
		rawGrassColorCache.invalidateAll();
		rawFoliageColorCache.invalidateAll();
   	}
	
	@Inject(method = "resetChunkColor", at = @At("HEAD"))
	public void 
	onResetChunkColor(int chunkX, int chunkZ, CallbackInfo ci)
	{
		waterColorCache.invalidateNeighbourhood(chunkX, chunkZ);
		grassColorCache.invalidateNeighbourhood(chunkX, chunkZ);
		foliageColorCache.invalidateNeighbourhood(chunkX, chunkZ);
		
		rawWaterColorCache.invalidateChunk(chunkX, chunkZ);
		rawGrassColorCache.invalidateChunk(chunkX, chunkZ);
		rawFoliageColorCache.invalidateChunk(chunkX, chunkZ);
	}
	
	@Overwrite
	public int 
	getColor(BlockPos blockPosIn, ColorResolver colorResolverIn)
	{
		int result;
		
		ThreadLocal<ColorChunk> threadLocalChunk;

		if (colorResolverIn == BiomeColors.GRASS_COLOR)
		{
			threadLocalChunk = threadLocalGrassChunk;
		}
		else if (colorResolverIn == BiomeColors.WATER_COLOR)
		{
			threadLocalChunk = threadLocalWaterChunk;
		}
		else
		{
			assert(colorResolverIn == BiomeColors.FOLIAGE_COLOR);
			
			threadLocalChunk = threadLocalFoliageChunk;
		}
	
		int x = blockPosIn.getX();
		int z = blockPosIn.getZ();
		
		int chunkX = x >> 4;
		int chunkZ = z >> 4;
		
		ColorChunk chunk = BetterBiomeBlendClient.getThreadLocalChunk(threadLocalChunk, chunkX, chunkZ);

		if (chunk != null)
		{
			result = chunk.getColor(x, z);
		}
		else
		{
			BiomeColorType  colorType;
			
			ColorChunkCache colorCache;
			ColorChunkCache rawColorCache;
			
			if (colorResolverIn == BiomeColors.GRASS_COLOR)
			{
				colorType     = BiomeColorType.GRASS;
				
				colorCache    = grassColorCache;
				rawColorCache = rawGrassColorCache;
			}
			else if (colorResolverIn == BiomeColors.WATER_COLOR)
			{
				colorType     = BiomeColorType.WATER;
				
				colorCache    = waterColorCache;
				rawColorCache = rawWaterColorCache;
			}
			else
			{
				assert(colorResolverIn == BiomeColors.FOLIAGE_COLOR);
				
				colorType     = BiomeColorType.FOLIAGE;
				
				colorCache    = foliageColorCache;
				rawColorCache = rawFoliageColorCache;
			}
			
			chunk = BetterBiomeBlendClient.getBlendedColorChunk(this, chunkX, chunkZ, colorCache, colorType, rawColorCache);
			
			if (chunk != null)
			{
				BetterBiomeBlendClient.setThreadLocalChunk(threadLocalChunk, chunk, colorCache);
				
				result = chunk.getColor(x, z);
			}
			else
			{
				result = 0;
			}
		}
		
		return result;
	}
}
