
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

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.*;

/**
 * Low Inter-reference Recency Set (LIRS) algorithm. This algorithm organizes
 * blocks by their inter-reference recency (IRR) and groups entries as either
 * having a low (LIR) or high (HIR) recency. A LIR entry is preferable to retain
 * in the cache and evicted HIR entries may be retained as non-resident HIR
 * entries. This allows a non-resident HIR entry to be promoted to a LIR entry
 * shortly after a cache miss. The authors recommend sizing the maximum number
 * of LIR blocks to 99% of the cache's total size (1% remaining for HIR blocks).
 * <p>
 * The authors do not provide a recommendation for setting the maximum number of
 * non-resident HIR blocks. To avoid unbounded memory usage, these blocks are
 * placed on a non-resident queue to allow immediate removal, when a
 * non-resident size limit is reached, instead of searching the stack.
 * <p>
 * The algorithm is explained by the authors in <a href=
 * "http://web.cse.ohio-state.edu/hpcs/WWW/HTML/publications/papers/TR-02-6.pdf">
 * LIRS: An Efficient Low Inter-reference Recency Set Replacement Policy to
 * Improve Buffer Cache Performance</a> and <a href=
 * "http://web.cse.ohio-state.edu/hpcs/WWW/HTML/publications/papers/TR-05-11.pdf">
 * Making LRU Friendly to Weak Locality Workloads: A Novel Replacement Algorithm
 * to Improve Buffer Cache Performance</a>.
 *
 * @author ben.manes@gmail.com (Ben Manes)
 */
public final class LIRSCache {
	private final HashMap<Long, Node> data;
	private final List<Object> evicted;
	private final Node headNR;
	private final Node headS;
	private final Node headQ;

	private final int maximumNonResidentSize;
	private final int maximumHotSize;
	private final int maximumSize;

	private int sizeS;
	private int sizeQ;
	private int sizeNR;
	private int sizeHot;
	private int residentSize;

	private long totalAccesses;
	private long totalHits;
	private long totalSize;
	private long totalHitsSize;

	// Enable to print out the internal state
	private static final boolean debug = false;

	public LIRSCache(int inputCacheSize) {
		int cacheSize = (int)(inputCacheSize * 1024L * 1024L / CacheSim.CACHE_BLOCK_SIZE);
		this.maximumNonResidentSize = cacheSize*5;
		this.maximumHotSize = (int)(cacheSize*0.9);
		this.data = new HashMap<Long, Node>();
		this.maximumSize = cacheSize;
		this.evicted = new ArrayList<>();
		this.headNR = new Node();
		this.headS = new Node();
		this.headQ = new Node();
	}

	public boolean accessCache(long segmentId) {
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

		return customInsert(segmentId, CacheSim.CACHE_BLOCK_SIZE);
	}

	public boolean customInsert(long key, int internalSize) {
		totalAccesses++;
		totalSize += internalSize;
		Node node = data.get(key);
		boolean wasHit = false;
		if (node == null) {
			node = new Node(key);
			data.put(key, node);
			onNonResidentHir(node);
		}
		else if (node.status == Status.LIR) {
			totalHits++;
			totalHitsSize += internalSize;
			wasHit = true;
			onLir(node);
		}
		else if (node.status == Status.HIR_RESIDENT) {
			totalHits++;
			totalHitsSize += internalSize;
			wasHit = true;
			onResidentHir(node);
		}
		else if (node.status == Status.HIR_NON_RESIDENT) {
			node.removeFrom(StackType.NR);
			onNonResidentHir(node);
		}
		else {
			throw new IllegalStateException();
		}
		return wasHit;
	}

	private void onLir(Node node) {
		// Upon accessing an LIR block X. This access is guaranteed to be a hit
		// in the cache. We move
		// it to the top of stack S. If the LIR block is originally located at
		// the bottom of the
		// stack, we conduct a stack pruning. This case is illustrated in the
		// transition from state
		// (a) to state (b) in Fig. 2.
		boolean wasBottom = (headS.prevS == node);
		node.moveToTop(StackType.S);
		if (wasBottom) {
			pruneStack();
		}
	}

