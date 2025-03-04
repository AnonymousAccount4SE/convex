package convex.peer;

import java.io.Closeable;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import convex.api.Convex;
import convex.core.Belief;
import convex.core.ErrorCodes;
import convex.core.Order;
import convex.core.Peer;
import convex.core.Result;
import convex.core.State;
import convex.core.crypto.AKeyPair;
import convex.core.data.ACell;
import convex.core.data.AMap;
import convex.core.data.AVector;
import convex.core.data.AccountKey;
import convex.core.data.AccountStatus;
import convex.core.data.Address;
import convex.core.data.Hash;
import convex.core.data.Keyword;
import convex.core.data.Keywords;
import convex.core.data.Maps;
import convex.core.data.Ref;
import convex.core.data.Strings;
import convex.core.data.Vectors;
import convex.core.data.prim.CVMLong;
import convex.core.exceptions.BadFormatException;
import convex.core.exceptions.MissingDataException;
import convex.core.init.Init;
import convex.core.lang.RT;
import convex.core.store.AStore;
import convex.core.store.Stores;
import convex.core.util.Counters;
import convex.core.util.Shutdown;
import convex.core.util.Utils;
import convex.net.MessageType;
import convex.net.NIOServer;
import convex.net.message.Message;


/**
 * A self contained Peer Server that can be launched with a config.
 * 
 * The primary role for the Server is to respond to incoming messages and maintain
 * network consensus.
 *
 * Components contained within the Server handle specific tasks, e.g:
 * - Client transaction handling
 * - CPoS Belief merges
 * - Belief Propagation
 * - CVM Execution
 *
 * "Programming is a science dressed up as art, because most of us don't
 * understand the physics of software and it's rarely, if ever, taught. The
 * physics of software is not algorithms, data structures, languages, and
 * abstractions. These are just tools we make, use, and throw away. The real
 * physics of software is the physics of people. Specifically, it's about our
 * limitations when it comes to complexity and our desire to work together to
 * solve large problems in pieces. This is the science of programming: make
 * building blocks that people can understand and use easily, and people will
 * work together to solve the very largest problems." ― Pieter Hintjens
 *
 */
public class Server implements Closeable {
	public static final int DEFAULT_PORT = 18888;


	static final Logger log = LoggerFactory.getLogger(Server.class.getName());

	// private static final Level LEVEL_MESSAGE = Level.FINER;

	/**
	 * Message Consumer that simply enqueues received client messages received by this peer
	 * Called on NIO thread: should never block
	 */
	Consumer<Message> receiveAction = m->processMessage(m);

	/**
	 * Connection manager instance.
	 */
	protected final ConnectionManager manager = new ConnectionManager(this);
	
	/**
	 * Connection manager instance.
	 */
	protected final BeliefPropagator propagator=new BeliefPropagator(this);
	
	/**
	 * Transaction handler instance.
	 */
	protected final TransactionHandler transactionHandler=new TransactionHandler(this);
	
	/**
	 * Transaction handler instance.
	 */
	protected final CVMExecutor executor=new CVMExecutor(this);


	/**
	 * Query handler instance.
	 */
	protected final QueryHandler queryHandler=new QueryHandler(this);

	/**
	 * Store to use for all threads associated with this server instance
	 */
	private final AStore store;

	/**
	 * Configuration
	 */

	private final HashMap<Keyword, Object> config;

	private final ACell rootKey;

	/**
	 * Flag for a running server. Setting to false will terminate server threads.
	 */
	private volatile boolean isRunning = false;

	/**
	 * NIO Server instance
	 */
	private NIOServer nio = NIOServer.create(this);

	/**
	 * The Peer Controller Address
	 */
	private Address controller;

