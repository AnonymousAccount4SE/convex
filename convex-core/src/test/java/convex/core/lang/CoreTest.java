package convex.core.lang;

import static convex.test.Assertions.assertArgumentError;
import static convex.test.Assertions.assertArityError;
import static convex.test.Assertions.assertAssertError;
import static convex.test.Assertions.assertBoundsError;
import static convex.test.Assertions.assertCVMEquals;
import static convex.test.Assertions.assertCastError;
import static convex.test.Assertions.assertCompileError;
import static convex.test.Assertions.assertDepthError;
import static convex.test.Assertions.assertError;
import static convex.test.Assertions.assertFundsError;
import static convex.test.Assertions.assertJuiceError;
import static convex.test.Assertions.assertMemoryError;
import static convex.test.Assertions.assertNobodyError;
import static convex.test.Assertions.assertNotError;
import static convex.test.Assertions.assertStateError;
import static convex.test.Assertions.assertTrustError;
import static convex.test.Assertions.assertUndeclaredError;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;

import org.junit.jupiter.api.Test;

import convex.core.Block;
import convex.core.BlockResult;
import convex.core.Constants;
import convex.core.ErrorCodes;
import convex.core.State;
import convex.core.crypto.AKeyPair;
import convex.core.data.ABlob;
import convex.core.data.ACell;
import convex.core.data.AHashMap;
import convex.core.data.ASet;
import convex.core.data.AVector;
import convex.core.data.AccountKey;
import convex.core.data.AccountStatus;
import convex.core.data.Address;
import convex.core.data.Blob;
import convex.core.data.BlobMap;
import convex.core.data.BlobMaps;
import convex.core.data.Format;
import convex.core.data.Hash;
import convex.core.data.Keyword;
import convex.core.data.Keywords;
import convex.core.data.List;
import convex.core.data.Lists;
import convex.core.data.MapEntry;
import convex.core.data.Maps;
import convex.core.data.Sets;
import convex.core.data.SignedData;
import convex.core.data.Strings;
import convex.core.data.Symbol;
import convex.core.data.Syntax;
import convex.core.data.Vectors;
import convex.core.data.prim.CVMBigInteger;
import convex.core.data.prim.CVMBool;
import convex.core.data.prim.CVMChar;
import convex.core.data.prim.CVMDouble;
import convex.core.data.prim.CVMLong;
import convex.core.exceptions.BadFormatException;
import convex.core.exceptions.BadSignatureException;
import convex.core.exceptions.InvalidDataException;
import convex.core.init.Init;
import convex.core.init.InitTest;
import convex.core.lang.impl.CorePred;
import convex.core.lang.impl.ICoreDef;
import convex.core.lang.ops.Constant;
import convex.core.lang.ops.Do;
import convex.core.lang.ops.Invoke;
import convex.core.lang.ops.Lookup;
import convex.core.lang.ops.Special;
import convex.test.Samples;

/**
 * Test class for core functions in the initial environment. Mainly targeted example based tests,
 * should cover all expected edge cases.
 *
 * The state setup included core libraries such as the registry and trust monitors
 * which require integration with core language features.
 *
 * Needs completely deterministic, fully specified behaviour if we want
 * consistent results so we need to do a lot of negative testing here.
 */
public class CoreTest extends ACVMTest {


	protected CoreTest() throws IOException {
		super(InitTest.BASE);
	}

	@Test
	public void testAddress() {
		Address a = HERO;
		assertEquals(a, eval("(address \"" + a.toHexString() + "\")"));
		assertEquals(a, eval("(address 0x" + a.toHexString() + ")"));
		assertEquals(a, eval("(address (address \"" + a.toHexString() + "\"))"));
		assertEquals(a, eval("(address (blob \"" + a.toHexString() + "\"))"));
		assertEquals(a, eval("(address "+a.toExactLong()+")"));

		// bad arities
		assertArityError(step("(address 1 2)"));
		assertArityError(step("(address)"));

		// invalid address lengths - not a cast error since argument types (in general) are valid
		assertArgumentError(step("(address \"1234abcd\")"));
		assertArgumentError(step("(address 0x1234abcd)"));

		// invalid conversions
		assertCastError(step("(address :foo)"));
		assertCastError(step("(address nil)"));
	}

	@Test
	public void testBlob() {
		Blob b = Blob.fromHex("cafebabe");
		assertEquals(b, eval("(blob \"Cafebabe\")"));
		assertEquals(b, eval("(blob (blob \"cafebabe\"))"));

		assertEquals("cafebabe", evalS("(str (blob \"Cafebabe\"))"));

		assertSame(eval("0x"),eval("(blob (str))")); // blob literal

		assertEquals(eval("*address*"),eval("(address (blob *address*))"));
		
		// Blob from a long
		assertEquals(eval("0x0000000000001234"),eval("(blob (long \\u1234))")); // blob literal
		assertEquals(eval("0xffffffffffffffff"),eval("(blob -1)")); 

		// Blob from a bigint
		assertEquals(eval("0x00ffffffffffffffff"),eval("(blob 18446744073709551615)")); 
		assertEquals(eval("0xff0000000000000000"),eval("(blob -18446744073709551616)")); 
		
		// Address converts to regular Blob
		assertEquals(eval("0x0000000000000013"),eval("(blob #19)")); 
		
		// Account key should be a Blob
		assertEquals(eval("*key*"),eval("(blob *key*)"));
		
		// Long converts to blob and back
		assertTrue(evalB("(= 0xffffffffffffffff (blob -1))"));
		assertTrue(evalB("(= -1 (long (blob -1)))"));

		// round trip back to Blob
		assertTrue(evalB("(blob? (blob (hash (encoding [1 2 3]))))"));

		assertArityError(step("(blob 1 2)"));
		assertArityError(step("(blob)"));

		assertCastError(step("(blob \"f\")")); // odd length hex string bug #54 special case
		assertCastError(step("(blob :foo)"));
		assertCastError(step("(blob nil)"));
	}

	@Test
	public void testByte() {
		assertSame(CVMLong.create(0x01), eval("(byte 1)"));
		assertSame(CVMLong.create(0xff), eval("(byte 255)"));
		assertSame(CVMLong.create(0xff), eval("(byte -1)"));
		assertSame(CVMLong.create(0xff), eval("(byte (byte -1))"));
		
		assertSame(CVMLong.create(0x00), eval("(byte 9223372036854775808)"));

		// Byte extracts last byte from Blob a default (similar behaviour to Long)
		assertSame(CVMLong.create(0xff), eval("(byte 0xff)"));
		assertSame(CVMLong.create(0xff), eval("(byte 0xeeff)"));
		
		assertCastError(step("(byte nil)"));
		assertCastError(step("(byte :foo)"));
		assertCastError(step("(byte 0x)")); // can't cast empty blob
		
		// Shouldn't try to convert an Address, see #431
		assertCastError(step("(byte #13)"));

		assertArityError(step("(byte)"));
		assertArityError(step("(byte nil nil)")); // arity before cast
	}
	
	@Test
	public void testBitAnd() {
		assertCVMEquals(0x0f000f000f000f00l, eval("(bit-and (long 0xff00ff00ff00ff00) (long 0x0ff00ff00ff00ff0))"));
		assertCVMEquals(0x0001, eval("(bit-and (long 0xffff) (byte 1))"));

		assertCastError(step("(bit-and nil 1)"));
		assertCastError(step("(bit-and 20 :foo)"));

		assertArityError(step("(bit-and)"));
		assertArityError(step("(bit-and 0xFF)")); // arity before cast
	}

	@Test
	public void testBitOr() {
		assertCVMEquals(0xfff0fff0fff0fff0l, eval("(bit-or (long 0xff00ff00ff00ff00) (long 0x0ff00ff00ff00ff0))"));
		assertCVMEquals(0xff01, eval("(bit-or (long 0xff00) (byte 1))"));

		assertCastError(step("(bit-or nil 1)"));
		assertCastError(step("(bit-or 20 :foo)"));

		assertArityError(step("(bit-or)"));
		assertArityError(step("(bit-or 0xFF)")); // arity before cast
	}
	
	@Test
	public void testBitNot() {
		assertCVMEquals(0x00ff00ff00ff00ffl, eval("(bit-not (long 0xff00ff00ff00ff00))"));
		assertCVMEquals(0, eval("(bit-not (long 0xffffffffffffffff))"));

		assertCastError(step("(bit-not nil)"));
		assertCastError(step("(bit-not :foo)"));

		assertArityError(step("(bit-not)"));
		assertArityError(step("(bit-not :foo :bar)")); // arity before cast
	}
	
	
	@Test
	public void testDoc() {
		assertEquals(42L, evalL("(do (def foo ^{:doc 42} nil) (doc foo))"));
		assertEquals(42L, evalL("(do (def a (deploy '(def foo ^{:doc 42} nil))) (doc a/foo))"));
	}

	@Test
	public void testLet() {
		assertNull(eval("(let[])"));

		assertCompileError(step("(let ['(a b) '(1 2)] b)"));

		// badly formed lets - Issue #80 related
		assertCompileError(step("(let)"));
		assertCompileError(step("(let :foo)"));
		assertCompileError(step("(let [a])"));
	}
	
	@Test
	public void testLetDestructuring() {
		assertNull(eval("(let[[] []])"));
		assertSame(Vectors.empty(),eval("(let [[& a] []] a)"));
		
		// nil treated as empty sequence
		assertSame(Vectors.empty(),eval("(let [[& a] nil] a)"));
		
		assertEquals(2L,evalL("(let [[a b] [1 2]] b)"));
		assertEquals(2L,evalL("(let [[a b] '(1 2)] b)"));

		assertCastError(step("(let [[a b] :foo] b)"));

		assertArityError(step("(let [[a b] nil] b)"));
		assertArityError(step("(let [[a b] [1]] b)"));

		// See issue #62
		assertArityError(step("(let [[a b & c d] [1 2]] c)"));
	}

	@Test
	public void testGet() {
		assertEquals(2L, evalL("(get {1 2} 1)"));
		assertEquals(4L, evalL("(get {1 2 3 4} 3)"));
		assertEquals(4L, evalL("(get {1 2 3 4} 3 7)"));
		assertNull(eval("(get {1 2} 2)")); // null if not present
		assertEquals(7L, evalL("(get {1 2 3 4} 5 7)")); // fallback arg

		assertSame(CVMBool.TRUE, eval("(get #{1 2} 1)"));
		assertSame(CVMBool.TRUE, eval("(get #{1 2} 2)"));
		assertSame(CVMBool.FALSE, eval("(get #{1 2} 3)")); // null if not present
		assertEquals(4L, evalL("(get #{1 2} 3 4)")); // fallback

		assertEquals(2L, evalL("(get [1 2 3] 1)"));
		assertEquals(2L, evalL("(get [1 2 3] 1 7)"));
		assertEquals(7L, evalL("(get [1 2 3] 4 7)"));
		assertEquals(7L, evalL("(get [1 2] nil 7)"));
		assertEquals(7L, evalL("(get [1 2] -5 7)"));
		assertNull(eval("(get [1 2] :foo)"));
		assertNull(eval("(get [1 2] 10)"));
		assertNull(eval("(get [1 2] -1)"));
		assertNull(eval("(get [1 2] 1.0)"));

		assertEquals(2L,evalL("(get [1 2 3] (byte 1))")); 
		assertNull(eval("(get [1 2 3] 18446744073709551616)")); // check we don't overflow to zero

		assertNull(eval("(get nil nil)"));
		assertNull(eval("(get nil 10)"));
		assertEquals(3L, evalL("(get nil 2 3)"));
		assertEquals(3L, evalL("(get nil nil 3)"));

		assertArityError(step("(get 1)")); // arity > cast
		assertArityError(step("(get)"));
		assertArityError(step("(get 1 2 3 4)"));

		assertCastError(step("(get \"ab\" 3)")); // Strings are not indexed

		assertCastError(step("(get 1 2 3)")); // 3 arg could work, so cast error on 1st arg
		assertCastError(step("(get 1 1)")); // 2 arg could work, so cast error on 1st arg
	}

	@Test
	public void testGetIn() {
		assertEquals(2L, evalL("(get-in {1 2} [1])"));
		assertEquals(4L, evalL("(get-in {1 {2 4} 3 5} [1 2])"));
		assertEquals(1L, evalL("(get-in #{1 2} [1])"));
		assertEquals(2L, evalL("(get-in [1 2 3] [1])"));
		assertEquals(2L, evalL("(get-in [1 2 3] [1] :foo)"));
		assertEquals(3L, evalL("(get-in [1 2 3] '(2))"));
		assertEquals(3L, evalL("(get-in (list 1 2 3) [2])"));
		assertEquals(4L, evalL("(get-in [1 2 {:foo 4} 3 5] [2 :foo])"));

		// special case: don't coerce to collection if empty sequence of keys
		// so non-collection value may be used safely
		assertEquals(3L, evalL("(get-in 3 [])"));

		assertEquals(Maps.of(1L, 2L), eval("(get-in {1 2} nil)"));
		assertEquals(Maps.of(1L, 2L), eval("(get-in {1 2} [])"));
		assertEquals(Vectors.empty(), eval("(get-in [] [])"));
		assertEquals(Lists.empty(), eval("(get-in (list) nil)"));

		assertEquals(Keywords.FOO, eval("(get-in {1 2} [3] :foo)"));
		assertEquals(Keywords.FOO, eval("(get-in nil [3] :foo)"));

		assertNull(eval("(get-in nil nil)"));
		assertNull(eval("(get-in [1 2 3] [:foo])"));
		assertNull(eval("(get-in nil [])"));
		assertNull(eval("(get-in nil [1 2])"));
		assertNull(eval("(get-in #{} [1 2 3])"));

		assertArityError(step("(get-in 1)")); // arity > cast
		assertArityError(step("(get-in 1 2 3 4)")); // arity > cast

		assertCastError(step("(get-in 1 2 3)"));
		assertCastError(step("(get-in 1 [1])"));
		assertCastError(step("(get-in [1] [0 2])"));
		assertCastError(step("(get-in 1 {1 2})")); // keys not a sequence

		assertCastError(step("(get-in [1] 1)"));
	}

	@Test
	public void testLong() {
		assertCVMEquals(1L, eval("(long 1)"));
		assertEquals(128L, evalL("(long (byte 128))"));
		assertEquals(97L, evalL("(long \\a)"));
		assertEquals(2147483648L, evalL("(long 2147483648)"));

		// Blob casts treat blob as extended long bits (zero extended if needed)
		assertEquals(4096L, evalL("(long 0x1000)"));
		assertEquals(255L, evalL("(long 0xff)"));
		assertEquals(4294967295L, evalL("(long 0xffffffff)"));
		assertEquals(0xff00000050l, evalL("(long 0xff00000050)"));
		assertEquals(-1L, evalL("(long 0xffffffffffffffff)"));
		assertEquals(255L, evalL("(long 0xff00000000000000ff)")); // only taking last 8 bytes
		assertEquals(-1L, evalL("(long 0xcafebabeffffffffffffffff)")); // interpret as big endian big integer

		// Currently we allow bools to explicitly cast to longs like this. TODO: maybe reconsider?
		assertEquals(1L, evalL("(long true)"));
		assertEquals(0L, evalL("(long false)"));
		
		// Address casts to equivalent Long value. See #431
		assertEquals(1L, evalL("(long #1)"));
		assertEquals(999L, evalL("(long #999)"));
		
		// Doubles may cast to longs - currently semantics as in Java primitive conversion
		assertEquals(CVMLong.ZERO,eval("(long 0.0)"));
		assertEquals(10L, evalL("(long 10.999)")); // note rounding towards zero
		assertEquals(CVMLong.MINUS_ONE,eval("(long -1.9)")); // note rounding towards zero
		assertEquals(CVMLong.MAX_VALUE,eval("(long 9223372036854775807.0)")); // actual max value
		assertEquals(CVMLong.MAX_VALUE,eval("(long 9223372036854775809.0)")); // above max value
		
		// Cast errors on non-finite doubles
		assertCastError(step("(long ##NaN)"));
		assertCastError(step("(long ##Inf)"));
		assertCastError(step("(long ##-Inf)"));

		assertArityError(step("(long)"));
		assertArityError(step("(long 1 2)")); 
		assertArityError(step("(long nil nil)")); // arity before cast
		assertCastError(step("(long nil)"));
		assertCastError(step("(long [])"));
		assertCastError(step("(long :foo)"));
		
		// Long limits and overflow
		assertEquals(Long.MAX_VALUE,evalL("(long 9223372036854775807)"));
		assertEquals(Long.MIN_VALUE,evalL("(long -9223372036854775808)"));
		assertCastError(step("(long 18446744073709551616)"));
		assertCastError(step("(long 9223372036854775808)"));
		assertCastError(step("(long -9223372036854775809)"));

	}
	
	@Test
	public void testInt() {
		assertCVMEquals(1L, eval("(int 1)"));
		assertEquals(128L, evalL("(int (byte 128))"));
		assertEquals(97L, evalL("(int \\a)"));
		assertEquals(2147483648L, evalL("(int 2147483648)"));

		// Blob casts treat blob as extended long bits (zero extended if needed)
		assertEquals(4096L, evalL("(int 0x1000)"));
		assertEquals(255L, evalL("(int 0xff)"));
		assertEquals(4294967295L, evalL("(int 0xffffffff)"));
		assertEquals(0xff00000050l, evalL("(int 0xff00000050)"));

		// Currently we allow bools to explicitly cast to longs like this. TODO: maybe reconsider?
		assertEquals(1L, evalL("(int true)"));
		assertEquals(0L, evalL("(int false)"));
		
		// Address casts to equivalent Long value. See #431
		assertEquals(1L, evalL("(long #1)"));
		assertEquals(999L, evalL("(long #999)"));
		
		// Doubles may cast to longs - currently semantics as in Java primitive conversion
		assertEquals(CVMLong.ZERO,eval("(int 0.0)"));
		assertEquals(10L, evalL("(int 10.999)")); // note rounding towards zero
		assertEquals(CVMLong.MINUS_ONE,eval("(int -1.9)")); // note rounding towards zero
		assertEquals(CVMLong.MAX_VALUE,eval("(int 9223372036854775807.0)")); // actual max value
		assertEquals(CVMLong.MAX_VALUE,eval("(int 9223372036854775809.0)")); // above max value
		
		assertCastError(step("(int ##NaN)"));
		assertCastError(step("(int ##Inf)"));
		assertCastError(step("(int ##-Inf)"));

		assertArityError(step("(int)"));
		assertArityError(step("(int 1 2)")); 
		assertArityError(step("(int nil nil)")); // arity before cast
		assertCastError(step("(int nil)"));
		assertCastError(step("(int [])"));
		assertCastError(step("(int :foo)"));
		
		// Long overflow and truncation
		assertEquals(Long.MAX_VALUE,evalL("(int 9223372036854775807)"));
		assertEquals(Long.MIN_VALUE,evalL("(int -9223372036854775808)"));
		assertEquals(CVMBigInteger.MIN_POSITIVE,eval("(int 9223372036854775808)"));
	}


	@Test
	public void testChar() {
		assertEquals(CVMChar.create('z'), eval("\\z"));
		
		assertCVMEquals('a', eval("\\a"));
		assertCVMEquals('a', eval("(char 97)"));
		assertCVMEquals('a', eval("(nth \"bar\" 1)"));
		
		// Out of Unicode range
		assertArgumentError(step("(char 12345678)"));
		assertArgumentError(step("(char (long 0xff00000050))"));
		
		// Bad types
		assertCastError(step("(char false)"));
		assertCastError(step("(char nil)"));
		assertCastError(step("(char {})"));
		
		assertArityError(step("(char)"));
		assertArityError(step("(char nil nil)")); // arity before cast

	}
	
	@Test
	public void testBoolean() {
		// test precise values
		assertSame(CVMBool.TRUE, eval("(boolean 1)"));
		assertSame(CVMBool.FALSE, eval("(boolean nil)"));

		// nil and false should be falsey
		assertFalse(evalB("(boolean false)"));
		assertFalse(evalB("(boolean nil)"));

		// anything else should be truthy
		assertTrue(evalB("(boolean true)"));
		assertTrue(evalB("(boolean [])"));
		assertTrue(evalB("(boolean #{})"));
		assertTrue(evalB("(boolean 1)"));
		assertTrue(evalB("(boolean :foo)"));

		assertArityError(step("(boolean)"));
		assertArityError(step("(boolean 1 2)"));
	}

	@Test public void testIf() {
		// basic branching
		assertEquals(1L,evalL("(if true 1 2)"));
		assertEquals(2L,evalL("(if false 1 2)"));

		// expressions
		assertEquals(6L,evalL("(if (= 1 1) (* 2 3) (* 3 4))"));
		assertEquals(12L,evalL("(if (nil? false) (* 2 3) (* 3 4))"));

		// Missing false branch
		assertNull(eval("(if false 1)"));
		assertEquals(CVMLong.ONE,eval("(if true 1)"));

		// These are arity errors to prevent obvious mistakes. Use cond if you want arbitrary arity!
		assertArityError(step("(if)"));
		assertArityError(step("(if 1)"));
		assertArityError(step("(if 1 2 3 4)"));
	}
	
	@Test public void testCond() {
		// basic matches
		assertEquals(1L,evalL("(cond true 1)"));
		assertEquals(1L,evalL("(cond true 1 true 2 true 3)"));
		assertEquals(2L,evalL("(cond false 1 true 2 true 3)"));
		assertEquals(3L,evalL("(cond false 1 false 2 true 3)"));
		
		// fallthroughs not taken
		assertEquals(1L,evalL("(cond true 1 2)"));
		assertEquals(2L,evalL("(cond false 1 true 2 3)"));
		
		// fallthroughs to default value
		assertEquals(2L,evalL("(cond false 1 2)"));
		assertEquals(3L,evalL("(cond false 1 false 2 3)"));

		// expressions
		assertEquals(6L,evalL("(cond (= 1 1) (* 2 3) (* 3 4))"));
		assertEquals(12L,evalL("(cond (nil? false) (* 2 3) (* 3 4))"));

		// Missing false branch
		assertNull(eval("(cond false 1)"));
		assertEquals(CVMLong.ONE,eval("(cond true 1)"));
		
		// No expressions, fall through to null
		assertNull(eval("(cond)"));
	}

	@Test
	public void testEquals() {
		assertTrue(evalB("(= \\a)"));
		assertTrue(evalB("(= 1 1)"));
		assertFalse(evalB("(= 1 2)"));
		assertFalse(evalB("(= 1 nil)"));
		assertFalse(evalB("(= 1 1.0)"));
		assertFalse(evalB("(= \\a \\b)"));
		assertFalse(evalB("(= :foo :baz)"));
		assertFalse(evalB("(= :foo 'foo)"));
		assertTrue(evalB("(= :bar :bar :bar)"));
		assertFalse(evalB("(= :bar :bar :bar 2)"));
		assertFalse(evalB("(= *juice* *juice*)"));
		
		assertTrue(evalB("(= [1 2] [1 2])"));
		assertTrue(evalB("(= #{1 2 4} #{1 2 4})"));
		assertFalse(evalB("(= #{1 2 4} [1 2 4])"));
		
		assertFalse(evalB("(= 9223372036854775808 -9223372036854775808)"));
		assertTrue(evalB("(= 9223372036854775808 9223372036854775808)"));

		assertTrue(evalB("(=)"));
		assertTrue(evalB("(= = =)"));
		assertTrue(evalB("(= nil nil)"));
		assertTrue(evalB("(= ##NaN ##NaN)")); // value equality, but not numeric equality

	}

	@Test
	public void testEqualsNumeric() {
		assertTrue(evalB("(==)"));
		assertTrue(evalB("(== ##Inf)"));
		assertTrue(evalB("(== ##NaN)"));
		assertTrue(evalB("(== 1 1.0)"));
		assertFalse(evalB("(== ##NaN ##NaN)")); // value equality, but not numeric equality

		assertCastError(step("(== :foo)"));
		assertCastError(step("(== 1 :foo)"));
		assertCastError(step("(== #4)"));
		assertCastError(step("(== [])"));
	}

