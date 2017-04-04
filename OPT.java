/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

/**
 *
 * optimal page replacement policy. The upper bound of the hit rate is estimated
 * by evicting from the cache the item that will next be used farthest into the
 * future.
 * 
 * @author Ashwin S
 */

import java.beans.Customizer;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

public class OPT extends LinkedHashMap {

    private static final long serialVersionUID = 1L;
    private int cacheSize;
    private long totalAccesses;
    private long totalHits;
    private long totalSize;
    private long totalHitsSize;
    boolean value = false;

    public OPT(int cacheSize) {
        super(cacheSize, (float) 0.75, true);
        this.cacheSize = cacheSize;
    }

//    @Override
//    protected boolean removeEldestEntry(Map.Entry eldest) {
//        int countOfSize = 0;
//    Iterator it = entrySet().iterator();
//    while (it.hasNext()) {
//        Map.Entry pair = (Map.Entry) it.next();
//        countOfSize += (int) pair.getValue();
//    }
//        return countOfSize >= cacheSize;
//    }
    
     protected boolean check(long id, Block[] b) {
        int countOfSize = 0;
    Iterator it = entrySet().iterator();
    HashMap<Long,Integer> hmap = new HashMap<>();
    while (it.hasNext()) {
        Map.Entry pair = (Map.Entry) it.next();
        countOfSize += (int) pair.getValue();
    }
        if(countOfSize >= cacheSize)
        {
            Iterator it1 = entrySet().iterator();
            while (it1.hasNext()) {
                   boolean val = false;
        Map.Entry pair = (Map.Entry) it1.next();
        int count = 0;
        for(int i=0; i<b.length; i++)
        {
            count += 1;
            long n =  (long) pair.getKey();
            int n1 = (int) (n - ((int) i) * 100000000000L);
            if(n1 == b[i].blockId)
            {
                //Do comparisons here
                long internalId = ((int) n1) + ((int) i) * 100000000000L;
                if(hmap.containsKey(internalId)){
                    val = true;
                    break;
                }
                else{
                val = true;
                hmap.put(internalId, count);
                break;
                }
                
            }
        }
        if(val == false)
        {
            long n =  (long) pair.getKey();
            remove(n);
            return true;
        }
        
    }
         Iterator it2 = hmap.entrySet().iterator();
         int rep;
         int max = 0;
         long id1 = 0;
            while (it2.hasNext()) {
        Map.Entry pair = (Map.Entry) it2.next();
        rep = (int) pair.getValue();
        if(rep>max)
        {
            id1 = (long) pair.getKey();
        }
        
    }   
            remove(id1);
            return true;
        }
        return false;
    }

    public void accessCache(Block block, Block[] b) {
        for (int i = 0; i < Math.ceil((double)block.size / (double)CacheSim.CACHE_BLOCK_SIZE); i++) {
			long internalId = ((int) block.blockId) + ((int) i) * 100000000000L;
			int internalSize = CacheSim.CACHE_BLOCK_SIZE;
			if ((i + 1) * CacheSim.CACHE_BLOCK_SIZE > block.size) {
				internalSize = block.size - (i * CacheSim.CACHE_BLOCK_SIZE);
			}
			switch (block.blockOperation) {
			case CacheSim.OPERATION_READ:
			case CacheSim.OPERATION_WRITE:
				customInsert(internalId, internalSize, block, b);
				break;
			case CacheSim.OPERATION_REMOVE:
				remove(internalId);
				break;
			}
		}
    }

    private void customInsert(long id, int internalSize, Block block, Block[] b) {
        if (check(id,b)!= false) {
			if (block.blockOperation == CacheSim.OPERATION_READ) {
				totalHits++;
				totalHitsSize += internalSize;
			}
		}		
		put(id, 1);
		totalAccesses++;
		totalSize += internalSize;
    }

    public void report() {
        if (totalAccesses == 0) {
            System.out.println("No Activity");
            return;
        }

        System.out.println(totalAccesses + "," + totalHits + "," + ((double) totalHits) / ((double) totalAccesses) + "," + totalSize + "," + totalHitsSize + "," + ((double) totalHitsSize) / ((double) totalSize));

    }

}
