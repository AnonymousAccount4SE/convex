package convex.core.data.prim;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigInteger;

import org.junit.jupiter.api.Test;

import convex.core.Constants;
import convex.core.data.AString;
import convex.core.data.Blob;
import convex.core.data.Format;
import convex.core.data.ObjectsTest;
import convex.core.data.Strings;
import convex.core.exceptions.BadFormatException;
import convex.core.lang.RT;
import convex.test.Samples;

public class BigIntegerTest {

	@Test public void testBigIntegerAssumptions() {
		assertThrows(java.lang.NumberFormatException.class,()->new BigInteger(new byte[0]));
		assertEquals(BigInteger.ZERO,new BigInteger(new byte[1]));
	}
	
	@Test public void testPrint() {
		String s="678638762397869864875634897567896587";
		AString cs=Strings.create(s);
		CVMBigInteger bi=CVMBigInteger.parse(s);
		assertEquals(s,bi.toString());
		assertEquals(cs,RT.print(bi));
		
		// insufficient print limit
		assertNull(RT.print(bi,10));
		
		assertEquals(s.substring(0, 20)+Constants.PRINT_EXCEEDED_MESSAGE,bi.print(20).toString());
	}
	
	@Test public void testZero() {
		CVMBigInteger bi=CVMBigInteger.wrap(new byte[] {0});
		assertEquals(0,bi.longValue());
		assertEquals(0.0,bi.doubleValue());
		assertEquals(BigInteger.ZERO,bi.getBigInteger());
		assertFalse(bi.isCanonical());
		
		doBigTest(bi);
	}
	
	@Test public void testComparisons() {
		assertEquals(-1,CVMLong.MAX_VALUE.compareTo(CVMBigInteger.MIN_POSITIVE));
		assertEquals(-1,CVMBigInteger.MIN_NEGATIVE.compareTo(CVMBigInteger.MIN_POSITIVE));
		assertEquals(0,CVMBigInteger.MIN_POSITIVE.compareTo(CVMBigInteger.MIN_POSITIVE));
		assertEquals(1,CVMBigInteger.MIN_POSITIVE.compareTo(CVMLong.ZERO));
	}
	
	@Test public void testOne() {
		CVMBigInteger bi=CVMBigInteger.wrap(new byte[] {1});
		assertEquals(1,bi.longValue());
		assertEquals(1.0,bi.doubleValue());
		assertEquals(BigInteger.ONE,bi.getBigInteger());
		
		// canonicality tests
		assertFalse(bi.isCanonical());
		
		doBigTest(bi);
	}
	
	@Test public void test0980Regression () {
		Blob b=Blob.fromHex("1980");
		assertThrows(BadFormatException.class,()->Format.read(b));
		
		BigInteger b1=BigInteger.valueOf(-128);
		CVMBigInteger cb=CVMBigInteger.wrap(b1);
		Blob bb=cb.getEncoding();
		assertNotEquals(b,bb);
		assertNotEquals(b,Format.encodedBlob(cb));
		
		ObjectsTest.doAnyValueTests(cb);
	}
	
	@Test 
	public void testByteArrayConstruction() {
		byte[] bs=new byte[] {-1,-1,-1,-1,-1,-1,-1,-1,-128};
		CVMBigInteger b=CVMBigInteger.wrap(bs);
		assertFalse(b.isCanonical());
		assertEquals(1,b.blob().count());
		assertEquals(CVMLong.create(-128),b.getCanonical());
	}
	
	@Test public void testSmallestPositive() {
		CVMBigInteger bi=CVMBigInteger.wrap(new byte[] {0,-128,0,0,0,0,0,0,0});
		assertEquals(CVMBigInteger.MIN_POSITIVE,bi);
		assertEquals(CVMBigInteger.MIN_POSITIVE_BIG,bi.big());


		assertEquals(Long.MIN_VALUE,bi.longValue());
		assertEquals(Long.MAX_VALUE,bi.dec().longValue());
		
		assertEquals(CVMBigInteger.MIN_POSITIVE,CVMLong.MAX_VALUE.inc());

		
		// Should be canonical, since too large for a CVMLong
		assertTrue(bi.isCanonical());

		
		// Extra leading zeros should get ignored
		assertEquals(bi,CVMBigInteger.wrap(new byte[] {0,0,0,-128,0,0,0,0,0,0,0}));
		
		doBigTest(bi);
	}
	