	@Test
	public void testComparisons() {
		assertTrue(evalB("(== 0 0.0)"));
		assertTrue(evalB("(< 1)"));
		assertTrue(evalB("(> 3 2 1)"));
		assertTrue(evalB("(>= 3 2)"));
		assertTrue(evalB("(<= 1 2.0 2)"));

		assertTrue(evalB("(==)"));
		assertTrue(evalB("(<=)"));
		assertTrue(evalB("(>=)"));
		assertTrue(evalB("(<)"));
		assertTrue(evalB("(>)"));
		
		// Negative zero special case behaviour
		assertFalse(evalB("(= -0.0 0.0)"));
		assertTrue(evalB("(== -0.0 0.0)"));


		assertCastError(step("(== nil nil)"));
		assertCastError(step("(> nil)"));
		assertCastError(step("(< 1 :foo)"));
		assertCastError(step("(<= 1 3 \"hello\")"));
		assertCastError(step("(>= nil 1.0)"));
		assertCastError(step("(>= 'foo)"));
		
		// ##NaN behaviour
		assertFalse(evalB("(<= ##NaN ##NaN)"));
		assertFalse(evalB("(<= ##NaN 42)"));
		assertFalse(evalB("(< ##NaN 42)"));
		assertFalse(evalB("(> 42 ##NaN)"));
		assertFalse(evalB("(<= 42 ##NaN)"));
		assertFalse(evalB("(== ##NaN 42)"));
		assertFalse(evalB("(== ##NaN ##NaN)"));
		assertFalse(evalB("(>= ##NaN 42)"));

		// TODO: decide if we want short-circuiting behaviour on casts? Probably not?
		// assertCastError(step("(>= 1 2 3 '*balance*)"));

		assertFalse(evalB("(>= 1 2 3 '*balance*)"));
	}

	@Test
	public void testLog() {
		AVector<ACell> v0=Vectors.of(1L, 2L);

		Context c=step("(log 1 2)");
		assertEquals(v0,c.getResult());
		AVector<AVector<ACell>> log=c.getLog();
		assertEquals(1,log.count()); // only one address did a log
		assertNotNull(log);

		assertEquals(1,log.count()); // one log entry only
		assertEquals(v0,log.get(0).get(1));

		// do second log in same context
		AVector<ACell> v1=Vectors.of(3L, 4L);
		c=step(c,"(log 3 4)");
		log=c.getLog();

		assertEquals(2,log.count()); // should be two entries now
		assertEquals(v0,log.get(0).get(1));
		assertEquals(v1,log.get(1).get(1));
	}


	@Test
	public void testLogInActor() {
		AVector<ACell> v0=Vectors.of(1L, 2L);

		Context c=step("(deploy '(do (defn event ^{:callable? true} [& args] (apply log args)) (defn non-event ^{:callable? true} [& args] (rollback (apply log args)))))");
		Address actor=(Address) c.getResult();

		assertEquals(0,c.getLog().count()); // Nothing logged so far

		// call actor function
		c=step(c,"(call "+actor+" (event 1 2))");
		AVector<AVector<ACell>> log = c.getLog();

		assertEquals(1,log.count()); // should be one entry by the actor
		assertEquals(v0,log.get(0).get(1));

		// call actor function which rolls back - should also roll back log
		c=step(c,"(call "+actor+" (non-event 3 4))");
		log = c.getLog();
		assertEquals(1,log.count()); // should be one entry by the actor
		assertEquals(v0,log.get(0).get(1));

	}

	@Test
	public void testVector() {
		assertEquals(Vectors.of(1L, 2L), eval("(vector 1 2)"));
		assertEquals(Vectors.empty(), eval("(vector)"));
	}

	@Test
	public void testVectorTypes() {
		assertEquals(Vectors.of(1L, 2L).getEncoding(),MapEntry.of(1L, 2L).getEncoding()); // should be same Hash / Encoding
		assertEquals(Vectors.of(1L, 2L), eval("(first {1 2})")); // map entry is a vector
	}

	@Test
	public void testIdentity() {
		assertNull(eval("(identity nil)"));
		assertEquals(Vectors.of(1L, 2L), eval("(identity [1 2])"));

		assertArityError(step("(identity)"));
		assertArityError(step("(identity 1 2)"));
	}

	@Test
	public void testConcat() {
		assertNull(eval("(concat)"));
		assertNull(eval("(concat nil nil nil)"));

		// singleton identity preservation
		assertSame(Vectors.empty(), eval("(concat [])"));
		assertSame(Vectors.empty(), eval("(concat nil [])"));
		assertSame(Lists.empty(), eval("(concat () nil)"));
		assertSame(Lists.empty(), eval("(concat nil ())"));

		assertCastError(step("(concat 1 2)"));
		assertCastError(step("(concat \"Foo\" \"Bar\")"));

		assertEquals(Vectors.of(1L, 2L, 3L, 4L), eval("(concat [1 2] [3 4])"));
		assertEquals(Vectors.of(1L, 2L, 3L, 4L), eval("(concat nil [1 2] '(3) [] [4])"));
		assertEquals(List.of(1L, 2L, 3L, 4L), eval("(concat nil '(1 2) [3 4] nil)"));
	}

	@Test
	public void testMapcat() {
		assertNull(eval("(mapcat (fn[x] x) nil)"));
		assertEquals(Vectors.of(2L, 2L), eval("(mapcat (fn [x] [x x]) [2])"));
		assertEquals(Vectors.empty(), eval("(mapcat (fn [x] nil) [1 2 3 4])"));
		assertEquals(Lists.empty(), eval("(mapcat (fn [x] nil) '(1 2 3 4))"));
		assertEquals(Vectors.of(1L, 2L, 3L), eval("(mapcat vector [1 2 3])"));
		assertEquals(Vectors.of(1L, 2L, 2L, 3L, 3L, 4L), eval("(mapcat (fn [a b] [a b]) [1 2 3] [2 3 4])"));

		assertArityError(step("(mapcat identity)"));
		assertCastError(step("(mapcat nil [1 2])"));
	}


	@Test
	public void testHashMap() {
		assertEquals(Maps.empty(), eval("(hash-map)"));
		assertEquals(Maps.of(1L, 2L), eval("(hash-map 1 2)"));
		assertEquals(Maps.of(null, null), eval("(hash-map nil nil)"));
		assertEquals(Maps.of(1L, 2L, 3L, 4L), eval("(hash-map 3 4 1 2)"));

		// Check last value of equal keys is used
		assertEquals(Maps.of(1L, 4L), eval("(hash-map 1 2 1 3 1 4)"));
		assertEquals(Maps.of(1L, 2L), eval("(hash-map 1 4 1 3 1 2)"));

		assertArityError(step("(hash-map 1)"));
		assertArityError(step("(hash-map 1 2 3)"));
		assertArityError(step("(hash-map 1 2 3 4 5)"));
	}

	@Test
	public void testBlobMap() {
		// Singleton empty BlobMap
		assertSame(BlobMaps.empty(), eval("(blob-map)"));

		assertEquals(eval("(blob-map 0xa2 :foo)"),eval("(assoc (blob-map) 0xa2 :foo)"));
		assertEquals(eval("(blob-map 0xa2 :foo 0xb3 :bar)"),eval("(assoc (blob-map) 0xa2 :foo 0xb3 :bar)"));

		// Bad key types should result in argument errors
		assertArgumentError(step("(blob-map :foo :bar)")); // See issue #162
		assertArgumentError(step("(assoc (blob-map) :foo 10)")); // See Issue #101
		
		// TODO: are we sure we want to preserve Address type in keys?
		assertEquals(Address.create(19),eval("(first (first (blob-map #19 #21)))"));
		assertEquals(Address.create(21),eval("(second (first (blob-map #19 #21)))"));
		
		// Keys collide between blobs and equivalent addresses
		assertEquals(CVMLong.ONE,eval("(count (blob-map #19 #21 0x0000000000000013 #22))"));
		assertEquals(Vectors.of(Blob.fromHex("0000000000000013"),Address.create(22)),eval("(first (blob-map #19 #21 0x0000000000000013 #22))"));

		assertArityError(step("(blob-map 0xabcd)"));
		assertArityError(step("(blob-map 0xa2 :foo 0xb3)"));
	}

	@Test
	public void testKeys() {
		assertEquals(Vectors.empty(), eval("(keys {})"));
		assertEquals(Vectors.of(1L), eval("(keys {1 2})"));
		assertEquals(Sets.of(1L, 3L, 5L), eval("(set (keys {1 2 3 4 5 6}))"));

		assertEquals(Vectors.empty(),RT.keys(BlobMaps.empty()));
		assertEquals(Vectors.of(HERO),RT.keys(BlobMap.of(HERO, 1L)));

		assertCastError(step("(keys 1)"));
		assertCastError(step("(keys [])"));
		assertCastError(step("(keys nil)")); // TODO: maybe empty set?

		assertArityError(step("(keys)"));
		assertArityError(step("(keys {} {})"));
	}

	@Test
	public void testValues() {
		assertEquals(Vectors.empty(), eval("(values {})"));
		assertEquals(Vectors.of(2L), eval("(values {1 2})"));
		assertEquals(Sets.of(2L, 4L, 6L), eval("(set (values {1 2 3 4 5 6}))"));

		assertCastError(step("(values 1)"));
		assertCastError(step("(values [])"));
		assertCastError(step("(values nil)")); // TODO: maybe empty set?

		assertArityError(step("(values)"));
		assertArityError(step("(values {} {})"));
	}

	@Test
	public void testHashSet() {
		assertEquals(Sets.empty(), eval("(hash-set)"));
		assertEquals(Sets.of(1L, 2L), eval("(hash-set 1 2)"));
		assertEquals(Sets.of((Object) null), eval("(hash-set nil nil)"));
		assertEquals(Sets.of(1L, 2L, 3L, 4L), eval("(hash-set 3 4 1 2)"));

		// de-duplication
		assertEquals(Sets.of(1L, 2L, 3L), eval("(hash-set 1 2 3 1)"));
		assertEquals(Sets.of((Long)null), eval("(hash-set nil nil nil)"));
		assertEquals(Sets.of(Sets.empty()), eval("(hash-set (hash-set) (hash-set))"));

		assertEquals(Sets.of((Object) null), eval("(hash-set nil)"));
	}

	@Test
	public void testStr() {
		assertEquals("", evalS("(str)"));
		assertEquals("1", evalS("(str 1)"));
		assertEquals("12", evalS("(str 1 2)"));
		assertEquals("42.0", evalS("(str 42.0)"));
		assertEquals("##Inf", evalS("(str ##Inf)"));
		assertEquals("##-Inf", evalS("(str ##-Inf)"));
		assertEquals("##NaN", evalS("(str ##NaN)"));
		assertEquals("255", evalS("(str (byte 0xff))"));
		assertEquals("baz", evalS("(str \"baz\")"));
		assertEquals("bazbar", evalS("(str \"baz\" \"bar\")"));
		assertEquals("baz", evalS("(str \\b \\a \\z)"));
		assertEquals(":foo", evalS("(str :foo)"));
		assertEquals("foo", evalS("(str 'foo)"));
		assertEquals("nil", evalS("(str nil)"));
		assertEquals("true", evalS("(str true)"));
		assertEquals("cafebabe", evalS("(str (blob \"CAFEBABE\"))")); // TODO sanity + consistency

		// Escaping behaviour. See #393
		assertEquals("\"", evalS("(str \"\\\"\")"));
		assertEquals("ok \"test\" foo", evalS("(str \"ok \\\"test\\\" foo\")"));
		
		// Standalone chars are stringified Java-style whereas chars embedded in a container (eg. in a vector)
		// must be EDN-style readable representations.
		// 
		assertEquals("a", evalS("(str \\a)"));
		assertEquals("conve x", evalS("(str \\c \\o \\n \"ve\" \\space \\x)"));
		assertEquals("[\\a \\b (fn [] \\newline) (\\return {\\space \\tab})]",
					 evalS("(str [\\a \\b (fn [] \\newline) (list \\return {\\space \\tab})])"));
	}
	
	@Test 
	public void testPrint() {
		assertEquals("1", evalS("(print 1)"));
		assertEquals("foo", evalS("(print 'foo)"));
		assertEquals("[foo :bar]", evalS("(print '[foo :bar])"));
		assertEquals("\\a", evalS("(print \\a)"));
		assertEquals("\\\u1234", evalS("(print \\u1234)"));
		assertEquals("\"nom\"", evalS("(print \"nom\")"));
		assertEquals("nil", evalS("(print nil)"));
		assertEquals("0x123456", evalS("(print 0x123456)"));
		
		// Test escaping cases
		assertEquals("\"\\\"\"", evalS("(print \"\\\"\")"));
		assertEquals("\"\\n\"", evalS("(print \"\\n\")"));
		assertEquals("\"\\\\\"", evalS("(print \"\\\\\")"));
		
		// Usual arity checks. TODO should we allow arbitrary arity?
		assertArityError(step("(print)"));
		assertArityError(step("(print {} {})"));
	}
	
	@Test
	public void testSplit() {
		assertEquals(Vectors.of(""), eval("(split \"\" \\a)"));
		assertEquals(Vectors.of("",""), eval("(split \":\" \\:)"));
		assertEquals(Vectors.of("foo","bar"), eval("(split \"foo.bar\" \\.)"));
		
		// Single char strings should work
		assertEquals(Vectors.of("foo","bar"), eval("(split \"foo.bar\" \".\")"));
		assertCastError(step("(split \"foo..bar\" \"..\")"));
		
		// Code point should work, but only if in range
		assertEquals(Vectors.of("foo","bar"), eval("(split \"foo.bar\" (long \\.))"));
		assertCastError(step("(split \"foo..bar\" (long 0x0100000000))"));
		
		assertCastError(step("(split :foo \\.)"));
		assertCastError(step("(split \"abc\" nil)"));
		assertCastError(step("(split \"abc\" \"\")"));
		
		assertArityError(step("(split)"));
		assertArityError(step("(split nil nil nil)"));
	}
	
	@Test
	public void testJoin() {
		assertEquals("",evalS("(join [] \\.)"));
		assertEquals("foo.bar",evalS("(join [\"foo\" \"bar\"] \\.)"));
		assertEquals("..",evalS("(join [\"\" \"\" \"\"] \\.)"));
		
		assertCastError(step("(join [] \"abc\")")); 
		assertCastError(step("(join [\"foo\" 2] \\.)")); 
		
		assertArityError(step("(join)"));
		assertArityError(step("(join nil nil nil)"));
	}
	
	@Test 
	public void testSlice() {
		assertEquals("World",evalS("(slice \"Hello World\" 6 11)"));
		assertEquals("World",evalS("(slice \"Hello World\" 6)"));
		assertEquals("0x34",evalS("(slice 0x1234 1 2)"));
		
		assertSame(Strings.EMPTY,eval("(slice \"Hello World\" 4 4)"));
		assertSame(Blob.EMPTY,eval("(slice 0xcafebabe 2 2)"));
		
		assertEquals(Lists.of(2),eval("(slice (list 1 2) 1)"));
		
		// TODO: slicing for sets
		assertEquals(Sets.of(1,2),eval("(slice #{1 2} 0)"));
		assertEquals(Sets.empty(),eval("(slice #{1 2} 1 1)"));
		
		// TODO: slicing for maps
		//assertEquals(Maps.of(1,2),eval("(slice {1 2} 0)"));

		assertBoundsError(step("(slice 0x 1)")); 
		assertBoundsError(step("(slice 0x -1 0)")); 
		assertBoundsError(step("(slice 0x1234 -1 1)")); 
		assertBoundsError(step("(slice 0x1234 1 3)")); 
		assertBoundsError(step("(slice 0x1234 2 0)")); 
		
		assertCastError(step("(slice :foo 0)")); 
		
		assertArityError(step("(slice)"));
		assertArityError(step("(slice nil nil nil nil)"));

	}

	@Test
	public void testAssert() {
		assertNull(eval("(assert)"));
		assertNull(eval("(assert true)"));
		assertNull(eval("(assert (= 1 1))"));
		assertNull(eval("(assert '(= 1 2))")); // form itself is truthy, not evaluated
		assertNull(eval("(assert '(assert false))")); // form itself is truthy, not evaluated
		assertNull(eval("(assert 1 2 3)"));

		assertAssertError(step("(assert false)"));
		assertAssertError(step("(assert true false)"));
		assertAssertError(step("(assert (= 1 2))"));
	}


	@Test
	public void testCeil() {
		// Double cases
		assertEquals(1.0,evalD("(ceil 0.001)"));
		assertEquals(-1.0,evalD("(ceil -1.25)"));

		// Integral cases
		assertEquals(-1.0,evalD("(ceil -1)"));
		assertEquals(0.0,evalD("(ceil 0)"));
		assertEquals(1.0,evalD("(ceil 1)"));
		assertEquals(2.0,evalD("(ceil (byte 0x02))"));

		// Special cases
		assertEquals(Double.NaN,evalD("(ceil ##NaN)"));
		assertEquals(Double.POSITIVE_INFINITY,evalD("(ceil (/ 1 0))"));
		assertEquals(Double.NEGATIVE_INFINITY,evalD("(ceil (/ -1 0))"));

		assertCastError(step("(ceil #3)"));
		assertCastError(step("(ceil :foo)"));
		assertCastError(step("(ceil nil)"));
		assertCastError(step("(ceil [])"));

		assertArityError(step("(ceil)"));
		assertArityError(step("(ceil :foo :bar)")); // arity > cast
	}

	@Test
	public void testFloor() {
		// Double cases
		assertEquals(0.0,evalD("(floor 0.001)"));
		assertEquals(-2.0,evalD("(floor -1.25)"));

		// Integral cases
		assertEquals(-1.0,evalD("(floor -1)"));
		assertEquals(0.0,evalD("(floor 0)"));
		assertEquals(1.0,evalD("(floor 1)"));

		// Special cases
		assertEquals(Double.NaN,evalD("(floor ##NaN)"));
		assertEquals(Double.POSITIVE_INFINITY,evalD("(floor (/ 1 0))"));
		assertEquals(Double.NEGATIVE_INFINITY,evalD("(floor (/ -1 0))"));

		assertCastError(step("(floor #666)"));
		assertCastError(step("(floor :foo)"));
		assertCastError(step("(floor nil)"));
		assertCastError(step("(floor [])"));

		assertArityError(step("(floor)"));
		assertArityError(step("(floor :foo :bar)")); // arity > cast
	}

	@Test
	public void testAbs() {
		// Integer cases
		assertEquals(1L,evalL("(abs 1)"));
		assertEquals(10L,evalL("(abs (byte 10))"));
		assertEquals(17L,evalL("(abs -17)"));
		assertEquals(Long.MAX_VALUE,evalL("(abs 9223372036854775807)"));

		// Double cases
		assertEquals(1.0,evalD("(abs 1.0)"));
		assertEquals(13.0,evalD("(abs (double -13))"));
		assertEquals(Math.pow(10,100),evalD("(abs (pow 10 100))"));

		// Fun Double cases
		assertTrue(evalB("(= 0.0 (abs -0.0))"));
		assertEquals(Double.NaN,evalD("(abs ##NaN)"));
		assertEquals(Double.POSITIVE_INFINITY,evalD("(abs (/ 1 0))"));
		assertEquals(Double.POSITIVE_INFINITY,evalD("(abs (/ -1 0))"));
		assertEquals(Double.POSITIVE_INFINITY,evalD("(abs ##-Inf)"));

		// long overflow case
		assertEquals(CVMBigInteger.MIN_POSITIVE,eval("(abs -9223372036854775808)"));
		assertEquals(Long.MAX_VALUE,evalL("(abs -9223372036854775807)"));

		// Needs a numeric type, else CAST error
		assertCastError(step("(abs :foo)"));
		assertCastError(step("(abs nil)"));
		assertCastError(step("(abs #78)"));
		assertCastError(step("(abs [1])"));
		assertCastError(step("(abs \\a)"));

		assertArityError(step("(abs)"));
		assertArityError(step("(abs :foo :bar)")); // arity > cast
	}

	@Test
	public void testSignum() {
		// Integer cases
		assertEquals(1L,evalL("(signum 1)"));
		assertEquals(1L,evalL("(signum (byte 10))"));
		assertEquals(-1L,evalL("(signum -17)"));
		assertEquals(1L,evalL("(signum 9223372036854775807)"));
		assertEquals(-1L,evalL("(signum -9223372036854775808)"));

		// Double cases
		assertEquals(0.0,evalD("(signum 0.0)"));
		assertEquals(-0.0,evalD("(signum -0.0)"));
		assertEquals(1.0,evalD("(signum 1.0)"));
		assertEquals(-1.0,evalD("(signum (double -13))"));
		assertEquals(1.0,evalD("(signum (pow 10 100))"));

		// Needs a numeric type, else cast error
		assertCastError(step("(signum #1)"));
		assertCastError(step("(signum 0xabab)"));
		assertCastError(step("(signum nil)"));
		assertCastError(step("(signum :foo)"));
		assertCastError(step("(signum \\a)"));

		// Fun Double cases
		assertEquals(Double.NaN,evalD("(signum ##NaN)"));
		assertEquals(1.0,evalD("(signum ##Inf)"));
		assertEquals(-1.0,evalD("(signum ##-Inf)"));

		assertArityError(step("(signum)"));
		assertArityError(step("(signum :foo :bar)")); // arity > cast
	}

	@Test
	public void testNot() {
		assertFalse(evalB("(not 1)"));
		assertTrue(evalB("(not false)"));
		assertTrue(evalB("(not nil)"));
		assertFalse(evalB("(not [])"));

		assertArityError(step("(not)"));
		assertArityError(step("(not true false)"));
	}

	@Test
	public void testNth() {
		assertEquals(2L, evalL("(nth [1 2] 1)"));
		assertEquals(2L, evalL("(nth [1 2] (byte 1))"));
		assertCVMEquals('c', eval("(nth \"abc\" 2)"));
		assertEquals(CVMLong.create(10), eval("(nth 0xff0a0b 1)")); // Blob nth byte

		assertArityError(step("(nth)"));
		assertArityError(step("(nth [])"));
		assertArityError(step("(nth [] 1 2)"));
		assertArityError(step("(nth 1 1 2)")); // arity > cast

		// nth on Blobs
		assertEquals(CVMLong.create(255),eval("(nth 0xFF 0)"));
		assertTrue(evalB("(= 16 (nth 0x0010 1))"));
		assertTrue(evalB("(== 16 (nth 0x0010 1))"));

		// cast errors for bad indexes
		assertCastError(step("(nth [] :foo)"));
		assertCastError(step("(nth [] nil)"));

		// cast errors for non-sequential objects
		assertCastError(step("(nth :foo 0)"));
		assertCastError(step("(nth 12 13)"));

		// BOUNDS error because treated as empty sequence
		assertBoundsError(step("(nth nil 10)"));
		
		// Big integer bounds are always invalid
		assertBoundsError(step("(nth 0x1234 9223372036854775808)"));
		assertBoundsError(step("(nth \"abc\" -9223372036854775809)"));

		assertBoundsError(step("(nth 0x 0)"));
		assertBoundsError(step("(nth nil 0)"));
		assertBoundsError(step("(nth (str) 0)"));
		assertBoundsError(step("(nth {} 10)"));

		assertBoundsError(step("(nth [1 2] 10)"));
		assertBoundsError(step("(nth [1 2] -1)"));
		assertBoundsError(step("(nth \"abc\" 3)"));
		assertBoundsError(step("(nth \"abc\" -1)"));
	}

	@Test
	public void testList() {
		assertEquals(Lists.of(1L, 2L), eval("(list 1 2)"));
		assertEquals(Lists.of(Symbols.LIST), eval("(list 'list)"));
		assertEquals(Lists.of(Symbols.LIST), eval("'(list)"));
		assertEquals(Lists.empty(), eval("(list)"));
	}

	@Test
	public void testVec() {
		assertSame(Vectors.empty(), eval("(vec nil)"));
		assertSame(Vectors.empty(), eval("(vec [])"));
		assertSame(Vectors.empty(), eval("(vec {})"));
		assertSame(Vectors.empty(), eval("(vec (blob-map))"));

		assertEquals( eval("[\\a \\b \\c]"), eval("(vec \"abc\")"));

		assertEquals(Vectors.of(1,2,3,4), eval("(vec (list 1 2 3 4))"));
		assertEquals(Vectors.of(MapEntry.of(1,2)), eval("(vec {1,2})"));

		assertCastError(step("(vec 1)"));
		assertCastError(step("(vec :foo)"));

		assertArityError(step("(vec)"));
		assertArityError(step("(vec 1 2)"));
	}

