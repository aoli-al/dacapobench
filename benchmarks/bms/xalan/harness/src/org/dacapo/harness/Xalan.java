/*
 * Copyright (c) 2005, 2009 The Australian National University.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Apache License v2.0.
 * You may obtain the license at
 * 
 *    http://www.opensource.org/licenses/apache2.0.php
 */
package org.dacapo.harness;

import java.io.*;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;

import org.dacapo.harness.Benchmark;
import org.dacapo.parser.Config;

/**
 * Xalan test harness. Uses a single pre-compiled stylesheet to transfrom a
 * number of sample files using a number of threads. The goal is to simulate a
 * typical server XSLT load which is performing XML to (X)HTML transforms as
 * part of a presentation layer.
 * 
 * date:  $Date: 2009-12-24 11:19:36 +1100 (Thu, 24 Dec 2009) $
 * id: $Id: Xalan.java 738 2009-12-24 00:19:36Z steveb-oss $
 */
public class Xalan extends Benchmark {
  Object benchmark;
  Method createWorkersMethod;

  public Xalan(Config config, File scratch, File data) throws Exception {
    super(config, scratch, data, false);
    Class<?> clazz = Class.forName("org.dacapo.xalan.XSLTBench", true, loader);
    this.method = clazz.getMethod("doWork");
    createWorkersMethod = clazz.getMethod("createWorkers", int.class, int.class);
    Constructor<?> constructor = clazz.getConstructor(File.class, File.class);
    useBenchmarkClassLoader();

    benchmark = constructor.newInstance(new File(data,"dat"), scratch);
  }

  /**
   * Create the threads, this is outside the timing loop to minimise the impact
   * of the startup. The threads will just sit waiting on the work queue.
   * 
   * @see org.dacapo.harness.Benchmark#preIteration(java.lang.String)
   */
  public void preIteration(String size) throws Exception {
    super.preIteration(size);

    String[] harnessArgs = config.getArgs(size);
    int iterations = Integer.parseInt(harnessArgs[0]);

    useBenchmarkClassLoader();
    try {
      createWorkersMethod.invoke(benchmark, new Object[] { config.getThreadCount(size), iterations });
    } finally {
      revertClassLoader();
    }
  }

  /*
   * Run the benchmark by just pushing jobs onto the work queue and waiting for
   * the threads to finish.
   * 
   * @see org.dacapo.harness.Benchmark#iterate(java.lang.String)
   */
  public void iterate(String size) throws Exception {
    method.invoke(benchmark);
    System.out.println("Normal completion.");
  }
}
