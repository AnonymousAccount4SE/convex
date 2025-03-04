package convex.core;

import java.nio.charset.StandardCharsets;
import java.time.Instant;

import convex.core.data.ACell;
import convex.core.data.AString;
import convex.core.data.AVector;
import convex.core.data.Strings;
import convex.core.data.Vectors;
import convex.core.data.prim.CVMBool;

/**
 * Static class for global configuration constants that affect protocol
 * behaviour
 */
public class Constants {

	/**
	 * Limit of scheduled transactions run in a single Block
	 */
	public static final long MAX_SCHEDULED_TRANSACTIONS_PER_BLOCK = 100;

	/**
	 * Threshold of stake required to propose consensus
	 */
	public static final double PROPOSAL_THRESHOLD = 0.67;

	/**
	 * Threshold of stake required to confirm consensus
	 */
	public static final double CONSENSUS_THRESHOLD = 0.67;

	/**
	 * Initial timestamp for new States
	 */
	public static final long INITIAL_TIMESTAMP = Instant.parse("2020-02-02T00:20:20.0202Z").toEpochMilli();

	/**
	 * Juice price in the initial Genesis State
	 */
	public static final long INITIAL_JUICE_PRICE = 2L;

	/**
	 * Initial memory Pool of 1gb
	 */
	public static final long INITIAL_MEMORY_POOL = 1000000000L;

	/**
	 * Initial memory price per byte
	 */
	public static final long INITIAL_MEMORY_PRICE = 1000L;
	
	/**
	 * Memory Pool of growth increment 1mn
	 */
	public static final long MEMORY_POOL_GROWTH = 1000000L;

	/**
	 * Memory Pool of growth interval (once per day)
	 */
	public static final long MEMORY_POOL_GROWTH_INTERVAL = 1000L*24*3600;

	/**
	 * Max juice allowable during execution of a single transaction.
	 */
	public static final long MAX_TRANSACTION_JUICE = 10000000;
	
	/**
	 * Max transactions in a legal Block.
	 */
	public static final int MAX_TRANSACTIONS_PER_BLOCK = 1024;

	/**
	 * Constant to set deletion of Etch temporary files on exit. Probably should be true, unless you want to dubug temp files.
	 */
	public static final boolean ETCH_DELETE_TEMP_ON_EXIT = true;

	/**
	 * Sequence number used for any new account
	 */
	public static final long INITIAL_SEQUENCE = 0;

	/**
	 * Size in bytes of constant overhead applied per non-embedded Cell in memory accounting
	 */
	public static final long MEMORY_OVERHEAD = 64;

	/**
	 * Allowance for initial user / peer accounts
	 */
	public static final long INITIAL_ACCOUNT_ALLOWANCE = 10000000;

	/**
	 * Maximum supply of Convex Coins set at protocol level
	 */
	public static final long MAX_SUPPLY = Coin.SUPPLY;

	/**
	 * Maximum CVM execution depth
	 */
	public static final int MAX_DEPTH = 256;

	/**
	 * Initial global values for a new State
	 */
	public static final AVector<ACell> INITIAL_GLOBALS = Vectors.of(
			Constants.INITIAL_TIMESTAMP, 0L, Constants.INITIAL_JUICE_PRICE,Constants.INITIAL_MEMORY_POOL,Constants.INITIAL_MEMORY_POOL*Constants.INITIAL_MEMORY_PRICE);

	/**
	 * Maximum length of a symbolic name in bytes (keywords and symbols)
	 *
	 * Note: Chosen so that small qualified symbolic values are always embedded
	 */
	public static final int MAX_NAME_LENGTH = 128;

	/**
	 * Value used to indicate inclusion of a key in a Set. Must be a singleton instance
	 */
	public static final CVMBool SET_INCLUDED = CVMBool.TRUE;

	/**
	 * Value used to indicate exclusion of a key from a Set. Must be a singleton instance
	 */
	public static final CVMBool SET_EXCLUDED = CVMBool.FALSE;

	/**
	 * Length for public keys
	 */
	public static final int KEY_LENGTH = 32;

	/**
	 * Length for Hash values
	 */
	public static final int HASH_LENGTH = 32;


	/**
	 * Minimum stake for a Peer to be considered by other Peers in consensus
	 */
	public static final long MINIMUM_EFFECTIVE_STAKE = Coin.GOLD*1;




	/**
	 * Option for static compilation support. Set to true for static inlines on core
	 */
	// TODO: Should ultimately be true for production usage
	public static final boolean OPT_STATIC = true;

	/**
	 * Char to represent bad Unicode characters in printing
	 */
	public static final char BAD_CHARACTER = '\uFFFD';
	public static final byte[] BAD_CHARACTER_BYTES = new byte[] {(byte) 0xff, (byte) 0xfd };
	public static final String BAD_CHARACTER_STRING = new String(BAD_CHARACTER_BYTES, StandardCharsets.UTF_8);
	public static final byte[] BAD_CHARACTER_UTF = BAD_CHARACTER_STRING.getBytes(StandardCharsets.UTF_8);

	/**
	 * Default print limit
	 */
	public static final long PRINT_LIMIT = 4096;

	public static final AString PRINT_EXCEEDED_MESSAGE = Strings.create("<<Print limit exceeded>>");

	/**
	 * Default port for Convex Peers
	 */
	public static final int DEFAULT_PEER_PORT = 18888;

	/**
	 * Minimum milliseconds to retain a proposal before switching
	 */
	public static final long KEEP_PROPOSAL_TIME = 100;

	/**
	 * Number of consensus levels (blocks, proposed, consensus, finality)
	 */
	public static final int CONSENSUS_LEVELS = 4;

	public static final int CONSENSUS_LEVEL_PROPOSAL = CONSENSUS_LEVELS-3;
	public static final int CONSENSUS_LEVEL_CONSENSUS = CONSENSUS_LEVELS-2;
	public static final int CONSENSUS_LEVEL_FINALITY = CONSENSUS_LEVELS-1;

	public static final boolean ENABLE_FORK_RECOVERY = false;

	public static final long INITIAL_PEER_TIMESTAMP = -1L;


}
