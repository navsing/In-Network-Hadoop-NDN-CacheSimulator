import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

class MyValue {
	int value, freq;

	public MyValue(int value, int freq) {
		this.value = value;
		this.freq = freq;
	}
}

public class LRFUCheckCache {
	private Map<Long, MyValue> lfuCache;
	private List<LinkedList<Long>> freqList;
	int cacheSize;

	public LRFUCheckCache(int cacheSize) {
		this.cacheSize = cacheSize;
		this.lfuCache = new HashMap<>();
		this.freqList = new ArrayList<>();
	}

	private long totalAccesses;
	private long totalHits;
	private long totalSize;
	private long totalHitsSize;

	public void accessCache(Block block) {
		for (int i = 0; i < Math.ceil((double) block.size / (double) CacheSim.CACHE_BLOCK_SIZE); i++) {
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

	private void customInsert(long id, int internalSize, Block block) {
		MyValue myValue = lfuCache.get(id);
		// value doesn't exist
		if (null == myValue) {
			myValue = new MyValue(1, 1);
			// time to remove least frequently used value
			if (lfuCache.size() == cacheSize) {
				if (!freqList.isEmpty() && !freqList.get(0).isEmpty()) {
					long myEvictedKey = freqList.get(0).removeFirst();
					lfuCache.remove(myEvictedKey);
				}
			}
			lfuCache.put(id, myValue);
			if (freqList.size() < 1 || null == freqList.get(0)) {
				freqList.add(0, new LinkedList<>());
			}
			freqList.get(0).add(id);

		} else {
			// value already exist, so just update the frequency
			freqList.get(myValue.freq - 1).remove(new Long(id));
			if (block.blockOperation == CacheSim.OPERATION_READ) {
				totalHits++;
				totalHitsSize += internalSize;
			}
			if (myValue.freq < cacheSize)
				++myValue.freq;
			if (freqList.size() <= myValue.freq - 1 || null == freqList.get(myValue.freq - 1)) {
				freqList.add(myValue.freq - 1, new LinkedList<>());
			}
			freqList.get(myValue.freq - 1).add(id);
			lfuCache.put(id, myValue);
		}
		totalAccesses++;
		totalSize += internalSize;
	}

	public void report() {
		if(totalAccesses == 0){
                        System.out.println("No Activity");
                        return;
                }

                System.out.println(totalAccesses+","+totalHits+","+((double)totalHits)/((double)totalAccesses)+","+totalSize+","+totalHitsSize+","+((double)totalHitsSize)/((double)totalSize));
	}
}
