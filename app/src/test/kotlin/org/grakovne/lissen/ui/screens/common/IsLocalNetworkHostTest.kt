package org.grakovne.lissen.ui.screens.common

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class IsLocalNetworkHostTest {
  @Nested
  inner class PrivateClassA {
    @Test
    fun `10-x-x-x address is local`() {
      assertTrue(isLocalNetworkHost("http://10.0.0.1"))
    }

    @Test
    fun `10-x-x-x with port is local`() {
      assertTrue(isLocalNetworkHost("http://10.0.1.5:8080"))
    }

    @Test
    fun `10-255-255-255 is local`() {
      assertTrue(isLocalNetworkHost("http://10.255.255.255"))
    }
  }

  @Nested
  inner class PrivateClassB {
    @Test
    fun `172-16-x-x is local`() {
      assertTrue(isLocalNetworkHost("http://172.16.0.1"))
    }

    @Test
    fun `172-31-x-x is local`() {
      assertTrue(isLocalNetworkHost("http://172.31.255.255"))
    }

    @Test
    fun `172-15-x-x is not local`() {
      assertFalse(isLocalNetworkHost("http://172.15.0.1"))
    }

    @Test
    fun `172-32-x-x is not local`() {
      assertFalse(isLocalNetworkHost("http://172.32.0.1"))
    }
  }

  @Nested
  inner class PrivateClassC {
    @Test
    fun `192-168-x-x is local`() {
      assertTrue(isLocalNetworkHost("http://192.168.1.1"))
    }

    @Test
    fun `192-168-x-x with port is local`() {
      assertTrue(isLocalNetworkHost("http://192.168.178.42:13378"))
    }

    @Test
    fun `192-167-x-x is not local`() {
      assertFalse(isLocalNetworkHost("http://192.167.1.1"))
    }
  }

  @Nested
  inner class Loopback {
    @Test
    fun `127-0-0-1 is local`() {
      assertTrue(isLocalNetworkHost("http://127.0.0.1"))
    }

    @Test
    fun `127-0-0-1 with port is local`() {
      assertTrue(isLocalNetworkHost("http://127.0.0.1:3000"))
    }

    @Test
    fun `localhost is local`() {
      assertTrue(isLocalNetworkHost("http://localhost"))
    }

    @Test
    fun `localhost with port is local`() {
      assertTrue(isLocalNetworkHost("http://localhost:8080"))
    }
  }

  @Nested
  inner class LinkLocal {
    @Test
    fun `169-254-x-x is local`() {
      assertTrue(isLocalNetworkHost("http://169.254.1.1"))
    }
  }

  @Nested
  inner class MdnsLocal {
    @Test
    fun `dot-local hostname is local`() {
      assertTrue(isLocalNetworkHost("http://myserver.local"))
    }

    @Test
    fun `dot-local with port is local`() {
      assertTrue(isLocalNetworkHost("http://myserver.local:13378"))
    }

    @Test
    fun `dot-LOCAL is case insensitive`() {
      assertTrue(isLocalNetworkHost("http://myserver.LOCAL"))
    }
  }

  @Nested
  inner class PublicAddresses {
    @Test
    fun `public IP is not local`() {
      assertFalse(isLocalNetworkHost("http://8.8.8.8"))
    }

    @Test
    fun `public domain is not local`() {
      assertFalse(isLocalNetworkHost("https://example.com"))
    }

    @Test
    fun `public domain with port is not local`() {
      assertFalse(isLocalNetworkHost("https://myserver.example.com:443"))
    }
  }

  @Nested
  inner class BareHosts {
    @Test
    fun `bare IP without scheme is local`() {
      assertTrue(isLocalNetworkHost("192.168.1.1"))
    }

    @Test
    fun `bare localhost without scheme is local`() {
      assertTrue(isLocalNetworkHost("localhost"))
    }
  }
}
