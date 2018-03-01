
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

/**
 * The MultiQueue algorithm. This algorithm organizes entries into queues that
 * represent a frequency range. When an entry is accessed, it may be promoted to
 * the next higher queue and, regardless, is reordered to the
 * least-recently-used position in the queue it resides in. A non-resident queue
 * retains evicted items that are being monitored (OUT) to allow entries to
 * retain their historic frequency and be eagerly promoted.
 * <p>
 * This policy is designed for second-level caches where a hit in this cache was
 * a miss at the first level. Thus the first-level cache captures most of the
 * recency information and the second-level cache access is dominated by usage
 * frequency.
 * <p>
 * This implementation is based on the pseudo code provided by the authors in
 * their paper <a href=
 * "https://www.usenix.org/legacy/event/usenix01/full_papers/zhou/zhou.pdf">The
 * Multi-Queue Replacement Algorithm for Second Level. Buffer Caches</a>.
 *
 * @author ben.manes@gmail.com (Ben Manes)
 */
public final class MQueue {
	private final SortedMap<String, Node> out;
	private final HashMap<String, Node> data;
	private final long[] threshold;
	private final int maximumSize;
	private final long lifetime;
	private final Node[] headQ;
	private final int maxOut;

	private final int CACHEBLOCKSIZE = 1048576;


	private long totalAccesses;
	private long totalHits;
	private long totalSize;
	private long totalHitsSize;

	private long currentTime;

	public MQueue(int cacheSize) {
		threshold = new long[10];
		headQ = new Node[10];
		out = new TreeMap<String, Node>();
		data = new HashMap<String, Node>();
		maximumSize = cacheSize;
		lifetime = 16000;

		Arrays.setAll(headQ, Node::sentinel);
		Arrays.setAll(threshold, i -> 1L << i);
		maxOut = maximumSize;
	}

	public boolean accessCache(Block block) {
		/*for (int i = 0; i < Math.ceil((double) block.size / (double) CacheSim.CACHE_BLOCK_SIZE); i++) {
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
		}*/

		boolean wasHitForAll = true;
		int nCacheBlocks = (int)Math.ceil((double)block.size / (double)CACHEBLOCKSIZE);
		for (int i = 0; i < nCacheBlocks; i++) {
			String cacheBlockId = block.blockId + "_" + i;
			if (!customInsert(cacheBlockId, CACHEBLOCKSIZE, block)) {
				wasHitForAll = false;
			}
		}

		if (wasHitForAll) {
			totalHits += nCacheBlocks;
			totalHitsSize += nCacheBlocks * CACHEBLOCKSIZE;
		}

		return wasHitForAll;
	}

	public boolean customInsert(String key, int internalSize, Block block) {
		Node node = data.get(key);
		boolean wasHit = false;
		if (node == null) {
			node = out.remove(key);
			if (node == null) {
				node = new Node(key);
			}
			data.put(key, node);
			/*if (data.size() > maximumSize) {
				evict();
			}*/
		} else {
			//if (block.blockOperation == CacheSim.OPERATION_READ) {
				wasHit = true;
			//}
			node.remove();
		}
		totalAccesses++;
		totalSize += internalSize;
		node.reference++;
		node.queueIndex = queueIndexFor(node);
		node.appendToTail(headQ[node.queueIndex]);
		node.expireTime = currentTime + lifetime;
		adjust();
		return wasHit;
	}

	private void adjust() {
		currentTime++;
		for (int i = 1; i < headQ.length; i++) {
			Node node = headQ[i].next;
			if (node.next.expireTime < currentTime) {
				node.remove();
				node.queueIndex = (i - 1);
				node.appendToTail(headQ[node.queueIndex]);
				node.expireTime = currentTime + lifetime;
			}
		}
	}

	private int queueIndexFor(Node node) {
		for (int i = threshold.length - 1; i >= 0; i--) {
			if (node.reference >= threshold[i]) {
				return i;
			}
		}
		throw new IllegalStateException();
	}

	private void evict() {
		Node victim = null;
		for (Node head : headQ) {
			if (head.next != head) {
				victim = head.next;
				break;
			}
		}
		if (victim == null) {
			return;
		}

		victim.remove();
		data.remove(victim.key);
		out.put(victim.key, victim);
		/*if (out.size() > maxOut) {
			out.remove(out.firstKey());
		}*/
	}

	static final class Node {
		final String key;

		Node prev;
		Node next;
		int reference;
		int queueIndex;
		long expireTime;

		Node(String key) {
			this.key = key;
		}

		static Node sentinel(int queueIndex) {
			Node node = new Node("SENTINEL");
			node.expireTime = Long.MAX_VALUE;
			node.queueIndex = queueIndex;
			node.prev = node;
			node.next = node;
			return node;
		}

		/** Appends the node to the tail of the list. */
		public void appendToTail(Node head) {
			Node tail = head.prev;
			head.prev = this;
			tail.next = this;
			next = head;
			prev = tail;
		}

		/** Moves the node to the tail. */
		public void moveToTail(Node head) {
			// unlink
			prev.next = next;
			next.prev = prev;

			// link
			next = head;
			prev = head.prev;
			head.prev = this;
			prev.next = this;
		}

		/** Removes the node from the list. */
		public void remove() {
			if (key.equals("SENTINEL")) {
				queueIndex = -1;
				prev.next = next;
				next.prev = prev;
				prev = next = null;
			}
		}
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
