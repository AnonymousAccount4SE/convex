package convex.core.transactions;

import java.nio.ByteBuffer;

import convex.core.Constants;
import convex.core.data.ACell;
import convex.core.data.Address;
import convex.core.data.Blob;
import convex.core.data.Format;
import convex.core.data.Keyword;
import convex.core.data.Keywords;
import convex.core.data.Tag;
import convex.core.data.prim.CVMLong;
import convex.core.exceptions.BadFormatException;
import convex.core.exceptions.InvalidDataException;
import convex.core.lang.Context;
import convex.core.lang.Juice;
import convex.core.lang.RT;
import convex.core.lang.impl.RecordFormat;

/**
 * Transaction class representing a coin Transfer from one account to another
 */
public class Transfer extends ATransaction {
	public static final long TRANSFER_JUICE = Juice.TRANSFER;

	protected final Address target;
	protected final long amount;

	private static final Keyword[] KEYS = new Keyword[] { Keywords.ORIGIN, Keywords.SEQUENCE, Keywords.TARGET, Keywords.AMOUNT};
	private static final RecordFormat FORMAT = RecordFormat.of(KEYS);

	protected Transfer(Address origin,long sequence, Address target, long amount) {
		super(FORMAT.count(),origin,sequence);
		this.target = target;
		this.amount = amount;
	}

	public static Transfer create(Address origin,long sequence, Address target, long amount) {
		return new Transfer(origin,sequence, target, amount);
	}


	@Override
	public int encode(byte[] bs, int pos) {
		bs[pos++]=Tag.TRANSFER;
		return encodeRaw(bs,pos);
	}

	@Override
	public int encodeRaw(byte[] bs, int pos) {
		pos = super.encodeRaw(bs,pos); // origin, sequence
		pos = target.encodeRaw(bs,pos);
		pos = Format.writeVLCLong(bs, pos, amount);
		return pos;
	}

	/**
	 * Read a Transfer transaction from a ByteBuffer
	 * 
	 * @param bb ByteBuffer containing the transaction
	 * @throws BadFormatException if the data is invalid
	 * @return The Transfer object
	 */
	public static Transfer read(ByteBuffer bb) throws BadFormatException {
		Address origin=Address.create(Format.readVLCLong(bb));
		long sequence = Format.readVLCLong(bb);
		Address target = Address.readRaw(bb);
		long amount = Format.readVLCLong(bb);
		if (!RT.isValidAmount(amount)) throw new BadFormatException("Invalid amount: "+amount);
		return create(origin,sequence, target, amount);
	}
	

	public static ATransaction read(Blob b, int pos) throws BadFormatException {
		int epos=pos+1; // skip tag
		long aval=Format.readVLCLong(b,epos);
		Address origin=Address.create(aval);
		epos+=Format.getVLCLength(aval);
		
		long sequence = Format.readVLCLong(b,epos);
		epos+=Format.getVLCLength(sequence);
		
		long tval=Format.readVLCLong(b,epos);
		Address target=Address.create(tval);
		epos+=Format.getVLCLength(tval);

		long amount = Format.readVLCLong(b,epos);
		epos+=Format.getVLCLength(amount);
		
		Transfer result=create(origin,sequence, target, amount);
		result.attachEncoding(b.slice(pos, epos));
		return result;
	}

	@Override
	public Context apply(Context ctx) {
		// consume juice, ensure we have enough to make transfer!
		ctx = ctx.consumeJuice(Juice.TRANSFER);
		
		// As long as juice was successfully consumed, make the transfer
		if (!ctx.isExceptional()) {
			ctx = ctx.transfer(target, amount);
		}
		
		// Return unconditionally. Might be an error.
		return ctx;
	}

	@Override
	public int estimatedEncodingSize() {
		// tag (1), sequence(<12) and target (33)
		// plus allowance for Amount
		return 1 + 12 + 33 + Format.MAX_VLC_LONG_LENGTH;
	}

	@Override
	public void validateCell() throws InvalidDataException {
		if ((amount<0)||(amount>Constants.MAX_SUPPLY)) throw new InvalidDataException("Invalid amount", this);
		if (target == null) throw new InvalidDataException("Null Address", this);
	}
	
	/**
	 * Gets the target address for this transfer
	 * @return Address of the destination for this transfer.
	 */
	public Address getTarget() {
		return target;
	}
	
	/**
	 * Gets the transfer amount for this transaction.
	 * @return Amount of transfer, as a long
	 */
	public long getAmount() {
		return amount;
	}
	
	@Override
	public int getRefCount() {
		return 0;
	}
	
	@Override
	public Transfer withSequence(long newSequence) {
		if (newSequence==this.sequence) return this;
		return create(origin,newSequence,target,amount);
	}
	
	@Override
	public Transfer withOrigin(Address newAddress) {
		if (newAddress==this.origin) return this;
		return create(newAddress,sequence,target,amount);
	}
	
	@Override
	public byte getTag() {
		return Tag.TRANSFER;
	}

	@Override
	public ACell get(Keyword key) {
		if (Keywords.AMOUNT.equals(key)) return CVMLong.create(amount);
		if (Keywords.ORIGIN.equals(key)) return origin;
		if (Keywords.SEQUENCE.equals(key)) return CVMLong.create(sequence);
		if (Keywords.TARGET.equals(key)) return target;

		return null;
	}

	@Override
	public RecordFormat getFormat() {
		return FORMAT;
	}

}
