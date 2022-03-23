package convex.core.data;

import java.nio.ByteBuffer;

import convex.core.lang.impl.BlobBuilder;
import convex.core.util.Errors;
import convex.core.util.Utils;

public abstract class ALongBlob extends ABlob {

	protected static final long LENGTH = 8;
	
	protected final long value;

	protected ALongBlob(long value) {
		this.value=value;
	}

	@Override
	public final long count() {
		return 8;
	}
	
	@Override
	@SuppressWarnings("unchecked")
	protected <R extends ACell> Ref<R> createRef() {
		// Create Ref at maximum status to reflect internal embedded nature
		Ref<ACell> newRef= RefDirect.create(this,cachedHash(),Ref.INTERNAL|Ref.KNOWN_EMBEDDED_MASK);
		cachedRef=newRef;
		return (Ref<R>) newRef;
	}

	@Override
	public void appendHexString(BlobBuilder sb,int length) {
		String s=Utils.toHexString(value);
		if (length<s.length()) s=s.substring(0,length);
		sb.append(s);
	}

	@Override
	public abstract ABlob slice(long start, long length);

	@Override
	public abstract Blob toFlatBlob();

	@Override
	public long commonHexPrefixLength(ABlob b) {
		return toFlatBlob().commonHexPrefixLength(b);
	}
	
	private void checkIndex(long i) {
		if ((i < 0) || (i >= LENGTH)) throw new IndexOutOfBoundsException(Errors.badIndex(i));
	}

	@Override
	public final byte byteAt(long i) {
		checkIndex(i);
		return (byte) (value >> ((LENGTH - i - 1) * 8));
	}
	
	@Override
	public final byte getUnchecked(long i) {
		return (byte) (value >> ((LENGTH - i - 1) * 8));
	}

	@Override
	public final ABlob append(ABlob d) {
		return toFlatBlob().append(d);
	}

	@Override
	public abstract boolean equals(ABlob o);

	@Override
	public final ByteBuffer writeToBuffer(ByteBuffer bb) {
		return bb.putLong(value);
	}
	
	@Override
	public final int writeToBuffer(byte[] bs, int pos) {
		Utils.writeLong(bs, pos, value);
		return pos+8;
	}

	@Override
	public final Blob getChunk(long i) {
		if (i == 0L) return toFlatBlob();
		throw new IndexOutOfBoundsException(Errors.badIndex(i));
	}

	@Override
	public final ByteBuffer getByteBuffer() {
		return toFlatBlob().getByteBuffer();
	}
	
	@Override
	protected final long calcMemorySize() {	
		// always embedded and no child Refs, so memory size == 0
		return 0;
	}
	
	@Override
	public long hexMatchLength(ABlob b, long start, long length) {
		return toFlatBlob().hexMatchLength(b,start,length);
	}

	@Override
	public final long toLong() {
		return value;
	}

	@Override
	public long longValue() {
		return value;
	}

	@Override
	public final boolean equalsBytes(ABlob b) {
		if (b.count()!=LENGTH) return false;
		return value==b.longValue();
	}

	@Override
	public abstract byte getTag();

	@Override
	public boolean isCanonical() {
		return true;
	}

	@Override
	public boolean isCVMValue() {
		return true;
	}

	@Override
	public final int getRefCount() {
		return 0;
	}

	@Override
	public final boolean isEmbedded() {
		// Always embedded
		return true;
	}

}
