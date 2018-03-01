import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import java.util.Random;

public class CacheSim {

	//public final static int CACHE_MAX_BLOCKS = 10240;
	public final static int CACHE_BLOCK_SIZE = 8192;
	public final static int TOPOLOGY_NUM_LEAF_NODES = 128;
	public final static int OPERATION_READ = 0;
	public final static int OPERATION_WRITE = 1;
	public final static int OPERATION_REMOVE = 2;

	static int[] lookupTable = new int[128];
	static Random rand = new Random();

	public static int getOrAssignHostId(int host) {
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

	public static void main(String[] args) {
		if (args.length < 7 || args.length > 8) {
			System.err.println("Usage: ");
			System.err.println("java CacheSim [topology] [inLogFile] [edgePolicyName] [aggrPolicyName] [corePolicyName] [cacheMaxBlocks] [fatTreeK] (randSeed)");
			System.exit(-1);
		}

		int blockOperation;
		int blockId;
		int size;
		int src;
		int dest;

		//Queue<Block> cache = new Queue<Block>();
		Block b;

		int CACHE_MAX_MBLOCKS = Integer.parseInt(args[5]);

		// Set prng seed (if specified)
		if (args.length == 6) {
			long seed = Long.parseLong(args[7]);
			rand.setSeed(seed);
		}

		// Topology values
		final int NUM_NODES = 128;
		int fatTreeK = Integer.parseInt(args[6]);
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
		int edgePolicyName = Integer.parseInt(args[2]);
		int aggrPolicyName = Integer.parseInt(args[3]);
		int corePolicyName = Integer.parseInt(args[4]);
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
			src = Integer.parseInt(meta[4]);
			dest = Integer.parseInt(meta[5]);

			b = new Block(blockOperation, blockId, size, src, dest);
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

			// Run cache over path
			while (path.size() > 0) {
				if (b.blockOperation == OPERATION_READ) {
					int curNode = path.pollLast();

					boolean wasHit = false;

					int policyName = -1;
					if (curNode >= coreStart && curNode < aggrStart) {
						policyName = corePolicyName;
					}
					else if (curNode >= aggrStart && curNode < edgeStart) {
						policyName = aggrPolicyName;
					}
					else if (curNode >= edgeStart && curNode < edgeStart + nPods * nAggrPerPod) {
						policyName = edgePolicyName;
					}

					switch(policyName){
						case 1:
							wasHit = G.returnVertex(curNode).getLRU().accessCache(b);
							break;
						case 2:
							// TODO: Implement wasHit
							//wasHit = G.returnVertex(curNode).getLRFU().accessCache(b);
							break;
						case 3:
							// TODO: Implement wasHit
							//wasHit = G.returnVertex(curNode).getLRU2().accessCache(b);
							break;
						case 4:
							wasHit = G.returnVertex(curNode).getARC().accessCache(b);
							break;
						case 5:
							// TODO: Implement wasHit
							//wasHit = G.returnVertex(curNode).getTwoQueue().accessCache(b);
							break;
						case 6:
							//G.returnVertex(curNoe).getOPT().accessCache(b);
							break;
						case 7:
							wasHit = G.returnVertex(curNode).getMQ().accessCache(b);
							break;
						case 8:
							wasHit = G.returnVertex(curNode).getLirs().accessCache(b);
							break;
						default:
							System.err.println("Enter the right parameter for cache policy");
							System.exit(-3);
					}

					if (wasHit) {
						break;
					}
				}
				else {
					System.err.println("Not processing write operations in current implementation");
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

		System.out.println("Core:");
		for(int i = coreStart; i < nCore; i++){
			switch(corePolicyName){
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
			default:
				System.out.println("Enter the right parameter for cache policy");
			}
		}

		System.out.println();
		System.out.println("Aggregation:");
		for(int i = aggrStart; i < aggrStart + nAggrPerPod * nPods; i++){
			switch(aggrPolicyName){
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
			default:
				System.out.println("Enter the right parameter for cache policy");
			}
		}

		System.out.println();
		System.out.println("Edge:");
		for(int i = edgeStart; i < edgeStart + nEdgePerPod * nPods; i++){
			switch(edgePolicyName){
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
			default:
				System.out.println("Enter the right parameter for cache policy");
			}
		}
	}
}
