package convex.core;

import java.util.ArrayList;
import java.util.HashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import convex.core.data.ABlob;
import convex.core.data.ACell;
import convex.core.data.AMap;
import convex.core.data.ARecord;
import convex.core.data.AVector;
import convex.core.data.AccountKey;
import convex.core.data.AccountStatus;
import convex.core.data.Address;
import convex.core.data.Blob;
import convex.core.data.BlobMap;
import convex.core.data.BlobMaps;
import convex.core.data.Format;
import convex.core.data.Hash;
import convex.core.data.IRefFunction;
import convex.core.data.Keyword;
import convex.core.data.Keywords;
import convex.core.data.LongBlob;
import convex.core.data.MapEntry;
import convex.core.data.PeerStatus;
import convex.core.data.Ref;
import convex.core.data.SignedData;
import convex.core.data.Strings;
import convex.core.data.Symbol;
import convex.core.data.Tag;
import convex.core.data.Vectors;
import convex.core.data.prim.CVMLong;
import convex.core.exceptions.BadFormatException;
import convex.core.exceptions.BadSignatureException;
import convex.core.exceptions.InvalidDataException;
import convex.core.lang.AOp;
import convex.core.lang.Context;
import convex.core.lang.Juice;
import convex.core.lang.RT;
import convex.core.lang.Symbols;
import convex.core.lang.impl.RecordFormat;
import convex.core.transactions.ATransaction;
import convex.core.util.Counters;
import convex.core.util.Utils;

/**
 * Class representing the immutable state of the CVM
 *
 * State transitions are represented by blocks of transactions, according to the logic: s[n+1] = s[n].applyBlock(b[n])
 *
 * State contains the following elements - Map of AccountStatus for every
 * Address - Map of PeerStatus for every Peer Address - Global values - Schedule
 * data structure
 *
 * "State. You're doing it wrong" - Rich Hickey
 *
 */
public class State extends ARecord {
	private static final Keyword[] STATE_KEYS = new Keyword[] { Keywords.ACCOUNTS, Keywords.PEERS,
			Keywords.GLOBALS, Keywords.SCHEDULE };

	private static final RecordFormat FORMAT = RecordFormat.of(STATE_KEYS);

	/**
	 * Symbols for global values in :globals Vector
	 */
	public static final AVector<Symbol> GLOBAL_SYMBOLS=Vectors.of(Symbols.TIMESTAMP, Symbols.FEES, Symbols.JUICE_PRICE, Symbols.MEMORY, Symbols.MEMORY_VALUE,Symbols.PROTOCOL);

	// Indexes for globals in :globals Vector
	public static final int GLOBAL_TIMESTAMP=0;
	public static final int GLOBAL_FEES=1;
	public static final int GLOBAL_JUICE_PRICE=2;
	public static final int GLOBAL_MEMORY_MEM=3;
	public static final int GLOBAL_MEMORY_CVX=4;
	public static final int GLOBAL_PROTOCOL=5; // TODO: move to actor

	/**
	 * An empty State
	 */
	public static final State EMPTY = create(Vectors.empty(), BlobMaps.empty(), Constants.INITIAL_GLOBALS,
			BlobMaps.empty());

	private static final Logger log = LoggerFactory.getLogger(State.class.getName());


	// Note: we are embedding these directly in the State cell.
	// TODO: check we aren't at risk of hitting max encoding size limits

	private final AVector<AccountStatus> accounts;
	private final BlobMap<AccountKey, PeerStatus> peers;
	private final AVector<ACell> globals;
	private final BlobMap<ABlob, AVector<ACell>> schedule;

	private State(AVector<AccountStatus> accounts, BlobMap<AccountKey, PeerStatus> peers,
			AVector<ACell> globals, BlobMap<ABlob, AVector<ACell>> schedule) {
		super(FORMAT.count());
		this.accounts = accounts;
		this.peers = peers;
		this.globals = globals;
		this.schedule = schedule;
	}

	@Override
	public ACell get(Keyword k) {
		if (Keywords.ACCOUNTS.equals(k)) return accounts;
		if (Keywords.PEERS.equals(k)) return peers;
		if (Keywords.GLOBALS.equals(k)) return globals;
		if (Keywords.SCHEDULE.equals(k)) return schedule;
		return null;
	}

