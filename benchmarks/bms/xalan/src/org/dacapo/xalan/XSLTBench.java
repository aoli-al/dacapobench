/*
 * Copyright (c) 2005, 2009 The Australian National University.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Apache License v2.0.
 * You may obtain the license at
 * 
 *    http://www.opensource.org/licenses/apache2.0.php
 */
package org.dacapo.xalan;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.LinkedList;
import java.util.Properties;

import javax.xml.transform.ErrorListener;
import javax.xml.transform.Result;
import javax.xml.transform.Source;
import javax.xml.transform.Templates;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;

public class XSLTBench {

  private static final boolean VERBOSE = false;
  private static final String XALAN_VERSION = "Xalan Java 2.7.2";
  private static final String[] FILE_LIST = {
    "xalan/acks.xml",
    "xalan/binding.xml",
    "xalan/changes.xml",
    "xalan/concepts.xml",
    "xalan/controls.xml",
    "xalan/datatypes.xml",
    "xalan/expr.xml",
    "xalan/intro.xml",
    "xalan/model.xml",
    "xalan/prod-notes.xml",
    "xalan/references.xml",
    "xalan/rpm.xml",
    "xalan/schema.xml",
    "xalan/structure.xml",
    "xalan/template.xml",
    "xalan/terms.xml",
    "xalan/ui.xml"
  };

  private static Integer index = 0;  // counter of files processed, synchronize on this

  private final File scratch;
  private final File data;
  private int tasks;   // number of files to process
  private Templates template = null;  // object for storing a handle to a 'compiled' transformation stylesheet
  private XalanWorker[] workers = null;  // An array for the workers

  public XSLTBench(File data, File scratch) throws Exception {
    // Check Xalan version, this is easy to get wrong because its
    // bundled with Java these days, so we do explict check
    this.data = data;
    this.scratch = scratch;

    Properties props = System.getProperties();
    if (!org.apache.xalan.Version.getVersion().equals(XALAN_VERSION)) {
      System.err.println("***  Incorrect version of Xalan in use!");
      System.err.println("***     Should be '" + XALAN_VERSION + "',");
      System.err.println("***     actually is '" + org.apache.xalan.Version.getVersion() + "').");
      System.err.println("***  To fix this, extract the included xalan.jar:");
      System.err.println("***     unzip " + props.get("java.class.path") + " xalan.jar");
      System.err.println("***  and override your jvm's boot classpath:");
      System.err.println("***     java -Xbootclasspath/p:xalan.jar [...] ");
      throw new Exception("Please fix your bootclasspath and try again.");
    }

    // Fix the JAXP transformer to be Xalan
    String key = "javax.xml.transform.TransformerFactory";
    String value = "org.apache.xalan.processor.TransformerFactoryImpl";
    props.put(key, value);
    System.setProperties(props);

    // Compile the test stylesheet for later use
    Source stylesheet = new StreamSource(new File(data, "xalan/xmlspec.xsl"));
    TransformerFactory factory = TransformerFactory.newInstance();
    template = factory.newTemplates(stylesheet);
  }

  /**
   * Called before the start of a benchmark iteration
   * 
   * @param threads Number of worker threads
   */
  public void createWorkers(int threads) {
    // Set up the workers
    if (workers == null)
      workers = new XalanWorker[threads];
    for (int i = 0; i < threads; i++) {
      workers[i] = new XalanWorker(i);
    }
  }

  /**
   * The heart of a benchmark iteration
   * 
   * @param runs Number of times the files will be processed
   * @throws InterruptedException
   */
  public void doWork(int runs) throws InterruptedException {
    this.tasks = runs * FILE_LIST.length;
    index = 0;

    for (int i = 0; i < workers.length; i++) 
      workers[i].start();

    for (int i = 0; i < workers.length; i++) {
      if (VERBOSE)
        System.out.println("Waiting for thread " + i);
      workers[i].join();
    }
  }

  /**
   * Synchronize threads accessing next available work item
   *
   * @return the new task number
   */
  private int getNextTask() {
    int rtn;
    synchronized(index) {
      rtn = index++;
      int fivePercent = tasks/20;
      if (fivePercent > 5 && rtn < tasks && rtn % fivePercent == 0) {
        int percentage = 5 * (rtn / fivePercent);
        System.out.print("Processing: "+percentage+"%\r");
        System.out.flush();
      }
    }
    return rtn;
  }

  /*
   * Worker thread. Provided with a queue that input files can be selected from
   * and a template object that can be used to perform a transform from. Results
   * of the transfrom are saved in the scratch directory as normal.
   */
  class XalanWorker extends Thread implements ErrorListener {

    private int id;

    XalanWorker(int id) {
      this.id = id;
    }

    public void run() {
      try {
        if (VERBOSE)
          System.out.println("Worker thread starting");
        FileOutputStream outputStream = new FileOutputStream(new File(scratch, "xalan.out." + id));
        Result outFile = new StreamResult(outputStream);
        int task;
        while ((task = getNextTask()) < tasks) {
          String fileName = FILE_LIST[task % FILE_LIST.length];
          Transformer transformer = template.newTransformer();
          transformer.setErrorListener(this);
          FileInputStream inputStream = new FileInputStream(new File(data, fileName));
          Source inFile = new StreamSource(inputStream);
          transformer.transform(inFile, outFile);
          inputStream.close();
        }
      } catch (TransformerConfigurationException e) {
        e.printStackTrace();
      } catch (TransformerException e) {
        e.printStackTrace();
      } catch (IOException e) {
        e.printStackTrace();
      }
      if (VERBOSE)
        System.out.println("Worker thread exiting");
    }

    // Provide an ErrorListener so that stderr warnings can be surpressed
    public void error(TransformerException exception) throws TransformerException {
      throw exception;
    }

    public void fatalError(TransformerException exception) throws TransformerException {
      throw exception;
    }

    public void warning(TransformerException exception) throws TransformerException {
      // Ignore warnings, the test transforms create some
    }
  }
}