	private Server(HashMap<Keyword, Object> config) throws TimeoutException, IOException {
		this.config = config;

		AStore configStore = (AStore) config.get(Keywords.STORE);
		this.store = (configStore == null) ? Stores.current() : configStore;
		
		
		// Switch to use the configured store for setup, saving the caller store
		final AStore savedStore=Stores.current();
		try {
			Stores.setCurrent(store);

			// Establish Peer state
			Peer peer = establishPeer();

			// Set up root key for Peer persistence. Default is Peer Account Key
			ACell rk=RT.cvm(config.get(Keywords.ROOT_KEY));
			if (rk==null) rk=peer.getPeerKey();
			rootKey=rk;

			// Ensure Peer is stored in executor and persisted
			executor.setPeer(peer);
			executor.persistPeerData();
			
			
			establishController();
		} finally {
			Stores.setCurrent(savedStore);
		}
	}

	/**
	 * Establish the controller Account for this Peer.
	 */
	private void establishController() {
		Peer peer=getPeer();
		Address controlAddress=RT.toAddress(getConfig().get(Keywords.CONTROLLER));
		if (controlAddress==null) {
			controlAddress=peer.getController();
			if (controlAddress==null) {
				throw new IllegalStateException("Peer Controller account does not exist for Peer Key: "+peer.getPeerKey());
			}
		}
		AccountStatus as=peer.getConsensusState().getAccount(controlAddress);
		if (as==null) {
			log.warn("Peer Controller Account does not exist: "+controlAddress);	
		} else if (!as.getAccountKey().equals(getKeyPair().getAccountKey())) {
			log.warn("Server keypair does not match keypair for control account: "+controlAddress);
		}
		this.setPeerController(controlAddress);
	}

	private Peer establishPeer() throws TimeoutException, IOException {
		log.debug("Establishing Peer with store: {}",Stores.current());
		try {
			AKeyPair keyPair = (AKeyPair) getConfig().get(Keywords.KEYPAIR);
			if (keyPair==null) {
				log.warn("No keypair provided for Server, deafulting to generated keypair for testing purposes");
				keyPair=AKeyPair.generate();
				log.warn("Generated keypair with public key: "+keyPair.getAccountKey());
			}

			// TODO: should probably move acquisition to launch phase?
			Object source=getConfig().get(Keywords.SOURCE);
			if (Utils.bool(source)) {
				// Peer sync case
				InetSocketAddress sourceAddr=Utils.toInetSocketAddress(source);
				Convex convex=Convex.connect(sourceAddr);
				log.info("Attempting Peer Sync with: "+sourceAddr);
				long timeout = establishTimeout();
				
				// Sync status and genesis state
				Result result = convex.requestStatusSync(timeout);
				AVector<ACell> status = result.getValue();
				if (status == null || status.count()!=Config.STATUS_COUNT) {
					throw new Error("Bad status message from remote Peer");
				}
				Hash beliefHash=RT.ensureHash(status.get(0));
				Hash networkID=RT.ensureHash(status.get(2));
				log.info("Attempting to sync genesis state with network: "+networkID);
				State genF=(State) convex.acquire(networkID).get(timeout,TimeUnit.MILLISECONDS);
				log.info("Retrieved Genesis State: "+networkID);
				
				// Belief acquisition
				log.info("Attempting to obtain peer Belief: "+beliefHash);
				Belief belF=null;
				long timeElapsed=0;
				while (belF==null) {
					try {
						belF=(Belief) convex.acquire(beliefHash).get(timeout,TimeUnit.MILLISECONDS);
					} catch (TimeoutException te) {
						timeElapsed+=timeout;
						log.info("Still waiting for Belief sync after "+timeElapsed/1000+"s");
					}
				}
				log.info("Retrieved Peer Belief: "+beliefHash+ " with memory size: "+belF.getMemorySize());

				convex.close();
				Peer peer=Peer.create(keyPair, genF, belF);
				return peer;

			} else if (Utils.bool(getConfig().get(Keywords.RESTORE))) {
				// Restore from storage case
				try {
					ACell rk=RT.cvm(config.get(Keywords.ROOT_KEY));
					if (rk==null) rk=keyPair.getAccountKey();

					Peer peer = Peer.restorePeer(store, keyPair, rk);
					if (peer != null) {
						log.info("Restored Peer with root data hash: {}",store.getRootHash());
						return peer;
					}
				} catch (Throwable e) {
					log.error("Can't restore Peer from store: {}",e);
				}
			}
			State genesisState = (State) config.get(Keywords.STATE);
			if (genesisState!=null) {
				log.debug("Defaulting to standard Peer startup with genesis state: "+genesisState.getHash());
			} else {
				AccountKey peerKey=keyPair.getAccountKey();
				genesisState=Init.createState(List.of(peerKey));
				log.debug("Created new genesis state: "+genesisState.getHash()+ " with initial peer: "+peerKey);
			}
			return Peer.createGenesisPeer(keyPair,genesisState);
		} catch (ExecutionException|InterruptedException e) {
			throw Utils.sneakyThrow(e);
		}
	}

