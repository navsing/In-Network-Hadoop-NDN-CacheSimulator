import java.util.*;

public final class TwoQueues {
	private static final Node UNLINKED = new Node();

	private final HashMap<Long, Node> data;
	private final int maximumSize;

	private int sizeIn;
	private final int maxIn;
	private final Node headIn;

	private int sizeOut;
	private final int maxOut;
	private final Node headOut;

	private int sizeMain;
	private final Node headMain;

	private long totalAccesses;
	private long totalHits;
	private long totalSize;
	private long totalHitsSize;
	
	public TwoQueues(int cacheSize) {

		this.headIn = new Node();
		this.headOut = new Node();
		this.headMain = new Node();
		this.maximumSize = cacheSize;
		this.data = new HashMap<Long, Node>();
		this.maxIn = (int)(maximumSize*0.9);
		this.maxOut = (int)(maximumSize*0.9);
	}

	public void accessCache(Block block) {
		for (int i = 0; i < Math.ceil((double) block.size / (double) CacheSim.CACHE_BLOCK_SIZE); i++) {
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
		}
	}
	
	public void customInsert(long key, int internalSize, Block block) {
		totalAccesses++;
		totalSize += internalSize;
		Node node = data.get(key);
		if (node != null) {
			switch (node.type) {
			case MAIN:
				node.moveToTail(headMain);
				if (block.blockOperation == CacheSim.OPERATION_READ) {
					totalHits++;
					totalHitsSize += internalSize;
				}
				return;
			case OUT:
				node.remove();
				sizeOut--;
				reclaimfor(node);
				node.appendToTail(headMain);
				node.type = QueueType.MAIN;
				sizeMain++;
				return;
			case IN:
				if (block.blockOperation == CacheSim.OPERATION_READ) {
					totalHits++;
					totalHitsSize += internalSize;
				}				
				return;
			default:
				throw new IllegalStateException();
			}
		} else {
			node = new Node(key);
			node.type = QueueType.IN;
			reclaimfor(node);
			node.appendToTail(headIn);
			sizeIn++;
		}
	}

	private void reclaimfor(Node node) {
		if ((sizeMain + sizeIn) < maximumSize) {
			data.put(node.key, node);
		} else if (sizeIn > maxIn) {
			// IN is full, move to OUT
			Node n = headIn.next;
			n.remove();
			sizeIn--;
			n.appendToTail(headOut);
			n.type = QueueType.OUT;
			sizeOut++;

			if (sizeOut > maxOut) {
				// OUT is full, drop oldest
				Node victim = headOut.next;
				data.remove(victim.key);
				victim.remove();
				sizeOut--;
			}
			data.put(node.key, node);
		} else {
			// OUT has room, evict from MAIN
			Node victim = headMain.next;
			data.remove(victim.key);
			victim.remove();
			sizeMain--;
			data.put(node.key, node);
		}
	}

	enum QueueType {
		MAIN, IN, OUT;
	}

	static final class Node {
		final long key;

		Node prev;
		Node next;
		QueueType type;

		Node() {
			this.key = Long.MIN_VALUE;
			this.prev = this;
			this.next = this;
		}

		Node(long key) {
			this.key = key;
			this.prev = UNLINKED;
			this.next = UNLINKED;
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
			if (key != Long.MIN_VALUE) {
				prev.next = next;
				next.prev = prev;
				prev = next = UNLINKED; // mark as unlinked
			}
		}
	}
	
	public void report() {
   if(totalAccesses == 0){
                        System.out.println("No Activity");
                        return;
                }

                System.out.println(totalAccesses+","+totalHits+","+((double)totalHits)/((double)totalAccesses)+","+totalSize+","+totalHitsSize+","+((double)totalHitsSize)/((double)totalSize));
	}
}
