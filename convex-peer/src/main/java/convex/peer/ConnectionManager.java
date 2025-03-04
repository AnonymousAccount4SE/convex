package convex.peer;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.UnresolvedAddressException;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import convex.api.Convex;
import convex.core.Belief;
import convex.core.Constants;
import convex.core.Peer;
import convex.core.Result;
import convex.core.State;
import convex.core.data.ACell;
import convex.core.data.AString;
import convex.core.data.AVector;
import convex.core.data.AccountKey;
import convex.core.data.Hash;
import convex.core.data.Keywords;
import convex.core.data.PeerStatus;
import convex.core.data.SignedData;
import convex.core.data.Vectors;
import convex.core.lang.RT;
import convex.core.util.LoadMonitor;
import convex.core.util.Utils;
import convex.net.ChallengeRequest;
import convex.net.Connection;
import convex.net.message.Message;
import convex.net.message.MessageRemote;

/**
 * Class for managing the outbound Peer connections from a Peer Server.
 *
 * Outbound connections need special handling: - Should be trusted connections
 * to known peers - Should be targets for broadcast of belief updates - Should
 * be limited in number
 */
public class ConnectionManager extends AThreadedComponent {

	private static final Logger log = LoggerFactory.getLogger(ConnectionManager.class.getName());

	/**
	 * Pause for each iteration of Server connection loop.
	 */
	static final long SERVER_CONNECTION_PAUSE = 500;

	/**
	 * Default pause for each iteration of Server connection loop.
	 */
	static final long SERVER_POLL_DELAY = 2000;

	/**
	 * How long to wait for a belief poll request of status.
	 */
	static final long POLL_TIMEOUT_MILLIS = 2000;

	/**
	 * How long to wait for a complete acquire of a belief.
	 */
	static final long POLL_ACQUIRE_TIMEOUT_MILLIS = 12000;

	/**
	 * Timeout for a regular Belief delta broadcast
	 */
	private static final long BROADCAST_TIMEOUT = 1000;

	/**
	 * Map of current connections.
	 */
	private final HashMap<AccountKey,Connection> connections = new HashMap<>();

	/**
	 * The list of outgoing challenges that are being made to remote peers
	 */
	private HashMap<AccountKey, ChallengeRequest> challengeList = new HashMap<>();

	private SecureRandom random = new SecureRandom();

	private long pollDelay;

	/**
	 * Celled by the connection manager to ensure we are tracking latest Beliefs on the network
	 */
	private void pollBelief() {
		try {
			// Poll only if no recent consensus updates
			long lastConsensus = server.getPeer().getConsensusState().getTimestamp().longValue();
			if (lastConsensus + pollDelay >= Utils.getCurrentTimestamp()) return;

			ArrayList<Connection> conns = new ArrayList<>(connections.values());
			if (conns.size() == 0) {
				// Nothing to do
				// log.debug("No connections available to poll!");
				return;
			}
			
			// TODO: probably shouldn't make a new connection?
			// Maybe use Convex instance instead of Connection?
			Connection c = conns.get(random.nextInt(conns.size()));

			if (c.isClosed()) return;
			Convex convex = Convex.connect(c.getRemoteAddress());
			try {
				// use requestStatusSync to auto acquire hash of the status instead of the value
				Result result=convex.requestStatusSync(POLL_TIMEOUT_MILLIS);
				AVector<ACell> status = result.getValue();

				Hash h=RT.ensureHash(status.get(0));

				Belief sb=(Belief) convex.acquire(h).get(POLL_ACQUIRE_TIMEOUT_MILLIS,TimeUnit.MILLISECONDS);

				server.queueBelief(Message.createBelief(sb));
			} finally {
				convex.close();
			}
		} catch (Throwable t) {
			if (server.isLive()) {
				log.warn("Belief Polling failed: {}",t.getClass().toString()+" : "+t.getMessage());
			}
		}
	}

	private long lastConnectionUpdate=Utils.getCurrentTimestamp();
	
