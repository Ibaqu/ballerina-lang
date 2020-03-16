/*
 * Copyright (c) 2019, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 * WSO2 Inc. licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.ballerinalang.packerina.task;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.ballerinalang.packerina.OsUtils;
import org.ballerinalang.packerina.buildcontext.BuildContext;
import org.ballerinalang.packerina.buildcontext.BuildContextField;
import org.ballerinalang.packerina.model.ExecutableJar;
import org.ballerinalang.test.runtime.entity.ModuleCoverage;
import org.ballerinalang.test.runtime.entity.ModuleStatus;
import org.ballerinalang.test.runtime.entity.TestReport;
import org.ballerinalang.test.runtime.entity.TestSuite;
import org.ballerinalang.test.runtime.util.CodeCoverageUtils;
import org.ballerinalang.test.runtime.util.TesterinaConstants;
import org.ballerinalang.testerina.core.TesterinaRegistry;
import org.ballerinalang.tool.LauncherUtils;
import org.ballerinalang.tool.util.BFileUtil;
import org.wso2.ballerinalang.compiler.tree.BLangPackage;
import org.wso2.ballerinalang.compiler.util.FileUtils;
import org.wso2.ballerinalang.util.Lists;
import org.wso2.ballerinalang.util.RepoUtils;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintStream;
import java.io.Writer;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;

import static org.ballerinalang.test.runtime.util.TesterinaConstants.TEST_RESULTS_FILE;
import static org.ballerinalang.test.runtime.util.TesterinaConstants.TEST_RESULTS_JSON;
import static org.ballerinalang.test.runtime.util.TesterinaConstants.TEST_RUNTIME_JAR_PREFIX;
import static org.ballerinalang.tool.LauncherUtils.createLauncherException;
import static org.wso2.ballerinalang.compiler.util.ProjectDirConstants.BALLERINA_HOME;
import static org.wso2.ballerinalang.compiler.util.ProjectDirConstants.BALLERINA_HOME_BRE;
import static org.wso2.ballerinalang.compiler.util.ProjectDirConstants.BALLERINA_HOME_LIB;
import static org.wso2.ballerinalang.compiler.util.ProjectDirConstants.BLANG_COMPILED_JAR_EXT;

/**
 * Task for executing tests.
 */
public class RunTestsTask implements Task {
    private final String[] args;
    private boolean coverage;
    private Path testJarPath;
    TestReport testReport;

    public RunTestsTask(boolean coverage, String[] args) {
        this.coverage = coverage;
        this.args = args;
        testReport = new TestReport();
    }

    public RunTestsTask(boolean coverage, String[] args, List<String> groupList, List<String> disableGroupList) {
        this.args = args;
        this.coverage = coverage;
        TesterinaRegistry testerinaRegistry = TesterinaRegistry.getInstance();
        if (disableGroupList != null) {
            testerinaRegistry.setGroups(disableGroupList);
            testerinaRegistry.setShouldIncludeGroups(false);
        } else if (groupList != null) {
            testerinaRegistry.setGroups(groupList);
            testerinaRegistry.setShouldIncludeGroups(true);
        }
        testReport = new TestReport();
    }

