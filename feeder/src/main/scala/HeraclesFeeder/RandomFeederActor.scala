package HeraclesFeeder

import akka.actor.{Actor, ActorLogging, Props}
import org.apache.kafka.clients.producer.{Callback, ProducerRecord, RecordMetadata}
import org.joda.time.DateTime

import scala.concurrent.duration.{Duration, _}
import scala.util.Random


/**
 * This keeps the file handle open and just reads on line at fixed time ticks.
 * Not the most efficient implementation, but it is the easiest.
 */
class RandomFeederActor(tickInterval: FiniteDuration) extends Actor with ActorLogging with FeederExtensionActor {

  log.info(s"Starting random feeder actor ${self.path}")

  import RandomFeederActor.SendNextLine

  var counter = 0

  implicit val executionContext = context.system.dispatcher

  val feederTick = context.system.scheduler.schedule(Duration.Zero, tickInterval, self, SendNextLine)

  val randMsgPicker = Random
  var errorMsgs: Array[String] = initData()
  val errorMsgsLength = errorMsgs.length

  val ERROR_RAND_ID = Random
  //pick out of 15 million random ids
  val randErrorIdLength = 15000000
  var errorMsgsSent = 0

  val RAND_LOGIN_KEY = Random

  val RAND_USER_LOGIN_ID = 10000
  val RAND_USER_LOGIN_CONST = Random


  val RAND_LOGIN_SUCCESS = Random
  val RAND_LOGIN_LENGTH = 1000
  val loginFailures = 0
  var loginMsgsSent = 0

  def receive = {
    case SendNextLine =>

      val nextErrorStatus = if ((RAND_LOGIN_SUCCESS.nextInt(RAND_LOGIN_LENGTH) % 50) == 0) {
        (false, sendRandomErrorMsg())
      }
      else {
        //if there was no error just set the error id to 0
        (true, 0)
      }

      val ul = UserLogin(RAND_USER_LOGIN_CONST.nextInt(RAND_USER_LOGIN_ID), nextErrorStatus._1, nextErrorStatus._2)
      val key = RAND_LOGIN_KEY.nextInt().toString
      if (!nextErrorStatus._1) log.info(s"sending $key   $ul")
      loginMsgsSent += 1

      val record = new ProducerRecord[String, String](feederExtension.loginTopic, key, ul.toString)
      val future = feederExtension.producer.send(record, new Callback {
        override def onCompletion(result: RecordMetadata, exception: Exception) {
          if (exception != null) log.info("Failed to send record: " + exception)
          else {
            //periodically log the num of messages sent
            if (loginMsgsSent % 20 == 0)
              log.info(s"login sent = $loginMsgsSent : ${ul.toString}")
          }
        }
      })


    // Use future.get() to make this a synchronous write
  }

  def sendRandomErrorMsg() = {
    val nxtErrorString = errorMsgs(randMsgPicker.nextInt(errorMsgsLength))
    val nextErrorId = ERROR_RAND_ID.nextInt(randErrorIdLength)
    val nxtErrorMsg = ErrorMsg(nextErrorId, nxtErrorString, new DateTime().getMillis)
    errorMsgsSent += 1

    //rating data has the format user_id:movie_id:rating:timestamp
    //the key for the producer record is user_id + movie_id
    val key = s"${nxtErrorMsg.error_id}"
    val record = new ProducerRecord[String, String](feederExtension.errorTopic, key, nxtErrorMsg.toString)
    val future = feederExtension.producer.send(record, new Callback {
      override def onCompletion(result: RecordMetadata, exception: Exception) {
        if (exception != null) log.info("Failed to send record: " + exception)
        else {
          //periodically log the num of messages sent
          if (errorMsgsSent % 20 == 0)
            log.info(s"error msg sent = $errorMsgsSent : ${nxtErrorMsg.toString}")
        }
      }
    })

    nextErrorId
  }

  def initData() = {
    scala.io.Source.fromFile(feederExtension.errorFile).getLines().toArray
  }
}

object RandomFeederActor {
  def props(tickInterval: FiniteDuration) = Props(new RandomFeederActor(tickInterval))

  case object ShutDown

  case object SendNextLine

}
