/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.githubrepodownloader.main

import java.io._

import akka.actor.{Actor, ActorSystem, Props}
import akka.routing.RoundRobinPool
import com.githubrepodownloader.logging.Logger
import com.typesafe.config.ConfigFactory
import org.apache.commons.httpclient.{HttpClient, MultiThreadedHttpConnectionManager}
import org.apache.commons.httpclient.methods.GetMethod
import org.json4s.JsonAST.{JField, JObject}
import org.json4s._
import org.json4s.jackson.JsonMethods._

import scala.collection.immutable.Map
import scala.util.Try

/**
  * Created by ramakrishnas on 18/7/16.
  */
case class GetRepositoryMetaData(printWriter: PrintWriter, repoMap: Map[String, String])

case class Superviser()

object GitHubRepoMetadataDownloader extends App {
    val configFile = getClass.getClassLoader.
      getResource("remote_gitHubReposMetadataDownloader.conf").getFile
    val config = ConfigFactory.parseFile(new File(configFile))
    val system = ActorSystem("GitHubRepoMetadataDownloader", config)
    val remoteActor = system.actorOf(Props[GitHubRepoMetadataDownloaderActor],
      name = "gitHubRepoMetadataDownloader")
}

class GitHubRepoMetadataDownloaderActor extends Actor with Logger {
  val conf = ConfigFactory.load()
  var noOfRepos = 0
  val noOfWorkers = conf.getString("noOfWorkers").toInt
  val printWriter = new PrintWriter("" + conf.getString("metadataDir") +
    self.path.name + ".txt")
  val workerRouter = context.actorOf(Props[RepositoryMetaDataDownloader].
    withRouter(RoundRobinPool(noOfWorkers)), "workers")

  override def receive: Actor.Receive = {
    case Superviser =>
      noOfRepos -= 1
      if (0 == noOfRepos) {
        printWriter.close()
        log.info("Task done...")

    }
    case msg =>
      sender() ! "RECEIVED"
      val args = msg.toString.split(',')
      val since = args(0).toInt
      val to = args(1).toInt
      log.info("Started Getting repositories info from " + since + " to " + to)
      var currentSince = since
      while (currentSince < to) {
        val (allGithubRepos, nextSince) = GitHubApiHelper.getAllGitHubRepos(currentSince)
        allGithubRepos.filter(repoMap => repoMap("fork").equals("false")).
          distinct.foreach(repoMap => {
          noOfRepos += 1
          workerRouter ! GetRepositoryMetaData(printWriter, repoMap)
        })
        currentSince = nextSince
      }
      sender() ! "COMPLETED"

  }
}

class RepositoryMetaDataDownloader extends Actor with Logger {
  override def receive: Actor.Receive = {
    case GetRepositoryMetaData(printWriter, repoMap) =>
      try {
        val repoMetadata: Option[String] =
          GitHubApiHelper.fetchAllDetails(repoMap)
        val repoMetadataJson = repoMetadata + "\n"
        printWriter.write(repoMetadataJson)
      }
      catch {
        case e: InterruptedException =>
          log.error(s"Failed to write " + e.printStackTrace())
      }
      finally {
        sender() ! Superviser
      }

  }
}

object GitHubApiHelper extends Logger {

  private val client = new HttpClient(new MultiThreadedHttpConnectionManager())
  val conf = ConfigFactory.load()
  var tokens = conf.getString("githubTokens").split(',')
  var currentToken = tokens(0)
  @volatile var lastIndex = 0

  def executeMethod(url: String, token: String): GetMethod = {
    val method = new GetMethod(url)
    method.setDoAuthentication(true)
    // Please add the oauth token instead of <token> here. Or github may give 403/401 as response.
    method.addRequestHeader("Authorization", s"token $token")
    log.debug(s"using token $token")
    client.executeMethod(method)
    val rateLimit = method.getResponseHeader("X-RateLimit-Remaining").getValue
    if (rateLimit == "0") {
      currentToken = nextToken()
      log.info("limit 0,token changed :" + currentToken)
    }
    method
  }

  def getAllGitHubRepos(since: Int): (List[Map[String, String]], Int) = {
    val method = executeMethod(s"https://api.github.com/repositories?since=$since", currentToken)
    val nextSinceValueRaw = Option(method.getResponseHeader("Link").getElements.toList(0).getValue)
    val nextSince = nextSinceValueRaw.get.substring(0, nextSinceValueRaw.get.length - 1).toInt
    val json = httpGetJson(method).toList
    // Here we can specify all the fields we need from repo query.
    val interestingFields = List("full_name", "fork")
    val allGitHubRepos = for {
      j <- json
      c <- j.children
      map = (for {
        JObject(child) <- c
        JField(name, value) <- child
        if interestingFields.contains(name)
      } yield name -> value.values.toString).toMap
    } yield map
    (allGitHubRepos, nextSince)
  }

  def httpGetJson(method: GetMethod): Option[JValue] = {
    val status = method.getStatusCode
    if (status == 200) {
      // ignored parsing errors if any, because we can not do anything about them anyway.
      Try(parse(method.getResponseBodyAsString)).toOption
    } else {
      log.error("Request failed with status:" + status + "Response:"
        + method.getResponseHeaders.mkString("\n") +
        "\nResponseBody " + method.getResponseBodyAsString)
      None
    }
  }

  def fetchAllDetails(repoMap: Map[String, String]): Option[String] = {
    for {
      repo <-
      httpGetJson(executeMethod("https://api.github.com/repos/" + repoMap("full_name"), currentToken))
    } yield compact(render(repo))

  }

  def nextToken(): String = {
    if (lastIndex == tokens.length - 1) {
      lastIndex = 0
      tokens(lastIndex)
    } else {
      lastIndex = lastIndex + 1
      tokens(lastIndex)
    }
  }

}