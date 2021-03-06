/*
 * Copyright (C) 2014 Christopher Batey and Dogan Narinc
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
/*
* Copyright (C) 2014 Christopher Batey and Dogan Narinc
*
* Licensed under the Apache License, Version 2.0 (the "License");
* you may not use this file except in compliance with the License.
* You may obtain a copy of the License at
*
* http://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing, software
* distributed under the License is distributed on an "AS IS" BASIS,
* WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
* See the License for the specific language governing permissions and
* limitations under the License.
*/
package org.scassandra.server.priming

import org.scalatest.{Matchers, FunSpec}
import org.scassandra.server.cqlmessages._
import java.util.UUID
import org.scassandra.server.priming.query._
import org.scassandra.server.cqlmessages.types._
import org.scassandra.server.priming.query.PrimeCriteria
import org.scassandra.server.priming.query.PrimeMatch
import org.scassandra.server.priming.query.Prime

class PrimeQueryStoreTest extends FunSpec with Matchers {

  describe("add() and get()") {
    it("should add so that it can be retrieved using get") {
      // given
      val primeResults = PrimeQueryStore()

      val query: PrimeCriteria = PrimeCriteria("select * from users", Consistency.all)
      val expectedResult: List[Map[String, String]] =
        List(
          Map(
            "name" -> "Mickey",
            "age" -> "99"
          ),
          Map(
            "name" -> "Mario",
            "age" -> "12"
          )
        )

      // when
      primeResults.add(query, Prime(expectedResult, columnTypes = Map("name" -> CqlVarchar, "age" -> CqlInt)))
      val actualResult = primeResults.get(PrimeMatch(query.query, ONE))

      // then
      actualResult.get.rows should equal(expectedResult)
    }

    it("should return passed in metadata") {
      // given
      val primeResults = PrimeQueryStore()
      val query: PrimeCriteria = PrimeCriteria("some query", Consistency.all)

      // when
      primeResults add(query, Prime(List(), ReadRequestTimeoutResult()))
      val actualResult = primeResults.get(PrimeMatch(query.query, ONE))

      // then
      actualResult.get.result should equal(ReadRequestTimeoutResult())
    }
  }

  describe("get()") {
    it("should return Nil if no results for given query") {
      // given
      val primeResults = PrimeQueryStore()
      // when
      val actualResult = primeResults.get(PrimeMatch("some random query", ONE))

      // then
      actualResult.isEmpty should equal(true)
    }
  }

  describe("clear()") {
    it("should clear all results") {
      // given
      val primeResults = PrimeQueryStore()
      val query: PrimeCriteria = PrimeCriteria("select * from users", Consistency.all)
      val queryPattern: PrimeCriteria = PrimeCriteria("select .*", Consistency.all, true)
      val result: List[Map[String, String]] =
        List(
          Map(
            "name" -> "Mickey",
            "age" -> "99"
          ),
          Map(
            "name" -> "Mario",
            "age" -> "12"
          )
        )

      // when
      primeResults.add(query, Prime(result, columnTypes = Map("name" -> CqlVarchar, "age" -> CqlInt)))
      primeResults.add(queryPattern, Prime(result, columnTypes = Map("name" -> CqlVarchar, "age" -> CqlInt)))
      primeResults.clear()
      val primes = primeResults.get(PrimeMatch(query.query, ONE))
      //val primesWithPatterns = primeResults.get(PrimeMatch(query.query, ONE))

      // then
      primes.isEmpty should equal(true)
    }
  }

