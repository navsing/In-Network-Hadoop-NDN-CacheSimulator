import java.util.ArrayDeque;
import java.util.HashSet;

public class TwoQueueCache {
  private int cacheCapacity;
  private int kin;
  private int kout;
  private int cacheSize;
  private ArrayDeque<Long> am;
  private HashSet<Long> amSet;
  private ArrayDeque<Long> a1in;
  private HashSet<Long> a1inSet;
  private ArrayDeque<Long> a1out;
  private HashSet<Long> a1outSet;

  private long totalAccesses;
  private long totalHits;
  private long totalSize;
  private long totalHitsSize;

  public TwoQueueCache(int cacheCapacity) {
    this.cacheCapacity = cacheCapacity;
    this.kin = (int)(0.25 * cacheCapacity);
    this.kout = (int)(0.5 * cacheCapacity);
    this.am = new ArrayDeque<Long>();
    this.amSet = new HashSet<Long>();
    this.a1in = new ArrayDeque<Long>();
    this.a1inSet = new HashSet<Long>();
    this.a1out = new ArrayDeque<Long>();
    this.a1outSet = new HashSet<Long>();
  }

  public boolean accessCache(long segmentId) {
    totalAccesses++;
    totalSize += CacheSim.CACHE_BLOCK_SIZE;

    boolean wasHit = false;
    if (this.amSet.contains(segmentId)) {
      this.am.remove(segmentId);
      this.am.addLast(segmentId);
      totalHits++;
      totalHitsSize += CacheSim.CACHE_BLOCK_SIZE;
      wasHit = true;
    }
    else if (this.a1outSet.contains(segmentId)) {
      reclaimFor(segmentId);
      this.am.addLast(segmentId);
      this.amSet.add(segmentId);
    }
    else if (this.a1inSet.contains(segmentId)) {
      // Do nothing
      totalHits++;
      totalHitsSize += CacheSim.CACHE_BLOCK_SIZE;
      wasHit = true;
    }
    else {
      reclaimFor(segmentId);
      this.a1in.addLast(segmentId);
      this.a1inSet.add(segmentId);
    }

    return wasHit;
  }

  public void reclaimFor(long segmentId) {
    if (cacheSize < cacheCapacity) {
      cacheSize++;
    }
    else if (this.a1inSet.size() > this.kin) {
      long removedY = this.a1in.removeFirst();
      this.a1inSet.remove(removedY);

      this.a1out.addLast(removedY);
      this.a1outSet.add(removedY);

      if (this.a1outSet.size() > this.kout) {
        long removedZ = this.a1out.removeFirst();
        this.a1outSet.remove(removedZ);
      }
    }
    else {
      long removedY = this.am.removeFirst();
      this.amSet.remove(removedY);
    }
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
