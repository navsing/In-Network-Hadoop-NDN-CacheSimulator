import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import java.util.Random;

public class CacheSim {

	//public final static int CACHE_MAX_BLOCKS = 10240;
	public static int CACHE_BLOCK_SIZE = 1048576;
	public static int NAME_NODE = -1;
	public static long totalTrafficBytes = 0;
	public final static int OPERATION_READ = 0;
	public final static int OPERATION_WRITE = 1;
	public final static int OPERATION_REMOVE = 2;

	static int[] lookupTable = new int[128];
	static Random rand = new Random();

	public static int getOrAssignHostId(int host) {
		if (host == NAME_NODE) {
			return 128;
		}

		for (int i = 0; i < 128; i++) {
			if (lookupTable[i] == host) {
				return i;
			}
		}

		while (true) {
			int i = rand.nextInt(128);
			if (lookupTable[i] == 0) {
				lookupTable[i] = host;
				return i;
			}
		}
	}

	public static void checkHasAdjacency(Graph g, int node, int test) throws Exception {
		boolean hasAdjacency = false;
		for (int adj : g.adj(node)) {
			if (adj == test) {
				hasAdjacency = true;
			}
		}
		if (!hasAdjacency) {
			System.err.println("Invalid: " + node + " to " + test);
			throw new Exception("ADJACENCY ERROR: Check algorithm");
		}
	}

	public static void assertNoOverflow(long oldVal, long newVal) {
		if (oldVal >= newVal) {
			System.err.println("OVERFLOW: " + oldVal + " -> " + newVal);
			System.exit(-4);
		}
	}

