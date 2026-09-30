package org.whispersystems.curve25519;

public class NativeCurve25519ProviderTest extends Curve25519ProviderTest {

  // load the library explicitly: the androidx runner does not guarantee that an inherited
  // provider test (whose static initializer loads the library) runs before testInternalTest
  static {
    System.loadLibrary("curve25519");
  }

  @Override
  protected Curve25519Provider createProvider() throws NoSuchProviderException {
    return new NativeCurve25519Provider();
  }

  private native boolean internalTest();

  public void testInternalTest() throws Exception {
    assertTrue(internalTest());
  }


}
