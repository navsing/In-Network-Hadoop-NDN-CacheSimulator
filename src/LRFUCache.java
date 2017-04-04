import java.util.LinkedHashMap;
import java.util.Map;

public class LRFUCache {

	class CacheEntry {
		private int frequency;

		// default constructor
		private CacheEntry() {
		}

		public int getFrequency() {
			return frequency;
		}

		public void setFrequency(int frequency) {
			this.frequency = frequency;
		}

	}

	private static int initialCapacity = 10;
	private long totalAccesses;
	private long totalHits;
	private long totalSize;
	private long totalHitsSize;

	public void accessCache(Block block) {
		for (int i = 0; i < Math.ceil((double)block.size / (double)CacheSim.CACHE_BLOCK_SIZE); i++) {
			long internalId = ((int) block.blockId) + ((int) i) * 100000000000L;
			int internalSize = CacheSim.CACHE_BLOCK_SIZE;
			if ((i + 1) * CacheSim.CACHE_BLOCK_SIZE > block.size) {
				internalSize = block.size - (i * CacheSim.CACHE_BLOCK_SIZE);
			}
			switch (block.blockOperation) {
			case CacheSim.OPERATION_READ:
			case CacheSim.OPERATION_WRITE:
				customInsert(internalId, internalSize, block);
				break;
			}
		}
	}
	
	private static LinkedHashMap<Long, CacheEntry> cacheMap = new LinkedHashMap<Long, CacheEntry>();
	/*
	 * LinkedHashMap is used because it has features of both HashMap and
	 * LinkedList. Thus, we can get an entry in O(1) and also, we can iterate
	 * over it easily.
	 */

	public LRFUCache(int initialCapacity) {
		this.initialCapacity = initialCapacity;
	}

	public void customInsert(long key, int internalSize, Block block) {
		totalAccesses++;
		totalSize += internalSize;
		if (!isFull()) {
			if(cacheMap.containsKey(key)){
				if (block.blockOperation == CacheSim.OPERATION_READ) {
					totalHits++;
					totalHitsSize += internalSize;
				}
			}
			CacheEntry temp = new CacheEntry();
			temp.setFrequency(0);
			cacheMap.put(key, temp);
		} else {
			long entryKeyToBeRemoved = getLFUKey();
			cacheMap.remove(entryKeyToBeRemoved);
			CacheEntry temp = new CacheEntry();
			temp.setFrequency(0);
			if(cacheMap.containsKey(key)){
				if (block.blockOperation == CacheSim.OPERATION_READ) {
					totalHits++;
					totalHitsSize += internalSize;
				}
			}
			cacheMap.put(key, temp);
		}
	}

	public long getLFUKey() {
		long key = 0;
		int minFreq = Integer.MAX_VALUE;

		for (Map.Entry<Long, CacheEntry> entry : cacheMap.entrySet()) {
			if (minFreq > entry.getValue().frequency) {
				key = entry.getKey();
				minFreq = entry.getValue().frequency;
			}
		}

		return key;
	}

	public static boolean isFull() {
		if (cacheMap.size() == initialCapacity)
			return true;

		return false;
	}
	
	public void report(){
		System.out.println("LRFU:");
		
		if(totalAccesses == 0){
			System.out.println("No Activity");
			return;
		}
		
		System.out.println("Total Accesses: "+totalAccesses);
		System.out.println("Total Hits: "+ totalHits);
		System.out.println("Hit Ratio: "+((double)totalHits)/((double)totalAccesses));
		
		System.out.println("Total Size: "+ totalSize);
		System.out.println("Total Hit Size: "+ totalHitsSize);
		System.out.println("Size Hit Ratio: "+((double)totalHitsSize)/((double)totalSize));
		System.out.println();
		
	}
}