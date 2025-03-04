package convex.core.lang;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import convex.core.data.Address;

/**
 * Tests for Expander behaviour
 */
public class ExpanderTest extends ACVMTest {

	@Test 
	public void testExpanderInActor() {
		Context ctx=context();
		
		ctx=step("(deploy `(let [[a c] [*address* *caller*]] (defmacro foo [] [*address* *caller* a c])))");
		Address aa=(Address) ctx.getResult();
		
		assertEquals(eval("[*address* *caller* "+aa+" *address*]"),eval(ctx,"(expand `("+aa+"/foo))"));
	}
}