	private long establishTimeout() {
		Object maybeTimeout=getConfig().get(Keywords.TIMEOUT);
		if (maybeTimeout==null) return Config.PEER_SYNC_TIMEOUT;
		Utils.toInt(maybeTimeout);
		return 0;
	}

	/**
	 * Creates a new (unlaunched) Server with a given config.
	 *
	 * @param config Server configuration map. Will be defensively copied.
	 *
	 * @return New Server instance
	 * @throws IOException If an IO Error occurred establishing the Peer
	 * @throws TimeoutException If Peer creation timed out
	 */
	public static Server create(HashMap<Keyword, Object> config) throws TimeoutException, IOException {
		return new Server(new HashMap<>(config));
	}

	/**
	 * Gets the current Belief held by this Peer
	 *
	 * @return Current Belief
	 */
	public Belief getBelief() {
		return getPeer().getBelief();
	}

	/**
	 * Gets the current Peer data structure for this {@link Server}.
	 *
	 * @return Current Peer data
	 */
	public Peer getPeer() {
		return executor.getPeer();
	}

	/**
	 * Gets the desired host name for this Peer
	 * @return Hostname String
	 */
	public String getHostname() {
		return (String) config.get(Keywords.URL);
	}

	/**
	 * Launch the Peer Server, including all main server threads
	 */
	public void launch() {
		AStore savedStore=Stores.current();
		try {
			Stores.setCurrent(store);

			HashMap<Keyword, Object> config = getConfig();

			Object p = config.get(Keywords.PORT);
			Integer port = (p == null) ? null : Utils.toInt(p);

			nio.launch((String)config.get(Keywords.BIND_ADDRESS), port);
			port = nio.getPort(); // Get the actual port (may be auto-allocated)

			// set running status now, so that loops don't terminate
			isRunning = true;

			// Start connection manager loop
			manager.start();

			queryHandler.start();

			propagator.start();
			
			transactionHandler.start();
			
			executor.start();
			
			// Close server on shutdown, should be before Etch stores in priority
			Shutdown.addHook(Shutdown.SERVER, ()->close());

			// Connect to source peer if specified
			if (getConfig().containsKey(Keywords.SOURCE)) {
				Object s=getConfig().get(Keywords.SOURCE);
				InetSocketAddress sa=Utils.toInetSocketAddress(s);
				if (sa!=null) {
					if (manager.connectToPeer(sa)!=null) {
						log.debug("Automatically connected to :source peer at: {}",sa);
					} else {
						log.warn("Failed to connect to :source peer at: {}",sa);
					}
				} else {
					log.warn("Failed to parse :source peer address {}",s);
				}
			}

			log.info( "Peer Server started at "+nio.getHostAddress()+" with Peer Address: {}",getPeerKey());
		} catch (Exception e) {
			close();
			throw new Error("Failed to launch Server", e);
		} finally {
			Stores.setCurrent(savedStore);
		}
	}

