
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
 * A cache that uses a linked list, in either insertion or access order, to
 * implement simple page replacement algorithms.
 *
 * @author ben.manes@gmail.com (Ben Manes)
 */
public final class LRU2 {
	private final HashMap<Long, Node> data;
	private final int maximumSize;
	private final Node sentinel;

	public LRU2(int cacheSize) {
		this.data = new HashMap<Long, Node>();
		this.maximumSize = cacheSize;
		this.sentinel = new Node();
	}

	private long totalAccesses;
	private long totalHits;
	private long totalSize;
	private long totalHitsSize;

	public boolean accessCache(long segmentId) {
		totalAccesses++;
		totalSize += CacheSim.CACHE_BLOCK_SIZE;
		return customInsert(segmentId);
	}

	public boolean customInsert(long key) {
		boolean wasHit = false;
		Node old = data.get(key);
		if (old == null) {
			Node node = new Node(key, sentinel);
			data.put(key, node);
			node.appendToTail();
			evict(node);
		}
		else {
			totalHits++;
			totalHitsSize += CacheSim.CACHE_BLOCK_SIZE;
			wasHit = true;
		}
		return wasHit;
	}

	/** Evicts while the map exceeds the maximum capacity. */
	private void evict(Node candidate) {
		if (data.size() > maximumSize) {
			evictEntry(candidate);
		}
	}

	private void evictEntry(Node node) {
		data.remove(node.key);
		node.remove();
	}

	/** A node on the double-linked list. */
	static final class Node {
		private final Node sentinel;

		private boolean marked;
		private Node prev;
		private Node next;
		private long key;

		/** Creates a new sentinel node. */
		public Node() {
			this.key = Long.MIN_VALUE;
			this.sentinel = this;
			this.prev = this;
			this.next = this;
		}

		/** Creates a new, unlinked node. */
		public Node(long key, Node sentinel) {
			this.sentinel = sentinel;
			this.key = key;
		}

		/** Appends the node to the tail of the list. */
		public void appendToTail() {
			Node tail = sentinel.prev;
			sentinel.prev = this;
			tail.next = this;
			next = sentinel;
			prev = tail;
		}

		/** Removes the node from the list. */
		public void remove() {
			prev.next = next;
			next.prev = prev;
			prev = next = null;
			key = Long.MIN_VALUE;
		}

		/** Moves the node to the head. */
		public void moveToHead() {
			if (key != Long.MIN_VALUE) {
				// unlink
				prev.next = next;
				next.prev = prev;
				// link
				next = sentinel.next;
				prev = sentinel;
				sentinel.next = this;
				next.prev = this;
			}
		}

		/** Moves the node to the tail. */
		public void moveToTail() {

			// unlink
			prev.next = next;
			next.prev = prev;

			// link
			next = sentinel;
			prev = sentinel.prev;
			sentinel.prev = this;
			prev.next = this;
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
