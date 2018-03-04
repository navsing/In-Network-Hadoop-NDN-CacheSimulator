import java.beans.Customizer;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.*;

public class LRUCache extends LinkedHashMap {

	private static final long serialVersionUID = 1L;
	private int cacheSize;
	private long totalAccesses;
	private long totalHits;
	private long totalSize;
	private long totalHitsSize;
	private long hashmapSize;

	public LRUCache(int cacheSize) {
		super(cacheSize * 1024 * 1024 / CacheSim.CACHE_BLOCK_SIZE, (float) 0.75, true);
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

	public boolean accessCache(long segmentId) {
			/*for (int i = 0; i < Math.ceil((double)block.size / (double)CacheSim.CACHE_BLOCK_SIZE); i++) {
				long internalId = ((int) block.blockId) + ((int) i) * 100000000000L;
				int internalSize = CacheSim.CACHE_BLOCK_SIZE;
				if ((i + 1) * CacheSim.CACHE_BLOCK_SIZE > block.size) {
					internalSize = block.size - (i * CacheSim.CACHE_BLOCK_SIZE);
				}
				customInsert(internalId, internalSize, block);

			}*/
			return customInsert(segmentId, CacheSim.CACHE_BLOCK_SIZE);
	}

	private boolean customInsert(long id, int internalSize) {
		boolean wasHit = false;
		if(remove(id) != null) {
			totalHits++;
			totalHitsSize += internalSize;
			wasHit = true;
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
