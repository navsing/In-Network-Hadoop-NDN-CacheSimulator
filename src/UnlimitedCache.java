import java.util.ArrayList;

public class UnlimitedCache {
  private ArrayList<Long> blocks;

  private long totalAccesses;
  private long totalHits;
  private long totalSize;
  private long totalHitsSize;

  public UnlimitedCache(int cacheSize) {
    this.blocks = new ArrayList<Long>();
  }

  public boolean accessCache(long segmentId) {
    totalAccesses++;
    totalSize += CacheSim.CACHE_BLOCK_SIZE;

    if (blocks.contains(segmentId)) {
      totalHits++;
      totalHitsSize += CacheSim.CACHE_BLOCK_SIZE;
      return true;
    }
    else {
      blocks.add(segmentId);
      return false;
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
