package convex.core.data.prim;

import java.math.BigDecimal;
import java.math.BigInteger;

import convex.core.data.ACell;
import convex.core.data.AString;
import convex.core.data.Blob;
import convex.core.data.BlobBuilder;
import convex.core.data.Strings;
import convex.core.data.Tag;
import convex.core.data.type.AType;
import convex.core.data.type.Types;
import convex.core.exceptions.BadFormatException;
import convex.core.exceptions.InvalidDataException;
import convex.core.util.Utils;

/**
 * Class for CVM double floating-point values.
 * 
 * Follows the Java standard / IEEE 784 spec.
 */
public final class CVMDouble extends ANumeric {

	public static final CVMDouble ZERO = CVMDouble.create(0.0);
	public static final CVMDouble NEGATIVE_ZERO = CVMDouble.create(-0.0);
	public static final CVMDouble ONE = CVMDouble.create(1.0);
	public static final CVMDouble MINUS_ONE = CVMDouble.create(-1.0);

	public static final CVMDouble NaN = CVMDouble.create(Double.NaN);
	public static final CVMDouble POSITIVE_INFINITY = CVMDouble.create(Double.POSITIVE_INFINITY);
	public static final CVMDouble NEGATIVE_INFINITY = CVMDouble.create(Double.NEGATIVE_INFINITY);
	
	private final double value;
	
	private static final long RAW_NAN_BITS=0x7ff8000000000000L;
	
	public static final int MAX_ENCODING_LENGTH = 9;
	
	public CVMDouble(double value) {
		this.value=value;
	}

	/**
	 * Creates a CVMDouble. Forces NaN to be canonical instance.
	 * @param value Double value to wrap
	 * @return CVMDouble value
	 */
	public static CVMDouble create(double value) {
		// We must use a canonical NaN value (0x7ff8000000000000L);
		if (Double.isNaN(value)) value=Double.NaN;
		return new CVMDouble(value);
	}
	
	@Override
	public AType getType() {
		return Types.DOUBLE;
	}
	
	@Override
	public long longValue() {
		return (long)value;
	}
	
	@Override
	public CVMLong toLong() {
		return CVMLong.create(longValue());
	}

	@Override
	public CVMDouble toDouble() {
		return this;
	}
	
	@Override
	public CVMDouble signum() {
		if (value>0.0) return CVMDouble.ONE;
		if (value<0.0) return CVMDouble.MINUS_ONE;
		if (Double.isNaN(value)) return NaN; // NaN special case
		return this;
	}
	
	@Override
	public int estimatedEncodingSize() {
		return 1+8;
	}

	@Override
	public void validateCell() throws InvalidDataException {
		// Nothing to check. Always valid
		if (Double.isNaN(value)) {
			if (value!=Double.NaN) throw new InvalidDataException("Non-canonical NaN value",this);
		}
	}

	@Override
	public int encode(byte[] bs, int pos) {
		bs[pos++]=Tag.DOUBLE;
		return encodeRaw(bs,pos);
	}

	@Override
	public int encodeRaw(byte[] bs, int pos) {
		long doubleBits=Double.doubleToRawLongBits(value);
		return Utils.writeLong(bs,pos,doubleBits);
	}
	
	@Override
	public String toString() {
		if (Double.isInfinite(value)) {
			if (value>0.0) {
				return "##Inf";
			} else {
				return "##-Inf";
			}
		} else if (Double.isNaN(value)) {
			return "##NaN";
		} else {
			return Double.toString(value);
		}
	}

	@Override
	public boolean print(BlobBuilder bb, long limit) {
		bb.append(toString());
		return bb.count()<=limit;
	}

	@Override
	public Class<?> numericType() {
		return Double.class;
	}

	@Override
	public double doubleValue() {
		return value;
	}

	/**
	 * Parses a CVM Double value. 
	 * @param s String to parse
	 * @return CVMDouble value
	 * @throws NumberFormatException If number format is invalid
	 */
	public static CVMDouble parse(String s) {
		return create(Double.parseDouble(s));
	}
	
	@Override
	public byte getTag() {
		return Tag.DOUBLE;
	}

	public static CVMDouble read(double value) throws BadFormatException {
		// Need to check for non-canonical NaN values
		if (Double.isNaN(value)) {
			if (Double.doubleToRawLongBits(value)!=RAW_NAN_BITS) {
				throw new BadFormatException("Non-canonical NaN value");
			}
		}
		return create(value);
	}
	
	public static CVMDouble read(byte tag, Blob blob, int offset) throws BadFormatException {
		if (blob.count()<offset+1+8) throw new BadFormatException("Insufficient blob bytes to read Double");
		long bits=Utils.readLong(blob.getInternalArray(), blob.getInternalOffset()+offset+1);
		double d=Double.longBitsToDouble(bits);
		CVMDouble result= read(d);
		result.attachEncoding(blob.slice(offset,offset+1+8));
		return result;
	}

	@Override
	public AString toCVMString(long limit) {
		if (limit<1) return null;
		return Strings.create(toString());
	}
	
	@Override
	public boolean equals(ACell a) {
		return ((a instanceof CVMDouble)&&(Double.compare(((CVMDouble)a).value,value)==0));
	}

	@Override
	public ANumeric abs() {
		// We use fast path here to save allocations in ~50% of cases
		if (value>0) return this;
		if (value==0) return ZERO; // note: we do this to handle -0.0
		return create(-value);
	}
	
	@Override
	public int compareTo(ANumeric o) {
		return Double.compare(value, o.doubleValue());
	}

	@Override
	public CVMLong ensureLong() {
		// TODO: possible conversion of some values?
		return null;
	}

	@Override
	public ANumeric add(ANumeric b) {
		return CVMDouble.create(value+b.doubleValue());
	}

	@Override
	public ANumeric sub(ANumeric b) {
		return CVMDouble.create(value-b.doubleValue());
	}

	@Override
	public ANumeric negate() {
		return CVMDouble.create(-value);
	}

	@Override
	public ANumeric multiply(ANumeric b) {
		return CVMDouble.create(value*b.doubleValue());
	}

	@Override
	public AInteger toInteger() {
		if (!Double.isFinite(value))return null; // catch NaN and infinity
		if ((value<=Long.MAX_VALUE)&&(value>=Long.MIN_VALUE)) {
			return CVMLong.create((long)value);
		}
		
		BigDecimal bd=BigDecimal.valueOf(value);
		BigInteger bi=bd.toBigInteger();
		return CVMBigInteger.wrap(bi);
	}

	@Override
	public boolean isZero() {
		// According to the IEEE 754 standard, negative zero and positive zero should
		// compare as equal with the usual (numerical) comparison operators
		// This is the behaviour in Java
		return value==0.0;
	}




}
