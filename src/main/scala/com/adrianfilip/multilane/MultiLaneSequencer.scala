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

  /**
   * The point of recorder is to give you the option to record requests and responses in order per lane
   * for monitoring and testing purposes.
   */
  def make(
    recorder: Option[Recorder] = None
  ): UIO[MultiLaneSequencer] =
    for {
      laningMap <- TMap.make[Lane, List[UUID]]().commit
    } yield new MultiLaneSequencer {
      def sequence[R, E, A](lanes: Set[Lane], programId: UUID, program: ZIO[R, E, A]): ZIO[R, E, A] = {

        val occupyLanes = STM
          .foreach(lanes) { lane =>
            for {
              _ <- appendToLane(laningMap, lane, programId)
              _ <- recorder match {
                    case Some(m) => appendToLane(m.requests, lane, programId)
                    case None    => STM.unit
                  }
            } yield ()
          }
          .commit

        val waitUntilFree = STM
          .foreach(lanes) { lane =>
            for {
              option <- laningMap.get(lane)
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
              _ <- removeFirstInLane(laningMap, lane)
              _ <- recorder match {
                    case Some(m) => appendToLane(m.responses, lane, programId)
                    case None    => STM.unit
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

  case class Recorder(
    requests: TMap[Lane, List[UUID]],
    responses: TMap[Lane, List[UUID]]
  )

  def appendToLane(map: TMap[Lane, List[UUID]], lane: Lane, id: UUID): STM[Nothing, Unit] =
    for {
      l <- map.get(lane)
      _ <- l match {
            case None     => map.put(lane, List(id))
            case Some(ls) => map.put(lane, ls :+ id)
          }
    } yield ()

  def removeFirstInLane(map: TMap[Lane, List[UUID]], lane: Lane): STM[Nothing, Unit] =
    for {
      option <- map.get(lane)
      _ <- option match {
            case None            => STM.unit
            case Some(Nil)       => map.put(lane, List.empty)
            case Some(_ :: tail) => map.put(lane, tail)
          }
    } yield ()

}
