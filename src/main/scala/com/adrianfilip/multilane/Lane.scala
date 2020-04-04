package com.adrianfilip.multilane

sealed trait Lane
object Lane {

  case object LANE1 extends Lane
  case object LANE2 extends Lane
  case object LANE3 extends Lane

  case class CustomLane(name: String) extends Lane
}
