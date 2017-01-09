package com.banno.twitter

import java.text.SimpleDateFormat
import java.util.{ Date, Locale }

import org.scalatest._
import prop._
import org.scalacheck.{ Arbitrary, Gen }

import org.http4s.{ Request, Uri }
import argonaut._, Argonaut._

import streaming._

class TwitterSpec extends PropSpec with GeneratorDrivenPropertyChecks with Matchers {
  val twitterFormat="EEE MMM dd HH:mm:ss ZZZZZ yyyy"
  val sf = new SimpleDateFormat(twitterFormat, Locale.ENGLISH)

  val genNEAlphaString = Gen.alphaStr.suchThat(!_.isEmpty)
  implicit lazy val arbNEString: Arbitrary[String] = Arbitrary(genNEAlphaString)
  implicit lazy val arbStats: Arbitrary[Stats] = Arbitrary(
    for {
      ls <- Gen.nonEmptyListOf[String](genNEAlphaString)
    } yield Stats(hashTags = MONOID.create(ls.map(s => (s, 1L))))
  )
  val genHashTag = Gen.frequency(
    1 -> "goldenglobes",
    1 -> "trump",
    1 -> "clinton",
    2 -> "neworleans",
    2 -> "sanfrancisco",
    2 -> "boston",
    2 -> "losangeles",
    2 -> "asheville",
    1 -> "populism",
    1 -> "elitism"
  )
  val genTweet = for {
    ht <- Gen.nonEmptyListOf[String](genHashTag)
    id <- Arbitrary.arbitrary[Long].map(BigInt.apply)
  } yield {
    val tags = ht.distinct
    Json(
      "coordinates" := ("coordinates" := jArray(List(jNumber(-75.14310264), jNumber(40.05701649)))),
      "created_at" := jString(sf.format(new Date())),
      "entities" := Json(
        "hashtags" := jArray(tags.map(jString(_))),
        "urls" := jEmptyArray,
        "user_mentions" := jEmptyArray
      ),
      "filter_level" := jString("medium"),
      "id" := id,
      "id_str" := jString(id.toString),
      "retweet_count" := jNumber(0),
      "retweeted" := jFalse,
      "text" := jString("Blah blah blah blah " + tags.map("#" + _).mkString(" ")),
      "user" := Json()              // Totally cheating, hope this works
    )
  }
  implicit lazy val arbJson: Arbitrary[Json] = Arbitrary(Gen.frequency(
    9 -> genTweet,
    1 -> Data.jsonValueGenerator()
  ))

  property("Getting Twitter Stream succeeds") {
    val testSize = 100
    val uri = Uri.uri("https://stream.twitter.com/1.1/statuses/sample.json")
    val req = Request(uri = uri)
    val s = stream("Q5iJkFWwqTcRfiEg5xcA4e38y", "HZOsyvvKpF3ASJtLYRA9E8cpHpO60NkPV5tbRVoyd0iUO6YIDN",
                   "803289399660199937-lqeTzoKzL5PxEncVIV9gedmN7BFwHeO", "IgPZb1WCwJu33iQi4MIWzQXcuEqDBx9tDG7F5SOZptyg7")(req)
    val r = s.take(testSize).runLog.run
    r should have size testSize
  }

  property("update() combines old Stats and new JSON correctly") {
    forAll ("old Stats", "new JSON") { (stats: Stats, json: Json) =>
      val ht = (json.hcursor --\ "entities" --\ "hashtags").as[List[String]].fold(
        (_, _) => List.empty[String],
        identity
      )
      val r = update(stats, json)
      assert(false)
    }
  }
}