  describe("add() with specific consistency") {
    it("should only return if consistency matches - single") {
      val primeResults = PrimeQueryStore()
      val query = PrimeCriteria("select * from users", List(ONE))
      val result: List[Map[String, String]] = List(Map("name" -> "Mickey", "age" -> "99"))

      // when
      primeResults add(query, Prime(result, columnTypes = Map("name" -> CqlVarchar, "age" -> CqlInt)))
      val actualResult = primeResults.get(PrimeMatch(query.query, ONE))

      // then
      actualResult.get.rows should equal(result)
    }

    it("should not return if consistency does not match - single") {
      val primeResults = PrimeQueryStore()
      val query = PrimeCriteria("select * from users", List(TWO))
      val result: List[Map[String, String]] = List(Map("name" -> "Mickey", "age" -> "99"))

      // when
      primeResults add(query, Prime(result, columnTypes = Map("name" -> CqlVarchar, "age" -> CqlInt)))
      val actualResult = primeResults.get(PrimeMatch(query.query, ONE))

      // then
      actualResult should equal(None)
    }

    it("should not return if consistency does not match - many") {
      val primeResults = PrimeQueryStore()
      val query = PrimeCriteria("select * from users", List(TWO, ANY))
      val result: List[Map[String, String]] = List(Map("name" -> "Mickey", "age" -> "99"))

      // when
      primeResults.add(query, Prime(result, columnTypes = Map("name" -> CqlVarchar, "age" -> CqlInt)))
      val actualResult = primeResults.get(PrimeMatch(query.query, ONE))

      // then
      actualResult should equal(None)
    }

    it("should throw something if primes over lap partially") {
      val primeResults = PrimeQueryStore()
      val query: String = "select * from users"
      val consistencies: List[Consistency] = List(TWO, ANY)
      val primeForTwoAndAny = PrimeCriteria(query, consistencies)
      val primeForThreeAndAny = PrimeCriteria(query, List(THREE, ANY))
      val resultForTwo = Prime(List(Map("name" -> "TWO_ANY")), columnTypes = Map("name" -> CqlVarchar))
      val resultForThree = Prime(List(Map("name" -> "THREE_ANY")), columnTypes = Map("name" -> CqlVarchar))

      primeResults.add(primeForTwoAndAny, resultForTwo)
      val primeAddResult = primeResults.add(primeForThreeAndAny, resultForThree)
      primeAddResult should equal(ConflictingPrimes(List(PrimeCriteria(query, consistencies))))
    }

    it("should override if it is the same prime criteria") {
      val primeResults = PrimeQueryStore()
      val query: String = "select * from users"
      val primeForTwoAndAny = PrimeCriteria(query, List(TWO, ANY))
      val primeForTwoAndAnyAgain = PrimeCriteria(query, List(TWO, ANY))
      val resultForTwo = Prime(List(Map("name" -> "FIRST_TIME")), columnTypes = Map("name" -> CqlVarchar))
      val resultForThree = Prime(List(Map("name" -> "SECOND_TIME")), columnTypes = Map("name" -> CqlVarchar))

      primeResults.add(primeForTwoAndAny, resultForTwo)
      primeResults.add(primeForTwoAndAnyAgain, resultForThree)

      val actualResult = primeResults.get(PrimeMatch(query, ANY))
      actualResult.get should equal(resultForThree)
    }

    it("should allow many primes for the same criteria if consistency is different") {
      val primeResults = PrimeQueryStore()
      val query: String = "select * from users"
      val primeCriteriaForONE = PrimeCriteria(query, List(ONE))
      val primeCriteriaForTWO = PrimeCriteria(query, List(TWO))
      val primeCriteriaForTHREE = PrimeCriteria(query, List(THREE))
      val rowsPrime = Prime(List(Map("name" -> "FIRST_TIME")), columnTypes = Map("name" -> CqlVarchar))

      primeResults.add(primeCriteriaForONE, rowsPrime)
      primeResults.add(primeCriteriaForTWO, rowsPrime)
      primeResults.add(primeCriteriaForTHREE, rowsPrime)
    }
  }

  describe("Get PrimeCriteria for query patterns") {
    it("should treat .* as a wild card") {
      //given
      val primeQueryStore = PrimeQueryStore()

      //when
      primeQueryStore.add(PrimeCriteria(".*", List(ONE), true), Prime(List()))
      val primeResults: Option[Prime] = primeQueryStore.get(PrimeMatch("select anything"))

      //then
      primeResults.isDefined should equal(true)
    }

    it("should treat .+ as a wild card - no match") {
      //given
      val primeQueryStore = PrimeQueryStore()

      //when
      primeQueryStore.add(PrimeCriteria("hello .+", List(ONE), true), Prime(List()))
      val primeResults: Option[Prime] = primeQueryStore.get(PrimeMatch("hello "))

      //then
      primeResults.isDefined should equal(false)
    }

    it("should treat .+ as a wild card - match") {
      //given
      val primeQueryStore = PrimeQueryStore()

      //when
      primeQueryStore.add(PrimeCriteria("hello .+", List(ONE), true), Prime(List()))
      val primeResults: Option[Prime] = primeQueryStore.get(PrimeMatch("hello a"))

      //then
      primeResults.isDefined should equal(true)
    }
  }

  describe("Get PrimeCriteria by query") {
    it("should get all PrimeCriteria with the same query") {
      val primeResults = PrimeQueryStore()
      val query: String = "select * from users"
      val primeForOneAndTwo = PrimeCriteria(query, List(ONE, TWO))
      val primeForThreeAndAny = PrimeCriteria(query, List(THREE, ANY))
      val resultForTwo = Prime(List(Map("name" -> "FIRST")), columnTypes = Map("name" -> CqlVarchar))
      val resultForThree = Prime(List(Map("name" -> "SECOND")), columnTypes = Map("name" -> CqlVarchar))

      primeResults.add(primeForOneAndTwo, resultForTwo)
      primeResults.add(primeForThreeAndAny, resultForThree)
      val actualResult = primeResults.getPrimeCriteriaByQuery(query)

      actualResult.size should equal(2)
      actualResult(0) should equal(PrimeCriteria(query, List(ONE, TWO)))
      actualResult(1) should equal(PrimeCriteria(query, List(THREE, ANY)))
    }
  }