	public static void main(String[] args) {
		//if (args.length < 7 || args.length > 8) {
		if (args.length < 5 || args.length > 7) {
			System.err.println("Usage: ");
			//System.err.println("java CacheSim [topology] [inLogFile] [edgePolicyName] [aggrPolicyName] [corePolicyName] [cacheMaxBlocks] [fatTreeK] (randSeed)");
			System.err.println("java CacheSim [topology] [inLogFile] [policyName] [cacheBlockSize] [cacheMaxBlocks] (randSeed) (nameNode)");
			System.exit(-1);
		}

		int blockOperation;
		int blockId;
		int size;
		int offset;
		int src;
		int dest;

		//Queue<Block> cache = new Queue<Block>();
		Block b;

		CACHE_BLOCK_SIZE = Integer.parseInt(args[3]);
		int CACHE_MAX_MBLOCKS = Integer.parseInt(args[4]);

		// Set prng seed (if specified)
		if (args.length > 5) {
			long seed = Long.parseLong(args[5]);
			rand.setSeed(seed);
		}

		if (args.length > 6) {
			NAME_NODE = Integer.parseInt(args[6]);
		}

		// Topology values
		final int NUM_NODES = 128;
		//int numRouters = 1;
		//int fatTreeK = Integer.parseInt(args[6]);
		int fatTreeK = 8;
		int podSize = (int)Math.pow(fatTreeK / 2, 2);
		int nPods = NUM_NODES / podSize;
		int nEdgePerPod = fatTreeK / 2;
		int nAggrPerPod = fatTreeK / 2;
		int nCore = (int)Math.pow(fatTreeK / 2, 2);
		int numRouters = nPods * (nEdgePerPod + nAggrPerPod) + nCore;
		int coreStart = 0;
		int aggrStart = coreStart + nCore;
		int edgeStart = aggrStart + nAggrPerPod * nPods;
		/*int edgeStart = NUM_NODES;
		int aggrStart = edgeStart + nEdgePerPod * nPods;
		int coreStart = aggrStart + nAggrPerPod * nPods;*/

		In topology = new In(args[0]);
		Graph G = new Graph(topology, CACHE_MAX_MBLOCKS);
		In inLogFile = new In(args[1]);
		int policyName = Integer.parseInt(args[2]);
		/*int edgePolicyName = Integer.parseInt(args[2]);
		int aggrPolicyName = Integer.parseInt(args[3]);
		int corePolicyName = Integer.parseInt(args[4]);*/
		//int numRouters = Integer.parseInt(args[4]);

		while (inLogFile.hasNextLine()) {
			String[] meta = inLogFile.readLine().split(" ");
			if (meta[1].equals("READ")) {
				blockOperation = OPERATION_READ;
			}
			else {
				blockOperation = OPERATION_WRITE;
			}
			blockId = Integer.parseInt(meta[2]);
			size = Integer.parseInt(meta[3]);
			offset = Integer.parseInt(meta[4]);
			src = Integer.parseInt(meta[5]);
			dest = Integer.parseInt(meta[6]);

			b = new Block(blockOperation, blockId, size, offset, src, dest);
			b.src = getOrAssignHostId(src) + numRouters;
			b.dest = getOrAssignHostId(dest) + numRouters;

			/*for (k = 0; k < 128; k++) {
				if (lookupTable[k] == 0) {
					lookupTable[k] = b.src;
					break;
				}
				if (lookupTable[k] == b.src) {
					break;
				}
			}
			b.src = k + numRouters;
			for (k = 0; k < 128; k++) {
				if (lookupTable[k] == 0) {
					lookupTable[k] = b.dest;
					break;
				}
				if (lookupTable[k] == b.dest) {
					break;
				}
			}
			b.dest = k + numRouters;*/

			if (b.src == b.dest) {
				continue;
			}

			Deque<Integer> path = new ArrayDeque<Integer>();

			if (b.dest == numRouters + NUM_NODES) {
				// The NameNode is added onto the first edge switch of the first pod since there are 129 nodes
				if (b.src == numRouters) {
					b.dest = numRouters + 1;
				}
				else {
					b.dest = numRouters;
				}
			}

			// Run operation across topology
			int srcPod = (b.src - numRouters) / podSize;
			int destPod = (b.dest - numRouters) / podSize;

			if (srcPod == destPod) {
				// Do intra-pod routing

				// Add edge router, since we must cross it to reach any other host
				int srcEdgeRouter = edgeStart + nEdgePerPod * srcPod + (b.src - numRouters - (srcPod * podSize)) / (fatTreeK / 2);
				int destEdgeRouter = edgeStart + nEdgePerPod * destPod + (b.dest - numRouters - (destPod * podSize)) / (fatTreeK / 2);
				path.addLast(srcEdgeRouter);

				try {
					checkHasAdjacency(G, b.src, srcEdgeRouter);
				} catch (Exception e) {
					e.printStackTrace();
					System.exit(-2);
				}

				if (srcEdgeRouter == destEdgeRouter) {
					// Destination is on same edge router

					try {
						checkHasAdjacency(G, srcEdgeRouter, b.dest);
					} catch (Exception e) {
						e.printStackTrace();
						System.exit(-2);
					}

					continue;
				}

				// Use hash of src + dest to decide which aggregation router to use
				// Use original identifier (instead of index 0 to NUM_NODES) for extra spread(?)
				int aggrRouter = aggrStart + nAggrPerPod * srcPod + (src + dest) % nAggrPerPod;
				path.addLast(aggrRouter);

				try {
					checkHasAdjacency(G, srcEdgeRouter, aggrRouter);
				} catch (Exception e) {
					e.printStackTrace();
					System.exit(-2);
				}

				// Add destination edge router
				path.addLast(destEdgeRouter);

				try {
					checkHasAdjacency(G, aggrRouter, destEdgeRouter);
				} catch (Exception e) {
					e.printStackTrace();
					System.exit(-2);
				}

				try {
					checkHasAdjacency(G, destEdgeRouter, b.dest);
				} catch (Exception e) {
					e.printStackTrace();
					System.exit(-2);
				}
			}
			else {
				// Do inter-pod routing

				// Add edge router, since we must cross it to reach any other host
				int srcEdgeRouter = edgeStart + nEdgePerPod * srcPod + (b.src - numRouters - (srcPod * podSize)) / (fatTreeK / 2);
				path.addLast(srcEdgeRouter);

				try {
					checkHasAdjacency(G, b.src, srcEdgeRouter);
				} catch (Exception e) {
					e.printStackTrace();
					System.exit(-2);
				}

				// Choose which core router to use. Any will work since all pods are reachable through any
				// core router.
				int coreRouter = coreStart + (src + dest) % nCore;

				// Route through an aggregation router to the correct core router
				int srcAggrRouter = aggrStart + srcPod * nAggrPerPod + coreRouter / (fatTreeK / 2);
				path.addLast(srcAggrRouter);
				path.addLast(coreRouter);

				// Verify that source aggregation and core routers are connected to our path
				try {
					checkHasAdjacency(G, srcEdgeRouter, srcAggrRouter);
				} catch (Exception e) {
					e.printStackTrace();
					System.exit(-2);
				}

				try {
					checkHasAdjacency(G, srcAggrRouter, coreRouter);
				} catch (Exception e) {
					e.printStackTrace();
					System.exit(-2);
				}

				// Add destination aggregation router
				int destAggrRouter = aggrStart + destPod * nAggrPerPod + coreRouter / (fatTreeK / 2);
				path.addLast(destAggrRouter);

				try {
					checkHasAdjacency(G, coreRouter, destAggrRouter);
				} catch (Exception e) {
					e.printStackTrace();
					System.exit(-2);
				}

				// Add destination edge router
				int destEdgeRouter = edgeStart + nEdgePerPod * destPod + (b.dest - numRouters - (destPod * podSize)) / (fatTreeK / 2);
				path.addLast(destEdgeRouter);

				try {
					checkHasAdjacency(G, destAggrRouter, destEdgeRouter);
				} catch (Exception e) {
					e.printStackTrace();
					System.exit(-2);
				}

				// Verify destination edge router connects to destination host
				try {
					checkHasAdjacency(G, destEdgeRouter, b.dest);
				} catch (Exception e) {
					e.printStackTrace();
					System.exit(-2);
				}
			}

			if (b.blockOperation == OPERATION_READ) {
				// Run cache over path for each segment
				int firstSegment = (int)Math.floor((double)b.offset / (double)CACHE_BLOCK_SIZE);
				//int lastSegment = (int)Math.floor((double)(b.offset + b.size) / (double)CACHE_BLOCK_SIZE);
				int lastSegment = (int)Math.floor((double)(b.size + b.offset - 1) / (double)CACHE_BLOCK_SIZE);
				//System.out.println(b.blockId + " - " + b.offset + " to " + (b.offset + b.size) + " / " + firstSegment + " to " + lastSegment);
				for (int segment = firstSegment; segment <= lastSegment; segment++) {
					long cacheId = 100000000000L * segment + b.blockId;
					Deque<Integer> segmentPath = new ArrayDeque(path);
					boolean wasHit = false;
					while (segmentPath.size() > 0) {
						int curNode = segmentPath.removeLast();
						long oldTrafficBytes = totalTrafficBytes;
						totalTrafficBytes += CACHE_BLOCK_SIZE; // Add before reaching node
						assertNoOverflow(oldTrafficBytes, totalTrafficBytes);
						switch (policyName) {
							case 1:
								wasHit = G.returnVertex(curNode).getLRU().accessCache(cacheId);
								break;
							case 2:
								//G.returnVertex(0).getLRFU().accessCache(b);
								break;
							case 3:
								//G.returnVertex(0).getLRU2().accessCache(b);
								break;
							case 4:
								wasHit = G.returnVertex(curNode).getARC().accessCache(cacheId);
								break;
							case 5:
								//G.returnVertex(0).getTwoQueue().accessCache(b);
								break;
							case 6:
								//G.returnVertex(0).getOPT().accessCache(b);
								break;
							case 7:
								wasHit = G.returnVertex(curNode).getMQ().accessCache(cacheId);
								break;
							case 8:
								wasHit = G.returnVertex(curNode).getLirs().accessCache(cacheId);
								break;
							case 9:
								wasHit = G.returnVertex(curNode).getUnlimited().accessCache(cacheId);
								break;
							case 10:
								wasHit = G.returnVertex(curNode).getNoCache().accessCache(cacheId);
								break;
							default:
								System.err.println("Enter the right parameter for cache policy");
								System.exit(-3);
						}

						if (wasHit) {
							/*System.out.print("Hit on ");
							if (curNode >= coreStart && curNode < aggrStart) {
								System.out.println("CORE");
							}
							else if (curNode >= aggrStart && curNode < edgeStart) {
								System.out.println("AGGREGATION");
							}
							else if (curNode >= edgeStart && curNode < edgeStart + (nPods * nEdgePerPod)) {
								System.out.println("EDGE");
							}
							else {
								System.out.println("INVALID");
							}*/
							break;
						}
					}

					if (!wasHit) {
						// Missed all the way to the DataNode, so add traffic from edge to storing DataNode
						long oldTrafficBytes = totalTrafficBytes;
						totalTrafficBytes += CACHE_BLOCK_SIZE;
						assertNoOverflow(oldTrafficBytes, totalTrafficBytes);
					}
				}
			}
		}

		/*while(true){
			boolean allEmpty = true;
			for(int i = 0 ; i < G.listOfVertex.size(); i++){
				Queue<Block> q = G.cache.get(i);
				if (!q.isEmpty()) {
					allEmpty = false;
					Block block = q.dequeue();
					if (block.src != block.dest) {
						BreadthFirstPaths bfs = new BreadthFirstPaths(G, block.src);
						List<Integer> list = bfs.ShortestPath(G, bfs, block.src, block.dest);
						Collections.reverse(list);
						block.src = list.get(1);
						G.cache.get(list.get(1)).enqueue(block);
					}
					if(i<numRouters){
					switch(policyName){
						case 1:
							G.returnVertex(i).getLRU().accessCache(block);
							break;
						case 2:
							G.returnVertex(i).getLRFU().accessCache(block);
							break;
						case 3:
							G.returnVertex(i).getLRU2().accessCache(block);
							break;
						case 4:
							G.returnVertex(i).getARC().accessCache(block);
							break;
						case 5:
							G.returnVertex(i).getTwoQueue().accessCache(block);
							break;
						case 6:
							//G.returnVertex(i).getOPT().accessCache(block);
							break;
						case 7:
							G.returnVertex(i).getMQ().accessCache(block);
							break;
						case 8:
							G.returnVertex(i).getLirs().accessCache(block);
							break;
						default:
							System.out.println("Enter the right parameter for cache policy");
					}
				}
				}
			}
			if(allEmpty){
				break;
			}
		}*/

		/*switch(policyName){
		case 1:
			G.returnVertex(0).getLRU().report();
			break;
		case 2:
			G.returnVertex(0).getLRFU().report();
			break;
		case 3:
			G.returnVertex(0).getLRU2().report();
			break;
		case 4:
			//System.out.println("ARC");
			G.returnVertex(0).getARC().report();
			break;
		case 5:
			G.returnVertex(0).getTwoQueue().report();
			break;
		case 6:
			//G.returnVertex(0).getOPT().report();
			break;
		case 7:
			G.returnVertex(0).getMQ().report();
			break;
		case 8:
			G.returnVertex(0).getLirs().report();
			break;
		default:
			System.out.println("Enter the right parameter for cache policy");
		}*/

		System.out.println("Core:");
		for(int i = coreStart; i < nCore; i++){
			switch(policyName){
			case 1:
				G.returnVertex(i).getLRU().report();
				break;
			case 2:
				G.returnVertex(i).getLRFU().report();
				break;
			case 3:
				G.returnVertex(i).getLRU2().report();
				break;
			case 4:
				//System.out.println("ARC");
				G.returnVertex(i).getARC().report();
				break;
			case 5:
				G.returnVertex(i).getTwoQueue().report();
				break;
			case 6:
				//G.returnVertex(i).getOPT().report();
				break;
			case 7:
				G.returnVertex(i).getMQ().report();
				break;
			case 8:
				G.returnVertex(i).getLirs().report();
				break;
			case 9:
				G.returnVertex(i).getUnlimited().report();
				break;
			case 10:
				G.returnVertex(i).getNoCache().report();
				break;
			default:
				System.out.println("Enter the right parameter for cache policy");
			}
		}

		System.out.println();
		System.out.println("Aggregation:");
		for(int i = aggrStart; i < aggrStart + nAggrPerPod * nPods; i++){
			switch(policyName){
			case 1:
				G.returnVertex(i).getLRU().report();
				break;
			case 2:
				G.returnVertex(i).getLRFU().report();
				break;
			case 3:
				G.returnVertex(i).getLRU2().report();
				break;
			case 4:
				//System.out.println("ARC");
				G.returnVertex(i).getARC().report();
				break;
			case 5:
				G.returnVertex(i).getTwoQueue().report();
				break;
			case 6:
				//G.returnVertex(i).getOPT().report();
				break;
			case 7:
				G.returnVertex(i).getMQ().report();
				break;
			case 8:
				G.returnVertex(i).getLirs().report();
				break;
			case 9:
				G.returnVertex(i).getUnlimited().report();
				break;
			case 10:
				G.returnVertex(i).getNoCache().report();
				break;
			default:
				System.out.println("Enter the right parameter for cache policy");
			}
		}

		System.out.println();
		System.out.println("Edge:");
		for(int i = edgeStart; i < edgeStart + nEdgePerPod * nPods; i++){
			switch(policyName){
			case 1:
				G.returnVertex(i).getLRU().report();
				break;
			case 2:
				G.returnVertex(i).getLRFU().report();
				break;
			case 3:
				G.returnVertex(i).getLRU2().report();
				break;
			case 4:
				//System.out.println("ARC");
				G.returnVertex(i).getARC().report();
				break;
			case 5:
				G.returnVertex(i).getTwoQueue().report();
				break;
			case 6:
				//G.returnVertex(i).getOPT().report();
				break;
			case 7:
				G.returnVertex(i).getMQ().report();
				break;
			case 8:
				G.returnVertex(i).getLirs().report();
				break;
			case 9:
				G.returnVertex(i).getUnlimited().report();
				break;
			case 10:
				G.returnVertex(i).getNoCache().report();
				break;
			default:
				System.out.println("Enter the right parameter for cache policy");
			}
		}

		System.err.println(totalTrafficBytes);
	}
}