	@Override
	public int getRefCount() {
		int rc=accounts.getRefCount();
		rc+=peers.getRefCount();
		rc+=globals.getRefCount();
		rc+=schedule.getRefCount();
		return rc;
	}

	public <R extends ACell> Ref<R> getRef(int i) {
		if (i<0) throw new IndexOutOfBoundsException(i);

		{
			int c=accounts.getRefCount();
			if (i<c) return accounts.getRef(i);
			i-=c;
		}

		{
			int c=peers.getRefCount();
			if (i<c) return peers.getRef(i);
			i-=c;
		}

		{
			int c=globals.getRefCount();
			if (i<c) return globals.getRef(i);
			i-=c;
		}

		{
			int c=schedule.getRefCount();
			if (i<c) return schedule.getRef(i);
			i-=c;
		}

		throw new IndexOutOfBoundsException(i);
	}

	@Override
	public State updateRefs(IRefFunction func) {
		AVector<AccountStatus> newAccounts = accounts.updateRefs(func);
		BlobMap<AccountKey, PeerStatus> newPeers = peers.updateRefs(func);
		AVector<ACell> newGlobals = globals.updateRefs(func);
		BlobMap<ABlob, AVector<ACell>> newSchedule = schedule.updateRefs(func);
		if ((accounts == newAccounts) && (peers == newPeers) && (globals == newGlobals)
				&& (schedule == newSchedule)) {
			return this;
		}
		return new State(newAccounts, newPeers, newGlobals, newSchedule);
	}

	/**
	 * Create a State
	 * @param accounts Accounts
	 * @param peers Peers
	 * @param globals Globals
	 * @param schedule Schedule (may be null)
	 * @return New State instance
	 */
	public static State create(AVector<AccountStatus> accounts, BlobMap<AccountKey, PeerStatus> peers,
			AVector<ACell> globals, BlobMap<ABlob, AVector<ACell>> schedule) {
		return new State(accounts, peers, globals, schedule);
	}

	@Override
	public int encode(byte[] bs, int pos) {
		bs[pos++]=getTag();
		return encodeRaw(bs,pos);
	}

	@Override
	public int encodeRaw(byte[] bs, int pos) {
		pos = accounts.encode(bs,pos);
		pos = peers.encode(bs,pos);
		pos = globals.encode(bs,pos);
		pos = schedule.encode(bs,pos);
		return pos;
	}

	@Override
	public int getEncodingLength() {
		int length=1;
		length+=accounts.getEncodingLength();
		length+=peers.getEncodingLength();
		length+=globals.getEncodingLength();
		length+=schedule.getEncodingLength();
		return length;
	}

	@Override
	public int estimatedEncodingSize() {
		int est=1;
		est+=accounts.estimatedEncodingSize();
		est+=peers.estimatedEncodingSize();
		est+=globals.estimatedEncodingSize();
		est+=schedule.estimatedEncodingSize();
		return est;
	}

	/**
	 * Reads a State from an encoding. Assumes tag byte already read.
	 *
	 * @param b Blob to read from
	 * @param pos start position in Blob 
	 * @return Decoded State
	 * @throws BadFormatException If a State could not be read
	 */
	public static State read(Blob b, int pos) throws BadFormatException {
		int epos=pos+1; // skip tag
		AVector<AccountStatus> accounts = Format.read(b,epos);
		if (accounts==null) throw new BadFormatException("Null accounts!");
		epos+=Format.getEncodingLength(accounts);

		BlobMap<AccountKey, PeerStatus> peers = Format.read(b,epos);
		if (peers==null) throw new BadFormatException("Null peers!");
		epos+=Format.getEncodingLength(peers);

		AVector<ACell> globals = Format.read(b,epos);
		if (globals==null) throw new BadFormatException("Null globals!");
		epos+=Format.getEncodingLength(globals);

		BlobMap<ABlob, AVector<ACell>> schedule = Format.read(b,epos);
		if (schedule==null) throw new BadFormatException("Null schedule!");
		epos+=Format.getEncodingLength(schedule);

		State result=create(accounts, peers, globals, schedule);
		result.attachEncoding(b.slice(pos,epos));
		return result;
	}