	/**
	 * Process a message received from a peer or client. We know at this point that the
	 * message decoded successfully, not much else.....
	 * 
	 * SECURITY: Should anticipate malicious messages
	 *
	 * If the message is partial, will be queued pending delivery of missing data.
	 *
	 * Runs on receiver thread
	 *
	 * @param m
	 */
	protected void processMessage(Message m) {
		MessageType type = m.getType();
		AStore tempStore=Stores.current();
		try {
			Stores.setCurrent(this.store);
			switch (type) {
			case BELIEF:
				processBelief(m);
				break;
			case CHALLENGE:
				processChallenge(m);
				break;
			case RESPONSE:
				processResponse(m);
				break;
			case COMMAND:
				break;
			case DATA:
				processData(m);
				break;
			case MISSING_DATA:
				processQuery(m);
				break;
			case QUERY:
				processQuery(m);
				break;
			case RESULT:
				break;
			case TRANSACT:
				processTransact(m);
				break;
			case GOODBYE:
				processClose(m);
				break;
			case STATUS:
				processStatus(m);
				break;
			default:
				Result r=Result.create(m.getID(), Strings.create("Bad Message Type: "+type), ErrorCodes.ARGUMENT);
				m.reportResult(r);
				break;
			}

		} catch (MissingDataException e) {
			Hash missingHash = e.getMissingHash();
			log.trace("Missing data: {} in message of type {}" , missingHash,type);
		} catch (Throwable e) {
			log.warn("Error processing client message: {}", e);
		} finally {
			Stores.setCurrent(tempStore);
		}
	}

	/**
	 * Respond to a request for missing data, on a best-efforts basis. Requests for
	 * missing data we do not hold are ignored.
	 *
	 * @param m
	 * @throws BadFormatException
	 */
	protected void handleMissingData(Message m)  {
		// payload for a missing data request should be a valid Hash
		Hash h = RT.ensureHash(m.getPayload());
		if (h == null) {
			log.warn("Bad missing data request, not a Hash, terminating client");
			m.getConnection().close();
			return;
		};

		Ref<?> r = store.refForHash(h);
		if (r != null) {
			try {
				ACell data = r.getValue();
				boolean sent = m.sendData(data);
				// log.trace( "Sent missing data for hash: {} with type {}",Utils.getClassName(data));
				if (!sent) {
					log.trace("Can't send missing data for hash {} due to full buffer",h);
				}
			} catch (Exception e) {
				log.trace("Unable to deliver missing data for {} due to exception: {}", h, e);
			}
		} else {
			// log.warn("Unable to provide missing data for {} from store: {}", h,Stores.current());
		}
	}

	protected void processTransact(Message m) {
		boolean queued=transactionHandler.offerTransaction(m);
		
		if (queued) {
			// log.info("transaction queued");
		} else {
			// Failed to queue transaction
			Result r=Result.create(m.getID(), Strings.SERVER_LOADED, ErrorCodes.LOAD);
			m.reportResult(r);
		} 
	}

	/**
	 * Called by a remote peer to close connections to the remote peer.
	 *
	 */
	protected void processClose(Message m) {
		m.closeConnection();
	}





	/**
	 * Gets the number of belief broadcasts made by this Peer
	 * @return Count of broadcasts from this Server instance
	 */
	public long getBroadcastCount() {
		return propagator.getBeliefBroadcastCount();
	}
	
	/**
	 * Gets the number of beliefs received by this Peer
	 * @return Count of the beliefs received by this Server instance
	 */
	public long getBeliefReceivedCount() {
		return propagator.beliefReceivedCount;
	}



	/**
	 * Gets the Peer controller Address
	 * @return Peer controller Address
	 */
	public Address getPeerController() {
		return controller;
	}

	/**
	 * Sets the Peer controller Address
	 * @param a Peer Controller Address to set
	 */
	public void setPeerController(Address a) {
		controller=a;
	}

	/**
	 * Adds an event to the inbound server event queue. May block.
	 * @param event Signed event to add to inbound event queue
	 * @return True if Belief was successfullly queued, false otherwise
	 */
	public boolean queueBelief(Message event) {
		boolean offered=propagator.queueBelief(event);
		return offered;
	}
	