	@Test
	public void testReverse() {
		assertSame(Lists.empty(), eval("(reverse nil)"));
		assertSame(Lists.empty(), eval("(reverse [])"));
		assertSame(Vectors.empty(), eval("(reverse ())"));
		assertEquals(Vectors.of(1,2,3), eval("(reverse '(3 2 1))"));
		assertEquals(Lists.of(1,2,3), eval("(reverse [3 2 1])"));

		assertCastError(step("(reverse #{})"));
		assertCastError(step("(reverse {:foo :bar})"));
		assertCastError(step("(reverse 0x1234)"));

		assertArityError(step("(reverse)"));
		assertArityError(step("(reverse 1 2)"));
	}
	
	@Test
	public void testAssoc() {
		// Associative values
		assertEquals(Maps.of(1,2),eval("(assoc {} 1 2)"));
		assertEquals(Vectors.of(1,2),eval("(assoc [1 3] 1 2)"));
		assertEquals(Sets.of(1,2),eval("(assoc #{1} 2 true)"));
		assertEquals(BlobMap.of(Blob.EMPTY,2),eval("(assoc (blob-map) 0x 2)"));
		
		// bad key types
		assertArgumentError(step("(assoc (blob-map) 1 2)"));
		assertArgumentError(step("(assoc [1 2 3] :foo 7)"));
	
		// Definitiely Non-associative values
		assertCastError(step("(assoc 1 2 3)"));
		assertCastError(step("(assoc :foo 2 3)"));
		
		// TODO: Not currently associative, but maybe should be?
		assertCastError(step("(assoc \"abc\" 2 \\d)"));
		assertCastError(step("(assoc 0xabcdef 2 12)"));
		
		assertArityError(step("(assoc :foo 1 2 3)"));
		assertArityError(step("(assoc :baz 1)"));
	}

	@Test
	public void testAssocNull() {
		// nil is treated as an empty map
		assertSame(Maps.empty(),eval("(assoc nil)"));

		// assoc promotes nil to maps
		assertEquals(Maps.of(1L, 2L), eval("(assoc nil 1 2)"));
		assertEquals(Maps.of(1L, 2L, 3L, 4L), eval("(assoc nil 1 2 3 4)"));
	}

	@Test
	public void testAssocMaps() {
		// no key/values is OK
		assertEquals(Maps.empty(), eval("(assoc {})"));

		assertEquals(Maps.of(1L, 2L), eval("(assoc {} 1 2)"));
		assertEquals(Maps.of(1L, 2L), eval("(assoc {1 2})"));
		assertEquals(Maps.of(1L, 2L, 3L, 4L), eval("(assoc {} 1 2 3 4)"));
		assertEquals(Maps.of(1L, 2L), eval("(assoc {1 2} 1 2)"));
		assertEquals(Maps.of(1L, 2L, 3L, 4L), eval("(assoc {1 2} 3 4)"));
		assertEquals(Maps.of(1L, 2L, 3L, 4L, 5L, 6L), eval("(assoc {1 2} 3 4 5 6)"));

		assertArityError(step("(assoc {} 1 2 3)"));
		assertArityError(step("(assoc {} 1)"));

	}
	
	@Test
	public void testAssocSets() {
		// no values is OK
		assertEquals(Sets.empty(), eval("(assoc #{})"));

		// Simple assoc
		assertEquals(Sets.of(1), eval("(assoc #{} 1 true)"));
		assertEquals(Sets.of(1), eval("(assoc #{} 1 true 2 false)"));
		assertEquals(Sets.of(1,2), eval("(assoc #{} 1 true 2 true)"));
		
		// Ordering of assocs
		assertEquals(Sets.of(1), eval("(assoc #{} 1 false 1 true 2 true 2 false)"));
		
		// Key removal
		assertEquals(Sets.of(1), eval("(assoc #{1 2} 2 false)"));

		// Associng a non-boolean value is an error
		assertArgumentError(step("(assoc #{} 1 2)"));

		// Standard error cases
		assertArityError(step("(assoc #{} 1 2 3)")); // arity before cast
		assertArityError(step("(assoc #{} 1)"));
	}

	@Test
	public void testAssocIn() {
		// empty index cases - type of first arg not checked since no idexing happens
		assertEquals(2L, evalL("(assoc-in {} [] 2)")); // empty indexes returns value
		assertEquals(2L, evalL("(assoc-in nil [] 2)")); // empty indexes returns value
		assertEquals(2L, evalL("(assoc-in :old [] 2)")); // empty indexes returns value
		assertEquals(2L, evalL("(assoc-in 13 nil 2)")); // empty indexes returns value (nil considered empty seq)

		// map cases
		assertEquals(Maps.of(1L,2L), eval("(assoc-in {} [1] 2)"));
		assertEquals(Maps.of(1L,2L,3L,4L), eval("(assoc-in {3 4} [1] 2)"));
		assertEquals(Maps.of(1L,2L), eval("(assoc-in nil [1] 2)"));
		assertEquals(Maps.of(1L,Maps.of(5L,6L),3L,4L), eval("(assoc-in {3 4} [1 5] 6)"));

		// vector cases
		assertEquals(Vectors.of(1L, 5L, 3L),eval("(assoc-in [1 2 3] [1] 5)"));
		assertEquals(Vectors.of(5L),eval("(assoc-in [1] [0] 5)"));
		assertEquals(MapEntry.of(1L, 5L),eval("(assoc-in (first {1 2}) [1] 5)"));

		// Set cases
		assertEquals(Sets.of(1L),eval("(assoc-in #{} [1] true)"));
		assertEquals(Sets.of(1L),eval("(assoc-in #{1} [1] true)"));
		assertEquals(Sets.of(1L,2L),eval("(assoc-in #{1} [2] true)"));
		assertArgumentError(step("(assoc-in #{3} [2] :fail)")); // bad value type
		assertCastError(step("(assoc-in #{3} [3 2] :fail)")); // 'true' is not a data structure

		// Cast error - wrong key types
		assertCastError(step("(assoc-in (blob-map) :foo :bar)"));

		// Cast errors - not associative collections
		assertCastError(step("(assoc-in 1 [2] 3)"));

		// Invalid keys
		assertArgumentError(step("(assoc-in [1] [:foo] 3)"));
		assertArgumentError(step("(assoc-in [] [42] :foo)")); // Issue #119

		// cast errors - paths not sequences
		assertCastError(step("(assoc-in {} #{:a :b} 42)")); // See Issue 95
		assertCastError(step("(assoc-in {} :foo 42)")); // See Issue 95

		// Arity error
		assertArityError(step("(assoc-in)"));
		assertArityError(step("(assoc-in nil)"));
		assertArityError(step("(assoc-in nil 1)"));
		assertArityError(step("(assoc-in nil 1 2 3)"));
		assertArityError(step("(assoc-in :bad-struct [1] 2 :blah)")); // ARITY before CAST
	}

	@Test
	public void testAssocFailures() {
		assertCastError(step("(assoc 1 1 2)"));
		assertCastError(step("(assoc :foo)"));

		// assertCastError(step("(assoc #{} :foo true)"));

		// Invalid keys
		assertArgumentError(step("(assoc [1 2 3] 1.4 :foo)"));
		assertArgumentError(step("(assoc [1 2 3] nil :foo)"));
		assertArgumentError(step("(assoc [] 2 :foo)"));
		assertArgumentError(step("(assoc (list) 2 :fail)"));
		assertArgumentError(step("(assoc (blob-map) 2 :fail)"));

		// Arity error
		assertArityError(step("(assoc)"));
		assertArityError(step("(assoc nil 1)"));
		assertArityError(step("(assoc nil 1 2 3)"));
		assertArityError(step("(assoc 1 1)")); // ARITY before CAST
	}

	@Test
	public void testAssocVectors() {
		assertEquals(Vectors.empty(), eval("(assoc [])"));
		assertEquals(Vectors.of(2L, 1L), eval("(assoc [1 2] 0 2 1 1)"));

		// Invalid keys
		assertArgumentError(step("(assoc [] 1 7)"));
		assertArgumentError(step("(assoc [] -1 7)"));
		assertArgumentError(step("(assoc [1 2] :a 2)"));

		assertArityError(step("(assoc [] 1 2 3)"));
		assertArityError(step("(assoc [] 1)"));
	}

	@Test
	public void testDissoc() {
		assertEquals(Maps.empty(), eval("(dissoc {1 2} 1)"));
		assertEquals(Maps.of(1L, 2L), eval("(dissoc {1 2 3 4} 3)"));
		assertEquals(Maps.of(1L, 2L), eval("(dissoc {1 2} 3)"));
		assertEquals(Maps.of(1L, 2L), eval("(dissoc {1 2})"));
		assertEquals(Maps.empty(), eval("(dissoc nil 1)"));
		assertEquals(Maps.empty(), eval("(dissoc {1 2 3 4} 1 3)"));
		assertEquals(Maps.of(3L, 4L), eval("(dissoc {1 2 3 4} 1 2)"));

		// blob-map dissocs. Regression tests for #140 (fatal error in dissoc with non-blob keys)
		assertSame(BlobMap.EMPTY,eval("(dissoc (blob-map) 1)"));
		assertEquals(BlobMap.of(Blob.fromHex("a2"), Keywords.FOO),eval("(dissoc (into (blob-map) [[0xa2 :foo] [0xb3 :bar]]) 0xb3)"));
		assertEquals(BlobMap.of(Blob.fromHex("a2"), Keywords.FOO),eval("(dissoc (into (blob-map) [[0xa2 :foo]]) :foo)"));
		assertEquals(BlobMaps.empty(), eval("(dissoc (blob-map 0x 0x1234) 0x)"));
		assertEquals(BlobMaps.empty(), eval("(dissoc(blob-map) :foo)"));

		assertCastError(step("(dissoc 1 1 2)"));
		assertCastError(step("(dissoc #{})"));
		assertCastError(step("(dissoc [])"));

		assertArityError(step("(dissoc)"));
	}

	@Test
	public void testContainsKey() {
		// Maps test for key presence
		assertFalse(evalB("(contains-key? {} 1)"));
		assertFalse(evalB("(contains-key? {} nil)"));
		assertTrue(evalB("(contains-key? {1 2} 1)"));

		// Sets test for inclusion
		assertFalse(evalB("(contains-key? #{} 1)"));
		assertFalse(evalB("(contains-key? #{1 2 3} nil)"));
		assertFalse(evalB("(contains-key? #{false} true)"));
		assertTrue(evalB("(contains-key? #{1 2} 1)"));
		assertTrue(evalB("(contains-key? #{nil 2 3} nil)"));

		// Vectors test for valid index
		assertFalse(evalB("(contains-key? [] 1)"));
		assertFalse(evalB("(contains-key? [0 1 2] :foo)"));
		assertTrue(evalB("(contains-key? [3 4] 1)"));
		
		// BlobMaps test for key presence
		assertFalse(evalB("(contains-key? (blob-map) 1)"));
		assertFalse(evalB("(contains-key? (blob-map) 0x)"));
		assertFalse(evalB("(contains-key? (blob-map 0x :foo) :foo)"));
		assertTrue(evalB("(contains-key? (blob-map 0x :foo) 0x)"));

		// Nil is treated as a data structure with no keys
		assertFalse(evalB("(contains-key? nil 1)"));
		assertFalse(evalB("(contains-key? nil #{nil})"));

		assertArityError(step("(contains-key? 3)"));
		assertArityError(step("(contains-key? {} 1 2)"));
		
		assertCastError(step("(contains-key? 3 4)"));
		assertCastError(step("(contains-key? \"ab\" 4)"));

	}

	@Test
	public void testDisj() {
		assertEquals(Sets.of(2L), eval("(disj #{1 2} 1)"));
		assertEquals(Sets.of(1L, 2L), eval("(disj #{1 2} 1.0)"));
		assertSame(Sets.empty(), eval("(disj #{1} 1)"));
		assertSame(Sets.empty(), eval("(reduce disj #{1 2} [1 2])"));
		assertEquals(Sets.empty(), eval("(disj #{} 1)"));
		assertEquals(Sets.of(1L, 2L, 3L), eval("(disj (set [3 2 1 2 4]) 4)"));
		assertEquals(Sets.of(1L), eval("(disj (set [3 2 1 2 4]) 2 3 4)"));
		assertEquals(Sets.empty(), eval("(disj #{})"));
		
		// nil is treated as empty set
		assertSame(Sets.empty(), eval("(disj nil 1)"));
		assertSame(Sets.empty(), eval("(disj nil 1 2 3)"));
		assertSame(Sets.empty(), eval("(disj nil nil)"));

		assertCastError(step("(disj [] 1)"));
		assertCastError(step("(disj (blob-map) 0x)"));
		
		assertArityError(step("(disj)"));
	}

	@Test
	public void testSet() {
		assertEquals(Sets.of(1L, 2L, 3L), eval("(set [3 2 1 2])"));
		assertEquals(Sets.of(1L, 2L, 3L), eval("(set #{1 2 3})"));

		// equivalent of get with set-as-function
		assertEquals(eval("(#{2 3} 2)"),eval("(get #{2 3} 2)"));
		assertEquals(eval("(#{2 3} 1)"),eval("(get #{2 3} 1)"));
		
		// Countables should work in sets
		assertEquals(eval("#{(byte 1) (byte 2)}"),eval("(set 0x0102)"));
		assertEquals(eval("#{\\T \\a \\b}"),eval("(set \"Tab\")"));
		assertEquals(eval("#{[1 2] [3 4]}"),eval("(set {1 2 3 4})"));

		assertSame(Sets.empty(), eval("(set nil)")); // nil treated as empty set of elements

		assertArityError(step("(set)"));
		assertArityError(step("(set 1 2)"));

		assertCastError(step("(set 1)"));
	}

	@SuppressWarnings("unchecked")
	@Test
	public void testSetRegression153() throws InvalidDataException {
		// See issue #153
		Context c=context();
		c=step(c, "(def s1 #{#5477106 \\*})");
		ASet<ACell> s1=(ASet<ACell>) c.getResult();
		s1.validate();
		c=step(c, "(def s2 #{#2 #0 true #3 0x61a049 #242411 #3478095 #9275832328719 #1489754187855142})");
		ASet<ACell> s2=(ASet<ACell>) c.getResult();
		s2.validate();

		ASet<ACell> u1=s2.includeAll(s1);
		u1.validate();

		c=step(c, "(def union1 (union s2 s1))");
		ASet<ACell> u2=(ASet<ACell>) c.getResult();
		u2.validate();

	}



	@Test
	public void testSubsetQ() {
		assertTrue(evalB("(subset? #{} #{})"));
		assertTrue(evalB("(subset? #{} #{1 2 3})"));
		assertTrue(evalB("(subset? #{2 3} #{1 2 3 4})"));

		// check nil is handled as empty set
		assertTrue(evalB("(subset? nil nil)"));
		assertTrue(evalB("(subset? nil #{})"));
		assertTrue(evalB("(subset? #{} nil)"));
		assertTrue(evalB("(subset? nil #{1 2 3})"));
		assertFalse(evalB("(subset? #{1 2 3} nil)"));


		assertFalse(evalB("(subset? #{2 3} #{1 2})"));
		assertFalse(evalB("(subset? #{1 2 3} #{0})"));
		assertFalse(evalB("(subset? #{#{}} #{#{1}})"));

		assertArityError(step("(subset?)"));
		assertArityError(step("(subset? 1)"));
		assertArityError(step("(subset? 1 2 3)"));

		assertCastError(step("(subset? 1 2)"));
		assertCastError(step("(subset? #{} [2])"));
	}

	@Test
	public void testSetUnion() {
		assertEquals(Sets.empty(),eval("(union)"));
		assertEquals(Sets.empty(),eval("(union #{})"));
		assertEquals(Sets.of(1L,2L),eval("(union #{1 2})"));

		// nil treated as empty set in all cases
		assertEquals(Sets.of(1L,2L),eval("(union nil #{1 2})"));
		assertEquals(Sets.of(1L,2L),eval("(union #{1 2} nil)"));
		assertEquals(Sets.empty(),eval("(union nil)"));
		assertEquals(Sets.empty(),eval("(union nil nil nil)"));

		assertEquals(Sets.of(1L,2L,3L),eval("(union #{1 2} #{3})"));

		assertEquals(Sets.of(1L,2L,3L,4L,5L),eval("(union #{1 2} #{3} #{4 5})"));

		assertCastError(step("(union :foo)"));
		assertCastError(step("(union [1] [2 3])"));
	}

	@Test
	public void testSetIntersection() {
		assertEquals(Sets.empty(),eval("(intersection nil)"));
		assertEquals(Sets.empty(),eval("(intersection #{})"));
		assertEquals(Sets.of(1L,2L),eval("(intersection #{1 2})"));

		assertEquals(Sets.empty(),eval("(intersection #{1 2} #{3})"));
		assertEquals(Sets.empty(),eval("(intersection #{1 2 3} nil)"));

		assertEquals(Sets.of(2L,3L),eval("(intersection #{1 2 3} #{2 3 4})"));

		assertEquals(Sets.of(3L),eval("(intersection #{1 2 3} #{2 3 4} #{3 4 5})"));

		assertArityError(step("(intersection)"));

		assertCastError(step("(intersection :foo)"));
		assertCastError(step("(intersection [1] [2 3])"));
	}

	@Test
	public void testSetDifference() {
		assertEquals(Sets.empty(),eval("(difference nil)"));
		assertEquals(Sets.empty(),eval("(difference #{})"));
		assertEquals(Sets.of(1L,2L),eval("(difference #{1 2})"));

		assertEquals(Sets.of(1L,2L),eval("(difference #{1 2} #{3})"));

		assertEquals(Sets.of(2L,3L),eval("(difference #{1 2 3} #{1 4})"));

		assertEquals(Sets.of(3L),eval("(difference #{1 2 3} #{2 4} #{1 5})"));

		assertArityError(step("(difference)"));

		assertCastError(step("(difference :foo)"));
		assertCastError(step("(difference [1] [2 3])"));
	}

	@Test
	public void testDifferenceRegression155() {
		Context c=step("(do  (def arg+ [#{nil #5 #4 #2 #0 #7 #6 #3 #1} #{nil 0x500360a6 :B2Qrb9d1U5WH00h6c \"1pC\" true \\Ñ (quote A/aHAb7K2) #5278509802049781 #515}])  (def u (apply union arg+))  (def d (apply difference arg+))  (= #{} (difference d u))  )");
		assertEquals(CVMBool.TRUE,c.getResult());
	}


	@Test
	public void testFirst() {
		assertEquals(1L, evalL("(first [1 2])"));
		assertEquals(1L, evalL("(first '(1 2 3 4))"));

		assertBoundsError(step("(first [])"));
		assertBoundsError(step("(first nil)"));

		assertArityError(step("(first)"));
		assertArityError(step("(first [1] 2)"));
		assertCastError(step("(first 1)"));
		assertCastError(step("(first :foo)"));
		
		assertEquals(17L,evalL("(first 0x11223344)"));
		
		// Not data structures, but are countable
		assertEquals(CVMChar.create('a'), eval("(first \"abc\")"));
		assertEquals(CVMLong.create(0x12), eval("(first 0x1234)"));
		assertBoundsError(step("(first \"\")"));
		assertBoundsError(step("(first 0x)"));
	}

	@Test
	public void testSecond() {
		assertEquals(2L, evalL("(second [1 2])"));

		assertBoundsError(step("(second [2])"));
		assertBoundsError(step("(second nil)"));

		assertArityError(step("(second)"));
		assertArityError(step("(second [1] 2)"));
		assertCastError(step("(second 1)"));
		
		// Not data structures, but are countable
		assertEquals(CVMChar.create('b'), eval("(second \"abc\")"));
		assertEquals(CVMLong.create(0x34), eval("(second 0x1234)"));
		assertBoundsError(step("(second \"a\")"));
		assertBoundsError(step("(second 0x01)"));

	}

	@Test
	public void testLast() {
		assertEquals(2L, evalL("(last [1 2])"));
		assertEquals(4L, evalL("(last [4])"));

		assertBoundsError(step("(last [])"));
		assertBoundsError(step("(last nil)"));

		assertArityError(step("(last)"));
		assertArityError(step("(last [1] 2)"));
		assertCastError(step("(last 1)"));
		
		// Not data structures, but are countable
		assertEquals(CVMChar.create('c'), eval("(last \"abc\")"));
		assertEquals(CVMLong.create(0x34), eval("(last 0x1234)"));
		assertBoundsError(step("(last \"\")"));
		assertBoundsError(step("(last 0x)"));
	}

	@Test
	public void testNext() {
		assertEquals(Vectors.of(2L, 3L), eval("(next [1 2 3])"));
		assertEquals(Lists.of(2L, 3L), eval("(next '(1 2 3))"));

		assertNull(eval("(next nil)"));
		assertNull(eval("(next [1])"));
		assertNull(eval("(next {1 2})"));

		assertArityError(step("(next)"));
		assertArityError(step("(next [1] [2 3])"));

		assertCastError(step("(next 1)"));
		assertCastError(step("(next :foo)"));
	}

	@Test
	public void testConj() {
		assertEquals(Vectors.of(1L, 2L, 3L), eval("(conj [1 2] 3)"));
		assertEquals(Lists.of(3L, 1L, 2L), eval("(conj (list 1 2) 3)"));

		// nil works like empty vector
		assertEquals(Vectors.of(1L), eval("(conj nil 1)"));
		assertEquals(Vectors.of(2L,3L), eval("(conj nil 2 3)"));
		
		// Sets conj like element inclusion
		assertEquals(Sets.of(3L), eval("(conj #{} 3)"));
		assertEquals(Sets.of(2L,3L), eval("(conj #{} 3 2)"));
		assertEquals(Sets.of(3L), eval("(conj #{3} 3)"));
		assertEquals(Sets.of(1L, 3L), eval("(conj #{1} 3)"));
		assertEquals(Sets.of(1L, 3L), eval("(conj #{1} 1 3)"));
		assertEquals(Sets.of(1L, 2L, 3L), eval("(conj #{2 3} 1)"));
		assertEquals(Sets.of(1L, 2L, 3L), eval("(conj #{2 3 1} 1)"));

		// Maps conj with map entry vectors
		assertEquals(Maps.of(1L, 2L), eval("(conj {} [1 2])"));
		assertEquals(Maps.of(1L, 2L, 5L, 6L), eval("(conj {1 3 5 6} [1 2])"));

		// Lists conj with addition at front
		assertEquals(Lists.of(1L,2L), eval("(conj '() 2 1)"));
		assertEquals(Lists.of(1L), eval("(conj (list) 1)"));
		assertEquals(Lists.of(1L, 2L), eval("(conj (list 2) 1)"));

		// arity 1 OK, no change
		assertEquals(Vectors.of(1L, 2L), eval("(conj [1 2])"));

		// Blobmaps
		assertEquals(BlobMaps.create(Blob.fromHex("a1"), Blob.fromHex("a2")),eval("(conj (blob-map) [0xa1 0xa2])"));

		// bad data structures
		assertCastError(step("(conj :foo)"));
		assertCastError(step("(conj :foo 1)"));

		// bad types of elements
		assertArgumentError(step("(conj {} 2)")); // can't cast long to a map entry
		assertArgumentError(step("(conj {} [1 2 3])")); // wrong size vector for a map entry
		assertArgumentError(step("(conj {1 2} [1])")); // wrong size vector for a map entry
		assertArgumentError(step("(conj {} '(1 2))")); // wrong type for a map entry
		assertArgumentError(step("(conj (blob-map) [:foo 0xa2])")); // bad key type for blobmap
		assertArgumentError(step("(conj (blob-map #0 #0) [false #0])")); // Issue #386

		assertCastError(step("(conj 1 2)"));
		assertCastError(step("(conj (str :foo) 2)")); // string is not a Data Structure

		assertArityError(step("(conj)"));
	}

	@Test
	public void testCons() {
		assertEquals(Lists.of(3L, 1L, 2L), eval("(cons 3 (list 1 2))"));
		assertEquals(Lists.of(3L, 1L, 2L), eval("(cons 3 [1 2])"));
		assertEquals(Lists.of(3L, 1L, 2L), eval("(cons 3 1 [2])"));

		assertEquals(Lists.of(1L, 3L), eval("(cons 1 #{3})"));
		assertEquals(Lists.of(1L), eval("(cons 1 [])"));

		// nil is treated as empty list
		assertEquals(Lists.of(3L), eval("(cons 3 nil)"));
		
		assertCastError(step("(cons 1 2)"));

		assertArityError(step("(cons [])"));
		assertArityError(step("(cons 1)"));
		assertArityError(step("(cons)"));

		assertCastError(step("(cons 1 2 3 4 5)"));
	}