	protected void maintainConnections() {
		State s=server.getPeer().getConsensusState();

		long now=Utils.getCurrentTimestamp();
		long millisSinceLastUpdate=Math.max(0,now-lastConnectionUpdate);

		int targetPeerCount=getTargetPeerCount();
		int currentPeerCount=connections.size();
		double totalStake=s.computeStakes().get(null);

		AccountKey[] peers = connections.keySet().toArray(new AccountKey[currentPeerCount]);
		for (AccountKey p: peers) {
			Connection conn=connections.get(p);

			// Remove closed connections. No point keeping these
			if ((conn==null)||(conn.isClosed())) {
				closeConnection(p);
				currentPeerCount--;
				continue;
			}

			/*
			 *  Always remove Peers not staked in consensus. This should eliminate Peers that have
			 *  withdrawn, have trivial stake or are slashed from current consideration.
			 */
			PeerStatus ps=s.getPeer(p);
			if ((ps==null)||(ps.getTotalStake()<=Constants.MINIMUM_EFFECTIVE_STAKE)) {
				closeConnection(p);
				currentPeerCount--;
				continue;
			}

			/* Drop Peers randomly if they have a small stake
			 * This ensure that new peers will get picked up occasionally and
			 * the distribution of peers tends towards the level of stake over time
			 */
			if ((millisSinceLastUpdate>0)&&(currentPeerCount>=targetPeerCount)) {
				double prop=ps.getTotalStake()/totalStake; // proportion of stake represented by this Peer
				// Very low chance of dropping a Peer with high stake (more than
				double keepChance=Math.min(1.0, prop*targetPeerCount);

				if (keepChance<1.0) {

					double dropRate=millisSinceLastUpdate/(double)Config.PEER_CONNECTION_DROP_TIME;
					if (random.nextDouble()<(dropRate*(1.0-keepChance))) {
						closeConnection(p);
						currentPeerCount--;
						continue;
					}
				}
			}

			// send request for a trusted peer connection if necessary
			// TODO: need to find out why the response message is not being received by the peers
			requestChallenge(p, conn, server.getPeer());
		}

		// refresh peers list
		currentPeerCount=connections.size();
		peers = connections.keySet().toArray(new AccountKey[currentPeerCount]);
		if (peers.length<targetPeerCount) {
			// Connect to a random peer with host address by stake
			// SECURITY: stake weighted connection is important to avoid bad peers
			// influencing the connection pool

			Set<AccountKey> potentialPeers=s.getPeers().keySet();
			InetSocketAddress target=null;
			double accStake=0.0;
			for (ACell c:potentialPeers) {
				AccountKey peerKey=RT.ensureAccountKey(c);
				if (connections.containsKey(peerKey)) continue; // skip if already connected

				if (server.getPeerKey().equals(peerKey)) continue; // don't connect to self!!

				PeerStatus ps=s.getPeers().get(peerKey);
				if (ps==null) continue; // skip
				AString hostName=ps.getHostname();
				if (hostName==null) continue;
				InetSocketAddress maybeAddress=Utils.toInetSocketAddress(hostName.toString());
				if (maybeAddress==null) continue;
				long peerStake=ps.getTotalStake();
				if (peerStake>0) {
					double t=random.nextDouble()*(accStake+peerStake);
					if (t>=accStake) {
						target=maybeAddress;
					}
					accStake+=peerStake;
				}
			}

			if (target!=null) {
				// Try to connect to Peer. If it fails, no worry, will retry another peer next time
				connectToPeer(target);
			}
		}
		
		lastConnectionUpdate=Utils.getCurrentTimestamp();
	}

	/**
	 * Gets the desired number of outgoing connections
	 * @return
	 */
	private int getTargetPeerCount() {
		Integer target;
		try {
			target = Utils.toInt(server.getConfig().get(Keywords.OUTGOING_CONNECTIONS));
		} catch (Exception ex) {
			target=null;
		}
		if (target==null) target=Config.DEFAULT_OUTGOING_CONNECTION_COUNT;
		return target;
	}


