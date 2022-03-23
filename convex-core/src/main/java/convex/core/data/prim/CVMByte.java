package convex.core.data.prim;

import convex.core.data.AString;
import convex.core.data.INumeric;
import convex.core.data.Strings;
import convex.core.data.Tag;
import convex.core.data.type.AType;
import convex.core.data.type.Types;
import convex.core.exceptions.InvalidDataException;
import convex.core.lang.impl.BlobBuilder;

/**
 * Class for CVM Byte instances.
 * 
 * Bytes are unsigned 8-bit integers which upcast to long for numerical operations.
 * 
 */
public final class CVMByte extends APrimitive implements INumeric {

	private final byte value;
	
	private static final CVMByte[] CACHE= new CVMByte[256];

	public static final CVMByte ZERO;
	public static final CVMByte ONE;

	public static final int MAX_ENCODING_LENGTH = 2;
	
	// Private constructor to enforce singleton instances
	private CVMByte(byte value) {
		this.value=value;
	}

	public static CVMByte create(long value) {
		return CACHE[((int)(value))&0xFF];
	}
	
	static {
		for (int i=0; i<256; i++) {
			CACHE[i]=new CVMByte((byte)i);
		}
		ZERO=CACHE[0];
		ONE=CACHE[1];
	}
	
	public AType getType() {
		return Types.BYTE;
	}
	
	/**
	 * Unsigned long value representing this Byte
	 */
	@Override
	public long longValue() {
		return 0xFFL&value;
	}
	
	@Override
	public int estimatedEncodingSize() {
		return 2;
	}

	@Override
	public void validateCell() throws InvalidDataException {
		// Nothing to check. Always valid
	}

	@Override
	public int encode(byte[] bs, int pos) {
		bs[pos++]=Tag.BYTE;
		return encodeRaw(bs,pos);
	}

	@Override
	public int encodeRaw(byte[] bs, int pos) {
		bs[pos++]=value;
		return pos;
	}

	@Override
	public boolean print(BlobBuilder bb, long limit) {
		AString s=toCVMString(limit);
		if (s==null) return false;
		bb.append(s);
		return bb.count()<=limit;
	}

	@Override
	public Class<?> numericType() {
		return Long.class;
	}

	@Override
	public double doubleValue() {
		return (double)longValue();
	}

	@Override
	public byte getTag() {
		return Tag.BYTE;
	}

	@Override
	public CVMLong toLong() {
		return CVMLong.create(longValue());
	}

	@Override
	public CVMDouble toDouble() {
		return CVMDouble.create(doubleValue());
	}
	
	@Override
	public CVMLong signum() {
		if (value==0) return CVMLong.ZERO;
		return CVMLong.ONE;
	}

	public byte byteValue() {
		return value;
	}

	@Override
	public INumeric toStandardNumber() {
		return toLong();
	}

	@Override
	public AString toCVMString(long limit) {
		return Strings.create(Long.toString(longValue()));
	}

}
