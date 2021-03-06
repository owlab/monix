/*
 * Copyright (c) 2014-2018 by The Monix Project Developers.
 * See the project homepage at: https://monix.io
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package monix.eval

import monix.execution.CancelableFuture
import monix.execution.exceptions.DummyException
import monix.execution.internal.Platform

import scala.concurrent.TimeoutException
import scala.concurrent.duration._
import scala.util.{Failure, Success}

object TaskRaceSuite extends BaseTestSuite {
  test("Task.raceList should switch to other") { implicit s =>
    val task = Task.raceMany(Seq(Task(1).delayExecution(10.seconds), Task(99).delayExecution(1.second)))
    val f = task.runAsync

    s.tick()
    assertEquals(f.value, None)
    s.tick(1.second)
    assertEquals(f.value, Some(Success(99)))
  }

  test("Task.raceList should onError from other") { implicit s =>
    val ex = DummyException("dummy")
    val task = Task.raceMany(Seq(Task(1).delayExecution(10.seconds), Task(throw ex).delayExecution(1.second)))
    val f = task.runAsync

    s.tick()
    assertEquals(f.value, None)
    s.tick(1.second)
    assertEquals(f.value, Some(Failure(ex)))
  }

  test("Task.raceList should mirror the source") { implicit s =>
    val task = Task.raceMany(Seq(Task(1).delayExecution(1.seconds), Task(99).delayExecution(10.second)))
    val f = task.runAsync

    s.tick()
    assertEquals(f.value, None)
    s.tick(1.second)
    assertEquals(f.value, Some(Success(1)))
    assert(s.state.tasks.isEmpty, "other should be canceled")
  }

  test("Task.raceList should onError from the source") { implicit s =>
    val ex = DummyException("dummy")
    val task = Task.raceMany(Seq(Task(throw ex).delayExecution(1.seconds), Task(99).delayExecution(10.second)))
    val f = task.runAsync

    s.tick()
    assertEquals(f.value, None)
    s.tick(1.second)
    assertEquals(f.value, Some(Failure(ex)))
    assert(s.state.tasks.isEmpty, "other should be canceled")
  }

  test("Task.raceList should cancel both") { implicit s =>
    val task = Task.raceMany(Seq(Task(1).delayExecution(10.seconds), Task(99).delayExecution(1.second)))
    val f = task.runAsync

    s.tick()
    assertEquals(f.value, None)
    f.cancel()
    s.tick()

    assertEquals(f.value, None)
    assert(s.state.tasks.isEmpty, "both should be canceled")
  }

  test("Task.raceList should be stack safe, take 1") { implicit s =>
    val count = if (Platform.isJVM) 100000 else 10000
    val tasks = (0 until count).map(x => Task(x))
    val sum = Task.raceMany(tasks)

    sum.runAsync
    s.tick()
  }

  test("Task.raceList should be stack safe, take 2") { implicit s =>
    val count = if (Platform.isJVM) 100000 else 10000
    val tasks = (0 until count).map(x => Task.eval(x))
    val sum = Task.raceMany(tasks)

    sum.runAsync
    s.tick()
  }

  test("Task#timeout should timeout") { implicit s =>
    val task = Task(1).delayExecution(10.seconds).timeout(1.second)
    val f = task.runAsync

    s.tick()
    assertEquals(f.value, None)
    s.tick(1.second)
    assert(f.value.isDefined && f.value.get.failed.get.isInstanceOf[TimeoutException],
      "isInstanceOf[TimeoutException]")

    assert(s.state.tasks.isEmpty,
      "Main task was not canceled!")
  }

  test("Task#timeout should mirror the source in case of success") { implicit s =>
    val task = Task(1).delayExecution(1.seconds).timeout(10.second)
    val f = task.runAsync

    s.tick()
    assertEquals(f.value, None)
    s.tick(1.second)
    assertEquals(f.value, Some(Success(1)))
    assert(s.state.tasks.isEmpty, "timer should be canceled")
  }

  test("Task#timeout should mirror the source in case of error") { implicit s =>
    val ex = DummyException("dummy")
    val task = Task(throw ex).delayExecution(1.seconds).timeout(10.second)
    val f = task.runAsync

    s.tick()
    assertEquals(f.value, None)
    s.tick(1.second)
    assertEquals(f.value, Some(Failure(ex)))
    assert(s.state.tasks.isEmpty, "timer should be canceled")
  }

  test("Task#timeout should cancel both the source and the timer") { implicit s =>
    val task = Task(1).delayExecution(10.seconds).timeout(1.second)
    val f = task.runAsync

    s.tick()
    assertEquals(f.value, None)
    f.cancel()
    s.tick()

    assertEquals(f.value, None)
  }

  test("Task#timeout with backup should timeout") { implicit s =>
    val task = Task(1).delayExecution(10.seconds).timeoutTo(1.second, Task(99))
    val f = task.runAsync

    s.tick()
    assertEquals(f.value, None)
    s.tick(1.second)
    assertEquals(f.value, Some(Success(99)))
  }

  test("Task#timeout with backup should mirror the source in case of success") { implicit s =>
    val task = Task(1).delayExecution(1.seconds).timeoutTo(10.second, Task(99))
    val f = task.runAsync

    s.tick()
    assertEquals(f.value, None)
    s.tick(1.second)
    assertEquals(f.value, Some(Success(1)))
    assert(s.state.tasks.isEmpty, "timer should be canceled")
  }

  test("Task#timeout with backup should mirror the source in case of error") { implicit s =>
    val ex = DummyException("dummy")
    val task = Task(throw ex).delayExecution(1.seconds).timeoutTo(10.second, Task(99))
    val f = task.runAsync

    s.tick()
    assertEquals(f.value, None)
    s.tick(1.second)
    assertEquals(f.value, Some(Failure(ex)))
    assert(s.state.tasks.isEmpty, "timer should be canceled")
  }

  test("Task#timeout should cancel both the source and the timer") { implicit s =>
    val task = Task(1).delayExecution(10.seconds).timeoutTo(1.second, Task(99))
    val f = task.runAsync

    s.tick()
    assertEquals(f.value, None)
    f.cancel()
    s.tick()

    assertEquals(f.value, None)
    assert(s.state.tasks.isEmpty, "timer should be canceled")
  }

  test("Task#timeout should cancel the backup") { implicit s =>
    val task = Task(1).delayExecution(10.seconds).timeoutTo(1.second, Task(99).delayExecution(2.seconds))
    val f = task.runAsync

    s.tick()
    assertEquals(f.value, None)
    s.tick(1.seconds)
    assertEquals(f.value, None)

    f.cancel(); s.tick()
    assertEquals(f.value, None)
    assert(s.state.tasks.isEmpty, "backup should be canceled")
  }

  test("Task#timeout should not return the source after timeout") { implicit s =>
    val task = Task(1).delayExecution(2.seconds).timeoutTo(1.second, Task(99).delayExecution(2.seconds))
    val f = task.runAsync

    s.tick()
    assertEquals(f.value, None)

    s.tick(3.seconds)
    assertEquals(f.value, Some(Success(99)))
  }

  test("Task#timeout should cancel the source after timeout") { implicit s =>
    val backup = Task(99).delayExecution(1.seconds)
    val task = Task(1).delayExecution(5.seconds).timeoutTo(1.second, backup)
    val f = task.runAsync

    s.tick()
    assertEquals(f.value, None)

    s.tick(1.seconds)
    assert(s.state.tasks.size == 1, "source should be canceled after timeout")

    s.tick(1.seconds)
    assert(s.state.tasks.isEmpty, "all task should be completed")
  }

  test("Task.racePair(a,b) should work if a completes first") { implicit s =>
    val ta = Task.now(10).delayExecution(1.second)
    val tb = Task.now(20).delayExecution(2.seconds)

    val t = Task.racePair(ta, tb).flatMap {
      case Left((a, taskB)) =>
        taskB.join.map(b => a + b)
      case Right((taskA, b)) =>
        taskA.join.map(a => a + b)
    }

    val f = t.runAsync
    s.tick(1.second)
    assertEquals(f.value, None)
    s.tick(1.second)
    assertEquals(f.value, Some(Success(30)))
  }

  test("Task.racePair(a,b) should cancel both") { implicit s =>
    val ta = Task.now(10).delayExecution(2.second)
    val tb = Task.now(20).delayExecution(1.seconds)

    val t = Task.racePair(ta, tb)
    val f = t.runAsync
    s.tick()
    f.cancel()
    assertEquals(f.value, None)
    assert(s.state.tasks.isEmpty, "tasks.isEmpty")
  }

  test("Task.racePair(A,B) should not cancel B if A completes first") { implicit s =>
    val ta = Task.now(10).delayExecution(1.second)
    val tb = Task.now(20).delayExecution(2.seconds)
    var future = Option.empty[CancelableFuture[Int]]

    val t = Task.racePair(ta, tb).map {
      case Left((a, taskB)) =>
        future = Some(taskB.join.runAsync)
        a
      case Right((taskA, b)) =>
        future = Some(taskA.join.runAsync)
        b
    }

    val f = t.runAsync
    s.tick(1.second)
    f.cancel()

    assertEquals(f.value, Some(Success(10)))
    assert(future.isDefined, "future.isDefined")
    assertEquals(future.flatMap(_.value), None)

    s.tick(1.second)
    assertEquals(future.flatMap(_.value), Some(Success(20)))
  }

  test("Task.racePair(A,B) should not cancel A if B completes first") { implicit s =>
    val ta = Task.now(10).delayExecution(2.second)
    val tb = Task.now(20).delayExecution(1.seconds)
    var future = Option.empty[CancelableFuture[Int]]

    val t = Task.racePair(ta, tb).map {
      case Left((a, taskB)) =>
        future = Some(taskB.join.runAsync)
        a
      case Right((taskA, b)) =>
        future = Some(taskA.join.runAsync)
        b
    }

    val f = t.runAsync
    s.tick(1.second)
    f.cancel()

    assertEquals(f.value, Some(Success(20)))
    assert(future.isDefined, "future.isDefined")
    assertEquals(future.flatMap(_.value), None)

    s.tick(1.second)
    assertEquals(future.flatMap(_.value), Some(Success(10)))
  }

  test("Task.racePair(A,B) should end both in error if A completes first in error") { implicit s =>
    val dummy = DummyException("dummy")
    val ta = Task.raiseError[Int](dummy).delayExecution(1.second)
    val tb = Task.now(20).delayExecution(2.seconds)

    val t = Task.racePair(ta, tb)
    val f = t.runAsync
    s.tick(1.second)
    assertEquals(f.value, Some(Failure(dummy)))
    assert(s.state.tasks.isEmpty, "tasks.isEmpty")
  }

  test("Task.racePair(A,B) should end both in error if B completes first in error") { implicit s =>
    val dummy = DummyException("dummy")
    val ta = Task.now(10).delayExecution(2.seconds)
    val tb = Task.raiseError[Int](dummy).delayExecution(1.second)

    val t = Task.racePair(ta, tb)
    val f = t.runAsync
    s.tick(1.second)
    assertEquals(f.value, Some(Failure(dummy)))
    assert(s.state.tasks.isEmpty, "tasks.isEmpty")
  }

  test("Task.racePair(A,B) should work if A completes second in error") { implicit s =>
    val dummy = DummyException("dummy")
    val ta = Task.raiseError[Int](dummy).delayExecution(2.second)
    val tb = Task.now(20).delayExecution(1.seconds)

    val t1 = Task.racePair(ta, tb).flatMap {
      case Left((a, taskB)) =>
        taskB.join.map(b => a + b)
      case Right((taskA, b)) =>
        taskA.join.map(a => a + b)
    }

    val t2 = Task.racePair(ta, tb).map {
      case Left((a, _)) => a
      case Right((_, b)) => b
    }

    val f1 = t1.runAsync
    val f2 = t2.runAsync
    s.tick(2.seconds)

    assertEquals(f1.value, Some(Failure(dummy)))
    assertEquals(f2.value, Some(Success(20)))
  }

  test("Task.racePair(A,B) should work if B completes second in error") { implicit s =>
    val dummy = DummyException("dummy")
    val ta = Task.now(10).delayExecution(1.seconds)
    val tb = Task.raiseError[Int](dummy).delayExecution(2.second)

    val t1 = Task.racePair(ta, tb).flatMap {
      case Left((a, taskB)) =>
        taskB.join.map(b => a + b)
      case Right((taskA, b)) =>
        taskA.join.map(a => a + b)
    }

    val t2 = Task.racePair(ta, tb).map {
      case Left((a, _)) => a
      case Right((_, b)) => b
    }

    val f1 = t1.runAsync
    val f2 = t2.runAsync
    s.tick(2.seconds)

    assertEquals(f1.value, Some(Failure(dummy)))
    assertEquals(f2.value, Some(Success(10)))
  }

  test("Task.racePair should be stack safe, take 1") { implicit s =>
    val count = if (Platform.isJVM) 100000 else 10000
    val tasks = (0 until count).map(x => Task(x))
    val init = Task.never[Int]

    val sum = tasks.foldLeft(init)((acc,t) => Task.racePair(acc,t).map {
      case Left((a, _)) => a
      case Right((_, b)) => b
    })

    sum.runAsync
    s.tick()
  }

  test("Task.racePair should be stack safe, take 2") { implicit s =>
    val count = if (Platform.isJVM) 100000 else 10000
    val tasks = (0 until count).map(x => Task.eval(x))
    val init = Task.never[Int]

    val sum = tasks.foldLeft(init)((acc,t) => Task.racePair(acc,t).map {
      case Left((a, _)) => a
      case Right((_, b)) => b
    })

    sum.runAsync
    s.tick()
  }
  
  test("Task.race(a, b) should work if a completes first") { implicit s =>
    val ta = Task.now(10).delayExecution(1.second)
    val tb = Task.now(20).delayExecution(2.seconds)

    val t = Task.race(ta, tb).map {
      case Left(a) => a
      case Right(b) => b
    }

    val f = t.runAsync
    s.tick(1.second)
    assertEquals(f.value, Some(Success(10)))
    assert(s.state.tasks.isEmpty, "tasks.isEmpty")
  }

  test("Task.race(a, b) should work if b completes first") { implicit s =>
    val ta = Task.now(10).delayExecution(2.second)
    val tb = Task.now(20).delayExecution(1.seconds)

    val t = Task.race(ta, tb).map {
      case Left(a) => a
      case Right(b) => b
    }

    val f = t.runAsync
    s.tick(1.second)
    assertEquals(f.value, Some(Success(20)))
    assert(s.state.tasks.isEmpty, "tasks.isEmpty")
  }


  test("Task.race(a, b) should cancel both") { implicit s =>
    val ta = Task.now(10).delayExecution(2.second)
    val tb = Task.now(20).delayExecution(1.seconds)

    val t = Task.race(ta, tb)
    val f = t.runAsync
    s.tick()
    f.cancel()
    assertEquals(f.value, None)
    assert(s.state.tasks.isEmpty, "tasks.isEmpty")
  }

  test("Task.race(a, b) should end both in error if `a` completes first in error") { implicit s =>
    val dummy = DummyException("dummy")
    val ta = Task.raiseError[Int](dummy).delayExecution(1.second)
    val tb = Task.now(20).delayExecution(2.seconds)

    val t = Task.race(ta, tb)
    val f = t.runAsync
    s.tick(1.second)
    assertEquals(f.value, Some(Failure(dummy)))
    assert(s.state.tasks.isEmpty, "tasks.isEmpty")
  }

  test("Task.race(a, b) should end both in error if `b` completes first in error") { implicit s =>
    val dummy = DummyException("dummy")
    val ta = Task.now(20).delayExecution(2.seconds)
    val tb = Task.raiseError[Int](dummy).delayExecution(1.second)

    val t = Task.race(ta, tb)
    val f = t.runAsync
    s.tick(1.second)
    assertEquals(f.value, Some(Failure(dummy)))
    assert(s.state.tasks.isEmpty, "tasks.isEmpty")
  }

  test("Task.race(a, b) should work if `a` completes in error") { implicit s =>
    val dummy = DummyException("dummy")
    val ta = Task.raiseError[Int](dummy).delayExecution(2.second).uncancelable
    val tb = Task.now(20).delayExecution(1.seconds)

    val task = Task.race(ta, tb).map {
      case Left(a) => a
      case Right(b) => b
    }

    val f = task.runAsync
    s.tick(2.seconds)

    assertEquals(f.value, Some(Success(20)))
    assertEquals(s.state.lastReportedError, dummy)
  }

  test("Task.race(a, b) should work if `b` completes in error") { implicit s =>
    val dummy = DummyException("dummy")
    val ta = Task.now(20).delayExecution(1.seconds)
    val tb = Task.raiseError[Int](dummy).delayExecution(2.second).uncancelable

    val task = Task.race(ta, tb).map {
      case Left(a) => a
      case Right(b) => b
    }

    val f = task.runAsync
    s.tick(2.seconds)

    assertEquals(f.value, Some(Success(20)))
    assertEquals(s.state.lastReportedError, dummy)
  }

  test("Task.race should be stack safe, take 1") { implicit s =>
    val count = if (Platform.isJVM) 100000 else 10000
    val tasks = (0 until count).map(x => Task(x))
    val init = Task.never[Int]

    val sum = tasks.foldLeft(init)((acc,t) => Task.race(acc,t).map {
      case Left(a) => a
      case Right(b) => b
    })

    sum.runAsync
    s.tick()
  }

  test("Task.race should be stack safe, take 2") { implicit s =>
    val count = if (Platform.isJVM) 100000 else 10000
    val tasks = (0 until count).map(x => Task.eval(x))
    val init = Task.never[Int]

    val sum = tasks.foldLeft(init)((acc,t) => Task.race(acc,t).map {
      case Left(a) => a
      case Right(b) => b
    })

    sum.runAsync
    s.tick()
  }
}
