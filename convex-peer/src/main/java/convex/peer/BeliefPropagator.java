package convex.peer;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import convex.core.Belief;
import convex.core.BeliefMerge;
import convex.core.Block;
import convex.core.ErrorCodes;
import convex.core.Order;
import convex.core.Result;
import convex.core.crypto.AKeyPair;
import convex.core.data.ACell;
import convex.core.data.AccountKey;
import convex.core.data.Blob;
import convex.core.data.BlobMap;
import convex.core.data.Format;
import convex.core.data.Hash;
import convex.core.data.Ref;
import convex.core.data.Refs;
import convex.core.data.SignedData;
import convex.core.data.Strings;
import convex.core.exceptions.BadFormatException;
import convex.core.exceptions.InvalidDataException;
import convex.core.exceptions.MissingDataException;
import convex.core.util.LoadMonitor;
import convex.core.util.Utils;
import convex.net.MessageType;
import convex.net.message.Message;

/**
 * Component class to handle propagation of new Beliefs from a Peer
 * 
 * Overall logic:
 * 1. We want to propagate a new Belief delta as fast as possible once one is received
 * 2. We want to pause to ensure that as many peers as possible have received the delta
 * 
 */
public class BeliefPropagator extends AThreadedComponent {
	/**
	 * Wait period for beliefs received in each iteration of Server Belief Merge loop.
	 */
	private static final long AWAIT_BELIEFS_PAUSE = 60L;

	
	public static final int BELIEF_REBROADCAST_DELAY=300;
	
	/**
	 * Time between full Belief broadcasts
	 */
	public static final int BELIEF_FULL_BROADCAST_DELAY=500;

	
	/**
	 * Minimum delay between successive Belief broadcasts
	 */
	public static final int BELIEF_BROADCAST_DELAY=10;
	
	/**
	 * Polling period for Belief propagator loop
	 */
	public static final int BELIEF_BROADCAST_POLL_TIME=1000;
	
	/**
	 * Queue on which Beliefs messages are received 
	 */
	// TODO: use config if provided
	private ArrayBlockingQueue<Message> beliefQueue = new ArrayBlockingQueue<>(Config.BELIEF_QUEUE_SIZE);

	
	static final Logger log = LoggerFactory.getLogger(BeliefPropagator.class.getName());


	private static final boolean ANALYSE_MISSING = false;
	

	long beliefReceivedCount=0L;


	public BeliefPropagator(Server server) {
		super(server);
	}
	
	
	/**
	 * Check if the propagator wants the latest Belief for rebroadcast
	 * @return True is rebroadcast is due
	 */
	public boolean isRebroadcastDue() {
		return (lastBroadcastTime+BELIEF_REBROADCAST_DELAY)<Utils.getCurrentTimestamp();
	}

	
	/**
	 * Time of last belief broadcast
	 */
	long lastBroadcastTime=0;
	
	/**
	 * Time of last full belief broadcast
	 */
	long lastFullBroadcastTime=0;
	
	private long beliefBroadcastCount=0L;
	
	public long getBeliefBroadcastCount() {
		return beliefBroadcastCount;
	}
	
	/**
	 * Queues a Belief Message for broadcast
	 * @param beliefMessage Belief Message to queue
	 * @return True if Belief is queued successfully
	 */
	public synchronized boolean queueBelief(Message beliefMessage) {
		return beliefQueue.offer(beliefMessage);
	}
	
	Belief belief=null;
	