  describe("add() with type mismatch should return validation errors") {

    it("when column value not CqlInt") {
      // given
      val prime = Prime(
        List(
          Map("name" -> "totoro", "hasInvalidValue" -> "123"),
          Map("name" -> "catbus", "hasInvalidValue" -> "NOT AN INTEGER!") // incorrect entry, should trigger exception
        ),
        columnTypes = Map("name" -> CqlVarchar, "hasInvalidValue" -> CqlInt)
      )

      // when and then
      val validationResult = PrimeQueryStore().add(PrimeCriteria("", List()), prime)
      validationResult should equal(TypeMismatches(List(TypeMismatch("NOT AN INTEGER!", "hasInvalidValue", CqlInt.stringRep))))

    }

    it("when column value CqlInt as BigDecimal") {
      // given
      val prime = Prime(
        List(
          Map("hasLongAsInt" -> BigDecimal("5"))
        ),
        columnTypes = Map("hasLongAsInt" -> CqlInt)
      )

      // when and then
      val validationResult = PrimeQueryStore().add(PrimeCriteria("", List()), prime)
      validationResult should equal(PrimeAddSuccess)

    }

    it("when column value not CqlBoolean") {
      // given
      val prime = Prime(
        List(
          Map("name" -> "totoro", "hasInvalidValue" -> "true"),
          Map("name" -> "catbus", "hasInvalidValue" -> "NOT A BOOLEAN!") // incorrect entry, should trigger exception
        ),
        columnTypes = Map("name" -> CqlVarchar, "hasInvalidValue" -> CqlBoolean)
      )

      // when and then
      val validationResult = PrimeQueryStore().add(PrimeCriteria("", List()), prime)
      validationResult should equal(TypeMismatches(List(TypeMismatch("NOT A BOOLEAN!", "hasInvalidValue", CqlBoolean.stringRep))))

    }

    it("when column value not CqlBigint") {
      // given
      val prime = Prime(
        List(
          Map("name" -> "totoro", "hasInvalidValue" -> "123345"),
          Map("name" -> "catbus", "hasInvalidValue" -> "NOT A BIGINT!") // incorrect entry, should trigger exception
        ),
        columnTypes = Map("name" -> CqlVarchar, "hasInvalidValue" -> CqlBigint)
      )

      // when and then
      val validationResult = PrimeQueryStore().add(PrimeCriteria("", List()), prime)
      validationResult should equal(TypeMismatches(List(TypeMismatch("NOT A BIGINT!", "hasInvalidValue", CqlBigint.stringRep))))
    }

    it("when column value not CqlCounter") {
      // given
      val prime = Prime(
        List(
          Map("name" -> "totoro", "hasInvalidValue" -> "1234"),
          Map("name" -> "catbus", "hasInvalidValue" -> "NOT A COUNTER!") // incorrect entry, should trigger exception
        ),
        columnTypes = Map("name" -> CqlVarchar, "hasInvalidValue" -> CqlCounter)
      )

      // when and then
      val validationResult = PrimeQueryStore().add(PrimeCriteria("", List()), prime)
      validationResult should equal(TypeMismatches(List(TypeMismatch("NOT A COUNTER!", "hasInvalidValue", CqlCounter.stringRep))))
    }

    it("when column value not CqlBlob") {
      // given
      val prime = Prime(
        List(
          Map("name" -> "totoro", "hasInvalidValue" -> "0x48656c6c6f"),
          Map("name" -> "catbus", "hasInvalidValue" -> false) // incorrect entry, should trigger exception
        ),
        columnTypes = Map("name" -> CqlVarchar, "hasInvalidValue" -> CqlBlob)
      )

      // when and then
      val validationResult = PrimeQueryStore().add(PrimeCriteria("", List()), prime)
      validationResult should equal(TypeMismatches(List(TypeMismatch(false, "hasInvalidValue", CqlBlob.stringRep))))
    }

    it("when column value not CqlDecimal") {
      // given
      val prime = Prime(
        List(
          Map("name" -> "totoro", "hasInvalidValue" -> "5.5456"),
          Map("name" -> "catbus", "hasInvalidValue" -> false) // incorrect entry, should trigger exception
        ),
        columnTypes = Map("name" -> CqlVarchar, "hasInvalidValue" -> CqlDecimal)
      )

      // when and then
      val validationResult = PrimeQueryStore().add(PrimeCriteria("", List()), prime)
      validationResult should equal(TypeMismatches(List(TypeMismatch(false, "hasInvalidValue", CqlDecimal.stringRep))))
    }

    it("when column value not CqlDouble") {
      // given
      val prime = Prime(
        List(
          Map("name" -> "totoro", "hasInvalidValue" -> "5.5456"),
          Map("name" -> "catbus", "hasInvalidValue" -> false) // incorrect entry, should trigger exception
        ),
        columnTypes = Map("name" -> CqlVarchar, "hasInvalidValue" -> CqlDouble)
      )

      // when and then
      val validationResult = PrimeQueryStore().add(PrimeCriteria("", List()), prime)
      validationResult should equal(TypeMismatches(List(TypeMismatch(false, "hasInvalidValue", CqlDouble.stringRep))))
    }

    it("when column value not CqlFloat") {
      // given
      val prime = Prime(
        List(
          Map("name" -> "totoro", "hasInvalidValue" -> "5.5456"),
          Map("name" -> "catbus", "hasInvalidValue" -> false) // incorrect entry, should trigger exception
        ),
        columnTypes = Map("name" -> CqlVarchar, "hasInvalidValue" -> CqlFloat)
      )

      // when and then
      val validationResult = PrimeQueryStore().add(PrimeCriteria("", List()), prime)
      validationResult should equal(TypeMismatches(List(TypeMismatch(false, "hasInvalidValue", CqlFloat.stringRep))))
    }

    it("when column value not CqlTimestamp") {
      // given
      val prime = Prime(
        List(
          Map("name" -> "totoro", "hasInvalidValue" -> "1368438171000"),
          Map("name" -> "catbus", "hasInvalidValue" -> "NOT A TIMESTAMP!") // incorrect entry, should trigger exception
        ),
        columnTypes = Map("name" -> CqlVarchar, "hasInvalidValue" -> CqlTimestamp)
      )

      // when and then
      val validationResult = PrimeQueryStore().add(PrimeCriteria("", List()), prime)
      validationResult should equal(TypeMismatches(List(TypeMismatch("NOT A TIMESTAMP!", "hasInvalidValue", CqlTimestamp.stringRep))))
    }

    it("when column value not CqlUUID") {
      // given
      val uuid = UUID.randomUUID().toString
      val prime = Prime(
        List(
          Map("name" -> "totoro", "hasInvalidValue" -> uuid),
          Map("name" -> "catbus", "hasInvalidValue" -> false) // incorrect entry, should trigger exception
        ),
        columnTypes = Map("name" -> CqlVarchar, "hasInvalidValue" -> CqlUUID)
      )

      // when and then
      val validationResult = PrimeQueryStore().add(PrimeCriteria("", List()), prime)
      validationResult should equal(TypeMismatches(List(TypeMismatch(false, "hasInvalidValue", CqlUUID.stringRep))))
    }

    it("when column value not CqlInet") {
      // given
      val prime = Prime(
        List(
          Map("name" -> "totoro", "hasInvalidValue" -> "127.0.0.1"),
          Map("name" -> "validIpv6", "hasInvalidValue" -> "::1"),
          Map("name" -> "catbus", "hasInvalidValue" -> "NOT AN INET!") // incorrect entry, should trigger exception
        ),
        columnTypes = Map("name" -> CqlVarchar, "hasInvalidValue" -> CqlInet)
      )

      // when and then
      val validationResult = PrimeQueryStore().add(PrimeCriteria("", List()), prime)
      validationResult should equal(TypeMismatches(List(TypeMismatch("NOT AN INET!", "hasInvalidValue", CqlInet.stringRep))))
    }

    it("when column value not CqlVarint") {
      // given
      val prime = Prime(
        List(
          Map("name" -> "totoro", "hasInvalidValue" -> "1234"),
          Map("name" -> "catbus", "hasInvalidValue" -> "NOT A VARINT!") // incorrect entry, should trigger exception
        ),
        columnTypes = Map("name" -> CqlVarchar, "hasInvalidValue" -> CqlVarint)
      )

      // when and then
      val validationResult = PrimeQueryStore().add(PrimeCriteria("", List()), prime)
      validationResult should equal(TypeMismatches(List(TypeMismatch("NOT A VARINT!", "hasInvalidValue", CqlVarint.stringRep))))
    }

    it("when column value not CqlTimeUUID") {
      // given
      val prime = Prime(
        List(
          Map("name" -> "totoro", "hasInvalidValue" -> "2c530380-b9f9-11e3-850e-338bb2a2e74f"),
          Map("name" -> "catbus", "hasInvalidValue" -> "NOT A TIME UUID!") // incorrect entry, should trigger exception
        ),
        columnTypes = Map("name" -> CqlVarchar, "hasInvalidValue" -> CqlTimeUUID)
      )

      // when and then
      val validationResult = PrimeQueryStore().add(PrimeCriteria("", List()), prime)
      validationResult should equal(TypeMismatches(List(TypeMismatch("NOT A TIME UUID!", "hasInvalidValue", CqlTimeUUID.stringRep))))
    }
  }
}
