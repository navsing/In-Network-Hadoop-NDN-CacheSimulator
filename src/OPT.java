
/*
 * Copyright 2015 Ben Manes. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import java.util.*;

import it.unimi.dsi.fastutil.ints.IntArrayFIFOQueue;
import it.unimi.dsi.fastutil.ints.IntPriorityQueue;
import it.unimi.dsi.fastutil.ints.IntRBTreeSet;
import it.unimi.dsi.fastutil.ints.IntSortedSet;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayFIFOQueue;

/**
 * <pre>
 * Bélády's
 * </pre>
 * 
 * optimal page replacement policy. The upper bound of the hit rate is estimated
 * by evicting from the cache the item that will next be used farthest into the
 * future.
 *
 * @author ben.manes@gmail.com (Ben Manes)
 */
public final class OPT {
	private final Long2ObjectMap<IntPriorityQueue> accessTimes;
	private final LongArrayFIFOQueue future;
	private final IntSortedSet data;
	private final int maximumSize;

	private int infiniteTimestamp;
	private int tick;

	private long totalAccesses;
	private long totalHits;
	private long totalSize;
	private long totalHitsSize;

	public OPT(int cacheSize) {
		accessTimes = new Long2ObjectOpenHashMap<>();
		infiniteTimestamp = Integer.MAX_VALUE;
		maximumSize = cacheSize;
		future = new LongArrayFIFOQueue();
		data = new IntRBTreeSet();
	}

	public void accessCache(Block block) {
		for (int i = 0; i < Math.ceil((double) block.size / (double) CacheSim.CACHE_BLOCK_SIZE); i++) {
			long internalId = ((int) block.blockId) + ((int) i) * 100000000000L;
			switch (block.blockOperation) {
			case CacheSim.OPERATION_READ:
			case CacheSim.OPERATION_WRITE:
				record(internalId);
				break;
			}
		}
	}
	
	public void accessCache1(Block block) {
		for (int i = 0; i < Math.ceil((double) block.size / (double) CacheSim.CACHE_BLOCK_SIZE); i++) {
			int internalSize = CacheSim.CACHE_BLOCK_SIZE;
			if ((i + 1) * CacheSim.CACHE_BLOCK_SIZE > block.size) {
				internalSize = block.size - (i * CacheSim.CACHE_BLOCK_SIZE);
			}
			switch (block.blockOperation) {
			case CacheSim.OPERATION_READ:
			case CacheSim.OPERATION_WRITE:
				customInsert(internalSize, block);
				break;
			}
		}
	}
	
	public void record(long key) {
	    tick++;
	    future.enqueue(key);
	    IntPriorityQueue times = accessTimes.get(key);
	    if (times == null) {
	      times = new IntArrayFIFOQueue();
	      accessTimes.put(key, times);
	    }
	    times.enqueue(tick);
	  }
	
	
	public void customInsert(int internalSize, Block block) {
		totalAccesses++;
		totalSize += internalSize;
		while (!future.isEmpty()) {
			process(future.dequeueLong(), internalSize, block);
			//System.out.println("future is not empty");
		}
	}

	private void process(long key, int internalSize, Block block) {
		IntPriorityQueue times = accessTimes.get(key);
		
		int lastAccess = times.dequeueInt();
		
		if(times.isEmpty()){
			data.add(lastAccess);
			accessTimes.remove(key);
		}
		
		boolean found = data.remove(lastAccess);
		
		
		
		if (found) {
			if (block.blockOperation == CacheSim.OPERATION_READ) {
				totalHits++;
				totalHitsSize += internalSize;
			}
		} else {
			if (data.size() > maximumSize) {
				evict();
			}
		}
	}

	/** Removes the entry whose next access is farthest away into the future. */
	private void evict() {
		data.rem(data.lastInt());
	}

	public void report() {
		System.out.println("OPT: ");

		if (totalAccesses == 0) {
			System.out.println("No Activity");
			return;
		}

		System.out.println("Total Accesses: " + totalAccesses);
		System.out.println("Total Hits: " + totalHits);
		System.out.println("Hit Ratio: " + ((double) totalHits) / ((double) totalAccesses));

		System.out.println("Total Size: " + totalSize);
		System.out.println("Total Hit Size: " + totalHitsSize);
		System.out.println("Size Hit Ratio: " + ((double) totalHitsSize) / ((double) totalSize));
		System.out.println();

	}
}
