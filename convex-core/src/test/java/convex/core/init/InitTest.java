package convex.core.init;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

import convex.core.Constants;
import convex.core.State;
import convex.core.crypto.AKeyPair;
import convex.core.data.AccountKey;
import convex.core.data.AccountStatus;
import convex.core.data.Address;
import convex.core.data.Hash;
import convex.core.data.Ref;
import convex.core.data.Refs;
import convex.core.data.prim.CVMLong;
import convex.core.exceptions.InvalidDataException;
import convex.core.lang.ACVMTest;

/**
 * Tests for Init functionality
 *
 * Also includes static State instances for Testing
 */
public class InitTest extends ACVMTest {
	
	public static final AKeyPair[] KEYPAIRS = new AKeyPair[] {
			AKeyPair.createSeeded(2),
			AKeyPair.createSeeded(3),
			AKeyPair.createSeeded(5),
			AKeyPair.createSeeded(7),
			AKeyPair.createSeeded(11),
			AKeyPair.createSeeded(13),
			AKeyPair.createSeeded(17),
			AKeyPair.createSeeded(19),
	};
	
	public static ArrayList<AKeyPair> PEER_KEYPAIRS=(ArrayList<AKeyPair>) Arrays.asList(KEYPAIRS).stream().collect(Collectors.toList());
	public static ArrayList<AccountKey> PEER_KEYS=(ArrayList<AccountKey>) Arrays.asList(KEYPAIRS).stream().map(kp->kp.getAccountKey()).collect(Collectors.toList());

	public static final AKeyPair FIRST_PEER_KEYPAIR = KEYPAIRS[0];
	public static final AccountKey FIRST_PEER_KEY = FIRST_PEER_KEYPAIR.getAccountKey();
	
	public static final AKeyPair HERO_KEYPAIR = KEYPAIRS[0];
	public static final AKeyPair VILLAIN_KEYPAIR = KEYPAIRS[1];
	
	public static final AccountKey HERO_KEY = HERO_KEYPAIR.getAccountKey();

	
	/**
	 * Standard Genesis state used for testing
	 */
	public static final State STATE= createState();
	
	/**
	 * Base state (without libraries)
	 */
	public static final State BASE = Init.createBaseState(PEER_KEYS);


	public static State createState() {
		return Init.createState(PEER_KEYS);
	}
	
	public static Address HERO=Init.getGenesisAddress();
	public static Address VILLAIN=Init.getGenesisPeerAddress(1);
	
	public static final Address FIRST_PEER_ADDRESS = Init.getGenesisPeerAddress(0);


	protected InitTest() {
		super(STATE);
	}

	@Test
	public void testDeploy() {
		// CNS resolution for standard libraries
		assertNotNull(evalA("(call *registry* (cns-resolve 'asset.box))"));
		assertNotNull(evalA("(call *registry* (cns-resolve 'asset.box.actor))"));
		assertNotNull(evalA("(call *registry* (cns-resolve 'asset.nft.simple))"));
		assertNotNull(evalA("(call *registry* (cns-resolve 'asset.nft.tokens))"));
		assertNotNull(evalA("(call *registry* (cns-resolve 'convex.asset))"));
		assertNotNull(evalA("(call *registry* (cns-resolve 'convex.fungible))"));
		assertNotNull(evalA("(call *registry* (cns-resolve 'convex.play))"));
		assertNotNull(evalA("(call *registry* (cns-resolve 'convex.trusted-oracle.actor))"));
		assertNotNull(evalA("(call *registry* (cns-resolve 'convex.oracle))"));
		assertNotNull(evalA("(call *registry* (cns-resolve 'torus.exchange))"));

		assertEquals(Init.CORE_ADDRESS, eval("(call *registry* (cns-resolve 'convex.core))"));
		assertEquals(Init.REGISTRY_ADDRESS, eval("(call *registry* (cns-resolve 'convex.registry))"));
		assertEquals(Init.TRUST_ADDRESS, eval("(call *registry* (cns-resolve 'convex.trust))"));
	}

	@Test
	public void testInitState() throws InvalidDataException {
		STATE.validate();
		assertEquals(0,context().getDepth());
		assertNull(context().getResult());

		assertEquals(Constants.MAX_SUPPLY, STATE.computeTotalFunds());
	}

	@Test
	public void testMemoryExchange() {
		CVMLong mem=STATE.getGlobalMemoryPool();
		CVMLong cvx=STATE.getGlobalMemoryValue();
		assertEquals(Constants.INITIAL_MEMORY_POOL,mem.longValue());
		assertEquals(Constants.INITIAL_MEMORY_POOL*Constants.INITIAL_MEMORY_PRICE,cvx.longValue());
	}

	@Test
	public void testHero() {
 		AccountStatus as=STATE.getAccount(HERO);
		assertNotNull(as);
		assertEquals(Constants.INITIAL_ACCOUNT_ALLOWANCE,as.getMemory());
	}
	
	@Test
	public void testVILLAIN() {
 		AccountStatus as=STATE.getAccount(VILLAIN);
		assertNotNull(as);
		assertEquals(Constants.INITIAL_ACCOUNT_ALLOWANCE,as.getMemory());
		assertNotEquals(HERO,VILLAIN);
	}
	
	@Test
	public void testInitRef() {
		State s=Init.createBaseState(PEER_KEYS);
		Ref<State> sr=s.getRef();
		
		Refs.RefTreeStats s1s=Refs.getRefTreeStats(sr);
		
		// TODO: need to work out why something already persisted,
		// Seems to be in Core shared instance? "%2, 1, 1"
		//ACell[] c=new ACell[2];
		//Refs.visitAllRefs(sr, r->{
		//	ACell v=r.getValue();
		//	if (r.isPersisted()) {
		//		throw new Error("Persisted: "+v.getType()+" = "+v+" after "+c[0]+","+c[1]);
		//	}
		//	c[0]=c[1];
		//	c[1]=v;
		//});
		//assertEquals(0L,s1s.persisted);
		
		assertSame(s1s.root,sr);
		
		HashSet<Hash> hs=new HashSet<>();
		sr.findMissing(hs, 100);
		assertTrue(hs.isEmpty());
	}

}