	@Test
	public void testComp() {
		assertEquals(43L, evalL("((comp inc) 42)"));
		assertEquals(44L, evalL("((comp inc inc) 42)"));
		assertEquals(45L, evalL("((comp inc inc inc) 42)"));
		assertEquals(46L, evalL("((comp inc inc inc inc) 42)"));

		assertEquals(3.0, evalD("((comp sqrt +) 4 5)"));

		assertArityError(step("(comp)"));

	}

	@Test
	public void testInto() {
		// nil as data structure
		assertNull(eval("(into nil nil)"));
		assertEquals(Maps.of(1L,2L),eval("(into nil {1 2})"));
		assertEquals(Vectors.empty(), eval("(into nil [])"));
		assertEquals(Vectors.of(1L, 2L, 3L), eval("(into nil [1 2 3])"));

		assertEquals(Vectors.of(1L, 2L, 3L), eval("(into [1 2] [3])"));
		assertEquals(Vectors.of(1L, 2L, 3L), eval("(into [1 2 3] nil)"));
		assertEquals(Vectors.of(1L, 2L, 3L), eval("(into nil [1 2 3])"));

		assertEquals(Lists.of(2L, 1L, 3L, 4L), eval("(into '(3 4) '(1 2))"));

		assertEquals(Sets.of(1L, 2L, 3L), eval("(into #{} [1 2 1 2 3])"));

		// map as data structure
		assertEquals(Maps.empty(), eval("(into {} [])"));
		assertEquals(Maps.of(1L, 2L, 3L, 4L), eval("(into {} [[1 2] [3 4] [1 2]])"));

		assertEquals(Vectors.of(MapEntry.of(1L, 2L)), eval("(into [] {1 2})"));

		assertCastError(step("(into 1 [2 3])")); // long is not a conjable data structure
		assertCastError(step("(into nil :foo)")); // keyword is not a sequence of elements

		// See #151
		assertCastError(step("(into (list) #0)")); // Address is not a sequential data structure
		assertCastError(step("(into #0 [])")); // Address is not a conjable data structure
		assertCastError(step("(into #0 [1 2])")); // Address is not a conjable data structure
		assertCastError(step("(into 0 [])")); // Long is not a conjable data structure

		// bad element types
		assertArgumentError(step("(into {} [nil])")); // nil is not a MapEntry
		assertArgumentError(step("(into {} [[:foo]])")); // length 1 vector shouldn't convert to MapEntry
		assertArgumentError(step("(into {} [[:foo :bar :baz]])")); // length 1 vector shouldn't convert to MapEntry
		assertArgumentError(step("(into {1 2} [2 3])")); // longs are not map entries
		assertArgumentError(step("(into {1 2} [[] []])")); // empty vectors are not map entries
		assertArgumentError(step("(into (blob-map) [[1 2]])"));

		assertArityError(step("(into)"));
		assertArityError(step("(into inc)"));
		assertArityError(step("(into 1 2 3)")); // arity > cast
	}

	@Test
	public void testMerge() {
		assertEquals(Maps.empty(),eval("(merge)"));
		assertEquals(Maps.empty(),eval("(merge nil)"));
		assertEquals(Maps.empty(),eval("(merge nil nil)"));

		assertEquals(Maps.of(1L,2L,3L,4L),eval("(merge {1 2} {3 4})"));
		assertEquals(Maps.of(1L,2L,3L,4L),eval("(merge {1 2 3 4} {})"));
		assertEquals(Maps.of(1L,2L,3L,4L),eval("(merge nil {1 2 3 4})"));

		assertEquals(Maps.of(1L,3L),eval("(merge {1 2} {1 3})"));
		assertEquals(Maps.of(1L,3L),eval("(merge nil {1 2} nil {1 3} nil)"));
		
		// Blobmap handling
		assertEquals(BlobMaps.empty(), eval("(merge (blob-map) nil)"));
		assertEquals(BlobMaps.of(Blob.fromHex("01"), CVMLong.create(2)), eval("(merge (blob-map 0x01 1) (blob-map 0x01 2))"));
		assertEquals(Maps.of(Blob.fromHex("01"), CVMLong.create(2)), eval("(merge {0x01 1} (blob-map 0x01 2))"));
		assertEquals(BlobMaps.of(Blob.fromHex("01"), CVMLong.create(2)), eval("(merge (blob-map 0x01 1) {0x01 2})"));

		assertCastError(step("(merge [])"));
		assertCastError(step("(merge {} [1 2 3])"));
		assertCastError(step("(merge nil :foo)"));
	}

	@Test
	public void testDotimes() {
		assertEquals(Vectors.of(0L, 1L, 2L), eval("(do (def a []) (dotimes [i 3] (def a (conj a i))) a)"));
		assertEquals(Vectors.empty(), eval("(do (def a []) (dotimes [i 0] (def a (conj a i))) a)"));
		assertEquals(Vectors.empty(), eval("(do (def a []) (dotimes [i -1.5] (def a (conj a i))) a)"));
		assertEquals(Vectors.empty(), eval("(do (def a []) (dotimes [i -1.5]) a)"));

		assertCastError(step("(dotimes [1 10])"));
		assertCastError(step("(dotimes [i :foo])"));
		assertCastError(step("(dotimes [:foo 10])"));
		assertCastError(step("(dotimes :foo)"));

		assertArityError(step("(dotimes)"));
		assertArityError(step("(dotimes [i])"));
		assertArityError(step("(dotimes [i 2 3])"));

	}

	@Test
	public void testDouble() {
		assertEquals(-13.0,evalD("(double -13)"));
		// TODO: double check with #344
		// assertEquals(1.0,evalD("(double true)")); 

		assertEquals(255.0,evalD("(double (byte -1))")); // byte should be 0-255

		assertCastError(step("(double :foo)"));
		
		// Shouldn't try to cast an Address, see #431
		assertCastError(step("(double #7)"));
		
		// Note: doesn't transitively cast Boolean -> Integer -> Double
		assertCastError(step("(double true)")); 

		assertArityError(step("(double)"));
		assertArityError(step("(double :foo :bar)"));
	}
	
	@Test
	public void testDoublePred() {
		assertTrue(evalB("(double? 1.0)"));
		assertTrue(evalB("(double? ##NaN)"));
		assertTrue(evalB("(double? ##Inf)"));

		assertFalse(evalB("(double? nil)"));
		assertFalse(evalB("(double? [])"));
		assertFalse(evalB("(double? 0)"));
		assertFalse(evalB("(double? :foo)"));

		assertArityError(step("(double?)"));
		assertArityError(step("(double? :foo :bar)"));
	}

	@Test
	public void testMap() {
		assertEquals(Vectors.of(2L, 3L), eval("(map inc [1 2])"));
		assertEquals(Lists.of(2L, 3L), eval("(map inc '(1 2))")); // TODO is this right?
		assertEquals(Vectors.of(4L, 6L), eval("(map + [1 2] [3 4 5])"));
		assertEquals(Vectors.of(3L), eval("(map + [1 2 3] [2])"));
		assertEquals(Vectors.of(1L, 2L, 3L), eval("(map identity [1 2 3])"));

		// Not a function => CAST error
		assertCastError(step("(map 1 [1])"));
		assertCastError(step("(map 1 [] [] [])"));
		
		// Map with nil (treated as empty collection)
		assertNull(eval("(map inc nil)"));
		assertEquals(Vectors.empty(),eval("(map inc [1 2] nil)"));
		assertEquals(Maps.empty(),eval("(map inc {:a 1 :b 3} nil)"));
		
		// Map with maps replaces elements by key, in order
		assertEquals(Maps.of(2,1,4,3),eval("(map (fn [[a b]] [b a]) {1 2 3 4})"));
		assertEquals(Maps.of(1,3),eval("(map (fn [a b] b) {0 0 1 1} [[1 2] [1 3] [1 4]])"));

		// Map with sets works like conj'ing in each result in turn
		assertEquals(Sets.of(4,5,6),eval("(map (fn [a b] b) #{1 2 3} [4 5 6])"));

		// CAST error if any following arguments are not a data structure
		assertCastError(step("(map inc 1)"));
		assertCastError(step("(map inc [1] 0x1234)"));
		
		// ARGUMENT if function creates wrong element type
		assertArgumentError(step("(map (fn [_ _] :foo) {1 2} [1 2 3 4])"));

		// ARITY if function accepts wrong number of arguments
		assertArityError(step("(map (fn [_] :foo) {1 2} [1 2 3 4])"));
		assertArityError(step("(map (fn []) [1 2 3 4])"));
		assertArityError(step("(map (fn [a b c]) [1 2 3 4] [5 6])"));

		// ARITY error if insufficient arguments to map
		assertArityError(step("(map)"));
		assertArityError(step("(map inc)"));
		assertArityError(step("(map 1)")); // arity > cast
	}

	@Test
	public void testFor() {
		assertEquals(Vectors.empty(), eval("(for [x nil] (inc x))"));
		assertEquals(Vectors.empty(), eval("(for [x []] (inc x))"));
		assertEquals(Vectors.of(2L,3L), eval("(for [x '(1 2)] (inc x))"));
		assertEquals(Vectors.of(2L,3L), eval("(for [x [1 2]] (inc x))"));

		// TODO: maybe dubious error types?

		assertCastError(step("(for 1 1)")); // bad binding form
		assertArityError(step("(for [x] 1)")); // bad binding form
		assertArityError(step("(for [x [1 2] [2 3]] 1)")); // bad binding form length
		assertCastError(step("(for [x :foo] 1)")); // bad sequence

		assertArityError(step("(for)"));
		assertArityError(step("(for [] nil nil)"));
		assertCastError(step("(for 1)")); // arity > cast
	}
	
	@Test
	public void testForLoop() {
		assertEquals(CVMLong.create(9), eval("(for-loop [i 0 (< i 10) (inc i)] i)"));
		assertNull(eval("(for-loop [i 0 false (inc i)] i)"));
		
		// TODO: maybe dubious error types?
		assertCompileError(step("(for-loop [1 2 3 4] 5)")); // bad binding form
		assertCastError(step("(for-loop 4 5)")); // bad binding form

		assertArityError(step("(for-loop [1 2 3] 4)"));
		assertArityError(step("(for-loop)"));
	}

	@Test
	public void testMapv() {
		assertEquals(Vectors.of(2L, 3L), eval("(mapv inc [1 2])"));
		assertEquals(Vectors.of(4L, 6L), eval("(mapv + '(1 2) '(3 4 5))"));
		
		// nil treated as empty collection
		assertEquals(Vectors.empty(), eval("(mapv + '(1 2) nil)"));
		assertEquals(Vectors.empty(), eval("(mapv + nil)"));
		
		// Check blob-map behaviour
		assertSame(Vectors.empty(),eval("(mapv inc (blob-map))"));
		assertEquals(Vectors.of(2),eval("(mapv count (blob-map 0x00 :foo))"));

		assertArityError(step("(mapv)"));
		assertArityError(step("(mapv inc)"));
	}

	@Test
	public void testFilter() {
		assertEquals(Vectors.of(1,2,3), eval("(filter number? [1 :foo 2 :bar 3])"));
		assertEquals(Lists.of(Keywords.FOO), eval("(filter #{:foo} '(:foo 2 3))"));
		
		// nil behaves as empty collection
		assertNull(eval("(filter keyword? nil)"));
		
		assertEquals(Maps.empty(), eval("(filter nil? {1 2 3 4})"));
		assertEquals(Maps.of(Keywords.FOO,1), eval("(filter (fn [[k v]] (keyword? k)) {:foo 1 'bar 2})"));
		assertEquals(Sets.of(1,2,3), eval("(filter number? #{1 2 3 :foo})"));

		assertCastError(step("(filter nil? 1)"));
		assertCastError(step("(filter 1 [1 2 3])"));

		assertArityError(step("(filter +)"));
		assertArityError(step("(filter 1 2 3)"));
	}
	
	@Test
	public void testFilterv() {
		assertEquals(Vectors.of(1,2,3), eval("(filterv number? [1 :foo 2 :bar 3])"));
		assertEquals(Lists.of(Keywords.FOO,3), eval("(filter #{:foo 3} '(:foo 2 3 :bar))"));
		
		// nil behaves as empty collection
		assertSame(Vectors.empty(),eval("(filterv keyword? nil)"));
		assertSame(Vectors.empty(),eval("(filterv nil? {1 2 3 4})"));

		assertCastError(step("(filterv nil? 1)"));
		assertCastError(step("(filterv 1 [1 2 3])"));

		assertArityError(step("(filterv +)"));
		assertArityError(step("(filterv 1 2 3)"));
	}

	
	@Test
	public void testLang() {
		{
			Context ctx=context();
			ctx=step(ctx,"(def *lang* (fn [x] x))");
			assertEquals(Symbols.COUNT,eval(ctx,"count"));
		}
	}

	@Test
	public void testReduce() {
		assertEquals(24L, evalL("(reduce * 1 [1 2 3 4])"));
		assertEquals(2L, evalL("(reduce + 2 [])"));
		assertEquals(2L, evalL("(reduce + 2 nil)"));

		// add values, indexing into map entries as vectors
		assertEquals(10.0, evalD("(reduce (fn [acc me] (+ acc (me 1))) 0.0 {:a 1, :b 2, 107 3, nil 4})"));
		
		// reduce over map, destructuring keys and values
		assertEquals(100.0, evalD(
				"(reduce (fn [acc [k v]] (let [x (double (v nil))] (+ acc (* x x)))) 0.0 {true {nil 10}})"));

		// reduce over BlobMap should be in order
		assertEquals(12.0, evalD(
				"(reduce (fn [acc [k v]] (let [x (double v)] (+ (* acc acc) x))) 0.0 (blob-map 0x 1 0x01 2 0x02 3))"));
		
		
		assertEquals(Lists.of(3,2,1), eval("(reduce conj '() '(1 2 3))"));

		// 2-arg reduce forms
		assertEquals(24L, evalL("(reduce * [1 2 3 4])"));
		assertEquals(1L, evalL("(reduce * nil)"));
		assertEquals(1L, evalL("(reduce + [1])"));
		assertEquals(0L, evalL("(reduce + [])"));
		assertEquals(Keywords.FOO, eval("(reduce (fn [] :foo) [])")); // 0 arity
		assertEquals(Keywords.FOO, eval("(reduce (fn [v] :foo) [:bar])")); // 1 arity
		assertEquals(Keywords.FOO, eval("(reduce (fn [a b] :foo) [:bar :baz])")); // 2 arity

		// Errors in reduce function
		assertCastError(step("(reduce + [:foo])"));
		assertCastError(step("(reduce + 1 [:foo])"));

		assertCastError(step("(reduce 1 2 [])"));
		assertCastError(step("(reduce + 2 :foo)"));
		assertCastError(step("(reduce + 1)"));

		assertArityError(step("(reduce +)"));
		assertArityError(step("(reduce + 1 [2] [3])"));
	}

	@Test public void testReduceFail() {
		// shouldn't fail because function never called
		assertEquals(2L,evalL("(reduce address 2 [])"));

		assertArityError(step("(reduce address 2 [:foo :bar])"));
		assertCastError(step("(reduce (fn [a x] (address x)) 2 [:foo :bar])"));
	}

	@Test
	public void testReduced() {
		assertEquals(Vectors.of(2L,3L), eval("(reduce (fn [i v] (if (== v 3) (reduced [i v]) v)) 1 [1 2 3 4 5])"));

		// 2 arg reduce
		assertEquals(Vectors.of(2L,3L), eval("(reduce (fn [i v] (if (== v 3) (reduced [i v]) v)) [1 2 3 4 5])"));
		assertEquals(CVMLong.create(5L), eval("(reduce (fn [a b] (if (== b 1) (reduced :foo) b)) [1 2 3 4 5])")); // b is never 1
		assertEquals(CVMLong.create(1L), eval("(reduce (fn [v] (reduced v)) [1])")); // fn called with arity 1
		assertEquals(Keywords.FOO, eval("(reduce (fn [] (reduced :foo)) [])")); // fn called with arity 0


		assertArityError(step("(reduced)"));
		assertArityError(step("(reduced 1 2)"));

		// reduced on its own is an :EXCEPTION Error
		assertError(ErrorCodes.EXCEPTION,step("(reduced 1)"));
		
		// reduced cannot escape actor call boundary
		{
			Context ctx=context();
			ctx=step(ctx,"(def act (deploy `(do (defn foo ^{:callable? true} [] (reduced 1)))))");
			ctx=step(ctx,"(reduce (fn [_ _] (call act (foo))) nil [nil])");
			assertError(ErrorCodes.EXCEPTION,ctx);
		}
		
		// reduced can escape  nested function call
		{
			Context ctx=context();
			ctx=step(ctx,"(defn foo [x] (reduced x))");
			ctx=step(ctx,"(reduce (fn [i x] (foo x)) nil [1 2])");
			assertCVMEquals(1L,ctx.getResult());
		}

	}

	@Test
	public void testReturn() {
		// basic return mechanics
		assertEquals(1L,evalL("(return 1)"));

		assertEquals(Vectors.empty(), eval("(let [f (fn [x] (+ 1 (return x)))] (f []))")); // return in function body
		assertEquals(Vectors.empty(), eval("(let [f (fn [x] (let [a (return x)] 2))] (f []))")); // return in let
																									// binding
		assertEquals(Vectors.empty(), eval("(let [f (fn [x] (let [a 2] (return x) a))] (f []))")); // return in let body
		assertEquals(Vectors.empty(), eval("(let [f (fn [x] (do (return x) 2))] (f []))")); // return in do
		assertEquals(Vectors.empty(), eval("(let [f (fn [x] (return (return x)))] (f []))")); // nested returns
		assertEquals(Vectors.empty(), eval("(let [f (fn [x] ((return x) 2 3))] (f []))")); // return in function call
																							// position
		assertEquals(Vectors.empty(), eval("(let [f (fn [x] (= 2 (return x) 3))] (f []))")); // return in function arg
		assertEquals(Vectors.empty(), eval("(let [f (fn [x] (if (return x) 2 3))] (f []))")); // return in cond test
		assertEquals(Vectors.empty(), eval("(let [f (fn [x] (if true (return x) 3))] (f []))")); // return in cond
																									// result
		assertEquals(Vectors.empty(), eval("(let [f (fn [x] (if true [] (return x)))] (f 3))")); // return in cond
																									// default
		assertArityError(step("(return)"));
		assertArityError(step("(return 1 2)"));
	}

	@Test
	public void testLoop() {
		assertNull(eval("(loop [])"));
		assertEquals(Keywords.FOO,eval("(loop [] :foo)"));
		assertEquals(Keywords.FOO,eval("(loop [] 1 2 3 :foo)"));
		assertEquals(Keywords.FOO,eval("(loop [a :foo] :bar a)"));

		assertCompileError(step("(loop [a])"));
		assertCompileError(step("(loop)")); // Issue #80
		assertCompileError(step("(loop :foo)")); // Not a binting vector
	}

	@Test
	public void testRecordLookup() {
		assertEquals(INITIAL.getAccounts(),eval("(*state* :accounts)"));
		assertEquals(Keywords.FOO,eval("(*state* [ 982788 ] :foo )"));
		assertNull(eval("(*state* [1 2 3])")); // Issue #85

		assertArityError(step("(*state* :accounts :foo :bar)"));
	}

	@Test
	public void testRecur() {
		// test factorial with accumulator
		assertEquals(120L, evalL("(let [f (fn [a x] (if (> x 1) (recur (* a x) (dec x)) a))] (f 1 5))"));

		assertArityError(step("(let [f (fn [x] (recur x x))] (f 1))"));
		assertJuiceError(step("(let [f (fn [x] (recur x))] (f 1))"));

		// should hit depth limits before running out of juice
		// TODO: think about letrec?
		assertDepthError(step("(do   (def f (fn [x] (recur (f x))))   (f 1))"));

		// Recur on its own is an :EXCEPTION Error
		assertError(ErrorCodes.EXCEPTION,step("(recur 1)"));
	}

	@Test
	public void testRecurMultiFn() {
		// test function that should exit on recur with value 13
		Context ctx=step("(defn f ([] 13) ([a] (inc (recur))))");

		assertEquals(13L,evalL(ctx,"(f)"));
		assertEquals(13L,evalL(ctx,"(f :foo)"));
		assertArityError(step(ctx,"(f 1 2)"));
	}

	@Test
	public void testTailcall() {
		assertEquals(Keywords.FOO,eval("(do (defn f [x] :foo) (defn g [] (tailcall (f 1))) (g))"));
		assertEquals(RT.cvm(3L),eval("((fn [] (tailcall (+ 1 2))))"));

		// Undeclared function in tailcall
		assertUndeclaredError(step("(do (defn f [x] :foo) (defn g [] (tailcall (h 1))) (g))"));

		// Arity error in tailcall
		assertArityError(step("(do (defn g [] :foo) (defn f [x] (tailcall (g 1))) (f 1))"));

		// check we aren't consuming stack, should fail with :JUICE not :DEPTH
		assertJuiceError(step("(do (def f (fn [x] (tailcall (f x)))) (f 1))"));

		// tailcall on its own is an :EXCEPTION Error
		assertError(ErrorCodes.EXCEPTION,step("(tailcall (count 1))"));
	}

	@Test
	public void testHalt() {
		assertEquals(1L, evalL("(do (halt 1) (assert false))"));
		assertNull(eval("(do (halt) (assert false))"));

		// halt should not roll back state changes
		{
			Context ctx = step("(do (def a 13) (halt 2))");
			assertCVMEquals(2L, ctx.getResult());
			assertEquals(13L, evalL(ctx, "a"));
		}

		// Halt should return from a smart contract call but still have state changes
		{
			Context ctx=step("(def act (deploy '(do (def g :foo) (defn f ^{:callable? true} [] (def g 3) (halt 2) 1))))");
			assertTrue(ctx.getResult() instanceof Address);
			assertEquals(Keywords.FOO, eval(ctx,"(lookup act g)")); // initial value of g
			ctx=step(ctx,"(call act (f))");
			assertCVMEquals(2L, ctx.getResult()); // halt value returned
			assertCVMEquals(3L, eval(ctx,"(lookup act g)")); // g has been updated
		}

		assertArityError(step("(halt 1 2)"));
	}

	@Test
	public void testFail() {
		assertAssertError(step("(fail)"));
		assertAssertError(step("(fail \"Foo\")"));
		assertAssertError(step("(fail :ASSERT \"Foo\")"));
		assertAssertError(step("(fail :foo)"));

		assertError(step("(fail 1 :bar)"));
		assertCastError(step("(fail :CAST :bar)"));


		assertAssertError(step("(fail)"));

		{ // need to double-step this: can't define macro and use it in the same expression?
			Context ctx=step("(defmacro check [condition reaction] `(if (not ~condition) ~reaction))");
			assertAssertError(step(ctx,"(check (= (+ 2 2) 5) (fail \"Laws of arithmetic violated\"))"));
		}

		// cannot have null error code
		assertArgumentError(step("(fail nil \"Hello\")"));

		assertArityError(step("(fail 1 \"Message\" 3)"));
	}

	@Test
	public void testFailContract() {
		Context ctx=step("(def act (deploy '(do (defn set-and-fail ^{:callable? true} [x] (def foo x) (fail :NOPE (str x))))))");
		Address act=(Address) ctx.getResult();
		assertNotNull(act);

		ctx=step(ctx,"(call act (set-and-fail 100))");
		assertError(Keyword.create("NOPE"),ctx);

		// Foo shouldn't be defined
		assertNull(ctx.getAccountStatus(act).getEnvironmentValue(Symbols.FOO));
	}

	@Test
	public void testRollback() {
		assertEquals(1L, evalL("(do (rollback 1) (assert false))"));
		assertEquals(1L, evalL("(do (def a 1) (rollback a) (assert false))"));

		// rollback should roll back state changes
		Context ctx = step("(def a 17)");
		ctx = step(ctx, "(do (def a 13) (rollback 2))");
		assertEquals(17L, evalL(ctx, "a"));
	}

	@Test
	public void testWhen() {
		assertNull(eval("(when false 2)"));
		assertNull(eval("(when true)"));
		assertEquals(Vectors.empty(), eval("(when 2 3 4 5 [])"));

		// TODO: needs to fix / check?
		assertArityError(step("(when)"));
	}

