public class NoCache {
  private long totalAccesses;
  private long totalSize;

  public NoCache(int cacheSize) {
  }

  public boolean accessCache(long segmentId) {
    totalAccesses++;
    totalSize += CacheSim.CACHE_BLOCK_SIZE;
    return false;
  }

  public void report() {
    if (totalAccesses == 0) {
      System.out.println("0,0,0,0,0,0");
    }
    else {
      System.out.println(totalAccesses + ",0,0," + totalSize + ",0,0");
    }
  }
}
