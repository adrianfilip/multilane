package com.adrianfilip.multilane

import zio.stm.{ STM, TMap }
import zio.{ UIO, ZIO }

/**
 * Given the following situation
 * L - lane
 * P - program
 *        L1   L2   L3  L4
 *  P1    X          X
 *  P2    X
 *  P3         X        X
 *  P4    X    X
 *
 * MultiLaneLocker guarantees that:
 * - P1 - P2 - P4 will run one at a time
 * - P3 - P4 will run one at a time
 * but it makes no quarantees about the order in which they run
 *
 */
trait MultiLaneLocker {
  def run[R, E, A](lanes: Set[Lane], program: ZIO[R, E, A]): ZIO[R, E, A]
}

object MultiLaneLocker {
  def make: UIO[MultiLaneLocker] =
    for {
      map <- TMap.make[Lane, Boolean]().commit
      _   <- ZIO.succeed(map)
    } yield new MultiLaneLocker {
      def run[R, E, A](lanes: Set[Lane], program: ZIO[R, E, A]): ZIO[R, E, A] = {
        val waitUntilFree = STM
          .foreach(lanes) { lane =>
            for {
              option <- map.get(lane)
              _ <- option match {
                    case None        => STM.unit
                    case Some(inUse) => if (inUse) STM.retry else STM.unit
                  }
              _ <- map.put(lane, true)
            } yield ()
          }
          .commit

        val release = STM
          .foreach(lanes) { lane =>
            map.put(lane, false)
          }
          .commit

        waitUntilFree.bracket_(release)(program)
      }
    }
}
