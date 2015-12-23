//
//  CloudClientService.swift
//  CloudClient
//
//  Created by Tomas Harkema on 07-12-15.
//  Copyright © 2015 Tomas Harkema Media. All rights reserved.
//

import Foundation

class CloudClientService {

  var pipe: NSPipe?
  var task: NSTask?

  init() {
    
  }

  func spawnProcess() {
    pipe = NSPipe()
    task = NSTask()
    guard let task = task, pipe = pipe else {
      print("blurk")
      return
    }

    task.launchPath = "/usr/bin/java"
    task.currentDirectoryPath = NSBundle.mainBundle().resourcePath!
    task.arguments = ["-jar", "cloud-client-one-jar.jar"]
    task.standardOutput = pipe
    task.standardOutput?.fileHandleForReading.readabilityHandler = { file in
      let data = file.availableData
      print(String(data: data, encoding: NSUTF8StringEncoding)!)

    }

    task.launch()
    

  }

  func terminate() {
    task?.terminate()
    task = nil
    pipe = nil
  }

  
}
