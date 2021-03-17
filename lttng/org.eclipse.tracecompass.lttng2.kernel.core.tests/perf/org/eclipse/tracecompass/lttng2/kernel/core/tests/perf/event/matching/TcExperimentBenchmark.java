/*******************************************************************************
 * Copyright (c) 2020 École Polytechnique de Montréal
 *
 * All rights reserved. This program and the accompanying materials are
 * made available under the terms of the Eclipse Public License 2.0 which
 * accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.eclipse.tracecompass.lttng2.kernel.core.tests.perf.event.matching;

import java.io.File;

import org.eclipse.test.performance.Dimension;
import org.eclipse.test.performance.Performance;
import org.eclipse.test.performance.PerformanceMeter;
import org.eclipse.tracecompass.analysis.os.linux.core.execution.graph.OsExecutionGraph;
import org.eclipse.tracecompass.tmf.core.analysis.IAnalysisModule;
import org.eclipse.tracecompass.tmf.core.exceptions.TmfAnalysisException;
import org.eclipse.tracecompass.tmf.core.exceptions.TmfTraceException;
import org.eclipse.tracecompass.tmf.ctf.core.event.CtfTmfEvent;
import org.eclipse.tracecompass.tmf.ctf.core.trace.CtfTmfTrace;
import org.junit.Test;

/**
 * Benchmark Experiments (many traces) with different Trace Compass Analyzes
 *
 * @author Abdellah Rahmani
 */
public class TcExperimentBenchmark {

    private static final String TEST_ID = "org.eclipse.linuxtools# Experiment benchmarking#"; // To_change
    private static final String CPU = " (cpu usage)";
    private static final String TEST_SUMMARY = "TC scalabilty Experiment benchmark";

    /**
     * Tests an experiment (group of traces) with an analysis module
     * (OsExecutionGraph() in this example)
     *
     * @param directoryPath
     *            Path to the directory containing the group of traces
     * @param loopCount
     *            Number of iterations
     */
    public static void TestExperiment(String directoryPath, int loopCount) {

        File parentDirectory = new File(directoryPath);

        // List of all files and directories
        File filesList[] = parentDirectory.listFiles();

        int size = filesList.length;
        String testName = "Experiment of" + Integer.toString(size);
        Performance perf = Performance.getDefault();
        PerformanceMeter pm = perf.createPerformanceMeter(TEST_ID + testName + CPU);
        perf.tagAsSummary(pm, TEST_SUMMARY + ':' + testName + CPU, Dimension.CPU_TIME);

        for (int j = 0; j < loopCount; j++) {

            IAnalysisModule[] modules = new IAnalysisModule[size];
            CtfTmfTrace[] traces = new CtfTmfTrace[size];
            int i = 0;

            for (File file : filesList) {
                String path = file.getAbsolutePath() + "/kernel";
                CtfTmfTrace trace = new CtfTmfTrace();
                try {
                    trace.initTrace(null, path, CtfTmfEvent.class);
                } catch (TmfTraceException e) {
                    // TODO Auto-generated catch block
                    e.printStackTrace();
                }
                traces[i] = trace;

                try {
                    IAnalysisModule module = null;

                    module = new OsExecutionGraph();
                    module.setId("test");

                    try {
                        module.setTrace(trace);

                    } catch (TmfAnalysisException e) {
                        // TODO Auto-generated catch block
                        e.printStackTrace();
                    }
                    modules[i] = module;
                    module.dispose();

                } finally {
                }
                i++;
            }

            pm.start();
            for (IAnalysisModule mod : modules) {
                mod.schedule();
            }

            for (IAnalysisModule mod : modules) {
                mod.waitForCompletion();

            }
            pm.stop();
            i = 0;
            for (CtfTmfTrace trace : traces) {
                trace.dispose();
                modules[i].dispose();
            }
        }

        pm.commit();
    }

    /**
     * Runs the Experiment test
     */
    @Test
    public void RunTest() {

        // Put the path to the parent directory here
        String directoryPath = "/home/abdellah/Documents/TC_scalability/traces/multiNode_Traces/10_traces_100MB";
        int loopCount = 5;

        TestExperiment(directoryPath, loopCount);

    }
}