	@Test public void testSmallestNegative() {
		CVMBigInteger bi=CVMBigInteger.create(Blob.fromHex("ff7fffffffffffffff"));
		assertEquals(CVMBigInteger.MIN_NEGATIVE,bi);
		assertEquals(CVMBigInteger.MIN_NEGATIVE_BIG,bi.big());

		assertEquals(Long.MAX_VALUE,bi.longValue());
		assertEquals(Long.MIN_VALUE,bi.inc().longValue());
		
		assertEquals(CVMBigInteger.MIN_NEGATIVE,CVMLong.MIN_VALUE.dec());
		
		// Should be canonical, since too large for a CVMLong
		assertTrue(bi.isCanonical());
		
		doBigTest(bi);
	}

	public static void doBigTest(CVMBigInteger bi) {
		// BigInteger value should be cached
		BigInteger big=bi.getBigInteger();
		assertSame(big,bi.getBigInteger());
		
		CVMBigInteger bi2=CVMBigInteger.wrap(big);
		assertEquals(bi.getEncoding(),bi2.getEncoding());
		assertEquals(bi,bi2);
		
		assertEquals(bi,bi.inc().dec());
		
		if (bi.isCanonical()) {
			assertTrue(bi.byteLength()>8);
			assertNotEquals(bi,bi.toLong());
		} else {
			assertTrue(bi.byteLength()<=8);
			assertEquals(bi,bi.toLong());
			long lv=bi.longValue();
			assertEquals(CVMLong.create(lv), bi);
		}
		
		String s=bi.toString();
		assertEquals(big,new BigInteger(s));
		
		ObjectsTest.doAnyValueTests(bi);
	}
	
	@Test public void testBadEncoding() throws BadFormatException {
		assertThrows(BadFormatException.class,()->Format.read("19")); // no data
		assertThrows(BadFormatException.class,()->Format.read("190113")); // non-caonoical length
		assertThrows(BadFormatException.class,()->Format.read("1909ffffff")); // short length
		assertThrows(BadFormatException.class,()->Format.read("1909ffffffffffffffffff")); // excess leading ff
		assertThrows(BadFormatException.class,()->Format.read("1909000000000000000000")); // excess leading 00
		assertThrows(BadFormatException.class,()->Format.read("190988888888888888888888")); // excess bytes
		Format.read("1909888888888888888888"); // OK with 9 valid bytes exactly 
		
	}
	
	@Test public void testFullBlobSize() {
		CVMBigInteger c=CVMBigInteger.create(Samples.FULL_BLOB);
		doBigTest(c);
	}
	
	@Test public void testCompares() {
		assertTrue(CVMBigInteger.MIN_POSITIVE.compareTo(CVMBigInteger.MIN_POSITIVE)==0);
		assertTrue(CVMBigInteger.MIN_NEGATIVE.compareTo(CVMBigInteger.MIN_POSITIVE)==-1);
		assertTrue(CVMBigInteger.MIN_POSITIVE.compareTo(CVMBigInteger.MIN_NEGATIVE)==1);
		assertTrue(CVMBigInteger.MIN_POSITIVE.compareTo(CVMLong.MAX_VALUE)>0);
		assertTrue(CVMBigInteger.MIN_NEGATIVE.compareTo(CVMLong.MIN_VALUE)<0);
	}
	
	@Test public void testDoubleCompares() {
		assertTrue(CVMDouble.ZERO.compareTo(CVMBigInteger.wrap(BigInteger.ZERO))==0);
		assertTrue(CVMDouble.POSITIVE_INFINITY.compareTo(CVMBigInteger.MIN_POSITIVE)>0);
		assertTrue(CVMDouble.NEGATIVE_INFINITY.compareTo(CVMBigInteger.MIN_NEGATIVE)<0);
	}
}
