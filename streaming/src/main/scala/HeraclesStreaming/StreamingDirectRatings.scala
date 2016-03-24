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

    val rawRatingsStream = KafkaUtils.createDirectStream[String, String, StringDecoder, StringDecoder](ssc, kafkaParams, topics)

    val ratingsStream = rawRatingsStream.map { case (key, nxtRating) =>
      val parsedRating = nxtRating.split("::")
      val timestamp: Long = new DateTime(parsedRating(3).trim.toLong).getMillis
      Rating(parsedRating(0).trim.toInt, parsedRating(1).trim.toInt, parsedRating(2).trim.toFloat, timestamp)
    }

    ratingsStream.foreachRDD {
      (message: RDD[Rating], batchTime: Time) => {

        // convert each RDD from the batch into a Ratings DataFrame
        //rating data has the format user_id:movie_id:rating:timestamp
        val ratingDF = message.toDF()

        // this can be used to debug dataframes
        if (debugOutput)
          ratingDF.show()

        // save the DataFrame to Cassandra
        // Note:  Cassandra has been initialized through dse spark-submit, so we don't have to explicitly set the connection
        ratingDF.write.format("org.apache.spark.sql.cassandra")
          .mode(SaveMode.Append)
          .options(Map("keyspace" -> "movie_db", "table" -> "rating_by_movie"))
          .save()
      }
    }

    //calcAverageRatings(ratingsStream, sqlContext)

    ssc.start()
    ssc.awaitTermination()
  }

  def calcAverageRatings(ratingsStream: DStream[Rating], sqlContext:SQLContext): Unit = {

    import sqlContext.implicits._

    val createRatingCombiner = (rating: Float) => (1, rating)

    val ratingCombiner = (collector: RatingCollector, score: Float) => {
      val (numberScores, totalScore) = collector
      (numberScores + 1, totalScore + score)
    }

    val ratingsMerger = (collector1: RatingCollector, collector2: RatingCollector) => {
      val (numScores1, totalScore1) = collector1
      val (numScores2, totalScore2) = collector2
      (numScores1 + numScores2, totalScore1 + totalScore2)
    }

    val averagingFunction = (movieRatings: MovieRatings, time: Long) => {
      val (movieID, (numberScores, totalScore)) = movieRatings
      AverageRating(movieID, totalScore / numberScores, time)
    }

    val data = new ArrayBuffer[(Long, Double)]()
    val w = ratingsStream.window(Seconds(60), Seconds(5)).foreachRDD { (ratingRDD, time) =>

      println(s"count: ${ratingRDD.count()}")

      val keyedRatings: RDD[(Int, Float)] = ratingRDD.map(rating => (rating.movie_id, rating.rating))
      val combinedRatings = keyedRatings.combineByKey(createRatingCombiner, ratingCombiner, ratingsMerger)

      //val averagingFunctionWithTime:AverageRating = (_:MovieRatings, time.milliseconds)

      val averageRatings = combinedRatings.map {
        nxtRating: MovieRatings => averagingFunction(nxtRating, time.milliseconds)
      }.toDF()

      println(s"Averaged Ratings count: ${averageRatings.count()}")
      averageRatings.show()

      // save the DataFrame to Cassandra
      // Note:  Cassandra has been initialized through dse spark-submit, so we don't have to explicitly set the connection
      averageRatings.write.format("org.apache.spark.sql.cassandra")
        .mode(SaveMode.Append)
        .options(Map("keyspace" -> "movie_db", "table" -> "average_rating"))
        .save()
    }
  }
}