	@Test
	public void testIfLet() {
		assertEquals(1L,evalL("(if-let [a 1] a)"));
		assertEquals(2L,evalL("(if-let [a true] 2)"));
		assertEquals(3L,evalL("(if-let [a []] 3 4)"));
		assertEquals(4L,evalL("(if-let [a nil] 3 4)"));
		assertEquals(5L,evalL("(if-let [a false] 3 5)"));

		//  TODO: fix destructuring examples
		//assertEquals(Vectors.of(2L,1L),eval("(if-let [[a b] [1 2]] [b a])"));
		//assertNull(eval("(if-let [[a b] nil] [b a])"));

		assertNull(eval("(if-let [a false] 1)")); // null on false branch

		assertArityError(step("(if-let [:foo 1])"));

		// TODO: needs to fix / check?
		assertArityError(step("(if-let [a true])")); // no branches
		assertArityError(step("(if-let [a true] 1 2 3)")); // too many branches
		assertArityError(step("(if-let)"));
		assertArityError(step("(if-let [])"));
		assertArityError(step("(if-let [foo] 1)"));
	}

	@Test
	public void testWhenLet() {
		assertEquals(1L,evalL("(when-let [a 1] a)"));
		assertEquals(2L,evalL("(when-let [a true] 2)"));
		assertEquals(3L,evalL("(when-let [a 1] 2 3)"));

		assertNull(eval("(when-let [a true])")); // empty trye branch
		assertNull(eval("(when-let [a false] 1)")); // null on false branch
		assertNull(eval("(when-let [a false])")); // null on false branch

		// See #442, shouldn't destructure on falsey case
		assertNull(eval("(when-let [[a] nil] a)")); 

		assertCompileError(step("(when-let [:foo 1])"));

		// TODO: needs to fix / check?
		assertArityError(step("(when-let)"));
		assertArityError(step("(when-let [])"));
		assertArityError(step("(when-let [foo] 1)"));
	}

	@Test
	public void testKeyword() {
		assertEquals(Keywords.STATE, eval("(keyword 'state)"));
		assertEquals(Keywords.STATE, eval("(keyword (name :state))"));
		assertEquals(Keywords.STATE, eval("(keyword (str 'state))"));
		assertEquals(Keywords.STATE, eval("(keyword 'state)"));
		
		assertTrue(evalB("(keyword? (keyword \"0\"))"));

		// keyword lookups
		assertNull(eval("((keyword :foo) nil)"));

		assertArgumentError(step("(keyword (str))")); // too short

		assertCastError(step("(keyword nil)"));
		assertCastError(step("(keyword 1)"));
		assertCastError(step("(keyword [])"));

		assertArityError(step("(keyword)"));
		assertArityError(step("(keyword 1 3)"));
	}

	@Test
	public void testKeywordAsFunction() {
		// lookups in maps
		assertNull(eval("(:foo {})"));
		assertNull(eval("(:foo {:bar 1} nil)"));
		assertEquals(1L,evalL("(:foo {} 1)"));
		assertEquals(1L,evalL("(:foo {:foo 1} 2)"));

		// lookups in sets
		assertSame(CVMBool.FALSE,eval("(:foo #{})"));
		assertEquals(1L,evalL("(:foo #{} 1)"));
		assertSame(CVMBool.TRUE,eval("(:foo #{:foo})"));
		assertNull(eval("(:foo #{:bar} nil)"));
		assertSame(CVMBool.TRUE,eval("(:foo #{:foo} 2)"));

		// lookups in vectors
		assertNull(eval("(:foo [])"));
		assertNull(eval("(:foo [] nil)"));
		assertEquals(1L,evalL("(:foo [1 2 3] 1)"));

		// lookups on nil
		assertNull(eval("((keyword :foo) nil)"));
		assertNull(eval("(:foo nil)"));
		assertEquals(1L,evalL("(:foo nil 1)"));
	}

	@Test
	public void testName() {
		assertEquals("count", evalS("(name :count)"));
		assertEquals("count", evalS("(name 'count)"));
		assertEquals("foo", evalS("(name \"foo\")"));

		// should extract symbol name
		assertEquals("bar", evalS("(name 'bar)"));

		// longer strings OK for name
		assertEquals("duicgidvgefiucefiuvfeiuvefiuvgifegvfuievgiuefgviuefgviufegvieufgviuefvgevevgi", evalS("(name \"duicgidvgefiucefiuvfeiuvefiuvgifegvfuievgiuefgviuefgviufegvieufgviuefvgevevgi\")"));

		assertCastError(step("(name nil)"));
		assertCastError(step("(name [])"));
		assertCastError(step("(name 12)"));

		assertArityError(step("(name)"));
		assertArityError(step("(name 1 3)"));
	}

	@Test
	public void testSymbol() {
		assertEquals(Symbols.COUNT, eval("(symbol :count)"));
		assertEquals(Symbols.COUNT, eval("(symbol (name 'count))"));
		assertEquals(Symbols.COUNT, eval("(symbol (str 'count))"));
		assertEquals(Symbols.COUNT, eval("(symbol (name :count))"));
		assertEquals(Symbols.COUNT, eval("(symbol (name \"count\"))"));

		// too short or too long results in ARGUMENT error
		assertArgumentError(step("(symbol (str))"));
		assertArgumentError(
			  step("(symbol \""+Samples.TOO_BIG_SYMBOLIC+"\")"));

		assertCastError(step("(symbol nil)"));
		assertCastError(step("(symbol [])"));

		assertArityError(step("(symbol)"));
		assertArityError(step("(symbol 1 2 3)"));
	}

	@Test
	public void testImport() {
		Context ctx = step("(def lib (deploy '(do (def foo 100))))");
		Address libAddress=ctx.getResult();

		{ // tests with a typical import
			Context ctx2=step(ctx,"(import ~lib :as mylib)");
			assertEquals(libAddress,ctx2.getResult());

			assertEquals(100L, evalL(ctx2, "mylib/foo"));
			assertUndeclaredError(step(ctx2, "mylib/bar"));
			assertTrue(evalB(ctx2,"(map? (lookup-meta mylib 'foo))"));
		}
		
		{ // tests with a named import
			assertUndeclaredError(step(ctx, "convex.core"));
			Context ctx2=step(ctx,"(import convex.core)");
			assertEquals(Init.CORE_ADDRESS ,ctx2.getResult());
			assertEquals(Init.CORE_ADDRESS, eval(ctx2, "convex.core"));
		}

		{ // test deploy and CNS import in a single form. See #107
			Context ctx2=step(ctx,"(do (let [addr (deploy nil)] (call *registry* (cns-update 'foo addr)) (import foo :as foo2)))");
			assertNotError(ctx2);
		}
		
		// can't import 1-arity unless a symbol
		assertAssertError(step(ctx,"(import #10)"));

		assertArityError(step(ctx,"(import)"));
		assertArityError(step(ctx,"(import ~lib :as)"));
		assertArityError(step(ctx,"(import ~lib :as mylib :blah)"));
	}

	@Test
	public void testImportCore() {
		Context ctx = step("(import convex.core :as cc)");
		assertNotError(ctx);
		assertEquals(eval(ctx,"count"),eval(ctx,"cc/count"));
	}


	@Test
	public void testLookup() {
		assertSame(Core.COUNT, eval("(lookup count)"));
		assertSame(Core.COUNT, eval("(lookup *address* count)"));
		assertSame(Core.COUNT, eval("(lookup "+Init.CORE_ADDRESS+" count)"));

		// Lookups after def
		assertEquals(1L,evalL("(do (def foo 1) (lookup foo))"));
		assertEquals(1L,evalL("(do (def foo 1) (lookup *address* foo))"));

		// UNDECLARED if not declared
		assertUndeclaredError(step("(lookup non-existent-symbol)"));

		// NOBODY for lookups in non-existent environment
		assertNobodyError(step("(lookup #77777777 count)"));
		assertNobodyError(step("(do (def foo 1) (lookup #66666666 foo))"));

		// COMPILE Errors for bad symbols
		assertCompileError(step("(lookup :count)"));
		assertCompileError(step("(lookup \"count\")"));
		assertCompileError(step("(lookup :non-existent-symbol)"));
		assertCompileError(
				step("(lookup \"cdiubcidciuecgieufgvuifeviufegviufeviuefbviufegviufevguiefvgfiuevgeufigv\")"));
		assertCompileError(step("(lookup nil)"));
		assertCompileError(step("(lookup 10)"));
		assertCompileError(step("(lookup [])"));

		// CAST Errors for bad Addresses
		assertCastError(step("(lookup 8 count)"));
		assertCastError(step("(lookup :foo count)"));

		assertArityError(step("(lookup)"));
		assertArityError(step("(lookup 1 2 3)"));
	}

	@Test
	public void testLookupSyntax() {
		AHashMap<ACell,ACell> countMeta=Core.METADATA.get(Symbols.COUNT);
		assertSame(countMeta, (eval("(lookup-meta 'count)")));
		assertSame(countMeta, (eval("(lookup-meta "+Init.CORE_ADDRESS+ " 'count)")));
		
		// Not actually defined in current address
		assertNull(eval("(lookup-meta *address* 'count)"));

		assertNull(eval("(lookup-meta 'non-existent-symbol)"));
		assertNull(eval("(lookup-meta #666666 'count)")); // invalid address

		assertSame(Maps.empty(),eval("(do (def foo 1) (lookup-meta 'foo))"));
		assertSame(Maps.empty(),eval("(do (def foo 1) (lookup-meta  *address* 'foo))"));
		assertNull(eval("(do (def foo 1) (lookup-meta #0 'foo))"));

		// invalid name string (too long)
		assertCastError(
				step("(lookup-meta \"cdiubcidciuecgieufgvuifeviufegviufeviuefbviufegviufevguiefvgfiuevgeufigv\")"));

		// bad symbols
		assertCastError(step("(lookup-meta count)"));
		assertCastError(step("(lookup-meta nil)"));
		assertCastError(step("(lookup-meta 10)"));
		assertCastError(step("(lookup-meta [])"));

		// Bad addresses
		assertCastError(step("(lookup-meta :foo 'bar)"));
		assertCastError(step("(lookup-meta 8 'count)"));

		assertArityError(step("(lookup-meta)"));
		assertArityError(step("(lookup-meta 1 2 3)"));
	}

	@Test
	public void testEmpty() {
		assertNull(eval("(empty nil)"));
		assertSame(Lists.empty(), eval("(empty (list 1 2))"));
		assertSame(Maps.empty(), eval("(empty {1 2 3 4})"));
		assertSame(Vectors.empty(), eval("(empty [1 2 3])"));
		assertSame(Sets.empty(), eval("(empty #{1 2})"));

		assertCastError(step("(empty 1)"));
		assertCastError(step("(empty :foo)"));
		assertArityError(step("(empty)"));
		assertArityError(step("(empty [1] [2])"));
	}

	@Test
	public void testMapAsFunction() {
		assertEquals(1L, evalL("({2 1 1 2} 2)"));
		assertNull(eval("({2 1 1 2} 3)"));
		assertNull(eval("({} 3)"));

		// fall-through behaviour
		assertEquals(10L, evalL("({2 1 1 2} 5 10)"));
		assertNull(eval("({} 1 nil)"));

		// bad arity
		assertArityError(step("({})"));
		assertArityError(step("({} 1 2 3 4)"));
	}

	@Test
	public void testVectorAsFunction() {
		assertEquals(5L, evalL("([1 3 5 7] 2)"));

		assertEquals(5L, evalL("([1 3 5 7] (byte 2))")); // TODO: is this sane? Implicit cast to Long is OK?

		// bounds checks get applied
		assertBoundsError(step("([] 0)"));
		assertBoundsError(step("([1 2 3] -1)"));
		assertBoundsError(step("([1 2 3] 3)"));

		// Bad index types
		assertCastError(step("([] nil)"));
		assertCastError(step("([] :foo)"));

		// bad arity
		assertArityError(step("([])"));
		assertArityError(step("([] 1 2 3 4)"));
	}

	@Test
	public void testListAsFunction() {
		assertEquals(5L, evalL("('(1 3 5 7) 2)"));

		// bounds checks get applied
		assertBoundsError(step("(() 0)"));
		assertBoundsError(step("('(1 2 3) -1)"));
		assertBoundsError(step("('(1 2 3) 3)"));

		// cast error
		assertCastError(step("(() nil)"));
		assertCastError(step("(() :foo)"));
		assertCastError(step("(() {})"));

		// bad arity
		assertArityError(step("(())"));
		assertArityError(step("(() 1 2 3 4)"));
	}

	@Test
	public void testApply() {
		// Basic data structure application
		assertSame(Maps.empty(),eval("(apply assoc [nil])"));
		assertSame(Vectors.empty(), eval("(apply vector ())"));
		assertSame(BlobMaps.empty(), eval("(apply blob-map ())"));
		assertSame(Lists.empty(), eval("(apply list [])"));

		assertEquals("foo", evalS("(apply str [\\f \\o \\o])"));

		assertEquals(10L,evalL("(apply + 1 2 [3 4])"));
		assertEquals(3L,evalL("(apply + 1 2 nil)"));

		assertEquals(Vectors.of(1L, 2L, 3L, 4L), eval("(apply vector 1 2 (list 3 4))"));
		assertEquals(List.of(1L, 2L, 3L, 4L), eval("(apply list 1 2 [3 4])"));
		assertEquals(List.of(1L, 2L), eval("(apply list 1 2 nil)"));

		// Bad function type
		assertCastError(step("(apply 666 1 2 [3 4])"));

		// Keyword works as a function lookup, but wrong arity (#79)
		assertArityError(step("(apply :n 1 2 [3 4])"));

		// Insufficient args to apply itself
		assertArityError(step("(apply)"));
		assertArityError(step("(apply vector)"));

		// Arity failure before cast
		assertArityError(step("(apply 666)"));

		// Insufficient args to applied function
		assertArityError(step("(apply assoc nil)"));

		// Cast error if not applied to collection
		assertCastError(step("(apply inc 1)"));
		assertCastError(step("(apply inc :foo)"));

		// not a sequential collection
		assertCastError(step("(apply + 1 2 {})"));
	}


	@Test
	public void testNonExistentAccountBalance() {
		// Address that doesn't exist address, shouldn't have any balance initially
		long addr=7777777777L;
		assertNull(eval("(let [a (address "+addr+")] (balance a))"));
	}

	@Test
	public void testBalance() {

		// hero balance, should reflect cost of initial juice
		Long expectedHeroBalance = HERO_BALANCE;
		assertEquals(expectedHeroBalance, evalL("(let [a (address " + HERO + ")] (balance a))"));

		// someone else's balance
		Long expectedVillainBalance = VILLAIN_BALANCE;
		assertEquals(expectedVillainBalance, evalL("(let [a (address " + VILLAIN + ")] (balance a))"));

		// A out of range address has nil balance
		assertNull(eval("(balance #666788)"));
		
		assertCastError(step("(balance nil)"));
		assertCastError(step("(balance 0x00)"));
		assertCastError(step("(balance 1)")); // shouldn't implicitly cast
		assertCastError(step("(balance :foo)"));

		assertArityError(step("(balance)"));
		assertArityError(step("(balance 1 2)"));
	}

	@Test
	public void testCreateAccount() {
		Context ctx=step("(create-account 0x817934590c058ee5b7f1265053eeb4cf77b869e14c33e7f85b2babc85d672bbc)");
		Address addr=ctx.getResult();
		assertEquals(addr.toExactLong()+1,ctx.getState().getAccounts().count()); // should be last Address added
		
		// Query rollback should result in same account being created
		assertTrue(evalB("(= (query (create-account *key*)) (query (create-account *key*)))"));

		// Check multiple create-accounts in same transaction
		assertNotError(step("(dotimes [i 100] (create-account *key*))"));
		
		assertCastError(step("(create-account :foo)"));
		assertCastError(step("(create-account 1)"));
		assertCastError(step("(create-account nil)"));
		assertCastError(step("(create-account #666666)"));

		assertArityError(step("(create-account)"));
		assertArityError(step("(create-account 1 2)"));
	}

	@Test
	public void testAccept() {
		assertEquals(0L, evalL("(accept 0)"));
		assertEquals(0L, evalL("(accept *offer*)"));  // offer should be initially zero
		assertEquals(0L, evalL("(accept (byte 0))")); // byte should widen to Long

		// accepting non-integer value -> CAST error
		assertCastError(step("(accept :foo)"));
		assertCastError(step("(accept :foo)"));
		assertCastError(step("(accept 0.3)"));

		// accepting negative -> ARGUMENT error
		assertArgumentError(step("(accept -1)"));

		// accepting more than is offered -> STATE error
		assertStateError(step("(accept 1)"));

		assertArityError(step("(accept)"));
		assertArityError(step("(accept 1 2)"));
	}

	@Test
	public void testAcceptInActor() {
		Context ctx=context();
		ctx=step(ctx,"(def act (deploy '(do (defn receive-coin ^{:callable? true} [sender amount data] (accept amount))  (defn echo-offer ^{:callable? true} [] *offer*))))");

		ctx=step(ctx,"(transfer act 100)");
		assertEquals(100L, (long)RT.jvm(ctx.getResult()));
		assertEquals(100L,evalL(ctx,"(balance act)"));
		assertEquals(999L,evalL(ctx,"(call act 999 (echo-offer))"));

		// send via contract call
		ctx=step(ctx,"(call act 666 (receive-coin *address* 350 nil))");
		assertEquals(350L, (long)RT.jvm(ctx.getResult()));
		assertEquals(450L,evalL(ctx,"(balance act)"));
	}
	
	@Test
	public void testOfferAndRefund() {
		Context ctx=context();
		ctx=step(ctx,"(def act (deploy '(do (defn receive-coin ^{:callable? true} [sender amount refund data] (accept amount) (transfer *caller* refund)))))");

		// send via contract call
		ctx=step(ctx,"(call act 666 (receive-coin *address* 350  100 nil))");
		assertEquals(100L, (long)RT.jvm(ctx.getResult()));
		assertEquals(250L,evalL(ctx,"(balance act)"));
	}

	@Test
	public void testCall() {
		Context ctx = step("(def ctr (deploy '(do (defn foo ^{:callable? true} [] :bar))))");

		assertEquals(Keywords.BAR,eval(ctx,"(call ctr (foo))")); // regular call
		assertEquals(Keywords.BAR,eval(ctx,"(call ctr 100 (foo))")); // call with offer

		assertArityError(step(ctx, "(call)"));
		assertArityError(step(ctx, "(call 12)"));

		assertCastError(step(ctx, "(call ctr :foo (bad-fn 1 2))")); // cast fail on offered value
		assertStateError(step(ctx, "(call ctr 12 (bad-fn 1 2))")); // bad function

		// bad format for call
		assertCompileError(step(ctx,"(call ctr foo)"));
		assertCompileError(step(ctx,"(call ctr [foo])")); // not a list
		assertCompileError(step(ctx,"(call ctr #{some-func 42})")); // See #135

		assertNobodyError(step(ctx, "(call #666666 12 (bad-fn 1 2))")); // bad actor
		assertArgumentError(step(ctx, "(call ctr -12 (bad-fn 1 2))")); // negative offer

		// bad actor takes precedence over bad offer
		assertNobodyError(step(ctx, "(call #666666 -12 (bad-fn 1 2))"));

	}

	@Test
	public void testCallSelf() {
		Context ctx = step("(def ctr (deploy '(do (defn foo ^{:callable? true} [] (call *address* (bar))) (defn bar ^{:callable? true} [] (= *address* *caller*)))))");
		Address actor=ctx.getResult();
		
		assertTrue(evalB(ctx, "(call ctr (foo))")); // nested call to same actor
		assertFalse(evalB(ctx, "(call ctr (bar))")); // call from hero only

		assertEquals(Sets.of(Symbols.FOO,Symbols.BAR),ctx.getAccountStatus(actor).getCallableFunctions());
	}
	
	@Test 
	public void testCallables() {
		assertSame(Sets.empty(),context().getAccountStatus().getCallableFunctions());
	}


	@Test
	public void testCallStar() {
		Context ctx = step("(def ctr (deploy '(do :foo (defn f ^{:callable? true} [x] (inc x)) )))");

		assertEquals(9L,evalL(ctx, "(call* ctr 0 'f 8)"));
		assertCastError(step(ctx, "(call* ctr 0 :f 8)")); // cast fail on keyword function name

		assertArityError(step(ctx, "(call*)"));
		assertArityError(step(ctx, "(call* 12)"));
		assertArityError(step(ctx, "(call* 1 2)")); // no function

		assertCastError(step(ctx, "(call* ctr :foo 'bad-fn 1 2)")); // cast fail on offered value
		assertStateError(step(ctx, "(call* ctr 12 'bad-fn 1 2)")); // bad function
	}

	@Test
	public void testDeploy() {
		Context ctx = step("(def ctr (deploy '(fn [] :foo :bar)))");
		Address ca = ctx.getResult();
		assertNotNull(ca);
		AccountStatus as = ctx.getAccountStatus(ca);
		assertNotNull(as);
		assertEquals(ca, eval(ctx, "ctr")); // defined address in environment
		
		// deployer is *caller* in deployed code
		assertTrue(evalB("(= *address* (let [ad (deploy '(def c *caller*))] ad/c))"));

		// initial deployed state
		assertEquals(0L, as.getBalance());

		// double-deploy should get different addresses
		assertFalse(evalB("(let [cfn '(do 1)] (= (deploy cfn) (deploy cfn)))"));
		
		// Deploy failure should propagate out 
		assertCastError(step("(deploy '(+ 1 :foo))"));
		assertArityError(step("(deploy '(count))"));
		assertCastError(step("(deploy '(def a :foo) '(address a))"));
		
		// Arity checks
		assertArityError(step("(deploy)"));
	}

	@Test
	public void testActorQ() {
		Context ctx = step("(def ctr (deploy '(fn [] :foo :bar)))");
		Address ctr=ctx.getResult();

		assertTrue(evalB(ctx,"(actor? ctr)"));

		assertTrue(evalB(ctx,"(actor? (address ctr))"));

		// hero address is not an Actor
		assertFalse(evalB(ctx,"(actor? *address*)"));

		// Not an Actor Address, even though the given values string refer to one.
		assertFalse(evalB(ctx,"(actor? \""+ctr.toHexString()+"\")"));
		assertFalse(evalB(ctx,"(actor? 8)"));

		// Above are OK if cast to addresses explicitly
		assertTrue(evalB(ctx,"(actor? (address \""+ctr.toHexString()+"\"))"));
		assertTrue(evalB(ctx,"(actor? (address 8))"));

		assertFalse(evalB(ctx,"(actor? :foo)"));
		assertFalse(evalB(ctx,"(actor? nil)"));
		assertFalse(evalB(ctx,"(actor? [ctr])"));
		assertFalse(evalB(ctx,"(actor? 'ctr)"));

		// non-existant account is not an actor
		assertFalse(evalB(ctx,"(actor? (address 99999999))"));
		assertFalse(evalB(ctx,"(actor? #4512)"));
		assertFalse(evalB(ctx,"(actor? -1234)"));

		assertArityError(step("(actor?)"));
		assertArityError(step("(actor? :foo :bar)")); // ARITY before CAST

	}

	@Test
	public void testAccountQ() {
		// a new Actor is an account
		Context ctx = step("(def ctr (deploy '(fn [] :foo :bar)))");
		assertTrue(evalB(ctx,"(account? ctr)"));

		// standard actors are accounts
		assertTrue(evalB(ctx,"(account? *registry*)"));

		// standard actors are accounts
		assertTrue(evalB(ctx,"(account? "+HERO+")"));

		// a fake address
		assertFalse(evalB(ctx,"(account? 77777777)"));

		// String with and without hex. See Issue #90
		assertFalse(evalB(ctx,"(account? \"deadbeef\")"));
		assertFalse(evalB(ctx,"(account? \"zzz\")"));

		// a blob that is wrong length for an address. See Issue #90
		assertFalse(evalB(ctx,"(account? 0x1234)"));

		// a blob that actually refers to a valid account. But it isn't an Address...
		assertFalse(evalB(ctx,"(account? 0x0000000000000008)"));

		// current hero address is an account
		assertTrue(evalB(ctx,"(account? *address*)"));

		assertFalse(evalB("(account? :foo)"));
		assertFalse(evalB("(account? nil)"));
		assertFalse(evalB("(account? [])"));
		assertFalse(evalB("(account? 'foo)"));

		assertArityError(step("(account?)"));
		assertArityError(step("(account? 1 2)")); // ARITY before CAST
	}

