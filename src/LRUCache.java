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
		super(1024, (float) 0.75, true);
		this.cacheSize = cacheSize;
	}

	protected boolean removeEldestEntry(Map.Entry eldest) {
		int countOfSize = 0;
		Iterator it = entrySet().iterator();
		while (it.hasNext()) {
			Map.Entry pair = (Map.Entry) it.next();
               		countOfSize += (int) pair.getValue();
		}
		return countOfSize >= cacheSize;
	}	
	public void accessCache(Block block) {
			/*for (int i = 0; i < Math.ceil((double)block.size / (double)CacheSim.CACHE_BLOCK_SIZE); i++) {
				long internalId = ((int) block.blockId) + ((int) i) * 100000000000L;
				int internalSize = CacheSim.CACHE_BLOCK_SIZE;
				if ((i + 1) * CacheSim.CACHE_BLOCK_SIZE > block.size) {
					internalSize = block.size - (i * CacheSim.CACHE_BLOCK_SIZE);
				}
				customInsert(internalId, internalSize, block);
					
			}*/
			customInsert(block.blockId, block.size, block);
	}

	private void customInsert(long id, int internalSize, Block block) {
		if (remove(id)!= null) {
			if (block.blockOperation == CacheSim.OPERATION_READ) {
				totalHits++;
				totalHitsSize += internalSize;
			}
		}
		
		put(id, internalSize);
		totalAccesses++;
		totalSize += internalSize;

		
	}
	
	public void report(){
		if(totalAccesses == 0){
			System.out.println("No Activity");
			return;
		}
		
		System.out.println(totalAccesses+","+totalHits+","+((double)totalHits)/((double)totalAccesses)+","+totalSize+","+totalHitsSize+","+((double)totalHitsSize)/((double)totalSize));
		
	}
	
}
