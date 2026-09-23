package org.grakovne.lissen.common

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.security.KeyPairGenerator
import java.security.PrivateKey
import java.security.cert.X509Certificate

class ClientCertKeyManagerTest {
  private val alias = "test-alias"

  private val privateKey: PrivateKey =
    KeyPairGenerator
      .getInstance("RSA")
      .apply { initialize(2048) }
      .generateKeyPair()
      .private

  private val certChain = emptyArray<X509Certificate>()

  private fun keyManagerWithCert() =
    ClientCertKeyManager(
      alias = alias,
      privateKeyLoader = { privateKey },
      certChainLoader = { certChain },
    )

  /** chooseEngineClientAlias returns null by default, silently withholding the cert when SSLEngine drives the handshake. */
  @Test
  fun `chooseEngineClientAlias returns the configured alias`() {
    assertEquals(alias, keyManagerWithCert().chooseEngineClientAlias(null, null, null))
  }

  /** KeyChain answers null once the cert is revoked or removed; offering the alias then would crash in getPrivateKey. */
  @Test
  fun `chooseEngineClientAlias returns null when private key load fails`() {
    val km =
      ClientCertKeyManager(
        alias = alias,
        privateKeyLoader = { null },
        certChainLoader = { certChain },
      )
    assertNull(km.chooseEngineClientAlias(null, null, null))
  }

  @Test
  fun `chooseEngineClientAlias returns null when cert chain load fails`() {
    val km =
      ClientCertKeyManager(
        alias = alias,
        privateKeyLoader = { privateKey },
        certChainLoader = { null },
      )
    assertNull(km.chooseEngineClientAlias(null, null, null))
  }
}
