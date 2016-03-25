package HeraclesFeeder

import akka.actor.{Actor, ActorLogging, Props}
import org.apache.kafka.clients.producer.{Callback, ProducerRecord, RecordMetadata}

import scala.concurrent.duration.{Duration, _}
import scala.util.Random

import org.joda.time.DateTime

case class ErrorMsg(error_id: Int, error_msg: String, error_time:Long) {
  override def toString: String = {
    s"$error_id::$error_msg::$error_time"
  }
}

/**
 * This keeps the file handle open and just reads on line at fixed time ticks.
 * Not the most efficient implementation, but it is the easiest.
 */
class RandomFeederActor(tickInterval:FiniteDuration) extends Actor with ActorLogging with FeederExtensionActor {

  log.info(s"Starting random feeder actor ${self.path}")

  import RandomFeederActor.SendNextLine

  var counter = 0

  implicit val executionContext = context.system.dispatcher

  val feederTick = context.system.scheduler.schedule(Duration.Zero, tickInterval, self, SendNextLine)

  val randMsgPicker = Random
  var errorMsgs: Array[String] = initData()
  val errorMsgsLength = errorMsgs.length

  val randId = Random
  //pick out of 15 million random ids
  val randIdLength = 15000000

  var errorMsgsSent = 0

  def receive = {
    case SendNextLine =>


      val nxtErrorString = errorMsgs(randMsgPicker.nextInt(errorMsgsLength))
      val nextId = randId.nextInt(randIdLength)

      val nxtErrorMsg = ErrorMsg(nextId, nxtErrorString, new DateTime().getMillis)

      errorMsgsSent += 1

      //rating data has the format user_id:movie_id:rating:timestamp
      //the key for the producer record is user_id + movie_id
      val key = s"${nxtErrorMsg.error_id}"
      val record = new ProducerRecord[String, String](feederExtension.kafkaTopic, key, nxtErrorMsg.toString)
      val future = feederExtension.producer.send(record, new Callback {
        override def onCompletion(result: RecordMetadata, exception: Exception) {
          if (exception != null) log.info("Failed to send record: " + exception)
          else {
            //periodically log the num of messages sent
            if (errorMsgsSent % 20 == 0)
              log.info(s"ratingsSent = $errorMsgsSent : ${nxtErrorMsg.toString}")
          }
        }
      })

    // Use future.get() to make this a synchronous write
  }

  def initData() = {
    scala.io.Source.fromFile(feederExtension.errorFile).getLines().toArray
  }
}

object RandomFeederActor {
  def props(tickInterval:FiniteDuration) = Props(new RandomFeederActor(tickInterval))
  case object ShutDown

  case object SendNextLine

}
