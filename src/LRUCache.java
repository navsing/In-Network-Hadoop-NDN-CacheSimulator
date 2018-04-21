import java.util.ArrayDeque;

public class LRUCache {
  private int cacheSize;
  private ArrayDeque<Long> queue;

  private long totalAccesses;
  private long totalHits;
  private long totalSize;
  private long totalHitsSize;

  public LRUCache(int cacheSize) {
    this.cacheSize = cacheSize;
    this.queue = new ArrayDeque<Long>();
  }

  public boolean accessCache(long segmentId) {
    totalAccesses++;
    totalSize += CacheSim.CACHE_BLOCK_SIZE;

    boolean wasHit = false;
    if (queue.remove(segmentId)) {
      wasHit = true;
      totalHits++;
      totalHitsSize += CacheSim.CACHE_BLOCK_SIZE;
    }

    if (queue.size() == cacheSize) {
      // Remove first element
      queue.removeFirst();
    }

    queue.addLast(segmentId);

    return wasHit;
  }

  public void report() {
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
