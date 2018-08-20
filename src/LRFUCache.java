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

	public boolean accessCache(long segmentId) {
		totalAccesses++;
		totalSize += CacheSim.CACHE_BLOCK_SIZE;
		return customInsert(segmentId);
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

	public boolean customInsert(long key) {
		boolean wasHit = false;
		if (!isFull()) {
			if(cacheMap.containsKey(key)){
				totalHits++;
				totalHitsSize += CacheSim.CACHE_BLOCK_SIZE;
				wasHit = true;
			}
			CacheEntry temp = new CacheEntry();
			temp.setFrequency(0);
			cacheMap.put(key, temp);
		}
		else {
			long entryKeyToBeRemoved = getLFUKey();
			cacheMap.remove(entryKeyToBeRemoved);
			CacheEntry temp = new CacheEntry();
			temp.setFrequency(0);
			if(cacheMap.containsKey(key)){
				totalHits++;
				totalHitsSize += CacheSim.CACHE_BLOCK_SIZE;
				wasHit = true;
			}
			cacheMap.put(key, temp);
		}

		return wasHit;
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
		if (cacheMap.size() == initialCapacity) {
			return true;
		}

		return false;
	}

	public void report(){
		if (totalAccesses == 0){
			System.out.println("0,0,0,0,0,0");
		}
		else {
			System.out.print(totalAccesses + "," + totalHits + ",");
			System.out.printf("%.16f", ((double)totalHits)/((double)totalAccesses));
			System.out.print("," + totalSize + "," + totalHitsSize + ",");
			System.out.printf("%.16f", ((double)totalHitsSize)/((double)totalSize));
			System.out.println();
		}
	}
}
