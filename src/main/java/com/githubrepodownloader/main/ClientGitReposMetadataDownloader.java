package com.githubrepodownloader.main;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.Props;
import com.typesafe.config.ConfigFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

/**
 * Created by ramakrishnas on 26/7/16.
 */
public class ClientGitReposMetadataDownloader {

 public static void main(String args[]) throws IOException {
     BufferedReader br = new BufferedReader(new InputStreamReader(System.in));
     String reposRange = br.readLine();

     ActorSystem system = ActorSystem.create("ClientGitReposMetadataDownloaderActorSystem", ConfigFactory.load("clientGitHubReposMetadataDownloader.conf"));
     ActorRef clientGitReposMetadataDownloaderActor = system.actorOf(Props.create(ClientGitReposMetadataDownloaderActor.class),
             "clientGitReposMetadataDownloaderActor");
     clientGitReposMetadataDownloaderActor.tell(reposRange, clientGitReposMetadataDownloaderActor);
    }
}
