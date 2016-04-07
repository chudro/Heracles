package HeraclesStreaming

//import com.datastax.bdp.spark.DseSparkConfHelper

import kafka.serializer.StringDecoder
import org.apache.spark.rdd.RDD
import org.apache.spark.sql.{SQLContext, SaveMode}
import org.apache.spark.streaming.dstream.DStream
import org.apache.spark.streaming.kafka.KafkaUtils
import org.apache.spark.streaming.{Seconds, StreamingContext, Time}
import org.apache.spark.{SparkConf, SparkContext}
import org.joda.time.{MutableDateTime, Days, DateTime}

import scala.collection.mutable.ArrayBuffer
import scala.util.Random


/** This uses the Kafka Direct introduced in Spark 1.4
  *
  */
object StreamingDirectRatings {

  //type alias for tuples, increases readablity
  type RatingCollector = (Int, Float)
  type MovieRatings = (Int, (Int, Float))

  val epoch = {
    val t = new MutableDateTime()
    t.setDate(0)
    t
  }

  val rand_dates = Random
  val currentDateTime = new MutableDateTime

  def main(args: Array[String]) {

    if (args.length < 4) {
      println("first parameter is kafka broker ")
      println("second parameter is the kafka topic for error msgs")
      println("third parameter is the kafka topic for login msgs")
      println("fourth param whether to display debug output  (true|false) ")
    }

    val brokers = args(0)
    val errorTopicsArg = args(1)
    val loginTopicsArg = args(2)
    val debugOutput = args(3).toBoolean

    //val sparkConf: SparkConf = DseSparkConfHelper.enrichSparkConf(new SparkConf().setAppName("SimpleSpark"))

    val sparkConf = new SparkConf()
    val contextDebugStr: String = sparkConf.toDebugString
    System.out.println("contextDebugStr = " + contextDebugStr)


    def createStreamingContext(): StreamingContext = {
      @transient val newSsc = new StreamingContext(sparkConf, Seconds(1))
      println(s"Creating new StreamingContext $newSsc")
      newSsc
    }

    val ssc = StreamingContext.getActiveOrCreate(createStreamingContext)
    val sc = SparkContext.getOrCreate(sparkConf)
    val sqlContext = SQLContext.getOrCreate(sc)

    val kafkaParams = Map[String, String]("metadata.broker.list" -> brokers)
    println(s"connecting to brokers: $brokers")
    println(s"ssc: $ssc")
    println(s"kafkaParams: $kafkaParams")


    extractRawLoginStream(loginTopicsArg, debugOutput, ssc, kafkaParams, sqlContext)
    extractRawErrorStream(errorTopicsArg, debugOutput, ssc, kafkaParams, sqlContext)

    ssc.start()
    ssc.awaitTermination()
  }

  def extractRawLoginStream(loginTopicsArg:String, debugOutput: Boolean, ssc: StreamingContext, kafkaParams: Map[String, String], sqlContext:SQLContext): Unit = {

    import sqlContext.implicits._

    val login_topics: Set[String] = loginTopicsArg.split(",").map(_.trim).toSet
    println(s"login_topics: $login_topics")
    val rawLoginsStream = KafkaUtils.createDirectStream[String, String, StringDecoder, StringDecoder](ssc, kafkaParams, login_topics)

    val loginsStream = rawLoginsStream.map { case (key, nxtLogins) =>
      val parsedLogins = nxtLogins.split("::")
      val login_success = parsedLogins(1).trim.toBoolean
      val timestamp: Long = new DateTime(parsedLogins(3).trim.toLong).getMillis
      UserLogin(parsedLogins(0).trim.toInt,login_success, parsedLogins(2).trim.toInt, timestamp)
    }

    loginsStream.foreachRDD {
      (user_login: RDD[UserLogin], batchTime: Time) => {

        // convert each RDD from the batch into a Ratings DataFrame
        //rating data has the format user_id:movie_id:rating:timestamp
        val user_login_df = user_login.toDF()

        // this can be used to debug dataframes
        if (debugOutput)
          user_login_df.show()

        // save the DataFrame to Cassandra
        // Note:  Cassandra has been initialized through dse spark-submit, so we don't have to explicitly set the connection
        user_login_df.write.format("org.apache.spark.sql.cassandra")
          .mode(SaveMode.Append)
          .options(Map("keyspace" -> "heracles_db", "table" -> "user_logins"))
          .save()
      }
    }
  }

  def extractRawErrorStream(errorTopicsArg: String, debugOutput: Boolean, ssc: StreamingContext, kafkaParams: Map[String, String], sqlContext:SQLContext): Unit = {

    import sqlContext.implicits._

    val error_topics: Set[String] = errorTopicsArg.split(",").map(_.trim).toSet
    println(s"error topics: $error_topics")
    val rawErrorsStream = KafkaUtils.createDirectStream[String, String, StringDecoder, StringDecoder](ssc, kafkaParams, error_topics)

    val errorsStream = rawErrorsStream.map { case (key, nxtRating) =>
      val parsedRating = nxtRating.split("::")
      val timestamp: Long = new DateTime(parsedRating(2).trim.toLong).getMillis
      ErrorMsg(parsedRating(0).trim.toInt, parsedRating(1).trim, timestamp)
    }

    errorsStream.foreachRDD {
      (error_msg: RDD[ErrorMsg], batchTime: Time) => {

        currentDateTime.addMinutes(rand_dates.nextInt(5))
        var lastDayOffset = (Days.daysBetween(epoch, currentDateTime)).getDays

        // convert each RDD from the batch into a Ratings DataFrame
        //rating data has the format user_id:movie_id:rating:timestamp
        val error_msg_df = error_msg.toDF()

        // this can be used to debug dataframes
        if (debugOutput)
          error_msg_df.show()

        // save the DataFrame to Cassandra
        // Note:  Cassandra has been initialized through dse spark-submit, so we don't have to explicitly set the connection
        error_msg_df.write.format("org.apache.spark.sql.cassandra")
          .mode(SaveMode.Append)
          .options(Map("keyspace" -> "heracles_db", "table" -> "error_msgs"))
          .save()
      }
    }
  }
}