	/**
	 * Get all Accounts in this State
	 * @return Vector of Accounts
	 */
	public AVector<AccountStatus> getAccounts() {
		return accounts;
	}

	/**
	 * Gets the map of Peers for this State
	 *
	 * @return A map of addresses to PeerStatus records
	 */
	public BlobMap<AccountKey, PeerStatus> getPeers() {
		return peers;
	}

	/**
	 * Gets the balance of a specific address, or null if the Address does not exist
	 * @param address Address to check
	 * @return Long balance, or null if Account does not exist
	 */
	public Long getBalance(Address address) {
		AccountStatus acc = getAccount(address);
		if (acc == null) return null;
		return acc.getBalance();
	}

	public State withBalance(Address address, long newBalance) {
		AccountStatus acc = getAccount(address);
		if (acc == null) {
			throw new Error("No account for " + address);
		} else {
			acc = acc.withBalance(newBalance);
		}
		return putAccount(address, acc);
	}

	/**
	 * Applies a signed Block to the current state, i.e. the State Transition function
	 * 
	 * @param signedBlock Signed Block to apply
	 * @return The BlockResult from applying the given Block to this State
	 */
	public BlockResult applyBlock(SignedData<Block> signedBlock) {
		Block block=signedBlock.getValue();
		Counters.applyBlock++;

		// First check the Block passes pre-conditions for application
		BlockResult maybeFailed=checkBlock(signedBlock);
		if (maybeFailed!=null) {
			return maybeFailed;
		}
		
		State state = prepareBlock(block);
		return state.applyTransactions(block);
	}

	private BlockResult checkBlock(SignedData<Block> signedBlock) {
		Block block=signedBlock.getValue();
		AccountKey peerKey=signedBlock.getAccountKey();
		PeerStatus ps=peers.get(peerKey);
		if (ps==null) return BlockResult.createInvalidBlock(this,block,Strings.MISSING_PEER);
		if (ps.getPeerStake()<Constants.MINIMUM_EFFECTIVE_STAKE) {
			return BlockResult.createInvalidBlock(this,block,Strings.INSUFFICIENT_STAKE);
		}		
		
		if (block.getTransactions().count()>Constants.MAX_TRANSACTIONS_PER_BLOCK) {
			return BlockResult.createInvalidBlock(this,block,Strings.ILLEGAL_BLOCK_SIZE);
		}
		
		return null;
	}

	/**
	 * Apply state updates consistent with time advancing to a given timestamp
	 * @param b Block to apply
	 * @return Updated state after block preparation
	 */
	private State prepareBlock(Block b) {
		State state = this;
		state = state.applyTimeUpdate(b.getTimeStamp());
		state = state.applyScheduledTransactions();
		return state;
	}
	
	/**
	 * Updates the State based on passage of time.
	 * 
	 * Note: while this can be applied independently, normal correct State updates
	 * should go via `applyBlock`
	 * 
	 * @param newTimestamp New timestamp to apply
	 * @return Updates State
	 */
	public State applyTimeUpdate(long newTimestamp) {
		State state = this;
		AVector<ACell> glbs = state.globals;
		long oldTimestamp=((CVMLong)glbs.get(GLOBAL_TIMESTAMP)).longValue();
		
		// Exit if no time elapsed
		if (newTimestamp <= oldTimestamp) return this;
		// long elapsed=newTimestamp-oldTimestamp;
		
		// Update timestamp
		glbs=glbs.assoc(GLOBAL_TIMESTAMP,CVMLong.create(newTimestamp));
		
		// Grow memory pool if required
		long memAdditions=(newTimestamp/Constants.MEMORY_POOL_GROWTH_INTERVAL)-(oldTimestamp/Constants.MEMORY_POOL_GROWTH_INTERVAL);
		if (memAdditions>0) {
			long mem=((CVMLong)glbs.get(GLOBAL_MEMORY_MEM)).longValue();
			long add=memAdditions*Constants.MEMORY_POOL_GROWTH;
			if (add>0) {
				long  newMem=mem+add;
				glbs=glbs.assoc(GLOBAL_MEMORY_MEM, CVMLong.create(newMem));
			} else {
				throw new Error("Bad memory additions?");
			}
		}
		
		state = state.withGlobals(glbs);
		return state;
	}