	public ConnectionManager(Server server) {
		super(server);

	}

	/**
	 * Close and remove a connection
	 *
	 * @param peerKey Peer key linked to the connection to close and remove.
	 *
	 */
	public synchronized void closeConnection(AccountKey peerKey) {
		if (connections.containsKey(peerKey)) {
			Connection conn=connections.get(peerKey);
			if (conn!=null) {
				conn.close();
			}
			connections.remove(peerKey);
		}
	}

	/**
	 * Close all outgoing connections from this Peer
	 */
	public synchronized void closeAllConnections() {
		for (Connection conn:connections.values()) {
			if (conn!=null) conn.close();
		}
		connections.clear();
	}

	/**
	 * Gets the current set of outbound peer connections from this server
	 *
	 * @return Set of connections
	 */
	public HashMap<AccountKey,Connection> getConnections() {
		return connections;
	}

	/**
	 * Return true if a specified Peer is connected
	 * @param peerKey Public Key of Peer
	 * @return True if connected
	 *
	 */
	public boolean isConnected(AccountKey peerKey) {
		return connections.containsKey(peerKey);
	}


	/**
	 * Gets a connection based on the peers public key
	 * @param peerKey Public key of Peer
	 *
	 * @return Connection instance, or null if not found
	 */
	public Connection getConnection(AccountKey peerKey) {
		if (!connections.containsKey(peerKey)) return null;
		return connections.get(peerKey);
	}

	/**
	 * Returns the number of active connections
	 * @return Number of connections
	 */
	public int getConnectionCount() {
		return connections.size();
	}

	/**
	 * Returns the number of trusted connections
	 * @return Number of trusted connections
	 *
	 */
	public int getTrustedConnectionCount() {
		int result = 0;
		for (Connection connection : connections.values()) {
			if (connection.isTrusted()) {
				result ++;
			}
		}
		return result;
	}

	public void processChallenge(Message m, Peer thisPeer) {
		try {
			SignedData<AVector<ACell>> signedData = m.getPayload();
			if ( signedData == null) {
				log.debug( "challenge bad message data sent");
				return;
			}
			AVector<ACell> challengeValues = signedData.getValue();

			if (challengeValues == null || challengeValues.size() != 3) {
				log.debug("challenge data incorrect number of items should be 3 not ",RT.count(challengeValues));
				return;
			}
			Connection pc = ((MessageRemote)m).getConnection();
			if ( pc == null) {
				log.warn( "No remote peer connection from challenge");
				return;
			}
			// log.log(LEVEL_CHALLENGE_RESPONSE, "Processing challenge request from: " + pc.getRemoteAddress());

			// get the token to respond with
			Hash token = RT.ensureHash(challengeValues.get(0));
			if (token == null) {
				log.warn( "no challenge token provided");
				return;
			}

			// check to see if we are both want to connect to the same network
			Hash networkId = RT.ensureHash(challengeValues.get(1));
			if (networkId == null) {
				log.warn( "challenge data has no networkId");
				return;
			}
			if ( !networkId.equals(thisPeer.getNetworkID())) {
				log.warn( "challenge data has incorrect networkId");
				return;
			}
			// check to see if the challenge is for this peer
			AccountKey toPeer = RT.ensureAccountKey(challengeValues.get(2));
			if (toPeer == null) {
				log.warn( "challenge data has no toPeer address");
				return;
			}
			if ( !toPeer.equals(thisPeer.getPeerKey())) {
				log.warn( "challenge data has incorrect addressed peer");
				return;
			}

			// get who sent this challenge
			AccountKey fromPeer = signedData.getAccountKey();

			// send the signed response back
			AVector<ACell> responseValues = Vectors.of(token, thisPeer.getNetworkID(), fromPeer, signedData.getHash());

			SignedData<ACell> response = thisPeer.sign(responseValues);
			// log.log(LEVEL_CHALLENGE_RESPONSE, "Sending response to "+ pc.getRemoteAddress());
			if (pc.sendResponse(response) == -1 ){
				log.warn("Failed sending response from challenge to ", pc.getRemoteAddress());
			}

		} catch (Throwable t) {
			log.error("Challenge Error: {}" ,t);
			// t.printStackTrace();
		}
	}

