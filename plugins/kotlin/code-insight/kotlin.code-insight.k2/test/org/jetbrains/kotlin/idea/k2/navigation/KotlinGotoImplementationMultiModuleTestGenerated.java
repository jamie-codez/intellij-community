// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.k2.navigation;

import com.intellij.testFramework.TestDataPath;
import org.jetbrains.kotlin.idea.base.plugin.KotlinPluginMode;
import org.jetbrains.kotlin.idea.base.test.TestRoot;
import org.jetbrains.kotlin.idea.test.JUnit3RunnerWithInners;
import org.jetbrains.kotlin.idea.test.KotlinTestUtils;
import org.jetbrains.kotlin.test.TestMetadata;
import org.junit.runner.RunWith;
import org.jetbrains.kotlin.idea.navigation.AbstractKotlinGotoImplementationMultiModuleTest;

/**
 * This class is generated by {@link org.jetbrains.kotlin.testGenerator.generator.TestGenerator}.
 * DO NOT MODIFY MANUALLY.
 */
@SuppressWarnings("all")
@TestRoot("code-insight/kotlin.code-insight.k2")
@TestDataPath("$CONTENT_ROOT")
@RunWith(JUnit3RunnerWithInners.class)
@TestMetadata("../../idea/tests/testData/navigation/implementations/multiModule")
public class KotlinGotoImplementationMultiModuleTestGenerated extends AbstractKotlinGotoImplementationMultiModuleTest {
    @java.lang.Override
    @org.jetbrains.annotations.NotNull
    public final KotlinPluginMode getPluginMode() {
        return KotlinPluginMode.K2;
    }

    private void runTest(String testDataFilePath) throws Exception {
        KotlinTestUtils.runTest(this::doTest, this, testDataFilePath);
    }

    @TestMetadata("actualFunction")
    public void testActualFunction() throws Exception {
        runTest("../../idea/tests/testData/navigation/implementations/multiModule/actualFunction/");
    }

    @TestMetadata("actualTypeAliasWithAnonymousSubclass")
    public void testActualTypeAliasWithAnonymousSubclass() throws Exception {
        runTest("../../idea/tests/testData/navigation/implementations/multiModule/actualTypeAliasWithAnonymousSubclass/");
    }

    @TestMetadata("expectClass")
    public void testExpectClass() throws Exception {
        runTest("../../idea/tests/testData/navigation/implementations/multiModule/expectClass/");
    }

    @TestMetadata("expectClassFun")
    public void testExpectClassFun() throws Exception {
        runTest("../../idea/tests/testData/navigation/implementations/multiModule/expectClassFun/");
    }

    @TestMetadata("expectClassProperty")
    public void testExpectClassProperty() throws Exception {
        runTest("../../idea/tests/testData/navigation/implementations/multiModule/expectClassProperty/");
    }

    @TestMetadata("expectClassSuperclass")
    public void testExpectClassSuperclass() throws Exception {
        runTest("../../idea/tests/testData/navigation/implementations/multiModule/expectClassSuperclass/");
    }

    @TestMetadata("expectClassSuperclassFun")
    public void testExpectClassSuperclassFun() throws Exception {
        runTest("../../idea/tests/testData/navigation/implementations/multiModule/expectClassSuperclassFun/");
    }

    @TestMetadata("expectClassSuperclassProperty")
    public void testExpectClassSuperclassProperty() throws Exception {
        runTest("../../idea/tests/testData/navigation/implementations/multiModule/expectClassSuperclassProperty/");
    }

    @TestMetadata("expectCompanion")
    public void testExpectCompanion() throws Exception {
        runTest("../../idea/tests/testData/navigation/implementations/multiModule/expectCompanion/");
    }

    @TestMetadata("expectEnumEntry")
    public void testExpectEnumEntry() throws Exception {
        runTest("../../idea/tests/testData/navigation/implementations/multiModule/expectEnumEntry/");
    }

    @TestMetadata("expectObject")
    public void testExpectObject() throws Exception {
        runTest("../../idea/tests/testData/navigation/implementations/multiModule/expectObject/");
    }

    @TestMetadata("suspendFunImpl")
    public void testSuspendFunImpl() throws Exception {
        runTest("../../idea/tests/testData/navigation/implementations/multiModule/suspendFunImpl/");
    }
}