	@SuppressWarnings("unchecked")
	public State applyScheduledTransactions() {
		long tcount = 0;
		BlobMap<ABlob, AVector<ACell>> sched = this.schedule;
		CVMLong timestamp = this.getTimestamp();

		// ArrayList to accumulate the transactions to apply. Null until we need it
		ArrayList<ACell> al = null;

		// walk schedule entries to determine how many there are
		// and remove from the current schedule
		// we can optimise bulk removal later
		while (tcount < Constants.MAX_SCHEDULED_TRANSACTIONS_PER_BLOCK) {
			if (sched.isEmpty()) break;
			MapEntry<ABlob, AVector<ACell>> me = sched.entryAt(0);
			ABlob key = me.getKey();
			long time = key.toExactLong();
			if (time > timestamp.longValue()) break; // exit if we are still in the future
			AVector<ACell> trans = me.getValue();
			long numScheduled = trans.count(); // number scheduled at this schedule timestamp
			long take = Math.min(numScheduled, Constants.MAX_SCHEDULED_TRANSACTIONS_PER_BLOCK - tcount);

			// add scheduled transactions to List
			if (al == null) al = new ArrayList<>();
			for (long i = 0; i < take; i++) {
				al.add(trans.get(i));
			}
			// remove schedule entries taken. Delete key if no more entries remaining
			trans = trans.slice(take, numScheduled);
			if (trans.isEmpty()) sched = sched.dissoc(key);
		}
		if (al==null) return this; // nothing to do if no transactions to execute

		// update state with amended schedule
		State state = this.withSchedule(sched);

		// now apply the transactions!
		int n = al.size();
		log.debug("Applying {} scheduled transactions",n);
		for (int i = 0; i < n; i++) {
			AVector<ACell> st = (AVector<ACell>) al.get(i);
			Address origin = (Address) st.get(0);
			AOp<?> op = (AOp<?>) st.get(1);
			Context ctx;
			
			// TODO juice limit? juice refund?
			ctx = Context.createInitial(state, origin, Constants.MAX_TRANSACTION_JUICE);
			ctx = ctx.run(op);
			if (ctx.isExceptional()) {
				// TODO: what to do here? probably ignore
				// we maybe need to think about reporting scheduled results?
				log.trace("Scheduled transaction error: {}", ctx.getExceptional());
			} else {
				state = ctx.getState();
				log.trace("Scheduled transaction succeeded");
			}
		}

		return state;
	}

	private State withSchedule(BlobMap<ABlob, AVector<ACell>> newSchedule) {
		if (schedule == newSchedule) return this;
		return new State(accounts, peers, globals, newSchedule);
	}

	private State withGlobals(AVector<ACell> newGlobals) {
		if (newGlobals == globals) return this;
		return new State(accounts, peers, newGlobals, schedule);
	}

	private BlockResult applyTransactions(Block block) {
		State state = this;
		int blockLength = block.length();
		Result[] results = new Result[blockLength];

		AVector<SignedData<ATransaction>> transactions = block.getTransactions();
		for (int i = 0; i < blockLength; i++) {
			// SECURITY: catch-all exception handler, needs consideration
			try {
				// extract the signed transaction from the block
				SignedData<? extends ATransaction> signed = transactions.get(i);
				
				// execute the transaction using the *latest* state (not necessarily "this")
				Context ctx = state.applyTransaction(signed);

				// record results and state update
				results[i] = Result.fromContext(CVMLong.create(i),ctx);
				state = ctx.getState();
			} catch (Throwable t) {
				String msg= "Unexpected fatal exception applying transaction: "+t.toString();
				results[i] = Result.create(CVMLong.create(i), Strings.create(msg),ErrorCodes.UNEXPECTED);
				log.error(msg,t);
			}
		}

		// TODO: changes for complete block?
		return BlockResult.create(state, results);
	}