    @Override
    public void execute(BuildContext buildContext) {
        Path targetDir = Paths.get(buildContext.get(BuildContextField.TARGET_DIR).toString());
        buildContext.out().println();
        buildContext.out().print("Running Tests");
        if (coverage) {
            buildContext.out().print(" with Coverage");
            try {
                CodeCoverageUtils.deleteDirectory(targetDir.resolve(TesterinaConstants.COVERAGE_DIR).toFile());
            } catch (IOException e) {
                throw createLauncherException("error while cleaning up coverage data", e);
            }
        }
        buildContext.out().println();

        Path sourceRootPath = buildContext.get(BuildContextField.SOURCE_ROOT);
        List<BLangPackage> moduleBirMap = buildContext.getModules();
        testReport.setProjectName(sourceRootPath.toFile().getName());

        // Only tests in packages are executed so default packages i.e. single bal files which has the package name
        // as "." are ignored. This is to be consistent with the "ballerina test" command which only executes tests
        // in packages.
        for (BLangPackage bLangPackage : moduleBirMap) {
            TestSuite suite = TesterinaRegistry.getInstance().getTestSuites().get(bLangPackage.packageID.toString());
            if (suite == null) {
                buildContext.out().println();
                buildContext.out().println("\t" + bLangPackage.packageID);
                buildContext.out().println("\t" + "No tests found");
                buildContext.out().println();
                continue;
            }
            HashSet<Path> testDependencies = getTestDependencies(buildContext, bLangPackage);
            Path jsonPath = buildContext.getTestJsonPathTargetCache(bLangPackage.packageID);
            createTestJson(bLangPackage, suite, sourceRootPath, jsonPath);
            int testResult = runTestSuit(jsonPath, buildContext, testDependencies, bLangPackage);
            if (testResult != 0) {
                throw createLauncherException("there are test failures");
            }
            Path statusJsonPath = jsonPath.resolve(TesterinaConstants.STATUS_FILE);
            try {
                ModuleStatus moduleStatus = loadModuleStatusFromFile(statusJsonPath);
                testReport.addModuleStatus(String.valueOf(bLangPackage.packageID.name), moduleStatus);
            } catch (IOException e) {
                throw createLauncherException("error while generating test report", e);
            }

            if (coverage) {
                int coverageResult = generateCoverageReport(buildContext, testDependencies, bLangPackage);
                if (coverageResult != 0) {
                    throw createLauncherException("there are test failures");
                }
                Path coverageJsonPath = jsonPath.resolve(TesterinaConstants.COVERAGE_FILE);
                try {
                    ModuleCoverage moduleCoverage = loadModuleCoverageFromFile(coverageJsonPath);
                    testReport.addCoverage(String.valueOf(bLangPackage.packageID.name), moduleCoverage);
                } catch (IOException e) {
                    throw createLauncherException("error while generating test report", e);
                }
            }
        }
        testReport.finalizeTestResults(coverage);
        writeReportToJson(buildContext.out(), testReport, targetDir);
    }

    /**
     * Extract data from the given bLangPackage.
     *
     * @param bLangPackage Ballerina package
     * @param sourceRootPath Source root path
     * @param jsonPath Path to the test json
     */
    private static void createTestJson(BLangPackage bLangPackage, TestSuite suite, Path sourceRootPath, Path jsonPath) {
        // set data
        suite.setInitFunctionName(bLangPackage.initFunction.name.value);
        suite.setStartFunctionName(bLangPackage.startFunction.name.value);
        suite.setStopFunctionName(bLangPackage.stopFunction.name.value);
        suite.setPackageName(bLangPackage.packageID.toString());
        suite.setSourceRootPath(sourceRootPath.toString());
        // add module functions
        bLangPackage.functions.forEach(function -> {
            String functionClassName = BFileUtil.getQualifiedClassName(bLangPackage.packageID.orgName.value,
                                                                       bLangPackage.packageID.name.value,
                                                                       getClassName(function.pos.src.cUnitName));
            suite.addTestUtilityFunction(function.name.value, functionClassName);
        });
        // add test functions
        if (bLangPackage.containsTestablePkg()) {
            suite.setTestInitFunctionName(bLangPackage.getTestablePkg().initFunction.name.value);
            suite.setTestStartFunctionName(bLangPackage.getTestablePkg().startFunction.name.value);
            suite.setTestStopFunctionName(bLangPackage.getTestablePkg().stopFunction.name.value);
            bLangPackage.getTestablePkg().functions.forEach(function -> {
                String functionClassName = BFileUtil.getQualifiedClassName(bLangPackage.packageID.orgName.value,
                                                                           bLangPackage.packageID.name.value,
                                                                           getClassName(function.pos.src.cUnitName));
                suite.addTestUtilityFunction(function.name.value, functionClassName);
            });
        } else {
            suite.setSourceFileName(bLangPackage.packageID.sourceFileName.value);
        }
        // write to json
        writeToJson(suite, jsonPath);
    }

    /**
     * return the function name.
     *
     * @param function String value of a function
     * @return function name
     */
    private static String getClassName(String function) {
        return function.replace(".bal", "").replace("/", ".");
    }

