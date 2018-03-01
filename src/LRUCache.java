import java.beans.Customizer;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.*;

public class LRUCache extends LinkedHashMap {

	private static final long serialVersionUID = 1L;
	private final int CACHEBLOCKSIZE = 1048576;
	private int cacheSize;
	private long totalAccesses;
	private long totalHits;
	private long totalSize;
	private long totalHitsSize;
	private long hashmapSize;

	public LRUCache(int cacheSize) {
		super(cacheSize, (float) 0.75, true);
		this.cacheSize = cacheSize;
	}

	protected boolean removeEldestEntry(Map.Entry eldest) {
		long countOfSize = 0;
		Iterator it = entrySet().iterator();
		while (it.hasNext()) {
			Map.Entry pair = (Map.Entry) it.next();
               		countOfSize += (int) pair.getValue();
		}
		return countOfSize >= (long)cacheSize * 1024 * 1024;
	}

	public boolean accessCache(Block block) {
			/*for (int i = 0; i < Math.ceil((double)block.size / (double)CacheSim.CACHE_BLOCK_SIZE); i++) {
				long internalId = ((int) block.blockId) + ((int) i) * 100000000000L;
				int internalSize = CacheSim.CACHE_BLOCK_SIZE;
				if ((i + 1) * CacheSim.CACHE_BLOCK_SIZE > block.size) {
					internalSize = block.size - (i * CacheSim.CACHE_BLOCK_SIZE);
				}
				customInsert(internalId, internalSize, block);

			}*/
			boolean wasHitForAll = true;
			int nCacheBlocks = (int)Math.ceil((double)block.size / (double)CACHEBLOCKSIZE);
			for (int i = 0; i < nCacheBlocks; i++) {
				String cacheBlockId = block.blockId + "_" + i;
				if (!customInsert(cacheBlockId, CACHEBLOCKSIZE, block)) {
					wasHitForAll = false;
				}
			}

			if (wasHitForAll) {
				totalHits += nCacheBlocks;
				totalHitsSize += nCacheBlocks * CACHEBLOCKSIZE;
			}

			return wasHitForAll;
	}

	private boolean customInsert(String id, int internalSize, Block block) {
		boolean wasHit = false;
		if (remove(id)!= null) {
			if (block.blockOperation == CacheSim.OPERATION_READ) {
				wasHit = true;
			}
		}

		put(id, internalSize);
		totalAccesses++;
		totalSize += internalSize;

		return wasHit;
	}

	public void report(){
		if (totalAccesses == 0){
			System.out.println("No Activity");
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