	/**
	 * Applies a signed transaction to the State.
	 *
	 * SECURITY: Checks digital signature and correctness of account key
	 *
	 * @return Context containing the updated chain State (may be exceptional)
	 */
	private Context applyTransaction(SignedData<? extends ATransaction> signedTransaction) throws BadSignatureException {
		// Extract transaction, performs signature check
		ATransaction t=signedTransaction.getValue();
		Address addr=t.getOrigin();
		AccountStatus as = getAccount(addr);
		if (as==null) {
			return Context.createFake(this).withError(ErrorCodes.NOBODY,"Transaction for non-existent Account: "+addr);
		} else {
			AccountKey key=as.getAccountKey();
			if (key==null) return Context.createFake(this).withError(ErrorCodes.NOBODY,"Transaction for account that is an Actor: "+addr);
			if (!Utils.equals(key, signedTransaction.getAccountKey())) {
				return Context.createFake(this).withError(ErrorCodes.SIGNATURE,"Signature not valid for Account: "+addr+" expected public key: "+key);
			}
			
			boolean sigValid=signedTransaction.checkSignature();
			if (!sigValid) return Context.createFake(this).withError(ErrorCodes.SIGNATURE, Strings.BAD_SIGNATURE);
		}

		Context ctx=applyTransaction(t);
		return ctx;
	}

	/**
	 * Applies a transaction to the State.
	 *
	 * There are three phases in application of a transaction:
	 * <ol>
	 * <li>Preparation for accounting, with {@link #prepareTransaction(ATransaction) prepareTransaction}</li>
	 * <li>Functional application of the transaction with ATransaction.apply(....)</li>
	 * <li>Completion of accounting, with completeTransaction</li>
	 * </ol>
	 *
	 * SECURITY: Assumes digital signature already checked.
	 *
	 * @param t Transaction to apply
	 * @return Context containing the updated chain State (may be exceptional)
	 */
	public Context applyTransaction(ATransaction t) {
		long juicePrice=getJuicePrice().longValue();
		
		// Create prepared context 
		Context ctx = prepareTransaction(t,juicePrice);
		if (ctx.isExceptional()) {
			// We hit some error while preparing transaction. Possible culprits:
			// - Non-existent Origin account
			// - Bad sequence number
			// Return context with no change, i.e. before executing the transaction
			return ctx;
		}

		State preparedState=ctx.getState();

		// apply transaction. This may result in an error / exceptional result!
		ctx = t.apply(ctx);

		// complete transaction
		// NOTE: completeTransaction handles error cases as well
		ctx = ctx.completeTransaction(preparedState,juicePrice);

		return ctx;
	}

	private Context prepareTransaction(ATransaction t, long juicePrice) {
		Address origin = t.getOrigin();
		
		// Pre-transaction state updates (persisted even if transaction fails)
		AccountStatus account = getAccount(origin);
		if (account == null) {
			return Context.createFake(this).withError(ErrorCodes.NOBODY);
		}

		// Update sequence number for target account
		long sequence=t.getSequence();
		long expectedSequence=account.getSequence()+1;
		if (sequence!=expectedSequence) {
			return Context.createFake(this,origin).withError(ErrorCodes.SEQUENCE, "Sequence = "+sequence+" but expected "+expectedSequence);
		}

		// Create context with juice limit
		long balance=account.getBalance();
		long juiceLimit=Juice.calcAvailable(balance, juicePrice);
		juiceLimit=Math.min(Constants.MAX_TRANSACTION_JUICE,juiceLimit);
		if (juiceLimit<=0) {
			return Context.createFake(this,origin).withJuiceError();
		}
		
		Context ctx = Context.createInitial(this, origin, juiceLimit);
		return ctx;
	}

	@Override
	public boolean isCanonical() {
		return true;
	}

	/**
	 * Computes the weighted stake for each peer. Adds a single entry for the null
	 * key, containing the total stake
	 *
	 * @return Map of Stakes
	 */
	public HashMap<AccountKey, Double> computeStakes() {
		HashMap<AccountKey, Double> hm = new HashMap<>(peers.size());
		Double totalStake = peers.reduceEntries((acc, e) -> {
			double stake = (double) (e.getValue().getTotalStake());
			
			// TODO: potential performance bottleneck from hashing?
			hm.put(RT.ensureAccountKey(e.getKey()), stake);
			return stake + acc;
		}, 0.0);
		hm.put(null, totalStake);
		return hm;
	}