	AccountKey processResponse(Message m, Peer thisPeer) {
		try {
			SignedData<ACell> signedData = m.getPayload();

			log.debug( "Processing response request from: {}",m.getOriginString());

			@SuppressWarnings("unchecked")
			AVector<ACell> responseValues = (AVector<ACell>) signedData.getValue();

			if (responseValues.size() != 4) {
				log.warn( "response data incorrect number of items should be 4 not {}",responseValues.size());
				return null;
			}


			// get the signed token
			Hash token = RT.ensureHash(responseValues.get(0));
			if (token == null) {
				log.warn( "no response token provided");
				return null;
			}

			// check to see if we are both want to connect to the same network
			Hash networkId = RT.ensureHash(responseValues.get(1));
			if ( networkId == null || !networkId.equals(thisPeer.getNetworkID())) {
				log.warn( "response data has incorrect networkId");
				return null;
			}
			// check to see if the challenge is for this peer
			AccountKey toPeer = RT.ensureAccountKey(responseValues.get(2));
			if ( toPeer == null || !toPeer.equals(thisPeer.getPeerKey())) {
				log.warn( "response data has incorrect addressed peer");
				return null;
			}

			// hash sent by the response
			Hash challengeHash = RT.ensureHash(responseValues.get(3));

			// get who sent this challenge
			AccountKey fromPeer = signedData.getAccountKey();


			if ( !challengeList.containsKey(fromPeer)) {
				log.warn( "response from an unkown challenge");
				return null;
			}
			synchronized(challengeList) {

				// get the challenge data we sent out for this peer
				ChallengeRequest challengeRequest = challengeList.get(fromPeer);

				Hash challengeToken = challengeRequest.getToken();
				if (!challengeToken.equals(token)) {
					log.warn( "invalid response token sent");
					return null;
				}

				AccountKey challengeFromPeer = challengeRequest.getPeerKey();
				if (!signedData.getAccountKey().equals(challengeFromPeer)) {
					log.warn("response key does not match requested key, sent from a different peer");
					return null;
				}

				// hash sent by this peer for the challenge
				Hash challengeSourceHash = challengeRequest.getSendHash();
				if ( !challengeHash.equals(challengeSourceHash)) {
					log.warn("response hash of the challenge does not match");
					return null;
				}
				// remove from list incase this fails, we can generate another challenge
				challengeList.remove(fromPeer);

				Connection connection = getConnection(fromPeer);
				if (connection != null) {
					connection.setTrustedPeerKey(fromPeer);
				}

				// return the trusted peer key
				return fromPeer;
			}

		} catch (Throwable t) {
			log.error("Response Error: {}",t);
		}
		return null;
	}



	/**
	 * Sends out a challenge to a connection that is not trusted.
	 * @param toPeerKey Peer key that we need to send the challenge too.
	 * @param connection untrusted connection
	 * @param thisPeer Source peer that the challenge is issued from
	 *
	 */
	public void requestChallenge(AccountKey toPeerKey, Connection connection, Peer thisPeer) {
		synchronized(challengeList) {
			if (connection.isTrusted()) {
				return;
			}
			// skip if a challenge is already being sent
			if (challengeList.containsKey(toPeerKey)) {
				if (!challengeList.get(toPeerKey).isTimedout()) {
					// not timed out, then continue to wait
					return;
				}
				// remove the old timed out request
				challengeList.remove(toPeerKey);
			}
			ChallengeRequest request = ChallengeRequest.create(toPeerKey);
			if (request.send(connection, thisPeer)>=0) {
				challengeList.put(toPeerKey, request);
			} else {
				// TODO: check OK to do nothing and send later?
			}
		}
	}

