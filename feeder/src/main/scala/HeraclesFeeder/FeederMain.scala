package HeraclesFeeder

import akka.actor.{Props, ActorSystem}

import scala.concurrent.duration.{FiniteDuration, Duration, MILLISECONDS}

object FeederMain {

  def main(args: Array[String]) {

    if (args.length < 2) {
      println("First argument is the number of feeders to start")
      println("Second argument is the time in Milliseconds to generate events in each actor")
      System.exit(0)
    }
    val numFeeders = args(0).toInt

    val system = ActorSystem("MyActorSystem")

    val tickDuration: FiniteDuration = Duration.create(args(1).toLong, MILLISECONDS)
    println(s"tick duration: ${tickDuration}")


    for (indx <- 1 to numFeeders) {
      val feederActor = system.actorOf(RandomFeederActor.props(tickDuration), s"feederActor-$indx")
    }

    system.awaitTermination()

  }

}