	private void onResidentHir(Node node) {
		// Upon accessing an HIR resident block X. This is a hit in the cache.
		// We move it to the top
		// of the stack S. There are two cases for the original location of
		// block X: a) If X is in
		// stack S, we change its status to LIR. This block is also removed from
		// stack Q. The LIR
		// block at the bottom of S is moved to the top of stack Q with its
		// status changed to HIR. A
		// stack pruning is then conducted. This case is illustrated in the
		// transition from state (a)
		// to state (c) in Fig. 2. b) If X is not in stack S, we leave its
		// status unchanged and move
		// it to the top of stack Q.
		boolean isInStack = node.isInStack(StackType.S);
		boolean isTop = node.isStackTop(StackType.S);
		node.moveToTop(StackType.S);

		if (isInStack && !isTop) {
			sizeHot++;
			node.status = Status.LIR;
			node.removeFrom(StackType.Q);

			Node bottom = headS.prevS;
			sizeHot--;

			bottom.status = Status.HIR_RESIDENT;
			bottom.removeFrom(StackType.S);
			bottom.moveToTop(StackType.Q);

			pruneStack();
		}
		else {
			node.moveToTop(StackType.Q);
		}
	}

	private void onNonResidentHir(Node node) {
		// When an LIR block set is not full, all the accessed blocks are given
		// LIR status until its
		// size reaches Llirs. After that, HIR status is given to any blocks
		// that are accessed for the
		// first time and to blocks that have not been accessed for a long time
		// so that currently they
		// are not in stack S.
		if (sizeHot < maximumHotSize) {
			onLirWarmupMiss(node);
		} else if (residentSize < maximumSize) {
			onHirWarmupMiss(node);
		} else {
			onFullMiss(node);
		}
		residentSize++;
	}

	/** Records a miss when the hot set is not full. */
	private void onLirWarmupMiss(Node node) {
		node.moveToTop(StackType.S);
		node.status = Status.LIR;
		sizeHot++;
	}

	/** Records a miss when the cold set is not full. */
	private void onHirWarmupMiss(Node node) {
		node.status = Status.HIR_RESIDENT;
		node.moveToTop(StackType.Q);
	}

	/** Records a miss when the hot set is full. */
	private void onFullMiss(Node node) {
		// Upon accessing an HIR non-resident block X. This is a miss. We remove
		// the HIR resident block
		// at the bottom of stack Q (it then becomes a non-resident block) and
		// evict it from the cache.
		// Then, we load the requested block X into the freed buffer and place
		// it at the top of stack
		// S. There are two cases for the original location of block X: a) If X
		// is in the stack S, we
		// change its status to LIR and move the LIR block at the bottom of
		// stack S to the top of
		// stack Q with its status changed to HIR. A stack pruning is then
		// conducted. This case is
		// illustrated in the transition from state(a) to state(d) in Fig.2. b)
		// If X is not in stack S,
		// we leave its status unchanged and place it at the top of stack Q.
		// This case is illustrated in
		// the transition from state (a) to state (e) in Fig. 2.

		node.status = Status.HIR_RESIDENT;
		if (residentSize >= maximumSize) {
			evict();
		}

		boolean isInStack = node.isInStack(StackType.S);
		node.moveToTop(StackType.S);

		if (isInStack) {
			node.status = Status.LIR;
			sizeHot++;

			Node bottom = headS.prevS;
			if (bottom.status != Status.LIR) {
				bottom.status = Status.HIR_RESIDENT;
				bottom.removeFrom(StackType.S);
				bottom.moveToTop(StackType.Q);
				sizeHot--;

				pruneStack();
			}
		}
		else {
			node.moveToTop(StackType.Q);
		}
	}

	private void pruneStack() {
		// In the LIRS replacement, there is an operation called "stack pruning"
		// on LIRS stack S, which
		// removes the HIR blocks at the stack bottom until a LIR block sits
		// there. This operation
		// serves two purposes: 1) We ensure the block at the stack bottom
		// always belongs to the LIR
		// block set. 2) After the LIR block in the bottom is removed, those HIR
		// blocks contiguously
		// located above it will not have a chance to change their status from
		// HIR to LIR since their
		// recencies are larger than the new maximum recency of the LIR blocks.
		for (;;) {
			//totalAccesses++;
			//totalSize += internalSize;

			Node bottom = headS.prevS;
			if ((bottom == headS) || (bottom.status == Status.LIR)) {
				break;
			}
			else if (bottom.status == Status.HIR_NON_RESIDENT) {
				// the map only needs to hold non-resident entries that are on
				// the stack
				bottom.removeFrom(StackType.NR);
				data.remove(bottom.key);
			}
			bottom.removeFrom(StackType.S);
		}

		// Bound the number of non-resident entries. While not described in the
		// paper, the author's
		// reference implementation provides a similar parameter to avoid
		// uncontrolled growth.
		Node node = headNR.prevNR;
		while (sizeNR > maximumNonResidentSize) {
			//totalAccesses++;
			//totalSize += internalSize;
			Node removed = node;
			node = node.prevNR;

			removed.removeFrom(StackType.NR);
			removed.removeFrom(StackType.S);
			data.remove(removed.key);
		}
	}

