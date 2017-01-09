package com.banno.twitter

object streaming {
  import scalaz.concurrent.Task
  import org.http4s.Request
  import org.http4s.client.blaze.SimpleHttp1Client

  /** The NIO-based HTTP Client.
    *
    * Because a streaming connection is long-lived, we use a dedicated Client
    * rather than a pooled one.
    */
  private[twitter] val client = SimpleHttp1Client()

  /** Twitter Streaming API requests must be OAuth (1) signed.
    *
    * This is just a simple helper function to take Strings and
    * construct the intermediate types for them.
    *
    * OAuth signing is an effect because it generates a nonce for
    * each call.
    */
  private[twitter] def sign(consumerKey: String, consumerSecret: String,
           tokenValue: String, tokenSecret: String)(req: Request): Task[Request] = {
    import org.http4s.client.oauth1.{ Consumer, Token, signRequest }

    val consumer = Consumer(consumerKey, consumerSecret)
    val token    = Token(tokenValue, tokenSecret)
    signRequest(
      req      = req,
      consumer = consumer,
      callback = None,
      verifier = None,
      token    = Some(token)
      )
  }

  /** Construct the Twitter stream of JSON values.
    *
    * This is a straightforward use of http4s' Client#streaming
    * function.
    *
    * It uses Argonaut, but could use any of http4s' supported JSON
    * libraries, which is nearly all of them.
    *
    * It attempts to handle stream termination, albeit somewhat naïvely.
    *
    * Because it strives to run forever, testing should be done against the
    * keys, secrets, and request you actually expect to use in production.
    * Just do something like .take(10) on the result, runLog the stream,
    * run the Task, and ensure the Vector has 10 elements.
    */
  import scalaz.stream.Process
  import argonaut.Json
  def stream(consumerKey: String, consumerSecret: String,
             tokenValue: String, tokenSecret: String)(req: Request): Process[Task, Json] =  {
    import scala.concurrent.duration._
    import scalaz.stream.time
    import jawnstreamz._

    implicit val S = scalaz.concurrent.Strategy.DefaultTimeoutScheduler
    implicit val f = jawn.support.argonaut.Parser.facade

    val s = for {
      sr <- Process.eval(sign(consumerKey, consumerSecret, tokenValue, tokenSecret)(req))
      js <- client.streaming(sr)(resp => resp.body.parseJsonStream)
    } yield js

    // Do something smarter here—log t, exponential fallback, etc.
    s.onFailure(t => time.sleep(5.seconds) ++ s).repeat
  }

  /** Our actual Stats type.
    *
    * The interesting bit is supporting "top N" stats on an infinite stream.
    * That's actually impossible, so we have to estimate them probabilistically.
    * This is a good opportunity to show off Algebird's SketchMap.
    */
  import com.twitter.algebird.{ SketchMap, SketchMapParams }
  private[twitter] val DELTA               = 1E-8
  private[twitter] val EPS                 = 0.001
  private[twitter] val SEED                = 1
  private[twitter] val HEAVY_HITTERS_COUNT = 5

  private[twitter] implicit def string2Bytes(i : String) = i.toCharArray.map(_.toByte)
  private[twitter] val PARAMS = SketchMapParams[String](SEED, EPS, DELTA, HEAVY_HITTERS_COUNT)
  private[twitter] val MONOID = SketchMap.monoid[String, Long](PARAMS)

  private[twitter] case class Stats(hashTags: SketchMap[String, Long])
  private[twitter] val zero = Stats(hashTags = MONOID.zero)

  /** A function to update the Stats value.
    * This is structured for use by process1#scan.
    * It's also ridiculously easy to test.
    */
  private[twitter] def update(old: Stats, v: Json): Stats = {
    (v.hcursor --\ "entities" --\ "hashtags").as[List[String]].fold(
      (_, _) => old,
      ls     => {
        val data = ls.map(s => (s, 1L))
        old.copy(hashTags = MONOID.plus(MONOID.create(data), old.hashTags))
      }
    )
  }

  /** A transducer from Process[F[_], Json] to Process[F[_], Stats].
    * Just pipe a Process[Task, Json] to it.
    * If you want, it's also easy to test, but probably not helpful
    * if you test the update function.
    */
  import scalaz.stream.process1
  private[twitter] val count = process1.scan(zero)(update)

  import scalaz.stream.async.signalOf
  // Suitable for querying with .get
  private[twitter] val currentStats = signalOf(zero)

  /** Main entry point.
    *
    * Pipes the Twitter stream to the count transducer,
    * then transforms the Stats to Signal#Set messages, which
    * are then sent to the currentStats' sink.
    * 
    * If you at least test update to your satisfaction, this is
    * essentially guaranteed to work.
    */
  def stats(consumerKey: String, consumerSecret: String,
            tokenValue: String, tokenSecret: String)(req: Request): Process[Task, Unit] = {
    import scalaz.stream.async.mutable.Signal
    val calc = stream(consumerKey, consumerSecret, tokenValue, tokenSecret)(req) |> count
    calc.map(Signal.Set.apply) to currentStats.sink
  }
}