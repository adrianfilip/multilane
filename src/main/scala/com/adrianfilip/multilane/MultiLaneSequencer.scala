package com.adrianfilip.multilane

import java.util.UUID

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
 * The order in which the programs above are executed is guaranteed to be:
 * – P1 and P3 can run concurrently because their lanes are free (I use can instead of will  because depends on your available threads)
 * – P2 is queued up behind P1 so it only runs after P1 is done
 * – P4 is queued up behind P2 and P3, so until they are both finished running it just waits in a non blocking fashion
 *
 */
trait MultiLaneSequencer {
  def sequence[R, E, A](lanes: Set[Lane], programId: UUID, program: ZIO[R, E, A]): ZIO[R, E, A]
}

object MultiLaneSequencer {

  def make: UIO[MultiLaneSequencer] =
    for {
      laningMap <- TMap.make[Lane, List[UUID]]().commit
    } yield new MultiLaneSequencer {

      def sequence[R, E, A](lanes: Set[Lane], programId: UUID, program: ZIO[R, E, A]): ZIO[R, E, A] = {
        val occupyLanes = STM
          .foreach(lanes) { lane =>
            for {
              option <- laningMap.get(lane)
              _ <- option match {
                    case None     => laningMap.put(lane, List(programId))
                    case Some(ls) => laningMap.put(lane, ls :+ programId)
                  }
            } yield ()
          }
          .commit

        val waitUntilFree = STM
          .foreach(lanes) { lane =>
            for {
              option <- laningMap.get(lane)
              _ <- option match {
                    case None            => STM.unit
                    case Some(Nil)       => STM.unit
                    case Some(head :: _) => if (head == programId) STM.unit else STM.retry
                  }
            } yield ()
          }
          .commit

        val release = STM
          .foreach(lanes) { lane =>
            for {
              option <- laningMap.get(lane)
              _ <- option match {
                    case None            => STM.unit
                    case Some(Nil)       => STM.unit
                    case Some(_ :: tail) => laningMap.put(lane, tail)
                  }
            } yield ()
          }
          .commit

        for {
          _ <- occupyLanes
          p <- waitUntilFree.bracket_(release)(program)
        } yield p

      }
    }

  def makeWithMonitoring(
    requestsMonitorMap: TMap[Lane, List[UUID]],
    responsesMonitorMap: TMap[Lane, List[UUID]]
  ): UIO[MultiLaneSequencer] =
    for {
      map <- TMap.make[Lane, List[UUID]]().commit
    } yield new MultiLaneSequencer {
      def sequence[R, E, A](lanes: Set[Lane], programId: UUID, program: ZIO[R, E, A]): ZIO[R, E, A] = {
        val occupyLanes = STM
          .foreach(lanes) { lane =>
            for {
              option <- map.get(lane)
              _ <- option match {
                    case None     => map.put(lane, List(programId))
                    case Some(ls) => map.put(lane, ls :+ programId)
                  }
              reqOp <- requestsMonitorMap.get(lane)
              _ <- reqOp match {
                    case None     => requestsMonitorMap.put(lane, List(programId))
                    case Some(ls) => requestsMonitorMap.put(lane, ls :+ programId)
                  }
            } yield ()
          }
          .commit

        val waitUntilFree = STM
          .foreach(lanes) { lane =>
            for {
              option <- map.get(lane)
              _ <- option match {
                    case None     => STM.unit
                    case Some(ls) => if (ls.head == programId) STM.unit else STM.retry
                  }
            } yield ()
          }
          .commit

        val release = STM
          .foreach(lanes) { lane =>
            for {
              option <- map.get(lane)
              _ <- option match {
                    case None            => STM.unit
                    case Some(Nil)       => map.put(lane, List.empty)
                    case Some(_ :: tail) => map.put(lane, tail)
                  }
              respOp <- responsesMonitorMap.get(lane)
              _ <- respOp match {
                    case None     => responsesMonitorMap.put(lane, List(programId))
                    case Some(ls) => responsesMonitorMap.put(lane, ls :+ programId)
                  }
            } yield ()
          }
          .commit

        for {
          _ <- occupyLanes
          _ = println(s"after occ $programId $lanes")
          p <- waitUntilFree.bracket_(release)(program)
          _ = println(s"after release $programId $lanes")
        } yield p

      }
    }

}
