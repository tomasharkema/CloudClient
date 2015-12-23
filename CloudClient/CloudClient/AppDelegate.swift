//
//  AppDelegate.swift
//  CloudClient
//
//  Created by Tomas Harkema on 07-12-15.
//  Copyright © 2015 Tomas Harkema Media. All rights reserved.
//

import Cocoa

@NSApplicationMain
class AppDelegate: NSObject, NSApplicationDelegate {

  var cloudService: CloudClientService?

  func applicationDidFinishLaunching(aNotification: NSNotification) {
    // Insert code here to initialize your application


    cloudService = CloudClientService()
    cloudService?.spawnProcess()
  }

  func applicationWillTerminate(aNotification: NSNotification) {
    cloudService?.terminate()
  }


}