	private void evict() {
		// Once a free block is needed, the LIRS algorithm removes a resident
		// HIR block from the bottom
		// of stack Q for replacement. However, the replaced HIR block remains
		// in stack S with its
		// residence status changed to "non resident" if it is originally in the
		// stack. We ensure the
		// block in the bottom of the stack S is an LIR block by removing HIR
		// blocks after it.


		residentSize--;
		Node bottom = headQ.prevQ;
		bottom.removeFrom(StackType.Q);
		bottom.status = Status.HIR_NON_RESIDENT;
		if (bottom.isInStack(StackType.S)) {
			bottom.moveToTop(StackType.NR);
		} else {
			// the map only needs to hold non-resident entries that are on the
			// stack
			data.remove(bottom.key);
		}
		pruneStack();

		if (debug) {
			evicted.add(bottom.key);
		}
	}

	enum Status {
		LIR, HIR_RESIDENT, HIR_NON_RESIDENT;
	}

	// S holds three types of blocks, LIR blocks, resident HIR blocks,
	// non-resident HIR blocks
	// Q holds all of the resident HIR blocks
	// NR holds all of the non-resident HIR blocks
	enum StackType {
		// We store LIR blocks and HIR blocks with their recencies less than the
		// maximum recency of the
		// LIR blocks in a stack called LIRS stack S. S is similar to the LRU
		// stack in operation but has
		// a variable size.
		S,
		// To facilitate the search of the resident HIR blocks, we link all
		// these blocks into a small
		// stack, Q, with its size of Lhirs.
		Q,
		// Adaption to facilitate the search of the non-resident HIR blocks
		NR;
	}

	// Each entry in the stack records the LIR/HIR status of a block and its
	// residence status,
	// indicating whether or not the block resides in the cache.
	final class Node {
		final long key;

		Status status;

		Node prevS;
		Node nextS;
		Node prevQ;
		Node nextQ;
		Node prevNR;
		Node nextNR;

		boolean isInS;
		boolean isInQ;
		boolean isInNR;

		Node() {
			key = Long.MIN_VALUE;
			prevS = nextS = this;
			prevQ = nextQ = this;
			prevNR = nextNR = this;
		}

		Node(long key) {
			this.key = key;
		}

		public boolean isInStack(StackType stackType) {
			if (key!=Long.MIN_VALUE) {
				if (stackType == StackType.S) {
					return isInS;
				} else if (stackType == StackType.Q) {
					return isInQ;
				} else if (stackType == StackType.NR) {
					return isInNR;
				} else {
					throw new IllegalArgumentException();
				}
			}
			return isInNR;
		}

		public boolean isStackTop(StackType stackType) {
			if (stackType == StackType.S) {
				return (headS.nextS == this);
			} else if (stackType == StackType.Q) {
				return (headQ.nextQ == this);
			} else if (stackType == StackType.NR) {
				return (headNR.nextNR == this);
			} else {
				throw new IllegalArgumentException();
			}
		}

		public void moveToTop(StackType stackType) {
			if (isInStack(stackType)) {
				removeFrom(stackType);
			}

			if (stackType == StackType.S) {
				Node next = headS.nextS;
				headS.nextS = this;
				next.prevS = this;
				this.nextS = next;
				this.prevS = headS;
				isInS = true;
				sizeS++;
			} else if (stackType == StackType.Q) {
				Node next = headQ.nextQ;
				headQ.nextQ = this;
				next.prevQ = this;
				this.nextQ = next;
				this.prevQ = headQ;
				isInQ = true;
				sizeQ++;
			} else if (stackType == StackType.NR) {
				Node next = headNR.nextNR;
				headNR.nextNR = this;
				next.prevNR = this;
				this.nextNR = next;
				this.prevNR = headNR;
				isInNR = true;
				sizeNR++;
			} else {
				throw new IllegalArgumentException();
			}
		}

		public void removeFrom(StackType stackType) {
			if (isInStack(stackType)) {
				if (stackType == StackType.S) {
					prevS.nextS = nextS;
					nextS.prevS = prevS;
					prevS = nextS = null;
					isInS = false;
					sizeS--;
				} else if (stackType == StackType.Q) {
					prevQ.nextQ = nextQ;
					nextQ.prevQ = prevQ;
					prevQ = nextQ = null;
					isInQ = false;
					sizeQ--;
				} else if (stackType == StackType.NR) {
					prevNR.nextNR = nextNR;
					nextNR.prevNR = prevNR;
					prevNR = nextNR = null;
					isInNR = false;
					sizeNR--;
				} else {
					throw new IllegalArgumentException();
				}
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