	protected void loop() throws InterruptedException {
		
		// Wait for some new Beliefs to accumulate up to a given time
		Belief incomingBelief = awaitBelief();
		boolean updated= maybeUpdateBelief(incomingBelief);
		
		if (updated) {
			// Trigger CVM update before broadcast
			// This can potentially help latency on transaction result reporting etc.
			server.updateBelief(belief);
			
			// After an update, we want to make sure we have another update
			// This means awaitBeliefs returns immediately next time so we check we have converged
			// If Belief Queue is empty then queue our belief again
			// Otherwise we are fine
			if (beliefQueue.isEmpty()) {
				Message trigger=Message.createBelief(belief);
				queueBelief(trigger);
			}
		}
		
		long ts=Utils.getCurrentTimestamp();
		if (updated||(ts>lastBroadcastTime+BELIEF_REBROADCAST_DELAY)) {
			lastBroadcastTime=ts;
			Message msg;
			if (ts>lastFullBroadcastTime+BELIEF_FULL_BROADCAST_DELAY) {
				msg=createFullUpdateMessage();
				lastFullBroadcastTime=ts;
			} else {
				msg=createQuickUpdateMessage();
			}
			
			// Actually broadcast the message to outbound connected Peers
			if (msg!=null) {
				doBroadcast(msg);
			} else {
				log.warn("null message in BeliefPropagator!");
			}
		}
		
		// Persist Belief in all cases, just without announcing
		// This is mainly in case we get missing data / sync requests for the Belief
		// This is super cheap if already persisted, so no problem
		belief=ACell.createPersisted(belief).getValue();
		
		/* Update Belief again after persistence. We want to be using
		 * Latest persisted version as much as possible
		 */
		server.updateBelief(belief);
	}
	
	@Override public void start() {
		belief=server.getBelief();
		super.start();
	}
	
	/**
	 * Handle general Belief update, taking belief registered in newBeliefs
	 *
	 * @return true if Peer Belief changed, false otherwise
	 * @throws InterruptedException
	 */
	protected boolean maybeUpdateBelief(Belief newBelief) throws InterruptedException {

		// we are in full consensus if there are no unconfirmed blocks after the consensus point
		//boolean inConsensus=peer.getConsensusPoint()==peer.getPeerOrder().getBlockCount();

		// only do belief merge if needed either after:
		// - publishing a new block
		// - incoming beliefs
		// - not in full consensus yet
		//if (inConsensus&&(!published) && newBeliefs.isEmpty()) return false;

		boolean updated = maybeMergeBeliefs(newBelief);
		
		// publish new Block if needed. Guaranteed to change Belief / Order if this happens
		SignedData<Block> signedBlock= server.transactionHandler.maybeGetBlock(); 
		boolean published=false;
		if (signedBlock!=null) {
			belief=belief.proposeBlock(server.getKeyPair(),signedBlock);
			if (log.isDebugEnabled()) {
				Block bl=signedBlock.getValue();
				log.debug("New block proposed: {} transaction(s), size= {}, hash={}", bl.getTransactions().count(), signedBlock.getMemorySize(),signedBlock.getHash());
			}
			published=true;
		}
		
		// Return true iff we published a new Block or updated our own Order
		return (updated||published);

	}

	
	/**
	 * Checks for mergeable remote beliefs, and if found merge and update own
	 * belief.
	 * @param newBelief 
	 *
	 * @return True if Peer Belief Order was changed, false otherwise.
	 */
	protected boolean maybeMergeBeliefs(Belief... newBeliefs) {
		try {
			long ts=Utils.getCurrentTimestamp();
			AKeyPair kp=server.getKeyPair();
			BeliefMerge mc = BeliefMerge.create(belief,kp, ts, server.getPeer().getConsensusState());
			Belief newBelief = mc.merge(newBeliefs);

			AccountKey key=mc.getAccountKey();
			Order oldOrder=belief.getOrder(key);
			Order newOrder=newBelief.getOrder(key);
			
			boolean beliefChanged=false;
			if (oldOrder==null) {
				beliefChanged=newOrder!=null;
			} else {
				if (newOrder==null) {
					beliefChanged=(oldOrder!=null);
				} else {
					beliefChanged=!newOrder.consensusEquals(oldOrder);
				}
			}
			belief=newBelief;

			return beliefChanged;
		} catch (MissingDataException e) {
			// Shouldn't happen if beliefs are correctly persisted
			// e.printStackTrace();
			throw new Error("Missing data in belief merge: " + e.getMissingHash().toHexString(), e);
		} catch (InvalidDataException e) {
			// Shouldn't happen if Beliefs are already validated
			// e.printStackTrace();
			throw new Error("Invalid data in belief merge!", e);
		}
	}
	
