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

  /**
   * Pins the bug fix for the original mTLS implementation: the inherited
   * X509ExtendedKeyManager.chooseEngineClientAlias returns null by default,
   * which silently prevents the cert from being offered when SSLEngine
   * (Conscrypt's default on modern Android) drives the handshake.
   */
  @Test
  fun `chooseEngineClientAlias returns the configured alias`() {
    assertEquals(alias, keyManagerWithCert().chooseEngineClientAlias(null, null, null))
  }

  /**
   * Guards the X509KeyManager contract: the SSL implementation may call
   * getPrivateKey with an alias other than ours; in that case we must return
   * null rather than handing over the wrong key.
   */
  @Test
  fun `getPrivateKey returns null for an unknown alias`() {
    assertNull(keyManagerWithCert().getPrivateKey("some-other-alias"))
  }

  /**
   * KeyChain may return null if the user revoked permission or the cert was
   * removed after the alias was saved. The key manager must not offer the
   * alias in that case, otherwise the SSL layer would call getPrivateKey and
   * crash on a null. These tests pin the gating logic.
   */
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
