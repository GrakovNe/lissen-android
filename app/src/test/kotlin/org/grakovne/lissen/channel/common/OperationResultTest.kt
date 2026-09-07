package org.grakovne.lissen.channel.common

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class OperationResultTest {
  @Nested
  inner class Fold {
    @Test
    fun `success invokes onSuccess with data`() {
      val result: OperationResult<String> = OperationResult.Success("hello")
      val out = result.fold(onSuccess = { it.uppercase() }, onFailure = { "fail" })
      assertEquals("HELLO", out)
    }

    @Test
    fun `error invokes onFailure`() {
      val result: OperationResult<String> = OperationResult.Error(OperationError.NetworkError)
      val out = result.fold(onSuccess = { "ok" }, onFailure = { "fail" })
      assertEquals("fail", out)
    }

    @Test
    fun `error carries correct code`() {
      val result: OperationResult<Int> = OperationResult.Error(OperationError.Unauthorized)
      result.fold(
        onSuccess = { },
        onFailure = { assertEquals(OperationError.Unauthorized, it.code) },
      )
    }
  }

  @Nested
  inner class FoldAsync {
    @Test
    fun `success invokes onSuccess`() =
      runBlocking {
        val result: OperationResult<Int> = OperationResult.Success(42)
        val out = result.foldAsync(onSuccess = { it * 2 }, onFailure = { -1 })
        assertEquals(84, out)
      }

    @Test
    fun `error invokes onFailure`() =
      runBlocking {
        val result: OperationResult<Int> = OperationResult.Error(OperationError.InternalError)
        val out = result.foldAsync(onSuccess = { it }, onFailure = { 0 })
        assertEquals(0, out)
      }
  }

  @Nested
  inner class Map {
    @Test
    fun `success maps value`() =
      runBlocking {
        val result: OperationResult<Int> = OperationResult.Success(5)
        val mapped = result.map { it.toString() }
        assertInstanceOf(OperationResult.Success::class.java, mapped)
        assertEquals("5", (mapped as OperationResult.Success).data)
      }

    @Test
    fun `error propagates unchanged through map`() =
      runBlocking {
        val result: OperationResult<Int> = OperationResult.Error(OperationError.NotFoundError)
        val mapped = result.map { it.toString() }
        assertInstanceOf(OperationResult.Error::class.java, mapped)
        assertEquals(OperationError.NotFoundError, (mapped as OperationResult.Error).code)
      }
  }

  @Nested
  inner class FlatMap {
    @Test
    fun `success chains to next operation`() =
      runBlocking {
        val result: OperationResult<Int> = OperationResult.Success(3)
        val chained = result.flatMap { OperationResult.Success(it * 10) }
        assertInstanceOf(OperationResult.Success::class.java, chained)
        assertEquals(30, (chained as OperationResult.Success).data)
      }

    @Test
    fun `success can chain to error`() =
      runBlocking {
        val result: OperationResult<Int> = OperationResult.Success(1)
        val chained = result.flatMap<String> { OperationResult.Error(OperationError.NetworkError) }
        assertInstanceOf(OperationResult.Error::class.java, chained)
      }

    @Test
    fun `error short-circuits without calling transform`() =
      runBlocking {
        val result: OperationResult<Int> = OperationResult.Error(OperationError.Unauthorized)
        var called = false
        val chained =
          result.flatMap {
            called = true
            OperationResult.Success(it)
          }
        assertFalse(called)
        assertInstanceOf(OperationResult.Error::class.java, chained)
      }
  }

  private fun assertFalse(value: Boolean) = assertEquals(false, value)
}
