package convex.core.data;

import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

import convex.core.data.prim.CVMLong;
import convex.core.exceptions.InvalidDataException;
import convex.test.Samples;

/**
 * Tests for adversarial data, i.e. data that should b=not be accepted by correct peers / clients
 */
public class AdversarialDataTest {

	@SuppressWarnings("unchecked")
	public static final AVector<CVMLong> NON_CVM=(AVector<CVMLong>)Samples.INT_VECTOR_300.getRef(0).getValue();
	
	@Test public void testBadVectors() {
		invalidTest(VectorTree.unsafeCreate(0)); // nothing in VectorTree
		invalidTest(VectorTree.unsafeCreate(16,Samples.INT_VECTOR_16)); // single child
		invalidTest(VectorTree.unsafeCreate(26, Samples.INT_VECTOR_16,Samples.INT_VECTOR_10)); // too short VectorTree
		invalidTest(VectorTree.unsafeCreate(33, Samples.INT_VECTOR_16,Samples.INT_VECTOR_16)); // Bad count
		invalidTest(VectorTree.unsafeCreate(29, Samples.INT_VECTOR_16,Samples.INT_VECTOR_16)); // Bad count
		invalidTest(VectorTree.unsafeCreate(42, Samples.INT_VECTOR_16,Samples.INT_VECTOR_10,Samples.INT_VECTOR_16)); // Bad child count
		invalidTest(VectorTree.unsafeCreate(42, Samples.INT_VECTOR_16,Samples.INT_VECTOR_16,Samples.INT_VECTOR_10)); // Non-packed final child
		invalidTest(VectorTree.unsafeCreate(316, Samples.INT_VECTOR_16,Samples.INT_VECTOR_300)); // Bad tailing vector
	}

	private void invalidTest(ACell b) {
		assertThrows(InvalidDataException.class, ()->b.validate());
	}
}