	protected void processStatus(Message m) {
		try {
			// We can ignore payload

			log.trace( "Processing status request from: {}" ,m.getOriginString());
			// log.log(LEVEL_MESSAGE, "Processing query: " + form + " with address: " +
			// address);

			AVector<ACell> reply = getStatusVector();
			Result r=Result.create(m.getID(), reply);

			m.reportResult(r);
		} catch (Throwable t) {
			log.warn("Status Request Error:", t);
		}
	}

	/**
	 * Gets the status vector for the Peer
	 * 0 = latest belief hash
	 * 1 = states vector hash
	 * 2 = genesis state hash
	 * 3 = peer key
	 * 4 = consensus state
	 * 5 = consensus point
	 * 6 = proposal point
	 * 7 = ordering length
	 * 8 = consensus point vector
	 * @return Status vector
	 */
	public AVector<ACell> getStatusVector() {
		Peer peer=getPeer();
		Belief belief=peer.getBelief();
		
		State state=peer.getConsensusState();
		
		Hash beliefHash=belief.getHash();
		Hash stateHash=state.getHash();
		Hash genesisHash=peer.getNetworkID();
		AccountKey peerKey=peer.getPeerKey();
		Hash consensusHash=state.getHash();
		
		Order order=belief.getOrder(peerKey);
		CVMLong cp = CVMLong.create(order.getConsensusPoint()) ;
		CVMLong pp = CVMLong.create(order.getProposalPoint()) ;
		CVMLong op = CVMLong.create(order.getBlockCount()) ;
		AVector<CVMLong> cps = Vectors.of(Utils.toObjectArray(order.getConsensusPoints())) ;

		AVector<ACell> reply=Vectors.of(beliefHash,stateHash,genesisHash,peerKey,consensusHash, cp,pp,op,cps);
		assert(reply.count()==Config.STATUS_COUNT);
		return reply;
	}

	private void processChallenge(Message m) {
		manager.processChallenge(m, getPeer());
	}

	protected void processResponse(Message m) {
		manager.processResponse(m, getPeer());
	}

	protected void processQuery(Message m) {
		boolean queued= queryHandler.offerQuery(m);
		if (!queued) {
			Result r=Result.create(m.getID(), Strings.SERVER_LOADED, ErrorCodes.LOAD);
			m.reportResult(r);
		} 
	}
	


	private void processData(Message m) {
		ACell payload = m.getPayload();
		Counters.peerDataReceived++;
		
		// Note: partial messages are handled in Connection now
		Ref<?> r = Ref.get(payload);
		if (r.isEmbedded()) {
			log.warn("DATA with embedded value: "+payload);
			return;
		}
		r = r.persistShallow();

		if (log.isTraceEnabled()) {
			Hash payloadHash = r.getHash();
			log.trace( "Processing DATA of type: " + Utils.getClassName(payload) + " with hash: "
					+ payloadHash.toHexString());
		}
	}

	/**
	 * Process an incoming message that represents a Belief
	 *
	 * @param m
	 */
	protected void processBelief(Message m) {
		if (!propagator.queueBelief(m)) {
			log.warn("Incoming belief queue full");
		}
	}

	/**
	 * Gets the port that this Server is currently accepting connections on
	 * @return Port number
	 */
	public int getPort() {
		return nio.getPort();
	}

	@Override
	public void finalize() {
		close();
	}