	@Test
	public void testAccount() {
		// a new Actor is an account
		Context ctx = step("(def ctr (deploy '(fn [] :foo :bar)))");
		AccountStatus as=eval(ctx,"(account ctr)");
		assertNotNull(as);

		// standard actors are accounts
		assertTrue(eval("(account *registry*)") instanceof AccountStatus);

		// a non-existent address returns null
		assertNull(eval(ctx,"(account #77777777)"));

		// current address is an account, and its balance is correct
		assertTrue(evalB("(= *balance* (:balance (account *address*)))"));

		// invalid addresses
		assertCastError(step("(account nil)"));
		assertCastError(step("(account 8)"));
		assertCastError(step("(account :foo)"));
		assertCastError(step("(account [])"));
		assertCastError(step("(account 'foo)"));

		assertArityError(step("(account)"));
		assertArityError(step("(account 1 2)")); // ARITY before CAST
	}

	@Test
	public void testSetKey() {
		Context ctx=context();

		ctx=step(ctx,"(set-key 0x0000000000000000000000000000000000000000000000000000000000000000)");
		assertEquals(Samples.ZERO_ACCOUNTKEY,ctx.getResult());
		assertEquals(eval(ctx,"*key*"),Samples.ZERO_ACCOUNTKEY);

		ctx=step(ctx,"(set-key nil)");
		assertNull(ctx.getResult());
		assertNull(eval(ctx,"*key*"));

		ctx=step(ctx,"(set-key "+InitTest.HERO_KEY+")");
		assertEquals(InitTest.HERO_KEY,ctx.getResult());
		assertEquals(InitTest.HERO_KEY,eval(ctx,"*key*"));
		
		assertEquals(true, evalB("(do "
				+ "    (def k 0x0000000000000000000000000000000000000000000000000000000000000000)"
				+ "    (def a (deploy `(set-key ~k)))"
				+ "    (= k (:key (account a))))"));
		
		assertCastError(step("(set-key :foo)"));
		assertArgumentError(step("(set-key 0x00)"));
		
		assertArityError(step("(set-key)"));
		assertArityError(step("(set-key 0x 0x)")); // arity before cast
	}

	@Test
	public void testSetAllowance() {

		// zero price for unchanged allowance
		assertEquals(0L, evalL("(set-memory *memory*)"));

		// sell whole allowance, should zero memory
		assertEquals(0L, evalL("(do (set-memory 0) *memory*)"));

		// buy allowance reduces balance
		assertTrue(evalL("(let [b *balance*] (set-memory (inc *memory*)) (- *balance* b))")<0);

		// sell allowance increases balance
		assertTrue(evalL("(let [b *balance*] (set-memory (dec *memory*)) (- *balance* b))")>0);

		// trying to buy too much is a funds error
		assertFundsError(step("(set-memory 1000000000000000000)"));

		// trying to set memory negative is an ARGUMENT error
		assertArgumentError(step("(set-memory -1)"));
		assertArgumentError(step("(set-memory -10000000)"));
		assertArgumentError(step("(set-memory "+Long.MIN_VALUE+")"));

		assertCastError(step("(set-memory :foo)"));
		assertCastError(step("(set-memory nil)"));

		assertArityError(step("(set-memory)"));
		assertArityError(step("(set-memory 1 2)"));

	}

	@Test
	public void testTransferMemory() {
		long ALL=Constants.INITIAL_ACCOUNT_ALLOWANCE;
		assertEquals(ALL, evalL(Symbols.STAR_MEMORY.toString()));

		{
			Context ctx=step("(transfer-memory *address* 1337)");
			assertCVMEquals(1337L, ctx.getResult());
			assertEquals(ALL, ctx.getAccountStatus(HERO).getMemory());
		}

		assertEquals(ALL-1337, step("(transfer-memory "+VILLAIN+" 1337)").getAccountStatus(HERO).getMemory());

		assertEquals(0L, step("(transfer-memory "+VILLAIN+" "+ALL+")").getAccountStatus(HERO).getMemory());

		assertArgumentError(step("(transfer-memory *address* -1000)"));
		assertNobodyError(step("(transfer-memory #88888888 0)"));

		assertMemoryError(step("(transfer-memory *address* (+ 1 "+ALL+"))"));

		// check bad arg types
		assertCastError(step("(transfer-memory -1000 1000)"));
		assertCastError(step("(transfer-memory *address* :foo)"));

		// check bad arities
		assertArityError(step("(transfer-memory -1000)"));
		assertArityError(step("(transfer-memory)"));
		assertArityError(step("(transfer-memory *address* 100 100)"));

	}

	@Test
	public void testTransferToActor() {
		// SECURITY: be careful with these tests
		Address CORE=Init.CORE_ADDRESS;

		// should fail transferring to an account with no receive-coins export
		assertStateError(step("(transfer "+CORE+" 1337)"));

		{ // transfer to an Actor that accepts everything
			Context ctx=step("(deploy '(do (defn receive-coin ^{:callable? true} [sender amount data] (accept amount))))");
			Address receiver=(Address) ctx.getResult();

			ctx=step(ctx,"(transfer "+receiver.toString()+" 100)");
			assertCVMEquals(100L,ctx.getResult());
			assertCVMEquals(100L,ctx.getBalance(receiver));
		}

		{ // transfer to an Actor that accepts nothing
			Context ctx=step("(deploy '(do (defn receive-coin ^{:callable? true} [sender amount data] (accept 0))))");
			Address receiver=(Address) ctx.getResult();

			ctx=step(ctx,"(transfer "+receiver.toString()+" 100)");
			assertCVMEquals(0L,ctx.getResult());
			assertCVMEquals(0L,ctx.getBalance(receiver));
		}

		{ // transfer to an Actor that accepts half
			Context ctx=step("(deploy '(do (defn receive-coin ^{:callable? true} [sender amount data] (accept (long (/ amount 2))))))");
			Address receiver=(Address) ctx.getResult();

			// should be OK with a Blob Address
			ctx=step(ctx,"(transfer "+receiver+" 100)");
			assertCVMEquals(50L,ctx.getResult());
			assertCVMEquals(50L,ctx.getBalance(receiver));
		}


	}

	@Test
	public void testTransfer() {
		// SECURITY: don't mess with these tests

		// balance at start of transaction
		long BAL = HERO_BALANCE;

		// transfer to self. Note juice already accounted for in context.
		assertEquals(1337L, evalL("(transfer *address* 1337)")); // should return transfer amount
		assertEquals(BAL, step("(transfer *address* 1337)").getBalance(HERO));

		// transfers to an address that doesn't exist
		{
			Context nc1=step("(transfer (address 666666) 1337)");
			assertNobodyError(nc1);
		}


		// String representing a new User Address
		Context ctx=step("(create-account "+InitTest.HERO_KEYPAIR.getAccountKey()+")");
		Address naddr=ctx.getResult();

		// transfers to a new address
		{
			Context nc1=step(ctx,"(transfer "+naddr+" 1337)");
			assertCVMEquals(1337L, nc1.getResult());
			assertEquals(BAL - 1337,nc1.getBalance(HERO));
			assertEquals(1337L, evalL(nc1,"(balance "+naddr+")"));
		}

		assertTrue(() -> evalB(ctx,"(let [a "+naddr+"]"
				+ "   (not (= *balance* (transfer a 1337))))"));

		// Should never be possible to transfer negative amounts
		assertArgumentError(step("(transfer *address* -1000)"));
		assertArgumentError(step("(transfer "+naddr+" -1)"));

		// Long.MAX_VALUE is too big for an Amount
		assertArgumentError(step("(transfer *address* 9223372036854775807)")); // Long.MAX_VALUE
		// BigInteger is too big for an Amount
		assertCastError(step("(transfer *address* 9223372036854775808)")); // Long.MAX_VALUE+1

		assertFundsError(step("(transfer *address* 999999999999999999)"));

		assertCastError(step("(transfer :foo 1)"));
		assertCastError(step("(transfer *address* :foo)"));

		assertArityError(step("(transfer)"));
		assertArityError(step("(transfer 1)"));
		assertArityError(step("(transfer 1 2 3)"));
	}

	@Test
	public void testStake() {
		Context ctx=step(context(),"(def my-peer 0x"+InitTest.FIRST_PEER_KEY.toHexString()+")");
		AccountKey MY_PEER=InitTest.FIRST_PEER_KEY;
		long PS=ctx.getState().getPeer(InitTest.FIRST_PEER_KEY).getPeerStake();

		{
			// simple case of staking 1000000 on first peer of the realm
			Context rc=step(ctx,"(stake my-peer 1000000)");
			assertNotError(rc);
			assertEquals(PS+1000000,rc.getState().getPeer(MY_PEER).getTotalStake());
			assertEquals(1000000,rc.getState().getPeer(MY_PEER).getDelegatedStake());
			assertEquals(Constants.MAX_SUPPLY, rc.getState().computeTotalFunds());
		}

		// staking on an account key that isn't a peer
		assertStateError(step(ctx,"(stake 0x1234567812345678123456781234567812345678123456781234567812345678 1234)"));

		// staking on an address
		assertCastError(step(ctx,"(stake *address* 1234)"));
		
		// staking on an invalid account key (wrong length)
		assertArgumentError(step(ctx,"(stake 0x12 1234)"));

		// bad arg types
		assertCastError(step(ctx,"(stake :foo 1234)"));
		assertCastError(step(ctx,"(stake my-peer :foo)"));
		assertCastError(step(ctx,"(stake my-peer nil)"));

		assertArityError(step(ctx,"(stake my-peer)"));
		assertArityError(step(ctx,"(stake my-peer 1000 :foo)"));
	}

	@Test
	public void testSetPeerData() {
		String newHostname = "new_hostname:1234";
		Context ctx=context();
		ctx=ctx.forkWithAddress(InitTest.FIRST_PEER_ADDRESS);
		AccountKey peerKey=InitTest.FIRST_PEER_KEY;
		ctx=step(ctx,"(def peer-key "+peerKey+")");
		{
			// make sure we are using the FIRST_PEER address
			ctx=step(ctx,"(set-peer-data peer-key {:url \"" + newHostname + "\"})");
			assertNotError(ctx);
			assertEquals(newHostname,ctx.getState().getPeer(InitTest.FIRST_PEER_KEY).getHostname().toString());
			ctx=step(ctx,"(set-peer-data peer-key {})");
			assertNotError(ctx);
			// should clear data
			assertNull(ctx.getState().getPeer(InitTest.FIRST_PEER_KEY).getHostname());
        }

		assertNull(eval(ctx, "(set-peer-data peer-key nil)"));
		
		assertCastError(step(ctx, "(set-peer-data peer-key :fail)"));
		assertCastError(step(ctx, "(set-peer-data peer-key (blob-map))"));

		// Try to hijack with an account that isn't the first Peer
		ctx=ctx.forkWithAddress(HERO.offset(2));
		{
			newHostname = "set-key-hijack";
			ctx=step(ctx,"(do (set-key "+peerKey+") (set-peer-data "+peerKey+" {:url \"" + newHostname + "\"}))");
			assertStateError(ctx);
		}
		
		ctx=ctx.forkWithAddress(InitTest.FIRST_PEER_ADDRESS);
		assertCastError(step(ctx,"(set-peer-data peer-key 0x1234567812345678123456781234567812345678123456781234567812345678)"));
		assertCastError(step(ctx,"(set-peer-data peer-key :bad-key)"));
		assertCastError(step(ctx,"(set-peer-data 12 {})"));
		assertCastError(step(ctx,"(set-peer-data nil {})"));
		
		assertArityError(step(ctx,"(set-peer-data)"));
		assertArityError(step(ctx,"(set-peer-data peer-key)"));
		assertArityError(step(ctx,"(set-peer-data peer-key {:url \"test\" :bad-key 1234} 2)"));
	}

	@Test
	public void testCreatePeer() {
		// Kep Pair for new Peer
		AKeyPair kp=AKeyPair.createSeeded(4583763);
		
		Context ctx=step(context(),"(def hero-peer 0x"+kp.getAccountKey().toHexString()+")");
		ctx=ctx.forkWithAddress(InitTest.HERO);

		Context peerCTX = step(ctx,"(create-peer hero-peer 1000)");
		// create a peer based on the HERO address and public key
		assertNotError(peerCTX);

		// create a peer again on the same peer key and address
		assertError(step(peerCTX,"(create-peer hero-peer 1000)"));

		// create a new peer with zero stake
		assertError(step(ctx,"(create-peer hero-peer 0)"));

		// creating a peer on an account key that isn't the hero account key
		// TODO: what should happen here?
		//assertArgumentError(step(ctx,"(create-peer 0x1234567812345678123456781234567812345678123456781234567812345678 1234)"));

		// creating a peer with invalid account key
		assertCastError(step(ctx,"(create-peer *address* 1234)"));

		// bad arg types
		assertCastError(step(ctx,"(create-peer :foo 1234)"));
		assertCastError(step(ctx,"(create-peer hero-peer :foo)"));
		assertCastError(step(ctx,"(create-peer hero-peer nil)"));

		assertArityError(step(ctx,"(create-peer hero-peer)"));
		assertArityError(step(ctx,"(create-peer hero-peer 1000 :foo)"));
	}
	
	@Test 
	public void testCreatePeerRegression() {
		assertJuiceError(step("(create-peer 0x42ae93b185bd2ba64fd9b0304fec81a4d4809221a5b68de4da041b48c85bcc2e *balance*)"));
		assertFundsError(step("(create-peer 0x42ae93b185bd2ba64fd9b0304fec81a4d4809221a5b68de4da041b48c85bcc2e (inc *balance*))"));
		
	}

	@Test
	public void testNumericComparisons() {
		assertFalse(evalB("(== 1 2)"));
		assertFalse(evalB("(== 1.0 2.0)"));

		assertTrue(evalB("(== 3 3)"));
		assertTrue(evalB("(== 0.0 -0.0)")); // IEE754 defines as equals
		assertFalse(evalB("(= 0.0 -0.0)")); // Not identical values
		assertTrue(evalB("(== 7.0000 7.0)"));
		assertTrue(evalB("(== -1.00E0 -1.0)"));
		assertTrue(evalB("(== 7 7.0)"));
		assertTrue(evalB("(== 7.0 7)"));

		assertTrue(evalB("(<)"));
		assertTrue(evalB("(>)"));
		assertTrue(evalB("(< 1 2)"));
		assertTrue(evalB("(<= 1 2)"));
		assertTrue(evalB("(<= 1 2 6)"));
		assertFalse(evalB("(< 2 2)"));
		assertFalse(evalB("(< 3 2)"));
		assertTrue(evalB("(<= 2.0 2.0)"));
		assertTrue(evalB("(>= 2.0 2.0)"));
		assertFalse(evalB("(> 1 2)"));
		assertFalse(evalB("(> 3.0 3.0)"));
		assertFalse(evalB("(> 3.0 1.0 7.0)"));
		assertTrue(evalB("(>= 3.0 3.0)"));
		
		// Big Integers
		assertTrue(evalB("(== 9223372036854775808 9223372036854775808)"));
		assertFalse(evalB("(== 9223372036854775808 -9223372036854775808)")); // check no long overflow
		assertFalse(evalB("(< 9223372036854775808 -9223372036854775808)")); 
		assertTrue(evalB("(>= 9223372036854775808 -9223372036854775808)")); 
		assertTrue(evalB("(> 9999999999999999999999 9223372036854775808  2 -9223372036854775808)")); 

		// assertTrue(evalB("(>= \\b \\a)")); // TODO: do we want this to work?

		// juice should go down in order of evaluation
		assertTrue(evalB("(< *juice* *juice* *juice*)"));

		assertCastError(step("(> :foo)"));
		assertCastError(step("(> :foo :bar)"));
		assertCastError(step("(> [] [1])"));
	}

	@Test
	public void testMin() {
		assertEquals(1L, evalL("(min 1 2 3 4)"));
		assertEquals(7L, evalL("(min 7)"));
		assertEquals(2L, evalL("(min 4 3 2)"));
		assertEquals(1L, evalL("(min 1 2)"));

		assertEquals(1L, evalL("("+Init.CORE_ADDRESS+"/min 1 2)"));

		assertEquals(1.0, evalD("(min 2.0 1.0 3.0)"));
		assertEquals(CVMDouble.NaN, eval("(min 2.0 ##NaN -0.0 ##Inf)"));
		assertEquals(CVMDouble.NaN, eval("(min ##NaN)"));

		// See issue https://github.com/Convex-Dev/convex/issues/99
		assertEquals(CVMDouble.NaN, eval("(min ##NaN 1 ##NaN)"));
		
		// min and max should preserve sign on zero
		// See: https://github.com/Convex-Dev/convex/issues/366
		assertEquals(CVMDouble.NEGATIVE_ZERO,eval("(min -0.0)"));
		
		assertEquals(-0.0, evalD("(min -0.0 0.0)"));
		assertEquals(0.0, evalD("(min 0.0 -0.0)"));
		
		assertEquals(10L, evalL("(min 10 1000 1000000000000000000000 1e30)"));
		assertEquals(CVMBigInteger.parse("1000000000000000000000"), eval("(min 1000000000000000000000 5000000000000000000000)"));

		assertCastError(step("(min true)"));
		assertCastError(step("(min \\c)"));
		assertCastError(step("(min ##NaN true)"));
		assertCastError(step("(min true ##NaN)"));

		// #NaNs should get ignored
		assertEquals(CVMDouble.NaN,eval("(min ##NaN 42)"));
		assertEquals(CVMDouble.NaN,eval("(min 42 ##NaN)"));

		assertArityError(step("(min)"));
	}

	@Test
	public void testMax() {
		assertEquals(4L, evalL("(max 1 2 3 4)"));
		assertEquals(CVMDouble.NaN, eval("(max 1 ##-Inf 3 ##NaN 4)"));
		assertEquals(7L, evalL("(max 7)"));
		assertEquals(4.0, evalD("(max 4.0 3 2)"));
		assertEquals(CVMDouble.NaN, eval("(max 1 2.5 ##NaN)"));
		
		assertEquals(-0.0, evalD("(max -0.0 0.0)"));
		assertEquals(0.0, evalD("(max 0.0 -0.0)"));

		assertEquals(1e30, evalD("(max 1e30 10000000000000000000000000)"));

		assertArityError(step("(max)"));
		
		assertEquals(CVMDouble.NaN, eval("(max ##NaN 1)"));
		
		// min and max should preserve sign on zero
		// See: https://github.com/Convex-Dev/convex/issues/366
		assertEquals(CVMDouble.NEGATIVE_ZERO,eval("(max -0.0)"));

	}

	@Test
	public void testPow() {
		assertEquals(4.0, evalD("(pow 2 2)"));
		
		// NaN handling
		assertEquals(CVMDouble.NaN, eval("(pow 2 ##NaN)"));
		assertEquals(CVMDouble.NaN, eval("(pow ##NaN 2)"));

		assertCastError(step("(pow :a 7)"));
		assertCastError(step("(pow 7 :a)"));

		assertArityError(step("(pow)"));
		assertArityError(step("(pow 1)"));
		assertArityError(step("(pow 1 2 3)"));
	}

	@Test
	public void testQuot() {
		assertEquals(0L, evalL("(quot 4 10)"));
		assertEquals(2L, evalL("(quot 10 4)"));
		assertEquals(-2L, evalL("(quot -10 4)"));

		// Division by zero
		assertArgumentError(step("(quot 2 0)"));
		assertArgumentError(step("(quot 0 0)"));
		
		assertCastError(step("(quot :a 7)"));
		assertCastError(step("(quot 7 nil)"));

		assertArityError(step("(quot)"));
		assertArityError(step("(quot 1)"));
		assertArityError(step("(quot 1 2 3)"));
	}
	
	@Test
	public void testDiv() {
		assertEquals(0L, evalL("(div 4 10)"));
		assertEquals(-4L, evalL("(div -10 3)"));
		// TODO: more tests
	}

	@Test
	public void testMod() {
		assertEquals(4L, evalL("(mod 4 10)"));
		assertEquals(4L, evalL("(mod 14 10)"));
		assertEquals(6L, evalL("(mod -1 7)"));
		assertEquals(0L, evalL("(mod 7 7)"));
		assertEquals(0L, evalL("(mod 0 -1)"));

		assertEquals(6L, evalL("(mod -1 -7)"));
		assertEquals(6L, evalL("(mod 10000000000000000000000006 10000000000000000000000000)"));
		assertEquals(CVMBigInteger.parse("9999999999999999999999999"), eval("(mod  9999999999999999999999999 10000000000000000000000000)"));

		assertEquals(1L, evalL("(mod 10000000000000000000000001 2)"));
		assertEquals(1L, evalL("(mod 10000000000000000000000001 -2)")); // TODO: check?

		assertEquals(CVMBigInteger.parse("999999999999999999999999"), eval("(mod -1 1000000000000000000000000)")); // TODO: check?

		// Division by zero
		assertArgumentError(step("(mod -2 0)"));
		assertArgumentError(step("(mod 0 0)"));
		assertArgumentError(step("(mod 10 0)"));
		assertArgumentError(step("(mod 9999999999999999999999999999999 0)"));

		assertCastError(step("(mod :a 7)"));
		assertCastError(step("(mod 7 nil)"));

		assertArityError(step("(mod)"));
		assertArityError(step("(mod 1)"));
		assertArityError(step("(mod 1 2 3)"));
	}

	@Test
	public void testRem() {
		assertEquals(4L, evalL("(rem 4 10)"));
		assertEquals(4L, evalL("(rem 14 10)"));
		assertEquals(-1L, evalL("(rem -1 7)"));
		assertEquals(0L, evalL("(rem 7 7)"));
		assertEquals(0L, evalL("(rem 0 -1)"));

		assertEquals(-1L, evalL("(rem -1 -7)"));

		assertArgumentError(step("(rem 10 0)"));

		assertCastError(step("(rem :a 7)"));
		assertCastError(step("(rem 7 nil)"));

		assertArityError(step("(rem)"));
		assertArityError(step("(rem 1)"));
		assertArityError(step("(rem 1 2 3)"));
	}

	@Test
	public void testExp() {
		assertEquals(1.0, evalD("(exp 0)"));
		assertEquals(1.0, evalD("(exp 0.0)"));
		assertEquals(1.0, evalD("(exp -0)"));
		assertEquals(StrictMath.exp(1.0), evalD("(exp 1)"));
		
		assertEquals(CVMDouble.ZERO, eval("(exp ##-Inf)"));
		assertEquals(CVMDouble.ONE, eval("(exp -0.0)"));
		assertEquals(CVMDouble.POSITIVE_INFINITY, eval("(exp ##Inf)"));
		assertEquals(CVMDouble.NaN, eval("(exp ##NaN)"));

		assertCastError(step("(exp :a)"));
		assertCastError(step("(exp #3)"));
		assertCastError(step("(exp nil)"));

		assertArityError(step("(exp)"));
		assertArityError(step("(exp 1 2)"));
	}

	@Test
	public void testHash() {
		assertEquals(Hash.fromHex("a7ffc6f8bf1ed76651c14756a061d662f580ff4de43b49fa82d80a4b80f8434a"),eval("(hash 0x)"));

		assertEquals(Hash.NULL_HASH, eval("(hash (encoding nil))"));
		assertEquals(Hash.TRUE_HASH, eval("(hash (encoding true))"));
		assertEquals(Maps.empty().getHash(), eval("(hash (encoding {}))"));

		assertTrue(evalB("(= (hash 0x12) (hash 0x12))"));
		assertTrue(evalB("(blob? (hash (encoding 42)))")); // Should be a Blob
		
		assertCastError(step("(hash nil)"));
		assertCastError(step("(hash :foo)"));
		assertCastError(step("(hash #44)")); // specialised blobs don't implicitly cast to Blob
		
		assertArityError(step("(hash)"));
		assertArityError(step("(hash nil nil)"));
	}

	@Test
	public void testCount() {
		assertEquals(0L, evalL("(count nil)"));
		assertEquals(0L, evalL("(count [])"));
		assertEquals(0L, evalL("(count ())"));
		assertEquals(0L, evalL("(count 0x)"));
		assertEquals(0L, evalL("(count \"\")"));
		assertEquals(2L, evalL("(count (list :foo :bar))"));
		assertEquals(2L, evalL("(count #{1 2 2})"));
		assertEquals(3L, evalL("(count [1 2 3])"));
		assertEquals(4L, evalL("(count 0xcafebabe)"));
		
		// TODO: do we want this? Address is blob-like when used in indexes
		assertEquals(8L, evalL("(count #123)"));

		// Count of a map is the number of entries
		assertEquals(2L, evalL("(count {1 2 2 3})"));

		// non-countable things fail with CAST
		assertCastError(step("(count 1)"));
		assertCastError(step("(count :foo)"));

		assertArityError(step("(count)"));
		assertArityError(step("(count 1 2)"));
	}

