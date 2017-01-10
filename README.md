This is intended as an example of computing the top 5 hashtags in Twitter's Streaming API.

It uses [http4s](http://http4s.org), [Argonaut](http://argonaut.io), and [Algebird](https://twitter.github.io/algebird/) for the heavy lifting.
It also uses [ScalaTest](http://scalatest.org) and [ScalaCheck](http://scalacheck.org) for testing.
It also uses [Coursier](https://github.com/alexarchambault/coursier) and [Ammonite](http://www.lihaoyi.com/Ammonite/) for sanity-maintenance.

It strives to be minimalistic without being utterly naïve.
For example, on streaming errors, it waits 5 seconds and retries.
On stream termination (possibly due to Twitter load-balancing), it retries immediately.
Successful parsing of the Twitter stream to JSON is tested.
Updating the statistics is also tested, and because they're probabilistic, this is somewhat complex.