	/**
	 * Await an incoming Belief for belief merge / potential update
	 * @return Incoming Belief, or null if nothing arrived within time window 
	 * @throws InterruptedException
	 */
	private Belief awaitBelief() throws InterruptedException {
		ArrayList<Message> beliefMessages=new ArrayList<>();
		
		// if we did a belief merge recently, pause for a bit to await more Beliefs
		LoadMonitor.down();
		Message firstEvent=beliefQueue.poll(AWAIT_BELIEFS_PAUSE, TimeUnit.MILLISECONDS);
		LoadMonitor.up();
		if (firstEvent==null) return null; // nothing arrived
		
		beliefMessages.add(firstEvent);
		beliefQueue.drainTo(beliefMessages); 
		HashMap<AccountKey,SignedData<Order>> newOrders=belief.getOrdersHashMap();
		// log.info("Merging Beliefs: "+allBeliefs.size());
		
		boolean anyOrderChanged=false;
		for (Message m: beliefMessages) {
			boolean changed=mergeBeliefMessage(newOrders,m);
			if (changed) anyOrderChanged=true;
		}
		if (!anyOrderChanged) return null;
		
		Belief newBelief= Belief.create(newOrders);
		return newBelief;
	}
	

	protected boolean mergeBeliefMessage(HashMap<AccountKey, SignedData<Order>> orders, Message m) {
		boolean changed=false;
		AccountKey myKey=server.getPeerKey();
		try {
			// Add to map of new Beliefs received for each Peer
			beliefReceivedCount++;
			
			try {
				ACell payload=m.getPayload();
				Collection<SignedData<Order>> a = Belief.extractOrders(payload);
				for (SignedData<Order> so:a ) {
					try {
						AccountKey key=so.getAccountKey();
						
						// Check if this Order could replace existing Order
						if (Utils.equals(myKey, key)) continue; // skip own order
						if (orders.containsKey(key)) {
							Order newOrder=so.getValue();
							Order oldOrder=orders.get(key).getValue();
							boolean replace=BeliefMerge.compareOrders(oldOrder, newOrder);
							if (!replace) continue;
						} 
						
						// TODO: check if Peer key is valid in current state?
						
						// Check signature before we accept Order
						if (!so.checkSignature()) {
							log.warn("Bad Order signature");
							m.reportResult(Result.create(m.getID(), Strings.BAD_SIGNATURE, ErrorCodes.SIGNATURE));
							// TODO: close connection?
							continue;
						};
						
						// Ensure we can persist newly received Order
						so=ACell.createPersisted(so).getValue();
						orders.put(key, so);
						changed=true;
					} catch (MissingDataException e) {
						Hash h=e.getMissingHash();
						log.warn("Missing data in Order! {}",h);
						analyseMissing(h,m,so);
						// System.exit(0);
						if (!m.sendMissingData(e.getMissingHash())) {
							log.warn("Unable to request Missing data in Belief!");
						}
						
					}
				}
			} catch (MissingDataException e) {
				// Missing data somewhere in Belief / Order received
				Hash h=e.getMissingHash();
				log.warn("Missing data in Belief! {}",e.getMessage());
				//System.out.print(Refs.printMissingTree(a));
				// System.exit(0);
				if (!m.sendMissingData(h)) {
					log.warn("Unable to request Missing data in Belief!");
				}
			}
		} catch (ClassCastException e) {
			// Bad message from Peer
			log.warn("Class cast exception in Belief!",e);
			m.reportResult(Result.create(m.getID(), Strings.BAD_FORMAT, ErrorCodes.FORMAT));
		}  catch (Exception e) {
			log.warn("Unexpected exception getting Belief",e);
		}
		return changed;
	}
	