	@Test
	public void testCompile() {
		assertEquals(Constant.of(1L), eval("(compile 1)"));

		assertEquals(Constant.of(1L), eval("(compile 1)"));
		assertEquals(Constant.of(null), eval("(compile nil)"));
		assertEquals(Invoke.class, eval("(compile '(+ 1 2))").getClass());
		assertEquals(Do.class, eval("(compile '(do a b))").getClass());

		assertArityError(step("(compile)"));
		assertArityError(step("(compile 1 2)"));
		assertArityError(step("(if 1)"));
	}

	private AVector<ACell> ALL_PREDICATES = Vectors
			.create(Core.ENVIRONMENT.filterValues(e -> e instanceof CorePred).values());
	private AVector<ACell> ALL_CORE_DEFS = Vectors
			.create(Core.ENVIRONMENT.filterValues(e -> e instanceof ICoreDef).values());

	@Test
	public void testPredArity() {
		// Every predicate should require arity 1, and never fail otherwise
		AVector<ACell> pvals = ALL_PREDICATES;
		assertFalse(pvals.isEmpty());
		Context C = context();
		ACell[] a0 = new ACell[0];
		ACell[] a1 = new ACell[1];
		ACell[] a2 = new ACell[2];
		for (ACell p : pvals) {
			CorePred pred = (CorePred) p;
			assertTrue(RT.isBoolean(pred.invoke(C, a1).getResult()), "Predicate: " + pred);
			assertArityError(pred.invoke(C, a0));
			assertArityError(pred.invoke(C, a2));
		}
	}

	@Test
	public void testCoreDefSymbols() throws BadFormatException {
		AVector<ACell> vals = ALL_CORE_DEFS;
		assertFalse(vals.isEmpty());
		for (ACell def : vals) {
			Symbol sym = ((ICoreDef)def).getSymbol();
			ACell v=Core.ENVIRONMENT.get(sym);
			assertSame(def, v);

			Blob b = Format.encodedBlob(def);
			assertSame(def, Format.read(b));

			AHashMap<ACell,ACell> meta= Core.METADATA.get(sym);
			assertNotNull(meta,"Missing metadata for core symbol: "+sym);
		    ACell dobj=meta.get(Keywords.DOC);
		    assertNotNull(dobj,"No documentation found for core definition: "+sym);
		}
	}

	@Test
	public void testNilPred() {
		assertTrue(evalB("(nil? nil)"));
		assertFalse(evalB("(nil? 1)"));
		assertFalse(evalB("(nil? [])"));
	}

	@Test
	public void testListPred() {
		assertFalse(evalB("(list? nil)")); // not a list, even though it casts to one
		assertFalse(evalB("(list? 1)"));
		assertTrue(evalB("(list? '())"));
		assertTrue(evalB("(list? '(3 4 5))"));
		assertFalse(evalB("(list? [1 2 3])"));
		assertFalse(evalB("(list? {1 2})"));
	}

	@Test
	public void testVectorPred() {
		assertFalse(evalB("(vector? nil)"));
		assertFalse(evalB("(vector? 1)"));
		assertTrue(evalB("(vector? [])"));
		assertFalse(evalB("(vector? '(3 4 5))"));
		assertTrue(evalB("(vector? [1 2 3])"));
		assertTrue(evalB("(vector? (first {1 2 3 4}))"));
		assertFalse(evalB("(vector? {1 2})"));
	}

	@Test
	public void testSetPred() {
		assertFalse(evalB("(set? nil)"));
		assertFalse(evalB("(set? 1)"));
		assertTrue(evalB("(set? #{})"));
		assertFalse(evalB("(set? '(3 4 5))"));
		assertTrue(evalB("(set? #{1 2 3})"));
		assertFalse(evalB("(set? {1 2})"));
	}

	@Test
	public void testMapPred() {
		assertFalse(evalB("(map? nil)"));
		assertFalse(evalB("(map? 1)"));
		assertTrue(evalB("(map? {})"));
		assertFalse(evalB("(map? '(3 4 5))"));
		assertTrue(evalB("(map? {1 2 3 4})"));
		assertFalse(evalB("(map? #{1 2})"));
	}

	@Test
	public void testCollPred() {
		assertFalse(evalB("(coll? nil)"));
		assertFalse(evalB("(coll? 1)"));
		assertFalse(evalB("(coll? :foo)"));
		assertTrue(evalB("(coll? {})"));
		assertTrue(evalB("(coll? [])"));
		assertTrue(evalB("(coll? ())"));
		assertTrue(evalB("(coll? '())"));
		assertTrue(evalB("(coll? #{})"));
		assertTrue(evalB("(coll? '(3 4 5))"));
		assertTrue(evalB("(coll? [:foo :bar])"));
		assertTrue(evalB("(coll? {1 2 3 4})"));
		assertTrue(evalB("(coll? #{1 2})"));
		assertTrue(evalB("(coll? (blob-map))"));
		assertTrue(evalB("(coll? (blob-map 0x 0x))"));
	}

	@Test
	public void testEmptyPred() {
		assertTrue(evalB("(empty? nil)"));
		assertTrue(evalB("(empty? {})"));
		assertTrue(evalB("(empty? [])"));
		assertTrue(evalB("(empty? ())"));
		assertTrue(evalB("(empty? (blob-map))"));
		assertTrue(evalB("(empty? \"\")"));
		assertTrue(evalB("(empty? #{})"));
		
		assertFalse(evalB("(empty? 0x1234)"));
		assertFalse(evalB("(empty? {1 2})"));
		assertFalse(evalB("(empty? (blob-map 0x 0x1234))"));
		assertFalse(evalB("(empty? [ 3])"));
		assertFalse(evalB("(empty? '(foo))"));
		assertFalse(evalB("(empty? #{[]})"));
	}

	@Test
	public void testSymbolPred() {
		assertTrue(evalB("(symbol? 'foo)"));
		assertTrue(evalB("(symbol? (symbol :bar))"));
		assertFalse(evalB("(symbol? nil)"));
		assertFalse(evalB("(symbol? 1)"));
		assertFalse(evalB("(symbol? ['foo])"));
	}

	@Test
	public void testKeywordPred() {
		assertTrue(evalB("(keyword? :foo)"));
		assertTrue(evalB("(keyword? (keyword 'bar))"));
		assertFalse(evalB("(keyword? nil)"));
		assertFalse(evalB("(keyword? 1)"));
		assertFalse(evalB("(keyword? [:foo])"));
	}

	@Test
	public void testAddressPred() {
		assertTrue(evalB("(address? *origin*)"));
		assertFalse(evalB("(address? nil)"));
		assertFalse(evalB("(address? 1)"));
		assertFalse(evalB("(address? \"0a1b2c3d\")"));
		assertFalse(evalB("(address? (blob *origin*))"));
	}

	@Test
	public void testBlobPred() {
		assertTrue(evalB("(blob? (blob *origin*))"));
		assertTrue(evalB("(blob? 0xFF)"));
		assertTrue(evalB("(blob? (blob 0x17))"));
		assertTrue(evalB("(blob? (hash (encoding *state*)))")); // HAsh
		assertTrue(evalB("(blob? *key*)")); // AccountKey

		assertFalse(evalB("(blob? 17)"));
		assertFalse(evalB("(blob? nil)"));
		assertFalse(evalB("(blob? *address*)"));
	}

	@Test
	public void testLongPred() {
		assertTrue(evalB("(long? 1)"));
		assertTrue(evalB("(long? (long *balance*))")); // TODO: is this sane?
		assertTrue(evalB("(long? (byte 1))"));
		assertFalse(evalB("(long? nil)"));
		assertFalse(evalB("(long? 0xFF)"));
		assertFalse(evalB("(long? [1 2])"));
		
		assertTrue(evalB("(long? 9223372036854775807)"));
		assertFalse(evalB("(long? 9223372036854775808)"));
		assertTrue(evalB("(long? -9223372036854775808)"));
		assertFalse(evalB("(long? -9223372036854775809)"));
	}

	@Test
	public void testStrPred() {
		assertTrue(evalB("(str? (name :foo))"));
		assertTrue(evalB("(str? (str :foo))"));
		assertTrue(evalB("(str? (str nil))"));
		assertFalse(evalB("(str? 1)"));
		assertFalse(evalB("(str? nil)"));
	}

	@Test
	public void testNumberPred() {
		assertTrue(evalB("(number? 0)"));
		assertTrue(evalB("(number? (byte 0))"));
		assertTrue(evalB("(number? 0.5)"));
		assertTrue(evalB("(number? ##NaN)")); // Sane? Is numeric double type....
		
		assertFalse(evalB("(number? nil)"));
		assertFalse(evalB("(number? :foo)"));
		assertFalse(evalB("(number? 0xFF)"));
		assertFalse(evalB("(number? [1 2])"));

		assertFalse(evalB("(number? true)"));

	}

	@Test
	public void testZeroPred() {
		assertTrue(evalB("(zero? 0)"));
		assertTrue(evalB("(zero? (byte 0))"));
		assertTrue(evalB("(zero? 0.0)"));
		assertFalse(evalB("(zero? 0.00005)"));
		assertFalse(evalB("(zero? 0x00)")); // not numeric!

		assertFalse(0.0 > -0.0); // check we are living in a sane universe
		assertTrue(evalB("(zero? -0.0)"));

		assertFalse(evalB("(zero? \\c)"));

		assertFalse(evalB("(zero? nil)"));
		assertFalse(evalB("(zero? :foo)"));
		assertFalse(evalB("(zero? [1 2])"));
	}

	@Test
	public void testFn() {
		assertEquals(1L,evalL("((fn [] 1))"));
		assertEquals(2L,evalL("((fn [x] 2) 1)"));
		assertEquals(3L,evalL("((fn [x] 2 3) 1)"));
		// TODO: more cases!

		// test closing over lexical scope
		assertEquals(3L,evalL("(let [a 3 f (fn [x] a)] (f 0))"));

		// Bad arity fn execution
		assertArityError(step("((fn [x] 0))"));
		assertArityError(step("((fn [] 0) 1)"));
		
		// Failed destructuring
		assertArityError(step("((fn [[a b]] :OK) [1])"));
		assertCastError(step("((fn [[a b]] :OK) :foobar)"));

		// Bad fn forms
		assertArityError(step("(fn)"));
		assertCompileError(step("(fn 1)"));
		assertCompileError(step("(fn {})"));
		assertCompileError(step("(fn '())"));

		// fn printing
		assertCVMEquals("(fn [x y] 0)",eval("(str (fn [x y] 0))"));
		assertCVMEquals("(fn [x y] (do 0 1))",eval("(str (fn [x y] 0 1))"));

	}

	@Test
	public void testFnMulti() {
		assertEquals(1L,evalL("((fn ([] 1)))"));
		assertEquals(2L,evalL("((fn ([x] 2)) 1)"));

		// dispatch by arity
		assertEquals(1L,evalL("((fn ([x] 1) ([x y] 2)) 3)"));
		assertEquals(2L,evalL("((fn ([x] 1) ([x y] 2)) 3 4)"));

		// first matching impl chosen
		assertEquals(1L,evalL("((fn ([x] 1) ([x] 2)) 3)"));

		// variadic match
		assertEquals(2L,evalL("((fn ([x] 1) ([x & more] 2)) 3 4 5 6)"));
		assertEquals(2L,evalL("((fn ([x] 1) ([x y & more] 2)) 3 4)"));

		// MultiFn printing
		assertCVMEquals("(fn ([] 0) ([x] 1))",eval("(str (fn ([]0) ([x] 1) ))"));

		// Issue #193 Error test
		assertEquals(Vectors.of(1,2,3),eval("(do (defn f ([[a b] c] [a b c])) (f [1 2] 3))"));

		// arity errors
		assertArityError(step("((fn ([x] 1) ([x & more] 2)))"));
		assertArityError(step("((fn ([x] 1) ([x y] 2)))"));
		assertArityError(step("((fn ([x] 1) ([x y z] 2)) 2 3)"));
		assertArityError(step("((fn ([x] 1) ([x y z & more] 2)) 2 3)"));
	}

	@Test
	public void testFnMultiRecur() {
		assertEquals(7L,evalL("((fn ([x] x) ([x y] (recur 7))) 1 2)"));

		assertArityError(step("((fn ([x] x) ([x y] (recur))) 1 2)"));

		assertJuiceError(step("((fn ([x] (recur 3 4)) ([x y] (recur 5))) 1 2)"));
	}

	@Test
	public void testFnPred() {
		assertFalse(evalB("(fn? 0)"));
		assertTrue(evalB("(fn? (fn[x] 0))"));
		assertFalse(evalB("(fn? {})"));
		assertTrue(evalB("(fn? count)"));
		assertTrue(evalB("(fn? fn?)"));
		assertTrue(evalB("(fn? if)"));
	}

	@Test
	public void testDef() {
		// Def returns defined value
		assertEquals(Keywords.FOO, eval("(def v :foo)"));

		// Def establishes mapping in environment
		assertEquals(CVMLong.ONE, step("(def foo 1)").getEnvironment().get(Symbols.FOO));

		// Def creates valid dynamic variables
		assertEquals(Vectors.of(2L, 3L), eval("(do (def v [2 3]) v)"));
		assertNull(eval("(do (def v nil) v)"));

		// def overwrites existing bindings
		assertEquals(Vectors.of(2L, 3L), eval("(do (def v nil) (def v [2 3]) v)"));
		

		// TODO: are these error types logical?
		assertCompileError(step("(def)"));
		assertCompileError(step("(def a b c)"));

		assertUndeclaredError(step("(def a b)"));

		assertUndeclaredError(step("(def a a)"));
	}
	
	@Test 
	public void testDefOverCore() {
		Context ctx=step("(def count [2 3])");
		assertTrue(ctx.getEnvironment().containsKey(Symbols.COUNT));
		assertNull(ctx.getMetadata().get(Symbols.COUNT));

		assertEquals(Vectors.of(2,3),eval(ctx,"count"));
	}
	
	@Test
	public void testDeclare() {
		// Declare creates an environment binding
		assertTrue(step("(declare bast)").getEnvironment().containsKey(Symbol.create("bast")));
		
		// declare allows future definition of value
		// assertCompileError(step("(defn bp [x] (+ x bzz))")); // TODO: what error type?
		
		assertCVMEquals(3,eval("(do (declare bzz) (defn bp [x] (+ x bzz)) (def bzz 2) (bp 1))"));
		
		// Empty declare is a no-op w.r.t. state
		assertEquals(step("(do)").getState(),step("(declare)").getState());
		
		// declare allows multiple declarations
		assertTrue(step("(declare foo bar baz)").getEnvironment().getKeys().containsAll(Sets.of(Symbols.FOO,Symbols.BAR,Symbols.BAZ)));
		
		// Declare requires symbols only at compile time
		assertNotError(step("(declare count)"));
		
		assertCastError(step("(declare ~'count)")); // TODO: sanity check??
		assertCastError(step("(declare 1)"));
		assertCastError(step("(declare foo 1)"));
		assertCastError(step("(declare :bar)"));
		assertCastError(step("(declare \"foo\")"));
		
		// Declare should not overwrite existing value (see #440)
		assertEquals(1L, evalL("(do (def a 1) (declare a) a)"));
	}
	
	
	@Test
	public void testDeclareVsDef() {
		// Normal behaviour with def
		Context ctx=step("(def foo 1)");
		assertEquals(CVMLong.ONE,eval(ctx,"foo"));
		
		assertUndeclaredError(step(ctx,"bar"));
		ctx=step("(declare bar)");
		assertEquals(null,eval(ctx,"bar"));
		ctx=step("(def bar 2)");
		assertCVMEquals(2,eval(ctx,"bar"));
	}
	
	@Test
	public void testDefMeta() {
		AHashMap<ACell, ACell> FOOMAP = Maps.of(Keywords.FOO, CVMBool.TRUE);
		AHashMap<ACell, ACell> BARMAP = Maps.of(Keywords.BAR, CVMBool.TRUE);
		AHashMap<ACell, ACell> FOOBARMAP=FOOMAP.merge(BARMAP);
		
		// def of simple symbol has empty meta
		assertEquals(Maps.empty(), eval("(do (def v 1) (lookup-meta 'v))"));

		// def with a keyword tag
		assertEquals(FOOMAP, eval("(do (def ^:foo v 1) (lookup-meta 'v))"));
		
		// def with a keyword tag on value
		assertEquals(FOOMAP, eval("(do (def v ^:foo 1) (lookup-meta 'v))"));

		// def with constructed syntax object shouldn't set metadata
		assertEquals(Maps.empty(), eval("(do (def v (syntax 1 {:foo true})) (lookup-meta 'v))"));

		// def with syntax object constructed for symbol inline
		// assertEquals(FOOMAP, eval("(do (def ~(syntax 'v {:foo true})) (lookup-meta 'v))"));
		
		// def without metadata on symbol shouldn't change metadata
		assertEquals(FOOMAP, eval("(do (def ^:foo v 1) (def v 2) (lookup-meta 'v))"));

		// def with new metadata should overwrite
		assertEquals(BARMAP, eval("(do (def ^:foo v 1) (def ^:bar v 2) (lookup-meta 'v))"));
		
		// def with metadata on both symbol and value should merge
		assertEquals(FOOBARMAP, eval("(do (def ^:foo v ^{:bar true} 1) (lookup-meta 'v))"));

	}

	@Test
	public void testDefinedQ() {
		assertFalse(evalB("(defined? 'foobar)"));

		assertTrue(evalB("(do (def foobar [2 3]) (defined? 'foobar))"));
		assertTrue(evalB("(do (def foobar [2 3]) (defined? *address* 'foobar))"));
		
		// Note: defined by reference to core. TODO: is this OK?
		assertTrue(evalB("(defined? 'count)"));
		
		// Not defined in explicit environment. TODO: is this OK?
		assertFalse(evalB("(defined? *address* 'count)"));

		// invalid names
		assertCastError(step("(defined? :count)")); // not a Symbol
		assertCastError(step("(defined? \"count\")")); // not a Symbol
		assertCastError(step("(defined? nil)"));
		assertCastError(step("(defined? 1)"));
		assertCastError(step("(defined? 0x)"));
		
		// invalid addresses
		assertCastError(step("(defined? nil 'foo)"));
		assertCastError(step("(defined? 12 'foo)"));

		assertArityError(step("(defined?)"));
		assertArityError(step("(defined? :foo :bar :baz)"));
	}

	@Test
	public void testUndef() {
		assertNull(eval("(undef count)"));
		assertNull(eval("(undef foo)"));
		assertNull(eval("(undef *balance*)"));
		assertNull(eval("(undef bar)"));

		assertEquals(Vectors.of(1L, 2L), eval("(do (def a 1) (def v [a 2]) (undef a) v)"));

		assertFalse(evalB("(do (def a 1) (undef a) (defined? 'a))"));

		assertUndeclaredError(step("(do (def a 1) (undef a) a)"));

		assertArityError(step("(undef a b)"));
		assertArityError(step("(undef)"));
	}
	
	@Test
	public void testUnquote() {
		assertEquals(Vectors.of(1L, 2L), eval("`[1 (unquote (+ 1 1))]"));
		assertEquals(Constant.create(CVMLong.create(3L)), comp("(unquote (+ 1 2))"));

		assertCompileError(step("(unquote)"));
		assertCompileError(step("(unquote 1 2)"));
	}
	
	@Test
	public void testUnquoteError() {
		assertUndeclaredError(step("~~foo"));
	}

	@Test
	public void testDefn() {
		assertTrue(evalB("(do (defn f [a] a) (fn? f))"));
		assertEquals(Vectors.of(2L, 3L), eval("(do (defn f [a & more] more) (f 1 2 3))"));

		// multiple expressions in body
		assertEquals(2L,evalL("(do (defn f [a] 1 2) (f 3))"));

		// arity problems
		assertArityError(step("(defn)"));
		assertArityError(step("(defn f)"));

		// bad function construction
		assertCompileError(step("(defn f b)"));

	}

	@Test
	public void testDefnMulti() {
		assertEquals(2L,evalL("(do (defn f ([a] 1 2)) (f 3))"));
		assertEquals(2L,evalL("(do (defn f ([] 4) ([a] 1 2)) (f 3))"));

		assertArityError(step("(do (defn f ([] nil)) (f 3))"));
	}

	@Test
	public void testDefExpander() {
		Context ctx=step("(defexpander expand-once [x e] (expand x (fn [x e] (syntax x))))");

		assertEquals(Syntax.of(42L),eval(ctx,"(expand 42 expand-once)"));
	}

	@Test
	public void testSetBang() {
		// set! fails on undeclared values
		assertCompileError(step("(set! a 13)"));
		assertCompileError(step("(do (set! a 13) a)"));

		// set! works in a function body
		assertEquals(35L,evalL("(let [a 13 f (fn [x] (set! a 25) (+ x a))] (f 10))"));

		// set! only works in the scope of the immediate surrounding binding expression
		assertEquals(10L,evalL("(let [a 10] (let [] (set! a 13)) a)"));

		// set! binding does not escape current form, still undeclared in enclosing local context
		assertUndeclaredError(step("(do (let [a 10] (set! a 20)) a)"));

		// set! cannot alter value across closure boundary
		{
			assertEquals(5L,evalL("(let [a 5] ((fn [] (set! a 666))) a)"));
		}

		// set! cannot alter value within query
		assertEquals(5L,evalL("(let [a 5] (query (set! a 6)) a)"));

		// TODO: reconsider this
		// set! doesn't work outside eval boundary?
		assertCompileError(step ("(let [a 5] (eval `(set! a 7)) a)"));
	}

	@Test
	public void testEval() {
		assertEquals("foo", evalS("(eval (list 'str \\f \\o \\o))"));
		assertNull(eval("(eval 'nil)"));
		assertEquals(10L, evalL("(eval '(+ 3 7))"));
		assertEquals(40L, evalL("(eval `(* 2 4 5))"));

		assertArityError(step("(eval)"));
		assertArityError(step("(eval 1 2)"));
	}

	@Test
	public void testEvalAs() {
		assertEquals("foo", evalS("(eval-as *address* (list 'str \\f \\o \\o))"));

		assertTrustError(step("(eval-as *registry* '1)"));

		assertCastError(step("(eval-as :foo 2)"));
		assertArityError(step("(eval-as 1)")); // arity > cast
		assertArityError(step("(eval-as 1 2 3)"));
	}

	@Test
	public void testEvalAsTrustedUser() {
		Context ctx=step("(set-controller "+VILLAIN+")");
		ctx=ctx.forkWithAddress(VILLAIN);
		ctx=step(ctx,"(def hero "+HERO+")");
		
		assertEquals(3L, evalL(ctx,"(eval-as hero '(+ 1 2))"));
		assertEquals(HERO, eval(ctx,"(eval-as hero '*address*)"));
		assertEquals(VILLAIN, eval(ctx,"(eval-as hero '*caller*)"));
		assertEquals(Keywords.FOO, eval(ctx,"(eval-as hero '(return :foo))"));
		assertEquals(Keywords.FOO, eval(ctx,"(eval-as hero '(halt :foo))"));
		assertEquals(Keywords.FOO, eval(ctx,"(eval-as hero '(rollback :foo))"));

		assertAssertError(step(ctx,"(eval-as hero '(assert false))"));
	}
	
	@Test
	public void testEvalAsUntrustedUser() {
		Context ctx=step("(set-controller nil)");
		ctx=ctx.forkWithAddress(VILLAIN);
		ctx=step(ctx,"(def hero "+HERO+")");

		assertTrustError(step(ctx,"(eval-as hero '(+ 1 2))"));
		assertTrustError(step(ctx,"(eval-as (address hero) '(+ 1 2))"));
	}

	@Test
	public void testEvalAsWhitelistedUser() {
		// create trust monitor that allows VILLAIN
		Context ctx=step("(deploy '(do (defn check-trusted? ^{:callable? true} [s a o] (= s (address "+VILLAIN+")))))");
		Address monitor = (Address) ctx.getResult();
		ctx=step(ctx,"(set-controller "+monitor+")");

		ctx=ctx.forkWithAddress(VILLAIN);
		ctx=step(ctx,"(def hero "+HERO+")");

		assertEquals(3L, evalL(ctx,"(eval-as hero '(+ 1 2))"));
	}
	