    /**
     * Write the content into a json.
     *
     * @param testSuite Data that are parsed to the json
     */
    private static void writeToJson(TestSuite testSuite, Path jsonPath) {
        Path tmpJsonPath = Paths.get(jsonPath.toString(), TesterinaConstants.TESTERINA_TEST_SUITE);
        File jsonFile = new File(tmpJsonPath.toString());
        try (Writer writer = new OutputStreamWriter(new FileOutputStream(jsonFile), StandardCharsets.UTF_8)) {
            Gson gson = new Gson();
            String json = gson.toJson(testSuite);
            writer.write(new String(json.getBytes(StandardCharsets.UTF_8), StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw LauncherUtils.createLauncherException("couldn't read data from the Json file : " + e.toString());
        }
    }

    /**
     * Write the test report content into a json file and html file
     *
     * @param out PrintStream object to print messages to console
     * @param testReport Data that are parsed to the json
     */
    private void writeReportToJson(PrintStream out, TestReport testReport, Path reportPath) {
        out.println();
        out.println("Generating Test Report");
        File htmlFile = new File(reportPath.resolve(TEST_RESULTS_FILE).toString());
        File jsonFile = new File(reportPath.resolve(TEST_RESULTS_JSON).toString());
        try (Writer writer = new OutputStreamWriter(new FileOutputStream(htmlFile), StandardCharsets.UTF_8)) {
            Gson gson;
            if (this.coverage) {
                 gson = new Gson();
            } else {
                gson = new GsonBuilder().setExclusionStrategies(new TestReport.ReportExclusionStrategy()).create();
            }
            String json = gson.toJson(testReport);
            Path resourceDirectory = Paths.get("src", "main", "resources");
            //String htmlString = new String(Files.readAllBytes(Paths.get("./testerina_report_template/testerina_results.html"))); //Doesnt work
            String htmlString = "<html>\n" +
                    "    <script type=\"application/json\" id=\"jsonReport\"> $jsonData </script>\n" +
                    "</html>";
            htmlString = htmlString.replace("$jsonData", json);

            writer.write(new String(htmlString.getBytes(StandardCharsets.UTF_8), StandardCharsets.UTF_8));
            out.println("\t" + Paths.get("").toAbsolutePath().relativize(htmlFile.toPath()));
        } catch (IOException e) {
            throw LauncherUtils.createLauncherException("couldn't read data from the Json file : " + e.toString());
        }
    }

    private int runTestSuit(Path jsonPath, BuildContext buildContext, HashSet<Path> testDependencies,
                            BLangPackage bLangPackage) {
        List<String> cmdArgs = new ArrayList<>();
        cmdArgs.add(System.getProperty("java.command"));
        String mainClassName = TesterinaConstants.TESTERINA_LAUNCHER_CLASS_NAME;
        Path targetDir = Paths.get(buildContext.get(BuildContextField.TARGET_DIR).toString());
        String orgName = String.valueOf(bLangPackage.packageID.orgName);
        String packageName = String.valueOf(bLangPackage.packageID.name);

        String jacocoAgentJarPath = Paths.get(System.getProperty(BALLERINA_HOME)).resolve(BALLERINA_HOME_BRE)
                .resolve(BALLERINA_HOME_LIB).resolve(TesterinaConstants.AGENT_FILE_NAME).toString();
        try {
            if (coverage) {
                String agentCommand = "-javaagent:"
                        + jacocoAgentJarPath
                        + "=destfile="
                        + targetDir.resolve(TesterinaConstants.COVERAGE_DIR)
                        .resolve(TesterinaConstants.EXEC_FILE_NAME).toString()
                        + ",includes=" + orgName + "." + packageName + ".*";
                cmdArgs.add(agentCommand);
            }

            String classPath = getClassPath(getTestRuntimeJar(buildContext), testDependencies);
            cmdArgs.addAll(Lists.of("-cp", classPath, mainClassName, jsonPath.toString()));
            cmdArgs.addAll(Arrays.asList(args));
            cmdArgs.add(targetDir.toString());
            cmdArgs.add(testJarPath.toString());
            cmdArgs.add(orgName);
            cmdArgs.add(packageName);
            ProcessBuilder processBuilder = new ProcessBuilder(cmdArgs).inheritIO();
            Process proc = processBuilder.start();
            return proc.waitFor();
        } catch (IOException | InterruptedException e) {
            throw createLauncherException("unable to run the tests: " + e.getMessage());
        }
    }

    private int generateCoverageReport(BuildContext buildContext, HashSet<Path> testDependencies,
                                       BLangPackage bLangPackage) {
        List<String> cmdArgs = new ArrayList<>();
        cmdArgs.add(System.getProperty("java.command"));
        String mainClassName = TesterinaConstants.CODE_COV_GENERATOR_CLASS_NAME;
        Path jsonPath = buildContext.getTestJsonPathTargetCache(bLangPackage.packageID);
        Path targetDir = Paths.get(buildContext.get(BuildContextField.TARGET_DIR).toString());
        String orgName = String.valueOf(bLangPackage.packageID.orgName);
        String packageName = String.valueOf(bLangPackage.packageID.name);
        try {
            String classPath = getClassPath(getTestRuntimeJar(buildContext), testDependencies);
            cmdArgs.addAll(Lists.of("-cp", classPath, mainClassName, jsonPath.toString()));
            cmdArgs.addAll(Arrays.asList(args));
            cmdArgs.add(targetDir.toString());
            cmdArgs.add(testJarPath.toString());
            cmdArgs.add(orgName);
            cmdArgs.add(packageName);
            ProcessBuilder processBuilder = new ProcessBuilder(cmdArgs).inheritIO();
            Process proc = processBuilder.start();
            return proc.waitFor();

        } catch (IOException | InterruptedException e) {
            throw createLauncherException("unable to run the tests: " + e.getMessage());
        }
    }

    private HashSet<Path> getTestDependencies(BuildContext buildContext, BLangPackage bLangPackage) {
        if (bLangPackage.containsTestablePkg()) {
            testJarPath = buildContext.getTestJarPathFromTargetCache(bLangPackage.packageID);
        } else {
            // Single bal file test code will be in module jar
            testJarPath = buildContext.getJarPathFromTargetCache(bLangPackage.packageID);
        }
        ExecutableJar executableJar = buildContext.moduleDependencyPathMap.get(bLangPackage.packageID);
        HashSet<Path> testDependencies = new HashSet<>(executableJar.moduleLibs);
        testDependencies.addAll(executableJar.testLibs);
        testDependencies.add(testJarPath);
        return testDependencies;
    }

    private String getClassPath(Path testRuntimeJar, HashSet<Path> testDependencies) {
        String separator = ":";
        StringBuilder classPath = new StringBuilder();
        classPath.append(testRuntimeJar);
        if (OsUtils.isWindows()) {
            separator = ";";
        }
        for (Path testDependency : testDependencies) {
            classPath.append(separator).append(testDependency);
        }
        return classPath.toString();
    }

    private Path getTestRuntimeJar(BuildContext buildContext) {
        String balHomePath = buildContext.get(BuildContextField.HOME_REPO).toString();
        String ballerinaVersion = RepoUtils.getBallerinaVersion();
        String runtimeJarName = TEST_RUNTIME_JAR_PREFIX + ballerinaVersion + BLANG_COMPILED_JAR_EXT;
        return Paths.get(balHomePath, "bre", "lib", runtimeJarName);
    }

    /**
     * Loads the ModuleCoverage object by reading a given Json.
     *
     * @param coverageJsonPath file path of json file
     * @return ModuleCoverage object
     * @throws FileNotFoundException if file does not exist
     */
    private ModuleCoverage loadModuleCoverageFromFile(Path coverageJsonPath) throws IOException {
        Gson gson = new Gson();
        BufferedReader bufferedReader = Files.newBufferedReader(coverageJsonPath, StandardCharsets.UTF_8);
        return gson.fromJson(bufferedReader, ModuleCoverage.class);
    }

    /**
     * Loads the ModuleStatus object by reading a given Json.
     * @param statusJsonPath file path of json file
     * @return ModuleStatus object
     * @throws FileNotFoundException if file does not exist
     */
    private ModuleStatus loadModuleStatusFromFile(Path statusJsonPath) throws IOException {
        Gson gson = new Gson();
        BufferedReader bufferedReader = Files.newBufferedReader(statusJsonPath, StandardCharsets.UTF_8);
        return gson.fromJson(bufferedReader, ModuleStatus.class);
    }
}