	private void analyseMissing(Hash h, Message m, SignedData<Order> so) throws BadFormatException {
		if (!ANALYSE_MISSING) return;
		
		StringBuilder sb=new StringBuilder();
		ACell[] cs=Format.decodeCells(m.getMessageData());
		
		int n=cs.length;
		sb.append("Delta cell count = " +n+"\n");
		for (int i=0; i<n; i++) {
			ACell c=cs[i];
			sb.append(cs[i].getHash()+" = "+c.getClass()+"\n");
		}
		sb.append("\n");
		
		String s=Refs.printMissingTree(so);
		sb.append(s);
		System.out.println(sb.toString());
	}


	private void doBroadcast(Message msg) throws InterruptedException {
		server.manager.broadcast(msg);
		beliefBroadcastCount++;
	}
	
	private Message createFullUpdateMessage() {
		ArrayList<ACell> novelty=new ArrayList<>();
		
		// At this point we know something updated our belief, so we want to rebroadcast
		// belief to network
		Consumer<Ref<ACell>> noveltyHandler = r -> {
			ACell o = r.getValue();
			novelty.add(o);
			// System.out.println("Recording novelty: "+r.getHash()+ " "+o.getClass().getSimpleName());
		};

		// persist the state of the Peer, announcing the new Belief
		// (ensure we can handle missing data requests etc.)
		belief=ACell.createAnnounced(belief, noveltyHandler);
		lastFullBroadcastBelief=belief;

		Message msg = createBelief(belief, novelty);
		long messageSize=msg.getMessageData().count();
		if (messageSize>=Format.MAX_MESSAGE_LENGTH*0.95) {
			log.warn("Long Belief Delta message: "+messageSize);
		}
		return msg;
	}
	
	private Message createQuickUpdateMessage() {
		ArrayList<ACell> novelty=new ArrayList<>();
		
		// At this point we know something updated our belief, so we want to rebroadcast
		// belief to network
		Consumer<Ref<ACell>> noveltyHandler = r -> {
			ACell o = r.getValue();
			novelty.add(o);
			// System.out.println("Recording novelty: "+r.getHash()+ " "+o.getClass().getSimpleName());
		};

		// persist the state of the Peer, announcing the new Belief
		// (ensure we can handle missing data requests etc.)
		AccountKey key=server.getPeerKey();
		BlobMap<AccountKey, SignedData<Order>> orders = belief.getOrders();
		SignedData<Order> order=belief.getOrders().get(key);
		if (order==null) return null;
		
		order=ACell.createAnnounced(order, noveltyHandler);
		
		// Update belief orders with persisted version
		orders=orders.assoc(key, order);
		belief=belief.withOrders(orders);

		Message msg = createBelief(order, novelty);
		long messageSize=msg.getMessageData().count();
		if (messageSize>=Format.MAX_MESSAGE_LENGTH*0.95) {
			log.warn("Long Belief Delta message: "+messageSize);
		}
		return msg;
	}
	
	/**
	 * Create a Belief message ready for broadcast including delta novelty
	 * @param novelty Novel cells for transmission. 
	 * @param belief Belief top level Cell to encode
	 * @return Message instance
	 */
	private static Message createBelief(ACell payload, List<ACell> novelty) {
		int n=novelty.size();
		if (n==0) {
			//log.warn("No novelty in Belief");
			novelty.add(n, payload);
		} else if (!payload.equals(novelty.get(n-1))) {
			//log.warn("Last element not Belief out of "+novelty.size());
			novelty.add(n, payload);
		}
		Blob data=Format.encodeDelta(novelty);
		return Message.create(null,MessageType.BELIEF,payload,data);
	}

	private Belief lastFullBroadcastBelief;

	public Belief getLastBroadcastBelief() {
		return lastFullBroadcastBelief;
	}


	@Override
	protected String getThreadName() {
		return "Belief propagator thread on port "+server.getPort();
	}
}