	/**
	 * Updates the Accounts in this State
	 * @param newAccounts New Accounts vector
	 * @return Updated State
	 */
	public State withAccounts(AVector<AccountStatus> newAccounts) {
		if (newAccounts == accounts) return this;
		return create(newAccounts, peers,globals, schedule);
	}

	/**
	 * Returns this state after updating the given account
	 *
	 * @param address Address of Account to update
	 * @param accountStatus New Account Status
	 * @return Updates State, or this state if Account was unchanged
	 * @throws IndexOutOfBoundsException if Address represents an illegal account position
	 */
	public State putAccount(Address address, AccountStatus accountStatus) {
		long ix=address.toExactLong();
		long n=accounts.count();
		if (ix>n) {
			throw new IndexOutOfBoundsException("Trying to add an account beyond accounts array at position: "+ix);
		}

		AVector<AccountStatus> newAccounts;
		if (ix==n) {
			// adding a new account in next position
			newAccounts=accounts.conj(accountStatus);
		} else {
			newAccounts = accounts.assoc(ix, accountStatus);
		}

		return withAccounts(newAccounts);
	}

	/**
	 * Gets the AccountStatus for a given account, or null if not found.
	 *
	 * @param target Address to look up. Must not be null
	 * @return The AccountStatus for the given account, or null.
	 */
	public AccountStatus getAccount(Address target) {
		long ix=target.toExactLong();
		if ((ix<0)||(ix>=accounts.count())) return null;
		return accounts.get(ix);
	}

	/**
	 * Gets the environment for a given account, or null if not found.
	 *
	 * @param addr Address of account to obtain
	 * @return The environment of the given account, or null if not found.
	 */
	public AMap<Symbol, ACell> getEnvironment(Address addr) {
		AccountStatus as = getAccount(addr);
		if (as == null) return null;
		return as.getEnvironment();
	}

	/**
	 * Updates the Peers in this State
	 * @param newPeers New Peer Map
	 * @return Updated State
	 */
	public State withPeers(BlobMap<AccountKey, PeerStatus> newPeers) {
		if (peers == newPeers) return this;
		return create(accounts, newPeers, globals, schedule);
	}

	@Override
	public byte getTag() {
		return Tag.STATE;
	}

	/**
	 * Adds a new Actor Account. The actor will be the last Account in the resulting State, and will
	 * have a default empty Actor environment.
	 *
	 * @return The updated state with the Actor Account added.
	 */
	public State addActor() {
		AccountStatus as = AccountStatus.createActor();
		AVector<AccountStatus> newAccounts = accounts.conj(as);
		return withAccounts(newAccounts);
	}

	/**
	 * Compute the total funds existing within this state.
	 *
	 * Should be constant! 1,000,000,000,000,000,000 in full deployment mode
	 *
	 * @return The total value of all funds
	 */
	public long computeTotalFunds() {
		long total = accounts.reduce((Long acc,AccountStatus as) -> acc + as.getBalance(), (Long)0L);
		total += peers.reduceValues((Long acc, PeerStatus ps) -> acc + ps.getTotalStake(), 0L);
		total += getGlobalFees().longValue();
		total += getGlobalMemoryValue().longValue();
		return total;
	}

	@Override
	public void validate() throws InvalidDataException {
		super.validate();
	}

	@Override
	public void validateCell() throws InvalidDataException {
		accounts.validateCell();
		peers.validateCell();
		globals.validateCell();
		schedule.validateCell();
	}

	/**
	 * Gets the current global timestamp from this state.
	 *
	 * @return The timestamp from this state.
	 */
	public CVMLong getTimestamp() {
		return (CVMLong) globals.get(GLOBAL_TIMESTAMP);
	}

	/**
	 * Gets the current Juice price
	 * 
	 * @return Juice Price
	 */
	public CVMLong getJuicePrice() {
		return (CVMLong) globals.get(GLOBAL_JUICE_PRICE);
	}

	/**
	 * Schedules an operation with the given timestamp and Op in this state
	 *
	 * @param time Timestamp at which to execute the scheduled op
	 *
	 * @param address AccountAddress to schedule op for
	 * @param op Op to execute in schedule
	 * @return The updated State
	 */
	public State scheduleOp(long time, Address address, AOp<?> op) {
		AVector<ACell> v = Vectors.of(address, op);

		LongBlob key = LongBlob.create(time);
		AVector<ACell> list = schedule.get(key);
		if (list == null) {
			list = Vectors.of(v);
		} else {
			list = list.append(v);
		}
		BlobMap<ABlob, AVector<ACell>> newSchedule = schedule.assoc(key, list);

		return this.withSchedule(newSchedule);
	}