	/**
	 * Broadcasts a Message to all connected Peers
	 * 
	 * @param msg Message to broadcast
	 * @throws InterruptedException If broadcast is interrupted
	 *
	 */
	public synchronized void broadcast(Message msg) throws InterruptedException {
		HashMap<AccountKey,Connection> hm=getCurrentConnections();
		
		long start=Utils.getCurrentTimestamp();
		while ((!hm.isEmpty())&&(start+BROADCAST_TIMEOUT>Utils.getCurrentTimestamp())) {
			ArrayList<Map.Entry<AccountKey,Connection>> left=new ArrayList<>(hm.entrySet());
			Utils.shuffle(left);
			for (Map.Entry<AccountKey,Connection> me: left) {
				Connection pc=me.getValue();
				try {
					boolean sent = pc.sendMessage(msg);
					if (sent) {
						hm.remove(me.getKey());	
					} else {
						// log.warn("Delayed sending to peer because of full Buffer");
					}
				} catch (ClosedChannelException e) {
					log.debug("Closed channel during broadcast");
					pc.close();
				} catch (IOException e) {
					log.debug("IO Error in broadcast: ", e);
					pc.close();
				}
			}
			
			// terminate loop if everything is successfully sent
			if (hm.isEmpty()) break;
			
			// Avoid a busy wait if buffers are full and still have things to send		
			LoadMonitor.down();
			Thread.sleep(10);
			LoadMonitor.up();
		}
		
		if ((!hm.isEmpty())&&server.isLive()) {
			log.warn("Unable to send broadcast to "+hm.size()+" peers");
		}
	}

	private HashMap<AccountKey, Connection> getCurrentConnections() {
		synchronized(connections) {
			return new HashMap<>(connections);
		}
	}

	/**
	 * Connects explicitly to a Peer at the given host address
	 * @param hostAddress Address to connect to
	 * @return new Connection, or null if attempt fails
	 */
	public Connection connectToPeer(InetSocketAddress hostAddress) {
		Connection newConn = null;
		try {
			// Use temp client connection to query status
			Convex convex=Convex.connect(hostAddress);
			Result result = convex.requestStatusSync(Config.DEFAULT_CLIENT_TIMEOUT);
			AVector<ACell> status = result.getValue();
			// close the temp connection to Convex API
			convex.close();
			
			if (status == null || status.count()!=Config.STATUS_COUNT) {
				throw new Error("Bad status message from remote Peer");
			}

			AccountKey peerKey =RT.ensureAccountKey(status.get(3));
			if (peerKey==null) return null;

			Connection existing=connections.get(peerKey);
			if ((existing!=null)&&!existing.isClosed()) return existing;
			synchronized(connections) {
				// reopen with connection to the peer and handle server messages
				newConn = Connection.connect(hostAddress, server.receiveAction, server.getStore(), null,Config.SOCKET_PEER_BUFFER_SIZE,Config.SOCKET_PEER_BUFFER_SIZE);
				connections.put(peerKey, newConn);
			}
		} catch (IOException | TimeoutException e) {
			// ignore any errors from the peer connections
		} catch (UnresolvedAddressException e) {
			log.info("Unable to resolve host address: "+hostAddress);
		}
		return newConn;
	}

	@Override
	public void close() {
		// broadcast GOODBYE message to all outgoing remote peers
		try {
			Message msg = Message.createGoodBye();
			broadcast(msg);
		} catch (Throwable e1) {
			// Ignore
		}
		
		super.close();
	}
	
	@Override
	public void start() {
		Object _pollDelay = server.getConfig().get(Keywords.POLL_DELAY);
		this.pollDelay = (_pollDelay == null) ? ConnectionManager.SERVER_POLL_DELAY : Utils.toInt(_pollDelay);

		super.start();
	}

	@Override
	protected void loop() throws InterruptedException {
		LoadMonitor.down();
		Thread.sleep(ConnectionManager.SERVER_CONNECTION_PAUSE);
		LoadMonitor.up();
		
		maintainConnections();
		pollBelief();
	}

	@Override
	protected String getThreadName() {
		return "Connection Manager thread at "+server.getPort();
	}




}