	/**
	 * Writes the Peer data to the configured store.
	 * 
	 * Note: Does not flush buffers to disk. 
	 *
	 * This will overwrite any previously persisted peer data.
	 * @return Updater Peer value with persisted data
	 * @throws IOException In case of any IO Error
	 */
	@SuppressWarnings("unchecked")
	public Peer persistPeerData() throws IOException {
		AStore tempStore = Stores.current();
		try {
			Stores.setCurrent(store);
			AMap<Keyword,ACell> peerData = getPeer().toData();

			Ref<AMap<ACell,ACell>> rootRef = store.refForHash(store.getRootHash());
			AMap<ACell,ACell> currentRootData = (rootRef == null)? Maps.empty() : rootRef.getValue();
			AMap<ACell,ACell> newRootData = currentRootData.assoc(rootKey, peerData);

			newRootData=store.setRootData(newRootData).getValue();
			peerData=(AMap<Keyword, ACell>) newRootData.get(rootKey);
			log.debug( "Stored peer data for Server with hash: {}", peerData.getHash().toHexString());
			return Peer.fromData(getKeyPair(), peerData);
		}  finally {
			Stores.setCurrent(tempStore);
		}
	}

	@Override
	public void close() {
		if (!isRunning) return;
		isRunning = false;
		
		// Shut down propagator first, no point sending any more Beliefs
		propagator.close();
		
		queryHandler.close();
		transactionHandler.close();
		executor.close();
		
		Peer peer=getPeer();
		// persist peer state if necessary
		if ((peer != null) && !Boolean.FALSE.equals(getConfig().get(Keywords.PERSIST))) {
			try {
				persistPeerData();
			} catch (IOException e) {
				log.warn("Unable to persist Peer data: ",e);
			}
		}

		manager.close();
		nio.close();
		// Note we don't do store.close(); because we don't own the store.
		log.info("Peer shutdown complete for "+peer.getPeerKey());
	}

	/**
	 * Gets the host address for this Server (including port), or null if closed
	 *
	 * @return Host Address
	 */
	public InetSocketAddress getHostAddress() {
		return nio.getHostAddress();
	}

	/**
	 * Returns the Keypair for this peer server
	 *
	 * SECURITY: Be careful with this!
	 * @return Key pair for Peer
	 */
	public AKeyPair getKeyPair() {
		return getPeer().getKeyPair();
	}

	/**
	 * Gets the public key of the peer account
	 *
	 * @return AccountKey of this Peer
	 */
	public AccountKey getPeerKey() {
		AKeyPair kp = getKeyPair();
		if (kp == null) return null;
		return kp.getAccountKey();
	}

	/**
	 * Gets the Store configured for this Server. A server must consistently use the
	 * same store instance for all Server threads, as values may be shared.
	 *
	 * @return Store instance
	 */
	public AStore getStore() {
		return store;
	}

	public ConnectionManager getConnectionManager() {
		return manager;
	}

	public HashMap<Keyword, Object> getConfig() {
		return config;
	}

	/**
	 * Gets the action to perform for an incoming client message
	 * @return Message consumer
	 */
	public Consumer<Message> getReceiveAction() {
		return receiveAction;
	}

	/**
	 * Sets the desired host name for this Server
	 * @param string Desired host name String, e.g. "my-domain.com:12345"
	 */
	public void setHostname(String string) {
		config.put(Keywords.URL, string);
	}

	public boolean isLive() {
		return isRunning;
	}

	public TransactionHandler getTransactionHandler() {
		return transactionHandler;
	}

	public BeliefPropagator getBeliefPropagator() {
		return propagator;
	}
 
	/**
	 * Triggers CVM Executor Belief update
	 * @param belief New Belief
	 */
	public void updateBelief(Belief belief) {
		executor.queueUpdate(belief);
	}

	public CVMExecutor getCVMExecutor() {
		return executor;
	}

	public QueryHandler getQueryProcessor() {
		return queryHandler;
	}

	/**
	 * Shut down the Server, as gracefully as possible.
	 * @throws TimeoutException If shitdown attempt times out
	 * @throws IOException  In case of IO Error
	 */
	public void shutdown() throws IOException, TimeoutException {
		try {
			AKeyPair kp= getKeyPair();
			AccountKey key=kp.getAccountKey();
			Convex convex=Convex.connect(this, controller,kp);
			Result r=convex.transactSync("(set-peer-stake "+key+" 0)");
			if (r.isError()) {
				log.warn("Unable to remove Peer stake: "+r);
			}
		} finally {
			close();
		}
	}



}