	/**
	 * Gets the current schedule data structure for this state
	 *
	 * @return The schedule data structure.
	 */
	public BlobMap<ABlob, AVector<ACell>> getSchedule() {
		return schedule;
	}

	/**
	 * Gets the Global Fees accumulated in the State
	 * @return Global Fees
	 */
	public CVMLong getGlobalFees() {
		return (CVMLong) globals.get(GLOBAL_FEES);
	}

	/**
	 * Update Global Fees
	 * @param newFees New Fees
	 * @return Updated State
	 */
	public State withGlobalFees(CVMLong newFees) {
		return withGlobals(globals.assoc(GLOBAL_FEES,newFees));
	}


	/**
	 * Gets the PeerStatus record for the given Address, or null if it does not
	 * exist
	 *
	 * @param peerAddress Address of Peer to check
	 * @return PeerStatus
	 */
	public PeerStatus getPeer(AccountKey peerAddress) {
		return getPeers().get(peerAddress);
	}

	/**
	 * Updates the specified peer status
	 *
	 * @param peerKey Peer Key
	 * @param updatedPeer New Peer Status
	 * @return Updated state
	 */
	public State withPeer(AccountKey peerKey, PeerStatus updatedPeer) {
		return withPeers(peers.assoc(peerKey, updatedPeer));
	}

	/**
	 * Gets the next available address for allocation, i.e. the lowest Address
	 * that does not yet exist in this State.
	 * 
	 * @return Next address available
	 */
	public Address nextAddress() {
		return Address.create(accounts.count());
	}

	/**
	 * Look up an Address from CNS
	 * @param name CNS name String
	 * @return Address from CNS, or null if not found
	 */
	public Address lookupCNS(String name) {
		Context ctx=Context.createFake(this);
		return (Address) ctx.lookupCNS(name).getResult();
	}

	/**
	 * Gets globals.
	 *
	 * @return Vector of global values
	 */
	public AVector<ACell> getGlobals() {
		return globals;
	}

	/**
	 * Updates the State with a new timestamp
	 * @param timestamp New timestamp
	 * @return Updated State
	 */
	public State withTimestamp(long timestamp) {
		return withGlobals(globals.assoc(GLOBAL_TIMESTAMP, CVMLong.create(timestamp)));
	}
	
	@Override 
	public boolean equals(ACell a) {
		if (!(a instanceof State)) return false;
		State as=(State)a;
		return equals(as);
	}
	
	/**
	 * Tests if this State is equal to another
	 * @param a State to compare with
	 * @return true if equal, false otherwise
	 */
	public boolean equals(State a) {
		if (this == a) return true; // important optimisation for e.g. hashmap equality
		if (a == null) return false;
		Hash h=this.cachedHash();
		if (h!=null) {
			Hash ha=a.cachedHash();
			if (ha!=null) return Utils.equals(h, ha);
		}
		
		if (!(Utils.equals(accounts, a.accounts))) return false;
		if (!(Utils.equals(globals, a.globals))) return false;
		if (!(Utils.equals(peers, a.peers))) return false;
		if (!(Utils.equals(schedule, a.schedule))) return false;
		return true;
	}

	@Override
	public RecordFormat getFormat() {
		return FORMAT;
	}

	public CVMLong getGlobalMemoryValue() {
		return (CVMLong)(globals.get(GLOBAL_MEMORY_CVX));
	}

	public CVMLong getGlobalMemoryPool() {
		return (CVMLong)(globals.get(GLOBAL_MEMORY_MEM));
	}

	public State updateMemoryPool(long cvx, long mem) {
		AVector<ACell> r=globals;
		r=r.assoc(GLOBAL_MEMORY_CVX, CVMLong.create(cvx));
		r=r.assoc(GLOBAL_MEMORY_MEM, CVMLong.create(mem));
		return withGlobals(r);
	}

}