	@Test
	public void testEvalAsScoped() {
		// create trust monitor that allows VILLAIN
		Context ctx=step("(deploy '(do (defn check-trusted? ^{:callable? true} [s a o] (= s *scope*))))");
		Address monitor = (Address) ctx.getResult();
		ctx=step(ctx,"(set-controller ["+monitor+" "+VILLAIN+"])");

		ctx=ctx.forkWithAddress(VILLAIN);
		ctx=step(ctx,"(def hero "+HERO+")");

		assertEquals(3L, evalL(ctx,"(eval-as hero '(+ 1 2))"));
	}

	@Test
	public void testQuery() {
		// Def should get rolled back
		assertEquals(CVMLong.ONE,eval("(do (def a 1) (query (def a 3)) a)"));

		// shouldn't be possible to mutate surrounding environment in query
		assertEquals(10L,evalL("(let [a 3] (+ (query (set! a 5) (+ a 2)) a) )"));
		
		// Query should consume fixed juice
		assertEquals(juice("1")+Juice.QUERY,juice("(query 1)"));
		
		// Query should add one to *depth*
		assertEquals(evalL("*depth*")+1,evalL("(query *depth*)"));
	}
	
	@Test
	public void testQueryExample() {
		Context ctx=step("(query (def a 10) [*address* *origin* *caller* 10])");
		assertEquals(Vectors.of(HERO,HERO,null,10L), ctx.getResult());

		// shouldn't be any def in the environment
		assertSame(INITIAL,ctx.getState());

		// some juice should be consumed
		assertTrue(context().getJuiceAvailable()>ctx.getJuiceAvailable());
	}

	@Test
	public void testQueryError() {
		Context ctx=step("(query (fail :FOO))");
		assertAssertError(ctx);

		// some juice should be consumed
		assertTrue(context().getJuiceAvailable()>ctx.getJuiceAvailable());
	}

// TODO: probably needs Op level support?
//	@Test
//	public void testQueryAs() {
//		Context<AVector<ACell>> ctx=step("(query-as "+Init.VILLAIN+" '(do (def a 10) [*address* *origin* *caller* 10]))");
//		assertEquals(Vectors.of(Init.VILLAIN,Init.VILLAIN,null,10L), ctx.getResult());
//
//		// shouldn't be any def in the environment
//		assertSame(INITIAL,ctx.getState());
//		assertSame(INITIAL_CONTEXT.getLocalBindings(),ctx.getLocalBindings());
//
//		// some juice should be consumed
//		assertTrue(INITIAL_CONTEXT.getJuice()>ctx.getJuice());
//	}

	@Test
	public void testEvalAsNotWhitelistedUser() {
		// create trust monitor that allows HERO only
		Context ctx=step("(deploy '(do (defn check-trusted? ^{:callable? true} [s a o] (= s (address "+HERO+")))))");
		Address monitor = (Address) ctx.getResult();
		ctx=step(ctx,"(set-controller "+monitor+")");

		ctx=ctx.forkWithAddress(VILLAIN);
		ctx=step(ctx,"(def hero "+HERO+")");

		assertTrustError(step(ctx,"(eval-as hero '(+ 1 2))"));
	}

	@Test
	public void testSetController() {
		// set-controller returns new controller
		assertEquals(VILLAIN, eval("(set-controller "+VILLAIN+")"));
		assertEquals(VILLAIN, eval("(set-controller (address "+VILLAIN+"))"));
		assertNull(eval("(set-controller nil)"));
		
		assertNotError(step("(set-controller ["+VILLAIN+" :random-scope])"));
		
		// Badly structured scope
		assertCastError(step("(set-controller ["+VILLAIN+"])"));
		assertCastError(step("(set-controller [:foo :bar])"));
		
		// non-existent controller accounts
		assertNobodyError(step("(set-controller [#9999999 :nonce])"));
		assertNobodyError(step("(set-controller #666666)")); 

		assertCastError(step("(set-controller :foo)"));
		assertCastError(step("(set-controller (address nil))")); // Address cast fails

		assertArityError(step("(set-controller)"));
		assertArityError(step("(set-controller 1 2)")); // arity > cast
	}

	@Test
	public void testScheduleFailures() {
		assertArityError(step("(schedule)"));
		assertArityError(step("(schedule 1)"));
		assertArityError(step("(schedule 1 2 3)"));
		assertArityError(step("(schedule :foo 2 3)")); // ARITY error before CAST

		assertCastError(step("(schedule :foo (def a 2))"));
		assertCastError(step("(schedule nil (def a 2))"));
	}
	
	@Test
	public void testScheduleStar() throws BadSignatureException {
		// Not an Op
		assertCastError(step("(schedule* (+ *timestamp* 1) '(def a 2))"));
		assertArityError(step("(schedule* (+ *timestamp* 1))"));
		assertArityError(step("(schedule* (+ *timestamp* 1) :foo :bar)"));// Arity before cast
	}

	@Test
	public void testScheduleExecution() throws BadSignatureException {
		long expectedTS = INITIAL.getTimestamp().longValue() + 1000;
		Context ctx = step("(schedule (+ *timestamp* 1000) (def a 2))");
		assertCVMEquals(expectedTS, ctx.getResult());
		State s = ctx.getState();
		BlobMap<ABlob, AVector<ACell>> sched = s.getSchedule();
		assertEquals(1L, sched.count());
		assertEquals(expectedTS, sched.entryAt(0).getKey().toExactLong());

		assertTrue(step(ctx, "(do a)").isExceptional());

		Block b = Block.of(expectedTS);
		SignedData<Block> sb=InitTest.FIRST_PEER_KEYPAIR.signData(b);
		BlockResult br = s.applyBlock(sb);
		State s2 = br.getState();

		Context ctx2 = Context.createInitial(s2, HERO, INITIAL_JUICE);
		assertEquals(2L, evalL(ctx2, "a"));
	}

	@Test
	public void testExpand() {
		assertEquals(Strings.create("foo"), eval("(expand (name :foo) (fn [x e] x))"));
		assertEquals(CVMLong.create(3), eval("(expand '[1 2 3] (fn [x e] (nth x 2)))"));

		assertNull(Syntax.unwrap(eval("(expand nil)")));

		assertCastError(step("(expand 1 :foo)"));
		assertCastError(step("(expand { 888 227 723 560} [75 561 258 833])"));


		assertArityError(step("(expand)"));
		assertArityError(step("(expand 1 (fn [x e] x) :blah :blah)"));

		// arity error calling expander function
		assertArityError(step("(expand 1 (fn [x] x))"));

		// arity error in expansion execution
		assertArityError(step("(expand 1 (fn [x e] (count)))"));
	}

	@Test
	public void testExpandEdgeCases() {
		// BAd functions
		assertCastError(step("(expand 123 #0 :foo)"));
		assertCastError(step("(expand 123 #0)"));

		// psuedo-function application, not valid for expand
		assertCastError(step("(expand 'foo 'bar 'baz)"));
		assertCastError(step("(expand {} :foo)"));
		assertCastError(step("(expand {:bar 1 :bax 2} :bar :baz)"));
		assertCastError(step("(expand {:foo 1 :bax 2} :bar :baz)"));
	}

	@Test
	public void testExpandOnce()  {
		// an expander that does nothing except wrap as syntax.
		Context c=step("(def identity-expand (fn [x e] x))");
		assertEquals(Keywords.FOO,eval(c,"(identity-expand :foo nil)"));

		// function that expands once with initial-expander, then with identity
		c=step(c,"(defn expand-once [x] (*initial-expander* x identity-expand))");
		// Should expand the outermost macro only
		assertEquals(read("(cond (if 1 2) 3 4)"),Syntax.unwrapAll(eval(c,"(expand-once '(if (if 1 2) 3 4))")));

		// Should be idempotent
		assertEquals(eval(c,"(expand '(if (if 1 2) 3 4))"),eval(c,"(expand (expand-once '(if (if 1 2) 3 4)))"));
	}


	@Test
	public void testMacro() {
		Context c=step("(defmacro foo [] :foo)");
		assertEquals(Keywords.FOO,eval(c,"(foo)"));
	}

	@Test
	public void testQuote() {
		assertEquals(Vectors.of(1,2,3),eval("(quote [1 2 3])"));
		assertEquals(Sets.of(42),eval("(quote #{42})")); // See Issue #109
		assertFalse(evalB("(= (quote #{42}) (quote #{(syntax 42)}))")); // See Issue #109

		assertEquals(Vectors.of(1,Lists.of(Symbols.IF,4,7),3),eval("(quote [1 (if 4 7) 3])"));
	}

	@Test
	public void testSyntax() {
		assertEquals(Syntax.of(null), eval("(syntax nil)"));
		assertEquals(Syntax.of(10L), eval("(syntax 10)"));

		// TODO: check if this is sensible
		// Syntax should be idempotent and wrap one level only
		assertCVMEquals(eval("(syntax 10)"), eval("(syntax (syntax 10))"));

		// Syntax with null / empty metadata should equal basic syntax
		assertCVMEquals(eval("(syntax 10)"), eval("(syntax 10 nil)"));
		assertCVMEquals(eval("(syntax 10)"), eval("(syntax 10 {})"));

		assertCastError(step("(syntax 2 3)"));

		assertArityError(step("(syntax)"));
		assertArityError(step("(syntax 2 3 4)"));
	}

	@Test
	public void testUnsyntax() {
		assertNull(eval("(unsyntax (syntax nil))"));
		assertNull(eval("(unsyntax nil)"));
		assertEquals(10L, evalL("(unsyntax (syntax 10))"));
		assertEquals(Keywords.FOO, eval("(unsyntax (expand :foo))"));

		assertArityError(step("(unsyntax)"));
		assertArityError(step("(unsyntax 2 3)"));
	}

	@Test
	public void testMeta() {
		assertEquals(Maps.empty(),eval("(meta (syntax nil))"));
		assertNull(eval("(meta nil)"));
		assertNull(eval("(meta 10)"));
		assertEquals(Maps.of(1L,2L), eval("(meta (syntax 10 {1 2}))"));

		assertArityError(step("(meta)"));
		assertArityError(step("(meta 2 3)"));
	}

	@Test
	public void testSyntaxQ() {
		assertFalse(evalB("(syntax? nil)"));
		assertTrue(evalB("(syntax? (syntax 10))"));

		assertArityError(step("(syntax?)"));
		assertArityError(step("(syntax? 2 3)"));
	}

	@Test
	public void testInitialExpander() {
		// bad continuation expanders
		assertCastError(step("(*initial-expander* (list #0) #0)"));

		assertArityError(step("(*initial-expander* 1 2 3)"));
		assertArityError(step("(*initial-expander* 1)"));
	}

	@Test
	public void testCallableQ() {
		Context ctx = step("(def caddr (deploy '(do " + "(defn private [] :priv) " + "(defn public ^{:callable? true} [] :pub))))");

		Address caddr = (Address) ctx.getResult();
		assertNotNull(caddr);

		assertTrue(evalB(ctx, "(callable? caddr 'public)")); // OK
		assertFalse(evalB(ctx, "(callable? caddr 'private)")); // Defined, but not exported
		assertFalse(evalB(ctx, "(callable? caddr 'random-symbol)")); // Doesn't exist

		// Valid scoped calls
		assertTrue(evalB(ctx, "(callable? [caddr nil] 'public)")); 
		assertFalse(evalB(ctx, "(callable? [caddr 1] 'private)")); 

		// 1-arity checks
		assertTrue(evalB(ctx, "(callable? #1)"));
		assertTrue(evalB(ctx, "(callable? [#1 nil])"));
		assertTrue(evalB(ctx, "(callable? [#1 #5675])"));
		assertFalse(evalB(ctx, "(callable? [#1 #5675 #5875])"));
		assertFalse(evalB(ctx, "(callable? nil)"));
		assertFalse(evalB(ctx, "(callable? :foo)"));
		assertFalse(evalB(ctx, "(callable? [])"));

		
		assertCastError(step(ctx, "(callable? caddr :public)")); // not a Symbol
		assertCastError(step(ctx, "(callable? caddr :random-name)"));
		assertCastError(step(ctx, "(callable? caddr :private)"));

		assertArityError(step(ctx, "(callable?)"));
		assertArityError(step(ctx, "(callable? 1 2 3)"));

		assertCastError(step(ctx, "(callable? :foo :foo)"));
		assertCastError(step(ctx, "(callable? nil :foo)"));
		assertCastError(step(ctx, "(callable? caddr nil)"));
		assertCastError(step(ctx, "(callable? caddr 1)"));
	}

	@Test
	public void testDec() {
		assertEquals(0L, evalL("(dec 1)"));
		assertEquals(0L, evalL("(dec (byte 1))"));
		assertEquals(-10L, evalL("(dec -9)"));
		// assertEquals(96L,(long)eval("(dec \\a)")); // TODO: think about this

		assertCastError(step("(dec nil)"));
		assertCastError(step("(dec :foo)"));
		assertCastError(step("(dec [1])"));
		assertCastError(step("(dec #666)"));
		assertCastError(step("(dec 3.0)"));

		assertArityError(step("(dec)"));
		assertArityError(step("(dec 1 2)"));
	}

	@Test
	public void testInc() {
		assertEquals(2L, evalL("(inc 1)"));
		assertEquals(2L, evalL("(inc (byte 1))"));

		assertTrue(evalB("(= 1000000000000000000000 (inc 999999999999999999999))"));
		
		assertCastError(step("(inc #42)")); // Issue #89
		assertCastError(step("(inc nil)"));
		assertCastError(step("(inc \\c)")); // Issue #89
		assertCastError(step("(inc true)")); // Issue #89

		assertArityError(step("(inc)"));
		assertArityError(step("(inc 1 2)"));
	}

	@Test
	public void testOr() {
		assertNull(eval("(or)"));
		assertNull(eval("(or nil)"));
		assertEquals(Keywords.FOO, eval("(or :foo)"));
		assertEquals(Keywords.FOO, eval("(or nil :foo :bar)"));
		assertEquals(Keywords.FOO, eval("(or :foo nil :bar)"));

		// ensure later branches never get executed
		assertEquals(Keywords.FOO, eval("(or :foo (+ nil :bar))"));

		assertFalse(evalB("(or nil nil false)"));
		assertTrue(evalB("(or nil nil true)"));

		// arity error if fails before first truth value
		assertArityError(step("(or nil (count) true)"));
	}

	@Test
	public void testAnd() {
		assertTrue(evalB("(and)"));
		assertNull(eval("(and nil)"));
		assertEquals(Keywords.FOO, eval("(and :foo)"));
		assertEquals(Keywords.FOO, eval("(and :bar :foo)"));
		assertNull(eval("(and :foo nil :bar)"));

		// ensure later branches never get executed
		assertNull(eval("(and nil (+ nil :bar))"));

		assertSame(CVMBool.FALSE,eval("(and 1 false 2)"));
		assertSame(CVMBool.TRUE,eval("(and 1 :foo true true)"));

		// arity error if fails before first falsey value
		assertArityError(step("(and true (count) nil)"));
	}

	@Test
	public void testSpecialAddress() {
		// Hero should be *address* initial context
		assertEquals(InitTest.HERO, eval("*address*"));

		// *address* MUST return Actor address within actor call
		Context ctx=step("(def act (deploy `(do (defn addr ^{:callable? true} [] *address*))))");
		Address act=(Address) ctx.getResult();
		assertEquals(act, eval(ctx,"(call act (addr))"));
		
		// *address* MUST be current address in library call
		assertEquals(InitTest.HERO, eval(ctx,"(act/addr)"));
	}
	
	@Test
	public void testSpecialOrigin() {
		// Hero should be *origin* in initial context
		assertEquals(InitTest.HERO, eval("*origin*"));
		
		// *origin* MUST return original address within actor call
		Context ctx=step("(def act (deploy `(do (defn origin ^{:callable? true} [] *origin*))))");
		assertEquals(InitTest.HERO, eval(ctx,"(call act (origin))"));
		
		// *origin* MUST be original address in library call
		assertEquals(InitTest.HERO, eval(ctx,"(act/origin)"));
	}

	@Test
	public void testSpecialAllowance() {
		// Should have initial allowance at start
		assertEquals(Constants.INITIAL_ACCOUNT_ALLOWANCE, evalL("*memory*"));
		
		// Buy some memory
		assertEquals(Constants.INITIAL_ACCOUNT_ALLOWANCE, evalL("*memory*"));

	}


	@Test
	public void testSpecialBalance() {
		// balance should return exact balance of account after execution
		Context ctx = step("(long *balance*)");
		Long bal=ctx.getAccountStatus(HERO).getBalance();
		assertCVMEquals(bal, ctx.getResult());

		// throwing it all away....
		assertEquals(666666L, evalL("(do (transfer "+VILLAIN+" (- *balance* 666666)) *balance*)"));

		// check balance as single expression
		assertEquals(bal, evalL("*balance*"));

		// Local values override specials
		assertNull(eval("(let [*balance* nil] *balance*)"));

		// TODO: reconsider this, special take priority over environment?
		assertCVMEquals(ctx.getOffer(),eval("(do (def *offer* :foo) *offer*)"));

		// Alternative behaviour
		//assertNull(eval("(let [*balance* nil] *balance*)"));
		//assertEquals(Keywords.FOO,eval("(do (def *balance* :foo) *balance*)"));
	}
	
	@Test
	public void testSpecialOverride() {
		// Should be possible to override specials in current environment
		assertEquals(CVMLong.ONE, eval("(let [*address* 1] *address*)"));
		
		// TODO: what should happen here?
		//assertEquals(eval("*address*"), eval("(do (def *address* 1) *address*)"));
	}


	@Test
	public void testSpecialCaller() {
		assertNull(eval("*caller*"));
		assertEquals(HERO, eval("(do (def c (deploy '(do (defn f ^{:callable? true} [] *caller*)))) (call c (f)))"));
	}

	@Test
	public void testSpecialResult() {
		// initial context result should be null
		assertNull(eval("*result*"));

		// Result should get value of last completed expression
		assertEquals(Keywords.FOO, eval("(do :foo *result*)"));
		assertNull(eval("(do 1 (do) *result*)"));
		
		// TODO: how should this behave?
		// assertEquals(Keywords.FOO, eval("(let [a :foo] *result*)"));
		
		assertEquals(Keywords.FOO, eval("(do ((fn [] :foo)) *result*)"));

		// *result* should be cleared to nil in an Actor call.
		assertNull(eval("(do (def c (deploy '(do (defn f ^{:callable? true} [] *result*)))) (call c (f)))"));

	}

	@Test
	public void testSpecialState() {
		assertSame(INITIAL, eval("*state*"));
		assertSame(INITIAL.getAccounts(), eval("(:accounts *state*)"));
	}
	
	@Test
	public void testSpecialScope() {
		assertNull(eval("*scope*"));
	}
	
	@Test
	public void testSqrt() {
		// Implicit double conversions for numeric values only, see #344
		assertCVMEquals(2.0, eval("(sqrt 4.0)"));
		assertCVMEquals(2.0, eval("(sqrt 4)"));
		assertCVMEquals(2.0, eval("(sqrt (byte 4))"));
		
		assertCVMEquals(Double.NaN, eval("(sqrt -1)"));

		
		assertCastError(step("(sqrt :foo)"));
		assertCastError(step("(sqrt nil)"));
		assertCastError(step("(sqrt false)"));
	}

	@Test
	public void testSpecialKey() {
		assertEquals(InitTest.HERO_KEYPAIR.getAccountKey(), eval("*key*"));
	}
	


	@Test
	public void testSpecialJuice() {
		// TODO: semantics of returning juice before lookup complete is OK?
		// seems sensible, represents "juice left at this position".
		assertCVMEquals(0, eval(Special.forSymbol(Symbols.STAR_JUICE)));

		// juice gets consumed before returning a value
		assertCVMEquals(Juice.DO + Juice.CONSTANT, eval(comp("(do 1 *juice*)")));
	}
	
	@Test
	public void testSpecialJuiceLimit() {
		Special<CVMLong> spec=Special.forSymbol(Symbols.STAR_JUICE_LIMIT);
		
		// Juice limit at start of transaction
		assertCVMEquals(Constants.MAX_TRANSACTION_JUICE, eval(spec));

		// Consuming a small amount of juice shouldn't change limit
		Context ctx=step("1");
		assertCVMEquals(Constants.MAX_TRANSACTION_JUICE, eval(ctx,"*juice-limit*"));
	}
	
	
	@Test
	public void testSpecialJuicePrice() {
		Special<?> jp=Special.forSymbol(Symbols.STAR_JUICE_PRICE);
		assertNotNull(jp);
		assertCVMEquals(Constants.INITIAL_JUICE_PRICE, eval(jp));
		
		assertCVMEquals(Constants.INITIAL_JUICE_PRICE, eval("*juice-price*"));
		
		assertSame(context().getState().getJuicePrice(),eval("*juice-price*"));
	}



	@Test
	public void testSpecialEdgeCases() {

		// TODO: consider this
		//assertEquals(Init.HERO,eval(Init.CORE_ADDRESS+"/*balance*"));

		// TODO: consider this
		// Lookup in core environment of special returns the Symbol
		assertEquals(Symbols.STAR_JUICE,eval("(lookup *juice*)"));

		assertEquals(Symbols.STAR_JUICE,eval(Lookup.create(Symbols.STAR_JUICE)));
	}

	@Test public void testSpecialHoldings() {
		assertSame(BlobMaps.empty(),eval("*holdings*"));

		// Test set-holding modifies *holdings* as expected
		assertNull(eval("(get-holding *address*)"));
		assertEquals(BlobMaps.of(HERO,1L),eval("(do (set-holding *address* 1) *holdings*)"));

		assertNull(eval("(*holdings* { :PuSg 650989 })"));
		assertEquals(Keywords.FOO,eval("(*holdings* { :PuSg 650989 } :foo )"));
	}

	@Test public void testHoldings() {
		Context ctx = step("(def VILLAIN (address \""+VILLAIN.toHexString()+"\"))");
		assertTrue(eval(ctx,"VILLAIN") instanceof Address);
		ctx=step(ctx,"(def NOONE (address 7777777))");

		// Basic empty holding should match empty blobmap in account record. See #131
		assertTrue(evalB("(= *holdings* (:holdings (account *address*)) (blob-map))"));

		// initial holding behaviour
		assertNull(eval(ctx,"(get-holding VILLAIN)"));
		assertCastError(step(ctx,"(get-holding :foo)"));
		assertCastError(step(ctx,"(get-holding nil)"));
		assertNobodyError(step(ctx,"(get-holding NOONE)"));

		// OK to set holding for a real owner account
	    assertEquals(100L,evalL(ctx,"(set-holding VILLAIN 100)"));

		// error to set holding for a non-existent owner account
		assertNobodyError(step(ctx,"(set-holding NOONE 200)"));

		// trying to set holding for the wrong type
		assertCastError(step(ctx,"(set-holding :foo 300)"));

		{ // test simple assign
			Context c2 = step(ctx,"(set-holding VILLAIN 123)");
			assertEquals(123L,evalL(c2,"(get-holding VILLAIN)"));

			assertTrue(c2.getAccountStatus(VILLAIN).getHoldings().containsKey(HERO));
			assertCVMEquals(123L,c2.getAccountStatus(VILLAIN).getHolding(HERO));
		}

		{ // test null assign
			Context c2 = step(ctx,"(set-holding VILLAIN nil)");
			assertFalse(c2.getAccountStatus(VILLAIN).getHoldings().containsKey(HERO));
		}
	}

	@Test
	public void testSymbolFor() {
		assertEquals(Symbols.COUNT, Core.symbolFor(Core.COUNT));
		assertThrows(Throwable.class, () -> Core.symbolFor(null));
	}

	@Test
	public void testCoreFormatRoundTrip() throws BadFormatException {
		{ // a core function
			ACell c = eval("count");
			Blob b = Format.encodedBlob(c);
			assertSame(c, Format.read(b));
		}

		{ // a core macro
			ACell c = eval("*initial-expander*");
			Blob b = Format.encodedBlob(c);
			assertSame(c, Format.read(b));
		}

		{ // a basic lambda expression
			ACell c = eval("(fn [x] x)");
			Blob b = Format.encodedBlob(c);
			assertEquals(c, Format.read(b));
		}
	}

}
