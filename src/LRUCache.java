import java.util.ArrayDeque;
import java.util.HashSet;

public class LRUCache {
  private int cacheSize;
  private ArrayDeque<Long> queue;
  private HashSet<Long> contents;

  private long totalAccesses;
  private long totalHits;
  private long totalSize;
  private long totalHitsSize;

  public LRUCache(int cacheSize) {
    this.cacheSize = cacheSize;
    this.queue = new ArrayDeque<Long>();
    this.contents = new HashSet<Long>();
  }

  public boolean accessCache(long segmentId) {
    totalAccesses++;
    totalSize += CacheSim.CACHE_BLOCK_SIZE;

    boolean wasHit = false;
    if (contents.contains(segmentId)) {
      wasHit = true;
      totalHits++;
      totalHitsSize += CacheSim.CACHE_BLOCK_SIZE;
      queue.remove(segmentId);
      contents.remove(segmentId);
    }

    if (queue.size() == cacheSize) {
      // Remove least-recently used cache block
      long removedBlock = queue.removeFirst();
      contents.remove(removedBlock);
    }

    queue.addLast(segmentId);
    contents.add(segmentId);

    return wasHit;
  }

  public void report() {
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
