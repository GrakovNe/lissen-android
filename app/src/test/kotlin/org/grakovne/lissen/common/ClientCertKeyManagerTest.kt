package org.grakovne.lissen.common

import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.security.PrivateKey
import java.security.cert.X509Certificate

class ClientCertKeyManagerTest {
  private val privateKey = mockk<PrivateKey>()
  private val chain = arrayOf(mockk<X509Certificate>())

  private fun manager(
    key: PrivateKey? = privateKey,
    certChain: Array<X509Certificate>? = chain,
    keyLoader: () -> PrivateKey? = { key },
    chainLoader: () -> Array<X509Certificate>? = { certChain },
  ): ClientCertKeyManager = ClientCertKeyManager(ALIAS, keyLoader, chainLoader)

  @Test
  fun `chooseClientAlias returns the alias when key and chain are available`() {
    assertEquals(ALIAS, manager().chooseClientAlias(null, null, null))
  }

  @Test
  fun `chooseClientAlias returns null when the private key is missing`() {
    assertNull(manager(key = null).chooseClientAlias(null, null, null))
  }

  @Test
  fun `chooseClientAlias returns null when the certificate chain is missing`() {
    assertNull(manager(certChain = null).chooseClientAlias(null, null, null))
  }

  @Test
  fun `chooseEngineClientAlias mirrors chooseClientAlias`() {
    assertEquals(ALIAS, manager().chooseEngineClientAlias(null, null, null))
    assertNull(manager(key = null).chooseEngineClientAlias(null, null, null))
  }

  @Test
  fun `getCertificateChain returns the chain only for the own alias`() {
    assertArrayEquals(chain, manager().getCertificateChain(ALIAS))
    assertNull(manager().getCertificateChain("other"))
    assertNull(manager().getCertificateChain(null))
  }

  @Test
  fun `getPrivateKey returns the key only for the own alias`() {
    assertEquals(privateKey, manager().getPrivateKey(ALIAS))
    assertNull(manager().getPrivateKey("other"))
    assertNull(manager().getPrivateKey(null))
  }

  @Test
  fun `getClientAliases returns the single alias when ready`() {
    assertArrayEquals(arrayOf(ALIAS), manager().getClientAliases("RSA", null))
  }

  @Test
  fun `getClientAliases returns null when not ready`() {
    assertNull(manager(certChain = null).getClientAliases("RSA", null))
  }

  @Test
  fun `server alias methods always return null`() {
    val ready = manager()
    assertNull(ready.chooseServerAlias("RSA", null, null))
    assertNull(ready.chooseEngineServerAlias("RSA", null, null))
    assertNull(ready.getServerAliases("RSA", null))
  }

  @Test
  fun `loaders are invoked lazily and only once`() {
    var keyCalls = 0
    var chainCalls = 0
    val subject =
      manager(
        keyLoader = {
          keyCalls++
          privateKey
        },
        chainLoader = {
          chainCalls++
          chain
        },
      )

    assertEquals(0, keyCalls)
    assertEquals(0, chainCalls)

    assertEquals(ALIAS, subject.chooseClientAlias(null, null, null))
    assertEquals(privateKey, subject.getPrivateKey(ALIAS))
    assertArrayEquals(chain, subject.getCertificateChain(ALIAS))
    assertEquals(ALIAS, subject.chooseEngineClientAlias(null, null, null))

    assertEquals(1, keyCalls)
    assertEquals(1, chainCalls)
  }

  private companion object {
    const val ALIAS = "client-cert"
  }
}
