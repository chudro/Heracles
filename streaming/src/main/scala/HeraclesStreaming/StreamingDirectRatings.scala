package HeraclesStreaming

//import com.datastax.bdp.spark.DseSparkConfHelper

import kafka.serializer.StringDecoder
import org.apache.spark.rdd.RDD
import org.apache.spark.sql.{SQLContext, SaveMode}
import org.apache.spark.streaming.dstream.DStream
import org.apache.spark.streaming.kafka.KafkaUtils
import org.apache.spark.streaming.{Seconds, StreamingContext, Time}
import org.apache.spark.{SparkConf, SparkContext}
import org.joda.time.DateTime

import scala.collection.mutable.ArrayBuffer


/** This uses the Kafka Direct introduced in Spark 1.4
  *
  */
object StreamingDirectRatings {

  //type alias for tuples, increases readablity
  type RatingCollector = (Int, Float)
  type MovieRatings = (Int, (Int, Float))

  def main(args: Array[String]) {

    if (args.length < 3) {
      println("first parameter is kafka broker ")
      println("second parameter is the kafka topic")
      println("third param whether to display debug output  (true|false) ")
    }

    val brokers = args(0)
    val topicsArg = args(1)
    val debugOutput = args(2).toBoolean

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
    import sqlContext.implicits._

    val topics: Set[String] = topicsArg.split(",").map(_.trim).toSet

    val kafkaParams = Map[String, String]("metadata.broker.list" -> brokers)
    println(s"connecting to brokers: $brokers")
    println(s"ssc: $ssc")
    println(s"kafkaParams: $kafkaParams")
    println(s"topics: $topics")

    val rawErrorsStream = KafkaUtils.createDirectStream[String, String, StringDecoder, StringDecoder](ssc, kafkaParams, topics)

    val errorsStream = rawErrorsStream.map { case (key, nxtRating) =>
      val parsedRating = nxtRating.split("::")
      val timestamp: Long = new DateTime(parsedRating(2).trim.toLong).getMillis
      ErrorMsg(parsedRating(0).trim.toInt, parsedRating(1).trim, timestamp)
    }

    errorsStream.foreachRDD {
      (error_msg: RDD[ErrorMsg], batchTime: Time) => {

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

    //calcAverageRatings(ratingsStream, sqlContext)

    ssc.start()
    ssc.awaitTermination()
  }

}
