import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class CacheSim {
	
	//public final static int CACHE_MAX_BLOCKS = 10240;
	public final static int CACHE_BLOCK_SIZE = 8192;
	public final static int TOPOLOGY_NUM_LEAF_NODES = 128;
	public final static int OPERATION_READ = 0;
	public final static int OPERATION_WRITE = 1;
	public final static int OPERATION_REMOVE = 2;

	public static void main(String[] args) {
		int blockOperation;
		int blockId;
		int size;
		int src;
		int dest;
                int numRouters = 1;
		
		//Queue<Block> cache = new Queue<Block>();
		Block b;
		

		int CACHE_MAX_MBLOCKS = Integer.parseInt(args[3]);

		
		In topology = new In(args[0]);
        Graph G = new Graph(topology, CACHE_MAX_MBLOCKS);
		In inLogFile = new In(args[1]);
		int policyName = Integer.parseInt(args[2]);
                if (args.length == 7) {
                  numRouters = Integer.parseInt(args[6]);
                }
		
		int k = 0;
		int[] lookupTable = new int[128];
		
		
		while(inLogFile.hasNextLine()){
			String[] meta = inLogFile.readLine().split(" ");
			if (meta[1].equals("READ")){
				blockOperation = OPERATION_READ;
			}else{
				blockOperation = OPERATION_WRITE;
			}
			blockId = Integer.parseInt(meta[2]);
			size = Integer.parseInt(meta[3]);
			src = Integer.parseInt(meta[4]);
			dest = Integer.parseInt(meta[5]);
			
			b = new Block(blockOperation, blockId, size, src, dest);
			
		
			for(k = 0; k < 128; k++){
				if(lookupTable[k]==0){
					lookupTable[k] = b.src;
					break;
				}
				if(lookupTable[k]== b.src){
					break;
				}
			}
			b.src = k + 1;
			for(k = 0; k < 128; k++){
				if(lookupTable[k]==0){
					lookupTable[k] = b.dest;
					break;
				}
				if(lookupTable[k]==b.dest){
					break;
				}
			}
			b.dest = k + 1;
			G.cache.get(b.src).enqueue(b);
		}
		while(true){
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
		}
		
		for(int i = 0; i < 1; i++){
//			System.out.println("Node :"+ i);
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
				System.out.println("ARC");
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
 
